"""
【作用】加载用户级 XGBoost 模型，对用户候选商品批量打分，取 TopN 并可与评论级模型分数加权融合。
【效果】输出 user_recommendations.csv（含 reason_json 等），再可由 write_recommendations_mysql 写入数据库。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import pandas as pd


def load_product_comment_xgb_scores(comment_features_path: Path, comment_model_dir: Path) -> pd.DataFrame:
    """
    评论级 XGBoost（train_xgboost.py，输入为 BERT 管线产出的意图/情感等）按条打分，
    再按 product_id 取均值，得到与用户无关的「评论侧商品分」。
    """
    pipe_path = comment_model_dir / "pipeline.joblib"
    if not pipe_path.exists():
        raise FileNotFoundError(f"评论级模型不存在: {pipe_path}，请先运行 train_xgboost.py")
    pipe = joblib.load(pipe_path)
    c = pd.read_csv(comment_features_path)
    required = {"product_id", "category", "text_len", "intent_label", "sentiment_label"}
    missing = required - set(c.columns)
    if missing:
        raise ValueError(f"comment_features 缺少列 {missing}，当前: {list(c.columns)}")

    X = c[["category", "text_len", "intent_label", "sentiment_label"]].copy()
    for col in ("category", "intent_label", "sentiment_label"):
        X[col] = X[col].fillna("unknown").astype(str)
    X["text_len"] = pd.to_numeric(X["text_len"], errors="coerce")

    lp = pipe.predict_proba(X)[:, 1]
    out = c[["product_id"]].copy()
    out["_lp"] = lp
    g = out.groupby("product_id", as_index=False).agg(score_comment_xgb=("_lp", "mean"))
    return g


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="为每个用户生成 TopN 推荐分数（输出 CSV，可再导入 MySQL）")
    p.add_argument("--model_dir", type=str, default="models/xgb_user_item_v1")
    p.add_argument("--product_features", type=str, default="data/product_features_agg.csv")
    p.add_argument(
        "--orders_csv",
        type=str,
        default="data/orders_user_product.csv",
        help="用于提取 user_id 列表与 user_order_cnt",
    )
    p.add_argument("--products_csv", type=str, default="", help="可选，与训练时一致")
    p.add_argument("--topn", type=int, default=20)
    p.add_argument("--model_version", type=str, default="xgb_user_item_v1")
    p.add_argument("--output", type=str, default="outputs/user_recommendations.csv")
    p.add_argument(
        "--comment_model_dir",
        type=str,
        default="",
        help="可选：评论级 XGBoost 目录（如 models/xgb_comment_ranker），与 BERT 标注特征结合；不设则仅用户级分数",
    )
    p.add_argument(
        "--comment_features_csv",
        type=str,
        default="data/comment_features.csv",
        help="与 train_xgboost.py 相同输入，用于计算按商品聚合的评论侧分",
    )
    p.add_argument(
        "--hybrid_alpha",
        type=float,
        default=0.65,
        help="混合权重：最终分 = alpha * 用户级XGB分 + (1-alpha) * 评论级XGB商品均分（仅当指定 comment_model_dir 时生效）",
    )
    return p


def main() -> None:
    args = build_parser().parse_args()
    pipe = joblib.load(Path(args.model_dir) / "pipeline.joblib")
    meta_path = Path(args.model_dir) / "feature_meta.json"
    if not meta_path.exists():
        raise FileNotFoundError(f"缺少 {meta_path}，请先运行 train_xgboost_user_item.py")

    feat = pd.read_csv(args.product_features)
    if "review_cnt" in feat.columns:
        feat = feat[feat["review_cnt"] > 0]

    orders = pd.read_csv(args.orders_csv)
    if "user_id" not in orders.columns or "product_id" not in orders.columns:
        raise ValueError("orders_csv 需要 user_id, product_id")

    user_order_cnt = (
        orders.groupby("user_id", as_index=False)
        .agg(user_order_cnt=("product_id", "count"))
    )
    users = user_order_cnt["user_id"].unique()

    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    cat_cols = meta.get("categorical_features", [])
    num_cols = meta.get("numerical_features", [])
    trained_cols = set(cat_cols + num_cols)

    products_extra = None
    if args.products_csv and Path(args.products_csv).exists():
        products_extra = pd.read_csv(args.products_csv)

    comment_by_product: pd.DataFrame | None = None
    if args.comment_model_dir:
        cdir = Path(args.comment_model_dir)
        cpath = Path(args.comment_features_csv)
        if not cpath.exists():
            raise FileNotFoundError(f"混合推荐需要评论特征文件: {cpath.resolve()}")
        alpha = float(args.hybrid_alpha)
        if not 0.0 <= alpha <= 1.0:
            raise ValueError("--hybrid_alpha 须在 [0,1] 内")
        comment_by_product = load_product_comment_xgb_scores(cpath, cdir)
        print(
            f"混合模式: comment_model_dir={cdir} alpha={alpha} "
            f"(评论侧商品数 {len(comment_by_product)})"
        )

    rows = []
    for uid in users:
        ucnt = int(user_order_cnt.loc[user_order_cnt["user_id"] == uid, "user_order_cnt"].iloc[0])
        base = feat.copy()
        base["user_id"] = uid
        base["user_order_cnt"] = ucnt
        if products_extra is not None:
            merge_cols = ["product_id"]
            for c in ("price", "stock", "category_id", "brand_id"):
                if c in products_extra.columns and c in trained_cols:
                    merge_cols.append(c)
            if len(merge_cols) > 1:
                base = base.merge(products_extra[merge_cols], on="product_id", how="left")

        use_cols = cat_cols + num_cols
        missing = [c for c in use_cols if c not in base.columns]
        if missing:
            raise ValueError(f"推理特征缺列: {missing}，请保证与训练一致（可先重新导出 products 或重训模型）")

        X = base[use_cols].copy()
        for c in cat_cols:
            X[c] = X[c].fillna("unknown").astype(str)
        for c in num_cols:
            X[c] = pd.to_numeric(X[c], errors="coerce")

        base["score_u2i"] = pipe.predict_proba(X)[:, 1]
        if comment_by_product is not None:
            base = base.merge(comment_by_product, on="product_id", how="left")
            base["score_comment_xgb"] = base["score_comment_xgb"].fillna(0.5)
            alpha = float(args.hybrid_alpha)
            base["score"] = alpha * base["score_u2i"] + (1.0 - alpha) * base["score_comment_xgb"]
        else:
            base["score"] = base["score_u2i"]

        top = base.nlargest(args.topn, "score")
        for rank, (_, r) in enumerate(top.iterrows(), start=1):
            if comment_by_product is not None:
                reason = {
                    "source": "hybrid_u2i_comment_xgb",
                    "alpha": float(args.hybrid_alpha),
                    "score_u2i": float(r["score_u2i"]),
                    "score_comment_xgb": float(r["score_comment_xgb"]),
                }
            else:
                reason = {"source": "xgb_user_item"}
            rows.append(
                {
                    "user_id": int(uid),
                    "product_id": int(r["product_id"]),
                    "model_version": args.model_version,
                    "score": float(r["score"]),
                    "rank_no": rank,
                    "reason_json": json.dumps(reason, ensure_ascii=False),
                }
            )

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(rows).to_csv(out, index=False, encoding="utf-8-sig")
    print(f"输出: {out.resolve()} 共 {len(rows)} 条")


if __name__ == "__main__":
    main()
