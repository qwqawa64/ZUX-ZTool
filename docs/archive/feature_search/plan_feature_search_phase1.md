# Plan: Feature Search Phase 1 — Index, Search Page, Navigation (No Highlight)

> AGENT-oriented implementation plan. Phase 1 of the feature-search effort analyzed in
> this session. **Phase 1 deliberately excludes the anchor-scroll + highlight mechanism**
> (Phase 2) and the `SettingItem.key` mandatory refactor (Phase 2/3).

## User Decisions (locked in this session)

1. **Search entry**: search icon in the top app bars of BOTH `Features` and `Settings` main tabs.
2. **Index scope**: all non-Home screens — the 11 feature cards, all Hook detail screens
   (including the 5 SystemUI sub-screens and the Magic-Window search sub-screen entry),
   and the app's own settings (Settings main / Theme / About / Advanced).
3. **Conditional child items**: stay in the index, with a subtitle noting the required
   parent switch (e.g. "needs 'Custom grid' enabled"); navigation lands on the containing
   screen and does NOT toggle any switch on the user's behalf.
4. **Search page form**: standalone full-screen route (same form factor as the existing
   `SearchPageRoute`), with a search field on top and grouped results below.

## Agreed Defaults (not asked, applied unless user objects)

- No pinyin matching in Phase 1. Matching runs against the current-locale title/summary
  plus optional manual keywords.
- Entry IDs reuse the corresponding `PreferenceKeys` constant name where one exists
  (e.g. `launcher_custom_grid`); entries without a preference get an ad-hoc snake_case id.
- `SettingItem.key` is NOT wired in Phase 1 (that is the Phase 2 highlight anchor work).

## Current-Architecture Facts This Plan Relies On (verified this session)

- All 19 non-Home screens render through `ZToolSettingsList` → `SettingSection` →
  `SettingItem` in `ui/components/ZToolSettingsModel.kt`; the funnel is single.
- `ZToolTopAppBar` (`ui/components/ZToolScaffold.kt:155`) already exposes an `actions`
  slot — no shared-component change needed to add a search icon.
- Navigation is static-string routes in `navigation/ZToolNavHost.kt` (~20 routes);
  adding optional query args (`?target={id}`) is mechanical.
- `MainActivity` re-navigates to `MainRoute.Home` whenever `isEnvironmentReady` is false;
  the search page and result targets must respect the same gate.
- Existing full-screen search precedent: `screens/zuisetting/magicwindowsearch/searchPage.kt`.
- Locale resources: default `res/values/strings.xml` (zh) + `res/values-en-rUS/strings.xml`.
  Storing `@StringRes` ids in the index gives per-locale titles for free.
- Estimated index size: ~170 entries (11 feature cards + ~140 hook/detail setting rows +
  ~20 app-own setting rows). Phase 1 target: **≥95% coverage of all non-Home rows**,
  verified against a screen-by-screen checklist in this file.

## New Files

```text
app/src/main/java/com/qimian233/ztool/search/
  SearchIndex.kt            // data model + static index registry (the ~170 entries)
  SearchMatcher.kt          // locale-aware token matching + scoring
  SearchIndexBuilder.kt     // assembles visible entries per device (package presence)
  SearchViewModel.kt        // query state, debounce, result UiState
app/src/main/java/com/qimian233/ztool/screens/search/
  SearchMainRoute.kt        // full-screen search page (field + grouped results)
  SearchResultList.kt       // grouped result list UI
res/values/strings.xml      // + ~10 new strings (search placeholder, no-results, etc.)
res/values-en-rUS/strings.xml // same names, English
```

## Modified Files

```text
navigation/ZToolDestinations.kt      // HiddenRoute.SEARCH + result-target contract docs
navigation/ZToolNavHost.kt           // register SearchMainRoute; add ?target= support to
                                     // ~20 existing routes; result-click navigation logic
screens/features/FeaturesRoute.kt    // top bar search icon -> navigate(SEARCH)
screens/ztoolsettings/SettingsRoute.kt // top bar search icon -> navigate(SEARCH)
screens/<all 17 detail screens>      // accept optional target: String? nav arg and
                                     // forward it (Phase 1: consumed as a no-op marker;
                                     // highlight lands in Phase 2)  — see "Phase 1
                                     // simplification" below
```

