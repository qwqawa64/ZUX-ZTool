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
 * SystemUI通知图标限制Hook模块
 * 功能：修改状态栏通知图标的最大显示数量限制
 * 支持Android 12+的SystemUI架构
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
        logger.info("开始 Hook SystemUI 通知图标限制，设置最大图标数: $newMaxIcons")

        try {
            // Hook 1: 修改资源获取的最大图标数量
//            hookResourceInteger(classLoader);

            // Hook 2: 修改 NotificationIconContainerStatusBarViewModel 的 maxIcons 字段
            hookViewModelConstructor(classLoader)

            // Hook 3: 修改 NotificationIconsViewData 构造函数，应用数量限制
            hookViewDataConstructor(classLoader)

            logger.info("SystemUI 通知图标限制Hook设置完成")
        } catch (e: Throwable) {
            logger.error("SystemUI Hook过程中发生错误", e)
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
                    logger.debug("成功修改 ViewModel maxIcons 为 $newMaxIcons")
                } catch (e: Exception) {
                    logger.error("修改 ViewModel maxIcons 字段失败", e)
                }
                null
            }

            logger.info("ViewModel构造函数Hook设置成功")
        } catch (e: Throwable) {
            logger.warn("找不到 ViewModel 类，可能系统版本不兼容: " + e.message)
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
                    // 获取图标列表
                    val iconList = chain.args[0]
                    val listSize = getListSize(iconList)

                    // 使用NEW_MAX_ICONS作为限制，但不超过实际图标数量
                    val effectiveLimit = minOf(newMaxIcons, listSize)
                    val currentLimit = chain.args[1] as Int

                    // 只有当当前限制不等于我们设置的有效限制时才修改
                    if (currentLimit != effectiveLimit) {
                        logger.debug("修改图标限制 $currentLimit -> $effectiveLimit (图标总数: $listSize)")
                        return@hookWithId chain.proceed(arrayOf(iconList, effectiveLimit, chain.args[2]))
                    }
                } catch (e: Exception) {
                    logger.error("ViewData Hook过程中发生错误", e)
                }
                chain.proceed()
            }

            logger.info("ViewData构造函数Hook设置成功")
        } catch (e: Throwable) {
            logger.warn("找不到 ViewData 类，可能系统版本不兼容: " + e.message)
        }
    }

    // 辅助方法：获取列表大小
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
