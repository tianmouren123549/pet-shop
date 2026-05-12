"""
【作用】将 comment_features 按 product_id 聚合为均值/计数等商品级统计特征。
【效果】输出 data/product_features_agg.csv，作为用户-商品 XGBoost 与在线打分的商品侧输入。
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="将评论级特征聚合为商品级特征（供用户-商品XGBoost使用）")
    parser.add_argument("--input", type=str, default="data/comment_features.csv", help="评论特征CSV")
    parser.add_argument("--output", type=str, default="data/product_features_agg.csv", help="输出商品聚合特征")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(args.input)
    required = {"product_id", "category", "text_len", "intent_label", "sentiment_label", "is_positive", "is_negative"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"输入缺少列: {missing}")

    def _cat_mode(s: pd.Series) -> str:
        m = s.mode()
        return str(m.iloc[0]) if len(m) else str(s.iloc[0])

    g = df.groupby("product_id", as_index=False).agg(
        category=("category", _cat_mode),
        review_cnt=("text_len", "count"),
        avg_text_len=("text_len", "mean"),
        positive_rate=("is_positive", "mean"),
        negative_rate=("is_negative", "mean"),
    )

    # 情感占比
    sent = (
        df.groupby(["product_id", "sentiment_label"])
        .size()
        .unstack(fill_value=0)
    )
    sent = sent.div(sent.sum(axis=1), axis=0).fillna(0)
    for col in sent.columns:
        g[f"sentiment_{col}_rate"] = g["product_id"].map(sent[col].to_dict()).fillna(0)

    # 意图占比
    intent = (
        df.groupby(["product_id", "intent_label"])
        .size()
        .unstack(fill_value=0)
    )
    intent = intent.div(intent.sum(axis=1), axis=0).fillna(0)
    for col in intent.columns:
        safe = str(col).replace(" ", "")
        g[f"intent_{safe}_rate"] = g["product_id"].map(intent[col].to_dict()).fillna(0)

    g.to_csv(out, index=False, encoding="utf-8-sig")
    print(f"输出: {out.resolve()}")
    print(f"商品数: {len(g)}")


if __name__ == "__main__":
    main()
