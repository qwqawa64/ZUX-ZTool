# ZUI work-mode desktop (`com.zui.desktoplauncher`): hook survey

Preliminary reverse-engineering notes for the "portrait PC mode" feature. Companion to
`zui_work_mode_portrait.md`, which covers the **system-server** half (already shipped as
`WorkModePortrait`); this document covers the **launcher-side** half, which is still open.

Everything below was read from `com.zui.desktoplauncher` 18.0.0.0165
(`ZuiLauncherPC.apk`, `/system/priv-app/ZuiLauncherPC/`) on TB710FU, with JADX plus the APK's
own resource table.

## 0. How to reproduce

```powershell
adb pull /system/priv-app/ZuiLauncherPC/ZuiLauncherPC.apk
aapt2 dump resources ZuiLauncherPC.apk > resources.txt        # name -> obfuscated res/<x>.xml
aapt2 dump xmltree --file res/5k.xml ZuiLauncherPC.apk        # the device_profiles variant in use
aapt2 dump xmltree --file res/5T.xml ZuiLauncherPC.apk        # work default workspace layout
```

Resource **names** survive (`xml/device_profiles`, `dimen/pc_allapps_cellwidth`, …); only file
paths are renamed. Qualifier resolution matters: TB710FU is `sw800dp` / 400 dpi, so
`res/xml/device_profiles` resolves to its **`sw650dp`** variant `res/5k.xml`, not the default
`res/9B.xml`. Resource *values* are not reachable through the JADX MCP resource API; use
`aapt2` against the pulled APK.

## 1. Target identity

| Property | Value |
|---|---|
| Package | `com.zui.desktoplauncher` |
| Version | 18.0.0.0165 (`versionCode` 18000165) |
| APK | `/system/priv-app/ZuiLauncherPC/ZuiLauncherPC.apk` |
| Size | 3632 classes in 592 packages under `com.zui.desktoplauncher.*` (17 290 classes in the APK) |
| Activities | `drawer.NormalLauncher` (MAIN + `SHOW_WORK_APPS`), `workmode.WorkLauncher` (`SECONDARY_HOME`), `workmode.pcallapps.PcAllAppsActivity` (`sensorLandscape`, `singleInstance`) |
| Workspace DB | `launcher_work.db` (`LauncherFiles.getLauncherDbName` returns it unconditionally here) |

**It is a separate build from `com.zui.launcher`, not the same codebase renamed** (consistent
with `zui_component_provenance_and_hookability.md` §2.3), and its
`Utilities.isWorkMode()` / `isWorkMode(Context)` are stubbed to `return true`. Every code path
in the APK is therefore the work/PC desktop; there is no phone-launcher behaviour in it. The
normal home on this device is still `com.zui.launcher`.

ZTool has **no scope and no hook module** for it: `ScopeKeys.LAUNCHER` is `com.zui.launcher`,
and `hook/modules/launcher/` targets that package only.

## 2. Why the desktop stays landscape-shaped

### 2.1 The work grid is hardcoded to one landscape profile

```
Utilities.getCurrentGridName(ctx)
  -> SettingsValue.WORK_CONFIG_KEY              // the String "work"
InvariantDeviceProfile.s(Context, String)       // private, obfuscated name
  -> r(ctx, "work", deviceType, …)              // parse <grid-option name="work">
  -> u(info, candidates, deviceType)            // nearest minWidthDps/minHeightDps
  -> t(ctx, info, option, deviceType, "work")   // commit numColumns/numRows/…
```

Even the public `InvariantDeviceProfile.setCurrentGrid(ctx, name)` ignores `name` on this build:
it skips persisting the choice (`if (!isWorkMode(…) …) setCurrentLayoutConfigKey`) and then
re-resolves `"work"`. `res/xml/device_profiles` (sw650dp variant) declares six grid options; the
work one is:

| field | value |
|---|---|
| `name` | `work` |
| `numColumns` x `numRows` | **13 x 7** |
| `deviceCategory` | `2` (tablet only) |
| `numAllAppsColumns` | 6 |
| `numFolderColumns` x `numFolderRows` | 4 x 4 |
| `numHotseatIcons` / `hotseatColumnSpanLandscape` | 6 / 6 |
| `isScalable` | `false` |
| its only `<display-option>` | `minWidthDps` 600, `minHeightDps` 800, `borderSpace` 16/64, `minCellSize` 102x120, `iconImageSize` 60 |

Every per-orientation value (`borderSpaceLandscape*`, `horizontalMarginLandscape`,
`allAppsCellWidthLandscape`, …) lives **inside that one option**, and `DeviceProfile` selects
the slot with

```java
f8178c = isTwoPanels ? (isLandscape ? 3 : 2) : (isLandscape ? 1 : 0);
```

so portrait and landscape share the same *grid shape* (13x7) and differ only in dimensions. No
portrait grid option exists in any qualifier variant, and the code cannot select one.

Portrait arithmetic: 2000 px / 2.5 = 800 dp wide, minus the 54 dp `horizontalMargin` pair leaves
~692 dp for 13 columns ≈ **53 dp per column against a 60 dp icon** — unusable as-is.

> The XML is not the whole story: the live work profile on TB710FU reports **12x6**, because the
> launcher restores a persisted grid (`migration_src_workspace_size=12,6`, `launcher_work.db`).
> A hook must read the live `InvariantDeviceProfile`, not this table — see §10.

### 2.2 Tablets never transpose the grid

```java
// DeviceProfile.Builder.build()
transposeLayoutWithOrientation = !info.isTablet(windowBounds);
```

The PC desktop window is tablet-class, so `transposeLayoutWithOrientation == false` and
`isVerticalBarLayout()` (`isLandscape && transposeLayoutWithOrientation`) is always false. The
phone launcher's "rotate the grid 90°" mechanism is unavailable here.

### 2.3 The shipped work layout is a single left column

The work `defaultLayoutId` (`res/5T.xml`) puts every default item in column 0:

```
<resolve container=-100 screen=0 x=0 y=0>   two browser favourites
<favorite com.zui.calendar x=0 y=1>
<favorite com.zui.notes    x=0 y=2>
```

"Desktop icons piled in the left column" is the **shipped design**, not a rotation artefact. A
portrait arrangement has to come from reflowing (or re-defining) the workspace, not from fixing
a measurement.

### 2.4 The all-apps drawer is orientation-invariant by construction

`workmode.pcallapps.PcAllAppsPagedView` never uses `GridOption`/`InvariantDeviceProfile`; it
derives its grid inline:

```java
this.G = res.getDimensionPixelSize(R.dimen.pc_allapps_cellwidth);    // sw720dp: 106 dp
this.H = res.getDimensionPixelSize(R.dimen.pc_allapps_cellheight);   // sw720dp: 125 dp
int pad  = res.getDimensionPixelSize(R.dimen.pc_allapps_h_padding);  // sw720dp: 143 dp
int cols = (Math.max(dp.widthPx, dp.heightPx) - pad * 2) / this.G;
int rows = (int) Math.floor((Math.min(dp.widthPx, dp.heightPx)
            - search_margin_top - search_height
            - indicator_padding_top - indicator_padding_bottom
            - dp2px(48)) / this.H);
this.cellCountX = cols; this.cellCountY = rows; this.itemsPerPage = cols * rows;
```

Both edges go through `Math.max`/`Math.min`, so **portrait yields the same counts as
landscape**. On TB710FU (2000x3200): cols = (3200 − 2·357) / 265 = **9**, rows =
(2000 − (230+90+75+112) − 120) / 312 = **4**. Nine 265 px columns need 2385 px against a
2000 px-wide portrait page → horizontal overflow, exactly the reported symptom. Pages are
`CellLayout`s inflated from `R.layout.folder_page`.

