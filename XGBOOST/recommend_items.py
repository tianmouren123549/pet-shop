"""
【作用】加载评论级 XGBoost 模型，对商品维度聚合打分并排序，导出推荐榜单。
【效果】生成如 outputs/topn_recommendations.csv，用于离线商品排序分析或报告图表。
"""

from __future__ import annotations

import argparse
from pathlib import Path

import joblib
import pandas as pd


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="用XGBoost对商品进行推荐排序（商品级）")
    parser.add_argument("--data", type=str, default="data/comment_features.csv", help="评论特征CSV")
    parser.add_argument(
        "--model_dir",
        type=str,
        default="models/xgb_comment_ranker",
        help="模型目录（包含 pipeline.joblib）",
    )
    parser.add_argument("--topn", type=int, default=10, help="每个类目输出TopN")
    parser.add_argument(
        "--output",
        type=str,
        default="outputs/topn_recommendations.csv",
        help="推荐结果输出路径",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(args.data)
    pipe = joblib.load(Path(args.model_dir) / "pipeline.joblib")

    features = df[["category", "text_len", "intent_label", "sentiment_label"]].copy()
    df["like_prob"] = pipe.predict_proba(features)[:, 1]

    # 评论级概率聚合到商品级
    item_df = (
        df.groupby(["product_id", "category"], as_index=False)
        .agg(
            recommend_score=("like_prob", "mean"),
            review_count=("comment_id", "count"),
            positive_rate=("is_positive", "mean"),
            negative_rate=("is_negative", "mean"),
        )
        .sort_values(["category", "recommend_score", "review_count"], ascending=[True, False, False])
    )

    topn_df = item_df.groupby("category", as_index=False, group_keys=False).head(args.topn)
    topn_df.to_csv(out_path, index=False, encoding="utf-8-sig")

    print(f"输出文件: {out_path.resolve()}")
    print("各类目Top1预览:")
    print(topn_df.groupby("category", as_index=False).head(1))


if __name__ == "__main__":
    main()
