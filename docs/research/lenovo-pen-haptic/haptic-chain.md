# Haptic Strength Chain (Tablet Side)

Targets found while auditing `com.lenovo.penservice` (v1.8.220260331) and the ZUI
framework `services.jar`. All class references are from Jadx decompilation.

## Call chain

```
HapticLevelPreference (com.lenovo.pen.bt.widget)          penservice UI, clamp 1..5
  |  writes Settings key
  v
Settings.Global "pen_haptic_level"                        persisted level, default 5
  |  ContentObserver in system_server
  v
ZuiPenHapticService.mSettingsHpaticLevel                  com.zui.server.input.styluspen.pen.bluetooth.haptic
  |  setHapticContinuousParams(id, level, friction)
  v
ZuiPenHapticPolicy.setContinuousHapticParams
  |  ZuiPenHapticUtils.checkHapticParamsValid()            <-- server-side validation gate
  |  ZuiPenHapticUtils.buildConValues()
  v
BluetoothGatt write to HAPTIC_CHARACTER_CON               payload [hapticId, level, friction, toolFlag]
  |
  v
PARKER pen firmware                                       level semantics decided here (not in tablet code)
```

Impact haptics (clicks) follow the same path via `setHapticImpactParams` /
`HAPTIC_CHARACTER_IMP`, payload `[id, level, countLo, countHi, 0, 0]`.

## Constants

`com.lenovo.pen.api.haptic.HapticConstants` (penservice):

- `HapticLevel`: LIGHTLY=1, SOFTLY=2, FIRMLY=3, STRONGLY=4, INTENSELY=5
- `ImpactHaptic`: STOP=0 ... PRESS=7 (ids 1..7 valid)
- `ContinuousHaptic`: BALL_POINT_PEN=32 ... LENOVO_PEN_NS=41 (ids 32..41 valid)
- Impact repeat count 1..10, cutoff time 0..300

Settings keys (`SystemSettings.Keys` / `ZuiPenHapticConstants`, all `Settings.Global`
except noted): `pen_haptic_feedback` (switch), `pen_haptic_sound`, `pen_haptic_brush`,
`pen_haptic_level`, `pen_haptic_packages` (whitelist), `pen_haptic_use_first`,
PARKER 6DOF switch (Secure).

## The three gates that clamp strength

1. **UI clamp** — `HapticLevelPreference` hard-coerces the seek bar to `1..5`
   (`coerceAtLeast(1)` / `coerceAtMost(5)`). Bypassed by writing
   `Settings.Global pen_haptic_level` directly; the service-side observer reads any value.
2. **Service validation** — `ZuiPenHapticUtils.checkContinuousParams()` /
   `checkImpactParams()` reject `level < 0 || level > 5` ("level illegal!"). Pure Java in
   `system_server`; hookable, but caller must still pass `checkCaller()`
   (allowed: `com.lenovo.penservice`, PKG_AISTYLUS, whitelist packages).
3. **Firmware semantics** — the level byte is forwarded verbatim. Whether >5 produces a
   stronger effect is decided by `PARKER.bin` (see `firmware-ota.md`). Only a saturation
   inside the firmware would make further hooking pointless.

## Default-path shortcut

`ZuiPenHapticService.processDefaultHaptic()` (hover enter / pen down) reads
`mSettingsHpaticLevel` directly and calls `setHapticContinuousParams()` internally,
bypassing both the UI clamp and the binder `checkCaller()` path. Writing the Settings
key exercises this path — the cheapest end-to-end test of lifted values.

## Also present

- `android.app.haptic.ZuiPenHapticManager` — client stub in the app APK; real binder
  interface is `android.app.haptic.IZuiPenHapticManager` served by `ZuiPenHapticService`
  registered as system service `"zui_pen_haptic"`.
- `ZuiPenHapticUtils.getNoSoundHapticId()` maps ids 32..36 to 37..41 when haptic sound
  is off; eraser events force id 35/40.
- Touch panel haptic enable goes through `TouchscreenServiceManager.setHaptics()` (TP
  node write), separate from the pen-side GATT channel.
