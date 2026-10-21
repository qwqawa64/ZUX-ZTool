package com.qimian233.ztool.hook.modules.desktoplauncher

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps desktop icon labels at the cell width instead of letting the host ellipsize them.
 *
 * The host's own two-line path only runs for all-apps and device search, and its breakpoint table
 * splits mixed names badly (`MT管理器` broke after `M`). This module keeps the original title and
 * re-splits it greedily at the measured width, preferring a break outside a Latin word.
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

    private val originalTitles = WeakHashMap<View, CharSequence>()

    private val appliedTexts = WeakHashMap<View, CharSequence>()

    private val logged = AtomicInteger(0)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        runCatching {
            val bubbleClass = classLoader.loadClass(BUBBLE_TEXT_VIEW_CLASS)
            val displayField = findField(bubbleClass, "mDisplay")
            val applyLabel = findMethod(
                bubbleClass, "applyLabel",
                classLoader.loadClass(ITEM_INFO_CLASS))
            val onMeasure = findMethod(
                bubbleClass, "onMeasure",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            hookWithId(applyLabel, "workspace_label_capture") { chain ->
                chain.proceed()
                runCatching {
                    val view = chain.thisObject as? View ?: return@runCatching
                    val info = chain.args[0]
                    val title = runCatching { info.javaClass.getField("title").get(info) }
                        .getOrNull() as? CharSequence
                    if (title != null) {
                        originalTitles[view] = title
                        appliedTexts.remove(view)
                    }
                }.onFailure { logger.error("WorkspaceLabelTwoLine: capture failed", it) }
                null
            }
            hookWithId(onMeasure, "workspace_label_two_line") { chain ->
                runCatching {
                    applyLabelAtWidth(
                        chain.thisObject as? View,
                        chain.args[0] as? Int ?: 0,
                        displayField
                    )
                }.onFailure { logger.error("WorkspaceLabelTwoLine: measure failed", it) }
                chain.proceed()
            }
            logger.info("WorkspaceLabelTwoLine installed")
        }.onFailure { logger.error("WorkspaceLabelTwoLine: hook not installed", it) }
    }

    /** Splits the label for the measured width and applies it before `super.onMeasure` runs. */
    private fun applyLabelAtWidth(view: View?, widthSpec: Int, displayField: Field) {
        if (view !is TextView) return
        if (!isWorkspaceIcon(displayField, view)) return
        if (!isPcMode(view.context)) return
        val original = originalTitles[view]
            ?: view.text?.takeIf { it.isNotEmpty() && '\n' !in it }
            ?: return
        val available = View.MeasureSpec.getSize(widthSpec) -
            view.compoundPaddingLeft - view.compoundPaddingRight
        if (available <= 0) return
        val split = splitToFit(original, view.paint, available.toFloat())
        if (TextUtils.equals(appliedTexts[view], split)) return
        appliedTexts[view] = split
        val twoLines = split !== original
        if (twoLines) {
            if (view.maxLines != 2) {
                view.setSingleLine(false)
                view.maxLines = 2
            }
        } else if (view.maxLines != 1) {
            view.setSingleLine(true)
        }
        if (!TextUtils.equals(view.text, split)) view.text = split
        if (logged.incrementAndGet() <= MAX_LOG_LINES) {
            logger.debug(
                "WorkspaceLabelTwoLine: '$original' -> '${split.toString().replace('\n', '/')}'" +
                    " at ${available}px"
            )
        }
    }

    /**
     * Greedy split at [maxWidth]; the second line is left to the host's end ellipsis.
     *
     * Returns [source] itself when it fits, so callers can compare by identity.
     */
    private fun splitToFit(source: CharSequence, paint: TextPaint, maxWidth: Float): CharSequence {
        if (paint.measureText(source, 0, source.length) <= maxWidth) return source
        var fit = 1
        var index = 1
        while (index <= source.length) {
            if (paint.measureText(source, 0, index) <= maxWidth) {
                fit = index
                index++
            } else {
                break
            }
        }
        var breakIndex = fit.coerceIn(1, source.length - 1)
        if (breakIndex < source.length &&
            isLatinWordChar(source[breakIndex]) && isLatinWordChar(source[breakIndex - 1])
        ) {
            var wordStart = breakIndex
            while (wordStart > 0 && isLatinWordChar(source[wordStart - 1])) wordStart--
            if (wordStart > 0) breakIndex = wordStart
        }
        while (breakIndex < source.length && source[breakIndex] == ' ') breakIndex++
        if (breakIndex <= 0 || breakIndex >= source.length) return source
        val first = source.subSequence(0, breakIndex).toString().trimEnd()
        val rest = source.subSequence(breakIndex, source.length).toString().trimStart()
        if (first.isEmpty() || rest.isEmpty()) return source
        return "$first\n$rest"
    }

    /** Latin letters/digits only, so CJK characters stay valid break points. */
    private fun isLatinWordChar(char: Char): Boolean =
        char.code < CJK_START && (char.isLetterOrDigit() || char == '_' || char == '-' || char == '.')

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
        const val ITEM_INFO_CLASS = "com.zui.desktoplauncher.model.data.ItemInfoWithIcon"
        const val DISPLAY_WORKSPACE = 0
        const val KEY_PC_MODE = "zui_pc_mode"
        const val CJK_START = 0x2E80
        const val CACHE_TTL_MS = 2000L
        const val MAX_LOG_LINES = 6
    }
}
