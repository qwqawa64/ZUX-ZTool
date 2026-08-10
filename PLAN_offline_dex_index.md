# PLAN: Offline DexKit Index（离线 DexKit 索引，解除热重载限制）

> AGENT 实施计划。状态：**待实施**（设计已与用户确认）。
> 用户已拍板：存储 = libxposed **Remote Files**；索引器 = **每作用域一个**；范围 = 框架 + 全部 7 个模块。

## 1. 背景与目标

现状：7 个 Hook 模块在 `handleLoadPackage`（目标进程内）同步执行 DexKit 查询
（`DexKitBridge.create` 加载 native 库 + mmap APK），导致加载这些 Hook 的进程
无法安全热重载（热重载 replay 时对同一 APK 重复 create bridge，native 状态冲突）。

目标：把 DexKit 查询从"hook 加载路径"移到"模块 app 侧预计算"：
安装/更新/目标 apk 变更后，app 进程用 DexKit 扫描目标 apk，结果写入
**模块私有目录的配置文件**；hook 加载时通过 **libxposed Remote Files**
（`openRemoteFile`）只读提取，加载路径零 DexKit、零 native。

## 2. 关键机制（已核实）

- libxposed API 102 `XposedInterface`：`listRemoteFiles()` / `openRemoteFile(name)`。
- LSPosed 服务端实现（`LSPInjectedModuleService` + `ConfigFileManager`）：
  `openRemoteFile` 解析为 `/data/user/<userId>/<pkg>/files/<name>` ——
  **即模块 app 自己的 filesDir**，daemon 以特权代读，**无需 chmod、保持私有**。
- 文件名限制：不含 `/`、`\`、`.`、`..`（`com.zui.launcher.json` 合法）。
- 能力标志：`PROP_CAP_REMOTE`（`1L << 1`）；embedded/老框架抛
  `UnsupportedOperationException` / `FileNotFoundException` / `AbstractMethodError`。
- 写入端即普通 `context.filesDir` 文件写入，实时可读（无服务端缓存）。

## 3. 架构

```
阶段 A（模块 app 进程）：
  触发（Receiver / 启动指纹检查 / 设置页手动刷新）
    → DexIndexManager
        ├ 对每个作用域包：sourceDir + splitSourceDirs → DexKitBridge.create(...)
        ├ 跑该作用域唯一的 Indexer（原样迁移的查询代码）
        └ 原子写 files/dex_index/<scopePkg>.json（含 apk 指纹）

阶段 B（目标进程，hook 加载时）：
  handleLoadPackage
    → DexIndexStore.lookup(xposed, scopePkg)          // openRemoteFile + gson
        ├ 能力检测 PROP_CAP_REMOTE + try-catch
        ├ 进程内缓存（每进程只读一次）
        └ 任何失败 → null → 调用方回退硬编码（现状语义不变）
