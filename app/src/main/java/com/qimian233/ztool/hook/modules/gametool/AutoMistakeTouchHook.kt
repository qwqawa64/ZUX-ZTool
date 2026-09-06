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
 * 自动开启游戏防误触功能Hook模块
 * 为特定游戏自动开启ZUI游戏助手的防误触功能
 */
class AutoMistakeTouchHook : AppHookModule() {

    // 持久化拦截标志：当通过本Hook自动开启防误触时，阻止写入Settings.Global
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
            // Hook GameHelperViewController 的初始化
            hookGameHelperViewController(classLoader)

            // Hook ItemBlockMistakeTouch 的状态同步
            hookItemBlockMistakeTouch(classLoader)

            // Hook LiveData 的状态同步
            hookLiveDataPostValue(classLoader)

            // Hook setPreventMisoperation 持久化拦截
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

            // Hook setPkgName 方法（游戏启动时调用）
            val setPkgNameMethod: Method =
                controllerClass.getDeclaredMethod("setPkgName", String::class.java)
            hookWithId(setPkgNameMethod, "set_pkg_name") { chain ->
                chain.proceed()
                val pkgName = chain.args[0] as String
                if (pkgName.isNotEmpty()) {
                    // 检查是否为白名单游戏
                    if (isTargetGame(pkgName)) {
                        logger.debug("Target game detected: $pkgName")

                        // 延迟设置，确保游戏助手完全初始化
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

            // Hook change2Status 方法，确保状态正确同步
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
            // Hook LiveData的postValue方法，确保状态同步
            val liveDataClass = classLoader.loadClass("androidx.lifecycle.MutableLiveData")
            val postValueMethod: Method =
                liveDataClass.getDeclaredMethod("postValue", Any::class.java)
            hookWithId(postValueMethod, "post_value") { chain ->
                val value = chain.args[0]
                if (value is Int) {
                    val status = value
                    // 检查这个LiveData是否是防误触的LiveData
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
            // Hook SettingsValueUtilKt.setPreventMisoperation 静态方法
            // 当通过本Hook自动开启防误触时(mBlockPersistence=true)，阻止写入Settings.Global
            // 这样防误触行为仅在内存态生效，关闭Hook后自动恢复原始设置
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
            // 获取Context
            var context = gameHelper.javaClass.getMethod("getContext").invoke(gameHelper)
            if (context == null) {
                context = gameHelper.javaClass.getMethod("getNotNullContext").invoke(gameHelper)
            }

            if (context is Context) {
                // 先获取当前系统设置状态
                val currentStatus = getCurrentMistakeTouchStatus(context)
                logger.debug("Current mistake touch status: $currentStatus")

                if (currentStatus != 1) {
                    // 通过游戏助手内部方法设置，确保状态同步
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
            // 开启持久化拦截，阻止 changeMistouchStatus 异步 observer
            // 将防误触状态写入 Settings.Global
            mBlockPersistence = true

            // 调用游戏助手内部的changeMistouchStatus方法
            val changeMistouchStatusMethod: Method =
                gameHelper.javaClass.getMethod("changeMistouchStatus", Boolean::class.javaPrimitiveType)
            changeMistouchStatusMethod.invoke(gameHelper, true)

            // 同时确保ItemBlockMistakeTouch的状态同步
            // 注意：mItemBlockMistakeTouch 是 Kotlin Lazy 委托，必须通过 getter 获取
            val getMItemMethod: Method =
                gameHelper.javaClass.getMethod("getMItemBlockMistakeTouch")
            val mItemBlockMistakeTouch = getMItemMethod.invoke(gameHelper)
            if (mItemBlockMistakeTouch != null) {
                val change2StatusMethod: Method =
                    mItemBlockMistakeTouch.javaClass.getMethod("change2Status", Int::class.javaPrimitiveType)
                change2StatusMethod.invoke(mItemBlockMistakeTouch, 0)
            }

            // 延迟清除拦截标志，确保所有异步 observer 回调执行完毕
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
            // 使用反射调用SettingsValueUtilKt.getPreventMisoperation
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
     * 检查防误触白名单功能是否启用
     */
    private fun isMistakeTouchWhiteListEnabled(): Boolean {
        return try {
            remotePreferences.getBoolean(PreferenceKeys.MISTAKE_TOUCH_WHITE_LIST.name, false)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 获取防误触白名单中的所有游戏包名
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
     * 检查指定游戏是否在防误触白名单中
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
     * 检查是否为特定目标游戏
     * 逻辑：如果白名单功能启用，则只对白名单中的游戏生效
     *       如果白名单功能未启用，则对所有游戏生效
     */
    private fun isTargetGame(packageName: String): Boolean {
        return if (isMistakeTouchWhiteListEnabled()) {
            // 白名单功能启用，只对白名单中的游戏生效
            isGameInMistakeTouchWhiteList(packageName)
        } else {
            // 白名单功能未启用，对所有游戏生效
            true
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.GAME_SERVICE.packageName
        private const val SETTINGS_UTIL_CLASS = "com.zui.util.SettingsValueUtilKt"
    }
}
