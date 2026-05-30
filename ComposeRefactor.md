# Compose Refactor Plan

This file is the detailed Compose migration plan and status log for ZUX-ZTool.

Use `AGENTS.MD` for the concise operating rules and preservation boundaries. Use this file to decide the next migration target, understand recent work, and avoid repeating already completed tasks.

## Current Status

Last updated: 2026-05-30.

The project is in a gradual UI-layer migration from XML/View/Fragment screens to Jetpack Compose. Hook modules, Xposed metadata, runtime assets, service behavior, shell behavior, existing preference keys, and external launch contracts must remain compatible.

The latest verified command is:

```powershell
.\gradlew.bat assembleDebug
```

It succeeds. Remaining warnings are known compatibility warnings:

- `MainActivity.kt`: deprecated `statusBarColor` and `navigationBarColor`.
- `SettingsFragment.kt`: deprecated `versionCode`.
- `StatusBarSettingsActivity.kt`: deprecated `MODE_WORLD_READABLE`, retained for Hook compatibility.

## Completed Compose Work

The following areas are migrated or substantially converted to Compose:

- `MainActivity.kt`: main Compose host framework.
- `FeaturesFragment.kt`: feature page.
- `SettingsFragment.kt`: settings home.
- `AuditFragment.kt`: log audit page.
- `HomeFragment.kt`: home page, migrated from Java to Kotlin Compose while preserving the Fragment route.
- `settingactivity/packageinstaller/packageinstallersettings.kt`: package installer settings.
- `settingactivity/systemframework/FrameworkSettingsActivity.kt`: system framework settings.
- `settingactivity/safecenter/SafeCenterSettingsActivity.kt`: security center settings.
- `settingactivity/gametool/GameToolSettngs.kt`: game tool settings.
- `settingactivity/launcher/LauncherSettingsActivity.kt`: launcher settings, including dropdown and grid slider fixes.
- `settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt`: lock screen settings.
- `settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt`: status bar settings.
- `settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt`: control center time settings.
- `settingactivity/systemui/systemUISettings.kt`: system UI aggregate settings.
- `settingactivity/ota/OtaSettings.kt`: OTA settings.
- `settingactivity/setting/SettingsDetailActivity.kt`: system settings detail page.
- `settingactivity/setting/magicwindowsearch/searchPage.kt`: magic-window strategy search page.
- `settingactivity/setting/floatingwindow/FloatingWindow.kt`: floating-window guide.
- `utils/AppChooserDialog.kt`: reusable app chooser dialog.
- `utils/CountdownDialog.kt`: reusable countdown confirmation dialog.
- `LoadingDialog.kt`: reusable loading dialog.

## Latest Migration Notes

### FloatingWindow

`settingactivity/setting/floatingwindow/FloatingWindow.kt` was migrated to a `WindowManager` overlay backed by `ComposeView`.

Latest fix:

- Fixed the fatal `IllegalStateException: Composed into the View which doesn't propagate ViewTreeLifecycleOwner!`.
- The overlay `ComposeView` now receives explicit lifecycle, ViewModelStore, and saved-state owners before attach.
- Existing wizard steps, foreground activity polling, tutorial video playback, Base64 config generation, and close/hide behavior remain intact.

### AppChooserDialog

`utils/AppChooserDialog.java` was replaced by `utils/AppChooserDialog.kt`.

Preserved behavior:

- Public `AppChooserDialog.show(...)` overloads.
- `AppInfo`.
- `AppSelectionCallback`.
- User app loading.
- Search by app label or package name.
- Multi-select.
- Existing selected packages shown first.
- Selected count.
- Existing call sites in game tool, launcher, and system settings detail flows.

Implementation note:

- The internals are now Compose-based, but the external API remains command-style for compatibility with current Activities.

### LoadingDialog

`LoadingDialog.java` was replaced by `LoadingDialog.kt`.

Preserved behavior:

- `show()`.
- `show(message)`.
- `dismiss()`.
- `updateMessage()`.
- `isShowing()`.
- Long-running task loading behavior in `SettingsDetailActivity`.

Implementation note:

