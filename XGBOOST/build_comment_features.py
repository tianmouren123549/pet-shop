"""
【作用】合并原始评论、意图 CSV、情感 CSV 等，生成每条评论级别的数值/类别特征表。
【效果】输出 data/comment_features.csv，作为评论级 XGBoost 训练与商品聚合特征的输入。
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="构建XGBoost训练所需的评论级特征表")
    parser.add_argument(
        "--raw_comments",
        type=str,
        default="../BERT/爬虫/comments_raw_all_current.csv",
        help="原始评论文件（需包含 product_id/comment_id/score/text/category）",
    )
    parser.add_argument(
        "--intent_csv",
        type=str,
        default="../BERT/爬虫/pet_intent_auto_all_v2.csv",
        help="意图标注结果（text,label）",
    )
    parser.add_argument(
        "--sentiment_csv",
        type=str,
        default="../BERT/pet_sentiment_data_all_v2.csv",
        help="情感标注结果（text,label）",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="data/comment_features.csv",
        help="输出特征文件",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    raw = pd.read_csv(args.raw_comments)
    intent = pd.read_csv(args.intent_csv)[["text", "label"]].rename(columns={"label": "intent_label"})
    sentiment = pd.read_csv(args.sentiment_csv)[["text", "label"]].rename(columns={"label": "sentiment_label"})

    df = raw.copy()
    df["text"] = df["text"].astype(str).str.replace(r"\s+", " ", regex=True).str.strip()
    intent["text"] = intent["text"].astype(str).str.replace(r"\s+", " ", regex=True).str.strip()
    sentiment["text"] = sentiment["text"].astype(str).str.replace(r"\s+", " ", regex=True).str.strip()

    # 同一文本可能重复出现，按首条映射即可
    intent_map = intent.drop_duplicates(subset=["text"], keep="first")
    sentiment_map = sentiment.drop_duplicates(subset=["text"], keep="first")

    df = df.merge(intent_map, on="text", how="left")
    df = df.merge(sentiment_map, on="text", how="left")

    df["text_len"] = df["text"].str.len()
    df["is_positive"] = (pd.to_numeric(df["score"], errors="coerce") >= 4).astype(int)
    df["is_negative"] = (pd.to_numeric(df["score"], errors="coerce") <= 2).astype(int)

    # 缺失兜底，避免后续编码失败
    df["intent_label"] = df["intent_label"].fillna("未知意图")
    df["sentiment_label"] = df["sentiment_label"].fillna("中评")
    df["category"] = df["category"].fillna("unknown")

    keep_cols = [
        "product_id",
        "comment_id",
        "category",
        "score",
        "text",
        "text_len",
        "intent_label",
        "sentiment_label",
        "is_positive",
        "is_negative",
    ]
    out = df[keep_cols].copy()
    out.to_csv(output_path, index=False, encoding="utf-8-sig")

    print(f"输出文件: {output_path.resolve()}")
    print(f"样本数: {len(out)}")
    print("意图分布:")
    print(out["intent_label"].value_counts())
    print("情感分布:")
    print(out["sentiment_label"].value_counts())


if __name__ == "__main__":
    main()
