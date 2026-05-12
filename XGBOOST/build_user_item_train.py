"""
【作用】将订单正样本与商品特征表拼接，并对未交互商品做负采样，生成用户-商品二分类训练集。
【效果】输出 data/user_item_train.csv，列含特征与 label，供 train_xgboost_user_item 训练。
"""

from __future__ import annotations

import argparse
import random
from pathlib import Path

import pandas as pd


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="构建用户-商品训练样本（正样本来自订单，负样本随机采样）")
    p.add_argument(
        "--orders_csv",
        type=str,
        default="data/orders_user_product.csv",
        help="订单明细导出：列 user_id, product_id（同一用户对同一商品多条可去重）",
    )
    p.add_argument(
        "--product_features",
        type=str,
        default="data/product_features_agg.csv",
        help="aggregate_product_features.py 产出的商品聚合特征",
    )
    p.add_argument(
        "--products_csv",
        type=str,
        default="",
        help="可选：商品表导出 product_id,price,stock,category_id,brand_id 用于补充数值特征",
    )
    p.add_argument("--neg_ratio", type=int, default=3, help="每个正样本配几条负样本")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--output", type=str, default="data/user_item_train.csv")
    return p


def main() -> None:
    args = build_parser().parse_args()
    random.seed(args.seed)
    orders_path = Path(args.orders_csv)
    if not orders_path.exists():
        raise FileNotFoundError(
            f"未找到订单文件: {orders_path}\n"
            "请用 sql/export_for_xgboost.sql 从库导出，或运行 simulate_orders.py 生成同列 CSV。"
        )

    pos = pd.read_csv(orders_path)
    for col in ("user_id", "product_id"):
        if col not in pos.columns:
            raise ValueError(f"orders_csv 必须包含列: user_id, product_id，当前列: {list(pos.columns)}")
    pos = pos[["user_id", "product_id"]].drop_duplicates()
    pos["label"] = 1

    feat = pd.read_csv(args.product_features)
    if "product_id" not in feat.columns:
        raise ValueError("product_features 缺少 product_id")

    prod_ids = set(feat["product_id"].astype(int))

    # 用户历史订单数（简单用户侧特征）
    user_order_cnt = pos.groupby("user_id", as_index=False).agg(user_order_cnt=("product_id", "count"))

    products_extra = None
    if args.products_csv and Path(args.products_csv).exists():
        products_extra = pd.read_csv(args.products_csv)

    neg_rows = []
    all_pids = list(prod_ids)

    for uid, g in pos.groupby("user_id"):
        bought = set(g["product_id"].astype(int))
        n_pos = len(bought)
        n_neg = min(n_pos * args.neg_ratio, max(len(all_pids) - len(bought), 0))
        candidates = [p for p in all_pids if p not in bought]
        if not candidates or n_neg <= 0:
            continue
        take = random.sample(candidates, min(n_neg, len(candidates)))
        for pid in take:
            neg_rows.append({"user_id": int(uid), "product_id": int(pid), "label": 0})

    neg = pd.DataFrame(neg_rows)
    train = pd.concat([pos, neg], ignore_index=True)
    train = train.merge(feat, on="product_id", how="inner")
    train = train.merge(user_order_cnt, on="user_id", how="left")
    train["user_order_cnt"] = train["user_order_cnt"].fillna(0).astype(int)

    if products_extra is not None:
        merge_cols = ["product_id"]
        for c in ("price", "stock", "category_id", "brand_id"):
            if c in products_extra.columns:
                merge_cols.append(c)
        train = train.merge(products_extra[merge_cols], on="product_id", how="left")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    train.to_csv(out, index=False, encoding="utf-8-sig")
    print(f"输出: {out.resolve()}")
    print(f"正样本: {(train['label']==1).sum()} 负样本: {(train['label']==0).sum()} 行数: {len(train)}")


if __name__ == "__main__":
    main()
