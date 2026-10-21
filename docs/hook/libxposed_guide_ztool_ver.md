# libxposed 基本指南（ZTool 专用）

本项目使用 libxposed API 而非传统 Rovo89 Xposed, 两者语法上几乎完全不一致。本文档简单说明在 ZTool 项目中编写 
Hook 的基本方法。如果你不熟悉 Java 反射和 Rovo89 Xposed，则需要提前了解这方面的知识。

文档同时面向人类开发者和 Agent 。

## 可能需利用的导入和限定名
在导入中写全限定名，此后不要重复全限定名，否则 Android Studio 将提示很多无意义的警告 `remove redundant qualifier name ...`。

导入：
```kotlin
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
```

这些限定名可以在后续代码中使用：
```kotlin
XposedModuleInterface.PackageLoadedParam
XposedModuleInterface.SystemServerStartingParam
XposedInterface.HookHandle
XposedInterface.Chain
```

## Hook 类
### 选择一个继承类
根据你的 Hook 目标，Hook 类需要从下面两种类中选一种继承：
- `AppHookModule` 用于非系统框架应用的 Hook, 例如 `系统界面 (com.android.systemui)`
- `SystemHookModule` 用于系统框架的 Hook , 可作用在 `android` 和 `system` 两个目标上

禁止直接继承 `BaseHookModule`, 这会对模块用途和 IDE 自动补全造成不便。

```Kotlin
// 继承 AppHookModule
class YourAppHooker: AppHookModule() {
    // implement methods here...
}
```

```kotlin
// 继承 SystemHookModule
class YourSystemServerHooker: SystemHookModule() {
    // implement methods here...
}
```

### 实现必要的抽象方法
Hook 类要实现三个方法：
- `getModuleName(): String`, 用于返回模块唯一标识符。这个标识符需要在 [PreferenceKeys.kt](../../app/src/main/java/com/qimian233/ztool/data/keys/PreferenceKeys.kt) 提前注册。因此 Hook
侧写法应该是这样，而不能硬编码字符串作为返回值：
  ```Kotlin
  override fun getModuleName(): String = PreferenceKeys.KEY_VAL_NAME.name
  ```
  关于偏好键的注册方式，请参考 [add_new_preference_key_zh-cn.md](../preference_key/add_new_preference_key_zh-cn.md) 。
  > [!tip]
  > 如果你需要临时测试一个 Hook 在本项目基础设施下的工作效果，可以让本方法返回 `PreferenceKeys.TEST_HOOK.name`，针对此返回值，基类会自动放行 Hook，无需提前修改 UI 层。
  >
  > 注意所有正式 Hook 严禁使用这个键名。
- `getTargetPackages(): Array<String>`, 用于返回模块作用的宿主包名，可同时作用于多个宿主。这个标识符需要在 
`ScopeKeys.kt` 提前注册。同样，不能硬编码字符串作为返回值：
  ```kotlin
  override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SCOPE1.name, ScopeKeys.SCOPE2.name, OtherScopes)
  ```
  > [!note] 处理多个宿主
  > 如果你需要 Hook 的宿主同时包括系统框架和非系统框架两种类别，则不可以在一个类中同时实现对两类宿主的 Hook 。正确的做法应该是：
  > - 在 com.qimian233.ztool.hook.modules.systemframework 下创建继承 SystemHookModule 的系统框架 Hook 类
  > - 并在 com.qimian233.ztool.hook.modules.<app-category> 下创建继承 AppHookModule 的 APP Hook 类
  > 
  > 若你需要 Hook 的宿主只有系统框架，则可以只在 com.qimian233.ztool.hook.modules.systemframework 创建一个或者若干个 Hook 类。
  > 
  > 若你需要 Hook 的宿主只有 APP, 则可以在对应 APP 的模块子包（例如 com.qimian233.ztool.hook.modules.mobiledesktop）中分别创建 Hook 类。
  > 
  > 虽然 getTargetPackages 支持返回多个目标宿主，但是这么做需要在 handleLoadPackage 中额外添加辨别不同包名的判断，且会模糊分包约定。因此现在建议所有 getTargetPackages 
  > 只返回一个目标作用域名称，服务于同一个功能的 Hook 拆分成若干个子类，放置于各自作用域的分包中，使用相同的偏好键统一管理开关。
