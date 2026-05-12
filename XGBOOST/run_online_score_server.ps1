# Usage:
#   .\run_online_score_server.ps1
#   .\run_online_score_server.ps1 -Port 8766
#   .\run_online_score_server.ps1 -KillExisting   # stop whatever is listening on the chosen port, then start
param(
    [int] $Port = 8765,
    [switch] $KillExisting
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

if ($env:ONLINE_SCORE_PORT -match "^\d+$") {
    $Port = [int]$env:ONLINE_SCORE_PORT
}

# 解析 Python：环境变量 > BERT\.venv311 > 系统/conda 的 py -3
$venvPy = Join-Path $here "..\BERT\.venv311\Scripts\python.exe"
$py = $null
$pyIsLauncher = $false
if ($env:ONLINE_SCORE_PYTHON -and (Test-Path $env:ONLINE_SCORE_PYTHON)) {
    $py = $env:ONLINE_SCORE_PYTHON
}
elseif (Test-Path $venvPy) {
    $py = $venvPy
}
elseif (Get-Command py -ErrorAction SilentlyContinue) {
    $pyIsLauncher = $true
}
else {
    Write-Error @"
未找到可用的 Python 解释器。
  1) 安装 Python 并确保 PATH 中有 py 启动器，或
  2) 创建虚拟环境: cd ..\BERT 后执行 py -3 -m venv .venv311
  3) 或设置环境变量 ONLINE_SCORE_PYTHON 指向 python.exe 的完整路径
原先硬编码路径已不存在: $venvPy
"@
}

if (-not (Test-Path (Join-Path $here "models\xgb_user_item_demo\pipeline.joblib"))) {
    Write-Host "Training demo model (pipeline.joblib)..."
    if ($pyIsLauncher) {
        & py -3 (Join-Path $here "train_xgboost_user_item.py") --data (Join-Path $here "data\user_item_train_demo.csv") --output_dir (Join-Path $here "models\xgb_user_item_demo")
    }
    else {
        & $py (Join-Path $here "train_xgboost_user_item.py") --data (Join-Path $here "data\user_item_train_demo.csv") --output_dir (Join-Path $here "models\xgb_user_item_demo")
    }
}

$listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
if ($listeners) {
    if ($KillExisting) {
        foreach ($procId in $listeners) {
            Write-Host "Stopping PID $procId on port $Port"
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Milliseconds 500
    }
    else {
        Write-Host "Port $Port is already in use (PID: $($listeners -join ', '))."
        Write-Host "Close that process, or run: .\run_online_score_server.ps1 -Port 8766 -KillExisting"
        Write-Host "If you change port, set backend: app.recommendation.online-inference-base-url=http://127.0.0.1:$Port"
        exit 1
    }
}

$env:MODEL_DIR = (Join-Path $here "models\xgb_user_item_demo")
$env:PRODUCT_FEATURES_CSV = (Join-Path $here "data\product_features_agg.csv")
$env:MODEL_VERSION = "xgb_online_v1"

Write-Host "Online score API: http://127.0.0.1:$Port/health"
Write-Host "MODEL_DIR=$($env:MODEL_DIR)"
if ($pyIsLauncher) {
    & py -3 -m uvicorn online_score_server:app --host 127.0.0.1 --port $Port
}
else {
    & $py -m uvicorn online_score_server:app --host 127.0.0.1 --port $Port
}
