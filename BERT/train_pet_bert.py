"""
【作用】基于 bert-base-chinese 微调文本分类（宠物意图/情感等），完成训练与验证指标输出。
【效果】在指定输出目录生成模型权重、tokenizer 与 label_map.json，供 predict_pet_bert 与下游流水线加载。
"""

from __future__ import annotations

import argparse
import json
import os
import random
from dataclasses import dataclass

import pandas as pd
import torch
from sklearn.metrics import accuracy_score, classification_report
from sklearn.model_selection import train_test_split
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler
from transformers import BertForSequenceClassification, BertTokenizer

MODEL_NAME = "bert-base-chinese"


def seed_everything(seed: int) -> None:
    random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


@dataclass
class Sample:
    text: str
    label_id: int


class IntentDataset(Dataset):
    def __init__(
        self,
        samples: list[Sample],
        tokenizer: BertTokenizer,
        max_length: int,
    ) -> None:
        self.samples = samples
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int):
        item = self.samples[idx]
        encoded = self.tokenizer(
            item.text,
            truncation=True,
            padding="max_length",
            max_length=self.max_length,
            return_tensors="pt",
        )
        return {
            "input_ids": encoded["input_ids"].squeeze(0),
            "attention_mask": encoded["attention_mask"].squeeze(0),
            "labels": torch.tensor(item.label_id, dtype=torch.long),
        }


def build_label_map(labels: list[str]) -> tuple[dict[str, int], dict[int, str]]:
    uniq = sorted(set(labels))
    label2id = {name: i for i, name in enumerate(uniq)}
    id2label = {i: name for name, i in label2id.items()}
    return label2id, id2label


def evaluate(
    model: BertForSequenceClassification,
    loader: DataLoader,
    device: torch.device,
    *,
    use_amp: bool,
) -> tuple[float, list[int], list[int]]:
    model.eval()
    all_preds: list[int] = []
    all_true: list[int] = []
    with torch.no_grad():
        for batch in loader:
            input_ids = batch["input_ids"].to(device, non_blocking=True)
            attention_mask = batch["attention_mask"].to(device, non_blocking=True)
            labels = batch["labels"].to(device, non_blocking=True)
            with torch.autocast(device_type="cuda", enabled=use_amp):
                outputs = model(input_ids=input_ids, attention_mask=attention_mask)
            preds = torch.argmax(outputs.logits, dim=1)
            all_preds.extend(preds.cpu().tolist())
            all_true.extend(labels.cpu().tolist())
    acc = accuracy_score(all_true, all_preds)
    return acc, all_true, all_preds


