package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * No-label mode for ZUI Launcher.
 *
 * Instead of clearing text content (which breaks TalkBack accessibility),
 * we hook setTextVisibility / setTextAlpha / setIgnoreSetAlphaVisible so that
 * labels are **visually hidden** while the text content stays intact for
 * screen readers.
 *
 * Covered paths:
 * - BubbleTextView: desktop & folder icons for non-ZUI apps, folder names
 * - ActiveIconView: desktop & folder icons for ZUI system apps
 *   (Calendar, SafeCenter, Lenovo Switch, etc.)
 */
@SuppressLint("PrivateApi")
class LauncherNoLabelMode : BaseHookModule() {

    override fun getModuleName(): String {
        return "launcher_no_label_mode"
    }

    override fun getTargetPackages(): Array<out String?> {
        return arrayOf("com.zui.launcher")
    }

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        installBubbleTextViewVisibilityHook(param)
        installActiveIconViewVisibilityHook(param)
    }

    /**
     * Check whether the current call stack includes
     * PopupContainerWithArrow.initializeSystemShortcut.
     *
     * When BubbleTextView is used by the system-shortcut popup, the no-label
     * logic must be skipped so the popup menu labels display correctly.
     */
    private fun isFromPopupSystemShortcut(): Boolean {
        return Thread.currentThread().stackTrace.any { frame ->
            frame.className == "com.android.launcher3.popup.PopupContainerWithArrow" &&
                frame.methodName == "initializeSystemShortcut"
        }
    }

    /**
     * Hook BubbleTextView.setTextVisibility and setTextAlpha so that labels
     * on BubbleTextView icons (non-ZUI apps, folder names) are always hidden.
     *
     * BubbleTextView is the base icon class used for apps that do NOT use
     * ActiveIconView (i.e. most third-party apps).  It is also used for the
     * folder name label on FolderIcon (field "e").
     */
    fun installBubbleTextViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")

            // Force setTextVisibility to always hide
            val setTextVisibilityMethod: Method = findMethod(
                bubbleTextViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setTextVisibilityMethod, "set_text_visibility_1") {  chain ->
                if (isFromPopupSystemShortcut()) {
                    chain.proceed(chain.args.toTypedArray())
                } else {
                    val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                    chain.proceed(args)
                }
            }

            // Force setTextAlpha to always stay at 0 (hidden).
            // FolderAnimationManager.z() calls setTextAlpha directly during
            // folder open/close animations, bypassing setTextVisibility.
            val setTextAlphaMethod: Method = findMethod(
                bubbleTextViewClass, "setTextAlpha",
                Float::class.javaPrimitiveType
            )
            hookWithId(setTextAlphaMethod, "set_text_alpha_1") {  chain ->
                if (isFromPopupSystemShortcut()) {
                    chain.proceed(chain.args.toTypedArray())
                } else {
                    val args = arrayOf<Any?>(java.lang.Float.valueOf(0.0f))
                    chain.proceed(args)
                }
            }

            logger.info("BubbleTextView visibility-block hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in BubbleTextView visibility hook: ", e)
        }
    }

    /**
     * Hook ActiveIconView.setTextVisibility, setTextAlpha and
     * setIgnoreSetAlphaVisible so that labels on ActiveIconView icons
     * (ZUI system apps) are always hidden.
     *
     * ActiveIconView is used for apps where isZuiActiveIcon() returns true,
     * e.g. Calendar (com.lenovo.calendar), SafeCenter, Lenovo Switch, etc.
     *
     * setIgnoreSetAlphaVisible must be forced to false, otherwise
     * FolderAnimationManager.z() can set it to true, which makes
     * setTextAlpha skip the normal visibility path — and the label
     * reappears during folder open/close animations.
     */
    fun installActiveIconViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val activeIconViewClass: Class<*> =
                loader.loadClass("com.zui.launcher.ActiveIconView")

            // Force setTextVisibility to always hide
            val setTextVisibilityMethod: Method = findMethod(
                activeIconViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setTextVisibilityMethod, "set_text_visibility_2") {  chain ->
                val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                chain.proceed(args)
            }

            // Force setTextAlpha to always stay at 0 (hidden)
            val setTextAlphaMethod: Method = findMethod(
                activeIconViewClass, "setTextAlpha",
                Float::class.javaPrimitiveType
            )
            hookWithId(setTextAlphaMethod, "set_text_alpha_2") {  chain ->
                val args = arrayOf<Any?>(java.lang.Float.valueOf(0.0f))
                chain.proceed(args)
            }

            // Prevent setIgnoreSetAlphaVisible from being set to true,
            // so setTextAlpha always flows through to setTextVisibility
            val setIgnoreMethod: Method = findMethod(
                activeIconViewClass, "setIgnoreSetAlphaVisible",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setIgnoreMethod, "set_ignore") {  chain ->
                val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                chain.proceed(args)
            }

            logger.info("ActiveIconView visibility-block hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in ActiveIconView visibility hook: ", e)
        }
    }

}

