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

### PackageInstallerSettings ViewModel Boundary

`settingactivity/packageinstaller/packageinstallersettings.kt` now delegates package installer preference state and package restart coordination to:

- `viewmodel/PackageInstallerSettingsViewModel.kt`.
- `data/packageinstaller/PackageInstallerSettingsRepository.kt`.

Preserved behavior:

- Existing `packageinstallersettings` class name, package, and Activity launch contract.
- Existing preference keys for APK scan disabling, always allowing permissions, warning-page skipping, installer ad disabling, row-style hook, and install-complete delete behavior.
- Existing restart confirmation dialog and `su -c killall <package>` restart command behavior.
- Existing restart failure Toast behavior.

Implementation note:

- `PackageInstallerSettingsUiState` now lives with `PackageInstallerSettingsViewModel`.
- The Activity now hosts Compose, shows Toast effects, and forwards user actions to the ViewModel.
- `PackageInstallerSettingsRepository` wraps `ModulePreferencesUtils` and package force-stop execution for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `packageinstallersettings` ViewModel/repository extraction.

### SafeCenterSettings ViewModel Boundary

`settingactivity/safecenter/SafeCenterSettingsActivity.kt` now delegates security center preference state and restart command coordination to:

- `viewmodel/SafeCenterSettingsViewModel.kt`.
- `data/safecenter/SafeCenterSettingsRepository.kt`.

Preserved behavior:

- Existing `SafeCenterSettingsActivity` class name, package, and Activity launch contract.
- Existing preference keys for default autorun enablement, Safe Center scan blocking, and DocumentsUI bypass.
- Existing restart confirmation dialog text including the target app package and `com.android.documentsui`.
- Existing root restart flow using `am force-stop <package>`, `am force-stop com.android.documentsui`, and `killall <package>` fallback.
- Existing duplicate restart guard and restart success/failure Toast behavior.

Implementation note:

- `SafeCenterSettingsUiState` now lives with `SafeCenterSettingsViewModel`.
- The Activity now hosts Compose, shows Toast effects, and forwards user actions to the ViewModel.
- `SafeCenterSettingsRepository` wraps `ModulePreferencesUtils` and `EnhancedShellExecutor` for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `SafeCenterSettingsActivity` ViewModel/repository extraction.

### GameToolSettings ViewModel Boundary

`settingactivity/gametool/GameToolSettngs.kt` now delegates game tool preference state, mistake-touch mode mapping, whitelist persistence, managed-game package loading, and package restart coordination to:

- `viewmodel/GameToolSettingsViewModel.kt`.
- `data/gametool/GameToolSettingsRepository.kt`.

Preserved behavior:

- Existing `GameToolSettngs` class name, package, and Activity launch contract.
- Existing preference keys for game audio disabling, TB322FC model disguise, CPU frequency fix, SoC temperature fix, automatic mistake touch, mistake-touch whitelist mode, and whitelist game package storage.
- Existing whitelist string format using comma-separated package names with a trailing comma.
- Existing managed-game package query command: `ls /data/system_ce/0/managed_apps/`.
- Existing `AppChooserDialog` whitelist selection flow and selected package logging.
- Existing restart confirmation dialog and `su -c killall <package>` restart command behavior.
- Existing restart failure Toast behavior.

Implementation note:

- `GameToolSettingsUiState` and `MistakeTouchMode` now live with `GameToolSettingsViewModel`.
- The Activity now hosts Compose, launches `AppChooserDialog`, shows Toast effects, and forwards user actions to the ViewModel.
- `GameToolSettingsRepository` wraps `ModulePreferencesUtils`, `EnhancedShellExecutor`, whitelist serialization, and package force-stop execution for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `GameToolSettngs` ViewModel/repository extraction.

### LauncherSettings ViewModel Boundary

`settingactivity/launcher/LauncherSettingsActivity.kt` now delegates launcher preference state, force-stop mode mapping, whitelist persistence, installed-app package loading, grid value persistence, and package restart coordination to:

- `viewmodel/LauncherSettingsViewModel.kt`.
- `data/launcher/LauncherSettingsRepository.kt`.

Preserved behavior:

- Existing `LauncherSettingsActivity` class name, package, and Activity launch contract.
- Existing preference keys for force-stop disabling, force-stop whitelist enablement, force-stop whitelist packages, dock expansion, custom grid enablement, custom launcher row, and custom launcher column.
- Existing whitelist string format using comma-separated package names with a trailing comma.
- Existing user-installed app filtering behavior, including updated system apps.
- Existing grid value bounds from 3 through 10.
- Existing restart confirmation dialog and `su -c killall <package>` restart command behavior.
- Existing empty-package restart behavior with no Toast, plus success/failure Toast behavior when a package is present.

