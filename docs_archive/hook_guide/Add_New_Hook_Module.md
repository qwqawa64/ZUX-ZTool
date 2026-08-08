# 新 Hook 模块（后端）接入指南

> 本文面向**后端 Hook 实现**（Xposed 模块侧）。前端配置接入请阅读 **[Add_Frontend_Item.md](./Add_Frontend_Item.md)**；偏好键集中管理请阅读根目录 **[Add_New_Preference_Key_zh-CN.md](../../Add_New_Preference_Key_zh-CN.md)**。
>
> 所有偏好键必须先注册到 `PreferenceKeys.kt`，然后通过 `PreferenceKeys.CONSTANT_NAME.name` 引用；所有作用域包名统一由 `ScopeKeys` 管理，通过 `ScopeKeys.CONSTANT.packageName` 引用。不要手写键名或包名字符串。

## 1. 创建 Kotlin 类

新 Hook 必须使用 **Kotlin**（`.kt`）编写，**不允许新增 Java 代码**。Java 仅限紧急修复既有 Java 基础设施（如 `BaseHookModule.java`、`HookManager.java`）。

- 位置：`app/src/main/java/com/qimian233/ztool/hook/modules/<作用域>/`，按目标应用放入对应子包（如 `systemui/`、`launcher/`、`setting/`）。
- 文件编码：显式使用 UTF-8（项目包含中文字符串）。
- 测试 Hook：`getModuleName()` 返回 `"hook_test"` 或 `"test_hook"` 时恒启用，无需前端开关。

## 2. 继承 AppHookModule 或 SystemHookModule

两个基类都是 Java 抽象类，Kotlin 类直接继承即可：

| 基类 | 适用场景 | 必须实现 |
|---|---|---|
| `AppHookModule` | 普通 App 包 Hook（SystemUI、Launcher、设置等） | `handleLoadPackage(...)` |
| `SystemHookModule` | 系统框架 Hook（`android` / `system`） | `handleSystemServerStarting(...)`（`handleLoadPackage` 按需实现） |

```kotlin
class ExampleHook : AppHookModule() {
    // ...
}
```

## 3. 实现 getModuleName() 与 getTargetPackages()

### getModuleName() —— 利用 PreferenceKeys

返回一个 `PreferenceKeys` 常量键名。该键名同时也是 `xposed_module_config` 中的 Boolean 开关，由 `BaseHookModule.isEnabled()` 自动读取：

```kotlin
override fun getModuleName(): String = PreferenceKeys.EXAMPLE_HOOK_ENABLED.name
```

- 默认值在 `PreferenceKeys.kt` 中注册时确定，前端开关和 Hook 侧读取共用同一个键。
- 如果 Hook 还有子功能开关或数值配置，在 `handleLoadPackage` 内通过 `remotePreferences` 读取（见第 4 节）。

### getTargetPackages() —— 利用 ScopeKeys

返回包名数组，必须引用 `ScopeKeys` 常量，不要手写包名字符串：

```kotlin
override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)
```

如果目标包尚未注册：

1. 在 `app/src/main/java/com/qimian233/ztool/data/keys/ScopeKeys.kt` 注册新 `Scope`（含推荐重启方式 `HowToRestart`）。
2. 在 `getTargetPackages()` 中引用 `ScopeKeys.CONSTANT.packageName`。
3. 将包名加入构建期资源 `app/src/main/resources/META-INF/xposed/scope.list`（LSPosed 注入声明，与 `ScopeKeys` 相互独立、两个都要维护）。

## 4. 实现 handleLoadPackage(...)

基类 `BaseHookModule` 提供了一组帮助类/方法，统一完成日志、Hook 安装、反射查找和配置读取。

### logger —— 统一日志

`protected ModuleLog logger` 实例字段，Log4j 风格六级别 API：`trace` / `debug` / `info` / `warn` / `error` / `fatal`。

```kotlin
logger.info("开始安装 ExampleHook")
logger.debug("修改字段: $fieldName")
logger.warn("方法未找到，回退到默认值")
logger.error("Hook 安装失败，阻断拦截链", throwable)
```

日志级别如何确定，参考 **[docs_archive/new_log_system/migrate_and_use_new_logging_system.md](../new_log_system/migrate_and_use_new_logging_system.md)**（相对本文件路径为 `../new_log_system/migrate_and_use_new_logging_system.md`）。

### hookWithId —— 统一安装 Hook

**受保护的实例方法**（非静态），所有 Hook 安装统一走它：

```kotlin
protected HookHandle hookWithId(Executable target, String id, Hooker hooker)
```

