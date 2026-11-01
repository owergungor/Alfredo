# Glassio

Glassio is a lightweight Android TV launcher with a cinematic carousel, app dock, configurable glass styling, custom backgrounds, a screensaver, a local Library, Trakt Continue Watching, and English/Turkish interface language support.

## Features

- Remote-first navigation designed for Android TV.
- Customizable carousel, title artwork, transitions, and backdrop rotation.
- Liquid-glass, clear, and dark themes with separate dock and settings panel styling.
- App dock, folders, custom icons, and icon packs.
- Personal Library stored locally on the TV and optional Trakt watchlist/playback integration.
- Custom image/video backgrounds and screensaver options.
- English and Turkish UI language selection in Settings → Language.
- Performance presets for lower-memory TV devices.

## Build

Requirements: Android SDK, JDK 17 or compatible, and network access for the Gradle wrapper/dependencies.

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install on Android TV

With ADB enabled and the TV reachable on the network:

```bash
adb connect TV_IP:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Replace `TV_IP` with the TV's address. Do not disable the system launcher until Glassio has been tested and you know how to restore the default launcher.

## Project notes

- Application ID: `com.glasslauncher.tv.devtest` for the current development build.
- The project uses Kotlin and Jetpack Compose.
- Settings are stored locally on the TV. The Library's saved titles remain local unless the user enables Trakt integration.

## Credits and trademarks

Glassio is an independent project and is not affiliated with Apple, Google, TMDB, or other third-party services. Third-party names and marks belong to their respective owners. See `LICENSE` and `TRADEMARKS.md` for details.
