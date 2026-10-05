# HyperOS 3 Volume System — services.jar Reverse Engineering

Reverse-engineered from the Xiaomi HyperOS 3 `services.jar` loaded in the JADX MCP
(23,787 classes / 3,730 packages, `classes.dex`). Scope: **how Xiaomi implements
stepless (fine-grained) media volume and how the volume-change path is made smooth**.
SystemUI-side animation is intentionally out of scope (that is the next step).

Classes that matter:

| Class | Role |
|---|---|
| `com.android.server.audio.AudioService` | AOSP 16 base + MIUI call sites (no policy of its own) |
| `com.android.server.audio.AudioService$VolumeStreamState` | per-stream index state, step factor, super index |
| `com.android.server.audio.AudioServiceStub` | **MIUI stub** — the entire Xiaomi volume policy surface |
| `com.android.server.audio.PlaybackActivityMonitorStub` | MIUI sound-assistant / per-app volume stub |
| `com.android.server.audio.AudioSystemAdapter` | thin `AudioSystem` bridge (`setStreamVolumeIndexAS`) |

---

## 0. Architecture: MIUI's stub indirection (read this first)

Xiaomi does **not** fork `AudioService`. It keeps an AOSP-identical `AudioService`
and inserts vendor hooks through a stub class:

```java
public class AudioServiceStub {
    public static final int DEFAULT_MIUI_STEP = 1;

    private static class SINGLETON {
        private static final AudioServiceStub sInstance =
                (AudioServiceStub) MiuiStubUtil.getImpl(AudioServiceStub.class);
    }
    public static AudioServiceStub get() { return SINGLETON.sInstance; }

    boolean isSupportSteplessVolume(int stream, String callingPkg) { return false; }
    int     getMusicVolumeStep(int stream, String callingPackage, int maxVolume) { return 1; }
    int     enableSuperIndex(int streamType, int indexMax) { return -1; }
    int     setSuperIndex(int index, int currentIndexMax, int device, int streamType) { return index; }
    int     getSuperIndex(int indexMax, int indexSuper, int streamType,
                          Set<AudioDeviceAttributes> deviceSet, Context context) { return indexMax; }
    boolean isSuperVolumeEnable() { return false; }
    boolean isNeedRescaleStepBySuperVolume(String callingPackage) { return false; }
    boolean isNeedSetIndexToMax(String callingPackage, int index, int indexMax) { return false; }
    ...
}
```

Every override above is a **no-op default**. The real implementation implements the
same class in Xiaomi's own jar and is bound by
`com.miui.base.MiuiStubUtil.getImpl(AudioServiceStub.class)`.

Consequences for this research:

- A stock `services.jar` preserves AOSP behaviour exactly; nothing here proves the
  feature is on — only **where** and **with what contract** Xiaomi consults its policy.
- The actual policy (`AudioServiceStubImpl`, plus a `MiAudioService extends AudioService`
  returned by `AudioServiceStub.getMiAudioService(...)`) is **not in `services.jar`**.
  See §5 for where to look next.

Related MIUI-only markers found in `AudioService`: `MSG_XIAOMI_MEDIA_MUTE`,
`MSG_MEDIASCENE_CHANGED` + `MEDIA_SCENE` / `VOICECALL_SCENE` / `VOIP_SCENE` /
`mCurrentSceneValue`, `mKeyVolumeCount` / `mSliderVolumeCount`,
`MIUI_AUDIO_DEBUG_ACTION` + `MIUI_DEBUG_*`, `isneedMuteMusic` /
`SetMuteAppName` (`"NONE"` default), `mMuteStateBeforeLoop`, `mMiBtCommDeviceActive`.

---

## 1. Stepless volume (无极音量)

### 1.1 The decisive code: the media step count

`AudioService.<init>` walks the stream types and, right after the
`ro.config.vc_call_vol_steps` / `ro.config.vc_call_vol_default` block
(stream 0 = `STREAM_VOICE_CALL`), applies this to **`STREAM_MUSIC` (3)**:

```java
if (AudioServiceStub.get().isSupportSteplessVolume(STREAM_MUSIC, "android")) {
    int steps = SystemProperties.getInt("ro.config.media_vol_steps", -1);
    if (steps != -1) {
        MAX_STREAM_VOLUME[STREAM_MUSIC] = steps;
    }
} else {
    MAX_STREAM_VOLUME[STREAM_MUSIC] = /* AOSP default */ 15;
}
```

