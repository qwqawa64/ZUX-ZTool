package com.qimian233.ztool.hook.modules.zuiui

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
 * Restyles every `zui.widget.Switch` of the host: the indicator style wraps the thumb,
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

    /** Seed list for readers only; the real gate is the ZUI switch class. */
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

        val switchClass = runCatching { param.defaultClassLoader.loadClass(SWITCH_CLASS) }
            .getOrNull() ?: return
        val constructors = switchClass.declaredConstructors
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
            "switch style installed: " + loaded.style.prefValue + "/" + loaded.mark.prefValue
        )
    }

    /** A missing switch or an unreadable preference file leaves the stock switch alone. */
    private fun readSettings(): Settings? = runCatching {
        val prefs = xposed.getRemotePreferences(PREFS_NAME)
        if (!prefs.getBoolean(KEY_ENABLED.name, KEY_ENABLED.default)) return@runCatching null
        Settings(
            style = SwitchStyle.fromPref(prefs.getString(KEY_STYLE.name, KEY_STYLE.default)),
            mark = SwitchMark.fromPref(prefs.getString(KEY_MARK.name, KEY_MARK.default))
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
            SwitchStyle.M3 -> applyM3(target, settings.mark)
        }
    }

    /** Keeping the native track means the stock slide animation and nine-patch stay intact. */
    private fun applyIndicator(target: Switch, mark: SwitchMark) {
        if (mark == SwitchMark.NONE) return
        val host = target.thumbDrawable ?: return
        if (host is GlyphThumbDrawable) return
        val context = target.context
        val checkColor = SwitchThemeColors.color(
            context, android.R.attr.colorAccent, SwitchFallbackColors.ACCENT
        )
        target.setThumbDrawable(
            GlyphThumbDrawable(
                host = host,
                mark = mark,
                checkColor = checkColor,
                crossColor = SwitchFallbackColors.CROSS,
                initiallyChecked = target.isChecked,
                initiallyEnabled = target.isEnabled
            )
        )
    }

    /** The track drawable paints the whole control, so the native thumb becomes invisible. */
    private fun applyM3(target: Switch, mark: SwitchMark) {
        if (target.trackDrawable is M3SwitchDrawable) return
        val context = target.context
        val density = context.resources.displayMetrics.density
        val night = SwitchThemeColors.isNight(context)
        val primary = SwitchThemeColors.color(
            context, android.R.attr.colorAccent, SwitchFallbackColors.ACCENT
        )
        val trackOff = if (night) NIGHT_TRACK_OFF else DAY_TRACK_OFF
        val outline = if (night) NIGHT_OUTLINE else DAY_OUTLINE
        val thumbOff = if (night) NIGHT_THUMB_OFF else DAY_THUMB_OFF
        val iconOff = if (night) NIGHT_ICON_OFF else DAY_ICON_OFF

        target.setShowText(false)
        target.setSplitTrack(false)
        target.setSwitchMinWidth((M3_WIDTH_DP * density).roundToInt())
        target.setThumbDrawable(TransparentThumbDrawable((M3_THUMB_DP * density).roundToInt()))
        target.setTrackDrawable(
            M3SwitchDrawable(
                mark = mark,
                trackOn = primary,
                trackOff = trackOff,
                outline = outline,
                thumbOn = 0xFFFFFFFF.toInt(),
                thumbOff = thumbOff,
                iconOn = primary,
                iconOff = iconOff,
                initiallyChecked = target.isChecked,
                intrinsicHeightPx = (M3_HEIGHT_DP * density).roundToInt()
            )
        )
    }

    private data class Settings(
        val style: SwitchStyle,
        val mark: SwitchMark
    )

    private companion object {
        const val SWITCH_CLASS = "zui.widget.Switch"
        const val HOOK_ID_PREFIX = "zui_switch_ctor_"
        const val M3_WIDTH_DP = 52
        const val M3_HEIGHT_DP = 32
        const val M3_THUMB_DP = 24

        // M3 baseline neutrals; the host theme's surface colours vary per ZUI build.
        val DAY_TRACK_OFF = 0xFFE4E6E9.toInt()
        val DAY_OUTLINE = 0xFF74777A.toInt()
        val DAY_THUMB_OFF = 0xFF74777A.toInt()
        val DAY_ICON_OFF = 0xFFFFFFFF.toInt()
        val NIGHT_TRACK_OFF = 0xFF3C4145.toInt()
        val NIGHT_OUTLINE = 0xFF8E9499.toInt()
        val NIGHT_THUMB_OFF = 0xFF9AA0A6.toInt()
        val NIGHT_ICON_OFF = 0xFF1B1F21.toInt()
        val KEY_ENABLED = PreferenceKeys.ZUI_SWITCH_STYLE
        val KEY_STYLE = PreferenceKeys.ZUI_SWITCH_STYLE_KIND
        val KEY_MARK = PreferenceKeys.ZUI_SWITCH_MARK
    }
}
