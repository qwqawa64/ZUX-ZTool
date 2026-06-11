# AGENTS.MD

## Project Summary

ZUX-ZTool is a single-module Android project for ZUX OS/ZUI devices. It combines a normal Android app UI, LSPosed/Xposed Hook modules, root/shell utilities, foreground log collection, configuration backup/restore, and embedded Magisk module assets.

The Android module is `:app`, package `com.qimian233.ztool`. The app UI is largely Jetpack Compose based: `MainActivity` hosts the main Compose routes for Home, Features, Audit, and Settings. Feature detail entry points are still Android `Activity` contracts declared in the Manifest and must remain stable unless the user explicitly approves a breaking change.

Use English for new agent-facing documentation where practical. Existing user-facing strings and legacy Chinese logs may remain Chinese.

## Current Build Facts

- Root project name: `ZTool`
- Android module: `:app`
- Android Gradle Plugin: `9.1.1`
- Kotlin: `2.3.21`
- Compose BOM: `2025.10.00`
- Miuix: `top.yukonga.miuix.kmp:miuix-ui-android:0.9.2`
- `compileSdk` / `targetSdk`: `37`
- `minSdk`: `27`
- Java/Kotlin JVM target: `11`
- Xposed API: `de.robv.android.xposed:api:82` as `compileOnly`
- Hidden API bypass: `org.lsposed.hiddenapibypass:hiddenapibypass:4.3`
- Main verification command: `.\gradlew.bat assembleDebug`

For any Miuix API or component behavior, consult the official Dokka site instead of decompiling or reading locally downloaded dependencies:

```text
https://compose-miuix-ui.github.io/miuix/dokka/index.html
```

## Repository Layout

```text
.
  app/                         Android app module
  docs/compose_refactor/        Compose migration notes and scoped agent guide
  docs/gradle_upgrade/          Gradle upgrade notes
  docs/material_enhancement/    Material UI notes
  gradle/                       Gradle wrapper and version catalog
  libs/                         Root local dependencies
  Add_New_Hook_zh-CN.md         Frontend guide for adding Hook settings
  AGENTS.MD                     This root agent guide
  README.md
  UpdateCheck.json
  ZToolLogo.png
  ZToolLogoForeground.svg
  ZToolUploadKey.jks            Release signing file; do not touch casually
  更新日志.txt
```

The Android module has this high-level shape:

```text
app/
  build.gradle.kts
  proguard-rules.pro
  libs/XposedBridgeAPI-82.jar
  src/main/
    AndroidManifest.xml
    assets/
      xposed_init
      embedding/
    java/com/qimian233/ztool/
    res/
  src/test/
  src/androidTest/
```

## Source Package Map

```text
com.qimian233.ztool
  MainActivity.kt, root Compose routes, shell executor, activation probe

audit
  Hook log parsing and structured audit data

config
  Module config constants

data
  Repository layer for preferences, root/shell operations, logs, OTA, theme, and feature settings

hook
  Xposed entry point, Hook manager, base Hook API, preference helpers, and Hook modules

service
  Foreground Hook log collection service and service manager

settingactivity
  Feature detail Activities and remaining helper screens

ui
  Shared Compose components and theme system

utils
  File, permission, Magisk, embedding, firmware, font, dialog, and config utilities

viewmodel
  UiState and state management for main routes and feature detail pages
```

Main route files:

- `MainActivity.kt`: app entry point and Compose host.
- `HomeRoute.kt`: environment status, module activation, device info, update prompt, reboot actions.
- `FeaturesRoute.kt`: feature entry list grouped by target app/scope.
- `AuditRoute.kt`: Hook log filtering, search, stats, export, clear, and details.
- `SettingsRoute.kt`: log service, detailed logging, backup/restore, theme settings, about dialog.

## Manifest And Xposed Contracts

Keep these stable unless the task explicitly targets them:

- `app/src/main/AndroidManifest.xml` Xposed metadata.
- `app/src/main/assets/xposed_init`, currently pointing to `com.qimian233.ztool.hook.HookInit`.
- `app/src/main/res/values/array.xml` `xposed_scope`.
- Existing Activity package/class names used as Manifest launch contracts.
- Existing SharedPreferences/config keys used by Hook modules.

Hook scope packages include Android framework/system apps and ZUI apps such as `android`, `com.android.systemui`, `com.android.settings`, `com.zui.launcher`, and `com.lenovo.ota`.

## Hook Architecture

Core files:

- `app/src/main/java/com/qimian233/ztool/hook/HookInit.java`: Xposed entry point.
- `app/src/main/java/com/qimian233/ztool/hook/base/BaseHookModule.java`: base class for Hook modules.
- `app/src/main/java/com/qimian233/ztool/hook/base/HookManager.java`: registration and dispatch for Hook modules.
- `app/src/main/java/com/qimian233/ztool/hook/base/PreferenceHelper.java`: Hook-side preference helper.
- `app/src/main/java/com/qimian233/ztool/hook/modules/SharedPreferencesTool/ModulePreferencesUtils.java`: app-side module preference utility.

Hook module folders:

```text
hook/modules/
  documentsui/
  gametool/
  launcher/
  ota/
  packageinstaller/
  safecenter/
  setting/
  SharedPreferencesTool/
  systemFramework/
  systemui/
  wallpaper/
```

## Adding Hook Settings In The Frontend

When adding a Hook switch or other Hook-facing setting in the app UI, read and follow:

```text
Add_New_Hook_zh-CN.md
```

The durable rules from that guide are:

- Do not read or write SharedPreferences directly from Composables.
- Put persistence in the appropriate `data/**/**Repository.kt`.
- Expose values through `UiState` and mutate them through the matching `ViewModel`.
- Hook-related settings should use `ModulePreferencesUtils` and the shared `xposed_module_config` preference file.
- Preference keys and default values must match the Hook-side reads exactly.
- Use existing shared setting components such as `SettingItem.Switch`, dropdowns, sliders, and text inputs where applicable.

## Adding A Backend Hook

To add a new Hook implementation on the backend:

1. Create a Hook class that extends `app/src/main/java/com/qimian233/ztool/hook/base/BaseHookModule.java`.
2. Implement `getModuleName()`, `getTargetPackages()`, and `handleLoadPackage(...)`.
3. Read Hook settings using the same keys/defaults that the frontend writes.
4. Register the Hook class in `app/src/main/java/com/qimian233/ztool/hook/base/HookManager.java`.
5. Add the required log handling in `app/src/main/java/com/qimian233/ztool/audit/LogParser.java`, including module pattern/display name/category and any module-specific extracted data needed by the Audit UI.
6. If the Hook needs a user-facing toggle, add the frontend state according to `Add_New_Hook_zh-CN.md`.

Use `BaseHookModule.log(...)` / `logError(...)` for module logs when possible. The log module name should match the parser pattern in `LogParser.java`.

## UI And Compose Guidance

The current UI direction is Compose-first. For Compose migration-specific work, also read:

```text
docs/compose_refactor/AGENTS.MD
```

General UI rules:

- Prefer shared components in `app/src/main/java/com/qimian233/ztool/ui/components`.
- Keep business screens style-agnostic; route Material/Miuix differences through theme and shared component adapters.
- Use `ZToolTheme` and `MaterialTheme.colorScheme` for normal app colors.
- Keep frontend style, theme mode, dynamic color, manual seed color, and AMOLED behavior in the theme settings layer, not in feature pages.
- Do not reintroduce XML navigation, View-based screen scaffolding, old adapters, or Fragment navigation for new work.
- Do not put shell/root operations, raw threads, preference I/O, or imperative dialogs directly in Composables. Use Repository/ViewModel/state.
- Preserve detail Activity launch contracts even if the screen body is Compose.

## High-Risk Boundaries

Treat these areas as behavior-sensitive:

- `app/src/main/java/com/qimian233/ztool/hook/**`
- `app/src/main/java/com/qimian233/ztool/service/**`
- `app/src/main/java/com/qimian233/ztool/config/**`
- `app/src/main/java/com/qimian233/ztool/EnhancedShellExecutor.java`
- `app/src/main/java/com/qimian233/ztool/utils/MagiskModuleManager.java`
- `app/src/main/java/com/qimian233/ztool/utils/EmbeddingConfigManager.java`
- `app/src/main/assets/embedding/**`
- `app/src/main/assets/xposed_init`
- Xposed metadata and scope declarations in resources/Manifest
- Existing preference keys and config file formats
- Root, shell, reboot, package kill, and Magisk install/update behavior

UI tasks should normally wrap these behaviors behind repositories or state holders rather than changing the behavior itself.

## Resources And Assets

- `res/values/`: strings, colors, themes, arrays.
- `res/values-night/`: night resources.
- `res/drawable/`: in-app drawables and icons.
- `res/mipmap-*`: launcher icons.
- `res/raw/`: demo video resources.
- `res/xml/`: backup, extraction, and locale config.
- `assets/embedding/`: embedded Magisk module configuration and scripts.
- `assets/embedding/zuxos_embedding/`: module package contents such as `module.prop`, shell scripts, sepolicy rules, and installer metadata.

Do not edit embedded module assets unless the task is specifically about Magisk/embedding behavior.

## Verification

Run `.\gradlew.bat assembleDebug` after code changes that touch:

- Compose UI, themes, shared components, or navigation.
- ViewModels/Repositories used by UI.
- Hook classes, Hook registration, or Hook preference wiring.
- Manifest, resources, Gradle files, or embedded assets.

For documentation-only changes, a build is usually unnecessary; verify with `git diff --check` at minimum.

## Git And Workflow Rules

- Start by checking `git status --short`.
- The working tree may contain user changes. Do not revert unrelated changes.
- Keep edits scoped to the request.
- Do not commit build outputs, IDE files, Gradle caches, or unrelated untracked files.
- Completed user-requested tasks must be committed unless the user explicitly says not to commit.
- Before committing, review `git diff --check` and the staged diff.
- Use concise commit messages that describe the actual scoped change.

## Common Commands

```powershell
git status --short
.\gradlew.bat assembleDebug
git diff --check
```
