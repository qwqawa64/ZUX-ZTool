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

See MigrationNotes.md for more information. Detailed changelogs should be written into MigratiionNotes.md too.

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

Phase 4 should proceed in small, verifiable slices rather than one full navigation rewrite:

1. Stabilize the main navigation host boundary. Completed on 2026-05-30.
   - Fix the current bug where configuration changes, such as portrait/landscape rotation, leave only the navigation rail visible while Fragment-hosted Compose content disappears.
   - Fix the current bug where system light/dark mode changes leave only the navigation rail visible while Fragment-hosted Compose content disappears.
   - Preserve `MainActivity`, existing Fragment classes, `nav_graph.xml`, destination ids, external launch contracts, and environment-ready gating while this stabilization is underway.
   - Investigate `LegacyNavHost`, `AndroidView`, `FragmentContainerView`, retained `NavHostFragment` lookup by `R.id.nav_host_fragment`, and configuration-change/theme-change reattachment behavior.

2. Introduce a Compose-owned main route state. Completed on 2026-05-30.
   - Keep the current main destinations: Home, Features, Audit, and Settings.
   - Move selected destination state, navigation rail state, and route dispatch toward Compose-owned state.
   - Keep existing Fragment screens as route content during the transition if needed.

3. Replace XML Navigation with `navigation-compose` for the main in-app routes. Completed on 2026-05-30.
   - Move main navigation route definitions into Compose.
   - Preserve existing entry points and Activity/Fragment class names until a dedicated cleanup phase.
   - Avoid deleting `nav_graph.xml` or legacy XML resources during the ordinary Phase 4 migration.

4. Convert main Fragment routes to pure Compose screens when their route host is stable. Deferred.
   - Reuse the existing ViewModel/repository boundaries for Home, Audit, and Settings.
   - Keep Hook, service, utility, configuration, shell, Xposed metadata, and asset behavior intact.
   - Main route definitions now live in Compose, but the active route contents still host existing Fragment classes through `FragmentContainerView`.
   - Fragment removal belongs to Phase 6 cleanup after active UI routes no longer need Fragment wrappers.

5. Verify each Phase 4 slice. Completed for the currently scoped Phase 4 work.
   - Run `.\gradlew.bat assembleDebug`.
   - Manually verify Material 3 Expressive and Miuix modes.
   - Manually verify portrait/landscape changes and system light/dark changes no longer blank the main content.
   - Record completed work, verification result, and next step in this file.

Phase 4 leftovers intentionally deferred to Phase 6 cleanup:

- `app/src/main/res/navigation/nav_graph.xml`, now no longer used for main in-app navigation dispatch but retained as a compatibility/cleanup-phase artifact.
- Main Fragment wrapper classes: `HomeFragment`, `FeaturesFragment`, `AuditFragment`, and `SettingsFragment`.
- Fragment-hosted `ComposeView` wrappers for the main routes.
- Legacy main route XML layout references under `res/layout/fragment_*.xml`.
- Navigation animation resources if they are no longer referenced after the final cleanup pass.
- Fragment Navigation dependencies if no other app paths still require them.

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
- `safecenter`.
- `systemframework`.
- `statusBarSetting`.
- `ControlCenter`.
- `lockscreen`.

Preserve all existing preference keys used by Hook modules.

Phase 5 goals:

1. Introduce a shared settings data model.
   - Model sections, rows, switches, entries, sliders, dropdowns, text inputs, color previews, and action rows as data where practical.
   - Keep callbacks explicit and page-owned so existing ViewModel/repository boundaries remain clear.
   - Do not move Hook/module preference keys into UI components.

2. Build shared settings renderers.
   - Add project-level renderers such as `ZToolSettingsList`, `ZToolSettingsSection`, and typed row renderers.
   - Render through existing shared components such as `ZToolCard`, `ZToolSwitchRow`, `ZListItem`, `ZToolDropdownField`, and `ZToolDialog`.
   - Keep Material 3 Expressive and Miuix behavior behind shared components.

