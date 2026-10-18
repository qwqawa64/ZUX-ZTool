package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.view.View
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hides the status-bar notification-icon overflow dot.
 *
 * The dot is a visible state of the icon view that would hold the next notification,
 * not a separate view. Background: docs/research/systemui_notification_overflow_dot.md.
 */
class NotificationOverflowDotHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.HIDE_NOTIFICATION_OVERFLOW_DOT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            val iconViewClass = classLoader.loadClass(STATUS_BAR_ICON_VIEW_CLASS)
            // The third parameter is a compiler-generated lambda class, so match by arity.
            val setVisibleState = iconViewClass.declaredMethods.firstOrNull {
                it.name == "setVisibleState" && it.parameterTypes.size == 4
            } ?: run {
                logger.warn("StatusBarIconView.setVisibleState(int, boolean, ..., long) not found")
                return
            }

            val statusBarIconsId = try {
                classLoader.loadClass(SYSTEM_UI_R_ID_CLASS)
                    .getField("notificationIcons")
                    .getInt(null)
            } catch (t: Throwable) {
                logger.warn("notificationIcons id unavailable, applying to every icon container: ${t.message}")
                null
            }

            hookWithId(setVisibleState, "notification_overflow_dot") { chain ->
                val parent = (chain.thisObject as? View)?.parent as? View
                if (chain.args[0] != DOT_STATE ||
                    (statusBarIconsId != null && parent?.id != statusBarIconsId)
                ) {
                    return@hookWithId chain.proceed()
                }
                val args = chain.args.toMutableList()
                args[0] = HIDDEN_STATE
                chain.proceed(args.toTypedArray())
            }
            logger.info("Notification overflow dot hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to install notification overflow dot hook", t)
        }
    }

    companion object {
        private const val STATUS_BAR_ICON_VIEW_CLASS = "com.android.systemui.statusbar.StatusBarIconView"
        private const val SYSTEM_UI_R_ID_CLASS = "com.android.wm.shell.R\$id"
        // StatusIconDisplayable states: 0 = ICON, 1 = DOT, 2 = HIDDEN
        private const val DOT_STATE = 1
        private const val HIDDEN_STATE = 2
    }
}
