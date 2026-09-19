package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Force-enable the long screenshot (scroll capture) chip in the SystemUI
 * screenshot overlay.
 *
 * The stock gate is ScreenshotView#canLongScreenshot(), which requires the
 * foreground app to report scroll-capture ability via
 * IApplicationThread#checkLongScreenshotAbility and short-circuits in PC mode,
 * area screenshot, freeform/split-screen, etc. When it returns false the
 * "share_long_screenshot" chip is rendered disabled
 * (screenshot_long_press_disable icon + setClickable(false)).
 *
 * Two hooks:
 * 1. canLongScreenshot() is forced to return true, so the chip is set enabled
 *    with the normal icon and click listener in both the onFinishInflate and
 *    createScreenshotActionsShadeAnimation paths.
 * 2. After initApplicationThread() runs (where the stock code probes the
 *    foreground app via checkLongScreenshotAbility), the
 *    mCanBeLongScreenshotMotoFlag field is forced to true so click handlers
 *    and the scroll-capture path that re-read the field stay consistent.
 *
 * Note: forcing the chip enabled does NOT guarantee the actual long screenshot
 * capture works well — the underlying app may genuinely not support scroll
 * capture.
 */
@SuppressLint("PrivateApi")
class ForceLongScreenshot : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val ID_CAN_LONG_SCREENSHOT = "force_long_screenshot_can"
        private const val ID_INIT_APPLICATION_THREAD = "force_long_screenshot_init"
    }

    override fun getModuleName(): String = PreferenceKeys.FORCE_LONG_SCREENSHOT.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logger.info("Loading module ForceLongScreenshot.")

        val screenshotViewClass = try {
            param.defaultClassLoader
                .loadClass("com.android.systemui.screenshot.ScreenshotView")
        } catch (e: Throwable) {
            logger.error("ForceLongScreenshot: ScreenshotView class not found", e)
            return
        }

        hookCanLongScreenshot(screenshotViewClass)
        hookInitApplicationThread(screenshotViewClass)
    }

    private fun hookCanLongScreenshot(screenshotViewClass: Class<*>) {
        try {
            val method = findMethod(screenshotViewClass, "canLongScreenshot")
            hookWithId(method, ID_CAN_LONG_SCREENSHOT) { true }
            logger.info("ForceLongScreenshot: canLongScreenshot hooked successfully.")
        } catch (e: Throwable) {
            logger.error("ForceLongScreenshot: failed to hook canLongScreenshot", e)
        }
    }

    private fun hookInitApplicationThread(screenshotViewClass: Class<*>) {
        try {
            val method = findMethod(screenshotViewClass, "initApplicationThread")
            val flagField = findField(screenshotViewClass, "mCanBeLongScreenshotMotoFlag")
            hookWithId(method, ID_INIT_APPLICATION_THREAD) { chain ->
                chain.proceed()
                try {
                    flagField.set(chain.thisObject, true)
                    logger.debug("ForceLongScreenshot: mCanBeLongScreenshotMotoFlag forced true")
                } catch (e: Throwable) {
                    logger.error("ForceLongScreenshot: failed to force ability flag", e)
                }
            }
            logger.info("ForceLongScreenshot: initApplicationThread hooked successfully.")
        } catch (e: Throwable) {
            logger.error("ForceLongScreenshot: failed to hook initApplicationThread", e)
        }
    }
}
