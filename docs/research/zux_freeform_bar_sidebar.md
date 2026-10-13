# ZuxFreeformBar (`com.zui.freeform.sidebar`) — APK structure and real responsibilities

Reverse-engineering note. Verified against the APK **`com.zui.freeform.sidebar` version
`1.18.02.26.20.2` (`versionCode` 18, `minSdk` 28, `targetSdk` 35, `compileSdk` 35,
platform build 15 / ZUXOS)** loaded in JADX: manifest, the full 4879-class inventory,
every app-owned class, the AIDL surface it talks to, its resources and its call graph
(`get_xrefs_to_*` was used for every "unused/dead" claim below).

App label `@string/app_name`, launcher icon `@drawable/ic_launcher_new`, theme
`@style/Theme.ZuiFreeformBar`. This is the **ZUXOS floating sidebar (浮窗侧边栏)** — the
screen-edge handle that hosts freeform (floating) windows and split-screen entries.

---

## 1. Packaging and identity

| Item | Value |
|---|---|
| Package | `com.zui.freeform.sidebar` |
| `sharedUserId` | `android.uid.system` (runs as the system uid) |
| Application class | `com.zui.freeform.sidebar.ZfbApplication` |
| Components | `MainActivity`, `FloatService`, `ui.PowerBootReceiver` — and nothing else |
| Permissions | `INTERACT_ACROSS_USERS`, `SYSTEM_ALERT_WINDOW`, `MOUNT_UNMOUNT_FILESYSTEMS`, `RECEIVE_BOOT_COMPLETED`, `zui.permission.OVFREEFORM_CLIENT`, `ACCESS_HIDDEN_PROFILES`, `VIBRATE` |
| DEX | one `classes.dex` (`classes2.dex` does not exist) |
| Assets | only `assets/dexopt/baseline.prof` + `baseline.profm` |
| Build flavour | `appComponentFactory=androidx.core.app.CoreComponentFactory`, ViewBinding (`META-INF/androidx.databinding_viewbinding.version`), `extractNativeLibs=false`, `allowBackup=false`, `resizeableActivity=false` |

There is **no `<provider>`, no native library, no service other than `FloatService`** and no
`android:persistent="true"`.

### 1.1 Component contracts

| Component | Declared | Notes |
|---|---|---|
| `MainActivity` | `exported=false`, filter: `android.intent.action.MAIN` **without** `CATEGORY_LAUNCHER` | Not reachable from the launcher and has an empty `ConstraintLayout` body. `onCreate()` only calls `startService(FloatService)`. Its only in-APK references are `FloatService.initSideBarView`/`onDestroy` toggling the static `MainActivity.isStart` flag, so it is started **explicitly by an external privileged component** (Settings / framework / SystemUI), never by the user. |
| `FloatService` | `exported=true`, action `com.zui.freeform.sidebar.FloatService`, `onBind` returns `null`, `onStartCommand` returns `START_STICKY` (3) | The whole product. |
| `ui.PowerBootReceiver` | `exported=true`, `priority=1000`, filters `ACTION_SHUTDOWN`, `BOOT_COMPLETED`, `…SPREAD_GUIDE`, `…SPLIT_BTN_GUIDE`, `…SPLIT_FINGERS_GUIDE`, `…SPLIT_FIRST_GUIDE`, `…SIDEBAR_GUIDE`, `…AILENS_BROADCAST` | The code handles **only `BOOT_COMPLETED`** (see §5). |

### 1.2 Bundled code vs. app code

Of the 4879 classes, the overwhelming majority is third-party/framework filler:

* `androidx.*`, `com.google.android.material.*`, `kotlin.*` — ordinary UI stack.
* `net.sourceforge.pinyin4j.*` (+ its XML parser `com.hp.hpl.sparta.*`, plus a leftover
  `demo.Pinyin4jAppletDemo`) — used for pinyin sorting of the app grid.
* `zui.*` — **the OEM framework UI toolkit is vendored into this dex**: `zui.widget.*`
  (ListViewX, AlphabeticIndexBar, KeyboardViewX, NumberPickerX, BottomTabBar, SlideView,
  AnimTextView, SimpleToolbar, BottomToolBar), `zui.app.*` (ActionDialog, MessageDialog,
  FloatDialog, Date/TimePickerDialogX, UnitPickerDialog), `zui.internal.app.*`
  (ActionController, DialogController, MessageController, FloatController),
  `zui.internal.menu.*`, `zui.preference.*` + `zui.appcompat.preference.*` (a full
  Preference framework), `zui.themes.*`, `zui.notification.*`, `zui.platform.R`.
