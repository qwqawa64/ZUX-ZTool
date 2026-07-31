# 偏好键管理系统

本文档说明 ZUX-ZTool 当前的偏好键（Preference Key）集中管理架构，以及如何在不同场景下添加新的偏好键。

## 架构概览

所有 Hook 和应用设置共用同一个 SharedPreferences 文件 `xposed_module_config`。偏好键的**名称**、**数据类型**、**默认值**现在统一在单一可信源中定义：

```
app/src/main/java/com/qimian233/ztool/data/PreferenceKeys.kt    ← 所有键的权威定义
app/src/main/java/com/qimian233/ztool/utils/ModulePreferencesUtils.kt  ← 应用侧读写工具
```

### 数据类

`PreferenceKeys.kt` 中定义了四种类型化键：

| 类型      | Kotlin 类                   | 示例                                                 |
|---------|----------------------------|----------------------------------------------------|
| Boolean | `BoolKey(name, default)`   | `BoolKey("disable_force_stop", false)`             |
| Int     | `IntKey(name, default)`    | `IntKey("CustomLauncherRow", 4)`                   |
| Float   | `FloatKey(name, default)`  | `FloatKey("Custom_StatusBarClockTextSize", 16.0f)` |
| String  | `StringKey(name, default)` | `StringKey("ForceStopWhiteList", "")`              |

每个键同时作为 `PreferenceKeys` object 的 `@JvmField val` 常量暴露，在 Kotlin 中可通过 `PreferenceKeys.CONSTANT_NAME.name` 访问，在 Java 中可通过 `PreferenceKeys.CONSTANT_NAME.name`（或 `.getName()`）访问。

### 自动类型推断

`ModulePreferencesUtils.writeConfigToSharedPrefs()` 在备份/恢复时，会遍历 `PreferenceKeys` 中的列表（`booleanKeys`、`intKeys`、`floatKeys`）来推断每个键的正确数据类型。**只要在 `PreferenceKeys.kt` 的对应列表中注册了新键，备份/恢复就能自动正确处理，无需额外代码。**

---

## 添加新偏好键的流程

### 步骤 1：在 PreferenceKeys.kt 中注册

打开 `app/src/main/java/com/qimian233/ztool/data/PreferenceKeys.kt`，根据键的数据类型在对应区域添加常量，同时将其加入对应的类型列表。

#### Boolean 键（最常见：Hook 启用开关、子功能开关）

```kotlin
// 在 Boolean 键区域按作用域分组的适当位置添加：
@JvmField val NEW_FEATURE_ENABLED = BoolKey("new_feature_enabled", false)

// 然后在 booleanKeys 列表末尾加入 NEW_FEATURE_ENABLED
```

**Boolean 键同时也是 Hook 的启用/禁用开关**：如果键名等于某个 Hook 模块的 `getModuleName()` 返回值，前端打开这个开关就会启用该 Hook（无需额外代码）；如果键是子功能开关（非模块名），则需要在 Hook 代码中手动读取。

#### Int 键

```kotlin
@JvmField val NEW_FEATURE_LEVEL = IntKey("new_feature_level", 5)

// 加入 intKeys 列表
```

#### Float 键

```kotlin
@JvmField val NEW_FEATURE_SCALE = FloatKey("new_feature_scale", 1.0f)

// 加入 floatKeys 列表
```

#### String 键

```kotlin
@JvmField val NEW_FEATURE_PATTERN = StringKey("new_feature_pattern", "")

// 加入 stringKeys 列表
```

**关键规则：**
- 默认值必须与 Hook 侧和 Repository 侧使用的一致
- `@JvmField` 使常量可从 Java Hook 中直接访问
- 必须将新键加入对应类型的列表（`booleanKeys` / `intKeys` / `floatKeys` / `stringKeys`），否则备份/恢复无法识别

---

### 步骤 2：在 Repository 中使用

Repository 的 `companion object` 中不再直接写字符串字面量，而是引用 `PreferenceKeys` 常量：

