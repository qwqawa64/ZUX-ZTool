# 冷启动路径并行化审计

审计对象：`com.qimian233.ztool` 桌面入口冷启动（`LauncherAlias` → `MainActivity`）。
目标：找出仍然跑在主线程关键路径上的工作、哪些已经并行、以及并行化的硬约束。

## 1. 现状时间线

```text
[Phase 0] Process fork
   └─ ContentProvider: XposedProvider (框架注入)
        └─ XposedServiceHelper.onBinderReceived → 缓存 binder
[Phase 1] Application.attachBaseContext()        ← 已最优
   └─ XposedServiceHelper.registerListener(this)   (ZToolApplication.kt:50)
        └─ 命中缓存则同步 onServiceBind → isModuleActivated = true   [O(1), 无 I/O]
[Phase 1b] Application.onCreate()                ← 未重写，无工作
[Phase 2] MainActivity.onCreate()  ★主线程串行★  (MainActivity.kt:97-187)
[Phase 3] setContent → 首次 composition
   └─ ZToolTheme (HCT 调色板计算 + 可能的 Settings.Secure 读取)
   └─ NavHost → HomeMainRoute → ViewModel 构造
   └─ LaunchedEffect(Unit) { start(); checkDexIndexOnEntry() }   (HomeRoute.kt:131-134)
[Phase 4] HomeViewModel 环境检测（后台 Thread）
   └─ checkRootAccess() → su -c id
   └─ 之后才串行启动 updateModuleStatus / updateSystemInfo / checkConfigUpgrade
[Phase 5] environmentReady = isModuleActive && isRootAvailable  → 导航栏/系统信息卡出现
```

**关键事实**：`setContent { }` 之后的代码（`MainActivity.kt:168-186`）仍然在主线程上同步执行完，
才轮到 Choreographer 出首帧。也就是说这一整段都在 Android 12+ 的 SplashScreen 窗口期内。

## 2. 已经在并行 / 已经最优的部分（不要重复改造）

| 工作 | 现状 | 评价 |
| --- | --- | --- |
| Xposed service 监听器注册 | `attachBaseContext`，早于 `onCreate` | 已是 HyperCeiler 模式，最优 |
| `ConfigUpgrade.configUpgrader` | `lifecycleScope.launch(Dispatchers.IO)` (MainActivity.kt:101) | 已并行，但见 §3.5 的重复调用 |
| `HomeViewModel.checkEnvironment` | 裸 `Thread{}` (HomeViewModel.kt:130) | 已离开主线程 |
| `updateModuleStatusAsync` / `updateSystemInfoAsync` / `checkConfigUpgrade` | 各自独立 `Thread{}` | 三者彼此已并发 |
| `checkAppUpdate` | 独立 `Thread{}`（网络） | 已并行 |
| `DexIndexManager.indexAll` | `viewModelScope.launch(Dispatchers.Default)` | 已是协程 |
| `LogServiceManager.restartServiceIfNeeded` | `postDelayed(3000)` | 已延迟；但延迟是拍脑袋的 |
| `EdgeBubbleBootReceiver` / `LogCollectorService` 自启 | 独立进程内组件 | 非关键路径 |

结论：**"能不能并行"这个问题，本项目在首页环境检测一侧基本已经回答了"能"；
真正的问题在主线程还剩什么没搬走，以及搬的时候会不会撞上 Shell 执行器的并发上限。**

## 3. 仍然在主线程上的启动工作（按收益排序）

### 3.1 P0 — `syncLsposedLogs()`：主线程上的 root shell（最大单点）

`MainActivity.kt:186` → `SettingsRepository.kt:378` → `LogUtils.kt:153-189`

```kotlin
fun syncLsposedLogs(context: Context) {
    ...
    val result = shell.executeRootCommand(
        "if [ -d /data/adb/lspd/log ]; then cp -rf /data/adb/lspd/log/* $destPath " +
        "&& chmod -R 644 $destPath/* && echo 'SYNC_OK'; else echo 'SRC_MISSING'; fi"
    )
}
```

调用链全程阻塞：`acquireCommandSlot()`（可能持锁 `Thread.sleep` 最多 50 ms）
→ `executorService.submit(...)` → `future.get(timeoutSeconds, SECONDS)`
（`EnhancedShellExecutor.kt:264-265`，默认超时 8 s，超时后还会重试一次）。

即：**fork/exec `su` + 复制整个 LSPosed 日志目录 + 递归 chmod，全在首帧之前。**
日志目录越大越慢，最坏情况可逼近 8 s 超时上限。

该函数本身已经是"可后台调用"的设计——失败提示走
`Handler(getMainLooper()).post{}`（`LogUtils.kt:191-200`），说明作者本就允许它不在主线程。