- `handleLoadPackage(param: PackageLoadedParam)` (AppHookModule) 或 `handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam)` 
  (SystemHookModule) 。这是对 Rovo89 Xposed 中的 handleLoadPackage 的拆分，用于区分系统框架和非系统框架 Hook 。

### 类加载器（ClassLoader）
对于 AppHookModule, 应当使用 `param.defaultClassLoader` 获取类加载器。
对于 SystemHookModule, 使用 `param.classLoader` 。

### 注册你的 Hook
未被注册的 Hook 类不会被 ZTool 自动调度派发。你需要在
[HookManager.kt](../../app/src/main/java/com/qimian233/ztool/hook/base/HookManager.kt)
中注册你的 Hook。注册按目标作用域分区：每个作用域对应一个私有 `registerXxxModules()`
方法，`registerAllModules(xposed: XposedInterface)` 只负责依次调用这些方法并注入
`XposedInterface`。请把注册语句写进作用域匹配的那个方法：

```kotlin
registerHookModule(ClassName())
```
例如 `registerHookModule(DisableAllVirusScans())` 应写进 `registerSafeCenterModules()` 。如果你使用 Android Studio 或者 IDEA ，则应当会自动完成对应导入。

| 注册方法 | 作用域 |
|---|---|
| `registerSystemServerModules()` | `ScopeKeys.SYSTEM_SERVER`（`system`，含 PackageManager 核心补丁） |
| `registerAndroidFrameworkModules()` | `ScopeKeys.ANDROID_SYSTEM`（`android`） |
| `registerAllProcessModules()` | `ScopeKeys.UNRESTRICTED`（`all`，仅限 `AiInputExpand`） |
| `registerZuiWidgetModules()` | 多应用 ZUI 控件（Settings / SystemUI / Launcher / SafeCenter / PackageInstaller） |
| `registerSystemUIModules()` | `ScopeKeys.SYSTEM_UI`（含自由窗口与任务栏控件） |
| `registerSettingsModules()` | `ScopeKeys.SETTINGS` |
| `registerPermissionControllerModules()` | `ScopeKeys.PERMISSION_CONTROLLER`（兼及 Settings / ZUI Safe Center） |
| `registerPackageInstallerModules()` | `ScopeKeys.PACKAGE_INSTALLER` |
| `registerLauncherModules()` | `ScopeKeys.LAUNCHER` |
| `registerDesktopLauncherModules()` | `ScopeKeys.DESKTOP_LAUNCHER` |
| `registerGameServiceModules()` | `ScopeKeys.GAME_SERVICE` |
| `registerOtaModules()` | `ScopeKeys.OTA` |
| `registerWallpaperSettingsModules()` | `ScopeKeys.WALLPAPER_SETTINGS` |
| `registerDocumentsUIModules()` | `ScopeKeys.DOCUMENTS_UI` |
| `registerFreeformSidebarModules()` | `ScopeKeys.FREEFORM_SIDEBAR` |
| `registerSafeCenterModules()` | `ScopeKeys.LENOVO_SAFE_CENTER` / `ScopeKeys.ZUI_SAFE_CENTER` |
| `registerMobileDesktopModules()` | `ScopeKeys.MOBILE_DESKTOP` |
| `registerTbEngineModules()` | `ScopeKeys.TB_ENGINE` |
| `registerLenovoDeviceServiceModules()` | `ScopeKeys.LENOVO_DEVICE_SERVICE` |
| `registerTabPushoutModules()` | `ScopeKeys.TAB_PUSHOUT` |
| `registerZuiPerformanceModules()` | `ScopeKeys.ZUI_PERFORMANCE` |
| `registerSogouImeModules()` | `ScopeKeys.SOGOU_OEM_IME` |

