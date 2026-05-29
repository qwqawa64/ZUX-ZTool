这个项目更适合做“UI 层重构”，不建议在现有 XML/Fragment/Activity 结构上逐页硬迁
  移。原因是当前 UI 代码和业务逻辑强耦合，XML 布局多且大，设置页大量 findViewById、
  MaterialSwitch、Toolbar、Dialog、RecyclerView Adapter 逻辑混在 Activity/Fragment 里。
  Compose 迁移如果只换布局，最后会变成 View 和 Compose 混杂、主题难切、状态更难维护。

  主要更改点

  1. 构建系统改造
      - 当前是 Java + XML View 项目，需要引入 Kotlin。
      - app/build.gradle.kts 需要启用 Compose：
          - kotlin-android
          - buildFeatures { compose = true }
          - Compose BOM
          - androidx.activity:activity-compose
          - androidx.navigation:navigation-compose
          - androidx.compose.material3:material3

      - Material 3 Expressive 已在 Compose Material3 体系中提供，部分 API 仍可能需要
        experimental opt-in，应按 AndroidX Material3 release note 锁版本。官方文档说明
        Compose Material3 用于构建 Material 3 / Material 3 Expressive UI。来源：Android
        Developers Material3 Compose 文档
        https://developer.android.com/jetpack/androidx/releases/compose-material3
        和设计系统文档
        https://developer.android.google.cn/develop/ui/compose/designsystems/material3

      - Miuix 可作为 Compose UI 库引入，官方项目是 compose-miuix-ui/miuix，文档里
        Android 依赖形态是 top.yukonga.miuix.kmp:miuix-ui-android
        等。来源：https://github.com/compose-miuix-ui/miuix 和
        https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started

  2. 架构改造
      - 当前页面逻辑集中在：
          - MainActivity
          - HomeFragment
          - FeaturesFragment
          - AuditFragment
          - SettingsFragment
          - settingactivity/**

      - Compose 后建议改成单 Activity 架构：
          - MainActivity 只负责 setContent { ZToolApp() }
          - 页面全部变成 Composable Screen
          - 使用 navigation-compose 替代 XML nav_graph.xml

      - 现有多个设置 Activity 可以逐步合并成 Compose Navigation 路由，而不是继续一个功
        能一个 Activity。

  3. 状态层抽离
      - 当前大量页面直接读写 ModulePreferencesUtils、直接开线程、直接操作 Shell、直接
        Toast/Dialog。

      - Compose 需要稳定状态模型：
          - ViewModel
          - UiState
          - StateFlow
          - Repository/Manager 层封装 Shell、日志、配置、更新检查

      - 例如 HomeFragment 里环境检测、更新检查、系统信息读取、卡片展开动画，都应该拆
        成：
          - HomeViewModel
          - HomeUiState
          - EnvironmentRepository
          - UpdateRepository

  4. 多前端风格支持
      - 不建议在业务页面里到处写 if (style == MIUIX)。
      - 应建立自己的设计系统适配层，例如：
          - ZTheme
          - ZScaffold
          - ZTopBar
          - ZCard
          - ZSwitchRow
          - ZListItem
          - ZDialog

      - 业务页面只调用 ZSwitchRow(...)，具体渲染由当前风格决定：
          - Material 3 Expressive 实现
          - Miuix 实现
          - 以后可扩展 ZUX/ZUI 风格实现

      - 风格选择可以保存在 SharedPreferences/DataStore 中，启动时读取并套用主题。

  5. 资源和主题改造
      - res/layout/** 会逐步废弃。
      - res/navigation/nav_graph.xml 会被 Compose Navigation 替代。
      - res/menu/** 需要替换成 Compose Dropdown/Menu。
      - res/values/themes.xml 仍需要保留作为 Activity 基础主题，但主要颜色、
        typography、shape 应迁到 Compose theme。

      - drawable/mipmap/raw/xml/assets 仍可继续复用。

  6. 页面迁移重点
      - 首页：HomeFragment 是最高风险页面，业务逻辑多，应先抽 ViewModel，再写 Compose。
      - 功能页：FeaturesFragment + FeaturesAdapter 适合较早迁移，列表结构清晰。
      - 设置页：settingactivity/** 数量多，建议先设计通用设置 DSL，再批量改。
      - 审计页：AuditFragment + LogParser，适合用 Compose LazyColumn 重写日志列表。
      - Dialog：LoadingDialog、CountdownDialog、各种 XML dialog 应统一替换为 Compose
        AlertDialog/自定义 Dialog。

  推荐重构方案

  阶段 1：先搭 Compose 壳，不动 Hook
  保留 hook/、service/、utils/、config/。新增 Kotlin UI 包，例如：

  app/src/main/java/com/qimian233/ztool/
    ui/
      ZToolApp.kt
      navigation/
      theme/
      components/
      screens/
        home/
        features/
        audit/
        settings/
        module/
    data/
      preference/
      shell/
      update/
      log/
    viewmodel/

  阶段 2：把业务状态从 Fragment/Activity 抽出来
  先处理 HomeFragment、systemUISettings 这类重逻辑页面。目标是让 UI 层只消费 UiState，
  不直接跑 shell、不直接读写偏好、不直接 new Thread。

  阶段 3：实现风格适配层
  定义统一组件接口：

  enum class FrontendStyle {
      MaterialExpressive,
      Miuix
  }

  页面使用项目组件：

  ZSwitchRow(
      title = stringResource(R.string.xxx),
      checked = state.enabled,
      onCheckedChange = viewModel::setEnabled
  )

  内部根据 FrontendStyle 切换 Material3 或 Miuix 实现。

  阶段 4：迁移主导航
  把 MainActivity + nav_graph.xml + BottomNavigationView + Fragment 改成：

  setContent {
      ZToolTheme(style = selectedStyle) {
          ZToolNavHost()
      }
  }

  底部导航、顶栏、页面切换动画都用 Compose 实现。

  阶段 5：迁移设置页
  先做通用设置模型：

  sealed interface SettingItem {
      data class Switch(...)
      data class Entry(...)
      data class Slider(...)
      data class TextInput(...)
      data class Category(...)
  }

  然后各设置页只声明数据结构，Compose 统一渲染。这样迁移 systemui、gametool、launcher、
  ota、packageinstaller 会快很多。

  阶段 6：清理 XML View 层
  全部页面迁移后删除：

  - res/layout/activity_*.xml
  - res/layout/fragment_*.xml
  - res/navigation/nav_graph.xml
  - 旧 Adapter
  - Fragment 类
  - 多余 AppCompat/Material View 依赖

  建议保留的部分

  - hook/**：不应参与 UI 重构。
  - service/**：只需要暴露状态给 ViewModel。
  - utils/EnhancedShellExecutor/MagiskModuleManager/EmbeddingConfigManager：可以保留，
    但建议通过 Repository 包一层。

  - assets/embedding/**：继续保留。
  - AndroidManifest.xml 的 Xposed metadata：必须保留。

  最终判断

  如果目标只是“让几个页面用 Compose”，可以渐进迁移；但你的目标是“从传统 XML 改为
  Compose，并支持 miuix 和 Material 3 Expressive 多风格”，那应该重构 UI 层。更合理的边
  界是：Hook/服务/工具层不动，UI、导航、状态管理、主题系统整体重写。这样成本更高，但后
  续多风格维护成本会明显低。

  当前项目结构快照（2026-05-29）

  根目录

  ```text
  ZUX-ZTool/
    app/                         Android 应用模块
    gradle/                      Gradle Wrapper 与版本目录
    libs/                        根级本地依赖
    build.gradle.kts             根项目构建脚本
    settings.gradle.kts          Gradle 模块配置
    gradle.properties            Gradle/Android 构建参数
    ComposeRefactor.md           Compose 重构计划
    README.md
    UpdateCheck.json
    ZToolLogo.png
    ZToolLogoForeground.svg
    更新日志.txt
  ```

  app 模块

  ```text
  app/
    build.gradle.kts
    proguard-rules.pro
    libs/
      XposedBridgeAPI-82.jar
    release/                     当前仓库内保留的发布产物/基线配置
    src/
      main/
        AndroidManifest.xml
        assets/
        java/
        res/
      androidTest/
      test/
  ```

  主源码包

  ```text
  app/src/main/java/com/qimian233/ztool/
    MainActivity.kt
    HomeFragment.java
    FeaturesFragment.kt
    SettingsFragment.kt
    AuditFragment.java
    FeaturesAdapter.java
    SettingsAdapter.java
    EnhancedShellExecutor.java
    LoadingDialog.java

    audit/
      LogParser.java

    config/
      ModuleConfig.java

    service/
      LogCollectorService.java
      LogServiceManager.java

    utils/
      AppChooserDialog.java
      ConfigUpgrade.java
      CountdownDialog.java
      EmbeddingConfigManager.java
      FileManager.java
      FileUtils.java
      FontInstallerManager.java
      GetPCFlashFirmware.java
      MagiskModuleManager.java
      OvCommonConfigManager.java
      PermissionChecker.java

    ui/
      components/
        ZToolScaffold.kt
        ZToolSettings.kt
      theme/
        FrontendStyle.kt
        ZToolTheme.kt
  ```

  Hook 相关结构

  ```text
  app/src/main/java/com/qimian233/ztool/hook/
    HookInit.java
    base/
      BaseHookModule.java
      HookManager.java
      PreferenceHelper.java
    modules/
      SharedPreferencesTool/
      documentsui/
      gametool/
      launcher/
      ota/
      packageinstaller/
      safecenter/
      setting/
      systemFramework/
      systemui/
      wallpaper/
  ```

  传统设置页结构

  ```text
  app/src/main/java/com/qimian233/ztool/settingactivity/
    gametool/
    launcher/
    ota/
    packageinstaller/
    safecenter/
    setting/
      floatingwindow/
      magicwindowsearch/
    systemframework/
    systemui/
      ControlCenter/
      lockscreen/
      statusBarSetting/
  ```

  资源结构

  ```text
  app/src/main/res/
    anim/                        Fragment/navigation 动画资源
    drawable/                    图标、背景、标签等 XML drawable
    layout/                      传统 Activity/Fragment/Dialog/Item XML 布局
    layout-v26/
    menu/                        Bottom navigation 与重启菜单
    mipmap-*/                    启动图标与渠道图标
    navigation/
      nav_graph.xml              当前 XML Navigation 图
    raw/
      mainact.mp4
      tutorial.mp4
    values/
      array.xml
      colors.xml
      strings.xml
      themes.xml
    values-night/
      themes.xml
    xml/
      backup_rules.xml
      data_extraction_rules.xml
  ```

  资产结构

  ```text
  app/src/main/assets/
    xposed_init
    embedding/
      embedding_config.json
      zuxos_embedding/
        README.md
        customize.sh
        module.prop
        post-fs-data.sh
        service.sh
        system.prop
        uninstall.sh
        sepolicy.rule
        embedding_config.json
        META-INF/com/google/android/
          update-binary
          updater-script
  ```

  结构观察

  - 项目已经引入 Kotlin 文件，并已出现 `ui/theme`、`ui/components`，可以作为 Compose 重构的起点。
  - 当前主界面仍由 `MainActivity`、多个 Fragment、`res/navigation/nav_graph.xml` 和 XML layout 组成。
  - 设置页仍集中在 `settingactivity/**`，每个功能域基本对应一个传统 Activity。
  - Hook 逻辑集中在 `hook/**`，应继续与 UI 重构隔离。
  - 可复用业务/系统能力主要在 `utils/**`、`service/**`、`config/**`，后续适合通过 Repository/ViewModel 包装给 Compose 层使用。
  - `assets/embedding/**` 与 Xposed 入口 `assets/xposed_init` 属于模块/Hook 运行资产，迁移 UI 时应保留。

  执行要求追加（2026-05-29）

  - 当前工作分支：`compose-refactor`。
  - 第二阶段目标：把业务状态从 Fragment/Activity 中逐步抽出，同时继续完成页面 Compose 化；每次只完成一个页面，降低回归范围。
  - 每完成一个页面 Compose 化后必须执行：
      1. 运行 `.\gradlew.bat assembleDebug`。
      2. 将完成页面、验证结果、下一步写入本文件。
      3. 提交 Git commit。
      4. 停止继续迁移，等待用户指示继续或反馈问题。
  - 提交时只包含当前页面重构和文档状态更新，不提交构建产物、本地 IDE/Gradle 缓存或无关未跟踪文件。

  已完成 Compose 化页面

  - `MainActivity.kt`：已使用 `setContent` 承载主界面框架。
  - `FeaturesFragment.kt`：功能页已迁移到 Compose。
  - `SettingsFragment.kt`：设置首页已迁移到 Compose；已修复 `isDetailedLoggingEnabled`、`isHomepageYiyanEnabled` 属性 setter 与同名方法的 JVM 签名冲突。
  - `settingactivity/packageinstaller/packageinstallersettings.kt`：包安装器设置页已迁移到 Compose，保留原类名和启动入口，保留原 SharedPreferences key 与重启作用域逻辑。
  - `settingactivity/systemframework/FrameworkSettingsActivity.kt`：系统框架设置页已迁移到 Compose，保留原类名和启动入口；保留 `keep_rotation`、`allow_get_packages`、`disable_flag_secure`、`ai_input_expand`、`AI_INPUT_EXPAND_SIGNS` 配置键；保留 AI 输入检测符格式校验和系统重启确认倒计时。
  - `settingactivity/safecenter/SafeCenterSettingsActivity.kt`：安全中心设置页已迁移到 Compose，保留原类名和启动入口；保留 `default_enable_autorun`、`block_safecenter_scan`、`documents_ui_bypass` 配置键；保留重启当前作用域与 `com.android.documentsui` 的 root shell 逻辑，并用 Compose state 控制重启处理中状态。
  - `settingactivity/gametool/GameToolSettngs.kt`：游戏助手设置页已迁移到 Compose，保留原类名和启动入口；保留 `disable_GameAudio`、`disguise_TB322FC`、`Fix_CpuClock`、`Fix_SocTemp`、`auto_mistake_touch`、`MistakeTouchWhiteList`、`MistakeTouchWhiteListGame` 配置键；防误触模式由 Spinner 改为 Compose 下拉菜单，白名单选择器暂时复用现有 `AppChooserDialog`。
  - `settingactivity/launcher/LauncherSettingsActivity.kt`：桌面设置页已迁移到 Compose，保留原类名和启动入口；保留 `disable_force_stop`、`ForceStopWhiteListEnable`、`ForceStopWhiteList`、`zui_launcher_hotseat`、`CustomGridSize`、`CustomLauncherRow`、`CustomLauncherColumn` 配置键；原生后台管理模式由 Spinner 改为 Compose 下拉菜单，白名单选择器暂时复用现有 `AppChooserDialog`，自定义网格行列改为 Compose 数字输入框。
  - `settingactivity/launcher/LauncherSettingsActivity.kt` 修复与改进：修复原生后台管理下拉选择器点击不展开的问题；自定义桌面网格行列输入由数字输入框改为 3-10 的带步进 Slider，并继续保存到 `CustomLauncherRow`、`CustomLauncherColumn`。
  - `settingactivity/systemui/lockscreen/LockScreenSettingsActivity.kt`：锁屏设置页已迁移到 Compose，保留原类名和启动入口；保留 `auto_owner_info`、`YiYan`、`API_URL`、`Regular`、`systemui_charge_watts`、`systemUI_RealWatts`、`real_watts_customized_interval`、`real_watts_refresh_interval`、`isSystemUIPermissionConfirmed`、`charge_watts_selected_option` 配置键；充电功率与刷新时机 Spinner 改为 Compose 下拉菜单，API 测试与正则提取逻辑保留并改用 Compose Dialog 展示结果。
  - `settingactivity/systemui/statusBarSetting/StatusBarSettingsActivity.kt`：状态栏设置页已迁移到 Compose，保留原类名和启动入口；保留 `StatusBarDisplay_Seconds`、`Custom_StatusBarClock`、`Custom_StatusBarClockFormat`、`Custom_StatusBarClockTextSize`、`Custom_StatusBarClockTextSizeEnabled`、`Custom_StatusBarClockLetterSpacing`、`Custom_StatusBarClockLetterSpacingEnabled`、`Custom_StatusBarClockTextColor`、`Custom_StatusBarClockTextColorEnabled`、`Custom_StatusBarClockTextBold`、`NativeNotificationIcon`、`notification_icon_limit`、`systemui_network_speed_size`、`systemui_network_speed_doublelayer`、`systemui_battery_percentage` 配置键；保留 `StatusBar_notifyNumSize` world-readable SharedPreferences 写入行为；SeekBar/Spinner/Dialog 改为 Compose Slider/下拉菜单/Dialog。
  - `settingactivity/systemui/ControlCenter/ControlCenterSettingsActivity.kt`：控制中心时间设置页已迁移到 Compose，保留原类名、包名和启动入口；保留 `Custom_ControlCenterDate`、`Custom_ControlCenterDateFormat`、`Custom_ControlCenterDateTextSize`、`Custom_ControlCenterDateTextSizeEnabled`、`Custom_ControlCenterDateLetterSpacing`、`Custom_ControlCenterDateLetterSpacingEnabled`、`Custom_ControlCenterDateTextColor`、`Custom_ControlCenterDateTextColorEnabled`、`Custom_ControlCenterDateTextBold` 配置键；日期格式输入、实时预览、格式帮助、示例复制、颜色选择、字体大小与字间距 SeekBar 均改为 Compose TextField/Dialog/Slider。
  - `settingactivity/setting/SettingsDetailActivity.kt`：系统设置详情页已迁移到 Compose，保留原类名和启动入口；保留 `remove_blacklist`、`Split_Screen_mandatory`、`allow_display_dolby`、`PermissionControllerHook`、`AlwaysDisplaySuggestion` 配置键；保留 Magisk/KSU 一视界模块安装与移除、强制小窗 root 命令、悬浮窗适配向导、手动适配策略刷入、ZUI `ov_common_persist_user_0.xml` 配置选择、字体导入与重启作用域逻辑；复杂选择器和字体输入暂时继续复用现有 View Dialog/工具类，页面主体、开关和重启确认已改为 Compose。
  - `AuditFragment.kt`：日志审计页已迁移到 Compose，保留 Fragment 路由入口与 `LogParser` 数据解析逻辑；RecyclerView/Adapter 改为 Compose `LazyColumn`，类别/模块/级别筛选改为 Compose 下拉菜单，搜索、仅显示错误、刷新、清除、统计、导出和日志详情改为 Compose 状态与 Dialog；日志 zip 导出继续使用 SAF `CreateDocument` 与现有 `FileManager`/`FileUtils`。
  - `settingactivity/ota/OtaSettings.kt`：系统更新设置页已迁移到 Compose，保留原类名和启动入口；保留 `custom_ota_parameters`、`disable_OtaCheck`、`Custom_ota_target_versionName`、`Custom_ota_target_deviceID` 配置键；OTA 信息拉取结果和 9008/深刷包查询结果已改为页面内状态区域展示，提供复制下载链接、复制更新日志和复制密码操作；保留当前系统版本/SN 读取、root 读取 OTA XML、XML 解析、`GetPCFlashFirmware` 异步查询和重启 `app_package` + `com.lenovo.tbengine` 作用域逻辑。
  - `settingactivity/systemui/systemUISettings.kt`：系统界面设置聚合页已迁移到 Compose，保留原小写类名和启动入口；保留状态栏、锁屏、控制中心三个子设置入口；保留 `ForceNativeAOD`、`ForceLenovoAOD`、`No_ChargeAnimation`、`charge_animation_fix`、`guest_mode_controller` 配置键；保留原生 AOD secure setting 写入、联想 AOD 入口启动、AOD 互斥处理和重启 `app_package` + `com.zui.wallpapersetting` 作用域逻辑。
  - `HomeFragment.kt`：首页已由 `HomeFragment.java` 迁移为同类名 Kotlin Compose Fragment，保留 XML Navigation 路由入口、`EnvironmentStateListener` 与 Xposed 自检测方法 `isModuleActive`；环境检测、模块状态、系统信息、更新检查、一言提示、配置升级提示和重启菜单均改为 Compose state/Dialog 驱动；保留 Root 检测、模块激活检测、版本更新忽略、ROM 地区缓存写入和重启命令逻辑。
  - `settingactivity/setting/magicwindowsearch/searchPage.kt`：一视界策略查找页已由 Java/XML/RecyclerView 迁移到 Kotlin Compose Activity，保留原小写类名和 Manifest 启动入口；保留 root 读取 `/data/system/zui/embedding/embedding_config.json`、失败回退 assets 官方配置、包名关键字不区分大小写搜索、搜索结果列表和策略详情字段展示逻辑；详情弹窗改为 Compose `AlertDialog`，结果列表改为 Compose `LazyColumn`。

  最近验证

  - 2026-05-29：`.\gradlew.bat assembleDebug` 构建成功。仅存在既有 deprecated warning：
      - `MainActivity.kt` 中 `statusBarColor`、`navigationBarColor`。
      - `SettingsFragment.kt` 中 `versionCode`。
  - 2026-05-29：完成 `FrameworkSettingsActivity` Compose 化后再次运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅存在上述既有 deprecated warning。
  - 2026-05-29：完成 `SafeCenterSettingsActivity` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅存在上述既有 deprecated warning。
  - 2026-05-29：完成 `GameToolSettngs` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。新增一个 Compose Material3 `menuAnchor()` deprecated warning，后续统一处理；其余仍为既有 deprecated warning。
  - 2026-05-29：完成 `LauncherSettingsActivity` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。`menuAnchor()` deprecated warning 现在出现在游戏助手和桌面两个下拉菜单页面，后续统一处理；其余仍为既有 deprecated warning。
  - 2026-05-29：修复 `LauncherSettingsActivity` 下拉展开问题并将自定义网格改为 Slider 后运行 `.\gradlew.bat assembleDebug`，构建成功。Launcher 页的 `menuAnchor()` deprecated warning 已消除，游戏助手页仍有同类 warning 待后续统一处理。
  - 2026-05-29：完成 `LockScreenSettingsActivity` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅剩既有 deprecated warning 与游戏助手页 `menuAnchor()` warning。
  - 2026-05-29：完成 `StatusBarSettingsActivity` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。新增 `MODE_WORLD_READABLE` deprecated warning，这是原状态栏通知数量 Hook 需要的兼容行为，暂时保留；游戏助手页 `menuAnchor()` warning 仍待后续处理。
  - 2026-05-29：完成 `ControlCenterSettingsActivity` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅剩既有 deprecated warning：`MainActivity.kt` 的系统栏颜色 API、`SettingsFragment.kt` 的 `versionCode`、游戏助手页 `menuAnchor()`、状态栏页 `MODE_WORLD_READABLE`。
  - 2026-05-29：完成 `SettingsDetailActivity` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。未新增迁移页面 warning；仍仅剩既有 deprecated warning：`MainActivity.kt` 的系统栏颜色 API、`SettingsFragment.kt` 的 `versionCode`、游戏助手页 `menuAnchor()`、状态栏页 `MODE_WORLD_READABLE`。
  - 2026-05-29：完成 `AuditFragment` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅剩既有 deprecated warning：`MainActivity.kt` 的系统栏颜色 API、`SettingsFragment.kt` 的 `versionCode`、游戏助手页 `menuAnchor()`、状态栏页 `MODE_WORLD_READABLE`。
  - 2026-05-29：完成 `OtaSettings` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅剩既有 deprecated warning：`MainActivity.kt` 的系统栏颜色 API、`SettingsFragment.kt` 的 `versionCode`、游戏助手页 `menuAnchor()`、状态栏页 `MODE_WORLD_READABLE`。
  - 2026-05-29：完成 `systemUISettings` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅剩既有 deprecated warning：`MainActivity.kt` 的系统栏颜色 API、`SettingsFragment.kt` 的 `versionCode`、游戏助手页 `menuAnchor()`、状态栏页 `MODE_WORLD_READABLE`。
  - 2026-05-29：完成 `HomeFragment` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅剩既有 deprecated warning：`MainActivity.kt` 的系统栏颜色 API、`SettingsFragment.kt` 的 `versionCode`、游戏助手页 `menuAnchor()`、状态栏页 `MODE_WORLD_READABLE`。
  - 2026-05-29：完成 `searchPage` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅剩既有 deprecated warning：`MainActivity.kt` 的系统栏颜色 API、`SettingsFragment.kt` 的 `versionCode`、状态栏页 `MODE_WORLD_READABLE`。

  下一步候选

  - 下一步可继续评估 `settingactivity/setting/floatingwindow/FloatingWindow.java`。该项涉及悬浮窗生命周期、视频播放和定时状态刷新，应单独迁移并验证。

  OtaSettings 迁移实施计划（2026-05-29）

  - 将 `settingactivity/ota/OtaSettings.java` 替换为同包名同类名的 Kotlin Compose Activity，保留 Manifest 启动入口和外部传入的 `app_name`、`app_package`。
  - 保留原配置键与 Hook 行为：
      - `custom_ota_parameters`：进入页面时继续保存为 `true`。
      - `disable_OtaCheck`：本地安装/禁用 OTA 检查开关。
      - `Custom_ota_target_versionName`：自定义 OTA 目标系统版本。
      - `Custom_ota_target_deviceID`：自定义 OTA 目标设备 SN。
  - 页面重排为 4 个 Compose 区域：
      1. 系统更新开关卡片：`disable_OtaCheck` 开关。
      2. OTA 信息卡片：点击“拉取 OTA 信息”后直接在页面内展示当前版本、新版本、更新日志、下载链接、大小、MD5，并提供“复制下载链接”和“复制更新日志”按钮，不再使用 `dialog_ota_info.xml` 承载主要结果。
      3. 9008/深刷包卡片：SN 输入框默认填充/提示本机 SN，点击查询后直接在页面内展示下载链接、解压密码、平台、刷机方式、首次上传时间、最后更新时间，并提供“复制下载链接”和“复制密码”按钮，不再使用 `dialog_pcflash_fetch.xml` 承载主要结果。
      4. OTA 请求伪装卡片：展示当前系统版本和当前机器 SN，提供系统版本与 SN 输入框并即时保存。
  - 异步逻辑处理：
      - 当前系统版本通过 `EnhancedShellExecutor.executeCommand("getprop ro.build.display.id")` 读取。
      - 当前 SN 继续按 `ro.odm.lenovo.gsn`、`ro.serialno`、`ro.boot.serialno` 顺序读取。
      - OTA 信息继续读取 `/data_mirror/data_ce/null/0/com.lenovo.tbengine/shared_prefs/lenovo_row_ota_package_info.xml` 并复用 XML 解析逻辑；失败时写入页面错误状态或使用轻量 Dialog/Toast。
      - 9008 固件查询先复用 `GetPCFlashFirmware.queryFirmwareAsync`，结果映射为 Compose state；后续可再把 `AsyncTask` 工具改成 Repository。
  - 仅保留必要 Dialog：
      - 重启 XP 模块作用域确认。
      - 无法获取 OTA 信息或固件信息时的错误提示可用页面内错误文本或 AlertDialog。
  - 保留重启作用域逻辑：继续停止 `app_package` 和 `com.lenovo.tbengine`。

  当前停止点

  - 已完成并验证：`searchPage` 页面 Compose 化。
  - 下一次继续时，建议评估 `FloatingWindow.java`，保留 `SettingsDetailActivity` 现有调用入口和悬浮窗行为，改为 `WindowManager` 承载 `ComposeView`。

  UI 基础问题整合与修正顺序（2026-05-29）

  按重构成本从低到高排序：

  1. 功能管理页卡片高度不一致（低成本，需现在修正）
      - 原因：`FeaturesFragment.kt` 的功能卡片高度由标题/描述文字自然撑开，描述行数不同会导致 LazyVerticalGrid 中不同卡片高度不一致。
      - 解决方案：给功能卡片设置稳定最小高度或固定高度，标题固定 1 行、描述固定 2 行，图标、箭头、内边距保持固定，避免同一网格中的卡片大小跳变。
      - 是否会后续自然修复：不会。功能管理页已经 Compose 化，细节页继续迁移不会改变这里的布局。

  2. Compose 下拉/Spinner 点击无反应（中低成本，需现在修正）
      - 原因：已迁移页面中下拉组件分散实现，部分仍使用旧 `menuAnchor()` 或不同的 `ExposedDropdownMenuBox` 写法，点击锚点和展开状态容易出现不一致。
      - 解决方案：在 `ui/components` 中新增统一 `ZToolDropdownField`，使用 Material3 新版 `menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)`，`OutlinedTextField` 设置 `readOnly`、`singleLine`、稳定 trailing icon，并让所有已迁移页面的下拉选择器逐步替换为该组件。
      - 是否会后续自然修复：不会。若继续复制现有写法，问题会扩散到后续迁移页面。

  3. 页面根背景不统一，动态取色只在部分页面生效（中等成本，需现在修正）
      - 原因：`ZToolTheme` 仍使用固定 `Md3eLightColors/Md3eDarkColors`，部分页面根容器没有显式使用 `MaterialTheme.colorScheme.background/surface`，因此不同页面在动态取色下呈现不一致。
      - 解决方案：`ZToolTheme` 在 Android 12+ 优先使用 `dynamicLightColorScheme/dynamicDarkColorScheme`；新增/使用统一的页面根背景组件或在现有页面根 `Box/Scaffold` 上统一设置 `MaterialTheme.colorScheme.background`。业务页面避免硬编码背景色。
      - 是否会后续自然修复：部分新页面可能因为使用新模板而改善，但已迁移页面不会自动变化，需要现在统一。

  4. 多数控件未统一适配 MD3 动态取色（中高成本，先修基础层，后续迁移中持续收敛）
      - 原因：页面内直接使用 `CardDefaults.cardColors`、局部固定 `Color(...)`、各自实现 Row/Card/Dialog，缺少项目级设计系统组件。
      - 解决方案：先补齐基础组件和默认色策略：`ZToolSurface`/页面容器、`ZToolCard`、`ZToolDropdownField`，后续再逐步把 `SwitchRow`、`SliderRow`、`TextField`、`ActionRow` 迁入组件层。日志级别色、用户自定义颜色预览等语义色可保留固定色，普通业务控件必须使用 `MaterialTheme.colorScheme`。
      - 是否会后续自然修复：只能在后续页面采用统一组件后逐步收敛；已迁移页面需要分批替换，不能依赖自然修复。

  剩余可迁移界面顺序（2026-05-29）

  按当前计划，现阶段不删除无引用 XML、旧 Adapter 或 `nav_graph.xml` 中指向旧布局的 `tools:layout`；这些清理项留到后续 XML View 层清理阶段。

  1. `settingactivity/setting/magicwindowsearch/searchPage.java`（已完成）
      - 迁移目标：保留 Manifest 启动入口和类名，将传统 `AppCompatActivity + activity_search_page.xml + RecyclerView + PackageAdapter` 改为 Kotlin `ComponentActivity + setContent`。
      - 行为保留：继续优先通过 root 读取 `/data/system/zui/embedding/embedding_config.json`，失败后回退到 `assets/embedding/embedding_config.json`；搜索包名时保持不区分大小写；结果详情继续展示包名、主 Activity、活动对、强制全屏、透明 Activity、左侧透明 Activity 和分屏配置字段。
      - Compose 方案：搜索框和按钮使用 Compose 状态；结果列表使用 `LazyColumn`；详情使用 Compose `AlertDialog`；错误/空结果使用 Toast 或页面内状态。

  2. `settingactivity/setting/floatingwindow/FloatingWindow.java`
      - 迁移目标：保留 `SettingsDetailActivity` 调用入口和悬浮窗行为，将 `WindowManager` 中承载的 XML View 改为 `ComposeView`。
      - 行为保留：步骤向导、当前前台应用/Activity 轮询、添加活动、配置选项、教程视频、Base64 配置保存和关闭/隐藏逻辑。
      - 风险说明：该项涉及悬浮窗生命周期、视频播放和定时状态刷新，成本高于普通 Activity，应在 `searchPage` 后单独迁移。

  3. `utils/AppChooserDialog.java`
      - 迁移目标：将复用范围较广的应用选择弹窗迁移为 Compose Dialog 或可复用选择器组件。
      - 行为保留：用户应用加载、搜索、多选、已选数量、回调 `AppSelectionCallback`，以及游戏助手、桌面、系统设置详情页现有调用入口。

  4. `LoadingDialog.java`
      - 迁移目标：将旧 XML loading dialog 替换为 Compose 状态或 Compose Dialog。
      - 行为保留：SettingsDetailActivity 中耗时任务开始/结束时的加载提示。

  5. `SettingsDetailActivity.kt` 内剩余 View Dialog
      - 迁移目标：将 `dialog_config_selection.xml`、`dialog_font_input.xml` 等仍由 `layoutInflater.inflate` 承载的复杂弹窗迁移到 Compose。
      - 行为保留：配置文件选择、多选状态、字体名称/描述输入、安装/移除/刷入相关流程和现有 root/Magisk/KSU 逻辑。
