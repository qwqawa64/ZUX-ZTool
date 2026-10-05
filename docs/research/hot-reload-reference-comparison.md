# Hot Reload in HyperLyric and HyperCeiler — How They Solve Two Problems, And Where They Fall Down

Analysis note. Scope: **module hot reload (libxposed API 102)** in two reference modules, compared
against ZUX-ZTool's current implementation.

Cloned into `reference/` (gitignored), all clones verified with `git fsck` and HEAD checks:

| Repo | Path | HEAD | Notes |
|---|---|---|---|
| limczhh/HyperLyric | `reference/HyperLyric` | `22a1e30` (`1940-7.7-22a1e30`) | no submodules, no LFS, no token needed |
| ReChronoRain/HyperCeiler | `reference/HyperCeiler` | `5e4686069` | 4664 commits, no submodules, no token needed |
| lingqiqi5211/EzHookTool | `reference/EzHookTool` | `4bbfcd6` | **not referenced by the task** — cloned because HyperCeiler's hot-reload engine lives here, not in HyperCeiler |

> Cloning needed `git -c http.sslBackend=openssl`. This sandbox's schannel backend fails with
> `SEC_E_NO_CREDENTIALS`; no GitHub token was required.

## 0. The two problems, stated precisely

1. **Reflection results and reflection caches do not survive the generation boundary.** Hot reload
   loads the module APK in a fresh module `ClassLoader`. Every static field in module code resets.
   A cached `Method`/`Field`/`Class` is only reusable by the next generation if it is
   *classloader-neutral*, i.e. its declaring class belongs to the **target app or the boot
   classloader**, not to the module. In practice the target app's `Class` objects *are* identical
   across generations (the target process is never restarted), so target-side reflection identity is
   stable — but any module-side cache is gone, and any value *carried* across the boundary must not
   pin the old module classloader.
2. **Replaying a constructor hook does not re-fire it.** A constructor hook only fires for
   instantiations that happen *after* it is installed. Everything constructed during target startup
   was already constructed before the reload. So a constructor hook that captured a long-lived
   singleton (`DarkIconDispatcherImpl`, a SystemUI tile, an adapter, a controller) cannot recover
   that singleton by reinstalling the hook. Reinstalling is necessary (so future instances are
   covered) but not sufficient.

Both problems are real in all three codebases; neither is fully "solved" — both are handled by
**carrying a narrow, validated, classloader-neutral payload across the boundary and rebuilding
everything else**.

## 1. Problem 1 — reflection caches and classloader neutrality

### 1.1 HyperLyric: no cross-generation reflection cache at all

HyperLyric carries no reflection results. It re-derives the target `ClassLoader` and redoes every
lookup. This is the whole strategy, and it is only viable because the module is small.

`reference/HyperLyric/app/src/main/java/com/lidesheng/hyperlyric/root/HookEntry.kt` (`onHotReloaded`,
line 255) reconstructs the classloader from two independent sources, because `onPackageLoaded` is
**not** replayed:

```kotlin
val app = findCurrentApplication()                    // ActivityThread.currentApplication()
val classLoader = app?.classLoader
    ?: param.oldHookHandles.asSequence()              // fallback: old handles carry target Class objects
        .map { it.executable.declaringClass.classLoader }
        .firstOrNull { loader -> loader != null && !loader.javaClass.name.contains("BootClassLoader") }
```

and plugin classloaders are re-derived the same way, from the old handles:

```kotlin
val pluginClassLoaders = param.oldHookHandles.asSequence()
    .map { it.executable.declaringClass.classLoader }
    .filterNotNull()
    .filterNot { it.javaClass.name.contains("BootClassLoader") }
    .toMutableSet()
pluginClassLoaders.forEach { UnlockIslandWhitelist.doHookInClassLoader(it); ... }
```

**This is the key trick worth copying: `oldHookHandles[].executable.declaringClass.classLoader` is a
framework-provided, classloader-neutral handle on the target's loaders.** ZTool currently ignores
`oldHookHandles` for anything except unhooking.

State that *is* carried lives in `encodeHotReloadState` (line 868) and is deliberately primitive
plus native-host objects only:

```kotlin
return ArrayList<Any?>(5).apply {
    add(HOT_RELOAD_TRANSFER_VERSION)
    add(meta)          // Bundle: booleans, Int
    add(dispatcher)    // raw SystemUI DarkIconDispatcherImpl
    add(hosts)         // ArrayList: native host ViewGroup + String packageName/kind/moduleType
    add(ArrayList(transfers.statusBarRoots))
}
```