```kotlin
// 建议
lifecycleScope.launch(Dispatchers.IO) { settingsRepo.syncLsposedLogs() }
```

### 3.2 P0 — Launcher alias 自愈：多次 PackageManager binder 读 + 2 次写

`MainActivity.kt:181,184`

```kotlin
settingsRepo.applyLeakCanaryAliasState()   // → isLauncherIconHidden() → 2 次 getComponentEnabledSetting
settingsRepo.applyLauncherIconAliasState() // → isLauncherIconHidden() → 2 次读 + 最多 2 次写
```

`isLauncherIconHidden()`（`SettingsRepository.kt:164-167`）在这一次调用里被重复执行了 2~3 次，
每次 2 个 `getComponentEnabledSetting` binder 事务；`setComponentEnabledSetting`
（`SettingsRepository.kt:264-274`）是同步写事务，并由 `DONT_KILL_APP` 保护。

- 两处合并成一次后台任务，且把 `isLauncherIconHidden()` 的结果算一次传下去；
- 整体可以搬进 `Dispatchers.IO`。唯一语义要求是"在配置导入后立即生效"，与主线程无关。
- Debug 构建还多一次 `getActivityInfo`（`isComponentDeclared`，`SettingsRepository.kt:276-286`）。

### 3.3 P0 — `cleanupAppLogsIfNeeded()`：主线程文件 I/O

`MainActivity.kt:185` → `LogUtils.kt:74-88`：`Log/app/` 目录 `listFiles()` + 逐文件 `length()`。
纯文件系统操作，无 UI 依赖，直接 `Dispatchers.IO`。目录文件多时会明显拖慢首帧。

### 3.4 P1 — `EdgeBubbleService.maybeStart()`：RemotePreferences binder + 权限查询

`MainActivity.kt:172` → `EdgeBubbleService.kt:52-62`

```kotlin
ModulePreferencesUtils(context).loadBooleanSetting(FREEFORM_EDGE_BUBBLE, false)
Settings.canDrawOverlays(context)
context.startForegroundService(...)
```

模块激活时 `ModulePreferencesUtils.modulePreferences`（`ModulePreferencesUtils.kt:31-51`）
走的是 `XposedServiceBridge.currentService.getRemotePreferences()` —— 每个读取是跨进程调用；
未激活时退化为 `createPackageContext(...)`（解析另一个包的资源，开销更大）。
再叠加一次 `Settings.canDrawOverlays` binder 查询。

`startForegroundService` 在任何线程调用都合法，整段可放 `Dispatchers.IO`。

### 3.5 P1 — `ConfigUpgrade` 在启动时被跑了两遍（并发缺陷，不只是性能）

- `MainActivity.kt:101` 无条件跑一次（IO 协程）；
- `HomeViewModel.checkConfigUpgrade()`（`HomeViewModel.kt:242-252`）在环境就绪后再跑一次（裸 Thread）。

两次进入 `ConfigUpgrade.configUpgrader()`（`ConfigUpgrade.kt:166-181`），而该 object 持有
非线程安全的可变单例 `mPreferencesUtils`（`ConfigUpgrade.kt:18`），入口处还会
`mPreferencesUtils = null` 重置——两个线程同时进就是数据竞争。

同时 `upgradeRemotePrefs` 内部会发 root 命令；与 `syncLsposedLogs`、根检测争抢
`EnhancedShellExecutor` 的 2 个槽位（见 §4），落败者拿到的是"系统繁忙"这种**失败结果**，
而不是排队等待——也就是说这里的重复调用不只是浪费，还可能让升级检测得出错误结论。

建议：单一入口（推荐保留 Home 侧的、带回显的那次），或用进程级 `AtomicBoolean`/`Mutex` 去重。

### 3.6 P1 — `checkDexIndexOnEntry()` 的判定阶段在主线程

`HomeRoute.kt:131-134` 的 `LaunchedEffect(Unit)` 在主线程调用
`HomeViewModel.checkDexIndexOnEntry(context)`，而该方法（`HomeViewModel.kt:74-89`）同步做：

- 每个 indexer 一次 `DexIndexManager.lastIndexedAt`（`File.exists()` + `readText()` + JSON 解析）
- 非首跑路径上再对每个 indexer 做 `needsReindex`：`readStoredSchemaVersion` +
  `readStoredFingerprint`（**同一文件读两遍**）+ `currentFingerprint`
  （2 次 PackageManager 调用 + 签名 SHA-256）

当前 `DexIndexRegistry` 有 3 个 indexer（`DexIndexRegistry.kt:12-15`），量级不大，
但这属于"每加一个 scope 就线性变慢"的主线程开销。
真正的索引任务本身已经在 `Dispatchers.Default` 上（`HomeViewModel.kt:98`），
只需把**判定**也移进去：

