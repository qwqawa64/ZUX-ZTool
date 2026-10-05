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
  15 media steps into the fine scale.
- The gate is `isSupportSteplessVolume(STREAM_MUSIC, "android")`; when it returns
  `false` the property is *ignored* and the AOSP 15 is written back. So an OEM must
  implement the stub for the property to have any effect. The stub's actual
  implementation (§7.1) shows the gate is in practice *always* true for music, and that
  Xiaomi ships **150** steps (`ro.config.media_vol_steps = 150`) — see §7.2 for how that
  number is pinned down.

### 1.2 Index model (why a wider step count works end-to-end)

`VolumeStreamState` (from `AudioService.VolumeStreamState.<init>`):

```java
this.mIndexMin = AudioService.MIN_STREAM_VOLUME[streamType] * 10;
this.mIndexMax = AudioService.MAX_STREAM_VOLUME[streamType] * 10;   // 1500 for 150 steps
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
| Per-press step | `getMusicVolumeStep(...) = maxVolume/15` → **10 of 150 steps = 1/15 of the range** (stock granularity kept), then `rescaleStep` / `rescaleStepBySuperVolume` | 1 step per tick = **1/150 of the range**, cadence controlled by `VolumeKeyNonlinearRamp` |
| Key-hold ramp | none anywhere — not in `AudioService`, not in the MIUI stub (§7.6), not in SystemUI (§6.3); native key-repeat drives 1/15-of-range steps | `VolumeKeyNonlinearRamp` owns the cadence itself: drops native ticks, drives `adjustSuggestedStreamVolume` on an accelerating curve (`V0=8`, `ACCEL=16` in the docstring vs `200` in the constant, `MAX=80`/`150`) |
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
   `VolumeDialogStreamModel.levelMax` is `150` (i.e. `ro.config.media_vol_steps`
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
| Steps | `ro.config.media_vol_steps` (150) → `MAX_STREAM_VOLUME[3]`; key step stays 1/15 (§7.1) | `FineVolumeSteps` → `MAX_STREAM_VOLUME[3] = 150`; key step becomes 1/150 |
| Key ramp | *not* in SystemUI and *not* in the MIUI stub; only per-step haptics | `VolumeKeyNonlinearRamp` self-driven accelerating loop + `ADJUST_SAME` panel refresh |
| Panel reveal | spring stiffness 700 / ζ 0.9 on `translationX` (width→0) + `alpha = ceil(v)` | stock ZUI window animation |
| Overscroll | `PathInterpolator(0.15,0,0.2,1)`, drag/3, capped, spring back k=800 ζ=0.6 | not implemented |
| Per-step haptics | `calculateHapticFeedbackState` bitmask (min/max/level-changed/show) | not implemented |
| Panel timeout | `Settings.Secure "volume_dialog_dismiss_timeout"` default 3 s, a11y-scaled | stock |

---

## 7. `miui-services.jar`: the real policy

Now that the MIUI STUB jar is loaded, every contract in §1 has a body. Classes:
`com.android.server.audio.AudioServiceStubImpl` (implements `AudioServiceStub`),
`com.android.server.audio.VolumeBoostHelper`, `com.android.server.audio.MiAudioService`,
`com.android.server.audio.MQSUtils`, `com.android.server.audio.AudioServiceInjector`
(**not in this jar** — see §7.8).

Wiring (`AudioServiceStubImpl.init(Context, AudioService)`, called from the `AudioService`
ctor): stores `mAudioService`/`mAudioManager`, then creates
`mMiAudioService = new MiAudioService(context, service, mWorkerThread.getLooper())`, and
`mVolumeBoostHelper = new VolumeBoostHelper(context, mWorkerThread.getLooper(), service)`
**only if `VolumeBoostHelper.ENABLE`**.

### 7.1 Stepless volume: the actual implementation

```java
public boolean isSupportSteplessVolume(int stream, String callingPackage) {
    return stream == 3 && !isCtsVerifier(callingPackage);
}

public int getMusicVolumeStep(int stream, String callingPackage, int maxVolume) {
    if (isSupportSteplessVolume(stream, callingPackage)) {
        Log.d(TAG, "adjustStreamVolume(): SupportSteplessVolume, maxVolume = " + maxVolume);
        return maxVolume / 15;
    }
    return 1;
}

