package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.os.Build
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 锁屏时钟颜色 Hook 模块
 *
 * ZUI 的锁屏时钟由 SystemUI 的 AnimatableClockViewWithDate 渲染：onAttachedToWindow
 * 和 ContentObserver 回调读取 Settings.System 的 lock_screen_clock_color（-1 表示
 * 跟随壁纸明暗自动黑/白），存入 mCustomizedColor 后调用 updateColor(int)，由后者把
 * 颜色写到时钟和日期 TextView 上。
 *
 * 本模块 hook updateColor(int) 替换其入参为用户自定义 ARGB 颜色。updateColor 会先把
 * 入参写入 mCustomizedColor，而系统主题色路径 setColors(int) 在 mCustomizedColor != -1
 * 时直接返回，因此替换后的颜色不会被壁纸/主题联动覆盖，且初始加载与动态变更都经过
 * 同一入口。
 *
 * ZUXOS 1.5（Android 16）起系统原生在 设置 -> 壁纸和主题风格 -> 锁屏 提供该能力，
 * 检测到 SDK >= 36 时不安装 Hook，避免与原生功能互相踩踏。
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
