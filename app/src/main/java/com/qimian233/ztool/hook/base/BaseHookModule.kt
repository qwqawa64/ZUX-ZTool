package com.qimian233.ztool.hook.base

import android.content.SharedPreferences
import android.util.Log
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface

/**
 * Hook module base class (libxposed version, Kotlin).
 * <p>
 * All Hook modules extend this class. Access the libxposed API through the
 * [xposed] field: [XposedInterface.hook], [XposedInterface.log],
 * [XposedInterface.getRemotePreferences], etc.
 * <p>
 * Logging concerns are split into [ModuleLog] (Log4j-style six-level API),
 * and reflection helpers are split into [HookReflectionHelper].
 * </p>
 *
 * <h3>Subclass conventions</h3>
 * <ul>
 *   <li>[xposed] / [logger] are plain Kotlin properties, accessed directly by subclasses.</li>
 *   <li>[handleLoadPackage] / [handleSystemServerStarting] carry
 *       [@Throws](Throwable::class), keeping the throws declaration in the JVM
 *       method signature as an error-contract document.</li>
 * </ul>
 */
abstract class BaseHookModule {

    /**
     * libxposed XposedInterface instance, injected by [setXposedInterface].
     */
    protected lateinit var xposed: XposedInterface

    /**
     * Log4j-style logger (Kotlin implementation, six levels: trace/debug/info/warn/error/fatal).
     * <p>Initialized to a real value in [setXposedInterface]; this placeholder initialization keeps the field non-null.</p>
     * Usage example: {@code logger.info("Hook installed"); logger.debug("detail: " + data);}
     */
    protected var logger: ModuleLog = ModuleLog("", null)

    abstract fun getModuleName(): String

    abstract fun getTargetPackages(): Array<out String?>?

