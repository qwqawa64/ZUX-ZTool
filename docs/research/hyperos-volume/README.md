# HyperOS 3 Volume System — services.jar Reverse Engineering

Reverse-engineered from the Xiaomi HyperOS 3 `services.jar` (23,787 classes / 3,730
packages) and `SystemUI.apk` (`17.03.260226.r`) loaded in the JADX MCP. Scope: **how Xiaomi
implements stepless (fine-grained) media volume and how the volume-change path and its
animation are made smooth.**

- §0–§4: `services.jar` — the framework contract (and the MIUI stub indirection).
- §5: how to read this APK when classes look empty (R8 inlining, not Rust).
- §6: `SystemUI.apk` — the Compose volume panel and every animation constant.
- §7: what still needs the MIUI stub jars.

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

## 5. Reading this APK: classes are not "missing", they are R8-inlined

Many volume classes decompile to **fields only, zero methods** — e.g.
`VolumeDialogSliderViewBinder`, `VolumeDialogOverscrollViewBinder`,
`VolumeDialogSliderInteractor`, `VolumeDialogOverscrollViewModel`, `VolumePanelFlag`.
`get_methods_of_class` returns empty and `get_smali_of_class` confirms there is no
`.method` block. This is **not** a Rust/native rewrite: R8 inlined the (single) method
into its only caller and kept the class only as a Dagger field holder.

Evidence: the APK's only native-library consumers anywhere are
`miuix.flexible.tile.TileBitmapNative`, `miuix.mipalette.MiPalette`,
`com.miui.fastplayer.FastPlayer`, `org.extra.relinker.*` and androidx
graphics/sync-fence bindings — **nothing in the volume path loads a `.so`**. The dex
carries `/* compiled from: go/retraceme <sha> */` (R8 `-sourcefile` mapping) and the
`...$$ExternalSyntheticBUOutline0` helpers are Kotlin coroutine outlining.

**Workaround that works** — read the *caller* instead of the class:

| Empty class | Where its body actually lives |
|---|---|
| `VolumeDialogSliderViewBinder`, `VolumeDialogOverscrollViewBinder` | `VolumeDialogSlidersViewBinder.access$bindSlider` |
| ctors of `VolumeDialogSliderInteractor`, `VolumeDialogOverscrollViewModel`, `VolumeDialogSliderInputEventsInteractor` | `DaggerReferenceGlobalRootComponent$VolumeDialogSliderComponentImpl$SwitchingProvider.get()` |
| ctor of `VolumeDialogViewBinder`, `VolumeDialogRingerViewBinder` springs | `DaggerReferenceGlobalRootComponent$VolumeDialogComponentImpl$SwitchingProvider.get()` |

Use `get_xrefs_to_class` / `get_xrefs_to_field` to find that caller, and
`get_smali_of_class` whenever the Java decompiler aborts on a method.

---

## 6. SystemUI: how the "smooth volume animation" is actually built

Source: HyperOS 3 `SystemUI.apk`, `versionName 17.03.260226.r`, `targetSdk 37`.
The volume panel in this build is **AOSP 16's refactored, Kotlin + Compose volume
dialog** (`com.android.systemui.volume.dialog.*`, `...sliders.*`, `...ringer.*`)
plus a newer Compose "volume panel" (`com.android.systemui.volume.panel.*`, the expanded
audio-tile surface). There is **no** `VolumeDialogImpl`, so older MIUI write-ups about
`VolumeDialogImpl`/`ToggleSliderView` do not apply to HyperOS 3.

Wiring: `VolumeUI` (`CoreStartable`) → `VolumeDialogComponent` (an `ExtensionController`
extension: plugin `VolumeDialog` vs. default `VolumeDialogPlugin`; `Settings.Global
"native_volume_bar"` selects a plugin) → `VolumeDialog` (`ComponentDialog`) →
`VolumeDialogViewBinder.bind(...)`. `R.bool.enable_volume_ui` / `enable_safety_warning`
gate the whole thing.

### 6.1 Every hard number in the animation

