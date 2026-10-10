# Game Assistant (`com.zui.game.service`) hardware telemetry — sources and ZTool fixes

Reverse-engineering notes for the ZTool Game Assistant feature (`hook/modules/gametool`).

## Test target

| Item | Value |
| --- | --- |
| Device | `TB710FU` (project `topaz`), `ro.config.lgsi.device.type=pad`, `ro.config.zui.devicetype=PAD` |
| ROM | `TB710FU_CN_OPEN_USER_Q00019.0_A16_ZUXOS_1.5.04.509_ST_260911`, Android 16, SELinux enforcing |
| App | `com.zui.game.service` versionName `2.3.0.9999`, versionCode `2309999`, targetSdk 35 |
| App process | `ps -AZ` → `u:r:platform_app:s0:c512,c768`, uid `u0_a124` (platform-signed system app, **not** root, **not** `system`) |
| CPU | 6 cores `cpu0..cpu5`; cpufreq policies `0`, `1`, `3`, `5`; `cpu5` = prime, `cpuinfo_max_freq` = 3302400 kHz |

## 1. Single telemetry source: `HWDataInterface` → Lenovo performance HAL

`com.zui.game.service.util.HWDataInterface` (singleton) is the only place the app reads
hardware counters. Its constructor picks one of two HAL flavours:

* AIDL `vendor.lenovo.hardware.performance.IPerformance`, service name
  `vendor.lenovo.hardware.performance.IPerformance/default` — used when the device is
  Inception / Kirby / Elden / Tenet / **Topaz**.
* HIDL `vendor.lenovo.hardware.performance.V1_0.IPerformance` — everything else.

TB710FU is `Topaz`, so the AIDL path is live. AIDL transaction ids (from the generated
`IPerformance.Stub` in the APK):