> [!tip]
> 不要在 `registerAllModules()` 中直接写 `registerHookModule(...)`，也不要把 Hook 塞进与目标作用域不符的方法。目标作用域还没有对应方法时，新增 `registerXxxModules()` 并在 `registerAllModules()` 中调用。一个 Hook 覆盖多个作用域时，放进其主要作用域的方法，并在该方法 KDoc 中注明其余作用域。


## 查找实例和查找用基础设施
libxposed 需要你手动反射查找方法。为了提供接近 Rovo89 Xposed 的查找体验，`HookReflectionHelper` 类提供了按照继承链查找实例的辅助方法：

- `findField(startClass: Class<*>?, name: String): Field` 从给定的开始类沿着继承链回溯查找字段，找到后设置 
accessible 并返回这个字段。若找不到，将抛出 `NoSuchFieldException` 。
- `findMethod(startClass: Class<*>?, name: String, vararg parameterTypes: Class<*>?): Method` 从给定的
开始类沿着继承链回溯查找方法，找到后返回这个方法。若找不到，将抛出 `NoSuchMethodException` 。

以上两个方法在 `BaseHookModule` 中已经完成封装，在两种 Hook 类中均可以直接使用。这里提供用例：

```kotlin
// 查找方法，参见 AllowRelativeAppLaunch.kt 了解完整实现
val classLoader: ClassLoader = param.classLoader
val securityBinderClass: Class<*> = classLoader.loadClass(
    $$"com.android.server.ZuiSecurityService$ZuiSecurityServiceBinder") // Kotlin 的特性，双美元符号可让后续字符串内的美元符无需转义
val getStatusMethod: Method = findMethod(securityBinderClass, "getRelativeAppStatus",
    String::class.java, String::class.java)
```

```kotlin
// 查找字段，参见 CustomGridSize.kt 了解完整实现
val numColsField = findField(gridOptionClass, "numColumns")
numColsField.set(thisObject, CUSTOM_COLUMNS)
val numRowsField = findField(gridOptionClass, "numRows")
numRowsField.set(thisObject, CUSTOM_ROWS)
```

## 挂钩（Hook）已被找到的方法实例
请使用 `BaseHookModule` 提供的帮助方法 `hookWithId()`，附录有基类提供的三种重载:

```kotlin
// 五参数版完整签名（可按需使用其他两种重载以省略 priority 和 exceptionMode）
protected open fun hookWithId(
  target: Executable,
  id: String,
  priority: Int,
  exceptionMode: ExceptionMode,
  hooker: Hooker
): XposedInterface.HookHandle {
    return if (xposed.apiVersion >= 102) {
      xposed.hook(target).setId(id).setPriority(priority).setExceptionMode(exceptionMode).intercept(hooker)
    } else {
      xposed.hook(target).setPriority(priority).setExceptionMode(exceptionMode).intercept(hooker)
    }
}

// 更常用的三参数版：
protected open fun hookWithId(
  target: Executable,
  id: String,
  hooker: Hooker
): XposedInterface.HookHandle {
  return hookWithId(
    target,
    id,
    XposedInterface.PRIORITY_DEFAULT,
    ExceptionMode.DEFAULT,
    hooker
  )
}
```

本方法用于兼容 libxposed API 101 (项目最低要求) 和 libxposed 102+ (项目目标 API 为 102，用于支持热重载模块)。
其中，ID 在每个作用域中唯一。因此：
- 以下 ID 声明是合法的：
```kotlin
// In com.android.settings scope
hookWithId(method1, "id1", SAM)
hookWithId(method2, "id2", SAM)

// In com.android.systemui scope
hookWithId(method1, "id1", SAM)
```
- 以下写法需要注意：在同一个作用域、同一个方法上使用重复 ID 时，新 Hook 会**原子替换**旧 Hook
（旧 HookHandle 立即失效，正在执行的调用不受影响）。这是热重载替换机制的预期行为；但如果这是
无意的重复注册，第二次调用会悄悄覆盖第一次的 Hook，属于编码错误：
```kotlin
// In com.android.settings scope
hookWithId(method1, "id1", SAM)
hookWithId(method1, "id1", SAM) // 原子替换上一个 "id1" Hook，通常是无意的重复注册
```

