package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.os.Build
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Lock screen clock color hook module.
 *
 * The ZUI lock screen clock is rendered by SystemUI's AnimatableClockViewWithDate:
 * onAttachedToWindow and a ContentObserver callback read Settings.System's
 * lock_screen_clock_color (-1 means automatic black/white based on wallpaper
 * brightness), store it in mCustomizedColor, then call updateColor(int), which
 * writes the color onto the clock and date TextViews.
 *
 * This module hooks updateColor(int) and replaces its argument with the
 * user-defined ARGB color. updateColor first stores its argument into
 * mCustomizedColor, and the system theme color path setColors(int) returns
 * immediately when mCustomizedColor != -1, so the replaced color is not
 * overwritten by wallpaper/theme sync, and both initial load and dynamic
 * changes go through the same entry point.
 *
 * Starting from ZUXOS 1.5 (Android 16), the system natively provides this
 * capability under Settings -> Wallpaper & theme style -> Lock screen. When
 * SDK >= 36 is detected, the hook is not installed to avoid conflicting with
 * the native feature.
 */
class LockScreenClockColorHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.LOCK_SCREEN_CLOCK_COLOR_CUSTOM.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (Build.VERSION.SDK_INT >= NATIVE_FEATURE_SDK) {
            logger.info("LockScreenClockColorHook skipped: ZUXOS 1.5 / Android 16 provides native lock screen clock color")
            return
        }
        val enabled = remotePreferences.getBoolean(
            PreferenceKeys.LOCK_SCREEN_CLOCK_COLOR_CUSTOM.name,
            PreferenceKeys.LOCK_SCREEN_CLOCK_COLOR_CUSTOM.default
        )
        if (!enabled) return
        val color = remotePreferences.getInt(
            PreferenceKeys.LOCK_SCREEN_CLOCK_COLOR_VALUE.name,
            PreferenceKeys.LOCK_SCREEN_CLOCK_COLOR_VALUE.default
        )
        try {
            val clockClass = param.defaultClassLoader.loadClass(TARGET_CLASS)
            hookWithId(
                findMethod(clockClass, "updateColor", Int::class.javaPrimitiveType),
                "lock_screen_clock_color_update"
            ) { chain ->
                try {
                    val args = chain.args.toMutableList()
                    args[0] = color
                    return@hookWithId chain.proceed(args.toTypedArray())
                } catch (t: Throwable) {
                    logger.error("LockScreenClockColorHook failed to apply color", t)
                    return@hookWithId chain.proceed()
                }
            }
            logger.info("LockScreenClockColorHook applied, color=0x%08X".format(color))
        } catch (t: Throwable) {
            logger.error("LockScreenClockColorHook failed", t)
        }
    }

    private companion object {
        const val TARGET_CLASS = "com.android.systemui.shared.clocks.AnimatableClockViewWithDate"
        const val NATIVE_FEATURE_SDK = 36
    }
}