    /**
     * Performs Hook operations (default no-op).
     * <p>App-class Hook modules should extend [AppHookModule] for IDE autocompletion;
     * system framework Hook modules should extend [SystemHookModule].</p>
     */
    @Throws(Throwable::class)
    open fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        // default no-op
    }

    /**
     * Injects the XposedInterface and initializes the logger.
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
     * System server callback (default no-op).
     * <p>System framework Hook modules should extend [SystemHookModule] for IDE autocompletion.</p>
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
        return hookWithId(target, id, hooker, XposedInterface.PRIORITY_DEFAULT, ExceptionMode.DEFAULT)
    }

    /**
     * Hook with a stable id and an explicit execution priority.
     * Equivalent to {@code xposed.hook(target).setId(id).setPriority(priority).intercept(hooker)},
     * with the exception handling mode left at [ExceptionMode.DEFAULT].
     *
     * @param target   the method or constructor to hook
     * @param id       a stable, module-unique identifier for the hook
     * @param hooker   the interception callback
     * @param priority the execution priority, from [Int.MIN_VALUE] to [Int.MAX_VALUE];
     *                 hooks with a higher priority execute first. Defaults to
     *                 [XposedInterface.PRIORITY_DEFAULT] (50). Use it to resolve contention
     *                 when multiple hooks target the same executable; see the full overload
     *                 for details.
     * @return the hook handle
     * @see hookWithId
     */
    protected open fun hookWithId(target: Executable,
                                  id: String,
                                  hooker: Hooker,
                                  priority: Int
    ): XposedInterface.HookHandle {
        return hookWithId(target, id, hooker, priority, ExceptionMode.DEFAULT)
    }

    /**
     * Hook with a stable id, an explicit execution priority and an exception handling mode.
     * Equivalent to {@code xposed.hook(target).setId(id).setPriority(priority)
     * .setExceptionMode(exceptionMode).intercept(hooker)}.
     * <p>
     * During hot reload, a new hook registered with the same id on the same executable
     * will atomically replace the old hook in the framework, eliminating the hook vacuum
     * window.
     * </p>
     * <h3>Priority and multi-hook contention</h3>
     * <p>
     * When multiple hooks (from this module or other modules) are registered on the same
     * executable, the framework invokes them in descending priority order as an interceptor
     * chain: the highest-priority hook runs first, and each {@code chain.proceed()} call
     * hands control to the next hook, with the original method executing last. Every hook
     * receives the result of its {@code proceed()} call, so earlier hooks can modify
     * arguments, rewrite the return value, or short-circuit the call entirely. Use an
     * explicit priority when your hook must run before or after other hooks on the same
     * method. Reference values: [XposedInterface.PRIORITY_LOWEST]
     * ({@link Integer#MIN_VALUE}, end of the chain), [XposedInterface.PRIORITY_DEFAULT]
     * (50), [XposedInterface.PRIORITY_HIGHEST] ({@link Integer#MAX_VALUE}, head of the
     * chain).
     * </p>
     *
     * @param target        the method or constructor to hook
     * @param id            a stable, module-unique identifier for the hook
     * @param hooker        the interception callback
     * @param priority      the execution priority, from [Int.MIN_VALUE] to [Int.MAX_VALUE];
     *                      hooks with a higher priority execute first. Defaults to
     *                      [XposedInterface.PRIORITY_DEFAULT] (50).
     * @param exceptionMode how the LSPosed framework handles exceptions thrown by the hooker:
     * <ul>
     *   <li>[ExceptionMode.DEFAULT] — follow the global exception mode configured in
     *       {@code module.prop}; defaults to [ExceptionMode.PROTECTIVE] if not specified.</li>
     *   <li>[ExceptionMode.PROTECTIVE] — any exception thrown by the hooker is caught and
     *       logged, and the call proceeds as if no hook exists. Recommended for most cases,
     *       as it prevents crashes caused by hook errors. If the exception is thrown before
     *       {@code proceed()}, the framework continues the chain without this hook; if
     *       thrown after {@code proceed()}, the framework returns the proceeded value /
     *       exception as the result. Exceptions thrown by {@code proceed()} are always
     *       propagated.</li>
     *   <li>[ExceptionMode.PASSTHROUGH] — any exception thrown by the hooker propagates to
     *       the caller as usual. Recommended for debugging, to help find and fix errors in
     *       your hooks.</li>
     * </ul>
     * @return the hook handle
     */
    protected open fun hookWithId(target: Executable,
                                  id: String,
                                  hooker: Hooker,
                                  priority: Int,
                                  exceptionMode: ExceptionMode
    ): XposedInterface.HookHandle {
        return if (xposed.apiVersion >= XposedInterface.API_102) {
            xposed.hook(target).setId(id).setPriority(priority).setExceptionMode(exceptionMode).intercept(hooker)
        } else {
            xposed.hook(target).setPriority(priority).setExceptionMode(exceptionMode).intercept(hooker)
        }
    }

    /**
     * XposedHelpers-style field finder. Delegates to [HookReflectionHelper.findField].
     * <p>
     * Always ensure you have filters to avoid unexpected field hits.
     * <p>Carries [@Throws](NoSuchFieldException::class) to preserve the Java checked-exception contract.</p>
     */
    @Throws(NoSuchFieldException::class)
    protected open fun findField(startClass: Class<*>?, name: String): Field =
            HookReflectionHelper.findField(startClass, name)

    /**
     * XposedHelpers-style method finder. Delegates to [HookReflectionHelper.findMethod].
     * <p>
     * Always ensure you have filters to avoid unexpected method hits.
     * <p>
     * Parameter types may be nullable (e.g. {@code Int::class.javaPrimitiveType}),
     * compatible with the historical Java platform-type signatures.
     * Carries [@Throws](NoSuchMethodException::class) to preserve the Java checked-exception contract.
     */
    @Throws(NoSuchMethodException::class)
    protected open fun findMethod(
            startClass: Class<*>?,
            name: String,
            vararg parameterTypes: Class<*>?
    ): Method = HookReflectionHelper.findMethod(startClass, name, *parameterTypes)

    /**
     * Remote preferences access entry (Kotlin property form; Java callers use {@code getRemotePreferences()}).
     * Equivalent to {@code xposed.getRemotePreferences("xposed_module_config")}.
     */
    open val remotePreferences: SharedPreferences
        get() = xposed.getRemotePreferences(PREFS_NAME)

    companion object {
        private const val TAG = "ZToolXposedModule"

        /**
         * Detailed logging switch (backward-compatible field; the actual state is managed by [ModuleLog.DEBUG]).
         * @see refreshDebugLoggingEnabled
         */
        @Volatile
        var DEBUG: Boolean = false

        /**
         * Refreshes the detailed logging switch.
         * <p>Delegates to [ModuleLog.refreshDebugLoggingEnabled] and syncs the result to the [DEBUG] field.</p>
         */
        fun refreshDebugLoggingEnabled() {
            ModuleLog.refreshDebugLoggingEnabled()
            DEBUG = ModuleLog.DEBUG
        }
    }

    /**
     * Module configuration SharedPreferences file name (Kotlin subclasses access it as an inherited property).
     */
    @Suppress("PropertyName")
    protected val PREFS_NAME: String = "xposed_module_config"
}
