"""
【作用】用模拟订单作为「用户真实偏好」参照，对推荐 CSV 离线算 Recall@K、Precision@K、新颖度等。
【效果】终端/日志输出指标，用于对比不同 model_version 或融合策略前的自检（非真实转化率）。

进库前离线看「预测效果」：用模拟订单 history 当参照，对推荐 CSV 算 Recall@K / Precision@K / 新颖度。

说明：正样本是构造的，指标反映「推荐是否还指向该用户历史买过的商品池」，
不是真实世界转化率；用于答辩前自检与对比不同 CSV/模型版本。
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="离线评估推荐 CSV（相对 orders 历史）")
    p.add_argument("--rec_csv", type=str, default="outputs/user_recommendations_hybrid.csv")
    p.add_argument("--orders_csv", type=str, default="data/orders_user_product_simulated.csv")
    p.add_argument("--topk", type=int, default=20, help="与推荐 CSV 中每用户条数一致，用于截断")
    return p


def main() -> None:
    args = build_parser().parse_args()
    rec_path = Path(args.rec_csv)
    ord_path = Path(args.orders_csv)
    if not rec_path.exists():
        raise FileNotFoundError(rec_path)
    if not ord_path.exists():
        raise FileNotFoundError(ord_path)

    rec = pd.read_csv(rec_path)
    orders = pd.read_csv(ord_path)
    for col in ("user_id", "product_id"):
        if col not in rec.columns or col not in orders.columns:
            raise ValueError(f"须含列 user_id, product_id: rec={list(rec.columns)} orders={list(orders.columns)}")

    k = int(args.topk)
    hist = orders.groupby("user_id")["product_id"].apply(lambda s: set(s.astype(int))).to_dict()

    recalls = []
    precs = []
    novelties = []

    for uid, g in rec.groupby("user_id"):
        uid = int(uid)
        bought = hist.get(uid, set())
        if not bought:
            continue
        g = g.sort_values("rank_no").head(k)
        rset = set(g["product_id"].astype(int))
        inter = len(rset & bought)
        recalls.append(inter / max(len(bought), 1))
        precs.append(inter / min(k, len(g)))
        novelties.append(1.0 - inter / min(k, len(g)) if len(g) else 0.0)

    print(f"推荐文件: {rec_path.resolve()}")
    print(f"订单参照: {ord_path.resolve()}")
    print(f"用户数(在历史非空): {len(recalls)}  TopK={k}")
    if not recalls:
        print("无可用用户（检查 orders 是否与 rec 用户一致）")
        return

    print(f"Recall@{k} 均值: {sum(recalls)/len(recalls):.4f}   (TopK 命中历史购买的比例，越高越「贴历史」)")
    print(f"Precision@{k} 均值: {sum(precs)/len(precs):.4f}   (TopK 里有多少在历史购买里)")
    print(f"新颖度 均值: {sum(novelties)/len(novelties):.4f}   (TopK 里不在历史购买中的占比，越高越「探索」)")
    print("— 解读: 虚拟订单下二者此消彼长；可对比纯用户级 CSV 与 hybrid 两版数值。")


if __name__ == "__main__":
    main()
