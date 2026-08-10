package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 访客模式控制Hook模块
 * 修复系统UI中自动创建访客用户的逻辑
 * 当用户切换器被禁用时，阻止自动添加访客用户
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
                // 获取应用上下文
                val context = chain.thisObject.javaClass
                    .getDeclaredField("applicationContext").get(chain.thisObject) as Context?

                // 检查用户切换器是否启用
                val userSwitcherEnabled = Settings.Global.getInt(
                    context!!.contentResolver,
                    "user_switcher_enabled",
                    0
                )

                // 如果用户切换器被禁用，则不允许添加访客
                if (userSwitcherEnabled == 0) {
                    logger.debug("阻止自动添加访客用户 - 用户切换器已禁用")
                    return@hookWithId false
                }
                chain.proceed()
            }

            logger.info("成功Hook访客用户交互器")
        } catch (t: Throwable) {
            logger.error("Hook访客用户交互器失败", t)
        }
    }
}
