"""
【作用】清洗原始评论 CSV，过滤过短文本，输出待人工或半自动标注的意图训练表。
【效果】生成如 pet_intent_todo.csv，列对齐后可供标注工具或 auto_label 使用。
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


def clean_text(s: str) -> str:
    s = str(s).replace("\u00a0", " ").strip()
    s = " ".join(s.split())
    return s


def main() -> None:
    parser = argparse.ArgumentParser(description="将原始评论转为待标注训练集")
    parser.add_argument("--input", type=str, default="comments_raw.csv")
    parser.add_argument("--output", type=str, default="pet_intent_todo.csv")
    parser.add_argument("--min_len", type=int, default=6)
    args = parser.parse_args()

    df = pd.read_csv(args.input)
    if "text" not in df.columns:
        raise ValueError("输入文件缺少 text 列")

    df["text"] = df["text"].astype(str).map(clean_text)
    df = df[df["text"].str.len() >= args.min_len].copy()
    if "comment_id" in df.columns:
        df = df.drop_duplicates(subset=["comment_id"], keep="first")
    df = df.drop_duplicates(subset=["text"], keep="first")

    out = df[["text"]].copy()
    out["label"] = ""
    out.to_csv(args.output, index=False, encoding="utf-8-sig")

    print(f"清洗后样本数: {len(out)}")
    print(f"输出文件: {Path(args.output).resolve()}")
    print("请人工填写 label 列，再用于训练。")


if __name__ == "__main__":
    main()

