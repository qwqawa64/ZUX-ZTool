package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.text.TextUtils
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * AI输入法扩展功能Hook模块
 * 功能：扩展AI触发符号，强制开启LGSI AI功能特性
 * 作用域：全局（动态检测类是否存在）
 */
@SuppressLint("PrivateApi")
class AiInputExpand : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.AI_INPUT_EXPAND.name

    override fun getTargetPackages(): Array<String?>? = null

    /**
     * 重写此方法以支持全局Hook
     * 因为RemoteInputConnectionImpl会在各个应用进程中加载
     */
    override fun supportsPackage(packageName: String?): Boolean = true

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        // 1. 修改 RemoteInputConnectionImpl 触发符号
        runCatching { hookRemoteInputConnection(classLoader) }
        // 2. 强制开启 LgsiFeatures 功能
        runCatching { hookLgsiFeatures(classLoader) }
    }

    private fun hookRemoteInputConnection(classLoader: ClassLoader) {
        val className = "android.view.inputmethod.RemoteInputConnectionImpl"

        // 检查类是否存在，不存在直接返回，避免无效Hook尝试
        val targetClass: Class<*>?
        try {
            targetClass = classLoader.loadClass(className)
        } catch (_: ClassNotFoundException) {
            return
        }

        // 定义新的触发符号数组，使用新的符号
        val newSignArray = this.prefStringArray

        // 修改静态常量数组 AI_COMMAND_SIGN_ARRAYS
        findField(targetClass, "AI_COMMAND_SIGN_ARRAYS").set(null, newSignArray)

        // 修改默认的 AI_COMMAND_SIGN
        findField(targetClass, "AI_COMMAND_SIGN").set(null, "&&")

        logger.info("Successfully expanded AI input signs [&&] for package")
    }

    private fun hookLgsiFeatures(classLoader: ClassLoader) {
        val className = "com.lgsi.config.LgsiFeatures"

        val featureClass: Class<*>
        try {
            featureClass = classLoader.loadClass(className)
        } catch (_: ClassNotFoundException) {
            return
        }

        // 强制 enabled 方法返回 true
        try {
            val method = featureClass.getDeclaredMethod("enabled", Int::class.javaPrimitiveType)
            hookWithId(
                method,
                "lgsi_features_enabled"
            ) { true }
            logger.info("Successfully forced LGSI Features check to TRUE")
        } catch (_: NoSuchMethodException) {
            // 方法不存在，忽略
        }
    }

    private val prefStringArray: Array<String?>
        /**
         * Read comma-separated string array from preferences.
         */
        get() {
            val value: String? = try {
                remotePreferences.getString(PreferenceKeys.AI_INPUT_EXPAND_SIGNS.name, "")
            } catch (_: Throwable) {
                ""
            }
            if (TextUtils.isEmpty(value)) return arrayOfNulls(0)
            return value!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        }
}
