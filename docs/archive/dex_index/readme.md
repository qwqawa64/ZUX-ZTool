# 离线 DexKit 索引（Offline DexKit Index）

> 解决 DexKit 查询破坏 LSPosed 热重载的问题：把 DexKit 查找从"hook 加载路径"
> 迁移到"模块 app 侧预计算"，hook 加载时只读结果文件。

## 背景

在 `handleLoadPackage`（目标进程内）同步执行 `DexKitBridge.create()` 会加载
native 库并 mmap APK；热重载 replay 时对同一 APK 重复 create，native 状态冲突，
导致加载这些 Hook 的进程无法安全热重载。本机制把查找移出 hook 加载路径。

## 架构

```
阶段 A（模块 app 进程）：
  触发（DexIndexReceiver / ZToolApplication 启动指纹检查 / 设置页手动刷新）
    → DexIndexManager
        ├ 对每个作用域包：sourceDir + splitSourceDirs → DexKitBridge.create(...)
        ├ 跑该作用域唯一的 Indexer（原样迁移的查询代码）
        └ 原子写 filesDir/<scopePackage>.json（含 apk 指纹，位于模块 filesDir 根目录）

阶段 B（目标进程，hook 加载时）：
  handleLoadPackage
    → DexIndexStore.lookup / string          // openRemoteFile + gson，进程内缓存
        ├ 能力检测 PROP_CAP_REMOTE + try-catch
        └ 任何失败 → null → 调用方回退硬编码（现状语义不变）
```

## 关键机制：libxposed Remote Files

- hook 进程通过 `XposedInterface.openRemoteFile(name)` 读取**模块私有目录**根
  `filesDir` 下的文件，LSPosed daemon 以特权代读，**无需 chmod、保持私有**。
- 文件名必须是简单名（不含 `/`、`\`、`.`、`..`），且 **Remote Files 根就是
  filesDir，不支持子目录**——索引文件必须直接写在 filesDir 根目录（`<scopePackage>.json`）。
- 老框架/embedded 框架不支持时抛 `UnsupportedOperationException`/`FileNotFoundException`
  （或 `AbstractMethodError`）→ `DexIndexStore` 静默返回 null，hook 走硬编码 fallback。

## 目录与文件

```text
com.qimian233.ztool.dexindex/
  DexIndexConstants.kt   // 目录名/schema 版本/模块 key/字段 key 常量
  DexIndexer.kt          // 接口：scopePackage + index(bridge, context): JsonObject
  LauncherDexIndexer.kt  // com.zui.launcher 作用域（3 模块查询）
  SystemUiDexIndexer.kt  // com.android.systemui 作用域（2 模块查询）
  MobileDesktopDexIndexer.kt // com.motorola.mobiledesktop 作用域（2 模块查询）
  DexIndexRegistry.kt    // indexers 注册表（作用域 → Indexer 唯一映射）
  DexIndexManager.kt     // app 侧执行器：bridge/原子写/指纹/异常隔离
  DexIndexReceiver.kt    // 模块安装/更新触发

com.qimian233.ztool.hook.base/
  DexIndexStore.kt       // hook 侧只读工具（唯一依赖 libxposed 的索引类）
```

## 新增一个使用 DexKit 的 Hook

1. 若目标包已有作用域 Indexer，把查询代码**原样迁移**进对应 Indexer（包一层 try-catch）；
   否则新建 `XxxDexIndexer` 并在 `DexIndexRegistry` 登记（scopePackage 引用 `ScopeKeys`）。
2. 在 `DexIndexConstants.Keys` 添加输出字段 key；如模块 key 不存在则加到 `ModuleKeys`
   （必须与 Hook 的 `getModuleName()` 一致）。
3. **Indexer 不写 fallback 值**：查询失败就不写该 key，由 Hook 侧回退硬编码。
4. Hook 侧（`handleLoadPackage` 回调阶段，勿在 lambda 内做 IO）：
   ```kotlin
   val name = DexIndexStore.string(
       xposed, ScopeKeys.XXX.packageName,
       DexIndexConstants.ModuleKeys.MODULE, // 或者 PreferenceKeys.MODULE_NAME.name
       DexIndexConstants.Keys.FIELD
   ) ?: "硬编码fallback"
   ```
5. 删除 Hook 内原 DexKit 相关 import/代码；不再引用 `DexKitHelper`（已删除）。

## 配置文件格式

`filesDir/com.zui.launcher.json`（模块 filesDir 根目录）

```json
{
  "schemaVersion": 1,
  "generatedAt": 1730000000000,
  "apk": { "path": "...", "lastUpdateTime": 123, "signatureHash": "sha256-hex" },
  "modules": {
    "clean_global_search": { "hotwordInitMethod": "K0", "hotwordDataMethod": "E0" }
  }
}
```

- `apk` 指纹（路径 + PackageInfo.lastUpdateTime + 签名 SHA-256）用于失效检测：
  目标 app 更新（OTA）后 `ZToolApplication` 启动时自动重扫。
- 写入为原子写（tmp + rename），避免 hook 侧读到半截 JSON。

## 注意

- 索引完成后需**重启目标进程或热重载**才生效（`handleLoadPackage` 仅在进程启动时执行一次）。
- 首次安装后目标进程可能先于索引启动 → 走硬编码 fallback（行为与改造前一致，不劣化）。
- 不要在 hook lambda（`hookWithId` 回调）里读索引/做 IO；在 `handleLoadPackage` 阶段读好。
- `DexIndexManager` 依赖 dexkit native 库（app 侧），索引失败不影响 app 使用。
