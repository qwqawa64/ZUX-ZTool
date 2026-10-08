# Screen On/Off Color Fade Animation (services.jar)

Reverse-engineering notes for `ForceScreenOnOffAnimation`
(`hook/modules/systemframework/ForceScreenOnOffAnimation.kt`), taken from the ZUI
`services.jar` decompiled in JADX. Target classes:
`com.android.server.display.DisplayPowerController`,
`com.android.server.display.DisplayPowerState`,
`com.android.server.display.ColorFade`.

## 1. Where the fade lives

- `DisplayPowerController.mColorFadeOnAnimator` / `mColorFadeOffAnimator` are created in
  `DisplayPowerController.initialize(int)` **only when `mColorFadeEnabled` is true**.
  Both are `ObjectAnimator.ofFloat(mPowerState, COLOR_FADE_LEVEL, ...)`:
  on `0.0f -> 1.0f`, off `1.0f -> 0.0f`. `COLOR_FADE_LEVEL` writes through
  `DisplayPowerState.setColorFadeLevel(float)`.
- `mColorFadeOnDurations` / `mColorFadeOffDurations` are **static** ints on
  `DisplayPowerController`, read when the animators are created. The module overwrites both
  animator durations in the `initialize` hook so a runtime preference change applies.
- `DisplayPowerState.mColorFadeLevel` is double-purposed: besides driving the ColorFade
  surface matrix, `PhotonicModulator`/`mScreenUpdateRunnable` passes backlight `-1.0f`
  (off) whenever `mScreenState == STATE_OFF || mColorFadeLevel <= 0.0f`. So a level of `0`
  means "panel state on, backlight off" — that is what makes the fade start from black
  instead of a flash of content.

## 2. The stock screen-on path (`animateScreenStateChange(int,int,boolean,boolean,boolean)`)

```text
if (mColorFadeEnabled && (onAnimator.isStarted() || offAnimator.isStarted())) {
    if (target != STATE_ON) return;      // a fade in flight swallows other transitions
    mPendingScreenOff = false;
}
... doze "blanks after doze" pre-step, mPendingScreenOff pre-step ...
if (target == STATE_ON) {
    if (setScreenState(STATE_ON, reason)) {          // (a)
        ready = MotoDesktopManager.isReadyForDisplay(...)
        if (!ready || !mColorFadeEnabled || !mPowerRequest.isBrightOrDim()) {
            setColorFadeLevel(1.0f); dismissColorFade(); return;   // no fade at all
        }
        if (onAnimator != null) {
            if (isInitialState) {                    // first request after initialize()
                setColorFadeLevel(0.0f);
                onAnimator.setDuration(2000);        // (b) 2 s hold, values {0,0}
                onAnimator.setFloatValues(0f, 0f);
            } else if (!onAnimator.isStarted() || mR4BootAnimOccluded) {
                Slog "Fade on animation already run or R4 animation occluded.";
                setColorFadeLevel(1.0f); dismissColorFade(); return;   // (c) kills the fade
            }
        }
        if (getColorFadeLevel() == 1.0f) { dismissColorFade(); return; }
        if (prepareColorFade(mContext, mode)) {
            if (onAnimator.isStarted()) return;
            Slog "Start fade on animation.";
            onAnimator.start();                      // (d) the real start
            return;
        }
        onAnimator.end();
    }
    return;
}
```

Branch (c) means **the stock ROM never animates the screen-on fade**: on a normal
off -> on transition the animator is not running yet, so the branch is taken and the fade
is dismissed. Branch (a) reaches the same outcome on this device because
`MotoDesktopManager.isReadyForDisplay` is always `false`. Only the initial-request branch (b)
and the "already started" case reach (d). This is why the module exists at all.

Note that branch (a) dismisses **regardless of whether the animator is running**, so
starting the animator before the state change does not save the fade here.

## 3. Why the panel stayed dark

`setScreenState(STATE_ON, reason)` is not a no-op: it applies
`mPowerState.setScreenState(STATE_ON, ...)`, then

```text
if (mReportedScreenStateToPolicy == 0 || == -1) {
    setReportedScreenState(1);
    if (getColorFadeLevel() == 0.0f) blockScreenOn(); else unblockScreenOn();
    mWindowManagerPolicy.screenTurningOn(displayId, mPendingScreenOnUnblocker);
}
return mPendingScreenOnUnblocker == null && mPendingScreenOnUnblockerByDisplayOffload == null;
```

While the panel is being unblanked the fade level is `0`, so the call **blocks** and returns
`false`; `animateScreenStateChange` then skips the whole `target == STATE_ON` body. On the
next update (message `what = 2` from `ScreenOnUnblocker.onScreenOn()`:
`unblockScreenOn(); updatePowerState();`) `setScreenState` returns `true` and the fade is
dismissed.

