package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import kotlin.math.roundToInt

@SuppressLint("PrivateApi")
class NotificationCenterTransparency : AppHookModule() {

    private var blurPercent = DEFAULT_BLUR_PERCENT

    override fun getModuleName(): String = PreferenceKeys.NOTIFICATION_CENTER_BLUR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        updatePrefs()
        hookShadeRootViewFactory(classLoader)
        hookNotificationShadeBlur(classLoader)
        hookQuickSettingsBackdropBlur(classLoader)
    }

    private fun hookShadeRootViewFactory(classLoader: ClassLoader) {
        try {
            val providesMethod: Method = classLoader
                .loadClass("com.android.systemui.shade.ShadeViewProviderModule_Companion_ProvidesWindowRootViewFactory")
                .getDeclaredMethod(
                    "providesWindowRootView",
                    classLoader.loadClass("android.view.LayoutInflater")
                )
            hookWithId(providesMethod, "provides") { chain ->
                val result = chain.proceed()
                if (result is View && isBlurCleared()) {
                    clearBlurFromViewTree(result)
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook shade root view factory", t)
        }
    }

    private fun hookNotificationShadeBlur(classLoader: ClassLoader) {
        try {
            val setBlurMethod: Method = classLoader
                .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                .getDeclaredMethod("setNotificationPanelBlurBehind")
            hookWithId(setBlurMethod, "set_blur") { chain ->
                if (isBlurCleared()) {
                    return@hookWithId null
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook setNotificationPanelBlurBehind()", t)
        }

        try {
            val setBlurIntMethod: Method = classLoader
                .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                .getDeclaredMethod("setNotificationPanelBlurBehind", Int::class.javaPrimitiveType)
            hookWithId(setBlurIntMethod, "set_blur_int") { chain ->
                chain.proceed(arrayOf(scaleBlur(chain.args[0] as Int)))
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook setNotificationPanelBlurBehind(int)", t)
        }

        try {
            val computeMethod: Method = classLoader
                .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                .getDeclaredMethod("computeBlurAndZoomOut")
            hookWithId(computeMethod, "compute") { chain ->
                val result = chain.proceed()
                if (result != null) {
                    val pairClass = classLoader.loadClass("kotlin.Pair")
                    val blur = pairClass.getDeclaredMethod("component1").invoke(result)
                    val zoomOut = pairClass.getDeclaredMethod("component2").invoke(result)
                    return@hookWithId pairClass.getDeclaredConstructor(Any::class.java, Any::class.java)
                        .newInstance(scaleBlur(blur), zoomOut)
                }
                null
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook NotificationShadeDepthController.computeBlurAndZoomOut", t)
        }

        try {
            val animateBlurMethod: Method = classLoader
                .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                .getDeclaredMethod("animateBlur", Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            hookWithId(animateBlurMethod, "animate_blur") { chain ->
                val newBlur = scaleBlur(chain.args[0] as Float)
                var newAnimate = chain.args[1] as Boolean
                if (isBlurCleared()) {
                    newAnimate = false
                }
                chain.proceed(arrayOf<Any>(newBlur, newAnimate))
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook NotificationShadeDepthController.animateBlur", t)
        }
    }

    private fun hookQuickSettingsBackdropBlur(classLoader: ClassLoader) {
        try {
            val updateExpansionMethod: Method = classLoader
                .loadClass("com.android.systemui.shade.QuickSettingsControllerImpl")
                .getDeclaredMethod("updateExpansion")
            hookWithId(updateExpansionMethod, "update_expansion") { chain ->
                val result = chain.proceed()
                if (isBlurCleared()) {
                    clearBackdropRenderEffect(chain.thisObject)
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook QuickSettingsControllerImpl.updateExpansion", t)
        }
    }

    private fun clearBackdropRenderEffect(quickSettingsController: Any) {
        try {
            val cl = quickSettingsController.javaClass
            val zuiCore = cl.getDeclaredField("mZuiCoreImpl").get(quickSettingsController)
            if (zuiCore == null) {
                logger.error("zuiCore is null, cannot proceed clearing backdrop render effect!")
                return
            }
            val backdrop = zuiCore.javaClass.getDeclaredField("backDropView").get(zuiCore)
            if (backdrop is View) {
                backdrop.setRenderEffect(null)
            }
        } catch (t: Throwable) {
            logger.error("Failed to clear QS backdrop render effect", t)
        }
    }

    private fun clearBlurFromViewTree(view: View) {
        view.setRenderEffect(null)

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                clearBlurFromViewTree(view.getChildAt(i))
            }
        }
    }

    private fun updatePrefs() {
        blurPercent = try {
            remotePreferences.getInt(PreferenceKeys.NOTIFICATION_CENTER_BLUR_PERCENT.name, DEFAULT_BLUR_PERCENT)
        } catch (_: Throwable) {
            DEFAULT_BLUR_PERCENT
        }
        if (blurPercent < 0) {
            blurPercent = 0
        } else if (blurPercent > 100) {
            blurPercent = 100
        }
    }

    private fun isBlurCleared(): Boolean {
        updatePrefs()
        return blurPercent <= 0
    }

    private fun scaleBlur(blur: Int): Int {
        updatePrefs()
        return (blur * (blurPercent / 100.0f)).roundToInt()
    }

    private fun scaleBlur(blur: Float): Float {
        updatePrefs()
        return blur * (blurPercent / 100.0f)
    }

    private fun scaleBlur(blur: Any?): Any? {
        if (blur is Int) {
            return scaleBlur(blur)
        }
        if (blur is Float) {
            return scaleBlur(blur)
        }
        if (blur is Number) {
            updatePrefs()
            return (blur.toFloat() * (blurPercent / 100.0f)).roundToInt()
        }
        return blur
    }

    companion object {
        private const val DEFAULT_BLUR_PERCENT = 0
    }
}
