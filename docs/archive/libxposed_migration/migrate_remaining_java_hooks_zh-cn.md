# 迁移剩余全部 Java Hook 到 Kotlin 计划

> 状态: ✅ 全部完成(22 个 Hook + 1 个工具类均已迁移 Kotlin, 分 6 批提交)
> 目标: 将 `hook/modules/` 下剩余的 22 个 Java Hook + 1 个 Java 工具类全部迁移为 Kotlin,
> 消除项目内 Java Hook 代码, 达成 AGENTS.md 要求的"新代码必须 Kotlin"。

## 0. 背景与约束

- 当前 `hook/modules/` 有 **61 个 Kotlin** / **23 个 Java** 文件。
- 23 个 Java 文件中, 22 个是 Hook 类, **1 个是纯工具类** `CustomDateFormatter`(非 Hook,
  被 Hook 与 Repository 双侧引用)。
- 所有 22 个 Hook 均已注册在 `HookManager.kt`, 全部偏好键已存在于 `PreferenceKeys.kt`,
  全部作用域已存在于 `ScopeKeys.kt` → **不需要新增任何键/作用域**(`OwnerInfoHook` 拆分方案
  也复用现有 `AUTO_OWNER_INFO` 键)。
- 迁移原则(参照已有 Kotlin Hook 范式):
  - 继承 `AppHookModule` / `SystemHookModule`(双回调模块拆分为两个单回调)。
  - `getModuleName()` 返回 `PreferenceKeys.CONSTANT.name`, 禁止手写字符串。
  - `getTargetPackages()` 返回 `ScopeKeys.CONSTANT.packageName`。
  - 配置读取用 `remotePreferences` 属性(Java 的 `getRemotePreferences()` 对应)。
  - Hook 用 `hookWithId(target, id) { chain -> ... }` lambda 形式。
  - 日志用 `logger.<level>`。
  - 反射查找优先 `findMethod`/`findField`(自带继承链上溯, 见 `HookReflectionHelper.kt`)。
  - 混淆名查找优先离线 DexKit 索引 `DexIndexStore.string(...)`, 缺失回退硬编码。
- 参考范式文件:
  - 标准 App Hook: `hook/modules/ota/LenovoOTAHook.kt`
  - 系统框架 Hook: `hook/modules/systemframework/NoMorePasswordPer24H.kt`
  - 索引化 App Hook: `hook/modules/mobiledesktop/DisableNearbyShareAutoOffHook.kt`
  - 双回调拆分先例: `DisableGameAudioApp.kt`(gametool) + `DisableGameAudio.kt`(systemframework)

## 1. 现状盘点(23 个 Java 文件)

### 1.1 Hook 类清单与分类

| # | 文件 | 模块 | 规模 | 特殊点 |
|---|------|------|------|--------|
| 1 | `gametool/AutoMistakeTouchHook.java` | GameTool | 大(~280) | Handler.postDelayed 延迟任务 |
| 2 | `gametool/CpuFrequencyFix.java` | GameTool | 中 | 无 |
| 3 | `launcher/misc/DisableForceStop.java` | Launcher | 大(~470) | **已用 DexIndexStore** |
| 4 | `launcher/misc/RecentTaskMemoryViewHook.java` | Launcher | 大(~460) | 无 |
| 5 | `mobiledesktop/AutoAcceptFileTransferHook.java` | MobileDesktop | 中(~207) | **待索引化**(4 链式查找) |
| 6 | `safecenter/DisableAllVirusScans.java` | SafeCenter | 中 | 双目标包 |
| 7 | `safecenter/EnableAutorunByDefault.java` | SafeCenter | 小 | 双目标包 |
| 8 | `setting/AppInfoHeaderDetailsHook.java` | Settings | 中 | 无 |
| 9 | `setting/OwnerInfoHook.java` | Settings/System | 大(~473) | **双回调 + BroadcastReceiver + 网络线程, 需拆分** |
| 10 | `systemui/misc/CustomControlCenterDate.java` | SystemUI | 大(~420) | 依赖 CustomDateFormatter |
| 11 | `systemui/misc/NotificationCenterTransparency.java` | SystemUI | 中 | 无 |
| 12 | `systemui/qs/BrightnessSliderPercentageHook.java` | SystemUI | 大(~517) | 无 |
| 13 | `systemui/qs/ControlCenterNoTileLabelsHook.java` | SystemUI | 中 | 无 |
| 14 | `systemui/qs/CustomQsColor.java` | SystemUI | 中 | 无 |
| 15 | `systemui/qs/CustomQsRoundCorner.java` | SystemUI | 中 | 无 |
| 16 | `systemui/qs/VolumeSliderPercentageHook.java` | SystemUI | 大(~510) | 无 |
| 17 | `systemui/statusbar/CustomStatusBarClock.java` | SystemUI | 大(~380) | 依赖 CustomDateFormatter |
| 18 | `systemui/statusbar/NativeNotificationIcon.java` | SystemUI | 小 | 无 |
| 19 | `systemui/statusbar/NotificationIconHook.java` | SystemUI | 中 | 无 |
| 20 | `systemui/statusbar/StatusBarClockSecondsHook.java` | SystemUI | 中 | 无 |
| 21 | `systemui/statusbar/SystemUIBatteryHook.java` | SystemUI | 大(~290) | 资源名反射 |
| 22 | `systemui/statusbar/SystemUINetworkSpeeddoublelayerHook.java` | SystemUI | 大(~310) | **已用 DexIndexStore** |

