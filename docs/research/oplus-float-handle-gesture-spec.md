# Oplus FloatHandle (Edge Bubble) Gesture Spec

Reverse-engineered from OnePlus (ColorOS) `oplus-services.jar` `classes3.dex` for the
ZTool edge-bubble replication on Lenovo ZUX. Source classes (jadx, deobfuscated names):

- `com.android.server.wm.FloatHandleController` — state/config singleton
- `com.android.server.wm.floathandle.FloatHandleView` — bubble UI + all gesture handling
- `com.android.server.wm.floathandle.FloatHandleViewManager` — window add/remove
- `com.android.server.wm.OplusZoomParameter.FloatHandleUIParams` — RUS-overridable dp defaults

## Mode model (FloatHandleInfo.mCurrentMode)

| Value | Name | Meaning | Alpha (`computeAlphaByMode`) |
|---|---|---|---|
| 1 | MODE_NONE | hidden | 0.0 |
| 2 | MODE_FULL | full pill visible | 1.0 |
| 4 | MODE_HALF | half-hidden sliver | 0.5 |
| 8 | (hidden variant) | — | 0.0 |
| 16 | MODE_FREE_STATE | freely dragged | 1.0 |

Side: `0 = LEFT`, `1 = RIGHT` (`FloatHandleInfo.LEFT_SIDE/RIGHT_SIDE`).

## Timing constants

| Constant | Value | Source |
|---|---|---|
| Long-press timer (msg 5, arms drag) | 400 ms | `FloatHandleView.processTouchEvent` ACTION_DOWN `sendMessageAtTime(downTime + 400)` |
| Half-hidden delay after release | 50 ms | `processTouchEvent` ACTION_UP → `postDelayMsgToHalfHiddenState(50)` |
| Full→half after mode-settle | 500 ms | `postFullToHalfAnimMsg()` default |
| Full→half in edit mode | 5000 ms | `processTouchEvent` ACTION_UP edit branch |
| Multi-icon fling → FULL then re-hide | 3000 ms | `onFling` `switchToNewMode(2, 3000L)` |
| Multi-icon fling → HALF | 50 ms | `onFling` `switchToNewMode(4, 50L)` |
| Relaunch delay | 200 ms | `FloatHandleController.RELAUNCH_DELAY` |
| Window relaunch after config change | 200 ms | `onConfigurationChange` postDelayed |

## Gesture thresholds

| Threshold | Value | Source |
|---|---|---|
| Tap/drag discrimination | `ViewConfiguration.getTouchSlop()²` | `loadResource` → `mTouchSlopSquare` |
| Long-press → free-drag commitment | 24 dp squared (`dip2px(density, 24f)²`) | `mToFreeStateTouchSlopSquare`; checked in MOVE: `dx²+dy² >= slop²` while `mInLongPressEdit` → `handleLongPressToDragFreeState()` |
| Fling velocity threshold | **1000 px/s**, prop `debug.zoom.flingX` | `mVelocityOfFling` field default |
| Fling distance threshold | **100 px**, prop `debug.zoom.distanceX` | `mDistanceOfFling` field default |
| Max bubbles | 2 (5 when panorama-work feature on) | `mLimitMaxFloatNum` |

Fling acceptance rule (`onFling`) — two OR'ed branches (fast fling, or slow long-distance fling):

```
fast : velocity > V   && distance > D
slow : velocity > (V + 0.5) / 5 && distance > D * 5
```

where `distance = |rawX(end) - rawX(start)|`, `velocity = |velocityX|`.
- 1 icon → restore freeform (`startFlexibleFreeformFromHandleView`)
- >1 icons → toggle between FULL (3000 ms auto re-hide) and HALF (50 ms)

Touch flow (`processTouchEvent`):
- DOWN: clear timers, record down pos/window translation, start 400 ms long-press msg, `mAlwaysInTapRegion = true`
- MOVE: once `dx²+dy² > touchSlop²`, leave tap region and route to `doScrollHandleView`; if long-press edit armed and distance ≥ 24 dp slop → enter FREE_STATE drag
- UP/CANCEL: if dragging → `handleGlobalMovingEventUp()` (spring to edge); else if single icon or FLAG_ADD_FLOAT → half-hidden after 50 ms; if still MODE_FULL and not animating → `startSwitchModeAnimation(2, 4)` (full→half)

## Snap position math (`computeCurrentPosition(mode)`)

