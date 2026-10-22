package com.qimian233.ztool.hook.modules.zuiui

import android.content.Context
import android.graphics.Color
import android.widget.Switch
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.zuiui.SwitchMark
import com.qimian233.ztool.data.zuiui.SwitchStyle
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.modules.zuiui.switch.GlyphThumbDrawable
import com.qimian233.ztool.hook.modules.zuiui.switch.M3SwitchDrawable
import com.qimian233.ztool.hook.modules.zuiui.switch.SwitchFallbackColors
import com.qimian233.ztool.hook.modules.zuiui.switch.SwitchThemeColors
import com.qimian233.ztool.hook.modules.zuiui.switch.TransparentThumbDrawable
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import kotlin.math.roundToInt

/**
 * Restyles every `android.widget.Switch` of the host, including `zui.widget.Switch`, plain
 * Material switches and the AOSP Wi-Fi detail switch: the indicator style wraps the thumb,
 * the M3 style replaces the track and forces a 52dp box.
 *
 * Anchor and geometry: docs/research/zui_switch_style.md.
 */
class ZuiSwitchStyleHook : AppHookModule() {

    @Volatile
    private var installed = false

    /** Read once per process; a hot reload reinstalls the hook and re-reads it. */
    private var settings: Settings? = null

    override fun getModuleName(): String = PreferenceKeys.ZUI_SWITCH_STYLE.name

