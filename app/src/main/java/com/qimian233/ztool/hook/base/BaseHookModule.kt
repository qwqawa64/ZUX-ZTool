package com.qimian233.ztool.hook.base

import android.content.SharedPreferences
import android.util.Log
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface

/**
 * Hook 模块基类（libxposed 版，Kotlin）。
 * <p>
 * 所有 Hook 模块继承此类。通过 [xposed] 字段访问 libxposed API：
 * [XposedInterface.hook]、[XposedInterface.log]、[XposedInterface.getRemotePreferences] 等。
 * <p>
 * 日志关注点已拆分至 [ModuleLog]（Log4j 风格六级别 API），
 * 反射辅助已拆分至 [HookReflectionHelper]。
 * </p>
 *
 * <h3>Java 互操作约定</h3>
 * <ul>
 *   <li>[xposed] 为 Kotlin 属性（lateinit，无 @JvmField），Java 子类须通过 [getXposed] 访问。</li>
 *   <li>[logger] 为 @JvmField 真实字段，Java 子类可直接字段访问（保持历史调用方式）。</li>
 *   <li>[handleLoadPackage] / [handleSystemServerStarting] 带 [@Throws](Throwable::class)，
 *       以便 Java 子类继续声明 {@code throws Throwable} 的 override。</li>
 * </ul>
 */
abstract class BaseHookModule {

    /**
     * libxposed XposedInterface 实例，由 [setXposedInterface] 注入。
     * <p>Java 子类请使用 {@code getXposed()} 访问（Kotlin 属性无公开字段）。</p>
     */
    protected lateinit var xposed: XposedInterface

    /**
     * Log4j 风格日志器（Kotlin 实现，六级别：trace/debug/info/warn/error/fatal）。
     * <p>在 [setXposedInterface] 中初始化为真实值；此处占位初始化保证字段非空。</p>
     * 用法示例：{@code logger.info("Hook installed"); logger.debug("detail: " + data);}
     */
    @JvmField
    protected var logger: ModuleLog = ModuleLog("", null)

    abstract fun getModuleName(): String

    abstract fun getTargetPackages(): Array<out String?>?

