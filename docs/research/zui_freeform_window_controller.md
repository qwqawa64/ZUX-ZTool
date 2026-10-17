# ZUI Freeform Window Controller (小窗控制器) — implementation notes

Reverse-engineering note for the window decoration that owns every ZUI freeform
window, written from the same injection point used by
`hook/modules/systemui/FreeformBottomEdgeResizeHook.kt`. It ends with a concrete
design for removing the reserved top strip (the "forehead" / 额头) instead of
carving it out of the window.

Verification basis:

| Item | Value |
| --- | --- |
| Device | Lenovo TB710FU (tablet), 3200×2000, 400 dpi, `ROTATION_90` |
| Build | `TB710FU_CN_OPEN_USER_Q00019.0_A16_ZUI_17.5.04.509_ST_260911` (ZUXOS 1.5) |
| APK | `/system_ext/priv-app/ZuiSystemUI/ZuiSystemUI.apk` (SystemUI + wm.shell) |
| Freeform task | 988×1600 px = 395×640 dp (a phone-shaped window on the tablet) |

All dimensions below were read back from the running device (`dumpsys activity`,
`dumpsys SurfaceFlinger`, `screencap`), not only from decompiled code. Where the
code path could not be pinned down statically it is marked **unconfirmed**.

---

## 1. Where the existing hook sits

`FreeformBottomEdgeResizeHook` touches three classes of the drag-resize stack:

```text
DragResizeInputListener.TaskResizeInputEventReceiver.handleMotionEvent   (ACTION_DOWN)
  -> DragResizeWindowGeometry.calculateCtrlType(x, y, isTouch, isEdgeResizePermitted)
  -> OvfDriListenerInjector.mCtrlType = ctrlType
       if (ctrlType == 8) return true;          // bottom gesture bar: swallow the DOWN
  -> TaskPositioner.onDragPositioningStart(rawX, rawY, ctrlType, displayId)
       -> VeiledResizeTaskPositioner.onDragPositioningStart   (or OvfScaleTaskPositionerDecorator)
  ACTION_MOVE -> onDragPositioningMove / OvfDriListenerInjector.handleDragGestureEvent
  ACTION_UP   -> onDragPositioningEnd / OvfDriListenerInjector.handleDragGestureEvent
       -> DragPositioningCallbackUtility.changeBounds -> OvfDpcUtility (triggers)
       -> Transitions.startTransition(6, wct, handler)
```

`ctrlType` is a 4-bit edge mask returned by `calculateCtrlType`; ZUI adds bit
`16` as a marker of its own ("proportional freeform resize"):

| Bit | Meaning |
| --- | --- |
| `1` | left edge |
| `2` | right edge |
| `4` | top edge |
| `8` | bottom edge — **alone (`8`) means the bottom gesture bar**, handled by `OvfDriListenerInjector`, not by the positioner |
| `5`/`6`/`9`/`10` | corners (LT/RT/LB/RB) |
| `16` | ZUI proportional-resize marker (not part of the geometry; set by the hook) |

The hook rewrites `8` to `8 or 16` so that:

1. `DragResizeInputListener` no longer takes the `ctrlType == 8` early return
   (which would swallow the DOWN and hand the gesture to the quick-swipe path);
2. `OvfDriListenerInjector.handleDragGestureEvent` rejects the gesture because it
   requires `mCtrlType == 8` exactly;
3. the normal `TaskPositioner` path runs, and
   `DragPositioningCallbackUtility.changeBounds` sees bit `16` and applies the
   proportional bottom-edge resize.

The rest of this document is about the *decoration* those touch regions belong
to, because that is what draws the "forehead".

---

## 2. Who owns a freeform window

`DesktopModeWindowDecorViewModel` creates one `DesktopModeWindowDecoration` per
task (`mWindowDecorByTaskId`). The decoration implements `WindowDecoration`
(AOSP desktop-mode decoration with ZUI additions) and is responsible for:

* the caption surface (`WindowDecorViewHost` → `SurfaceControlViewHost`),
* the border surface (`OvWindowDecorationInjector`),
* the resize input layer (`DragResizeInputListener`),
* the inset sources (`captionBar`, `mandatorySystemGestures`) applied to the task,
* the handle menu (`OvHandleMenu`) opened from the caption handle.

Relevant ZUI machinery:

| Class | Role |
| --- | --- |
| `WindowDecoration` | base: `relayout`, `updateCaptionInsets`, surface/view-host lifecycle, caption insets |
| `DesktopModeWindowDecoration` | freeform/desktop decoration: geometry, maximize/handle menus, veil, `OvfDpcUtility` |
| `OvWindowDecorationInjector` | ZUI-only surfaces: border view, caption handle sampling, user guide |
| `OvcWinDecorUtils` | feature gates (`canDoOvCaptionBar`, `isOvDefaultMode`, `getOvCaptionHeightId`, `isProportionalScaleFreeForm`) |
| `OvcWinDecorCtlList` | package/component allow-deny lists (top bar, spy feature, caption-from-app-bounds) |
| `OvCaptionBarObserver` | Secure settings that relayout all decorations (`show_ov_top_bar`, `ov_tbar_settings_changed`, `ov_user_guide_shown`, …) |
| `OvfDriListenerInjector` | bottom-bar quick-swipe (up = minimize, down = fullscreen) |
| `VeiledResizeTaskPositioner` / `OvfScaleTaskPositionerDecorator` | edge/corner resize positioners (selected per `OvcSupportInfo.freeformDragMode`) |
| `OvfDpcUtility` | resize trigger state machine (maximize / minimize / fullscreen / safe margin) |

### 2.1 Surfaces of one freeform window (measured, task 33)

```text
Task=33 surface (988x1600), color #303030 (task description background)
├─ app surface                 988x1600 at (0,0)     <- app is NOT resized
└─ Decor container of Task=33  z=30000, 988x1600
   ├─ Caption of Task=33       988x98  at (0,0)     <- transparent, draws the 3-dot handle
   ├─ TaskInputSink of Decor   988x1600 (invisible) <- DragResizeInputListener sink
   └─ Border container of Task=33 988x1600
      └─ Border of Task=33     988x1600             <- rounded border + bottom gesture handles
```

`dumpsys SurfaceFlinger` for `Caption of Task=33`:

```text
displayFrame=[1567 396 2555 494]  sourceCrop=[0 0 988.000000 98.000000]
```

i.e. the caption surface is **full window width × 98 px tall** and sits on the
window's top edge. The 98 px equals 39 dp × 2.5 (the framework
`status_bar_height` is 39 dp); the same 39 dp is the height of the handle vector
`ovc_handle_menu_focused_night` (`android:height="39dp"`, `android:width="50dp"`).
The three dots render at exactly 50×39 dp (measured dot runs: 13 px wide, 26.6 px
centre spacing), so the pill is a 50 dp-wide ImageView centred in the strip.

The caption view itself is transparent — in the ZTool window the strip sampled
`#303030`, while the app content sampled `#0f1416`; `#303030` is the task
description background colour, not anything painted by the caption. In the
Settings window both the strip and the app background sampled `#171c1f`. In
both cases the strip only shows what is *under* the caption surface.

### 2.2 Which layout feeds the caption (partly unconfirmed)

`DesktopModeWindowDecoration.updateRelayoutParams` chooses between
`ov_desktop_mode_window_decor_pad` (ZUI One Vision caption bar),
`desktop_mode_app_header` / `desktop_mode_app_handle` (AOSP), and, in
ZUI desktop mode, `OvcPcModeUtils.getLayoutResId()` →
`zui_desktop_window_decor_freeform` / `pcmode_window_decor`.

`ov_desktop_mode_window_decor_pad.xml` is the only layout that puts the 3-dot
vector in the caption:

```xml
<WindowDecorLinearLayout id="@+id/desktop_mode_caption"
    android:background="@color/transparent"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <ImageView id="@+id/caption_handle"
        android:layout_width="@dimen/ov_window_decor_caption_bar_width_pad" (50dp)
        android:layout_height="match_parent"
        android:src="@drawable/ovc_caption_bar_focused"/>
    ...
</WindowDecorLinearLayout>
```

Two observations do not line up statically and should be confirmed with one
runtime log before implementation:

1. the caption surface is 988 px wide, which means `RelayoutParams.mCaptionWidthId
   == 0` (full width), while the pad branch sets it to
   `ov_window_decor_caption_bar_width_pad` (50 dp → 125 px);
2. `mCaptionHeightId` + `mCaptionTopPadding` resolve to 98 px, but the exact
   contributing term could not be pinned down (the handle has no top padding, so
   the height comes from the dimension id, not from `mCaptionTopPadding`).

Log `mRelayoutParams.mLayoutResId`, `mCaptionWidthId`, `mCaptionHeightId`,
`mCaptionTopPadding`, `mIsCaptionVisible` once inside `relayout` to close this.
None of the design in §5 depends on the answer.

### 2.3 The border surface and the handles

`OvWindowDecorationInjector.updateViewsAndSurfaces_Ovf` inflates
`ovc_window_decor_border` into its own full-task-size view host ("Border of
Task=…") when `OvfSubFeatureFlags.ENABLE_OVF_WD_BORDER_VIEW`:

* `ovc_wdb_bottom_gesture` — bottom bar container (visibility from
  `@integer/ovc_wdb_bottom_indicator_visibility`);
* `ovc_wdb_bottom_gesture_center` — centre drag handle (size from
  `ovc_wdb_bottom_gesture_handle_*`, divided by `scaleX` / `hangingScaleX`);
* `ovc_wdb_bottom_gesture_left` / `_right` — bottom corner handles
  (`ovc_wdb_bottom_gesture_corner_size`, `..._corner_margin`);
* background `ovc_wd_border_line` with corner radii from `relayoutParams.mCornerRadius`.

These are the views the existing `FreeformCornerHandleIndicatorHook` captures via
`SurfaceControlViewHost.setView` and toggles by id, and the click targets that
`OvfTaskCorners` (`leftBottomCornerRegion`, `rightBottomCornerRegion`) map to
`ctrlType 9` / `10`. `OvfTaskEdges` maps the bottom centre bar region to
`ctrlType 8`.

### 2.4 Handle menu

The caption handle is also a button: `OvHandleMenu` inflates
`ov_desktop_mode_window_decor_handle_menu_pad` and positions it at
`captionY + captionHeight − reservedSpace`, i.e. **already** a floating overlay
on top of the window content (`OV_HANDLE_MENU_POSITION_RELATED_TO_WIN_ENABLED`
adds the task offset for freeform). Opening/closing it writes
`Settings.Secure.ov_handle_menu_state`. This is the existing precedent for a
controller element that overlays the app instead of reserving window height.

---

## 3. The reserved strip ("forehead") and how it is created

### 3.1 The ROM applies a forced `captionBar` inset

`WindowDecoration.updateCaptionInsets` (called at the end of every `relayout`)
builds a `WindowDecorationInsets` and applies it through the
`WindowContainerTransaction`:

```java
Rect rect2 = new Rect(rect);                    // rect = task bounds
rect2.bottom = rect2.top + relayoutResult.mCaptionHeight;   // top strip
...
new WindowDecorationInsets(token, mOwner, rect2, scaledFrame, windowingMode,
        taskFrame, boundingRects, insetFlags, mIsInsetSource, mShouldSetAppBounds)
        .update(wct);
```

`WindowDecorationInsets.update` calls

```java
wct.addInsetsSource(token, mOwner, 0, WindowInsets.Type.captionBar(),
        mFrame, mScaledFrame, mWindowingMode, mBoundingRects, mFlags);
wct.addInsetsSource(token, mOwner, 0, WindowInsets.Type.mandatorySystemGestures(),
        mFrame, mBoundingRects, 0);
if (mExcludedFromAppBounds) {
    Rect r = new Rect(mTaskFrame);
    r.top += mFrame.height();
    wct.setAppBounds(token, r);
}
```

`mFlags` comes from `RelayoutParams.mInsetSourceFlags`, set in
`updateRelayoutParams`:

* `DesktopModeFlags.ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION`
  → `|= 4` unless the package is in `OvcWinDecorCtlList.mExcldeCaptionFromAppBoundsPkgList`;
* `DesktopModeFlags.ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS`
  → `|= 16` unless that package/component is listed;
* packages in those lists instead get `mShouldSetAppBounds = true`
  (`setAppBounds` path, the caption is excluded from app bounds).

Measured on the device for a freeform Settings task:

```text
LocalInsetsSources
  InsetsSource type=captionBar frame=[2054,200][3042,298] visible=true
      flags=FORCE_CONSUMING|FORCE_CONSUMING_OPAQUE_CAPTION_BAR sideHint=TOP
  InsetsSource type=mandatorySystemGestures frame=[2054,200][3042,298] ...
```

So the default is: **the app gets a 98 px `captionBar` inset that it is forced to
consume**. The window frame itself stays the full task bounds (the app surface
measured 988×1600), and the app's own decor consumes the inset.

### 3.2 Why it looks like a solid band

`captionBar` is part of `WindowInsets.Type.systemBars()`. Every app that fits
system bars — including Compose apps using `Scaffold` / `WindowInsets.systemBars`
— therefore pads its content down by 98 px. The padded area shows the app's own
`windowBackground` (ZTool: `#303030`; Settings happens to use the same colour as
its content). The caption surface above it is transparent and only draws the
3-dot handle, so the visual result is:

```text
task bounds
┌───────────────────────────────┐  <- app surface starts here (full size)
│  [app windowBackground]  • • •│  98 px = caption height, app content inset
├───────────────────────────────┤  <- app content starts here
│  app content                  │
```

The strip is not painted by the ROM decoration: it is the app's background
showing through the space the forced inset reserved. That is why it is always
solid-colour and why removing it is an *inset* problem, not a drawing problem.

### 3.3 ZUI One Vision gates worth knowing

* `OvcWinDecorUtils.canDoOvCaptionBar(...)` requires
  `OvWindowDecorFeatureFlags.ONE_VISION_CAPTION_BAR_ENABLED` and non-PC mode.
* `OvcWinDecorUtils.getOvCaptionHeightId()` is used for the ZUI pad layout.
* `OvCaptionBarObserver` watches `Settings.Secure.show_ov_top_bar`
  (default 1 on non-commercial channel, 0 on commercial),
  `Settings.Secure.ov_tbar_settings_changed` (`pkg:…` written by Settings),
  `Settings.Secure.ov_user_guide_shown`, `Settings.System.enable_onevision_all_feature`,
  `Settings.System.zui_taskbar_guide_show`; any change triggers a relayout of
  every visible decoration.
* `OvcWinDecorCtlList.mNotSupportTopBarPkgList` /
  `mNotSupportTopBarComponentList` gate the One Vision top bar per app.

Do not try to remove the band by flipping `show_ov_top_bar` or
`enable_onevision_all_feature`: on this build those only gate the fullscreen /
PC-mode caption paths and will not remove the freeform `captionBar` inset.

---

## 4. Resize path details used by the hooks

* `DragResizeInputListener` grants two input channels: one on the decoration
  surface (edge/corner regions, `LayoutParams` flags `8 = NOT_FOCUSABLE`,
  `536870912`), one on a `TaskInputSink` child surface used during drags so the
  app does not receive the gesture.
* `setGeometry` unions `TaskEdges` + `TaskCorners` (+ `TaskPanel` in hanging
  mode) into a `Region` and calls `IWindowSession.updateInputChannel(..., region)`.
* On `ACTION_DOWN`, `ctrlType` is computed and, for the ZUI bottom bar
  (`ctrlType == 8`), `DragResizeInputListener` returns before
  `onDragPositioningStart`; the gesture then goes to
  `OvfDriListenerInjector.handleDragGestureEvent` (fast up = minimize,
  fast down = fullscreen) using `mCtrlType`, `mDragStartY`,
  `mQuickSwipeStartTimeMs`, `SCALED_TOUCH_SLOP`, `SCALED_FLING_VELOCITY`.
* That injector only recognises exactly `8`; the existing hook's `8 | 16` makes
  it fall through to the normal resize positioner.
* `VeiledResizeTaskPositioner` stores `mCtrlType` on start, calls
  `DragPositioningCallbackUtility.changeBounds(mCtrlType, ...)` on move/end, and
  commits through `Transitions.startTransition(6, wct, this)`.
* `OvfDpcUtility` is consulted when `ENABLE_OVF_DRAG_RESIZE` and
  `ovfDpcUtilityCanProcess()`; `triggerResult` bits select fullscreen /
  maximize / minimize / safe-margin animations (`startAnimation` in the
  positioners). This is the object `FreeformFullscreenThresholdHook` rewrites.

---

## 5. Design: overlay the controller instead of reserving height

### 5.1 Target state

```text
today                                  target
┌──────────────────────────────┐       ┌──────────────────────────────┐
│ • • •   (98 px, no app UI)   │       │ • • •  (dots over the app)   │
├──────────────────────────────┤       │                              │
│ app content (inset by 98 px) │       │ app content (full height)    │
│                              │       │                              │
└──────────────────────────────┘       └──────────────────────────────┘
```

* The task bounds do not change. The app keeps the full window height.
* The caption surface stays where it is (988×98 at the top) but becomes a pure
  overlay: only the 50 dp pill is touchable.
* The handle still opens `OvHandleMenu` and still drags the window.

### 5.2 Step 1 — stop forcing the caption inset

Hook `WindowDecoration.updateCaptionInsets(RelayoutParams, WindowContainerTransaction, RelayoutResult, Rect)`
and, for freeform tasks only, remove the existing source and skip the call:

```kotlin
val decorClass   = loader.loadClass("com.android.wm.shell.windowdecor.WindowDecoration")
val paramsClass  = loader.loadClass("com.android.wm.shell.windowdecor.WindowDecoration\$RelayoutParams")
val resultClass  = loader.loadClass("com.android.wm.shell.windowdecor.WindowDecoration\$RelayoutResult")
val insetsClass  = loader.loadClass("com.android.wm.shell.windowdecor.WindowDecoration\$WindowDecorationInsets")

val updateCaptionInsets = findMethod(
    decorClass, "updateCaptionInsets",
    paramsClass, WindowContainerTransaction::class.java, resultClass, Rect::class.java
)
val mTaskInfoField    = findField(decorClass, "mTaskInfo")
val mInsetsField      = findField(decorClass, "mWindowDecorationInsets")
val removeInsets      = insetsClass.getDeclaredMethod("remove", WindowContainerTransaction::class.java)

hookWithId(updateCaptionInsets, "freeform_forehead_overlay") { chain ->
    val decor = chain.thisObject ?: return@hookWithId chain.proceed()
    val wct   = chain.args.getOrNull(1) ?: return@hookWithId chain.proceed()
    if (!isFreeform(decor)) return@hookWithId chain.proceed()
    val task  = mTaskInfoField.get(decor) as ActivityManager.RunningTaskInfo
    // WindowDecorationInsets.remove() skips captionBar outside PC/OV-default mode,
    // so drop both sources directly on the transaction.
    wct.removeInsetsSource(task.token, mOwnerField.get(decor), 0, captionBar())
    wct.removeInsetsSource(task.token, mOwnerField.get(decor), 0, mandatorySystemGestures())
    mInsetsField.set(decor, null)
    null
}
```

(The shipped hook reaches these two calls through reflection: `WindowContainerTransaction`,
`TaskInfo.getWindowingMode()` and `TaskInfo.token` are `@SystemApi` and not visible
to the module at compile time.)

Why skip the call instead of letting it run and removing afterwards: the source
and the `setAppBounds` decision are both built inside that method; skipping it
guarantees neither is (re)applied. `mWindowDecorationInsets` must be nulled so
`releaseViews` / `close` do not remove a stale source twice.

Result: the next WCT drops both `captionBar` and `mandatorySystemGestures`,
WM relayouts the task, the app window keeps its full bounds and its own inset
padding becomes 0.

Notes:

* Also guard `WindowDecoration.addCaptionInset` (used by
  `DesktopTasksController.moveToFreeformFromKeyBoard`, `onToDesktop` and a
  handle-menu click) for freeform, in case a future path re-adds the source.
* Gate on freeform only — **PC mode is included** (user decision). Step 1 drops the
  inset for PC-mode freeform windows as well; step 2 is skipped there because the
  PC caption is a title bar with minimize/maximize/close buttons (§5.3).
* `OvcPcModeUtils.isInZuiDesktopModeStatus()` is **false** for the launcher-desktop
  freeform windows on this device, so it must not be used as the gate; it denotes
  the PC-like "ZUI desktop mode", not "windows floating over the ZUI launcher".
* Apps that ignore insets never showed the band; for them nothing changes except
  the dots now sit on their content.

### 5.3 Step 2 — shrink the caption's touch region to the pill

After step 1 the caption surface still covers the top 98 px and, with a null
region, its input channel is the whole surface — the app's own top bar buttons
would become dead. Restrict it (non-PC mode only; in PC mode the caption is the
`pcmode_window_decor` title bar whose buttons must stay touchable):

Hook `WindowDecoration.updateViewHierarchy(RelayoutParams, RelayoutResult, SurfaceControl.Transaction, Region)`.
When the incoming region is null and the task is freeform, replace it with the
pill rectangle in caption-local coordinates:

```kotlin
val pillW = decor.getResources()   // or a cached px value
    .getDimensionPixelSize(/* ov_window_decor_caption_bar_width_pad = 50dp */)
val result = chain.args[1]
val w = result.mCaptionWidth
val h = result.mCaptionHeight
val pill = Region((w - pillW) / 2, 0, (w + pillW) / 2, h)
chain.proceed(chain.args[0], result, chain.args[2], pill)
```

The region ends up in `SurfaceControlViewHostAdapter.prepareViewHost` →
`WindowDecorWindowlessWindowManager.setTouchRegion(viewHost, region)`, so touches
outside the pill fall through to the app window below. Keep the original region
when the host already computed one (`mLimitTouchRegionToSystemAreas` for
transparent-caption apps) so that path is not broken.

### 5.4 Step 3 — what NOT to change

* Do not hide the caption surface: the handle is the only drag target left.
* Do not touch `mCaptionTopPadding` / `mCaptionHeight`: the surface has to stay
  98 px tall so the dots keep their size and vertical position.
* Do not "compensate" by shrinking the task bounds inside SystemUI. Freeform
  bounds are chosen in system_server (`OvFreeformService` / `OvFreeformManager`,
  `getFreeformNextAvailableBounds`, `queryDefaultFreeformBoundsByPos`; the
  window is 395×640 dp here). Shrinking it from SystemUI would fight
  `DragPositioningCallbackUtility` min sizes and user-driven resizes. After
  step 1 the app simply gains the 39 dp that the controller used to reserve —
  which is exactly "the controller no longer occupies extra height".

### 5.5 Interactions with existing hooks

| Hook | Interaction |
| --- | --- |
| `FreeformBottomEdgeResizeHook` | none — different methods (`calculateCtrlType`, `onDragPositioningEnd`) |
| `FreeformCornerHandleIndicatorHook` | none — border view ids unchanged |
| `FreeformFullscreenThresholdHook` | none — `OvfDpcUtility` / `changeBounds` unchanged |
| Any future caption-bar hook | must run after step 1, since the source is gone |

### 5.6 Risks

1. **App top bar under the dots.** With the inset gone, the pill sits on top of
   the app's own top bar. That is the requested behaviour, but the app's
   controls under the pill (centre of the title bar) become unreachable.
2. **Touch dead zone.** If step 2 is not implemented, the whole 39 dp strip
   swallows touches. Implement both steps together.
3. **Transparent-caption apps** (`TaskInfoKt.isTransparentCaptionBarAppearance`)
   already request `mLimitTouchRegionToSystemAreas`; preserve that region.
4. **Inset consumers.** Desktop-mode-aware apps that draw their own title bar
   from `captionBar` insets will draw it into the window top, under the dots.
5. **PC-mode title bar overlays the app.** PC mode keeps its
   `pcmode_window_decor` title bar (step 2 skipped), so the title bar now floats
   over the app's top ~40 dp instead of the app being inset. Apps whose top bar
   carries controls in that strip will be partly covered.
6. **OTA drift.** Class/method names (`WindowDecoration`, `updateCaptionInsets`,
   `mWindowDecorationInsets`, `remove`) are obfuscation-sensitive; fail closed
   (no hook installed, log a warning) like the other SystemUI hooks.
7. **Hot reload.** Resolve classes/fields in `handleLoadPackage`, never inside the
   callback, and capture them (project rule).

### 5.7 Verification plan

```powershell
# 1. freeform task, expect no captionBar source afterwards
adb shell dumpsys activity activities | Select-String "mode=freeform" -Context 0,8

# 2. caption surface still present, still 988x98 at the window top
adb shell dumpsys SurfaceFlinger | Select-String "Caption of Task=" -Context 0,4

# 3. app surface starts at the task top; sample pixels instead of eyeballing
adb exec-out screencap -p > $env:TEMP\ff.png   # compare row task.top+10 with task.top+120

# 4. input: tap the app's back button (outside the pill) and the pill
adb shell input tap <x> <y>
adb logcat -s WindowDecoration OvfDriListenerInjector VeiledResizeTaskPositioner
```

Pass criteria: the pixel row at `task.top + 10` matches the app content (not a
distinct band), the app's top bar receives taps outside the pill, dragging the
pill moves the window, and tapping the pill opens the handle menu.

### 5.8 Result on TB710FU (2026-10-18, test hook `FreeformForeheadOverlayHook`)

| Check | Before | After |
| --- | --- | --- |
| Task `LocalInsetsSources` | `2` (`captionBar` + `mandatorySystemGestures`, `FORCE_CONSUMING`) | none printed (0) |
| Caption input window `touchableRegion` | `[1431,2485][1529,3042]` (98×988 strip) | `[1431,2485][1529,2610]` (98×125 = the 50 dp pill) |
| Pixels at `task.top+10..+90` | `#303030` band | `#0f1416` app background, dots overlay |
| Pill drag | moves window | moves window (bounds 200 → 471) |
| Pill tap | opens handle menu | opens handle menu, positioned under the caption |

So the app now owns the full window height, the strip is gone, and only the pill
keeps its input channel.

PC-mode freeform windows take step 1 but not step 2. On TB710FU
`OvcPcModeUtils.isUnderPcMode()` is **false** (PC mode needs an external
display / keyboard setup), so that branch is not covered by the table above; the
first PC-mode window logs `pcMode=true` in the drop message for confirmation.

---

## 6. Open items

1. Confirm which layout and which `RelayoutParams` values produce the caption on
   this build (`mLayoutResId`, `mCaptionWidthId`, `mCaptionHeightId`,
   `mCaptionTopPadding`) — see §2.2. One `relayout` log closes it.
2. Confirm whether `OvcWinDecorCtlList.mExcldeCaptionFromAppBounds*` contains
   ZTool/Settings; those packages take the `setAppBounds` path instead of the
   forced inset and would need the same treatment in step 1.
3. Scope: every freeform window, **PC mode included** (user decision). Step 1 runs
   for both; step 2 only outside PC mode because the PC caption is a title bar
   with buttons. `isInZuiDesktopModeStatus()` is false on the observed
   launcher-desktop windows, so it is not usable as a gate.
4. Test hook `FreeformForeheadOverlayHook` (`hook_test`) implements steps 1–2 and
   is verified on TB710FU for non-PC freeform windows (§5.8). The PC branch is
   compiled but not observed: `isUnderPcMode()` is false on this device, and PC
   mode requires an external display / keyboard. Remaining work: turn the test
   hook into a preference-backed module (scope key, switch + search entry) before
   removing `hook_test`.
5. The separate ZUI "TopBar" (`Settings.Secure.show_ov_top_bar`,
   `IOvFreeformService.supportsOvTopBar/enableOvTopBar`) lives in system_server
   and is not in the SystemUI APK; it is gated by `OvCaptionBarObserver` for the
   fullscreen caption only. If a per-app top bar ever draws its own region,
   that code is outside this document's scope.

---

## 7. Class / method index

| Symbol | Purpose |
| --- | --- |
| `WindowDecoration.relayout(RelayoutParams, Transaction, Transaction, WCT, View, RelayoutResult)` | lays out caption surface, border, insets |
| `WindowDecoration.updateCaptionInsets(...)` | adds/removes `captionBar` + `mandatorySystemGestures` sources |
| `WindowDecoration.addCaptionInset(WCT)` | one-shot caption inset for fullscreen/PC paths |
| `WindowDecoration$WindowDecorationInsets.update/remove` | applies/removes the sources and `setAppBounds` |
| `WindowDecoration.updateViewHierarchy(...)` | caption `LayoutParams` + touch region + `Caption of Task=` title |
| `DesktopModeWindowDecoration.updateRelayoutParams(...)` | chooses layout, caption size, inset flags |
| `DesktopModeWindowDecoration.relayout(...)` | ZUI freeform relayout, `updateViewsAndSurfaces_Ovf` |
| `OvWindowDecorationInjector.updateViewsAndSurfaces_Ovf(...)` | border view host, bottom gesture handles |
| `OvWindowDecorationInjector.updateCaptionHandleResource(...)` | focused/none-focused/pressed handle drawables |
| `OvcWinDecorUtils` | `canDoOvCaptionBar`, `isOvDefaultMode`, `getOvCaptionHeightId`, `isProportionalScaleFreeForm` |
| `OvcWinDecorCtlList` | top-bar / spy / caption-from-app-bounds package lists |
| `OvCaptionBarObserver` | Secure/System setting observers that relayout decorations |
| `DragResizeWindowGeometry.calculateCtrlType/calculateEdgeResizeCtrlType` | ctrlType mask |
| `DragResizeInputListener` | input channels, geometry union, drag dispatch |
| `OvfDriListenerInjector` | bottom-bar quick swipe (requires ctrlType == 8) |
| `VeiledResizeTaskPositioner` / `OvfScaleTaskPositionerDecorator` | resize/reposition, veil, transitions |
| `DragPositioningCallbackUtility.changeBounds` | bounds math consumed by `FreeformFullscreenThresholdHook` |
| `OvfDpcUtility` | resize trigger state machine |
| `OvHandleMenu` | handle menu overlay (`ov_desktop_mode_window_decor_handle_menu_pad`) |

Layouts: `ov_desktop_mode_window_decor_pad`, `ovc_window_decor_border`,
`ovc_window_decor_user_guide`, `ov_desktop_mode_window_decor_handle_menu_pad`,
`zui_desktop_window_decor_freeform`, `caption_window_decor`.

Drawables: `ovc_handle_menu_{focused,none_focused,pressed}_{light,night}`
(50×39 dp, 3 dots), `ovc_wdb_bottom_gesture_{left,right,handle}`,
`ovc_wd_border_line`.
