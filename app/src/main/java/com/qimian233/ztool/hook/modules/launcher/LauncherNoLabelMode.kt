package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class LauncherNoLabelMode : BaseHookModule() {
    override fun getModuleName(): String {
        // return "launcher_no_label_mode"
        return "test_hook"
    }

    override fun getTargetPackages(): Array<out String?> {
        return arrayOf("com.zui.launcher")
    }

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")

            // Resolve helper methods once outside the callback so failures are loud
            val setTextMethod: Method = findMethod(bubbleTextViewClass, "setText",
                CharSequence::class.java)
            val setContentDescriptionMethod: Method = findMethod(bubbleTextViewClass,
                "setContentDescription", CharSequence::class.java)

            val targetMethod: Method = findMethod(
                bubbleTextViewClass, "applyIconAndLabel", Drawable::class.java,
                CharSequence::class.java
            )
            log("Ready to install hook!")
            xposed.hook(targetMethod).intercept { chain ->
                log("Intercept chain triggered!")
                try {
                    // Let original method apply icon + label first ...
                    val result = chain.proceed()
                    // ... then clear the text label
                    setTextMethod.invoke(chain.thisObject, "")
                    setContentDescriptionMethod.invoke(chain.thisObject, "")
                    return@intercept result
                } catch (e: Throwable) {
                    logError("Failed inside applyIconAndLabel hook: ", e)
                    return@intercept chain.proceed()
                }
            }
            log("Hook installed successfully!")
        } catch (e: Throwable) {
            logError("Exception caught in launcher no label mode hook: ", e)
        }
    }
}