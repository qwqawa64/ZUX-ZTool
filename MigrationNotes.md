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

### Phase 3 Theme Settings Foundation

The first Phase 3 slice introduced persisted app UI theme settings through:

- `ui/theme/ZToolThemeSettings.kt`.
- `ui/theme/ThemeMode.kt`.
- `data/theme/ThemePreferencesRepository.kt`.

Preserved behavior:

- Existing `ZToolTheme` rendering path is not changed yet.
- Existing `MainActivity` launch contract and Fragment navigation are unchanged.
- Existing Hook/module preference keys are untouched.
- Theme preferences are stored in the app UI-only preference file `ztool_ui_theme_preferences`.

Implementation note:

- `ZToolThemeSettings` covers frontend style, follow-system/light/dark mode, Monet dynamic color, AMOLED black, manual color enablement, and manual seed color.
- `ThemePreferencesRepository` wraps persistence for those values and falls back to defaults when stored enum names are missing or stale.
- This slice intentionally does not wire settings into `ZToolTheme`; that is the next Phase 3 task.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after adding the theme settings model and repository.

### Phase 3 ZToolTheme Settings Resolution

The second Phase 3 slice updated `ui/theme/ZToolTheme.kt` so the theme layer can resolve a final Material 3 color scheme from `ZToolThemeSettings`.

Preserved behavior:

- Existing `ZToolTheme { ... }` call sites remain source-compatible.
- Existing default Material 3 Expressive rendering remains the default path.
- Existing `FrontendStyle.Miuix` remains a placeholder that falls back to Material 3 colors until Miuix is introduced behind shared components.
- Existing `MainActivity` launch contract, Fragment navigation, Hook/module preferences, and runtime assets are unchanged.

Implementation note:

- `ZToolTheme` now accepts an optional `settings: ZToolThemeSettings`.
- Theme mode now resolves follow-system, light, and dark behavior.
- Monet dynamic color is used only when enabled, supported by the platform, and manual color is disabled.
- Manual color now derives a full Material 3 `ColorScheme` from the configured seed color.
- AMOLED pure black is applied as a dark-theme post-processing step over background and surface container roles.
- The persisted settings repository is still not wired into `MainActivity`; that remains the next slice.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after updating `ZToolTheme` settings resolution.

### Phase 3 Top-Level Theme Settings Wiring

The third Phase 3 slice wired the top-level app shell to the persisted app UI theme settings through:

- `MainActivity.kt`.
- `data/theme/ThemePreferencesRepository.kt`.
- `ui/theme/ZToolTheme.kt`.

Preserved behavior:

- Existing `MainActivity` class name, launch contract, Fragment navigation, and first-run agreement flow.
- Existing `ZToolTheme` defaults for callers that do not provide settings.
- Existing Hook/module preference keys and runtime assets.
- Existing system-bar transparent setup, including the known deprecated `statusBarColor` and `navigationBarColor` warnings.

Implementation note:

- `MainActivity` now loads `ZToolThemeSettings` from `ThemePreferencesRepository` before calling `ZToolTheme`.
- The same settings are used to resolve light/dark system-bar icon appearance.
- Theme settings are read from the dedicated app UI preference file and are not yet editable from a settings page.
- Dialog and overlay surfaces still use their existing theme call sites; shared propagation and user-facing controls remain later Phase 3 work.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after wiring `MainActivity` to persisted theme settings.

Follow-up fix:

- Theme hot switching updated the main navigation rail, but Fragment-hosted Compose pages could disappear or fail to repaint because those pages own separate `ComposeView` trees and used default `ZToolTheme { ... }` calls outside the top-level composition.
- `ThemePreferencesRepository` now exposes a preference observer for app UI theme settings.
- Default `ZToolTheme { ... }` calls now observe `ThemePreferencesRepository` when explicit settings are not supplied, so Fragment, dialog, and overlay Compose trees can recompose on theme changes.
- `MainActivity` also observes theme settings and updates system-bar icon appearance when theme mode changes.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the hot-switch observer fix.

### Phase 3 Shared Component Adapter Foundation

The fourth Phase 3 task is expanding the shared component adapter layer. Its goal is to make feature screens call project components while the component/theme layer decides Material 3 Expressive, Miuix, or future style rendering.

Current status: in progress.

Completed shared component surface:

- `ui/components/ZToolScaffold.kt`.
- `ui/components/ZToolDialog.kt`.
- `ui/components/ZToolSettings.kt`.

