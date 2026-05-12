"""
【作用】基于关键词规则对评论文本打「宠物意图」粗标签，快速扩充意图分类训练样本。
【效果】输出带 text、label 的 CSV，与 build_comment_features 中意图列来源衔接。
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


RULES: list[tuple[str, list[str]]] = [
    ("售后退换", ["退货", "换货", "售后", "退款", "破损", "坏了", "不一致", "质量问题", "赔偿", "发霉", "异物"]),
    ("物流发货", ["发货", "物流", "快递", "几天到", "多久到", "单号", "到货", "隔天到", "配送", "送货"]),
    ("价格优惠", ["优惠", "便宜", "折扣", "满减", "券", "包邮", "活动", "降价", "性价比", "贵", "涨价"]),
    (
        "商品反馈",
        [
            "玩具",
            "喷雾",
            "牵引",
            "清洁",
            "除臭",
            "味道很重",
            "做工",
            "材质",
            "异味",
            "效果一般",
            "漏液",
            "不耐用",
        ],
    ),
    ("商品咨询", ["配料", "成分", "规格", "颗粒", "重量", "口味", "是否适合", "保质期", "真假", "日期"]),
    ("喂养咨询", ["幼猫", "成猫", "幼犬", "成犬", "喂", "喂养", "驱虫", "肠胃", "软便", "营养", "毛发", "适口性", "挑食"]),
]


def infer_label(text: str) -> tuple[str, str]:
    s = str(text)
    hit_labels: list[str] = []
    hit_keys: list[str] = []
    for label, keys in RULES:
        for k in keys:
            if k in s:
                hit_labels.append(label)
                hit_keys.append(k)
                break
    if len(hit_labels) == 1:
        return hit_labels[0], f"rule:{hit_keys[0]}"
    if len(hit_labels) > 1:
        # 按规则先后顺序优先级解决冲突
        for label, _ in RULES:
            if label in hit_labels:
                return label, f"rule:multi->{label}"
    # 没命中规则：默认归入商品咨询，避免喂养咨询被“兜底”放大
    return "商品咨询", "rule:default"


def main() -> None:
    parser = argparse.ArgumentParser(description="宠物电商评论规则初标注")
    parser.add_argument("--input", type=str, default="pet_intent_todo.csv")
    parser.add_argument("--output", type=str, default="pet_intent_auto.csv")
    args = parser.parse_args()

    df = pd.read_csv(args.input)
    if "text" not in df.columns:
        raise ValueError("输入文件必须包含 text 列")

    texts = df["text"].astype(str).tolist()
    labels: list[str] = []
    reasons: list[str] = []
    for t in texts:
        y, r = infer_label(t)
        labels.append(y)
        reasons.append(r)

    out = pd.DataFrame({"text": texts, "label": labels, "label_reason": reasons})
    out.to_csv(args.output, index=False, encoding="utf-8-sig")

    print(f"输出文件: {Path(args.output).resolve()}")
    print("标签分布:")
    print(out["label"].value_counts())
    print("提示：先用该文件快速实验，再人工抽样修正后正式训练。")


if __name__ == "__main__":
    main()

