# 演示用：每次被后端拉起时执行 — 换随机种子重算模拟订单 → 推理推荐 → 写库（模型不变，列表会变）
# 由 Spring Boot 配置 app.demo.refresh-recommendations-script 指向本文件。

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$Python = Join-Path $Root "..\BERT\.venv311\Scripts\python.exe"
if (-not (Test-Path $Python)) { $Python = "python" }

$env:MYSQL_HOST = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "127.0.0.1" }
$env:MYSQL_PORT = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }
$env:MYSQL_USER = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }
if (-not $env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD = "trq123549" }
$env:MYSQL_DATABASE = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "pet_shop" }

# 兼容 Windows PowerShell 5.1：用时间与随机混合种子
$seed = ([int]([DateTime]::UtcNow.Ticks % 2000000000)) -bxor (Get-Random -Minimum 1 -Maximum 1000000000)
if ($seed -le 0) { $seed = (Get-Random -Minimum 1 -Maximum 2000000000) }
Write-Host "[demo-refresh] seed=$seed" -ForegroundColor Cyan

& $Python @(
  "simulate_orders.py",
  "--num_users", "120",
  "--min_orders", "1",
  "--max_orders", "8",
  "--output", "data\orders_user_product_simulated.csv",
  "--seed", "$seed"
)

$Feat = "data\product_features_agg_with_bert_pca.csv"
if (-not (Test-Path (Join-Path $Root $Feat))) { $Feat = "data\product_features_agg.csv" }

& $Python @(
  "generate_user_recommendations.py",
  "--model_dir", "models\xgb_user_item_v2",
  "--orders_csv", "data\orders_user_product_simulated.csv",
  "--product_features", $Feat,
  "--topn", "20",
  "--comment_model_dir", "models\xgb_comment_ranker",
  "--comment_features_csv", "data\comment_features.csv",
  "--hybrid_alpha", "0.65",
  "--model_version", "xgb_hybrid_v1",
  "--output", "outputs\user_recommendations_hybrid.csv"
)

& $Python @("write_recommendations_mysql.py", "--csv", "outputs\user_recommendations_hybrid.csv", "--replace_model_version")
Write-Host "[demo-refresh] 完成" -ForegroundColor Green
