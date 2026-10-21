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

ZTool now has a scope (`ScopeKeys.DESKTOP_LAUNCHER`) and two modules in
`hook/modules/desktoplauncher/`: the read-only probe (§9) and the bounds fix (§12).
`com.zui.launcher` keeps its own separate hooks.

## 2. Why the desktop stays landscape-shaped

**The controlling fact is §12 / §10.5: `WindowBounds(Rect, Rect)` force-rotates every portrait
rect into landscape, so the launcher only ever sees a landscape bound and therefore only ever
builds a landscape `DeviceProfile`.** Everything in this section describes the *grid*, which is
why the grid never reflows even after the bound is fixed.

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

This was settled on device (§10.2): **the PC bottom bar is `ZuiHotseat`**. `taskbar.*` is never
instantiated (`recreateTaskbar context=null`), while the hotseat stays `INVISIBLE` and is rendered
through a surface with a populated recent-used container. Either way the geometry story is the
same: one horizontal row, sized from `DeviceProfile` (`hotseatBarSizePx`), with
`numShownHotseatIcons` slots.

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

### 6.0 Bounds first (new top candidate after §10.1)

Everything downstream — grid, taskbar, all-apps, insets — is derived from the `DeviceProfile`
built for a `WindowBounds`. If the launcher only ever sees `3200x2000`, then no grid trick will
make the desktop fit a 2000x3200 panel.

Two levers, in order (§11):

1. **Rebuild on rotation.** `InvariantDeviceProfile.D` returns early for every flag except
   density/navigation mode, so a rotation never rebuilds the profiles. A hook has to inject that
   refresh (after `DisplayController.h` computed the new `Info`).
2. **Feed it a portrait bound.** If `Info.supportedBounds` is landscape-only, add the portrait
   `WindowBounds` (the set is mutable) or hook `WindowManagerProxy.getRealBounds` /
   `estimateInternalDisplayBounds`.

Launcher3 already carries the per-orientation slot machinery (`f8178c`, `*Landscape` arrays), so
feeding it a portrait bound is the "native" fix and would make §6.1/§6.4 unnecessary.

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

Measured on device: with the §12 bound fix, rotating the panel keeps every icon at its relative
position, so **no grid migration is needed** — the desktop is already stable in both orientations.
The visible defect is therefore not the grid but the label width (§14).

### 6.3 PC all-apps drawer

**Implemented — see §13.** The three private counts (`cellCountX/cellCountY/itemsPerPage`) are
`final`, so the module re-factorises `itemsPerPage` through the public static
`calculateGridSize` instead of writing them.

