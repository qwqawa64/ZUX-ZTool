package com.qimian233.ztool.hook.modules.gametool

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

    /**
     * Hook module that auto-enables the game mistake-touch prevention feature.
     * Automatically enables ZUI game assistant's mistake-touch prevention
     * for specific games.
     */
class AutoMistakeTouchHook : AppHookModule() {

    // Persistent interception flag: when mistake touch is auto-enabled by this hook,
    // block writes to Settings.Global
    @Volatile
    private var mBlockPersistence = false

    override fun getModuleName(): String = PreferenceKeys.AUTO_MISTAKE_TOUCH.name

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (TARGET_PACKAGE == packageName) {
            hookGameService(classLoader)
        }
    }

    private fun hookGameService(classLoader: ClassLoader) {
        try {
            // Hook GameHelperViewController initialization
            hookGameHelperViewController(classLoader)

            // Hook ItemBlockMistakeTouch state synchronization
            hookItemBlockMistakeTouch(classLoader)

            // Hook LiveData state synchronization
            hookLiveDataPostValue(classLoader)

            // Hook setPreventMisoperation persistence interception
            hookPreventMisoperationPersistence(classLoader)

            logger.info("AutoMistakeTouch Hook initialized successfully")
        } catch (e: Throwable) {
            logger.error("Hook GameService failed", e)
        }
    }

    private fun hookGameHelperViewController(classLoader: ClassLoader) {
        try {
            val className = "com.zui.game.service.ui.GameHelperViewController"
            val controllerClass = classLoader.loadClass(className)

            // Hook the setPkgName method (called when a game starts)
            val setPkgNameMethod: Method =
                controllerClass.getDeclaredMethod("setPkgName", String::class.java)
            hookWithId(setPkgNameMethod, "set_pkg_name") { chain ->
                chain.proceed()
                val pkgName = chain.args[0] as String
                if (pkgName.isNotEmpty()) {
                    // Check whether this is a whitelisted game
                    if (isTargetGame(pkgName)) {
                        logger.debug("Target game detected: $pkgName")

                        // Delay the call to ensure the game helper is fully initialized
                        Handler(Looper.getMainLooper()).postDelayed(
                            { enableMistakeTouchWithSync(chain.thisObject) }, 1000
                        )
                    }
                }
                null
            }

            logger.info("Successfully hooked GameHelperViewController")
        } catch (e: Throwable) {
            logger.error("Hook GameHelperViewController failed", e)
        }
    }

    private fun hookItemBlockMistakeTouch(classLoader: ClassLoader) {
        try {
            val itemClassName = "com.zui.game.service.sys.item.ItemBlockMistakeTouch"
            val itemClass = classLoader.loadClass(itemClassName)

            // Hook the change2Status method to ensure the state is synchronized correctly
            val change2StatusMethod: Method =
                itemClass.getDeclaredMethod("change2Status", Int::class.javaPrimitiveType)
            hookWithId(change2StatusMethod, "change2_status") { chain ->
                val targetStatus = chain.args[0] as Int
                logger.debug("ItemBlockMistakeTouch.change2Status called with: $targetStatus")
                chain.proceed()
            }

            logger.info("Successfully hooked ItemBlockMistakeTouch")
        } catch (e: Throwable) {
            logger.error("Hook ItemBlockMistakeTouch failed", e)
        }
    }

    private fun hookLiveDataPostValue(classLoader: ClassLoader) {
        try {
            // Hook LiveData's postValue method to ensure state synchronization
            val liveDataClass = classLoader.loadClass("androidx.lifecycle.MutableLiveData")
            val postValueMethod: Method =
                liveDataClass.getDeclaredMethod("postValue", Any::class.java)
            hookWithId(postValueMethod, "post_value") { chain ->
                val value = chain.args[0]
                if (value is Int) {
                    val status = value
                    // Check whether this LiveData belongs to the mistake touch item
                    val stackTrace = Log.getStackTraceString(Throwable())
                    if (stackTrace.contains("ItemBlockMistakeTouch") ||
                        stackTrace.contains("change2Status")
                    ) {
                        logger.debug("LiveData postValue for mistake touch: $status")
                    }
                }
                chain.proceed()
            }

            logger.info("Successfully hooked LiveData")
        } catch (e: Throwable) {
            logger.error("Hook LiveData failed", e)
        }
    }

    private fun hookPreventMisoperationPersistence(classLoader: ClassLoader) {
        try {
            // Hook the SettingsValueUtilKt.setPreventMisoperation static method.
            // When mistake touch is auto-enabled by this hook (mBlockPersistence=true),
            // block the write to Settings.Global so the behavior only takes effect in
            // memory and the original setting is restored after the hook is disabled.
            val settingsUtilClass = classLoader.loadClass(SETTINGS_UTIL_CLASS)
            val setPreventMethod: Method = settingsUtilClass.getDeclaredMethod(
                "setPreventMisoperation", Context::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(setPreventMethod, "set_prevent") { chain ->
                if (mBlockPersistence) {
                    logger.debug("Blocked setPreventMisoperation persistence")
                    return@hookWithId null
                }
                chain.proceed()
            }

            logger.info("Successfully hooked setPreventMisoperation for anti-persistence")
        } catch (e: Throwable) {
            logger.error("Hook setPreventMisoperation failed", e)
        }
    }

    private fun enableMistakeTouchWithSync(gameHelper: Any) {
        try {
            // Get the Context
            var context = gameHelper.javaClass.getMethod("getContext").invoke(gameHelper)
            if (context == null) {
                context = gameHelper.javaClass.getMethod("getNotNullContext").invoke(gameHelper)
            }

            if (context is Context) {
                // Read the current system setting status first
                val currentStatus = getCurrentMistakeTouchStatus(context)
                logger.debug("Current mistake touch status: $currentStatus")

                if (currentStatus != 1) {
                    // Set through the game helper's internal method to keep the state in sync
                    setMistakeTouchThroughGameHelper(gameHelper)

                    logger.debug("Auto-enabled mistake touch with sync")
                } else {
                    logger.debug("Mistake touch already enabled")
                }
            }
        } catch (e: Throwable) {
            logger.error("Enable mistake touch with sync failed", e)
        }
    }

    private fun setMistakeTouchThroughGameHelper(gameHelper: Any) {
        try {
            // Enable persistence interception to prevent the changeMistouchStatus
            // async observer from writing the mistake touch state to Settings.Global
            mBlockPersistence = true

            // Call the game helper's internal changeMistouchStatus method
            val changeMistouchStatusMethod: Method =
                gameHelper.javaClass.getMethod("changeMistouchStatus", Boolean::class.javaPrimitiveType)
            changeMistouchStatusMethod.invoke(gameHelper, true)

            // Also ensure the ItemBlockMistakeTouch state is synchronized.
            // Note: mItemBlockMistakeTouch is a Kotlin Lazy delegate; it must be read via its getter
            val getMItemMethod: Method =
                gameHelper.javaClass.getMethod("getMItemBlockMistakeTouch")
            val mItemBlockMistakeTouch = getMItemMethod.invoke(gameHelper)
            if (mItemBlockMistakeTouch != null) {
                val change2StatusMethod: Method =
                    mItemBlockMistakeTouch.javaClass.getMethod("change2Status", Int::class.javaPrimitiveType)
                change2StatusMethod.invoke(mItemBlockMistakeTouch, 0)
            }

            // Clear the interception flag after a delay so all async observer callbacks finish
            Handler(Looper.getMainLooper()).postDelayed({
                mBlockPersistence = false
                logger.debug("Persistence block cleared")
            }, 3000)
        } catch (e: Throwable) {
            mBlockPersistence = false
            logger.error("Set through game helper failed", e)
        }
    }

    private fun getCurrentMistakeTouchStatus(context: Context): Int {
        return try {
            // Use reflection to call SettingsValueUtilKt.getPreventMisoperation
            val settingsUtilClass = Class.forName(SETTINGS_UTIL_CLASS)
            val method: Method =
                settingsUtilClass.getMethod("getPreventMisoperation", Context::class.java)
            val result = method.invoke(null, context)
            if (result != null) {
                result as Int
            } else {
                logger.warn("getPreventMisoperation returned null")
                -1
            }
        } catch (e: Throwable) {
            logger.error("Get current status failed", e)
            -1
        }
    }

    /**
     * Check whether the mistake touch whitelist feature is enabled.
     */
    private fun isMistakeTouchWhiteListEnabled(): Boolean {
        return try {
            remotePreferences.getBoolean(PreferenceKeys.MISTAKE_TOUCH_WHITE_LIST.name, false)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Get all game package names in the mistake touch whitelist.
     */
    private fun getMistakeTouchWhiteListGames(): Array<String> {
        val value = try {
            remotePreferences.getString(PreferenceKeys.MISTAKE_TOUCH_WHITE_LIST_GAME.name, "")
        } catch (_: Throwable) {
            ""
        }
        if (TextUtils.isEmpty(value)) return arrayOf()
        return value!!.split(",").toTypedArray()
    }

    /**
     * Check whether the given game is in the mistake touch whitelist.
     */
    private fun isGameInMistakeTouchWhiteList(packageName: String): Boolean {
        val whiteListGames = getMistakeTouchWhiteListGames()
        for (gamePackage in whiteListGames) {
            if (TextUtils.isEmpty(gamePackage)) {
                continue
            }
            if (gamePackage.trim() == packageName) {
                return true
            }
        }
        return false
    }

    /**
     * Check whether the package is a specific target game.
     * Logic: if the whitelist feature is enabled, only whitelisted games are affected;
     * otherwise all games are affected.
     */
    private fun isTargetGame(packageName: String): Boolean {
        return if (isMistakeTouchWhiteListEnabled()) {
            // Whitelist enabled: only whitelisted games are affected
            isGameInMistakeTouchWhiteList(packageName)
        } else {
            // Whitelist disabled: all games are affected
            true
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.GAME_SERVICE.packageName
        private const val SETTINGS_UTIL_CLASS = "com.zui.util.SettingsValueUtilKt"
    }
}
