# libxposed 基本指南（ZTool 专用）

本项目使用 libxposed API 而非传统 Rovo89 Xposed, 两者语法上几乎完全不一致。本文档简单说明在 ZTool 项目中编写 
Hook 的基本方法。如果你不熟悉 Java 反射和 Rovo89 Xposed，则需要提前了解这方面的知识。

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
- `getModuleName(): String`, 用于返回模块唯一标识符。这个标识符需要在 `PrefereneKeys.kt` 提前注册。因此 Hook 
侧写法应该是这样，而不能硬编码字符串作为返回值：
  ```Kotlin
  override fun getModuleName(): String = Preferencekeys.KEY_VAL_NAME.name
  ```
  关于偏好键的注册方式，请参考 [Add_New_Preference_Key_zh-CN.md](../preference_key/Add_New_Preference_Key_zh-CN.md) 。
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
    $$"com.android.server.ZuiSecurityService$ZuiSecurityServiceBinder")
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
请使用 `BaseHookModule` 提供的帮助方法 `hookWithId()`:

```kotlin
fun hookWithId(
            target: Executable, // 方法实例，通过上一节中的帮助方法找到
            id: String,         // 提供一个 ID 用于原子化替换 Hook
            hooker: Hooker      // libxposed Chain SAM, 详见下一节
    ): XposedInterface.HookHandle {
        return if (xposed.apiVersion >= 102) {
          xposed.hook(target).setId(id).intercept(hooker)
        } else {
          xposed.hook(target).intercept(hooker)
        }
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
- 以下 ID 声明不合法：
```kotlin
// In com.android.settings scope
hookWithId(method1, "id1", SAM)
hookWithId(method1, "id1", SAM) // 和已有 ID 重复，框架将不知道如何原子替换 Hook
```

## libxposed Chain SAM
Chain 函数式接口用于编写 Hook 流程，这是 libxposed 和 Rovo89 Xposed 最大的区别。本项目使用的 hookWithId 又导致

### 替换 beforeHookedMethod, afterHookedMethod 以及 replaceHookedMethod
我们使用的 Chain SAM 有些许区别。下面的示例直接写 Chain SAM, 也就是你需要填入 hookWithId() 的最后一个参数：
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

## 日志系统
参见 [migrate_and_use_new_logging_system.md](../new_log_system/migrate_and_use_new_logging_system.md) 了解日志系统用法和等级划分。

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
项目支持 DexKit, 但是请使用离线索引功能，不要在 Hook 内引入 Native 调用破坏热重载能力。具体参见 [README.md](../dex_index/README.md)