### priority：多 Hook 竞争的处置机制

当多个 Hook（无论是本模块注册多次，还是其它 Xposed 模块）注册到**同一个方法**时，框架会按
priority 从高到低把它们串成一条拦截链：priority 最高的 Hook 最先执行，它调用 `chain.proceed()`
后轮到 priority 次高的 Hook，最后执行原方法；每个 Hook 拿到的返回值都是 `proceed()` 的结果，
因此前序 Hook 可以修改参数、修改返回值或完全短路原方法。

- 取值范围：`Int.MIN_VALUE`（`PRIORITY_LOWEST`，链末尾）到 `Int.MAX_VALUE`（`PRIORITY_HIGHEST`，
  链开头）；默认值 `PRIORITY_DEFAULT` 为 50。
- 当你的 Hook 需要确保先于/后于同一方法上的其它 Hook 执行时（例如先于某个会短路方法的 Hook），
  显式设置 priority 来避免竞争，而不是依赖默认值。
- Hook 链是快照式的：替换或新增 Hook 不影响正在执行中的调用。

### exceptionMode：异常处理模式

`exceptionMode` 决定 LSPosed 框架如何处理 Hooker 抛出的异常：

- `DEFAULT` — 遵循 module.prop 中配置的全局异常模式。若未配置则默认为 `PROTECTIVE`。
- `PROTECTIVE` — 捕获并记录 Hooker 抛出的任何异常，然后调用继续（如同没有 Hook 一样）。
  推荐用于大多数情况，可防止因 Hook 错误导致的崩溃。如果异常在 `proceed()` 之前抛出，
  框架会跳过当前 Hook 继续链；如果在 `proceed()` 之后抛出，框架将返回已继续的值/异常。
  `proceed()` 抛出的异常始终会传播。
- `PASSTHROUGH` — Hooker 抛出的任何异常都将正常传播给调用者。推荐用于调试，
  帮助发现和修复 Hook 中的错误。

默认值 `ExceptionMode.DEFAULT` 对绝大多数 Hook 已经足够；仅在调试某个反复出错的 Hook 时
临时切换到 `PASSTHROUGH`。

## libxposed Chain SAM
Chain 函数式接口用于编写 Hook 流程，这是 libxposed 和 Rovo89 Xposed 最大的区别。本项目使用的 hookWithId 又导致我们使用的 Chain SAM 有些许区别。

### 替换 beforeHookedMethod, afterHookedMethod 以及 replaceHookedMethod
下面的示例直接写 Chain SAM, 也就是你需要填入 hookWithId() 的最后一个参数，当然也可以用 DSL 风格写法，将最后一个参数放在圆括号外面：
```kotlin
// replace beforeHookedMethod:
{ chain -> 
    // do something before method executes
    chain.proceed() // let the original method execute
}
```

```kotlin
// replace afterHookedMethod
{ chain ->
    chain.proceed() // let the original method execute first
    // then do something
}
```

显然现在组合使用 before 和 after 两种 Hook 时机的语法变简单了：
```kotlin
{ chain ->
    // do something before method executes
    chain.proceed() // let the original method executes
    // do something after the method executes
}
```

如果需要修改返回值：
```kotlin
{
    yourRetval
}
```

如果需要完全替代原方法：
```kotlin
{ chain ->
    // do something without calling chain.proceed()
}
```

如果遇到异常或者模块关闭等状况，需要中途退出 Chain:
```kotlin
{ chain ->
  val result = chain.proceed() // 建议在拦截链开始时就设置好 chain.proceed()
  if (!isEnabled()) {
    return@hookWithId result // 直接使用原始逻辑
  }
}
```

