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

## 7. TB375FC (`PERIDOT`, MediaTek) — measured

Device: `TB375FC` / project `peridot` / model `NP03J` / MediaTek Dimensity 8300 / `ro.config.lgsi.device.type=pad`
/ ZUXOS 1.5.10.123. Reporter's symptom set: **CPU frequency works, GPU frequency missing, temperature missing.**

### 7.1 HAL measurements

`service list` shows the Lenovo AIDL service is present:

```
304 vendor.lenovo.hardware.performance.IPerformance/default: [vendor.lenovo.hardware.performance.IPerformance]
```

| Call | Result | Reading |
| --- | --- | --- |
| `getCpuCurFreq(7)` (tid 3) | `0x00331df0` | **3,350,000 kHz** — valid |
| `getGpuCurFreq()` (tid 4) | `0xfffffffe` | **-2** — not implemented by the vendor HAL |

Two conclusions follow:

* **CPU frequency is fine on TB375FC because the SoC has 8 cores.** The app's hardcoded index `7` is
  in range here, so `getCpuCurFreq(7)` returns a real value. The same hardcoded `7` is what breaks
  TB710FU (6 cores → `-2`). `CpuFrequencyFix` is therefore unnecessary on TB375FC, and the TB710FU
  summary string in the UI should not be read as "the app is generally broken".
* **GPU frequency is a vendor HAL gap, not an app bug.** `getGpuCurFreq()` returns `-2`, so
  `Gpu.cur()` becomes `-2 / 1e6` and the UI prints `0`. ZTool cannot fix this through the HAL; it
  would need a MediaTek-specific sysfs fallback (Dimensity 8300 uses Mali-G615, so the Qualcomm
  `kgsl` nodes do not apply). The reporter rates GPU as unimportant.

### 7.2 Thermal zone inventory

`cat /sys/class/thermal/thermal_zone*/type` and `.../temp` (68 zones). **The shell expands the glob
lexicographically, not numerically** — `thermal_zone9` is the *last* entry, not the tenth. This is
confirmed on TB710FU, where `thermal_zone9/type` reads back as `aoss-0` and `aoss-0` is the final
element of the expanded list (numeric order would put `cpu-2-1-1` at index 9). Decoded for TB375FC:

| zone | type | temp (milli-°C) |
| --- | --- | --- |
| 22 | `cpu-big-core7-1` | 78726 |
| 23 | `cpu-big-core7-2` | 81686 |
| 26–29 | `soc-top1..4` | 47663 / 47586 / 47788 / 48417 |
| 30–33 | `soc-bot1..4` | 47984 / 49725 / 51836 / 53349 |
| 36 / 37 | `gpu1` / `gpu2` | 46315 / 45445 |
| 50 | `ap_ntc` | 38633 |
| 55 | `main_board_ntc` | 30932 |
| 58–64 | `vtskin-max`, `vtskin1..6` | `-274000` (unavailable sentinel) |
| 66 | `battery_mm8013` | 33000 |
| 67 | `battery` | 30500 |
| **9** | **`soc_max`** | **68026** |

**`thermal_zone9` is `soc_max` on TB375FC** — i.e. exactly the node ZTool's `SocTemperatureFix`
already reads. The hardcoded path is therefore *correct on this device too*, which makes the fix a
configuration problem rather than a porting problem.

Note also that TB375FC exposes **none** of the zone names the app's `RomFeatures.temperatureText`
(`TemperatureType`) refers to — `user_temp`, `front_temp`, `user_front_temp`,
`skin-msm-therm-usr` are all Qualcomm/ZUI-specific and absent here. Those names are not used by the
island (the island reads `HWDataInterface.getTemp()`), so this only matters if a future fix wants to
reuse `TemperatureType`.

### 7.3 Why the app still shows nothing

The island renders the value as `String.format("%.1f", value / 1000.0f)` and always calls
`setText` — there is no range or visibility guard, and `IslandManager.temperatureFlow` polls
`HWDataInterface.getTemp()` every 5 s independently of the `isPad()` gate. (The integer-division
defect that pins the tenths digit to `.0` is in `StateLiveData.updateXpuTemp`, i.e. the small
floating window, *not* the island.) So a working hook would visibly change the island readout.

The reporter then enabled **修复 SoC 温度显示** and still got no reading. Because zone 9 *is*
`soc_max` on this device, resolving the zone by name lands on the same file — the name-based change
cannot by itself explain or fix this symptom. Three failure modes remain:

1. **LSPosed scope not enabled for `com.zui.game.service`** (most likely). CPU frequency looks
   correct *natively* on TB375FC because core index 7 is valid there, so it proves nothing about
   whether hooks are running. With the scope off, every ZTool hook in that process is a no-op,
   including `SocTemperatureFix` — and no ZTool log line will appear for that package at all.
2. **Hook install failed on this ROM's build** — `HWDataInterface` may have been renamed or
   refactored; the log then shows `Failed to hook ZUI game service!`.
3. **The read is denied** for `platform_app` on this ROM. On TB710FU every `sysfs_thermal` file was
   readable by both `platform_app` and `untrusted_app`, but MediaTek may label zones differently.

A single `logcat -s ZToolXposedModule` capture taken while the island is visible separates all
three: no output at all for the package ⇒ scope; `Failed to hook` ⇒ build mismatch;
`Hook executed successfully.` plus `Thermal candidate ... = unreadable` ⇒ denial. The new
implementation logs every candidate zone name with its raw value (or `unreadable`) at DEBUG, and the
selected zone at INFO, so one capture is enough.

### 7.4 Implemented: name-based zone resolution

`SocTemperatureFix` now delegates to `ThermalZoneSource`, which scans `/sys/class/thermal/thermal_zone*`
and picks the first zone with a plausible value (5–150 °C, which rejects the `-274000` / `-40000`
sentinels, `0`, and non-temperature zones such as `vbat` at 4235 mV) in this order:

1. preferred zone names — `soc_max`, `user_temp`, `skin-msm-therm-usr`, `skin-msm-therm`,
   `back_temp`, `user_back_temp`, `front_temp`, `user_front_temp`, `soc_therm`, `mtktscpu`,
   `ap_therm`;
2. the previous hardcoded paths (`thermal_zone9`, zones 0/1/2, the virtual zone 9, hwmon);
3. any remaining zone whose name contains `soc`, `cpu`, `skin` or `ap_`.

`user_temp` is ranked second because it is the zone name the app's own `RomFeatures.temperatureText`
(`TemperatureType.UserTemp`) prefers for most models. On TB710FU this resolves to
`skin-msm-therm`/`user_temp` (~31 °C) instead of `aoss-0` (32.4 °C) — a ~1 °C shift that is more
semantically correct, and the reason the hardcoded paths are kept only as tier 2. The resolved path
is cached and re-resolved (throttled to once per 30 s) when a read fails.

### 7.5 Remaining recommendations

| Goal | Action |
| --- | --- |
| Confirm the TB375FC failure mode | One `logcat -s ZToolXposedModule` capture; see 7.3 |
| Temperature on TB375FC | Likely no further code change — the same file is already targeted. If 7.3 case 3 is confirmed, tier 3 of the resolver should already find an alternative zone; check the candidate log for one that is readable |
| GPU frequency | Not fixable through the HAL on TB375FC (`getGpuCurFreq()` = `-2`). Needs a MediaTek Mali-G615 sysfs fallback; the reporter rates it unimportant, so leaving it is reasonable |
| CPU frequency | Do **not** re-map the hardcoded index globally. Index `7` is invalid on TB710FU (6 cores) but valid on TB375FC (8 cores); the existing "read the last core" replacement is the right generalisation |

### 7.6 Still open

`getThemalTemp(0..8)` values on TB375FC, and a `logcat -s ZToolXposedModule` capture taken while the
island is visible with `Fix_SocTemp` enabled. A TB375FC ROM dump is still useful for diffing
`HWDataInterface` / `DeviceUtils` / `Settings` / `CpuGpuTemperatureFpsReader`, but the thermal-zone
data already collected makes it non-blocking.

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

Caveat: the glob is expanded **lexicographically**, so `thermal_zone9` is the last element of
`thermal_zone*` and `thermal_zone10` comes second. Do not index the output numerically — always read
`thermal_zone<N>/type` directly when a specific zone matters.

On-device probe without `adb` (Termux `adb` may be unusable):

```bash
su -c 'for t in 0 1 2 3 4 5 6 7 8; do echo -n "type $t: "; \
  service call vendor.lenovo.hardware.performance.IPerformance/default 6 i32 $t; done'
su -c 'cat /sys/class/thermal/thermal_zone9/type; cat /sys/class/thermal/thermal_zone9/temp'
```

ZTool's hook logger tag is `ZToolXposedModule` (`ModuleLog.TAG`), so hook install failures are
visible with `logcat -s ZToolXposedModule`.
