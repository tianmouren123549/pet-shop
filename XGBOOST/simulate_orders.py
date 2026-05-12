"""
【作用】在无真实订单时，从商品特征池随机抽样合成 user_id–product_id「购买」正样本。
【效果】输出 orders_user_product_*.csv，供 build_user_item_train 与后续用户级推荐训练使用。

模拟「用户–已购商品」正样本（user_id, product_id）→ CSV。

适用于无真实订单、商品/评论均为虚拟数据的毕设场景：不依赖订单表，
只从本项目的 `product_features_agg.csv`（及可选 CSV 白名单）里取 product_id，
合成用户 1..N 或外部 CSV 中的 user_id。
"""

from __future__ import annotations

import argparse
import random
from pathlib import Path

import pandas as pd


def _require_csv(path: Path, flag_name: str) -> None:
    if not path.is_file():
        raise FileNotFoundError(
            f"未找到 {flag_name}: {path.resolve()}\n"
            "请检查路径，或去掉该可选参数（虚拟数据一般不需要白名单 CSV）。"
        )


def _load_id_column(path: Path, col: str) -> list[int]:
    """读取单列 ID；兼容无表头 CSV（首行数字被误当成列名如 '1'）。"""
    sub = pd.read_csv(path)
    sub.columns = [str(c).strip().lstrip("\ufeff") for c in sub.columns]

    if col not in sub.columns:
        by_lower = {str(c).lower(): c for c in sub.columns}
        if col.lower() in by_lower:
            sub = sub.rename(columns={by_lower[col.lower()]: col})
        elif len(sub.columns) == 1:
            lone = sub.columns[0]
            try:
                int(str(lone).strip(), 10)
            except ValueError:
                print(f"提示: {path.name} 表头为 {lone!r}，已将唯一列当作 {col}。")
                return sub.iloc[:, 0].dropna().astype(int).unique().tolist()
            sub = pd.read_csv(path, header=None, names=[col])
            print(
                f"提示: {path.name} 无表头行 {col!r}，首行数字被误当作列名；"
                "已按「整列均为 ID」重新读取。建议 CSV 第一行写列名 product_id / user_id。"
            )
        else:
            raise ValueError(f"{path} 必须包含列 {col!r}，当前: {list(sub.columns)}")

    return sub[col].dropna().astype(int).unique().tolist()


def _resolve_user_ids(args: argparse.Namespace) -> list[int]:
    if not args.user_ids_csv:
        return list(range(1, args.num_users + 1))

    u_path = Path(args.user_ids_csv)
    _require_csv(u_path, "--user_ids_csv")
    pool = _load_id_column(u_path, "user_id")
    if not pool:
        raise ValueError("user_ids_csv 中无有效 user_id")
    if len(pool) < args.num_users:
        print(f"提示: 用户池中仅 {len(pool)} 个 ID，少于 --num_users={args.num_users}，将全部使用。")
    k = min(args.num_users, len(pool))
    return random.sample(pool, k)


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="CSV 模拟 user_id + product_id（无真实订单时使用）")
    p.add_argument(
        "--product_source",
        type=str,
        default="data/product_features_agg.csv",
        help="含 product_id 的 CSV，默认 product_features_agg.csv（与训练特征同源）",
    )
    p.add_argument(
        "--user_ids_csv",
        type=str,
        default="",
        help="可选：含 user_id 的 CSV，在此池中抽样；不设则用合成 ID 1..num_users",
    )
    p.add_argument(
        "--product_ids_csv",
        type=str,
        default="",
        help="可选：product_id 白名单，与 product_source 求交集（虚拟课题常可省略）",
    )
    p.add_argument(
        "--skip_product_ids_if_no_overlap",
        action="store_true",
        help="白名单与聚合表交集不足 2 个时，忽略白名单，仅用聚合表商品池（并打印警告）",
    )
    p.add_argument("--num_users", type=int, default=80, help="参与模拟的用户数（无 user_ids_csv 时为合成 ID 1..N）")
    p.add_argument("--min_orders", type=int, default=1, help="每用户最少「购买」商品数")
    p.add_argument("--max_orders", type=int, default=6, help="每用户最多「购买」商品数")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--output", type=str, default="data/orders_user_product_simulated.csv")
    p.add_argument(
        "--shuffle_output",
        action="store_true",
        help="默认按 user_id, product_id 排序（与 export_for_xgboost.sql 的 ORDER BY 一致）；指定本参数则保持生成时的行序",
    )
    return p


