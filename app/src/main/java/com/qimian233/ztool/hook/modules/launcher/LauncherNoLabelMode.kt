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
        installFolderNoLabelHook(param)
        installItemInflaterNoLabelHook(param)
        installFolderPagedViewNoLabelHook(param)
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
     * Hook com.android.launcher3.util.ItemInflater.d — the inflation path used by
     * ActiveIconView icons (both on desktop and inside folders).  These icons do
     * NOT go through BubbleTextView.applyFromWorkspaceItem, so the main hook misses
     * them.  We iterate every method named "d" (the name is obfuscated) and clear
     * any View result's subtree of text labels.
     */
    fun installItemInflaterNoLabelHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val itemInflaterClass: Class<*> =
                loader.loadClass("com.android.launcher3.util.ItemInflater")
            val textViewClass: Class<*> = loader.loadClass("android.widget.TextView")
            val setTextMethod: Method = findMethod(textViewClass, "setText",
                CharSequence::class.java)
            val setContentDescriptionMethod: Method = findMethod(View::class.java,
                "setContentDescription", CharSequence::class.java)

            for (method in itemInflaterClass.declaredMethods) {
                if (method.name != "d") continue
                method.isAccessible = true
                xposed.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        if (result is View) {
                            clearTextInHierarchy(result, textViewClass, setTextMethod,
                                setContentDescriptionMethod)
                            (result as View).post {
                                clearTextInHierarchy(result, textViewClass, setTextMethod,
                                    setContentDescriptionMethod)
                            }
                        }
                    } catch (t: Throwable) {
                        logError("Failed to clear label in ItemInflater.d hook!", t)
                    }
                    return@intercept result
                }
            }
            log("ItemInflater no-label hook installed successfully!")
        } catch (e: Throwable) {
            logError("Exception caught in ItemInflater no label mode hook: ", e)
        }
    }

    /**
     * Hook FolderPagedView.createNewView — the inflation path for item icons inside
     * opened folders.  createNewView only creates the View; text is set later by
     * d1.apply (called from bindItems).  We clear immediately (in case some text is
     * already present) and post a one-frame re-check to catch labels set during the
     * synchronous bind phase.
     *
     * The returned view class varies (e.g. Tratp, ActiveIconView) — we use
     * clearTextInHierarchy to traverse children regardless of type.
     */
    fun installFolderPagedViewNoLabelHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val folderPagedViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.folder.FolderPagedView")
            val textViewClass: Class<*> = loader.loadClass("android.widget.TextView")
            val setTextMethod: Method = findMethod(textViewClass, "setText",
                CharSequence::class.java)
            val setContentDescriptionMethod: Method = findMethod(View::class.java,
                "setContentDescription", CharSequence::class.java)

            for (method in folderPagedViewClass.declaredMethods) {
                if (method.name == "createNewView" && method.returnType == View::class.java) {
                    method.isAccessible = true
                    xposed.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            if (result is View) {
                                // clear synchronously for text set in constructor
                                clearTextInHierarchy(result, textViewClass, setTextMethod,
                                    setContentDescriptionMethod)
                                // post-clear for text set in d1.apply / bindItems
                                (result as View).post {
                                    clearTextInHierarchy(result, textViewClass, setTextMethod,
                                        setContentDescriptionMethod)
                                }
                            }
                        } catch (t: Throwable) {
                            logError("Failed to clear folder-item label!", t)
                        }
                        return@intercept result
                    }
                }
            }
            log("FolderPagedView no-label hook installed successfully!")
        } catch (e: Throwable) {
            logError("Exception caught in folder-paged-view no label mode hook: ", e)
        }
    }

    /** Walk [root] and its descendants; blank text + contentDescription on every TextView. */
    private fun clearTextInHierarchy(
        root: View,
        textViewClass: Class<*>,
        setTextMethod: Method,
        setContentDescriptionMethod: Method
    ) {
        if (textViewClass.isInstance(root)) {
            setTextMethod.invoke(root, "")
        }
        setContentDescriptionMethod.invoke(root, "")
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i) ?: continue
                clearTextInHierarchy(child, textViewClass, setTextMethod,
                    setContentDescriptionMethod)
            }
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