```kotlin
fun checkDexIndexOnEntry(context: Context) = viewModelScope.launch(Dispatchers.IO) { ... }
```

顺带可优化：`needsReindex` 把同一个 JSON 读两遍，合并成一次读取。

### 3.7 P1 — 根检测没有和 Activity 启动重叠（影响"可用感"）

`environmentReady = isModuleActive && isRootAvailable`（`HomeViewModel.kt:324-325`），
而 `isRootAvailable` 要等 `su -c id` 回来。这条命令**从 HomeRoute 首次组合才开始**，
即排在 Phase 2/3 之后；而 Activity 启动期间（主题加载、首帧、PackageManager 调用）
Shell 线程完全是空闲的。

把 `EnhancedShellExecutor.checkRootAccess()` 提前到 `ZToolApplication.onCreate()`
的后台协程里发起，`checkEnvironment` 直接吃 30 s 缓存
（`EnhancedShellExecutor.kt:341-345`），可以把整段 Activity 启动时间从
`environmentReady` 的关键路径上抹掉。

注意 `ZToolApplication` 目前没有 `onCreate` 重写，加一个需要持有
`CoroutineScope(SupervisorJob() + Dispatchers.IO)` 并在 Application 生命周期内不做取消。

### 3.8 P1 — 旋转/配置变更会重跑整套启动工作

`MainActivity` 未声明 `android:configChanges`（`AndroidManifest.xml:101-112`），
而 UI 明确支持横屏（`MainLayout` 的 rail 布局）。因此每次旋转都会重建 Activity，
`onCreate` 里 §3.1~§3.5 的 root shell、PackageManager 写事务、日志清理**全部重跑一遍**。

两条路：声明 `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode|density"`
（Compose 侧通过 `LocalConfiguration` 已经能正确响应），或把这些一次性任务
上提到 Application 级别并加进程级守卫。前者更符合"配置变更由 Compose 处理"的现状。

### 3.9 P2 — 首帧内的主题计算

`ZToolTheme`（`ZToolTheme.kt:96-129`）：
- `readSystemPaletteSeed` 读 `Settings.Secure`（binder + JSON 解析）——仅在动态取色开启时；
- `buildZToolColorScheme` 用 material-color-utilities 的 HCT 推导整套 40+ 角色调色板。

两者都在首次 composition 里同步执行。动态取色默认关闭
（`ThemePreferencesRepository.kt:35` 默认 `false`），所以 `systemSeed` 通常为 null，
但 HCT 调色板推导仍在跑。

可选优化：把"默认设置 + 品牌 seed"这条最常见组合的结果做成进程级缓存/`lazy` 常量；
把 `readSystemPaletteSeed` 预热到 Application 的后台协程里。

`ThemePreferencesRepository.loadSettings()`（`MainActivity.kt:121`）是主线程首次
SharedPreferences XML 解析。它必须在 `setContent` 前拿到以避免主题闪烁，
因此**不能**改成异步——正确做法是在 `attachBaseContext`/`onCreate` 里
后台预热 `getSharedPreferences(...)`，让 XML 解析与 Activity 启动重叠。

### 3.10 P2 — 缺少 Baseline Profile

仓库中没有任何 `baseline-prof.txt`/`*.prof`，`app/build.gradle.kts` 也没有
`androidx.profileinstaller` 依赖（只有打包已有的 `.dm` 文件的逻辑）。
冷启动路径（Application → MainActivity → ZToolTheme → NavHost → Home）是
Baseline Profile 的标准受益场景，属于低风险、无行为变更的优化。

另外：Debug 构建未 minify 且带 LeakCanary（其 ContentProvider 会在进程启动时安装
AppWatcher），**用 Debug 包测出来的冷启动数字会显著差于 Release**，测量口径需要区分。

## 4. 并行化的硬约束：`EnhancedShellExecutor` 不能被盲目并发

这是做这项优化时最容易踩的坑。`EnhancedShellExecutor.kt`：

```kotlin
private const val MAX_CONCURRENT_COMMANDS = 2            // :421
private const val MIN_COMMAND_INTERVAL: Long = 50        // :426
private val executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_COMMANDS)  // :26
```

```kotlin
private fun acquireCommandSlot(): Boolean = commandLock.lock().use {  // :175-200
    if (commandCounter.get() >= MAX_CONCURRENT_COMMANDS) return false   // ★ 直接失败，不排队
    if (now - lastCommandTime < MIN_COMMAND_INTERVAL) Thread.sleep(...) // ★ 持锁睡眠
    ...
}
```

两个后果：

