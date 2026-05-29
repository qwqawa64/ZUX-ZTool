# Compose Refactor Plan

This file is the detailed Compose migration plan and status log for ZUX-ZTool.

Use `AGENTS.MD` for the concise operating rules and preservation boundaries. Use this file to decide the next migration target, understand recent work, and avoid repeating already completed tasks.

## Current Status

Last updated: 2026-05-29.

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

1. `AuditFragment.kt`
   - Extract log loading, filtering, stats, clear, and export coordination into a ViewModel plus log/file repository boundary.
   - Preserve current log file locations and export behavior.

2. `SettingsFragment.kt`
   - Extract settings backup/restore, log-service toggles, and app metadata lookup into a ViewModel/repository boundary.
   - Preserve existing preference keys and SAF launch contracts.

3. `settingactivity/systemui/systemUISettings.kt`
   - Extract System UI aggregate settings state and shell/restart coordination into a ViewModel/repository boundary.
   - Preserve existing preference keys, shell commands, and sub-settings navigation contracts.
