# 评论数据处理与快速实验

本目录用于：把采集或保存的评论转成可训练 CSV，并可一键跑通「清洗 → 规则初标 → 训练」。

## 目录建议

- `pages/`：存放从浏览器保存的评论接口响应 JSON（`page_*.json`），需包含 `commentlist-list` 楼层结构。
- `extract_from_pages.py`：从上述 JSON 提取原始评论 → `comments_raw.csv`
- `crawl_douban_comments.py`：豆瓣图书短评采集 → 默认 `douban_comments_raw.csv`
- `build_train_csv.py`：清洗原始评论并导出待标注文件

## 方式 A：已保存的接口响应 JSON

1. 在目标页打开开发者工具，找到评论列表接口的响应体（含 `commentData` 等字段）。
2. 每翻一页保存一份响应到 `pages/page_XXX.json`。
3. 在 `BERT/爬虫` 目录执行：

```powershell
python extract_from_pages.py --pages_dir pages --output comments_raw.csv
```

输出字段：`product_id`, `comment_id`, `score`, `date`, `text`。

## 方式 B：豆瓣图书短评

```powershell
python crawl_douban_comments.py --book "https://book.douban.com/subject/你的ID/comments/" --max_pages 10 --output douban_comments_raw.csv
```

输出需含 `text` 列，后续与方式 A 共用清洗脚本。

## 清洗并生成待标注文件

```powershell
python build_train_csv.py --input comments_raw.csv --output pet_intent_todo.csv
```

若使用豆瓣数据，将 `--input` 改为 `douban_comments_raw.csv`。

默认会：按 `comment_id`（若有）与 `text` 去重、去空、去掉过短文本（默认少于 6 字）。

输出：`text` + 空 `label`（人工填写）。

## 人工标注后训练

把 `pet_intent_todo.csv` 的 `label` 填好后，另存为 `pet_intent_data.csv` 放到 `BERT` 根目录：

```powershell
cd ..
python train_pet_bert.py --epochs 3 --batch_size 8 --output_dir pet_bert_model
```

## 建议标签

- `物流发货`
- `价格优惠`
- `售后退换`
- `商品咨询`
- `喂养咨询`

---

## 一键快跑（规则初标 + 训练）

先看效果、不全手工标时可用：

```powershell
python quick_run.py --pages_dir pages --epochs 2 --batch_size 8 --output_dir pet_bert_model_quick
```

逻辑简述：

1. 若存在 `pages/*.json`：提取 → `comments_raw.csv`；否则若已有 `comments_raw.csv` 或 `douban_comments_raw.csv` 则直接使用。
2. 清洗 → `pet_intent_todo.csv`
3. 规则初标注 → `pet_intent_auto.csv`
4. 训练 → `../pet_bert_model_quick`（相对 BERT 根目录）
5. 运行两条示例预测

说明：快速实验路径；正式使用建议在 `pet_intent_todo.csv` 上人工复核后再训练。
