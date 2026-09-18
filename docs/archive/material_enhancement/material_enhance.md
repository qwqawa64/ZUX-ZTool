# Material 3 Expressive 前端增强方案

## 目标

增强项目中 `FrontendStyle.Material3Expressive` 的前端表现，使其更接近 Google Material 3 / Material 3 Expressive 的设计范式，同时不破坏现有 Miuix 分支。

本次增强重点包括：

- 卡片内增加更明显的分段式间隔与层级。
- 使用弹出式菜单替代设置页中的表单式 dropdown。
- 为设置项增加图标提示，提高可扫读性。
- 支持 Material 调色板模式选择：2021 模式与 2025 模式。

## 设计原则

- Material3 Expressive 与 Miuix 分支分开处理，避免 Miuix 视觉被 Material 规则污染。
- 主题状态、设置项模型、共享组件优先改造，减少单页面重复实现。
- 设置页中的选项选择器应更像系统设置菜单，而不是表单输入框。
- 高风险业务边界不变：不改 Hook、Root/Shell、Magisk、SharedPreferences 业务配置键。

## 1. 增加 Material 调色板模式

### 新增模型

在 `app/src/main/java/com/qimian233/ztool/ui/theme/ZToolThemeSettings.kt` 增加：

```kotlin
enum class MaterialPaletteMode {
    MaterialYou2021,
    Expressive2025
}
```

并扩展 `ZToolThemeSettings`：

```kotlin
val materialPaletteMode: MaterialPaletteMode = MaterialPaletteMode.Expressive2025
```

### 语义定义

- `MaterialYou2021`：接近 Android 12 / Material You 的柔和 tonal palette，整体更稳定、低饱和。
- `Expressive2025`：接近 Material 3 Expressive，提升 primary/tertiary 的存在感，更积极使用 `surfaceContainerHigh`、`surfaceContainerHighest` 等层级色。

### 偏好保存

在 `app/src/main/java/com/qimian233/ztool/data/theme/ThemePreferencesRepository.kt` 增加：

- `KEY_MATERIAL_PALETTE_MODE`
- `saveMaterialPaletteMode(mode: MaterialPaletteMode)`
- `loadSettings()` 中读取该枚举
- `THEME_KEYS` 中加入该 key

### ViewModel 支持

在 `app/src/main/java/com/qimian233/ztool/viewmodel/SettingsViewModel.kt` 增加：

```kotlin
fun setMaterialPaletteMode(mode: MaterialPaletteMode)
```

并更新 `SettingsUiState.themeSettings`。

## 2. 调整主题解析逻辑

在 `app/src/main/java/com/qimian233/ztool/ui/theme/ZToolTheme.kt` 中，让 `resolveZToolColorScheme()` 根据 `materialPaletteMode` 选择不同策略。

推荐策略：

- 动态色开启时：仍优先使用系统动态色，但可在 2025 模式中更积极映射 tertiary、surface container 层级。
- 手动色开启时：`manualColorScheme()` 增加 `paletteMode` 参数。
- 默认色关闭动态色时：`defaultColorScheme()` 增加 `paletteMode` 参数。

建议拆分为：

```kotlin
private fun defaultMaterial2021ColorScheme(darkTheme: Boolean): ColorScheme
private fun defaultExpressive2025ColorScheme(darkTheme: Boolean): ColorScheme
private fun manualColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    paletteMode: MaterialPaletteMode
): ColorScheme
```

2021 模式应更柔和，2025 模式可以保留并增强当前 `Md3eLightColors` / `Md3eDarkColors` 的表达。

## 3. 卡片内明显分段式间隔

当前 `ZToolSettingsSection` 使用一个大 `ZToolCard` 包裹所有设置项，项目之间主要依赖 `ZToolSettingsDivider()`。

Material3 Expressive 下建议改为 grouped-list 视觉：

- 每个 item 拥有独立 surface 层，例如 `surfaceContainerHigh`。
- item 之间加入 6-8dp 间隔，或使用更明显的 inset separator。
- 首项、尾项使用不同 shape：顶部圆角、底部圆角，中间项近似矩形。
- section title 与内容组分离，title 使用较轻的视觉权重。
- Miuix 分支保留现状，继续使用 Miuix `BasicComponent` 和 divider。

主要涉及文件：

