# Plan: Feature Search Phase 2 — Anchor Scroll, Pulse Highlight, SettingItem.key

> AGENT-oriented implementation plan. Builds on the shipped Phase 1 (search index +
> search page + cross-screen navigation, commits 8ec28c2d / 2c255c63). Phase 2 adds the
> missing "land and point at the exact row" behavior: every indexed entry can scroll to
> its row on the target screen and pulse-highlight it.

## User Decisions (locked this session)

1. **Conditional-child fallback**: wait ~1.2s for the target row to render; if it never
   appears because the parent switch is OFF, fall back to highlighting the PARENT switch
   row instead. Requires `parentKey` in the search index.
2. **Highlight visual**: container-color double pulse — background fades in
   (`primaryContainer`), holds, fades out, repeats once (~2.4s total), then clears.
   AOSP-settings-like; works under both Miuix and M3-expressive row renderers.

## Agreed Defaults (applied unless user objects)

- **Highlight target = the `SettingItem` row wrapper** (the `ZToolSettingItem` call site
  inside `ZToolSettingsSection` / `ExpressiveSectionItems`), not the card. Highlighting
  the card would be ambiguous when a section holds many rows.
- **Scroll container stays as-is** (non-lazy `Column.verticalScroll` on 17 screens).
  Anchor offsets are captured via `onGloballyPositioned` relative to the scroll column;
  no LazyColumn migration. The two lazy exceptions (ZuiSettings inner list, Features
  grid) are handled by their own paths (see Step 6).
- **Key = id**: the mandatory `SettingItem.key` value MUST equal the `SearchEntry.id`
  for indexed rows. One namespace, debug-checkable.
