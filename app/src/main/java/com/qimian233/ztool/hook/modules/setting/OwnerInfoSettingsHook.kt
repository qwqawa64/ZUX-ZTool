package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 锁屏OwnerInfo自动更新Hook模块（Settings 进程侧）。
 * <p>
 * 由 [OwnerInfoHook] 拆分而来：仅处理 com.android.settings 进程，
 * 在 Settings 页面恢复 / Activity 恢复时注册屏幕状态广播接收器。
 * 核心更新逻辑见 [OwnerInfoUpdater]。
 * </p>
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class OwnerInfoSettingsHook : AppHookModule() {

    private var mScreenReceiver: BroadcastReceiver? = null
    private var mIsReceiverRegistered = false

    override fun getModuleName(): String = PreferenceKeys.AUTO_OWNER_INFO.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SETTINGS.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (ScopeKeys.SETTINGS.packageName == packageName) {
            hookSettingsPackage(classLoader)
        }
    }

    private fun hookSettingsPackage(classLoader: ClassLoader) {
        logger.info("开始Hook Settings包")

        // Hook点1: 在Settings的SecuritySettings中注册
        try {
            val onResumeMethod: Method = classLoader
                .loadClass("com.android.settings.SecuritySettings")
                .getDeclaredMethod("onResume")
            hookWithId(onResumeMethod, "on_resume") { chain ->
                val result = chain.proceed()
                logger.debug("SecuritySettings resumed, registering screen receiver")
                registerScreenReceiver(chain.thisObject, classLoader)
                result
            }
            logger.info("成功Hook SecuritySettings.onResume")
        } catch (e: Throwable) {
            logger.error("Hook SecuritySettings失败", e)
        }

        // Hook点2: ActivityThread中注册屏幕状态监听器
        try {
            val activityThreadClass = classLoader.loadClass("android.app.ActivityThread")
            val activityRecordClass =
                classLoader.loadClass("android.app.ActivityThread\$ActivityClientRecord")
            val performResumeMethod: Method = activityThreadClass
                .getDeclaredMethod(
                    "performResumeActivity",
                    activityRecordClass,
                    Boolean::class.javaPrimitiveType,
                    String::class.java
                )
            hookWithId(performResumeMethod, "perform_resume") { chain ->
                val result = chain.proceed()
                val activityRecord = chain.args[0]
                val activityField: Field = activityRecord.javaClass.getDeclaredField("activity")
                activityField.isAccessible = true
                val activity = activityField.get(activityRecord)

                if (activity != null) {
                    registerScreenReceiver(activity, classLoader)
                }
                result
            }
            logger.info("成功Hook ActivityThread.performResumeActivity")
        } catch (e: Throwable) {
            logger.error("Hook ActivityThread.performResumeActivity失败", e)
        }
    }

    private fun registerScreenReceiver(contextObj: Any?, classLoader: ClassLoader) {
        if (mIsReceiverRegistered) {
            return
        }

        try {
            val context: Context
            if (contextObj is Context) {
                context = contextObj
            } else {
                // 尝试通过反射获取Context
                val getContextMethod: Method = contextObj!!.javaClass.getDeclaredMethod("getContext")
                context = getContextMethod.invoke(contextObj) as Context
            }

            val updater = OwnerInfoUpdater(xposed, logger)
            mScreenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    logger.debug("收到广播: $action")

                    if (Intent.ACTION_SCREEN_ON == action ||
                        Intent.ACTION_USER_PRESENT == action
                    ) {
                        // 屏幕亮起或用户解锁时更新OwnerInfo
                        updater.updateOwnerInfo(context, classLoader)
                    }
                }
            }

            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_USER_PRESENT)

            context.registerReceiver(mScreenReceiver, filter)
            mIsReceiverRegistered = true
            logger.debug("Successfully registered screen state broadcast receiver")
            // 立即更新一次
            updater.updateOwnerInfo(context, classLoader)
        } catch (e: Throwable) {
            logger.error("注册广播接收器失败", e)
        }
    }
}
