"""
【作用】把评论与 BERT 标签聚合为每商品一行，写入 product_text_feature 表（与后端读特征一致）。
【效果】库内商品具备统计型文本特征/可选 PCA JSON，便于推荐与检索侧直接 SQL 读取。

将站内评论聚合为 product_text_feature 表的一行/商品（与 data.sql 中表结构一致）。

数据来源：
  - review 表的 rating、sentiment_label、intent_label（需先跑 BERT 回填或手工有标签）
  - 可选：product_features_agg_with_bert_pca.csv 中的 bert_pca_0..63 写入 pca_json（JSON 数组）

环境变量：MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE

示例：
  python sync_product_text_feature_mysql.py --dry-run
  python sync_product_text_feature_mysql.py --model-version mysql_agg_v1
  python sync_product_text_feature_mysql.py --pca-csv data/product_features_agg_with_bert_pca.csv \\
      --model-version agg_v1+pca64_csv
"""

from __future__ import annotations

import argparse
import json
import os
from decimal import Decimal
from pathlib import Path

import pandas as pd

try:
    import pymysql
except ImportError as e:
    raise SystemExit("请先安装: pip install pymysql") from e

INTENT_TO_KEY = {
    "喂养咨询": "feed",
    "商品咨询": "product_consult",
    "价格优惠": "price",
    "物流发货": "logistics",
    "售后退换": "after_sale",
    "商品反馈": "feedback",
}

SQL_REVIEWS = """
SELECT product_id, rating, sentiment_label, intent_label, created_at
FROM review
WHERE IFNULL(status, 1) = 1
  AND rating IS NOT NULL
{time_filter}
"""

UPSERT = """
INSERT INTO product_text_feature (
  product_id, model_version, embed_json, pca_json,
  review_cnt, avg_rating,
  sentiment_pos_rate, sentiment_neu_rate, sentiment_neg_rate,
  intent_feed_rate, intent_product_consult_rate, intent_price_rate,
  intent_logistics_rate, intent_after_sale_rate, intent_feedback_rate,
  feature_window_days, updated_at
) VALUES (
  %s, %s, %s, %s,
  %s, %s,
  %s, %s, %s,
  %s, %s, %s,
  %s, %s, %s,
  %s, NOW()
)
ON DUPLICATE KEY UPDATE
  embed_json = VALUES(embed_json),
  pca_json = VALUES(pca_json),
  review_cnt = VALUES(review_cnt),
  avg_rating = VALUES(avg_rating),
  sentiment_pos_rate = VALUES(sentiment_pos_rate),
  sentiment_neu_rate = VALUES(sentiment_neu_rate),
  sentiment_neg_rate = VALUES(sentiment_neg_rate),
  intent_feed_rate = VALUES(intent_feed_rate),
  intent_product_consult_rate = VALUES(intent_product_consult_rate),
  intent_price_rate = VALUES(intent_price_rate),
  intent_logistics_rate = VALUES(intent_logistics_rate),
  intent_after_sale_rate = VALUES(intent_after_sale_rate),
  intent_feedback_rate = VALUES(intent_feedback_rate),
  feature_window_days = VALUES(feature_window_days),
  updated_at = NOW()
"""


def round4(x: float) -> float:
    return float(Decimal(str(x)).quantize(Decimal("0.0001")))


def aggregate_product_rows(df: pd.DataFrame) -> list[dict]:
    out: list[dict] = []
    for pid, g in df.groupby("product_id"):
        pid = int(pid)
        n = len(g)
        avg_rating = float(pd.to_numeric(g["rating"], errors="coerce").mean() or 0.0)
        avg_rating = min(5.0, max(0.0, round(avg_rating + 1e-9, 2)))

        raw_s = g["sentiment_label"]
        m_s = raw_s.notna() & ~raw_s.astype(str).str.strip().isin(["", "nan", "None"])
        svalid = g[m_s]
        if len(svalid) > 0:
            vc = svalid["sentiment_label"].astype(str).str.strip().value_counts(normalize=True)
            pos = round4(float(vc.get("好评", 0)))
            neu = round4(float(vc.get("中评", 0)))
            neg = round4(float(vc.get("差评", 0)))
        else:
            pos = neu = neg = 0.0

        raw_i = g["intent_label"]
        m_i = raw_i.notna() & ~raw_i.astype(str).str.strip().isin(["", "nan", "None"])
        ivalid = g[m_i]
        ir = {k: 0.0 for k in INTENT_TO_KEY.values()}
        if len(ivalid) > 0:
            vc = ivalid["intent_label"].astype(str).str.strip().value_counts(normalize=True)
            for label, p in vc.items():
                p = float(p)
                key = INTENT_TO_KEY.get(label)
                if key:
                    ir[key] += p
                else:
                    ir["feedback"] += p
        feed = round4(ir["feed"])
        pc = round4(ir["product_consult"])
        price = round4(ir["price"])
        logi = round4(ir["logistics"])
        afters = round4(ir["after_sale"])
        fb = round4(ir["feedback"])

        out.append(
            {
                "product_id": pid,
                "review_cnt": int(n),
                "avg_rating": avg_rating,
                "sentiment_pos_rate": pos,
                "sentiment_neu_rate": neu,
                "sentiment_neg_rate": neg,
                "intent_feed_rate": feed,
                "intent_product_consult_rate": pc,
                "intent_price_rate": price,
                "intent_logistics_rate": logi,
                "intent_after_sale_rate": afters,
                "intent_feedback_rate": fb,
            }
        )
    return out


