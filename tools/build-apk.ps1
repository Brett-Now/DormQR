# 不依赖 Gradle / Maven 的构建脚本：aapt2 + javac + d8 + zipalign + apksigner
# 目的：在只有 android.jar 和 build-tools 的机器上也能产出可安装的 APK。
# 如果装了 Android Studio，直接用 Studio 打开本工程即可，无需此脚本。

param(
    [string]$Sdk       = 'D:\Android\android-sdk',
    [string]$BuildTools = '34.0.0',
    [string]$Jdk       = 'C:\Program Files\Java\jdk-17',
    [switch]$Release
)

$ErrorActionPreference = 'Stop'
$ErrorActionPreference = 'Stop'

# 版本号：改版本时只动这两行，产物名与包内信息同步
$versionCode = 3
$versionName = '1.2'
$root    = Split-Path -Parent $PSScriptRoot
$app     = Join-Path $root 'app'
$main    = Join-Path $app 'src\main'
$bt      = Join-Path $Sdk "build-tools\$BuildTools"
$androidJar = Join-Path $Sdk 'platforms\android-35\android.jar'
$out     = Join-Path $root 'build\apk'
$gen     = Join-Path $root 'build\gen'
$obj     = Join-Path $root 'build\obj'
$dex     = Join-Path $root 'build\dex'

foreach ($p in @($out, $gen, $obj, $dex)) { New-Item -ItemType Directory -Force -Path $p | Out-Null }

# 每次构建先清空中间产物：
# 否则已删除源文件的 .class 会残留，被 jar 进 dex（曾导致 APK 虚增 7KB）
foreach ($p in @($gen, $obj, $dex)) {
    if (Test-Path $p) { Remove-Item $p -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $p | Out-Null
}

function Step($name) { Write-Host "==> $name" -ForegroundColor Cyan }

# 0. 检查
foreach ($t in @("$bt\aapt2.exe", "$bt\d8.bat", "$bt\zipalign.exe", "$bt\apksigner.bat", $androidJar,
                 "$Jdk\bin\javac.exe", "$Jdk\bin\keytool.exe")) {
    if (-not (Test-Path $t)) { throw "缺少构建工具：$t" }
}

# 1. 资源编译
Step "aapt2 compile (res)"
$flat = Join-Path $obj 'res.zip'
& "$bt\aapt2.exe" compile --dir "$main\res" -o $flat
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile 失败' }

# 2. 资源链接 + 生成 R.java + 基础 APK
Step "aapt2 link (manifest + resources)"
$baseApk = Join-Path $out 'base.apk'

# Release 用干净清单；Debug 在临时副本里注入 debuggable=true（便于 run-as 调试）
$manifestToUse = "$main\AndroidManifest.xml"
if (-not $Release) {
    $manifestToUse = Join-Path $obj 'AndroidManifest.debug.xml'
    $raw = Get-Content "$main\AndroidManifest.xml" -Raw
    $raw = $raw.Replace('<application', '<application' + "`r`n" + '        android:debuggable="true"')
    Set-Content -Path $manifestToUse -Value $raw -Encoding utf8
    Write-Host "    Debug 构建：已注入 debuggable=true"
} else {
    Write-Host "    Release 构建：清单不含 debuggable"
}

& "$bt\aapt2.exe" link -o $baseApk `
    -I $androidJar `
    --manifest "$manifestToUse" `
    --java $gen `
    --min-sdk-version 26 `
    --target-sdk-version 35 `
    --version-code $versionCode --version-name $versionName `
    $flat
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link 失败' }

# 3. 编译 Java
Step "javac"
$sources = @(Get-ChildItem -Path (Join-Path $main 'java') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$rJava   = @(Get-ChildItem -Path $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-Host "    java 源文件 $($sources.Count) 个，R.java $($rJava.Count) 个"

$classes = Join-Path $obj 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null

# 用 argfile 传参，避开 PowerShell 对长列表的转义问题
# javac 的 argfile 里反斜杠是转义符，统一换成正斜杠
function Fix-Path([string]$p) { $p.Replace('\', '/') }

$argFile = Join-Path $obj 'javac-args.txt'
$lines = @('"-encoding" "UTF-8"')
$lines += '"-source" "1.8"'
$lines += '"-target" "1.8"'
$lines += '-nowarn'
$lines += ('"-bootclasspath" "{0}"' -f (Fix-Path $androidJar))
$lines += ('"-classpath" "{0}"' -f (Fix-Path $androidJar))
$lines += ('"-d" "{0}"' -f (Fix-Path $classes))
$lines += ($sources + $rJava | ForEach-Object { '"{0}"' -f (Fix-Path $_) })
Set-Content -Path $argFile -Value $lines -Encoding utf8

& "$Jdk\bin\javac.exe" "@$argFile"
if ($LASTEXITCODE -ne 0) { throw 'javac 失败' }

# 4. dex（先打 jar：类文件多了直接传路径会超过命令行长度上限）
Step "d8"
$classesJar = Join-Path $obj 'classes.jar'
if (Test-Path $classesJar) { Remove-Item $classesJar -Force }
& "$Jdk\bin\jar.exe" cf $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar 打包失败' }

$d8Args = @('--lib', $androidJar, '--min-api', '26', '--output', $dex)
if ($Release) { $d8Args += '--release' }
$d8Args += $classesJar
& "$bt\d8.bat" @d8Args
if ($LASTEXITCODE -ne 0) { throw 'd8 失败' }

# 5. 把 classes.dex 塞进 APK
Step "打包 classes.dex"
$unsigned = Join-Path $out 'unsigned.apk'
Copy-Item $baseApk $unsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
$dexFile = Join-Path $dex 'classes.dex'
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
    $zip, $dexFile, 'classes.dex', [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()

# 6. 对齐
Step "zipalign"
$aligned = Join-Path $out 'aligned.apk'
& "$bt\zipalign.exe" -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign 失败' }

# 7. 签名
Step "apksigner"
$ks = Join-Path $root 'build\dormqr-debug.keystore'
if (-not (Test-Path $ks)) {
    & "$Jdk\bin\keytool.exe" -genkeypair -v -keystore $ks -storepass dormqr123 `
        -keypass dormqr123 -alias dormqr -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=DormQR, OU=Local, O=DormQR, L=Local, S=Local, C=CN" | Out-Null
}

$finalName = if ($Release) { 'DormQR-v' + $versionName + '-release.apk' } else { 'DormQR-v' + $versionName + '-debug.apk' }
$final = Join-Path $out $finalName
if (Test-Path $final) { Remove-Item $final -Force }

& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:dormqr123 --key-pass pass:dormqr123 `
    --ks-key-alias dormqr --out $final $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner 失败' }

Step "校验"
& "$bt\apksigner.bat" verify --print-certs $final | Select-Object -First 3
& "$bt\aapt2.exe" dump badging $final | Select-String 'package:|application-label:|sdkVersion|targetSdkVersion|launchable'

$size = [math]::Round((Get-Item $final).Length / 1KB, 1)
Write-Host ""
Write-Host "APK: $final  ($size KB)" -ForegroundColor Green






