# XGBoost 推荐（评论级 + 用户级）

目录说明：

- **评论级模型**：无 `user_id` 时，用评论特征训练「好评倾向」，再按商品聚合排序（`train_xgboost.py` + `recommend_items.py`）。
- **用户级模型**：用 **CSV 构造** 的「用户–已购商品」正样本（无真实订单、虚拟商品亦可），训练个性化模型；推荐结果可写入 `user_product_recommendation` 供首页接口读取。

## 环境

```bash
cd "E:\毕业设计\毕业设计_模型训练\XGBOOST"
..\BERT\.venv311\Scripts\python.exe -m pip install -r requirements.txt
```

---

## A. 评论级（已有流程）

1. 构建评论特征：`build_comment_features.py` → `data/comment_features.csv`
2. 训练：`train_xgboost.py` → `models/xgb_comment_ranker/`
3. 商品榜单：`recommend_items.py` → `outputs/topn_recommendations.csv`

---

## B. 用户级（与后端 `/api/recommendations/user/{userId}` 对接）

**毕设常见设定**：无真实订单、商品与评论均为虚拟数据。此时**不必**连接订单表、不必从库导出「购买记录」；在本地用 CSV 模拟「谁买过什么」即可，与后续特征表同一套 `product_id`。

### 1) 聚合商品特征（先有 `product_id` 池）

```bash
..\BERT\.venv311\Scripts\python.exe aggregate_product_features.py
```

输出：`data/product_features_agg.csv`（来自 `comment_features.csv` 中的商品，与评论管线一致）。

### 1a) （可选）BERT 句向量 + PCA 并入商品特征

对每条评论的 `text` 用预训练 BERT Encoder 取 **[CLS] 768 维**，在全体评论上 **拟合 PCA**（默认降到 64 维），再按 `product_id` **均值聚合**，合并进上一步的聚合表，列名为 `bert_pca_0` … `bert_pca_{K-1}`。

依赖 `torch`、`transformers`、`tqdm`（可用 BERT 目录下同一虚拟环境）：

```bash
..\BERT\.venv311\Scripts\python.exe bert_comment_embed_pca_aggregate.py ^
  --comment_features "data/comment_features.csv" ^
  --agg_csv "data/product_features_agg.csv" ^
  --output "data/product_features_agg_with_bert_pca.csv" ^
  --n_pca 64
```

- **默认离线**：不访问 HuggingFace；自动依次尝试「本机 HF 缓存里的 bert-base-chinese」→「`BERT/训练成果/` 下含权重的子目录」。若均失败，请用 **`--bert_model "你的\\完整\\权重目录"`**（须含 `config.json` 与 `model.safetensors` 或 `pytorch_model.bin`）。  
- 必须联网下载时：可先设镜像 `$env:HF_ENDPOINT='https://hf-mirror.com'`，再加 **`--allow_download`**。  
- 使用 Hub 名 **`bert-base-chinese`** 的 Encoder（与分类头独立，仅取 `BertModel` 做 [CLS] 向量）。  
- 产出：`models/pca_bert_comment.joblib`（含 `sklearn` 的 PCA 对象）及同名 `.json` 元信息。  
- **之后** `build_user_item_train`、`train_xgboost_user_item`、`generate_user_recommendations` 中的 **`--product_features` 一律改为** `--output` 指向的 CSV；用户级模型需 **重新训练** 才能利用新列。

### 2) 纯 CSV 模拟「订单」正样本（`user_id`, `product_id`）

从聚合表里的 `product_id` 抽样，合成用户 `1..N` 的购买关系，写出两列 CSV（默认按 `user_id`, `product_id` 排序）：

```bash
..\BERT\.venv311\Scripts\python.exe simulate_orders.py --num_users 120 --min_orders 1 --max_orders 8 --output "data/orders_user_product_simulated.csv"
```

后续命令里 **`--orders_csv` 一律指该文件**（或你自定义的输出路径）。小样本可参考 `data/orders_user_product.example.csv`。

**可选（仅当你坚持与业务库主键对齐时）**：`simulate_orders.py` 支持 `--user_ids_csv` / `--product_ids_csv`；虚拟数据课题下通常不需要。

