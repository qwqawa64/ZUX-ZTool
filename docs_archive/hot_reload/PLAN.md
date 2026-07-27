# 模块热重载实现计划

## 概述

根据 libxposed API 102 热重载示例 (`reference/example`) 调查结果，
ZTool 完全未实现热重载生命周期回调，导致热重载被拒绝。

本文档记录逐层实现计划，Phase 1–2 已全部完成，Phase 3 待评估。

---

## Phase 1 — 启用热重载 + 基础重挂 Hook ✅ 已完成

> 提交: `a4412967`

### 目标

让热重载不被打回，新代码能重新安装全部 Hook。

### 改动文件

| # | 文件 | 变更 |
|---|------|------|
| 1 | `HookManager.java` | 拆分 `initialize` → `registerAllModules()`；新增 `savedPackageParams`/`savedSystemServerParam` 缓存；新增 `reinitializeForHotReload()` + `replayAllHooks()` |
| 2 | `HookInit.java` | 覆写 `onHotReloading` → `return true`；覆写 `onHotReloaded` → reinitialize + replay + unhook old |

### 热重载流程

```
onHotReloaded:
  1. HookManager.reinitializeForHotReload(this)   ← 清空旧模块，注册全部新模块
  2. HookManager.replayAllHooks()                  ← 回放缓存的 package/systemServer 参数
  3. oldHookHandles.forEach(unhook)                ← 移除旧 Hook
```

### 已知局限

旧 Hook 先安装再移除，存在短暂真空窗口 → Phase 2 解决。

---

## Phase 2 — 原子替换：消除 Hook 真空窗口 ✅ 已完成

> P0: `26357fb` | P1: `749b9b1d` | P2+: `e6a24c2e`

### 原理

libxposed API 内置：同一 module、同一 executable 上，使用相同 `setId()` 的新 Hook
会**自动原子替换**旧 Hook。

### 基础设施

| 文件 | 变更 |
|------|------|
| `BaseHookModule.java` | 新增 `hookWithId(Executable, String id, Hooker)` 方法 |

### 覆盖范围

| 批次 | 目录 | 文件数 | Hook 数 | 方式 |
|------|------|:---:|:---:|------|
| P0 | `systemFramework/` | 8 | 14 | 手工逐文件 |
| P1 | `systemui/` + `setting/` | 31 | 101 | Python 脚本批量 |
| P2+ | `launcher/`, `gametool/`, `ota/`, `packageinstaller/`, `wallpaper/`, `documentsui/`, `safecenter/`, `mobiledesktop/` | 32 | 103 | Python 脚本批量 |
| **合计** | **全部 16 个模块目录** | **71** | **218** | |

### ID 命名约定

模块内唯一，描述性小写+下划线。从方法变量名自动生成（camelCase → snake_case）。
同文件内重复 ID 自动加 `_2`, `_3` 后缀去重。

### 热重载流程（Phase 2 增强）

```
onHotReloaded:
  1. reinitializeForHotReload(this)     ← 注册全部新模块
  2. replayAllHooks()                   ← 回放生命周期 → hookWithId() 带相同 ID
                                         → 框架自动 replaceHook() 原子替换
  3. oldHookHandles.forEach(unhook)     ← 移除残余旧 Hook（replaced 的已失效，unhook 是 no-op）
```

---

## Phase 3 — 资源清理：防止 classloader 泄漏

### 状态：待评估

### 问题清单

| # | 文件 | 问题 | 类型 | 风险 |
|---|------|------|------|------|
| 1 | `OwnerInfoHook.java:252` | `new Thread()` 无停止机制 | 线程 | 🟡 低 — HTTP 请求线程，快速完成 |
| 2 | `DexKitHelper.kt:24,32` | `System.loadLibrary("dexkit")` + bridge 缓存无清理 | Native | 🟡 低 — loadLibrary 一次性，旧 bridge 未关闭 |
| 3 | `NativeNotificationIcon.java:33` | `ThreadLocal<Boolean> isCtsMode` | ThreadLocal | 🟢 极低 — 阻止 GC 但不影响功能 |
| 4 | `PermissionControllerHook.java:75` | `ThreadLocal<Boolean> isRowVersionTls` | ThreadLocal | 🟢 极低 — 同上 |

### 影响评估

| 风险项 | 是否阻止热重载？ | 是否导致崩溃？ | 实际影响 |
|--------|:---:|:---:|------|
| 未停止的线程 | 否（`onHotReloading` 仍返回 true） | 可能有竞态 | 旧线程可能在新代码加载后继续操作旧对象，但 OwnerInfoHook 的线程是短生命周期 HTTP fetch |
| ThreadLocal 残留 | 否 | 否 | 阻止旧 classloader GC → 内存泄漏。一个 classloader 通常几 MB，可接受 |
| Native 库未卸载 | 否 | 否（再次 load 是 no-op） | `DexKitBridge` 旧实例持有 native 资源未 close，但热重载后会被新 bridge 替代 |
| LsposedServiceProtector | N/A | N/A | 当前**未注册**到 HookManager，不参与热重载 |

### 建议

**Phase 3 优先级：低。** 当前热重载功能（Phase 1+2）在实测中已验证可用（7 SUCCEEDED, 3 UNSUPPORTED）。
Phase 3 的改善主要在于长期运行的内存效率（避免多次热重载后的 classloader 累积），
不影响单次热重载的正确性。

如果后续长期运行中出现 classloader 泄漏导致 OOM，
再实施 Phase 3。届时方案：

- `BaseHookModule` 新增 `onHotReloading()` 调用的 `prepareForHotReload()` 钩子
- 各模块覆写以清理线程/ThreadLocal/native 资源
- `HookInit.onHotReloading()` 在 `return true` 之前遍历模块调用 `prepareForHotReload()`

---

## 验证方案

1. `.\gradlew.bat assembleDebug` 编译通过 ✅
2. 安装到设备，触发"高级选项 → 热重载全部模块" ✅
   - 7 个 SUCCEEDED，3 个 UNSUPPORTED（已是最新版，无需热重载）
3. 观察 Logcat 确认 `onHotReloading` / `onHotReloaded` 被调用 ✅
4. 验证各 Hook 功能在热重载后仍正常工作 ✅
