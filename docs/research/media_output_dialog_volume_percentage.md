# ZUI MediaOutputDialog volume percentage vs. the control-center slider label

Reverse-engineered from the ZUI `SystemUI.apk` loaded in the JADX MCP (the APK that
contains `com.android.systemui.settings.ToggleSliderView` and `zui.widget.SeekBarNps`).

Scope: how `MediaOutputDialog` computes the number it shows in `R.id.volume_value`, how
`VolumeSliderPercentageHook` computes its own label, and what has to change for the two
to agree.

Entry point suggested by the user, confirmed as the right one:
`res/layout/media_output_list_item_advanced.xml` → `@+id/volume_value` (`TextView`,
`visibility="gone"`) next to `@+id/volume_seekbar`
(`com.android.systemui.media.dialog.MediaOutputSeekbar`).

---

## 1. The dialog's formula (authoritative)

`MediaOutputAdapterLegacy$MediaDeviceViewHolderLegacy` binds the row
(`mVolumeValueText = view.requireViewById(R.id.volume_value)`), and there are exactly two
writers of that text — both the same expression:

```java
// MediaOutputAdapterLegacy$MediaDeviceViewHolderLegacy$MediaSeekBarChangedListener#onProgressChanged
mVolumeValueText.setText(resources.getString(
    R.string.media_output_dialog_volume_percentage,
    (int) ( (double) ((seekBar.getProgress() / 1000) * 1000) * 100.0d / (double) seekBar.getMax() )));

// MediaOutputSeekbar$1#onProgressChanged  (accessibility path, same expression)
setStateDescription(resources.getString(R.string.media_output_dialog_volume_percentage, /* same */));
```

`R.string.media_output_dialog_volume_percentage` is `%1$d%%` (verified in
`res\values\strings.xml`), i.e. the dialog prints exactly `N%` — the same shape as the
hook's label, so only the number is at issue.

Inputs (all from `MediaOutputAdapterLegacy#onBindViewHolder`):

| Value | Source |
|---|---|
| `seekBar.max` | `routeInfo.getVolumeMax() * 1000` (`MediaRoute2Info`, 0 when route info is null) |
| `seekBar.progress` | `mediaDevice.getCurrentVolume() * 1000` (`initializeSeekbarVolume` → `setProgress(i * 1000, true)`) |
| `MediaDevice.getCurrentVolume()` | `mRouteInfo.getVolume()` (settingslib `MediaDevice`, not overridden by `PhoneMediaDevice`) |
| drag → applied volume | `level = i / 1000` → `SeekBarVolumeControl.setVolume(level)` → `InfoMediaManager.setRouteVolume(routeInfo, level)` → `MediaRouter2.setRouteVolume` |

Consequences:

- In exact arithmetic the dialog's number is `trunc(level * 100 / routeVolumeMax)`.
  `(int)` of a non-negative `double` truncates (floors), it does **not** round.
- `(progress / 1000) * 1000` re-quantizes the raw drag position onto whole volume steps
  before dividing, and the apply path uses the same `i / 1000` floor. So the dialog's
  percentage always describes the **applied, whole-step volume**, never the finger
  position.
- The text is only visible while dragging (`setVisibility(0)` in
  `onStartTrackingTouch`, `8` in `onStopTrackingTouch`). A route whose
  `getVolumeHandling() == 0` disables the seekbar entirely, so it never shows a number.
- For the local/active playback route, route volume/volumeMax mirror
  `AudioManager.getStreamVolume/getStreamMaxVolume(STREAM_MUSIC)` — the stock formula can
  only produce sane UI (0..100%) under that assumption, since a route reporting volume
  0..15 against max 100 would read 1..15%. **Not independently verifiable from this APK**
  (the route info is built in the framework, `services.jar`); verify on device if a
  mismatch survives the fixes below.

## 2. What the hook computes today

`VolumeSliderPercentageHook.getVolumeProgress()` / `formatPercent()`:

```kotlin
var percent = (((progress - min) * 100f) / (max - min)).roundToInt()   // min = 0, half-up
```

with `progress` / `min` / `max` read straight off `ToggleSliderView.mMediaVolumeSlider`.
ZUI's own raw↔step conversions around that slider (ZUI `ToggleSliderView`):

