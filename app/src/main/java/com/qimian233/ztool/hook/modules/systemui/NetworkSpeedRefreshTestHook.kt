package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import com.qimian233.ztool.hook.base.BaseHookModule
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
class NetworkSpeedRefreshTestHook : BaseHookModule() {

    companion object {
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
    }

    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return

        log("[SpeedRefreshTest] Module loaded, hooking Handler.sendEmptyMessageDelayed...")

        hookSendEmptyMessageDelayed(param.defaultClassLoader)
    }

    private fun hookSendEmptyMessageDelayed(classLoader: ClassLoader) {
        try {
            val handlerClass = classLoader.loadClass("android.os.Handler")

            // Hook sendEmptyMessageDelayed(int, long)
            val method: Method = findMethod(
                handlerClass,
                "sendEmptyMessageDelayed",
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )

            xposed.hook(method).intercept { chain ->
                val handler = chain.thisObject
                val what = chain.args[0] as Int
                val delayMillis = chain.args[1] as Long

                // 判断是否为 NetworkSpeedView 的内部 Handler
                val handlerClassName = handler.javaClass.name
                if (handlerClassName.startsWith(NETWORK_SPEED_VIEW_CLASS + "$")) {
                    log(
                        "[SpeedRefreshTest] sendEmptyMessageDelayed called: " +
                            "handler=$handlerClassName, what=$what, delayMillis=$delayMillis ms"
                    )

                    // 可选：将间隔改为 1 秒验证效果（取消注释下一行）
                    // chain.proceed(arrayOf<Any>(what, 1000L))
                    // return
                }

                chain.proceed()
            }

            log("[SpeedRefreshTest] Handler.sendEmptyMessageDelayed hooked successfully.")
        } catch (e: Throwable) {
            logError("[SpeedRefreshTest] Failed to hook sendEmptyMessageDelayed", e)
        }
    }
}
