# PLAN: sidebar drawer capacity, user ordering and all-apps search for `com.zui.freeform.sidebar`

AGENT-oriented implementation plan. Background: `docs/research/zux_freeform_bar_sidebar.md`
(structure of the currently-hooked build).

Target host: **`com.zui.freeform.sidebar` 1.18.02.26.20.2** (`versionCode` 18, the build that
is in JADX / on the device). Reference build: **1.18.5.0.26.38.2** (`versionCode` **also 18**),
decompiled with `D:\Program Files\Jadx\bin\jadx.bat` into
`F:\USER\Jadx项目\Lenovo\ZuxFreeformBar_1.18.5.0.26.38.2.jadx`.

## Goal

Give the old build what it lacks, without porting the new build's code:

1. **Drawer capacity** above the hard-coded 5 slots.
2. **User-editable drawer ordering**, ideally `2 recent slots + N user-ordered slots`.
3. **All-apps A–Z rail / grouping** — the panel's order is already correct pinyin; the rail was
   never wired (§4.5).
4. **Search in the all-apps panel.**

## 0. Decisions taken

| Question | Decision |
|---|---|
| Editing UX | **Stage B** — a `编辑` entry that switches the drawer into edit mode with per-row `▲`/`▼`/`✕` (keeps the host `ListView`; no drag-and-drop). Stage A (long-press menu) stays as a fallback if Stage B's row affordances turn out to be too cramped. |
| Search placement | **In-panel `EditText` — verified working on-device, no window-flag change needed.** See §5.1 and §9. |
| Implementation start | **Ready.** Requirement 3 was confirmed as **H1** (order is correct, the A–Z rail/grouping is missing) — see §4.5 and §8. |

## 1. Host facts (old build 1.18.02.26.20.2)

| Fact | Where |
|---|---|
| Drawer = `ListView@id/appicon_lv` inside `LinearLayout@id/drawer_layout` of `PopupWindow` over the overlay window | `FloatService.initSideBarView` |
| Drawer item adapter = `ui.AppIconListAdapter`; `getCount()` returns a literal **5** and `getView()` early-returns for `i >= 5` | `AppIconListAdapter` |
| Drawer content = `mAppTop5List`, filled from `SidebarAppUtils.generateTop5App(Context)` (2 call sites: the drawer-open lambda and `FloatService.PackageReceiver.onReceive`) | `FloatService` |
| Drawer slot store = `freeform_preferences` (`MODE_MULTI_PROCESS`), keys `top_1`…`top_5`, values `pkg:userId` | `utils.PreferenceUtils` |
| The host **overwrites** `top_1..top_5` from `Settings.Secure.cur_visible_freeform_list` | `SidebarAppUtils.ChangeContentObserver` → `refreshFreeFormApp` → `refreshTop5App` |
| Drawer item click = `ListView.setOnItemClickListener`; footer position (`i >= mAppTop5List.size()`) opens the all-apps panel | `FloatService.m34lambda$new$1$…` |
| **Bug**: after any `PACKAGE_REMOVED`, the listener is rebuilt with a hard-coded `if (i < 5)` | `FloatService.PackageReceiver.onReceive` |
| All-apps panel = `RecyclerView@id/appicon_gv` (5-column `GridLayoutManager`) in `RelativeLayout@id/allapp_rl`, adapter `ui.AppIconRecyclerAdapter`, data `mAppAllList` | `FloatService.showAllAppWindow` |
| All-apps click → `mAppAllList.get(i)` (reads the **field**, not the adapter list) | `FloatService.m37lambda$showAllAppWindow$4$…` |
| All-apps sort = pinyin of `userId + appName` | `SidebarAppUtils.covertMapToList` + `PinYinUtils` |
| A–Z index bar is **dead**: `mIndexBar` / `appsAlphabeticIndexBar` fields are never assigned | `FloatService` |
| Overlay window is `FLAG_NOT_FOCUSABLE` (`flags = 296`) | `FloatService.initSideBarView` |

## 2. Reference facts (new build 1.18.5.0.26.38.2)

The new build is R8-obfuscated (`J0.*`, `I0.*`), so nothing can be copied verbatim — but its
data model is fully readable and is what the old build should converge on.

