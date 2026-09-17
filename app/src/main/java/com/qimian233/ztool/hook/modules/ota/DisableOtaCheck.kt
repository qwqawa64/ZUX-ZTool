package com.qimian233.ztool.hook.modules.ota

import android.view.Menu
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hook module to disable the Lenovo OTA check.
 * Function: force-show the local install menu item and bypass the counter check logic.
 */
class DisableOtaCheck : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_OTA_CHECK.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.OTA.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        logger.info("Starting to hook com.lenovo.ota - enabling local install service")

        try {
            hookOnCreateOptionsMenu(classLoader)
            hookOnPrepareOptionsMenu(classLoader)
            hookClickCountCallBack(classLoader)

            logger.info("All OTA check disable hooks set up")
        } catch (e: Exception) {
            logger.error("Error initializing OTA check disable module", e)
        }
    }

    /**
     * Hooks onCreateOptionsMenu to ensure the menu item is not hidden by default.
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
                    // Find the local install menu item and make it visible
                    val menuLocalInstallField = findField(rClass, "memu_localInstall")
                    val menuLocalInstallId = menuLocalInstallField.getInt(null)

                    val localInstallItem = menu.findItem(menuLocalInstallId)
                    if (localInstallItem != null) {
                        localInstallItem.isVisible = true
                        logger.debug("Enabled local install menu in onCreateOptionsMenu")
                    }
                } catch (e: Exception) {
                    logger.error("Error in onCreateOptionsMenu hook", e)
                }
                result
            }
        } catch (e: Exception) {
            logger.error("Failed to set up onCreateOptionsMenu hook", e)
        }
    }

    /**
     * Hooks onPrepareOptionsMenu to bypass the conditional check.
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
                    // Get the menu item ID via reflection
                    val menuLocalInstallField = findField(rClass, "memu_localInstall")
                    val menuLocalInstallId = menuLocalInstallField.getInt(null)

                    val localInstallItem = menu.findItem(menuLocalInstallId)
                    if (localInstallItem != null) {
                        // Force visible, bypassing the original mCount >= 6 check
                        localInstallItem.isVisible = true
                        logger.debug("Forced local install menu visible in onPrepareOptionsMenu")
                    }

                    // Also set the counter to 6 to keep other related logic working
                    val mCountField = mainActivityClass.getDeclaredField("mCount")
                    mCountField.isAccessible = true
                    mCountField.setInt(chain.thisObject, 6)
                } catch (e: Exception) {
                    logger.error("Error in onPrepareOptionsMenu hook", e)
                }
                result
            }
        } catch (e: Exception) {
            logger.error("Failed to set up onPrepareOptionsMenu hook", e)
        }
    }

    /**
     * Hooks clickCountCallBack to ensure the counter always satisfies the condition.
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
                // Set the counter to 6 directly before the call
                mCountField.setInt(chain.thisObject, 6)
                logger.debug("Forced counter to 6 before clickCountCallBack")
                chain.proceed()
            }
        } catch (e: Exception) {
            logger.error("Failed to set up clickCountCallBack hook", e)
        }
    }

    companion object {
        private const val MAIN_ACTIVITY = "com.lenovo.row.ota.core.d.ui.MainActivity"
    }
}