## Step 1 — Index data model + registry (search/SearchIndex.kt)

Create `SearchEntry`:

```kotlin
data class SearchEntry(
    val id: String,                 // stable id, PreferenceKeys-based where applicable
    val route: String,              // destination route name (see contract below)
    val titleRes: Int,              // @StringRes primary label
    val summaryRes: Int? = null,    // @StringRes secondary label
    val keywordsRes: Int? = null,   // @StringRes comma-separated manual keywords (opt-in)
    val keywords: List<String> = emptyList(), // literal manual keywords (rare, e.g. EN/CN aliases)
    val requiresPackage: String? = null,      // hide result when package not installed
    val parentTitleRes: Int? = null,// conditional-child annotation: parent switch title
    val isFeatureCard: Boolean = false // true for the 11 Features-route cards
)
```

Route contract — `route` MUST be one of the existing static route names already
registered in `ZToolNavHost.kt` (`MainRoute.Features.name`, `FeatureDestination.X.route`,
`HiddenRoute.SYSTEM_UI_*`, `HiddenRoute.SETTINGS_*`, `SettingsAboutRouteName`). No new
destination routes in Phase 1; the search page itself is the only new route. For feature
cards, `route = MainRoute.Features.name` + the card's `FeatureDestination` encoded in a
companion field `featureDestination: FeatureDestination?` (add it to `SearchEntry`).

Screen-by-screen checklist for index coverage (tick while implementing, keep in this
file; source = the `grep -c SettingItem` census done in the analysis round):

- [x] FeaturesRoute: 11 cards (`SettingsDetail`, `GameTool`, `Ota`, `PackageInstaller`,
      `SystemUi`, `Launcher`, `MobileDesktop`, `Framework`, `SafeCenter`, `TbEngine`,
      `ZuiPerformance`) — `isFeatureCard = true`, `requiresPackage` = card packageName
      (Framework card: always visible, no package gate)
- [x] ZuiSettingsDetailScreen: 22 items (incl. Magic-Window search entry row)
- [x] FrameworkSettingsScreen: 18
- [x] LauncherSettingsScreen: 17
- [x] ControlCenterSettingsScreen: 15
- [x] SettingsRoute: 13
- [x] LockScreenSettingsScreen: 12
- [x] AnimationWallpaperSettingsScreen: 11
- [x] ThemeSettingsRoute: 10
- [x] StatusBarSettingsScreen: 8
- [x] SettingsAdvancedRoute: 7
- [x] PackageInstallerSettingsScreen: 6
- [x] OtaSettingsScreen: 5
- [x] GameToolSettingsScreen: 4
- [x] SafeCenterSettingsScreen: 3
- [x] MobileDesktopSettingsScreen: 3
- [x] SystemUiSettingsScreen (hub): 1 (the hub is one card grid; index the 5 sub-screen
      entry rows instead — they are `SettingItem.Entry`s)
- [x] TbEngineSettingsScreen: 6
- [x] ZuiPerformanceSettingsScreen: 2
- [x] SystemUiMiscSettingsScreen: 2
- [x] SettingsAboutRoute: index as 1 entry (screen-level, no SettingItems)

Conditional children (keep in index + `parentTitleRes` annotation) — known groups from
the analysis round: Launcher custom-grid sliders (2), app-icon-unmask dynamic (1),
search-recommendation + hot-word (2, under "clean search"), RAM beautify (1), whitelist
row (1), dock "larger dock" (1, hidden when dock disabled); plus equivalents found while
writing entries (AnimationWallpaper, ControlCenter, ZuiSettings etc.). Use the PARENT
switch's `titleRes` in `parentTitleRes`.

## Step 2 — Matcher (search/SearchMatcher.kt)

- Tokenize the query (split on whitespace; no pinyin).
- Score each entry: title-prefix hit > title-substring hit > summary hit > keyword hit;
  group results by best score. Case-insensitive; locale-aware lowercase via
  `String.lowercase(locale)`.
- Resolve all `@StringRes` against `LocalConfiguration`/app context at query time, so the
  result language follows the app locale automatically (zh default, en-rUS).
- Empty query: show nothing (or a short hint text), NOT the full index dump.

## Step 3 — Visibility filter (search/SearchIndexBuilder.kt)

