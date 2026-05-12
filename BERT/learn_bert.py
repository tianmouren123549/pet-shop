"""
【作用】分步打印 BERT 的 tokenizer、张量形状与前向输出，帮助理解「文本如何进模型」。
【效果】仅控制台教学输出；不参与训练、不产出模型文件，适合入门对照论文阅读。

BERT 入门练习脚本（bert-base-chinese）

建议顺序：先通读每个 step 的打印结果，再对照注释改句子、改 max_length 做实验。
运行：python learn_bert.py           # 跑完全部步骤
      python learn_bert.py --step 2   # 只跑第 2 步
"""
from __future__ import annotations

import argparse

import torch
from transformers import BertModel, BertTokenizer

MODEL_NAME = "bert-base-chinese"


def load():
    tokenizer = BertTokenizer.from_pretrained(MODEL_NAME)
    model = BertModel.from_pretrained(MODEL_NAME)
    model.eval()
    return tokenizer, model


def step1_load(tokenizer: BertTokenizer, model: BertModel):
    """Step1：加载与基本配置（和 main.py 类似，巩固概念）"""
    print("\n=== Step1：加载模型 ===")
    print("词表大小 vocab_size:", tokenizer.vocab_size)
    print("隐藏维度 hidden_size:", model.config.hidden_size)
    print("最大位置编码 max_position_embeddings:", model.config.max_position_embeddings)
    print("说明：hidden_size 就是每个 token 输出向量的长度（本模型为 768）。")


def step2_tokenize(tokenizer: BertTokenizer):
    """Step2：分词 —— 文本如何变成模型输入"""
    print("\n=== Step2：Tokenizer 在做什么 ===")
    text = "蓝牙耳机音质清晰，续航不错。"
    encoded = tokenizer(
        text,
        return_tensors="pt",
        padding=True,
        truncation=True,
        max_length=32,
    )
    print("原始句子:", text)
    print("input_ids 形状:", encoded["input_ids"].shape)
    print("input_ids:", encoded["input_ids"][0].tolist())
    print("attention_mask:", encoded["attention_mask"][0].tolist())
    cls_id = tokenizer.cls_token_id
    sep_id = tokenizer.sep_token_id
    pad_id = tokenizer.pad_token_id
    print(f"[CLS] id={cls_id}, [SEP] id={sep_id},  id={pad_id}")
    print("说明：首尾一般是 [CLS] … [SEP]；padding 位置 attention_mask 为 0，模型应忽略。")


def step3_forward(tokenizer: BertTokenizer, model: BertModel):
    """Step3：前向传播 —— 得到每个位置的向量"""
    print("\n=== Step3：模型前向与张量形状 ===")
    text = "明天发货吗？"
    inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=32)
    with torch.no_grad():
        out = model(**inputs)
    h = out.last_hidden_state
    print("last_hidden_state 形状 [batch, seq_len, hidden]:", tuple(h.shape))
    print("说明：每个 token 对应一个 768 维向量；下游任务常从中取句向量或做序列标注。")


def step4_sentence_vector(tokenizer: BertTokenizer, model: BertModel):
    """Step4：从 token 向量得到「句向量」的两种常见做法"""
    print("\n=== Step4：句向量 —— CLS 与 mean pooling ===")
    text = "这款商品性价比很高。"
    inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=64)
    with torch.no_grad():
        out = model(**inputs)
    h = out.last_hidden_state
    vec_cls = h[:, 0, :]
    mask = inputs["attention_mask"].unsqueeze(-1).expand(h.size())
    summed = (h * mask).sum(dim=1)
    denom = mask.sum(dim=1).clamp(min=1e-9)
    vec_mean = summed / denom
    print("CLS 向量形状:", tuple(vec_cls.shape))
    print("mean pooling 向量形状:", tuple(vec_mean.shape))
    print("CLS 前 5 维:", vec_cls[0, :5].tolist())
    print("说明：毕设里可把 vec 存盘或与表格特征拼接；也可先 PCA 再喂 XGBoost。")


def step5_similarity(tokenizer: BertTokenizer, model: BertModel):
    """Step5：两句语义有多像？—— 余弦相似度（练习用）"""
    print("\n=== Step5：两句余弦相似度 ===")
    a = "这款耳机低音很好。"
    b = "这个耳塞低频表现不错。"
    c = "今天天气真好。"
    inputs = tokenizer(
        [a, b, c],
        return_tensors="pt",
        padding=True,
        truncation=True,
        max_length=64,
    )
    with torch.no_grad():
        h = model(**inputs).last_hidden_state
    mask = inputs["attention_mask"].unsqueeze(-1).expand(h.size())
    summed = (h * mask).sum(dim=1)
    denom = mask.sum(dim=1).clamp(min=1e-9)
    emb = summed / denom

    def cosine(x, y):
        return (x * y).sum() / (x.norm() * y.norm() + 1e-9)

    e0, e1, e2 = emb[0], emb[1], emb[2]
    print("句子 A:", a)
    print("句子 B:", b)
    print("句子 C:", c)
    print("cosine(A, B) ≈", float(cosine(e0, e1)))
    print("cosine(A, C) ≈", float(cosine(e0, e2)))
    print("说明：语义相近的句子，余弦相似度通常更高（非绝对，仅作直观练习）。")


def main():
    parser = argparse.ArgumentParser(description="BERT 入门分步练习")
    parser.add_argument(
        "--step",
        type=str,
        default="all",
        help="1-5 或 all（默认跑全部）",
    )
    args = parser.parse_args()

    tokenizer, model = load()

    steps = {
        "1": lambda: step1_load(tokenizer, model),
        "2": lambda: step2_tokenize(tokenizer),
        "3": lambda: step3_forward(tokenizer, model),
        "4": lambda: step4_sentence_vector(tokenizer, model),
        "5": lambda: step5_similarity(tokenizer, model),
    }

    if args.step == "all":
        step1_load(tokenizer, model)
        step2_tokenize(tokenizer)
        step3_forward(tokenizer, model)
        step4_sentence_vector(tokenizer, model)
        step5_similarity(tokenizer, model)
        print("\n全部步骤跑完。建议：改各 step 里的中文句子，观察 input_ids 与相似度变化。")
        return

    if args.step not in steps:
        print("未知 --step，请用 1-5 或 all")
        return

    steps[args.step]()


if __name__ == "__main__":
    main()
