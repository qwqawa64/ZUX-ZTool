# 新 Hook 接入前端指南

本文说明在 ZUX-ZTool 中为新的 Hook 功能接入前端配置项的推荐做法，重点覆盖 SharedPreferences、开关项和其它自定义控件。这里的“前端”主要指应用侧 Compose 设置页、Repository、ViewModel 和 UiState；Hook 侧仍按既有模块入口和 HookManager 规则接入。

## 基本原则

1. 前端不要直接在 Composable 中读写 SharedPreferences。配置读写应放在 `data/**/**Repository.kt`，页面只消费 `UiState` 并调用 ViewModel 方法。
2. Hook 相关配置统一使用 `ModulePreferencesUtils` 写入 `xposed_module_config`。不要为 Hook 开关新增独立 SharedPreferences 文件，除非确实不是 Hook 配置。
3. 新增配置键必须和 Hook 侧读取的键完全一致，包括大小写。已有键不得重命名。
4. 默认值必须前端和 Hook 侧一致。前端显示默认关闭，Hook 侧却默认开启，会造成未打开页面时行为不一致。
5. 修改功能页 UI 后建议运行 `.\gradlew.bat assembleDebug`。新增文档或完成用户指定任务后，按项目要求提交。

## SharedPrefs 处理

应用侧 Hook 配置使用：

```kotlin
private val prefsUtils = ModulePreferencesUtils(context)
```

`ModulePreferencesUtils` 默认读写模块包 `com.qimian233.ztool` 下的 `xposed_module_config`，并提供以下常用方法：

```kotlin
prefsUtils.loadBooleanSetting(KEY, false)
prefsUtils.saveBooleanSetting(KEY, enabled)

prefsUtils.loadStringSetting(KEY, "")
prefsUtils.saveStringSetting(KEY, value)

prefsUtils.loadIntegerSetting(KEY, defaultValue)
prefsUtils.saveIntegerSetting(KEY, value)

prefsUtils.loadFloatSetting(KEY, defaultValue)
prefsUtils.saveFloatSetting(KEY, value)
```

推荐在对应 Repository 的 `companion object` 中集中声明键：

```kotlin
class ExampleSettingsRepository(
    context: Context
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): ExampleSettingsUiState {
        return ExampleSettingsUiState(
            newHookEnabled = prefsUtils.loadBooleanSetting(KEY_NEW_HOOK_ENABLED, false),
            customLevel = prefsUtils.loadIntegerSetting(KEY_CUSTOM_LEVEL, DEFAULT_LEVEL)
        )
    }

    fun saveNewHookEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NEW_HOOK_ENABLED, enabled)
    }

    fun saveCustomLevel(level: Int) {
        prefsUtils.saveIntegerSetting(KEY_CUSTOM_LEVEL, level.coerceIn(LEVEL_MIN, LEVEL_MAX))
    }

    companion object {
        const val LEVEL_MIN = 0
        const val LEVEL_MAX = 10
        private const val DEFAULT_LEVEL = 5

        private const val KEY_NEW_HOOK_ENABLED = "new_hook_enabled"
        private const val KEY_CUSTOM_LEVEL = "new_hook_level"
    }
}
```

Hook 侧读取时使用同一个键和默认值：

```java
boolean enabled = prefs.loadBooleanSetting("new_hook_enabled", false);
int level = prefs.loadIntegerSetting("new_hook_level", 5);
```

## 接入一个开关

一个普通 Hook 开关通常需要改四处：Repository、UiState、ViewModel、页面 section。

### 1. Repository

在对应作用域的 Repository 中增加读取和保存方法。例如安全中心功能放在 `data/safecenter/SafeCenterSettingsRepository.kt`，桌面功能放在 `data/launcher/LauncherSettingsRepository.kt`。

```kotlin
fun loadState(): ExampleSettingsUiState {
    return ExampleSettingsUiState(
        newHookEnabled = prefsUtils.loadBooleanSetting(KEY_NEW_HOOK_ENABLED, false)
    )
}

fun saveNewHookEnabled(enabled: Boolean) {
    prefsUtils.saveBooleanSetting(KEY_NEW_HOOK_ENABLED, enabled)
}
```

### 2. UiState

在对应 ViewModel 文件里的 UiState 增加状态字段：

```kotlin
data class ExampleSettingsUiState(
    val newHookEnabled: Boolean = false
)
```

### 3. ViewModel

ViewModel 负责先更新内存状态，再写入 Repository：