The window half (the activity's `sensorLandscape`) is handled by the system-side
`WorkModePortrait` hook; `PcAllAppsActivity.getDeviceProfile()` is not useful (the `max()/min()`
math ignores the swap), and `PcAllAppsContainerView.onDeviceProfileChanged` is an empty override.

### 6.4 Bottom bar

It is the hotseat (`ZuiHotseat`, §10.2), so the seams are:

1. `ZuiHotseat.resetCellSize(DeviceProfile)` — public, receives the profile whose
   `numShownHotseatIcons`, `hotseatBarSizePx`, `hotseatCellHeightPx` drive the row.
2. `ZuiHotseat.onMeasure`/`onLayout` — the actual row placement; `getRecentUsedLayout()` and
   `getAllAppsEntryCount()` decide how much width the recent-used block claims.
3. `DeviceProfile.numShownHotseatIcons` — the slot count, already read from the profile.
4. `ZuiHotseat.setAlpha(float)` / `setRecentUsedShow(boolean)` — visible-state knobs (the view is
   surface-rendered and stays `INVISIBLE`).

The launcher taskbar (`taskbar.*`) is dead weight in PC mode, but if a later ZUI build re-enables
it, its seams were: `TaskbarActivityContext.createDefaultWindowLayoutParams(int, String)` →
`DeviceProfile.Builder.build(boolean)` (finalises the `final` `taskbarHeight`) →
`TaskbarManager.recreateTaskbar()` → `TaskbarView.onLayout` / private `n(…)`.

## 7. Open questions (must be resolved before implementation)

1. ~~**Which component draws the PC bottom bar?**~~ **Resolved (§10.2): `ZuiHotseat`.** The launcher
   taskbar (`taskbar.*`) is never instantiated — `TaskbarManager.recreateTaskbar()` reports
   `context=null` throughout — while the hotseat, a child of `DragLayer`, stays `INVISIBLE` and is
   alpha-animated with a populated recent-used container, i.e. it is rendered through a surface.
2. ~~Grid change vs workspace DB.~~ Resolved: `GridSizeMigrationTask.migrateGridIfNeeded` compares
   the live grid with `KEY_WORKSPACE_SIZE`/`KEY_HOTSEAT_COUNT` and re-runs
   `MultiStepMigrationTask.migrate(old, new)` whenever they differ, then persists the new grid
   (§6.1). A per-orientation grid flip is therefore destructive; pick variant 1, 2 or 3 there.
3. **Exact bounds.** Confirmed in real PC mode (§10.1): the only `DeviceProfile` is
   `bounds=3200x2000`, `landscape=true` while the panel reports `orientation=1`, `800x1280dp`.
   Next: the `displayinfo` line shows whether `DisplayController.Info.supportedBounds` even lists a
   portrait entry — that decides between "hook the bounds source" and "transpose inside
   `DeviceProfile`".
4. **Launcher-side orientation requests.** Only `PcAllAppsActivity` declares `sensorLandscape`;
   `WorkLauncher`/`NormalLauncher` are `unspecified`. The probe now logs every
   `setRequestedOrientation` call, which also explains the three activity recreations (§10.1).
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
| `DeviceProfile.<init>` (widest ctor) | one `profile …` line per supported window bound: `isLandscape/isTablet/isMultiDisplay/isTwoPanels/transpose/taskbarPresent`, `taskbarHeight`, `stashedTaskbarHeight`, `hotseatBarHeight`, `hotseatIcons`, `getInsets()`, `widthPx x heightPx`, the `InvariantDeviceProfile` grid (`numColumns x numRows`, folders, `numAllAppsColumns`, `deviceType`) and `toSmallString()`. Plus one `environment …` line (`zui_pc_mode`, `zui_dp_display_pc_mode`, `persist.sys.zui.pcmode`, orientation, dp size, density, `extra_new_layout_config`) and one `displayinfo …` line: `DisplayController.Info.currentSize/rotation/density` and **every** `supportedBounds` entry with its `bounds/availableSize/insets/rotationHint/landscape`. |
| `WorkLauncher.onCreate`, `PcAllAppsActivity.onCreate` | `activity <name>.onCreate window=<w>x<h> gravity=<g>` — which screen the following lines belong to |
| `Activity.setRequestedOrientation(int)` | `orientation <activity> requested=<v> (was <v>)` — catches whoever re-locks landscape |
| `PcAllAppsPagedView.setupContentDimensions(int)` | `allapps cols/rows/perPage/items/count/view=<measured px>` |
| `TaskbarManager.recreateTaskbar()` | `taskbar recreateTaskbar context=<TaskbarActivityContext|TaskbarActivityContextDp|null>` |
| `TaskbarActivityContext.createDefaultWindowLayoutParams(int, String)` | `taskbar class/gravity/width/height/defaultHeight/requestedHeight` |
| `ZuiHotseat.onLayout(…)` | `hotseat wxh visibility=… alpha=… parent=… recentUsed=wxh children=…` |
| `ZuiHotseat.setAlpha(float)` | `hotseat set alpha=… visibility=…` — reveals surface-based rendering |

Reading it:

```powershell
adb shell "su -c 'grep -h \"\[test_hook\]\" /data/adb/lspd/log/modules_*.log | tail -40'"
adb logcat -d -b all | Select-String "\[test_hook\]"    # only with verbose log off
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
   `INVISIBLE` (visibility 4) with an empty recent-used container. §10.2 shows what that becomes in
   real PC mode.
4. **Writing `Settings.System zui_pc_mode=1` is not enough to enter PC mode** — `persist.sys.zui.pcmode`
   and the display's `inPcmode` both stayed 0, and only the launcher's `recreateTaskbar()` fired.
   The probe therefore has to be driven from the real system toggle (quick settings / settings UI).

Still open after this run: the PC-mode taskbar identity (Q1), the portrait `DeviceProfile`
(only the landscape bound appeared), and the `allapps` grid once `PcAllAppsActivity` is opened.

### 10.1 PC-mode run (same day, 23:54, `zui_pc_mode=1`, `persist.sys.zui.pcmode=1`)

```
taskbar recreateTaskbar context=null
profile  landscape=true … taskbarPresent=false taskbarHeight=195 … bounds=3200x2000
         | grid=12x6 folder=4x4 allAppsCols=6 deviceType=2 | … rotationHint:1
environment pcMode=1 dpPcMode=0 pcProp=1 orientation=1 size=800x1280dp density=400
activity WorkLauncher.onCreate
hotseat 3200x415 visibility=4 recentUsed=0x172 children=0
hotseat 2000x415 visibility=4 recentUsed=0x172 children=0
hotseat 2000x415 visibility=4 recentUsed=582x172 children=3 visibility=0
profile  … taskbarHeight=120 …                                  # second profile, same bounds
allapps  cols=9 rows=4 perPage=36 items=0 count=60 view=0x0
activity PcAllAppsActivity.onCreate                             # ×3 within 80 s
hotseat 3200x415 visibility=4 recentUsed=582x172 children=3
```

What the real PC-mode run changes:

1. **PC mode is on, the panel is portrait, and the desktop canvas is still landscape.**
   `environment` says `orientation=1`, `800x1280dp`; every `profile` says `bounds=3200x2000`,
   `landscape=true`, `rotationHint=1`, and no portrait profile is ever constructed. So the
   launcher measures the desktop against a 3200x2000 canvas on a 2000x3200 screen — the right
   third of the workspace and of the bottom bar simply falls outside the panel. **This is the
   primary root cause candidate, ahead of the grid count**: even a perfect portrait grid would be
   drawn on a landscape canvas.
   Open sub-question: whether `DisplayController.Info.supportedBounds` contains a portrait entry
   at all. The extended probe dumps it (`displayinfo …`), and if it does not, the correct hook is
   at the bounds source, not at `DeviceProfile`.
2. **Still no launcher taskbar in PC mode**: `recreateTaskbar context=null`. The bar that is
   actually laid out is `ZuiHotseat` — `INVISIBLE` (visibility 4, not GONE) with a populated
   recent-used container (`582x172`, 3 children). §10.2 confirms it: the hotseat is the bar, drawn
   through a surface while the view stays invisible.
3. **The all-apps drawer reproduces the overflow by construction**: `cols=9 rows=4 perPage=36`
   are the same counts landscape would give, and 9 × 265 px = 2385 px on a 2000 px page.
4. **`PcAllAppsActivity` is recreated three times in 80 s** (`23:55:06`, `23:56:17`,
   `23:56:24`) — consistent with an orientation fight, since the activity declares
   `sensorLandscape`. The extended probe logs every `setRequestedOrientation` call with the
   requesting activity.
5. `taskbarHeight` differs between two profiles for identical bounds (195 then 120) — two
   `DeviceProfile` variants coexist (home vs in-app/taskbar presentation).

### 10.2 Second PC-mode run (00:46, probe v2, two processes)

Both processes started while the display was **landscape** (`orientation=2`, `1280x800dp`):

```
taskbar recreateTaskbar context=null
profile landscape=true … taskbarHeight=120 … bounds=3200x2000 | grid=12x6 … deviceType=2
environment pcMode=1 dpPcMode=0 pcProp=1 orientation=2 size=1280x800dp density=400
displayinfo currentSize=Point(3200, 2000) rotation=1 density=400 supportedCount=null [null]
hotseat set alpha=0.0 visibility=8
orientation WorkLauncher requested=-1 (was -1)
activity WorkLauncher.onCreate window=-1x-1 gravity=0
hotseat set alpha=0.0 visibility=4
hotseat 3200x415 visibility=4 alpha=0.0 parent=DragLayer recentUsed=582x172 children=3
allapps cols=9 rows=4 perPage=36 items=0 count=60 view=0x0
activity PcAllAppsActivity.onCreate window=-1x-1 gravity=0
… later in the same process …
hotseat set alpha=1.0 visibility=4
hotseat 2000x415 visibility=4 alpha=1.0 parent=DragLayer recentUsed=582x172 children=3
```

What this settles:

1. **Q1 answered — the PC bottom bar is `ZuiHotseat`, not `taskbar.*`.**
   `TaskbarManager.recreateTaskbar()` stays `context=null` for the whole session, while the hotseat
   is a child of `DragLayer` that is kept `INVISIBLE` (4) and alpha-animated (0.0 → 1.0) with a
   populated recent-used container. ZUI lays it out and renders it through a surface, so the view
   itself never becomes `VISIBLE`. `DeviceProfile.isTaskbarPresent=false` is therefore consistent
   with the real component, not a suppression of it.
2. **The work desktop does not request landscape itself**: the only request is
   `WorkLauncher requested=-1 (was -1)`. The repeat `PcAllAppsActivity.onCreate` calls in the
   previous run must come from the manifest's `sensorLandscape` plus the system rotation, not from
   in-process requests.
3. **The same process re-measures the hotseat at 2000 px wide** (`3200x415` → `2000x415`) with no
   new `profile` line. A 2000 px width is the portrait side of the panel, so the window rotated
   while the profile stayed landscape — either the profile was rebuilt identically (the log gate
   de-duplicates identical lines) or it was not rebuilt at all. Probe v3 distinguishes the two:
   `displayinfo` now carries `currentSize`/`rotation`, and `config` lines mark the
   `onConfigurationChanged` events.
4. **`supportedBounds` was unreadable** in that run (`supportedCount=null`): the field is a
   `Set<WindowBounds>`, so a `List` cast dropped it. Probe v4 casts to `Collection`, prints every
   entry plus `realBounds`, and stamps each `profile`/`displayinfo` line with a sequence number so
   a rebuild can no longer be hidden by the de-duplicating gate.

### 10.3 Third run (00:53) — a rotation changes nothing

```
00:53:53.901 taskbar recreateTaskbar context=null
00:53:53.999 profile landscape=true … bounds=3200x2000   # taskbarHeight=195
00:53:54.000 environment pcMode=1 pcProp=1 orientation=1 size=800x1280dp    ← panel is portrait
00:53:54.000 displayinfo currentSize=Point(3200, 2000) rotation=1 …
00:53:54.101 orientation WorkLauncher requested=-1 (was 0)                  ← was locked landscape
00:53:54.164 hotseat 3200x415 … recentUsed=0x172 children=0
00:53:54.736 hotseat 3200x415 … recentUsed=582x172 children=3
00:53:55.357 config WorkLauncher orientation=2 size=1280x800dp             ← rotated to landscape
00:53:55.392 profile landscape=true … bounds=3200x2000   # taskbarHeight=120
00:53:55.667 config WorkLauncher orientation=1 size=800x1280dp             ← rotated back
00:53:55.673 hotseat 2000x415 …                                            ← 2000 = portrait width
00:54:06.168 hotseat 3200x415 …
```

- The panel **is** portrait at startup (`orientation=1`, `800x1280dp`) while the launcher still
  builds a `3200x2000` profile and reports `Info.currentSize = Point(3200, 2000)`, `rotation=1`.
- After the final rotation back to portrait the hotseat re-measures at 2000 px (the real window),
  and **no new `profile` appears**. §11 explains why: the IDP refresh is gated on the wrong flags.
- `orientation WorkLauncher requested=-1 (was 0)` — the activity *had* been locked to landscape
  (`0`), and something in-process reset it to unspecified. So the launcher starts by asking for
  landscape even though its manifest is `unspecified`.

### 10.4 Capturing the probe output

LSPosed with verbose logging writes module output to files, **not** to the module's logcat tag:

```powershell
adb shell "su -c 'grep -h \"\[test_hook\]\" /data/adb/lspd/log/modules_*.log | tail -40'"
# or the LSPosed manager's Log tab, which shows the same lines
```

`logcat -s ZToolXposedModule` only shows the framework's own `D` lines (module dispatch), which is
why the probe output appears "missing" there.

### 10.5 Fourth run (00:57) — one bound, always landscape

```
profile #1 … bounds=3200x2000
displayinfo #1 currentSize=Point(3200, 2000) rotation=1
    realBounds=(Rect(0,0-3200,2000), available=3200x1764, insets=Rect(0,98-0,138), hint=1, landscape=true)
    supportedCount=1 [(the same landscape bound)]
config WorkLauncher orientation=1 size=800x1280dp          ← panel rotated to portrait
profile #2 … bounds=3200x2000                              ← rebuilt, still landscape
displayinfo #2 currentSize=Point(2000, 3200) rotation=0     ← Info followed the panel
    realBounds=(Rect(0,0-3200,2000), … landscape=true)      ← the bounds did not
    supportedCount=1 [(3200x2000 landscape)]
```

- `Info.currentSize` / `Info.rotation` **do** follow the panel, so the launcher sees the rotation.
- `Info.realBounds` and `Info.supportedBounds` do **not** — both keep the landscape 3200x2000 that
  `WindowBounds`'s swapping constructor produced (§12.1).
- The `DeviceProfile` *is* rebuilt after the rotation (so §11's listener gate is not the only path),
  but it is rebuilt from the same landscape bound, hence identical.

That is the whole bug in one line: **the launcher is told about the rotation, rebuilds, and then
builds a landscape profile because the only bound it has is landscape.** §12 is the fix.

## 11. Why a rotation never reaches the workspace

The refresh chain, read from `DisplayController` and `InvariantDeviceProfile`:

```
Activity.onConfigurationChanged(Configuration)
  → DisplayController.onConfigurationChanged(Configuration)      # ComponentCallbacks
      early-returns unless rotation / densityDpi / fontScale / PortraitSize changed
  → DisplayController.h(display)  (obfuscated private recompute)
      builds a new Info, derives change flags, notifies on the main executor
  → DisplayInfoChangeListener.onDisplayInfoChanged(context, info, flags)
  → InvariantDeviceProfile.D(context, info, flags)
```

Change flags: `CHANGE_ACTIVE_SCREEN=1`, `CHANGE_ROTATION=2`, `CHANGE_DENSITY=4`,
`CHANGE_SUPPORTED_BOUNDS=8`, `CHANGE_NAVIGATION_MODE=16`, `CHANGE_ALL=31`.

`DisplayController.Info` (public fields): `currentSize`, `realBounds`, `rotation`,
`supportedBounds` (**`Set<WindowBounds>`**, one entry per distinct supported bound),
`normalizedDisplayInfo`, `cutout`, `fontScale`, `navigationMode`, `systemDensityDpi`,
`getDensityDpi()`, `isTablet(bounds)` (= smallest side ≥ 500 dp), `smallestSizeDp(bounds)`.

And the gate that matters:

```java
// InvariantDeviceProfile.D(context, info, flags)
if (!Utilities.isWorkMode() || (flags & 20) == 0) return;
H(context);   // re-resolve the grid and rebuild every DeviceProfile
```

`20 == CHANGE_DENSITY(4) | CHANGE_NAVIGATION_MODE(16)`. **On this listener path the profile is
rebuilt only for a density or navigation-mode change — a rotation (2) or a supported-bounds change
(8) alone would not rebuild it.** §10.3 matches that (a rotation produced a `config` line and a
re-measured hotseat but no `profile`), yet §10.5 shows a profile being rebuilt right after a
rotation, so at least one other path (launcher-side config handling or the IDP singleton being
re-initialised) also triggers a rebuild. Either way the rebuild is useless while the only bound is
landscape (§12).

Two consequences for the portrait feature:

1. A hook must supply the missing refresh: notify `DisplayController` listeners (or call the
   private `H`) once the display actually rotates, otherwise the workspace keeps the landscape
   `DeviceProfile` forever.
2. The refresh is only useful if `Info.supportedBounds` contains a portrait bound. Probe v4 prints
   `supportedCount` and every entry; if the set is landscape-only, the fix belongs one level deeper
   (`WindowManagerProxy.getRealBounds` / `estimateInternalDisplayBounds`, or adding a portrait
   `WindowBounds` to the mutable `Set` right after `Info` is constructed).

## 12. The fix that works: inject the portrait bound

### 12.1 Root cause, in one constructor

```java
// com.zui.desktoplauncher.util.WindowBounds
public WindowBounds(Rect rect, Rect insets, int hint) {
    if (rect.height() > rect.width()) {
        this.bounds = new Rect(0, 0, rect.height(), rect.width());   // portrait -> landscape
    } else {
        this.bounds = rect;
    }
    this.insets = insets;
    this.rotationHint = 1;                                           // always landscape
    this.availableSize = new Point(bounds.width() - l - r, bounds.height() - t - b);
}
```

Every `WindowBounds` produced from a `WindowMetrics`/`Rect` goes through this constructor
(`fromWindowMetrics` calls the 2-arg overload), so a 2000x3200 portrait window is stored as
3200x2000 with `rotationHint = 1`. `DisplayController.Info.supportedBounds` therefore contained a
single, landscape entry (confirmed: §10.5), and `InvariantDeviceProfile` built exactly one
`DeviceProfile` — landscape — no matter which way the panel was held.

### 12.2 The module

`hook/modules/desktoplauncher/WorkDesktopPortrait.kt`, gated by
`PreferenceKeys.WORK_MODE_PORTRAIT` (same switch as the system-side release), hooks
`DisplayController$Info.<init>(Context, WindowManagerProxy, Map)` and, when the panel is portrait
and `zui_pc_mode == 1`, appends a real portrait `WindowBounds` (built with the 5-arg constructor,
which does **not** swap) to `Info.supportedBounds` — the set is mutable, so no field hacking is
needed. The launcher's own machinery then builds both profiles and `getBestMatch` picks the
portrait one.

Measured effect (2026-10-21 00:59):

```
[work_mode_portrait] WorkDesktopPortrait: added bound 2000x3200 available=2000x2964 rotation=0
[test_hook] displayinfo … supportedCount=2 [(2000x3200, landscape=false, hint=0); (3200x2000, landscape=true, hint=1)]
[test_hook] profile #1 landscape=false … bounds=2000x3200 | grid=12x6 … rotationHint:0
[test_hook] profile #2 landscape=true  … bounds=3200x2000 | grid=12x6 … rotationHint:1
[test_hook] hotseat 2000x415 …
```

A screenshot of that state shows the work desktop filling a 2000x3200 panel: wallpaper, window
icons, and the bottom bar spanning the full portrait width. This is the first time a portrait
`DeviceProfile` ever existed in this APK.

### 12.3 What is still landscape-shaped after this fix

The bound fixes the *canvas*; the contents still come from landscape-tuned sources:

1. **Grid shape**: `grid=12x6` in both profiles — the column count is the `work` grid option, not
   derived from the bound. In a 2000 px-wide portrait window, 12 columns are ~53 dp apart against
   a 60 dp icon, so the workspace is cramped and the shipped single-column layout (§2.3) hides it.
   Making this portrait needs §6.1 (with its migration caveat).
2. **PC all-apps drawer**: addressed separately in §13.
3. **Hotseat grouping**: the bar now spans 2000 px, but `numShownHotseatIcons=6` and the
   recent-used block keep their landscape proportions.
4. **`DisplayController.Info.realBounds` stays landscape** even after injection; only the
   `supportedBounds` set was corrected. Widgets/drag code that reads `realBounds` directly still
   sees 3200x2000, so a later revision may want to correct it too (it is a `final` field, so that
   needs field reflection).

## 13. PC all-apps drawer: portrait grid (implemented)

`hook/modules/desktoplauncher/PcAllAppsPortraitGrid.kt`, gated by the same
`PreferenceKeys.WORK_MODE_PORTRAIT` switch.

The three counts the host computes in `PcAllAppsPagedView.<init>` (`cellCountX`, `cellCountY`,
`itemsPerPage`) are **`final`**, and `itemsPerPage` is the page-splitting threshold in the layout
pass, so patching the fields would desynchronise paging. The module instead re-factorises
`itemsPerPage` at the public static seam:

1. The paged view's constructor is hooked to capture its live `DeviceProfile` (the field typed
   `com.zui.desktoplauncher.DeviceProfile`) and `dimen/pc_allapps_cellwidth`, i.e. whether the
   drawer is portrait and how many columns the panel can hold.
2. `calculateGridSize(count, cellX, cellY, cellCountX, cellCountY, itemsPerPage, out)` is hooked
   after `proceed()`; when portrait it rewrites `out[0] = columns`, `out[1] = ceil(count / columns)`,
   where `columns` is the **largest divisor of `itemsPerPage` that fits `widthPx / cellWidth`**.

Keeping `columns × rows == itemsPerPage` means `rankOnCurrentPage`, `getIconViewByItemInfo`,
`getItemCount` and the `i5 >= itemsPerPage` page split all stay consistent — no final-field
reflection.

TB710FU in portrait: `itemsPerPage = 36`, `2000 / 265 = 7` → **6 columns x 6 rows**
(6 x 265 = 1590 px of 2000; 6 x 312 = 1872 px of ~2700 usable). Landscape is untouched
(`portrait` is false).

Status: **verified on device** — the user reports 6x6 looks very good.

## 14. Desktop icon labels: two lines (implemented)

With the bounds fixed (§12), the remaining portrait defect is label truncation: a portrait desktop
keeps the 12-column grid, so each cell is ~55 dp wide while the icon is 60 dp — single-line labels
ellipsize at 3-4 CJK characters.

The host already ships the machinery, in `BubbleTextView`:

```java
// onMeasure
if (shouldUseTwoLine() && this.f8020c != null) {
    CharSequence split = modifyTitleToSupportMultiLine(measuredWidth - paddings, this.f8020c,
                                                        getPaint(), this.f8019b);
    if (!TextUtils.equals(split, this.f8021d)) {
        this.f8021d = split;
        setText(split);
        if (TextUtils.indexOf(split, LINE_SEPARATOR) != -1) { setSingleLine(false); setMaxLines(2); }
        else                                                 { setSingleLine(true);  setMaxLines(1); }
    }
}
```

but `shouldUseTwoLine()` only enables it for `mDisplay == 1` (all apps,
`ENABLE_TWOLINE_ALLAPPS`) and `mDisplay == 6` (device search, `ENABLE_TWOLINE_DEVICESEARCH`).
Desktop icons are `mDisplay == 0`.

**The host's splitter is also wrong for mixed names.** Once forced on for desktop icons it produced

```
MT管理器      -> "M"          / "T管…"      (break after the first glyph)
NoteIn一笔记   -> "Note…"                    (no break at all, single-line ellipsis)
```

so `modifyTitleToSupportMultiLine`'s breakpoint table is not usable here. The module therefore does
its own split:

- `BubbleTextView.applyLabel(ItemInfoWithIcon)` is hooked to keep the *original* title per view
  (weak map), so later measures always split from the untruncated string.
- `BubbleTextView.onMeasure(int, int)` is hooked **before** `proceed()`: the available width is
  `MeasureSpec.getSize(widthSpec) - compoundPaddingLeft - compoundPaddingRight` (115 px for a
  12-column portrait desktop on TB710FU), the title is split greedily at that width, and the split
  is applied with `setMaxLines(2)` before the host measures.
- The break prefers a boundary outside a Latin word: a split that lands between two Latin
  letters/digits is moved back to the start of that run (`Layout Inspect` → `Layout` / `Inspect`).
  CJK characters are always valid break points.
- A title that already fits is returned by identity, which also lets the hook restore
  `setMaxLines(1)` after rotating back to landscape.

Measured on TB710FU portrait:

```
KernelSU       -> Kernel / SU
Layout Inspect -> Layout / Inspect
MT管理器        -> MT管   / 理器
Notein一笔记    -> Notein / 一笔记
```

All complete, no ellipsis; `日历`, `设置`, `相机`, `飞牛`, `Via`, `微信` stay single-line. No grid
migration was involved — the user confirmed that icon positions are stable across rotation once the
bound is fixed (§6.2).

## 15. The bottom-right status cluster is SystemUI's, not the launcher's

The bar's right-hand controls (search, volume, WLAN, control-center chevron, notifications, battery,
clock) cannot be reached from `com.zui.desktoplauncher`. Evidence:

- The probe now dumps the hotseat's view tree once (§9). In PC mode portrait:

  ```
  ZuiHotseat#hotseat(2000x415,v4 INVISIBLE)
    ShortcutAndWidgetContainer#-(2306x172)   ← pinned-app row, 306 px wider than the screen
    View#-(0x0)
    View#-(262x195)
    HotseatRecentUsedContainer#-(0x172)
    ImageButton#-(172x172)                   ← all-apps entry
  ```

  and its parent `DragLayer` holds only `AccessibilityActionsView`, `Workspace`,
  `PageIndicatorDots`, `ZuiEditModePanel`, `DropTargetBar2`, `DeleteDropTarget2`,
  `SecondaryDropTarget`, `ScrimView`, `ZuiHotseat`, `LauncherRecentsView`. **No status cluster.**
- `uiautomator dump` sees only `com.zui.desktoplauncher` nodes, and none of them is a status icon.
- The remaining window at that height is SystemUI's `NavigationBar0`, frame `[0,3050][2000,3200]`.
- SystemUI's own dex (extracted from `/system_ext/priv-app/ZuiSystemUI/ZuiSystemUI.apk`) carries a
  whole PC-mode package: `com.android.systemui.pcmode.nav.*` (`MWorkNavManager`, `NavUtils`,
  `MyRecyclerView`, `CommonAdapter`, `FixAdapter`, `NavItemRelativeLayout`,
  `panel.NavPanelManager`, `panel.NavVolumeManager`, `panel.AppVolumeAdapter`, …) plus
  `pcmode.view.XFButton` and the `pc_nav_fix_layout_1..4` / `nav_workmode_poppanel` layouts.

So shrinking those controls is a **SystemUI** change (`com.android.systemui` is already a ZTool
scope), and it needs SystemUI's code loaded in the decompiler — the JADX MCP only exposes the APK
currently open, which is `ZuiLauncherPC.apk`.

Two separate widths are in play:

1. **SystemUI cluster** (the user's suggestion): the number/size of the right-hand controls, which
   currently eats roughly the last third of the 2000 px bar.
2. **Launcher icon row**: `ShortcutAndWidgetContainer` measures **2306 px** while the window is
   2000 px, i.e. the pinned-app row is laid out with landscape spacing and the rightmost icons are
   clipped regardless of the cluster. That one *is* ours (`ZuiHotseat` / `DeviceProfile`
   `hotseatCellHeightPx`, `numShownHotseatIcons`, `hotseatBorderSpace`).
