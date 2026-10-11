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
3. **All-apps panel ordering** — see the finding in §3: this is *already* pinyin-sorted in the
   old build; only an optional improvement is planned.
4. **Search in the all-apps panel.**

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
| All-apps sort | **identical to the old build** — `J0.C0258g.r()` with `J0.Y.b(userId + appName)`, and `J0.Y` is a byte-for-byte equivalent of `PinYinUtils` |
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
| 3 | All-apps "reasonable" order | **already** pinyin-sorted by `userId+label`, same comparator as the new build | same | **Already satisfied.** Optional polish only (dead A–Z index bar, `Collator`) |
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

### 4.4 Edit UI — staged

**Stage A (default, low risk): long-press action menus.**

* Drawer row long-press → action sheet: `上移` / `下移` / `置顶` / `从侧边栏移除`.
* All-apps item long-press → `添加到侧边栏` / `从侧边栏移除`.
* Both mutate `ztool_drawer_order`, then `notifyDataSetChanged()` + rewrite `mAppTop5List`.
* UI container: build a `PopupWindow` the same way the host does (a `Service` cannot show an
  `AlertDialog` without a window token; if a dialog is preferred, set
  `dialog.window.setType(2038)` before `show()`).

**Stage B (optional, closer to the new build): footer `编辑` entry.**

* Append an `编辑` row to our adapter (or repurpose the footer): in edit mode every row gets
  `▲`/`▼`/`✕` affordances, and the all-apps panel marks apps already in the drawer (item
  type analogue of the new build's types 4/5) with a `＋` to add them.
* True drag-and-drop would require replacing `ListView@id/appicon_lv` with a `RecyclerView`
  plus `ItemTouchHelper` (`makeMovementFlags(3, 0)` + `Collections.swap`, exactly as
  `I0.f$a`). Feasible but invasive; only do it if the ▲/▼ version is judged insufficient.

### 4.5 All-apps ordering (optional polish)

The old build already sorts by pinyin. If a visible defect is confirmed, the cheap fixes are:

* Hook `SidebarAppUtils.covertMapToList` (or `generateLauncherApp`) and re-sort with
  `java.text.Collator.getInstance(Locale.CHINA)` on the label only (drops the `userId` prefix
  that currently groups by profile).
* Wire the dead A–Z index bar: assign `mIndexBar` / `appsAlphabeticIndexBar`, make
  `index_bar` visible, and feed `AppsAlphabeticIndexBar` pinyin initials instead of the raw
  `appName.charAt(0)` (today the letters are Chinese characters while the order is pinyin).

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
| `sidebar_drawer_slots` | int | 8 | drawer capacity (6–16) |
| `sidebar_drawer_recent_slots` | int | 2 | how many leading slots are recency-driven (0–3) |
| `sidebar_drawer_edit_menu` | bool | true | long-press editing (Stage A) |
| `sidebar_all_apps_search` | bool | true | inject the search box |
| `sidebar_all_apps_collator_sort` | bool | false | §4.5 optional re-sort |
| `sidebar_all_apps_index_bar` | bool | false | §4.5 optional A–Z bar |

Plus a `重置抽屉顺序` action. Because the order lives in the host's private prefs, the reset is
either an in-sidebar menu entry or a root/shell write from the module app.

## 5. Risks and mitigations

1. **IME focus on the search box (the only real blocker).** The overlay window is created with
   `FLAG_NOT_FOCUSABLE`, and the panel is a `PopupWindow` sub-window of it, so an `EditText`
   there will normally not receive input focus / show the IME. Mitigation: while the search box
   is focused, clear `FLAG_NOT_FOCUSABLE` on the overlay's `mLayoutParams` and
   `windowManager.updateViewLayout(...)`, restore the original flags on dismiss
   (`popupWindow.setOnDismissListener`). Fallback if that misbehaves: launch a ZTool-side search
   Activity and push the selection back through a broadcast. **Prototype this first** — it
   determines whether requirement 4 is delivered in-panel or out-of-panel.
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

* **§3 row 3**: the all-apps panel is already pinyin-sorted by the same comparator in both
  builds, so requirement 3 appears satisfied today. If a concrete mis-ordering was observed on
  the device, it needs the exact symptom (which apps, which order) before §4.5 is implemented.
* Editing UX: is the Stage-A long-press menu acceptable, or is drag-and-drop (Stage B) required?
* Search placement: in-panel (needs the §5.1 IME workaround) or a ZTool-side search screen?