| Feature | Implementation |
|---|---|
| Drawer adapter | `I0.f` = `NewDrawerAppAdapter`, `RecyclerView.Adapter`, `MAX = 16` |
| Drawer capacity | `getItemCount()` returns `list.size()`, or **pads to 16** when in edit mode (`f1534f != 0`) so empty slots can be dropped into |
| Visible rows | computed in `J0.C0270t.A(...)`: **9 rows portrait / 7 landscape**, reduced until the popup fits (`B(context)` = screen − status bar) |
| Drawer composition | `J0.C0270t.v()` = pinned SQLite apps (type 0) + **dynamic/recent, hard-capped at 2** (type 1) + divider (type 3) + `top_1..top_16` (type 2) |
| Recent source | `Settings.Secure.cur_visible_freeform_list` → `J0.C0270t.s0()`, `if (i2 < 2)` |
| User order store | `freeform_preferences` → `top_1`…**`top_16`** + `sidebar_fixed_apps_customed = true`; written by `J0.C0270t.i0()` (`saveCustomeAppList`), padded with `""` |
| Reorder | `ItemTouchHelper` (`I0.f$a`): `makeMovementFlags(3, 0)`, `onMove` → `Collections.swap` + `notifyItemMoved`, `clearView` → `C0270t.r0(list)` |
| Remove | per-item ✕ (`drawerappitem.xml@id/delete_button_container`, `@string/float_edit_remove`), shown when **not** in edit mode |
| Add | per-item ＋ (`drawerallappitem.xml@id/add_button_container`, `@string/float_edit_add`), shown when in edit mode for apps not already in the drawer (item types 4/5 = already present) |
| Edit-mode strings | `float_edit` 编辑 / `float_edit_add` 添加 / `float_edit_down` 完成 / `float_edit_remove` 删除 |
| Pinning | `PinAppReceiver` (action `…action.PIN_APP`) + `db.FixedAppProvider` (`content://com.zui.freeform.sidebar.provider/fixed_apps`, columns `_id, rank, package_name, class_name, app_label, icon, count, action`) |
| All-apps panel | `x2()` = `showAllDrawerView()`: `I0.b(this, C0270t.u(this))`, i.e. the **full pinyin-sorted app list**, in a **6-column** `GridLayoutManager`; `bVar.n(C0270t.K())` additionally marks apps already in the drawer (item types 4/5) |
| All-apps sort | **identical to the old build** — `J0.C0258g.r()` with `J0.Y.b(userId + appName)`, and `J0.Y` is a byte-for-byte equivalent of `PinYinUtils`; `C0270t.u()` returns that same map's values |
| All-apps search | **absent** (confirmed: no filter, no `EditText`, no search entry in `I0.b`) |
| A–Z index bar | also dead (`F0.c.f478i` is bound but never used) |

**Conclusion from the diff**: the new build's advantage is *not* a better sort algorithm — it is
the 16-slot store plus an edit mode with drag/＋/✕. That is exactly what has to be reproduced.

## 3. Requirement → gap → hookability

| # | Requirement | Old build | New build | Hook verdict |
|---|---|---|---|---|
| 1 | Drawer shows many apps | hard cap 5 (`getCount()==5`, `getView` early-return, `generateTop5App` `min(5, …)`) | 16 slots | **Yes**, clean |
| 2 | Editable drawer order | none — order is recency-driven and rewritten by the host | drag + ＋/✕ + `top_1..16` | **Yes**, via a hook-owned adapter + injected menus |
| 2b | `2 recent + N user` hybrid | n/a (host already front-loads recents, but not user-orderable) | has 2 dynamic + up to 16 fixed, separated by a divider (not interleaved) | **Yes** — this is purely our composition rule |
| 3 | All-apps "reasonable" order | order **is** correct pinyin; the A–Z rail/grouping was never wired (`mIndexBar` / `appsAlphabeticIndexBar` unassigned, `index_bar` `gone`) | same order; rail also dead | **Confirmed H1** → the work is to finish the rail, §4.5 |
| 4 | All-apps search | absent | absent | **Yes, with one real risk** — IME focus, see §5.4 |

## 4. Hook design

### 4.0 Scope and skeleton

* `ScopeKeys`: add `FREEFORM_SIDEBAR("com.zui.freeform.sidebar")` with its `HowToRestart`.
* `scope.list`: append `com.zui.freeform.sidebar`.
* New module `hook/modules/com/zui/freeform/sidebar/SidebarDrawerHook.kt` (extends
  `AppHookModule`, `getModuleName()` = new `PreferenceKeys` entry,
  `getTargetPackages()` = `arrayOf(ScopeKeys.FREEFORM_SIDEBAR.name)`), registered in
  `HookManager`.
