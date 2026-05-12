# 全量重训 + 写库：评论/订单数据有更新时跑（建议每周或按需；耗时长）
# 任务计划程序可单独建「每周日凌晨」任务指向本脚本。
#
# 顺序：aggregate_product_features -> bert_comment_embed_pca_aggregate（可选）->
#       simulate_orders -> build_user_item_train -> train_xgboost_user_item ->
#       generate_user_recommendations -> write_recommendations_mysql
# 按需注释掉不需要的步骤（例如不做 PCA 时跳过 bert_comment 行）。

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$Python = Join-Path $Root "..\BERT\.venv311\Scripts\python.exe"
if (-not (Test-Path $Python)) { $Python = "python" }

$env:MYSQL_DATABASE = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "pet_shop" }
if (-not $env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD = "trq123549" }

function Run-Step([string[]]$ArgList) {
  Write-Host ">> $($ArgList -join ' ')" -ForegroundColor Yellow
  & $Python @ArgList
  if ($LASTEXITCODE -ne 0) { throw "命令失败: $ArgList" }
}

Run-Step @("aggregate_product_features.py")
# 若已含 bert_pca 列且脚本环境有 torch，取消下一行注释：
# Run @("bert_comment_embed_pca_aggregate.py", "--n_pca", "64")

Run-Step @("simulate_orders.py", "--num_users", "120", "--min_orders", "1", "--max_orders", "8", "--output", "data\orders_user_product_simulated.csv")

$Feat = "data\product_features_agg_with_bert_pca.csv"
if (-not (Test-Path (Join-Path $Root $Feat))) { $Feat = "data\product_features_agg.csv" }

Run-Step @("build_user_item_train.py", "--orders_csv", "data\orders_user_product_simulated.csv", "--product_features", $Feat)
Run-Step @("train_xgboost_user_item.py", "--data", "data\user_item_train.csv", "--output_dir", "models\xgb_user_item_v2")

Run-Step @(
  "generate_user_recommendations.py",
  "--model_dir", "models\xgb_user_item_v2",
  "--orders_csv", "data\orders_user_product_simulated.csv",
  "--product_features", $Feat,
  "--comment_model_dir", "models\xgb_comment_ranker",
  "--comment_features_csv", "data\comment_features.csv",
  "--hybrid_alpha", "0.65",
  "--topn", "20",
  "--model_version", "xgb_hybrid_v1",
  "--output", "outputs\user_recommendations_hybrid.csv"
)

Run-Step @("write_recommendations_mysql.py", "--csv", "outputs\user_recommendations_hybrid.csv", "--replace_model_version")
Write-Host "全量流水线完成。" -ForegroundColor Green
