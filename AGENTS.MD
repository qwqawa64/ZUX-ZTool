# AGENTS.MD

## Agent Quick Start

- Generate your commit message as `.gitmessage` indicates.
- Prefer Kotlin unless it fundamentally conflicts with existing infrastructure.
- Explicitly assign UTF-8 as the decode format for all project files to handle Chinese characters.
- JDK is available **only on Windows** in most scenarios. If you cannot find Java in your workspace on Windows, notify the user to config their JAVA_HOME and Path variable, then restart the terminal or system.
- If you are on WSL2 and you have to use Java related tools such as gradlew.bat, use Windows environment by explicitly calling cmd or PowerShell. For example, `cmd.exe /c ".\gradlew.bat assembleDebug"`
- To conduct compilation test on WSL2, the command is `bash cd ./ && cmd.exe /c ".\gradlew.bat assembleDebug"`
- To commit your work, use `git.exe commit <commit_messages>`
- If any of these Windows-specific commands failed, stop the work and ask the user to confirm what went wrong.

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
- Xposed API: libxposed API 102
- Hidden API bypass: `org.lsposed.hiddenapibypass:hiddenapibypass:4.3`
- Main verification command: `.\gradlew.bat assembleDebug` (Pure Windows) or `cmd.exe /c ".\gradlew.bat assembleDebug"` (WSL2)

For any Miuix API or component behavior, consult the official Dokka site instead of decompiling or reading locally downloaded dependencies:

```text
https://compose-miuix-ui.github.io/miuix/dokka/index.html
```

## Repository Layout

