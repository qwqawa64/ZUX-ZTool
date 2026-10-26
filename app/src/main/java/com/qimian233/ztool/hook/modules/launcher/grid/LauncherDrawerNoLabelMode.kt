package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Drawer no-label mode; companion of [LauncherNoLabelMode] with an inverted gate.
 *
 * Hides drawer labels for calls from the all-apps list and prediction row, and
 * at attach/bind time inside drawer containers. Hook ids use the "drawer_"
 * prefix because both modules hook the same executables.
 * See docs/research/launcher_no_label_mode.md.
 */
@SuppressLint("PrivateApi")
class LauncherDrawerNoLabelMode : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_DRAWER_NO_LABEL_MODE.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        installBubbleTextViewVisibilityHook(param)
        installActiveIconViewVisibilityHook(param)
        installActiveIconViewDrawerLabelHook(param)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            installBubbleTextViewDrawerLabelHook(param)
        }
    }

    /** Whether the call stack comes from the app drawer UI. */
    private fun isFromDrawerPath(): Boolean {
        return Thread.currentThread().stackTrace.any { frame ->
            frame.className.startsWith("com.android.launcher3.allapps.") ||
                    frame.className.startsWith("com.android.launcher3.appprediction.")
        }
    }

    /** Forces drawer BubbleTextView label visibility and alpha to stay hidden. */
    fun installBubbleTextViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")

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

    /** Forces drawer ActiveIconView labels, alpha and the alpha-ignore flag to stay hidden. */
    fun installActiveIconViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val activeIconViewClass: Class<*> =
                loader.loadClass("com.zui.launcher.ActiveIconView")

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

    /**
     * Hides ActiveIconView labels at attach and rebind time when the parent is
     * a drawer container.
     */
    fun installActiveIconViewDrawerLabelHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val activeIconViewClass: Class<*> =
                loader.loadClass("com.zui.launcher.ActiveIconView")
            val allAppsRecyclerViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.allapps.AllAppsRecyclerView")
            val predictionRowViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.appprediction.PredictionRowView")
            val setTextVisibilityMethod: Method = findMethod(
                activeIconViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )

            fun hideLabelIfInDrawer(iconView: View) {
                val parent = iconView.parent ?: return
                if (allAppsRecyclerViewClass.isInstance(parent) ||
                    predictionRowViewClass.isInstance(parent)
                ) {
                    // Re-enters the same hooks once; their drawer gate passes the call through.
                    setTextVisibilityMethod.invoke(iconView, java.lang.Boolean.valueOf(false))
                }
            }

            val attachMethod: Method = findMethod(activeIconViewClass, "onAttachedToWindow")
            hookWithId(attachMethod, "drawer_active_icon_attach") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            val appInfoClass: Class<*> =
                loader.loadClass("com.android.launcher3.model.data.AppInfo")
            val workspaceItemInfoClass: Class<*> =
                loader.loadClass("com.android.launcher3.model.data.WorkspaceItemInfo")
            val itemInfoWithIconClass: Class<*> =
                loader.loadClass("com.android.launcher3.model.data.ItemInfoWithIcon")

            val applyFromApplicationInfoMethod: Method = findMethod(
                activeIconViewClass, "applyFromApplicationInfo", appInfoClass
            )
            hookWithId(applyFromApplicationInfoMethod, "drawer_active_icon_bind_appinfo") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            val applyFromWorkspaceItemMethod: Method = findMethod(
                activeIconViewClass, "applyFromWorkspaceItem", workspaceItemInfoClass
            )
            hookWithId(applyFromWorkspaceItemMethod, "drawer_active_icon_bind_workspaceitem") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            val applyFromItemInfoWithIconMethod: Method = findMethod(
                activeIconViewClass, "applyFromItemInfoWithIcon", itemInfoWithIconClass
            )
            hookWithId(applyFromItemInfoWithIconMethod, "drawer_active_icon_bind_itemwithicon") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            logger.info("ActiveIconView drawer label hooks installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in ActiveIconView drawer label hook: ", e)
        }
    }

    /**
     * Android 17+ only. Force-hides drawer BubbleTextView labels at attach and
     * bind time, mirroring the ActiveIconView path above.
     */
    fun installBubbleTextViewDrawerLabelHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")
            val allAppsRecyclerViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.allapps.AllAppsRecyclerView")
            val predictionRowViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.appprediction.PredictionRowView")
            val setTextVisibilityMethod: Method = findMethod(
                bubbleTextViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )

            fun hideLabelIfInDrawer(iconView: View) {
                val parent = iconView.parent ?: return
                if (allAppsRecyclerViewClass.isInstance(parent) ||
                    predictionRowViewClass.isInstance(parent)
                ) {
                    setTextVisibilityMethod.invoke(iconView, java.lang.Boolean.valueOf(false))
                }
            }

            val attachMethod: Method = findMethod(bubbleTextViewClass, "onAttachedToWindow")
            hookWithId(attachMethod, "drawer_bubble_text_attach") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            val appInfoClass: Class<*> =
                loader.loadClass("com.android.launcher3.model.data.AppInfo")
            val workspaceItemInfoClass: Class<*> =
                loader.loadClass("com.android.launcher3.model.data.WorkspaceItemInfo")
            val itemInfoWithIconClass: Class<*> =
                loader.loadClass("com.android.launcher3.model.data.ItemInfoWithIcon")

            val applyFromApplicationInfoMethod: Method = findMethod(
                bubbleTextViewClass, "applyFromApplicationInfo", appInfoClass
            )
            hookWithId(applyFromApplicationInfoMethod, "drawer_bubble_text_bind_appinfo") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            val applyFromWorkspaceItemMethod: Method = findMethod(
                bubbleTextViewClass, "applyFromWorkspaceItem", workspaceItemInfoClass
            )
            hookWithId(applyFromWorkspaceItemMethod, "drawer_bubble_text_bind_workspaceitem") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            val applyFromItemInfoWithIconMethod: Method = findMethod(
                bubbleTextViewClass, "applyFromItemInfoWithIcon", itemInfoWithIconClass
            )
            hookWithId(applyFromItemInfoWithIconMethod, "drawer_bubble_text_bind_itemwithicon") { chain ->
                chain.proceed()
                (chain.thisObject as? View)?.let { hideLabelIfInDrawer(it) }
            }

            logger.info("BubbleTextView drawer label hooks installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in BubbleTextView drawer label hook: ", e)
        }
    }

}
