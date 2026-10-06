# Capturing the framework's update-triggered hot reload

Analysis note. Scope: the hot reload the **framework** starts on its own after the module APK is
replaced, and whether ZTool's own process can observe it. The user-requested reload from
Settings → Advanced is a separate, trivially observable event and is covered in §5.

Written for the home tip card (`HomeTipRepository`,
`data/hotreload/HotReloadNoticeRepository.kt`), which has to show its hot reload warning
unconditionally when either trigger fired.

## 0. Verdict

| Trigger | Observable from ZTool's process? | How |
|---|---|---|
| Manual (Advanced options) | **Yes, exactly** | ZTool is the caller; `AdvancedSettingsRepository.performHotReloadAll` publishes a notice at dispatch time |
| Framework, on module APK update | **No — only the update is observable** | The reload happens in the *target* processes; libxposed gives the hook side read-only remote storage, and installing an update kills the ZTool process before it could record anything. ZTool records "the APK was replaced since the previous launch" instead, which is an exact precondition of that reload |

The hook-side discriminator for the event *does* exist and is exact
(`HotReloadingParam.getExtras() == null`, see §3.1). It is simply not transportable to this app.
§4 lists the channels that were considered for transporting it and why each was rejected.

## 1. The event, precisely

The framework, not the module, decides to reload. The module opts in with one line of
`module.prop`; ZTool already ships it:

```
app/src/main/resources/META-INF/xposed/module.prop:4:  autoHotReload=true
```

The reload is per **stale target**: a hooked process whose loaded module version code differs from
the installed one. A process that already runs the current generation is not reloaded, and a
process that is not running at all is not reloaded.

## 2. What the framework does, verified against the daemon source

The chain below was read end to end in `reference/Vector` (clone of `JingMatrix/Vector` at
`ddeed8c`, the maintained libxposed-API-102 implementation; its native-library/stale-target rule is
the one already recorded in `docs/archive/hot_reload/plan.md`). The libxposed API javadoc states the
same contract framework-independently — see §7.

```
APK replaced
  → Android broadcasts ACTION_PACKAGE_ADDED (EXTRA_REPLACING=true) + ACTION_PACKAGE_CHANGED
      daemon/…/VectorService.kt:135-141        receiver filter (ADDED / CHANGED / FULLY_REMOVED, scheme "package")
      daemon/…/VectorService.kt:328-334        module branch → ModuleDatabase.updateModuleApkPath(…, force=false)
  → the new install path differs from the stored one
      daemon/…/ModuleDatabase.kt:376-400       row updated, count > 0
      daemon/…/ModuleDatabase.kt:399           ConfigCache.requestCacheUpdate()
  → the daemon re-parses the module APK
      daemon/…/ConfigCache.kt:279              FileSystem.loadModule(apkPath, …)
      daemon/…/FileSystem.kt:308               autoHotReload = props["autoHotReload"].toBoolean()
  → after the cache swap
      daemon/…/ConfigCache.kt:447              newModules.values.forEach { ModuleAppService.autoHotReload(it) }
      daemon/…/ModuleAppService.kt:301-310     if (!module.code.autoHotReload) return
                                               staleHotReloadTargets(pkg).forEach { runHotReload(target, null, null) }
```

Four consequences that matter to ZTool:

1. **The trigger is the update, not a schedule.** No timer, no boot event, no module-app launch.
   The reload fires while the daemon processes the package broadcast, i.e. right after the install.
2. **It is gated on `autoHotReload=true`.** With the flag off, nothing is reloaded and the module
   app must not claim otherwise.
3. **`extras` is null for it.**
   `ModuleAppService.kt:307` passes `runHotReload(target, null, null)`; the daemon's own comment at
   `ModuleAppService.kt:300` says it "drives the same cycle as a service request". The API javadoc
   states the same from the other side: extras "can be null if the app passes null **or the hot
   reload is triggered by app updating**".
4. **It is unconditional in the other direction.** There is no framework-side notification to the
   module app when it happens. `HotReloadCallback` (`reference/service/…/XposedService.java:108`)
   is registered only by `hotReloadModule(...)`, i.e. only for app-requested reloads.