    /** Seed list for readers only; the real gate is the platform switch class. */
    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.SETTINGS.packageName,
        ScopeKeys.SYSTEM_UI.packageName,
        ScopeKeys.LAUNCHER.packageName,
        ScopeKeys.DESKTOP_LAUNCHER.packageName
    )

    /** Membership means "the user scoped this app". */
    override fun supportsPackage(packageName: String?): Boolean = true

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (installed) return
        val loaded = readSettings() ?: return
        settings = loaded

        val constructors = Switch::class.java.declaredConstructors
        if (constructors.isEmpty()) return

        constructors.forEachIndexed { index, constructor ->
            hookWithId(constructor, "$HOOK_ID_PREFIX$index") { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Switch)?.let(::safeRestyle)
                result
            }
        }
        installed = true
        logger.info(
            "switch style installed: " + loaded.style.prefValue + "/" + loaded.mark.prefValue +
                " follow=" + loaded.followSystemColors
        )
    }

    /** An unreadable preference file leaves the stock switch alone. */
    private fun readSettings(): Settings? = runCatching {
        val prefs = xposed.getRemotePreferences(PREFS_NAME)
        if (!prefs.getBoolean(KEY_ENABLED.name, KEY_ENABLED.default)) return@runCatching null
        Settings(
            style = SwitchStyle.fromPref(prefs.getString(KEY_STYLE.name, KEY_STYLE.default)),
            mark = SwitchMark.fromPref(prefs.getString(KEY_MARK.name, KEY_MARK.default)),
            followSystemColors = prefs.getBoolean(
                KEY_FOLLOW_SYSTEM.name, KEY_FOLLOW_SYSTEM.default
            ),
            trackColor = prefs.getInt(KEY_TRACK_COLOR.name, KEY_TRACK_COLOR.default)
        )
    }.getOrNull()

    private fun safeRestyle(target: Switch) {
        val current = settings ?: return
        runCatching { restyle(target, current) }
            .onFailure { logger.warn("switch style skipped: " + it.message) }
    }

    private fun restyle(target: Switch, settings: Settings) {
        when (settings.style) {
            SwitchStyle.INDICATOR -> applyIndicator(target, settings.mark)
            SwitchStyle.M3 -> applyM3(target, settings)
        }
    }

    /** Keeping the native track means the stock slide animation and nine-patch stay intact. */
    private fun applyIndicator(target: Switch, mark: SwitchMark) {
        if (mark == SwitchMark.NONE) return
        val host = target.thumbDrawable ?: return
        if (host is GlyphThumbDrawable) return
        val context = target.context
        val accent = systemSwitchColors(context).on
        target.setThumbDrawable(
            GlyphThumbDrawable(
                host = host,
                mark = mark,
                checkColor = accent,
                crossColor = SwitchFallbackColors.CROSS,
                initiallyChecked = target.isChecked,
                initiallyEnabled = target.isEnabled
            )
        )
    }

    /** The track drawable paints the whole control, so the native thumb becomes invisible. */
    private fun applyM3(target: Switch, settings: Settings) {
        if (target.trackDrawable is M3SwitchDrawable) return
        val context = target.context
        val density = context.resources.displayMetrics.density
        val system = systemSwitchColors(context)
        val trackOn = if (settings.followSystemColors) system.on else settings.trackColor

        target.setShowText(false)
        target.setSplitTrack(false)
        target.setSwitchMinWidth((M3_WIDTH_DP * density).roundToInt())
        target.setThumbDrawable(TransparentThumbDrawable((M3_THUMB_DP * density).roundToInt()))
        target.setTrackDrawable(
            M3SwitchDrawable(
                mark = settings.mark,
                trackOn = trackOn,
                trackOff = system.off,
                outline = system.outline,
                thumbOn = system.thumb,
                thumbOff = system.thumb,
                iconOn = trackOn,
                iconOff = contrastIcon(system.thumb),
                initiallyChecked = target.isChecked,
                intrinsicHeightPx = (M3_HEIGHT_DP * density).roundToInt()
            )
        )
    }

    /**
     * Colours of the stock ZUI switch, so the drawn track follows dynamic colour and the
     * day/night theme instead of a fixed Material 3 baseline palette.
     */
    private fun systemSwitchColors(context: Context): SystemSwitchColors {
        val track = SwitchThemeColors.colorStateList(context, "switch_track_background_zui")
        val thumb = SwitchThemeColors.colorStateList(context, "switch_thumb_zui")
        val enabled = intArrayOf(android.R.attr.state_enabled)
        val checked = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        val night = SwitchThemeColors.isNight(context)

        val accent = SwitchThemeColors.color(
            context, android.R.attr.colorAccent, SwitchFallbackColors.ACCENT
        )
        val on = track?.getColorForState(checked, track.defaultColor)?.takeIfVisible() ?: accent
        val off = track?.getColorForState(enabled, track.defaultColor)?.takeIfVisible()
            ?: if (night) NIGHT_TRACK_OFF else DAY_TRACK_OFF
        val thumbColor = thumb?.getColorForState(enabled, thumb.defaultColor)?.takeIfVisible()
            ?: WHITE
        val outline = SwitchThemeColors.themeColorStateList(
            context, android.R.attr.textColorSecondary
        )?.defaultColor?.takeIfVisible() ?: SwitchFallbackColors.OUTLINE

        return SystemSwitchColors(on = on, off = off, outline = outline, thumb = thumbColor)
    }

    /** Marks use a dark glyph on a white thumb and a light one on a dark thumb. */
    private fun contrastIcon(thumb: Int): Int {
        val luminance = (0.299f * Color.red(thumb) + 0.587f * Color.green(thumb) +
            0.114f * Color.blue(thumb)) / 255f
        return if (luminance > 0.5f) 0xFF6E6E6E.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun Int.takeIfVisible(): Int? = if (Color.alpha(this) == 0) null else this

    private data class SystemSwitchColors(
        val on: Int,
        val off: Int,
        val outline: Int,
        val thumb: Int
    )

    private data class Settings(
        val style: SwitchStyle,
        val mark: SwitchMark,
        val followSystemColors: Boolean,
        val trackColor: Int
    )

    private companion object {
        const val HOOK_ID_PREFIX = "zui_switch_ctor_"
        const val M3_WIDTH_DP = 52
        const val M3_HEIGHT_DP = 32
        const val M3_THUMB_DP = 24

        // Last-resort neutrals; normally the host's switch_track_background_zui is used.
        val DAY_TRACK_OFF = 0xFFE4E6E9.toInt()
        val NIGHT_TRACK_OFF = 0xFF3C4145.toInt()
        val WHITE = 0xFFFFFFFF.toInt()

        val KEY_ENABLED = PreferenceKeys.ZUI_SWITCH_STYLE
        val KEY_STYLE = PreferenceKeys.ZUI_SWITCH_STYLE_KIND
        val KEY_MARK = PreferenceKeys.ZUI_SWITCH_MARK
        val KEY_FOLLOW_SYSTEM = PreferenceKeys.ZUI_SWITCH_FOLLOW_SYSTEM_COLORS
        val KEY_TRACK_COLOR = PreferenceKeys.ZUI_SWITCH_TRACK_COLOR
    }
}
