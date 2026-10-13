# PLAN: all-apps A–Z sections and in-panel search for `com.zui.freeform.sidebar`

AGENT-oriented implementation plan. Continues `PLAN_sidebar_drawer_upgrade.md` (requirements 1–3
are done: drawer capacity, user ordering, A–Z rail). Background and host facts:
`docs/research/zux_freeform_bar_sidebar.md`.

Target host: **`com.zui.freeform.sidebar` 1.18.02.26.20.2** (`versionCode` 18). The obfuscated
1.18.5.0.26.38.2 build has none of the class names used here, so the module's existing version
guard still applies and this feature no-ops there.

## Goal

Turn the all-apps panel into a sectioned app drawer, matching the reference design the user
supplied (search field on top, a full-width letter heading before each group, the existing A–Z
rail on the right):

1. **A–Z sections** — apps are grouped by the pinyin initial of their label; every group starts on
   a new row and is introduced by a left-aligned letter heading (`#` for digits/symbols, which the
   host's sort already places first).
2. **Search** — a text field at the top of the panel filters by label, full pinyin, or pinyin
   initials; the sections are rebuilt from the matches and the rail follows.

Explicitly **not** in scope (confirmed with the user): merging the top-5 drawer row into the panel.

## 1. Host facts that shape the design

| Fact | Consequence |
|---|---|
| `showAllAppWindow()` builds `AppIconRecyclerAdapter` + `GridLayoutManager(5)` on every show; nothing else ever touches `mAppIconGv` | All interception can hang off the existing `showAllAppWindow` after-hook |
| `AppIconRecyclerAdapter.getItemViewType`/`onBindViewHolder` read `mAppInfoList.get(position)` | Model positions must be translated to host list indices before the original runs |
| `AppIconRecyclerAdapter.onCreateViewHolder` **ignores** its `viewType` and always inflates `appgriditem` | No need to hook it: a heading can reuse the very same item view |
| `appgriditem` = `AppIconItemView` (RelativeLayout) with `appicon_iv` (fixed size, `layout_centerHorizontal`) + `appname_tv` (`match_parent`, `layout_below`, centered) | A heading is produced by hiding the icon and re-anchoring the label to the row start |
| The host's item click is `mOnItemClickListener.onItemClick(i)` → `startActivity(mAppAllList.get(i))`, and the adapter sets it per bind with the **bind index** | Passing the host list index into `proceed()` keeps launching correct with no click code of ours |
| `GridLayoutManager.getSpanSizeLookup()` is public; the default lookup returns 1 | A heading gets the whole row by hooking the lookup instance's `getSpanSize` |
| `drawer_window.xml`: `allapp_rl` (RelativeLayout, `paddingTop 21dp`) → `appicon_gv` (RecyclerView) + a `match_parent` FrameLayout holding `index_bar` (`visibility="gone"`) | The search field goes into `allapp_rl`; both siblings take a `layout_below` rule |
| The module never statically references `androidx.recyclerview` (the host has its own copy; ZTool ships none) | Everything is done through `Any` + reflection, exactly like the rail and the badge decorator |
| `PinYinUtils.toPinYin(String)` (static, per-character pinyin4j) is the host's own sort key | Letter/pinyin/initials all come from it, so sections and sort order cannot disagree |

## 2. Hook design

### 2.1 Model — `SidebarAllAppsModel.kt`

Flat row list built from the host's app list plus a query:

* `Row.Header(letter)` and `Row.App(app, hostIndex)`.
* `submit(apps, query)` rebuilds it: filter, then emit a header whenever the letter changes.
* `hostIndex` is the index into the host's `mAppAllList`, i.e. what the host's bind and click
  need; `appAt(position)` returns the app for a row, `null` for a header.
* Per-app metadata (label, pinyin, initials, letter) is computed once per app and cached by
  `pkg:userId`; the cache is dropped on every install.
* Matching: label `contains`, full pinyin `contains`, initials `contains` (case-insensitive).

### 2.2 Adapter interception — `SidebarAllAppsGrid.kt`

Hooks, installed once per process, all no-ops while `chain.thisObject` is not the adapter the
current panel installed:

| Target | Behaviour |
|---|---|
| `AppIconRecyclerAdapter.getItemCount()` | model size |
| `AppIconRecyclerAdapter.getItemViewType(int)` | `HEADER_TYPE` for a header row; otherwise the original, called with the translated host index |
| `AppIconRecyclerAdapter.onBindViewHolder(VH, int)` | for a header: reconfigure the bound item view into a heading and stop; otherwise `proceed` with the translated host index |
| the `SpanSizeLookup` instance's `getSpanSize(int)` | `spanCount` for a header row, otherwise the original |

The host calls `getItemViewType` **from inside its own `onBindViewHolder`** with the index we
pass, so the two are disambiguated with a thread-local "binding" flag rather than by position
arithmetic.

Heading rendering reuses the host item view: icon `GONE`, label text = letter, left-aligned, own
size/colour/padding, `layout_below` replaced by a start anchor. RecyclerView keeps one view pool
per view type, so a view converted to a heading is never bound as an app again.

### 2.3 Search field — `SidebarSearchBar.kt`

An `EditText` built in code and inserted as the first child of `allapp_rl`; every other child gets
`layout_below` it, so the grid and the rail keep their own space. Installed once per service
process (the panel's view tree is inflated once), reset to empty on every panel show. Its own
rounded background, hint from `strings.xml`, `IME_ACTION_SEARCH`, no extract UI.

### 2.4 Rail — `SidebarIndexRail.kt`

`attach(panel, grid, source)` now takes a `SidebarLetterSource` (letter → adapter row, plus
"which letter owns this row") instead of deriving positions from the raw app list, because the
rows now carry headings. Scroll sync asks the source for the letter of the first visible row.

### 2.5 Badges — `SidebarBadgeDecorator.kt`

`refresh(gridView, appAt, member, toggle, addAllowed)` takes a `position -> app` provider instead
of reading the host adapter's own list field, which no longer lines up with adapter positions. A
header resolves to `null`, which is the decorator's existing "no app here" path.

### 2.6 Wiring — `SidebarDrawerHook.kt`

The existing `showAllAppWindow` after-hook becomes: install the grid (model + span hook), attach
the search field, re-attach the rail, refresh the badges. `PackageReceiver` refreshes the grid as
well, so an install/removal while the panel is open does not leave stale rows.

## 3. Settings

| Key | Type | Default | Meaning |
|---|---|---|---|
| `sidebar_allapps_sections` | bool | true | letter headings + one row per group |
| `sidebar_allapps_search` | bool | true | in-panel search field |

Both are gated by `sidebar_drawer_enhance` in the UI, carry a `SearchIndex` entry, a
`SettingItem.Switch` row and strings in both locales.

## 4. Verification

1. `.\gradlew.bat assembleDebug`.
2. `.\gradlew.bat testDebugUnitTest --tests "com.qimian233.ztool.SearchIndexConsistencyTest"`.
3. `python tools/check_unused_strings.py` — both locales in sync, no new UNUSED.
4. On device (host 1.18.02.26.20.2): sections start on a new row, `#` leads, the rail highlights
   the group at the top while scrolling and jumps to a tapped letter, typing filters by name,
   pinyin and initials, tapping a filtered result launches that app (index mapping), and edit-mode
   badges only land on app rows.
5. Regression: the 1.18.5.x build still no-ops.

## 5. Risks

1. **Heading look is a reconfigured app item.** The letter uses the host's `appname_tv`; if a
   future host build re-anchors it differently the heading degrades but cannot crash.
2. **Span hook is per class.** The default `SpanSizeLookup` is instantiated per
   `GridLayoutManager`; the host builds exactly one, and the hook ignores positions that are not
   header rows.
3. **IME.** The panel's popup takes input focus on its own (verified earlier, §9 of the drawer
   plan); the keyboard overlays rather than resizes the panel, so the field stays pinned at the
   top and the grid scrolls under it.
4. **Row count.** Headings add ~30 rows; the panel is a RecyclerView, so only visible rows cost
   anything.

## 6. Implementation status

Written and built (`assembleDebug`, `SearchIndexConsistencyTest`, `check_unused_strings.py` all
pass); not yet exercised on a device. New files: `SidebarAllAppsModel.kt`,
`SidebarAllAppsGrid.kt`, `SidebarAllAppsSearch.kt`. Changed: `SidebarDrawerHook.kt`,
`SidebarIndexRail.kt`, `SidebarBadgeDecorator.kt`, `SidebarPressScale.kt`, `SidebarStrings.kt`,
`PreferenceKeys.kt`, the freeform settings repository/viewmodel/screen, `SearchIndex.kt` and both
locale string files.