* `com.android.internal.ov.*` — vendored OEM platform helpers (13 top-level classes):
  `IOvFreeformService` (+ `.Stub`/`.Default`/`.Proxy`), `IOvAutoRestartCbk`,
  `IOvVisibleTasksListener`, `OvFreeformManager`, `OvAccFuncId`, `OvWindowControllerCallback`,
  `OvcBdc`. (`OvFreeformManagerIA` / `OvcBdcIA` are D8 synthetic interface-default bridges,
  not real classes.)
* `android.util.Ov*` / `android.app.OvDialogUtils` — vendored platform utility classes
  (`OvCommonUtils`, `OvFeatureUtils`, `OvFreeformUtils`, `OvSplitScreenUtils`,
  `OvWindowUtils`, `OvcExtraActivityOptionParams`, `OvcKeyEventUtils`, `OvcTaskFindConst`,
  `OvDialogUtils`). **These are dead weight here**: their only xrefs are each other, and
  `android.util.OvFreeformUtils`, `android.util.OvWindowUtils` and `android.app.OvDialogUtils`
  have zero xrefs at all. `OvSplitScreenUtils.supportsOvSplitScreen()` even calls
  `OvCommonUtils.TODO(...)`, which throws `RuntimeException("OneVision-TODO: …")` — the
  vendored copies are unfinished OEM scaffolding, not usable API.
* `com.zui.freeform.sidebar.*` — the actual product: **75 classes** (10 in the root package
  incl. 5 synthetic lambdas, 23 in `FloatService`, 11 in `utils`, ~11 in `ui`, 7
  `databinding` bindings, 19 `R` inner classes).

**Classes the dex references but does not contain.** The APK has **no `android.os` package
and no `android.app` package beyond `OvDialogUtils`**, and `com.android.internal.ov` has no
`OvCommonManager`. Therefore `android.os.OVBundle`, `android.os.OneModeManager`,
`android.os.RetCallback`, `android.app.ActivityManagerNative`, `android.app.TaskStackListener`,
`android.app.UserSwitchObserver`, `android.app.IRemoteCallback` and
`com.android.internal.ov.OvCommonManager` are **compile-time references resolved from the ZUI
boot classpath at runtime**, not code shipped in this APK. Static analysis that only reads
this dex will find no implementation for them.

### 1.3 Which vendored `zui.*` code is real

| Subpackage | Verdict |
|---|---|
| `zui.widget` (23) | Only `AlphabeticIndexBar` is used (index bar in the all-apps grid). KeyboardViewX / NumberPickerX / ListViewX / SlideView / BottomToolBar / BottomTabBar / SimpleToolbar / AnimTextView … are dead. |
| `zui.util` (7) | `CommonUtils` is used by `FloatService` (product name, pad detection, `getAndroidInternalResDimen`); `ReflectClass`/`ReflectMethod` are used by `ThemeManager`. |
| `zui.themes` (2) | Only the constant `ThemeManager.ACTION_THEME_CHANGED = "com.zui.intent.action.THEME_CHANGED"`; `IThemeService` (21 methods, service name `zuithemes`) is never called. |
| `zui.app` (12) | Dead as dialogs; `ZuiContextConstants` is used indirectly by `ThemeManager`. |
| `zui.preference` (14), `zui.appcompat.preference` (20), `zui.appcompat.app` (4), `zui.appcompat.widget` (6), `zui.internal.app` (4), `zui.internal.menu` (5), `zui.notification` (2) | Fully dead — a vendored copy of the ZUI Preference/dialog toolkit that this app never touches. |
| `net.sourceforge.pinyin4j` (10) + `com.hp.hpl.sparta` (30) | **Actually used** — `PinYinUtils` sorts the app grid by pinyin; sparta is pinyin4j's XML parser. `demo.Pinyin4jAppletDemo` is dead sample code. |