    /**
     * 执行 Hook 操作（默认 no-op）。
     * <p>App 类 Hook 模块应继承 [AppHookModule] 以获得 IDE 自动补全；
     * 系统框架 Hook 模块应继承 [SystemHookModule]。</p>
     */
    @Throws(Throwable::class)
    open fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        // default no-op
    }

    /**
     * 注入 XposedInterface 并初始化日志器。
     */
    open fun setXposedInterface(xposed: XposedInterface) {
        this.xposed = xposed
        this.logger = ModuleLog(getModuleName(), xposed)
    }

    open fun supportsPackage(packageName: String?): Boolean {
        val targets = getTargetPackages() ?: return false
        for (target in targets) {
            if (target == packageName) {
                return true
            }
        }
        return false
    }

    open fun isEnabled(): Boolean {
        val moduleName = getModuleName()
        if (moduleName == "hook_test" || moduleName == "test_hook") {
            return true
        }
        return try {
            val prefs = xposed.getRemotePreferences(PREFS_NAME)
            prefs.getBoolean(moduleName, false)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 系统服务器回调（默认 no-op）。
     * <p>系统框架 Hook 模块应继承 [SystemHookModule] 以获得 IDE 自动补全。</p>
     */
    @Throws(Throwable::class)
    open fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        // default no-op
    }

    fun safeHandleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        refreshDebugLoggingEnabled()
        val packageName = param.packageName
        if (!supportsPackage(packageName)) return
        if (!isEnabled()) {
            if (DEBUG) Log.d(TAG, "module disabled: " + getModuleName())
            return
        }
        try {
            if (DEBUG) Log.d(TAG, "Executing hook module: " + getModuleName()
                    + " for package: " + packageName)
            handleLoadPackage(param)
            if (DEBUG) Log.d(TAG, "Hook module executed successfully: " + getModuleName())
        } catch (t: Throwable) {
            Log.e(TAG, "Error in hook module: " + getModuleName(), t)
        }
    }

    fun safeHandleSystemServerStarting(
            param: XposedModuleInterface.SystemServerStartingParam) {
        refreshDebugLoggingEnabled()
        if (!isEnabled()) {
            if (DEBUG) Log.d(TAG, "module disabled for system server: " + getModuleName())
            return
        }
        try {
            if (DEBUG) Log.d(TAG, "Executing system server hook module: " + getModuleName())
            handleSystemServerStarting(param)
            if (DEBUG) Log.d(TAG, "System server hook module executed successfully: "
                    + getModuleName())
        } catch (t: Throwable) {
            Log.e(TAG, "Error in system server hook module: " + getModuleName(), t)
        }
    }

    /**
     * Hook with a stable id for hot-reload atomic replacement.
     * Equivalent to {@code xposed.hook(target).setId(id).intercept(hooker)}.
     * <p>
     * During hot reload, a new hook registered with the same id on the same executable
     * will atomically replace the old hook in the framework, eliminating the hook vacuum
     * window.
     * </p>
     *
     * @param target the method or constructor to hook
     * @param id     a stable, module-unique identifier for the hook
     * @param hooker the interception callback
     * @return the hook handle
     */
    protected open fun hookWithId(
            target: Executable,
            id: String,
            hooker: Hooker
    ): XposedInterface.HookHandle {
        return if (xposed.apiVersion >= 102) {
            xposed.hook(target).setId(id).intercept(hooker)
        } else {
            xposed.hook(target).intercept(hooker)
        }
    }

    /**
     * XposedHelpers-style field finder. Delegates to [HookReflectionHelper.findField].
     * <p>
     * Always ensure you have filters to avoid unexpected field hits.
     * <p>带 [@Throws](NoSuchFieldException::class) 以保留 Java checked 异常契约。</p>
     */
    @Throws(NoSuchFieldException::class)
    protected open fun findField(startClass: Class<*>?, name: String): Field =
            HookReflectionHelper.findField(startClass, name)

    /**
     * XposedHelpers-style method finder. Delegates to [HookReflectionHelper.findMethod].
     * <p>
     * Always ensure you have filters to avoid unexpected method hits.
     * <p>
     * 参数类型允许可空（如 {@code Int::class.javaPrimitiveType}），与历史 Java 平台类型签名兼容。
     * 带 [@Throws](NoSuchMethodException::class) 以保留 Java checked 异常契约。
     */
    @Throws(NoSuchMethodException::class)
    protected open fun findMethod(
            startClass: Class<*>?,
            name: String,
            vararg parameterTypes: Class<*>?
    ): Method = HookReflectionHelper.findMethod(startClass, name, *parameterTypes)

    /**
     * 远程配置读取入口（Kotlin 属性形式，Java 侧通过 {@code getRemotePreferences()} 调用）。
     * 等价于 {@code xposed.getRemotePreferences("xposed_module_config")}。
     */
    open val remotePreferences: SharedPreferences
        get() = xposed.getRemotePreferences(PREFS_NAME)

    companion object {
        private const val TAG = "ZToolXposedModule"

        /**
         * 详细日志开关（向后兼容字段，实际状态由 [ModuleLog.DEBUG] 管理）。
         * @see refreshDebugLoggingEnabled
         */
        @JvmField
        @Volatile
        var DEBUG: Boolean = false

        /**
         * 刷新详细日志开关。
         * <p>委托给 [ModuleLog.refreshDebugLoggingEnabled]，并将结果同步到 [DEBUG] 字段。</p>
         */
        @JvmStatic
        fun refreshDebugLoggingEnabled() {
            ModuleLog.refreshDebugLoggingEnabled()
            DEBUG = ModuleLog.DEBUG
        }
    }

    /**
     * 模块配置 SharedPreferences 文件名（@JvmField 实例字段：Java 子类在实例上下文中可简单名访问，
     * 与原 Java 版 {@code protected static final} 的字段访问语义一致；Kotlin 子类按继承属性访问）。
     */
    @Suppress("PropertyName")
    @JvmField
    protected val PREFS_NAME: String = "xposed_module_config"
}