- **`FeatureCard` (Features tab)**: highlight supported too (Phase 1 already navigates
  there for feature cards when tapped from search — but a search result that points at a
  specific row inside a detail screen does NOT route through the card; cards only
  highlight when they themselves are the target, which never happens in Phase 2 since
  cards aren't search targets. Out of scope, noted for completeness.)

## Architecture

```
Search page ── navigate(route?target=<SearchEntry.id>) ──▶ Target screen
Target screen:
  1. reads target id from nav args
  2. passes it down to ZToolSettingsList(sections, highlightTargetId)
  3. ZToolSettingsSection / ExpressiveSectionItems renders rows; each row wrapper:
       - reports "I have key=<id>, I am on screen, my offset is Y" into a
         HighlightAnchorRegistry (CompositionLocal-scoped per screen)
       - draws the pulse overlay when registry says this row is the active highlight
  4. screen-level HighlightController LaunchedEffect:
       - waits for registry to report the target key (or 1.2s timeout)
       - found  → scrollState.animateScrollTo(offset), start pulse, clear after
       - missed → if SearchEntry.parentKey present and that key appears → scroll to
                  parent + pulse parent; else clear silently
       - consumes the target (screen back stack keeps no stale highlight)
```

New pieces (package `com.qimian233.ztool.search.highlight` or
`com.qimian233.ztool.ui.highlight`, decided at implementation as
`ui/components` locality suggests the latter):

```text
ui/components/SettingHighlight.kt
  - class HighlightAnchorRegistry  // key -> offset provider; report/clear/await
  - data class HighlightRequest(id, generation)  // generation guards stale requests
  - LocalHighlightRegistry / LocalHighlightRequest CompositionLocals
  - Modifier.settingHighlightRow(key)  // row wrapper: registers offset + draws pulse bg
ui/components/ZToolSettingsModel.kt (modified)
  - ZToolSettingsList/Section: provide registry, pass highlight state down,
    apply Modifier.settingHighlightRow(item.key) per row (both style renderers)
ZToolNavHost.kt (modified)
  - ~20 routes gain optional query arg "?target={id}"; screens receive targetId: String?
  - Search result click now navigates with target= instead of Phase 1's bare route
SearchIndex.kt (modified)
  - SearchEntry gains parentKey: String? (drives fallback)
search/SearchViewModel or SearchMainRoute (modified)
  - result click builds "route?target=<id>" (feature cards unchanged: bare route)
```

## Why query-arg routes and not a shared VM channel

The NavHost owns navigation; a `?target={id}` optional arg keeps each screen's contract
explicit, survives process restore (savedStateStore), and needs no global singleton.
`navController.navigate("$route?target=$id")` with `route` declared as
`"$route?target={id}"` and `defaultValue = null` per destination is the standard
pattern; `composable` lambda reads `arguments?.getString("target")`.

## Steps

### Step 1 — SettingItem.key becomes the anchor namespace
- Keep `key: String?` nullable (no API break in Phase 2; mandatory-ization stays a
  Phase 3 decision as per Phase 1 plan).
- Inventory task: for each of the ~170 SearchEntry ids, set the matching `key = "<id>"`
  on the corresponding `SettingItem` construction across 19 screens. This is the bulk
  of the diff (mechanical). Rows inside `Custom` blocks (ForceStopModeRow,
  GridSliderRow, AiInputSettingsContent, custom clock/date blocks, charge-watts row…)
  take the key via their inner composable's wrapper — extend those private composables
  with an optional `modifier`/`highlightKey` param, or wrap the `Custom` item itself
  (preferred: set `key` on the `SettingItem.Custom` and let the row wrapper handle it,
  since `ZToolSettingItem(Custom)` already renders inside a Column).
- Non-indexable rows (dynamic reset/hot-reload detail rows) get no key — highlight
  simply never targets them.
- `parentKey` mirrors `parentTitleRes` pairings already in SearchIndex (e.g.
  `launcher_custom_grid_row.parentKey = "launcher_custom_grid"`).

### Step 2 — Highlight primitives (ui/components/SettingHighlight.kt)
- `HighlightAnchorRegistry`: `report(key, offsetInScrollContainer)`,
  `clear(key)`, `snapshot(): Map<String, Int>`, `await(key, timeout): Int?`
  (suspend, polls or uses a CompletableDeferred per key).
- `settingHighlightRow(key, request, modifier)`: registers via
  `onGloballyPositioned` (coords relative to the screen's scroll container root —
  obtain by also placing a zero-marker at the column top, or use `positionInRoot()`
  delta between the scroll column and the row; implementation detail, verify at build),
  and when `request?.id == key` animates background color:
  `animateColorAsState` driven by a 2-iteration `InfiniteTransition`-free timeline —
  use `LaunchedEffect(request)` with two `animateFloat(0→1→0)` cycles of 1.2s each
  (tween(400) up, tween(200) hold, tween(400) down) drawing
  `primaryContainer.copy(alpha = f * 0.9f)` behind content via `drawWithContent`.
- Row content must stay readable during pulse (alpha cap 0.9; container color is
  already a low-emphasis color).

### Step 3 — Funnel wiring (ZToolSettingsModel.kt)
- `ZToolSettingsList(sections, modifier, sectionSpacing, bottomPadding,
  highlightTargetId: String? = null)`:
  - remember a registry keyed by `highlightTargetId` generation,
  - CompositionLocalProvider(LocalHighlightRegistry),
  - after first composition frame with a registered target, hand off to the screen
    controller (Step 5).
- `ZToolSettingsSection` and `MaterialExpressiveSettingsSection` /
  `ExpressiveSectionItems`: wrap each `ZToolSettingItem` call in
  `Modifier.settingHighlightRow(item.key, activeRequest)`; Miuix path draws the pulse
  on the row Column (rows are transparent there), M3 path draws over the chip-shaped
  background (clip to `expressiveSettingsItemShape` so the pulse respects rounded
  corners).

### Step 4 — Route plumbing
- All ~20 `composable(route)` calls: `route = "$route?target={id}"`, add
  `arguments = listOf(navArgument("target") { type = NavType.StringType; nullable =
  true; defaultValue = null })`, pass `targetId` into the screen composable.
- Keep the original constants as the base path (`HiddenRoute.SEARCH` etc. unchanged).
  `MainRoute.Home/Features/Settings` names are plain enum names — appending `?target=`
  changes `destination.route` matching; verify `MainRoute.fromName` still matches
  (it compares full route string — so for the three main tabs use the pattern-ful
  route only if `fromName` is extended, otherwise keep main tabs arg-less: search
  never targets the three tab roots; app-settings rows target
  `MainRoute.Settings.name`? NO — app settings rows live on the Settings tab root.
  Decision: Settings tab root route stays arg-less; its rows are indexed with
  `route = MainRoute.Settings.name` and Phase 2 highlights are supported via a
  screen-local `highlightTargetId` state hoisted in `SettingsRoute` fed from nav args
  — which requires the Settings tab root to accept the arg too. Resolution: switch
  `MainRoute.fromName` to `route?.substringBefore('?')` matching and give all three
  main tabs the `?target={id}` pattern. Document this in code.)
- Search result click: `onOpenEntry` builds
  `entry.route + "?target=" + URLEncoder.encode(entry.id, UTF-8)`; feature cards keep
  the two-step navigation WITHOUT target (cards are not rows).
- The `navigationRouteIndex` table must strip query strings before comparing
  (`route?.substringBefore('?')`) — add this defensively in both
  `navigationRouteIndex` and `mainRouteIndex`.

### Step 5 — Screen-side controller (one shared composable)
- `HighlightController(highlightTargetId, scrollState, registry, onConsumed)`:
  LaunchedEffect(highlightTargetId):
  1. `withTimeoutOrNull(1200) { registry.await(target) }`
  2. found → `scrollState.animateScrollTo(offset)`, set activeRequest(generation++),
     wait pulse duration, clear.
  3. missed → look up `SearchIndex.byId(target)?.parentKey`; await parent similarly
     (another 400ms grace); pulse parent if found.
  4. `onConsumed()` — screen clears its local target state so rotation/back-reenter
     doesn't re-pulse.
- Screens with `verticalScroll` pass their existing `rememberScrollState()`. All 19
  screens already create it inline inside the screen Column — hoist it up one level in
  each screen (mechanical, part of Step 4's screen edits).
- ZuiSettingsDetailScreen: the LazyColumn there is a dialog-internal list, not the
  screen body; the screen body is still `verticalScroll` — no special casing.
- FeaturesRoute (grid): Phase 2 targets never land here; no work.

### Step 6 — Index: parentKey + byId lookup
- Add `parentKey: String? = null` to SearchEntry + `Screen.item(...)` param.
- Populate for every entry that has `parentTitleRes` (1:1 with the pairings listed in
  the Phase 1 index — mechanical).
- `SearchIndex.byId(id): SearchEntry?` lazy map.
- The parent rows themselves must have keys set (they all do — every parent is
  itself an indexed row).

### Step 7 — Strings
None required. (Pulse is visual-only; fallback needs no new text since the user sees
the parent row light up.)

### Step 8 — Verification
1. `.\gradlew.bat assembleDebug` clean.
2. Manual matrix (user-side, real device):
   - plain switch row (e.g. 搜索「隐藏蓝点」) → lands Launcher screen, scrolls, row pulses twice;
   - conditional child with parent ON (e.g. 行数滑条, after enabling 自定义网格) → row pulses;
   - conditional child with parent OFF → parent switch row pulses after ≤1.6s;
   - REQUIRE_OFF child (安装包管理器 rows with 行样式 enabled) → same fallback logic
     (parent row pulses when the child is invisible);
   - theme rows style-gated (AMOLED 黑 in Miuix style) → falls back to… nothing (no
     parentKey) → lands on screen, no highlight, no crash. Acceptable; note in docs.
   - Miuix AND Material3Expressive both exercised.
   - back from screen → no re-pulse; rotate device → no re-pulse.
3. Debug-only assertion (optional, cheap): in `HighlightController`, if target not
   found AND no parentKey, log a warning via `Log.w` — surfaces index/key drift in
   QA without user impact.
4. Commit (English): `module(search): add anchor scroll and pulse highlight for search targets`.

## Risks / notes

- Offset capture across nested padding: use delta of `positionInRoot()` between the
  scroll container's content column and the row; recompute on demand (await returns
  latest snapshot). Scroll-then-capture ordering is handled because the controller
  scrolls to the pre-highlight snapshot and the pulse draws at composition position
  (rows are always composed under verticalScroll, so offsets stay valid mid-scroll).
- `animateScrollTo` on a `verticalScroll` Column is stable API; content smaller than
  viewport clamps silently.
- Miuix rows are flush/transparent (no per-row clip) — pulse background draws a
  rounded-rect (12.dp) behind the row content; visually consistent with the card look.
- Process death with `?target=` arg: nav args restore, but highlight generation state
  is `rememberSaveable`-free — on restore the pulse replays once. Acceptable (arguably
  desirable); no extra work.