App-owned classes worth naming: `ZfbApplication`, `MainActivity`, `FloatService` (plus
`FloatingOnTouchListener`, `FloatingOnLongClickListener`, `SideBarOnHoverListener`,
`MoreMenuOnHoverListener`, `SidebarAlphaHandler`, `ShowPopupWindowHandler`,
`UserActionTaskStackListener`, `SettingsValueObserver`, `ThemeReceiver`, `PackageReceiver`,
`LanguageReceiver`, `SuspendReceiver`, `mDreamReceiver`); `utils.{SidebarAppUtils, AppInfo,
FreeFormUtils, FreeFormDataCollection, PreferenceUtils, PinYinUtils, ActiveIconUtil,
FastBitmapDrawable, SystemProperties}`; `ui.{FloatFrameLayoutView, AppIconItemView,
AppIconListAdapter, AppIconRecyclerAdapter, AppIconGridAdapter, AppsAlphabeticIndexBar,
FloatGifView, RoundImageDrawable, PowerBootReceiver}`.

---

## 2. Process model and lifecycle

The app is a **system-managed overlay that is expected to die and be resurrected**:

1. `ZfbApplication.onCreate()` → `OvFreeformManager.getService().registerAutoRestart(new
   IOvAutoRestartCbk.Stub(){…}, 1)`. `OvFreeformManager.getService()` is
   `ServiceManager.getService("ovfreeform")` — i.e. the app subscribes to the platform's
   **`ovfreeform` system service** and asks it to auto-restart the process. The callback body
   is empty; the registration itself is the point.
2. `PowerBootReceiver` starts `FloatService` on `BOOT_COMPLETED` **iff**
   `Settings.System."enable_zuifreeformbar" == 1` (default 0). `ACTION_SHUTDOWN` is declared
   but falls through to nothing.
3. `FloatService.onCreate()` builds the app list, adds the overlay window, registers one
   `ContentObserver` set and six receivers + a `TaskStackListener`.
4. `FloatService.onDestroy()` tears everything down and ends with **`System.exit(0)`** —
   it kills its own process, relying on `registerAutoRestart` to bring it back.

Two private `SharedPreferences` files are used (both via `Context.getSharedPreferences`):

| File | Keys | Writer |
|---|---|---|
| `barLoaction` (sic) | `LOCATION_X`, `LOCATION_Y`, `BAR_RIGHT`, `RATIO_Y` | `FloatService.saveBarState()` |
| `freeform_preferences` (`MODE_MULTI_PROCESS`=4) | `top_1` … `top_5`, plus a boolean key named after `ro.build.version.incremental` | `PreferenceUtils` |

---

## 3. The overlay window itself

`FloatService.initSideBarView()` + `showFloatingWindow()`:

* `WindowManager.LayoutParams`: `type = 2038` (`TYPE_APPLICATION_OVERLAY`),
  `format = 1` (translucent), `flags = 296` (`FLAG_NOT_FOCUSABLE` 0x8 |
  `FLAG_NOT_TOUCH_MODAL` 0x20 | `FLAG_LAYOUT_IN_SCREEN` 0x100),
  `width/height = WRAP_CONTENT`, `gravity = TOP|START`, `setTitle("sidebar")`.
* Content is `R.layout.float_window` (or `float_window_large` on the two large-panel SKUs
  `TB-9707F_PRC` / `TB-J616F_PRC`): a single `ImageView@id/sidebar` showing
  `freeform_side_bar` / `freeform_side_bar_left` (and `_large` variants).
* Position: `x = widthPixels` (right edge) or `0` (left edge); `y` from the persisted
  `RATIO_Y`, defaulting to 33 % of screen height minus the handle height, clamped below the
  status bar. `setNoMoveAnim()` uses reflection to OR `PRIVATE_FLAG_NO_MOVE_ANIMATION` into
  `WindowManager.LayoutParams.privateFlags` so the handle does not animate while dragged.
* Gesture exclusion: `setSystemGestureExclusionRects([Rect(0,0,3640,192)])` at creation
  (a hard-coded 3640×192 rectangle), replaced on every `ACTION_DOWN` with the handle's real
  visible rect widened by `R.dimen.gesture_exclusion_offset` — so edge-back gestures do not
  fight the handle.

### 3.1 Touch / hover behaviour (`FloatingOnTouchListener`)

