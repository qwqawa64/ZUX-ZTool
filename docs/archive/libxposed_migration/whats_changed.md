# libxposed API 102 升级变更说明

这份文档适合已经适应本模块在 Legacy Xposed API 下开发的开发者快速了解变更。 如果您在之前完全没有参与过本模块的开发，请考虑暂缓阅读本文档。

如果您只参与具体 Hook 设计，则只需要重点阅读第四、第六节并简单查阅备注。然后查看本项目的具体 Hook 即可快速上手。

如果您不熟悉现代 libxposed API ，我们建议您先阅读 libxposed 文档: https://libxposed.github.io/api/ 或者查看其 GitHub 仓库。

> 如果您需要中文文档，可以在这个第三方网站获取：https://nspron.github.io/libXposed-101-Api-Chinese/

---

## 一、依赖与构建变更

### 1. Gradle 依赖切换（`gradle/libs.versions.toml` + `app/build.gradle.kts`）

| 旧 | 新 |
|---|---|
| `de.robv.android.xposed:api:82` (compileOnly) | `io.github.libxposed:api:102.0.0` (compileOnly) |
| （无） | `io.github.libxposed:service:102.0.0` (implementation，新增) |

- 版本号从 **82** 跃迁到 **102**。
- 新增 `libxposed-service` 运行时依赖，供 app 端通过 `XposedService` 与框架通信。

### 2. 删除旧 API JAR

- `app/libs/XposedBridgeAPI-82.jar` 被删除（二进制 0 字节）。

---

## 二、模块声明方式彻底改变

### 旧方式：`AndroidManifest.xml` 中的 `<meta-data>`

删除了所有 xposed 相关 meta-data 声明：
```xml
<meta-data android:name="xposedmodule" android:value="true" />
<meta-data android:name="xposeddescription" ... />
<meta-data android:name="xposedminversion" android:value="93" />
<meta-data android:name="xposedsharedprefs" android:value="true" />
<meta-data android:name="xposedscope" android:resource="@array/xposed_scope" />
```

### 新方式：独立的 META-INF 文件

新增三个标准的 libxposed 配置文件：

- **`META-INF/xposed/java_init.list`** — 声明入口类：
  ```
  com.qimian233.ztool.hook.HookInit
  ```
- **`META-INF/xposed/module.prop`** — 声明 API 版本和 scope 模式：
  ```properties
  minApiVersion=102
  targetApiVersion=102
  staticScope=false
  ```
- **`META-INF/xposed/scope.list`** — 显式列出 13 个目标包名（之前嵌入在 Manifest 的 `@array/xposed_scope` 资源中）。
- 备注：**`native_init.list`** 是原生 Hook 入口点，如果日后需要相关功能可以加上

---

## 三、Hook 入口类的 API 迁移（`HookInit.java`）

| 旧 API | 新 API |
|---|---|
| 实现 `IXposedHookLoadPackage` 接口 | 继承 `XposedModule` 抽象类 |
| 重写 `handleLoadPackage(XC_LoadPackage.LoadPackageParam)` | 重写 `onModuleLoaded(...)`、`onPackageLoaded(...)`、`onSystemServerStarting(...)` 生命周期回调 |
| 用 `XposedHelpers.findAndHookMethod` + `XC_MethodReplacement.returnConstant(true)` 来 hook 自身进程的 `isModuleActive()` | **不再 hook 自身进程**（libxposed 禁止 hook 自身进程）；改为通过 `XposedServiceHelper` 监听 binder 激活状态 |
| `de.robv.android.xposed.*` 旧 import 路径 | `io.github.libxposed.api.*` 新 import 路径 |

---

## 四、Hook 基类的 API 迁移（`BaseHookModule.java`）

### 参数类型变化（重要）

| 旧 | 新 |
|---|---|
| `handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam)` | `handleLoadPackage(XposedModuleInterface.PackageLoadedParam param)` |
| 新增 | `handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param)` |

> [!important]
> 这一更改还导致所有之前依赖 handleLoadPackage() 回调实现的系统框架 Hook 全部失效。如果您需要新建一个针对系统框架的 Hook 类。请确保您的 `handleLoadPackage(XposedModuleInterface.PackageLoadedParam param)` 只是空的占位实现，所有 Hook 逻辑都写在 `handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param)` 中。

### 配置读取方式变化

| 旧 | 新 |
|---|---|
| `ModuleConfig.isModuleEnabled(name)` — 通过 `XSharedPreferences` + `reload()` 读取 | `this.xposed.getRemotePreferences("xposed_module_config")` — 通过 libxposed 的 `XposedInterface` 直接获取 `SharedPreferences`，**不再需要手动 `reload()`** |
| 独立的 `ModuleConfig.java` 单例工具类 | 内联到 `BaseHookModule` 中，通过注入的 `xposed` 字段访问 |

### Hook 操作方式变化（重要）

| 旧 | 新 |
|---|---|
| `XposedHelpers.findAndHookMethod(cls, "methodName", XC_MethodReplacement...)` | `this.xposed.hook(method).intercept(chain -> result)` lambda 风格 |
| `XposedHelpers.findClass(name, classLoader)` | `classLoader.loadClass(name)` 标准反射 |
| `Log.i/e(TAG, msg)` | `this.xposed.log(level, TAG, msg)` — 通过 libxposed 的统一日志接口 |

> [!note]
> 对于 Hook 模块，仍旧推荐使用 `log(String message)` 和 `logError(String message, Throwable tr)` 保证日志被统一记录。

### 系统服务器 Hook 调度

- 新增 `safeHandleSystemServerStarting()` **调度方法**，支持派发 `system_server` 进程中的 hook 任务。