Completed component wrappers:

- `ZToolScaffold` for ordinary page scaffolds.
- `ZToolTabletScaffold` for tablet-style rail + content layouts.
- `ZToolTopAppBar`, including navigation icon and action slots.
- `ZToolNavigationRail`.
- `ZToolNavigationRailItem`.
- `ZToolCard`.
- `ZToolSwitchRow`.
- `ZToolDropdownField`.
- `ZListItem`.
- `ZToolDialog`.
- `ZToolDialogSurface`.

Completed pilots:

- `MainActivity.kt`.
- `settingactivity/packageinstaller/packageinstallersettings.kt`.
- `settingactivity/safecenter/SafeCenterSettingsActivity.kt`.

Completed broad page migration:

- `HomeFragment.kt`.
- `AuditFragment.kt`.
- `SettingsFragment.kt`.
- `settingactivity/gametool/GameToolSettngs.kt`.
- `settingactivity/launcher/LauncherSettingsActivity.kt`.
- `settingactivity/systemframework/FrameworkSettingsActivity.kt`.
- `settingactivity/systemui/systemUISettings.kt`.
- `settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt`.
- `settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt`.
- `settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt`.
- `settingactivity/ota/OtaSettings.kt`.
- `settingactivity/setting/SettingsDetailActivity.kt`.
- `settingactivity/setting/magicwindowsearch/searchPage.kt`.

Preserved behavior:

- Existing `MainActivity` class name, launch contract, Fragment navigation, and destination IDs.
- Existing navigation rail labels, icons, selected destination behavior, and environment-ready gating.
- Existing package installer Activity class name, package name, launch contract, ViewModel/repository boundary, preference keys, restart confirmation behavior, and package force-stop behavior.
- Existing safe center Activity class name, package name, launch contract, ViewModel/repository boundary, preference keys, restart confirmation behavior, and package restart behavior.
- Existing broad page Activity/Fragment class names, launch contracts, ViewModel/repository boundaries, preference keys, shell/restart behavior, log export behavior, SAF contracts, dialog actions, and overlay/font/config flows.
- Existing Material 3 rendering while keeping Miuix/future style switching behind project components for later slices.

Implementation note:

- Added shared `ZToolTopAppBar`, `ZToolNavigationRail`, and `ZToolNavigationRailItem` wrappers.
- Added shared `ZToolDialog` and `ZToolDialogSurface` wrappers.
- Added shared `ZToolScaffold` for non-tablet page scaffolds.
- Added shared `ZListItem` for style-agnostic list rows.
- Updated `ZToolTabletScaffold` to use the shared top app bar and navigation rail.
- Updated `ZToolTopAppBar` to centralize single-line title ellipsizing.
- Migrated the main navigation rail in `MainActivity` to `ZToolNavigationRail` and `ZToolNavigationRailItem`.
- Migrated `settingactivity/packageinstaller/packageinstallersettings.kt` from direct Material 3 `Scaffold`, `TopAppBar`, `Card`, and `AlertDialog` usage to `ZToolScaffold`, `ZToolTopAppBar`, `ZToolCard`, and `ZToolDialog`.
- Migrated `settingactivity/safecenter/SafeCenterSettingsActivity.kt` from direct Material 3 `Scaffold`, `TopAppBar`, `Card`, and `AlertDialog` usage to `ZToolScaffold`, `ZToolTopAppBar`, `ZToolCard`, and `ZToolDialog`.
- Extended `ZToolCard` with optional container color and elevation so semantic cards can still use the shared component adapter.
- Migrated remaining active Compose pages from direct Material 3 page-level `Scaffold`, `TopAppBar`, `Card`, and Compose `AlertDialog` usage to `ZToolScaffold`, `ZToolTopAppBar`, `ZToolCard`, and `ZToolDialog`.
- Kept semantic data colors, including log levels and color-preview swatches, explicit.
- Left AppCompat/MaterialAlertDialogBuilder host dialogs in utility and compatibility flows intact where they are not page-level Compose components.
- Miuix is still not introduced; this is the adapter surface needed before adding style-specific rendering.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after adding the shared component adapter foundation.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the package installer shared-component pilot.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the safe center shared-component pilot.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the broad shared-component page migration.

Remaining Phase 3 task 4 work:

- Business pages no longer directly use Material 3 page-level `Scaffold`, `TopAppBar`, `Card`, or Compose `AlertDialog`; remaining direct Material 3 usage for those primitives is concentrated inside `ui/components`.
- Keep AppCompat/MaterialAlertDialogBuilder compatibility dialogs as-is unless a later task explicitly migrates those host flows.
- Broaden wrapper coverage only when a real page needs it; avoid adding speculative components.
- Keep `LocalZToolThemeSpec` as the style-selection boundary and do not add screen-level `if (style == FrontendStyle.Miuix)` branches.
- Do not add the Miuix dependency until the Material 3 adapter path is stable across a few pages.
- After future adapter changes, run `.\gradlew.bat assembleDebug` and record the result here.

### Phase 3 User-Facing Theme Settings Entry

`SettingsFragment.kt` now exposes the persisted app UI theme settings to users.

Preserved behavior:

- Existing `SettingsFragment` class name, Fragment route, backup/restore flow, log-service controls, detailed logging key, homepage YiYan key, about dialog links, and external launch behavior.
- Existing Hook/module preference keys are unchanged.
- App UI theme settings remain stored through the dedicated `ThemePreferencesRepository` preference file.

Implementation note:

- `SettingsRepository` wraps `ThemePreferencesRepository` for loading and saving app UI theme settings.
- `SettingsUiState` now carries `ZToolThemeSettings`, manual seed color text, and validation state.
- `SettingsViewModel` exposes setters for frontend style, theme mode, Monet dynamic color, manual color enablement, manual seed color, and AMOLED black.
- The settings screen now has an App UI theme section using shared ZTool components.
- Manual seed color accepts `#RRGGBB` or `#AARRGGBB`; valid values are persisted as a full ARGB seed color.
- The `FrontendStyle.Miuix` option is user-visible but still renders through the existing Material 3 fallback until the Miuix dependency and component rendering are added.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after adding the user-facing theme settings entry.

### Phase 3 Miuix Dependency Introduction

The Miuix dependency was introduced as the next component-adapter prerequisite.

Changed build inputs:

- Kotlin and the Compose compiler plugin were upgraded to `2.3.21`.
- Kotlin `jvmTarget` configuration moved from the deprecated `kotlinOptions` DSL to `compilerOptions`.
- Miuix was pinned to `top.yukonga.miuix.kmp:miuix-android:0.8.8`.

Compatibility note:

- The newer `top.yukonga.miuix.kmp:miuix-ui-android:0.9.1` artifact was tested first, but its AAR metadata requires `compileSdk >= 37`.
- This project currently uses `compileSdk = 36` with AGP `8.13.0`, whose recommended maximum compile SDK is 36.
- To keep this slice scoped and avoid an AGP/Gradle 9 migration, the compatible `0.8.8` artifact was selected. It keeps `minCompileSdk = 36`.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after upgrading Kotlin and adding Miuix `0.8.8`.

Follow-up fix:

- Fixed a composition crash in the manual color theme path after the Kotlin/Compose upgrade.
- Root cause: app UI manual seed colors are stored as ARGB `Long` values, but `ZToolTheme` converted them with the Compose packed-color `ULong` constructor. This could create an invalid color-space index and crash during `Color.lerp`.
- Fix: convert stored ARGB values through `Color(Int)` after masking to 32 bits.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the fix.

Follow-up interaction fix:

- Manual seed color input now preserves the user's in-progress text while the field is focused.
- Valid partial input such as `1D5FA8` still updates the stored manual color, but the field is not rewritten to `#AARRGGBB` until editing is finished.
- Editing is finished on IME Done or focus loss; invalid text then falls back to the current saved `#AARRGGBB` value.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the input behavior fix.

Follow-up dark-mode fix:

- Fixed the top inset of the main navigation rail showing the underlying light/dynamic window background in dark mode.
- Root cause: the main rail used external top padding, so the padded area was outside the `NavigationRail` themed surface.
- Fix: move the top spacing inside the rail content so the rail container paints the full height with `MaterialTheme.colorScheme.surface`.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the rail spacing fix.

### Phase 3 Miuix Adapter First Slice

The first Miuix rendering slice wires Miuix into the shared component/theme layer without adding style branches to business screens.

Implemented:

- `ZToolTheme` now provides Miuix theme locals for shared components while `LocalZToolThemeSpec` controls which components render with the Miuix style.
- Miuix colors are derived from the already-resolved Material `ColorScheme`, so theme mode, dynamic color, manual seed color, and AMOLED surface overrides continue to share the existing project theme resolution path.
- `ZToolTopAppBar` uses Miuix `SmallTopAppBar` in Miuix mode.
- `ZToolCard` uses Miuix `Card` in Miuix mode.
- `ZToolSwitchRow` uses Miuix `Switch` in Miuix mode.
- `ZToolDialogSurface` uses Miuix `Surface` in Miuix mode.

Deferred:

- `ZToolScaffold` and `ZToolNavigationRail` keep the Material container implementations for now because replacing these host-level containers can interfere with Fragment-hosted Compose trees and popup/spinner hosts during hot style switching.
- `ZToolNavigationRailItem` still uses the Material implementation because Miuix `NavigationRailItem` in `0.8.8` accepts `ImageVector`, while the existing project wrapper accepts composable `Painter` icons. Changing that requires a separate navigation icon contract slice.
- `ZToolDialog` still uses Material `AlertDialog` because the current wrapper accepts arbitrary composable title/text/button content, while Miuix `SuperDialog` is string/content-layout oriented. Dialog migration should be a dedicated compatibility slice.
- `ZToolDropdownField` still uses Material `ExposedDropdownMenuBox` because it preserves the current non-editable anchor behavior and existing form layout.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the first Miuix adapter slice.

Follow-up stability fix:

- `ZToolTheme` now reads system night mode from `LocalConfiguration.current.uiMode`, so each independent Compose tree can re-resolve follow-system theme mode when Android toggles light/dark mode.
- `ZToolTheme` keeps the `MiuixTheme` provider stable across md3e/miuix switches instead of inserting/removing it only for Miuix.
- `ZToolScaffold` and `ZToolNavigationRail` no longer replace their Material host containers with Miuix host containers during style switching.
- Miuix rendering remains enabled for lower-risk shared components: `ZToolTopAppBar`, `ZToolCard`, `ZToolSwitchRow`, and `ZToolDialogSurface`.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the stability fix.

Follow-up settings component slice:

- `ZListItem` now uses Miuix `BasicComponent` in Miuix mode while preserving the existing shared component API for business screens.
- `ZToolSettingsDivider` now uses Miuix `HorizontalDivider` in Miuix mode.
- `ZToolDropdownField` remains on Material `ExposedDropdownMenuBox` because it is used as a form field and the Miuix `SuperDropdown` API is row-oriented; replacing it should be a dedicated UX-compatible slice.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the settings component slice.

Follow-up navigation rail item slice:

- `ZToolNavigationRailItem` now accepts an `ImageVector` and label string so Miuix mode can use Miuix `NavigationRailItem` directly.
- `MainActivity` now passes vector resources through the shared wrapper; navigation behavior, destination ids, labels, and environment-ready gating are unchanged.
- Material mode still renders through Material `NavigationRailItem` inside the wrapper.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the navigation rail item slice.

Follow-up navigation rail layout fix:

- Fixed the Miuix style switch leaving only a centered, interactive navigation rail visible while the Fragment-hosted content disappeared.
- Root cause: Miuix `NavigationRailItem` uses `fillMaxWidth()` internally; when hosted inside the retained Material `NavigationRail`, this could expand the rail measurement and squeeze the legacy `NavHost` content out of the main row.
- Fix: constrain the Miuix item wrapper to the rail width before calling Miuix `NavigationRailItem`.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the navigation rail layout fix.

Follow-up dialog adapter slice:

- `ZToolDialog` now uses a Miuix `Surface` hosted by Compose `Dialog` in Miuix mode.
- The existing composable slot API for title, text, confirm button, and dismiss button is preserved, so business screens do not need style-specific branches or call-site changes.
- Material mode still uses Material `AlertDialog`.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the dialog adapter slice.

Follow-up dropdown adapter slice:

- `ZToolDropdownField` now uses Miuix `TextField` for the read-only anchor in Miuix mode while preserving the existing field-style API.
- The dropdown popup and non-editable anchor behavior still use the current Material `ExposedDropdownMenuBox` path, so existing form layouts and option-selection behavior remain unchanged.
- This completes the planned Phase 3 component adapter rendering pass without adding business-screen style branches.
- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the dropdown adapter slice.

### Phase 4 Main Navigation Host Stabilization

The first Phase 4 slice stabilizes the existing mixed Compose/Fragment navigation host without replacing XML Navigation yet.