| Input | Effect |
|---|---|
| long-press ≥ `LONGPRESSED_TIME_THRESHOLD` (10 s on pads, 100 ms on phones) with movement ≤ `LONGPRESSED_POS_THRESHOLD − 15` px | enters "drag mode": vibrate 600 ms, switch handle to `freeform_side_bar_pressed`, then `ACTION_MOVE` repositions the window (clamped to ≥ status-bar height) and updates `RATIO_Y` |
| `ACTION_UP` in drag mode | snap to the nearer screen edge (`x = widthPixels` if released on the right half, `0` otherwise) unless `canDragLeft()` forbids it — `canDragLeft()` returns false on products containing `J716F`, `K606F`, `J706F`, `J607F`. Then `saveBarState()` + `setSettingDb()`. |
| horizontal slip ≥ `R.dimen.sidebar_left_slip_offset` without long-press | opens the top-5 drawer (`showTop5PopupWindow`) |
| `ACTION_CANCEL` | restore the handle to its current edge |
| `ACTION_HOVER_ENTER` on the handle | after 400 ms → top-5 drawer |
| `ACTION_HOVER_EXIT` | dismiss after 500 ms |
| hover on the "more" footer | after 400 ms → dismiss the top-5 drawer, then reopen it as the all-apps grid 400 ms later |

Handle opacity: `SidebarAlphaHandler` fades `ImageAlpha` to 153 three seconds after the last
interaction (`resetTime()` restores 255).

### 3.2 Publishing the hit region

`FloatService.setSettingDb()` writes
`Settings.System."sidebar_edge_inhibition"` as `"<enabled 0|1>:<rotation>:<side 1|2>:<top>:<bottom>"`
(side 2 = right, 1 = left; the band is `mLayoutParams.y … y + side_bar_height + 150`, clamped
to the screen). This is the contract by which the rest of the system knows where the sidebar
currently sits.

---

## 4. What the app is actually responsible for

### 4.1 Enabling / gating

`FloatService.isFreeformBarEnabled()` requires **all** of:

1. not in PC mode (`Settings.System."zui_pc_mode" != 1`);
2. `isOneVisionEnable()` — `Settings.System."enable_onevision_all_feature" == 1`
   (default `1` on non-commercial builds, `0` when
   `ro.config.lgsi.channel == "commercial"`);
3. `Settings.System."enable_zuifreeformbar" == 1` (default from the platform bool
   `android:config_zuifreeformbar_default`, read by `Resources.getIdentifier`);
4. `Settings.System."enable_temp_zuifreeformbar" == 1` (default 1) — the temporary kill
   switch that `FreeFormUtils.disableSidebar()` toggles;
5. `Settings.Global."device_provisioned" == 1`.

`SettingsValueObserver` re-evaluates on every one of those URIs plus
`Settings.Global."force_resizable_activities"`, `Settings.Secure."ov_collapse_side_bar"`
(dismisses the drawer) and `zui_pc_mode` (recreates the window). A `TaskStackListener`
(`onTaskStackChanged` / `onTaskFocusChanged`) drives `updateSidebarVisibility()`, which hides
the window while `android.service.dreams.DreamActivity` or
`com.android.permissioncontroller.permission.ui.GrantPermissionsActivity` is the top activity
(the list is `hideScreenRecordViewPackages`), and restores it afterwards. A private
`action_dreampage_enter` / `action_dreampage_exit` receiver does the same for dream pages.

### 4.2 The two app drawers

**Top-5 list** (`showPopupWindow` / `showTop5PopupWindow`) — `R.layout.drawer_window` with
`drawer_layout` visible: a `ListView` of five icon rows (`R.layout.applistitem`) plus a
"more" footer (`R.layout.list_footview`, `ic_more_menu`,
`@string/sidebar_more_apps`). `AppIconListAdapter.getCount()` is hard-coded to 5. Entries
already visible as freeform windows are rendered disabled at alpha 0.3; suspended apps are
greyed through `FastBitmapDrawable(bitmap, true)`.

**All-apps grid** (`showAllAppWindow`) — same window, `allapp_rl` visible: a 5-column
`GridLayoutManager` `RecyclerView` (`R.layout.appgriditem`, icon + name) over the full
launcher-app list, plus a `zui.widget.AlphabeticIndexBar@id/index_bar` bound by
`ui.AppsAlphabeticIndexBar`. Item view types distinguish normal / currently-in-freeform /
suspended. `AppIconGridAdapter` is the older `BaseAdapter` variant (still present, row-height
alignment and `com.zui.calendar` active-icon special case).

