package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import com.qimian233.ztool.hook.modules.setting.OwnerInfoUpdater
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method

/**
 * Lock screen OwnerInfo auto-update hook module (system server side).
 * <p>
 * Split from [OwnerInfoHook]: listens in system server to
 * PowerManagerService.setPowerState / userActivity and
 * ContextImpl.registerReceiver, triggering OwnerInfo updates.
 * Core update logic lives in [OwnerInfoUpdater].
 * </p>
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class OwnerInfoSystemHook : SystemHookModule() {

    private var mScreenReceiver: BroadcastReceiver? = null
    private var mIsReceiverRegistered = false

    override fun getModuleName(): String = PreferenceKeys.AUTO_OWNER_INFO.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        hookSystemPackage(classLoader)
    }

    private fun hookSystemPackage(classLoader: ClassLoader) {
        logger.info("Hooking System package")
        val updater = OwnerInfoUpdater(xposed, logger)

        // Hook point 1: listen to screen state in PowerManagerService
        try {
            val setPowerStateMethod: Method = classLoader
                .loadClass("com.android.server.power.PowerManagerService")
                .getDeclaredMethod("setPowerState", Boolean::class.javaPrimitiveType)
            hookWithId(setPowerStateMethod, "set_power_state") { chain ->
                val result = chain.proceed()
                val screenOn = chain.args[0] as Boolean
                logger.debug("Power state changed, screen on: $screenOn")

                if (screenOn) {
                    // Update OwnerInfo when the screen turns on
                    updater.updateOwnerInfo(null, classLoader)
                }
                result
            }
            logger.info("Successfully hooked PowerManagerService.setPowerState")
        } catch (e: Throwable) {
            logger.error("Failed to hook PowerManagerService.setPowerState", e)
        }

        // Hook point 2: user activity listener
        try {
            val userActivityMethod: Method = classLoader
                .loadClass("com.android.server.power.PowerManagerService")
                .getDeclaredMethod(
                    "userActivity",
                    Int::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            hookWithId(userActivityMethod, "user_activity") { chain ->
                val result = chain.proceed()
                val event = chain.args[0] as Int
                // User activity events, including screen touches, key presses, etc.
                if (event == 0 || event == 2 || event == 3) { // POWER_BUTTON, TOUCH, etc.
                    logger.debug("User activity detected, updating OwnerInfo")
                    updater.updateOwnerInfo(null, classLoader)
                }
                result
            }
            logger.info("Successfully hooked PowerManagerService.userActivity")
        } catch (e: Throwable) {
            logger.error("Failed to hook PowerManagerService.userActivity", e)
        }

        // Hook point 3: register broadcast receiver in ContextImpl
        try {
            val registerReceiverMethod: Method = classLoader
                .loadClass("android.app.ContextImpl")
                .getDeclaredMethod(
                    "registerReceiver",
                    BroadcastReceiver::class.java,
                    IntentFilter::class.java
                )
            hookWithId(registerReceiverMethod, "register_receiver") { chain ->
                // Check if this is our own receiver to avoid duplicate registration
                if (chain.args[0] == mScreenReceiver) {
                    return@hookWithId chain.proceed()
                }

                val filter = chain.args[1] as IntentFilter
                if (hasScreenActions(filter)) {
                    // This filter contains screen actions, so we can register our own receiver here
                    registerScreenReceiver(chain.thisObject, classLoader, updater)
                }
                chain.proceed()
            }
            logger.info("Successfully hooked ContextImpl.registerReceiver")
        } catch (e: Throwable) {
            logger.error("Failed to hook ContextImpl.registerReceiver", e)
        }
    }

    private fun hasScreenActions(filter: IntentFilter): Boolean {
        return try {
            // Check whether the filter contains screen-related actions
            val actions = filter.actionsIterator()
            while (actions != null && actions.hasNext()) {
                val action = actions.next()
                if (Intent.ACTION_SCREEN_ON == action ||
                    Intent.ACTION_SCREEN_OFF == action ||
                    Intent.ACTION_USER_PRESENT == action
                ) {
                    return true
                }
            }
            false
        } catch (e: Throwable) {
            logger.error("Error checking IntentFilter actions", e)
            false
        }
    }

    private fun registerScreenReceiver(
        contextObj: Any?,
        classLoader: ClassLoader,
        updater: OwnerInfoUpdater
    ) {
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
