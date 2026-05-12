"""
【作用】从 MySQL 订单/明细中导出「用户已购商品」两列关系，替代手工 CSV。
【效果】生成 data/orders_user_product.csv（或指定路径），与 build_user_item_train 输入格式一致。

从 MySQL 读取「用户-已购商品」正样本，写入 data/orders_user_product.csv，
供 build_user_item_train.py 使用（无需在客户端手工导出 CSV）。

环境变量：MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE（默认 pet_shop）
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


SQL = """
SELECT DISTINCT o.user_id AS user_id, oi.product_id AS product_id
FROM order_item oi
INNER JOIN orders o ON o.order_id = oi.order_id
WHERE o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
ORDER BY o.user_id, oi.product_id
"""


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="从 MySQL 导出 user_id,product_id 到 CSV")
    p.add_argument("--output", type=str, default="data/orders_user_product.csv")
    p.add_argument("--host", type=str, default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    p.add_argument("--user", type=str, default=os.environ.get("MYSQL_USER", "root"))
    p.add_argument("--password", type=str, default=os.environ.get("MYSQL_PASSWORD", ""))
    p.add_argument(
        "--database",
        type=str,
        default=os.environ.get("MYSQL_DATABASE", "pet_shop"),
        help="默认 pet_shop，与后端 application.properties 一致",
    )
    return p


def main() -> None:
    args = build_parser().parse_args()
    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
    )
    try:
        df = pd.read_sql(SQL, conn)
    finally:
        conn.close()

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(out, index=False, encoding="utf-8-sig")
    print(f"输出: {out.resolve()} 行数: {len(df)}")


if __name__ == "__main__":
    main()