Independently, `PcAllAppsActivity` is declared `sensorLandscape` with no `configChanges`, so
opening the drawer re-orients the display (handled by the system-side hook only once the
activity stops requesting landscape).

### 2.5 The bottom bar: taskbar and hotseat are both live

Two components can own the bottom bar, and both are present in this APK:

| Candidate | Evidence for | Evidence against |
|---|---|---|
| `taskbar.*` (266 classes, `TaskbarView` + `TaskbarActivityContext`) | `uioverrides.QuickstepLauncher` holds a `TaskbarManager` (`openTbAllApps`, `dispatchDeviceProfileChanged`, `isTouchWithInEdgeBackGesture`); `TaskbarModelCallbacks.bindRecentUsedApps` + `DesktopTaskbarRecentAppsController` add running apps; `TaskbarView` lays out `numShownHotseatIcons` pinned icons in one row | `DeviceProfile.<init>` sets `isTaskbarPresent = false` whenever `Utilities.isWorkMode()` |
| `uiextend.ZuiHotseat` | owns `HotseatRecentUsedContainer`, `getRecentUsedCount`, `getAllAppsEntryCount`, `setRecentUsedShow`, `startActivityByIndex` | the launcher hotseat is the classic dock of `NormalLauncher`; nothing marks it work-mode-specific |

`DeviceProfile.isTaskbarPresent` is consumed by `TaskbarManager.E()`
(`FLAG_HIDE_NAVBAR_WINDOW || isTaskbarPresent`), by `getHotseatLayoutPadding`, by
`getOverviewActionsClaimedSpaceBelow` and by `getAbsoluteOpenFolderBounds` — i.e. it decides how
much bottom space the **workspace** reserves, not necessarily whether the taskbar window exists.

This must be settled on device before either hook is written (see §7 Q1). Either way the
geometry story is the same: one horizontal row, sized from `DeviceProfile`
(`taskbarHeight`/`hotseatBarSizePx`), with `numShownHotseatIcons` slots.

First runtime check (§10, PC mode off): `TaskbarManager.recreateTaskbar()` reports `context=null`
and `ZuiHotseat` is attached but `GONE` — no bottom bar exists outside PC mode, so the PC-mode run
is still the deciding one.

Taskbar geometry, already located:

```java
// TaskbarActivityContext.createDefaultWindowLayoutParams(int, String)
boolean z2 = TaskbarManager.isPhoneButtonNavMode(this) && getDeviceProfile().isLandscape;
new WindowManager.LayoutParams(z2 ? height : -1, z2 ? -1 : height, …);
layoutParams.gravity = z2 ? GravityCompat.END : 80 /* BOTTOM */;

// TaskbarView.reLoadResource(float): touch size = dp.taskbarIconSize,
//   side margin = taskbar_icon_margin_left_right, divider = taskbar_icon_divider_line_*,
//   edge padding = taskbarview_padding_left_right
// TaskbarView.f(int): if the row is wider than the display, scale all icons down
//   (updateIconSizeByScale) instead of wrapping
```

`TaskbarView.onLayout` is a single right-to-left horizontal walk; its `Configuration.orientation`
test calls the same method in both arms — a portrait branch was stubbed out upstream.

## 3. Business-logic map