| Stock code | Effect |
|---|---|
| ctor: `mMediaVolumeSlider.setMax(150000); setMin(0)` | 15 stock steps × 10000 raw units |
| `setVolumeProgress(level)` → `setProgress(level * 10000)` | slider position is step-aligned once settled |
| `getVolumeProgressReal$1()` → `ceil(progress / 10000)` | raw progress → stream level (**ceil**, not floor) |
| `ToggleSliderView$2.onProgressChanged` (user drag) → `setStreamVolume(3, ceil(progress/10000), 0)` | the applied level is the ceil'd one |
| `SeekBarNps.onTouchEvent` → `clamp(start + (int)(dx * max/width))` | raw progress is **not** quantized while dragging |

`FineVolumeStepsSliderHook` re-asserts `max = getStreamMaxVolume(3) * 10000` after the
`FineVolumeSteps` system hook raises the stream max to 150, so `max / 10000` is the stream
max level **only while that realignment is active**.

## 3. Why the two numbers differ

1. **Rounding rule.** Dialog truncates, hook rounds half-up. At 15 steps the two disagree
   by one point at `level = 1, 4, 7, 10, 13` (dialog 6/26/46/66/86, hook 7/27/47/67/87).
   At 150 steps every `level ≡ 1 (mod 3)` differs.
2. **Quantization.** The dialog describes the applied step; the hook describes the raw
   slider position, and ZUI applies `ceil(progress / 10000)`. At 15 steps a drag to raw
   `145000` is level 15 → dialog `100%`, hook `97%`; raw `140001` is also level 15 →
   dialog `100%`, hook `93%`. Mid-drag the hook can be several points off from both the
   dialog and the volume actually in effect.
3. **Source of truth / range.** The dialog reads the *route* (`getCurrentVolume()`,
   `getVolumeMax()`); the hook reads the SeekBar's own `progress`/`max`. When
   `FineVolumeSteps` is on but `FineVolumeStepsSliderHook` has not run (SystemUI not
   restarted, scope not applied), the slider max stays 150000: the bar tops out at level
   15 and the hook saturates at `100%` from there, while the dialog keeps climbing
   (level 30/150 → `20%`). Fixing the percentage formula cannot hide a stale slider range
   — that is the realign hook's job.
4. **Mute quirk.** `ToggleSliderView.setVolumeProgress()` restores
   `Settings.System "record_volume_progress"` (a *raw* value) while `muteswitch` is set,
   so the bar — and therefore the hook's label — can sit at a non-step position while the
   stream (and the dialog's route volume) is 0.

## 4. How to make them agree

**Option A — read the same source as the dialog (exact for the active route).** The hook's
callbacks run after `chain.proceed()`, so during a drag the stream already holds the
ceil'd level:

```kotlin
val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val level = am.getStreamVolume(AudioManager.STREAM_MUSIC)
val maxLevel = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
val percent = if (maxLevel <= 0) 0 else (level * 100) / maxLevel   // int division == the dialog's (int) cast
```

Immune to causes 2, 3 and 4. Downside: the label follows the applied step, not the finger
(which is exactly what the dialog does).

**Option B — keep reading the slider, quantize like ZUI, truncate like the dialog.**

```kotlin
val level = ceil(progress / 10000f).toInt()           // == ToggleSliderView.getVolumeProgressReal$1
val maxLevel = (slider.max / 10000).coerceAtLeast(1)  // == stream max only if FineVolumeStepsSliderHook ran
val percent = (level * 100 / maxLevel).coerceIn(0, 100)
```

Still disagrees with the dialog when the slider range is stale (cause 3); keep the
`FineVolumeStepsSliderHook` realignment as the precondition.

Both options only need `getVolumeProgress()` (and the now-redundant `formatPercent()`
re-scaling) changed; the call sites (`refreshVolumeLabel`, `refreshVolumeFromToggleSlider`,
`on_progress_changed`) can stay as they are.

## 5. On-device verification checklist

1. Open the media output dialog, drag the row of the active device, and compare with the
   QS label at levels 1/15, 4/15, 7/15 (the levels where the two formulas disagree most).
2. Log `mMediaVolumeSlider.max` and `AudioManager.getStreamMaxVolume(3)`: they must be
   `streamMax * 10000` (1500000 with 150 steps). If not, the realign hook is not active.
3. Log the active route's `MediaRoute2Info.getVolume()/getVolumeMax()`
   (`InfoMediaManager` already logs `route : ..., volume : ...` under
   `log.tag.InfoMediaManager=DEBUG`) to confirm the local-route assumption in §1.
