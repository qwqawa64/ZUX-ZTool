# AGENTS.MD

## Agent Quick Start

- Generate your commit message as `.gitmessage` indicates.
- No Java. The Android APP part of this repository is Kotlin-only.
- Explicitly assign UTF-8 as the decode format for all project files to handle Chinese characters.

## Project Summary

ZUX-ZTool is a single-module Android project (`:app`, package `com.qimian233.ztool`) for Lenovo ZUX OS/ZUI devices. It combines a Jetpack Compose app UI, LSPosed/libxposed Hook modules, root/shell utilities, foreground log collection, configuration backup/restore, and embedded Magisk module assets.

Use English for new agent-facing documentation where practical. Existing user-facing strings and legacy Chinese logs may remain Chinese.

## Current Build Facts

- Root project name: `ZTool`
- Android module: `:app`
- Android Gradle Plugin: `9.4.1`
- Kotlin: `2.3.21`
- Compose BOM: `2025.10.00`
- Miuix: `top.yukonga.miuix.kmp:miuix-ui-android:0.9.3`
- `compileSdk` / `targetSdk`: `37`
- `minSdk`: `27`
- Java/Kotlin JVM target: `11`
- Xposed API: libxposed API 102
- Hidden API bypass: `org.lsposed.hiddenapibypass:hiddenapibypass:4.3`
- Main verification command: `.\gradlew.bat assembleDebug` (Windows PowerShell or CMD) or `./gradlew.bat assembleDebug` (WSL2 and Git Bash)