| Method | tid | Unit returned |
| --- | --- | --- |
| `getCpuCurFreq(int)` | 3 | kHz |
| `getCpuAvailableFreq(int)` | 1 | `int[]` kHz |
| `getGpuCurFreq()` | 4 | **Hz** |
| `getGpuMaxFreq()` | 5 | **Hz** |
| `getThemalTemp(int)` | 6 | milli-°C (Lenovo's spelling) |

Note the mixed units: CPU values are kHz, GPU values are Hz.

### App-side wrappers

* `getCpuCurFreq()` → `getCpuCurFreq(7)` — **core index 7 is hardcoded**.
* `getCpuMaxFreq()` → `max(getCpuAvailableFreq(7))`.
* `getTemp()` → `getThermalTemp(type)` where `type` depends on the device model:
  `2` for Asphalt/Inception, `4` for Elden/Kirby/Lapis, **otherwise `6`**.
* Thermal type constants: `THERMAL_TEMP_FRONT = 1`, `THERMAL_TEMP_BACK = 2`,
  `THERMAL_TEMP_CPU = 3`.

## 2. How the floating overlays get the values

```
Lenovo IPerformance HAL
        │
        ▼
HWDataInterface ──► StateLiveData.updateXpuTemp(showTemperature)
        │                    │
        │                    ├─► FloatingHwInfoView   (small floating HW window, LiveData observers)
        │                    ├─► FloatingMainViewNew / FloatingMainViewRight (CPU/GPU gauges)
        │                    └─► IslandManager.cpuFlow / gpuFlow / fpsFlow ─► IslandWindow
        └─► IslandManager.temperatureFlow (5 s loop) ─► IslandWindow
        └─► ItemBypassChargingViewModel._temperature (5 s loop) ─► bypass-charging battery-temp warning
```

* `StateLiveData.updateXpuTemp(boolean)` posts
  `Cpu(getCpuCurFreq(), getCpuMaxFreq())`, `Gpu(getGpuCurFreq(), getGpuMaxFreq())` and
  `String.format("%.1f℃", getTemp() / 1000)`.
* **`CpuGpuTemperatureFpsReader` is the only periodic driver**: an IO coroutine on a
  2000 ms delay calling `updateXpuTemp(showTemperature)`. Both its FPS and XPU loops
  early-return when `Settings.isPad()` is true.
* `FloatingMainViewNew` / `FloatingMainViewRight` call `updateXpuTemp(false)` once when the
  performance/overclock mode is switched — i.e. frequency only, temperature is not refreshed.
* `IslandManager.temperatureFlow` is a separate 5000 ms flow that calls
  `HWDataInterface.getTemp()` directly; `IslandWindow` collects it.
* `FloatingHwInfoView` formats: CPU via `Cpu.cur()` = kHz / 1e6 → GHz
  (`BigDecimal(..., HALF_UP)` 2 dp); GPU via `(int) Gpu.cur()` = Hz / 1e6 → integer MHz;
  temperature uses the pre-formatted `"%.1f℃"` string and the temp-only group strips `"℃"`.
* Battery **level** only comes from `com.zui.game.service.sys.receiver.BatteryReceiver`
  (`ACTION_BATTERY_CHANGED` → `level`/`scale`).

## 3. "Battery temperature" is not a real battery temperature

There is no battery-temperature read anywhere in the APK:

* No `EXTRA_TEMPERATURE`, no `BatteryManager` temperature property, no `/sys/class/power_supply` read.
* `BatteryReceiver` extracts only level, charge rate, multi-charging and plugged state.
* The bypass-charging "battery temperature" warning
  (`ItemBypassChargingViewModel._temperature`, 5 s, thresholds 39 / 41 / 43 °C) is computed as
  `HWDataInterface.getInstance().getTemp() / 1000.0f` — the very same HAL thermal value.

So every temperature the user sees (floating window, island ring, bypass-charging warning)
is one number: `HWDataInterface.getTemp()`, i.e. the HAL thermal type selected by device model.
The app merely *labels* it as a battery/shell temperature.

## 4. Why it is broken on TB710FU (measured against the live HAL)

Queried with `service call vendor.lenovo.hardware.performance.IPerformance/default <tid> ...`:

| Call | Result |
| --- | --- |
| `getCpuCurFreq(0)` … `(5)` | `672000` / `2707200` / `2707200` / `2707200` / `2707200` / `672000` kHz |
| `getCpuCurFreq(7)` | **`-2`** (error sentinel) |
| `getCpuAvailableFreq(7)` | 31 entries, max `3052800` kHz (real HW max is `3302400`) |
| `getGpuCurFreq()` / `getGpuMaxFreq()` | `231000000` / `903000000` Hz |
| `getThemalTemp(1)` / `(2)` / `(3)` | `24458` / `25642` / `32800` |
| `getThemalTemp(0)`, `(4)` … `(8)` | **`0`** |

TB710FU matches none of Asphalt / Inception / Elden / Kirby / Lapis, so `getTemp()` selects
type **6** → `0`. Therefore:

* CPU current frequency: `-2` → `Cpu.cur()` special-cases only `-1`, so the window shows `-0.00` GHz.
* Temperature: `0` → the window shows `0.0℃`.

`Cpu.max()` uses `max / 1e6` = `3.05` GHz, under-reporting the prime core's 3.3024 GHz.

Secondary display defect, independent of the HAL: `getTemp() / 1000` is **integer** division
before being boxed into `%.1f`, so the tenths digit is always `0` even on devices where
`getTemp()` returns a good value.

## 5. What ZTool changes

`CpuFrequencyFix` (pref `Fix_CpuClock`, default off) hooks `HWDataInterface`:

| Target | Replacement |
| --- | --- |
| `getCpuCurFreq()` | `/sys/devices/system/cpu/cpu<last>/cpufreq/scaling_cur_freq` |
| `getCpuCurFreq(int)` | same, argument ignored |
| `getCpuMaxFreq()` | `/sys/devices/system/cpu/cpu<last>/cpufreq/scaling_max_freq` |

`<last>` = highest-numbered `cpu[0-9]+` directory under `/sys/devices/system/cpu/`.
On TB710FU that is `cpu5`, giving the correct prime-core max `3302400` kHz — better than the
HAL's `3052800`.

`SocTemperatureFix` (pref `Fix_SocTemp`, default off) hooks:

| Target | Replacement |
| --- | --- |
| `getTemp()` | `/sys/class/thermal/thermal_zone9/temp` |
| `getThermalTemp(int)` | same, type ignored |

Both call `chain.proceed()` first and discard the result. Fallbacks: zones 0/1/2,
`/sys/devices/virtual/thermal/thermal_zone9/temp`, `/sys/class/hwmon/hwmon0/temp1_input`.

### Correctness caveats

* On TB710FU `thermal_zone9` is `aoss-0` (always-on-subsystem sensor), **not** a CPU or battery
  sensor; the HAL's CPU type (3) tracks the `cpuss-*` / `cpu-2-*` cluster. The hardcoded zone is
  device-specific and is not a SoC temperature on this model.
* Hooking `getThermalTemp(int)` unconditionally makes front / back / CPU all report the same
  zone, which breaks `getMaxShellTemp()` (type 2) and the semantics every other consumer expects.
* `scaling_max_freq` is the *current policy limit*, not the hardware maximum; under thermal or
  power capping it under-reports. `cpuinfo_max_freq` is the stable value.
* `readFallbackCpuFreq()` logs "Using default current freq: 2000000" but returns `0`;
  `DEFAULT_CURRENT_FREQ` / `DEFAULT_MAX_FREQ` are only reached from `catch` blocks.
  `getLastCpuCoreIndex()` logs its normal path at ERROR level.
* `getCpuCurFreq()` and `getCpuCurFreq(int)` are hooked redundantly — the no-arg overload is the
  only entry point the app calls.
* The 2 s reader is gated by `Settings.isPad()`, which is **true** on TB710FU
  (`isOtherPad()` = `!isLegionDevice && (isPadProduct() || isPadLgsi())`, and
  `ro.config.lgsi.device.type=pad`). On this tablet the periodic refresh therefore does not run
  and the observed values come from the island's 5 s loop plus the one-shot mode-switch updates.

## 6. Do the hooks work without root? — measured

The hooks execute inside the Game Assistant process, so they run with **its** UID and SELinux
domain. LSPosed injection needs root to install, but the injected code gets no extra privilege.

Read tests run as the exact app domain (`su -Z <context>` on the rooted test device), reading the
paths the hooks use:

| Path | `platform_app` (the app) | `untrusted_app` |
| --- | --- | --- |
| `/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq` | OK | OK |
| `/sys/devices/system/cpu/cpu5/cpufreq/scaling_cur_freq` | OK | OK |
| `/sys/devices/system/cpu/cpu5/cpufreq/scaling_max_freq` | OK | OK |
| `/sys/devices/system/cpu/cpu5/cpufreq/cpuinfo_max_freq` | OK | OK |
| `ls /sys/devices/system/cpu/` (needed by `listFiles()`) | OK | OK |
| `ls /sys/class/thermal/` | OK | OK |
| `/sys/class/thermal/thermal_zone9/temp` | OK | OK |
| `/sys/class/thermal/thermal_zone80/temp` | OK | OK |
| `/sys/class/kgsl/kgsl-3d0/gpuclk` | **DENIED** | **DENIED** |
| `/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies` | **DENIED** | **DENIED** |
| `/proc/stat` | **DENIED** | **DENIED** |

File labels: `/sys/devices/system/cpu/...` is `sysfs_devices_system_cpu`, thermal zones are
`sysfs_thermal`, both mode `0444`; the GPU nodes are `vendor_sysfs_kgsl_gpuclk`.

Conclusions:

1. **Both hooks are viable without root.** Every sysfs read and the directory listing they need is
   permitted for `platform_app` and even for `untrusted_app` on this vendor policy. No `su`, no
   `chmod`, no sepolicy rule required.
2. **A GPU fix cannot use this approach.** `/sys/class/kgsl/kgsl-3d0/*` is denied to app domains,
   so any GPU frequency/temperature fix has to go through the HAL (which already returns sane
   GPU values: 231 MHz / 903 MHz).
3. The `getLastCpuCoreIndex()` enumeration is safe because directory read on
   `/sys/devices/system/cpu/` is allowed; the only failure mode is a hot-unplugged top-numbered
   CPU, whose directory disappears and triggers the fallback.

## 7. TB375FC (`PERIDOT`, MediaTek) — open issue, hypotheses only

User report: on TB375FC the floating island shows neither temperature nor GPU; temperature matters
most (bypass charging monitoring). Nothing below is verified — it is a ranked hypothesis list plus
the exact data needed to confirm.

Two independent mechanisms can blank those two fields:

**(A) The temperature value is invalid.** `getTemp()` maps the model to a thermal type and TB375FC
matches none of the special cases (`DeviceUtils.PERIDOT = "TB375FC"` is only referenced for the
game-list selection in `FeaturesBaseOnRomKt`, never for telemetry), so it falls through to type `6`
— the same type that returns `0` on TB710FU. Additionally, TB375FC is **not** in
`HWDataInterface`'s AIDL model list (`isInception || isKirbyPrc || isKirbyRow || isEldenPrc ||
isEldenRow || isTenet || isTopaz`), so the app takes the **HIDL** `V1_0.IPerformance` branch there.
If that HIDL service is absent on the MediaTek ROM, `mHIDL_PerfService` and `mAIDL_PerfService` are
both null and every getter returns `-1` → temperature `0.0℃`, CPU `0.00`, GPU `0`.

**(B) Nothing polls the value.** `mCpu` / `mGpu` / `mTemperature` LiveData are written *only* by
`StateLiveData.updateXpuTemp()`. The only periodic caller is `CpuGpuTemperatureFpsReader`, whose
`handleXpuTemperature()` and `handleFps()` both early-return when `Settings.isPad()` is true.
TB375FC is a pad (`ro.config.lgsi.device.type=pad`), so the 2 s loop is disabled and CPU/GPU are
only ever posted by the one-shot `updateXpuTemp(false)` calls in the overclock-mode switch — which
do **not** refresh temperature. The island's temperature ring is the exception: it is fed by
`IslandManager.temperatureFlow`, a separate 5 s loop that calls `HWDataInterface.getTemp()` directly
and is *not* gated by `isPad()`.

Note (A) and (B) predict different symptoms for CPU. If CPU *does* display on TB375FC, the periodic
reader is running there and only the value sources are broken; if CPU is also blank, mechanism (B)
is active. This discriminates the two and is the first thing to confirm.

`Settings.isPad()` is referenced by 17 classes, so a blanket hook is unsafe; scope any fix to the
reader (caller check) or drive `StateLiveData.updateXpuTemp(true)` from ZTool's own timer.

### Candidate hook points

| Goal | Hook point | Notes |
| --- | --- | --- |
| Temperature only (user priority) | `HWDataInterface.getTemp()` | Already hooked by `SocTemperatureFix`. Replacing the hardcoded `thermal_zone9` with a **runtime probe over `getThermalTemp(0..N)`** (accept the first plausible milli-°C value) would be device-agnostic and needs no zone knowledge |
| Temperature, HAL unusable | sysfs thermal zone | Requires the TB375FC zone name; `thermal_zone9` is TB710FU-specific |
| CPU/GPU refresh | `Settings.isPad()` scoped to `CpuGpuTemperatureFpsReader`, or self-driven `updateXpuTemp(true)` | Re-enables the app's own 2 s loop; affects FPS too |
| GPU value | `HWDataInterface.getGpuCurFreq()` / `getGpuMaxFreq()` | Only needed if the HAL returns `-1`; the sysfs route is impossible (kgsl nodes are denied to app domains) |

### Data requested to close this out

1. TB375FC ROM dump — most valuable first:
   * the ROM's Game Assistant APK (`com.zui.game.service`) to diff `HWDataInterface`,
     `DeviceUtils`, `Settings` and `CpuGpuTemperatureFpsReader` against the TB710FU build;
   * the Lenovo performance HAL binary (`/vendor/bin/hw/vendor.lenovo.hardware.performance*`) —
     decompiling it yields the authoritative `getThemalTemp(type)` → thermal-zone mapping and which
     types are implemented;
   * `/vendor/etc/vintf/manifest.xml` to see whether the HAL is AIDL, HIDL, or both;
   * MediaTek thermal configs (`/vendor/etc/thermal*.conf`, `/vendor/etc/.tp/`) for zone names;
   * `build.prop` / vendor props for `ro.config.lgsi.device.type`, `ro.config.lgsi.project`,
     `ro.product.model`.
2. From the device (root is available since LSPosed is installed):

```bash
adb shell su -c 'service list | grep -i performance'
adb shell su -c 'lshal | grep -i lenovo'                 # HIDL presence
S=vendor.lenovo.hardware.performance.IPerformance/default
for t in 0 1 2 3 4 5 6 7 8; do echo -n "type $t: "; adb shell su -c "service call $S 6 i32 $t"; done
adb shell su -c "service call $S 3 i32 7"                # getCpuCurFreq(7)
adb shell su -c "service call $S 4"                      # getGpuCurFreq
adb shell su -c 'cat /sys/class/thermal/thermal_zone*/type'
adb shell getprop | grep -iE 'lgsi|product.model'
adb shell setprop log.tag.ZuiGameHelper DEBUG
adb shell logcat -c; adb shell logcat -s ZuiGameHelper:V   # while the island is visible in a game
```

## Reproduction

```bash
# CPU topology / cpufreq
adb shell su -c 'ls -d /sys/devices/system/cpu/cpu[0-9]*'
adb shell su -c 'cat /sys/devices/system/cpu/cpu5/cpufreq/cpuinfo_max_freq'

# HAL probe (AIDL transaction ids from IPerformance.Stub)
S=vendor.lenovo.hardware.performance.IPerformance/default
adb shell su -c "service call $S 3 i32 7"   # getCpuCurFreq(7)      -> -2
adb shell su -c "service call $S 1 i32 7"   # getCpuAvailableFreq(7)
adb shell su -c "service call $S 6 i32 6"   # getThemalTemp(6)      -> 0

# Per-domain file access (KernelSU `su -Z`)
adb shell su -Z u:r:platform_app:s0:c512,c768 -c 'cat /sys/class/thermal/thermal_zone9/temp'

# Thermal zone inventory
adb shell su -c 'cat /sys/class/thermal/thermal_zone*/type'
```
