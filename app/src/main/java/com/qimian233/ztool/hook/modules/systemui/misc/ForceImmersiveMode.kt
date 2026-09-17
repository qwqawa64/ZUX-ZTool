package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Force immersive mode hook.
 *
 * Intercepts SystemUI CommandQueue methods controlling system bar visibility to
 * force immersive mode in all apps (status/navigation bars hidden, swipeable).
 *
 * Modern Android (13+) primarily controls bar visibility through the
 * requestedVisibleTypes parameter of onSystemBarAttributesChanged.
 * setWindowState serves as a fallback for the legacy path.
 */
@SuppressLint("PrivateApi")
class ForceImmersiveMode : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val WINDOW_STATE_SHOWING = 0
        private const val WINDOW_STATE_HIDDEN = 2
    }

    override fun getModuleName(): String = "force_immersive_mode"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logger.info("Loading module ForceImmersiveMode.")

        val commandQueueClass = param.defaultClassLoader
            .loadClass("com.android.systemui.statusbar.CommandQueue")

        hookSystemBarAttributes(commandQueueClass)
        hookSetWindowState(commandQueueClass)
    }

    /**
     * Main hook: intercept onSystemBarAttributesChanged and force requestedVisibleTypes
     * to 0 so both status bar and navigation bar are hidden.
     *
     * Method signature (8 parameters):
     *   onSystemBarAttributesChanged(
     *     int displayId,             // args[0]
     *     int appearance,            // args[1]
     *     AppearanceRegion[] regions,// args[2]
     *     boolean imeManaged,        // args[3]
     *     int behavior,              // args[4]
     *     int requestedVisibleTypes, // args[5] <- core: 0 = hide all bars
     *     String packageName,        // args[6]
     *     LetterboxDetails[] details // args[7]
     *   )
     */
    private fun hookSystemBarAttributes(commandQueueClass: Class<*>) {
        try {
            // onSystemBarAttributesChanged takes internal Android types as parameters
            // (AppearanceRegion[], LetterboxDetails[]) and cannot be referenced directly.
            // Locate the target method by name + parameter count.
            val targetMethod: Method = commandQueueClass.declaredMethods
                .first { it.name == "onSystemBarAttributesChanged" && it.parameterTypes.size == 8 }

            hookWithId(targetMethod, "target") {  chain ->
                val args = chain.args.toMutableList()
                // args[5] = requestedVisibleTypes; set to 0 to hide status bar + navigation bar
                val current = args[5] as Int
                if (current != 0) {
                    args[5] = 0
                    logger.debug("ForceImmersiveMode: onSystemBarAttributesChanged " +
                        "requestedVisibleTypes=$current -> 0 (hide all bars)")
                }
                chain.proceed(args.toTypedArray())
            }

            logger.info("ForceImmersiveMode: onSystemBarAttributesChanged hooked successfully.")
        } catch (e: Throwable) {
            logger.error("Failed to hook onSystemBarAttributesChanged", e)
        }
    }

    /**
     * Fallback hook: intercept the legacy setWindowState(int, int, int) and rewrite
     * state=0 (showing) to state=2 (immersive hidden).
     */
    private fun hookSetWindowState(commandQueueClass: Class<*>) {
        try {
            val method: Method = findMethod(
                commandQueueClass,
                "setWindowState",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            hookWithId(method, "method") {  chain ->
                val args = chain.args
                val displayId = args[0] as Int
                val type = args[1] as Int
                val state = args[2] as Int

                if (state == WINDOW_STATE_SHOWING) {
                    logger.debug("ForceImmersiveMode: setWindowState(" +
                        "displayId=$displayId, type=$type, state=${0}" +
                        ") -> forcing state=$WINDOW_STATE_HIDDEN")
                    chain.proceed(arrayOf(displayId, type, WINDOW_STATE_HIDDEN))
                } else {
                    chain.proceed()
                }
            }

            logger.info("ForceImmersiveMode: setWindowState hooked successfully.")
        } catch (e: Throwable) {
            logger.error("Failed to hook setWindowState", e)
        }
    }
}