| Area | Class (package) | Notes |
|---|---|---|
| Desktop entry | `workmode.WorkLauncher` extends `uioverrides.QuickstepLauncher` | adds only the `zui_show_all_apps` observer → `PcAllAppsManager.toggle()`, locale receiver, `finishBindingAllApps` bookkeeping |
| All-apps drawer | `workmode.pcallapps.PcAllAppsActivity`, `PcAllAppsManager`, `PcAllAppsPagedView`, `PcAllAppsContainerView`, `PcAllAppsSearchContainerLayout` | fullscreen `sensorLandscape` activity, `singleInstance`; `PcAllAppsManager` is a PC-mode-gated singleton (`zui_pc_mode`) |
| Grid selection | `InvariantDeviceProfile` (`s`/`r`/`u`/`t` private) | reads `device_profiles.xml`; owns `numColumns`, `numRows`, `numFolder*`, `hotseatColumnSpan[]`, `iconSize[]`, `startAlignTaskbar[]`, `transientTaskbarIconSize[]` |
| Geometry | `DeviceProfile` (+ `Builder`) | one instance per supported window bounds; owns `taskbarHeight` (final), `workspacePadding`, `cellLayoutPaddingPx`, `isLandscape`, `isTablet`, `transposeLayoutWithOrientation`, `numShownHotseatIcons` |
| Bounds / rotation input | `util.DisplayController`, `util.WindowBounds` | `Info.supportedBounds`, `Info.isTablet(bounds)`, `rotation`, `rotationHint` |
| Bottom bar | `taskbar.*`, `uiextend.ZuiHotseat`, `hybridhotseat.*` | see §2.5 |
| Workspace / cells | `Workspace`, `CellLayout`, `PagedView`, `views/*` | same measurement model as `zui_launcher_wide_grid_geometry.md` describes for `com.zui.launcher` |
| Settings surface | `util.SettingsValue`, `Utilities`, `LauncherFiles` | `WORK_CONFIG_KEY`, `CURRENT_LAYOUT_CONFIG_KEY`, `PERMANENT_PREFERENCE`, `launcher_work.db` |

## 4. Obfuscation assessment

**Moderate R8: package and class names fully preserved, members shortened.**

| Layer | State | Evidence |
|---|---|---|
| Package names | preserved, readable tree | 592 packages, all `com.zui.desktoplauncher.<feature>` |
| Class names | preserved | `InvariantDeviceProfile`, `TaskbarActivityContext`, `PcAllAppsPagedView`, … |
| Public API / overrides / fields | preserved | `InvariantDeviceProfile.numColumns`, `DeviceProfile.taskbarHeight`, `TaskbarActivityContext.createDefaultWindowLayoutParams` |
| Private methods | shortened `a…z`, `A…Z`, `a0`, `b0`, … | `InvariantDeviceProfile.s/t/u/v/w`, `DeviceProfile.a…x`, `TaskbarView.n` |
| Private fields | `f<id><letter>` (`f8331b`, `f8191c`, `f15355x`) or `a`/`b`/`c` | `DeviceProfile`, `PcAllAppsPagedView` |
| Anonymous/inner classes | numbered (`a`, `b`, `C0001a`) | `WorkLauncher.a`, `InvariantDeviceProfile.a` |
| Resource names | preserved | `xml/device_profiles`, `dimen/taskbar_size`, `integer/all_apps_column_num` |
| Resource file paths | renamed | `res/5k.xml`, `res/5T.xml` |
| Fully obfuscated helpers | few | packages `p` (24), `w` (16), `a` (10), `i` (8) |

Survivors are per-build luck (`TaskbarBackgroundRenderer` and `TaskbarInsetsController` are
Kotlin, so `kotlin.Metadata` `d2` keeps their original names; some Java privates survive because
XML or lambdas reference them). Nothing about it is a contract.

Hooking consequences:

- Class lookups are safe; component/authority/`call`-string/AIDL contracts are
  obfuscation-invariant.
- **In-process method and field names are not stable across versions.** Resolve them by
  declaring class + descriptor through the project's offline DexKit index (pattern:
  `dexindex/indexer/LauncherDexIndexer.kt`). A new `DesktopLauncherDexIndexer` and scope entry
  are prerequisites for any non-obvious hook.
- Never hardcode `f<id><letter>` field names; the numeric id shifts every build.

## 5. Stable call points