def load_pca_map(csv_path: Path) -> dict[int, str]:
    """product_id -> JSON array string for bert_pca_* columns."""
    df = pd.read_csv(csv_path)
    if "product_id" not in df.columns:
        raise ValueError(f"{csv_path} 缺少 product_id")
    pca_cols = [c for c in df.columns if c.startswith("bert_pca_")]
    if not pca_cols:
        raise ValueError(f"{csv_path} 无 bert_pca_* 列")
    pca_cols = sorted(pca_cols, key=lambda x: int(x.replace("bert_pca_", "")))
    m: dict[int, str] = {}
    for _, row in df.iterrows():
        pid = int(row["product_id"])
        vec = [float(row[c]) for c in pca_cols]
        m[pid] = json.dumps(vec, ensure_ascii=False)
    return m


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="聚合 review → 写入 product_text_feature")
    p.add_argument("--model-version", type=str, default="mysql_agg_v1", help="对应表 uk_ptf_product_model")
    p.add_argument("--window-days", type=int, default=0, help="仅统计最近 N 天评论，0 表示不限")
    p.add_argument("--feature-window-days", type=int, default=90, help="写入 feature_window_days 字段（说明用）")
    p.add_argument("--pca-csv", type=str, default=None, help="可选，含 bert_pca_0.. 的 CSV")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--host", type=str, default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    p.add_argument("--user", type=str, default=os.environ.get("MYSQL_USER", "root"))
    p.add_argument(
        "--password",
        type=str,
        default=os.environ.get("MYSQL_PASSWORD", "trq123549"),
    )
    p.add_argument("--database", type=str, default=os.environ.get("MYSQL_DATABASE", "pet_shop"))
    return p


def main() -> None:
    args = build_parser().parse_args()
    tf = ""
    if args.window_days and args.window_days > 0:
        tf = f" AND created_at >= DATE_SUB(NOW(), INTERVAL {int(args.window_days)} DAY)"
    sql = SQL_REVIEWS.format(time_filter=tf)

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
        df = pd.read_sql(sql, conn)
    finally:
        pass

    if df.empty:
        print("无可用评论行，退出。")
        conn.close()
        return

    rows = aggregate_product_rows(df)
    pca_by_pid: dict[int, str] = {}
    if args.pca_csv:
        pca_by_pid = load_pca_map(Path(args.pca_csv).resolve())

    print(f"聚合商品数: {len(rows)} model_version={args.model_version}")
    if args.dry_run:
        for r in rows[:5]:
            pid = r["product_id"]
            extra = " pca=有" if pid in pca_by_pid else ""
            print(f"  product_id={pid} review_cnt={r['review_cnt']} avg_rating={r['avg_rating']}{extra}")
        conn.close()
        return

    try:
        with conn.cursor() as cur:
            for r in rows:
                pid = r["product_id"]
                pca_json = pca_by_pid.get(pid)
                cur.execute(
                    UPSERT,
                    (
                        pid,
                        args.model_version,
                        None,
                        pca_json,
                        r["review_cnt"],
                        r["avg_rating"],
                        r["sentiment_pos_rate"],
                        r["sentiment_neu_rate"],
                        r["sentiment_neg_rate"],
                        r["intent_feed_rate"],
                        r["intent_product_consult_rate"],
                        r["intent_price_rate"],
                        r["intent_logistics_rate"],
                        r["intent_after_sale_rate"],
                        r["intent_feedback_rate"],
                        int(args.feature_window_days),
                    ),
                )
        conn.commit()
        print(f"已 UPSERT {len(rows)} 条 product_text_feature。")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