Preserved behavior:

- Existing `MainActivity` class name and launch contract.
- Existing `nav_graph.xml`, Fragment routes, destination ids, and navigation animations.
- Existing main navigation rail labels, icons, selected destination behavior, and environment-ready gating.
- Existing external launch contracts and Fragment-hosted Compose screens.

Implementation note:

- `MainActivity.LegacyNavHost` now delegates Fragment host binding to `bindLegacyNavHost(...)`.
- When a `NavHostFragment` already exists but its view is not attached to the newly created `FragmentContainerView`, the host is explicitly detached and attached so the Fragment view is recreated for the current container.
- The existing `NavHostFragment` remains the route owner; this slice does not introduce `navigation-compose`, delete `nav_graph.xml`, or remove legacy Fragment entry points.
- This targets the blank-content failure mode where configuration or theme changes leave the navigation rail visible while the Fragment-hosted content disappears.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the host stabilization slice.
- Manual observation confirmed both blank-content bugs are fixed: portrait/landscape switching and system light/dark switching no longer leave only the navigation rail visible.
- Remaining warnings: deprecated `statusBarColor` and `navigationBarColor` in `MainActivity.kt`, already known.

### Phase 4 Compose-Owned Main Route State

The second Phase 4 slice moves main route state toward the Compose shell while keeping the legacy XML Navigation host intact.

Preserved behavior:

- Existing `MainActivity` class name and launch contract.
- Existing `nav_graph.xml`, Fragment routes, destination ids, and navigation animations.
- Existing Home, Features, Audit, and Settings destinations.
- Existing navigation rail behavior, destination restore behavior, and environment-ready gating.
- Existing Fragment-hosted screen content.

Implementation note:

- `MainActivity` now models the main destinations as `MainRoute`.
- The Compose shell consumes `selectedRoute` and dispatches `MainRoute` values from the navigation rail.
- XML Navigation destination ids are now mapped at the legacy navigation boundary through `MainRoute.destinationId`.
- Saved instance state now stores the route name while retaining the old destination-id key as a fallback compatibility path.
- This slice prepares for a future `navigation-compose` route graph without replacing `nav_graph.xml` yet.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after introducing Compose-owned main route state.
- Remaining warnings: deprecated `statusBarColor` and `navigationBarColor` in `MainActivity.kt`, already known.

### Phase 4 Navigation Compose Main Routes

The third Phase 4 slice replaces the main in-app XML Navigation route graph with Compose Navigation while preserving the existing Fragment page implementations.

Preserved behavior:

- Existing `MainActivity` class name and launch contract.
- Existing Home, Features, Audit, and Settings Fragment classes.
- Existing main navigation rail labels, icons, selected-route behavior, and environment-ready gating.
- Existing Fragment-hosted Compose screen behavior.
- Existing `nav_graph.xml` resource is intentionally left in place for the dedicated XML/View cleanup phase.

Implementation note:

- `MainActivity` now creates a Compose `NavHost` with `MainRoute` destinations.
- `MainRoute` remains the main route model consumed by the Compose shell and navigation rail.
- Each Compose route currently hosts the matching legacy Fragment through `LegacyFragmentRoute` and a `FragmentContainerView`.
- Legacy XML destination ids are no longer used for main in-app navigation dispatch; they remain as compatibility metadata and cleanup candidates.
- This slice does not migrate the main Fragment screens to pure composable route functions yet.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after replacing the main route graph with Compose Navigation.
- Remaining warnings: deprecated `statusBarColor` and `navigationBarColor` in `MainActivity.kt`, already known.

### Phase 4 Style Verification Fixes

This slice fixes issues found while verifying Material 3 Expressive and Miuix behavior after the Phase 4 navigation work.

Fixed:

- Disabled Material 3 switches now use explicit disabled colors derived from the current `MaterialTheme.colorScheme`.
- This keeps the disabled dynamic-color switch visually tied to the active manual seed color instead of falling back to default Material colors when manual color disables it.
- Main navigation rail items are vertically centered in the rail for both Material 3 Expressive and Miuix modes.
- Main navigation item width and height are constrained consistently across Material 3 Expressive and Miuix modes to reduce spacing differences.
- The previous fixed top spacer in `MainActivity`'s navigation rail content was removed so all nav items can be centered as a group.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the style verification fixes.
- Remaining warnings: deprecated `statusBarColor` and `navigationBarColor` in `MainActivity.kt`, already known.

