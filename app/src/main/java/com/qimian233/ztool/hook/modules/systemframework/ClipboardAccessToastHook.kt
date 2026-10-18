package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Skips the clipboard access toast (and its Slog line) for whitelisted reading apps.
 *
 * See docs/research/clipboard_access_toast.md
 */
@SuppressLint("PrivateApi")
class ClipboardAccessToastHook : SystemHookModule() {

    @Volatile
    private var suppressEnabled: Boolean = false

    @Volatile
    private var whitelist: Set<String> = emptySet()

    @Volatile
    private var snapshotLoadedAt: Long = 0L

    private fun refreshSnapshotIfStale() {
        val now = SystemClock.elapsedRealtime()
        if (now - snapshotLoadedAt <= SNAPSHOT_REFRESH_INTERVAL_MS) return
        val prefs = remotePreferences
        suppressEnabled = prefs.getBoolean(
            PreferenceKeys.CLIPBOARD_ACCESS_TOAST_SUPPRESS.name, false
        )
        whitelist = (prefs
            .getString(PreferenceKeys.CLIPBOARD_ACCESS_TOAST_PACKAGES.name, "") ?: "")
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        snapshotLoadedAt = now
    }

    override fun getModuleName(): String = PreferenceKeys.CLIPBOARD_ACCESS_TOAST_SUPPRESS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        try {
            val clipboardServiceClass = param.classLoader.loadClass(
                "com.android.server.clipboard.ClipboardService"
            )
            val clipboardClass = param.classLoader.loadClass(
                "com.android.server.clipboard.ClipboardService\$Clipboard"
            )
            val method = findMethod(
                clipboardServiceClass,
                "showAccessNotificationLocked",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                clipboardClass,
                Int::class.javaPrimitiveType
            )
            hookWithId(method, "clipboard_access_toast") { chain ->
                refreshSnapshotIfStale()
                val readingPackage = chain.args[0] as? String
                if (suppressEnabled && readingPackage != null && whitelist.contains(readingPackage)) {
                    logger.debug("Skipped clipboard access toast for $readingPackage")
                    null
                } else {
                    chain.proceed()
                }
            }
            logger.info("Hooked ClipboardService.showAccessNotificationLocked")
        } catch (t: Throwable) {
            logger.error("Failed to hook clipboard access notification", t)
        }
    }

    companion object {
        private const val SNAPSHOT_REFRESH_INTERVAL_MS = 5_000L
    }
}