def main() -> None:
    args = build_parser().parse_args()
    random.seed(args.seed)

    src = Path(args.product_source)
    if not src.exists():
        raise FileNotFoundError(src)

    df = pd.read_csv(src)
    if "product_id" not in df.columns:
        raise ValueError("product_source 必须包含 product_id")

    pids_all = df["product_id"].dropna().astype(int).unique().tolist()
    pids = pids_all
    if args.product_ids_csv:
        p_path = Path(args.product_ids_csv)
        _require_csv(p_path, "--product_ids_csv")
        allow = set(_load_id_column(p_path, "product_id"))
        allow_n = len(allow)
        pids = [p for p in pids if int(p) in allow]
        if len(pids) < 2:
            if args.skip_product_ids_if_no_overlap:
                print(
                    f"警告: 库白名单与「有评论商品」交集仅 {len(pids)} 个；"
                    f"聚合表共 {len(pids_all)} 个、白名单 {allow_n} 个。"
                    "已忽略 --product_ids_csv，仅用聚合表商品池（与训练特征一致）。"
                )
                pids = pids_all
            else:
                raise ValueError(
                    f"product_source 与白名单交集仅 {len(pids)} 个商品，无法模拟（至少需要 2 个）。\n"
                    f"  说明: product_features_agg 来自评论聚合，一般只含「有评论」的 product_id（当前 {len(pids_all)} 个）；"
                    f"库内导出的在售商品有 {allow_n} 个，二者常不完全重合。\n"
                    "可选: ① 去掉 --product_ids_csv；② 在库里只导出「review 表里出现过的」在售 product_id；"
                    "③ 扩充评论数据后重跑 aggregate_product_features.py；"
                    "④ 若接受仅用当前聚合表里的商品模拟，请加 --skip_product_ids_if_no_overlap。"
                )
    if len(pids) < 2:
        raise ValueError(
            "商品数量太少: 请检查 product_source（comment_features → aggregate 后是否至少 2 个 product_id）。"
        )

    # 若有 review_cnt，略偏向评论多的商品（更像「热卖」）
    weights = None
    if "review_cnt" in df.columns:
        wmap = df.set_index("product_id")["review_cnt"].astype(float).clip(lower=1.0)
        weights = [float(wmap.get(pid, 1.0)) for pid in pids]

    user_ids = _resolve_user_ids(args)

    rows = []
    for uid in user_ids:
        k = random.randint(args.min_orders, min(args.max_orders, len(pids)))
        chosen = random.choices(pids, weights=weights, k=k) if weights else random.sample(pids, k=min(k, len(pids)))
        seen: set[int] = set()
        for pid in chosen:
            pid = int(pid)
            if pid in seen:
                continue
            seen.add(pid)
            rows.append({"user_id": uid, "product_id": pid})

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out_df = pd.DataFrame(rows).drop_duplicates()
    if not args.shuffle_output:
        out_df = out_df.sort_values(["user_id", "product_id"], kind="mergesort").reset_index(drop=True)
    out_df.to_csv(out, index=False, encoding="utf-8-sig")

    print(f"输出: {out.resolve()}")
    print(f"用户数: {out_df['user_id'].nunique()} 订单行数(去重后): {len(out_df)}")


if __name__ == "__main__":
    main()
