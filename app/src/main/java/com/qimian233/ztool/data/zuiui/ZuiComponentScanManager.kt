package com.qimian233.ztool.data.zuiui

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Progress and results of the ZUI component scan. */
data class ZuiComponentScanState(
    val running: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val currentPackage: String = "",
    val hasScannedOnce: Boolean = false,
    /** Packages carrying a verdict, scanned or not. */
    val scannedApps: Int = 0,
    val zuiPackages: List<String> = emptyList(),
    /** Increments when a user-triggered run finishes, so the UI can react once. */
    val completedManualRuns: Int = 0
)

/**
 * Runs the ZUI component scan and owns its progress.
 *
 * A process-wide singleton on purpose: the work must outlive the page that started it, so
 * neither the state nor the coroutine scope may belong to a ViewModel.
 */
object ZuiComponentScanManager {

    private const val TAG = "ZuiComponentScan"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ZuiComponentScanState())
    val state: StateFlow<ZuiComponentScanState> = _state.asStateFlow()

    @Volatile
    private var job: Job? = null

    /** User-triggered run. The first call also opens the gate for automatic runs. */
    fun start(context: Context) = launch(context.applicationContext, manual = true)

    /** Automatic incremental run on page entry; a no-op until the user has triggered once. */
    fun refreshStale(context: Context) = launch(context.applicationContext, manual = false)

    fun cancel() {
        job?.cancel()
    }

    @Synchronized
    private fun launch(context: Context, manual: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            val store = ZuiComponentScanStore(context)
            var snapshot = store.load()
            if (manual && !snapshot.firstScanStarted) {
                snapshot = snapshot.copy(firstScanStarted = true)
                store.save(snapshot)
            }
            publish(snapshot)
            if (!snapshot.firstScanStarted) return@launch

            val installed = installedApps(context)
            val pruned = snapshot.verdicts.filterKeys { it in installed }
            if (pruned.size != snapshot.verdicts.size) {
                snapshot = snapshot.copy(verdicts = pruned)
                store.save(snapshot)
                publish(snapshot)
            }

            val stale = installed.keys
                .filter { pkg -> isStale(snapshot.verdicts[pkg], installed.getValue(pkg)) }
                .sorted()

            if (stale.isEmpty()) {
                if (manual) finishManual()
                return@launch
            }

            try {
                _state.value = _state.value.copy(running = true, scanned = 0, total = stale.size)
                for ((index, pkg) in stale.withIndex()) {
                    if (!isActive) return@launch
                    val app = installed.getValue(pkg)
                    _state.value = _state.value.copy(scanned = index, currentPackage = pkg)
                    val contains = ZuiComponentScanner.containsZuiComponents(app.paths)
                    snapshot = snapshot.copy(
                        verdicts = snapshot.verdicts + (
                            pkg to ZuiComponentVerdict(
                                containsZuiComponents = contains,
                                sourceDir = app.sourceDir,
                                lastUpdateTime = app.lastUpdateTime,
                                checkedAt = System.currentTimeMillis()
                            )
                            )
                    )
                    store.save(snapshot)
                    _state.value = _state.value.copy(
                        scanned = index + 1,
                        scannedApps = snapshot.verdicts.size,
                        zuiPackages = zuiPackagesOf(snapshot)
                    )
                }
                if (manual) finishManual()
            } finally {
                _state.value = _state.value.copy(running = false, currentPackage = "")
            }
        }
    }

    private fun finishManual() {
        _state.value = _state.value.copy(
            completedManualRuns = _state.value.completedManualRuns + 1
        )
    }

    private fun publish(snapshot: ZuiComponentScanSnapshot) {
        _state.value = _state.value.copy(
            hasScannedOnce = snapshot.firstScanStarted,
            scannedApps = snapshot.verdicts.size,
            zuiPackages = zuiPackagesOf(snapshot)
        )
    }

    private fun zuiPackagesOf(snapshot: ZuiComponentScanSnapshot): List<String> =
        snapshot.verdicts.filterValues { it.containsZuiComponents }.keys.sorted()

    private fun isStale(verdict: ZuiComponentVerdict?, app: InstalledApp): Boolean =
        verdict == null ||
            verdict.lastUpdateTime != app.lastUpdateTime ||
            verdict.sourceDir != app.sourceDir

    private data class InstalledApp(
        val sourceDir: String,
        val lastUpdateTime: Long,
        val paths: List<String>
    )

    @Suppress("DEPRECATION")
    private fun installedApps(context: Context): Map<String, InstalledApp> {
        val self = context.packageName
        return runCatching {
            context.packageManager.getInstalledPackages(0)
                .asSequence()
                .filter { it.packageName != self }
                .mapNotNull { info ->
                    val application = info.applicationInfo ?: return@mapNotNull null
                    val paths = buildList {
                        add(application.sourceDir)
                        application.splitSourceDirs?.filterNotNull()?.forEach { add(it) }
                    }
                    info.packageName to InstalledApp(
                        sourceDir = application.sourceDir,
                        lastUpdateTime = info.lastUpdateTime,
                        paths = paths
                    )
                }
                .toMap()
        }.getOrElse {
            Log.w(TAG, "installed package enumeration failed", it)
            emptyMap()
        }
    }
}