| Surface | Access | Verdict |
|---|---|---|
| `content://com.zui.desktoplauncher.grid_control` (`GridCustomizationsProvider`) | `android:exported="true"`, **no permission** | **Stable public API.** `query` `/list_options` → `name, rows, cols, preview_count, is_default, canBeShown`; `query` `/icon_themed`, `/get_icon_themed`; `update` `/default_grid` (`name`), `/set_icon_themed` (`boolean_value`). `call("get_preview")` needs `BIND_WALLPAPER`. Discoverable via the `com.android.launcher3.grid.control` activity meta-data. |
| ↳ work-mode caveat | `update` `/default_grid` executes but is ignored for layout: `setCurrentGrid` re-resolves `"work"` | Selection knob only for a non-work build |
| `Settings.System` `zui_pc_mode` (0/1) | `Utilities.isPcMode()` / `isWorkModeSetting()` read it; write needs `WRITE_SETTINGS` | **Stable, public.** The PC-mode flag the launcher acts on |
| `Settings.System` `zui_dp_display_pc_mode` | `dpmode.DpModeManager`, `Utilities.isInDpMode()` | Separate display-mode flag (DP = external display desktop) |
| `Settings.System` `zui_show_all_apps` | observed by `WorkLauncher` → `PcAllAppsManager.toggle()` | **Usable trigger** for the drawer |
| `Settings.System` `all_apps_show_state` (0/1) | mirror written by `PcAllAppsManager` | readable state |
| `Settings.System` `extra_new_layout_config` | mirror of `current_layout_config_key`, only written when `!isWorkMode` | **Not the work grid**; writing it changes nothing |
| launcher prefs `taskbar_pinning`, `allow_rotation` | `LauncherPrefs`, `states.RotationHelper` | in-process prefs, not externally writable |
| `com.zui.desktoplauncher.settings` (`LauncherProvider`) | `READ_SETTINGS`/`WRITE_SETTINGS` = `signatureOrSystem`; `call()` additionally rejects non-own-UID callers | **Not usable** |
| `.settings.export` (`ZuiLauncherExportProvider`), `.settings.permanent` (`PermanentSettingsProvider`) | same `signatureOrSystem` perms | **Not usable** (clean `getAllValues/getValue/setValue` contract, locked door) |
| `.settings.global_search` (`GlobalSearchProvider`) | `exported="false"` + perms | Not usable |
| `.TestInfo` | `WRITE_SECURE_SETTINGS`, `android:enabled="false"`, harness gate | Disabled |
| `workmode.search.WorkSearchService` / `IWorkSearch` | `com.zui.desktoplauncher.permission.WORK_SEARCH` (`signatureOrSystem`) | Not usable; AIDL is `showWin/closeWin/isShow/switchSearch/exitWorkMode` |
| `backup.BackupController` | `com.zui.launcher.permission.SEND_MESSAGE` | Not usable |
| `com.zui.launchersdk` (`ZLauncherClient`) | separate APK's overlay service, `IZLauncherOverlay` AIDL | Feed overlay only — **irrelevant** to layout |
| `device_profiles.xml` | read-only APK asset | The only definition of every grid; not writable without repacking |

## 6. Candidate hook points

### 6.1 Work desktop grid (workspace columns/rows)

`InvariantDeviceProfile` keeps `numColumns`, `numRows`, `numFolderColumns`, `numFolderRows`,
`numAllAppsColumns` as **public mutable ints**, committed by the private `t(...)`. A post-hook on
`t(...)` can rewrite them for the portrait case (13x7 → e.g. 7x13) without touching the selected
`GridOption` (whose fields are `final`).

- Target: private `InvariantDeviceProfile.t(Context, DisplayController$Info, a, int, String) →
  void`, resolved by descriptor via DexKit; fall back to the constructor.
