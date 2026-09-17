package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * SystemUI status bar clock seconds display hook module.
 * Force-enables the seconds display of the system status bar clock.
 */
@SuppressLint("PrivateApi")
class StatusBarClockSecondsHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.STATUSBAR_DISPLAY_SECONDS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (ScopeKeys.SYSTEM_UI.packageName == packageName) {
            hookSystemUIClock(classLoader)
        }
    }

    private fun hookSystemUIClock(classLoader: ClassLoader) {
        try {
            // Hook 1: force-enable seconds display when the Clock object is created
            val onAttachedMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("onAttachedToWindow")
            hookWithId(onAttachedMethod, "on_attached") { chain ->
                val result = chain.proceed()
                forceEnableClockSeconds(chain.thisObject)
                result
            }

            logger.info("Successfully hooked Clock.onAttachedToWindow")
        } catch (t: Throwable) {
            logger.error("Failed to hook Clock.onAttachedToWindow", t)
        }

        try {
            // Hook 2: prevent system settings from overriding our modification
            val onTuningMethod: Method = classLoader.loadClass(CLOCK_CLASS)
                .getDeclaredMethod("onTuningChanged", String::class.java, String::class.java)
            hookWithId(onTuningMethod, "on_tuning") { chain ->
                val key = chain.args[0] as String
                if ("clock_seconds" == key) {
                    // Force the setting to enabled
                    val clockCls = chain.thisObject.javaClass
                    clockCls.getDeclaredField("mShowSeconds").setBoolean(chain.thisObject, true)
                    // Call the original method, but modify the second argument to "1"
                    val result = chain.proceed(arrayOf(key, "1"))
                    // Ensure seconds display is updated
                    clockCls.getDeclaredMethod("updateShowSeconds").invoke(chain.thisObject)
                    result
                } else {
                    chain.proceed()
                }
            }

            logger.info("Successfully hooked Clock.onTuningChanged")
        } catch (t: Throwable) {
            logger.error("Failed to hook Clock.onTuningChanged", t)
        }

        try {
            // Hook 3: directly modify the updateShowSeconds method
            val updateMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("updateShowSeconds")
            hookWithId(updateMethod, "update") { chain ->
                // Force-enable seconds display
                chain.thisObject.javaClass.getDeclaredField("mShowSeconds")
                    .setBoolean(chain.thisObject, true)
                chain.proceed()
            }

            logger.info("Successfully hooked Clock.updateShowSeconds")
        } catch (t: Throwable) {
            logger.error("Failed to hook Clock.updateShowSeconds", t)
        }
    }

    /**
     * Force-enable the clock seconds display
     */
    private fun forceEnableClockSeconds(clockInstance: Any) {
        try {
            val cl = clockInstance.javaClass
            // Set the seconds display flag
            cl.getDeclaredField("mShowSeconds").setBoolean(clockInstance, true)

            // Ensure the seconds update handler exists
            val handlerField: java.lang.reflect.Field = cl.getDeclaredField("mSecondsHandler")
            handlerField.isAccessible = true
            val secondsHandler = handlerField.get(clockInstance)
            if (secondsHandler == null) {
                val clLoader = clockInstance.javaClass.classLoader
                val handlerClass = clLoader?.loadClass("android.os.Handler")
                    ?: throw IllegalStateException("ClassLoader is null")
                val newHandler = handlerClass.getDeclaredConstructor().newInstance()
                handlerField.set(clockInstance, newHandler)
            }

            // Trigger the seconds display update
            cl.getDeclaredMethod("updateShowSeconds").invoke(clockInstance)

            logger.debug("Force enabled clock seconds display")
        } catch (t: Throwable) {
            logger.error("Force enable seconds failed", t)
        }
    }

    companion object {
        private const val CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock"
    }
}
