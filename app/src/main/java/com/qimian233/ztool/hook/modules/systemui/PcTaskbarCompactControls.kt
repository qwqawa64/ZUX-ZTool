package com.qimian233.ztool.hook.modules.systemui

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shrinks the PC taskbar's right-hand control cluster in SystemUI.
 *
 * The PC navigation bar is inflated from the `config_navBarLayout_work2` string, whose second group
 * assigns fixed dp widths to the search / wifi+volume / notification / battery / clock buttons. On
 * a 2000 px bar that group takes ~1300 px and leaves `tasks[F]` (the running-app icons) ~130 px.
 * This module rewrites those widths in the inflater string, per button name, so every sw bucket
 * keeps its own layout and only the values shrink.
 *
 * Gated by [PreferenceKeys.WORK_MODE_PORTRAIT]; see docs/research/zui_work_mode_desktop_hook_survey.md
 */
class PcTaskbarCompactControls : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.WORK_MODE_PORTRAIT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    private val logged = AtomicInteger(0)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        runCatching {
            val inflaterClass = classLoader.loadClass(INFLATER_CLASS)
            val getDefaultLayout = findMethod(inflaterClass, "getDefaultLayout")
            hookWithId(getDefaultLayout, "pc_taskbar_compact_controls") { chain ->
                val original = chain.proceed() as? String
                if (original == null || !original.contains(PC_LAYOUT_MARKER)) {
                    original
                } else {
                    val compacted = compact(original)
                    if (compacted != original && logged.incrementAndGet() <= MAX_LOG_LINES) {
                        logger.info("PcTaskbarCompactControls: $compacted")
                    }
                    compacted
                }
            }
            logger.info("PcTaskbarCompactControls installed")
        }.onFailure { logger.error("PcTaskbarCompactControls: hook not installed", it) }
    }

    /** Rewrites `name[NNd]` widths downward; names absent from a bucket are left alone. */
    private fun compact(layout: String): String {
        var out = layout
        COMPACT_WIDTHS.forEach { (name, maxWidth) ->
            val pattern = Regex(Regex.escape(name) + "\\[(\\d+)")
            out = pattern.replace(out) { match ->
                val original = match.groupValues[1].toIntOrNull()
                if (original == null) {
                    match.value
                } else {
                    "$name[${minOf(original, maxWidth)}"
                }
            }
        }
        return out
    }

    private companion object {
        const val INFLATER_CLASS = "com.android.systemui.navigationbar.views.NavigationBarInflaterView"
        const val PC_LAYOUT_MARKER = "tasks[F]"
        const val MAX_LOG_LINES = 3

        /** Icon-sized touch targets (~44 dp) instead of the stock 50-150 dp blocks. */
        val COMPACT_WIDTHS = mapOf(
            "line" to 10,
            "ime_switcher" to 40,
            "search" to 40,
            "wifivolume_pc" to 96,
            "notification_pc" to 40,
            "battery_pc" to 48,
            "fm_clock_pc" to 100
        )
    }
}
