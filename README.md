<h1 align="center">DormQR</h1>

<p align="center">
The door-lock QR code for Qilu University of Technology's 2026 dorm buildings, as a lightweight Android app. Finally, less time standing in the cold. And why is there still no NFC on these locks?
</p>

<p align="center">
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8.0%2B-3B6EA5?style=flat-square&logo=android&logoColor=white">
  <img alt="Version" src="https://img.shields.io/badge/version-1.2-3B6EA5?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/license-AGPL--3.0-3B6EA5?style=flat-square">
</p>

<p align="center">
  <b>English</b> · <a href="README.zh-CN.md">简体中文</a>
</p>

## What this is

It fetches a QR code from a server and shows it.

The payload for the lock is a string starting with `VMS://`. This app hardcodes the request body, POSTs it, and draws the `staffQRCode` that comes back.

## Features

- Caches locally on cold start
- A new code every 8 minutes (80% of the validity window); old codes stay valid server-side
- The QR code sits in the ongoing notification as a large image, so it scans even when the door has no signal
- Home screen widget on the phone, resizable, tap to refresh
- Fits the OPPO Watch round face: the code lands inside the inscribed square, corners never clipped
- Follows the system light/dark theme while the QR stays pure black and white

## Install

Android 8.0 or newer. Sideloaded APK, not on any store.

1. Download `DormQR-v1.2-release.apk` from [Releases](https://github.com/Brett-Now/DormQR/releases)
2. Allow installs from unknown sources
3. On first launch, fill in the key in settings (next section)

## Quick start

> [!IMPORTANT]
>
> First run needs the encrypted request body (grab it with a packet capture — ~~automatic HAR extraction may come later~~).
>
> How: long-press the main screen or tap the gear at the top right → Service → expand "Encrypted request body" → paste the ciphertext → save.
>
> If it's wrong or empty, the main screen says "payload not configured".

- Tap anywhere on screen = fetch a new code
- Long-press, or tap the gear = settings

Same on the watch. There, tapping the "Settings" title also returns to the code, and a right swipe beyond 100dp does too.

## Watch support

Tested device: OPPO Watch X3 (OWW261, 466×466 round display)

## Building from source

No Gradle, no dependency downloads. The toolchain is `aapt2 + javac + d8 + zipalign + apksigner`.

```powershell
# Release (manifest without debuggable)
pwsh tools/build-apk.ps1 -Release
# → build/apk/DormQR-v1.2-release.apk

# adb (Debug, run-as works for pulling prefs)
pwsh tools/build-apk.ps1
# → build/apk/DormQR-v1.2-debug.apk
```

Requires JDK 17, `platforms/android-35/android.jar` and `build-tools/34.0.0`. Paths sit at the top of the script; adjust them yourself.

Both channels are signed with the same keystore and can overwrite each other. **The keystore is generated under `build/` on the first build and must be kept afterwards.** Lose it when moving the project and the signature changes.

## Layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/app/dormqr/qr/` | UI, service, networking, cache, QR rendering |
| `app/src/main/java/io/nayuki/qrcodegen/` | Bundled QR encoder (MIT) |
| `app/src/main/res/` | Layouts, colors, strings, icons |
| `tools/build-apk.ps1` | Gradle-free build script |
| `tools/install-apk.ps1` | adb install helper |
| `tools/restore-watch-settings.ps1` | Restores the watch settings this project changed |
| `tools/*.java` | Desktop checks for the encoder, refresh rules and network layer |
| `docs/` | Handoff notes, requirements, reports, screenshots (local only, not tracked) |

## Known limits

- Validity was only verified up to **10 minutes**. If it turns out longer, raise "Known validity" in settings and the refresh point follows as a percentage.

## About the credential

The request body is an equivalent identity credential; it yields your dorm QR code.

If you fork this project, don't commit anyone's ciphertext.

## Risks and disclaimer

This is an unofficial personal tool, not affiliated with Qilu University of Technology or the access-control vendor, and not representing either. Before using it:

- **Don't leak the credential.** A leaked request body hands over your door code. Don't paste the ciphertext into issues, chats or screenshots, and don't back up configs containing it to cloud storage.
- **The endpoint can change.** A school-side update, a new building or a system migration can break fetching.
- **It changes system settings.** Watch support meant changing the AOD allowlist and the lift-to-wake switch over adb; the repo has a restore script. Changing system settings carries risk — if you don't know what it does, leave it alone.
- **No warranty.** To the extent permitted by law, this software is provided "as is", without warranty of availability, accuracy or fitness for a particular purpose. The author is not responsible for failed door access, data loss or other damage caused by using it. See sections 15–17 of [LICENSE](LICENSE). This notice does not exclude liability that cannot be excluded by law.

## Credits

- QR encoder: [QR Code generator library](https://www.nayuki.io/page/qr-code-generator-library) (Project Nayuki, MIT)

## License

AGPL-3.0. Third-party code and assets keep their own terms.

Copyright (C) 2026 Brett-Now
