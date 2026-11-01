# Glassio — Antigravity project handoff

## Project choice
Continue developing this existing Glassio project directly. Do not create a replacement project and do not merge Alfredo/JADX output into this repository unless the user explicitly requests it.

## First task: inspect only
Before editing any files:
1. Read `README.md`, `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, and `gradle/wrapper/gradle-wrapper.properties`.
2. Map the Kotlin source tree, Android manifest, resources/assets, and existing build/test scripts.
3. Identify the current app identity and compatibility: namespace `com.glasslauncher.tv`, applicationId `com.glasslauncher.tv.devtest`, minSdk 26, targetSdk 35, versionName 1.9.8 (verify against files).
4. Summarize architecture, screens, settings persistence, services, permissions, integrations, and build requirements.
5. Run a local debug build only if Android SDK/JDK/dependencies are already configured and the user has authorized the build. Do not install or launch the APK on any device.
6. Report build blockers separately from source-code issues.

## Strict safety rules
- Do not run ADB commands, connect to the Mi TV Stick, install/uninstall APKs, change its default launcher, clear app data, or modify device settings.
- Do not modify, delete, rename, or reformat source files during the initial inspection phase.
- Do not remove features, permissions, dependencies, assets, or code just because they appear unused. Explain and ask first.
- Preserve the existing project structure and Gradle configuration unless a verified build issue requires a proposed change. Explain the reason and ask before changing build configuration.
- Never place secrets, API keys, tokens, signing keys, or local.properties values in source control or reports. `local.properties` may contain local credentials/configuration.
- Do not assume the separate Alfredo decompiled project is identical to this Glassio source tree.
- Make changes in small, reviewable steps. Before each substantial change, state the files to be changed and why.

## Known target device context
The intended test device is a Xiaomi Mi TV Stick (MiTV-AESP0), Android 9 / API 28, Amlogic GXL. The project currently declares minSdk 26, so API 28 is above its declared minimum; runtime compatibility still needs to be verified feature by feature. Keep TV remote / D-pad navigation as a first-class requirement.

## User priorities
- Develop the supplied Glassio project directly.
- Preserve the existing design, features, settings, assets, and TV-first interaction model unless explicitly asked to change them.
- The installed TV app and its existing user data/background images must remain untouched unless the user explicitly authorizes device operations.
- Use `MainActivity.kt` as the activity filename if any future activity restructuring is requested; do not rename it casually.

## Expected first response
Return an inspection report with:
1. project structure;
2. app architecture and main entry points;
3. build toolchain versions and compatibility concerns;
4. Android TV / API 28 risks;
5. available tests and build commands;
6. a proposed development plan.
Do not implement features in this first pass.
