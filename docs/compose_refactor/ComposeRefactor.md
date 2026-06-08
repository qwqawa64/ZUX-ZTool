# Compose Refactor Plan

This file is the detailed Compose migration plan and status log for ZUX-ZTool.

Use `AGENTS.MD` for the concise operating rules and preservation boundaries. This file is now archived with the completed Compose migration history.

## Current Status

Last updated: 2026-05-31.

The Compose refactor is complete. The active app UI is Compose-owned, and the former XML/View/Fragment/AppCompat/Material Components compatibility layer has been removed where it was part of the UI shell. Hook modules, Xposed metadata, runtime assets, service behavior, shell behavior, existing preference keys, and external launch contracts remain compatible.

The latest verified command is:

```powershell
.\gradlew.bat assembleDebug
```

It succeeds. Remaining warnings are known compatibility warnings:

- `MainActivity.kt`: deprecated `statusBarColor` and `navigationBarColor`.
- `StatusBarSettingsActivity.kt`: deprecated `MODE_WORLD_READABLE`, retained for Hook compatibility.

Manual device verification on 2026-05-31 confirmed all previously listed final checks pass, including Material 3 Expressive and Miuix theme modes, LSPosed self-check, settings backup/restore, log export, app chooser flows, loading/progress flows, countdown confirmation, and Settings Detail command dialogs.

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
   - Historical investigation covered `LegacyNavHost`, `AndroidView`, `FragmentContainerView`, retained `NavHostFragment` lookup by `R.id.nav_host_fragment`, and configuration-change/theme-change reattachment behavior before the Fragment host was removed.

2. Introduce a Compose-owned main route state. Completed on 2026-05-30.
   - Keep the current main destinations: Home, Features, Audit, and Settings.
   - Move selected destination state, navigation rail state, and route dispatch toward Compose-owned state.
   - Keep existing Fragment screens as route content during the transition if needed.

3. Replace XML Navigation with `navigation-compose` for the main in-app routes. Completed on 2026-05-30.
   - Move main navigation route definitions into Compose.
   - Preserve existing entry points and Activity/Fragment class names until a dedicated cleanup phase.
   - Avoid deleting `nav_graph.xml` or legacy XML resources during the ordinary Phase 4 migration.

4. Convert main Fragment routes to pure Compose screens when their route host is stable. Completed during Phase 6.
   - Reuse the existing ViewModel/repository boundaries for Home, Audit, and Settings.
   - Keep Hook, service, utility, configuration, shell, Xposed metadata, and asset behavior intact.
   - Main route definitions now live in Compose and active route contents use direct composable route entry points.
   - Fragment wrapper class removal is deferred to Phase 7 because `HomeFragment.isModuleActive()` remains a Hook compatibility target.

5. Verify each Phase 4 slice. Completed for the currently scoped Phase 4 work.
   - Run `.\gradlew.bat assembleDebug`.
   - Manually verify Material 3 Expressive and Miuix modes.
   - Manually verify portrait/landscape changes and system light/dark changes no longer blank the main content.
   - Record completed work, verification result, and next step in this file.

Phase 4 leftovers intentionally deferred to Phase 6 cleanup:

- `app/src/main/res/navigation/nav_graph.xml`, removed during Phase 6 on 2026-05-31 after runtime references were audited.
- Main Fragment wrapper classes: `HomeFragment`, `FeaturesFragment`, `AuditFragment`, and `SettingsFragment`, now deferred to Phase 7 compatibility-target cleanup.
- Fragment-hosted `ComposeView` wrappers for the main routes, now inactive for main navigation and deferred to Phase 7 wrapper removal.
- Legacy main route XML layout references under `res/layout/fragment_*.xml`, removed during Phase 6.
- Navigation animation resources, removed during Phase 6.
- Fragment Navigation dependencies, removed during Phase 6.

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
   - During Phase 5, legacy XML layouts, `nav_graph.xml`, old Fragment classes, and old View dependencies were intentionally left for Phase 6.
   - Phase 6 has since removed XML/navigation/adapters and pruned unused View dependencies; do not reintroduce them during Phase 7.

### Phase 6. Clean Up The XML/View Layer

Completed on 2026-05-31 after active UI routes stopped depending on the old View layer.

Cleanup candidates:

- `res/layout/activity_*.xml`: removed.
- `res/layout/fragment_*.xml`: removed.
- `res/navigation/nav_graph.xml`: removed.
- Old adapters: removed.
- Obsolete Fragment classes: removed during Phase 7 after Hook compatibility moved to `ModuleActivationProbe`.
- Unneeded AppCompat or Material View dependencies: removed during Phase 7 after dialog bridges and dynamic-color setup no longer required them.

Do not reintroduce XML layouts, XML navigation, old adapters, RecyclerView UI, Fragment Navigation dependencies, AppCompat, or Material Components dependencies.

### Phase 7. Remove Compatibility Wrappers And Dialog Bridges

Phase 7 is the final Compose-architecture cleanup after the XML/View layer has been removed. The goal is to remove compatibility wrappers that are no longer part of active UI while preserving Hook behavior, launch contracts, preference keys, shell behavior, services, Xposed metadata, and runtime assets.

Phase 7 cleanup status:

- `HomeFragment.isModuleActive()` was replaced with `ModuleActivationProbe.isModuleActive()`.
- Inactive main Fragment wrapper classes were removed.
- Direct Fragment dependencies were audited away.
- `MainActivity` now extends `ComponentActivity`.
- AppCompat and Material dialog bridges were replaced with Compose-hosted platform dialogs.
- AppCompat and Material Components dependencies were removed.

Remaining final manual verification:

   - Verify Material 3 Expressive and Miuix theme modes.
   - Verify LSPosed self-check still reports active when hooked.
   - Verify settings backup/restore, log export, app chooser flows, loading/progress flows, countdown confirmation, and Settings Detail command dialogs.
   - Record completed work and residual risks in `MigrationNotes.md`.

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

No active Compose refactor task remains. This document and the operating notes have been archived under `docs/compose_refactor/`.

Clean Compose app review:

- Active UI routes are Compose-owned and no longer use Fragment hosting.
- No `res/layout`, `res/menu`, or `res/navigation` resources remain.
- No old adapters, RecyclerView UI, Fragment Navigation, AppCompat, or Material Components dependencies remain.
- Platform interop remains only where it is still appropriate: `ComposeView` for platform dialog/overlay hosting and `AndroidView` for app icons or media/overlay renderer interop.

Final manual verification is recorded in `MigrationNotes.md`.

## Phase 8. Compose Feature Navigation

Started on 2026-06-09 to move feature detail pages away from Activity boundaries and into
the app-level Compose Navigation graph.

Completed first slice:

- Added root-level migration plan: `FEATURE_COMPOSE_NAV_MIGRATION.md`.
- Added stable `FeatureDestination` route ids for all current Features entries.
- Updated `FeaturesMainRoute` so migrated destinations can navigate through Compose while
  non-migrated destinations temporarily keep the existing Activity launch path.
- Added Compose NavHost destinations for:
  - `feature/package-installer`
  - `feature/safe-center`
- Added reusable route entry points for package installer and safe center settings pages.
- These two pages now share the same app-level predictive-back transition policy as Home,
  Features, Audit, Settings, and theme settings when launched from the Features page.

Verification:

```powershell
.\gradlew.bat assembleDebug
```

Result: succeeded on 2026-06-09 after the first Compose feature navigation slice.

Next targets:

- Migrated additional top-level Features pages into the app-level Compose NavHost:
  - `feature/framework`
  - `feature/game-tool`
  - `feature/launcher`
  - `feature/ota`
- These pages now share the same predictive-back transition policy when launched from
  the Features page. Their existing Activity classes remain temporarily as compatibility
  hosts until the old launch paths are deleted in a later cleanup.

Verification:

```powershell
.\gradlew.bat assembleDebug
```

Result: succeeded on 2026-06-09 after migrating framework, game tool, launcher, and OTA
to Compose feature destinations.

Next targets:

- Migrated the remaining complex top-level Features entries into the app-level Compose
  NavHost:
  - `feature/system-ui`
  - `feature/settings-detail`
- All top-level Features cards now enter Compose destinations and share the same app-level
  predictive-back transition policy.
- `system-ui` still opens status bar, lock screen, and control center through their existing
  Activity launch paths. These are now the next System UI subtree migration targets.
- `settings-detail` now has a Compose route host for its existing screen and platform effects,
  while magic-window search still uses its existing Activity path and floating-window remains
  a WindowManager overlay controller.

Verification:

```powershell
.\gradlew.bat assembleDebug
```

Result: succeeded on 2026-06-09 after migrating the system UI and settings detail top-level
Features entries to Compose destinations.

Next targets:

- Migrated the System UI subtree detail pages into Compose destinations:
  - `feature/system-ui/status-bar`
  - `feature/system-ui/lock-screen`
  - `feature/system-ui/control-center`
- `systemUISettings` now navigates to those child pages through the app-level Compose
  NavHost instead of starting their Activities from the in-app path.
- Status bar, lock screen, and control center child pages now share the same predictive-back
  transition policy as the System UI aggregate page.

Verification:

```powershell
.\gradlew.bat assembleDebug
```

Result: succeeded on 2026-06-09 after migrating the System UI child pages to Compose
destinations.

Next targets:

- Migrated the System Settings detail subtree search page into a Compose destination:
  - `feature/settings-detail/magic-window-search`
- `SettingsDetailRoute` now opens magic-window search through the app-level Compose NavHost
  instead of starting `searchPage` from the in-app path.
- The floating-window guide remains a WindowManager overlay because that is its domain
  behavior, but it now exposes `FloatingWindow.create(ComponentActivity)` so it no longer
  implicitly depends on the old `SettingsDetailActivity` shell.

Verification:

```powershell
.\gradlew.bat assembleDebug
```

Result: succeeded on 2026-06-09 after migrating magic-window search and tightening the
floating-window overlay host boundary.

Next targets:

- Audit and remove old feature Activity launch paths that are no longer used by the in-app
  Compose route graph.
- Remove obsolete Manifest Activity entries after direct source references are gone.
- Remove Activity transition helpers if no remaining in-app path uses Activity transitions.
- After those subtrees are Compose-owned, remove the old feature Activity launch paths,
  Manifest entries, and Activity transition helpers.
