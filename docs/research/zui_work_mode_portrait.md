# ZUI 工作模式（PC 模式）竖屏适配

## 结论

工作模式的横屏由 system_server 中三处硬编码共同保证，全部绕过之后 `WORK_MODE_PORTRAIT`
可以让工作桌面跟随设备方向旋转（已在 TB710FU / ZUX 17.5 上验证：`mRotation=2`、
`2000x3200`、桌面与任务栏正常显示）。

## 三层强制横屏

1. **旋转锁**
   - `com.android.server.wm.PcModeBlackAnimation#fixOrientation(true)` 在进入工作模式时调用
     `WindowManagerService#freezeDisplayRotation(0, ROTATION_90, "PcModeBlackAnimation")`。
   - 该调用最终进入 `DisplayRotation#setUserRotation(LOCKED, ROTATION_90, ...)`，并顺带把
     `Settings.System.ACCELEROMETER_ROTATION` 写成 0，所以“进入工作模式后自动旋转被关掉”。
   - 退出时 `fixOrientation(false)` 走 `thawDisplayRotation` 或恢复进入前的 rotation，
     `setUserRotation(FREE, ...)` 再把 `accelerometer_rotation` 写回 1。
   - SystemUI 的 `NavigationBar#onViewAttached` 还会在 2 秒后通过 binder 再 freeze 一次，
     因此只拦 `PcModeBlackAnimation` 不够，需要在 `DisplayRotation#setUserRotation` 统一改写。

2. **容器方向**
   - `com.android.server.wm.WindowContainer#getOverrideOrientation()` 在
     `OVDesktopController.isUnderPcDesktopMode()` 为真时，只要 `mOverrideOrientation != LOCKED`
     就直接返回 `SCREEN_ORIENTATION_LANDSCAPE`。
   - 这是纯 getter 覆写，没有任何 setter 调用，所以 dumpsys 里所有 Activity 都显示
     `requestedOrientation=LANDSCAPE`、`manifest-orientation=UNSPECIFIED`。

3. **显示方向**
   - `com.android.server.wm.DisplayContent#getOrientation()` 在相机策略之后、其余计算之前：
     `if (OVDesktopController.isUnderPcDesktopMode()) return LANDSCAPE;`
   - 这一层决定 `mCurrentAppOrientation` 与 `DisplayRotation` 的首选方向，是“旋转模式已经是
     FREE 但屏幕仍不转”的直接原因。

## 实现

`hook/modules/systemframework/WorkModePortrait.kt`（模块名 `work_mode_portrait`）：

- `DisplayRotation#setUserRotation`：在 PC 模式（`persist.sys.zui.pcmode` 或 reason 含
  `PcMode`）下把 `LOCKED/ROTATION_90` 改写为 `FREE`，同时借原方法把
  `accelerometer_rotation` 写回 1。
- `WindowContainer#getOverrideOrientation`：PC 模式下返回原始 `mOverrideOrientation`；
  PC 桌面自身的 Activity（包名取自 `OVDesktopController` 的
  `ZUI_DESKTOP_LAUNCHER_PACKAGE_NAME` / `ZUI_EXTEND_DESKTOP_LAUNCHER_PACKAGE_NAME`）
  按 unspecified 处理。
- `DisplayContent#getOrientation`：PC 模式且顶层 Activity 的
  `ActivityInfo.screenOrientation` 为 unspecified 时，把 PC 快捷返回的 `LANDSCAPE` 改成
  `UNSPECIFIED`，让显示跟随传感器/用户旋转；声明了固定方向的应用保持原值。PC 桌面自身的
  Activity 同样按 unspecified 处理，否则 `PcAllAppsActivity` 的 `sensorLandscape` 会在打开
  “全部应用”时把屏幕拉回横屏，也会让进入工作模式时的首屏停留在横屏。

三个 Hook 都只在开关打开且 PC 模式下生效；开关关闭时行为与官方一致。

## 注意

- `DisplayContent#getOrientation` 只在 `DisplayContent#updateOrientation` 里被调用，因此
  开关切换后需要一次配置/窗口变化（重新进入工作模式即可）才生效。
- 当前只对“清单方向为 unspecified”的顶层 Activity 放行，PC 桌面自身的 Activity 例外；
  清单里写死 `portrait` 的第三方应用在 PC 模式下仍会保持横屏。后续若要支持，需要在
  `getOrientation` 的旁路里按 `screenOrientation` 映射到 `PORTRAIT/LANDSCAPE`，而不是简单
  返回 `UNSPECIFIED`。
- `PcAllAppsActivity` 的清单是 `sensorLandscape`；桌面例外会把它按 unspecified 处理，
  打开“全部应用”和进入工作模式首屏都会跟随设备方向。
- 手动验证（TB710FU / ZUX 17.5）：开启开关后进入工作模式即为竖屏；“全部应用”抽屉不再拉回
  横屏；返回桌面无需先打开竖屏应用。

## 已知问题：抽屉/回桌面的横屏快照闪烁

现象：打开“全部应用”抽屉、或从应用回到工作桌面时，会短暂看到横屏画面。

排查结论（Transition 日志 + 逐帧截图）：

- 逐帧 `screencap` 全部是 2000x3200，显示并没有真的转横屏；
- `WindowManagerShell` 的过渡项是
  `CHANGE ... sb=Rect(0,0-3200,2000) eb=Rect(0,0-2000,3200) r=1->0`，即窗口从横屏 bounds
  动画到竖屏；
- 来源是 `TaskLaunchParamsModifier.resolveOrientation` 在 PC 模式下对启动 bounds 的横屏强制
  （`bounds` 为空的分支，以及 `getPCModeAppForceOrientation` 的 per-app 值）。

失败的尝试（已回退）：Hook 三参数
`resolveOrientation(ActivityRecord, TaskDisplayArea, Rect)`，对桌面包返回当前显示方向。
结果横屏快照仍在，并且进入工作模式后桌面会停留在横屏，直到打开一个竖屏应用才触发方向重算。

后续可行方向：

1. 在 SystemUI 侧抑制这次 `CHANGE` 过渡的旋转动画
   （`EnterOrExitPcModeTransitionHandler`），让配置切换即时完成；
2. 只修正“重复打开已有 task”时的初始 bounds，不干预首次进入工作模式的 task 创建；
3. 接受该过渡动画，作为已知视觉瑕疵记录。

## 剩余工作：Work Launcher 竖屏布局

方向链路已验证可用，但 `com.zui.desktoplauncher` 的桌面布局仍按横屏设计：

- 桌面图标集中在左侧一列，没有按竖屏重新排布网格；
- 任务栏仍在底部横向排列（可用，但图标间距/分组未针对竖屏调整）；
- `WorkLauncher` / `PcAllAppsActivity` 的 grid 配置、窗口默认 bounds 需要按竖屏重新计算。

待办入口见根目录 `TODOS.md` 的“竖屏电脑模式”一项。
