package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Guest mode control hook module.
 * Fixes the auto guest user creation logic in SystemUI.
 * Prevents automatically adding a guest user when the user switcher is disabled.
 */
class GuestModeController : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.GUEST_MODE_CONTROLLER.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookGuestUserInteractor(classLoader)
    }

    private fun hookGuestUserInteractor(classLoader: ClassLoader) {
        try {
            @SuppressLint("PrivateApi") val isAllowedMethod = classLoader
                .loadClass("com.android.systemui.user.domain.interactor.GuestUserInteractor")
                .getDeclaredMethod("isDeviceAllowedToAddGuest")
            hookWithId(isAllowedMethod, "is_allowed") { chain ->
                // Get the application context
                val context = chain.thisObject.javaClass
                    .getDeclaredField("applicationContext").get(chain.thisObject) as Context?

                // Check whether the user switcher is enabled
                val userSwitcherEnabled = Settings.Global.getInt(
                    context!!.contentResolver,
                    "user_switcher_enabled",
                    0
                )

                // If the user switcher is disabled, do not allow adding a guest
                if (userSwitcherEnabled == 0) {
                    logger.debug("Blocked automatic guest user addition - user switcher disabled")
                    return@hookWithId false
                }
                chain.proceed()
            }

            logger.info("Successfully hooked GuestUserInteractor")
        } catch (t: Throwable) {
            logger.error("Failed to hook GuestUserInteractor", t)
        }
    }
}
