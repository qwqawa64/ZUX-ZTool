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
is dismissed. Only the initial-request branch (b) and the "already started" case reach (d).
This is why the module exists at all.

## 3. Why the old hook only showed the tail of the animation

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
`false`. `animateScreenStateChange` then skips the whole `target == STATE_ON` body. On the
next update (message `what = 2` from `ScreenOnUnblocker.onScreenOn()`:
`unblockScreenOn(); updatePowerState();`) `setScreenState` returns `true` and branch (c)
dismisses the fade.

The old hook returned `null` instead of calling `chain.proceed()` on the first screen-on
call, so `setScreenState(STATE_ON, ...)` never ran at all: the panel stayed off (the
backlight is forced off while `mScreenState == STATE_OFF`) while the animator faded the
ColorFade surface. When the animator ended, `onAnimationEnd -> sendUpdatePowerState()`
re-entered the method, the level was already `1.0f`, the hook stepped aside, the panel
finally unblanked and the fade was dismissed — hence "only the end of the animation is
visible".

## 4. What the module does instead

Start the ON animator **before** calling `chain.proceed()`. Branch (c) then sees
`isStarted() == true` and is skipped, so the host keeps the prepared fade; the original
screen-on code unblanks the panel (backlight still off at level 0) and returns at (d)'s
`if (onAnimator.isStarted()) return;`.

Guards, all read before proceeding:

| Guard | Reason |
| --- | --- |
| `mReportedScreenStateToPolicy` is `0`/`-1` | This call is the blocking one; starting the fade here burns the WindowManager draw wait out of the animation. |
| `mPendingScreenOnUnblocker != null` | The panel is still blocked; wait for the unblock message. |
| screen state is `DOZE`/`ON_SUSPEND`/`DOZE_SUSPEND` | Doze exit runs the "blanks after doze" pre-step; keep the stock ordering. |

The start itself is only attempted when the fade is pending (`mColorFadePrepared`, level `< 1`,
animator not started). Calling `prepareColorFade` again inside the original is harmless:
`ColorFade.createSurfaceControl` returns early when `mSurfaceControl != null` (it only
re-applies `setSecure`).

### Fallback: rebuilding a fade that the host dropped

Branch (a) dismisses the fade too (`!readyForDisplay || !mColorFadeEnabled || !isBrightOrDim`),
and it does so **even while the animator is running**, destroying the surface the animator draws
into. The hook therefore re-checks `mColorFadePrepared` after `chain.proceed()` and, when a fade
that was live before the call is gone, rebuilds it:

```text
prepareColorFade(mContext, mode) -> setColorFadeLevel(level) -> startColorFadeAnimator(...)
```

This is safe inside the same handler message: `DisplayPowerState` posts its screen update and
ColorFade draw to a `Handler`/`Choreographer` on the DisplayPowerController thread, so both the
panel unblank and the first ColorFade frame still observe the rebuilt level.

`ColorFade.draw(level)` in mode `2` is `showSurface(1.0f - level)`: a solid color layer whose
alpha is `1 - level`, so level `0` is opaque black and level `1` is transparent.

## 5. Open questions

- Does the ROM drop the screen-on fade only through branch (c), or also through branch (a)?
- Which caller dismisses a live fade on this device. The `color_fade_dismiss` hook logs that
  caller frame, and the screen-on log line dumps `reported`, `prepared`, `level`, animator
  state, `brightOrDim`, `r4Occluded` and `readyForDisplay` for every screen-on call.