```kotlin
import com.qimian233.ztool.data.PreferenceKeys

class ExampleSettingsRepository(
    private val context: Context
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

        // 使用 PreferenceKeys 常量而非手写字符串
        private val KEY_NEW_HOOK_ENABLED = PreferenceKeys.NEW_FEATURE_ENABLED.name
        private val KEY_CUSTOM_LEVEL = PreferenceKeys.NEW_FEATURE_LEVEL.name
    }
}
```

**注意：** 因为 `PreferenceKeys.CONSTANT.name` 不是编译期常量，companion object 中的声明需从 `const val` 改为 `val`。

---

### 步骤 3：在 Kotlin Hook 中使用

Kotlin Hook 中通过 `PreferenceKeys` 常量读取偏好键：

```kotlin
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

class NewFeatureHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.NEW_FEATURE_ENABLED.name
    override fun getTargetPackages(): Array<String> = arrayOf("com.android.systemui")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        // 读取主开关（实际上 isEnabled() 已经检查了 getModuleName()，
        // 但 Hook 内部可能需要读取子功能开关）
        val prefs = xposed.getRemotePreferences("xposed_module_config")

        // Boolean 子功能
        val subFeatureEnabled = prefs.getBoolean(
            PreferenceKeys.SUB_FEATURE_ENABLED.name,
            PreferenceKeys.SUB_FEATURE_ENABLED.default
        )

        // Int 配置
        val level = prefs.getInt(
            PreferenceKeys.NEW_FEATURE_LEVEL.name,
            PreferenceKeys.NEW_FEATURE_LEVEL.default
        )

        // Float 配置
        val scale = prefs.getFloat(
            PreferenceKeys.NEW_FEATURE_SCALE.name,
            PreferenceKeys.NEW_FEATURE_SCALE.default
        )

        // String 配置
        val pattern = prefs.getString(
            PreferenceKeys.NEW_FEATURE_PATTERN.name,
            PreferenceKeys.NEW_FEATURE_PATTERN.default
        ) ?: ""

        // ... Hook 逻辑 ...
    }
}
```

**要点：**
- `getModuleName()` 返回的字符串同时也是 `xposed_module_config` 中的 Boolean 键，由 `BaseHookModule.isEnabled()` 自动读取
- 如果 Hook 没有子功能开关（仅由模块名控制启用/禁用），则 Hook 中无需额外读取偏好键
- 子功能键使用 `PreferenceKeys.CONSTANT_NAME.name` 获取键名字符串，用 `PreferenceKeys.CONSTANT_NAME.default` 获取默认值

---

## 关键规则

1. **所有 `xposed_module_config` 中的键必须先在 `PreferenceKeys.kt` 注册**，再在 Repository 和 Hook 中使用。
2. **键名不得重命名**。已存在的键名必须保持不变，以免破坏用户配置。
3. **默认值必须一致**：`PreferenceKeys` 中定义的默认值应与 Repository 和 Hook 中使用的默认值完全一致。
4. **类型必须匹配**：Boolean 键加入 `booleanKeys` 列表，Int 键加入 `intKeys` 列表，以此类推。类型不匹配会导致备份/恢复时数据损坏。
5. **不要手写键名字符串**。始终使用 `PreferenceKeys.CONSTANT_NAME.name` 引用，确保拼写和大小写完全一致。
6. **`PreferenceKeys` 中的 `@JvmField val` 常量命名**使用 `SCREAMING_SNAKE_CASE`，与 `BoolKey` 的 `name` 参数（通常为 `snake_case` 或 `PascalCase` 的历史命名）区分开。

---

## 文件清单

| 文件                                | 作用                                                     |
|-----------------------------------|--------------------------------------------------------|
| `data/PreferenceKeys.kt`          | 所有键的单一可信源，按类型分列表                                       |
| `utils/ModulePreferencesUtils.kt` | 应用侧 SharedPreferences 读写工具                             |
| `data/**/*Repository.kt`          | 各功能模块的 Repository，通过 `PreferenceKeys.CONST.name` 引用键   |
| `hook/modules/**/`                | Hook 实现，Kotlin Hook 通过 `PreferenceKeys.CONST.name` 读取键 |