- The dialog now uses Compose state for the message and a Compose progress indicator.

### CountdownDialog

`utils/CountdownDialog.java` was replaced by `utils/CountdownDialog.kt`.

Preserved behavior:

- Public `CountdownDialog.Builder(...)` construction.
- `OnCountdownFinishListener`.
- Configurable title, message, positive text, negative text, countdown seconds, and cancelable state.
- Disabled positive action until the countdown completes.
- `onCountdownFinished()`, `onPositiveButtonClick()`, and `onNegativeButtonClick()` callbacks.
- Existing first-launch agreement flow in `MainActivity`.

Implementation note:

- The dialog now uses a `ComposeView` hosted by `MaterialAlertDialogBuilder`, with Compose state driving the remaining countdown and button enabled state.
- Explicit lifecycle, ViewModelStore, and saved-state owners are attached when the context provides them.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29.

### FeaturesFragment Card Height

`FeaturesFragment.kt` cards now use a single fixed height constant and let card content fill that height.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29.

### SettingsDetailActivity Dialogs

The remaining View-inflated dialogs in `SettingsDetailActivity.kt` were migrated to Compose:

- `dialog_config_selection.xml` flow.
- `dialog_font_input.xml` flow.

Preserved behavior:

- Config file selection.
- Multi-select state.
- Flashed configs are shown as disabled/already selected.
- Delete selected config files.
- Flash selected config files.
- Restore module flow.
- Font name and description input.
- Existing root/Magisk-related behavior.

Legacy XML resources were intentionally left in place for a later cleanup phase.

### SystemUISettings State Shape

`settingactivity/systemui/systemUISettings.kt` now uses a single immutable `SystemUiSettingsUiState` for screen state instead of separate Activity-level mutable Compose fields.

Preserved behavior:

- Existing Activity class name and package.
- Existing preference keys: `ForceNativeAOD`, `ForceLenovoAOD`, `No_ChargeAnimation`, `charge_animation_fix`, and `guest_mode_controller`.
- Existing shell commands for AOD settings, Lenovo AOD settings, and restart scope behavior.
- Existing sub-settings navigation contracts.

Implementation note:

- The screen composable now consumes one `state` object, reducing parameter sprawl and preparing the page for a future ViewModel/repository extraction.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29.

### Settings Screen UiState Consolidation

The following settings screens now consume a single screen state object instead of separate boolean/state parameters:

- `settingactivity/ota/OtaSettings.kt`: `OtaSettingsUiState`.
- `settingactivity/setting/SettingsDetailActivity.kt`: `SettingsDetailUiState`.
- `settingactivity/setting/magicwindowsearch/searchPage.kt`: `SearchPageUiState`.
- `settingactivity/packageinstaller/packageinstallersettings.kt`: `PackageInstallerSettingsUiState`.
- `settingactivity/safecenter/SafeCenterSettingsActivity.kt`: `SafeCenterSettingsUiState`.
- `settingactivity/gametool/GameToolSettngs.kt`: `GameToolSettingsUiState`.
- `settingactivity/launcher/LauncherSettingsActivity.kt`: `LauncherSettingsUiState`.
- `settingactivity/systemframework/FrameworkSettingsActivity.kt`: `FrameworkSettingsUiState`.
- `settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt`: `ControlCenterSettingsUiState`.
- `settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt`: `StatusBarSettingsUiState`.
- `settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt`: `LockScreenSettingsUiState`.

Preserved behavior:

- Existing Activity class names and packages.
- Existing preference keys for OTA disable check and custom OTA parameters.
- Existing preference keys for system settings detail hooks, embedding options, permissions, Dolby display, and suggestions.
- Existing preference keys for package installer and safe center hooks.
- Existing preference keys for game tool hooks and mistake-touch whitelist configuration.
- Existing preference keys for launcher force-stop, dock, and grid configuration.
- Existing preference keys for framework, AI input expansion, and secure flag configuration.
- Existing preference keys for control center date, text size, spacing, color, and bold configuration.
- Existing preference keys for status bar clock, notification icon, network speed, and battery configuration.
- Existing preference keys for lock screen YiYan, charge watts, real watts interval, and SystemUI permission confirmation.
- Existing restart confirmation and restart scope behavior.
- Existing root/shell behavior.
- Existing OTA info parsing, firmware query, clipboard, and restart-scope behavior.
- Existing settings detail config flashing, font import, overlay guide, strategy search, Magisk, and OV config behavior.
- Existing magic-window strategy search JSON loading, root config fallback, result display, and details dialog behavior.