1. **第 3 个并发 root 命令会直接返回失败**（`"系统繁忙，请稍后重试"`，`:135`），
   而不是排队。如果把 §3.7 的并行化做成"同时甩 4~5 个协程去跑 root 命令"，
   根来源检测 / ROM 区域 / slot 检测会随机拿到失败结果，UI 上表现为
   `page_home_unknown_root_available` 之类的错误文案。
2. `commandLock` 在 `Thread.sleep` 期间被持有，所以所有调用者被串行压到
   50 ms 的节奏上。启动路径上的 root 命令大致有
   `id`、`detectRootSource`（最多 3 条）、`getCurrentBootSlot`、`getRomRegion`（最多 3 条）、
   config upgrade、lsposed sync —— 光节流睡眠就有数百毫秒。

**因此正确的并行策略是：**

- root/shell 工作统一走**单并发**的调度器（`Dispatchers.IO.limitedParallelism(1)`，
  或一个 `Mutex`），保证不触发 `MAX_CONCURRENT_COMMANDS` 拒绝路径；
- 只有**不碰 shell**的工作才真正并行：文件 I/O（§3.3）、PackageManager 事务（§3.2）、
  `startForegroundService` / `canDrawOverlays`（§3.4）、DexIndex 判定（§3.6）、
  `detectFrameworkVersionAndMode`（纯 `XposedServiceBridge` binder，`HomeRepository.kt:248-261`）。

顺带建议：`acquireCommandSlot` 的失败语义值得改成"等待"而非"失败"——
至少把节流睡眠移到锁外，否则任何并行化都会放大这个隐患。

## 5. 建议的落地顺序

| 步骤 | 内容 | 风险 |
| --- | --- | --- |
| 1 | `syncLsposedLogs` → `Dispatchers.IO` | 低 |
| 2 | `cleanupAppLogsIfNeeded` → `Dispatchers.IO` | 低 |
| 3 | 合并 + 后台化 alias 自愈（§3.2） | 低 |
| 4 | `EdgeBubbleService.maybeStart` → `Dispatchers.IO` | 低 |
| 5 | `ConfigUpgrade` 去重 + 线程安全（§3.5） | 中（需确认保留哪一个入口） |
| 6 | `checkDexIndexOnEntry` 判定 → `Dispatchers.IO` | 低 |
| 7 | 根检测提前到 Application（§3.7） | 中（需验证缓存命中语义） |
| 8 | 旋转重跑守卫（§3.8） | 中（涉及 Manifest 契约） |
| 9 | Baseline Profile + Release 口径测量（§3.10） | 低 |
| 10 | 主题预热 / 调色板缓存（§3.9） | 低 |

第 1~4 步互不依赖，可以在一个 `coroutineScope` 里并发发起（都不碰 shell，
不会撞 §4 的并发上限），一次性拿掉 `onCreate` 尾部的主线程阻塞。

## 6. 验证方式

改造前后用同一口径测量，避免用 Debug 包下结论：

```powershell
# 冷启动总时长（多次取中位数）
adb shell am force-stop com.qimian233.ztool
adb shell am start -W -n com.qimian233.ztool/.LauncherAlias
```

更细的定位用 Perfetto/Macrobenchmark 的 `cold_start` 轨迹，重点看两类区间：

1. `MainActivity.onCreate` 的**绝对时长**（应显著下降）；
2. 主线程 `Choreographer#doFrame` 首次回调相对 `ActivityThread.handleBindApplication` 的偏移
   （衡量首帧是否提前）。

`environmentReady` 的到达时间用现有 UI 观测即可（导航栏 + 系统信息卡出现），
或用 §3.7 改造后的 `checkRootAccess` 缓存命中日志
（`EnhancedShellExecutor.kt:343` "Using cached root check result"）确认提前量生效。

## 7. 相关文件

- `app/src/main/java/com/qimian233/ztool/MainActivity.kt`
- `app/src/main/java/com/qimian233/ztool/ZToolApplication.kt`
- `app/src/main/java/com/qimian233/ztool/viewmodel/HomeViewModel.kt`
- `app/src/main/java/com/qimian233/ztool/data/home/HomeRepository.kt`
- `app/src/main/java/com/qimian233/ztool/data/settings/SettingsRepository.kt`
- `app/src/main/java/com/qimian233/ztool/utils/LogUtils.kt`
- `app/src/main/java/com/qimian233/ztool/utils/ConfigUpgrade.kt`
- `app/src/main/java/com/qimian233/ztool/EnhancedShellExecutor.kt`
- `app/src/main/java/com/qimian233/ztool/service/EdgeBubbleService.kt`
- `app/src/main/java/com/qimian233/ztool/dexindex/base/DexIndexManager.kt`
- `app/src/main/java/com/qimian233/ztool/ui/theme/ZToolTheme.kt`
