package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Field

/**
 * SystemUI notification icon limit hook module.
 * Function: modifies the maximum display count limit for status bar notification icons.
 * Supports the Android 12+ SystemUI architecture.
 */
@SuppressLint("PrivateApi")
class NotificationIconHook : AppHookModule() {

    private var newMaxIcons = 0

    override fun getModuleName(): String = PreferenceKeys.NOTIFICATION_ICON_LIMIT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (ScopeKeys.SYSTEM_UI.packageName == packageName) {
            val prefs: SharedPreferences = xposed.getRemotePreferences(PREFS_NAME)
            newMaxIcons = prefs.getInt("notify_num_size", 4)
            hookSystemUIIconLimit(classLoader)
        }
    }

    private fun hookSystemUIIconLimit(classLoader: ClassLoader) {
        logger.info("Hooking SystemUI notification icon limit, max icons: $newMaxIcons")

        try {
            // Hook 1: modify the max icon count obtained from resources
//            hookResourceInteger(classLoader);

            // Hook 2: modify the maxIcons field of NotificationIconContainerStatusBarViewModel
            hookViewModelConstructor(classLoader)

            // Hook 3: modify the NotificationIconsViewData constructor to apply the count limit
            hookViewDataConstructor(classLoader)

            logger.info("SystemUI notification icon limit hooks installed")
        } catch (e: Throwable) {
            logger.error("Error during SystemUI hook", e)
        }
    }

    private fun hookViewModelConstructor(classLoader: ClassLoader) {
        try {
            val viewModelClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel"
            )

            val ctor: Constructor<*> = viewModelClass.getDeclaredConstructor(
                classLoader.loadClass("kotlin.coroutines.CoroutineContext"),
                classLoader.loadClass("com.android.systemui.statusbar.phone.domain.interactor.DarkIconInteractor"),
                classLoader.loadClass("com.android.systemui.statusbar.notification.icon.domain.interactor.StatusBarNotificationIconsInteractor"),
                classLoader.loadClass("com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationIconInteractor"),
                classLoader.loadClass("com.android.systemui.keyguard.domain.interactor.KeyguardInteractor"),
                android.content.res.Resources::class.java,
                classLoader.loadClass("com.android.systemui.shade.domain.interactor.ShadeInteractor")
            )

            hookWithId(ctor, "ctor_1") { chain ->
                // after constructor: chain.proceed() then set field
                chain.proceed()
                try {
                    val myField: Field = chain.thisObject.javaClass.getDeclaredField("maxIcons")
                    myField.isAccessible = true
                    myField.setInt(chain.thisObject, newMaxIcons)
                    logger.debug("Successfully set ViewModel maxIcons to $newMaxIcons")
                } catch (e: Exception) {
                    logger.error("Failed to modify ViewModel maxIcons field", e)
                }
                null
            }

            logger.info("ViewModel constructor hook applied")
        } catch (e: Throwable) {
            logger.warn("ViewModel class not found, system version may be incompatible: " + e.message)
        }
    }

    private fun hookViewDataConstructor(classLoader: ClassLoader) {
        try {
            val viewDataClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconsViewData"
            )

            val ctor: Constructor<*> = viewDataClass.getDeclaredConstructor(
                List::class.java,
                Int::class.javaPrimitiveType,
                classLoader.loadClass("com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconsViewData\$LimitType")
            )

            hookWithId(ctor, "ctor_2") { chain ->
                try {
                    // Get the icon list
                    val iconList = chain.args[0]
                    val listSize = getListSize(iconList)

                    // Use NEW_MAX_ICONS as the limit, but not exceeding the actual icon count
                    val effectiveLimit = minOf(newMaxIcons, listSize)
                    val currentLimit = chain.args[1] as Int

                    // Only modify when the current limit differs from our effective limit
                    if (currentLimit != effectiveLimit) {
                        logger.debug("Icon limit changed $currentLimit -> $effectiveLimit (total icons: $listSize)")
                        return@hookWithId chain.proceed(arrayOf(iconList, effectiveLimit, chain.args[2]))
                    }
                } catch (e: Exception) {
                    logger.error("Error during ViewData hook", e)
                }
                chain.proceed()
            }

            logger.info("ViewData constructor hook applied")
        } catch (e: Throwable) {
            logger.warn("ViewData class not found, system version may be incompatible: " + e.message)
        }
    }

    // Helper: get the list size
    private fun getListSize(list: Any?): Int {
        return try {
            list!!.javaClass.getDeclaredMethod("size").invoke(list) as Int
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        private const val PREFS_NAME = "StatusBar_notifyNumSize"
    }
}