### 1.2 非 Hook 工具类

- `systemui/misc/CustomDateFormatter.java`(~280): 日期格式化工具(农历/节气/时辰等),
  被 `CustomControlCenterDate`、`CustomStatusBarClock` 以及 **两个 Repository**
  (`ControlCenterSettingsRepository.kt:76`、`StatusBarSettingsRepository.kt:51`)引用,
  **迁移时必须保持类名、包名、静态方法签名不变**。

### 1.3 关键事实(已核实)

- 所有 moduleName 字符串均已在 `PreferenceKeys.kt` 有对应 BoolKey:
  - `"auto_mistake_touch"`→`AUTO_MISTAKE_TOUCH`, `"Fix_CpuClock"`→`FIX_CPU_CLOCK`,
    `"disable_force_stop"`→`DISABLE_FORCE_STOP`, `"launcher_recent_task_memory_view"`→`LAUNCHER_RECENT_TASK_MEMORY_VIEW`,
    `"auto_accept_file_transfer"`→`AUTO_ACCEPT_FILE_TRANSFER`, `"disable_all_virus_scans"`→`DISABLE_ALL_VIRUS_SCANS`,
    `"default_enable_autorun"`→`DEFAULT_ENABLE_AUTORUN`, `"app_details"`→`APP_DETAILS`,
    `"auto_owner_info"`→`AUTO_OWNER_INFO`, `"Custom_ControlCenterDate"`→`CUSTOM_CONTROL_CENTER_DATE`,
    `"notification_center_blur"`→`NOTIFICATION_CENTER_BLUR`, `"control_center_no_tile_labels"`→`CONTROL_CENTER_NO_TILE_LABELS`,
    `"qs_color"`→`CUSTOM_QS_COLOR`(开关键), `"qs_round_corner"`→`QS_ROUND_CORNER`,
    `"Custom_StatusBarClock"`→`CUSTOM_STATUSBAR_CLOCK`, `"NativeNotificationIcon"`→`NATIVE_NOTIFICATION_ICON`,
    `"notification_icon_limit"`→`NOTIFICATION_ICON_LIMIT`, `"StatusBarDisplay_Seconds"`→`STATUS_BAR_DISPLAY_SECONDS`,
    `"systemui_battery_percentage"`→`SYSTEMUI_BATTERY_PERCENTAGE`, `"systemui_network_speed_doublelayer"`→`SYSTEMUI_NETWORK_SPEED_DOUBLELAYER`。
  - `BrightnessSliderPercentageHook`/`VolumeSliderPercentageHook` 已直接返回
    `PreferenceKeys.BRIGHTNESS_SLIDER_PERCENTAGE.name` / `VOLUME_SLIDER_PERCENTAGE.name`。
- 已用 DexIndexStore 的: `DisableForceStop`(FORCE_STOP_METHOD)、`SystemUINetworkSpeeddoublelayerHook`(HANDLER_INNER_CLASS)。
- `CustomDateFormatter` 类内 `CUSTOM_PATTERNS` 静态 Map 用 `static {}` 初始化 → Kotlin 用 `companion object` + `init` 或 `mapOf`。

## 2. 迁移策略

### 2.1 分批顺序(风险从低到高, 每批独立编译验证)