- **But the change is persisted and migrates the workspace.** `GridSizeMigrationTask.migrateGridIfNeeded(ctx, idp)`
  compares the live grid with `KEY_WORKSPACE_SIZE` / `KEY_HOTSEAT_COUNT` in the launcher prefs:

  ```java
  String pointString = Utilities.getPointString(idp.numColumns, idp.numRows);
  if (pointString.equals(prefs.getString(DeviceGridState.KEY_WORKSPACE_SIZE, ""))) { … return true; }
  new MultiStepMigrationTask(validPackages, ctx, db).migrate(oldPoint, new Point(idp.numColumns, idp.numRows));
  prefs.edit().putString(KEY_WORKSPACE_SIZE, pointString)...   // the new grid is written back
  ```

  So the first flip runs Launcher3's grid-resize migration (re-flowing items, aborting the
  transaction if everything would be removed), and every later flip re-runs it because the
  persisted value keeps changing. Flipping 13x7 ↔ 7x13 per rotation is therefore **not safe**.
- Safe variants:
  1. **Migrate once, stay portrait.** Change the grid unconditionally in work mode (not per
     orientation) and let the migration run a single time; landscape PC mode then uses the
     portrait-shaped grid too.
  2. **Leave the DB grid alone.** Hook only the layout consumers of the counts and re-map cell
     coordinates inside `CellLayout#onMeasure`/`onLayout` (the seam `zui_launcher_wide_grid_geometry.md`
     documents for `LauncherWideGridHook`). The persisted grid never changes, so migration never
     runs — at the cost of a custom layout pass over every item.
  3. **Silence the comparison** by keeping `KEY_WORKSPACE_SIZE` in sync with the hooked grid
     (write-through hook). Cheapest but it disables real grid-change migration as a side effect.

### 6.2 Workspace icon arrangement

The single-column layout is data (§2.3), not geometry. Options: accept the left column in
portrait (cheapest), or re-map `ItemInfo.cellX/cellY` in the loaded model
(`model.ModelDbController` / the loader task) — a data mutation with its own risk.

### 6.3 PC all-apps drawer

1. `PcAllAppsPagedView.setupContentDimensions(int)` — public void, runs on every bind; post-hook
   recompute the portrait `cols/rows/itemsPerPage` and write all three private fields
   (`itemsPerPage` drives page splitting). Most surgical.
2. `PcAllAppsPagedView.calculateGridSize(...)` — public static, array out-param; rewrite
   `[cols, rows]`. Page geometry only → pair with (1).
3. `PcAllAppsPagedView.bindItems(...)` — public; use as the rebind trigger.
4. Window half (mandatory): `Activity.setRequestedOrientation` (framework) or
   `PcAllAppsActivity.getRequestedOrientation` → `SCREEN_ORIENTATION_UNSPECIFIED`.

`PcAllAppsActivity.getDeviceProfile()` is not useful (the `max()/min()` math ignores the swap),
and `PcAllAppsContainerView.onDeviceProfileChanged` is an empty override.

### 6.4 Bottom bar

If it is the taskbar (`taskbar.*`), ranked by stability:

1. `TaskbarActivityContext.createDefaultWindowLayoutParams(int, String)` — public, 3 callers;
   rewrite the returned `LayoutParams` (gravity/width/height) to change the bar's bounds.
2. `DeviceProfile.Builder.build(boolean)` — public seam into the constructor that finalises
   `taskbarHeight` (final field), `taskbarIconSize`, `numShownHotseatIcons`, `startAlignTaskbar`,
   `isLandscape`. One hook covers every consumer.
3. `TaskbarManager.recreateTaskbar()` — public (`@VisibleForTesting`); trigger a re-layout after
   changing dims.
4. `TaskbarViewController.setTbHeight(float)` (protected, one caller) and
   `TaskbarActivityContext.setTaskbarWindowHeight(int)` — cheap height overrides.
5. `TaskbarView.onLayout` / private `n(boolean,int,int,int,int)` — the real icon walk; needed
   only for a different arrangement (multi-row, top-anchored). Obfuscated → DexKit.

If it is the hotseat (`ZuiHotseat`), the seam is `resetCellSize(DeviceProfile)` (public) plus
`onMeasure`/`onLayout`, with `DeviceProfile.numShownHotseatIcons` as the slot count.