* Helpers in the same package: `SidebarDrawerStore.kt` (order/recency persistence),
  `SidebarDrawerAdapter.kt` (hook-owned `BaseAdapter`), `SidebarSearchBox.kt`,
  `SidebarEditMenu.kt`.
* **Version guard**: the new build has none of the old class names. Resolve every target with
  `runCatching` and bail out of the whole module when
  `com.zui.freeform.sidebar.ui.AppIconListAdapter` is missing. Do **not** gate on
  `versionCode` (both builds are 18); use `versionName` only as a log hint.

### 4.1 Capacity — own the drawer adapter

Do **not** patch `AppIconListAdapter.getCount()`/`getView()`: `getView`'s `i >= 5`
early-return returns a recycled/`null` view, and patching both is more fragile than replacing
the adapter.

Instead:

1. Hook `FloatService`'s drawer-open method (dex name
   `lambda$new$1$com-zui-freeform-sidebar-FloatService`, merged with the private
   `showTop5PopupWindow`; resolve by scanning declared methods for the `lambda$new$1$` prefix)
   and `FloatService.PackageReceiver.onReceive`, both **after** the original runs.
2. In the after-hook, via `findField`: read `mAppTop5List`, `mAppIconLv`, `mDrawerLayout`.
3. Replace the adapter with our own `BaseAdapter` inflating
   `R.layout.applistitem` + `R.id.appicon_iv` (resolve ids through the host's
   `R$layout`/`R$id` classes, not hard-coded ints), rendering the list built in §4.2, including
   divider rows and an optional badge.