### Phase 5 Shared Settings Model Foundation

The first Phase 5 slice adds a shared settings data model and renderer foundation without changing any existing settings page behavior.

Implemented:

- Added `SettingSection` and `SettingItem` under `ui/components`.
- Added renderer composables for settings lists, sections, switches, entries, dropdown fields, sliders, text inputs, color previews, actions, and custom rows.
- Renderers route common rows through the existing ZTool shared components, including `ZToolCard`, `ZToolSwitchRow`, `ZListItem`, `ZToolDropdownField`, and `ZToolSettingsDivider`.
- Callbacks remain page-owned, so existing ViewModels/screens continue to own state, preference keys, shell behavior, and restart flows when pages are piloted.

Deferred:

- No concrete settings page was migrated in this slice.
- `packageinstaller/packageinstallersettings.kt` remains the next pilot target.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after adding the shared settings model foundation.

### Phase 5 Package Installer Settings Model Pilot

`settingactivity/packageinstaller/packageinstallersettings.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `packageinstallersettings` Activity class name, package, and launch contract.
- Existing `PackageInstallerSettingsViewModel` and `PackageInstallerSettingsRepository` boundaries.
- Existing preference keys for scan APK, permission allow, warn page skip, installer ads, row style, and delete-package behavior.
- Existing restart confirmation dialog and package force-stop behavior.

Implementation note:

- Replaced the page-local `SettingsCard` plus hand-written `ZToolSwitchRow`/divider layout with `SettingSection`, `SettingItem.Switch`, and `ZToolSettingsList`.
- State and callbacks remain owned by the existing screen/ViewModel boundary, so the shared renderer does not hide package-installer business behavior.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the package installer model pilot.

### Phase 5 Safe Center Settings Model Pilot

`settingactivity/safecenter/SafeCenterSettingsActivity.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `SafeCenterSettingsActivity` class name, package, and launch contract.
- Existing `SafeCenterSettingsViewModel` and `SafeCenterSettingsRepository` boundaries.
- Existing preference keys for default autorun, Safe Center scan blocking, and DocumentsUI bypass.
- Existing restart confirmation dialog, DocumentsUI restart scope, duplicate restart guard, and package restart result Toast behavior.

Implementation note:

- Replaced the page-local `SettingsCard` plus hand-written `ZToolSwitchRow`/divider layout with `SettingSection`, `SettingItem.Switch`, and `ZToolSettingsList`.
- State and callbacks remain owned by the existing screen/ViewModel boundary, so the shared renderer does not hide Safe Center restart or preference behavior.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Safe Center model pilot.

### Phase 5 Pilot Review Decision

The package installer and Safe Center pilots were reviewed after confirming Material 3 Expressive and Miuix still present the same behavior as before.

Decision:

- Proceed with Phase 5 expansion to medium-complexity settings pages.
- Start with `settingactivity/systemframework/FrameworkSettingsActivity.kt`, then continue to game tool, launcher, and OTA if the model remains readable.

Code complexity findings:

- The shared model reduces duplicated card, divider, and switch-row layout code in simple settings pages.
- Page-specific state, callbacks, preference persistence, shell commands, restart dialogs, and Toast effects remain outside the shared renderer.
- No business-screen style branches were introduced for Material 3 Expressive or Miuix.
- The only complexity issue found was `SettingItem.Dropdown` losing type safety by using `Any?`; it was corrected to a generic `Dropdown<T>` with a private generic renderer bridge.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the pilot review and dropdown type-safety fix.

### Phase 5 Framework Settings Model Migration

`settingactivity/systemframework/FrameworkSettingsActivity.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `FrameworkSettingsActivity` class name, package, and launch contract.
- Existing `FrameworkSettingsViewModel` and `FrameworkSettingsRepository` boundaries.
- Existing preference keys for keep rotation, package visibility, secure flag disabling, AI input expansion, and AI input expansion signs.
- Existing AI input signs validation, information dialog, restart countdown dialog, root reboot command, and restart failure Toast behavior.

Model suitability review:

- The shared model remains suitable for medium-complexity pages when it handles only sections and ordinary rows.
- The AI input configuration remains a page-local composable hosted by `SettingItem.Custom`, so validation state, the info button, and conditional text input stay explicit and readable.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Framework settings model migration.

### Phase 5 Game Tool Settings Model Migration

`settingactivity/gametool/GameToolSettngs.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `GameToolSettngs` Activity class name, package, and launch contract.
- Existing `GameToolSettingsViewModel` and `GameToolSettingsRepository` boundaries.
- Existing preference keys for game audio, device disguise, CPU frequency, SoC temperature, mistake-touch mode, and mistake-touch whitelist packages.
- Existing AppChooser whitelist flow, managed game package loading, restart confirmation dialog, package force-stop behavior, and restart failure Toast behavior.