| What | Class | Values |
|---|---|---|
| Panel show / dismiss slide | `VolumeDialogViewBinder.bind` + `$animateVisibility$1` + `$animateVisibility$animation$1` | `SpringForce` stiffness **700**, dampingRatio **0.9** (`setMinimumVisibleChange(0.01f)`); animated to **1.0f** on `Visible`, **0.0f** on `Dismissed`; per frame `alpha = ceil(v)` and `translationX = lerp(v, width, 0) = width * (1 - v)`. The dialog is `dismiss()`ed only after the spring reaches 0, and a `JankListenerFactory` listener tagged `"show"`/`"dismiss"` is swapped in per direction |
| Volume slider value | `com.android.systemui.volume.ui.compose.slider.SliderKt.Slider` | `DefaultAnimationSpec = spring(dampingRatio = 1.0f, stiffness = 1500.0f)` (critically damped, no overshoot); `Animatable(initialValue, visibilityThreshold = 0.01f)`; retarget guard in `SliderKt$$ExternalSyntheticLambda0` (skip if dragging / target already equals the debounced value); driver `SliderKt$Slider$4$1$1`: `if (!animatable.isRunning()) animatable.snapTo(sliderState.value); animatable.animateTo(debouncedValue, spec)` |
| Incoming-value debounce | `SliderKt.debouncedValueState` (+ `$1$1`, `$2$1`) | **100 ms**. On `DragInteraction.Stop` → `debounceStartTimestamp = now`, `debouncedValue = sliderState.value`; `shouldDebounce = (now - timestamp) < 100ms`; if set → `delay(100ms)` before adopting the incoming value, else adopt immediately |
| Slider granularity | `VolumeDialogSliderViewBinderKt.VolumeDialogSlider` | `value = state.value`, `valueRange = state.valueRange`, **`stepDistance = 1.0f`** (one volume step), `Haptics.Enabled(SliderHapticFeedbackConfig(0f, 0f, 0.1f, 0.02f, 4, 0.5f, 0f, SliderHapticFeedbackFilter(3, false)), SeekableSliderTrackerConfig(3, 0.01f, 0.99f), Orientation.Vertical)` |
| Slider value source | `VolumeDialogSliderInteractor` | `slider = state.mapNotNull { it.streamModels[sliderType.audioStream] }`, with `level` coerced into `[levelMin, levelMax]` |
| Overscroll (drag past the end) | `VolumeDialogOverscrollViewModel` + `VolumeDialogSlidersViewBinder.access$bindSlider` + `$bind$1` / `$bind$animation$2` | `maxDeviation = R.dimen.volume_dialog_slider_max_deviation`; `offsetInterpolator = PathInterpolator(0.15f, 0.0f, 0.2f, 1.0f)`; `delta = (touchY - startY) / 3.0f`; `offset = sign(delta) * interpolator(|delta| / maxDeviation) * maxDeviation` (only when dragging past the direction the slider cannot move); applied as `setTranslationY(offset)` on `{mainSliderContainer, background, bottomSection, topSection}`; on `Touch.End` → `SpringAnimation(FloatValueHolder(0f)).setStiffness(800f).setDampingRatio(0.6f).animateToFinalPosition(0f)` |
| Ringer button roundness | `VolumeDialogRingerViewBinder.roundnessSpringForce` | `SpringForce(1.0f)` stiffness **800**, ζ **0.6**, `setMinimumVisibleChange(0.05f)`; drawer/button corner radius interpolated per frame |
| Ringer button colour | `VolumeDialogRingerViewBinder.colorSpringForce` | stiffness **3800**, ζ **1.0**, plus `ArgbEvaluator` |
| Ringer drawer open/close | `VolumeDialogRingerViewBinder.access$closeDrawer` | `MotionLayout` + `R.anim.volume_dialog_ringer_open` / `R.anim.volume_dialog_ringer_close` (`transition.mDefaultInterpolator = -2`, then `mDefaultInterpolatorID = R.anim.volume_dialog_ringer_close`) |
| Half-opened (expanded audio tile) | `VolumeDialogViewBinder$bind$4` | `viewGroup.animate().setDuration(150L).translationY(z ? halfOpenedOffsetPx : 0f)`, `halfOpenedOffsetPx = R.dimen.volume_dialog_half_opened_offset` |
| Panel auto-dismiss | `VolumeDialogVisibilityInteractor` | `defaultTimeout = 3 s`, overridden by `Settings.Secure "volume_dialog_dismiss_timeout"`; `computeTimeout()` then applies `AccessibilityRepository.getRecommendedTimeout(4 \| 6, t)`; `resetDismissTimeout()` = `controller.userActivity()` + re-emit; `VolumeDialog.onTouchEvent` dismisses on `ACTION_OUTSIDE` (`reason = 1`) |

