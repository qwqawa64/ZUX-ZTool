package com.qimian233.ztool.hook.modules.zuiui

import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Restyles the button row of ZUI dialogs in every scoped app.
 *
 * Hooks `android.app.Dialog.show()` instead of a minified controller routine, and
 * recognises ZUI dialogs by their `zui.app.*` class hierarchy. The two visual
 * changes are independent switches. See `docs/research/zui_dialog_button_style.md`.
 */
class ZuiDialogSkinHook : AppHookModule() {

    @Volatile
    private var installed = false

    override fun getModuleName(): String = PreferenceKeys.ZUI_DIALOG_SKIN.name

    /** Seed list for readers only; see [supportsPackage]. */
    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.SETTINGS.packageName,
        ScopeKeys.SYSTEM_UI.packageName,
        ScopeKeys.LAUNCHER.packageName,
        ScopeKeys.ZUI_SAFE_CENTER.packageName,
        ScopeKeys.PACKAGE_INSTALLER.packageName
    )

    /** Membership means "the user scoped this app"; the real gate is the ZUI dialog class. */
    override fun supportsPackage(packageName: String?): Boolean = true

    /** Either visual change keeps the module alive; each one has its own switch. */
    override fun isEnabled(): Boolean = readSwitches().let { it.hideDivider || it.filledButtons }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        // One process reports several loaded packages; the skin is a per-process concern.
        if (installed) return
        val switches = readSwitches()
        if (!switches.hideDivider && !switches.filledButtons) return

        val show = runCatching { Dialog::class.java.getDeclaredMethod("show") }.getOrNull() ?: return
        hookWithId(show, "zui_dialog_skin") { chain ->
            val result = chain.proceed()
            val dialog = chain.thisObject as? Dialog
            if (dialog != null && isZuiDialog(dialog)) {
                runCatching { skin(dialog, switches) }
                    .onFailure { logger.warn("ZUI dialog skin skipped: " + it.message) }
            }
            result
        }
        installed = true
        logger.info(
            "ZUI dialog skin installed: divider=" + switches.hideDivider +
                " filled=" + switches.filledButtons
        )
    }

    /** Reads both switches once per process; any remote-preferences failure disables the module. */
    private fun readSwitches(): Switches = runCatching {
        val prefs = xposed.getRemotePreferences(PREFS_NAME)
        Switches(
            hideDivider = prefs.getBoolean(KEY_HIDE_DIVIDER.name, KEY_HIDE_DIVIDER.default),
            filledButtons = prefs.getBoolean(KEY_FILLED_BUTTONS.name, KEY_FILLED_BUTTONS.default)
        )
    }.getOrDefault(Switches(false, false))

    /** ZUI dialogs all extend `zui.app.a`, which in turn extends the platform [Dialog]. */
    private fun isZuiDialog(dialog: Dialog): Boolean {
        var type: Class<*>? = dialog.javaClass
        while (type != null && type != Dialog::class.java) {
            if (type.name.startsWith(ZUI_DIALOG_PACKAGE)) return true
            type = type.superclass
        }
        return false
    }

    private fun skin(dialog: Dialog, switches: Switches) {
        val window = dialog.window ?: return
        // android.R.id.button1 is public API, so the panel is reached without any app-local id.
        val primary = window.findViewById<View>(android.R.id.button1) as? Button ?: return
        val panel = primary.parent as? ViewGroup ?: return
        if (switches.hideDivider) hideMessageSeparator(panel)
        if (switches.filledButtons) applyFilledButtons(panel)
    }

    /**
     * Stock layouts place a bare View between the content and the button panel;
     * anything else at that position is not the separator and is left alone.
     */
    private fun hideMessageSeparator(panel: ViewGroup) {
        val parent = panel.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(panel)
        if (index <= 0) return
        val separator = parent.getChildAt(index - 1)
        if (separator.javaClass === View::class.java) separator.visibility = View.GONE
    }

    private fun applyFilledButtons(panel: ViewGroup) {
        val resources = panel.resources
        val theme = panel.context.theme
        val packageName = panel.context.packageName
        val density = resources.displayMetrics.density

        panel.setPadding(
            (PANEL_SIDE_DP * density).toInt(),
            (PANEL_VERTICAL_DP * density).toInt(),
            (PANEL_SIDE_DP * density).toInt(),
            (PANEL_VERTICAL_DP * density).toInt()
        )
        panelBackground(resources, theme, packageName)?.let { panel.background = it }

        val coloredFill = accentColor(theme)
        val neutralFill = if (isNight(resources)) NEUTRAL_OVERLAY_DARK else NEUTRAL_OVERLAY_LIGHT
        val textPadding = (BUTTON_PADDING_DP * density).toInt()
        val minHeight = dimension(resources, packageName, "button_big_min_height_zui", BUTTON_MIN_HEIGHT_DP).toInt()

        val buttons = ArrayList<Button>()
        for (index in 0 until panel.childCount) {
            val child = panel.getChildAt(index)
            if (child !is Button) {
                // The vertical divider between buttons; separate pills replace it.
                child.visibility = View.GONE
                continue
            }
            buttons += child
            val primary = child.id == android.R.id.button1
            child.background = buttonBackground(
                resources,
                theme,
                packageName,
                if (primary) COLORED_BACKGROUND else NEUTRAL_BACKGROUND,
                if (primary) coloredFill else neutralFill
            )
            val textColors = buttonTextColors(
                resources,
                theme,
                packageName,
                if (primary) COLORED_TEXT else NEUTRAL_TEXT
            ) ?: if (primary) ColorStateList.valueOf(Color.WHITE) else null
            if (textColors != null) child.setTextColor(textColors)
            child.setPadding(textPadding, child.paddingTop, textPadding, child.paddingBottom)
            child.minimumHeight = minHeight
        }
        applyGaps(panel, buttons.filter { it.visibility != View.GONE })
    }

    /** The stock bottom rounding lives on the button background, so the panel takes it over. */
    private fun panelBackground(
        resources: Resources,
        theme: Resources.Theme,
        packageName: String
    ): Drawable? {
        val id = resources.getIdentifier(BODY_COLOR, "color", packageName)
        if (id == 0) return null
        val body = runCatching { resources.getColor(id, theme) }.getOrNull() ?: return null
        val radius = dimension(resources, packageName, "dialog_radius_zui", RADIUS_FALLBACK_DP)
        return GradientDrawable().apply {
            setColor(body)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }
    }

    /** Reuses the host's own filled-button drawable; a flat pill is the fallback. */
    private fun buttonBackground(
        resources: Resources,
        theme: Resources.Theme,
        packageName: String,
        name: String,
        fallbackFill: Int
    ): Drawable {
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id != 0) {
            runCatching { resources.getDrawable(id, theme) }.getOrNull()?.let { return it }
        }
        return GradientDrawable().apply {
            setColor(fallbackFill)
            cornerRadius = dimension(resources, packageName, "dialog_radius_zui", RADIUS_FALLBACK_DP)
        }
    }

    private fun buttonTextColors(
        resources: Resources,
        theme: Resources.Theme,
        packageName: String,
        name: String
    ): ColorStateList? {
        val id = resources.getIdentifier(name, "color", packageName)
        if (id == 0) return null
        return runCatching { resources.getColorStateList(id, theme) }.getOrNull()
    }

    private fun applyGaps(panel: ViewGroup, buttons: List<Button>) {
        if (buttons.isEmpty()) return
        val gap = (BUTTON_GAP_DP * panel.resources.displayMetrics.density).toInt()
        val vertical = panel is LinearLayout && panel.orientation == LinearLayout.VERTICAL
        for ((index, button) in buttons.withIndex()) {
            val params = button.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            val before = if (index == 0) 0 else gap / 2
            val after = if (index == buttons.lastIndex) 0 else gap / 2
            if (vertical) {
                params.topMargin = before
                params.bottomMargin = after
            } else {
                params.leftMargin = before
                params.rightMargin = after
            }
            button.layoutParams = params
        }
    }

    private fun accentColor(theme: Resources.Theme): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.colorAccent, value, true)) {
            value.data
        } else {
            ACCENT_FALLBACK
        }
    }

    private fun dimension(
        resources: Resources,
        packageName: String,
        name: String,
        fallbackDp: Float
    ): Float {
        val fallback = fallbackDp * resources.displayMetrics.density
        val id = resources.getIdentifier(name, "dimen", packageName)
        if (id == 0) return fallback
        return runCatching { resources.getDimensionPixelSize(id).toFloat() }.getOrDefault(fallback)
    }

    private fun isNight(resources: Resources): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private data class Switches(val hideDivider: Boolean, val filledButtons: Boolean)

    private companion object {
        const val ZUI_DIALOG_PACKAGE = "zui.app."

        const val COLORED_BACKGROUND = "button_colored_big_background_zui"
        const val NEUTRAL_BACKGROUND = "button_normal_big_background_zui"
        const val COLORED_TEXT = "button_colored_text_color_zui"
        const val NEUTRAL_TEXT = "button_normal_text_color_zui"
        const val BODY_COLOR = "dialog_background_zui"

        /** Sub-feature switch for the horizontal separator above the button row. */
        val KEY_HIDE_DIVIDER = PreferenceKeys.ZUI_DIALOG_HIDE_DIVIDER

        /** The existing switch, now scoped to the filled button containers. */
        val KEY_FILLED_BUTTONS = PreferenceKeys.ZUI_DIALOG_SKIN

        /** Screen margins and the gap between two pills, in dp. */
        const val PANEL_SIDE_DP = 24f
        const val PANEL_VERTICAL_DP = 12f
        const val BUTTON_GAP_DP = 12f
        const val BUTTON_PADDING_DP = 12f

        /** Mirrors the host's button_big_min_height_zui, used only when that dimen is missing. */
        const val BUTTON_MIN_HEIGHT_DP = 48f
        const val RADIUS_FALLBACK_DP = 20f

        /** 12% white / 8% black: the neutral pill when the host drawable is unreachable. */
        const val NEUTRAL_OVERLAY_DARK = 0x1FFFFFFF
        const val NEUTRAL_OVERLAY_LIGHT = 0x14000000

        const val ACCENT_FALLBACK = 0xFF1979FF.toInt()
    }
}