## 7. Open questions (must be resolved before implementation)

1. **Which component draws the PC bottom bar?** Code is contradictory (§2.5). Check on device in
   PC mode: `dumpsys window windows | grep -i taskbar` and `dumpsys activity top`. A probe hook
   logging `TaskbarManager.getCurrentActivityContext()` is the code-level answer. With PC mode off
   it is already `null` and the hotseat is `GONE` (§10); only a real PC-mode run can decide.
2. ~~Grid change vs workspace DB.~~ Resolved: `GridSizeMigrationTask.migrateGridIfNeeded` compares
   the live grid with `KEY_WORKSPACE_SIZE`/`KEY_HOTSEAT_COUNT` and re-runs
   `MultiStepMigrationTask.migrate(old, new)` whenever they differ, then persists the new grid
   (§6.1). A per-orientation grid flip is therefore destructive; pick variant 1, 2 or 3 there.
3. **Exact bounds.** First run suggests the work desktop is measured against `3200x2000` landscape
   bounds while the display is portrait `800x1280dp` (§10) — i.e. the landscape window is the whole
   problem. Confirm with the real PC-mode toggle and watch for a portrait `DeviceProfile` line.
4. **Launcher-side orientation requests.** Only `PcAllAppsActivity` declares `sensorLandscape`;
   `WorkLauncher`/`NormalLauncher` are `unspecified`. Confirm nothing else re-locks rotation.
5. **Where `migrateGridIfNeeded` is called from** (`ModelDbController`) and whether it runs before
   or after the first workspace bind — it decides whether a one-time migration is visible as a
   flicker or is silent.
6. **Why the live grid is 12x6 while the XML says 13x7.** The launcher restores a persisted grid
   (`migration_src_workspace_size=12,6`); the code path that feeds it back into
   `InvariantDeviceProfile` has not been located yet.

## 8. Suggested next steps

1. ~~Add `ScopeKeys.DESKTOP_LAUNCHER` …~~ Done: `ScopeKeys.DESKTOP_LAUNCHER` (`AmStop`) and
   `com.zui.desktoplauncher` in `scope.list`.
2. ~~Ship a probe-only hook~~ Done: `hook/modules/desktoplauncher/WorkDesktopProbe.kt` (§9).
3. Then implement independently: §6.3 (all-apps) and §6.4 (bottom bar) are self-contained; §6.1
   waits for Q2.

## 9. The probe hook (`WorkDesktopProbe`)

`com.qimian233.ztool.hook.modules.desktoplauncher.WorkDesktopProbe` is read-only: it hooks and
logs, never changes behaviour. It is gated by `PreferenceKeys.TEST_HOOK` (`"test_hook"`), which
`BaseHookModule.isEnabled()` always lets through, so it runs without any frontend switch — a real
key plus UI wiring must replace it before this ships as a feature.

| Hook | What it prints |
|---|---|
| `DeviceProfile.<init>` (widest ctor) | one `profile …` line per supported window bound: `isLandscape/isTablet/isMultiDisplay/isTwoPanels/transpose/taskbarPresent`, `taskbarHeight`, `stashedTaskbarHeight`, `hotseatBarHeight`, `hotseatIcons`, `getInsets()`, `widthPx x heightPx`, the `InvariantDeviceProfile` grid (`numColumns x numRows`, folders, `numAllAppsColumns`, `deviceType`) and `toSmallString()`. Plus one `environment …` line: `zui_pc_mode`, `zui_dp_display_pc_mode`, `persist.sys.zui.pcmode`, orientation, dp size, density, `extra_new_layout_config`. |
| `WorkLauncher.onCreate`, `PcAllAppsActivity.onCreate` | `activity <name>.onCreate` — tells which screen the following lines belong to |
| `PcAllAppsPagedView.setupContentDimensions(int)` | `allapps cols/rows/perPage/items/count/view=<measured px>` |
| `TaskbarManager.recreateTaskbar()` | `taskbar recreateTaskbar context=<TaskbarActivityContext|TaskbarActivityContextDp|null>` |
| `TaskbarActivityContext.createDefaultWindowLayoutParams(int, String)` | `taskbar class/gravity/width/height/defaultHeight/requestedHeight` |
| `ZuiHotseat.onLayout(…)` | `hotseat wxh visibility=… recentUsed=wxh children=…` |

