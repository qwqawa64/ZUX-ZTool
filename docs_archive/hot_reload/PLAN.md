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

## Phase 2 — 原子替换：消除 Hook 真空窗口 ✅ 已完成

### 状态：已完成（P0 模块）

### 原理

libxposed API 内置：同一 module、同一 executable 上，使用相同 `setId()` 的新 Hook
会**自动原子替换**旧 Hook，无需手动调用 `replaceHook()`。

```
旧模块                                    新模块
  hook(m).setId("foo").intercept(old)        hook(m).setId("foo").intercept(new)
                                           ↑ 框架识别 ID 匹配 → 原子替换，无真空窗口
```

### 改动文件

| # | 文件 | 变更 |
|---|------|------|
| 1 | `BaseHookModule.java` | 新增 `hookWithId(Executable, String id, Hooker)` 方法 |
| 2-9 | P0 systemFramework 模块 ×8 | 将 `xposed.hook(X).intercept(Y)` 改为 `hookWithId(X, id, Y)` |

### ID 命名约定

模块内唯一，描述性小写+下划线，如 `"op_to_default_mode"`, `"is_secure_locked"`。

### P0 模块清单（全部已加 ID）

| 模块 | Hook 数 | ID 列表 |
|------|:---:|------|
| `AiInputExpand` | 1 | `lgsi_features_enabled` |
| `AllowGetPackages` | 2 | `op_to_default_mode`, `check_operation_raw_zui` |
| `AllowRelativeAppLaunch` | 1 | `relative_app_status` |
| `AllowUntrustedTouch` | 1 | `touch_occlusion_mode` |
| `DisableFlagSecure` | 1 | `is_secure_locked` |
| `ForceScreenOnOffAnimation` | 4 | `color_fade_enabled`, `display_power_controller_init`, `display_power_init`, `animate_screen_state` |
| `KeepRotation` | 1 | `is_rotation_cts` |
| `NoMorePasswordPer24H` | 3 | `reschedule_strong_auth`, `handle_idle_timeout`, `handle_timeout` |

### 待后续完成

- P1 模块（SystemUI、Settings 等）渐进补 ID
- 当前 `onHotReloaded` 流程中 `replayAllHooks()` 后 `unhook` 残余旧 handle 的逻辑无需改动

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
