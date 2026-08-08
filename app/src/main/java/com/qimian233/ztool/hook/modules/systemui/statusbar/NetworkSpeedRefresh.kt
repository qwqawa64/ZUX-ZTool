package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * 测试 Hook — 验证 NetworkSpeedView 中 sendEmptyMessageDelayed 控制刷新间隔的猜想。
 *
 * Hook android.os.Handler.sendEmptyMessageDelayed(int, long)，
 * 当调用方是 NetworkSpeedView 的内部 Handler 时，记录 what 和 delayMillis。
 *
 * getModuleName() 返回 "test_hook"，始终启用，无需前端开关。
 */
@SuppressLint("PrivateApi")
class NetworkSpeedRefresh : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
    }

    override fun getModuleName(): String = "custom_network_speed_refresh_interval"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        if (xposed.getRemotePreferences(PREFS_NAME).getBoolean("systemui_network_speed_doublelayer", false)) {
            logger.info("Will not load refresh interval hook when double layer network speed is enabled. This function is implemented in that hook!")
            return
        }
        hookSendEmptyMessageDelayed(param.defaultClassLoader)
    }

    private fun hookSendEmptyMessageDelayed(classLoader: ClassLoader) {
        try {
            val handlerClass = classLoader.loadClass("android.os.Handler")
            val refreshInterval: Long = (xposed.getRemotePreferences(PREFS_NAME).getFloat("systemui_network_speed_refresh_interval", 3.0f) * 1000.0).toLong()
            // Hook sendEmptyMessageDelayed(int, long)
            val method: Method = findMethod(
                handlerClass,
                "sendEmptyMessageDelayed",
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )

            hookWithId(method, "method") { chain ->
                val handler = chain.thisObject
                val what = chain.args[0] as Int
                val delayMillis = chain.args[1] as Long

                // 判断是否为 NetworkSpeedView 的内部 Handler
                val handlerClassName = handler.javaClass.name
                if (handlerClassName.startsWith("$NETWORK_SPEED_VIEW_CLASS$")) {
                    logger.debug(
                        "[SpeedRefreshTest] sendEmptyMessageDelayed called: " +
                            "handler=$handlerClassName, what=$what, delayMillis=$delayMillis ms"
                    )

                    // 可选：将间隔改为 1 秒验证效果（取消注释下一行）
                    chain.proceed(arrayOf<Any>(what, refreshInterval))
                } else {
                    chain.proceed()
                }
            }

            logger.info("[SpeedRefreshTest] Handler.sendEmptyMessageDelayed hooked successfully.")
        } catch (e: Throwable) {
            logger.error("[SpeedRefreshTest] Failed to hook sendEmptyMessageDelayed", e)
        }
    }
}