```
inset = mode == HALF(4) ? maxDistanceToScreenInHalfHidden
      : mode == FULL(2) ? handleViewScreenMargin
      : handleViewContainerWidth                       // FREE_STATE / NONE

x = side == LEFT  ? -inset
  : side == RIGHT ? screenWidth + inset - handleViewContainerWidth

y = clamp(screenHeight * scaleY - containerHeight / 2,
          movingEdgeLimit,                     // top limit
          getBottomMaxLimitArea(screenHeight) - containerHeight)
```

- `maxDistanceToScreenInHalfHidden = handleViewContainerWidth - handleViewCollapseWidth`
  (recomputed in `loadResource`; how much sliver stays visible in half-hidden)
- Left side anchors window offscreen at `-inset`; right side mirrors.
- `scaleY` is the remembered vertical position ratio (0..1 of screen height), persisted
  via `setRememberPoint` / `FloatHandleInfo.setScaleY`; side flips when remembered x
  crosses screen midline (`addFloatHandle`).

## Bubble sizing (dp, RUS-overridable defaults from `FloatHandleUIParams`)

| Param | dp |
|---|---|
| `mHandleViewCornerRadius` | 16 |
| `mMarginInnerFloatHandle` (icon inset) | 6 |
| `mHandleViewIconWidth` / `Height` | 40 / 40 |
| `mHandleViewCollapseWidth` (half-hidden exposed width) | 16 |
| `mExpandIconMarginTop` | 8 |
| `mStackingIconMarginTop` | 3 |
| Delete icon oversize | +10 |

Derived in `loadResource`:
- `containerHeight = iconHeight + 2 * marginInner` (→ 52 dp)
- `handleViewContainerWidth = iconWidth + 2 * marginInner + screenMargin`
- `handleIconItemViewWidth = iconWidth + 10`

Unresolved raw dp (oplus framework-res dimens, numeric IDs seen in `loadResource`;
resolve from a OnePlus framework-res when the device is reconnected):
`201654523` maxDistanceToScreenInHalfHidden (superseded by derived value),
`201654537` maxDistanceToScreenInNone, `201654527` distanceToRebound,
`201654524` movingEdgeLimit, `201654525` distanceOfFling (superseded by prop default
100 px), `201654546` handleViewScreenMargin.

## Attach-to-edge spring (FREE_STATE release → `handleGlobalMovingEventUp`)

- `SpringForce(finalPosition = 1.0)`, `dampingRatio = getDampingRatioFromBounce(0.22f)`,
  `stiffness = FlexibleAnimationParamsUtils.RESPONSE_0_6_TO_STIFFNESS` (≈ response 0.6 s),
  `minimumVisibleChange = 0.002`
- Drives window translation from current pos to `computeCurrentPosition(HALF)`;
  parallel scale spring animates FREE_STATE(16) → HALF(4) icon size.

## Window params (`FloatHandleView.initWindowParams`)

- `type = ComplexSceneConstants.TYPE_SYSTEM_EDGE_PANEL` (=2314 as seen in dumpsys)
- flags `|= 0x1001828`: NOT_FOCUSABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN | LAYOUT_NO_LIMITS
- `privateFlags = 0x10`, `setFitInsetsTypes(0)`, `layoutInDisplayCutoutMode = shortEdges`,
  `width/height = MATCH_PARENT`, `gravity = TOP|START`, `format = TRANSLUCENT`
- title `OplusOSZoomFloatHandleView#<taskId>`; touch region shrunk via
  `onComputeInternalInsets` / `getTouchableRegion`

## State-behaviour extras worth copying

- Window never moves via LayoutParams during drag; the container `translationX/Y` moves,
  so the full-screen window stays and only the drawn capsule travels.
- `addFloatHandle`: dedup by taskId, forbidden list (`SafeControl`), FIFO eviction at
  `mLimitMaxFloatNum` — evicted task is reset to fullscreen (`resetFlexibleTaskToFullScreen`).
- Bubble hidden in zen mode / child mode / when its package is foreground
  (`updateFloatHandleInSpecMode` → `hideFloatHandleView`).
- Avoid regions: game bar (`AVOID_REGION_GAMEBAR`, observed via
  `IOplusWindowStateObserver`) and smart sidebar (`AVOID_REGION_SIDEBAR`, from
  Secure setting `SIDEBAR_SCENE_REGION`, 4 ints) push the bubble away
  (`avoidBarAdjustRect` / `avoidTargetBar`).
- Rotation/density change resets remember-point and relaunches the bubble window
  after 200 ms.
