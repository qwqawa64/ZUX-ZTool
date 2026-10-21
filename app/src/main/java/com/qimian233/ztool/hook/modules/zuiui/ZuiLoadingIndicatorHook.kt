package com.qimian233.ztool.hook.modules.zuiui

import android.app.Dialog
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.zuiui.LoadingIndicatorStyle
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.modules.zuiui.indicator.CubeGridLoadingIndicator
import com.qimian233.ztool.hook.modules.zuiui.indicator.LoadingIndicatorDrawable
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Replaces the spinner inside `zui.app.ProgressDialogX` with a drawn animation.
 *
 * Hooks `Dialog.setContentView` because that is both the `onCreate` layout and the
 * layout `ResetAppsHelper` swaps in after `show()`; the dialog class is matched by
 * name, the view by type. See docs/research/zui_progress_dialog_x_indicator.md §3.
 */
class ZuiLoadingIndicatorHook : AppHookModule() {

    @Volatile
    private var installed = false

    /** Read once per process; a hot reload reinstalls the hook and re-reads it. */
    private var settings: Settings? = null

    override fun getModuleName(): String = PreferenceKeys.ZUI_LOADING_INDICATOR.name

    /** Seed list for readers only; the real gate is the ZUI dialog class. */
    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.SETTINGS.packageName,
        ScopeKeys.SYSTEM_UI.packageName,
        ScopeKeys.LAUNCHER.packageName,
        ScopeKeys.ZUI_SAFE_CENTER.packageName,
        ScopeKeys.PACKAGE_INSTALLER.packageName
    )

    /** Membership means "the user scoped this app". */
    override fun supportsPackage(packageName: String?): Boolean = true

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        // One process reports several loaded packages; the replacement is per-process.
        if (installed) return
        val loaded = readSettings() ?: return
        settings = loaded

        val dialogClass = Dialog::class.java
        val forLayout = findSetContentView(dialogClass, Int::class.javaPrimitiveType)
        val forView = findSetContentView(dialogClass, View::class.java)
        if (forLayout == null && forView == null) return

        if (forLayout != null) {
            hookWithId(forLayout, HOOK_ID_CONTENT_LAYOUT) { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Dialog)?.let(::safeRestyle)
                result
            }
        }
        if (forView != null) {
            hookWithId(forView, HOOK_ID_CONTENT_VIEW) { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Dialog)?.let(::safeRestyle)
                result
            }
        }
        installed = true
        logger.info("loading indicator installed: style=" + loaded.style.prefValue)
    }

    /**
     * `getDeclaredMethod` covers the AOSP shape; ROM forks that move the body into a
     * superclass are covered by `getMethod`, which returns the inherited executable.
     */
    private fun findSetContentView(declaring: Class<*>, param: Class<*>?): Method? {
        val argument = param ?: return null
        return runCatching { declaring.getDeclaredMethod("setContentView", argument) }.getOrNull()
            ?: runCatching { declaring.getMethod("setContentView", argument) }.getOrNull()
    }

    /** A missing switch or an unreadable preference file leaves the stock spinner alone. */
    private fun readSettings(): Settings? = runCatching {
        val prefs = xposed.getRemotePreferences(PREFS_NAME)
        val enabled = prefs.getBoolean(KEY_ENABLED.name, KEY_ENABLED.default)
        val style = LoadingIndicatorStyle.fromPref(
            prefs.getString(KEY_STYLE.name, KEY_STYLE.default)
        )
        val settings = Settings(
            style = style,
            followSystemColors = prefs.getBoolean(
                KEY_FOLLOW_SYSTEM_COLORS.name, KEY_FOLLOW_SYSTEM_COLORS.default
            ),
            color = prefs.getInt(KEY_COLOR.name, KEY_COLOR.default)
        )
        if (enabled) settings else null
    }.getOrNull()

    private fun safeRestyle(dialog: Dialog) {
        val current = settings ?: return
        runCatching { restyle(dialog, current) }
            .onFailure { logger.warn("loading indicator skipped: " + it.message) }
    }

    private fun restyle(dialog: Dialog, settings: Settings) {
        if (!isProgressDialogX(dialog)) return
        val decor = dialog.window?.decorView ?: return
        val bars = ArrayList<ProgressBar>(1)
        collectIndeterminateBars(decor, bars)
        if (bars.isEmpty()) return
        for (bar in bars) {
            val current = bar.indeterminateDrawable
            if (current is LoadingIndicatorDrawable) {
                // Re-showing a cached dialog keeps the drawable but loses its ticker.
                current.start()
                continue
            }
            val indicator = createIndicator(settings.style, indicatorColor(bar.context, settings))
            bar.setIndeterminateDrawable(indicator)
            indicator.start()
        }
    }

    /**
     * `ProgressDialogX` is public and unminified, unlike its `Dialog` base. Subclasses
     * are matched through the superclass chain.
     */
    private fun isProgressDialogX(dialog: Dialog): Boolean {
        var type: Class<*>? = dialog.javaClass
        while (type != null && type != Dialog::class.java) {
            if (type.name == PROGRESS_DIALOG_X) return true
            type = type.superclass
        }
        return false
    }

    /** The ZUI layouts put the bar inside a `LinearLayout`; a bare child walk misses it. */
    private fun collectIndeterminateBars(view: View, out: MutableList<ProgressBar>) {
        if (view is ProgressBar) {
            if (view.isIndeterminate) out += view
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectIndeterminateBars(view.getChildAt(index), out)
        }
    }

    /** Adding a style means adding an enum entry and a branch here; `when` stays exhaustive. */
    private fun createIndicator(style: LoadingIndicatorStyle, color: Int): LoadingIndicatorDrawable =
        when (style) {
            LoadingIndicatorStyle.CUBE_GRID -> CubeGridLoadingIndicator(color)
        }

    /** Styles with a built-in palette ignore the colour they are handed. */
    private fun indicatorColor(context: Context, settings: Settings): Int {
        if (!settings.followSystemColors) return settings.color or 0xFF000000.toInt()
        val themed = themeColor(context, android.R.attr.colorAccent)
            ?: themeColor(context, android.R.attr.textColorPrimary)
            ?: settings.color
        return themed or 0xFF000000.toInt()
    }

    private fun themeColor(context: Context, attr: Int): Int? {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return null
        if (value.resourceId != 0) {
            return runCatching { context.getColor(value.resourceId) }.getOrNull()
        }
        val isColor = value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            value.type <= TypedValue.TYPE_LAST_COLOR_INT
        return if (isColor) value.data else null
    }

    private data class Settings(
        val style: LoadingIndicatorStyle,
        val followSystemColors: Boolean,
        val color: Int
    )

    private companion object {
        const val HOOK_ID_CONTENT_LAYOUT = "zui_loading_indicator_content_layout"
        const val HOOK_ID_CONTENT_VIEW = "zui_loading_indicator_content_view"
        const val PROGRESS_DIALOG_X = "zui.app.ProgressDialogX"

        val KEY_ENABLED = PreferenceKeys.ZUI_LOADING_INDICATOR
        val KEY_STYLE = PreferenceKeys.ZUI_LOADING_INDICATOR_STYLE
        val KEY_FOLLOW_SYSTEM_COLORS = PreferenceKeys.ZUI_LOADING_INDICATOR_FOLLOW_SYSTEM_COLORS
        val KEY_COLOR = PreferenceKeys.ZUI_LOADING_INDICATOR_COLOR
    }
}