- **批次 A(纯直译, 无特殊依赖)**: `CpuFrequencyFix`、`EnableAutorunByDefault`、
  `DisableAllVirusScans`、`AppInfoHeaderDetailsHook`、`NativeNotificationIcon`、
  `NotificationIconHook`、`StatusBarClockSecondsHook`、`ControlCenterNoTileLabelsHook`、
  `CustomQsColor`、`CustomQsRoundCorner`、`NotificationCenterTransparency`。
- **批次 B(大文件直译)**: `AutoMistakeTouchHook`、`RecentTaskMemoryViewHook`、
  `BrightnessSliderPercentageHook`、`VolumeSliderPercentageHook`、`SystemUIBatteryHook`。
- **批次 C(工具类前置)**: `CustomDateFormatter` → 再迁依赖它的
  `CustomControlCenterDate`、`CustomStatusBarClock`。
- **批次 D(索引化)**: `AutoAcceptFileTransferHook`(4 链式查找全部入 DexKit 索引)。
- **批次 E(拆分)**: `OwnerInfoHook` 拆分为 Settings 侧 + System 侧两个 Kotlin Hook。
- **批次 F(收尾)**: `DisableForceStop`、`SystemUINetworkSpeeddoublelayerHook`
  (已索引化的两个, 迁移时保持 DexIndexStore 调用不变)。

### 2.2 迁移手法

- 优先**手写直译**(对照 Java 语义逐行转 Kotlin), 而非依赖 IDE J2K 自动转换——
  自动转换产物是"Java 风格 Kotlin", 仍需大量手工清理, 且不会套用项目范式。
  对于个别超长方法可先 J2K 再重构(仅作辅助)。
- 每个文件迁移后: 删除 `.java` 源文件; 保持类名/包名不变
  (HookManager 的 `registerHookModule(Xxx())` 注册语句**无需改动**)。
- 删除 `Xxx.java` 后必须确认没有其他文件引用它(用 `grep`)。

## 3. 分步实施

### 批次 A: 11 个中小型 Hook 直译

步骤:
1. 逐个迁移 `CpuFrequencyFix`、`EnableAutorunByDefault`、`DisableAllVirusScans`、
   `AppInfoHeaderDetailsHook`、`NativeNotificationIcon`、`NotificationIconHook`、
   `StatusBarClockSecondsHook`、`ControlCenterNoTileLabelsHook`、`CustomQsColor`、
   `CustomQsRoundCorner`、`NotificationCenterTransparency` 为 `.kt`。
2. 每个文件:
   - 类名/包名不变, `extends AppHookModule` → `: AppHookModule()`。
   - `getModuleName()` 返回对应 `PreferenceKeys.CONSTANT.name`(按 1.3 映射)。
   - `hookWithId(target, id, chain -> {...})` → `hookWithId(target, id) { chain -> ... }`。
   - `return chain.proceed()` → `chain.proceed()`(作为 lambda 最后表达式)。
   - `getRemotePreferences()` → `remotePreferences` 属性。
   - 静态辅助方法 → `companion object` 内 `@JvmStatic` 或私有顶层函数。
3. `EnableAutorunByDefault`/`DisableAllVirusScans` 的双目标包保持
   `arrayOf(ScopeKeys.LENOVO_SAFE_CENTER.packageName, ScopeKeys.ZUI_SAFE_CENTER.packageName)`。

### 批次 B: 5 个大型 Hook 直译

步骤:
4. 迁移 `AutoMistakeTouchHook`(`Handler(Looper.getMainLooper()).postDelayed` 保持,
   Kotlin 写 `Handler(Looper.getMainLooper()).postDelayed({ ... }, delay)`; 配置读取转 `remotePreferences`)。
5. 迁移 `RecentTaskMemoryViewHook`。
6. 迁移 `BrightnessSliderPercentageHook`(含 `@SuppressLint` 保持)。
7. 迁移 `VolumeSliderPercentageHook`。
8. 迁移 `SystemUIBatteryHook`(资源名反射保持原逻辑)。

### 批次 C: 工具类 + 依赖它的两个大文件

步骤:
9. 迁移 `CustomDateFormatter.java` → `CustomDateFormatter.kt`:
   - 保持类名、包名(`systemui.misc`)、`format(String, Date): String` 静态方法签名。
   - `static {}` 初始化 → `private val CUSTOM_PATTERNS = mapOf(...)` 或 companion init。
   - 迁移后立即验证两个 Repository 引用(`ControlCenterSettingsRepository.kt:76`、
     `StatusBarSettingsRepository.kt:51`)编译通过。
