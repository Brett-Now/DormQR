# 还原手表上被本工具改动过的系统设置。
#
# 曾用 adb 改过：
#   1) settings put system lift_wrist_switch 0
#   2) global aod_support_apps / aod_switch_apps 追加 app.dormqr.qr
#
# 原始值的备份在 docs/.toolchain/aod_*_backup.txt。
# 想恢复手表原行为，指定备份目录运行本脚本即可。

param(
    [string]$Serial = '',
    [string]$BackupDir = '',
    [switch]$LiftWristOnly
)

$ErrorActionPreference = 'Stop'

if (-not $BackupDir) {
    # 默认位置：仓库根/docs/.toolchain
    $root = Split-Path -Parent $PSScriptRoot
    $BackupDir = Join-Path $root 'docs\.toolchain'
}

function Adb {
    param([string[]]$Args)
    if ($Serial) { & adb -s $Serial @Args } else { & adb @Args }
}

Write-Host "备份目录：$BackupDir" -ForegroundColor DarkGray

# 1) 抬腕开关恢复为开启
Adb @('shell', 'settings put system lift_wrist_switch 1')
Write-Host ("lift_wrist_switch = " + (Adb @('shell', 'settings get system lift_wrist_switch')))
if ($LiftWristOnly) { exit 0 }

# 2) AOD 白名单按备份原值写回
$pairs = @(
    @{ File = Join-Path $BackupDir 'aod_support_backup.txt'; Key = 'aod_support_apps' },
    @{ File = Join-Path $BackupDir 'aod_switch_backup.txt';  Key = 'aod_switch_apps'  }
)

foreach ($pair in $pairs) {
    if (-not (Test-Path $pair.File)) {
        Write-Warning "缺少备份：$($pair.File)，跳过 $($pair.Key)"
        continue
    }
    $value = (Get-Content $pair.File -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Warning "$($pair.Key) 备份为空，跳过"
        continue
    }

    $tmp = Join-Path $env:TEMP ("dormqr_" + $pair.Key + ".txt")
    Set-Content -Path $tmp -Value $value -NoNewline -Encoding ascii
    Adb @('push', $tmp, "/data/local/tmp/$($pair.Key).txt") | Out-Null
    Adb @('shell', "settings put global $($pair.Key) `"`$(cat /data/local/tmp/$($pair.Key).txt)`"")
    Write-Host "    已还原 $($pair.Key)"
}

Write-Host ""
Write-Host "完成。建议重启一次手表让系统重新读取这些设置。" -ForegroundColor Green