> [!note]
> 对于您编写的 Hook 模块，应当继承 BaseHookModule 并使用 `handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param)`

---

## 五、激活探测机制变更（`ModuleActivationProbe.kt`）

| 旧方案 | 新方案 |
|---|---|
| 在自身 app 进程 hook `isModuleActive()` 方法，始终返回 `true` | 使用 `XposedServiceHelper.registerListener()` 监听 `XposedService` 的 bind/die 事件 |
| 本质是"自己 hook 自己"（libxposed 不支持） | 通过 binder 回调得知框架是否已激活：`onServiceBind` → active=true, `onServiceDied` → active=false |

---

## 六、Hook 管理器重构（`HookManager.java`）

- `initialize()` 现在接受 `XposedInterface xposed` 参数，注入到每个注册的 `BaseHookModule` 中。
- 新增 `handleSystemServerStarting()` 方法，遍历所有模块分发系统服务器回调。
- 模块按目标包分组注释清晰化（SystemUI、Settings、PackageInstaller、Launcher 等）。
- 新增 `HookTestModule`（`hook_test` 模块）用于验证 libxposed 回调是否正常工作。

---

## 七、PreferenceHelper 简化（`PreferenceHelper.java`）

| 旧 | 新 |
|---|---|
| 基于 `XSharedPreferences`，每次读取前必须 `reload()` | 基于 `SharedPreferences`（通过 `XposedModule.getRemotePreferences()` 获取），**无需 reload()** |
| 单例模式，持有 `XSharedPreferences` 实例 | 工厂方法 `wrap(XposedModule)` / `wrap(SharedPreferences)` |
| 内部处理 `reload()` 失败、缓存失效等复杂逻辑 | 极简包装，所有复杂度由 libxposed 框架处理 |

---

## 八、App 端 SharedPreferences 读取改进（`ModulePreferencesUtils.java`）

- 优先通过 `ModuleActivationProbe.currentService.getRemotePreferences()` 读取远程配置（激活时）。
- 降级方案不再使用 `MODE_WORLD_READABLE`（已废弃），改用 `MODE_PRIVATE`。

---

## 九、`ModuleConfig.java` 删除

整个 `app/src/main/java/com/qimian233/ztool/config/ModuleConfig.java` 被删除。其功能分散到：
- `BaseHookModule.isEnabled()` / `isDetailedLoggingEnabledStatic()`
- `PreferenceHelper.wrap()`

---

## 十、所有 Hook 模块的批量适配（~50 个文件）

每个 Hook 模块的改动模式一致：

1. `import de.robv.android.xposed.*` → `import io.github.libxposed.api.*`
2. `XC_LoadPackage.LoadPackageParam lpparam` → `XposedModuleInterface.PackageLoadedParam param`
3. `lpparam.classLoader` → `param.getDefaultClassLoader()`
4. `XposedHelpers.findClass(name, classLoader)` → `classLoader.loadClass(name)`
5. `XposedHelpers.findAndHookMethod(cls, "method", args...)` → `xposed.hook(method).intercept(chain -> ...)`（lambda 风格，更简洁）
6. 添加显式无参构造函数（`public ClassName() {}`）
7. `"android"` 目标包名 → `"system"`（libxposed 语义：system 表示 system_server）

---

## 变更总结

这次升级的**核心变化**是：

1. **依赖**：从 `de.robv.android.xposed:api:82` 切换到 `io.github.libxposed:api:102` + `service`。
2. **模块声明**：从 Manifest `<meta-data>` 迁移到 `META-INF/xposed/` 标准文件。
3. **入口类**：从 `implements IXposedHookLoadPackage` 变为 `extends XposedModule`，获得生命周期回调体系。
4. **Hook 操作**：从 `XposedHelpers.findAndHookMethod(...)` 变为 `xposed.hook(method).intercept(chain -> ...)` lambda 风格。
5. **配置读取**：从 `XSharedPreferences.reload()` 变为 `getRemotePreferences()`，无需手动刷新。
6. **激活探测**：从"hook 自身进程"变为 `XposedServiceHelper` binder 监听。
7. **系统服务器**：新增 `handleSystemServerStarting` 回调支持。
8. **所有 ~50 个 Hook 模块**同步适配了参数类型、类加载方式、hook 方法和日志接口。

## 备注

### 为 Hook 开发者带来的变更

1. Hook 层面不再具备 XposedHelpers 静态方法组，需要使用反射获取类、字段和方法
> [!tip]
> 基类中提供了简单的 `findMethod(Class startClass, String methodName, Args argTypes)` 和 `findField(Class startClass, String fieldName)` 来模拟 XposedHelpers 向父类查找方法的功能。
> 这两个方法存在不明问题，可能匹配到您不想要的某些字段和方法。如果使用它们后您发现系统行为变得不对劲，请参考 `CustomControlCenterDate.java`  的方式为获取到的对象添加过滤器。
> 
> 另一个方法是使用 `DEXKit` 精确查找，这不是 libxposed 重构引入的新功能，这里不做说明。
2. 所有反射获得的对象默认不可直接访问。要读写或者调用您获取的对象，请使用语法 `myObject.setAccessible(true)` 。
3. 现在的 Hook 方法是 *OKHttp 风格的拦截链系统* 。

### 为模块综合开发者带来的变更

1. 现在可以动态增删作用域了
2. 通过传入的 libxposed 服务可以获取框架的版本和名称，这有助于识别可能无法让模块正常工作的框架，并主动拒绝启动
3. RemotePreference 让 SharedPreferences 管理变得和一般 Android 开发中的 SharedPreferences 一致，但是它会导致之前使用 New XSharedPreference API 存储的配置信息丢失
4. 可以通过服务请求热重载模块了（API 102 及以上），作用域重启将很快成为过去式