and immediately after, Xiaomi's own default-volume knob:

```java
int def = SystemProperties.getInt("ro.config.media_vol_default", -1);
if (def != -1 && def <= MAX_STREAM_VOLUME[STREAM_MUSIC]
        && def >= MIN_STREAM_VOLUME[STREAM_MUSIC]) {
    AudioSystem.DEFAULT_STREAM_VOLUME[STREAM_MUSIC] = def;
}
```

Facts confirmed from `AudioService.<clinit>`:

```java
MAX_STREAM_VOLUME = new int[]{5, 7, 7, 15, 7, 7, 15, 7, 15, 15, 15, 15};
MIN_STREAM_VOLUME = new int[]{1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};
```

- AOSP ships `ro.config.vc_call_vol_steps` only. **`ro.config.media_vol_steps` and
  `ro.config.media_vol_default` are Xiaomi additions** — this is the switch that turns
  15 media steps into 100 (or whatever the device ships).
- The gate is `isSupportSteplessVolume(STREAM_MUSIC, "android")`; when it returns
  `false` the property is *ignored* and the AOSP 15 is written back. So an OEM must
  implement the stub for the property to have any effect.

### 1.2 Index model (why 100 steps works end-to-end)

`VolumeStreamState` (from `AudioService.VolumeStreamState.<init>`):

```java
this.mIndexMin = AudioService.MIN_STREAM_VOLUME[streamType] * 10;
this.mIndexMax = AudioService.MAX_STREAM_VOLUME[streamType] * 10;   // 1000 for 100 steps
this.mIndexSuper = AudioServiceStub.get().enableSuperIndex(this.mStreamType, this.mIndexMax);
updateIndexFactors();
```

`updateIndexFactors()` then **reprograms the native volume curve** to the new range:

```java
indexMinVolCurve = MIN_STREAM_VOLUME[streamType];
indexMaxVolCurve = MAX_STREAM_VOLUME[streamType];       // 100
...
groupId = AudioService.initMinMaxForVolumeGroup(
        getVolumeGroupForStreamType(streamType), indexMinVolCurve, indexMaxVolCurve, true, mAudioSystem);
// == audioSystem.setMinVolumeIndexForGroup(groupId, min)
//    audioSystem.setMaxVolumeIndexForGroup(groupId, max)
```

and computes the AOSP step factor:

```java
mIndexStepFactor = (mIndexMax - mIndexMin)
                 / ((MAX_STREAM_VOLUME[streamType] * 10) - (MIN_STREAM_VOLUME[streamType] * 10));
// == 1.0 for the normal music case: internal index is exactly step*10
```

Public API rounding (this is what every client slider sees):

```java
public int getStreamMaxVolume(int streamType) {
    return (getVssForStreamOrDefault(streamType).getMaxIndex() + 5) / 10;   // 100
}
private int getStreamVolume(int streamType, int device) {
    ... return (index + 5) / 10;
}
```

So the chain is: `ro.config.media_vol_steps = 100` → `MAX_STREAM_VOLUME[3] = 100`
→ native group range `0..100` → `mIndexMax = 1000` (index unit = 1/10 step)
→ `AudioManager.getStreamMaxVolume(STREAM_MUSIC) == 100`.

### 1.3 Per-key-press step (the "fine" in fine volume)

Inside `AudioService.adjustStreamVolume(...)`, the AOSP
`step = <one step>` computation is replaced by:

```java
int miuistep = AudioServiceStub.get()
        .getMusicVolumeStep(streamType, callingPackage, MAX_STREAM_VOLUME[streamType]);
int step = rescaleStep(miuistep * 10, streamType, aliasStream);
if (AudioServiceStub.get().isNeedRescaleStepBySuperVolume(caller)) {
    step = rescaleStepBySuperVolume(miuistep * 10, streamType, aliasStream);
}
...
vss.adjustIndex(direction * step, device, caller, hasModifyAudioSettings);
```

with