### 6.2 The exact key-press → animation chain

```
AudioService.sendVolumeUpdate
  -> IVolumeController.volumeChanged(streamType, flags)
  -> ProducingVolumeController.volumeChanged                     [settingslib, IVolumeController.Stub]
       tryEmit(VolumeControllerEvent.VolumeChanged(streamType, flags))
  -> AudioRepositoryImpl.volumeControllerEvents (SharedFlow)
  -> VolumeControllerAdapter.collectToController(mVolumeController)
       -> VolumeDialogControllerImpl.<IVolumeController>.volumeChanged(...)
  -> VolumeDialogControllerImpl.onVolumeChangedW(stream, flags, fromKey)   [worker handler]
       showUI   = flags & 0x1        (FLAG_SHOW_UI)
       fromKey  = flags & 0x1000     (FLAG_FROM_KEY)
       vibrateHint = flags & 0x800 ; silentHint = flags & 0x80
       updateStreamLevelW(stream, mAudio.getLastAudibleStreamVolume(stream))
       if (showUI) {
           legacy = VolumeDialogTransformHelper.calculateHapticFeedbackState(streamState, level, flags, levelChanged, true)
           callbacks.onPerformHapticFeedback(legacy)            // MIUI per-step tick
           callbacks.onShowRequested(1, keyguardLocked, lockTaskModeState)
       } else if (mLastShowUI && fromKey) {
           callbacks.onPerformHapticFeedback(calculateHapticFeedbackState(..., false))  // release tick
           mLastShowUI = false
       }
       if (levelChanged && fromKey) callbacks.onVolumeChangedFromKey()
  -> VolumeDialogCallbacksInteractor.VolumeDialogEventModelProducer
       callbackFlow{}.buffer(16, DROP_OLDEST).shareIn(Eagerly)  -> VolumeDialogEventModel
  -> VolumeDialogStateInteractor: StateChanged -> VolumeDialogStateRepository.mutableState
       VolumeDialogStateModel.streamModels : Map<stream, VolumeDialogStreamModel{level, levelMin, levelMax, muted, ...}>
  -> VolumeDialogSliderInteractor.slider  (level coerced into [levelMin, levelMax])
  -> VolumeDialogSliderViewModel.state    -> VolumeDialogSliderStateModel{value, valueRange, ...}
  -> VolumeDialogSliderViewBinderKt.VolumeDialogSlider -> SliderKt.Slider(value, valueRange, stepDistance = 1f)
  -> Animatable.animateTo(value, spring(dampingRatio = 1.0f, stiffness = 1500.0f))   <-- the smooth motion
```

So the "smooth" feel is three cooperating mechanisms, not one:

1. **Step count** — because `services.jar` raises `MAX_STREAM_VOLUME[STREAM_MUSIC]`,
   `VolumeDialogStreamModel.levelMax` is `100` (or whatever `ro.config.media_vol_steps`
   is) instead of `15`, so a single key press moves 1/100 of the track. This is the part
   Xiaomi actually changed on the framework side.
2. **Spring retargeting** — `spring(dampingRatio = 1.0f, stiffness = 1500.0f)` is
   re-targeted on every state emission. A new target does not restart an animation: it
   redirects the running spring, so a burst of 1-step updates composes into one continuous
   motion. `Animatable.animateTo` is only (re)launched when the target actually differs
   and the slider is not being dragged.
