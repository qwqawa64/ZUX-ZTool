package com.qimian233.ztool.hook.modules.desktoplauncher

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lets desktop icon labels wrap instead of ellipsizing in PC mode.
 *
 * `BubbleTextView.onMeasure` only re-splits a title when `shouldUseTwoLine()` is true, and the host
 * enables that just for the all-apps list and device search. Returning true for workspace icons
 * lets the measured width decide: portrait's ~55 dp cells wrap to two lines, landscape keeps one.
 * Keeping it true in both orientations also avoids a stale two-line label after rotating back.
 *
 * Gated by [PreferenceKeys.WORK_MODE_PORTRAIT]; see docs/research/zui_work_mode_desktop_hook_survey.md
 */
class WorkspaceLabelTwoLine : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.WORK_MODE_PORTRAIT.name

    override fun getTargetPackages(): Array<String> =
        arrayOf(ScopeKeys.DESKTOP_LAUNCHER.packageName)

    @Volatile
    private var pcModeCheckedAt = 0L

    @Volatile
    private var pcMode = false

    private val logged = AtomicInteger(0)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        runCatching {
            val bubbleClass = classLoader.loadClass(BUBBLE_TEXT_VIEW_CLASS)
            val shouldUseTwoLine = findMethod(bubbleClass, "shouldUseTwoLine")
            val displayField = findField(bubbleClass, "mDisplay")
            hookWithId(shouldUseTwoLine, "workspace_label_two_line") { chain ->
                val original = chain.proceed()
                val twoLine = runCatching {
                    if (original == true) return@runCatching false
                    val view = chain.thisObject as? View ?: return@runCatching false
                    isWorkspaceIcon(displayField, view) && isPcMode(view.context)
                }.getOrDefault(false)
                if (!twoLine) {
                    original
                } else {
                    if (logged.incrementAndGet() <= MAX_LOG_LINES) {
                        logger.debug("WorkspaceLabelTwoLine: two-line label enabled in portrait")
                    }
                    true
                }
            }
            logger.info("WorkspaceLabelTwoLine installed")
        }.onFailure { logger.error("WorkspaceLabelTwoLine: hook not installed", it) }
    }

    /** The host uses `mDisplay == 1` for all apps and `6` for device search; desktop icons are 0. */
    private fun isWorkspaceIcon(displayField: Field, view: View): Boolean =
        runCatching { displayField.getInt(view) == DISPLAY_WORKSPACE }.getOrDefault(false)

    private fun isPcMode(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - pcModeCheckedAt > CACHE_TTL_MS) {
            pcMode = runCatching {
                Settings.System.getInt(context.contentResolver, KEY_PC_MODE, 0) == 1
            }.getOrDefault(false)
            pcModeCheckedAt = now
        }
        return pcMode
    }

    private companion object {
        const val BUBBLE_TEXT_VIEW_CLASS = "com.zui.desktoplauncher.BubbleTextView"
        const val DISPLAY_WORKSPACE = 0
        const val KEY_PC_MODE = "zui_pc_mode"
        const val CACHE_TTL_MS = 2000L
        const val MAX_LOG_LINES = 3
    }
}
