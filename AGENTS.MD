# AGENTS.MD

## 项目概览

ZUX-ZTool 是一个面向 ZUX OS/ZUI 设备的 Android + LSPosed/Xposed 功能增强模块。项目主体是单模块 Android 工程 `:app`，同时包含普通 Android 应用界面、Root/Shell 工具能力、前台日志采集服务、配置备份恢复、Magisk 嵌入模块资源，以及 Xposed Hook 入口和各功能 Hook 模块。

当前 UI 已大量迁移到 Jetpack Compose：主入口 `MainActivity` 通过 `setContent` 承载首页、功能页、日志页和设置页；各功能详情页仍以独立 `ComponentActivity` 的方式保留 Manifest 启动契约。`docs/compose_refactor/AGENTS.MD` 是 Compose 重构专项指南。

关于 miuix 的所有内容参阅此网站，不要通过本地下载的 miuix 依赖直接获取：https://compose-miuix-ui.github.io/miuix/dokka/index.html

## 根目录结构

- `.gradle/`、`.kotlin/`、`build/`：本地 Gradle/Kotlin 构建产物或缓存，不应提交。
- `.idea/`：Android Studio/IntelliJ 工程配置。
- `app/`：Android 应用模块，包含源码、资源、Manifest、Xposed 资产和模块级构建脚本。
- `docs/compose_refactor/`：Compose 重构计划、迁移记录和专项 agent 指南。
- `gradle/`：Gradle Wrapper 与版本目录。
- `libs/`：根目录本地依赖，目前包含 `swiperefreshlayout-1.1.0.pom`。
- `build.gradle.kts`：根项目插件声明。
- `settings.gradle.kts`：仓库源、插件源、项目名 `ZTool` 和 `:app` 模块声明。
- `gradle.properties`：Gradle/Android 全局构建参数。
- `gradlew`、`gradlew.bat`：Gradle Wrapper 启动脚本。
- `gradle/libs.versions.toml`：AGP、Kotlin、Compose BOM、Miuix、Lifecycle、Navigation 等依赖版本目录。
- `README.md`：项目说明。
- `UpdateCheck.json`：应用更新检查数据。
- `ZToolLogo.png`、`ZToolLogoForeground.svg`：项目 Logo 资源。
- `ZToolUploadKey.jks`：发布签名文件，修改和提交前需谨慎。
- `更新日志.txt`：项目更新日志。

## 构建与依赖

- Android Gradle Plugin：`8.13.0`
- Kotlin：`2.3.21`
- Compose BOM：`2025.10.00`
- compileSdk/targetSdk：`36`
- minSdk：`27`
- Java/Kotlin JVM target：`11`
- 主要 UI 依赖：Jetpack Compose、Material 3、Navigation Compose、Activity Compose、Miuix。
- Hook 相关依赖：`de.robv.android.xposed:api:82` 为 `compileOnly`，`org.lsposed.hiddenapibypass:hiddenapibypass` 用于隐藏 API 绕过。
- 其他依赖：Gson、`cn.6tail:lunar`、Lifecycle、SavedState、JUnit/Espresso。
- 常用验证命令：`.\gradlew.bat assembleDebug`

## Android 模块结构

```text
app/
  build.gradle.kts
  proguard-rules.pro
  libs/XposedBridgeAPI-82.jar
  src/main/
    AndroidManifest.xml
    java/com/qimian233/ztool/
    res/
    assets/
    ic_launcher-playstore.png
  src/test/
  src/androidTest/
```

### Manifest 与 Xposed 声明

- `app/src/main/AndroidManifest.xml` 声明主应用、功能详情 Activity、日志采集 Service、权限、Xposed 元数据和作用域。
- `assets/xposed_init` 指向 `com.qimian233.ztool.hook.HookInit`，是 LSPosed/Xposed 加载入口。
- `res/values/array.xml` 的 `xposed_scope` 声明 Hook 作用域包名，包括 `android`、`com.android.systemui`、`com.android.settings`、`com.zui.launcher`、`com.lenovo.ota` 等。

