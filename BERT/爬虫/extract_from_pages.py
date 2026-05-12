"""
【作用】解析从商品页复制的 JSON/拼接 JSON，抽取评论字段并落盘为原始评论 CSV。
【效果】得到含评论正文、评分等列的中间文件，供 build_train_csv、build_sentiment_data 等后续步骤使用。
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


def parse_json_objects(raw: str) -> list[dict]:
    """
    兼容两种输入：
    1) 标准单个 JSON 对象
    2) 一键复制后多个 JSON 对象直接拼接（如: {}{}）
    """
    decoder = json.JSONDecoder()
    n = len(raw)
    idx = 0
    objs: list[dict] = []

    while idx < n:
        # 跳过空白与常见分隔符
        while idx < n and raw[idx] in " \t\r\n,":
            idx += 1
        if idx >= n:
            break

        try:
            obj, end = decoder.raw_decode(raw, idx)
        except json.JSONDecodeError:
            # 找下一个可能的对象起点，尽量继续解析
            next_idx = raw.find("{", idx + 1)
            if next_idx == -1:
                break
            idx = next_idx
            continue

        if isinstance(obj, dict):
            objs.append(obj)
        idx = end

    return objs


def extract_rows_from_obj(obj: dict) -> list[dict]:
    floors = obj.get("result", {}).get("floors", [])
    comment_floor = None
    for floor in floors:
        if floor.get("mId") == "commentlist-list":
            comment_floor = floor
            break
    if not comment_floor:
        return []

    rows: list[dict] = []
    for item in comment_floor.get("data", []):
        c = item.get("commentInfo", {})
        text = (c.get("commentData") or c.get("tagCommentContent") or "").strip()
        if not text:
            continue
        rows.append(
            {
                "product_id": c.get("productId", ""),
                "comment_id": c.get("commentId", ""),
                "score": c.get("commentScore", ""),
                "date": c.get("commentDate", ""),
                "text": text,
            }
        )
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(
        description="从 page_*.json 批量提取评论（需含 commentlist-list 楼层的接口响应）"
    )
    parser.add_argument("--pages_dir", type=str, default="pages")
    parser.add_argument("--output", type=str, default="comments_raw.csv")
    args = parser.parse_args()

    pages_dir = Path(args.pages_dir)
    json_files = sorted(pages_dir.glob("*.json"))
    if not json_files:
        raise FileNotFoundError(f"未找到 JSON 文件: {pages_dir.resolve()}")

    rows: list[dict] = []
    for fp in json_files:
        try:
            raw = fp.read_text(encoding="utf-8")
            objs = parse_json_objects(raw)
            if not objs:
                raise ValueError("未解析到任何 JSON 对象")
            for obj in objs:
                rows.extend(extract_rows_from_obj(obj))
        except Exception as e:
            print(f"[WARN] 读取失败: {fp.name} -> {e}")

    # 去重：优先 comment_id，其次 text
    dedup: list[dict] = []
    seen_id: set[str] = set()
    seen_text: set[str] = set()
    for r in rows:
        cid = str(r.get("comment_id", "")).strip()
        txt = str(r.get("text", "")).strip()
        if cid and cid in seen_id:
            continue
        if txt in seen_text:
            continue
        if cid:
            seen_id.add(cid)
        seen_text.add(txt)
        dedup.append(r)

    with open(args.output, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["product_id", "comment_id", "score", "date", "text"],
        )
        writer.writeheader()
        writer.writerows(dedup)

    print(f"输入文件: {len(json_files)}")
    print(f"提取条数: {len(rows)}")
    print(f"去重后条数: {len(dedup)}")
    print(f"输出文件: {Path(args.output).resolve()}")


if __name__ == "__main__":
    main()

