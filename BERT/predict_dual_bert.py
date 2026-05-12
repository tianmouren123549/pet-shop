"""
【作用】同时加载意图与情感等微调 BERT，并结合关键词规则做宠物评论/咨询文本的综合打标。
【效果】输出结构化标签（及置信相关信息），供 batch_backfill_review_bert_mysql 写库或本地联调使用。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

import torch
from transformers import BertForSequenceClassification, BertTokenizer

POS_HINTS = ["好评", "很好", "非常好", "满意", "推荐", "回购", "爱吃", "认准", "下次还买"]
NEG_HINTS = ["差评", "不行", "吐", "拉稀", "软便", "难吃", "有问题", "发霉", "霉味", "异味", "退款", "退货"]
# 这些症状词常出现在中性提问里（如「换粮会软便吗」），不应单独触发「规则差评」
NEG_SYMPTOM_AMBIGUOUS = frozenset({"吐", "拉稀", "软便"})
YOUNG_PET_HINTS = ["幼猫", "幼犬", "奶猫", "奶狗", "离乳", "断奶"]
FEEDING_QUESTION_HINTS = ["能吃吗", "可以吃吗", "适合吗", "怎么喂", "喂多少", "吃多少", "能不能吃"]
GI_SYMPTOM_HINTS = ["拉稀", "软便", "腹泻", "呕吐", "吐了", "便血", "肠胃不适", "不适应"]
FEEDING_CONTEXT_HINTS = ["吃完", "吃了", "喂了", "不太适合", "不适合", "换粮", "这款粮", "狗粮", "猫粮"]

# 意图：成分/营养参数咨询（易与「价格优惠」混淆）
PRODUCT_NUTRITION_HINTS = ["蛋白质", "脂肪", "配料", "成分表", "营养成分", "粗纤维", "碳水", "添加剂", "过敏原"]
# 意图：配送时效（易与「商品咨询」混淆）
LOGISTICS_TIME_HINTS = [
    "多久能到",
    "几天到",
    "几天送达",
    "同城",
    "配送",
    "快递",
    "顺丰",
    "到货时间",
    "发货",
    "驿站",
    "次日达",
    "短信",
    "揽收",
    "派送",
    "天才到",
]

# 情感：命中正向词但仍不应强行「好评」的体验/物流抱怨（不在 NEG_HINTS 内）
POS_RULE_BREAKERS = [
    "太慢",
    "太慢了",
    "一周才",
    "好几天才",
    "迟迟",
    "延误",
    "物流慢",
    "慢死了",
    "袋子破了",
    "破袋",
    "包装破",
    "漏气",
    "潮乎乎",
    "受潮",
    "霉味",
]


def load_model(model_dir: str):
    tokenizer = BertTokenizer.from_pretrained(model_dir, local_files_only=True)
    model = BertForSequenceClassification.from_pretrained(model_dir, local_files_only=True)
    map_path = os.path.join(model_dir, "label_map.json")
    with open(map_path, "r", encoding="utf-8") as f:
        mapping = json.load(f)
    id2label = {int(k): v for k, v in mapping["id2label"].items()}
    return tokenizer, model, id2label


@torch.no_grad()
def predict_one_with_bias(
    text: str,
    tokenizer: BertTokenizer,
    model: BertForSequenceClassification,
    id2label: dict[int, str],
    device: torch.device,
    max_length: int = 64,
    logits_bias: dict[str, float] | None = None,
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
    logits = out.logits.squeeze(0)

    if logits_bias:
        for idx, label in id2label.items():
            b = logits_bias.get(label, 0.0)
            if b != 0.0:
                logits[idx] = logits[idx] + b

    probs = torch.softmax(logits, dim=-1)
    pred_id = int(torch.argmax(probs).item())
    return id2label[pred_id], float(probs[pred_id].item())


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


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="统一预测：同时输出意图分类与情感分类")
    parser.add_argument(
        "--intent_model_dir",
        type=str,
        default="训练成果/bert_intent_cat_dog_product_gpu_v2",
        help="意图模型目录",
    )
    parser.add_argument(
        "--sentiment_model_dir",
        type=str,
        default="训练成果/bert_sentiment_cat_dog_product_gpu_v2_e2",
        help="情感模型目录",
    )
    parser.add_argument(
        "--text",
        type=str,
        default=None,
        help="待识别文字；可一段或多行，每行一句即多条（模型只加载一次）。空行与 # 开头行忽略",
    )
    parser.add_argument(
        "--chat",
        action="store_true",
        help="无 --text 时默认进入粘贴模式：多行粘贴后以一行 END 提交",
    )
    parser.add_argument("--max_length", type=int, default=64, help="分词最大长度")
    parser.add_argument("--young_pet_boost", type=float, default=0.5, help="命中幼猫/幼犬关键词时，对喂养咨询的logits加权")
    parser.add_argument(
        "--no-sentiment-rules",
        action="store_true",
        help="不做情感规则后处理，仅输出 BERT 原始三分类",
    )
    parser.add_argument("--no-intent-rules", action="store_true", help="不做意图规则后处理")
    parser.add_argument(
        "--batch-file",
        type=str,
        default=None,
        metavar="PATH",
        help="（可选）从 UTF-8 文件读入文字；一行一句。路径填 - 则读标准输入",
    )
    parser.add_argument(
        "--batch-stdin",
        action="store_true",
        help="（可选）从标准输入读入多行文字，语义同 --text 多行",
    )
    parser.add_argument(
        "--batch-json",
        action="store_true",
        help="批量输出为 JSON 数组到 stdout（便于脚本解析）",
    )
    parser.add_argument("--batch-size", type=int, default=16, help="GPU 批量前向时每批条数，CPU 也可减小显存占用")
    return parser


def _neutral_after_sales_question(text: str) -> bool:
    """退换货/物流费用类提问，常见为中性咨询，不宜仅靠泛化负向词拉成差评。"""
    s = str(text).strip()
    if not s:
        return False
    return any(
        p in s
        for p in (
            "还能退吗",
            "给退吗",
            "能退吗",
            "怎么退",
            "退换",
            "运费谁出",
            "谁出运费",
            "拒签",
            "补发吗",
            "给补发",
            "拆封",
        )
    )


def _after_sales_neutral_tone(text: str) -> bool:
    """售后/运费/退换语境下的中性咨询语气（含无句末「吗」的「运费谁出」类）。"""
    s = str(text).strip()
    if not s or not _neutral_after_sales_question(s):
        return False
    if _looks_like_review_question(s):
        return True
    return any(
        p in s
        for p in (
            "谁出",
            "谁付",
            "谁承担",
            "谁来承担",
            "谁来管",
        )
    )


def _looks_like_review_question(text: str) -> bool:
    """判断是否为咨询/提问语气，避免「会软便吗」类句被症状词误判为差评。"""
    s = str(text).strip()
    if not s:
        return False
    if s.endswith(("吗", "么", "嘛", "呢")) or s.endswith("?"):
        return True
    if "？" in s:
        return True
    return any(
        p in s
        for p in (
            "会不会",
            "有没有",
            "是不是",
            "要不要",
            "能否",
            "是否可以",
            "怎么办",
            "为什么",
            "怎么会",
            "哪个",
            "哪种",
            "多久",
            "多少",
            "谁出",
            "谁来",
            "谁负责",
            "咋办",
        )
    )


def _mask_for_neg_keyword_scan(s: str) -> str:
    """遮蔽否定语境下的词，避免「不是难吃」仍命中「难吃」；兼容夹空白/全角标点。"""
    masked = str(s)
    masked = re.sub(r"不\s*是\s*难\s*吃", lambda m: "\uff03" * len(m.group(0)), masked)
    masked = re.sub(r"不\s*算\s*难\s*吃", lambda m: "\uff03" * len(m.group(0)), masked)
    for phrase in ("没那么难吃", "不难吃"):
        while phrase in masked:
            masked = masked.replace(phrase, "\uff03" * len(phrase), 1)
    return masked


def _mask_for_pos_keyword_scan(s: str) -> str:
    """遮蔽「不爱吃」等，避免子串误命中 POS_HINTS 里的「爱吃」。"""
    masked = str(s)
    for phrase in ("不爱吃", "不太爱吃", "不怎么爱吃", "不肯吃", "一口不吃"):
        while phrase in masked:
            masked = masked.replace(phrase, "\uff03" * len(phrase), 1)
    return masked


def sentiment_rule_adjust(
    text: str,
    label: str,
    score: float,
    labels: set[str],
    *,
    skip_rules: bool = False,
) -> tuple[str, float, str]:
    # 仅对情感三分类模型启用规则修正
    if skip_rules:
        return label, score, ""
    if not {"好评", "中评", "差评"}.issubset(labels):
        return label, score, ""
    s = str(text)
    masked_pos = _mask_for_pos_keyword_scan(s)
    has_pos = any(k in masked_pos for k in POS_HINTS)
    masked_neg = _mask_for_neg_keyword_scan(s)
    hits = [k for k in NEG_HINTS if k in masked_neg]
    if not hits:
        has_neg = False
    elif _looks_like_review_question(s):
        strict = [k for k in hits if k not in NEG_SYMPTOM_AMBIGUOUS]
        has_neg = len(strict) > 0
    else:
        has_neg = True
    breaks_pos_pull = any(k in s for k in POS_RULE_BREAKERS)
    if has_pos and not has_neg and not breaks_pos_pull and label != "好评":
        return "好评", score, "规则修正:正向关键词"
    if (
        has_neg
        and not has_pos
        and label != "差评"
        and not _after_sales_neutral_tone(s)
    ):
        return "差评", score, "规则修正:负向关键词"
    if has_pos and not has_neg and breaks_pos_pull and label == "好评":
        return "中评", score, "规则修正:正向词但含物流/体验抱怨"
    # 售后/运费/补发类提问，BERT 常打成差评；不因零星命中词而保留差评（句内未出现「差评」字样）
    if (
        label == "差评"
        and _after_sales_neutral_tone(s)
        and "差评" not in s
    ):
        return "中评", score, "规则修正:售后政策类中性提问"
    return label, score, ""


def intent_rule_adjust(
    text: str,
    label: str,
    score: float,
    labels: set[str],
    *,
    skip_rules: bool = False,
) -> tuple[str, float, str]:
    # 对“幼宠+喂养问法”做强规则，减少商品咨询/价格优惠误判
    if skip_rules:
        return label, score, ""
    if "喂养咨询" not in labels:
        return label, score, ""
    s = str(text)
    has_gi_symptom = any(k in s for k in GI_SYMPTOM_HINTS)
    has_feeding_context = any(k in s for k in FEEDING_CONTEXT_HINTS)
    if has_gi_symptom and has_feeding_context and label != "喂养咨询":
        return "喂养咨询", score, "规则修正:肠胃不适喂养场景"
    has_young_pet = any(k in s for k in YOUNG_PET_HINTS)
    has_feeding_question = any(k in s for k in FEEDING_QUESTION_HINTS)
    if has_young_pet and has_feeding_question and label != "喂养咨询":
        return "喂养咨询", score, "规则修正:幼宠喂养问法"
    if "喂养咨询" in labels and "玻璃胃" in s and label != "喂养咨询":
        return "喂养咨询", score, "规则修正:敏感肠胃咨询"

    if "商品咨询" in labels:
        if "批次" in s or ("颜色" in s and any(k in s for k in ("不一样", "不同", "差别", "不一致"))):
            if label != "商品咨询":
                return "商品咨询", score, "规则修正:批次外观咨询"
        if any(k in s for k in PRODUCT_NUTRITION_HINTS) and any(
            q in s for q in ("多少", "大概", "配比", "配方", "含量")
        ):
            if label != "商品咨询":
                return "商品咨询", score, "规则修正:成分营养咨询"
        looks_compare = False
        if any(k in s for k in ("对比", "相比", "比起来")):
            looks_compare = True
        elif "怎么样" in s:
            ih = s.find("和")
            ib = s.find("比")
            if ih >= 0 and ib > ih:
                looks_compare = True
        elif any(k in s for k in ("哪款", "哪个好", "选哪个", "哪个更适合")):
            looks_compare = True
        if looks_compare and label != "商品咨询":
            return "商品咨询", score, "规则修正:竞品对比咨询"

    if "售后退换" in labels:
        if ("运费" in s and "谁出" in s) or "拒签" in s:
            if label != "售后退换":
                return "售后退换", score, "规则修正:运费拒签咨询"
        if "补发" in s or (
            "破" in s and any(k in s for k in ("口", "袋", "包装", "裂", "损"))
        ):
            if label != "售后退换":
                return "售后退换", score, "规则修正:破损补发咨询"

    if "物流发货" in labels:
        if (
            any(k in s for k in LOGISTICS_TIME_HINTS)
            or ("多久" in s and "到" in s)
            or "就是慢" in s
            or ("慢" in s and any(k in s for k in ("物流", "快递", "配送", "发货")))
        ):
            if label != "物流发货":
                return "物流发货", score, "规则修正:配送时效咨询"

    if "商品反馈" in labels:
        price_hit = any(
            k in s for k in ("双十一", "满减", "叠加", "优惠", "折扣", "券", "促销", "降价", "会员价")
        )
        if not price_hit:
            if ("很满意" in s and "好评" in s) or ("下次还买" in s and "好评" in s):
                if label != "商品反馈":
                    return "商品反馈", score, "规则修正:满意度反馈"
        if "差评" in s and any(k in s for k in ("不吃", "难吃", "一口不吃", "完全不吃")):
            if label != "商品反馈":
                return "商品反馈", score, "规则修正:差评体验陈述"

    if "售后退换" in labels and label == "售后退换":
        if ("运费" in s and "谁出" in s) or "拒签" in s:
            return label, score, "规则修正:运费拒签咨询"
        if "补发" in s or ("破" in s and any(k in s for k in ("口", "袋", "包装", "裂", "损"))):
            return label, score, "规则修正:破损补发咨询"

    return label, score, ""


def predict_dual_labels(
    text: str,
    intent_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    sentiment_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    device: torch.device,
    max_length: int = 64,
    young_pet_boost: float = 0.5,
    *,
    skip_intent_rules: bool = False,
    skip_sentiment_rules: bool = False,
) -> dict[str, str | float]:
    """
    单条评论的意图+情感预测（与 CLI 逻辑一致），供批量回写 MySQL review 表等调用。
    返回 intent_label / sentiment_label / 置信度及可选的规则修正说明。
    """
    intent_tokenizer, intent_model, intent_id2label = intent_pack
    sentiment_tokenizer, sentiment_model, sentiment_id2label = sentiment_pack

    intent_bias: dict[str, float] | None = None
    if any(k in str(text) for k in YOUNG_PET_HINTS):
        intent_bias = {"喂养咨询": young_pet_boost}
    intent_label, intent_score = predict_one_with_bias(
        text,
        intent_tokenizer,
        intent_model,
        intent_id2label,
        device,
        max_length=max_length,
        logits_bias=intent_bias,
    )
    intent_label, intent_score, intent_reason = intent_rule_adjust(
        text, intent_label, intent_score, set(intent_id2label.values()), skip_rules=skip_intent_rules
    )
    sentiment_label, sentiment_score = predict_one(
        text,
        sentiment_tokenizer,
        sentiment_model,
        sentiment_id2label,
        device,
        max_length=max_length,
    )
    sentiment_label, sentiment_score, sentiment_reason = sentiment_rule_adjust(
        text,
        sentiment_label,
        sentiment_score,
        set(sentiment_id2label.values()),
        skip_rules=skip_sentiment_rules,
    )
    return {
        "intent_label": intent_label,
        "intent_score": intent_score,
        "intent_rule_note": intent_reason or "",
        "sentiment_label": sentiment_label,
        "sentiment_score": sentiment_score,
        "sentiment_rule_note": sentiment_reason or "",
    }


@torch.no_grad()
def predict_dual_labels_batch(
    texts: list[str],
    intent_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    sentiment_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    device: torch.device,
    max_length: int,
    young_pet_boost: float,
    *,
    skip_intent_rules: bool,
    skip_sentiment_rules: bool,
    batch_size: int,
) -> list[dict[str, str | float]]:
    """批量前向（意图/情感各按 batch 跑 GPU），规则仍逐条与单条推理一致。"""
    intent_tokenizer, intent_model, intent_id2label = intent_pack
    sentiment_tokenizer, sentiment_model, sentiment_id2label = sentiment_pack
    intent_labels_set = set(intent_id2label.values())
    sentiment_labels_set = set(sentiment_id2label.values())
    label2idx_i = {v: k for k, v in intent_id2label.items()}
    feed_idx = label2idx_i.get("喂养咨询")

    out: list[dict[str, str | float]] = []
    n = len(texts)
    i = 0
    bs = max(1, batch_size)
    while i < n:
        chunk = texts[i : i + bs]
        enc_i = intent_tokenizer(
            chunk,
            truncation=True,
            padding=True,
            max_length=max_length,
            return_tensors="pt",
        )
        enc_i = {k: v.to(device) for k, v in enc_i.items()}
        logits_i = intent_model(**enc_i).logits
        if feed_idx is not None:
            for row, tx in enumerate(chunk):
                if any(k in str(tx) for k in YOUNG_PET_HINTS):
                    logits_i[row, feed_idx] = logits_i[row, feed_idx] + young_pet_boost
        iprob = torch.softmax(logits_i, dim=-1)
        i_pred = torch.argmax(iprob, dim=-1)
        i_conf = iprob.gather(1, i_pred.unsqueeze(1)).squeeze(1)

        enc_s = sentiment_tokenizer(
            chunk,
            truncation=True,
            padding=True,
            max_length=max_length,
            return_tensors="pt",
        )
        enc_s = {k: v.to(device) for k, v in enc_s.items()}
        logits_s = sentiment_model(**enc_s).logits
        sprob = torch.softmax(logits_s, dim=-1)
        s_pred = torch.argmax(sprob, dim=-1)
        s_conf = sprob.gather(1, s_pred.unsqueeze(1)).squeeze(1)

        for j, t in enumerate(chunk):
            intent_label = intent_id2label[int(i_pred[j].item())]
            intent_score = float(i_conf[j].item())
            intent_label, intent_score, intent_reason = intent_rule_adjust(
                t, intent_label, intent_score, intent_labels_set, skip_rules=skip_intent_rules
            )
            sentiment_label = sentiment_id2label[int(s_pred[j].item())]
            sentiment_score = float(s_conf[j].item())
            sentiment_label, sentiment_score, sentiment_reason = sentiment_rule_adjust(
                t,
                sentiment_label,
                sentiment_score,
                sentiment_labels_set,
                skip_rules=skip_sentiment_rules,
            )
            out.append(
                {
                    "intent_label": intent_label,
                    "intent_score": intent_score,
                    "intent_rule_note": intent_reason or "",
                    "sentiment_label": sentiment_label,
                    "sentiment_score": sentiment_score,
                    "sentiment_rule_note": sentiment_reason or "",
                }
            )
        i += bs
    return out


# 误把上一轮终端输出粘进来时的典型行（跳过，避免当成「待识别句子」）
_LINE_SKIP_HEAD = re.compile(
    r"^\s*(意图|情感|文本)\s*[:：]",
    re.UNICODE,
)


def _normalize_pasted_line(raw: str) -> str | None:
    s = raw.strip()
    if not s or s.startswith("#"):
        return None
    if _LINE_SKIP_HEAD.match(s):
        return None
    if re.fullmatch(r"-+", s):
        return None
    if re.match(r"^共\s*\d+\s*条", s):
        return None
    # 列表编号「1. xxx」「1、xxx」「1) xxx」，避免与程序自己的「  1. xxx」叠成双编号
    s = re.sub(r"^\s*\d+[\.\、\)]\s*", "", s)
    s = s.strip()
    return s if s else None


def _lines_from_text_blob(blob: str) -> list[str]:
    out: list[str] = []
    for line in str(blob).splitlines():
        n = _normalize_pasted_line(line)
        if n is None:
            continue
        out.append(n)
    return out


def _load_batch_lines(path: Path) -> list[str]:
    return _lines_from_text_blob(path.read_text(encoding="utf-8"))


def _read_batch_from_stdin() -> list[str]:
    if sys.stdin is None:
        return []
    data = sys.stdin.read()
    return _lines_from_text_blob(data)


def run_batch(
    lines: list[str],
    intent_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    sentiment_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    device: torch.device,
    max_length: int,
    young_pet_boost: float,
    batch_size: int,
    *,
    skip_intent_rules: bool,
    skip_sentiment_rules: bool,
    as_json: bool,
) -> None:
    if not lines:
        print("批量列表为空，退出。")
        return
    rows = predict_dual_labels_batch(
        lines,
        intent_pack,
        sentiment_pack,
        device,
        max_length,
        young_pet_boost,
        skip_intent_rules=skip_intent_rules,
        skip_sentiment_rules=skip_sentiment_rules,
        batch_size=batch_size,
    )
    payload = []
    for text, r in zip(lines, rows):
        item = {
            "text": text,
            "intent": str(r["intent_label"]),
            "intent_score": round(float(r["intent_score"]), 4),
            "intent_note": str(r["intent_rule_note"] or ""),
            "sentiment": str(r["sentiment_label"]),
            "sentiment_score": round(float(r["sentiment_score"]), 4),
            "sentiment_note": str(r["sentiment_rule_note"] or ""),
        }
        payload.append(item)
    if as_json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    w = max(len(t) for t in lines)
    w = min(max(w, 20), 72)
    print("-" * min(96, w + 56))
    print(f"共 {len(lines)} 条 | 批量前向 batch_size={batch_size}")
    print("-" * min(96, w + 56))
    for i, item in enumerate(payload, 1):
        t = item["text"]
        show = t if len(t) <= w else t[: w - 3] + "..."
        note_i = f' [{item["intent_note"]}]' if item["intent_note"] else ""
        note_s = f' [{item["sentiment_note"]}]' if item["sentiment_note"] else ""
        print(f'{i:3}. {show}')
        print(f'     意图: {item["intent"]} ({item["intent_score"]:.4f}){note_i}')
        print(f'     情感: {item["sentiment"]} ({item["sentiment_score"]:.4f}){note_s}')
        print()
    print(f"--- 本轮共输出 {len(payload)} 条，每条仅 1 个意图 + 1 个情感 ---")


def run_once(
    text: str,
    intent_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    sentiment_pack: tuple[BertTokenizer, BertForSequenceClassification, dict[int, str]],
    device: torch.device,
    max_length: int,
    young_pet_boost: float,
    *,
    skip_intent_rules: bool = False,
    skip_sentiment_rules: bool = False,
) -> None:
    out = predict_dual_labels(
        text,
        intent_pack,
        sentiment_pack,
        device,
        max_length,
        young_pet_boost,
        skip_intent_rules=skip_intent_rules,
        skip_sentiment_rules=skip_sentiment_rules,
    )
    intent_label = str(out["intent_label"])
    intent_score = float(out["intent_score"])
    intent_reason = str(out["intent_rule_note"])
    sentiment_label = str(out["sentiment_label"])
    sentiment_score = float(out["sentiment_score"])
    reason = str(out["sentiment_rule_note"])
    print(f"文本: {text}")
    if intent_reason:
        print(f"意图 => {intent_label} (置信度: {intent_score:.4f}, {intent_reason})")
    else:
        print(f"意图 => {intent_label} (置信度: {intent_score:.4f})")
    if reason:
        print(f"情感 => {sentiment_label} (置信度: {sentiment_score:.4f}, {reason})")
    else:
        print(f"情感 => {sentiment_label} (置信度: {sentiment_score:.4f})")


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"加载设备: {device}")

    intent_dir = Path(args.intent_model_dir).resolve()
    sent_dir = Path(args.sentiment_model_dir).resolve()
    print(f"意图模型目录: {intent_dir}")
    print(f"情感模型目录: {sent_dir}")

    intent_tokenizer, intent_model, intent_id2label = load_model(str(intent_dir))
    sentiment_tokenizer, sentiment_model, sentiment_id2label = load_model(str(sent_dir))
    intent_model.to(device).eval()
    sentiment_model.to(device).eval()
    print("模型加载完成。")
    print(f"当前脚本: {Path(__file__).resolve()}")

    intent_pack = (intent_tokenizer, intent_model, intent_id2label)
    sentiment_pack = (sentiment_tokenizer, sentiment_model, sentiment_id2label)

    use_stdin = args.batch_stdin or (
        args.batch_file is not None and str(args.batch_file).strip() in ("-", "/dev/stdin")
    )
    if args.batch_stdin and args.batch_file and str(args.batch_file).strip() not in ("-", "/dev/stdin"):
        raise SystemExit("不要同时使用 --batch-stdin 与具体文件路径的 --batch-file")

    if use_stdin or (args.batch_file is not None):
        if use_stdin:
            if sys.stdin.isatty() and not args.batch_json:
                print(
                    "（stdin）请粘贴多行问句，每行一句；空行与 # 开头行忽略。"
                    " 结束输入：Windows 按 Ctrl+Z 再回车；Linux/macOS 按 Ctrl+D",
                    file=sys.stderr,
                )
            lines = _read_batch_from_stdin()
        else:
            bp = Path(args.batch_file).expanduser()
            if not bp.is_file():
                raise SystemExit(f"批量文件不存在: {bp.resolve()}")
            lines = _load_batch_lines(bp)
        run_batch(
            lines,
            intent_pack,
            sentiment_pack,
            device,
            args.max_length,
            args.young_pet_boost,
            args.batch_size,
            skip_intent_rules=args.no_intent_rules,
            skip_sentiment_rules=args.no_sentiment_rules,
            as_json=args.batch_json,
        )
        return

    if args.text and not args.chat:
        lines = _lines_from_text_blob(args.text)
        if not lines:
            print("（--text）有效行为空，退出。")
            return
        if len(lines) == 1:
            run_once(
                lines[0],
                intent_pack,
                sentiment_pack,
                device,
                args.max_length,
                args.young_pet_boost,
                skip_intent_rules=args.no_intent_rules,
                skip_sentiment_rules=args.no_sentiment_rules,
            )
        else:
            run_batch(
                lines,
                intent_pack,
                sentiment_pack,
                device,
                args.max_length,
                args.young_pet_boost,
                args.batch_size,
                skip_intent_rules=args.no_intent_rules,
                skip_sentiment_rules=args.no_sentiment_rules,
                as_json=args.batch_json,
            )
        return

    print("模型已就绪（粘贴模式）。")
    print(
        "用法：在下面整块粘贴多行文字（每行一句）；粘完后单独一行输入 END 再回车即开始识别。"
        " 仅输入 q 退出。\n"
        "注意：不要连同上一轮的「意图:/情感:」结果一起粘进来；也不要在一次 END 里粘多次运行的输出，否则行数会翻倍。\n"
        "若句子自带「1. xxx」编号，程序会自动去掉编号，无需改。\n"
    )
    while True:
        print("— 请粘贴，结束后一行输入 END —")
        block_lines: list[str] = []
        while True:
            try:
                line = input()
            except EOFError:
                print("\n结束。")
                return
            stripped = line.strip()
            if stripped.upper() == "END":
                break
            if not block_lines and stripped.lower() == "q":
                print("结束。")
                return
            block_lines.append(line)
        blob = "\n".join(block_lines)
        lines = _lines_from_text_blob(blob)
        if not lines:
            print("（没有有效句子，空行与 # 开头行已忽略；请继续粘贴或输入 q。）\n")
            continue
        if len(lines) == 1:
            run_once(
                lines[0],
                intent_pack,
                sentiment_pack,
                device,
                args.max_length,
                args.young_pet_boost,
                skip_intent_rules=args.no_intent_rules,
                skip_sentiment_rules=args.no_sentiment_rules,
            )
        else:
            run_batch(
                lines,
                intent_pack,
                sentiment_pack,
                device,
                args.max_length,
                args.young_pet_boost,
                args.batch_size,
                skip_intent_rules=args.no_intent_rules,
                skip_sentiment_rules=args.no_sentiment_rules,
                as_json=args.batch_json,
            )
        print()


if __name__ == "__main__":
    main()