### 资源与资产

- `res/values/`：字符串、颜色、主题、数组等基础资源。
- `res/values-night/`：夜间主题资源。
- `res/drawable/`：应用内图标、背景、标签、状态图形资源。
- `res/mipmap-*`：启动图标与渠道图标。
- `res/raw/`：演示视频资源。
- `res/xml/`：备份与数据提取规则。
- `assets/embedding/`：内置 Magisk/嵌入模块配置与脚本。
- `assets/embedding/zuxos_embedding/`：模块包内容，包括 `module.prop`、`service.sh`、`post-fs-data.sh`、`customize.sh`、`sepolicy.rule`、`META-INF` 安装脚本等。

## 源码包职责

```text
com.qimian233.ztool
  主 Activity、Compose 主路由、全局 Shell 执行器、模块激活探针。
audit
  Hook 日志解析。
config
  模块配置常量。
data
  Repository 层，封装偏好配置、Root/Shell、日志、OTA、主题等数据操作。
hook
  Xposed 入口、Hook 管理器、Hook 基类与各业务 Hook 模块。
service
  日志采集前台服务及服务管理器。
settingactivity
  各功能详情页 Activity 和少量旧式/辅助页面。
ui
  Compose 共享组件与主题系统。
utils
  文件、权限、Magisk、字体、配置升级、弹窗等工具类。
viewmodel
  Compose 页面和详情页的状态管理。
```

## 根包源码

- `MainActivity.kt`：应用主入口。初始化主题、首次启动声明倒计时弹窗、日志服务状态监听、Compose 主界面和主导航。
- `HomeRoute.kt`：首页 Compose 路由和页面，展示环境状态、模块状态、设备信息、更新提示和重启菜单。
- `FeaturesRoute.kt`：功能管理页 Compose 路由，按作用域应用展示功能入口并跳转到对应详情 Activity。
- `AuditRoute.kt`：日志页 Compose 路由，负责日志筛选、搜索、统计、导出、清空和详情弹窗。
- `SettingsRoute.kt`：设置页 Compose 路由，负责日志服务、详细日志、配置备份恢复、主题设置、关于弹窗等。
- `ModuleActivationProbe.kt`：模块激活自检探针；Xposed 加载自身应用时会 Hook 该方法返回激活状态。
- `EnhancedShellExecutor.java`：增强 Shell 执行器，集中处理 Root/Shell 命令执行。
- `LoadingDialog.kt`：加载弹窗封装。

## UI 与主题源码

- `ui/theme/ZToolTheme.kt`：项目 Compose 主题入口，解析 Material/Miuix、动态色、手动种子色、AMOLED 纯黑等主题策略。
- `ui/theme/ZToolThemeSettings.kt`：主题设置数据模型。
- `ui/theme/ThemeMode.kt`：主题模式枚举，包含跟随系统、浅色、深色。
- `ui/theme/FrontendStyle.kt`：前端风格枚举，区分 Material 与 Miuix。
- `ui/components/ZToolScaffold.kt`：项目级 Scaffold、导航栏/栏项等布局组件。
- `ui/components/ZToolSurfaces.kt`：页面 Surface、Card 等表面组件。
- `ui/components/ZToolSettings.kt`：设置项、设置区块等 Compose UI 组件。
- `ui/components/ZToolSettingsModel.kt`：设置页面的模型对象，如 Section、Item。
- `ui/components/ZToolDialog.kt`：项目级 Compose 对话框组件。
- `ui/components/PlatformComposeDialog.kt`：平台 Dialog 与 Compose 内容桥接。

## ViewModel 源码

