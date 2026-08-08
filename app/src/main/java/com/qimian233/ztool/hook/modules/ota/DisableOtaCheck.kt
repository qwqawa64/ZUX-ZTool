package com.qimian233.ztool.hook.modules.ota

import android.view.Menu
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 禁用联想OTA检查Hook模块
 * 功能：强制显示本地安装菜单项，绕过计数器检查逻辑
 */
class DisableOtaCheck : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_OTA_CHECK.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.OTA.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        logger.info("开始挂钩 com.lenovo.ota - 开启本地安装服务")

        try {
            hookOnCreateOptionsMenu(classLoader)
            hookOnPrepareOptionsMenu(classLoader)
            hookClickCountCallBack(classLoader)

            logger.info("所有OTA检查禁用钩子设置完成")
        } catch (e: Exception) {
            logger.error("初始化OTA检查禁用模块时出错", e)
        }
    }

    /**
     * 钩住 onCreateOptionsMenu 方法，确保菜单项不被默认隐藏
     */
    private fun hookOnCreateOptionsMenu(classLoader: ClassLoader) {
        try {
            val mainActivityClass = classLoader.loadClass(MAIN_ACTIVITY)
            val onCreateOptionsMenu =
                mainActivityClass.getDeclaredMethod("onCreateOptionsMenu", Menu::class.java)
            val rClass = classLoader.loadClass($$"com.lenovo.ota.R$id")

            hookWithId(
                onCreateOptionsMenu,
                "on_create_options_menu"
            ) { chain ->
                val result = chain.proceed()
                try {
                    val menu = chain.args[0] as Menu
                    // 找到本地安装菜单项并设置为可见
                    val menuLocalInstallField = findField(rClass, "memu_localInstall")
                    val menuLocalInstallId = menuLocalInstallField.getInt(null)

                    val localInstallItem = menu.findItem(menuLocalInstallId)
                    if (localInstallItem != null) {
                        localInstallItem.isVisible = true
                        logger.debug("在 onCreateOptionsMenu 中启用本地安装菜单")
                    }
                } catch (e: Exception) {
                    logger.error("onCreateOptionsMenu 钩子执行错误", e)
                }
                result
            }
        } catch (e: Exception) {
            logger.error("设置 onCreateOptionsMenu 钩子失败", e)
        }
    }

    /**
     * 钩住 onPrepareOptionsMenu 方法，绕过条件检查
     */
    private fun hookOnPrepareOptionsMenu(classLoader: ClassLoader) {
        try {
            val mainActivityClass = classLoader.loadClass(MAIN_ACTIVITY)
            val onPrepareOptionsMenu =
                mainActivityClass.getDeclaredMethod("onPrepareOptionsMenu", Menu::class.java)
            val rClass = classLoader.loadClass($$"com.lenovo.ota.R$id")

            hookWithId(
                onPrepareOptionsMenu,
                "on_prepare_options_menu"
            ) { chain ->
                val result = chain.proceed()
                try {
                    val menu = chain.args[0] as Menu
                    // 通过反射获取菜单项ID
                    val menuLocalInstallField = findField(rClass, "memu_localInstall")
                    val menuLocalInstallId = menuLocalInstallField.getInt(null)

                    val localInstallItem = menu.findItem(menuLocalInstallId)
                    if (localInstallItem != null) {
                        // 强制设置为可见，绕过原有的 mCount >= 6 检查
                        localInstallItem.isVisible = true
                        logger.debug("在 onPrepareOptionsMenu 中强制显示本地安装菜单")
                    }

                    // 同时设置计数器为6，确保其他相关逻辑正常工作
                    val mCountField = mainActivityClass.getDeclaredField("mCount")
                    mCountField.isAccessible = true
                    mCountField.setInt(chain.thisObject, 6)
                } catch (e: Exception) {
                    logger.error("onPrepareOptionsMenu 钩子执行错误", e)
                }
                result
            }
        } catch (e: Exception) {
            logger.error("设置 onPrepareOptionsMenu 钩子失败", e)
        }
    }

    /**
     * 钩住 clickCountCallBack 方法，确保计数器始终满足条件
     */
    private fun hookClickCountCallBack(classLoader: ClassLoader) {
        try {
            val mainActivityClass = classLoader.loadClass(MAIN_ACTIVITY)
            val clickCountCallBack = mainActivityClass.getDeclaredMethod("clickCountCallBack")
            val mCountField = findField(mainActivityClass, "mCount")

            hookWithId(
                clickCountCallBack,
                "click_count_call_back"
            ) { chain ->
                // 在调用前直接设置计数器为6
                mCountField.setInt(chain.thisObject, 6)
                logger.debug("在 clickCountCallBack 前强制设置计数器为6")
                chain.proceed()
            }
        } catch (e: Exception) {
            logger.error("设置 clickCountCallBack 钩子失败", e)
        }
    }

    companion object {
        private const val MAIN_ACTIVITY = "com.lenovo.row.ota.core.d.ui.MainActivity"
    }
}
