package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import android.view.ViewGroup
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class LauncherNoLabelMode : BaseHookModule() {
    override fun getModuleName(): String {
        return "launcher_no_label_mode"
    }

    override fun getTargetPackages(): Array<out String?> {
        return arrayOf("com.zui.launcher")
    }

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        installNormalAppNoLabelHook(param)
        installFolderNoLabelHook(param)
    }

    fun installNormalAppNoLabelHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")
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
            val bubbleTextViewClass: Class<*> = loader.loadClass("com.android.launcher3.BubbleTextView")

            // folderBubbleTextView 是 FolderIcon 里持有 BubbleTextView 的字段
            val folderBubbleField: Field = findField(folderIconClass, "folderBubbleTextView")
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
}