"""
【作用】用「未微调」的 bert-base-chinese 将输入句与预设示例句做向量余弦相似度，粗分意图/情绪倾向。
【效果】交互式终端问答，输出最相近的示例类别；非分类头训练结果，仅作语义空间演示。

用 bert-base-chinese 做「你说的话像哪一类」—— 教学向小工具

原理：把你的话和若干「示例句」都编成向量，算余弦相似度，谁最接近就认为语义上最像哪一类。
这不是聊天机器人，也没有专门训练「理解你」；只是预训练模型的语义空间在起作用。

运行：python analyze_say.py
      输入中文后回车；空行或 q 退出。

可自行修改下面 INTENT_EXAMPLES / MOOD_POS / MOOD_NEG 里的句子，观察结果变化。
"""
from __future__ import annotations

import torch
from transformers import BertModel, BertTokenizer

MODEL_NAME = "bert-base-chinese"

# 每个「意图」下面多写几句同义说法，匹配会更稳
INTENT_EXAMPLES: dict[str, list[str]] = {
    "问价格或优惠": [
        "这个多少钱",
        "有没有优惠券",
        "能便宜一点吗",
        "包邮吗运费怎么算",
    ],
    "问物流或发货": [
        "什么时候发货",
        "几天能到",
        "发什么快递",
        "物流怎么查",
    ],
    "售后或质量问题": [
        "坏了怎么修",
        "我要退货",
        "和描述不一样",
        "质量太差了",
    ],
    "单纯好评或推荐": [
        "很好用推荐购买",
        "超出预期很满意",
        "性价比很高",
    ],
}

# 粗情绪：只和两组「样例句」比远近，娱乐/学习用，不能当专业情感分析
MOOD_POS = ["我很满意", "挺不错的", "心情很好", "这次体验很棒"]
MOOD_NEG = ["我很失望", "太差了", "很生气", "再也不买了"]


def load_model():
    tokenizer = BertTokenizer.from_pretrained(MODEL_NAME)
    model = BertModel.from_pretrained(MODEL_NAME)
    model.eval()
    return tokenizer, model


@torch.no_grad()
def sentence_embedding(
    text: str,
    tokenizer: BertTokenizer,
    model: BertModel,
    max_length: int = 64,
) -> torch.Tensor:
    inputs = tokenizer(
        text,
        return_tensors="pt",
        padding=True,
        truncation=True,
        max_length=max_length,
    )
    h = model(**inputs).last_hidden_state
    mask = inputs["attention_mask"].unsqueeze(-1).expand(h.size())
    summed = (h * mask).sum(dim=1)
    denom = mask.sum(dim=1).clamp(min=1e-9)
    return (summed / denom).squeeze(0)


def cosine(a: torch.Tensor, b: torch.Tensor) -> float:
    return float((a * b).sum() / (a.norm() * b.norm() + 1e-9))


def precompute_intent_table(
    tokenizer: BertTokenizer,
    model: BertModel,
    examples: dict[str, list[str]],
) -> list[tuple[str, str, torch.Tensor]]:
    """每条：(意图名, 示例句, 向量)，启动时算一次即可"""
    rows: list[tuple[str, str, torch.Tensor]] = []
    for name, sents in examples.items():
        for s in sents:
            rows.append((name, s, sentence_embedding(s, tokenizer, model)))
    return rows


def best_intent(
    user_emb: torch.Tensor,
    table: list[tuple[str, str, torch.Tensor]],
) -> tuple[str, float, str]:
    best_name = ""
    best_score = -1.0
    best_hit = ""
    for name, sent, e in table:
        s_score = cosine(user_emb, e)
        if s_score > best_score:
            best_score = s_score
            best_name = name
            best_hit = sent
    return best_name, best_score, best_hit


def precompute_list(tokenizer: BertTokenizer, model: BertModel, sents: list[str]) -> list[torch.Tensor]:
    return [sentence_embedding(s, tokenizer, model) for s in sents]


def rough_mood(
    user_emb: torch.Tensor,
    pos_embs: list[torch.Tensor],
    neg_embs: list[torch.Tensor],
) -> tuple[str, float, float]:
    p = max(cosine(user_emb, e) for e in pos_embs)
    n = max(cosine(user_emb, e) for e in neg_embs)
    if p >= n:
        return "更接近正面样例", p, n
    return "更接近负面样例", p, n


def main() -> None:
    print("正在加载模型（首次可能稍慢）…")
    tokenizer, model = load_model()
    print("正在编码内置示例句（只需一次）…")
    intent_table = precompute_intent_table(tokenizer, model, INTENT_EXAMPLES)
    pos_embs = precompute_list(tokenizer, model, MOOD_POS)
    neg_embs = precompute_list(tokenizer, model, MOOD_NEG)
    print("加载完成。输入你想「分析」的中文句子；空行或 q 退出。\n")

    while True:
        try:
            line = input("你说：").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n再见。")
            break
        if not line or line.lower() == "q":
            print("再见。")
            break

        user_emb = sentence_embedding(line, tokenizer, model)
        name, score, hit = best_intent(user_emb, intent_table)
        mood_label, pos_sim, neg_sim = rough_mood(user_emb, pos_embs, neg_embs)

        print(f"  → 语义上最像：【{name}】（与示例句相似度约 {score:.3f}）")
        print(f"     当前命中的参考句：「{hit}」")
        print(f"  → 粗情绪（仅对比固定样例，非专业分类）：{mood_label}")
        print(f"     与正面样例最高相似 {pos_sim:.3f}，与负面样例最高相似 {neg_sim:.3f}")
        print()


if __name__ == "__main__":
    main()
