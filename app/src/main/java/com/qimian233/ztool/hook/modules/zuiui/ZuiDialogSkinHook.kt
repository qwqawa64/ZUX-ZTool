package com.qimian233.ztool.hook.modules.zuiui

import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import kotlin.math.pow

/**
 * Restyles the button row of ZUI dialogs in every scoped app.
 *
 * Hooks `android.app.Dialog.show()` instead of a minified controller routine, and
 * recognises ZUI dialogs by their `zui.app.*` class hierarchy. The two visual
 * changes are independent switches; the pills also honour a radius and colour
 * preference. See `docs/research/zui_dialog_button_style.md`.
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
    override fun isEnabled(): Boolean = readSettings().let { it.hideDivider || it.filledButtons }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        // One process reports several loaded packages; the skin is a per-process concern.
        if (installed) return
        val settings = readSettings()
        if (!settings.hideDivider && !settings.filledButtons) return

        val show = runCatching { Dialog::class.java.getDeclaredMethod("show") }.getOrNull() ?: return
        hookWithId(show, "zui_dialog_skin") { chain ->
            val result = chain.proceed()
            val dialog = chain.thisObject as? Dialog
            if (dialog != null && isZuiDialog(dialog)) {
                runCatching { skin(dialog, settings) }
                    .onFailure { logger.warn("ZUI dialog skin skipped: " + it.message) }
            }
            result
        }
        installed = true
        logger.info(
            "ZUI dialog skin installed: divider=" + settings.hideDivider +
                " filled=" + settings.filledButtons +
                " radius=" + settings.radiusDp +
                " followSystem=" + settings.followSystemColors
        )
    }

    /** Reads every knob once per process; any remote-preferences failure disables the module. */
    private fun readSettings(): Settings = runCatching {
        val prefs = xposed.getRemotePreferences(PREFS_NAME)
        Settings(
            hideDivider = prefs.getBoolean(KEY_HIDE_DIVIDER.name, KEY_HIDE_DIVIDER.default),
            filledButtons = prefs.getBoolean(KEY_FILLED_BUTTONS.name, KEY_FILLED_BUTTONS.default),
            radiusDp = prefs.getInt(KEY_RADIUS.name, KEY_RADIUS.default).coerceIn(RADIUS_MIN_DP, RADIUS_MAX_DP),
            followSystemColors = prefs.getBoolean(KEY_FOLLOW_SYSTEM.name, KEY_FOLLOW_SYSTEM.default),
            positiveColor = prefs.getInt(KEY_POSITIVE_COLOR.name, KEY_POSITIVE_COLOR.default),
            negativeColor = prefs.getInt(KEY_NEGATIVE_COLOR.name, KEY_NEGATIVE_COLOR.default)
        )
    }.getOrDefault(defaultSettings())

    private fun defaultSettings(): Settings = Settings(
        hideDivider = false,
        filledButtons = false,
        radiusDp = KEY_RADIUS.default,
        followSystemColors = KEY_FOLLOW_SYSTEM.default,
        positiveColor = KEY_POSITIVE_COLOR.default,
        negativeColor = KEY_NEGATIVE_COLOR.default
    )

    /** ZUI dialogs all extend `zui.app.a`, which in turn extends the platform [Dialog]. */
    private fun isZuiDialog(dialog: Dialog): Boolean {
        var type: Class<*>? = dialog.javaClass
        while (type != null && type != Dialog::class.java) {
            if (type.name.startsWith(ZUI_DIALOG_PACKAGE)) return true
            type = type.superclass
        }
        return false
    }

    private fun skin(dialog: Dialog, settings: Settings) {
        val window = dialog.window ?: return
        // android.R.id.button1 is public API, so the panel is reached without any app-local id.
        val primary = window.findViewById<View>(android.R.id.button1) as? Button ?: return
        val panel = primary.parent as? ViewGroup ?: return
        if (settings.hideDivider) hideMessageSeparator(panel)
        if (settings.filledButtons) applyFilledButtons(panel, settings)
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

    private fun applyFilledButtons(panel: ViewGroup, settings: Settings) {
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

        val positive = opaque(
            if (settings.followSystemColors) {
                themedColor(resources, theme, packageName, SYSTEM_POSITIVE_COLOR, accentColor(theme))
            } else {
                settings.positiveColor
            }
        )
        val negative = opaque(
            if (settings.followSystemColors) {
                themedColor(
                    resources, theme, packageName, SYSTEM_NEGATIVE_COLOR,
                    if (isNight(resources)) NEUTRAL_OVERLAY_DARK else NEUTRAL_OVERLAY_LIGHT
                )
            } else {
                settings.negativeColor
            }
        )
        val radius = settings.radiusDp * density

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
            val fill = if (child.id == android.R.id.button1) positive else negative
            child.background = pillBackground(fill, radius)
            child.setTextColor(pillTextColors(fill))
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

    /** One pill per state: the host drawables cannot carry a custom radius or fill. */
    private fun pillBackground(fill: Int, radius: Float): Drawable = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), pill(withAlpha(fill, DISABLED_FILL_ALPHA), radius))
        addState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
            pill(feedbackColor(fill, PRESSED_FEEDBACK), radius)
        )
        addState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            pill(feedbackColor(fill, FOCUSED_FEEDBACK), radius)
        )
        addState(intArrayOf(), pill(fill, radius))
    }

    private fun pill(fill: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radius
    }

    /** White or near-black, whichever reads better on the pill; disabled keeps the same hue. */
    private fun pillTextColors(fill: Int): ColorStateList {
        val base = if (relativeLuminance(fill) > LIGHT_FILL_LUMINANCE) DARK_TEXT_COLOR else Color.WHITE
        return ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(withAlpha(base, DISABLED_TEXT_ALPHA), base)
        )
    }

    /** Press feedback moves away from the fill's own lightness so it never disappears. */
    private fun feedbackColor(fill: Int, amount: Float): Int {
        val target = if (relativeLuminance(fill) > LIGHT_FILL_LUMINANCE) Color.BLACK else Color.WHITE
        return blend(fill, target, amount)
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun mix(a: Int, b: Int): Int = (a + (b - a) * amount).toInt().coerceIn(0, 255)
        return Color.argb(
            Color.alpha(from),
            mix(Color.red(from), Color.red(to)),
            mix(Color.green(from), Color.green(to)),
            mix(Color.blue(from), Color.blue(to))
        )
    }

    /** WCAG relative luminance; the 0.45 cut-off matches when ZUI's own light text should flip. */
    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val ratio = value / 255.0
            return if (ratio <= 0.03928) ratio / 12.92 else ((ratio + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun themedColor(
        resources: Resources,
        theme: Resources.Theme,
        packageName: String,
        name: String,
        fallback: Int
    ): Int {
        val id = resources.getIdentifier(name, "color", packageName)
        if (id == 0) return fallback
        return runCatching { resources.getColor(id, theme) }.getOrDefault(fallback)
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

    private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (Color.alpha(color) * alpha).toInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private data class Settings(
        val hideDivider: Boolean,
        val filledButtons: Boolean,
        val radiusDp: Int,
        val followSystemColors: Boolean,
        val positiveColor: Int,
        val negativeColor: Int
    )

    private companion object {
        const val ZUI_DIALOG_PACKAGE = "zui.app."

        const val SYSTEM_POSITIVE_COLOR = "system_primary_color_zui"
        const val SYSTEM_NEGATIVE_COLOR = "system_button_normal_zui"
        const val BODY_COLOR = "dialog_background_zui"

        /** Sub-feature switch for the horizontal separator above the button row. */
        val KEY_HIDE_DIVIDER = PreferenceKeys.ZUI_DIALOG_HIDE_DIVIDER

        /** The existing switch, now scoped to the filled button containers. */
        val KEY_FILLED_BUTTONS = PreferenceKeys.ZUI_DIALOG_SKIN

        val KEY_FOLLOW_SYSTEM = PreferenceKeys.ZUI_DIALOG_FOLLOW_SYSTEM_COLORS
        val KEY_RADIUS = PreferenceKeys.ZUI_DIALOG_BUTTON_RADIUS
        val KEY_POSITIVE_COLOR = PreferenceKeys.ZUI_DIALOG_POSITIVE_COLOR
        val KEY_NEGATIVE_COLOR = PreferenceKeys.ZUI_DIALOG_NEGATIVE_COLOR

        /** Clamp shared with the settings slider. */
        const val RADIUS_MIN_DP = 0
        const val RADIUS_MAX_DP = 32

        /** Screen margins and the gap between two pills, in dp. */
        const val PANEL_SIDE_DP = 24f
        const val PANEL_VERTICAL_DP = 12f
        const val BUTTON_GAP_DP = 12f
        const val BUTTON_PADDING_DP = 12f

        /** Mirrors the host's button_big_min_height_zui, used only when that dimen is missing. */
        const val BUTTON_MIN_HEIGHT_DP = 48f
        const val RADIUS_FALLBACK_DP = 20f

        /** 12% white / 8% black: the neutral pill when follow-system cannot resolve a colour. */
        const val NEUTRAL_OVERLAY_DARK = 0x1FFFFFFF
        const val NEUTRAL_OVERLAY_LIGHT = 0x14000000

        const val ACCENT_FALLBACK = 0xFF1979FF.toInt()
        const val DARK_TEXT_COLOR = 0xFF1F1F1F.toInt()

        /** Relative luminance above which a fill counts as light and gets dark text. */
        const val LIGHT_FILL_LUMINANCE = 0.45

        const val PRESSED_FEEDBACK = 0.12f
        const val FOCUSED_FEEDBACK = 0.06f
        const val DISABLED_FILL_ALPHA = 0.4f
        const val DISABLED_TEXT_ALPHA = 0.45f
    }
}
