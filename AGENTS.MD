# AGENTS.MD

## Project Overview

ZUX-ZTool is an Android/Xposed tool project. The current refactor direction is a UI-layer migration from traditional XML/View/Fragment screens to Jetpack Compose, while preserving Hook, service, utility, configuration, and runtime asset behavior.

The preferred boundary is:

- Refactor UI, navigation, state handling, dialogs, and theme/design-system code.
- Keep `hook/**`, Xposed metadata, module assets, shell behavior, service behavior, existing preference keys, and existing launch entry points intact unless explicitly requested.
- Do not turn the migration into a mixed View/Compose layout swap when a screen contains business logic that should first be separated into state and reusable logic.

## Repository Shape

Root:

```text
ZUX-ZTool/
  app/                         Android application module
  gradle/                      Gradle wrapper files
  libs/                        root-level local dependencies
  build.gradle.kts             root build script
  settings.gradle.kts          Gradle module configuration
  gradle.properties            Gradle/Android build settings
  ComposeRefactor.md           historical Compose refactor notes, UTF-8 encoding
  README.md
  UpdateCheck.json
  ZToolLogo.png
  ZToolLogoForeground.svg
  更新日志.txt
```

Main application areas:

```text
app/src/main/java/com/qimian233/ztool/
  MainActivity.kt
  HomeFragment.kt / HomeFragment.java history
  FeaturesFragment.kt
  SettingsFragment.kt
  AuditFragment.kt / AuditFragment.java history
  EnhancedShellExecutor.java
  LoadingDialog.kt

  audit/
  config/
  hook/
  service/
  settingactivity/
  ui/
    components/
    theme/
  utils/
```

Important resources and assets:

```text
app/src/main/res/
  layout/                      legacy XML layouts
  menu/                        legacy menus
  navigation/nav_graph.xml     legacy XML Navigation graph
  values/
  values-night/
  raw/
  xml/

app/src/main/assets/
  xposed_init
  embedding/
```

## Architecture Direction

Use Compose as the long-term UI layer.

- Use `ComposeRefactor.md` as the detailed migration plan and status log. Before selecting the next migration target, consult the latest plan and verification notes there; keep this file as the concise operating guide.
- `MainActivity` should move toward only hosting `setContent { ZToolApp() }`.
- Screens should become composable screen implementations.
- XML Navigation should eventually be replaced by `navigation-compose`.
- Existing `settingactivity/**` Activities may be migrated gradually, but new UI should not expand the old one-Activity-per-setting pattern unless compatibility requires preserving an entry point.
- Business pages should consume stable state rather than directly running shell commands, starting raw threads, showing imperative dialogs, or reading/writing preferences inline.

Preferred state shape:

- `ViewModel`
- `UiState`
- `StateFlow`
- Repository/Manager wrappers for shell, logs, config, preferences, update checks, and system services.

For example, logic from `HomeFragment` should be modeled around:

- `HomeViewModel`
- `HomeUiState`
- `EnvironmentRepository`
- `UpdateRepository`

## Compose And Design-System Rules

Avoid placing frontend-style conditionals directly in business screens.

Do not scatter code like:

```kotlin
if (style == FrontendStyle.Miuix) { ... }
```

inside feature screens. Instead, prefer project-level UI components and theme adapters:

- `ZToolTheme`
- `ZToolScaffold`
- `ZToolCard`
- `ZToolDropdownField`
- `ZSwitchRow`
- `ZListItem`
- `ZDialog`

Business screens should call project components and let the component/theme layer choose the Material 3 Expressive, Miuix, or future ZUX/ZUI rendering.

Keep these conventions when editing Compose UI:

- Use `MaterialTheme.colorScheme` for normal UI colors.
- Avoid hard-coded background/surface colors unless the color has semantic meaning, such as log severity or user-selected color preview.
- Root screen containers should use the theme background/surface consistently.
- Prefer shared components in `ui/components` over repeated local implementations.
- Use stable layout dimensions for grids, cards, controls, and rows to prevent text or state changes from shifting the UI unexpectedly.
- For dropdowns, prefer a shared `ZToolDropdownField` using the current Material3 `menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)` pattern.

## Build System Notes

Compose support requires:

- Kotlin Android plugin
- `buildFeatures { compose = true }`
- Compose BOM
- `androidx.activity:activity-compose`
- `androidx.navigation:navigation-compose`
- `androidx.compose.material3:material3`

Material 3 Expressive is provided through the Compose Material3 family; some APIs may require experimental opt-ins and version pinning based on AndroidX Material3 release notes.

Miuix can be introduced as a Compose UI library. The official project is `compose-miuix-ui/miuix`, with Android dependency forms such as `top.yukonga.miuix.kmp:miuix-ui-android`.

## Migration Rules

When migrating a page to Compose:

1. Preserve the original class name, package name, Manifest entry, and external launch contract unless the user explicitly approves a breaking change.
2. Preserve all existing SharedPreferences/config keys used by Hook modules.
3. Preserve root shell behavior, restart scope behavior, and system package names exactly unless intentionally changing the behavior.
4. Preserve Xposed metadata and `assets/xposed_init`.
5. Preserve `assets/embedding/**`.
6. Prefer converting one page or dialog at a time to keep regression scope small.
7. Do not delete legacy XML layouts, old adapters, or `nav_graph.xml` simply because a page was migrated; leave cleanup for a dedicated XML View cleanup phase.
8. After a page migration, run `.\gradlew.bat assembleDebug`.
9. Document the completed page, verification result, and next step in the project notes if requested by the user.
10. If committing, include only the current page refactor and related documentation. Do not include build outputs, IDE files, Gradle caches, or unrelated untracked files.

## Areas To Preserve

Do not casually refactor these as part of UI migration:

- `app/src/main/java/com/qimian233/ztool/hook/**`
- `app/src/main/java/com/qimian233/ztool/service/**`
- `app/src/main/java/com/qimian233/ztool/config/**`
- `app/src/main/java/com/qimian233/ztool/EnhancedShellExecutor.java`
- `app/src/main/java/com/qimian233/ztool/utils/MagiskModuleManager.java`
- `app/src/main/java/com/qimian233/ztool/utils/EmbeddingConfigManager.java`
- `app/src/main/assets/embedding/**`
- `app/src/main/assets/xposed_init`
- Xposed-related `AndroidManifest.xml` metadata

These may be wrapped behind repositories or state holders, but behavior must remain compatible.

## Completed Compose Migration State

As of 2026-05-29, these areas have already been migrated or substantially converted to Compose:

- `MainActivity.kt`: hosts the main Compose framework.
- `FeaturesFragment.kt`: feature page.
- `SettingsFragment.kt`: settings home.
- `settingactivity/packageinstaller/packageinstallersettings.kt`: package installer settings.
- `settingactivity/systemframework/FrameworkSettingsActivity.kt`: system framework settings.
- `settingactivity/safecenter/SafeCenterSettingsActivity.kt`: security center settings.
- `settingactivity/gametool/GameToolSettngs.kt`: game tool settings.
- `settingactivity/launcher/LauncherSettingsActivity.kt`: launcher settings, including later dropdown and grid slider fixes.
- `settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt`: lock screen settings.
- `settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt`: status bar settings.
- `settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt`: control center time settings.
- `settingactivity/setting/SettingsDetailActivity.kt`: system settings detail page; remaining config-selection and font-input View dialogs have been migrated to Compose while preserving config delete/flash/restore and font import flows.
- `AuditFragment.kt`: log audit page.
- `settingactivity/ota/OtaSettings.kt`: OTA settings.
- `settingactivity/systemui/systemUISettings.kt`: system UI aggregate settings.
- `HomeFragment.kt`: home page, migrated from Java to Kotlin Compose while preserving Fragment route entry.
- `settingactivity/setting/magicwindowsearch/searchPage.kt`: magic-window strategy search page.
- `settingactivity/setting/floatingwindow/FloatingWindow.kt`: floating-window guide, now using `WindowManager` with `ComposeView`; overlay lifecycle, ViewModelStore, and saved-state owners are explicitly provided to avoid `ViewTreeLifecycleOwner` attach crashes.
- `utils/AppChooserDialog.kt`: reusable app chooser migrated from Java/XML/RecyclerView to Compose while preserving `show(...)`, `AppInfo`, `AppSelectionCallback`, loading, search, multi-select, selected count, and current game tool, launcher, and system settings detail call sites.
- `LoadingDialog.kt`: legacy XML loading dialog migrated to Compose while preserving `show()`, `show(message)`, `dismiss()`, `updateMessage()`, and `isShowing()` behavior for long-running tasks.

Known verification state:

- `.\gradlew.bat assembleDebug` succeeded after the latest recorded dialog migrations and FloatingWindow lifecycle fix.
- Remaining known deprecated warnings are existing compatibility items:
  - `MainActivity.kt`: `statusBarColor`, `navigationBarColor`.
  - `SettingsFragment.kt`: `versionCode`.
  - `StatusBarSettingsActivity.kt`: `MODE_WORLD_READABLE`, retained for Hook compatibility.

## Current Follow-Up Priorities

Use `ComposeRefactor.md` as the source of truth for the detailed migration order, rationale, completed work log, and next-step notes.

DO NOT ADD ANY FOLLOW-UP PLANNING IN AGENTS.MD, ADD THESE INFORMATION IN ComposeRefactor.md

## Current Working Branch

The historical refactor notes refer to branch `compose-refactor`. Confirm the active branch with Git before committing.

## Verification Command

Use this command after UI migrations:

```powershell
.\gradlew.bat assembleDebug
```

The command is expected to build successfully even if the known deprecated warnings listed above remain.