```java
private int rescaleStep(int step, int srcStream, int dstStream) {
    int srcRange = getIndexRange(srcStream);     // getMaxIndex() - getMinIndex()
    int dstRange = getIndexRange(dstStream);
    if (srcRange == 0) { Log.e(TAG, "rescaleStep : index range should not be zero"); return 0; }
    return ((step * dstRange) + (srcRange / 2)) / srcRange;
}

private int rescaleStepBySuperVolume(int step, int srcStream, int dstStream) {
    int srcRange = getVssForStreamOrDefault(srcStream).mIndexMax - ...getMinIndex();
    int dstRange = getVssForStreamOrDefault(dstStream).mIndexMax - ...getMinIndex();
    if (srcRange == 0) return 0;
    return ((step * dstRange) + (srcRange / 2)) / srcRange;
}

private int getIndexRange(int streamType) {
    return getVssForStreamOrDefault(streamType).getMaxIndex()
         - getVssForStreamOrDefault(streamType).getMinIndex();
}
```

- `getMusicVolumeStep` default is `DEFAULT_MIUI_STEP = 1` → one user step per key press.
- `* 10` converts a user step into the internal index unit.
- The `rescaleStep*` pair keeps the *audible* step constant when the caller's stream is
  aliased onto another (e.g. notification → ring).
- `rescaleStepBySuperVolume` deliberately uses the raw `mIndexMax` (ignoring the
  super-index expansion) while `rescaleStep` uses `getMaxIndex()` (which may be the
  expanded super index) — this is why `isNeedRescaleStepBySuperVolume(caller)` exists.

Two more policy levers on the same path:

```java
// VolumeStreamState.setIndex(...)
if (AudioServiceStub.get().isNeedSetIndexToMax(caller, index, mIndexMax)) {
    index = mIndexMax;
}
```

```java
// head of adjustStreamVolume(11 args)  -> key-driven path
AudioServiceStub.get().updataVolumeAdjustCount(0);
// head of setStreamVolumeWithAttribution -> slider/API-driven path
AudioServiceStub.get().updataVolumeAdjustCount(1);
```

i.e. MiuiStub explicitly distinguishes **key adjustments** from **slider sets**
(fields `mKeyVolumeCount` / `mSliderVolumeCount`). Also present: `isCtsVerifier(pkg)`,
`isGTTigoCustomizationVolume()`.

### 1.4 The HAL write path

```java
// AudioService.VolumeStreamState.setStreamVolumeIndex(int index, int device)
if (mIsEffectPolicySupported && mStreamType == 3 && device == 2 /* speaker */) {
    AudioServiceStub.get().notifyVolumeIndexChangedToEffectMiAudioService(mStreamType, index, device);
}
int i = AudioServiceStub.get().setSuperIndex(index, mIndexMax, device, mStreamType); // default: identity
i = (i > (mIndexMax + 5) / 10) ? (mIndexMax + 5) / 10 : i;
if (isStreamBluetoothSco(mStreamType) && i == 0 && !isFullyMuted()) i = 1;
if (i != 0) {
    i = (int) ((mIndexMin + (((i * 10) - mIndexMin) / getIndexStepFactor())) + 5.0f) / 10;
}
...
if (mStreamType == 0 || (mStreamType == 3 && !mAvrcpAbsVolSupported)) {
    AudioServiceStub.get().setStreamMusicOrVoiceCallIndex(i, mStreamType, device);
}
mAudioSystem.setStreamVolumeIndexAS(mStreamType, i, muted, device);
```

Note the naming trap: the value handed to `setSuperIndex` is the **user-space step
index** (≤ `mIndexMax/10`), not the 10× internal index.

### 1.5 The "super index" (extended / boosted range)

`mIndexSuper` is a second, independent expansion of the max index — an extended or
boosted range (per device), evaluated lazily:

```java
public int getMaxIndex() {
    if (AudioServiceStub.get().isSuperVolumeEnable()) {
        Set<AudioDeviceAttributes> deviceSet = getDeviceSetForStreamDirect(mStreamType);
        return AudioServiceStub.get().getSuperIndex(mIndexMax, mIndexSuper, mStreamType,
                                                    deviceSet, mContext);
    }
    ... // AOSP SCO / LE-Audio range handling (equalScoLeaVcIndexRange, ro.config.vc_call_vol_steps)
    return mIndexMax;
}
```

Wiring around it:

| Stub method | Called from | Purpose |
|---|---|---|
| `enableSuperIndex(stream, indexMax)` | `VolumeStreamState.<init>` | compute + cache `mIndexSuper` |
| `getSuperIndex(...)` | `VolumeStreamState.getMaxIndex` | effective max when super volume is on |
| `setSuperIndex(index, indexMax, device, stream)` | `VolumeStreamState.setStreamVolumeIndex` | map user index → HAL index |
| `setSuperIndexOnPlaybackStart(index, indexMax, device, stream)` | `AudioService.setSuperIndexForStreams(int[])` | re-apply on playback start |
| `postSuperIndexOnPlaybackStart(piid)` | `AudioService.setSuperIndexOnPlaybackStart(piid)` | async variant |
| `isNeedSetIndexToMax`, `calibrateIndexForBoostIfNeed`, `calibrateMaxIndexForBoostIfNeed` | adjust / index paths | volume-boost calibration |
| `getVolumeBoostState`, `setVolumeBoostState(...)`, `enableVoiceVolumeBoost(...)` | adjust / set path, `VolumeStreamState.setIndex` | voice/speaker boost state |

`rescaleVolumeInfoIndex(VolumeInfo, vss)` rescales indexes coming from callers that
hold a stale `[min,max]` (needed because stepless/super volume can change the range at
runtime):

```java
int min = (vss.getMinIndex() + 5) / 10;
int max = (vss.getMaxIndex() + 5) / 10;
if (vi.getMinVolumeIndex() != min || vi.getMaxVolumeIndex() != max)
    return rescaleIndex(index, vi.getMinVolumeIndex(), vi.getMaxVolumeIndex(), min, max);
```

### 1.6 Other MIUI-only volume knobs

| Item | Detail |
|---|---|
| `SLEEP_LOW_VOLUME` | `SystemProperties.getInt("ro.vendor.audio.sleep_low_volume", 0)`; in `adjustStreamVolume`, for `stream == 3 && device == 2 && "android".equals(caller)` it computes a small step and calls `targetSleepLowVolumeIndex(direction, smallStepIndex, unit, currentIndex)` |
| `customMinStreamVolume(int[])` | called from the `AudioService` ctor, mutates `MIN_STREAM_VOLUME` |
| `adjustDefaultStreamVolumeForMiui(int[])` | mutates `DEFAULT_STREAM_VOLUME` |
| `getMaxVssVolumeForStream(stream)` | returns `VolumeStreamState.getMaxIndex()` (used by MIUI internals) |
| `setStreamMusicOrVoiceCallIndex` / `setMusicMuteState` | push per-stream state to MIUI's own listener |

---

## 2. "Smooth" volume switching — the data path in services.jar

### 2.1 Key → SystemUI (exact chain)

```
PhoneWindowManager / MediaSessionService key loop
  └─ AudioService.handleVolumeKey(KeyEvent, isOnTv, callingPackage, caller)
       KEYCODE_VOLUME_UP   (24) -> adjustSuggestedStreamVolume(+1, MIN_VALUE, 0x1005, ..., keyEventMode)
       KEYCODE_VOLUME_DOWN (25) -> adjustSuggestedStreamVolume(-1, MIN_VALUE, 0x1005, ..., keyEventMode)
       KEYCODE_VOLUME_MUTE (164)-> adjustSuggestedStreamVolume(101, ...)
       (keyEventMode: 0 = non-TV DOWN, 1 = TV DOWN, 2 = TV UP; non-TV UP returns immediately)
  └─ AudioService.adjustSuggestedStreamVolume(...)
       mVolumeController.suppressAdjustment(resolvedStream, flags, isMute)   <-- long-press gate
  └─ AudioService.adjustStreamVolume(...)
       updataVolumeAdjustCount(0) / getMusicVolumeStep / adjustIndex
       VolumeStreamState.setIndex -> mIndexMap write + VOLUME_CHANGED_ACTION broadcast
  └─ AudioService.sendVolumeUpdate(streamType, oldIndex, index, flags, device)
       mVolumeController.postVolumeChanged(aliasStream, flags)
         -> IVolumeController.volumeChanged(streamType, flags)      <-- SystemUI VolumeDialogControllerImpl
       PlaybackActivityMonitorStub.get().startPlayerVolumeService(context, streamType, flags)
```