- `HomeViewModel.kt`：首页状态、环境检查、更新检查、重启操作、配置升级提示。
- `AuditViewModel.kt`：日志列表加载、过滤、统计、导出、清理和详情状态。
- `SettingsViewModel.kt`：全局设置页状态、配置备份恢复、日志服务开关、主题设置、关于弹窗。
- `SettingsDetailViewModel.kt`：系统设置详情页状态，管理字体导入、配置备份恢复、浮窗/平行视界等入口。
- `SearchPageViewModel.kt`：平行视界/魔窗适配搜索页状态。
- `FloatingWindowViewModel.kt`：悬浮窗/平行视界适配向导状态。
- `GameToolSettingsViewModel.kt`：游戏助手设置状态。
- `LauncherSettingsViewModel.kt`：桌面设置状态，包括后台保护和网格/Dock 相关配置。
- `OtaSettingsViewModel.kt`：OTA 设置状态、当前设备信息、固件链接查询。
- `PackageInstallerSettingsViewModel.kt`：安装器设置状态。
- `SafeCenterSettingsViewModel.kt`：安全中心设置状态。
- `FrameworkSettingsViewModel.kt`：系统框架设置状态。
- `SystemUiSettingsViewModel.kt`：系统界面总设置状态。
- `StatusBarSettingsViewModel.kt`：状态栏时钟、网速、电量等设置状态。
- `ControlCenterSettingsViewModel.kt`：控制中心日期/样式设置状态。
- `LockScreenSettingsViewModel.kt`：锁屏一言、充电功率、API 测试等设置状态。

## Repository 源码

- `data/home/HomeRepository.kt`：首页数据源，处理 Root/LSPosed 环境检测、设备信息、更新检查、重启命令、Shell 缓存。
- `data/audit/AuditRepository.kt`：日志目录读取、日志解析、压缩导出、清空日志。
- `data/settings/SettingsRepository.kt`：全局设置页数据源，封装 SharedPreferences、配置备份恢复、日志服务配置。
- `data/settings/SettingsDetailRepository.kt`：系统设置详情数据源，封装字体、配置文件、模块配置等操作。
- `data/settings/MagicWindowSearchRepository.kt`：平行视界/魔窗搜索和应用/Activity 信息读取。
- `data/settings/FloatingWindowRepository.kt`：悬浮窗/平行视界配置读写、前台应用快照和配置请求。
- `data/theme/ThemePreferencesRepository.kt`：应用 UI 主题偏好读写与监听。
- `data/gametool/GameToolSettingsRepository.kt`：游戏助手 Hook 开关与参数配置。
- `data/launcher/LauncherSettingsRepository.kt`：桌面/启动器相关配置。
- `data/ota/OtaSettingsRepository.kt`：OTA 配置、设备信息和固件链接处理。
- `data/packageinstaller/PackageInstallerSettingsRepository.kt`：安装器 Hook 配置与作用域重启。
- `data/safecenter/SafeCenterSettingsRepository.kt`：安全中心相关配置。
- `data/systemframework/FrameworkSettingsRepository.kt`：系统框架配置和系统重启请求。
- `data/systemui/SystemUiSettingsRepository.kt`：系统界面总设置与 Shell 操作。
- `data/systemui/StatusBarSettingsRepository.kt`：状态栏时钟、网速、电量配置。
- `data/systemui/ControlCenterSettingsRepository.kt`：控制中心日期格式和样式配置。
- `data/systemui/LockScreenSettingsRepository.kt`：锁屏一言 API、充电功率显示等配置。

## settingactivity 源码

