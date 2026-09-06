package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * No-label mode for the ZUI Launcher app drawer.
 *
 * Companion module of [LauncherNoLabelMode], but with an inverted gate:
 * labels are hidden ONLY when the hooked call originates from the drawer
 * UI, i.e. the call stack contains frames from
 *
 * - com.android.launcher3.allapps        (all-apps list)
 * - com.android.launcher3.appprediction  (prediction row)
 *
 * Hook points are the same as [LauncherNoLabelMode]:
 * - BubbleTextView: drawer icons for non-ZUI apps
 * - ActiveIconView: drawer icons for ZUI system apps
 *   (Calendar, SafeCenter, Lenovo Switch, etc.)
 *
 * The two modules can coexist: LauncherNoLabelMode skips drawer callers
 * while this module only handles drawer callers, so they never fight
 * over the same icon. Hook ids are prefixed with "drawer_" because both
 * modules hook the same executables and ids must stay unique per hook.
 */
@SuppressLint("PrivateApi")
class LauncherDrawerNoLabelMode : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_DRAWER_NO_LABEL_MODE.name

    override fun getTargetPackages(): Array<out String?> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        installBubbleTextViewVisibilityHook(param)
        installActiveIconViewVisibilityHook(param)
    }

    /**
     * Check whether the current call stack comes from the app drawer UI
     * (all apps list or prediction row). The no-label logic is applied
     * only when this returns true; any other caller (desktop, folder,
     * popups...) keeps the original label behaviour.
     */
    private fun isFromDrawerPath(): Boolean {
        return Thread.currentThread().stackTrace.any { frame ->
            frame.className.startsWith("com.android.launcher3.allapps.") ||
                    frame.className.startsWith("com.android.launcher3.appprediction.")
        }
    }

    /**
     * Hook BubbleTextView.setTextVisibility and setTextAlpha so that labels
     * on drawer BubbleTextView icons (non-ZUI apps) are always hidden.
     */
    fun installBubbleTextViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")

            // Force setTextVisibility to always hide, but only for drawer callers
            val setTextVisibilityMethod: Method = findMethod(
                bubbleTextViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setTextVisibilityMethod, "drawer_set_text_visibility_1") { chain ->
                if (isFromDrawerPath()) {
                    val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }

            // Force setTextAlpha to always stay at 0 (hidden), but only for
            // drawer callers
            val setTextAlphaMethod: Method = findMethod(
                bubbleTextViewClass, "setTextAlpha",
                Float::class.javaPrimitiveType
            )
            hookWithId(setTextAlphaMethod, "drawer_set_text_alpha_1") { chain ->
                if (isFromDrawerPath()) {
                    val args = arrayOf<Any?>(java.lang.Float.valueOf(0.0f))
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }

            logger.info("BubbleTextView drawer visibility-block hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in BubbleTextView drawer visibility hook: ", e)
        }
    }

    /**
     * Hook ActiveIconView.setTextVisibility, setTextAlpha and
     * setIgnoreSetAlphaVisible so that labels on drawer ActiveIconView
     * icons (ZUI system apps) are always hidden.
     */
    fun installActiveIconViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val activeIconViewClass: Class<*> =
                loader.loadClass("com.zui.launcher.ActiveIconView")

            // Force setTextVisibility to always hide, but only for drawer callers
            val setTextVisibilityMethod: Method = findMethod(
                activeIconViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setTextVisibilityMethod, "drawer_set_text_visibility_2") { chain ->
                if (isFromDrawerPath()) {
                    val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }

            // Force setTextAlpha to always stay at 0 (hidden), but only for
            // drawer callers
            val setTextAlphaMethod: Method = findMethod(
                activeIconViewClass, "setTextAlpha",
                Float::class.javaPrimitiveType
            )
            hookWithId(setTextAlphaMethod, "drawer_set_text_alpha_2") { chain ->
                if (isFromDrawerPath()) {
                    val args = arrayOf<Any?>(java.lang.Float.valueOf(0.0f))
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }

            // Prevent setIgnoreSetAlphaVisible from being set to true for
            // drawer callers, so setTextAlpha always flows through to
            // setTextVisibility
            val setIgnoreMethod: Method = findMethod(
                activeIconViewClass, "setIgnoreSetAlphaVisible",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setIgnoreMethod, "drawer_set_ignore") { chain ->
                if (isFromDrawerPath()) {
                    val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }

            logger.info("ActiveIconView drawer visibility-block hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in ActiveIconView drawer visibility hook: ", e)
        }
    }

}