10. 迁移 `CustomControlCenterDate`(依赖 `CustomDateFormatter.format`, 保持调用不变)。
11. 迁移 `CustomStatusBarClock`(同上)。

### 批次 D: AutoAcceptFileTransferHook 索引化

步骤:
12. `DexIndexConstants.kt`: `ModuleKeys` 加 `AUTO_ACCEPT_FILE_TRANSFER =
    PreferenceKeys.AUTO_ACCEPT_FILE_TRANSFER.name`; `Keys` 加
    `VM_FIELD_NAME` / `ACCEPTED_FIELD_NAME` / `LIVE_DATA_FIELD_NAME` / `LIVE_DATA_UPDATE_METHOD`。
13. `MobileDesktopDexIndexer.kt`: 新增 `indexAutoAcceptFileTransfer(bridge)`(链式 4 查询):
    - A: `findField { matcher { declaredClass(TARGET_CLASS);
      type(ClassMatcher.create().superClass("androidx.lifecycle.ViewModel")) } }` → `vmClassName` + 字段名;
    - B: 在 `vmClassName` 中找 `boolean` 字段 → `acceptedFieldName`;
    - C: 在 `vmClassName` 中找 LiveData 子类型字段 → `liveDataFieldName` + `liveDataClassName`;
    - D: 在 `liveDataClassName`(及其 superclass, 视 DexKit 继承链支持情况)中找
      `(Object)void` 方法 → `updateMethodName`。
    - 每个查询独立 try-catch, 失败不写该 key(遵循 Indexer 约定)。
    在 `index()` 中注册。
14. 迁移 `AutoAcceptFileTransferHook.java` → `.kt`:
    - `getModuleName()` → `PreferenceKeys.AUTO_ACCEPT_FILE_TRANSFER.name`。
    - `handleLoadPackage` 阶段用 `DexIndexStore.string(xposed,
      ScopeKeys.MOBILE_DESKTOP.packageName, ModuleKeys.AUTO_ACCEPT_FILE_TRANSFER, ...)`
      读 4 个值, 缺失回退硬编码 `"c"`/`"d"`/`"b"`/null。
    - 删除运行时反射遍历辅助方法(或保留为 fallback, 与 `DisableNearbyShareAutoOffHook` 一致)。
    - `this.getXposed().hook(...).intercept(...)` → `hookWithId(...)`。
15. 验证: 索引器编译 + Hook 编译; 索引 JSON 结构正确性(参照 README 格式)。

### 批次 E: OwnerInfoHook 拆分(双回调)

步骤:
16. 新建 `hook/modules/setting/OwnerInfoSettingsHook.kt`(`AppHookModule`):
    - `getTargetPackages() = arrayOf(ScopeKeys.SETTINGS.packageName)`。
    - 承接原 `hookSettingsPackage`(SecuritySettings.onResume、ActivityThread.performResumeActivity)。
    - 广播接收器注册逻辑(`registerScreenReceiver` + 匿名 `BroadcastReceiver` + `mIsReceiverRegistered` 状态)保留在 Settings 侧。
17. 新建 `hook/modules/systemframework/OwnerInfoSystemHook.kt`(`SystemHookModule`):
    - `getTargetPackages() = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)`。
    - 承接原 `hookSystemPackage`(PowerManagerService.setPowerState/userActivity、ContextImpl.registerReceiver 拦截 + `registerScreenReceiver`)。
17a. 新建 `hook/modules/setting/OwnerInfoUpdater.kt`(共享核心逻辑):
    - 承接 `updateOwnerInfo` / `fetchContentFromAPI` / `parseContentFromJson` /
      `setOwnerInfoContent` / `getObject` / 配置读取(API 网络、主线程 Handler、写锁屏)。
    - 构造注入 `xposed` + `logger`;两侧 Hook 各建实例。
18. 两个 Hook 的 `getModuleName()` **均返回 `PreferenceKeys.AUTO_OWNER_INFO.name`**
    (共用同一开关, `isEnabled()` 按 moduleName 读同一个键, 零前端改动)。
19. 删除 `OwnerInfoHook.java`; `HookManager.kt` 把 `registerHookModule(OwnerInfoHook())`
    替换为两个新注册(相邻, 参照 `DisableGameAudio`/`DisableGameAudioApp` 先例)。