### chain 的方法和属性
- chain.thisObject 返回方法实例指针，对于静态方法而言它为 null
- chain.args 返回完整参数列表，可以使用下标取某个参数，例如 `chain.args[0]`
- chain.proceed() 继续执行方法。可以传入数组对象改变方法参数：
  ```kotlin
  // 参见 HideOtaNotifications.kt 了解完整实现
  hookWithId(targetMethod, "target_2") {  chain ->
      try {
          val argList = chain.args.toMutableList()
          if (argList[1] != 0) {
              argList[1] = 0
              logger.debug("Modified red dot count to 0!")
          }
          chain.proceed(argList.toTypedArray())
      } catch (th: Throwable) {
          logger.error("Failed to set red dot count to 0!", th)
          chain.proceed()
      }
  }
  ```
- chain.proceedWith() 替换原方法指针继续执行方法，本项目中暂时没有用例，这里请看文档：
> Object proceedWith(@NonNull Object thisObject) throws Throwable
> 替换 this 指针继续执行链（静态方法不应调用此方法）。
> 
> 参数：thisObject — 用于调用的新 this 指针
> 返回：下一个拦截器或原始可执行文件的结果（void 返回 null）
> 抛出：Throwable — 如果任何拦截器或原始可执行文件抛出异常
> Object proceedWith(@NonNull Object thisObject, @NonNull Object[] args) throws Throwable
> 替换 this 指针和参数继续执行链（静态方法不应调用此方法）。
> 
> 参数：thisObject — 新 this 指针；args — 新参数
> 返回：下一个拦截器或原始可执行文件的结果（void 返回 null）
> 抛出：Throwable — 如果任何拦截器或原始可执行文件抛出异常`

### 关于 Chain SAM 的约定
1. 尽可能不要将 Chain 内的拦截逻辑写进外部函数，尤其是逻辑小于 100 行的简单业务。例如下面这样是不推荐的：
  ```kotlin
  override fun handleLoadPackage(param: PackageLoadedParam) {
    // locate the method instance as myMethod...
    hookWithId(myMethod, "hook1") { chain ->
      myImplementation()
    }
  }
  
  private fun myImplementation() {
    return null // 逻辑极度简单，不应该拆成独立方法
  }
  ```
2. 在 hoodWithId 的 Chain SAM 中，Chain 参数不需要签名
3. 如果你需要将 Chain 拆进独立方法，显然需要提供方法参数签名：`chain: XposedInterface.Chain`

## 日志系统
所有的 Hook 模块都具备继承自 `BaseHookModule` 的 logger 实例。logger 提供 Log4j 风格的六种日志等级（TRACE, DEBUG, INFO, WARN, ERROR, FATAL）对应的方法。以下是等级用途：
- `trace`: **仅限诊断和探针用途**，如记录屏幕点击位置、触控事件、计数器、大量包名、调用堆栈（非错误堆栈）的日志条目。
- `debug`: 用于记录拦截器链 (interceptor chain) 内部和 Hook 安装过程的事件。例如通过反射 invoke 构造器来拿到对应类的实例。
- `info`: 标记安装流程和单个拦截器逻辑的开始和结束。
- `warn`: 标记非阻断性问题，例如降级、回落默认值、切换替代方案。
- `error`: 支持记录堆栈。记录安装流程和拦截器内部的阻断性错误。例如空指针异常，找不到关键类、方法和字段
- `fatal`: 支持记录堆栈。保留作日后用途。

用户可以在 APP 中*实时*调整日志等级。日志等级低于用户指定值的日志将**不会被记录**。例如用户设定日志等级为 WARN, 则 INFO 及更低等级日志不会被记录。

方法签名：
```kotlin
fun trace(msg: String)
fun debug(msg: String)
fun info(msg: String)
fun warn(msg: String)
fun error(msg: String, t: Throwable? = null)
fun fatal(msg: String, t: Throwable? = null)
```

## 跨进程共享信息

先获取 remotePreferences, 然后按照正常读 SharedPreferences 的方法获取数据。不支持写 Preferences。

```kotlin
// 参见 CustomGridSize.kt 了解完整实现
val prefs = remotePreferences
CUSTOM_ROWS =
    prefs.getInt(PreferenceKeys.CUSTOM_LAUNCHER_ROW.name, 4)