Note the **pinyin inconsistency**: the list *order* comes from
`PinYinUtils.toPinYin(userId + appName)` (`pinyin4j`, `LOWERCASE + WITHOUT_TONE + WITH_V`),
but the index-bar letter comes from the raw `appName.charAt(0)`, so Chinese names are not
indexed by their pinyin initial.

### 4.2.1 All-apps panel: layouts and interception points

The panel's three layout variants are compiled to `res/OS.xml` (default), `res/9T.xml` and
`res/sA.xml` (the `sw400dp`/landscape variants), and the grid item to `res/q8.xml` /
`res/bH.xml`. Resource *names* survive in `resources.arsc` even though the file names are
obfuscated, so `getIdentifier("appgriditem", "layout", …)` and
`getIdentifier("appicon_iv", "id", …)` both work from a hook.

| View | Attributes that matter when reusing or replacing it |
|---|---|
| `allapp_rl` (RelativeLayout) | `paddingTop 21dp`, `paddingBottom 24dp`, size `all_grid_window_width` × `all_grid_window_height`; children are the grid and a `match_parent` FrameLayout holding `index_bar` |
| `appicon_gv` (RecyclerView) | `match_parent` × `match_parent`, `paddingStart/End` `all_grid_padding_*`, `overScrollMode="never"` |
| `index_bar` (`zui.widget.AlphabeticIndexBar`) | `visibility="gone"`, `layout_gravity="end|center_vertical"`, inside that FrameLayout — which is why a replacement rail is added to the FrameLayout, not to the panel |
| `appgriditem` (`AppIconItemView`, RelativeLayout) | `wrap_content`; `appicon_iv` = `all_grid_item_width` × `all_grid_item_height`, `marginTop 18dp`, `layout_centerHorizontal`; `appname_tv` = `match_parent`, `marginTop 4.5dp`, `layout_below` the icon, `gravity="center_horizontal"`, `textSize 14dp` |

Interception points found while adding the letter sections:

* `AppIconRecyclerAdapter.onCreateViewHolder` **ignores** its `viewType` and always inflates
  `appgriditem`, so a heading row can reuse the host's own item view and its `ViewHolder`.
* `onBindViewHolder(ViewHolder, int)` is the only bind override; the payload overload is not
  overridden, and the host reads `mAppInfoList.get(position)` inside it, so a row model that
  inserts headings has to translate the position and hand it back through `Chain#proceed`.
* `onBindViewHolder` calls `getItemViewType(i)` on `this` with the index it was given, so a
  `getItemViewType` hook cannot tell the host's internal call from RecyclerView's by position
  alone; the hook uses a re-entrancy flag instead.
* `GridLayoutManager.getSpanSizeLookup()` is public API, and the default lookup is instantiated
  per manager. Hooking `getSpanSize` on that instance's class is therefore the supported way to
  make one row span the whole grid without linking `androidx.recyclerview` in the module.
* **Both** span questions have to be answered, not just the size: `DefaultSpanSizeLookup`
  overrides `getSpanIndex(int, int)` with `position % spanCount`, a fast path that is only
  correct while every item is one column wide. With headings spanning whole rows the column has
  to come from the row model as well (`GridLayoutManager.getSpanIndex` → `SpanSizeLookup`
  `getCachedSpanIndex` → `getSpanIndex`). `assignSpans` computes its own span index from the span
  sizes, so it needs no help.
* `RecyclerView$ViewHolder.itemView` is a public field, reachable by `getField` on the class the
  host's classloader holds.


### 4.3 The app list

`SidebarAppUtils.loadAppsWithLauncher()` enumerates
`Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER)` with
`PackageManager.queryIntentActivitiesAsUser(..., userHandle)` for **every**
`UserManager.getUserProfiles()` profile, keeping only packages for which
`OvCommonManager.supportsOvFreeformOnPkg(pkg, userId)` is true. Keys are `"pkg:userId"`.
Icons: `getApplicationIcon` → `ActiveIconUtil` background for non-adaptive icons →
`FastBitmapDrawable` → `getUserBadgedIcon` for secondary users → `RoundImageDrawable` when
`Settings.System."icon_themed_enable" == 1`. (`OvCommonManager` is **not present in this
dex** — it is resolved from the platform at runtime.)

The list is rebuilt on: `PACKAGE_ADDED` / `PACKAGE_CHANGED` / `PACKAGE_REMOVED`,
`PACKAGE_REPLACED` (icon only), `PACKAGES_SUSPENDED` / `PACKAGES_UNSUSPENDED`,
`LOCALE_CHANGED` (names), `ThemeManager.ACTION_THEME_CHANGED` (icons), and
`Settings.Secure."freeform_list_pkg_switch_update"` (500 ms debounce).