3. **Debounce** — the 100 ms post-drag debounce stops `AudioService`'s lagging echoes from
   fighting the user's finger right after a drag, and the `callbackFlow` buffer
   (`16, DROP_OLDEST`) keeps a fast burst from blocking the pipeline.

### 6.3 MIUI/HyperOS-specific layer in SystemUI (deliberately tiny)

Only three classes in `com.miui.systemui.volume`:

- `VolumeDialogControllerInjector` — holds `VolumeDisplayWindowListener`; injected as the
  last parameter of `VolumeDialogControllerImpl`'s constructor.
- `VolumeDisplayWindowListener extends IDisplayWindowListener.Stub` — on
  `onFixedRotationStarted` it logs and runs a callback (dismiss/refresh the volume dialog
  when a fixed rotation begins); every other callback is empty.
- `VolumeDialogTransformHelper.calculateHapticFeedbackState(streamState, level, flags,
  levelChanged, isShowUI)` → bitmask `(flags & 0x4000 ? 1 : 0) | (level == levelMax ? 2 : 0)
  | (level == levelMin ? 4 : 0) | (levelChanged ? 8 : 0) | (isShowUI ? 16 : 0)`, consumed by
  `Callbacks.onPerformHapticFeedback`.

Note also: `setVolumeControllerLongPressTimeoutEnabled` is **not called anywhere** in this
SystemUI, so the AOSP "controller opts into long-press suppression" path is unused here —
further evidence that HyperOS lets the service deliver every step and animates the UI
itself instead of batching at the framework.

### 6.4 Updated comparison with ZTool

| Aspect | HyperOS 3 | ZTool today |
|---|---|---|
| Panel technology | Kotlin + Compose (`volume.dialog.*`), Compose `Slider` with a spring `Animatable` | ZUI `zui.widget.SeekBarNps` / `ToggleSliderView` (View-based) |
| Slider motion | `spring(ζ=1.0, k=1500)` on the value, `stepDistance = 1.0f`, 100 ms post-drag debounce | no value animation; the label/colour are refreshed per event |
| Steps | `ro.config.media_vol_steps` → `MAX_STREAM_VOLUME[3]` | `FineVolumeSteps` → `MAX_STREAM_VOLUME[3] = 150` |
| Key ramp | *not* in SystemUI; only per-step haptics. Framework has no self-driven loop either | `VolumeKeyNonlinearRamp` self-driven accelerating loop + `ADJUST_SAME` panel refresh |
| Panel reveal | spring stiffness 700 / ζ 0.9 on `translationX` (width→0) + `alpha = ceil(v)` | stock ZUI window animation |
| Overscroll | `PathInterpolator(0.15,0,0.2,1)`, drag/3, capped, spring back k=800 ζ=0.6 | not implemented |
| Per-step haptics | `calculateHapticFeedbackState` bitmask (min/max/level-changed/show) | not implemented |
| Panel timeout | `Settings.Secure "volume_dialog_dismiss_timeout"` default 3 s, a11y-scaled | stock |

---

## 7. What is still unknown

1. The MIUI stub jars (`miui-framework.jar` / `miui-services.jar`) — `AudioServiceStub`
   implementation and `MiAudioService`: the real `getMusicVolumeStep`,
   `isSupportSteplessVolume`, super-index math, and the actual
   `ro.config.media_vol_steps` value (`adb shell getprop`).
2. Whether `ro.config.media_vol_steps` is actually set on a shipping HyperOS 3 device
   (if it is not, the stepless path stays inert and the framework never widens the range).
3. `R.dimen.volume_dialog_slider_max_deviation` and
   `R.dimen.volume_dialog_half_opened_offset` actual dp values — they live in
   `resources.arsc`, which the JADX MCP cannot hand out as a file.
4. Whether HyperOS also wires `IAudioVolumeChangeDispatcher` (§2.6) into the panel; the
   SystemUI side seen here still goes through the legacy `IVolumeController` path.
