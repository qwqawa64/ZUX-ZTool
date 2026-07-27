# 模块热重载实现计划

## 概述

根据 libxposed API 102 热重载示例 (`reference/example`) 调查结果，
ZTool 当前完全未实现热重载生命周期回调，导致热重载被拒绝。

本文档记录逐层实现计划，分为三个 Phase。

---

## Phase 1 — 启用热重载 + 基础重挂 Hook

### 目标

让热重载不被打回，新代码能重新安装全部 Hook。
存在短暂的 Hook 真空窗口（旧 Hook 先 unhook 再安装新 Hook），留待 Phase 2 解决。

### 改动文件

| # | 文件 | 变更 |
|---|------|------|
| 1 | `HookManager.java` | 拆分 `initialize`，新增缓存+回放机制 |
| 2 | `HookInit.java` | 覆写 `onHotReloading` / `onHotReloaded` |

### HookManager.java 改动细节

1. 将模块注册逻辑提取为 `registerAllModules(XposedInterface)` 私有方法
2. `initialize()` 调用 `registerAllModules()` + 设置 `initialized = true`
3. 新增 `reinitializeForHotReload(XposedInterface)`：
   - 清空 `hookModules`，调用 `registerAllModules()`
4. 新增参数缓存：
   - `List<PackageLoadedParam> savedPackageParams`
   - `SystemServerStartingParam savedSystemServerParam`（可为 null）
5. `handlePackageLoaded()` 中保存 param 到列表
6. `handleSystemServerStarting()` 中保存 param
7. 新增 `replayAllHooks()`：遍历已保存参数，对每个新模块调用 safe handler
   - 用 try-catch 包裹每个模块调用，单个模块失败不影响其他

### HookInit.java 改动细节

1. 覆写 `onHotReloading(HotReloadingParam)` → `return true`
2. 覆写 `onHotReloaded(HotReloadedParam)`：
   ```java
   instance = this;
   HookManager.reinitializeForHotReload(this);
   HookManager.replayAllHooks();
   param.getOldHookHandles().forEach(HookHandle::unhook);
   ```

### 不变更

- `module.prop` — 已有 `autoHotReload=true`
- `BaseHookModule.java` — 无需改动
- 各 Hook 模块 — 无需改动

---

## Phase 2 — 原子替换：消除 Hook 真空窗口

> 待 Phase 1 完成后规划细节。

核心思路：
- `BaseHookModule` 新增 HookHandle 追踪 Map
- 包装 `hook().setId(...)` 自动记录
- `HookManager` 热重载时用 `replaceHook()` 原子替换

---

## Phase 3 — 资源清理：防止 classloader 泄漏

> 待 Phase 1 完成后规划细节。

核心思路：
- `BaseHookModule` 新增 `prepareForHotReload()` 生命周期钩子
- 逐个清理：线程停止、ThreadLocal 移除、native 资源释放

---

## 验证方案

1. `.\gradlew.bat assembleDebug` 编译通过
2. 安装到设备，触发"高级选项 → 热重载全部模块"
3. 观察 Logcat 中 `ZToolXposedModuleInit` tag 确认 `onHotReloading` / `onHotReloaded` 被调用
4. 验证各 Hook 功能在热重载后仍正常工作