```

## 4. 文件清单

### 新增（app 侧，纯 Kotlin，**禁止依赖 libxposed**）

| 文件 | 职责 |
|---|---|
| `dexindex/DexIndexer.kt` | 接口：`scopePackage` + `index(bridge, context): JsonObject` |
| `dexindex/LauncherDexIndexer.kt` | 作用域 `ScopeKeys.LAUNCHER`：CleanGlobalSearch + DisableForceStop + ZuiLauncherHotseatHook 的查询 |
| `dexindex/SystemUiDexIndexer.kt` | 作用域 `ScopeKeys.SYSTEM_UI`：NoChargeAnimation + SystemUINetworkSpeeddoublelayerHook 的查询 |
| `dexindex/MobileDesktopDexIndexer.kt` | 作用域 `ScopeKeys.MOBILE_DESKTOP`：BypassShareWarningHook + DisableNearbyShareAutoOffHook 的查询 |
| `dexindex/DexIndexRegistry.kt` | `val indexers: List<DexIndexer>`（作用域 → indexer 唯一映射） |
| `dexindex/DexIndexManager.kt` | 执行入口：取 apk 路径(split)、建/关 bridge、跑 indexer、原子写、指纹、异常隔离、chmod 不需要 |
| `dexindex/DexIndexConstants.kt` | 目录名 `dex_index`、schemaVersion、文件名规则、JSON key 常量 |

### 新增（hook 侧，唯一允许依赖 libxposed）

| 文件 | 职责 |
|---|---|
| `hook/base/DexIndexStore.kt` | `lookup(xposed, scopePkg): JsonObject?` + `string(...)`；能力检测、openRemoteFile、gson 解析、进程内缓存 |

### 新增（触发与 UI）

| 文件 | 职责 |
|---|---|
| `dexindex/DexIndexReceiver.kt` | `ACTION_MY_PACKAGE_REPLACED` + `ACTION_PACKAGE_ADDED`（包名==本模块）触发扫描 |
| 设置页（`SettingsRoute` + 对应 ViewModel/Repository） | 手动"刷新索引"入口 + 显示上次索引时间/状态 |

### 修改

| 文件 | 改动 |
|---|---|
| `AndroidManifest.xml` | 注册 `DexIndexReceiver` |
| `ZToolApplication.kt` | 启动时比对 apk 指纹，变化则后台重扫（覆盖 OTA 更新目标 app） |
| 7 个 Hook 文件 | `handleLoadPackage` 中删除 DexKitHelper/bridge 查询，改 `DexIndexStore` 读取 + 保留硬编码 fallback |
| `docs_archive/dex_index/` | 新增接入指南；更新 `AGENTS.md` Hook 架构章节 |

### 删除

| 文件 | 说明 |
|---|---|
| `hook/base/DexKitHelper.kt` | 迁移完成后 hook 侧无引用；dexkit 依赖保留（app 侧 Indexer 仍用） |

## 5. JSON Schema（每作用域一文件）

`files/dex_index/com.zui.launcher.json`

```json
{
  "schemaVersion": 1,
  "generatedAt": 1730000000000,
  "apk": { "path": "/system/priv-app/.../base.apk", "lastUpdateTime": 123, "signatureHash": "sha256-hex" },
  "modules": {
    "CleanGlobalSearch": { "hotwordInitMethod": "K0", "hotwordDataMethod": "E0" },
    "DisableForceStop":   { "forceStopMethod": "c" },
    "ZuiLauncherHotseatHook": { "loaderCursorBMethod": "b" }
  }
}
```

- `modules` 的 key 用 `getModuleName()`（与 PreferenceKeys 一致）。
- Indexer 内**原样保留**现有查询代码（含 usingFields、字段类型回退、方法遍历等逻辑），只把"取第一个命中"改为"序列化结果"。
- 指纹用于失效检测：`lastUpdateTime` + `PackageInfo.signatures[0]` 的 SHA-256（minSdk 27 用 GET_SIGNATURES）。
- 原子写：先写 `.tmp-<pkg>.json` 再 rename。

## 6. 各 Indexer 输出键（迁移映射）

| 作用域 | 模块 | 输出键 | 来源方法 |
|---|---|---|---|
| launcher | CleanGlobalSearch | `hotwordInitMethod` / `hotwordDataMethod` | `discoverInitMethods` / `discoverE0Method` |
| launcher | DisableForceStop | `forceStopMethod` | `findCMethodName` |
| launcher | ZuiLauncherHotseatHook | `loaderCursorBMethod` | `findBMethodName` |
| systemui | NoChargeAnimation | `handlerFieldName` | findClass+FieldsMatcher（含回退逻辑） |
| systemui | SystemUINetworkSpeeddoublelayerHook | `handlerInnerClass` | `findHandlerInnerClass` 的 DexKit 段 |
| mobiledesktop | BypassShareWarningHook | `managerClass` / `managerFactoryMethod` / `managerSetMethod` / `dialogMethod` / `tileRefreshMethod` | 各 findClass/findMethod |
| mobiledesktop | DisableNearbyShareAutoOffHook | `targetClass` / `targetMethod` | findClass(methods) |

## 7. Hook 侧改造模式

```kotlin
// 改造前
val bridge = DexKitHelper.getBridgeForApp(param.applicationInfo)
val name = discoverInitMethods(bridge).firstOrNull() ?: "K0"

// 改造后
val idx = DexIndexStore.lookup(xposed, ScopeKeys.LAUNCHER.packageName)
val name = idx?.getAsJsonObject(moduleName)?.get("hotwordInitMethod")?.asString ?: "K0"
```

- 读取失败（老框架/未索引/文件缺失）一律静默走硬编码 fallback，行为不劣化。
- **禁止**在 hookWithId lambda 里做 IO——配置在 `handleLoadPackage` 阶段读好。

## 8. 实施步骤

1. 建 `dexindex` 框架（常量/接口/注册表/Manager/Store）+ Manifest 注册 Receiver
2. `LauncherDexIndexer.kt`（迁移 3 模块查询）
3. `SystemUiDexIndexer.kt`（迁移 2 模块查询）
4. `MobileDesktopDexIndexer.kt`（迁移 2 模块查询）
5. 触发：Receiver + `ZToolApplication` 指纹检查 + 设置页手动刷新
6. 改造 7 个 Hook，删除 `DexKitHelper.kt`
7. 文档（`docs_archive/dex_index/` + AGENTS.md）
8. 验证：`.\gradlew.bat assembleDebug`；`git diff --check`；真机清单（见下）

## 9. 验证计划

- 编译：`.\gradlew.bat assembleDebug`
- 静态：`git diff --check`；确认 7 个 Hook 无 DexKit 导入残留
- 真机（需用户）：
  1. 首次安装后打开 ZTool → `files/dex_index/*.json` 生成
  2. 索引生成前 hook 走 fallback（不崩）
  3. 索引生成后重启目标进程 → hook 生效（日志比对查询结果）
  4. 目标 app OTA（改 lastUpdateTime/签名）→ 重扫触发
  5. 热重载：加载 DexKit 相关 Hook 的进程可正常热重载（replay 无 DexKit 调用）
  6. 老框架/embedded（无 remote files）→ 静默 fallback

## 10. 风险与回退

- 首次安装后目标进程先于索引启动 → fallback（现状兜底，不劣化）
- `openRemoteFile` 老框架 `AbstractMethodError` → try-catch + `getApiVersion()` 守卫
- 索引结果随目标 app 更新过期 → 指纹检测重扫（索引完成后需重启目标进程生效）
- 单个 indexer 失败 → 异常隔离，其余照常；文件缺字段 → hook 侧 fallback