private boolean isCtsVerifier(String p) {   // com.android.cts*, android.media.cts,
    ...                                     // com.google.android.gts*, android.media.audio.cts
}
```

Two consequences that change the §1 reading:

1. **The gate is unconditionally true for `STREAM_MUSIC`** (except for CTS/GTS verifier
   packages). The gate is *not* what enables the feature — `ro.config.media_vol_steps` is.
   If that property is unset, `MAX_STREAM_VOLUME[3]` keeps the AOSP 15 and
   `getMusicVolumeStep` returns `15/15 = 1`, i.e. byte-for-byte stock behaviour.
2. **`maxVolume / 15` deliberately preserves the stock key-press granularity.** The step
   is `miuistep * 10` internal units = `(maxVolume/15) * 10`; with `maxVolume = 150` that
   is 10 user steps out of 150 = **exactly 1/15 of the range** — the same audible step as
   a stock 15-step phone. So "无极音量" is *not* finer key stepping: the extra resolution is
   for the slider/drag, per-app volume, absolute (Bluetooth) volume, and the DSP volume
   index. Without the MIUI stub (e.g. on ZUI) a 150-step scale makes one key press move
   1/150 instead — which is why ZTool needs its own ramp driver and Xiaomi does not.

### 7.2 Which step counts exist at all

Three independent places divide by 15 or 150, and they agree on exactly two scales:

- `getMusicVolumeStep` → `maxVolume / 15`
- `VolumeBoostHelper.enableSuperIndex` → `mMusicVolumeStep = (indexMax/10) / 15`
- `VolumeBoostHelper.setSuperIndex` special-cases `mMusicVolumeStep == 10` and `== 2`
- `AudioServiceStubImpl.getAbsoluteVolumeIndex` → `step = indexMax / 150`

`mMusicVolumeStep == 10` ⇔ `mIndexMax == 1500` ⇔ **150 steps**; `== 2` ⇔ 300 ⇔ **30 steps**.
Nothing else is representable, so `ro.config.media_vol_steps` is 150 (primary; the
`/150` in `getAbsoluteVolumeIndex` only makes sense at 1500) or 30. **This confirms
ZTool's `TARGET_STEPS = 150` is exactly Xiaomi's number.**

### 7.3 "Super index" = 超级音量 (speaker-only DSP boost), not a step-count feature

Implemented by `VolumeBoostHelper`, created only when
`ENABLE = SUPER_VOLUME_ENABLE || CALL_VOLUME_BOOST_ENABLE`.

- Feature detection: `SUPER_VOLUME_ENABLE = (ro.vendor.audio.volume_super_index_add != -1)`;
  the *streams* that participate come from the bitmask
  `ro.vendor.audio.volume_super_streamtype` (`initSuperVolumeStateMap` sets bit *i* for
  stream *i*). Extra headroom per stream = `ro.vendor.audio.volume_super_index_add`.
- `enableSuperIndex(streamType, indexMax)` (called from the `VolumeStreamState` ctor):
  music → `mMusicVolumeStep = (indexMax/10)/15; indexSuper = SUPER_VOLUME_PROP * mMusicVolumeStep + indexMax`;
  other streams → `indexSuper = SUPER_VOLUME_PROP + indexMax`; otherwise `-1`.
- `getSuperIndex(...)` (reached from `VolumeStreamState.getMaxIndex()` when
  `isSuperVolumeEnable()`) returns `indexSuper` **only if** `indexSuper != -1` **and the
  stream has exactly one output device** **and that device's internal type == 2
  (speaker)** **and the calling package is non-null, not CTS, and not
  `android.uid.bluetooth`** (`isWhiteList`). Otherwise it returns `indexMax` → the extra
  range is invisible to Bluetooth/CTS callers and to non-speaker routes.
- `setSuperIndex(index, currentIndexMax, device, streamType)` (reached from
  `VolumeStreamState.setStreamVolumeIndex`, i.e. the HAL write) does **not** widen the HAL
  index range. With `maxIndex = currentIndexMax/10` and `device == 2`:
  - going above max → `AudioSystem.setParameters("SuperVolume=super_speaker_on;SuperGrade=<g>;SpkVolIdx=<i>[;SuperStream=<s>]")`
    where `g = (index - maxIndex)/mMusicVolumeStep` for music (else `index - maxIndex`),
    and `SpkVolIdx = index` (or `index/mMusicVolumeStep` when step == 2); then starts the
    temperature monitor and OneTrack-tracks `super / <stream> / speaker`.
  - falling back to/below max → `"SuperVolume=super_speaker_off;SuperGrade=0;SpkVolIdx=..."`
  - **returns `min(index, maxIndex)`**, so the framework index saturates at max and the
    extra loudness is applied entirely by the DSP parameter.
- Thermal guard: `TEMPERATURE_MONITOR_ENABLE` = `ro.vendor.audio.volume_super_temp_monitor == "true"`;
  a `FileObserver` on `/sys/class/thermal/thermal_message/board_sensor_temp`; levels
  `TEMPERATURE_FIRST_LEVEL` (default **37000**, `ro.vendor.audio.volume_super_temp_first_level`)
  and `TEMPERATURE_SECOND_LEVEL` (default **41000**, `..._second_level`). Above the first
  → `dropSuperVolume()`; above the second → `closeSuperVolume()`, which walks the
  stream-volume aliases and calls `mAudioManager.adjustStreamVolume(stream, -1, 0)` once
  or twice depending on the stored grade.
- CTS hiding: `isNeedSetIndexToMax(pkg, index, indexMax) = SUPER_VOLUME_ENABLE &&
  isCtsVerifier(pkg) && index > indexMax` (used by `VolumeStreamState.setIndex` to clamp)
  and `isNeedRescaleStepBySuperVolume(pkg) = SUPER_VOLUME_ENABLE && isCtsVerifier(pkg)`
  (used by `adjustStreamVolume` to compute the step from the *non*-super range). Both
  exist purely so CTS never observes the extra range.

### 7.4 Call-volume boost (通话/免提音量增强) and the extra UI step

Same helper, separate feature set:

- Props: `ro.vendor.audio.volume.boost.support` bitmask (1 voice-earpiece, 2 voice-speaker,
  4 voip-earpiece, 8 voip-speaker), `ro.vendor.audio.volume.boost.supportMtk` (same bits
  for the MTK paths), `ro.vendor.audio.call.vol_12_levels` (MTK 12-level earpiece).
  Runtime state is persisted in `persist.audio.call_volume_boost.enabled` and announced
  with the broadcast `miui.intent.action.CALL_VOLUME_BOOST_ON` (extra `boost_state`).
- Qcom path → `AudioManager.setParameters("volume_boost_support=voice_handset_on" /
  "voice_speaker_on" / "voip_handset_on" / "voip_speaker_on"`, and `..._off`).
- MTK path → `CustInfo=super_voice` (voice) and
  `CustInfo=voip,vol_level,extra,cust_scene,default` (voip), or
  `voice_volume_boost=true`.
- **The visible "one extra step" trick:**
  ```java
  public int calibrateMaxIndexForBoostIfNeed(int originalMaxIndex, int stream, Set<Integer> deviceSet) {
      ... if (!enabled || isCtsVerifierUid(...)) return originalMaxIndex;
      int maxIndexForBoostUI = originalMaxIndex + 10;      // +1 user step (internal x10)
      return maxIndexForBoostUI;
  }
  public int calibrateIndexForBoostIfNeed(int originalIndex, int stream, Set<AudioDeviceAttributes> deviceSet) {
      ... int streamMaxVolume = mAudioService.getStreamMaxVolume(stream);
      if ((originalIndex + 5) / 10 == streamMaxVolume - 1) return originalIndex + 10;
      return originalIndex;
  }
  ```
  i.e. while on a call (STREAM_VOICE_CALL, single device, earpiece 1 / speaker 2, mode 2
  or 3, and `CALL_VOLUME_BOOST_ENABLE`), the framework reports **one more step than it
  really has**, and moving onto that step turns the DSP boost on. That is 小米的
  "再按一格，音量增强".
- Trigger points: `onUpdateAudioMode`, `onHeadsetPlugStateChanged`,
  `onCommunicationDeviceChanged` (posts `updateCallVolumeBoostState` after 1000 ms), and
  `updateCallVolumeBoostState()` (requires exactly one output device for stream 0, and
  `getStreamVolume(0) == getStreamMaxVolume(0)`), plus `enableVoiceVoipVolumeBoost(...)`
  for the `adjustStreamVolume` path at max.

### 7.5 Absolute (Bluetooth/AVRCP) volume: non-linear low end

```java
public int getAbsoluteVolumeIndex(int index, int indexMax) {
    int step = indexMax / 150;                       // 10 when indexMax == 1500
    if (index == 0) return 0;
    if (index > 0 && index <= step * 5) {            // first 5 user steps
        int pos = (int) Math.ceil((index - step) / step);
        return (int) (indexMax * this.mPrescaleAbsoluteVolume[pos]) / 10;
    }
    return (indexMax + 5) / 10;
}
```
with the stub's **own** table `mPrescaleAbsoluteVolume = {0.5f, 0.7f, 0.85f, 0.9f, 0.95f}`
— note this is *different* from `AudioService.mPrescaleAbsoluteVolume = {0.985f, 1.0f, 1.03f}`
(see §0/§1.2), so the stub is not reusing the AOSP table. The intent is a deliberately
**non-linear bottom end** for absolute-volume devices instead of a plain linear split.
(The call site lives in `services.jar`'s absolute-volume path, so the unit convention of
`index`/`indexMax` cannot be re-verified now that only `miui-services.jar` is loaded; the
formula and the `indexMax/150` scaling are certain.)

### 7.6 Corrections to the `services.jar`-era reading

- `setStreamMusicOrVoiceCallIndex(index, stream, device)` is **a real behaviour hook, not
  telemetry**: on MIUI's worker thread (handler msg 26) it calls
  `AudioSystem.setParameters("audio_volume_stream_music_device_<outputDeviceName>=<index/10>")`
  for music, or `"audio_volume_stream_voice_call_device_<name>=<index>"` for stream 0.
  This is how the HAL/SmartPA/DSP learns the current per-device volume index. (The
  original §1.4 note about it was right; the "likely how SystemUI gets fast updates" guess
  was not — see the next bullet.)
- `updataVolumeAdjustCount(0|1)` is **analytics only**: after a 60 s per-type throttle it
  sends msg 28, which increments `MQSUtils.mKeyAdjustCount` / `mSliderAdjustCount`. It has
  no effect on volume, stepping or ramping. My earlier speculation that it distinguishes
  key-vs-slider for ramping purposes was wrong — it distinguishes them for statistics.
- Confirmed: there is **no key-hold ramp in the MIUI stub either**. The only framework-side
  ramp affordance remains AOSP's `VolumeController.suppressAdjustment` +
  `setVolumeControllerLongPressTimeoutEnabled`, and HyperOS's SystemUI never calls the
  latter (§6.3). So neither the framework nor SystemUI implements "hold to accelerate" —
  HyperOS simply lets input key-repeat drive 1/15-of-range steps.

### 7.7 The DSP/effect side (`MiAudioService`)

`MiAudioService` does **not** override the volume flow; it only carries the effect-chain
side and is reached through two stub hooks:

- `notifyVolumeIndexChangedToEffectMiAudioService(stream, index, device)` →
  `MiAudioService.notifyVolumeIndexChangedToEffect(...)` →
  `DolbyEffectController.setStreamVolumeForDolby(index)` when Dolby multi-volume is
  supported, and `MiSoundEffectController.setStreamVolumeForMiSound(index)` when MiSound
  multi-volume is supported. This is how the vendor effects follow the *fine* (150-step)
  index.
- `MiAudioService.adjustStreamVolume(stream, dir, flags, pkg, caller, uid, pid, tag,
  hasModifyAudioSettings, keyEventMode)` — the hook the `AudioService` calls at the end of
  `adjustStreamVolume` — only forwards `getDeviceStreamVolume(3, 2)` to
  `DolbyEffectController.receiveVolumeChanged(...)` when
  `ro.vendor.audio.dolby.*`-style Dolby tuning-by-volume is enabled.
- `AudioServiceStubImpl.notifyVolumeChangedToDolbyEffectController(context, stream, index)`
  → `DolbyEffectController.getInstance(context).receiveVolumeChanged(index)` for stream 3.

### 7.8 Still not visible (updated)

1. `com.android.server.audio.AudioServiceInjector` — referenced by
   `AudioServiceStubImpl.customMinStreamVolume(...)` and
   `adjustDefaultStreamVolumeForMiui(...)` (guarded by
   `isApplyMiuiCustom() = !ro.vendor.audio.skip_miui_volume_custom`), but **the class
   itself is not in `miui-services.jar`** — it lives in `framework.jar` /
   `miui-framework.jar`. That is where `ro.config.media_vol_steps`'s consumers and MIUI's
   min/default volume tables are finalised.
2. The actual on-device `getprop` values:
   `ro.config.media_vol_steps`, `ro.config.media_vol_default`,
   `ro.vendor.audio.volume.super_index_add`, `ro.vendor.audio.volume_super_streamtype`,
   `ro.vendor.audio.volume.boost.support`, `ro.vendor.audio.volume_super_temp_monitor`.
3. `R.dimen.volume_dialog_slider_max_deviation` and
   `R.dimen.volume_dialog_half_opened_offset` actual dp values (they live in
   `resources.arsc`, which the JADX MCP cannot hand out as a file).
4. Whether HyperOS wires `IAudioVolumeChangeDispatcher` (§2.6) into the panel; the
   SystemUI side seen here goes through the legacy `IVolumeController` path only.

---

## 8. Final answer in one page

**无极音量 (stepless media volume) — `services.jar` + `miui-services.jar`:**
`ro.config.media_vol_steps = 150` → `AudioService.<init>` sets
`MAX_STREAM_VOLUME[STREAM_MUSIC] = 150` (gated by `AudioServiceStubImpl
.isSupportSteplessVolume(3, pkg)`, which is true for everything except CTS) →
`VolumeStreamState.mIndexMax = 1500` → `updateIndexFactors()` pushes `0..150` into the
native volume group range → every client (`AudioManager.getStreamMaxVolume`) sees 150
steps. Key presses still move `150/15 = 10` steps (= 1/15 of the range) because of
`getMusicVolumeStep`. Extra headroom beyond max is 超级音量, a speaker-only DSP
`SuperVolume=` parameter with thermal back-off and CTS masking, not an index extension.

**流畅的音量切换 (smooth switching) — `SystemUI.apk`:**
AOSP 16's Compose volume dialog. Value motion = `Animatable` driven by
`spring(dampingRatio = 1.0, stiffness = 1500)`, re-targeted on every
`IVolumeController.volumeChanged` → `VolumeDialogControllerImpl.onVolumeChangedW` →
`VolumeDialogStateModel.streamModels` → `VolumeDialogSliderInteractor` → ViewModel
emission, with a 100 ms post-drag debounce and a `callbackFlow` buffer
(`16, DROP_OLDEST`). Panel reveal = spring `k=700 ζ=0.9` on `translationX = width*(1-v)`
with `alpha = ceil(v)`; overscroll = `PathInterpolator(0.15,0,0.2,1)` on `drag/3` capped by
`volume_dialog_slider_max_deviation`, sprung back with `k=800 ζ=0.6`; auto-dismiss after
3 s (`Settings.Secure "volume_dialog_dismiss_timeout"`), a11y-scaled. Xiaomi adds only
per-step haptics (`VolumeDialogTransformHelper.calculateHapticFeedbackState`), a
fixed-rotation dismiss listener, and the Dolby/MiSound volume callbacks.

---

## 9. Device configuration (verified on a HyperOS 3 device)

```
ro.config.media_vol_steps            = 150
ro.vendor.audio.volume_super_index_add   (unset → getInt default -1)
ro.vendor.audio.volume_super_streamtype  (unset → 0)
ro.vendor.audio.volume.boost.support     (unset → 0)
```

Working through the `VolumeBoostHelper` static initialisers (§7.3, §7.4):

- `SUPER_VOLUME_ENABLE = SUPER_VOLUME_PROP != -1` → **false** (property unset).
- `VOICE_VOIP_VOLUME_BOOST_PROP = 0` → every `*_VOLUME_BOOST_ENABLE` flag is false;
  `ro.vendor.audio.call.vol_12_levels` is unset → `MTK_VOICE_HANDSET_VOLUME_BOOST_ENABLE`
  false → `CALL_VOLUME_BOOST_ENABLE` false.
- `ENABLE = SUPER_VOLUME_ENABLE || CALL_VOLUME_BOOST_ENABLE` → **false**, so
  `AudioServiceStubImpl.init()` never constructs the helper
  (`if (VolumeBoostHelper.ENABLE) { mVolumeBoostHelper = new ... }`).

**Consequence: on this device the entire super-index / volume-boost subsystem is dead
code.** `enableSuperIndex` returns -1, `getSuperIndex` returns `indexMax`,
`setSuperIndex` returns `index` unchanged, `isSuperVolumeEnable` false,
`isNeedSetIndexToMax` / `isNeedRescaleStepBySuperVolume` false — the guards short-circuit
before touching the null helper. §7.3–§7.5 describe a *capability* of the build, not
behaviour of this phone.

So the complete list of **active** Xiaomi volume customisations on this device is:

1. `MAX_STREAM_VOLUME[STREAM_MUSIC] = 150` from `ro.config.media_vol_steps` (§7.1).
2. The `AudioServiceInjector` table edits in §10 (`adjustMaxStreamVolume`,
   `customMinStreamVolume`, `adjustDefaultStreamVolume`, `adjustVolumeSetting`).
3. The effect-chain index push (`notifyVolumeIndexChangedToEffect...`, §7.7) and the HAL
   parameter push (`setStreamMusicOrVoiceCallIndex`, §7.6) — both unconditional.
4. The SystemUI animation and per-step haptics (§6).

Still unchecked but relevant: `ro.vendor.audio.voice.volume.boost` (the *earpiece* call
boost uses `== "manual"`, a different property from `volume.boost.support`) and
`persist.vendor.audio.voice.spk_super_volume` (speaker call boost).

---

## 10. `miui_framework.jar`: `android.media.AudioServiceInjector`

The class the stub delegates to (§7.8). Note the package: **`android.media`**, not
`com.android.server.audio` — it is a framework-bootclasspath extension called both by
`AudioServiceStubImpl` and directly by the framework. On this device
`isApplyMiuiCustom() = !ro.vendor.audio.skip_miui_volume_custom` is true, so all of the
following are live.

### 10.1 The volume scale tables

```java
public static void adjustMaxStreamVolume(int[] maxStreamVolume) {
    for (int i = 0; i < maxStreamVolume.length; i++)
        if (i != 0 && i != 6 && i != 7) maxStreamVolume[i] = 15;      // not VOICE_CALL / SCO / SYSTEM_ENFORCED
}
public static void customMinStreamVolume(int[] minStreamVolume) { minStreamVolume[6] = 1; }   // BT SCO min 0 -> 1
public static void adjustDefaultStreamVolume(int[] defaultStreamVolume) {
    for (int i = 0; i < defaultStreamVolume.length; i++)
        if (i != 0 && i != 6) defaultStreamVolume[i] = 10;            // stock-15-scale default 10
}
public static int calculateStreamVolume(int streamType, int index, Context c) { return (index + 5) / 10; }
public static int calculateStreamMaxVolume(int streamType, int maxIndex, Context c) { return (maxIndex + 5) / 10; }
```

Ordering matters and explains the final table: AOSP's `int[]{5,7,7,15,7,7,15,7,15,15,15,15}`
→ the volume-group loop overwrites from the policy → `adjustMaxStreamVolume` forces **all
streams to 15 except 0/6/7** → `ro.config.vc_call_vol_steps` (if set) overrides stream 0 →
the stepless branch raises **music to 150**. Net result for this phone: music 150, voice
call 5, BT SCO 15, system-enforced 7, everything else 15; BT SCO minimum 1 (which is why
`VolumeStreamState.setStreamVolumeIndex` contains the `isStreamBluetoothSco && index == 0
→ 1` clamp seen in §1.4); every default 10 except voice call / BT SCO.

Note `calculateStreamVolume` / `calculateStreamMaxVolume` are plain `+5)/10` rounding —
they are **not** a volume curve; MIUI does not reshape the index→loudness mapping here.

### 10.2 The one-time 15 → 150 migration (the canonical Xiaomi recipe)

```java
public static void adjustVolumeSetting() {
    Context ctx = ActivityThread.currentApplication().getApplicationContext();
    ContentResolver cr = ctx.getContentResolver();
    if (Settings.System.getIntForUser(cr, "volume_use_150_level", 0, -2) == 0) {
        Settings.System.putIntForUser(cr, "volume_use_150_level", 1, -2);
        for (String k : {"volume_music_speaker", "volume_music_headset",
                         "volume_music_usb_headset", "volume_music_bt_a2dp"}) {
            int v = Settings.System.getIntForUser(cr, k, AudioSystem.DEFAULT_STREAM_VOLUME[3], -2);
            Settings.System.putIntForUser(cr, k, v * 10, -2);
        }
    }
}
```

This is exactly the problem ZTool's `FineVolumeSteps.migratePersistedMusicVolume` solves,
and it is worth comparing directly:

| | HyperOS | ZTool |
|---|---|---|
| Marker | `Settings.System "volume_use_150_level"` = 1 | `Settings.Global "ztool_fine_volume_steps_migrated"` = 1 |
| Scope | the four `volume_music_*` per-device keys, named explicitly | every `Settings.System` row matching `volume_music%` (catches future/unknown device suffixes too) |
| Factor | hard-coded `× 10` (15 → 150) | `TARGET_STEPS / stockMax`, computed from the actual stock max |
| Fallback default | `AudioSystem.DEFAULT_STREAM_VOLUME[3]` | same idea |

ZTool's is the more general version (any target step count, any device suffix). One
detail worth copying from Xiaomi: it also rewrites `AudioSystem.DEFAULT_STREAM_VOLUME`
usage as the migration *seed*, and it stores the marker in `Settings.System` (so it
survives and is visible) rather than `Settings.Global`.

### 10.3 Per-device "music volume before mute"

`saveAllDevicesMusicVolume` / `restoreAllDevicesMusicVolume` (private, called with the
`AudioService` instance, and `int maxIndexSrc, int maxIndexDst, int[] streamVolumeAlias`):
for each output device they copy `volume_music<suffix>` (suffix =
`"_" + AudioSystem.getOutputDeviceName(device)`) into / out of
`MiuiSettings.SilenceMode.VOLUME_MUSIC_BEFORE_MUTE<suffix>`, then call
`updateMusicStreamVolume(audioService)` — a **reflective call to
`AudioService.reloadMusicVolume()`** (a MIUI-added method on the services.jar side).
This is "remember the music level per output device across silent-mode toggles / device
switches", i.e. another mechanism that prevents audible jumps when the route changes.
(The decompiled device-mask loops are unreliable — jadx lost the mask initialiser — so
treat the loop bounds as "the connected device set".)

### 10.4 Framework-side earpiece call boost

```java
public static boolean needEnableVoiceVolumeBoost(int direction, boolean isMaxVol, int device,
                                                 int streamTypeAlias, boolean boostEnabled) {
    if (isXOptMode() || streamTypeAlias != 0 || device != 1
            || !"manual".equals(SystemProperties.get("ro.vendor.audio.voice.volume.boost"))) return false;
    if (direction == 1 && isMaxVol && !boostEnabled) return true;   // press up at max -> boost on
    return direction == -1 && boostEnabled;                        // press down -> boost off
}
public static boolean setVolumeBoost(boolean boostEnabled, Context context) {
    am.setParameters("voice_volume_boost=" + (boostEnabled ? "false" : "true"));
    sendVolumeBoostBroadcast(!boostEnabled, context);
    return !boostEnabled;
}
public static void sendVolumeBoostBroadcast(boolean boostEnabled, Context context) {
    intent = new Intent(ACTION_VOLUME_BOOST); intent.putExtra(EXTRA_BOOST_STATE, boostEnabled);
    context.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
}
```

Two things to flag: this is gated by a **different property** than §7.4
(`ro.vendor.audio.voice.volume.boost == "manual"`, the same one the stub reads into
`mVolumeBoostSupported`), and it is earpiece-only (`device == 1`, `alias == 0`) and
`isXOptMode()`-excluded. The parameter polarity here (`boostEnabled → "false"`) is the
opposite of `AudioServiceStubImpl.setVoiceHandsetVolumeBoostMtk`
(`boostOn → "true"`), so one of the two call sites is inverted — worth remembering if
this feature is ever ported.

`getActiveStreamType(boolean isInCommunication, int platformType, int suggestedStreamType,
int streamOverrideDelayMs, boolean DEBUG_VOL[, boolean])` exists here too (the 5-arg
overload delegates to the 6-arg), i.e. MIUI also overrides **which stream the volume keys
act on** — the direct analogue of the `getActiveStreamType` the ZTool ramp hook sits in
front of. Its body was not extracted (budget).