```kotlin
fun setNewHookEnabled(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(newHookEnabled = enabled)
    repository.saveNewHookEnabled(enabled)
}
```

### 4. Compose 页面

页面中使用 `SettingItem.Switch`。不要直接调用 `prefsUtils`。

```kotlin
SettingItem.Switch(
    title = stringResource(R.string.new_hook_title),
    summary = stringResource(R.string.new_hook_summary),
    checked = state.newHookEnabled,
    onCheckedChange = onNewHookEnabledChanged
)
```

页面函数参数也需要把事件一路传进来：

```kotlin
onNewHookEnabledChanged = viewModel::setNewHookEnabled
```

## 添加其它自定义控件

项目已有 `SettingItem` 模型，优先使用共享组件，避免在业务页面重复写样式。

### 下拉选项

适合模式选择、策略选择、样式选择。使用 `SettingItem.Dropdown` 或现有 `ZToolPopupMenuSettingRow`。

```kotlin
enum class NewHookMode {
    Default,
    Aggressive,
    Compatibility
}
```

Repository 保存为字符串时，应显式转换，避免未来 enum 重命名破坏兼容：

```kotlin
fun saveMode(mode: NewHookMode) {
    prefsUtils.saveStringSetting(KEY_MODE, mode.name)
}

private fun loadMode(): NewHookMode {
    val raw = prefsUtils.loadStringSetting(KEY_MODE, NewHookMode.Default.name)
    return NewHookMode.entries.firstOrNull { it.name == raw } ?: NewHookMode.Default
}
```

页面：

```kotlin
SettingItem.Dropdown(
    label = stringResource(R.string.new_hook_mode_title),
    value = modeLabel(state.mode),
    options = NewHookMode.entries,
    optionLabel = { modeLabel(it) },
    onOptionSelected = onModeChanged
)
```

如果选项有较长说明，或需要和其它控件组合，可参考 `LauncherSettingsActivity.kt` 中 `ForceStopModeRow` 的写法。

### 滑块

适合有限范围的数值配置，例如行列数、尺寸、阈值。数值必须在 Repository 和 ViewModel 中做边界约束。

```kotlin
SettingItem.Slider(
    title = stringResource(R.string.new_hook_level_title),
    summary = stringResource(R.string.new_hook_level_summary),
    value = state.customLevel.toFloat(),
    valueText = state.customLevel.toString(),
    valueRange = 0f..10f,
    steps = 9,
    onValueChange = { onCustomLevelChanged(it.toInt()) }
)
```

Repository：

```kotlin
fun saveCustomLevel(level: Int) {
    prefsUtils.saveIntegerSetting(KEY_CUSTOM_LEVEL, level.coerceIn(LEVEL_MIN, LEVEL_MAX))
}
```

### 文本输入

适合格式字符串、包名、API 地址、白名单等。使用 `SettingItem.TextInput`，并在保存前做必要的 trim、空值处理或格式校验。

```kotlin
SettingItem.TextInput(
    title = stringResource(R.string.new_hook_pattern_title),
    summary = stringResource(R.string.new_hook_pattern_summary),
    label = stringResource(R.string.new_hook_pattern_label),
    value = state.pattern,
    onValueChange = onPatternChanged,
    singleLine = true
)
```

Repository：

```kotlin
fun savePattern(pattern: String) {
    prefsUtils.saveStringSetting(KEY_PATTERN, pattern.trim())
}
```

### 条件显示的子项

当某个开关关闭时，其附属配置通常不要显示，或设置为 `enabled = false`。项目里常见写法是 `buildList`：

```kotlin
val items = buildList {
    add(
        SettingItem.Switch(
            title = stringResource(R.string.new_hook_title),
            checked = state.newHookEnabled,
            onCheckedChange = onNewHookEnabledChanged
        )
    )

    if (state.newHookEnabled) {
        add(
            SettingItem.Slider(
                title = stringResource(R.string.new_hook_level_title),
                value = state.customLevel.toFloat(),
                onValueChange = { onCustomLevelChanged(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9
            )
        )
    }
}
```

### 完全自定义行

当现有模型无法表达复杂交互时使用 `SettingItem.Custom`。例如应用选择器、复合滑块、带按钮的行。自定义行仍应使用 `MaterialTheme` 和项目共享组件，避免写死颜色和重复卡片。

```kotlin
SettingItem.Custom(
    content = {
        CustomHookConfigRow(
            value = state.value,
            onClick = onOpenPicker
        )
    }
)
```

## 字符串和文案