### 4.4 Top-5 selection and recency

* Default top-5 = first five entries of the pinyin-sorted launcher list; written to
  `freeform_preferences.top_1..top_5` on first run, guarded by a boolean keyed on
  `ro.build.version.incremental` (`SystemProperties` reflection wrapper).
* The framework/SystemUI publishes the currently visible freeform windows into
  `Settings.Secure."cur_visible_freeform_list"` (semicolon-separated). A `ContentObserver`
  parses it, de-duplicates into `mRecentFreeFormList` (max 5) and rewrites `top_1..top_5`
  as *recent ∪ previous top-5 ∪ default top-5*, truncated to five. This is how "recently used
  floating apps" replace the defaults.

### 4.5 Launching a window — the core action

`FloatService.startActivity(AppInfo)` resolves a `ComponentName` (launcher intent, falling
back to the cached class name; `com.google.android.apps.safetyhub` is forced to the cached
name) and then picks one of four routes:

| Condition | Action |
|---|---|
| app already in a multi-window task — `RunningTaskInfo.windowingMode == 6` and `activityType != 2` | `OneModeManager.sendMsgRouteCallback("com.android.SystemUI", 2, bundle{android.intent.extra.TASK_ID})` → SystemUI brings the existing window forward |
| app is full-screen **and** is `cn.com.langeasy.LangEasyLexis` or `com.myscript.calculator.lenovo` | `OneModeManager.sendMsgRouteCallback("com.android.SystemUI", 134, bundle{TASK_ID})` (`CMD_FREEFORM_BAR_ENTER_FREEFORM`) |
| otherwise | `FreeFormUtils.startActivityInFreeform()` |

`startActivityInFreeform()` builds `ActivityOptions.makeBasic()`, reflectively calls the
hidden `setLaunchWindowingMode(5)` (`WINDOWING_MODE_FREEFORM`), `setLaunchDisplayId(0)` and
`setLaunchBounds(¼…¾ of the screen)`, then `Context.startActivityAsUser(intent, bundle, user)`
on an intent built by `makeLaunchIntent()` (`ACTION_MAIN` + `CATEGORY_LAUNCHER`, flags
`270532608` = `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`). The
`com.lenovo.menu_assistant.hd` package additionally receives `extra "ModuleSrc" =
"ZuiFreeformBar"`.

The app never *owns* the floating windows: it only asks the system to create them. It also
never draws its own dialogs — `float_dialog.xml` / `float_dialog_anchor_mask_window.xml` are
referenced only by the vendored `com.zui.internal.app.FloatController` / `zui.app.FloatDialog`.

**Split-screen boundary.** This app does *not* implement split screen. Its only split-screen
behaviour is (a) recognising that a tapped app already lives in a multi-window task and asking
SystemUI to raise it, and (b) the six now-dead guide broadcasts. Everything else — the split
UI, the divider, the windowing state machine — is SystemUI / `wm.shell`.

### 4.6 Telemetry

`FreeFormDataCollection` (own `HandlerThread`) inserts into Lenovo's UE provider:

| Event | When | Columns |
|---|---|---|
| `ZOV125` | tap in the top-5 drawer (`type`=1; `type`=2 for the "more" entry with `pkg=null`) | `Event`, `timestamp`, `type`, `pkg` |
| `ZOV126` | tap in the all-apps grid | `Event`, `timestamp`, `pkg` |

URIs: `content://com.lenovo.ue.device.provider/platform` (used here) and `…/app`
(`sendAppData`, currently unreferenced). There is no consent check or opt-out in this APK.

---

## 5. Dead / vestigial surface (verified by xref)

These exist in the APK but no code path reaches them. They are the main "the manifest lies"
traps when reasoning about this app:

| Item | Evidence |
|---|---|
| Six guide broadcast actions (`SPREAD_GUIDE`, `SPLIT_BTN_GUIDE`, `SPLIT_FINGERS_GUIDE`, `SPLIT_FIRST_GUIDE`, `SIDEBAR_GUIDE`, `AILENS_BROADCAST`) | Declared in the manifest with `priority=1000`, but `PowerBootReceiver.onReceive` (confirmed in smali) branches only on `BOOT_COMPLETED`; the action strings appear nowhere else in the dex. |
| All guide UI strings (`float_guide_*`, `sidebar_guide_*`, `split_screen_*`, `guide_message*`, `fingers_guide_title`) | Referenced only by `R`; no code site. |
| `ui.FloatGifView` (`android.graphics.Movie` GIF player) | 0 xrefs. |
| `SidebarAppUtils.inZuiFreeformBlackList()` and therefore the whole `force_resizable_activities` / `config_zuiFreeformPackageBlackList` / `R.array.app_not_resizeable_black_list` logic | `setForceResizeable()` is still called, but its only reader is this private method, which has 0 xrefs. The app list is *not* filtered by either blacklist. |
| `FreeFormUtils.disableSidebar()`, `disableTopBar()`, `disableNoficationPanel()`, `disableHomeAndRecent()` | 0 xrefs each (kiosk / one-vision helpers, unused). |
| `android.util.OvFreeformUtils`, `android.app.OvDialogUtils`, `android.util.OvWindowUtils` | 0 xrefs. |
| `ISplitScreen` binder | `getISplitScreen()` fetches the binder from SystemUI over `OneModeManager` and stores it in `mSplitScreen`, but `isAppExistInSplitScreen()` only null-checks it and then uses a local `getRunningTasks()` scan instead. The AIDL method `isAppExistInSplitScreen` is never invoked. |
| `SystemProperties.set(String, String)` | Empty body — a no-op. |
| `IOvVisibleTasksListener`, `OvcBdc`, `OvAccFuncId`, `OvWccId`, `OvWindowControllerCallback` | Only self/AIDL references; the app never registers or calls them. |
| `zui.preference.*`, `zui.appcompat.preference.*`, `zui.app.*` dialogs, `zui.internal.*`, `zui.notification.*`, most of `zui.widget.*` | Vendored toolkit with no call site in the product code (§1.3). |
| `android.app.OvDialogUtils` | 0 xrefs; its methods all route through the external `ActivityClient.getInstance()` (`ovAccFunc` 102/106/107). |

**Gotchas when touching this app:**

* `FloatService.onDestroy()` ends in `System.exit(0)`. Because the app runs under
  `android.uid.system`, a hook that triggers service teardown kills the whole system-uid
  process, not just this app.
* The manifest's guide broadcasts and guide strings are a false lead (§5) — anything built on
  them will never fire on this build.
* `Settings.System.sidebar_edge_inhibition` is written with
  `notifyChange(uri, null, 32768)` (i.e. `UserHandle.USER_ALL`), so observers in other users
  see it too.

---

## 6. Platform integration surface (what it really depends on)