with the invariant written down next to it (line 884):

> The ArrayList/Bundle containers are framework objects. The only non-container reference is the
> native host ViewGroup, after `removeInjectedViewsForHotReload` has removed every HyperLyric View,
> tag and listener-owned child from its tree.

So the module object graph is severed **before** the native object is handed over — the comment on
`dispatcherForHotReload()` (line 130) says the same thing:

> Its old receiver is removed before this object is passed across generations, so the dispatcher
> does not retain a module proxy from the old classloader.

Individual reflection caches inside HyperLyric are all keyed by target `Class`/`ClassLoader` in
`WeakHashMap`s (`StatusBarTextColorHooker.dispatcherFieldCache`, `SystemUIHookRegistry.hookedClassLoaders`),
so they cannot go stale — they die with the generation. `SystemUIHookRegistry.prepareForHotReload()`
explicitly clears them because the registry object itself is per-generation.

### 1.2 HyperCeiler: two layers, and the inner one is the real one

**Outer layer — `BaseHook.putHotReloadRuntimeState`** (`library/libhook/.../base/BaseHook.java`, line 176):

```java
/** 仅保存宿主/系统 classloader 创建的对象，供下一 generation 重建外部注册。 */
public static void putHotReloadRuntimeState(@NonNull String key, @Nullable Object value) {
    ...
    ClassLoader moduleClassLoader = BaseHook.class.getClassLoader();
    if (moduleClassLoader != null && value.getClass().getClassLoader() == moduleClassLoader) {
        throw new IllegalArgumentException(
            "Hot reload runtime state must not retain a module-classloader object: " + value.getClass().getName());
    }
    HOT_RELOAD_RUNTIME_STATE.put(key, value);
}
```