Model suitability review:

- The shared model remains suitable when ordinary switches are represented as `SettingItem.Switch`.
- Mistake-touch mode selection and the conditional whitelist entry remain page-local composables hosted by `SettingItem.Custom`, keeping mode mapping and AppChooser behavior explicit.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Game Tool settings model migration.

### Phase 5 Launcher Settings Model Migration

`settingactivity/launcher/LauncherSettingsActivity.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `LauncherSettingsActivity` class name, package, and launch contract.
- Existing `LauncherSettingsViewModel` and `LauncherSettingsRepository` boundaries.
- Existing preference keys for force-stop mode, force-stop whitelist, big dock, custom grid enablement, and launcher row/column values.
- Existing AppChooser force-stop whitelist flow, user-app loading, restart confirmation dialog, package force-stop behavior, and restart result Toast behavior.

Model suitability review:

- The shared model remains suitable for pages that mix ordinary switches with page-specific controls.
- Force-stop mode selection, conditional whitelist entry, and conditional grid sliders remain page-local composables hosted by `SettingItem.Custom`.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Launcher settings model migration.

### Phase 5 OTA Settings Model Migration

`settingactivity/ota/OtaSettings.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `OtaSettings` Activity class name, package, and launch contract.
- Existing `OtaSettingsViewModel` and `OtaSettingsRepository` boundaries.
- Existing preference keys for disabling OTA checks, custom OTA target version, custom OTA target device ID, and the custom OTA parameters enable flag.
- Existing OTA info fetch, firmware query, clipboard copy actions, error dialog, restart-scope dialog, package restart behavior, and restart failure Toast behavior.

Model suitability review:

- The shared model remains suitable, but OTA is near the useful boundary for this abstraction.
- The model is used only for the section/card shell and the ordinary OTA-disable switch.
- OTA info fetching, firmware fetching, result rendering, copy buttons, SN input, and custom OTA parameter fields remain page-local composables hosted by `SettingItem.Custom`.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the OTA settings model migration.

### Phase 5 Lock Screen Settings Model Migration

`settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `LockScreenSettingsActivity` class name, package, and launch contract.
- Existing `LockScreenSettingsViewModel` and `LockScreenSettingsRepository` boundaries.
- Existing preference keys for YiYan, owner info, YiYan API URL/regex, charge watts, real watts, custom refresh interval, selected watts option, and SystemUI permission confirmation.
- Existing YiYan API test flow, save confirmation, root-permission dialog, charge-watts option behavior, and custom refresh interval persistence.

Model suitability review:

- The shared model remains suitable for System UI detail pages when it only owns section/card structure and ordinary switches.
- YiYan API configuration and the charge-watts cascading dropdown/input flow remain page-local composables hosted by `SettingItem.Custom`.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Lock Screen settings model migration.

### Phase 5 Status Bar Settings Model Migration

`settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt` now renders its setting sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `StatusBarSettingsActivity` class name, package, and launch contract.
- Existing `StatusBarSettingsViewModel` and `StatusBarSettingsRepository` boundaries.
- Existing preference keys for status bar seconds, custom clock format/style, notification icon limit/native icon, network speed, and external battery percentage.
- Existing world-readable `StatusBar_notifyNumSize` preference behavior for Hook compatibility.
- Existing clock format preview, format help copy action, color picker dialog, save confirmation dialog, and Toast behavior.

Model suitability review:

- The shared model remains suitable, but Status Bar is near the useful boundary for this abstraction.
- The model owns section/card structure and ordinary switches.
- Custom clock formatting, text style sliders, color preview/picker, and notification icon limit dropdown remain page-local composables hosted by `SettingItem.Custom`.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Status Bar settings model migration.

### Phase 5 Control Center Settings Model Migration

`settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt` now renders its setting section through the shared Phase 5 settings model.

Preserved behavior:

- Existing `ControlCenterSettingsActivity` class name, package, and launch contract.
- Existing `ControlCenterSettingsViewModel` and `ControlCenterSettingsRepository` boundaries.
- Existing preference keys for custom control center date, date format, text size, letter spacing, text color, and bold style.
- Existing date format preview, format help copy action, color picker dialog, and save confirmation dialog behavior.

Model suitability review:

- The shared model remains suitable for the current System UI detail pages, but should remain limited to section/card structure and ordinary rows.
- Custom date formatting, preview, style sliders, color preview/picker, and help/save dialog flows remain page-local composables hosted by `SettingItem.Custom`.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-30 after the Control Center settings model migration.

### Phase 5 System UI Aggregate Settings Model Migration

`settingactivity/systemui/systemUISettings.kt` now renders its aggregate settings sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `systemUISettings` Activity class name, package, and launch contract.
- Existing `SystemUiSettingsViewModel` and `SystemUiSettingsRepository` boundaries.
- Existing sub-settings navigation to status bar, lock screen, and control center pages.
- Existing preference keys for native AOD, Lenovo AOD, charge animation disabling/fix, and guest-mode controller.
- Existing restart confirmation dialog, package restart behavior, wallpaper settings restart behavior, root shell commands, and Lenovo AOD settings launch behavior.

Model suitability review:

- The shared model remains suitable for the aggregate System UI page when it owns only section/card structure and ordinary rows.
- Detail-page navigation is represented as ordinary `SettingItem.Entry` rows with icon and trailing affordance slots.
- AOD, charging animation, and guest-mode controls use `SettingItem.Switch`.
- The conditional Lenovo AOD settings launcher uses `SettingItem.Action`; shell and restart behavior remain ViewModel/repository-owned.
- No new settings model item types were added.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-31 after the System UI aggregate settings model migration.

### Phase 5 Settings Detail Ordinary Rows Model Migration

`settingactivity/setting/SettingsDetailActivity.kt` now renders its ordinary settings sections through the shared Phase 5 settings model.

Preserved behavior:

- Existing `SettingsDetailActivity` Activity class name, package, and launch contract.
- Existing `SettingsDetailViewModel` and `SettingsDetailRepository` boundaries.
- Existing preference keys for embedding blacklist removal, split-screen mandatory, permission controller, Dolby display disabling, and suggestions display.
- Existing Magisk module enable/disable behavior, module restore/config flashing behavior, floating-window launch, strategy search launch, OV config generation flows, font import flow, loading dialogs, and restart-scope behavior.
- Existing embedding asset and config file behavior.

Model suitability review:

- The shared model remains suitable for the ordinary rows on this page.
- Basic switches now use `SettingItem.Switch`.
- Simple launch/action rows now use `SettingItem.Action`.
- The Android-version-specific ZUI force-config summary remains explicit as `SettingItem.Custom`.
- Config selection, Magisk/module restore, OV config, font import, floating-window, strategy search, loading dialog, and restart workflows remain page/ViewModel/repository-owned.
- No new settings model item types were added.
- No Material 3 Expressive or Miuix style branches were added to the business screen.

Verification:

- `.\gradlew.bat assembleDebug` succeeded on 2026-05-31 after the Settings Detail ordinary rows model migration.

### Phase 5 Closeout And Phase 6 Entry Decision

Phase 5 settings-model work is complete for the current candidate set.

Closeout decision:

- The remaining `settingactivity/setting/magicwindowsearch/searchPage.kt` and `settingactivity/setting/floatingwindow/FloatingWindow.kt` flows are not Phase 5 settings-model targets.
- `searchPage.kt` is a query/result/detail workflow.
- `FloatingWindow.kt` is an overlay wizard backed by foreground-app polling, tutorial playback, generated config output, and close/hide behavior.
- Further work on those pages should be targeted query/card-shell or overlay/wizard cleanup, not settings model expansion.

Manual verification on 2026-05-31:

- Material 3 Expressive and Miuix rendering work.
- Theme switching works.
- Preference values read and write correctly.
- Restart scope works.
- Shell/root-dependent business flows work.
- Hook compatibility remains intact.

Next phase:

- Move to Phase 6 XML/View cleanup.
- Start by removing the main-route Fragment hosting layer only after extracting Home, Features, Audit, and Settings routes into direct composable route functions.
- Preserve `HomeFragment.isModuleActive()` as the LSPosed self-check Hook target until an explicitly approved compatibility replacement exists.
