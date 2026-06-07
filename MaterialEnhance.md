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

用于统一处理 item 间距、shape、container color 和 pressed 状态。

## 1. 设置项增加图标提示

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

## 2. 设置页入口调整

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

## 验证建议

- 运行 `.\gradlew.bat assembleDebug`。
- 在 Material3 Expressive 和 Miuix 两种前端风格下分别检查设置页。
- 检查浅色、深色、AMOLED、动态色、手动色、2021/2025 调色板组合。
- 确认旧偏好升级后不会崩溃，未设置 `materialPaletteMode` 时默认使用 `Expressive2025`。
- 检查窄屏下 popup menu、图标、标题和 summary 不重叠。

