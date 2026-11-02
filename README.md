# Alfredo

Alfredo is a fast, lightweight, liquid-glass Android TV launcher with a cinematic carousel, customizable app dock, dynamic background themes, ambient clock, personal Library, Trakt Continue Watching integration, and full English / Turkish interface support.

Alfredo is a continuation and optimized fork of the open-source launcher codebase originally created as Glassio by Mohmmad (mrh12).

## Features

- **Remote-first navigation**: Built specifically for Android TV remotes and low-latency D-Pad interactions.
- **Optimized for TV hardware**: Tailored for memory-constrained devices (e.g. Xiaomi Mi TV Stick, 1 GB RAM) with asynchronous cold startup, instant disk cache, and low-RAM rendering safeguards.
- **Liquid-Glass Aesthetics**: Cinematic tile rising, scale, and subtle specular lighting effects.
- **Customizable Dock & Folders**: Reorder apps, create folders, hide unused apps, and set favorites.
- **Local Library & Trakt Sync**: Manage personal watchlists locally on the TV or link a Trakt account.
- **Dynamic Backgrounds & Screensaver**: Built-in support for custom images, videos, and TV screensaver handoff.
- **Bilingual Interface**: Native English and Turkish localization.
- **In-App Updater**: Direct release updates from official GitHub releases (`owergungor/alfredo`).

## Building from Source

Requirements:
- Android SDK (API 35/36 build-tools)
- JDK 17
- Gradle 8+

To build the Debug APK:
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

To build the Release APK:
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

## Installation via ADB

With USB/Network Debugging enabled on your Android TV:

```bash
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Configuration & Notes

- **Application ID**: `com.glasslauncher.tv.devtest` (maintained for seamless upgrades over existing installations and TV preference retention).
- **Update Channel**: Automated update checks query the `owergungor/alfredo` GitHub Releases API.
- **Privacy & Storage**: Settings and Library items remain stored locally on your TV device.

## License & Credits

- Licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for details.
- Originally developed as Glassio by [Mohmmad (mrh12)](https://github.com/mohammadmrh12).
- Alfredo is an independent project and is not affiliated with Google, Apple, TMDB, or Xiaomi.