- `settingactivity/gametool/GameToolSettngs.kt`：游戏助手详情页 Activity。
- `settingactivity/launcher/LauncherSettingsActivity.kt`：桌面/启动器详情页 Activity。
- `settingactivity/ota/OtaSettings.kt`：系统更新/OTA 详情页 Activity。
- `settingactivity/packageinstaller/packageinstallersettings.kt`：软件包安装器详情页 Activity。
- `settingactivity/safecenter/SafeCenterSettingsActivity.kt`：安全中心详情页 Activity。
- `settingactivity/systemframework/FrameworkSettingsActivity.kt`：系统框架详情页 Activity。
- `settingactivity/systemui/systemUISettings.kt`：系统界面总入口 Activity。
- `settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt`：状态栏详情页 Activity。
- `settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt`：控制中心详情页 Activity。
- `settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt`：锁屏详情页 Activity。
- `settingactivity/setting/SettingsDetailActivity.kt`：系统设置类功能详情页。
- `settingactivity/setting/floatingwindow/FloatingWindow.kt`：悬浮窗/平行视界适配向导 UI。
- `settingactivity/setting/magicwindowsearch/searchPage.kt`：平行视界/魔窗 Activity 搜索选择页。
- `settingactivity/setting/magicwindowsearch/ActivityPair.java`：应用 Activity 配对数据结构。
- `settingactivity/setting/magicwindowsearch/PackageInfo.java`：应用包信息数据结构。

## Hook 框架源码

- `hook/HookInit.java`：Xposed 模块入口。初始化隐藏 API 绕过、自身激活自检 Hook，并委托 `HookManager` 分发。
- `hook/base/BaseHookModule.java`：Hook 模块基类，提供包名匹配、初始化和通用 Hook 能力。
- `hook/base/HookManager.java`：Hook 模块注册与加载分发中心。
- `hook/base/PreferenceHelper.java`：Hook 侧读取模块 SharedPreferences 的工具。
- `hook/modules/SharedPreferencesTool/ModulePreferencesUtils.java`：Hook 模块偏好读取辅助工具。

## Hook 模块源码

### documentsui

- `DocumentsUIBypass.java`：绕过 DocumentsUI 对受限目录选择的限制。

### gametool

- `AutoMistakeTouchHook.java`：游戏助手防误触相关 Hook。
- `CpuFrequencyFix.java`：修正游戏服务读取 CPU 频率/时钟信息的逻辑。
- `DeviceModelDisguiseHook.java`：设备型号伪装/拯救者游戏模式相关 Hook。
- `DisableGameAudio.java`：禁用或调整游戏模式音频优化。
- `SocTemperatureFix.java`：修正 SoC 温度读取。

### launcher

- `CustomGridSize.java`：自定义桌面网格行列数。
- `DisableForceStop.java`：调整启动器后台清理/强停行为。
- `ZuiLauncherHotseatHook.java`：解除或调整 Dock/Hotseat 限制。

### ota

- `DisableOtaCheck.java`：禁用 OTA 检查相关逻辑。
- `LenovoOTAHook.java`：联想 OTA 功能增强，保留本地安装或处理更新参数。

### packageinstaller

- `Hook_Skip_WarnPage.java`：跳过安装器警告页。
- `Hook_disable_installerAD.java`：禁用安装器广告/推荐。
- `PackageInstallerHookScan.java`：安装包扫描流程 Hook。
- `PackageInstallerNoDeleteModule.java`：调整安装器删除安装包行为。
- `PackageInstallerPermissionHook.java`：安装权限/默认授权相关 Hook。
- `packageInstallerStyleHook.java`：安装器样式或原生安装器相关 Hook。

### safecenter

- `EnableAutorunByDefault.java`：默认允许应用自启动。
- `SafeCenterScanBlocker.java`：阻止安全中心周期扫描或提示。

### setting

- `AllowDisplayDolbyHook.java`：允许显示杜比相关设置。
- `AlwaysDisplaySuggestion.java`：控制设置建议项显示。
- `KeepRotation.java`：保持/修正屏幕方向策略。
- `OwnerInfoHook.java`：锁屏签名/OwnerInfo 相关 Hook。
- `PermissionControllerHook.java`：权限控制器相关 Hook。
- `SplitScreenMandatory.java`：绕过分屏限制。
- `yishijiecompletion.java`：一视界/平行视界补全相关 Hook。

### systemFramework