CUSTOM_COLUMNS =
    prefs.getInt(PreferenceKeys.CUSTOM_LAUNCHER_COLUMN.name, 6)
```

## 对抗混淆
项目支持 DexKit, 但是请使用离线索引功能，不要在 Hook 内引入 Native 调用破坏热重载能力。具体参见 [readme.md](../dex_index/readme.md)

## 签名参考
### 基类
[BaseHookModule.kt](../../app/src/main/java/com/qimian233/ztool/hook/base/BaseHookModule.kt) 包含本项目所有 Hook 模块的开关控制和派发逻辑，Hook 模块不得直接继承。公开的方法和属性：

```kotlin
protected lateinit var xposed: XposedInterface // XposedInterface 实例
protected var logger: ModuleLog = ModuleLog("", null) // 项目的 logger 实例。支持 Log4j 风格日志登记，ERROR 和 FATAL 级别支持打印堆栈信息
abstract fun getModuleName(): String // 获取模块名称
abstract fun getTargetPackages(): Array<out String> // 获取作用域包名
open fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) // 非系统框架应用的注入回调，默认 NOP
open fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) // 注入系统框架和系统服务器时使用，默认 NOP
open val remotePreferences: SharedPreferences
    get() = xposed.getRemotePreferences(PREFS_NAME) // 获取只读的 RemotePreferences

// 方法查找辅助函数，顺继承链向上回溯，找到返回方法对象
@Throws(NoSuchMethodException::class)
protected open fun findMethod(
  startClass: Class<*>?,
  name: String,
  vararg parameterTypes: Class<*>?
): Method

// 字段查找辅助函数，顺继承链向上回溯，找到即设置 accessible = true 并返回字段对象
@Throws(NoSuchFieldException::class)
protected open fun findField(startClass: Class<*>?, name: String): Field

// hookWithId 的三种重载
protected open fun hookWithId(
  target: Executable,           // 方法对象
  id: String,                   // 标识 Hook 唯一身份的字符串，持有相同 id 的 Hook ，后注册的将替换先注册的
  priority: Int,                // 项目默认 XposedInterface.PRIORITY_DEFAULT = 50, 仅在多个 Hook 面向同一方法操作时需要分别设置
  exceptionMode: ExceptionMode, // 若不写则回落 ExceptionMode.DEFAULT，根据项目 module.prop 配置而定。若 module.prop 也未指定，则框架将按照 PROTECTIVE 模式进行处理
  hooker: Hooker                // 见上文 chain 语法
): XposedInterface.HookHandle

protected open fun hookWithId(
  target: Executable,
  id: String,
  priority: Int,
  hooker: Hooker
): XposedInterface.HookHandle

protected open fun hookWithId(
  target: Executable,
  id: String,
  hooker: Hooker
): XposedInterface.HookHandle

// 以下部分谨慎覆写
open fun supportsPackage(packageName: String?): Boolean // 判断是否应当注入本包，如果 Hook 面向的包不明确，例如针对未知个应用的 ZUI SDK 前端控件进行注入，则应当在模块内覆写为默认返回 true
// 注：项目还有一种已有模式可让所有包被注入，但是仅限 AiInputExpand 使用，其余模块禁止：让 getTargetPackages() 返回 arrayOf(ScopeKeys.UNRESTRICTED.packageName)

// 以下部分不需要自行实现
open fun isEnabled(): Boolean // Hook 模块可调用。判断当前 Hook 是否被启用，结果随着用户偏好开关变化实时变化
open fun setXposedInterface(xposed: XposedInterface) // 只能由 HookManager 调用，用于注入 XposedInterface 实例
fun safeHandleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) // 只能由 HookManager 调用，派发执行 handleLoadPackage(param), 自带异常处理
fun safeHandleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) // 只能由 HookManager 调用，派发执行 handleSystemServerStarting(param)，自带异常处理
```