So SystemUI is notified through `IVolumeController.volumeChanged(streamType, flags)`
and then re-reads the value through `AudioManager`. `sendVolumeUpdate` and
`postVolumeChanged` are unconditional (they do not depend on the index having changed)
— that is the mechanism behind `ADJUST_SAME + FLAG_SHOW_UI` ("show/refresh the panel
without changing volume").

### 2.2 The long-press gate (`suppressAdjustment`)

```java
public boolean suppressAdjustment(int resolvedStream, int flags, boolean isMute) {
    if (isMute || resolvedStream != 3 || mController == null) return false;
    if (resolvedStream == 3 && mAudioSystem.isStreamActive(3, mLongPressTimeout)) return false;
    long now = SystemClock.uptimeMillis();
    if ((flags & 1) != 0 && !mVisible) {                 // FLAG_SHOW_UI && panel hidden
        if (mNextLongPress < now) {
            mNextLongPress = (mVolumeControllerLongPressEnabled.get() ? mLongPressTimeout : 0) + now;
        }
        return true;                                     // swallow this adjustment
    }
    if (mNextLongPress <= 0) return false;
    if (now > mNextLongPress) { mNextLongPress = 0L; return false; }
    return true;
}
```

`mVolumeControllerLongPressEnabled` is set by
`AudioService.setVolumeControllerLongPressTimeoutEnabled(boolean)` (permission-enforced),
i.e. **the volume controller (SystemUI) decides** whether the framework suppresses rapid
adjustments for `mLongPressTimeout` after the panel appears. This is the official AOSP 16
seam for "hold the key → let the UI own the cadence".

### 2.3 Broadcast coalescing

`VolumeStreamState.mVolumeChanged` and `mStreamDevicesChanged` are sent with
`BroadcastOptions`:

```java
volumeChangedOptions.setDeliveryGroupPolicy(1);                                   // MERGE
volumeChangedOptions.setDeliveryGroupMatchingKey("android.media.VOLUME_CHANGED_ACTION",
                                                 String.valueOf(mStreamType));
volumeChangedOptions.setDeferralPolicy(2);                                        // until next proc state
```

Rapid volume changes are therefore coalesced per stream instead of producing one
broadcast per key press — a prerequisite for smooth animation driven by the broadcast.

### 2.4 Multi-remote-device volume: float + proportion (Xiaomi-specific)

Xiaomi replaces AOSP's integer remote-device volume with a **float + proportion** model,
so that when several Bluetooth/remote devices are connected, moving the volume keeps the
inter-device balance and never snaps:

```java
float maxRemoteDeviceVolume = mRemoteDeviceVolumefloatMap.get(maxVolumeDevice.getDeviceId());
float maxVolumeDifference = volumeIndex - maxRemoteDeviceVolume;
for (AudioRemoteDeviceVolume deviceVolume : mRemoteDeviceVolumeList) {
    float splitRemoteDeviceVolume =
        mRemoteDeviceVolumefloatMap.get(deviceVolume.getDeviceId())
      + mRemoteDeviceVolumeProportionMap.get(deviceVolume.getDeviceId()) * maxVolumeDifference;
    ... // clamp to [getMinIndex(), getMaxIndex()] of STREAM_MUSIC, store back as float
    deviceVolume.setVolumeIndex(Math.round(mRemoteDeviceVolumefloatMap.get(deviceVolume.getDeviceId())));
}
```

- `mRemoteDeviceVolumeProportionMap` = each device's relative share of the loudest device
  (`volumeIndex / maxVolumeDevice.getVolumeIndex()`, or `1.0` when both are 0).
- `mRemoteDeviceVolumefloatMap` = fractional index per device (accumulates sub-step moves).
- `remoteDeviceVolumeMap(list, srcMax, dstMax)` = `(int)((original / srcMax) * dstMax)`,
  clamped — used to convert between the device's `0..100` space and
  `STREAM_MUSIC.getMaxIndex()` (which is why the stepless step count matters here too).
- Consumers: `notifyRemoteDeviceVolumeChanged(volumeIndex)` →
  `IVolumeChangeDispatcher.dispatchVolumeChange(List<AudioRemoteDeviceVolume>)`, and the
  `AUDIO_REMOTE_DEVICES_CHANGED` broadcast.
- `adjustIndex()` forwards remote-device (device `0x20000000` = `DEVICE_OUT_REMOTE_SUBMIX`-style
  remote id `536870928`) deltas into this path:

```java
public boolean adjustIndex(int deltaIndex, int device, String caller, boolean hasModifyAudioSettings) {
    if (device == 536870928) notifyRemoteDeviceVolumeChanged(getIndex(device) + deltaIndex);
    return setIndex(getIndex(device) + deltaIndex, device, caller, hasModifyAudioSettings);
}
```

### 2.5 Audio-effect / DSP synchronization

| Hook | Call site | Condition |
|---|---|---|
| `notifyVolumeIndexChangedToEffectMiAudioService(stream, index, device)` | `VolumeStreamState.setStreamVolumeIndex` | `mIsEffectPolicySupported` (`vendor.audio.effect_policy.support`) && stream 3 && device == speaker |
| `notifyVolumeChangedToDolbyEffectController(context, stream, newVolumeIndex)` | volume path | Dolby effect controller present |
| `setStreamMusicOrVoiceCallIndex(index, stream, device)` | `setStreamVolumeIndex` | stream 0, or stream 3 without AVRCP absolute volume |

These keep the vendor effect chain in step with the (now fine-grained) index, which is
what avoids zipper noise / discontinuity when the index moves in small increments.
AOSP `FadeManagerConfiguration` (`clearFadeManagerConfigurationForFocusLoss`,
`validateFadeManagerConfiguration`) covers the focus-loss fade path.

### 2.6 Volume-group callbacks (AOSP 16, also shipped here)

`AudioVolumeChangeHandler` registers `INativeAudioVolumeGroupCallback` on first use and
fans `AudioVolumeGroupChangeEvent` out to `IAudioVolumeChangeDispatcher` clients:

```java
public void onAudioVolumeGroupChanged(AudioVolumeGroupChangeEvent volumeEvent) {
    sendAudioVolumeGroupChangedToClients(volumeEvent.groupId, volumeEvent.flags);
}
```

This is a lower-latency, per-volume-group alternative to the legacy
`VOLUME_CHANGED_ACTION` broadcast, and is what SystemUI can use for fast updates on
volume-group-managed routes (BLE/LE-Audio, absolute-volume devices).

---

## 3. What `services.jar` cannot answer

The policy is entirely behind `MiuiStubUtil.getImpl(...)`:

1. `AudioServiceStub` implementation (name pattern `...StubImpl`) — the real
   `isSupportSteplessVolume`, `getMusicVolumeStep`, `enableSuperIndex` / `getSuperIndex` /
   `setSuperIndex`, `isSuperVolumeEnable`, `isNeedRescaleStepBySuperVolume`,
   `targetSleepLowVolumeIndex`, `updataVolumeAdjustCount`.
2. `MiAudioService extends AudioService` — returned by
   `AudioServiceStub.getMiAudioService(...)`; may override the whole volume flow.
3. `PlaybackActivityMonitorStub` implementation — sound assistant / per-app volume
   (`loadAppsVolume`, `setPlayerVolume`, `SOUND_ASSIST_KEY`, `key_ignore_music_focus_req`).

Likely locations on a HyperOS device: `miui-framework.jar` /
`miui-services.jar` under `/system/framework` or `/system_ext/framework`.
**Until those are loaded, every number in §1.1–§1.5 is a contract, not an observed
value** (e.g. the actual `ro.config.media_vol_steps` on a HyperOS phone, and the actual
`getMusicVolumeStep` per stream, are unknown from this jar alone).

---

## 4. Comparison with ZTool's current implementation

| Aspect | HyperOS 3 (`services.jar`) | ZTool today |
|---|---|---|
| Fine step count | `MAX_STREAM_VOLUME[3]` overwritten in the `AudioService` ctor from `ro.config.media_vol_steps`, gated by `isSupportSteplessVolume(3, "android")` | `FineVolumeSteps` hooks `createStreamStates()` and sets `MAX_STREAM_VOLUME[3] = 150` |
| Step-space propagation | implicit: `VolumeStreamState` ctor (`*10`) + `updateIndexFactors()` → `setMaxVolumeIndexForGroup` | same AOSP mechanism, so also implicit — no extra work needed |
| Persisted value migration | not visible in `services.jar` (policy is in the stub) | `FineVolumeSteps.migratePersistedMusicVolume` rescales `volume_music*` and `DEFAULT_STREAM_VOLUME` once, behind a `Settings.Global` marker |
| Per-press step | `getMusicVolumeStep(stream, pkg, MAX_STREAM_VOLUME[stream])` × `rescaleStep` / `rescaleStepBySuperVolume` | 1 step per tick (stock), cadence controlled by `VolumeKeyNonlinearRamp` |
| Key-hold ramp | not in `AudioService` at all — must live in the stub impl / `MiAudioService` / SystemUI | `VolumeKeyNonlinearRamp` owns the cadence itself: drops native ticks, drives `adjustSuggestedStreamVolume` on an accelerating curve (`V0=8`, `ACCEL=16` in the docstring vs `200` in the constant, `MAX=80`/`150`) |
| Panel refresh between steps | `sendVolumeUpdate` is unconditional; `ADJUST_SAME + FLAG_SHOW_UI` is the documented "refresh without change" path | already used: `refreshRunnable` emits `ADJUST_SAME | FLAG_SHOW_UI` every 50 ms |
| Rapid-adjust suppression | `VolumeController.suppressAdjustment` + `mVolumeControllerLongPressEnabled` (SystemUI opt-in) | **not used** — ZTool bypasses the gate by re-entering `adjustSuggestedStreamVolume`; adopting the AOSP knob may be cleaner than dropping native ticks |
| Broadcast coalescing | AOSP `BroadcastOptions` merge/deferral per stream | inherited for free |
| Multi-remote-device volume | float + proportion model (§2.4) | not implemented (ZUI keeps AOSP integer remote volume) |
| Effect/DSP sync | `notifyVolumeIndexChangedToEffectMiAudioService`, `notifyVolumeChangedToDolbyEffectController` | none (ZUI has no such hook surface) |

Two concrete takeaways for ZTool:

1. **The step-count mechanism is confirmed correct.** Xiaomi does exactly what
   `FineVolumeSteps` does — override `MAX_STREAM_VOLUME[STREAM_MUSIC]` — and relies on
   `VolumeStreamState`'s `*10` + `updateIndexFactors()` to propagate it to the native
   volume group range. The only differences are the trigger (a `ro.*` property read once
   in the ctor, so it needs a system_server restart) and the step count (Xiaomi's
   property value vs ZTool's 150).
2. **Xiaomi's ramp is not in `services.jar`.** `AudioService` contains no self-driven
   volume loop; the only framework-side ramp affordances are
   `suppressAdjustment` + `setVolumeControllerLongPressTimeoutEnabled` (controller opt-in)
   and the unconditional `postVolumeChanged`. ZTool's self-driven driver is therefore a
   *replacement* for whatever the MIUI stub/SystemUI does, not a port of it — worth
   confirming against the MIUI stub jar and SystemUI before investing further.

---

## 5. Next steps

1. Load the MIUI stub jars (`miui-framework.jar`, `miui-services.jar`) and read
   `AudioServiceStub`'s implementation + `MiAudioService`:
   - the real `getMusicVolumeStep` / `isSupportSteplessVolume` / super-index math;
   - the actual `ro.config.media_vol_steps` value via `getprop`;
   - whether the ramp lives there (`updataVolumeAdjustCount`, `targetSleepLowVolumeIndex`).
2. **Load `SystemUI.apk`** (agreed next step) and analyse the animation:
   - `com.android.systemui.volume.VolumeDialogImpl` — how the panel is shown/dismissed and
     how it animates between fast `IVolumeController.volumeChanged` callbacks;
   - `VolumeDialogControllerImpl` — registration with `IVolumeController`, use of
     `setVolumeControllerLongPressTimeoutEnabled`, and whether it drives its own ramp;
   - the slider widget (ZUI analogue: `zui.widget.SeekBarNps` / `ToggleSliderView`) and its
     progress-to-stream mapping (raw units vs steps);
   - whether HyperOS uses `IAudioVolumeChangeDispatcher` / volume groups or the legacy
     broadcast for its updates;
   - the exact interpolation/alpha/scale animation used when the index changes by 1 of 100.