Implementation note:

- This continues the interim goal of making screens consume one state object before introducing ViewModels.
- ViewModel and repository extraction remains intentionally deferred until the active screens have consistent `UiState` shapes.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the OTA settings consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the settings detail and magic-window search consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the package installer and safe center consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the game tool consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the launcher consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the framework consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the control center consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the status bar consolidation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the lock screen consolidation.

### Fragment Screen UiState Consolidation

The following Fragment-level Compose screens now consume one screen state object:

- `AuditFragment.kt`: `AuditUiState`.
- `SettingsFragment.kt`: `SettingsUiState`.
- `HomeFragment.kt`: `HomeUiState`, including config-upgrade and reboot dialog flags.

Preserved behavior:

- Existing Fragment class names, packages, and main navigation contracts.
- Existing log parsing, filtering, statistics, clear, export, and copy behavior in `AuditFragment.kt`.
- Existing settings backup, restore, default restore, log service, detailed logging, homepage YiYan, about, and external-link behavior in `SettingsFragment.kt`.
- Existing environment checks, root/framework/system info, update check, config upgrade prompt, homepage hint, reboot menu, and shell behavior in `HomeFragment.kt`.

Implementation note:

- This completes the current pre-ViewModel consolidation pass for the listed Fragment-level screens.
- The next migration step should move selected heavy Fragment business logic behind ViewModels and repository wrappers rather than adding more Activity/Fragment-local state.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the Fragment-level consolidation.

### HomeFragment ViewModel Boundary

`HomeFragment.kt` now delegates business state and background work to:

- `viewmodel/HomeViewModel.kt`.
- `data/home/HomeRepository.kt`.

Preserved behavior:

- Existing `HomeFragment` class name, package, Fragment route, and `EnvironmentStateListener` contract.
- Existing environment/root/module checks, module status display, system info display, homepage YiYan hint, update check, update ignore preference key, config-upgrade prompt, reboot menu, and reboot shell commands.
- Existing shell executor usage remains behavior-compatible behind the repository boundary.

Implementation note:

- `HomeUiState`, `UpdateInfo`, and `RebootTarget` now live with `HomeViewModel`.
- `HomeFragment` now hosts Compose, handles the reboot menu anchor, opens external update URLs, shows Toasts, and forwards user actions to the ViewModel.
- `HomeRepository` wraps shell, network, package metadata, config upgrade, and preference access for the home screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the `HomeFragment` ViewModel/repository extraction.

Follow-up fix:

- Root availability could be detected, so the reboot button appeared, but the rest of the home screen still showed the missing Root/LSPosed state.
- Root cause: the ViewModel extraction moved the self-check implementation away from `HomeFragment.isModuleActive()`, while `hook/HookInit.java` still hooks that exact method and replaces it with `true` when the module is active.
- Fix: keep `HomeFragment.isModuleActive()` as the stable LSPosed self-check hook target and inject it into `HomeRepository` as `moduleActiveChecker`.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after restoring the self-check compatibility path.

### AuditFragment ViewModel Boundary

`AuditFragment.kt` now delegates log state and file work to:

- `viewmodel/AuditViewModel.kt`.
- `data/audit/AuditRepository.kt`.

Preserved behavior:

- Existing `AuditFragment` class name, package, Fragment route, and Compose UI surface.
- Existing log file location under app files `Log`.
- Existing hook log file matching for `hook_log_*.txt`.
- Existing log parsing, timestamp sorting, category/module/level/search/error-only filtering, statistics, clear, export, detail, and copy behavior.
- Existing SAF export launcher contract and exported zip filename shape.

Implementation note:

- `AuditUiState` and `ModuleOption` now live with `AuditViewModel`.
- `AuditFragment` now hosts Compose, handles the SAF document callback, copies log details to clipboard, shows Toasts, and forwards user actions to the ViewModel.
- `AuditRepository` wraps `LogParser`, log directory access, zip creation, SAF export, and localized status/statistics/detail text.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the `AuditFragment` ViewModel/repository extraction.

### SettingsFragment ViewModel Boundary

`SettingsFragment.kt` now delegates settings state and configuration work to:

- `viewmodel/SettingsViewModel.kt`.
- `data/settings/SettingsRepository.kt`.

Preserved behavior:

- Existing `SettingsFragment` class name, package, Fragment route, and Compose UI surface.
- Existing preference keys: `isDetailedLogging` and `enable_homepage_yiyan`.
- Existing settings backup and restore through SAF document launchers.
- Existing default-restore behavior through `ModulePreferencesUtils.clearAllSettings()`.
- Existing log service start/stop behavior through `LogServiceManager`.
- Existing about dialog links and external app/package handling.

Implementation note:

- `SettingsUiState` now lives with `SettingsViewModel`.
- `SettingsFragment` now hosts Compose, handles SAF callbacks, opens external links, shows Toasts, and forwards user actions to the ViewModel.
- `SettingsRepository` wraps config backup/restore, log-service state, preference reads/writes, and app version lookup.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the `SettingsFragment` ViewModel/repository extraction.

### SystemUISettings ViewModel Boundary

`settingactivity/systemui/systemUISettings.kt` now delegates aggregate System UI settings state and shell coordination to:

- `viewmodel/SystemUiSettingsViewModel.kt`.
- `data/systemui/SystemUiSettingsRepository.kt`.

Preserved behavior:

- Existing `systemUISettings` class name, package, Activity launch contract, and sub-settings navigation.
- Existing preference keys: `ForceNativeAOD`, `ForceLenovoAOD`, `No_ChargeAnimation`, `charge_animation_fix`, and `guest_mode_controller`.
- Existing root shell commands for native AOD, Lenovo AOD settings, app process restart, and wallpaper settings restart.
- Existing restart confirmation dialog and restart result Toast behavior.

Implementation note:

- `SystemUiSettingsUiState` now lives with `SystemUiSettingsViewModel`.
- The Activity now hosts Compose, opens sub-settings routes, shows Toasts, and forwards user actions to the ViewModel.
- `SystemUiSettingsRepository` wraps `ModulePreferencesUtils` and `EnhancedShellExecutor` for this aggregate page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-29 after the `systemUISettings` ViewModel/repository extraction.

### StatusBarSettings ViewModel Boundary

`settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt` now delegates status bar preference state to:

- `viewmodel/StatusBarSettingsViewModel.kt`.
- `data/systemui/StatusBarSettingsRepository.kt`.

Preserved behavior:

- Existing `StatusBarSettingsActivity` class name, package, and Activity launch contract.
- Existing preference keys for status bar seconds, custom clock format/style, notification icon limit/native icon, network speed size/double layer, and external battery percentage.
- Existing world-readable `StatusBar_notifyNumSize` preference file behavior for Hook compatibility.
- Existing custom clock preview formatting and invalid-format fallback.
- Existing format help copy action, color picker dialog, save confirmation dialog, and Toast behavior.

Implementation note:

- `StatusBarSettingsUiState` now lives with `StatusBarSettingsViewModel`.
- The Activity now hosts Compose, handles clipboard/Toast effects, and forwards user actions to the ViewModel.
- `StatusBarSettingsRepository` wraps `ModulePreferencesUtils`, `StatusBar_notifyNumSize`, and clock preview formatting for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded from Android Studio on 2026-05-30 after the `StatusBarSettingsActivity` ViewModel/repository extraction.
- Remaining warning: deprecated `MODE_WORLD_READABLE`, retained for Hook compatibility.

### LockScreenSettings ViewModel Boundary

`settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt` now delegates lock screen preference state, YiYan API testing, and charge-watt option coordination to:

- `viewmodel/LockScreenSettingsViewModel.kt`.
- `data/systemui/LockScreenSettingsRepository.kt`.

Preserved behavior:

- Existing `LockScreenSettingsActivity` class name, package, and Activity launch contract.
- Existing preference keys for YiYan, owner info, YiYan API URL/regex, charge watts, real watts, custom refresh interval, selected watts option, and SystemUI permission confirmation.
- Existing charge-watts option behavior, including disabled/handshake/actual mutual exclusivity.
- Existing SystemUI root-permission explanation dialog and "do not show again" confirmation behavior.
- Existing YiYan API test request, regex extraction, response/error dialogs, and configuration save behavior.

Implementation note:

- `LockScreenSettingsUiState` and `ApiTestResult` now live with `LockScreenSettingsViewModel`.
- The Activity now hosts Compose, shows Toast effects, and forwards user actions to the ViewModel.
- `LockScreenSettingsRepository` wraps `ModulePreferencesUtils`, HTTP testing, regex extraction, and localized result text for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `LockScreenSettingsActivity` ViewModel/repository extraction.

### ControlCenterSettings ViewModel Boundary

`settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt` now delegates control center date preference state and date-preview formatting to:

- `viewmodel/ControlCenterSettingsViewModel.kt`.
- `data/systemui/ControlCenterSettingsRepository.kt`.

Preserved behavior:

- Existing `ControlCenterSettingsActivity` class name, package, and Activity launch contract.
- Existing preference keys for custom control center date, date format, text size, letter spacing, text color, and bold style.
- Existing date format preview and invalid-format fallback behavior.
- Existing format help copy action, color picker dialog, and save confirmation dialog behavior.

Implementation note:

- `ControlCenterSettingsUiState` now lives with `ControlCenterSettingsViewModel`.
- The Activity now hosts Compose, handles clipboard/Toast effects, and forwards user actions to the ViewModel.
- `ControlCenterSettingsRepository` wraps `ModulePreferencesUtils` and `CustomDateFormatter` for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `ControlCenterSettingsActivity` ViewModel/repository extraction.

### FrameworkSettings ViewModel Boundary

`settingactivity/systemframework/FrameworkSettingsActivity.kt` now delegates framework preference state, AI input expansion validation, and restart command coordination to:

- `viewmodel/FrameworkSettingsViewModel.kt`.
- `data/systemframework/FrameworkSettingsRepository.kt`.

Preserved behavior:

- Existing `FrameworkSettingsActivity` class name, package, and Activity launch contract.
- Existing preference keys for keep rotation, package visibility, secure flag disabling, AI input expansion, and AI input expansion signs.
- Existing AI input signs validation behavior, including rejecting full-width commas and empty comma-separated entries.
- Existing restart confirmation countdown, root reboot command, and restart failure Toast behavior.

Implementation note:

- `FrameworkSettingsUiState` now lives with `FrameworkSettingsViewModel`.
- The Activity now hosts Compose, shows Toast effects, and forwards user actions to the ViewModel.
- `FrameworkSettingsRepository` wraps `ModulePreferencesUtils`, AI input validation, and the root reboot command for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `FrameworkSettingsActivity` ViewModel/repository extraction.

## Full Refactor Roadmap

### Phase 1. Build the Compose Shell Without Touching Hook Logic

Keep `hook/`, `service/`, `utils/`, and `config/` behavior intact. Add or continue consolidating Kotlin UI architecture under packages such as:

```text
app/src/main/java/com/qimian233/ztool/
  ui/
    ZToolApp.kt
    navigation/
    theme/
    components/
    screens/
      home/
      features/
      audit/
      settings/
      module/
  data/
    preference/
    shell/
    update/
    log/
  viewmodel/
```

The Compose shell should become the main UI surface while preserving existing launch entry points during migration.

### Phase 2. Extract Business State From Fragments And Activities

Prioritize heavy logic pages such as `HomeFragment` and `settingactivity/systemui/systemUISettings.kt`.

The target shape is:

- Screens consume stable `UiState`.
- Screens dispatch events to a `ViewModel`.
- UI code does not directly run shell commands.
- UI code does not directly read or write preferences.
- UI code does not create raw threads for business work.

Use repository or manager wrappers for shell execution, logs, config, preferences, update checks, and system services.

### Phase 3. Implement The Frontend Style Adapter Layer

Define a project-level style model:

```kotlin
enum class FrontendStyle {
    MaterialExpressive,
    Miuix
}
```

Feature screens should call project components instead of branching on style directly:

```kotlin
ZSwitchRow(
    title = stringResource(R.string.xxx),
    checked = state.enabled,
    onCheckedChange = viewModel::setEnabled
)
```

Components and theme adapters, such as `ZToolTheme`, `ZToolScaffold`, `ZToolCard`, `ZToolDropdownField`, `ZSwitchRow`, `ZListItem`, and `ZDialog`, should choose the Material 3 Expressive, Miuix, or future ZUX/ZUI rendering internally.

Avoid scattering checks such as `if (style == FrontendStyle.Miuix)` through business screens.

### Phase 4. Migrate Main Navigation

Replace the active `MainActivity` + `nav_graph.xml` + `BottomNavigationView` + Fragment navigation surface with Compose navigation:

```kotlin
setContent {
    ZToolTheme(style = selectedStyle) {
        ZToolNavHost()
    }
}
```

Bottom navigation, top bars, page transitions, and route state should be implemented in Compose.

Preserve compatibility for existing external launch contracts while moving the main in-app navigation to `navigation-compose`.

### Phase 5. Migrate Settings Pages Through A Shared Settings Model

Create a shared settings item model before migrating more setting pages:

```kotlin
sealed interface SettingItem {
    data class Switch(...)
    data class Entry(...)
    data class Slider(...)
    data class TextInput(...)
    data class Category(...)
}
```

Each settings page should declare data and behavior through the model, and Compose should render the common rows consistently.

This should speed up migration and reduce duplicated UI logic for:

- `systemui`.
- `gametool`.
- `launcher`.
- `ota`.
- `packageinstaller`.

Preserve all existing preference keys used by Hook modules.

### Phase 6. Clean Up The XML/View Layer

Only start this phase after active UI routes no longer depend on the old View layer.

Cleanup candidates:

- `res/layout/activity_*.xml`.
- `res/layout/fragment_*.xml`.
- `res/navigation/nav_graph.xml`.
- Old adapters.
- Obsolete Fragment classes.
- Unneeded AppCompat or Material View dependencies.

Do not delete XML resources or legacy classes during ordinary page migrations unless the cleanup is explicitly part of the scoped task.

## Preservation Boundaries

Keep these areas behavior-compatible throughout the Compose migration:

- `hook/**`: must not participate in UI refactors.
- `service/**`: expose state to ViewModels where needed, but preserve service behavior.
- `utils/EnhancedShellExecutor`, `utils/MagiskModuleManager`, and `utils/EmbeddingConfigManager`: may be wrapped by repositories, but their behavior should remain compatible.
- `assets/embedding/**`: must remain.
- `assets/xposed_init`: must remain.
- Xposed metadata in `AndroidManifest.xml`: must remain.

## Verification Policy

After each page, dialog, or shared UI migration:

```powershell
.\gradlew.bat assembleDebug
```

Record the completed target, verification result, and next planned target in this file when requested.

## Current Recommended Next Target

Begin Phase 2 ViewModel/repository extraction now that the active Compose screens consume stable `UiState` objects.

Recommended next order:

1. `settingactivity/packageinstaller/packageinstallersettings.kt`
   - Extract package installer preference state into a ViewModel/repository boundary.
   - Preserve existing preference keys and package installer hook behavior.
2. `settingactivity/safecenter/SafeCenterSettingsActivity.kt`
   - Extract security center preference state into a ViewModel/repository boundary.
   - Preserve existing preference keys and hook behavior.
3. `settingactivity/gametool/GameToolSettngs.kt`
   - Extract game tool preference and whitelist state into a ViewModel/repository boundary.
   - Preserve mistake-touch whitelist configuration and existing app chooser behavior.