Reading it:

```powershell
adb logcat -d -b all | Select-String "\[test_hook\]"
```

- If `taskbar … context=…` lines appear while `hotseat recentUsed=null`, the bottom bar is the
  taskbar (§2.5 first row) — Q1 answered.
- `profile` lines come in pairs (portrait + landscape bounds); `bounds=` and `insets=` answer Q3,
  and `transpose=false` on both validates §2.2.
- `allapps` in portrait should print `cols=9 rows=4` on TB710FU, confirming §2.4.

All lines are de-duplicated and capped per process, so the probe stays quiet after the first
rebuilds.

## 10. First probe run (TB710FU, 2026-10-20)

Manually started `WorkLauncher` with PC mode off (`zui_pc_mode=0`), log level DEBUG:

```
profile landscape=true tablet=true multiDisplay=false twoPanels=false transpose=false
        taskbarPresent=false taskbarHeight=195 stashedTaskbarHeight=30
        hotseatBarHeight=308 hotseatIcons=6 insets=Rect(0, 98 - 0, 138)
        bounds=3200x2000
        | grid=12x6 folder=4x4 allAppsCols=6 idpHotseatIcons=6 deviceType=2
        | isTablet:true, isMultiDisplay:false, widthPx:3200, heightPx:2000, … rotationHint:1
environment pkg=com.zui.desktoplauncher pcMode=0 dpPcMode=0 pcProp=0
        orientation=1 size=800x1280dp density=400 layoutConfig=6x4
activity WorkLauncher.onCreate
hotseat 2000x415 visibility=4 recentUsed=0x172 children=0 visibility=0
taskbar recreateTaskbar context=null                # after writing zui_pc_mode=1
```

What this already settles:

1. **The work desktop is laid out against landscape bounds even when the display is portrait.**
   The display reports `orientation=1`, `800x1280dp`, yet the only `DeviceProfile` constructed has
   `bounds=3200x2000`, `landscape=true`, `rotationHint=1`. That is Q3's "landscape-sized window"
   answer (to be re-confirmed with the real PC-mode toggle, since this run had PC mode off).
2. **The live grid is 12x6, not the 13x7 every `device_profiles` variant declares.** The
   launcher's own prefs hold `migration_src_workspace_size=12,6`,
   `migration_src_db_file=launcher_work.db`, `migration_src_device_type=2`, so the work grid is
   restored/migrated from persisted state rather than taken raw from the XML. A hook must read the
   live `InvariantDeviceProfile` instead of assuming the XML numbers.
3. **`isTaskbarPresent=false` is real at runtime**: `TaskbarManager.recreateTaskbar()` reports
   `context=null` and no `TaskbarActivityContext` is ever created, even when the launcher reacts to
   `zui_pc_mode` flipping to 1 by recreating its taskbar. `ZuiHotseat` is instead attached but
   `visibility=4` (GONE) with an empty recent-used container. Neither bar is visible outside PC
   mode; the real PC-mode run is still needed for Q1.
4. **Writing `Settings.System zui_pc_mode=1` is not enough to enter PC mode** — `persist.sys.zui.pcmode`
   and the display's `inPcmode` both stayed 0, and only the launcher's `recreateTaskbar()` fired.
   The probe therefore has to be driven from the real system toggle (quick settings / settings UI).

Still open after this run: the PC-mode taskbar identity (Q1), the portrait `DeviceProfile`
(only the landscape bound appeared), and the `allapps` grid once `PcAllAppsActivity` is opened.
