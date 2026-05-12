"""
【作用】对评论文本用 BERT Encoder 取 [CLS] 向量，PCA 降维后按商品均值聚合，并拼入商品特征表。
【效果】得到含 bert_pca_* 列的增强 CSV 与 pca 工件；需与用户级 XGBoost 重训配套使用。

对评论文本用预训练 BERT（仅 Encoder，取 [CLS] 向量）编码 → PCA 降维 → 按商品 mean 聚合，
再并入已有的 product_features_agg.csv，供用户级 XGBoost 使用。

依赖 torch、transformers、tqdm；建议用 BERT 目录下虚拟环境运行：
  ..\\BERT\\.venv311\\Scripts\\python.exe bert_comment_embed_pca_aggregate.py

流程：先 aggregate_product_features.py 生成 product_features_agg.csv，再运行本脚本，
最后在 build_user_item_train / train / generate 中将 --product_features 指向输出 CSV。
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import torch
from sklearn.decomposition import PCA
from tqdm import tqdm
from transformers import BertModel, BertTokenizer


def _has_encoder_weights(d: Path) -> bool:
    return (d / "model.safetensors").is_file() or (d / "pytorch_model.bin").is_file()


def find_hf_hub_bert_chinese_snapshot() -> Path | None:
    """在默认 HF 缓存目录中查找已下载的 bert-base-chinese（不发起网络请求）。"""
    hub = Path.home() / ".cache" / "huggingface" / "hub"
    if not hub.is_dir():
        return None
    candidates: list[Path] = []
    for d in hub.iterdir():
        if not d.is_dir():
            continue
        name = d.name.lower()
        if "bert" not in name or "chinese" not in name:
            continue
        for snap in d.glob("snapshots/*"):
            if (snap / "config.json").is_file() and _has_encoder_weights(snap):
                candidates.append(snap)
    if not candidates:
        return None
    candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates[0]


def find_project_bert_checkpoint() -> Path | None:
    """查找项目 BERT/训练成果 下含权重的子目录（可用 BertModel 加载 Encoder）。"""
    root = Path(__file__).resolve().parent.parent / "BERT" / "训练成果"
    if not root.is_dir():
        return None
    found: list[Path] = []
    for sub in root.iterdir():
        if not sub.is_dir():
            continue
        if (sub / "config.json").is_file() and _has_encoder_weights(sub):
            found.append(sub)
    if not found:
        return None
    found.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return found[0]


def resolve_bert_model_path(arg: str, *, allow_download: bool) -> str:
    if allow_download:
        return arg
    p = Path(arg).expanduser()
    looks_like_path = "\\" in arg or "/" in arg or arg.startswith(".")
    if looks_like_path:
        if not p.is_dir():
            raise FileNotFoundError(f"--bert_model 路径不存在或不是目录: {p}")
        if not (p / "config.json").is_file():
            raise FileNotFoundError(f"目录缺少 config.json: {p.resolve()}")
        if not _has_encoder_weights(p):
            raise FileNotFoundError(
                f"目录缺少 model.safetensors 或 pytorch_model.bin: {p.resolve()}\n"
                "请指向完整 BERT 权重目录，或使用本机 HF 缓存。"
            )
        return str(p.resolve())

    # 视为 Hub 模型名：仅离线时使用缓存 / 项目内权重
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    snap = find_hf_hub_bert_chinese_snapshot()
    if snap is not None:
        print(f"使用本机 HuggingFace 缓存: {snap}")
        return str(snap)
    proj = find_project_bert_checkpoint()
    if proj is not None:
        print(f"使用项目内 BERT 权重: {proj}")
        return str(proj.resolve())

    raise FileNotFoundError(
        "离线模式下未找到可用 BERT 权重（无法连接 HuggingFace）。\n"
        "请任选其一：\n"
        "  1) 用 --bert_model 指向含 config.json + model.safetensors（或 pytorch_model.bin）的本地目录；\n"
        "  2) 在有网环境执行一次 `transformers` 下载 bert-base-chinese 后，本机缓存会出现在 "
        f"{Path.home() / '.cache' / 'huggingface' / 'hub'}；\n"
        "  3) 设置镜像后加 --allow_download，例如 PowerShell: "
        "$env:HF_ENDPOINT='https://hf-mirror.com'\n"
        "     然后: python bert_comment_embed_pca_aggregate.py --allow_download\n"
    )


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="BERT 句向量 + PCA + 按商品聚合，并入 product_features_agg")
    p.add_argument("--comment_features", type=str, default="data/comment_features.csv")
    p.add_argument("--agg_csv", type=str, default="data/product_features_agg.csv", help="aggregate_product_features 产出")
    p.add_argument(
        "--output",
        type=str,
        default="data/product_features_agg_with_bert_pca.csv",
        help="合并后的商品特征表（训练/推理改指向此文件）",
    )
    p.add_argument(
        "--bert_model",
        type=str,
        default="bert-base-chinese",
        help="Hub 模型名或本地目录（config + model.safetensors / pytorch_model.bin）。默认离线：自动用缓存或 BERT/训练成果",
    )
    p.add_argument(
        "--allow_download",
        action="store_true",
        help="允许访问 HuggingFace（不设则离线，避免 list_repo / 下载超时）",
    )
    p.add_argument("--n_pca", type=int, default=64, help="PCA 目标维数（≤768）")
    p.add_argument("--batch_size", type=int, default=16)
    p.add_argument("--max_length", type=int, default=128)
    p.add_argument("--device", type=str, default="", help="cuda / cpu，空则自动")
    p.add_argument(
        "--pca_artifact",
        type=str,
        default="models/pca_bert_comment.joblib",
        help="保存 PCA 与元信息，便于论文记录随机种子与维数",
    )
    p.add_argument("--seed", type=int, default=42)
    return p


@torch.inference_mode()
def cls_embeddings(
    model: torch.nn.Module,
    tokenizer,
    texts: list[str],
    batch_size: int,
    device: torch.device,
    max_length: int,
) -> np.ndarray:
    model.eval()
    chunks: list[np.ndarray] = []
    for start in tqdm(range(0, len(texts), batch_size), desc="BERT encode"):
        batch = texts[start : start + batch_size]
        enc = tokenizer(
            batch,
            padding=True,
            truncation=True,
            max_length=max_length,
            return_tensors="pt",
        )
        enc = {k: v.to(device) for k, v in enc.items()}
        out = model(**enc)
        # [CLS] = 第 0 个 token
        cls_vec = out.last_hidden_state[:, 0, :].float().cpu().numpy()
        chunks.append(cls_vec)
    return np.vstack(chunks).astype(np.float32)


def main() -> None:
    args = build_parser().parse_args()
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)

    cf = Path(args.comment_features)
    agg_path = Path(args.agg_csv)
    if not cf.exists():
        raise FileNotFoundError(cf)
    if not agg_path.exists():
        raise FileNotFoundError(agg_path)

    df = pd.read_csv(cf)
    if "text" not in df.columns or "product_id" not in df.columns:
        raise ValueError("comment_features 须含 text, product_id")

    texts = df["text"].fillna("").astype(str).tolist()
    if len(texts) < 2:
        raise ValueError("评论条数过少")

    device_s = (args.device or "").strip().lower()
    if device_s in ("cuda", "cpu"):
        device = torch.device(device_s)
    else:
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    model_path = resolve_bert_model_path(args.bert_model, allow_download=args.allow_download)
    local_only = not args.allow_download
    tokenizer = BertTokenizer.from_pretrained(model_path, local_files_only=local_only)
    model = BertModel.from_pretrained(model_path, local_files_only=local_only)
    model.to(device)

    X = cls_embeddings(model, tokenizer, texts, args.batch_size, device, args.max_length)
    hid = X.shape[1]
    n_pca = min(int(args.n_pca), hid, max(len(texts) - 1, 1))
    if n_pca < 1:
        raise ValueError("n_pca 无效")

    pca = PCA(n_components=n_pca, random_state=args.seed)
    Z = pca.fit_transform(X).astype(np.float32)

    pca_cols = [f"bert_pca_{j}" for j in range(n_pca)]
    side = pd.DataFrame(Z, columns=pca_cols)
    side["product_id"] = df["product_id"].values
    prod_pca = side.groupby("product_id", as_index=False)[pca_cols].mean()

    agg = pd.read_csv(agg_path)
    if "product_id" not in agg.columns:
        raise ValueError("agg_csv 须含 product_id")
    merged = agg.merge(prod_pca, on="product_id", how="left")
    for c in pca_cols:
        merged[c] = pd.to_numeric(merged[c], errors="coerce").fillna(0.0)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    merged.to_csv(out, index=False, encoding="utf-8-sig")

    art = Path(args.pca_artifact)
    art.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "pca": pca,
            "bert_model": model_path,
            "n_pca": n_pca,
            "explained_variance_ratio_sum": float(np.sum(pca.explained_variance_ratio_)),
            "random_state": args.seed,
        },
        art,
    )
    meta = {
        "bert_model": model_path,
        "n_pca": n_pca,
        "explained_variance_ratio_sum": float(np.sum(pca.explained_variance_ratio_)),
        "comment_rows": len(df),
        "products_with_pca": int(len(prod_pca)),
    }
    art.with_suffix(".json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"设备: {device}")
    print(f"PCA 维数: {n_pca}，累计解释方差比之和: {meta['explained_variance_ratio_sum']:.4f}")
    print(f"合并写出: {out.resolve()} 行数: {len(merged)}")
    print(f"PCA 对象: {art.resolve()}")


if __name__ == "__main__":
    main()