For any Miuix API or component behavior, consult the official [Dokka site](https://compose-miuix-ui.github.io/miuix/dokka/index.html) instead of decompiling or reading locally downloaded dependencies:

## Project Structure — Boundary Rules Only

Do not rely on a memorized file tree; explore the repository directly when needed. The durable structural rules are:

- All project documentation lives under `docs/` (`hook/`, `preference_key/`, `dex_index/`, `logging/` are active guides; `archive/` holds outdated docs; `research/` holds analysis notes). The active guides referenced below are the canonical reading list — prefer them over scanning source.
- Reference AARs are under `reference/` and maintenance scripts are under `tools/`.
- Keystore/signing files (`*.jks`, `*.keystore`) are gitignored and intentionally not stored in the repository — never commit them.
- Single sources of truth in code:
  - `app/src/main/java/com/qimian233/ztool/data/keys/PreferenceKeys.kt` — every key in `xposed_module_config`.
  - `app/src/main/java/com/qimian233/ztool/data/keys/ScopeKeys.kt` — every scope package name and its preferred restart method.
- Layering: screens (`screens/**`) and shared UI (`ui/**`) must not do preference I/O, shell/root operations, or raw threads directly; persistence lives in `data/**/*Repository.kt`, values flow through `viewmodel/**` UiStates.
- Hook entry point and infrastructure live in `hook/` (entry: `hook/HookInit.kt`, registered in `app/src/main/resources/META-INF/xposed/java_init.list`); per-app Hook modules live in `hook/modules/<package>/`.
- App-side offline DexKit indexing lives in `dexindex/` (pure Kotlin, no libxposed dependency); the hook-side reader is `hook/base/DexIndexStore.kt`.
- Foreground services live in `service/`. Currently: `LogCollectorService` (foreground, `specialUse`, not exported) and `MediaOutputTileService`. New services must declare an appropriate `foregroundServiceType` and stay in `service/`.

## Manifest And Xposed Contracts

Keep these stable unless the task explicitly targets them:

- Existing Manifest component declarations (`MainActivity`, `LogCollectorService`, `MediaOutputTileService`, `DexIndexReceiver`) and their intent actions.
- Existing SharedPreferences/config keys used by Hook modules.
- `app/src/main/resources/META-INF/xposed/java_init.list` — Hook entry class (`com.qimian233.ztool.hook.HookInit`).
- `app/src/main/resources/META-INF/xposed/module.prop` — module metadata (e.g. `minApiVersion`, `staticScope`); keep hardcoded.
- `app/src/main/resources/META-INF/xposed/scope.list` — the LSPosed injection scope declaration (build-time; package names are intentionally hardcoded there, NOT driven by `ScopeKeys`).

Hook scope packages include Android framework/system apps and ZUI/third-party apps such as `android`, `com.android.systemui`, `com.android.settings`, `com.zui.launcher`, `com.lenovo.ota`, `com.sohu.inputmethod.sogou*`, and others; see `ScopeKeys.kt` for the authoritative list.

## Hook Architecture

Core files (all Kotlin):

- `hook/HookInit.kt`: Xposed entry point.
- `hook/base/BaseHookModule.kt`: base class; `AppHookModule` for app hooks, `SystemHookModule` for system-framework hooks.
- `hook/base/HookManager.kt`: registration and dispatch for Hook modules.
- `hook/base/DexIndexStore.kt`: hook-side offline DexKit index reader (only index class allowed to touch libxposed).
- `utils/ModulePreferencesUtils.kt`: app-side SharedPreferences read/write; uses `PreferenceKeys` list-based matching for backup/restore type inference.

**libxposed Hook design patterns**: This project uses the libxposed API, NOT the legacy Rovo89 Xposed API — the two are almost entirely different syntactically. An agent unfamiliar with Hook development here should read the [guide](docs/hook/libxposed_guide_ztool_ver.md) instead of scanning hook sources; it defines the project's canonical Hook patterns (base-class choice, required method trio, ClassLoader acquisition, `findField`/`findMethod`, `hookWithId` with per-scope unique IDs, Chain SAM syntax, remote preferences reads, and the DexKit offline-index anti-obfuscation rule):

### Adding A Hook

0. If you are not yet familiar with this project's libxposed Hook patterns, read `docs/hook/libxposed_guide_ztool_ver.md` first before writing any Hook code.
1. Create a Hook class extending `AppHookModule` (or `SystemHookModule` for system framework), in **Kotlin**.
2. Implement `getModuleName()`, `getTargetPackages()` and `handleLoadPackage(...)`. **Return `ScopeKeys.CONSTANT.packageName` references from `getTargetPackages()`** — register any new target package in `ScopeKeys.kt` first (see "Scope Management") and add it to `scope.list`. For system-framework hooks, implement `handleSystemServerStarting(...)` too. Returning "hook_test" or "test_hook" from `getModuleName()` always enables a test hook without a frontend switch.
3. Read Hook settings using `PreferenceKeys` constants: `xposed.getRemotePreferences("xposed_module_config").getXxx(PreferenceKeys.CONSTANT_NAME.name, PreferenceKeys.CONSTANT_NAME.default)`. Do not hand-write key strings.
4. Register the Hook class in `hook/base/HookManager.kt`.
5. If the Hook needs a user-facing toggle, add the frontend state according to `docs/hook/add_frontend_item.md`. New backend module guide: `docs/hook/add_new_hook_module.md`.

Use `logger.<level>` for module logs when possible, see `docs/logging/migrate_and_use_new_logging_system.md`.

### Offline DexKit Index (DexIndex)

DexKit lookups must NOT run inside `handleLoadPackage` (they break LSPosed hot reload — replay re-creates the bridge for the same APK and the native state conflicts). Instead, use the offline index pipeline:

- App side (`com.qimian233.ztool.dexindex`, pure Kotlin, **no libxposed dependency**): one `DexIndexer` per scope package (see `DexIndexRegistry`); `DexIndexManager` runs them after install/update (triggered by `DexIndexReceiver`), on app start when the target APK fingerprint changed, or via the Settings manual refresh entry. Results are written atomically to `<scopePackage>.json` in the module's private `filesDir` root (Remote Files cannot address subdirectories).
- Hook side (`hook/base/DexIndexStore.kt`): reads the JSON via `XposedInterface.openRemoteFile(...)` (Remote Files — the LSPosed daemon reads the module's private `filesDir` with privileges, no chmod needed). Any failure returns null and the hook falls back to hardcoded names.
- Never read the index inside hook lambdas (`hookWithId` callbacks) — resolve values in `handleLoadPackage` and capture them.
- Constants: `DexIndexConstants.kt`. Full guide: `docs/dex_index/readme.md`.

### Scope Management

**`ScopeKeys` is the single source of truth for scope package names in code.** Every place that manages scope packages — Hook `getTargetPackages()` implementations, `ScopeUtils.getScopes()` / `getScopePackages()`, and frontend feature entries — must reference `ScopeKeys.CONSTANT.packageName` and never hardcode package name strings. The `HowToRestart` registered on each `Scope` drives the unified restart logic in `ScopeUtils.restartScope()`.

When adding a Hook that targets a package:

1. Register the package in `ScopeKeys.kt` (with its preferred `HowToRestart`) before using it anywhere.
2. Reference `ScopeKeys.CONSTANT.packageName` in the Hook's `getTargetPackages()`, and add it to `ScopeUtils.getScopes(...)` if the package belongs to a frontend feature entry.
3. Add the package to the build-time `scope.list` resource.

Other scope rules:

- **`android` and `system` (system framework) are already in scope** — no change needed for system-framework hooks.
- **Always verify scope coverage after adding a new Hook module.** If a target package is missing from scope, the Hook will silently never execute.
- Do NOT remove scope entries unless their corresponding Hook modules are also removed.
- The scope is a build-time declaration; users must also configure it in LSPosed Manager.

## Frontend Wiring

When adding a Hook switch or other Hook-facing setting in the app UI, read and follow:

```text
docs/hook/add_frontend_item.md
```

**For preference key management**, all keys are centrally defined in `PreferenceKeys.kt`. When adding a new key, read:

```text
docs/preference_key/add_new_preference_key_zh-cn.md
```

The durable rules are:

- Do not read or write SharedPreferences directly from Composables.
- Put persistence in the appropriate `data/**/**Repository.kt`.
- Expose values through `UiState` and mutate them through the matching `ViewModel`.
- Hook-related settings must use `ModulePreferencesUtils` and the shared `xposed_module_config` preference file.
- **Preference keys must be registered in `PreferenceKeys.kt`** before use in Repository or Hook code. Reference them as `PreferenceKeys.CONSTANT_NAME.name` — never write key strings by hand.
- Preference keys and default values must match the Hook-side reads exactly.
- Use existing shared setting components such as `SettingItem.Switch`, dropdowns, sliders, and text inputs where applicable.

## UI And Compose Guidance

The current UI direction is Compose-first. For Compose migration-specific work, also read:

```text
docs/archive/compose_refactor/agents.md
```

General UI rules:

- Prefer shared components in `app/src/main/java/com/qimian233/ztool/ui/components`.
- Keep business screens style-agnostic; route Material/Miuix differences through theme and shared component adapters.
- Use `ZToolTheme` and `MaterialTheme.colorScheme` for normal app colors.
- Keep frontend style, theme mode, dynamic color, manual seed color, and AMOLED behavior in the theme settings layer, not in feature pages.
- Do not reintroduce XML navigation, View-based screen scaffolding, old adapters, or Fragment navigation for new work.
- Do not put shell/root operations, raw threads, preference I/O, or imperative dialogs directly in Composables. Use Repository/ViewModel/state.

## Resources And Assets

- `res/values/` + localized values dirs (e.g. `values-en/`, `values-night/`): strings, colors, themes, arrays.
- `res/drawable/`, `res/mipmap-*`: icons; `res/raw/`: demo video and user agreement; `res/xml/`: backup/extraction/locale config.
- `assets/embedding/` + `assets/embedding/zuxos_embedding/`: embedded Magisk module config, scripts, sepolicy rules, installer metadata.

Do not edit embedded module assets unless the task is specifically about Magisk/embedding behavior.

- **When adding new user-facing strings**, always add entries to both `res/values/strings.xml` (default/Chinese) AND non-default language files such as `res/values-en/strings.xml` (English). Use the same `name` attribute in both files.
- Developer-only or debug strings that should never be translated must use `translatable="false"`.

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