Staleness, for completeness — the daemon computes it live rather than storing it:

```
daemon/…/FrameworkService.kt:173-181   stale ⟺ hotReloadable && loadedVersionCode != 0 && loadedVersionCode != installedVersionCode
daemon/…/FrameworkService.kt:144-154   reportedState(): RELOADING / FAILED outrank the version comparison
daemon/…/FrameworkService.kt:157-166   backfillLoadedVersions() only fills loadedVersionCode == 0 (unknown)
```

## 3. What the module can observe

### 3.1 Hook side — exact, but trapped in the target process

`HookInit.onHotReloading` runs in the **target** process and can tell the two triggers apart:

```kotlin
override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
    val manual = param.extras != null     // see §2.3
    ...
}
```

`reference/api/…/XposedModuleInterface.java:117-144` defines the param; the javadoc on
`getExtras()` is the normative statement of the null rule.

This information cannot leave the target process:

- `XposedInterface.openRemoteFile` is **read-only** in hooked apps —
  `reference/api/…/XposedInterface.java:554-563`: "The file is opened in read-only mode."
  (The writable side, `XposedService.openRemoteFile`, exists only in the module app process.)
- `XposedInterface.getRemotePreferences` is likewise read-only —
  `XposedInterface.java:536-543`: "Note that those are read-only in hooked apps."
- The module app's own `filesDir` is private to ZTool's uid; a target process cannot write there.

So the hook can *know* the answer and has nowhere to put it.

### 3.2 App side — the update, and two live diagnostics

Observable from ZTool's process:

| Signal | Source | Value |
|---|---|---|
| The module APK was replaced since the previous launch | `PackageInfo.lastUpdateTime` (recorded by ZTool itself) | Exact precondition of the auto reload; **this is what is used** |
| A target is still on old code / reload failed | `XposedService.getRunningTargets()` → `HookedTarget.state` and `.loadedVersionCode` | Live, but racy at launch: the reload is asynchronous, so a query can land before, during or after it |
| `autoHotReload` is on | `module.prop`, i.e. a build-time fact | Mirrored as one constant in the repository |

`lastUpdateTime` rather than `versionCode`: a nightly rebuilt from an unchanged version code still
replaces the APK and still makes the framework reload it, and a version-code comparison would miss
exactly those updates.

The `getRunningTargets()` signal is deliberately **not** part of the decision. Sampling it at launch
races the daemon's `hotReloadExecutor` (`ModuleAppService.kt:307`), so the same install can show
`UP_TO_DATE` or `STALE` depending on how fast the user gets to the home screen. It remains a
reasonable future enrichment ("N processes are still on old code") — as a separate, refreshable
row, not as the trigger.

## 4. Channels considered for transporting the hook-side fact, and why each was rejected

| # | Channel | Verdict |
|---|---|---|
| 1 | Hook writes a marker into the module's shared data dir | **Impossible** — remote files/prefs are read-only on the hook side (§3.1) |
| 2 | Hook writes into the target app's own data dir, app reads it with root | Rejected — the hook has no reliable `Context` at `onHotReloading` time (system_server has none at all), the marker would be scattered per target app, and it would make a tip card depend on root |
| 3 | Hook sends an explicit broadcast to `com.qimian233.ztool` | Rejected — the sender is an arbitrary app's uid, so the receiver would have to be `exported="true"` with no permission that sender can hold; a spoofable component is too much surface for a tip card |
| 4 | Hook writes to the framework log; app parses it with root | Rejected — log location and verbosity are framework/version dependent, and the information does not exist at all when verbose logging is off |
| 5 | Put `com.qimian233.ztool` in its own scope so `HookInit` runs inside ZTool | Rejected — **installing an update kills the updated app's process**, so `onHotReloading` would never run in the process being updated. The one case it was meant to cover is exactly the case it cannot see |
| 6 | App-side inference from the update + `autoHotReload` | **Adopted** — §5 |

## 5. The adopted design

`data/hotreload/HotReloadNoticeRepository.kt` + `data/home/HomeTipRepository.kt`:

1. **Manual reload** — `AdvancedSettingsRepository.performHotReloadAll` publishes
   `HotReloadTrigger.MANUAL` on a process-scoped bus at the exact point the request goes out (after
   every early return, so a reload that will not happen cannot raise a notice). The home ViewModel
   collects the bus, so the card updates immediately even though the launch decision was made long
   before. The request also carries a non-null extras bundle with an explicit trigger marker
   (`AdvancedSettingsRepository.EXTRA_HOT_RELOAD_TRIGGER`); the null/non-null distinction is what
   the framework itself uses, and the marker makes it readable once a hook consumes it.
2. **Framework reload on update** — the first launch after the APK's `lastUpdateTime` changed is
   treated as that reload. The marker lives in `noBackupFilesDir`, never in SharedPreferences, so it
   cannot arrive through adb backup or the in-app config export and claim an update this install
   never had.
3. Both publish the same notice type; the tip repository renders `pendingNotice != null` as the
   forced warning and only rolls the 5 % dice when there is no notice.

## 6. What this does not give us

- **The auto-reload notice is an inference, not an observation.** If the installed framework
  predates libxposed API 102, or the reload was refused / failed / skipped because no target was
  stale, the card still appears on the first launch after an update. It is a tip whose text is
  advice ("restart the scope manually"), not an assertion that a reload succeeded — which is why
  gating the tip on the service API version was deliberately left out: that version is not
  necessarily known yet when the home screen resolves its tip, and the card is harmless without it.
- **No count of what was reloaded.** The daemon knows which targets were stale; the module app does
  not, except through the racy `getRunningTargets()` sample.
- **`HotReloadTrigger` records *why* the card is claiming a reload** and is currently only
  distinguished in the notice, not in the UI. It is kept because it is the honest provenance of the
  claim and because item 1 above is the natural place to attach a future hook-side confirmation.

## 7. Sources

Project:

- `app/src/main/resources/META-INF/xposed/module.prop` L4 — `autoHotReload=true`
- `app/src/main/java/com/qimian233/ztool/data/hotreload/HotReloadNoticeRepository.kt` — the detector and the bus
- `app/src/main/java/com/qimian233/ztool/data/home/HomeTipRepository.kt` — the tip decision
- `app/src/main/java/com/qimian233/ztool/data/advanced/AdvancedSettingsRepository.kt` — manual trigger, extras marker
- `docs/archive/hot_reload/plan.md` — ZTool's own hot reload phases and the native-library/stale-target limit

libxposed API (normative):

- `reference/api/api/src/main/java/io/github/libxposed/api/XposedModuleInterface.java` L117-144 (`HotReloadingParam`, extras null rule), L236-267 (`onHotReloading`, "or by app updating if `autoHotReload` is set to true in `module.prop`")
- `reference/api/api/src/main/java/io/github/libxposed/api/XposedInterface.java` L536-543, L554-563 (read-only remote prefs/files on the hook side)
- `reference/service/service/src/main/java/io/github/libxposed/service/XposedService.java` L108-120 (`HotReloadCallback`, app-requested reloads only), L286-344 (`hotReloadModule`)
- `reference/service/service/src/main/java/io/github/libxposed/service/HookedTarget.java` L19-40 (`State`), L90-97 (`loadedVersionCode`)

Framework implementation (`reference/Vector`, `JingMatrix/Vector` @ `ddeed8c`):

- `daemon/src/main/kotlin/org/matrix/vector/daemon/VectorService.kt` L135-141, L268-334 — package broadcasts → module branch
- `daemon/src/main/kotlin/org/matrix/vector/daemon/data/ModuleDatabase.kt` L376-400 — APK path change → cache update
- `daemon/src/main/kotlin/org/matrix/vector/daemon/data/ConfigCache.kt` L279, L419-447 — module reload → `autoHotReload` fan-out
- `daemon/src/main/kotlin/org/matrix/vector/daemon/data/FileSystem.kt` L287, L306-308, L386-396 — `autoHotReload` parsed from `module.prop`
- `daemon/src/main/kotlin/org/matrix/vector/daemon/ipc/ModuleAppService.kt` L300-310 — the auto reload itself; L586-632 — `runHotReload`
- `daemon/src/main/kotlin/org/matrix/vector/daemon/ipc/FrameworkService.kt` L144-181 — stale computation and backfill
