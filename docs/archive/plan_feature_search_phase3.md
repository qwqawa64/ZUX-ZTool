# Plan: Feature Search Phase 3 — Mandatory Keys, Index Audit, Pinyin, Residual Rows

> AGENT-oriented implementation plan. Builds on Phase 1 (index + search page +
> navigation, commits 8ec28c2d / 2c255c63) and Phase 2 (anchor scroll + pulse highlight,
> commits 4b0f965d / 28a04f26 / dcf2e7ca).

## User Decisions (locked this session)

1. **`SettingItem.key` becomes compile-time mandatory** (non-null, no default).
2. **Debug-only index audit** — release builds pay zero cost; no CI unit test this phase.
3. **Pinyin matching added** via TinyPinyin (pinyin-initial + full-pinyin matching).
4. **Fix the ~7 Phase-2 residual rows** that cannot land directly (shared `Custom`
   blocks) so every indexed row is directly reachable.

## Current Facts (verified this session)

- 194 `SettingItem` constructions across 19 screens; ~150 already carry `key`
  (= SearchEntry ids from Phase 2). ~44 lack one: decorative notes (e.g. PkgMgr
  Android-16 note, ZuiForce summary), dynamic detail rows (Advanced reset/hot-reload
  results, API version), and helper-produced rows.
- Only `ZuiSettingsDetailScreen` still has structural key gaps (6 constructions);
  every other file's non-keyed items are decorations/dynamic rows.
- Phase-2 residuals: 4 clock rows in `CustomClockConfig`/`CustomDateConfig` (bare
  `ZToolSwitchRow`/`ZToolSliderRow`/`ZToolArgbColorTextFieldRow`, not `SettingItem`s),
  `control_center_normal_tile_corner_radius` (second slider inside one shared Custom),
  plus index rows whose parentKey fallback was acceptable. These need small structural
  refactors, not new UI.
- Repositories already include mavenCentral + aliyun mirrors; TinyPinyin
  (`com.github.promeg:tinypinyin:2.0.3`) is on JitPack — a JitPack repo entry is
  needed, OR use the aliyun-hosted mirror if resolvable; verify at implementation.
  Size ~200KB, pure Java, minSdk-safe.
- `search/SearchMatcher.kt` is a small single object — pinyin support slots in without
  touching the index schema.
- `add_frontend_item.md` currently documents the pre-search workflow and must gain the
  index-registration + key steps.

## Steps

### Step 1 — Make `key` compile-time mandatory
- In `ui/components/ZToolSettingsModel.kt`, change every `SettingItem` variant's
  `key: String? = null` to `key: String` (no default, positioned as the LAST parameter
  so trailing-lambda style stays unaffected; `Custom` also takes `key: String`).
- Mechanical fallout (~44 sites):
  - Indexed rows: nothing to do (already keyed).
  - Decorative/dynamic rows: give stable, namespaced keys that are intentionally NOT
    in the index, prefix `deco_` / `dyn_` (e.g. `deco_framework_android16_note`,
    `dyn_advanced_reset_detail`). The audit (Step 3) treats `deco_`/`dyn_` prefixes as
    exempt. This keeps every call site total while preserving index semantics.
- Build must pass with zero new warnings.

### Step 2 — Residual direct-landing fixes (Phase-2 leftovers)
- `CustomClockConfig` (StatusBar) & `CustomDateConfig` (ControlCenter): the four clock
  sub-rows are plain composables inside a `SettingItem.Custom` shell. Refactor: add an
  optional `modifier` param path by wrapping each sub-row with
  `HighlightableSettingRow(highlightKey = ...)` directly (the wrapper is public and
  composable-safe inside `Custom` content), OR split each sub-row into its own
  `SettingItem` (`Custom` per row). Preferred: split into separate `SettingItem`s —
  keeps the highlight funnel single-path (wrapper applies `item.key` automatically).
  - status_bar_clock_text_size / letter_spacing / text_color / text_bold
  - control_center_custom_clock_text_size / letter_spacing / text_color / text_bold
- `control_center_normal_tile_corner_radius`: split `QsRoundCornerRadius` Custom into
  two `SettingItem`s (one per slider), each keyed.
- After this step, EVERY SearchEntry id has a same-named `SettingItem.key` render
  target; verify with the Step-3 audit on all 19 screens.

### Step 3 — Debug index audit (anti-drift)
- New file `search/SearchIndexAudit.kt`:
  - `auditRoute(route: String, renderedKeys: Set<String>, registry: HighlightAnchorRegistry)`:
    compares `SearchIndex.all.filter { it.route == route }.map { it.id }` against the
    keys actually rendered on that screen; logs `Log.w(TAG, …)` for index-ids with no
    rendered key and rendered keys with no index entry (excluding `deco_`/`dyn_`).
  - Trigger: `HighlightController`'s screen already holds the registry — add the audit
    call behind `BuildConfig.DEBUG` when a screen finishes first composition (reuse the
    controller's LaunchedEffect with a short delay; no target needed for the audit).
- Zero cost in release (`if (BuildConfig.DEBUG)` short-circuits before any work).
- `SearchIndex.groupTitleRes == R.string.search_group_feature_cards` entries are
  exempt (feature cards render on the grid, not as SettingItems).

### Step 4 — Pinyin matching
- Add `com.github.promeg:tinypinyin:2.0.3` to `gradle/libs.versions.toml` +
  `app/build.gradle.kts`; add `maven { url = uri("https://jitpack.io") }` to
  `settings.gradle.kts` `dependencyResolutionManagement.repositories` (content-filtered
  to `com.github.promeg` to keep FAIL_ON_PROJECT_REPOS hygiene).
- Precompute per-entry pinyin strings lazily ONCE per process:
  - `pinyinFull`: full pinyin of the title (lowercase, no separators),
  - `pinyinInitials`: first letters of each syllable.
  - Cache in a `Map<Int(entry id), Pair<String, String>>` built on first query on
    `Dispatchers.Default`; ~170 entries is negligible.
- `SearchMatcher.score` gains two lower tiers: pinyinFull-contains >
  pinyinInitials-contains (below keyword hits, above nothing). Query tokens are
  matched against these strings as-is (ASCII input hits Chinese titles).
- Chinese titles only; English titles naturally have identity pinyin (skip via a fast
  ASCII check).

### Step 5 — Documentation / maintenance
- `docs/archive/hook_guide/add_frontend_item.md`: add a step "register a SearchEntry +
  set the mandatory `key` (same id)" with a pointer to SearchIndex and the audit
  behavior.
- `docs/archive/plan_feature_search_phase2.md`-style checklist not needed here; the audit IS the
  long-term checklist.

### Step 6 — Verification
1. `.\gradlew.bat assembleDebug` clean.
2. Real-device spot checks: pinyin queries ("ztl"→ 状态栏, "kzzx"→ 控制中心), clock
   sub-rows now land directly (no parent fallback), audit logs empty on all 19 screens
   (debug build), release build behavior unchanged.
3. Commit: `module(search): mandatory setting keys, debug index audit, pinyin matching`.

## Risks / notes

- Mandatory `key` touches the shared model API; any out-of-tree screen code would break
  at compile time — that is the point.
- TinyPinyin on JitPack: if resolution fails behind the existing mirror setup, fall
  back to `com.github.houbb:pinyin` (mavenCentral) or vendor the small IME dict file.
  Decide at implementation time; the matcher interface does not change.
- The audit is advisory (Log.w only) by design; if it proves noisy, escalate later.
