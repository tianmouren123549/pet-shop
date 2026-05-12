"""
【作用】在 comment_features.csv 上训练「单条评论是否偏好评」的 XGBoost 二分类器。
【效果】生成 models/xgb_comment_ranker/（含 pipeline.joblib 等），供 recommend_items 或混合推荐打分使用。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.metrics import classification_report, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from xgboost import XGBClassifier


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="训练XGBoost评论偏好分类器")
    parser.add_argument("--data", type=str, default="data/comment_features.csv", help="评论特征CSV")
    parser.add_argument("--output_dir", type=str, default="models/xgb_comment_ranker", help="模型输出目录")
    parser.add_argument("--test_size", type=float, default=0.2, help="测试集比例")
    parser.add_argument("--random_state", type=int, default=42, help="随机种子")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(args.data)
    required = {"category", "text_len", "intent_label", "sentiment_label", "is_positive"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"输入文件缺少必要列: {missing}")

    y = df["is_positive"].astype(int)
    X = df[["category", "text_len", "intent_label", "sentiment_label"]].copy()

    cat_features = ["category", "intent_label", "sentiment_label"]
    num_features = ["text_len"]

    preprocessor = ColumnTransformer(
        transformers=[
            ("cat", Pipeline([("imputer", SimpleImputer(strategy="most_frequent")), ("oh", OneHotEncoder(handle_unknown="ignore"))]), cat_features),
            ("num", Pipeline([("imputer", SimpleImputer(strategy="median"))]), num_features),
        ]
    )

    clf = XGBClassifier(
        n_estimators=300,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.9,
        colsample_bytree=0.9,
        objective="binary:logistic",
        eval_metric="logloss",
        random_state=args.random_state,
    )

    pipe = Pipeline([("prep", preprocessor), ("clf", clf)])

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_state, stratify=y
    )

    pipe.fit(X_train, y_train)
    pred = pipe.predict(X_test)
    prob = pipe.predict_proba(X_test)[:, 1]

    report = classification_report(y_test, pred, digits=4)
    auc = roc_auc_score(y_test, prob)

    model_path = out_dir / "model.json"
    prep_path = out_dir / "preprocess_columns.json"

    # 保存xgboost模型参数（仅保存boosters）
    booster = pipe.named_steps["clf"].get_booster()
    booster.save_model(str(model_path))

    metadata = {
        "categorical_features": cat_features,
        "numerical_features": num_features,
        "test_size": args.test_size,
        "random_state": args.random_state,
        "auc": float(auc),
    }
    prep_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")

    # 同时保存完整pipeline（便于直接推理）
    import joblib

    joblib.dump(pipe, out_dir / "pipeline.joblib")

    print(f"模型目录: {out_dir.resolve()}")
    print(f"AUC: {auc:.4f}")
    print("分类报告:")
    print(report)


if __name__ == "__main__":
    main()