The original hook returned `null` instead of calling `chain.proceed()` on the first screen-on
call, so `setScreenState(STATE_ON, ...)` never ran at all: the panel stayed off (the
backlight is forced off while `mScreenState == STATE_OFF`) while the animator faded the
ColorFade surface. When the animator ended, `onAnimationEnd -> sendUpdatePowerState()`
re-entered the method, the level was already `1.0f`, the hook stepped aside, the panel
finally unblanked and the fade was dismissed — hence "only the end of the animation is
visible".

## 4. What the module does instead

Only one intervention remains, plus the two hooks that make the fade exist at all:

1. `DisplayPowerController.<init>` — force `mColorFadeEnabled` and `mColorFadeFadesConfig`
   to `true` after the constructor returns, so `initialize()` creates the animators and the
   fade uses the mode-`2` color layer.
2. `DisplayPowerController.initialize` — overwrite both animator durations with the
   configured value (`mColorFadeOnDurations`/`mColorFadeOffDurations` are static and only
   read when the animators are created).
3. `DisplayPowerController.animateScreenStateChange` — let the host run, then rebuild the
   fade it dropped:

```text
level = getColorFadeLevel()             # before proceed()
preparedBefore = mColorFadePrepared
proceed()
if (preparedBefore && level < 1 && !mColorFadePrepared && getScreenState() == STATE_ON) {
    prepareColorFade(mContext, 2)
    setColorFadeLevel(level)
    onAnimator.setDuration(preference)
    onAnimator.setFloatValues(level, 1.0f)
    onAnimator.start()
}
```

Rebuilding is safe inside the same handler message: `DisplayPowerState` posts its screen
update and ColorFade draw to a `Handler`/`Choreographer` on the DisplayPowerController
thread, so both the panel unblank and the first ColorFade frame already observe the rebuilt
level. `ColorFade.draw(level)` in mode `2` is `showSurface(1.0f - level)` — a solid color
layer with alpha `1 - level`, i.e. level `0` is opaque black and level `1` is transparent.

### Dropped along the way

- **`DisplayPowerController$Injector.isColorFadeEnabled` hook** — its only caller is the
  DisplayPowerController constructor (line 1819 of the smali, stored into `mColorFadeEnabled`
  at 1837), and hook 1 above already writes that field. Verified working: the module logged
  `fadeEnabled=true` while reading the field from `animateScreenStateChange`.
- **Starting the ON animator before `chain.proceed()`** — useless on this ROM, see section 2.
- **The `dismissColorFade` diagnostic hook** — the dismiss is always preceded by
  `setColorFadeLevel(1.0f)`, so a "live fade" test on the level never fires on this ROM.

### Rejected: forcing `MotoDesktopManager.isReadyForDisplay` to `true`

That flag means "this display is the target of a Ready For (Lenovo/Motorola cross-screen
projection, i.e. desktop mode) session", which is why it is `false` on a tablet's own panel.
Forcing it would falsely advertise the local display as a projection target to every caller
(the static method is referenced from 39 classes, mostly WindowManager/input/audio), so the
"fast path" it would unlock in `ColorFade.prepare(Context, int)` is not worth the risk.

The cost of leaving it alone is one wasted `ScreenCapture` per screen-on: for mode `2`
`ColorFade.prepare` takes the screenshot branch, throws the buffer away (`if (mMode == 2)
return true;`) and only needs `createSurfaceControl`. It measures about 75 ms and runs before
the queued unblank is applied, so it delays the start of the fade but never shows content.


## 5. Confirmed behaviour (ZUX, `readyForDisplay=false`)

Measured log of one screen-on with a 1000 ms preference. Three `animateScreenStateChange(2, …)`
calls happen before a fade can start:

| When | `reported` | `unblocker` | `displayState` | `level` | What happens |
| --- | --- | --- | --- | --- | --- |
| +0 ms | `0` | `false` | `1` | `0.0` | `setScreenState(STATE_ON, …)` applies the state and calls `blockScreenOn()`; it returns `false`, so the host skips its whole ON body and no fade is dropped. |
| +250 ms | `1` | `true` | `2` | `0.0` | still blocked; `setScreenState` returns `false` again for the same reason. |
| +251 ms | `1` | `false` | `2` | `0.0` | unblocked: the host reaches its ON body, branch (a) fires (`readyForDisplay=false`) and dismisses the prepared fade; the module rebuilds it here. |
| +1077 ms | `1` | `false` | `2` | `1.0` | animation finished (1000 ms preference); the host dismisses the finished fade itself. |

The two blocked calls never reach a dismiss, which is why no start-timing guards are needed:
the rebuild only triggers on the call that actually dropped a live fade.
