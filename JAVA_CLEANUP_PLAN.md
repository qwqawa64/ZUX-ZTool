# Java Cleanup Plan — root / utils / magicwindowsearch

> Agent-oriented execution plan. Work through batches in order; build and commit after each
> green batch. Mark checkboxes as you go. Scope was confirmed by the user on 2026-09-09.

## Goal

Eliminate the residual Java source files in the three user-named locations by converting
them to Kotlin (or deleting them when dead), leaving zero `.java` files outside the
Java-infrastructure exemptions in `AGENTS.MD`.

## Inventory (12 files, ~2,450 LOC)

| # | File (under `app/src/main/java/com/qimian233/ztool/`) | LOC | Known users | Strategy |
|---|---|---|---|---|
| 1 | `EnhancedShellExecutor.java` | 500 | ~16 files: `LogUtils.kt`, `ScopeUtils.kt`, 13+ `data/**Repository.kt`, utils managers | Kotlin class + `companion object getInstance()`; nested `ShellResult` → `val` fields + `val isSuccess` property |
| 2 | `utils/FileUtils.java` | 175 | `LogUtils.kt` + 4 utils managers | `object FileUtils` |
| 3 | `utils/FileManager.java` | 118 | `SettingsRepository.kt`, `LogUtils.kt` | `object FileManager` |
| 4 | `utils/ConfigUpgrade.java` | 171 | `MainActivity.kt`, `HomeRepository.kt` | `object ConfigUpgrade` |
| 5 | `utils/MagiskModuleManager.java` | 81 | `SettingsDetailRepository.kt` | Kotlin class, same API |
| 6 | `utils/EmbeddingConfigManager.java` | 151 | `SettingsDetailRepository.kt`, `SettingsDetailViewModel.kt`, `ZuiSettingsDetailScreen.kt` | Kotlin class + nested `data class ConfigFileInfo` |
| 7 | `utils/FontInstallerManager.java` | 164 | `SettingsDetailRepository.kt` | Kotlin class, same API |
| 8 | `utils/OvCommonConfigManager.java` | 272 | `SettingsDetailRepository.kt`, `ZuiSettingsDetailViewModel.kt`, `ZuiSettingsDetailScreen.kt` | Kotlin class + nested `AppConfig` + `companion object` `const val MODE_*` |
| 9 | `utils/GetPCFlashFirmware.java` | 205 | `OtaSettingsRepository.kt` | Kotlin class + `fun interface OnFirmwareQueryListener`; keep `AsyncTask` with `@Suppress("DEPRECATION")` |
| 10 | `utils/PermissionChecker.java` | 61 | **none — dead code** | **Delete** (no conversion) |
| 11 | `screens/zuisetting/magicwindowsearch/ActivityPair.java` | 15 | `PackageInfo` only | `data class ActivityPair(val from: String, val to: String)` |
| 12 | `screens/zuisetting/magicwindowsearch/PackageInfo.java` | 88 | `MagicWindowSearchRepository.kt`, `SearchPageViewModel.kt`, `searchPage.kt` | Kotlin class, `val` properties, JSON-parsing constructor kept |

Call-site churn already verified: the only Kotlin call sites that break mechanically are
3 method-syntax `isSuccess()` calls in `data/launcher/BatchUninstallRepository.kt`
(lines 15, 22, 34). Everything else (`FileManager.foo(...)`, `FileUtils.bar(...)`,
`ConfigUpgrade.configUpgrader(...)`, `EnhancedShellExecutor.getInstance()`,
`packageInfo.name` / `activityPairs` property access, manager constructors) compiles
unchanged after conversion. No Kotlin code constructs `ShellResult` / `ConfigFileInfo` /
`AppConfig` directly. Proguard rules reference none of these classes.

## Scope revision (2026-09-09, user confirmed)

- The `service/` log classes and the two test boilerplate files ARE in scope (former Phase 6
  promoted to Batch 6). `hook/` is already 100% Kotlin — no Java there at all.
- Final target: **zero `.java` files in the whole `app` module.**

## Conversion rules

1. Kotlin only. Keep package, class, and member names exactly unless a rule below says otherwise.
2. Static-only classes → `object` (`FileManager`, `FileUtils`, `ConfigUpgrade`); call syntax unchanged.
3. `EnhancedShellExecutor`: `private constructor` + `companion object` with `@Volatile` instance and
   `fun getInstance()` (14+ default-arg injections depend on it). Port thread pool, cache
   whitelist, rate limiting (ReentrantLock/AtomicInteger/ConcurrentHashMap) 1:1.
