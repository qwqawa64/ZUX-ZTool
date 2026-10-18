# Default Writing-Feedback Waveform Kind

Which vibration waveform the pen plays for the *default* writing feedback, and how far
the tablet side can change it. Companion to `haptic-chain.md` (vibration strength).
Sources: `com.lenovo.penservice` 1.8.220260331 (Jadx) and the ZUI 15
`/system/framework/services.jar` (`classes3.dex`, dexdump + Jadx).

## Verdict

Yes. The pen app has a "默认书写效果" (default writing effect) selector that writes
`Settings.Global pen_haptic_brush`; `system_server` observes that key and forwards the
value verbatim as the haptic id of the default continuous feedback. Four kinds are
offered in the UI, five more `_NS` variants become reachable through the 书写声音
(writing sound) switch, and id 35 (eraser) is accepted although not offered in the UI.

What it cannot do is invent a new waveform: the waveform samples live in the pen
firmware (19 slots, see `firmware-notes.md`), and the tablet only selects a slot.

## Waveform ids (`ZuiPenHapticConstants`, services.jar)

Continuous (writing feedback), `WAVE_FORM_ID_*`:

| id | constant / UI label | `_NS` id | `_NS` label |
|----|---------------------|----------|-------------|
| 32 | `BALLPEN` — 中性笔 | 37 | `BALLPEN_NS` |
| 33 | `PENCIL` — 铅笔 (default) | 38 | `PENCIL_NS` |
| 34 | `CHISEL_MARKER` — 荧光笔 | 39 | `CHISEL_MARKER_NS` |
| 35 | `ERASER` — not in UI | 40 | `ERASER_NS` |
| 36 | `LENOVO_BRUSH` — 钢笔 | 41 | `LENOVO_BRUSH_NS` |

`NS_GAP = 5` is the offset between the two families. Impact (event) ids:
`STOP 0`, `CLICK 1`, `CONNECTED 2`, `HAPTIC_ENABLED 3`, `BRUSH_CHANGE 4`,
`TEXT_INPUT_FOCUS 5`, `RECOG_FINISHED 6`, `PRESS 7`, plus `EDGE_WARNING 42`
(accepted only by the impact validator).

## Chain

```
HapticBrushPreference (4 buttons: 32 / 33 / 34 / 36)
  |  PrefBrush -> SystemSettings.putPenHapticBrush()
  v
Settings.Global "pen_haptic_brush"
  |  ContentObserver ZuiPenHapticService$1.onChange -> mCurrentHapticType (live, no restart)
  v
ZuiPenHapticService.processDefaultHaptic()          hover enter / ACTION_DOWN fallback
  |  setHapticContinuousParams(mCurrentHapticType, mSettingsHpaticLevel, mCurrentFriction)
  v
ZuiPenHapticPolicy.setContinuousHapticParams
  |  checkContinuousParams(): id 32..41, level 0..5, friction 0/1
  |  buildConValues() -> byte[4]
  v
GATT write HAPTIC_CHARACTER_CON = [id, level, friction, toolFlag]
  v
PARKER firmware: selects the waveform blob for that id
```

`processDefaultHaptic` is the internal fallback path, so it is not subject to the
binder `checkCaller()` gate. On the observed device the chain was confirmed end-to-end:
`pen_haptic_brush = 33` produced
`setHapticContinuousParams called, id = 33` and
`writeCharacteristic cmds = [33, 5, 1, 0]`.

## Defaults (two different ones)

- App UI default: 33 (`HapticBrushPreference.brush` and
  `SystemSettings.getPenHapticBrush()` both use 33).
- Framework default: 32 (`Settings.Global.getInt("pen_haptic_brush", 32)` in both
  `initHapticSettings` and the observer).

So on a device where the user never touched the row, the framework applies 32 (ball pen)
while the settings screen renders 铅笔. Writing the row once makes the two agree.

## 书写声音 selects the `_NS` family

`pen_haptic_sound` (label 书写声音, "play sound while writing") also switches the
waveform family, because the pen's sound is part of the waveform blob:

- sound on -> id stays 32..36; eraser tool is forced to 35.
- sound off -> `ZuiPenHapticUtils.getNoSoundHapticId(id)` maps 32..36 to 37..41; eraser
  tool is forced to 40.

Eraser override is `mCurrentToolType == 4` inside `setHapticContinuousParams`, so tail
erasing ignores the brush setting either way.

## What "default" covers

`isDynamicHapticMatch()` checks the current writing package against
`pen_haptic_packages` (UI: 书写反馈应用管理). Packages in that list drive their own
haptic ids through the binder; every other package falls back to
`processDefaultHaptic` and therefore to `pen_haptic_brush`. This matches the settings
copy: "在未匹配各类笔型效果的应用内书写时，将使用此处设置的默认书写效果及强度。"

## Gates

- `checkContinuousParams`: id must be 32..41. Values outside that range (including 42)
  are rejected with `setContinuousHapticParams params invalid!`.
- App preview canvas (`HapticUtils.convertHapticBrush`) only maps 32/33/34/36; 35 and
  37..41 fall back to PENCIL, so the preview can differ from what the system sends.
- Binder `checkCaller`: only `com.lenovo.penservice`, `com.zui.ai.stylus`, or a package
  contained in `pen_haptic_packages`.

## Control without a hook

Because the selector is a plain Global setting consumed by an observer, no Hook module is
needed to change the default waveform kind:

```
adb shell settings put global pen_haptic_brush 32    # ball pen
adb shell settings put global pen_haptic_brush 33    # pencil (UI default)
adb shell settings put global pen_haptic_brush 38    # pencil, no-sound variant
```

The next pen-down / hover-enter uses the new id. Writing `Settings.Global` needs
`WRITE_SECURE_SETTINGS`, which root/shell has but the ZTool app uid does not; direct
binder injection is closed by `checkCaller()`.

## Firmware side

`firmware-notes.md` counts 18 registered waveform blobs + STOP in 19 slots
(7 impact + 10 continuous), which matches 5 audible + 5 no-sound continuous waveforms.
The BLE payload carries ids 32..41 verbatim; the conversion from those ids to the
firmware's 0..18 slot index has not been located yet, and id 42 (edge warning) does not
fit the 19-slot budget.

## Open items

- [ ] Record one clip per id and confirm the five `_NS` ids sound/vibrate differently.
- [ ] Locate the 32..41 -> 0..18 slot conversion in `PARKER.bin`.
- [ ] Decide what firmware id 42 does and whether it is reachable.
- [ ] Consider exposing the selector in ZTool via a shell/root setting write.