Implementation note:

- `LauncherSettingsUiState` and `ForceStopMode` now live with `LauncherSettingsViewModel`.
- The Activity now hosts Compose, launches `AppChooserDialog`, shows Toast effects, and forwards user actions to the ViewModel.
- `LauncherSettingsRepository` wraps `ModulePreferencesUtils`, package-manager app filtering, whitelist serialization, grid persistence, and package force-stop execution for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `LauncherSettingsActivity` ViewModel/repository extraction.

### OtaSettings ViewModel Boundary

`settingactivity/ota/OtaSettings.kt` now delegates OTA preference state, current device info loading, OTA XML reading/parsing, firmware lookup, and restart-scope coordination to:

- `viewmodel/OtaSettingsViewModel.kt`.
- `data/ota/OtaSettingsRepository.kt`.

Preserved behavior:

- Existing `OtaSettings` class name, package, and Activity launch contract.
- Existing preference keys: `custom_ota_parameters`, `disable_OtaCheck`, `Custom_ota_target_versionName`, and `Custom_ota_target_deviceID`.
- Existing automatic enablement of `custom_ota_parameters`.
- Existing current version and SN shell lookups.
- Existing OTA package info path and root `cat` behavior.
- Existing OTA XML parsing, locale-aware changelog selection, file-size formatting, and copy text formatting.
- Existing PC flash firmware lookup through `GetPCFlashFirmware`.
- Existing clipboard copy behavior and copied Toasts.
- Existing restart confirmation dialog and restart scope behavior for the OTA package plus `com.lenovo.tbengine`.

Implementation note:

- `OtaSettingsUiState`, `OtaInfoResult`, and `FirmwareResult` now live with `OtaSettingsViewModel`.
- The Activity now hosts Compose, handles clipboard/Toast effects, and forwards user actions to the ViewModel.
- `OtaSettingsRepository` wraps `ModulePreferencesUtils`, `EnhancedShellExecutor`, root file reads, XML parsing, firmware lookup, localized result formatting, and restart-scope execution for this page.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `OtaSettings` ViewModel/repository extraction.

### SettingsDetailActivity ViewModel Boundary, Slice 1

`settingactivity/setting/SettingsDetailActivity.kt` now delegates its basic settings state and simple command coordination to:

- `viewmodel/SettingsDetailViewModel.kt`.
- `data/settings/SettingsDetailRepository.kt`.

Preserved behavior:

- Existing `SettingsDetailActivity` class name, package, and Activity launch contract.
- Existing preference keys for remove blacklist, split-screen mandatory, native permission controller, Dolby display, and always-display suggestions.
- Existing force-resizable global setting command for floating window mandatory mode.
- Existing restart-scope commands for the target package, `com.android.permissioncontroller`, and `com.zui.safecenter`.
- Existing Magisk module install/remove flow, embedding config flashing, font import, overlay guide, magic-window strategy search, and OV config flows remain in the Activity for later smaller slices.

Implementation note:

- `SettingsDetailUiState` now lives with `SettingsDetailViewModel`.
- The Activity now hosts Compose, handles complex dialogs/file pickers, and forwards simple preference and restart actions to the ViewModel.
- `SettingsDetailRepository` wraps `ModulePreferencesUtils`, `EnhancedShellExecutor`, and module-enabled state loading for this first slice.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `SettingsDetailActivity` basic state/restart extraction.

### SettingsDetailActivity Magisk And Flashed Config Persistence Slice

`settingactivity/setting/SettingsDetailActivity.kt` now delegates Magisk module switching, module restore, and flashed config set persistence to:

- `viewmodel/SettingsDetailViewModel.kt`.
- `data/settings/SettingsDetailRepository.kt`.

Preserved behavior:

- Existing Magisk module install/remove behavior through `MagiskModuleManager`.
- Existing restore-original-module behavior, including remove then install.
- Existing success and failure dialog text from the Activity.
- Existing `module_settings` SharedPreferences file name.
- Existing `flashed_configs` string-set key.
- Existing flashed config key format: `timestamp_packageName`.
- Existing config selection dialog rendering and config flashing UI flow remain in the Activity for the next slice.

Implementation note:

- The Activity no longer holds `MagiskModuleManager`.
- The Activity no longer directly reads or writes `flashed_configs`.
- `SettingsDetailViewModel` now returns module and restore result objects while the Activity keeps loading/dialog effects.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Magisk and flashed config persistence extraction.

### SettingsDetailActivity Remaining Business Boundary Slices