- `app/src/main/java/com/qimian233/ztool/ui/components/ZToolSettingsModel.kt`
- `app/src/main/java/com/qimian233/ztool/ui/components/ZToolSettings.kt`
- `app/src/main/java/com/qimian233/ztool/ui/components/ZToolSurfaces.kt`

建议新增内部组件：

```kotlin
@Composable
private fun MaterialExpressiveSettingsItemSurface(
    index: Int,
    count: Int,
    content: @Composable () -> Unit
)
```

用于统一处理 item 间距、shape、container color 和 pressed 状态。

## 4. 使用弹出式菜单替代 dropdown

当前 `ZToolDropdownField` 使用 `ExposedDropdownMenuBox + OutlinedTextField`。对于设置页中的固定选项，这种形式偏表单输入，不够接近系统设置范式。

建议新增 `ZToolPopupMenuField`：

```kotlin
@Composable
fun <T> ZToolPopupMenuField(
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
)
```

Material3 Expressive 下：

- 触发器使用 `FilledTonalButton`、`OutlinedButton` 或 compact trailing field。
- 菜单使用 `DropdownMenu` + `DropdownMenuItem`。
- 当前选中项显示 check icon。
- 可扩展支持 option leading icon 与 supporting label。

Miuix 分支：

- 可暂时保留现有 `MiuixTextField + ExposedDropdownMenu`。
- 或后续单独实现 Miuix 风格弹出菜单。

优先替换设置页中的：

- 前端风格
- 主题模式
- Material 调色板模式

## 5. 设置项增加图标提示

当前 `SettingItem.Entry` 和 `SettingItem.Action` 支持 `leadingContent`，但 `Switch`、`Dropdown`、`Slider`、`TextInput` 没有统一图标入口。

建议在 `SettingItem` 各数据类中增加：

```kotlin
val icon: ImageVector? = null
```

由共享组件统一渲染 leading icon。

优先为全局设置页补充图标：

- 前端风格：`Palette` 或 `DashboardCustomize`
- 主题模式：`DarkMode`
- 动态色：`AutoAwesome`
- 手动主题色：`FormatColorFill`
- Material 调色板模式：`Tune`
- AMOLED 纯黑：`Contrast`
- 日志服务：`Article` 或 `Terminal`
- 备份：`Backup`
- 恢复：`RestorePage`
- 关于：`Info`

实现时优先使用项目现有 Material Icons 依赖，不新增图标库。

## 6. 设置页入口调整

在 `app/src/main/java/com/qimian233/ztool/SettingsRoute.kt` 的 `ThemeSettingsSection` 中新增调色板模式选择：

```kotlin
DropdownSettingRow(
    title = stringResource(R.string.material_palette_mode_title),
    value = paletteModeOptions.first { it.value == settings.materialPaletteMode }.label,
    options = paletteModeOptions,
    optionLabel = { it.label },
    onOptionSelected = { onMaterialPaletteModeChanged(it.value) }
)
```

实际实现时应优先使用新的 popup menu 组件，而不是继续使用旧 `DropdownSettingRow`。

需要新增字符串资源：

- `material_palette_mode_title`
- `material_palette_mode_2021`
- `material_palette_mode_2025`
- `material_palette_mode_summary`，如需要 summary。

## 7. 推荐实施顺序

1. 增加 `MaterialPaletteMode`、主题设置字段和偏好保存。
2. 增加 ViewModel setter，并在设置页接入调色板模式选择。
3. 调整 `ZToolTheme.resolveZToolColorScheme()`，让 2021/2025 模式影响默认色和手动 seed 生成策略。
4. 新增 popup menu 组件，并替换设置页中的主题类 dropdown。
5. 增强 `ZToolSettingsSection` 的 Material3 Expressive 分段视觉。
6. 扩展设置项图标模型，并先为全局设置页补图标。
7. 运行 `.\gradlew.bat assembleDebug` 验证。

## 验证建议

- 运行 `.\gradlew.bat assembleDebug`。
- 在 Material3 Expressive 和 Miuix 两种前端风格下分别检查设置页。
- 检查浅色、深色、AMOLED、动态色、手动色、2021/2025 调色板组合。
- 确认旧偏好升级后不会崩溃，未设置 `materialPaletteMode` 时默认使用 `Expressive2025`。
- 检查窄屏下 popup menu、图标、标题和 summary 不重叠。

