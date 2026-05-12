# 轻量刷新：在「模型与训练数据 CSV 不变」时，定期重算推荐并写库（适合每日/每小时）
# 任务计划程序：操作 → 启动程序 powershell.exe，参数：-NoProfile -ExecutionPolicy Bypass -File "E:\毕业设计\毕业设计_模型训练\XGBOOST\scripts\refresh_recommendations.ps1"
#
# 请先按本机修改下列路径与 MySQL 密码（或改为从环境变量读取）。

$ErrorActionPreference = "Stop"
# 本脚本在 XGBOOST\scripts 下，工作目录为 XGBOOST
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$Python = Join-Path $Root "..\BERT\.venv311\Scripts\python.exe"
if (-not (Test-Path $Python)) { $Python = "python" }

# MySQL（与后端 application.properties 一致）
$env:MYSQL_HOST = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "127.0.0.1" }
$env:MYSQL_PORT = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }
$env:MYSQL_USER = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }
if (-not $env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD = "trq123549" }
$env:MYSQL_DATABASE = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "pet_shop" }

$ModelDir = "models\xgb_user_item_v2"
$Orders = "data\orders_user_product_simulated.csv"
$Feat = "data\product_features_agg_with_bert_pca.csv"
$OutCsv = "outputs\user_recommendations_hybrid.csv"
$GenArgs = @(
  "generate_user_recommendations.py",
  "--model_dir", $ModelDir,
  "--orders_csv", $Orders,
  "--product_features", $Feat,
  "--topn", "20",
  "--comment_model_dir", "models\xgb_comment_ranker",
  "--comment_features_csv", "data\comment_features.csv",
  "--hybrid_alpha", "0.65",
  "--model_version", "xgb_hybrid_v1",
  "--output", $OutCsv
)

Write-Host "== generate_user_recommendations ==" -ForegroundColor Cyan
& $Python @GenArgs

Write-Host "== write_recommendations_mysql ==" -ForegroundColor Cyan
& $Python "write_recommendations_mysql.py" "--csv", $OutCsv, "--replace_model_version"

Write-Host "完成: $OutCsv -> pet_shop.user_product_recommendation" -ForegroundColor Green