```text
.
  app/                         Android app module
  docs_archive/compose_refactor/        Compose migration notes and scoped agent guide
  docs_archive/gradle_upgrade/          Gradle upgrade notes
  docs_archive/material_enhancement/    Material UI notes
  reference/                    Reference AAR files
  gradle/                       Gradle wrapper and version catalog
  libs/                         Root local dependencies
  Add_New_Hook_zh-CN.md         Frontend guide for adding Hook settings, UTF-8 encoding
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

- Existing Activity package/class names used as Manifest launch contracts.
- Existing SharedPreferences/config keys used by Hook modules.
- `app/src/main/resources/META-INF/xposed/java_init.list` Hook entry class, currently points to `com.qimian233.ztool.hook.HookInit`.
- `app/src/main/resources/META-INF/xposed/module.prop` Module properties, such as minimum API version.
- `app/src/main/resources/META-INF/xposed/scope.list` Module scope.

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
  mobiledesktop/
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

1. Create a Hook class that extends `AppHookModule` for non-system-framework-hook, and extend `SystemHookModule` for system-framwork-hook. Prefer **Kotlin** (`.kt`) for new hooks; Java is acceptable only when it fundamentally conflicts with existing infrastructure.
2. Implement `getModuleName()`, `getTargetPackages()` and `handleLoadPackage(...)`. For system server (system framework) hooks, implement `handleSystemServerStarting(...)`, too. When implementing test hook, return "hook_test" or "test_hook" in getModuleName() will always enable this test hook without bothering to add frontend switch.
3. Read Hook settings using the same keys/defaults that the frontend writes.
4. Register the Hook class in `app/src/main/java/com/qimian233/ztool/hook/base/HookManager.java`.
5. If the Hook needs a user-facing toggle, add the frontend state according to `Add_New_Hook_zh-CN.md`.

Use `logger.<level>` for module logs when possible, see `/docs_archive/new_log_system_migrate_and_use_new_logging_system.md` for more information.

## Service, Scope, And Activation Rules

### Scope Management

The `app/src/main/resources/META-INF/xposed/scope.list` declares which packages LSPosed should inject the module into. Rules for managing scope:

- **When a new Hook targets a package not already in the scope, add it to `scope.list`.** The `getTargetPackages()` returned by each module must be a subset of the declared scope.
- **`android` and `system` (system framework) are already in scope** — no change needed for system-framework hooks.
- **Always verify scope coverage after adding a new Hook module.** If a target package is missing from scope, the Hook will silently never execute.
- Do NOT remove scope entries unless their corresponding Hook modules are also removed.
- The scope is a build-time declaration; users must also configure it in LSPosed Manager.

### Activation Chain

- LSPosed sends a bind when module starts up.
- `ModuleActivationProbe` has a onBind receiver that sets `active` field in this class to `true`.
- Frontend reads this value and shows the activated user interface.

### Service Protection Hook Guidelines

When creating hooks that protect or interact with Android system services (e.g. keeping daemon processes alive, preventing service GC):

- **Target `android`** (system framework) for hooks that intercept AMS, process management, or Binder behavior.
- **Use `hookMethodBySignature`** when hooking AMS or ActiveServices methods — Android version differences change method parameter counts across releases. Iterate declared methods by name to cover all overloads.
- **Wrap every hook attempt in its own try-catch** — missing methods on a particular ROM/version should not prevent other hooks from applying.
- **For binder anti-GC**: hold `ServiceManager.getService(...)` references in a static `List<IBinder>` to prevent weak-reference-only GC.
- **For death recipient protection**: hook `Binder.unlinkToDeath` and inspect the call stack for LSPosed-related classes before deciding to intercept.
- **Log each defense layer** independently so the Audit UI can show which protections are active.
- Such hooks typically require a system reboot to take effect after enabling.

### Service Declaration In Manifest

- The only current service is `LogCollectorService` (foreground, `specialUse`, not exported). Declared in `AndroidManifest.xml:40-44`.
- New services must be declared with appropriate `foregroundServiceType` for the target SDK.
- Services that interact with Hook modules should stay in `com.qimian233.ztool.service`.

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

## Resources And Assets

- `res/values/`: strings, colors, themes, arrays.
- `res/values-night/`: night resources.
- `res/drawable/`: in-app drawables and icons.
- `res/mipmap-*`: launcher icons.
- `res/raw/`: demo video resources and user agreement.
- `res/xml/`: backup, extraction, and locale config.
- `assets/embedding/`: embedded Magisk module configuration and scripts.
- `assets/embedding/zuxos_embedding/`: module package contents such as `module.prop`, shell scripts, sepolicy rules, and installer metadata.

Do not edit embedded module assets unless the task is specifically about Magisk/embedding behavior.

## Verification

Follow the steps below:

1. If the code changes, run `.\gradlew.bat assembleDebug` after code changes that touch:
    - Compose UI, themes, shared components, or navigation.
    - ViewModels/Repositories used by UI.
    - Hook classes, Hook registration, or Hook preference wiring.
    - Manifest, resources, Gradle files, or embedded assets.

    For documentation-only changes, a build is usually unnecessary; verify with `git diff --check` at minimum.
2. Commit your changes.

## Git And Workflow Rules

- Start by checking `git status --short`.
- The working tree may contain user changes. Do not revert unrelated changes.
- Keep edits scoped to the request.
- Do not commit build outputs, IDE files, Gradle caches, keystore, or unrelated untracked files.
- Completed user-requested tasks must be committed unless the user explicitly says not to commit.
- Before committing, review `git diff --check` and the staged diff.
- Use concise commit messages that describe the actual scoped change.
- If Git commit times out with GPG signature, retry it for once. If the commit still fails, commit without GPG signing.

## Common Commands

```powershell
git status --short
.\gradlew.bat assembleDebug
git diff --check
git commit ...
```

## Typical Work Flow

1. User assigns a task
2. Analyze user's demand. If you receive an ambiguous demand with too little information, provide a question list for the user to submit missing information.
3. Make a plan for the task. If the plan consists of more than 10 steps or more than 10 files are estimated to be added, modified and removed, write the plan into a Markdown file under the root directory with AGENT-oriented format
4. Conduct the plan:
    - Work with files
    - Do necessary verifications noted in the Verification chapter
    - *Commit changes*
    - Mark the corresponding todo item in your list or the Markdown file written in step 3 as completed
5. Continue until at least one of the following condition is/are satisfied. Notify the user with the current situation after encountering these situations:
    - The work is done, no todo item left unchecked.
    - Some step requires additional information and these information cannot be fetched via Internet, build cache and user input.
    - Unable to access critical tools: gradlew.bat, git, javap, javac for 3 times. 