等价于 `xposed.hook(target).setId(id).intercept(hooker)`。`id` 需在模块内稳定唯一；热重载时，同一可执行对象上相同 `id` 的新 Hook 会原子替换旧 Hook，消除 Hook 空窗期。

```kotlin
hookWithId(targetMethod, "example_hook_method") { chain ->
    logger.debug("拦截到目标方法")
    chain.proceed()
}
```

### findMethod / findField —— 反射查找

均为 **public static** 工具方法，沿继承链**逐级向父类递归查找**，找到后自动 `setAccessible(true)`：

```kotlin
fun findMethod(startClass: Class<*>, name: String, vararg parameterTypes: Class<*>): Method
fun findField(startClass: Class<*>, name: String): Field
```

- `findMethod` 支持带签名查找（变长 `parameterTypes`），跨 Android 版本方法参数变化时优先用签名定位。
- 整个继承链都找不到时抛 `NoSuchMethodException` / `NoSuchFieldException`，调用方需要 try-catch。
- 注意添加足够的过滤条件，避免命中不期望的方法/字段重载。

### remotePreferences —— 读取远程配置（只读）

远程配置来自宿主进程的 `xposed_module_config`，**只能读、不能写**。获取方式二选一：

```kotlin
// 方式一（推荐）：BaseHookModule.getRemotePreferences() 合成的 Kotlin 属性
val prefs = remotePreferences

// 方式二：XposedInterface 实例方法
val prefs = xposed.getRemotePreferences("xposed_module_config")
```

拿到后像 `SharedPreferences` 一样按类型读取，键名和默认值都引用 `PreferenceKeys` 常量：

```kotlin
val enabled = prefs.getBoolean(PreferenceKeys.EXAMPLE_HOOK_ENABLED.name, PreferenceKeys.EXAMPLE_HOOK_ENABLED.default)
val level = prefs.getInt(PreferenceKeys.EXAMPLE_HOOK_LEVEL.name, PreferenceKeys.EXAMPLE_HOOK_LEVEL.default)
```

## 5. 注册到 HookManager

在 `app/src/main/java/com/qimian233/ztool/hook/base/HookManager.java` 的 `registerAllModules()` 中注册：

```java
registerHookModule(new ExampleHook());
```

- 若类名与其它包冲突，使用全限定名（项目中有 `SplitScreenMandatory` 重名的先例）。
- 注册后 Hook 才会被 `HookInit` 分发执行。

## 6. 作用域声明（构建期）

- `getTargetPackages()` 必须是被 `scope.list` 覆盖的子集；新目标包记得加入 `scope.list`，否则 Hook 静默不执行。
- `module.prop`、`scope.list` 等构建期资源保持硬编码，不引用 `ScopeKeys`。
- 用户还需在 LSPosed Manager 中勾选作用域。

## 7. 接入完成后的检查清单

1. Hook 类是否已注册到 `HookManager.registerAllModules()`。
2. `getTargetPackages()` 是否引用 `ScopeKeys` 常量，目标包是否已加入构建期 `scope.list`。
3. 前端保存的 SharedPreferences 键是否与 Hook 侧读取完全一致（同一 `PreferenceKeys` 常量）。
4. 前端默认值是否与 `PreferenceKeys` 中注册的默认值一致。
5. 字符串、数值、列表格式是否与 Hook 侧解析方式一致。
6. 目标应用重启后配置是否生效（重启方式见 `ScopeKeys` 中注册的 `HowToRestart`）。

## 完整示例

以下示例综合展示上述全部要点（`PreferenceKeys` / `ScopeKeys` / `logger` / `hookWithId` / `findMethod` / `findField` / `remotePreferences`）：

```kotlin
package com.qimian233.ztool.hook.modules.example

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class ExampleHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.EXAMPLE_HOOK_ENABLED.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // 读取子功能配置（只读远程配置，键名/默认值来自 PreferenceKeys）
        val level = remotePreferences.getInt(
            PreferenceKeys.EXAMPLE_HOOK_LEVEL.name,
            PreferenceKeys.EXAMPLE_HOOK_LEVEL.default
        )

        logger.info("开始安装 ExampleHook, level=$level")
        try {
            val targetClass = classLoader.loadClass("com.example.TargetClass")

            // 带签名查找目标方法（自动 setAccessible，沿父类递归）
            val targetMethod = findMethod(
                targetClass, "targetMethod", String::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(targetMethod, "example_target") { chain ->
                logger.debug("拦截 targetMethod")
                val thisObject = chain.thisObject
                val targetField = findField(targetClass, "targetField")
                targetField.setInt(thisObject, level)
                chain.proceed()
            }
            logger.info("ExampleHook 安装完成")
        } catch (t: Throwable) {
            logger.error("ExampleHook 安装失败", t)
        }
    }
}
```