Three weaknesses relative to what the tool layer does (and the file says so itself — *"HashMap 来自
boot classloader；其中内容仍由 libxposed 在 setSavedInstanceState 时校验"*):

- **Shallow.** Only `value.getClass()` is checked. A boot-classloader `HashMap` holding module
  objects passes here.
- **Exact equality.** `== moduleClassLoader`, not a walk of the parent chain, so a *child* loader
  created by the module passes.
- **No size/depth/cycle bound.** The payload is an unbounded `ConcurrentHashMap`.

HyperCeiler's `XposedInitEntry.prepareHotReloadState` then wraps its extras in a versioned envelope —
a pattern worth copying on its own:

```java
private static final String EXTRA_MAGIC = "HyperCeiler.HotReloadExtras";
private static final int EXTRA_VERSION = 1;
```

> 热重载语义是「旧代码写、新代码读」，槽位布局一旦变化就会被跨版本误读，因此和 EzHookTool 的
> snapshot 一样带 MAGIC + VERSION 头：布局变更时必须递增 `EXTRA_VERSION`，恢复端校验不通过就
> 整体拒绝，不做部分降级。

**Inner layer — EzHookTool `CrossGenerationState`** (`hook-xposed-102/.../HotReloadSession.kt`, line 424)
is the rigorous implementation. It deep-copies the payload (32 levels / 4096 values), rejects cyclic
containers and key-collapsing, and rejects module-classloader objects **at every node**:

```kotlin
private fun validateAtomic(value: Any) {
    if (CrossGenerationState.isModuleClassLoader(value.javaClass.classLoader)) reject(value.javaClass.name)
    when (value) {
        is Class<*>       -> if (isModuleClassLoader(value.classLoader)) reject("Class<${value.name}>")
        is ClassLoader    -> if (isModuleClassLoader(value)) reject(value.javaClass.name)
        is java.lang.reflect.Member ->
            if (isModuleClassLoader(value.declaringClass.classLoader))
                reject("${value.javaClass.name}<${value.declaringClass.name}>")
    }
}

private fun isModuleClassLoader(classLoader: ClassLoader?): Boolean {
    var current = classLoader
    while (current != null) {                        // walks the parent chain
        if (current === moduleClassLoader) return true
        current = current.parent
    }
    return false
}
```

Arrays check their component type; `Map`/`Collection`/arrays are copied rather than referenced. This
is the reference-grade version of "only carry values that are classloader-neutral".

### 1.3 The reflection cache itself is retired, not shared

EzHookTool's `core` is a separate artifact with no Android/Xposed dependency. Its cache is explicitly
**generation-scoped and retired** (`core/src/main/kotlin/.../ReflectionContext.kt`):

```kotlin
@Synchronized
fun update(change: (ReflectionState) -> ReflectionState) {
    val previous = current
    val next = change(previous).copy(version = previous.version + 1)
    current = next
    previous.retire()          // retired = true; cache.clear(); later put() is a no-op
}
```

with `ReflectionState` holding a bounded LRU (`capacity = 4096`, results >256 items are not cached)
and identity-keyed entries (`owner === other.owner` — a `Class` object). Per-thread snapshotting via
`ThreadLocal` guarantees an in-flight old query cannot write into the new table.

`EzXposed.restoreHotReloaded` (line 715) drives it from the restored snapshot:

```kotlin
EzReflect.init(snapshot.classLoader)
```

So the design decision is: **the cache is never shared across generations; it is retired and rebuilt
against the restored classloader.** ZTool's offline DexIndex follows the same spirit for the
*expensive* lookups — but HyperCeiler additionally has to carry `ApplicationInfo` purely to rebuild
DexKit cache paths, because DexKit is not classloader-neutral:

> 普通应用进程必须带上 `ApplicationInfo`，否则热重载后 DexKit 无法重建缓存路径。

### 1.4 Summary for problem 1

| | mechanism | guard strength |
|---|---|---|
| HyperLyric | carry primitives + native host objects; re-derive loaders from `oldHookHandles` | informal, enforced by comments + manual receiver/view detachment |
| HyperCeiler (own layer) | `putHotReloadRuntimeState` + MAGIC/VERSION envelope | shallow: own class only, exact equality, unbounded |
| EzHookTool (HyperCeiler's engine) | `CrossGenerationState` deep snapshot validator | strong: recursive, parent-chain aware, Class/ClassLoader/Member aware, bounded, cycle-rejecting |
| `EzReflect` cache | scoped state, retired on `update()` | strong: identity-keyed, bounded, no writes after retire |
| **ZTool** | carries `PackageLoadedParam` / `SystemServerStartingParam` objects and replays them | **no neutrality validation and no host-state channel at all** |

## 2. Problem 2 — constructor hooks are one-shot

Nobody makes a constructor re-fire. All three projects do the same two things: (a) keep the hook
continuously attached so *future* instantiations are covered, and (b) carry the **already-constructed
object** across the boundary and re-establish the new generation's listeners on it.

### 2.1 (a) Continuous attachment via API 102 atomic replace

`HookHandle.replaceHook` / `setId` + stable ID means the new generation replaces the old hook on the
same `Executable` without an unhook window. HyperLyric (`HookRuntimeRegistry.Owner.install`, line 256):

```kotlin
val oldHandle = if (reloadable) takeOldHandle(signature, id) else null
val handle = oldHandle?.replaceHook(guarded) ?: run {
    if (deoptimize) module.deoptimize(executable)
    module.hook(executable).also { if (api102) it.setId(id) }.intercept(guarded)
}
```

It matches on `ExecutableSignature(declaringClass, name, parameterTypes, returnType, constructor)`,
i.e. full executable identity rather than a name — and `takeOldHandle` has a one-time legacy path for
handles installed before IDs existed.

EzHookTool's `HotReloadSession` does the same by `executable + reloadKey`, and never unhooks a
replaced handle:

```kotlin
if (oldHook.identity != null && oldHook.identity in currentHooks) {
    // libxposed 已根据 executable + hook ID 原子替换它；旧 handle 现在无效，不能再 unhook。
    replacedCount++
    continue
}
```

HyperCeiler does not hand-roll this; it bridges to EzHookTool and gets `AutomaticHotReloadResult`.

### 2.2 (b) Carrying the already-constructed host object

**HyperLyric — `StatusBarTextColorHooker`.** The dispatcher is created once by SystemUI; the
constructor hook (line 187) fires at most once per process, so hot reload must not rely on it:

```kotlin
private var activeDispatcher: WeakReference<Any>? = null

// onHotReloading (old generation)
fun dispatcherForHotReload(): Any? = activeDispatcher?.get()

// onHotReloaded (new generation)
fun restoreDispatcherAfterHotReload(dispatcher: Any?) {
    if (dispatcher == null) return
    registerReceiver(dispatcher)     // new generation's receiver
    captureDispatcher(dispatcher)
}
```

Ordering matters and is explicit in `onHotReloading`: `cleanupForHotReload()` removes the *old*
receiver from the dispatcher before the raw dispatcher is placed into the saved state, so the
dispatcher never holds a proxy from the dying generation.

**HyperCeiler — `TileUtils`.** Same shape, for the same reason
(`library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/appbase/systemui/TileUtils.java`, line 694):

```java
/**
 * handleSetListening 不会在 API 102 热重载后由 SystemUI 重放；记录宿主 tile 后，
 * 新 generation 可以立即为仍在监听的磁贴重新建立 Observer/Receiver。
 */
private void rememberListeningState(@NonNull Object tileInstance, boolean listening) {
    if (!listening) { BaseHook.putHotReloadRuntimeState(hotReloadStateKey("tile"), null); ... return; }
    BaseHook.putHotReloadRuntimeState(hotReloadStateKey("tile"), tileInstance);
    BaseHook.putHotReloadRuntimeState(hotReloadStateKey("active"), Boolean.TRUE);
}
```

and `restoreListeningStateAfterHotReload()` replays the *effect* the constructor hook would have had
(`onListeningChanged(ctx, true)`) using the carried tile instance.

**EzHookTool** supplies only the channel (`HotReloadScope.putState` / `state(key, type)`, restored in
`onExtra` before `onTargetReady`). It does not automate the constructor case — that remains the
module's job.

### 2.3 The escape hatch: declare a capability non-reloadable

HyperLyric's media-card hooks are deliberately *not* migrated. `HookRuntimeRegistry`:

```kotlin
private fun isReloadableCapability(capability: String): Boolean = !capability.startsWith("media.")
```

> Handles belonging to media cards remain installed in their original generation; Super Island,
> lyric, color, lifecycle and whitelist handles are replaceable.

Non-reloadable hooks are excluded from atomic replacement, excluded from `deactivateAndAwait`, and
keep running old-generation code. `isLegacyMediaSignature` infers this for pre-ID handles by a
name heuristic (`className.contains("media", ignoreCase = true)`, `name == "dispatchTouchEvent"`, …)
— fragile, and admitted as a one-time migration aid in the comment.

This is the pragmatic answer when a constructor hook owns state that cannot be reconstructed: accept
a stale-code island instead of restarting the scope. Its cost is a pinned old classloader.

## 3. Where each project steps in it

### 3.1 HyperLyric

| # | Pitfall | Evidence |
|---|---|---|
| 1 | `media.*` hooks stay in the old generation forever → the old module classloader is pinned, and those hooks keep executing **stale code**. Accepted, not solved. | `HookRuntimeRegistry.isReloadableCapability`, line 144 |
| 2 | Legacy capability inference is a class-name heuristic; `dispatchTouchEvent` matches any class. | `isLegacyMediaSignature`, line 157 |
| 3 | `ExecutableSignature` is a `data class` keyed on live `Class<*>` objects, so the reload holds strong references to the dying generation's classes for its duration. Benign (target classes outlive it) but it is not neutral. | line 103 |
| 4 | Refuses the reload rather than degrade when handoff cannot be proven: main-thread point unavailable, whitelist listeners not detached, or in-flight hook callbacks not quiescent within 2500 ms. Good behaviour, but it means a busy SystemUI falls back to restart. | `onHotReloading`, lines 181–232 |
| 5 | Any failure in `onHotReloaded` triggers a heavy unwind (`abortAndAwait`, runtime cleanup, island cleanup). Correct but the generation is left native-only — no retry. | `onHotReloaded` catch, line 349 |
| 6 | No native library and no DexKit anywhere → it never meets LSPosed's native-library restriction. This is a consequence of its pure-reflection design, not of any hot-reload work. | no `.so` in `git ls-files`, no `dexkit` in `libs.versions.toml` |

### 3.2 HyperCeiler

| # | Pitfall | Evidence |
|---|---|---|
| 1 | Own layer's neutrality guard is shallow (own class only, exact equality, unbounded, not recursive). It leans on libxposed's final validation instead. | `BaseHook.putHotReloadRuntimeState`, line 176 |
| 2 | **It ships a native library** (`arm64-v8a`, CMake, `externalNativeBuild`) *and* DexKit 2.2.0, and it dlopens the native lib inside the launcher process. By the LSPosed rule ZTool already documented (APK-level `lib/` presence ⇒ `"Hot reload with native libraries is supported only for stale targets"`), HyperCeiler lands in the same bucket. *Inferred, not measured on HyperCeiler* — the `.so` presence and the dlopen are verified, the LSPosed consequence is ZTool's documented behaviour. HyperCeiler does not work around it; it only reflects the framework's `UNSUPPORTED`. | `app/build.gradle.kts` lines 69–104; `XposedInitEntry.probeLauncherNativeEntry`; no `native` handling in `HotReloadManager` |
| 3 | **It still falls back to restarting the scope — exactly the behaviour in question.** `FAILED` / `UNSUPPORTED` / `SERVICE_UNAVAILABLE` / `SERVICE_ERROR` escalate to killing the target processes, or to a full device-reboot dialog for `system_server`. | `HotReloadDialogHelper.showRestartFallbackIfNeeded`, line 178 |
| 4 | The escalation is at least honest about uncertainty: `TIMED_OUT` and `IN_PROGRESS` deliberately do **not** kill, because they do not prove failure. | same, line 174 comment |
| 5 | Any rule that failed to initialise poisons the generation permanently: the next `onHotReloading` is *refused* (`getHotReloadBlockReason`). Correct safety, but it means one bad rule ⇒ restart required. | `BaseLoad.java` lines 77–103 |
| 6 | Any rule failing during the *new* generation raises out of `verifyHotReloadInitialization()`, failing the whole reload instead of degrading. | `BaseLoad.java` lines 113–123 |

### 3.3 EzHookTool (the engine HyperCeiler relies on)

| # | Pitfall | Evidence |
|---|---|---|
| 1 | `markHotReloadIrreversible()` is called on essentially every install path, so rollback after the first new hook is impossible by construction; a mid-way failure becomes *"Restart the target process before continuing"*. | `EzXposed.kt` line 113; `HotReloadSession.installHook` line 186; `HotReloadAttempt.kt` |
| 2 | Failure is **sticky**: `recordHotReloadFailure` + `requireHealthyHotReload()` makes every *subsequent* hot reload throw until the process restarts. | `EzXposed.kt` lines 117–125 |
| 3 | First migration to stable IDs requires one full restart, enforced by a hard `check`: *"Restart the target process once after adding stable keys to every hook."* | `HotReloadSession.restore`, lines 132–136; `HookReloadBatch.captureOldHooks` |
| 4 | Hooks installed asynchronously cannot be reconciled — only the synchronous `onTargetReady` window is tracked. Unkeyed handles outside the batch are simply not managed. | `HotReloadSession` KDoc, lines 21–24 |
| 5 | No batch-level rollback: *"libxposed 只保证单个 handle / 相同 ID hook 的原子替换，不提供跨多个 hook 的整批回滚"*. | `HotReloadSession` KDoc, line 24 |
| 6 | `HotReloadScope` snapshots containers but explicitly **does not isolate mutable state** of non-container host objects (`其它 system / target app 对象保留引用，不隔离内部可变状态`). | `HotReloadScope` KDoc, line 308 |

### 3.4 The pitfall all three share

None of them can resurrect an already-constructed object through a hook. The constructor problem is
only ever *mitigated* by carrying the object. Where the object cannot be carried, the options are:
keep the hook in the old generation (HyperLyric `media.*`), or accept that the reload is refused and
the scope restarts (HyperCeiler's fallback, EzHookTool's sticky failure).

## 4. What this implies for ZUX-ZTool

Current code: `app/src/main/java/com/qimian233/ztool/hook/HookInit.kt` and
`app/src/main/java/com/qimian233/ztool/hook/base/HookManager.kt`.

ZTool's approach — cache `PackageLoadedParam` / `SystemServerStartingParam`, replay
`handleLoadPackage` in the new generation — is a legitimate variant, and the params are framework
objects, so they are classloader-neutral in practice. But it sits at the weakest end of all four
implementations, and the two problems in question are the two it does **not** address:

| Gap | Detail | Reference pattern to adopt |
|---|---|---|
| No host-state channel | There is no equivalent of `putHotReloadRuntimeState` / `HotReloadScope.putState`, so a one-shot constructor hook has **no way at all** to recover its singleton. This is problem 2, unmitigated. | EzHookTool `HotReloadScope.putState` + recursive module-classloader rejection; HyperCeiler MAGIC/VERSION envelope |
| Unhooking handles that were already atomically replaced | `param.oldHookHandles.forEach(XposedInterface.HookHandle::unhook)` touches **all** old handles. Both reference implementations treat a replaced handle as invalid (`旧 handle 现在无效，不能再 unhook`) and unhook only the *unmatched* ones. | HyperLyric `finishHotReload` over `oldHandles` (only unconsumed); EzHookTool `removeObsoleteOldHooks`, line 207 |
| Silent hook loss | `replayAllHooks` wraps every module in `catch (_: Throwable) {}`. A module that throws during replay silently ends up with no hooks, and nothing detects it. | HyperLyric `HookRuntimeRegistry.unmatched()` → hard error; EzHookTool `verifyHotReloadInitialization()` |
| Cannot decline a reload | `onHotReloading` returns `true` unconditionally — no quiescence check, no host-health check. | HyperLyric's four refusal paths (lines 181–232); `BaseLoad.getHotReloadBlockReason()` |
| No in-flight quiescence | Nothing stops old-generation hook bodies from running while the new generation installs. | HyperLyric `HookSlot` + `deactivateAndAwait`, `awaitQuiescent` |
| No external-callback cleanup registry | Listeners / receivers / observers / handlers registered during `handleLoadPackage` are not detached, so replay re-registers them. | `BaseHook.registerHotReloadCleanup` + the typed helpers; HyperLyric's per-hooker `cleanupForHotReload()` |
| No sticky failure record | A generation with failed hooks stays eligible as a reload base, so the next reload starts from a corrupt state. | `BaseLoad.beginHookInitialization` / `getHotReloadBlockReason` |
| Reflection identity is not used | `oldHookHandles` is only used for unhooking. It is the only framework-provided carrier of the target's `ClassLoader`s. | HyperLyric's `oldHookHandles[].executable.declaringClass.classLoader` re-derivation |
| Native-library restriction | DexKit's `libdexkit.so` marks the module as native-containing, permanently `UNSUPPORTED` for `com.android.systemui`, `com.zui.launcher`, `com.motorola.mobiledesktop`. Already documented and accepted in `docs/archive/hot_reload/plan.md`. HyperCeiler has the identical problem with its own native lib and also does not solve it — it just surfaces `UNSUPPORTED` from the framework. | no reference solution exists; HyperLyric avoids it only by having zero native code |

The single highest-value change is the **host-state channel with a recursive
module-classloader guard** (EzHookTool's `CrossGenerationState` shape): without it, problem 2 has no
answer in ZTool at all. The second is **only unhooking unmatched old handles**, which is a small,
self-contained correctness fix in `HookInit.onHotReloaded`.

## 5. Sources

- `reference/HyperLyric/app/src/main/java/com/lidesheng/hyperlyric/root/HookEntry.kt` — `onHotReloading` L172, `onHotReloaded` L255, `encodeHotReloadState` L868
- `reference/HyperLyric/app/src/main/java/com/lidesheng/hyperlyric/root/HookRuntimeRegistry.kt` — `Owner.install` L231, `isReloadableCapability` L144, `HookSlot` L381
- `reference/HyperLyric/.../root/island/effects/color/StatusBarTextColorHooker.kt` — `dispatcherForHotReload` L135, `restoreDispatcherAfterHotReload` L142, constructor hook L187
- `reference/HyperCeiler/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/BaseHook.java` — `putHotReloadRuntimeState` L176, cleanup registry L109
- `reference/HyperCeiler/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/BaseLoad.java` — `getHotReloadBlockReason` L96, `verifyHotReloadInitialization` L113
- `reference/HyperCeiler/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/XposedInitEntry.java` — `onHotReloading` L139, `onHotReloaded` L196, extras envelope L73
- `reference/HyperCeiler/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/appbase/systemui/TileUtils.java` — `rememberListeningState` L702
- `reference/HyperCeiler/library/core/src/main/java/com/sevtinge/hyperceiler/utils/HotReloadDialogHelper.java` — restart fallback L178
- `reference/EzHookTool/hook-xposed-102/src/main/kotlin/io/github/lingqiqi5211/ezhooktool/xposed/HotReloadSession.kt` — `CrossGenerationState` L424, `removeObsoleteOldHooks` L201, key check L132
- `reference/EzHookTool/hook-xposed-102/src/main/kotlin/io/github/lingqiqi5211/ezhooktool/xposed/HookReloadBatch.kt` — batch aggregation, `captureOldHooks` L97
- `reference/EzHookTool/hook-xposed-102/src/main/kotlin/io/github/lingqiqi5211/ezhooktool/xposed/HotReloadAttempt.kt` — irreversible/failure escalation
- `reference/EzHookTool/core/src/main/kotlin/io/github/lingqiqi5211/ezhooktool/core/ReflectionContext.kt` — retire-on-update cache
