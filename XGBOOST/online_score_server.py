"""
【作用】提供 FastAPI HTTP 接口，按请求中的 user_id 与 product_id 列表实时加载 XGBoost 管道打分。
【效果】Java 推荐模块可配置 base-url 调用，实现「在线优先、离线表兜底」中的在线分支。

最小在线推理：加载与离线相同的 XGB pipeline + product_features_agg.csv，
对请求中的 product_id 批量打分（含库中无评论特征的新品：用列中位数 + category=unknown 冷启动）。

依赖（与训练环境一致即可）:
  pip install fastapi uvicorn pandas joblib scikit-learn xgboost

一键启动（推荐）:
  PowerShell 执行: .\\run_online_score_server.ps1

手动示例（在 XGBOOST 目录下）:
  ..\\BERT\\.venv311\\Scripts\\python.exe -m uvicorn online_score_server:app --host 127.0.0.1 --port 8765

后端已默认开启（见 application.properties）:
  app.recommendation.online-inference-enabled=true
  app.recommendation.online-inference-base-url=http://127.0.0.1:8765
"""

from __future__ import annotations

import json
import os
from pathlib import Path

import joblib
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel, Field

MODEL_DIR = Path(os.environ.get("MODEL_DIR", "models/xgb_user_item_demo"))
FEAT_CSV = Path(os.environ.get("PRODUCT_FEATURES_CSV", "data/product_features_agg.csv"))
MODEL_VERSION = os.environ.get("MODEL_VERSION", "xgb_online_v1")

pipe = None
cat_cols: list[str] = []
num_cols: list[str] = []
feat_by_pid: dict[int, pd.Series] = {}
default_row: dict = {}


def _startup() -> None:
    global pipe, cat_cols, num_cols, feat_by_pid, default_row

    meta_path = MODEL_DIR / "feature_meta.json"
    if not meta_path.exists():
        raise FileNotFoundError(f"缺少 {meta_path.resolve()}，请先训练或设置 MODEL_DIR")
    if not FEAT_CSV.exists():
        raise FileNotFoundError(f"缺少 {FEAT_CSV.resolve()}，请先 aggregate_product_features 或设置 PRODUCT_FEATURES_CSV")

    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    cat_cols = list(meta.get("categorical_features", []))
    num_cols = list(meta.get("numerical_features", []))
    trained = cat_cols + num_cols
    if not trained:
        raise ValueError("feature_meta.json 中无特征列")

    pipe = joblib.load(MODEL_DIR / "pipeline.joblib")

    feat = pd.read_csv(FEAT_CSV)
    if "product_id" not in feat.columns:
        raise ValueError(f"{FEAT_CSV} 缺少 product_id")

    feat_by_pid.clear()
    for _, row in feat.iterrows():
        feat_by_pid[int(row["product_id"])] = row.copy()

    default_row = {}
    for c in cat_cols:
        default_row[c] = "unknown"
    for c in num_cols:
        if c == "user_order_cnt":
            default_row[c] = 0.0
        elif c in feat.columns:
            default_row[c] = float(pd.to_numeric(feat[c], errors="coerce").median())
        else:
            default_row[c] = 0.0


app = FastAPI(title="petshop-online-xgb", version="1.0.0")


@app.on_event("startup")
def on_startup() -> None:
    _startup()


class ScoreIn(BaseModel):
    user_order_cnt: int = Field(default=0, ge=0, le=500_000)
    product_ids: list[int] = Field(default_factory=list, min_length=1, max_length=300)


class ScoreOut(BaseModel):
    model_version: str
    scores: list[dict]


def _row_for_product(pid: int, ucnt: int) -> dict[str, object]:
    trained = cat_cols + num_cols
    if pid in feat_by_pid:
        d = feat_by_pid[pid].to_dict()
    else:
        d = dict(default_row)
    d["product_id"] = pid
    for c in trained:
        if c == "user_order_cnt":
            continue
        v = d.get(c)
        try:
            bad = c not in d or pd.isna(v)
        except (TypeError, ValueError):
            bad = c not in d
        if not bad and isinstance(v, str) and not v.strip():
            bad = True
        if bad:
            d[c] = default_row.get(c, 0.0 if c in num_cols else "unknown")
    d["user_order_cnt"] = ucnt
    return d


@app.get("/")
def root() -> dict:
    """浏览器打开根路径时返回说明（无 favicon，/favicon.ico 仍可能 404，可忽略）。"""
    return {
        "service": "petshop-online-xgb",
        "health": "/health",
        "score": "POST /v1/score",
        "openapi": "/docs",
    }


@app.get("/health")
def health() -> dict:
    return {"ok": True, "model_version": MODEL_VERSION, "model_dir": str(MODEL_DIR.resolve())}


@app.post("/v1/score", response_model=ScoreOut)
def score(body: ScoreIn) -> ScoreOut:
    if pipe is None:
        raise RuntimeError("模型未加载")

    seen: set[int] = set()
    pids: list[int] = []
    for x in body.product_ids:
        if x in seen:
            continue
        seen.add(x)
        pids.append(int(x))
        if len(pids) >= 300:
            break

    ucnt = int(body.user_order_cnt)
    rows = [_row_for_product(pid, ucnt) for pid in pids]
    base = pd.DataFrame(rows)
    use_cols = cat_cols + num_cols
    missing = [c for c in use_cols if c not in base.columns]
    if missing:
        raise ValueError(f"特征缺列: {missing}")

    X = base[use_cols].copy()
    for c in cat_cols:
        X[c] = X[c].fillna("unknown").astype(str)
    for c in num_cols:
        X[c] = pd.to_numeric(X[c], errors="coerce")

    prob = pipe.predict_proba(X)[:, 1]
    base["score"] = prob
    base = base.sort_values("score", ascending=False)
    scores = [
        {"product_id": int(r["product_id"]), "score": float(r["score"])}
        for _, r in base.iterrows()
    ]
    return ScoreOut(model_version=MODEL_VERSION, scores=scores)
