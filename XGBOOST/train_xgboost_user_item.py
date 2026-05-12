"""
【作用】在 user_item_train.csv 上训练 sklearn+XGBoost 管道（类别 OneHot + 数值填充 + XGBClassifier）。
【效果】输出 models/.../pipeline.joblib 与 feature_meta.json，供 generate_user_recommendations 与在线服务加载。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.metrics import classification_report, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from xgboost import XGBClassifier


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="训练用户-商品 XGBoost（点击/购买倾向二分类）")
    p.add_argument("--data", type=str, default="data/user_item_train.csv")
    p.add_argument("--output_dir", type=str, default="models/xgb_user_item_v1")
    p.add_argument("--test_size", type=float, default=0.2)
    p.add_argument("--random_state", type=int, default=42)
    return p


def pick_feature_columns(df: pd.DataFrame) -> tuple[list[str], list[str]]:
    drop = {"user_id", "product_id", "label"}
    cols = [c for c in df.columns if c not in drop]
    force_cat = {"category"}
    cat_cols = []
    num_cols = []
    for c in cols:
        if c in force_cat:
            cat_cols.append(c)
        elif df[c].dtype == object or str(df[c].dtype) == "string":
            cat_cols.append(c)
        else:
            num_cols.append(c)
    return cat_cols, num_cols


def main() -> None:
    args = build_parser().parse_args()
    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(args.data)
    if "label" not in df.columns:
        raise ValueError("训练集必须包含 label 列")

    y = df["label"].astype(int)
    cat_features, num_features = pick_feature_columns(df)
    if not cat_features and not num_features:
        raise ValueError("没有可用特征列")

    X = df[cat_features + num_features].copy()
    for c in cat_features:
        X[c] = X[c].fillna("unknown").astype(str)
    for c in num_features:
        X[c] = pd.to_numeric(X[c], errors="coerce")

    transformers = []
    if cat_features:
        transformers.append(
            (
                "cat",
                Pipeline([("imputer", SimpleImputer(strategy="most_frequent")), ("oh", OneHotEncoder(handle_unknown="ignore"))]),
                cat_features,
            )
        )
    if num_features:
        transformers.append(
            ("num", Pipeline([("imputer", SimpleImputer(strategy="median"))]), num_features),
        )

    preprocessor = ColumnTransformer(transformers=transformers)

    clf = XGBClassifier(
        n_estimators=400,
        max_depth=8,
        learning_rate=0.05,
        subsample=0.9,
        colsample_bytree=0.85,
        objective="binary:logistic",
        eval_metric="logloss",
        random_state=args.random_state,
    )
    pipe = Pipeline([("prep", preprocessor), ("clf", clf)])

    try:
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=args.test_size, random_state=args.random_state, stratify=y
        )
    except ValueError:
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=args.test_size, random_state=args.random_state
        )
    pipe.fit(X_train, y_train)
    prob = pipe.predict_proba(X_test)[:, 1]
    pred = (prob >= 0.5).astype(int)
    auc = roc_auc_score(y_test, prob)
    report = classification_report(y_test, pred, digits=4)

    joblib.dump(pipe, out_dir / "pipeline.joblib")
    pipe.named_steps["clf"].get_booster().save_model(str(out_dir / "model.json"))
    meta = {
        "categorical_features": cat_features,
        "numerical_features": num_features,
        "auc": float(auc),
        "test_size": args.test_size,
        "random_state": args.random_state,
    }
    (out_dir / "feature_meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"模型目录: {out_dir.resolve()}")
    print(f"AUC: {auc:.4f}")
    print(report)


if __name__ == "__main__":
    main()