4. `ShellResult` → nested class with `val success/output/error/exitCode/executionTime`,
   `val exception: Exception?`, and `val isSuccess: Boolean get() = success && exitCode == 0`
   (replaces the `isSuccess()` method). Update the 3 call sites in `BatchUninstallRepository.kt`.
5. `GetPCFlashFirmware`: listener becomes `fun interface` so the existing trailing-lambda call
   in `OtaSettingsRepository.kt:99` compiles unchanged; `String[]` → `Array<String>?`.
   Faithful port — do NOT rewrite AsyncTask into coroutines in this pass.
6. `PackageInfo` / `ActivityPair` → Kotlin with `val` constructor properties. Kotlin callers
   already use property access (`.name`, `.activityPairs`, ...), so zero churn. Keep the
   `JSONObject`-parsing constructor; `JSONException` is unchecked in Kotlin, caller already
   wraps in try/catch. `android.content.pm.PackageInfo` users import the platform class by
   FQN — no clash.
7. `EmbeddingConfigManager.ConfigFileInfo` → nested `data class` with constructor values
   (`file`, `timestamp`, `packageName`, `appName`, `configContent`); build fields into locals
   and construct after a fully successful parse (current code mutates then returns null on
   failure — behavior identical). Callers only read.
8. `OvCommonConfigManager.AppConfig` → nested class with `var` nullable fields
   (`Boolean?` / `Int?`) and `fun isEmpty()` kept as a function (call sites use method syntax).
9. Manager classes stay instance classes; `SettingsDetailRepository` constructor defaults
   (`MagiskModuleManager()`, etc.) must keep compiling.
10. Preserve all logic, log tags, Chinese comments and messages; all files UTF-8.
    No Manifest / preference-key / scope.list changes in this task.

## Batches (executed 2026-09-09 — all complete)

- [x] **Batch 0 — baseline.** `assembleDebug` green before touching anything.
- [x] **Batch 1 — FileManager → `object`** (no Java callers). Commit `1cf34b43`.
- [x] **Batch 2 — the 5 utils that CALL EnhancedShellExecutor/FileUtils**
  (`MagiskModuleManager`, `EmbeddingConfigManager`, `FontInstallerManager`,
  `OvCommonConfigManager`, `ConfigUpgrade`), converted while those deps were still Java.
  Commits `041fed71`, `aca5dcfd` (nullable font-file guard in `SettingsDetailRepository`).
- [x] **Batch 3 — EnhancedShellExecutor + FileUtils** (no Java callers left).
  `ShellResult.isSuccess()` became a `val isSuccess` property; the 3 method-syntax call
  sites in `BatchUninstallRepository.kt` updated. Commit `ca36f290`.
- [x] **Batch 4 — GetPCFlashFirmware** (`fun interface` listener, `Array<String?>?`
  faithful to Java's null-able elements, 2 safe-call fixes in `OtaSettingsRepository`)
  **+ magicwindowsearch models** (`PackageInfo`/`ActivityPair` data classes) +
  **deleted dead `PermissionChecker`.** Commits `1b4c835b`, `163463f8`.
- [x] **Batch 5 — services + tests.** `LogCollectorService` (class name kept — Manifest
  contract), `LogServiceManager` (`object`, `ServiceStatusListener` interface —
  `MainActivity` implements it unchanged), both example tests. Verified with
  `assembleDebug` + `compileDebugUnitTestKotlin` + `compileDebugAndroidTestKotlin`.
  Commits `c8eefe81`, `e0014434`.
- [x] **Final gate.**
  - `find app/src -name "*.java"` → **zero results**.
  - `assembleDebug` green (per batch and final).
  - `git diff --check` clean.

Note: execution order differs from the original draft — it follows the real Java caller
dependency graph (callers convert before the Java classes they reference), so intermediate
batches always compile.

## Risks & mitigations

- **Chinese text / encoding**: write every file as UTF-8; never let tooling re-encode.
- **`isSuccess()` property switch**: exactly 3 known sites; re-grep `isSuccess()` in `*.kt`
  after Batch 2 to confirm zero remain.
- **Platform-type nullability**: `ShellResult.output/error` are always constructed non-null;
  declare them `String` (non-null) — matches existing `.take(160)`, `.contains(...)`,
  `.split(...)` usage.
- **Behavior drift**: port 1:1, no "improvements" (no coroutine rewrite, no API redesign).
- **Silent scope regression**: none expected — this task touches no hook registration,
  manifest entries, or preference keys.

## Commands

```bash
git status --short
cmd.exe /c "gradlew.bat assembleDebug"
git diff --check
git add <files> && git commit -m "<type>(<scope>): <subject>"
```