新增 UI 文案应写入 `app/src/main/res/values/strings.xml`，页面中使用 `stringResource(R.string.xxx)`。不要在 Composable 中硬编码中文或英文文本，除非是调试临时内容。

推荐命名：

```xml
<string name="new_hook_title">新 Hook 功能</string>
<string name="new_hook_summary">说明该功能影响的系统行为和生效条件。</string>
```

文案应写清楚：

1. 功能影响哪个应用或系统区域。
2. 是否需要重启目标应用、SystemUI 或系统。
3. 是否依赖 Root、LSPosed 作用域或特定 ZUI/ZUX 版本。

## 重启和生效提示

大多数 Hook 配置不会立即影响已加载的目标进程。已有页面通常提供右下角刷新按钮，并通过 Repository 执行 `am force-stop` 或其它重启命令。

如果新 Hook 属于已有作用域页面，优先复用该页面已有的重启按钮和确认弹窗。不要为每个开关都弹 Toast 或立即重启目标应用。

如果新 Hook 需要特殊重启目标：

1. 在 Repository 中封装 shell 或 root 操作。
2. ViewModel 暴露 `showRestartConfirmDialog`、`dismissRestartConfirmDialog` 和执行方法。
3. Composable 只负责显示确认弹窗和 Toast 结果。

## 新页面还是加入已有页面

优先把新 Hook 加到对应作用域的现有详情页：

| Hook 作用域 | 推荐页面/Repository |
| --- | --- |
| `com.android.systemui` | `SystemUiSettingsRoute`、状态栏、控制中心或锁屏子页面 |
| `com.zui.launcher` | `LauncherSettingsRoute` / `LauncherSettingsRepository` |
| `com.lenovo.safecenter` 或 DocumentsUI 相关 | `SafeCenterSettingsRoute` / `SafeCenterSettingsRepository` |
| `com.android.settings` | `SettingsDetailRoute` / `SettingsDetailRepository` |
| `android` 系统框架 | `FrameworkSettingsRoute` / `FrameworkSettingsRepository` |
| 安装器 | `PackageInstallerSettingsRoute` / `PackageInstallerSettingsRepository` |
| 游戏助手 | `GameToolSettingsRoute` / `GameToolSettingsRepository` |
| OTA | `OtaSettingsRoute` / `OtaSettingsRepository` |

只有当新 Hook 有独立的复杂流程、多个子页面或已有页面无法合理承载时，再考虑新增 Route/Activity。保留 Manifest 中已有 Activity 的启动契约，不要随意改包名或类名。

## Hook 侧一致性检查

前端接入完成后，至少检查以下事项：

1. Hook 模块是否已经注册到 `HookManager`。
2. Hook 作用域包名是否已在 `res/values/array.xml` 的 `xposed_scope` 中覆盖。
3. 前端保存的 SharedPrefs 键是否和 Hook 侧读取完全一致。
4. 前端默认值是否和 Hook 侧默认值一致。
5. 字符串、数值、列表格式是否和 Hook 侧解析方式一致。
6. 目标应用重启后配置是否能生效。

## 常见错误

1. 在 Composable 中直接写 `context.getSharedPreferences(...)`：应移动到 Repository。
2. 前端使用 `apply()`，Hook 侧马上读取导致时序不稳定：`ModulePreferencesUtils` 现有保存方法使用 `commit()`，新增保存逻辑应保持一致。
3. 配置键大小写不一致：例如 `CustomGridSize` 和 `custom_grid_size` 是两个不同键。
4. 把 Hook 配置写进主题或应用 UI 偏好：Hook 配置必须使用 `ModulePreferencesUtils`。
5. 只更新 UiState 不保存 Repository，退出页面后丢失配置。
6. 只保存 Repository 不更新 UiState，开关 UI 不跟手。
7. 在页面中直接执行 Root/Shell：应由 Repository 封装，ViewModel 调用。

## 最小接入清单

新增一个 Hook 前端配置时，按以下顺序处理：

1. 确定配置键、类型、默认值，并和 Hook 侧保持一致。
2. 在对应 Repository 中加入 `loadState()` 字段读取和保存方法。
3. 在对应 `UiState` 增加字段。
4. 在 ViewModel 中增加 `setXxx(...)` 方法。
5. 在设置页的 `SettingSection` 中加入 `SettingItem.Switch`、`Dropdown`、`Slider`、`TextInput` 或 `Custom`。
6. 在 `strings.xml` 增加标题和说明。
7. 如需重启，复用或补充该页面的重启确认流程。
8. 运行 `.\gradlew.bat assembleDebug` 验证。