- `AiInputExpand.java`：AI 全局输入触发符扩展。
- `AllowGetPackages.java`：允许获取应用列表。
- `DisableFlagSecure.java`：移除安全窗口截屏限制。
- `NoMorePasswordPer24H.java`：禁用每 24 小时锁屏密码验证限制。

### systemui

- `CustomDateFormatter.java`：状态栏/控制中心自定义日期格式工具。
- `CustomControlCenterDate.java`：控制中心日期显示 Hook。
- `CustomStatusBarClock.java`：状态栏时钟格式/样式 Hook。
- `ForceLenovoAOD.java`：强制启用联想 AOD/原生息屏显示相关逻辑。
- `GuestModeController.java`：访客用户/访客模式相关控制。
- `NativeNotificationIcon.java`：启用原生通知图标显示。
- `NoChargeAnimation.java`：禁用全屏充电动画。
- `NotificationIconHook.java`：通知图标数量/显示策略 Hook。
- `StatusBarClockSecondsHook.java`：状态栏时钟秒显 Hook。
- `SystemUIBatteryHook.java`：状态栏电量百分比显示 Hook。
- `SystemUIChargeWattsHook.java`：锁屏/AOD 充电功率显示 Hook。
- `SystemUINetworkSpeeddoublelayerHook.java`：双层网速显示 Hook。
- `SystemUINetworkSpeedSIzeHook.java`：网速显示尺寸优化 Hook。
- `SystemUIRealWatts.java`：真实充电功率读取与显示 Hook。

### wallpaper

- `ChargeAnimationFixModule.java`：充电动画修复或适配 Hook。

## 工具与服务源码

- `service/LogCollectorService.java`：前台日志采集服务，收集 Hook 模块运行日志。
- `service/LogServiceManager.java`：日志服务启动、停止、重启和状态回调管理。
- `audit/LogParser.java`：日志文本解析器，输出日志级别、模块、分类、详情等结构化数据。
- `config/ModuleConfig.java`：模块配置常量。
- `utils/AppChooserDialog.kt`：应用选择对话框。
- `utils/CountdownDialog.kt`：带倒计时确认按钮的声明/确认对话框。
- `utils/ConfigUpgrade.java`：配置版本升级和兼容处理。
- `utils/EmbeddingConfigManager.java`：内置嵌入模块配置读取与处理。
- `utils/FileManager.java`：文件管理工具。
- `utils/FileUtils.java`：通用文件工具。
- `utils/FontInstallerManager.java`：字体导入、安装或模块化处理。
- `utils/GetPCFlashFirmware.java`：获取 PC 刷机包/固件链接辅助逻辑。
- `utils/MagiskModuleManager.java`：Magisk 模块安装、还原或管理。
- `utils/OvCommonConfigManager.java`：一视界/平行视界配置管理。
- `utils/PermissionChecker.java`：权限检查工具。

## 测试源码

- `app/src/test/java/com/qimian233/ztool/ExampleUnitTest.java`：默认本地单元测试样例。
- `app/src/androidTest/java/com/qimian233/ztool/ExampleInstrumentedTest.java`：默认 Android Instrumentation 测试样例。

## 修改注意事项

- 不要随意改动 `assets/xposed_init`、Manifest 中的 Xposed 元数据、`xposed_scope`、Hook 作用域包名和已有 SharedPreferences/配置键。
- Hook 模块、Root/Shell 命令、Magisk 嵌入资源属于高风险行为边界；UI 调整时优先通过 Repository/ViewModel 包装，不直接改行为。
- 详情页 Activity 名称和包名是 Manifest 启动契约，重构时应保留。
- 修改 Compose UI、主题、导航或共享组件后，建议运行 `.\gradlew.bat assembleDebug`。
- 工作区当前可能存在他人未提交改动，提交或格式化前先检查 `git status --short`。
- 完成一项计划文档中提供的或者用户要求的任务都必须提交，除非用户明确要求不要提交。
