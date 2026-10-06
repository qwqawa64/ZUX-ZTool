package com.qimian233.ztool.data.hotreload

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * What made the framework load a new module generation.
 */
enum class HotReloadTrigger {
    /** The user pressed the hot reload action on the Advanced options screen. */
    MANUAL,

    /**
     * The framework reloaded the module on its own because the module APK was replaced.
     *
     * See [HotReloadNoticeRepository.consumeModuleApkUpdate] for why this is inferred from the
     * update rather than observed directly.
     */
    MODULE_UPDATE
}

/**
 * One "a hot reload just happened" record.
 *
 * Carries [SystemClock.elapsedRealtime] rather than wall clock time: the only question ever asked
 * of it is "how long ago", which a clock the user or NTP can move would answer wrongly.
 */
data class HotReloadNotice(
    val trigger: HotReloadTrigger,
    val recordedAtElapsedRealtime: Long
)

/**
 * Process-scoped channel between whoever learns that a hot reload happened and the home card that
 * has to say so.
 *
 * It has to be process-scoped rather than owned by one repository instance: the writer is
 * [com.qimian233.ztool.data.advanced.AdvancedSettingsRepository] on the Advanced options screen and
 * the reader is the home ViewModel, two object graphs that share nothing else.
 */
object HotReloadNoticeBus {

    private val _pending = MutableStateFlow<HotReloadNotice?>(null)

    /**
     * The notice waiting to be shown, or null once it has been consumed. Deliberately has no
     * expiry: it is consumed the moment the home card fills itself from it, so a notice that is
     * never read is one whose screen was never opened, not a stale one.
     */
    val pending: StateFlow<HotReloadNotice?> = _pending.asStateFlow()

    internal fun consume() {
        _pending.value = null
    }

    /**
     * Records a hot reload that is happening now, for the home card to pick up.
     *
     * Deliberately takes no Context: the writer is the Advanced options repository, which is built
     * without one, and nothing about announcing a reload needs to touch disk.
     */
    fun publish(trigger: HotReloadTrigger) {
        _pending.value = HotReloadNotice(trigger, SystemClock.elapsedRealtime())
    }
}

/**
 * The two hot reload triggers ZTool can tell apart from its own process.
 *
 * [HotReloadTrigger.MANUAL] is observed directly, because ZTool is what requests it. The
 * framework's update-triggered reload is not directly observable from here, and this class records
 * the update instead — see [publishModuleApkUpdateIfReplaced].
 */
class HotReloadNoticeRepository(context: Context) {

    private val context = context.applicationContext
    private val lastSeenBuildFile = File(context.noBackupFilesDir, LAST_SEEN_BUILD_FILE_NAME)

    private var launchChecked = false

    /** The notice waiting to be shown, or null. */
    fun pendingNotice(): HotReloadNotice? = HotReloadNoticeBus.pending.value

    /** Drops the pending notice; called once the home card has filled itself from it. */
    fun consumePendingNotice() = HotReloadNoticeBus.consume()

    /**
     * Raises a [HotReloadTrigger.MODULE_UPDATE] notice when the module APK was replaced since the
     * previous launch, and reports whether it did.
     *
     * The framework reloads an updated module into every stale hooked process on its own, but that
     * runs inside those target processes and cannot be reported back here: libxposed hands the hook
     * side its remote files and remote preferences read-only, and installing an update kills this
     * process before it could record anything anyway. The update itself is the observable half and
     * an exact precondition of that reload, so this is what is recorded. Full reasoning:
     * `docs/research/hot-reload-auto-update-detection.md`.
     *
     * The recorded value is the APK's own `lastUpdateTime`, not the version code: a nightly rebuilt
     * from an unchanged version code still replaces the APK and still makes the framework reload
     * it, and a version-code comparison would miss exactly those updates.
     *
     * Evaluated once per process, in `noBackupFilesDir` rather than SharedPreferences so the marker
     * can never arrive through adb backup or the in-app config export and claim an update this
     * install never had.
     */
    fun publishModuleApkUpdateIfReplaced(): Boolean {
        if (launchChecked) return false
        launchChecked = true

        if (!MODULE_OPTS_INTO_AUTO_HOT_RELOAD) return false

        val installedAt = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrNull() ?: return false
        // Zero means the platform did not tell us, not that the APK predates the epoch; claiming
        // an update from it would fire on every launch.
        if (installedAt <= 0L) return false

        val previouslySeen = runCatching {
            lastSeenBuildFile.readText(Charsets.UTF_8).trim().toLongOrNull()
        }.getOrNull()

        runCatching {
            lastSeenBuildFile.parentFile?.mkdirs()
            lastSeenBuildFile.writeText(installedAt.toString(), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "Failed to record the module APK update time", it) }

        // A missing record is the first launch this install ever had: nothing was replaced, so
        // there is no reload to report.
        val replaced = previouslySeen != null && previouslySeen != installedAt
        if (replaced) HotReloadNoticeBus.publish(HotReloadTrigger.MODULE_UPDATE)
        return replaced
    }

    private companion object {
        const val TAG = "HotReloadNoticeRepo"
        const val LAST_SEEN_BUILD_FILE_NAME = "last_seen_module_build.txt"

        /**
         * Mirrors `autoHotReload=true` in
         * `app/src/main/resources/META-INF/xposed/module.prop`. The framework only reloads a module
         * that opted in, so with that flag off an APK update implies no reload at all and the card
         * would be claiming something that never happened.
         */
        const val MODULE_OPTS_INTO_AUTO_HOT_RELOAD = true
    }
}