def main() -> None:
    parser = argparse.ArgumentParser(description="宠物电商 BERT 意图分类训练")
    parser.add_argument("--data", type=str, default="pet_intent_data.csv")
    parser.add_argument("--output_dir", type=str, default="pet_bert_model")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch_size", type=int, default=8)
    parser.add_argument("--max_length", type=int, default=64)
    parser.add_argument("--lr", type=float, default=2e-5)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--device",
        type=str,
        default="auto",
        choices=["auto", "cuda", "cpu"],
        help="训练设备：auto 自动选择，cuda 强制 GPU，cpu 强制 CPU",
    )
    parser.add_argument("--num_workers", type=int, default=2, help="DataLoader 线程数")
    parser.add_argument("--no_amp", action="store_true", help="关闭混合精度（默认 GPU 开启 AMP）")
    args = parser.parse_args()

    seed_everything(args.seed)
    if args.device == "cpu":
        device = torch.device("cpu")
    elif args.device == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("你指定了 --device cuda，但当前 PyTorch 未检测到可用 CUDA。")
        device = torch.device("cuda")
    else:
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    use_amp = (device.type == "cuda") and (not args.no_amp)
    print(f"使用设备: {device} | AMP: {use_amp}")
    if device.type == "cuda":
        print(f"GPU: {torch.cuda.get_device_name(0)}")

    df = pd.read_csv(args.data)
    if "text" not in df.columns or "label" not in df.columns:
        raise ValueError("数据文件必须包含 text,label 两列")
    df = df.dropna(subset=["text", "label"]).copy()
    df["text"] = df["text"].astype(str)
    df["label"] = df["label"].astype(str)

    label2id, id2label = build_label_map(df["label"].tolist())
    df["label_id"] = df["label"].map(label2id)
    print(f"标签映射: {label2id}")

    class_count = len(label2id)
    min_ratio = class_count / max(len(df), 1)
    test_size = max(0.2, min_ratio)
    if test_size >= 0.5:
        test_size = 0.4
    use_stratify = len(df) * test_size >= class_count
    train_df, val_df = train_test_split(
        df,
        test_size=test_size,
        random_state=args.seed,
        stratify=df["label_id"] if use_stratify else None,
    )

    try:
        tokenizer = BertTokenizer.from_pretrained(MODEL_NAME, local_files_only=True)
        model = BertForSequenceClassification.from_pretrained(
            MODEL_NAME,
            num_labels=len(label2id),
            local_files_only=True,
        ).to(device)
    except Exception:
        print("本地未找到 bert-base-chinese 缓存，尝试联网下载...")
        tokenizer = BertTokenizer.from_pretrained(MODEL_NAME)
        model = BertForSequenceClassification.from_pretrained(
            MODEL_NAME,
            num_labels=len(label2id),
        ).to(device)

    train_samples = [Sample(text=r.text, label_id=int(r.label_id)) for r in train_df.itertuples()]
    val_samples = [Sample(text=r.text, label_id=int(r.label_id)) for r in val_df.itertuples()]

    train_set = IntentDataset(train_samples, tokenizer, args.max_length)
    val_set = IntentDataset(val_samples, tokenizer, args.max_length)
    # 使用加权采样缓解类别不平衡
    train_label_counts = train_df["label_id"].value_counts().to_dict()
    sample_weights = [
        1.0 / max(float(train_label_counts.get(int(r.label_id), 1)), 1.0)
        for r in train_df.itertuples()
    ]
    sampler = WeightedRandomSampler(
        weights=torch.DoubleTensor(sample_weights),
        num_samples=len(sample_weights),
        replacement=True,
    )
    pin_memory = device.type == "cuda"
    persistent_workers = args.num_workers > 0
    train_loader = DataLoader(
        train_set,
        batch_size=args.batch_size,
        sampler=sampler,
        num_workers=args.num_workers,
        pin_memory=pin_memory,
        persistent_workers=persistent_workers,
    )
    val_loader = DataLoader(
        val_set,
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=args.num_workers,
        pin_memory=pin_memory,
        persistent_workers=persistent_workers,
    )

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)
    scaler = torch.amp.GradScaler("cuda", enabled=use_amp)
    # 类别权重交叉熵，进一步提升长尾类学习能力
    class_weights = []
    total = float(len(train_df))
    n_cls = float(len(label2id))
    for i in range(len(label2id)):
        cnt = float(train_label_counts.get(i, 1))
        class_weights.append(total / (n_cls * cnt))
    loss_fn = torch.nn.CrossEntropyLoss(weight=torch.tensor(class_weights, dtype=torch.float, device=device))

    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        for batch in train_loader:
            optimizer.zero_grad()
            input_ids = batch["input_ids"].to(device, non_blocking=True)
            attention_mask = batch["attention_mask"].to(device, non_blocking=True)
            labels = batch["labels"].to(device, non_blocking=True)
            with torch.autocast(device_type="cuda", enabled=use_amp):
                outputs = model(input_ids=input_ids, attention_mask=attention_mask)
                loss = loss_fn(outputs.logits, labels)
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
            total_loss += float(loss.item())

        avg_loss = total_loss / max(len(train_loader), 1)
        val_acc, y_true, y_pred = evaluate(model, val_loader, device, use_amp=use_amp)
        print(f"Epoch {epoch}/{args.epochs} | train_loss={avg_loss:.4f} | val_acc={val_acc:.4f}")
        print(classification_report(y_true, y_pred, digits=4, zero_division=0))

    os.makedirs(args.output_dir, exist_ok=True)
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    with open(os.path.join(args.output_dir, "label_map.json"), "w", encoding="utf-8") as f:
        json.dump({"label2id": label2id, "id2label": {str(k): v for k, v in id2label.items()}}, f, ensure_ascii=False, indent=2)

    print(f"训练完成，模型已保存到: {args.output_dir}")


if __name__ == "__main__":
    main()