`settingactivity/setting/SettingsDetailActivity.kt` now delegates the remaining embedding config, font import, and OV config business operations to:

- `viewmodel/SettingsDetailViewModel.kt`.
- `data/settings/SettingsDetailRepository.kt`.

Preserved behavior:

- Existing config file selection dialog, selected-state handling, disabled already-flashed configs, restore-module entry, and flashed key format.
- Existing embedding config loading, deletion count behavior, config flashing, and flashed config persistence.
- Existing font SAF picker launch contract, font name/description input dialog, temp font copy, font install behavior, and result dialogs.
- Existing OV force split/freeform/fixed modes, launchable package filtering, current selected-package lookup, config update, save behavior, and `AppChooserDialog` flow.
- Existing Activity class name, package, launch contract, user-visible strings, and result dialog sequencing.

Implementation note:

- The Activity no longer directly holds `EmbeddingConfigManager`, `FontInstallerManager`, or an `OvCommonConfigManager` instance.
- The Activity no longer directly loads, deletes, flashes, or persists embedding configs.
- The Activity no longer directly copies temp font files, resolves selected font filenames, or installs fonts.
- The Activity no longer directly loads installed launchable packages or reads/updates/saves OV config XML.
- The Activity remains responsible for Android launchers, overlay permission flow, Compose dialog rendering, loading indicators, Toasts, and result dialogs.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the embedding config load/delete/flash extraction.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the font import extraction.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the OV config extraction.

### MagicWindow Search ViewModel Boundary

`settingactivity/setting/magicwindowsearch/searchPage.kt` now delegates config loading, root file fallback, JSON parsing, and search state to:

- `viewmodel/SearchPageViewModel.kt`.
- `data/settings/MagicWindowSearchRepository.kt`.

Preserved behavior:

- Existing `searchPage` class name, package, and Activity launch contract.
- Existing module config path: `/data/system/zui/embedding/embedding_config.json`.
- Existing asset fallback: `assets/embedding/embedding_config.json`.
- Existing search by package `name`.
- Existing empty-result Toast and empty result card behavior.
- Existing package details dialog and displayed fields.
- Existing root `su` + `cat` file read behavior, now behind the repository boundary.

Implementation note:

- `SearchPageUiState` now lives with `SearchPageViewModel`.
- The Activity now hosts Compose, shows the empty-result Toast, opens package details, and forwards user actions to the ViewModel.
- `MagicWindowSearchRepository` wraps root config reading, asset fallback reading, JSON storage, and search result creation.
- Config loading now runs off the Activity initialization path in the ViewModel.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `searchPage` ViewModel/repository extraction.

### FloatingWindow ViewModel Boundary

`settingactivity/setting/floatingwindow/FloatingWindow.kt` now delegates foreground app/activity lookup, wizard state, generated config creation, and Base64 config persistence to:

- `viewmodel/FloatingWindowViewModel.kt`.
- `data/settings/FloatingWindowRepository.kt`.

Preserved behavior:

- Existing `FloatingWindow` class name, package, and `FloatingWindow(this)` launch contract from `SettingsDetailActivity`.
- Existing overlay `ComposeView` lifecycle, ViewModelStore, saved-state owner, custom recomposer, and close/hide behavior.
- Existing wizard sequence, selected-app blocking behavior, add-current-activity flow, tutorial video display, and option defaults.
- Existing root shell foreground lookup command and fallback behavior.
- Existing generated embedding config JSON shape.
- Existing Base64 config storage path under app files `data/custom_EmbeddingConfig`.

Implementation note:

- The overlay class now owns only WindowManager, ComposeView hosting, periodic refresh scheduling, Toast effects, and close effects.
- `FloatingWindowUiState` and `FloatingWizardStep` now live with `FloatingWindowViewModel`.
- `FloatingWindowRepository` wraps package label lookup, usage-stats fallback, root/runtime foreground lookup, JSON generation, and file persistence.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the `FloatingWindow` ViewModel/repository extraction.

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

Current feasibility assessment:

- The codebase can support this phase, but it needs a theme settings model before real multi-style switching is useful.
- `ZToolTheme.kt` already has `FrontendStyle`, `ZToolThemeSpec`, `LocalZToolThemeSpec`, Material 3 color schemes, and Android 12+ dynamic color calls.
- `FrontendStyle.Miuix` currently exists only as a placeholder and still resolves to the Material 3 color scheme.
- Many screens already use `MaterialTheme.colorScheme`, which makes dynamic color, user color, and AMOLED overrides practical.
- Several screens still call Material 3 primitives such as `Scaffold`, `TopAppBar`, `NavigationRail`, `Switch`, and `Card` directly. These should gradually move behind project components before a real Miuix style is enabled.

