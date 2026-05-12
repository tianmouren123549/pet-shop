"""
【作用】加载 train_pet_bert 产出的微调 BERT 分类模型，对输入文本做批量或单条推理。
【效果】在终端或输出文件中给出预测标签（及可选概率），用于离线验证或小规模打标。
"""

from __future__ import annotations

import argparse
import json
import os

import torch
from transformers import BertForSequenceClassification, BertTokenizer

POS_HINTS = ["好评", "很好", "非常好", "满意", "推荐", "回购", "爱吃", "认准", "下次还买"]
NEG_HINTS = ["差评", "不行", "吐", "拉稀", "软便", "难吃", "有问题", "发霉", "异味", "退款", "退货"]


def load_model(model_dir: str):
    tokenizer = BertTokenizer.from_pretrained(model_dir, local_files_only=True)
    model = BertForSequenceClassification.from_pretrained(model_dir, local_files_only=True)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)
    model.eval()

    map_path = os.path.join(model_dir, "label_map.json")
    with open(map_path, "r", encoding="utf-8") as f:
        mapping = json.load(f)
    id2label = {int(k): v for k, v in mapping["id2label"].items()}
    return tokenizer, model, id2label, device


@torch.no_grad()
def predict_one(
    text: str,
    tokenizer: BertTokenizer,
    model: BertForSequenceClassification,
    id2label: dict[int, str],
    device: torch.device,
    max_length: int = 64,
) -> tuple[str, float]:
    encoded = tokenizer(
        text,
        truncation=True,
        padding=True,
        max_length=max_length,
        return_tensors="pt",
    )
    encoded = {k: v.to(device) for k, v in encoded.items()}
    out = model(**encoded)
    probs = torch.softmax(out.logits, dim=-1).squeeze(0)
    pred_id = int(torch.argmax(probs).item())
    return id2label[pred_id], float(probs[pred_id].item())


def sentiment_rule_adjust(text: str, label: str, score: float, labels: set[str]) -> tuple[str, float, str]:
    # 仅对情感三分类模型启用规则修正，避免影响意图模型
    if not {"好评", "中评", "差评"}.issubset(labels):
        return label, score, ""

    s = str(text)
    has_pos = any(k in s for k in POS_HINTS)
    has_neg = any(k in s for k in NEG_HINTS)
    if has_pos and not has_neg and label != "好评":
        return "好评", score, "规则修正:正向关键词"
    if has_neg and not has_pos and label != "差评":
        return "差评", score, "规则修正:负向关键词"
    return label, score, ""


def main() -> None:
    parser = argparse.ArgumentParser(description="宠物电商 BERT 意图预测")
    parser.add_argument("--model_dir", type=str, default="pet_bert_model")
    parser.add_argument("--text", type=str, default=None, help="单条文本预测")
    parser.add_argument("--chat", action="store_true", help="连续提问模式（输入 q 退出）")
    parser.add_argument("--max_length", type=int, default=64, help="分词最大长度")
    args = parser.parse_args()

    tokenizer, model, id2label, device = load_model(args.model_dir)
    label_set = set(id2label.values())
    print(f"加载模型完成，设备: {device}")

    if args.text and not args.chat:
        label, score = predict_one(
            args.text,
            tokenizer,
            model,
            id2label,
            device,
            max_length=args.max_length,
        )
        label, score, reason = sentiment_rule_adjust(args.text, label, score, label_set)
        print(f"文本: {args.text}")
        if reason:
            print(f"预测: {label} (置信度: {score:.4f}, {reason})")
        else:
            print(f"预测: {label} (置信度: {score:.4f})")
        return

    print("进入连续提问模式，输入 q 退出。")
    while True:
        line = input("请输入评论: ").strip()
        if line.lower() == "q":
            print("结束。")
            break
        if not line:
            continue
        label, score = predict_one(line, tokenizer, model, id2label, device, max_length=args.max_length)
        label, score, reason = sentiment_rule_adjust(line, label, score, label_set)
        if reason:
            print(f"预测结果 => {label} (置信度: {score:.4f}, {reason})")
        else:
            print(f"预测结果 => {label} (置信度: {score:.4f})")


if __name__ == "__main__":
    main()
