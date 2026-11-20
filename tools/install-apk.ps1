# 一键安装到已连接的手机
param([string]$Apk = '', [switch]$Launch = $true)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $Apk) {
    # 不给参数时，挑 build\apk 里最新的成品包（文件名带版本号，逐版递增）
    $candidate = Get-ChildItem (Join-Path $root 'build\apk') -Filter '*.apk' -ErrorAction SilentlyContinue |
                 Where-Object { $_.Name -notmatch '^(base|aligned|unsigned)' } |
                 Sort-Object LastWriteTime -Descending |
                 Select-Object -First 1
    if (-not $candidate) { throw 'build\apk 下没有 APK，先跑 tools\build-apk.ps1' }
    $Apk = $candidate.FullName
}

if (-not (Test-Path $Apk)) { throw "找不到 APK：$Apk，先跑 tools\build-apk.ps1" }

$devices = (adb devices) | Select-String 'device$'
if (-not $devices) {
    Write-Host "没有检测到设备。请先：1) 手机开启开发者选项+USB调试 2) 数据线连接 3) 手机上点允许调试" -ForegroundColor Yellow
    adb devices
    exit 1
}

Write-Host "设备：" -NoNewline; Write-Host ($devices -join ', ')
adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'adb install 失败' }

if ($Launch) {
    adb shell am start -n app.dormqr.qr/.MainActivity
}
Write-Host "完成" -ForegroundColor Green
