package com.qimian233.ztool.hook.modules.safecenter

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class DisableAllVirusScans : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_ALL_VIRUS_SCANS.name

    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.LENOVO_SAFE_CENTER.packageName,
        ScopeKeys.ZUI_SAFE_CENTER.packageName
    )

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookGetManager(classLoader)
        hookDbManager(classLoader)
        disableVirusPopup(classLoader)
        blockIconNumChange(classLoader)
        blockDynamicIconSettings(classLoader)
        forceActiveViewNormalIcon(classLoader)
        disableAutoScan(classLoader)
    }

    private fun hookGetManager(classLoader: ClassLoader) {
        try {
            logger.info("Hooking safecenter to block manager initialization.")
            val managerCreatorFClass = classLoader.loadClass("tmsdk.fg.creator.ManagerCreatorF")
            val getManagerMethod: Method =
                managerCreatorFClass.getDeclaredMethod("getManager", Class::class.java)
            hookWithId(getManagerMethod, "get_manager") { null }
            logger.info("Successfully hooked safecenter!")
        } catch (e: Exception) {
            logger.error("Failed to hook scan manager! ", e)
        }
    }

    private fun hookDbManager(classLoader: ClassLoader) {
        try {
            logger.info("Set getVirusAppsCount return value to int 0")
            val antiVirusDBManagerClass = classLoader.loadClass(
                "com.lenovo.safecenter.antivirus.db.AntiVirusDBManager"
            )
            val getVirusAppsCountMethod: Method =
                antiVirusDBManagerClass.getDeclaredMethod("getVirusAppsCount")
            hookWithId(getVirusAppsCountMethod, "get_virus_apps_count") { 0 }
            logger.info("getVirusAppsCount is set to 0.")

            logger.info("Blocking AntiVirusDBHelper initialization.")
            val antiVirusDBHelperClass = classLoader.loadClass(
                "com.lenovo.safecenter.antivirus.db.AntiVirusDBHelper"
            )
            val ctor: Constructor<*> = antiVirusDBHelperClass.getDeclaredConstructor(Context::class.java)
            hookWithId(ctor, "ctor") { null }
        } catch (e: Exception) {
            logger.error("Failed to hook DB manager! ", e)
        }
    }

    private fun disableVirusPopup(classLoader: ClassLoader) {
        try {
            val notiSMSActivityClass = classLoader.loadClass(
                "com.lenovo.safecenter.antivirus.views.NotiSMSActivity"
            )
            val onCreateMethod: Method =
                notiSMSActivityClass.getDeclaredMethod("onCreate", Bundle::class.java)
            hookWithId(onCreateMethod, "on_create") { null }
            logger.info("Virus popup blocked successfully.")
        } catch (e: Exception) {
            logger.error("Failed to disable virus popup! ", e)
        }
    }

    private fun blockInstallVirusHandler(classLoader: ClassLoader) {
        try {
            val installJudgeServiceClass = classLoader.loadClass(
                "com.lenovo.safecenter.antivirus.support.InstallJudgeService"
            )
            val resultEntityClass = classLoader.loadClass("com.lesafe.utils.mode.ResultEntity")
            val dealVirusMethod: Method = installJudgeServiceClass.getDeclaredMethod(
                "dealVirus", resultEntityClass, Boolean::class.javaPrimitiveType
            )
            hookWithId(dealVirusMethod, "deal_virus") {
                logger.debug("Blocked installed-virus handler from switching SafeCenter icon")
                null
            }
            logger.info("InstallJudgeService virus icon handler blocked.")
        } catch (t: Throwable) {
            logger.error("Failed to hook InstallJudgeService virus icon handler! ", t)
        }
    }

    private fun blockIconNumChange(classLoader: ClassLoader) {
        blockInstallVirusHandler(classLoader)
        try {
            val healthScannerClass = classLoader.loadClass(
                "com.lenovo.safecenter.services.HealthScanner"
            )
            val setNumIconMethod: Method =
                healthScannerClass.getDeclaredMethod("setNumIcon", Int::class.javaPrimitiveType)
            hookWithId(setNumIconMethod, "set_num_icon") { chain ->
                val originalCount = chain.args[0] as Int
                if (originalCount != 0) {
                    logger.debug("Forced HealthScanner icon warning count $originalCount to 0")
                }
                chain.proceed(arrayOf(0))
            }
            logger.info("HealthScanner icon count changes blocked.")
        } catch (t: Throwable) {
            logger.error("Failed to hook HealthScanner icon count! ", t)
        }
    }

    private fun blockDynamicIconSettings(classLoader: ClassLoader) {
        hookSystemPutInt()
        hookSystemGetInt()
    }

    private fun hookSystemPutInt() {
        try {
            val putIntMethod: Method = android.provider.Settings.System::class.java.getDeclaredMethod(
                "putInt", ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(putIntMethod, "put_int") { chain ->
                val key = chain.args[1] as String
                if (isSafeCenterIconSetting(key)) {
                    val value = chain.args[2] as Int
                    if (value != 0) {
                        logger.debug("Blocked SafeCenter dynamic icon setting $key=$value")
                    }
                    chain.proceed(arrayOf(chain.args[0], key, 0))
                } else {
                    chain.proceed()
                }
            }
            logger.info("SafeCenter dynamic icon Settings.System.putInt writes blocked.")
        } catch (t: Throwable) {
            logger.error("Failed to hook dynamic icon Settings.System.putInt! ", t)
        }
    }

    private fun hookSystemGetInt() {
        try {
            val getIntMethod: Method = android.provider.Settings.System::class.java.getDeclaredMethod(
                "getInt", ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(getIntMethod, "get_int") { chain ->
                val key = chain.args[1] as String
                if (isSafeCenterIconSetting(key)) {
                    0
                } else {
                    chain.proceed()
                }
            }
            logger.info("SafeCenter dynamic icon Settings.System.getInt reads forced to normal.")
        } catch (t: Throwable) {
            logger.error("Failed to hook dynamic icon Settings.System.getInt! ", t)
        }
    }

    private fun forceActiveViewNormalIcon(classLoader: ClassLoader) {
        try {
            val activeViewClass = classLoader.loadClass(
                "com.lenovo.safecenter.MainTab.ActiveView"
            )
            val getBitmapDrawableMethod: Method = activeViewClass.getDeclaredMethod(
                "getBitmapDrawable", Context::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(getBitmapDrawableMethod, "get_bitmap_drawable") { chain ->
                chain.proceed(arrayOf(chain.args[0], 0))
            }
            logger.info("ActiveView dynamic icon rendering forced to normal.")
        } catch (t: Throwable) {
            logger.error("Failed to hook ActiveView dynamic icon rendering! ", t)
        }
    }

    private fun isSafeCenterIconSetting(key: String): Boolean {
        return KEY_DYNAMIC_ICONS == key || KEY_SAFE_CENTER_ICON == key
    }

    private fun disableAutoScan(classLoader: ClassLoader) {
        try {
            val autoOverallScanClass = classLoader.loadClass(
                "com.lenovo.safecenter.antivirus.autoscan.AutoOverallScan"
            )
            val localOverallScanVirusMethod: Method = autoOverallScanClass.getDeclaredMethod(
                "LocalOverallScanVirus", Context::class.java
            )
            hookWithId(localOverallScanVirusMethod, "local_overall_scan_virus") {
                // 直接返回null，阻止自动扫描执行
                logger.debug("Auto virus scan blocked at entry point")
                null
            }
            logger.info("Successfully hooked SafeCenter auto scan entry")
        } catch (t: Throwable) {
            logger.error("Failed to hook SafeCenter auto scan", t)
        }
    }

    companion object {
        private const val KEY_DYNAMIC_ICONS = "com.zui.safecenter.dynamic_icons"
        private const val KEY_SAFE_CENTER_ICON = "safecentericon"
    }
}
