package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * SystemUI状态栏时钟秒显示Hook模块
 * 强制启用系统状态栏时钟的秒显示功能
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
            // Hook 1: 在 Clock 对象创建时强制启用秒显示
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
            // Hook 2: 防止系统设置覆盖我们的修改
            val onTuningMethod: Method = classLoader.loadClass(CLOCK_CLASS)
                .getDeclaredMethod("onTuningChanged", String::class.java, String::class.java)
            hookWithId(onTuningMethod, "on_tuning") { chain ->
                val key = chain.args[0] as String
                if ("clock_seconds" == key) {
                    // 强制覆盖设置为开启
                    val clockCls = chain.thisObject.javaClass
                    clockCls.getDeclaredField("mShowSeconds").setBoolean(chain.thisObject, true)
                    // 调用原始方法，但修改第二个参数为 "1"
                    val result = chain.proceed(arrayOf(key, "1"))
                    // 确保秒显示更新
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
            // Hook 3: 直接修改 updateShowSeconds 方法
            val updateMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("updateShowSeconds")
            hookWithId(updateMethod, "update") { chain ->
                // 强制启用秒显示
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
     * 强制启用时钟秒显示功能
     */
    private fun forceEnableClockSeconds(clockInstance: Any) {
        try {
            val cl = clockInstance.javaClass
            // 设置秒显示标志
            cl.getDeclaredField("mShowSeconds").setBoolean(clockInstance, true)

            // 确保秒更新处理器存在
            val handlerField: java.lang.reflect.Field = cl.getDeclaredField("mSecondsHandler")
            handlerField.isAccessible = true
            val secondsHandler = handlerField.get(clockInstance)
            if (secondsHandler == null) {
                val clLoader = clockInstance.javaClass.classLoader
                val handlerClass = clLoader.loadClass("android.os.Handler")
                val newHandler = handlerClass.getDeclaredConstructor().newInstance()
                handlerField.set(clockInstance, newHandler)
            }

            // 触发秒显示更新
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
