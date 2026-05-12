"""
【作用】从原始评论 CSV 读取星级与正文，规则细化后构造「好评/中评/差评」三分类训练集。
【效果】写出含 text、label 等列的 CSV，作为 train_pet_bert 的情感任务输入数据。
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

POS_HINTS = ["好评", "很好", "非常好", "满意", "推荐", "回购", "爱吃", "认准", "下次还买", "性价比高"]
NEG_HINTS = ["差评", "不行", "吐", "拉稀", "软便", "难吃", "有问题", "发霉", "异味", "退款", "退货", "破损"]


def score_to_label(score: int) -> str:
    if score >= 4:
        return "好评"
    if score == 3:
        return "中评"
    return "差评"


def refine_three_star_label(text: str, label: str) -> tuple[str, str]:
    if label != "中评":
        return label, "score"
    s = str(text)
    has_pos = any(k in s for k in POS_HINTS)
    has_neg = any(k in s for k in NEG_HINTS)
    if has_pos and not has_neg:
        return "好评", "score+pos_hint"
    if has_neg and not has_pos:
        return "差评", "score+neg_hint"
    return "中评", "score"


def main() -> None:
    parser = argparse.ArgumentParser(description="从评论原始数据构建情感分类训练集（好评/中评/差评）")
    parser.add_argument("--input", type=str, default="爬虫/comments_raw_all.csv")
    parser.add_argument("--output", type=str, default="pet_sentiment_data.csv")
    parser.add_argument("--min_len", type=int, default=6)
    args = parser.parse_args()

    df = pd.read_csv(args.input)
    required = {"text", "score"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"输入文件缺少必要列: {missing}")

    out = df.copy()
    out["text"] = out["text"].astype(str).str.replace("\u00a0", " ").str.strip()
    out["text"] = out["text"].str.replace(r"\s+", " ", regex=True)
    out = out[out["text"].str.len() >= args.min_len].copy()

    out["score"] = pd.to_numeric(out["score"], errors="coerce")
    out = out.dropna(subset=["score"])
    out["score"] = out["score"].astype(int).clip(lower=1, upper=5)
    out["label"] = out["score"].map(score_to_label)
    refined = out.apply(lambda r: refine_three_star_label(r["text"], r["label"]), axis=1)
    out["label"] = refined.map(lambda x: x[0])
    out["label_reason"] = refined.map(lambda x: x[1])

    if "comment_id" in out.columns:
        out = out.drop_duplicates(subset=["comment_id"], keep="first")
    out = out.drop_duplicates(subset=["text"], keep="first")

    final_df = out[["text", "label", "score", "label_reason"]].copy()
    final_df.to_csv(args.output, index=False, encoding="utf-8-sig")

    print(f"输出文件: {Path(args.output).resolve()}")
    print("情感标签分布:")
    print(final_df["label"].value_counts())
    print("标注来源分布:")
    print(final_df["label_reason"].value_counts())


if __name__ == "__main__":
    main()

