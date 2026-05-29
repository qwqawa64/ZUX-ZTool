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

  最近验证

  - 2026-05-29：`.\gradlew.bat assembleDebug` 构建成功。仅存在既有 deprecated warning：
      - `MainActivity.kt` 中 `statusBarColor`、`navigationBarColor`。
      - `SettingsFragment.kt` 中 `versionCode`。
  - 2026-05-29：完成 `FrameworkSettingsActivity` Compose 化后再次运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅存在上述既有 deprecated warning。
  - 2026-05-29：完成 `SafeCenterSettingsActivity` Compose 化后运行 `.\gradlew.bat assembleDebug`，构建成功。仍仅存在上述既有 deprecated warning。

  下一步候选

  - 优先继续迁移结构简单、以开关为主的设置页，例如：
      - `settingactivity/gametool/GameToolSettngs.java`
      - `settingactivity/launcher/LauncherSettingsActivity.java`
  - 暂缓迁移 `OtaSettings.java`，因为它包含输入框、异步拉取、剪贴板、多个自定义 Dialog 和 root 文件读取，适合在通用设置页模式稳定后处理。

  当前停止点

  - 已完成并验证：`SafeCenterSettingsActivity` 页面 Compose 化。
  - 下一次继续时，应从候选页中选择一个页面迁移；建议优先处理 `GameToolSettngs.java` 或 `LauncherSettingsActivity.java`，继续沉淀设置页 Compose 模式。
