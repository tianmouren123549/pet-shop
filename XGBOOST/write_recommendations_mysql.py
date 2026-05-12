"""
【作用】将 generate_user_recommendations 产出的推荐 CSV 批量 UPSERT 到 user_product_recommendation 表。
【效果】商城首页/推荐接口可读到离线 TopN；支持按 model_version 覆盖旧批次。

将 generate_user_recommendations.py 产出的 CSV 写入表 user_product_recommendation。

环境变量（与命令行二选一，命令行优先）:
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE（默认库名 pet_shop，与后端 application.properties 一致）

用法:
  ..\\BERT\\.venv311\\Scripts\\python.exe write_recommendations_mysql.py --csv outputs/user_recommendations.csv

与商城「可对齐」的 ID:
  若训练/CSV 里的 product_id 与 pet_shop.product 不一致，后端会全部丢弃只剩热门补位。
  根本做法是订单与特征都用同一库商品重训；写入前可用 --filter-to-sellable-products 只保留库内可售商品并重排 rank_no。
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

import pandas as pd

try:
    import pymysql
except ImportError as e:
    raise SystemExit("请先安装: pip install pymysql") from e


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="将推荐 CSV 写入 MySQL user_product_recommendation")
    p.add_argument("--csv", type=str, default="outputs/user_recommendations.csv")
    p.add_argument("--host", type=str, default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    p.add_argument("--user", type=str, default=os.environ.get("MYSQL_USER", "root"))
    p.add_argument("--password", type=str, default=os.environ.get("MYSQL_PASSWORD", ""))
    p.add_argument(
        "--database",
        type=str,
        default=os.environ.get("MYSQL_DATABASE", "pet_shop"),
        help="与后端 application.properties 中库名一致（默认 pet_shop）",
    )
    p.add_argument(
        "--replace_model_version",
        action="store_true",
        help="写入前删除该 model_version 的全部旧行（避免重复）",
    )
    p.add_argument(
        "--filter-to-sellable-products",
        action="store_true",
        help="仅写入 pet_shop.product 中存在且 status=1、stock>0 的行（与后端 RecommendationService 一致），并按 user_id+model_version 按 score 重算 rank_no",
    )
    return p


def _fetch_sellable_product_ids(cur) -> set[int]:
    cur.execute(
        "SELECT product_id FROM product WHERE status = 1 AND stock > 0"
    )
    return {int(r[0]) for r in cur.fetchall()}


def main() -> None:
    args = build_parser().parse_args()
    path = Path(args.csv)
    if not path.exists():
        raise FileNotFoundError(path)

    df = pd.read_csv(path)
    required = {"user_id", "product_id", "model_version", "score", "rank_no"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"CSV 缺少列: {missing}")

    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            if args.filter_to_sellable_products:
                allowed = _fetch_sellable_product_ids(cur)
                before = len(df)
                df = df[df["product_id"].astype(int).isin(allowed)].copy()
                dropped = before - len(df)
                if dropped:
                    print(
                        f"已按可售商品过滤: 丢弃 {dropped} 行（CSV 与当前库 product_id 不一致时请重训或改订单数据源）"
                    )
                if df.empty:
                    raise SystemExit("过滤后无行可写入；请对齐训练数据与 pet_shop.product 后再生成 CSV。")
                df = df.sort_values(
                    ["user_id", "model_version", "score"],
                    ascending=[True, True, False],
                )
                df["rank_no"] = df.groupby(["user_id", "model_version"], sort=False).cumcount() + 1

            versions = df["model_version"].dropna().unique().tolist()
            if args.replace_model_version and versions:
                placeholders = ",".join(["%s"] * len(versions))
                cur.execute(
                    f"DELETE FROM user_product_recommendation WHERE model_version IN ({placeholders})",
                    versions,
                )

            sql = (
                "INSERT INTO user_product_recommendation "
                "(user_id, product_id, model_version, score, rank_no, reason_json, generated_at) "
                "VALUES (%s, %s, %s, %s, %s, %s, NOW())"
            )
            for _, row in df.iterrows():
                reason = row.get("reason_json")
                if pd.isna(reason) if reason is not None else True:
                    reason_json = None
                else:
                    reason_json = str(reason)
                cur.execute(
                    sql,
                    (
                        int(row["user_id"]),
                        int(row["product_id"]),
                        str(row["model_version"]),
                        float(row["score"]),
                        int(row["rank_no"]),
                        reason_json,
                    ),
                )
        conn.commit()
        print(f"已写入 {len(df)} 条到 {args.database}.user_product_recommendation")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