20. 原 Java 侧 `getString(key)` 辅助(`getRemotePreferences().getString`)→ Kotlin
    `remotePreferences.getString(PreferenceKeys.XXX.name, "")`。

### 批次 F: 已索引化的两个 Hook 收尾

步骤:
21. 迁移 `DisableForceStop.java` → `.kt`: 保持 `DexIndexStore.string(...)` 调用
    (Java `DexIndexStore.INSTANCE.string` → Kotlin `DexIndexStore.string`), 键引用不变。
22. 迁移 `SystemUINetworkSpeeddoublelayerHook.java` → `.kt`: 同上。

### 收尾

23. 全局搜索 `hook/modules/**/*.java` 确认清零。
24. `git diff --check` + 全量编译 `.\gradlew.bat assembleDebug`。
25. 提交(按 `.gitmessage` 格式)。

## 4. 决策点(已确认)

- **D1(广播接收器归属)**: **原样保留**。Settings 侧保留
  `SecuritySettings.onResume` / `performResumeActivity` → `registerScreenReceiver`(Activity context);
  System 侧保留 `ContextImpl.registerReceiver` 拦截 → `registerScreenReceiver`(ContextImpl context)。
  两侧各自持有 `mScreenReceiver`/`mIsReceiverRegistered` 实例状态(与单类多进程实例化语义一致)。
- **D2(共享逻辑粒度)**: **新增 `OwnerInfoUpdater.kt`**(放 `hook/modules/setting/`,
  与 `OwnerInfoSettingsHook` 同包, System 侧跨包 import)。设计:
  `class OwnerInfoUpdater(private val xposed: XposedInterface, private val logger: ModuleLog)`,
  承接 `updateOwnerInfo` / `fetchContentFromAPI` / `parseContentFromJson` /
  `setOwnerInfoContent` / `getObject` / 配置读取(`remotePreferences.getString(...)`)。
  两侧 Hook 在回调阶段各建实例。注意: 这是 `hook/modules/` 下首个共享 helper。
- **D3(索引化粒度)**: **全部索引化**(4 个查找点全部入
  `MobileDesktopDexIndexer`)。**不提升 `SCHEMA_VERSION`**(保持 2, 开发阶段手动刷新):
  - `needsReindex` 仅按 schema 或 apk 指纹判定 → 已有索引文件不自动重建 → 新字段缺失时
    `DexIndexStore` 返回 null → Hook 回退硬编码, 行为不劣化;
  - 手动刷新(`DexIndexManager.indexAll`)重写文件带新字段后生效。
  - 执行期细节: 查询 D 需沿 LiveData 声明类型的 superClass 链查 `(Object)void` 方法
    (先 `findClass` 拿 `ClassData`, 落地时验证 DexKit 是否暴露 superClass 链)。

## 5. 验证

- 每批完成后: `.\gradlew.bat assembleDebug` 编译通过。
- 删除 Java 文件后: `grep -rn "Xxx" app/src/main/java` 确认无残留引用。
- 涉及 Repository 引用(`CustomDateFormatter`): 确认 `data/systemui/*Repository.kt` 编译通过。
- 涉及 HookManager: 确认注册类名与 Kotlin 类名一致。
- 最终: `git diff --check`。

## 6. 风险与注意

- `CustomDateFormatter` 被 Repository 引用 → 类名/包名/方法签名**必须保持**, 否则前端编译失败。
- `OwnerInfoHook` 网络代码在主线程外跑线程 + 主线程 Handler → Kotlin 迁移保持
  `Thread { ... }.start()` 与 `Handler(Looper.getMainLooper()).post { ... }`, 不做架构改造。
- `hookWithId` 的 id 字符串保持原值(热重载稳定性依赖稳定 id)。
- `@SuppressLint("PrivateApi", "DiscouragedPrivateApi")` 类级注解在 Kotlin 中保持。
- 所有 `getModuleName()` 硬编码字符串必须换成 `PreferenceKeys` 引用(行为等价, 键名不变,
  已安装用户的开关状态不丢失)。
- 预计新增/修改文件: 22 个 `.kt` + 2 个新 Hook + 1 个工具类 + `DexIndexConstants.kt` +
  `MobileDesktopDexIndexer.kt` + `HookManager.kt` ≈ **28 个文件**, 删除 23 个 `.java`。