Define a project-level style model:

```kotlin
enum class FrontendStyle {
    Material3Expressive,
    Miuix
}
```

The model should stay open for future themes by keeping style-specific behavior in the theme/component layer rather than in business screens.

Add a persisted theme settings shape before wiring style switches into pages:

```kotlin
data class ZToolThemeSettings(
    val frontendStyle: FrontendStyle,
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val amoledBlackEnabled: Boolean,
    val manualColorEnabled: Boolean,
    val manualSeedColor: Long
)

enum class ThemeMode {
    FollowSystem,
    Light,
    Dark
}
```

Store these preferences behind a small repository, for example `ThemePreferencesRepository`, rather than reading `SharedPreferences` directly inside `ZToolTheme` or screens.

Color behavior:

- Android 12+ Monet dynamic color should be used only when `dynamicColorEnabled` is true and manual color is not enabled.
- Manual color should generate or derive a complete Material 3 `ColorScheme` from a seed color instead of only replacing `primary`.
- AMOLED pure black should be a dark-theme post-processing step that overrides `background`, `surface`, and `surfaceContainer*` roles to black or near-black consistently.
- Semantic colors, such as log severity and user-selected color previews, may remain hard-coded when they represent data rather than theme chrome.

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

Recommended Phase 3 implementation order:

1. Introduce `ZToolThemeSettings`, `ThemeMode`, and a repository-backed source of persisted theme preferences.
2. Update `ZToolTheme` to consume `ZToolThemeSettings` and resolve the final `ColorScheme` from theme mode, Monet, manual seed color, and AMOLED black options.
3. Keep `FrontendStyle.Material3Expressive` as the first fully functional style and verify dynamic color/manual color/AMOLED behavior there.
4. Expand shared components (`ZToolScaffold`, `ZToolCard`, `ZToolSwitchRow`, `ZToolDropdownField`, `ZListItem`, `ZDialog`) so screens can avoid direct Material 3 component usage.
5. Add the Miuix dependency and implement Miuix rendering inside the shared components and theme adapter only.
6. Pilot the adapter on a small already-migrated settings page before applying it to larger pages such as System UI, launcher, and settings detail.

Do not start by adding `if (style == FrontendStyle.Miuix)` branches inside every screen. That would make future themes expensive and would violate the intended UI-layer boundary.

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

Execute Phase 3 first. The planned Phase 2 heavy-screen ViewModel/repository boundary pass is now complete for the active Compose screens listed in this document, including the floating-window guide. Before continuing broad settings-page model work in Phase 5, establish the theme settings model and style adapter layer so future screens can consume stable project components.

Recommended next order:

1. Introduce persisted theme settings.
   - Add `ZToolThemeSettings` and `ThemeMode`.
   - Add a small `ThemePreferencesRepository` or equivalent wrapper for frontend style, theme mode, dynamic color, AMOLED black, manual color enabled, and manual seed color.
   - Keep these keys scoped to app UI preferences and do not change Hook/module preference keys.

2. Update `ZToolTheme` to resolve the final Material 3 theme from settings.
   - Support follow-system, light, and dark modes.
   - Use Android 12+ Monet only when dynamic color is enabled and manual color is disabled.
   - Support manual seed color as a complete `ColorScheme` source rather than changing only `primary`.
   - Apply AMOLED pure black as a dark-theme post-processing step over `background`, `surface`, and `surfaceContainer*`.

3. Wire the top-level app shell to the persisted theme settings.
   - Load theme settings before calling `ZToolTheme` in `MainActivity`.
   - Keep existing launch contracts, Fragment navigation, and system-bar behavior compatible.
   - Make sure dialogs and overlay Compose surfaces still receive the same theme context.

4. Expand the shared component adapter layer.
   - Add or refine `ZToolScaffold`, `ZToolTopAppBar`, `ZToolNavigationRail`, `ZListItem`, and `ZDialog`.
   - Keep style selection inside components through `LocalZToolThemeSpec`.
   - Do not add screen-level branches such as `if (style == FrontendStyle.Miuix)`.

5. Add the Miuix dependency only after the Material 3 theme settings path is verified.
   - Implement Miuix rendering behind shared components and theme adapters.
   - Keep Material 3 Expressive as the first complete and verified style.
   - Treat Miuix as a component-layer alternative, not a separate business-screen implementation.

6. Pilot Phase 3 on a small already-migrated settings page.
   - Prefer package installer or safe center before larger pages.
   - Preserve the existing ViewModel/repository boundary.
   - Run `.\gradlew.bat assembleDebug` after each slice and record the result here.
