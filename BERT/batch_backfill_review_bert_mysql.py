"""
【作用】连接 MySQL，读取 review.content，调用 predict_dual_bert 批量推理后回写情感/意图等分析列。
【效果】表中 sentiment_label、intent_label、bert_version、analyzed_at 等被填充，便于统计与前端展示。

将站内 review 表的 content 用本地双 BERT（意图 + 情感）批量打标，回写：
  sentiment_label, intent_label, bert_version, analyzed_at

为何库里这四列为空：建表后若未跑本脚本（也未在后端接入实时推理），则一直为 NULL。

依赖（与 predict_dual_bert 一致）:
  pip install torch transformers pymysql

环境变量（与 XGBOOST/export_orders_from_mysql.py 一致）:
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE

示例（在 BERT 目录下，且已放好 训练成果 模型）:
  python batch_backfill_review_bert_mysql.py --dry-run
  python batch_backfill_review_bert_mysql.py --limit 500
"""

from __future__ import annotations

import argparse
import os
import sys
from datetime import datetime
from pathlib import Path

import torch

try:
    import pymysql
except ImportError as e:
    raise SystemExit("请先安装: pip install pymysql") from e

# 保证可导入同目录下的 predict_dual_bert
BERT_DIR = Path(__file__).resolve().parent
if str(BERT_DIR) not in sys.path:
    sys.path.insert(0, str(BERT_DIR))

from predict_dual_bert import load_model, predict_dual_labels  # noqa: E402


SELECT_EMPTY = """
SELECT review_id, content
FROM review
WHERE IFNULL(status, 1) = 1
  AND content IS NOT NULL
  AND TRIM(content) <> ''
  AND (
    sentiment_label IS NULL
    OR intent_label IS NULL
    OR bert_version IS NULL
    OR analyzed_at IS NULL
  )
ORDER BY review_id
"""

SELECT_ALL = """
SELECT review_id, content
FROM review
WHERE IFNULL(status, 1) = 1
  AND content IS NOT NULL
  AND TRIM(content) <> ''
ORDER BY review_id
"""

UPDATE_SQL = """
UPDATE review
SET sentiment_label = %s,
    intent_label = %s,
    bert_version = %s,
    analyzed_at = %s
WHERE review_id = %s
"""


def build_bert_version(intent_dir: str, sentiment_dir: str) -> str:
    a = Path(intent_dir).name
    b = Path(sentiment_dir).name
    s = f"{a}+{b}"
    # 与 data.sql 中 review.bert_version VARCHAR(64) 一致
    return s if len(s) <= 64 else s[:64]


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="批量 BERT 分析评论并回写 MySQL review 表")
    p.add_argument(
        "--intent_model_dir",
        type=str,
        default="训练成果/bert_intent_cat_dog_product_gpu_v2",
        help="相对 BERT 目录的意图模型路径",
    )
    p.add_argument(
        "--sentiment_model_dir",
        type=str,
        default="训练成果/bert_sentiment_cat_dog_product_gpu_v2_e2",
        help="相对 BERT 目录的情感模型路径",
    )
    p.add_argument("--max_length", type=int, default=64)
    p.add_argument("--young_pet_boost", type=float, default=0.5)
    p.add_argument("--limit", type=int, default=0, help="最多处理条数，0 表示不限制")
    p.add_argument("--batch_commit", type=int, default=50, help="每 N 条提交一次")
    p.add_argument("--dry-run", action="store_true", help="只打印前几条预测，不写库")
    p.add_argument(
        "--all",
        action="store_true",
        help="重算所有有效评论（默认只处理四列仍为空缺的行）",
    )
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
    intent_path = (BERT_DIR / args.intent_model_dir).resolve()
    sentiment_path = (BERT_DIR / args.sentiment_model_dir).resolve()
    if not intent_path.is_dir() or not (intent_path / "config.json").exists():
        raise SystemExit(f"意图模型目录无效: {intent_path}")
    if not sentiment_path.is_dir() or not (sentiment_path / "config.json").exists():
        raise SystemExit(f"情感模型目录无效: {sentiment_path}")

    bert_version = build_bert_version(str(intent_path), str(sentiment_path))
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"设备: {device}  bert_version: {bert_version}")

    intent_tok, intent_model, intent_map = load_model(str(intent_path))
    sent_tok, sent_model, sent_map = load_model(str(sentiment_path))
    intent_model.to(device).eval()
    sent_model.to(device).eval()
    intent_pack = (intent_tok, intent_model, intent_map)
    sentiment_pack = (sent_tok, sent_model, sent_map)

    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )
    sql = SELECT_ALL if args.all else SELECT_EMPTY
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
            rows = cur.fetchall()
    finally:
        pass

    if args.limit and args.limit > 0:
        rows = rows[: args.limit]

    print(f"待处理评论数: {len(rows)}")
    if not rows:
        conn.close()
        return

    if args.dry_run:
        for rid, content in rows[:5]:
            text = str(content)[:200]
            pred = predict_dual_labels(
                str(content),
                intent_pack,
                sentiment_pack,
                device,
                args.max_length,
                args.young_pet_boost,
            )
            print(
                f"[dry-run] id={rid} intent={pred['intent_label']} sent={pred['sentiment_label']} "
                f"snippet={text!r}..."
            )
        conn.close()
        return

    done = 0
    try:
        with conn.cursor() as cur:
            for rid, content in rows:
                pred = predict_dual_labels(
                    str(content),
                    intent_pack,
                    sentiment_pack,
                    device,
                    args.max_length,
                    args.young_pet_boost,
                )
                at = datetime.now()
                cur.execute(
                    UPDATE_SQL,
                    (
                        str(pred["sentiment_label"]),
                        str(pred["intent_label"]),
                        bert_version,
                        at,
                        int(rid),
                    ),
                )
                done += 1
                if done % max(1, args.batch_commit) == 0:
                    conn.commit()
                    print(f"已提交 {done} 条…")
        conn.commit()
        print(f"完成，共更新 {done} 条。")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
