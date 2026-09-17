package com.qimian233.ztool.hook.modules.launcher.misc

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayList

/**
 * ZUI Launcher background management optimization hook module.
 * Prevents killing apps' background services when swiping away recents cards.
 * Adapts to Android 16+ and Android 15- versions.
 * Supports a whitelist mechanism that protects only specified apps.
 */
class DisableForceStop : AppHookModule() {

    // Whitelist package name set
    private var whiteList: Array<String> = arrayOf()

    override fun getModuleName(): String = PreferenceKeys.DISABLE_FORCE_STOP.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        // Get the current Android SDK version
        val sdkVersion = getSDKVersion()
        whiteList = getWhiteListPackages()
        logger.trace("Current Android SDK: $sdkVersion, target package name: $packageName")
        logger.trace("White list enabled, app in whitelist: ${whiteList.size}")

        // Choose the hook strategy by Android version
        if (sdkVersion >= 36) { // Includes Android 16
            hookForAndroid16Plus(classLoader, packageName)
        } else {
            hookForAndroid15Minus(classLoader, packageName)
        }
    }

    /**
     * Android 16+ hook strategy targeting the reworked ZUI Launcher architecture.
     */
    private fun hookForAndroid16Plus(classLoader: ClassLoader, packageName: String) {
        try {
            if (ScopeKeys.LAUNCHER.packageName == packageName) {
                hookZuiLauncherAndroid16(classLoader)
            } else if ("com.android.launcher3" == packageName) {
                hookBaseLauncherAndroid16()
            }
            logger.info("Android 16+ Hook applied, whitelist protection enabled")
        } catch (t: Throwable) {
            logger.error("Android 16+ Hook failed!", t)
        }
    }

    // Check whether whitelist protection is enabled
    private fun isWhiteListEnabled(): Boolean {
        return try {
            remotePreferences.getBoolean(PreferenceKeys.FORCE_STOP_WHITE_LIST_ENABLE.name, false)
        } catch (_: Throwable) {
            false
        }
    }

    // Get whitelisted package names
    private fun getWhiteListPackages(): Array<String> {
        val value = try {
            remotePreferences.getString(PreferenceKeys.FORCE_STOP_WHITE_LIST.name, "")
        } catch (_: Throwable) {
            ""
        }
        if (value.isNullOrEmpty()) return arrayOf()
        return value.split(",").toTypedArray()
    }

    // Check whether the given package name is whitelisted
    private fun isProtectedPackage(packageName: String): Boolean {
        if (!isWhiteListEnabled()) return true // Whitelist disabled, protect all apps
        for (pkg in whiteList) {
            if (pkg == packageName) {
                return true
            }
        }
        return false
    }

    /**
     * Android 15- hook strategy for the legacy launcher architecture.
     */
    private fun hookForAndroid15Minus(classLoader: ClassLoader, packageName: String) {
        try {
            if (ScopeKeys.LAUNCHER.packageName == packageName || "com.android.launcher3" == packageName) {
                hookLegacyLauncher(classLoader)
            }
            logger.info("Android 15- Hook applied, whitelist protection enabled")
        } catch (t: Throwable) {
            logger.error("Android 15- Hook failed!", t)
        }
    }

    /**
     * Android 16+ ZUI Launcher hook (with whitelist mechanism).
     */
    private fun hookZuiLauncherAndroid16(classLoader: ClassLoader) {
        try {
            val overviewUtilitiesClass = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities")

            // Hook the removeAppProcess method - the main process-kill entry point
            val removeAppProcessMethod: Method = overviewUtilitiesClass.getDeclaredMethod(
                "removeAppProcess", Context::class.java, Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(removeAppProcessMethod, "remove_app_process_1") { chain ->
                val pkgName = chain.args[2] as String // Note: corrected argument index
                val uid = chain.args[3] as Int

                // Check whitelist membership
                if (isProtectedPackage(pkgName)) {
                    // In whitelist, block the kill operation
                    logger.trace("Android 16: Avoid killing app in whitelist: $pkgName (UID: $uid)")
                    return@hookWithId null
                }

                // Not in whitelist, allow the original method to execute
                logger.trace("Android 16: Allow killing app: $pkgName")
                chain.proceed()
            }

            // Hook the c method - forced-kill helper method (found dynamically via DexKit)
            val cMethodName = findCMethodName()
            val cMethod: Method = overviewUtilitiesClass.getDeclaredMethod(
                cMethodName, Context::class.java, String::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(cMethod, "hook_165") { chain ->
                val pkgName = chain.args[1] as String
                val uid = chain.args[2] as Int

                // Check whitelist membership
                if (isProtectedPackage(pkgName)) {
                    // In whitelist, block the forced kill
                    logger.trace("Android 16: Blocked forced killing app in whitelist: $pkgName (UID: $uid)")
                    return@hookWithId null
                }

                // Not in whitelist, allow the original method to execute
                logger.trace("Android 16: Allow forced killing app: $pkgName")
                chain.proceed()
            }

            // Hook the removeAllRunningAppProcesses method - batch cleanup entry point
            val removeAllMethod: Method = overviewUtilitiesClass.getDeclaredMethod(
                "removeAllRunningAppProcesses", Context::class.java, ArrayList::class.java, Boolean::class.javaPrimitiveType
            )
            hookWithId(removeAllMethod, "remove_all_1") { chain ->
                val tasks = chain.args[1] as ArrayList<*>?

                if (tasks != null) {
                    val totalTasks = tasks.size
                    var protectedCount = 0

                    // Track whitelisted apps
                    for (task in tasks) {
                        try {
                            // Try to get the task's package name
                            val pkgName = getPackageNameFromTask(task)
                            if (pkgName != null && isProtectedPackage(pkgName)) {
                                protectedCount++
                                logger.trace("Android 16: Whitelist APP detected when performing batch kill: $pkgName")
                            }
                        } catch (_: Exception) {
                            // If the package name cannot be obtained, skip
                        }
                    }

                    if (protectedCount > 0) {
                        // If whitelisted apps are included, block the entire batch cleanup operation
                        logger.trace("Android 16: Blocking kill operation, $protectedCount whitelisted app(s) in batch kill list")
                        return@hookWithId null
                    }

                    // No whitelisted apps included, allow the batch cleanup
                    logger.trace("Android 16: $totalTasks APP(s) are allowed to be killed.")
                }

                chain.proceed()
            }

            // Hook the AsyncTask subclass's doInBackground method - async cleanup logic
            val asyncTaskClass = findInnerClass(classLoader)

            if (asyncTaskClass != null) {
                val doInBackgroundMethod: Method =
                    asyncTaskClass.getDeclaredMethod("doInBackground", arrayOf<Void>().javaClass)
                hookWithId(doInBackgroundMethod, "do_in_background") { chain ->
                    try {
                        // Try to get the task list
                        val thisObject = chain.thisObject
                        val tasksField: Field = thisObject.javaClass.getDeclaredField("tasks")
                        tasksField.isAccessible = true
                        val tasks = tasksField.get(thisObject)

                        if (tasks is ArrayList<*>) {
                            for (task in tasks) {
                                try {
                                    val pkgName = getPackageNameFromTask(task)
                                    if (pkgName != null && isProtectedPackage(pkgName)) {
                                        logger.trace("Android 16: Whitelist app detected in async task, count: $pkgName, blocking async task")
                                        return@hookWithId null
                                    }
                                } catch (_: Exception) {
                                    // Skip unrecognized tasks
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // If the check cannot be performed, block by default
                        logger.warn("Android 16: Unable to check async task, blocking it by default")
                        return@hookWithId null
                    }

                    // No whitelisted apps included, allow execution
                    logger.trace("Android 16: Allowed to perform async kill")
                    chain.proceed()
                }
            }

            // Try hooking methods Android 16 may have added
            hookAdditionalAndroid16Methods(classLoader)

            logger.info("Hook for Android 16+ ZUI Launcher successfully applied.")
        } catch (t: Throwable) {
            logger.error("Android 16+: Failed to hook ZUI Launcher", t)
        }
    }

    /**
     * Android 16+ base Launcher hook.
     */
    private fun hookBaseLauncherAndroid16() {
        try {
            // Possible hook points on the base Launcher for Android 16.
            // Specific hooks for com.android.launcher3 can be added here as needed.
            logger.warn("Android 16 logic not implemented yet!")
        } catch (t: Throwable) {
            logger.error("Android 16+: failed to hook basic Launcher", t)
        }
    }

    /**
     * Android 16- hook strategy with whitelist mechanism.
     */
    @SuppressLint("PrivateApi")
    private fun hookLegacyLauncher(classLoader: ClassLoader) {
        try {
            logger.info("Start hooking legacy Launcher with whitelist enabled.")

            // Hook ActivityManagerWrapper methods
            val amwclass = try {
                classLoader.loadClass("com.android.systemui.shared.system.ActivityManagerWrapper")
            } catch (_: ClassNotFoundException) {
                null
            }

            if (amwclass != null) {
                logger.info("Found ActivityManagerWrapper class, starting hook...")

                val removeAllMethod: Method = amwclass.getDeclaredMethod(
                    "removeAllRunningAppProcesses", Context::class.java, ArrayList::class.java
                )
                hookWithId(removeAllMethod, "remove_all_2") { chain ->
                    val tasks = chain.args[1] as ArrayList<*>?

                    if (tasks != null) {
                        var protectedCount = 0
                        for (task in tasks) {
                            try {
                                val pkgName = getPackageNameFromTask(task)
                                if (pkgName != null && isProtectedPackage(pkgName)) {
                                    protectedCount++
                                }
                            } catch (_: Exception) {
                                // Skip unrecognized tasks
                            }
                        }

                        if (protectedCount > 0) {
                            logger.trace("Legacy architecture: batch kill includes $protectedCount whitelisted app(s), blocking kill")
                            return@hookWithId null
                        }
                    }

                    chain.proceed()
                }

                val removeAppProcessMethod: Method = amwclass.getDeclaredMethod(
                    "removeAppProcess", Context::class.java, Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType
                )
                hookWithId(removeAppProcessMethod, "remove_app_process_2") { chain ->
                    val pkgName = chain.args[2] as String

                    if (isProtectedPackage(pkgName)) {
                        logger.trace("Legacy architecture: blocked killing whitelisted app: $pkgName")
                        return@hookWithId null
                    }

                    chain.proceed()
                }

                logger.info("ActivityManagerWrapper hook completed [OK], whitelist mechanism active")
            } else {
                logger.warn("ActivityManagerWrapper class not found, trying other hook points...")
                // Alternative hook points can be added here
            }
        } catch (e: Exception) {
            logger.error("Failed to hook legacy launcher", e)
        }
    }

    /**
     * Additional possible Android 16 hook points.
     */
    private fun hookAdditionalAndroid16Methods(classLoader: ClassLoader) {
        try {
            // Try hooking task-management methods Android 16 may have added
            val potentialClasses = arrayOf(
                "com.zui.launcher.taskbar.TaskbarManager",
                "com.zui.launcher.recents.RecentsModel",
                "com.zui.launcher.recents.TaskStackListener"
            )

            for (className in potentialClasses) {
                val targetClass = try {
                    classLoader.loadClass(className)
                } catch (_: ClassNotFoundException) {
                    null
                }
                if (targetClass != null) {
                    logger.debug("Android 16 new class: $className")
                    // Specific hook logic can be added as needed
                }
            }
        } catch (_: Throwable) {
            // Ignore errors; these are optional hook points
            logger.info("Android 16 extra hook points detection completed.")
        }
    }

    /**
     * Extract the package name from a task object.
     * @param task task object
     * @return package name, or null when it cannot be extracted
     */
    private fun getPackageNameFromTask(task: Any?): String? {
        if (task == null) {
            return null
        }

        try {
            // Method 1: try getting the package name via ComponentName
            val componentNameField: Field = task.javaClass.getDeclaredField("componentName")
            componentNameField.isAccessible = true
            val componentName = componentNameField.get(task)
            if (componentName != null) {
                val getPackageNameMethod: Method =
                    componentName.javaClass.getMethod("getPackageName")
                val packageNameObj = getPackageNameMethod.invoke(componentName)
                if (packageNameObj is String) {
                    return packageNameObj
                }
            }

            // Method 2: try getting the packageName field directly
            try {
                val packageNameField: Field = task.javaClass.getDeclaredField("packageName")
                packageNameField.isAccessible = true
                val packageNameFieldVal = packageNameField.get(task)
                if (packageNameFieldVal is String) {
                    return packageNameFieldVal
                }
            } catch (_: NoSuchFieldException) {
                // The field may not exist; try other methods
            }

            // Method 3: try getting the package name via BaseActivityInfo
            try {
                val baseActivityInfoField: Field = task.javaClass.getDeclaredField("baseActivityInfo")
                baseActivityInfoField.isAccessible = true
                val baseActivityInfo = baseActivityInfoField.get(task)
                if (baseActivityInfo != null) {
                    val packageNameField: Field =
                        baseActivityInfo.javaClass.getDeclaredField("packageName")
                    packageNameField.isAccessible = true
                    val packageNameObj = packageNameField.get(baseActivityInfo)
                    if (packageNameObj is String) {
                        return packageNameObj
                    }
                }
            } catch (_: NoSuchFieldException) {
                // The field may not exist
            }

            // Method 4: try getting the package name via taskDescription
            try {
                val taskDescriptionField: Field = task.javaClass.getDeclaredField("taskDescription")
                taskDescriptionField.isAccessible = true
                val taskDescription = taskDescriptionField.get(task)
                if (taskDescription != null) {
                    val getPackageNameMethod: Method =
                        taskDescription.javaClass.getMethod("getPackageName")
                    val packageNameObj = getPackageNameMethod.invoke(taskDescription)
                    if (packageNameObj is String) {
                        return packageNameObj
                    }
                }
            } catch (_: NoSuchFieldException) {
                // The field may not exist
            }
        } catch (_: Exception) {
            // All methods failed, return null
        }

        return null
    }

    /**
     * Read from the offline index the obfuscated method name in OverviewUtilities
     * with signature (Context, String, int)→void. Falls back to the hardcoded "c"
     * when the index is missing or the lookup fails.
     */
    private fun findCMethodName(): String {
        val name = DexIndexStore.string(
            xposed,
            ScopeKeys.LAUNCHER.packageName,
            DexIndexConstants.ModuleKeys.DISABLE_FORCE_STOP,
            DexIndexConstants.Keys.FORCE_STOP_METHOD
        )
        if (name != null) {
            logger.info("Loaded force-stop method from dex index: $name")
            return name
        }
        return "c" // Hardcoded fallback
    }

    /**
     * Find inner classes via reflection (handling obfuscated inner class names).
     * Iterates possible inner class names ($1-$5, $a-$e) until a class with a
     * doInBackground method is found.
     */
    private fun findInnerClass(classLoader: ClassLoader): Class<*>? {
        // Try common obfuscation patterns first: $a, $b, $c, $d, $e
        for (suffix in 'a'..'e') {
            try {
                val cls = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities$$suffix")
                // Validation: the inner class should have a doInBackground method
                try {
                    cls.getDeclaredMethod("doInBackground", arrayOf<Void>().javaClass)
                    logger.info("Found inner class: ${cls.name}")
                    return cls
                } catch (_: NoSuchMethodException) {
                }
            } catch (_: ClassNotFoundException) {
            }
        }
        // Then try numeric suffixes: $1, $2, $3, $4, $5
        for (i in 1..5) {
            try {
                val cls = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities$$i")
                try {
                    cls.getDeclaredMethod("doInBackground", arrayOf<Void>().javaClass)
                    logger.info("Found inner class: ${cls.name}")
                    return cls
                } catch (_: NoSuchMethodException) {
                }
            } catch (_: ClassNotFoundException) {
            }
        }
        return null
    }

    /**
     * Get the current Android SDK version.
     */
    private fun getSDKVersion(): Int {
        return try {
            Build.VERSION.SDK_INT
        } catch (t: Throwable) {
            logger.error("Failed to fetch SDK level, use default.", t)
            Build.VERSION_CODES.BASE // Lowest version
        }
    }
}
