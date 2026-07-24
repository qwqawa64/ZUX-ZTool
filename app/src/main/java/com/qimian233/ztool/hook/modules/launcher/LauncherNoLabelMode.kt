package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class LauncherNoLabelMode : BaseHookModule() {

    private var bubbleTextViewClass : Class<*>? = null

    override fun getModuleName(): String {
        return "launcher_no_label_mode"
    }

    override fun getTargetPackages(): Array<out String?> {
        return arrayOf("com.zui.launcher")
    }

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        installNormalAppNoLabelHook(param)
        installActiveIconViewVisibilityHook(param)
        installFolderNoLabelHook(param)
        installBluePointRemovalHook(param)
    }

    fun installNormalAppNoLabelHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")
            this.bubbleTextViewClass = bubbleTextViewClass
            val workspaceItemInfoClass: Class<*> =
                loader.loadClass("com.android.launcher3.model.data.WorkspaceItemInfo")

            val setTextMethod: Method = findMethod(bubbleTextViewClass, "setText",
                CharSequence::class.java)
            val setContentDescriptionMethod: Method = findMethod(bubbleTextViewClass,
                "setContentDescription", CharSequence::class.java)

            val targetMethod: Method = findMethod(
                bubbleTextViewClass, "applyFromWorkspaceItem",
                workspaceItemInfoClass
            )
            log("Ready to install hook on applyFromWorkspaceItem!")
            xposed.hook(targetMethod).intercept { chain ->
                try {
                    val result = chain.proceed()
                    setTextMethod.invoke(chain.thisObject, "")
                    setContentDescriptionMethod.invoke(chain.thisObject, "")
                    return@intercept result
                } catch (e: Throwable) {
                    logError("Failed inside applyFromWorkspaceItem hook: ", e)
                    return@intercept chain.proceed()
                }
            }
            log("Hook installed successfully!")
        } catch (e: Throwable) {
            logError("Exception caught in launcher no label mode hook: ", e)
        }
    }

    /**
     * Hook ActiveIconView.setTextVisibility, setTextAlpha and setIgnoreSetAlphaVisible
     * to prevent folder animations from re-enabling label visibility after clearing.
     *
     * FolderAnimationManager.z() calls setIgnoreSetAlphaVisible(true) then
     * setTextAlpha(...), which skips the normal text-visibility path and only
     * changes color.  When the animation later calls setTextVisibility(true)
     * (e.g. from the animator end callback g.onAnimationEnd → applyFromWorkspaceItem),
     * the label can become visible again even if the text content was cleared.
     *
     * This hook forces text to stay invisible regardless of what the animation does.
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
            xposed.hook(setTextVisibilityMethod).intercept { chain ->
                // Ignore the argument — always pass false to hide text
                val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                chain.proceed(args)
            }

            // Force setTextAlpha to always keep alpha at 0 (hidden)
            val setTextAlphaMethod: Method = findMethod(
                activeIconViewClass, "setTextAlpha",
                Float::class.javaPrimitiveType
            )
            xposed.hook(setTextAlphaMethod).intercept { chain ->
                val args = arrayOf<Any?>(java.lang.Float.valueOf(0.0f))
                chain.proceed(args)
            }

            // Prevent setIgnoreSetAlphaVisible from being set to true,
            // so setTextAlpha always flows through to setTextVisibility
            val setIgnoreMethod: Method = findMethod(
                activeIconViewClass, "setIgnoreSetAlphaVisible",
                Boolean::class.javaPrimitiveType
            )
            xposed.hook(setIgnoreMethod).intercept { chain ->
                val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                chain.proceed(args)
            }

            log("ActiveIconView visibility-block hook installed successfully!")
        } catch (e: Throwable) {
            logError("Exception caught in ActiveIconView visibility hook: ", e)
        }
    }

    fun installFolderNoLabelHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val folderIconClass: Class<*> = loader.loadClass("com.android.launcher3.folder.FolderIcon")
            val bubbleTextViewClass: Class<*>? = this.bubbleTextViewClass
            if (bubbleTextViewClass == null) {
                log("BubbleTextViewClass is null!")
                return
            }
            val folderBubbleField: Field = findField(folderIconClass, "e")
            val setTextMethod: Method = findMethod(bubbleTextViewClass, "setText",
                CharSequence::class.java)
            val setContentDescriptionMethod: Method = findMethod(bubbleTextViewClass,
                "setContentDescription", CharSequence::class.java)

            val folderInfoClass: Class<*> = loader.loadClass("com.android.launcher3.model.data.FolderInfo")
            val activityContextClass: Class<*> = loader.loadClass("com.android.launcher3.views.ActivityContext")

            // inflateIcon is static → chain.thisObject is null; use chain.result instead
            val inflateMethod: Method = findMethod(folderIconClass, "inflateIcon",
                Int::class.javaPrimitiveType, activityContextClass, ViewGroup::class.java, folderInfoClass)
            xposed.hook(inflateMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    // inflateIcon is static; result is the returned FolderIcon
                    if (result != null) {
                        val bubbleTextView = folderBubbleField.get(result)
                        if (bubbleTextView != null) {
                            setTextMethod.invoke(bubbleTextView, "")
                            setContentDescriptionMethod.invoke(bubbleTextView, "")
                        }
                    }
                } catch (t: Throwable) {
                    logError("Failed to clear folder label!", t)
                }
                return@intercept result
            }
            log("Folder no-label hook installed successfully!")
        } catch (e: Throwable) {
            logError("Exception caught in folder no label mode hook: ", e)
        }
    }

    /**
     * Hook BluePoint.isPackageNew(View) → always return false.
     *
     * In no-label mode the app label text is cleared, but the blue update dot
     * (drawn in DoubleShadowBubbleTextView.onDraw via BluePoint.isPackageNew)
     * would still appear next to the empty label.  Suppress it at the source.
     */
    fun installBluePointRemovalHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bluePointClass: Class<*> = loader.loadClass("com.zui.launcher.BluePoint")

            val isPackageNewMethod: Method = findMethod(bluePointClass, "isPackageNew",
                View::class.java)
            xposed.hook(isPackageNewMethod).intercept {
                // Always tell Launcher "this is not a newly-updated package"
                return@intercept false
            }
            log("BluePoint removal hook installed successfully!")
        } catch (e: Throwable) {
            logError("Exception caught in blue point removal hook: ", e)
        }
    }
}