| Channel | Detail |
|---|---|
| `ovfreeform` system service | `OvFreeformManager.getService()` → `ServiceManager.getService("ovfreeform")` (constant `OVFREEFORM_SERVICE`), cached in a `Singleton`. `IOvFreeformService` exposes **23 transactions** (`getMaxTransactionId()` = 22): `supportsOvFreeform(Arr)`, `enableOvFreeformOnPkg`, `getFreeformNextAvailableBounds`, `queryDefaultFreeformBoundsByPos`, `hide/restoreHiddenOvFreeformsBy*`, `hide/restoreHiddenGroupByRecents`, `inLandscape`, `isShowingWhenLocked`, `getCurFullscreenTaskInfo`, `getVersion`, `notifyCurVisibleTasks`, `printLogInWms`, `supports/enableOvTopBar*`, `register/unregisterVisibleTasksListener`, `register/unregisterAutoRestart`. This app calls **only** `registerAutoRestart` and `supportsOvFreeform` (the latter via the static `SidebarAppUtils.freeformService`). |
| Auto-restart callback | `registerAutoRestart(cbk, 1)` where `1 = AR_CBK_CONDITION_TYPE_SIDE_BAR` (the sibling constants `AR_CBK_CONDITION_TYPE_NONE = 0` live in the vendored `OvFreeformUtils`). The callback body is empty. |
| `OneModeManager.sendMsgRouteCallback(route, cmd, bundle[, RetCallback])` | `route = "com.android.systemui.recents.OverviewProxyService"` with `cmd = 2` returns a bundle whose `extra_shell_split_screen` binder is `ISplitScreen.Stub.asInterface(...)`; `route = "com.android.SystemUI"` with `cmd = 2` (raise an existing multi-window task) and `cmd = 134` (`CMD_FREEFORM_BAR_ENTER_FREEFORM`, freeform→full-screen for two apps). |
| `ISplitScreen` | A single method, `int isAppExistInSplitScreen(String, int)` (transaction 32), fetched over the `OneModeManager` route above — never obtained from `ServiceManager`, and (see §5) never actually called. |
| `ActivityManager` | `getRunningTasks()` for the visible-window list and the multi-window task scan; `ActivityManagerNative.getDefault().registerTaskStackListener` / `unregisterTaskStackListener`; `ActivityManager.getService().registerUserSwitchObserver`. |
| `ActivityTaskManager` | `getTasks(MAX_VALUE,false,false)` inside `FreeFormUtils.isAppExistInFullScreen`. |
| `Settings.System` | `enable_zuifreeformbar`, `enable_temp_zuifreeformbar`, `enable_onevision_all_feature`, `zui_pc_mode`, `icon_themed_enable`, `sidebar_edge_inhibition` |
| `Settings.Secure` | `cur_visible_freeform_list`, `freeform_list_pkg_switch_update`, `ov_collapse_side_bar`, `ufreeform_gesture_guide_notified`, `ov_splitscreen_button_guide_notified`, `ov_splitscreen_fingers_guide_notified`, `ov_user_guide_shown` |
| `Settings.Global` | `device_provisioned`, `force_resizable_activities` |
| System properties (reflection) | `ro.config.lgsi.channel` (`commercial`), `ro.config.zui.region` (`ROW`), `ro.build.version.incremental`, `ro.build.version.sdk`, `persist.sys.zui.theme` |
| Reflection | `WindowManager.LayoutParams.privateFlags` / `PRIVATE_FLAG_NO_MOVE_ANIMATION`, `ActivityOptions.setLaunchWindowingMode`, `android.app.StatusBarManager.disable`, `android.os.SystemProperties.*`, `UserHandle.USER_MULTISPACE_MIN/MAX` (inside the framework `OvCommonUtils`, fallback 900–999). No hidden-API bypass library is used — the app relies purely on `android.uid.system`. |
| `zui.themes.ThemeManager.ACTION_THEME_CHANGED` | theme-change icon refresh |
| Other vendored OEM scaffolding (all unreferenced) | `OvAccFuncId` (`ActivityClient.ovAccFunc` function ids 100–112), `OvWccId` (9 window-controller function ids consumed by `OvWindowControllerCallback.executeRequest`), `OvcBdc` (a duplicate of the UE telemetry writer), `OvcGuideConst` (`persist.ov.freeform.dcv_menu_show`), `OvcTaskFindConst`, `zui.notification.INotificationManager`. The app sends no notifications. |

---

## 7. Notes for ZTool

Concrete, verified anchors if this app is ever hooked or reproduced:

* **Enable switch**: `Settings.System.enable_zuifreeformbar` is the master toggle the ROM's
  Settings UI writes and `PowerBootReceiver`/`isFreeformBarEnabled()` read. Its default comes
  from the platform bool `android:config_zuifreeformbar_default`.
* **Temporary kill switch**: `Settings.System.enable_temp_zuifreeformbar` (`0` hides the
  sidebar without changing the user-visible switch; observed by `SettingsValueObserver`).
* **Live window list**: `Settings.Secure.cur_visible_freeform_list` is written by the platform
  and read by this app — it is the cleanest existing source of "which apps are floating now".
* **Top-5 store**: `freeform_preferences.xml`, keys `top_1`…`top_5`, values `pkg:userId`.
* **Geometry store**: `barLoaction.xml` (`BAR_RIGHT`, `RATIO_Y`, `LOCATION_X`, `LOCATION_Y`)
  plus `Settings.System.sidebar_edge_inhibition` as the published band.
* **IPC entry point**: the `ovfreeform` service (`IOvFreeformService`, 23 transactions), of
  which this app only uses `registerAutoRestart(cbk, AR_CBK_CONDITION_TYPE_SIDE_BAR)` and
  `supportsOvFreeform`.
* **Not in this APK**: `OneModeManager`, `OVBundle`, `RetCallback`, `ActivityManagerNative`,
  `TaskStackListener`, `UserSwitchObserver` and `OvCommonManager` must be read from the ZUI
  `framework.jar`/`services.jar`, not from this dex.