**若将来有真实订单**：在库中执行 `sql/export_for_xgboost.sql` 导出同列 CSV 即可；`sql/seed_simulated_orders.sql` + `export_orders_from_mysql.py` 为可选工程手段，与「直接 CSV 模拟」二选一即可。

### 3) 构建训练集（正样本 + 负采样）

```bash
..\BERT\.venv311\Scripts\python.exe build_user_item_train.py ^
  --orders_csv "data/orders_user_product_simulated.csv" ^
  --product_features "data/product_features_agg.csv" ^
  --products_csv "data/products_export.csv"
```

说明：`--products_csv` 可选；若训练时带了 `price/stock` 等列，推理阶段 `generate_user_recommendations.py` 也需传同结构 CSV。若已做 **1a（BERT+PCA）**，请将本步与第 5 步中的 `--product_features` 改为 `data/product_features_agg_with_bert_pca.csv`。

输出：`data/user_item_train.csv`

### 4) 训练用户级 XGBoost

```bash
..\BERT\.venv311\Scripts\python.exe train_xgboost_user_item.py ^
  --data "data/user_item_train.csv" ^
  --output_dir "models/xgb_user_item_v1"
```

输出：`models/xgb_user_item_v1/pipeline.joblib`、`feature_meta.json`

### 5) 生成每个用户的 TopN 推荐（CSV）

```bash
..\BERT\.venv311\Scripts\python.exe generate_user_recommendations.py ^
  --model_dir "models/xgb_user_item_v1" ^
  --orders_csv "data/orders_user_product_simulated.csv" ^
  --product_features "data/product_features_agg.csv" ^
  --topn 20 ^
  --model_version "xgb_user_item_v1" ^
  --output "outputs/user_recommendations.csv"
```

### 6) 写入 MySQL（首页读取）

设置环境变量或使用参数：

- `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_DATABASE`（脚本默认 **`pet_shop`**，与后端 `application.properties` 一致）

```bash
..\BERT\.venv311\Scripts\python.exe write_recommendations_mysql.py ^
  --csv "outputs/user_recommendations.csv" ^
  --replace_model_version
```

写入表：`user_product_recommendation`（与 `数据库/data.sql` 中定义一致）。

---

## C. BERT 评论链路与用户级 XGBoost 混合（推理融合）

**数据流**：BERT 管线产出意图/情感等 → `comment_features.csv` → **评论级** `train_xgboost.py`（`models/xgb_comment_ranker`）对「每条评论」打好评倾向分；用户级 `train_xgboost_user_item.py` 用聚合特征 + 模拟订单做「用户–商品」分。

**混合方式（推荐）**：在生成推荐时做**分数融合**（不必重训用户级模型）：

1. 确保已训练评论级模型：`train_xgboost.py` → `models/xgb_comment_ranker/`  
2. 生成推荐时增加参数（`alpha` 越大越信用户级，越小越信评论侧商品口碑）：

```bash
..\BERT\.venv311\Scripts\python.exe generate_user_recommendations.py ^
  --model_dir "models/xgb_user_item_v1" ^
  --orders_csv "data/orders_user_product_simulated.csv" ^
  --product_features "data/product_features_agg.csv" ^
  --comment_model_dir "models/xgb_comment_ranker" ^
  --comment_features_csv "data/comment_features.csv" ^
  --hybrid_alpha 0.65 ^
  --topn 20 ^
  --model_version "xgb_hybrid_v1" ^
  --output "outputs/user_recommendations_hybrid.csv"
```

输出 CSV 的 `reason_json` 中会包含 `score_u2i`、`score_comment_xgb` 与 `alpha`，便于写论文说明融合策略。再按需执行 `write_recommendations_mysql.py` 写入库（`model_version` 与前端展示可区分）。

---

## 与 BERT 的关系

- 评论侧意图/情感已进入 `comment_features.csv`，聚合后进入 `product_features_agg.csv`，作为用户级 XGBoost **特征**的一部分。
- **BERT 向量 + PCA**：见 **B.1a**（`bert_comment_embed_pca_aggregate.py`），将 `bert_pca_*` 列并入商品表后需 **重训用户级模型**。
- **评论级 XGBoost** 与 **用户级 XGBoost** 的显式融合见上节 **C**（加权排序）；可与 1a 同时使用（语义稠密特征 + 分数融合）。
