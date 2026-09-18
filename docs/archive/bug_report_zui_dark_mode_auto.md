# 问题反馈：ZUXOS 深色模式"日落到日出"自动切换失效（ui_night_mode_override_off 残留）

**适用机型/系统**：ZUX OS，基于 Android 16（services.jar 版本以本文反编译信息为准）
**反馈模块**：系统框架（system_server）/ 设置 APP 深色模式页面
**严重程度**：中——功能完全失效，但可通过手动清除一个 Settings 值恢复

## 现象

用户在 设置 → 显示 → 深色模式 中选择"日落到日出"（`UiModeManager.setNightMode(0)`，即 `MODE_NIGHT_AUTO`）后，系统不再在日落/日出时刻自动切换亮色/暗色主题，主题被长期锁定为亮色。以下操作均无法恢复：

- 熄屏再亮屏（等待延迟应用路径执行）
- 重新选择"日落到日出"单选项
- 重新开关省电模式

## 诊断过程与证据

1. `dumpsys uimode` 显示 `mNightMode=0 (auto)`，调度模式已正确设置；`mZuiNightModeOverride=0`、`mPowerSave=false`、`mWaitForDeviceInactive=false`，排除了 ZUI 主题钉死、省电限制与熄屏延迟路径。
2. `logcat -s TwilightService` 显示 twilight 计算完全正常：定位成功、日出日落时间正确、状态在日落时刻后正常更新为夜间（例：`TwilightState { sunrise=09-19 05:59 sunset=09-18 18:16 }`，当前时间 21:15）。
3. 但同一份 dump 中 `mOverrideOn/Off=false/true`、`mCurUiMode=0x11`（日间）、`mComputedNightMode=false` —— twilight 已判定"夜间"，结果却被改写为"日间"。
4. `settings get secure ui_night_mode_override_off` 返回 `1`。手动置 0 并触发一次重算（`cmd uimode night no` → `night auto`）后，`mComputedNightMode=true`、`mCurUiMode=0x21`（暗色），自动切换立即恢复。

## 根因分析（基于 services.jar 反编译）

`com.android.server.UiModeManagerService` 中存在一条 AOSP 没有的 ZUI 定制覆盖链，配合两处细节形成"永久覆盖"：

1. **覆盖值被持久化**：`mOverrideNightModeOff` 从 `Settings.Secure.ui_night_mode_override_off` 读取，且 `persistNightModeOverrides()` 会把它写回磁盘。AOSP 上游的 override 设计是**单次性的**（快捷磁贴临时覆盖一次，下次 twilight 重算时自清），不应持久化。
2. **强制改写 twilight 结果**：`updateComputedNightModeLocked()` 中：

   ```java
   } else if (this.mOverrideNightModeOff && z) {
       z = false;   // twilight 判定夜间，被强制改回日间
   }
   this.mComputedNightMode = z;
   ```

3. **自清路径失效**：正常情况下 `updateComputedNightModeLocked()` 尾部会调用 `resetNightModeOverrideLocked()` 清除覆盖，但存在条件分支：当 `mNightMode == 0` 且 `getLastTwilightState() == null` 时直接 return，不清除。而调度侧（`mTwilightListener`）在屏幕亮着时只注册 `SCREEN_OFF` 延迟监听、不重算配置，导致实际到达的自清机会极少；一旦某次覆盖发生后系统再未走到自清分支，持久化的 `ui_night_mode_override_off=1` 就会在每次开机时被重新读入，永久压制自动切换。

推测触发场景：用户在深色模式自动调度为"日落到日出"期间，通过控制中心深色模式磁贴手动关闭了一次深色模式（该操作写入 override_off），此后 override 未被正常清除并意外持久化。

## 建议修复方向

任选其一或组合：

1. **遵循 AOSP 单次语义**：`ui_night_mode_override_on/off` 不持久化，或在 `resetNightModeOverrideLocked()` 成功清除后不再写回 `Settings.Secure`；
2. **修补自清条件**：`updateComputedNightModeLocked()` 中 `mNightMode == 0 && twilightState == null` 的提前 return 改为仍执行 override 自清（或放宽自清触发条件，例如在每次 `setNightMode(0)` 进入 auto 时先重置 override）；
3. **设置页防御**：深色模式设置页选择"日落到日出"时（`DarkModeScheduleSelectorController.onPreferenceChange` 的 sunrise 分支），显式清除 `ui_night_mode_override_on/off` 两个 Secure 值，保证用户主动选择调度时覆盖不会残留。

## 复现步骤

1. 深色模式调度设为"日落到日出"；
2. 在夜间（ twilight 处于夜间状态时）通过控制中心磁贴手动关闭深色模式（或以其它途径写入 `ui_night_mode_override_off=1`）；
3. 次日观察：日落时刻后主题不再自动切换，且重启后依旧；
4. `adb shell settings get secure ui_night_mode_override_off` 确认为 `1`；
5. `adb shell settings put secure ui_night_mode_override_off 0` + `adb shell cmd uimode night no` + `adb shell cmd uimode night auto` 后恢复正常。

## 用户侧临时解决方法（需 Root）

```bash
adb shell settings put secure ui_night_mode_override_off 0
adb shell cmd uimode night no
adb shell cmd uimode night auto
```
