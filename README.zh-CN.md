> [!IMPORTANT]
> **现已可以配置NFC，故归档。**

<h1 align="center">门禁码 / DormQR</h1>

<p align="center">
齐鲁工业大学 2026 年新宿舍的门锁二维码，做成一个轻量安卓应用。终于可以少罚站一会儿了！话说为什么还不开放NFC？？
</p>

<p align="center">
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8.0%2B-3B6EA5?style=flat-square&logo=android&logoColor=white">
  <img alt="版本" src="https://img.shields.io/badge/version-1.2-3B6EA5?style=flat-square">
  <img alt="许可证" src="https://img.shields.io/badge/license-AGPL--3.0-3B6EA5?style=flat-square">
</p>

<p align="center">
  <b>简体中文</b> · <a href="README.md">English</a>
</p>

## 这是什么

从服务器获取二维码并显示。

用于解锁的二维码内容是以 `VMS://` 开头的一段字符串。本项目把请求体固定，直接 POST，获取 `staffQRCode` 并显示。

## 特性

- 冷启动进行本地缓存
- 后台每 8 分钟换一张（有效期的 80%），旧码在服务端不失效
- 常驻通知里挂着大图二维码，门口没信号也扫得到
- 手机桌面卡片，可缩放，点一下换码
- OPPO Watch 圆形表盘适配：二维码落在内切正方形里，四角不会被圆边切掉
- 深浅色跟随系统，二维码始终纯黑白

## 安装

需要 Android 8.0 及以上。APK 侧载，不在任何应用商店。

1. 从 [Releases](https://github.com/Brett-Now/DormQR/releases) 下载 `DormQR-v1.2-release.apk`
2. 安装时允许「未知来源」
3. 首次打开在设置里填密钥（见下节）

## 快速上手

> [!IMPORTANT]
>
> 首次使用需要填写加密请求体（抓包获取~~，后续可能会出个自动从HAR提取~~）。
>
> 填写步骤：长按主界面或点右上角齿轮 → 服务连接 → 展开「加密请求体」→ 粘贴密文 → 保存。
>
> 填错或留空时，主界面会明确说「密文未配置」。

- 点屏幕任意处 = 换一张码
- 长按屏幕，或点右上角齿轮 = 进设置

手表上同理，另外点标题「设置」可以返回二维码页，从左侧往右滑超过 100dp 也能返回。

## 手表适配

测试设备：OPPO Watch X3（OWW261，466×466 圆屏）

## 从源码构建

不需要 Gradle，也不需要联网拉依赖。工具链是 `aapt2 + javac + d8 + zipalign + apksigner`。

```powershell
# Release（清单不含 debuggable）
pwsh tools/build-apk.ps1 -Release
# → build/apk/DormQR-v1.2-release.apk

# adb（Debug，可用 run-as 抓 prefs）
pwsh tools/build-apk.ps1
# → build/apk/DormQR-v1.2-debug.apk
```

依赖：JDK 17、`platforms/android-35/android.jar`、`build-tools/34.0.0`。路径在脚本头部，自行更换。

两条通道用同一张 keystore 签名，可以互相覆盖。**`build/` 下的 keystore 首次构建时生成，之后务必保留**；换目录时漏掉它会导致签名变化。

## 目录

| 路径 | 内容 |
| --- | --- |
| `app/src/main/java/app/dormqr/qr/` | 界面、服务、网络、缓存、二维码渲染 |
| `app/src/main/java/io/nayuki/qrcodegen/` | 内置的二维码编码器（MIT） |
| `app/src/main/res/` | 布局、颜色、字符串、图标 |
| `tools/build-apk.ps1` | 不依赖 Gradle 的构建脚本 |
| `tools/install-apk.ps1` | adb 安装 |
| `tools/restore-watch-settings.ps1` | 还原手表上被改动过的系统设置 |
| `tools/*.java` | 编码器、刷新规则、网络层的桌面端验证脚本 |
| `docs/` | 交接文档、需求、报告、截图（本机资料，不入库） |

## 已知限制

- 二维码有效期只验证到 **10 分钟**。若实际更长，把设置里的「已知有效期」改大，换码线按百分比自动跟随。

## 关于凭证

请求体是等效身份凭证，能换出你的门禁二维码。

如果你 fork 了这个项目，请不要把任何人的密文提交进仓库。

## 风险提示与免责声明

本项目是个人做的非官方工具，与齐鲁工业大学、门禁系统厂商都没有关联，也不代表任何一方。使用前请了解：

- **请勿泄露凭证**：请求体泄露等同于把门禁码交给别人。别把密文贴进 issue、聊天记录或截图，也别把带密文的配置备份传到网盘。
- **接口随时可能变**：学校改版、换楼、迁移系统都可能让取码失效。
- **会改动系统设置**：为适配手表曾用 adb 改过 AOD 白名单和抬腕开关，仓库里有还原脚本。动系统设置有风险，不清楚后果就别动。
- **无担保与责任范围**：在适用法律允许的范围内，本软件按「现状」提供，不保证可用性、准确性或适用于特定用途；作者不对因使用本软件造成的门禁失败、数据丢失或其他损失负责。具体条款见 [LICENSE](LICENSE) 第 15–17 条。本声明不排除依法不得排除的责任。

## 致谢

- 二维码编码器：[QR Code generator library](https://www.nayuki.io/page/qr-code-generator-library)（Project Nayuki，MIT）

## 许可

本项目采用 AGPL-3.0。第三方代码和素材以各自声明为准。

Copyright (C) 2026 Brett-Now