4. Set our own `OnItemClickListener` on `mAppIconLv` (replacing the host's, which also fixes
   the `if (i < 5)` bug in `PackageReceiver`): `position >= list.size` → footer → open the
   all-apps panel (call the host's `showAllAppWindow`); otherwise launch + record recency.
5. Write our list back into `mAppTop5List` so any host code path that reads the field stays
   consistent.
6. The drawer height is fixed by the host's one-shot `OnGlobalLayoutListener`
   (`min(measured, heightPixels − 150)`), so a longer list simply scrolls. Cap the **visible**
   rows the same way the new build does (9 portrait / 7 landscape) by capping the popup height
   if it turns out not to scroll on device.

### 4.2 Ordering — `2 recent + N user-ordered`

Hook `SidebarAppUtils.generateTop5App(Context)` (the single source of `mAppTop5List`) and
return our own list:

1. **User order** — read `ztool_drawer_order` from the host's own
   `freeform_preferences` (`context.getSharedPreferences("freeform_preferences", 4)`); value is
   a `;`-joined `pkg:userId` string. Using a private key namespace means the host's
   `refreshTop5App()` writer cannot clobber it (see §4.3).
2. **2 recent slots** — `ztool_drawer_recent` (our own record, appended on every drawer
   launch) with `Settings.Secure.cur_visible_freeform_list` as the fallback seed. Take
   `min(recentSlotCount, 2)` entries not already in the user list.
3. Compose `recent + userOrder`, de-duplicate, resolve each key through
   `SidebarAppUtils.mLauncherAppInfoList` (private static `HashMap<String, AppInfo>`), drop
   nulls, truncate to the configured capacity.
4. `AppInfo` and its getters are public → no further reflection needed.

Optionally insert a divider marker `AppInfo(null pkg)` after the recent block and render it as
a rule in our adapter, mirroring the new build's type-3 divider.

### 4.3 Neutralise the host's recency writer

`SidebarAppUtils.ChangeContentObserver.onChange` → `refreshFreeFormApp` → `refreshTop5App`
rewrites `top_1..top_5` on every `cur_visible_freeform_list` change. Because §4.2 keeps its
state under our own keys this is harmless, but the hook should still hook
`SidebarAppUtils.refreshTop5App(Context)` and short-circuit it while the feature is enabled, so
the host cannot fight the user's order (and so the old build stops rewriting `top_5`).

### 4.4 Edit UI — chosen: footer `编辑` mode with `▲`/`▼`/`✕`

* Append an `编辑` row to our adapter (or repurpose the footer row) that toggles edit mode on
  our hook-owned adapter; in edit mode every drawer row shows `▲` / `▼` / `✕` affordances, and
  `✕` on the last row exits edit mode (`完成`).
* While in edit mode the all-apps panel marks apps already in the drawer and shows a `＋` to
  add them — the analogue of the new build's item types 4/5 and `add_button_container`.
* Keep the host `ListView@id/appicon_lv`; do **not** swap it for a `RecyclerView`. Drag-and-drop
  (`ItemTouchHelper` + `Collections.swap`, as in `I0.f$a`) is only worth it if the `▲`/`▼`
  affordances prove too cramped for a 9–16 row drawer.
* Fallback (Stage A) if Stage B's rows are too tight: long-press an item for a
  `上移 / 下移 / 置顶 / 移除` action sheet. UI container note: a `Service` cannot show an
  `AlertDialog` without a window token — use a `PopupWindow` like the host does, or set
  `dialog.window.setType(2038)` before `show()`.

### 4.5 All-apps ordering — confirmed as H1: wire up the dead A–Z rail

Confirmed with the user: the panel's order **is** the intended pinyin order; what is missing is
the A–Z rail / grouping, which makes it read as unordered. So this requirement is not a sort
fix, it is a **feature that was never finished in the old build**.

The host already ships the pieces, all unused:

* layout `res/layout/drawer_window.xml` → `FrameLayout` → `zui.widget.AlphabeticIndexBar@id/index_bar`
  (`layout_gravity="end|center_vertical"`, `visibility="gone"`);
* `FloatService.mIndexBar` (`AlphabeticIndexBar`) and `FloatService.appsAlphabeticIndexBar`
  (`ui.AppsAlphabeticIndexBar`) fields, **never assigned**;
* `ui.AppsAlphabeticIndexBar` exists but is **not reusable here**: `setIndexBar(GridView, …)`
  type-checks `adapter instanceof AppIconGridAdapter`, while the panel is a `RecyclerView` +
  `AppIconRecyclerAdapter`.

**Design (hook-side, ~1 small class):**

1. In the `showAllAppWindow` after-hook, resolve `mDrawerContent.findViewById(id("index_bar"))`
   and store it in `mIndexBar` (keeping the host field consistent).
2. Derive the letter for an entry as the uppercase first character of
   `PinYinUtils.toPinYin(appLabel)`; anything outside `A–Z` becomes `#`.
3. `setValidAlphabetList(letters actually present)` so the rail only shows reachable letters.
4. `setOnFocusedAlphabetChangeListener { _, focused -> mAppIconGv.scrollToPosition(firstIndex(focused)) }`.
5. `mAppIconGv.addOnScrollListener { … setFocusAlphabet(letterOf(firstVisibleItem)) … }`.
6. Toggle `index_bar` visibility (e.g. only when the panel holds more than ~20 entries).
7. **Rail/order consistency**: today's sort key is `pinyin(userId + appLabel)`, so on a
   multi-profile device the rail restarts at every profile boundary. If the device has profiles,
   also hook `SidebarAppUtils.covertMapToList` and sort by `pinyin(appLabel)` with the user id as
   a tiebreaker, so the rail stays monotonic. On a single-profile device the host sort already
   matches the rail and step 7 can be skipped.
8. Optional, independent of the rail: switch the comparator to
   `java.text.Collator.getInstance(Locale.CHINA)` on the label, which orders Latin before CJK and
   is what most users expect from an A–Z rail.

### 4.6 Search in the all-apps panel

Hook `FloatService.showAllAppWindow()` (private, stable name) **after**:

1. Resolve `mAllAppLayout` (`RelativeLayout`), `mAppIconGv` (`RecyclerView`), `mAppAllList`.
2. Snapshot the full list into hook state; add (or reuse, tagged) an `EditText` at the top of
   `mAllAppLayout`, push the grid down by its height, and shrink the panel accordingly.
3. On text change, filter the snapshot by: case-insensitive `appName.contains(q)`,
   `PinYinUtils.toPinYin(appName).contains(q)` (call the host's own helper, so "weixin" matches
   微信), or `pkgName.contains(q)`.
4. Apply the result to **both** `mAppAllList` (field) and the adapter's `mAppInfoList`
   (private field) and `notifyDataSetChanged()`. Swapping the field is what keeps the host's
   click lambda (`mAppAllList.get(i)`) index-correct.
5. Reset the query on every `showAllAppWindow` entry so each open starts unfiltered.

### 4.7 Settings and frontend

New `PreferenceKeys` (each with a `SearchIndex` entry, `SettingItem` row and strings in both
locales, per the repo rules):

| Key | Type | Default | Meaning |
|---|---|---|---|
| `sidebar_drawer_slots` | int | 8 | drawer capacity (6–8, see below) |
| `sidebar_drawer_recent_slots` | int | 2 | how many leading slots are recency-driven (0–3) |
| `sidebar_drawer_edit_menu` | bool | true | long-press editing (Stage A) |
| `sidebar_all_apps_search` | bool | true | inject the search box |
| `sidebar_all_apps_rail` | bool | true | §4.5 A–Z rail |
| `sidebar_all_apps_rail_min_items` | int | 20 | hide the rail on short lists |
| `sidebar_all_apps_collator_sort` | bool | false | §4.5 step 8 (`Collator` instead of per-char pinyin) |

Plus a `重置抽屉顺序` action. Because the order lives in the host's private prefs, the reset is
either an in-sidebar menu entry or a root/shell write from the module app.

## 5. Risks and mitigations

1. **IME focus on the search box — RESOLVED on-device, no mitigation needed.** The assumption
   that `FLAG_NOT_FOCUSABLE` on the overlay would block the IME turned out to be wrong: the panel
   is a separate focusable `PopupWindow` (sub-window) that takes input focus on its own. Verified
   twice on TB710FU (ZUXOS, sidebar 1.18.02.26.20.2) — see §9. Consequence for §4.6: do **not**
   touch `mLayoutParams.flags`; the probe's phase B (clearing the flag) produced no behavioural
   difference.
   Remaining layout note from the same run: the keyboard **overlays** rather than resizes the
   panel (the panel kept its height; the screenshot shows the grid behind the keyboard), so keep
   the search box pinned at the top of `allapp_rl` and let the grid scroll, or set the popup's
   `softInputMode` to `ADJUST_RESIZE` if the grid should shrink instead.
2. **`System.exit(0)` in `FloatService.onDestroy()`** kills the whole `android.uid.system`
   process. Never force a service restart from the hook; use the module's scope-restart path.
3. **Synthetic lambda names** (`lambda$new$1$com-zui-freeform-sidebar-FloatService`) are
   R8-generated. Resolve them by prefix scan and treat a miss as "feature unavailable" rather
   than crashing.
4. **Drawer height** is latched by a one-shot layout listener; verify a 16-slot drawer actually
   scrolls on device, otherwise cap the popup height ourselves.
5. **Multi-user** keys are `pkg:userId`; the recent list must keep the suffix when resolving
   through `mLauncherAppInfoList`.
6. **New-build compatibility**: store the order in the new build's format
   (`top_1..top_16` + `sidebar_fixed_apps_customed`) as an *optional mirror* so a future
   upgrade keeps the user's order. Only enable it together with §4.3, or the old host will
   overwrite `top_1..top_5`.
7. **Version drift**: both builds report `versionCode 18`; rely on class presence, not the
   version code.

## 6. Verification

1. `.\gradlew.bat assembleDebug`.
2. `.\gradlew.bat testDebugUnitTest --tests "com.qimian233.ztool.SearchIndexConsistencyTest"`.
3. `python tools/check_unused_strings.py` — both locales in sync.
4. On device (old build, module enabled + scope configured + sidebar restarted):
   * drawer shows more than 5 apps and scrolls;
   * reordering via the edit menu survives a sidebar restart and a `cur_visible_freeform_list`
     change;
   * the first 2 slots follow the most recent floating apps;
   * the all-apps panel filters while typing, and clicking a filtered result launches the
     correct app (index mapping);
   * tapping a drawer entry still launches into freeform, and the "more" footer still opens the
     all-apps panel.
5. Regression: install the new build (1.18.5.0.26.38.2) and confirm the module no-ops (all
   lookups miss) instead of throwing.

## 7. Open questions

* Editing UX: settled — Stage B (`编辑` mode with `▲`/`▼`/`✕`).
* Search placement: settled — in-panel `EditText`; the §5.1 blocker is resolved (§9).
* Still open: whether to mirror the order into the new build's `top_1..top_16` format (§5.6).

## 8. Confirming the all-apps ordering symptom — resolved as H1

Both builds sort the all-apps panel with the same comparator
(`pinyin(userId + appLabel).lowercase()`), so the code predicted no ordering difference.
Confirmed with the user: the observed issue is **H1 — the order is the intended pinyin order, but
there is no A–Z rail or grouping, so the panel reads as unordered**. Work item: §4.5.

The other hypotheses were ruled out and are recorded here only in case a second symptom shows up
later: H2 drawer-vs-panel confusion, H3 multi-profile clumping, H4 Latin/CJK mixing, H5 unwanted
entries, H6 column count (old 5 vs new 6), H7 greyed already-floating entries not moving to the
top.

## 9. IME prototype — result

Probe: `hook/modules/freeformbar/SidebarSearchProbe.kt` (registered as `hook_test`, armed only by
`Settings.Global.ztool_ime_probe = 1`; **delete it when the real feature lands**). Tested on
TB710FU (ZUXOS, sidebar 1.18.02.26.20.2 in `/system/priv-app`, KernelSU + LSPosed, scope already
enabled for `com.zui.freeform.sidebar`).

Procedure: `am startservice com.zui.freeform.sidebar/.FloatService`, then the probe injects an
`EditText` into `mAllAppLayout` and drives two phases.

| Phase | Overlay flags | Result |
|---|---|---|
| A — untouched | `0x1000128` (`FLAG_NOT_FOCUSABLE` set) | `showSoftInput=true focus=true windowFocus=true imm.isActive=true isAcceptingText=true`; `adb shell input text aaa` reached the box (`TEXT_CHANGED 'a'/'aa'/'aaa'`); `dumpsys input_method` → `mInputShown=true`, `mServedView=android.widget.EditText{…}` |
| B — flag cleared | `0x1000120` | identical behaviour; clearing the flag changed nothing |

Second run, realistic path (no explicit `showSoftInput`, just `input tap` on the box):

```
READY_FOR_TAP screen=[2027,151] size=1173x118 center=[2613,210] focus=false immActive=true accepting=false
AFTER_TAP     text='' focus=true windowFocus=true immActive=true accepting=true visibleFrame=[98,1033] rootH=935
```

`visibleFrame.bottom` fell from 1999 to 1033 (~966 px of keyboard) and the screenshot shows the
Sogou keyboard displayed over the panel with the probe box focused.

**Conclusions**

1. The panel `PopupWindow` takes input focus independently of the parent overlay's
   `FLAG_NOT_FOCUSABLE`; the IME attaches and is usable. No flag manipulation is required.
2. The keyboard **overlays** the panel instead of resizing it (the panel keeps its height), so
   §4.6 must either keep the search box at the top of `allapp_rl` and let the grid scroll, or set
   the popup's `softInputMode` to `ADJUST_RESIZE`.
3. Key injection (`input text`) reaches the box, so the filter path can be exercised from adb
   during verification.

## 10. Implementation status

### Done and verified on-device (TB710FU, host 1.18.02.26.20.2)

Requirements 1 (capacity) and 2 (user-editable ordering) are implemented:

* `hook/modules/freeformbar/SidebarDrawerHook.kt` — hooks `SidebarAppUtils.generateTop5App`
  (drawer data), the R8 drawer-open lambda, `FloatService.onCreate` and
  `FloatService.PackageReceiver.onReceive` (adapter + click listener installation).
* `hook/modules/freeformbar/SidebarDrawerAdapter.kt` — hook-owned `BaseAdapter`: app rows plus a
  trailing 编辑 / 完成 row, and ▲ / ▼ / ✕ per row while editing.
* `hook/modules/freeformbar/SidebarDrawerStore.kt` — order / recency / customized flag in the
  host's `freeform_preferences` under private `ztool_*` keys.
* `hook/modules/freeformbar/SidebarReflect.kt` — cached reflection onto `utils.AppInfo`.
* `ScopeKeys.FREEFORM_SIDEBAR` + `scope.list` entry; `PreferenceKeys.SIDEBAR_DRAWER_*`.

Observed on device: drawer shows the configured capacity (8) instead of 5; the 编辑 row enters
an edit mode where every row carries ▲ ▼ ✕; ▲ swaps two entries and ✕ removes one, both
persisted into `ztool_drawer_order` (a full permutation, `;`-joined `pkg:userId`);
`ztool_drawer_customized` flips to true; launching an entry records `ztool_drawer_recent`.

### Host facts discovered while implementing (not in §1)

| Fact | Consequence |
|---|---|
| The host re-attaches **its own** `AppIconListAdapter` inside the drawer-open lambda, after our `onCreate` install | The after-hook must **re-attach** our adapter, not merely push data into it |
| The drawer-open method is the private R8 lambda `lambda$new$1$com-zui-freeform-sidebar-FloatService` | Resolve by prefix scan **and** `setAccessible(true)`; `findMethod` handles the accessible flag, a raw `declaredMethods` scan does not |
| ListView positions with the host's "more" footer present | apps `0 until n`, our 编辑 row at `n`, host footer at `n + 1` |
| Drawer width is only `top_list_window_width` = **70dp** landscape / 94dp sw400dp, while the icon is 48–72dp | Three per-row glyphs cannot fit; the hook widens `mDrawerLayout` by `3 × 26dp` while editing and restores it afterwards |
| LSPosed serves `getRemotePreferences` from its own `modules_config.db` → `module_configs`, not from the module's `shared_prefs` file | Writing the app's `xposed_module_config.xml` does **not** enable a hook; test enablement has to go through the app's own service path (or the DB) |
| The test device already has `('com.qimian233.ztool', 'com.zui.freeform.sidebar')` in LSPosed's `scope` table | No scope work needed on that device; `scope.list` still had to gain the entry for other users |

### Fixes after the first on-device round

Reported: tapping the host's "..." (more) footer opened an app instead of the all-apps panel.

Root causes found and fixed:

1. **The panel was opened the wrong way.** The host's own "more" path is
   `setAnimationStyle(0)` → `update()` → `dismiss()` → `post(showAllAppWindow())`, because
   `showAllAppWindow()` starts with `showPopupWindow()`, which early-returns while the popup is
   still showing — so the popup was never rebuilt for the grid content. `openAllAppsPanel()` now
   mirrors the host's sequence exactly.
2. **Footer detection by position was fragile.** The footer is now identified by view identity
   (`view === mFootView`) instead of by `adapter.count`, so it survives any change in how many
   footer views the host attaches. Positions are only used for the action row and app rows.
3. **完成 was unreachable in edit mode.** Edit mode lists the full app permutation, so a trailing
   action row sat dozens of rows down. The action row now renders at the **top** while editing
   (`SidebarDrawerAdapter.actionPosition()`) and the list scrolls to it after the toggle; in
   normal mode it stays at the bottom as before.

Verified after the fixes: footer → all-apps panel with no app launched; action row toggles
编辑/完成 in both directions with the row following the mode; an app row still launches its app.

### Reverted: leaving `mAppTop5List` alone

An attempt to stop hooking `SidebarAppUtils.generateTop5App` and stop writing `mAppTop5List` was
reverted on user feedback. The drawer adapter is built from that list, so it has to be replaced
for the extra rows to appear at all. Note the coupling that remains: while our `generateTop5App`
hook is active, `mAppTop5List` is longer than the host's own 5-row adapter, so the host's stock
click listener would treat the "more" row as an app row. Our adapter and our listener must
therefore be the ones attached — `installDrawer()` re-asserts both, and logs
`drawer ListView not found` / `took over the drawer adapter` so an on-device failure is visible.

### Capacity range

The drawer's ListView is 1850 px tall with 180 px rows, so exactly **10 rows** are visible.
The list is `capacity + 1` (our action row) + 1 (the host's "more" row), which caps the usable
capacity at **8**. The slider range is therefore 6–8: anything larger pushes 编辑 and the "more"
row below the fold, inside a hover-driven popup that dismisses itself — the opposite of the
"one tap away" requirement. Both the frontend and the hook clamp to this range.

### Not started

* §4.5 A–Z rail / grouping (requirement 3).
* §4.6 search box (requirement 4) — unblocked by §9, not yet written.
* Frontend settings page (feature card, switch, two sliders, strings, search index) — in
  progress; the hook is already driven by `PreferenceKeys.SIDEBAR_DRAWER_ENHANCE`,
  `SIDEBAR_DRAWER_SLOTS` and `SIDEBAR_DRAWER_RECENT_SLOTS`.

### Note on testing

Because LSPosed serves remote preferences from its own DB, the on-device hook testing used a
temporary `isEnabled()` override plus a `Settings.Global` auto-open trigger. **Both were removed
before the commit**; the shipped module only runs when `sidebar_drawer_enhance` is set through
the app.
