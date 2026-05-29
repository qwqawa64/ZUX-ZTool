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

## Next Plan

### 1. UI Foundation Fixes

Do these before starting another broad screen migration.

- Normalize `FeaturesFragment.kt` card heights.
- Continue using and improving `ZToolDropdownField`.
- Ensure root screen containers consistently use `MaterialTheme.colorScheme.background` or an approved shared page container.
- Move duplicated rows, cards, sliders, text fields, dialogs, and action rows into `ui/components` gradually.
- Avoid adding screen-local versions of components that already exist in `ui/components`.

### 2. Remaining Legacy Dialog Review

Evaluate whether `utils/CountdownDialog.java` is still used by active flows.

If still used:

- Migrate it to Compose while preserving existing callback and countdown behavior.
- Keep the public entry contract stable unless the call sites are migrated in the same change.

If unused:

- Leave deletion for the dedicated XML/View cleanup phase unless explicitly asked to remove dead code now.

### 3. State Architecture Follow-Up

Gradually move business logic out of composable screens when touching risky pages.

Preferred direction:

- `ViewModel`.
- `UiState`.
- `StateFlow`.
- Repository wrappers for shell, logs, config, preferences, update checks, and system services.

Do not turn a page migration into a large architecture rewrite unless the page logic is already blocking a safe UI migration.

### 4. XML/View Cleanup Phase

Do not delete old XML layouts, adapters, or `nav_graph.xml` during ordinary page migrations.

Reserve these for a dedicated cleanup pass after active UI routes no longer depend on them:

- Unused `res/layout/activity_*.xml`.
- Unused `res/layout/fragment_*.xml`.
- Unused dialog and item XML files.
- Old RecyclerView adapters.
- Obsolete Fragment classes.
- XML Navigation references that are no longer active.
- Unneeded AppCompat/Material View dependencies.

## Verification Policy

After each page, dialog, or shared UI migration:

```powershell
.\gradlew.bat assembleDebug
```

Record the completed target, verification result, and next planned target in this file when requested.

## Current Recommended Next Target

Continue with shared component consolidation and the remaining legacy dialog review, starting with whether `utils/CountdownDialog.java` is still used by active flows.