3. Pilot the model on low-risk settings pages.
   - Start with `packageinstaller/packageinstallersettings.kt` and `safecenter/SafeCenterSettingsActivity.kt`.
   - Preserve Activity names, launch contracts, preference keys, restart confirmations, and package restart behavior.
   - Run `.\gradlew.bat assembleDebug` after each pilot.

4. Expand to medium-complexity pages.
   - Candidates: `systemframework/FrameworkSettingsActivity.kt`, `gametool/GameToolSettngs.kt`, `launcher/LauncherSettingsActivity.kt`, and `ota/OtaSettings.kt`.
   - Keep shell commands, root behavior, app chooser behavior, and existing saved values intact.

5. Expand to high-complexity System UI detail pages.
   - Candidates: lock screen, status bar, and control center pages.
   - Preserve world-readable preference behavior and Hook compatibility paths where they exist.
   - Keep semantic color previews and domain-specific dialogs behavior-compatible.

6. Avoid cleanup during Phase 5.
   - Do not delete legacy XML layouts, `nav_graph.xml`, old Fragment classes, or old View dependencies as part of settings model work.
   - Leave deletion and dependency pruning for Phase 6.

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

Move to Phase 6: clean up the XML/View layer. Phase 5's shared settings model work is complete for the current candidate set.

Phase 5 closeout:

- Shared settings model foundation, low-risk pilots, medium-complexity pages, System UI detail pages, the System UI aggregate page, and Settings Detail ordinary rows are complete.
- `settingactivity/setting/magicwindowsearch/searchPage.kt` and `settingactivity/setting/floatingwindow/FloatingWindow.kt` are intentionally not Phase 5 settings-model targets; they are query/result and overlay-wizard workflows.
- Manual verification on 2026-05-31 confirmed Material 3 Expressive and Miuix rendering work, theme switching works, preference values read/write correctly, restart scope works, shell/root-dependent business flows work, and Hook compatibility remains intact.
- Keep the shared settings model limited to section/card structure and ordinary rows for now; do not add generic query, format-preview, color-picker, app-picker, root/shell, or wizard model items.

Recommended next order:

1. Start Phase 6 by removing the main-route Fragment hosting layer. Completed on 2026-05-31.
   - `MainActivity` no longer uses `LegacyFragmentRoute`, `FragmentContainerView`, or Fragment transactions inside the Compose `NavHost`.
   - Home, Features, Audit, and Settings routes now have direct composable route entry points.
   - Existing Fragment class names are preserved for now.
   - `HomeFragment.isModuleActive()` remains available as the LSPosed self-check Hook target.
   - Home, Features, Audit, and Settings behavior, ViewModel/repository boundaries, SAF launchers, external intents, reboot shell behavior, and log export behavior were preserved.
   - `.\gradlew.bat assembleDebug` succeeded on 2026-05-31.
   - Implementation notes and verification are recorded in `MigrationNotes.md`.

2. Audit and remove obsolete XML Navigation references. Next.
   - Delete or detach `res/navigation/nav_graph.xml` only when no runtime path references it.
   - Remove legacy Navigation resource ids from `MainRoute` only after saved-state fallback compatibility is no longer needed.
   - Confirm whether the retained Fragment wrappers and `HomeFragment.isModuleActive()` compatibility target require any XML metadata before deleting resources.
   - Run `.\gradlew.bat assembleDebug`.
   - Record implementation notes and verification in `MigrationNotes.md`.

3. Clean up legacy Fragment/XML resources in small slices.
   - Candidates: `fragment_*.xml`, unused `activity_*.xml`, navigation animation resources, and obsolete Fragment-only helpers.
   - Use `rg` before deletion and preserve any resource still referenced by Activities, dialogs, assets, manifest metadata, or Hook compatibility paths.
   - Run `.\gradlew.bat assembleDebug` after each cleanup slice.

4. Dependency cleanup comes last.
   - Remove Fragment Navigation/AppCompat/View dependencies only after code and resources no longer use them.
   - Do not remove Material/Compose dependencies used by shared ZTool components or Miuix adapters.
   - Run `.\gradlew.bat assembleDebug`.
