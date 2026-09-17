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
 * Lock screen OwnerInfo auto-update hook module (Settings process side).
 * <p>
 * Split from [OwnerInfoHook]: handles only the com.android.settings process,
 * registering a screen-state broadcast receiver when Settings pages / activities resume.
 * Core update logic lives in [OwnerInfoUpdater].
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
        logger.info("Hooking Settings package")

        // Hook point 1: register in Settings SecuritySettings
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
            logger.info("Successfully hooked SecuritySettings.onResume")
        } catch (e: Throwable) {
            logger.error("Failed to hook SecuritySettings", e)
        }

        // Hook point 2: register screen state listener in ActivityThread
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
            logger.info("Successfully hooked ActivityThread.performResumeActivity")
        } catch (e: Throwable) {
            logger.error("Failed to hook ActivityThread.performResumeActivity", e)
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
                // Try to obtain the Context via reflection
                val getContextMethod: Method = contextObj!!.javaClass.getDeclaredMethod("getContext")
                context = getContextMethod.invoke(contextObj) as Context
            }

            val updater = OwnerInfoUpdater(xposed, logger)
            mScreenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    logger.debug("Received broadcast: $action")

                    if (Intent.ACTION_SCREEN_ON == action ||
                        Intent.ACTION_USER_PRESENT == action
                    ) {
                        // Update OwnerInfo when the screen turns on or the user unlocks
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
            // Update immediately once
            updater.updateOwnerInfo(context, classLoader)
        } catch (e: Throwable) {
            logger.error("Failed to register broadcast receiver", e)
        }
    }
}
