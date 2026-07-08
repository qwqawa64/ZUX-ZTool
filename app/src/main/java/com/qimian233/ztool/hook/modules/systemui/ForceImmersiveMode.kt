package com.qimian233.ztool.hook.modules.systemui

import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * 强制沉浸式模式 Hook。
 *
 * 通过拦截 SystemUI 的 CommandQueue.setWindowState 方法，
 * 当应用试图显示状态栏/导航栏时，强制将其设为沉浸式（隐藏）状态。
 * 用户仍可通过从顶部/底部滑动手势临时唤出系统栏。
 */
class ForceImmersiveMode : BaseHookModule() {

    companion object {
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"

        // 系统栏窗口状态常量
        // state=0: 显示, state=1: 过渡态, state=2: 沉浸式隐藏（可滑动唤出）
        private const val WINDOW_STATE_SHOWING = 0
        private const val WINDOW_STATE_HIDDEN = 2
    }

    override fun getModuleName(): String = "force_immersive_mode"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        log("Loading module ForceImmersiveMode.")
        hookSetWindowState(param.defaultClassLoader)
    }

    private fun hookSetWindowState(classLoader: ClassLoader) {
        try {
            val commandQueueClass = classLoader.loadClass(
                "com.android.systemui.statusbar.CommandQueue"
            )

            // setWindowState(int displayId, int type, int state)
            val setWindowStateMethod: Method = findMethod(
                commandQueueClass,
                "setWindowState",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            xposed.hook(setWindowStateMethod).intercept { chain ->
                val args = chain.args
                val displayId = args[0] as Int
                val type = args[1] as Int
                val state = args[2] as Int

                if (state == WINDOW_STATE_SHOWING) {
                    if (DEBUG) {
                        log(
                            "ForceImmersiveMode: intercepting setWindowState(" +
                                "displayId=$displayId, type=$type, state=$state" +
                                ") -> forcing state=$WINDOW_STATE_HIDDEN"
                        )
                    }
                    chain.proceed(arrayOf(displayId, type, WINDOW_STATE_HIDDEN))
                } else {
                    chain.proceed()
                }
            }

            log("ForceImmersiveMode: CommandQueue.setWindowState hooked successfully.")
        } catch (e: Exception) {
            logError("Failed to hook CommandQueue.setWindowState", e)
        }
    }
}