- Reads installed packages (`PackageManager`, same try-catch posture as
  `FeaturesRoute.rememberInstalledPackages`).
- Drops entries whose `requiresPackage` is not installed, EXCEPT the Framework card and
  framework-screen entries (mirroring `alwaysVisible = true` in FeaturesRoute).
- No LSPosed scope check in Phase 1: out-of-scope entries remain listed; the scope-request
  dialog flow already exists on the Features page and still works when the user lands
  there. (Revisit if user feedback disagrees.)

## Step 4 — Search page (screens/search/SearchMainRoute.kt + SearchResultList.kt)

- Full-screen route `SearchRoute`-style scaffold: `ZToolScaffold` + `ZToolTopAppBar`
  with a back arrow and an embedded `TextField` (focus on entry, IME action = Search).
- Results grouped: "Feature cards" section first (if any), then per-screen groups
  ("系统框架 › 条目名"), each result row showing title + summary + conditional-child note
  ("需先开启〈parent〉" using `parentTitleRes`).
- Row click → `onResultSelected(entry)` → NavHost executes:
  - feature card → `navigate(MainRoute.Features.name)` then `navigate(featureDestination.route)`
    (two-step so back stack reads Features › detail, matching normal browsing);
  - plain entry → `navigate(entry.route)`.
  - Pop `SearchPageRoute` after navigation so system-back returns to the originating tab
    (Features or Settings), NOT back into search. Keep search itself reachable only via
    the icons.
- Gate behavior: `MainActivity.navigateFromRail` already forces Home when environment not
  ready; the search page inherits this automatically because it sits in the same NavHost.
  Result navigation additionally no-ops when the target gate would redirect (existing
  LaunchedEffect handles it).

## Step 5 — Wiring: top-bar icons + NavHost registration

- `FeaturesRoute.kt`: add search icon into `ZToolTopAppBar(actions = …)` →
  `onOpenSearch` lambda up through `FeaturesMainRoute` → NavHost
  `navigate(HiddenRoute.SEARCH)`.
- `SettingsRoute.kt`: same.
- `ZToolDestinations.kt`: `const val SEARCH = "search"`.
- `ZToolNavHost.kt`: `composable(HiddenRoute.SEARCH)` rendering `SearchMainRoute`.
- **Phase 1 simplification**: do NOT add `?target=` query args to the ~20 routes this
  phase. Since highlight is a Phase 2 feature, landing on the containing screen is the
  entire Phase 1 contract; `target` becomes meaningful only together with anchors. This
  shrinks the diff substantially. (Documented here so Phase 2 picks it up deliberately.)

## Step 6 — ViewModel

- `SearchViewModel`: `query: StateFlow<String>`, debounce 150ms, results =
  `SearchIndexBuilder.visibleEntries(context)` × `SearchMatcher.score(...)`, top 50.
  Keep the builder call off the main thread (`Dispatchers.Default`).
- UiState: `query`, `results: List<SearchGroup>`, `isSearching`.

## Step 7 — Strings (i18n)

Add to BOTH `res/values/strings.xml` (zh) and `res/values-en-rUS/strings.xml`, same
`name`s: `search_title`, `search_hint`, `search_no_results`, `search_group_feature_cards`,
`search_group_screen_prefix` (if needed), `search_requires_parent_switch` (format string,
one `%s`), plus any page-level strings discovered while building entries. Follow
`add_new_preference_key_zh-cn.md` conventions for naming.

## Step 8 — Verification & commit

1. `git status --short` — confirm only intended files touched.
2. `.\gradlew.bat assembleDebug` — must pass.
3. Manual smoke checklist (user-side): search from both tabs; zh + en-rUS queries;
   conditional-child annotation shows; feature-card result lands on the card's detail
   screen; back from detail returns to the originating tab; environment-not-ready flow
   unaffected.
4. `git diff --check`; commit (English, `.gitmessage` convention — suggest
   `module(search): add feature search page with index and cross-screen navigation`).

## Maintenance Rule (goes into the repo after Phase 1 lands)

- `docs/archive/hook_guide/add_frontend_item.md` gains a step: every new visible
  `SettingItem` row must add a matching `SearchEntry` (id = PreferenceKeys constant).
- Phase 3 (future): make `SettingItem.key` mandatory + debug-time self-check comparing
  rendered keys vs index; pinyin matching optional.
