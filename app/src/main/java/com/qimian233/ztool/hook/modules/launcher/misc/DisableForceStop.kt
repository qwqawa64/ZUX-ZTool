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
 * ZUI Launcher后台管理优化Hook模块
 * 防止划掉后台卡片时杀死应用的后台服务
 * 智能适配Android 16+和Android 15-版本
 * 支持白名单机制，只保护指定应用
 */
class DisableForceStop : AppHookModule() {

    // 白名单应用包名集合
    private var whiteList: Array<String> = arrayOf()

    override fun getModuleName(): String = PreferenceKeys.DISABLE_FORCE_STOP.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        // 获取当前Android SDK版本
        val sdkVersion = getSDKVersion()
        whiteList = getWhiteListPackages()
        logger.trace("Current Android SDK: $sdkVersion, target package name: $packageName")
        logger.trace("White list enabled, app in whitelist: ${whiteList.size}")

        // 根据Android版本选择Hook策略
        if (sdkVersion >= 36) { // 包括Android 16
            hookForAndroid16Plus(classLoader, packageName)
        } else {
            hookForAndroid15Minus(classLoader, packageName)
        }
    }

    /**
     * Android 16+版本的Hook策略
     * 针对ZUI Launcher桌面大改后的新架构
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

    // 检查是否启用白名单保护
    private fun isWhiteListEnabled(): Boolean {
        return try {
            remotePreferences.getBoolean(PreferenceKeys.FORCE_STOP_WHITE_LIST_ENABLE.name, false)
        } catch (_: Throwable) {
            false
        }
    }

    // 获取白名单中的应用包名
    private fun getWhiteListPackages(): Array<String> {
        val value = try {
            remotePreferences.getString(PreferenceKeys.FORCE_STOP_WHITE_LIST.name, "")
        } catch (_: Throwable) {
            ""
        }
        if (value.isNullOrEmpty()) return arrayOf()
        return value.split(",").toTypedArray()
    }

    // 检查指定包名是否在白名单中
    private fun isProtectedPackage(packageName: String): Boolean {
        if (!isWhiteListEnabled()) return true // 白名单未启用，保护所有应用
        for (pkg in whiteList) {
            if (pkg == packageName) {
                return true
            }
        }
        return false
    }

    /**
     * Android 15及以下版本的Hook策略
     * 针对传统Launcher架构
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
     * Android 16+ ZUI Launcher专用Hook（带白名单机制）
     */
    private fun hookZuiLauncherAndroid16(classLoader: ClassLoader) {
        try {
            val overviewUtilitiesClass = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities")

            // Hook removeAppProcess方法 - 主要的进程杀死入口
            val removeAppProcessMethod: Method = overviewUtilitiesClass.getDeclaredMethod(
                "removeAppProcess", Context::class.java, Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(removeAppProcessMethod, "remove_app_process_1") { chain ->
                val pkgName = chain.args[2] as String // 注意：参数索引修正
                val uid = chain.args[3] as Int

                // 检查是否在白名单中
                if (isProtectedPackage(pkgName)) {
                    // 在白名单中，阻止杀死操作
                    logger.trace("Android 16: Avoid killing app in whitelist: $pkgName (UID: $uid)")
                    return@hookWithId null
                }

                // 不在白名单中，允许执行原方法
                logger.trace("Android 16: Allow killing app: $pkgName")
                chain.proceed()
            }

            // Hook c方法 - 强制杀死进程的辅助方法（DEXKit 动态查找）
            val cMethodName = findCMethodName()
            val cMethod: Method = overviewUtilitiesClass.getDeclaredMethod(
                cMethodName, Context::class.java, String::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(cMethod, "hook_165") { chain ->
                val pkgName = chain.args[1] as String
                val uid = chain.args[2] as Int

                // 检查是否在白名单中
                if (isProtectedPackage(pkgName)) {
                    // 在白名单中，阻止强制杀死
                    logger.trace("Android 16: Blocked forced killing app in whitelist: $pkgName (UID: $uid)")
                    return@hookWithId null
                }

                // 不在白名单中，允许执行原方法
                logger.trace("Android 16: Allow forced killing app: $pkgName")
                chain.proceed()
            }

            // Hook removeAllRunningAppProcesses方法 - 批量清理入口
            val removeAllMethod: Method = overviewUtilitiesClass.getDeclaredMethod(
                "removeAllRunningAppProcesses", Context::class.java, ArrayList::class.java, Boolean::class.javaPrimitiveType
            )
            hookWithId(removeAllMethod, "remove_all_1") { chain ->
                val tasks = chain.args[1] as ArrayList<*>?

                if (tasks != null) {
                    val totalTasks = tasks.size
                    var protectedCount = 0

                    // 记录白名单应用
                    for (task in tasks) {
                        try {
                            // 尝试获取任务对应的包名
                            val pkgName = getPackageNameFromTask(task)
                            if (pkgName != null && isProtectedPackage(pkgName)) {
                                protectedCount++
                                logger.trace("Android 16: Whitelist APP detected when performing batch kill: $pkgName")
                            }
                        } catch (e: Exception) {
                            // 如果无法获取包名，跳过
                        }
                    }

                    if (protectedCount > 0) {
                        // 如果包含白名单应用，阻止整个批量清理操作
                        logger.trace("Android 16: $protectedCount included in batch kill list, blocking kill operation")
                        return@hookWithId null
                    }

                    // 不包含白名单应用，允许执行批量清理
                    logger.trace("Android 16: $totalTasks APP(s) are allowed to be killed.")
                }

                chain.proceed()
            }

            // Hook AsyncTask子类的doInBackground方法 - 异步清理逻辑
            val asyncTaskClass = findInnerClass(classLoader)

            if (asyncTaskClass != null) {
                val doInBackgroundMethod: Method =
                    asyncTaskClass.getDeclaredMethod("doInBackground", arrayOf<Void>().javaClass)
                hookWithId(doInBackgroundMethod, "do_in_background") { chain ->
                    try {
                        // 尝试获取任务列表
                        val thisObject = chain.thisObject
                        val tasksField: Field = thisObject.javaClass.getDeclaredField("tasks")
                        tasksField.isAccessible = true
                        val tasks = tasksField.get(thisObject)

                        if (tasks is ArrayList<*>) {
                            val taskList = tasks
                            for (task in taskList) {
                                try {
                                    val pkgName = getPackageNameFromTask(task)
                                    if (pkgName != null && isProtectedPackage(pkgName)) {
                                        logger.trace("Android 16: Whitelist app detected in async task, count: $pkgName, blocking async task")
                                        return@hookWithId null
                                    }
                                } catch (e: Exception) {
                                    // 跳过无法识别的任务
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 如果无法检查，默认阻止
                        logger.warn("Android 16: Unable to check async task, blocking it by default")
                        return@hookWithId null
                    }

                    // 不包含白名单应用，允许执行
                    logger.trace("Android 16: Allowed to perform async kill")
                    chain.proceed()
                }
            }

            // 尝试Hook Android 16可能新增的方法
            hookAdditionalAndroid16Methods(classLoader)

            logger.info("Hook for Android 16+ ZUI Launcher successfully applied.")
        } catch (t: Throwable) {
            logger.error("Android 16+: Failed to hook ZUI Launcher", t)
        }
    }

    /**
     * Android 16+ 基础Launcher Hook
     */
    private fun hookBaseLauncherAndroid16() {
        try {
            // Android 16上基础Launcher可能的Hook点
            // 这里可以根据需要添加对com.android.launcher3的特定Hook
            logger.warn("Android 16 logic not implemented yet!")
        } catch (t: Throwable) {
            logger.error("Android 16+: failed to hook basic Launcher", t)
        }
    }

    /**
     * Android 15及以下版本的通用Hook策略（带白名单机制）
     */
    @SuppressLint("PrivateApi")
    private fun hookLegacyLauncher(classLoader: ClassLoader) {
        try {
            logger.info("Start hooking legacy Launcher with whitelist enabled.")

            // Hook ActivityManagerWrapper类的方法
            val amwclass = try {
                classLoader.loadClass("com.android.systemui.shared.system.ActivityManagerWrapper")
            } catch (e: ClassNotFoundException) {
                null
            }

            if (amwclass != null) {
                logger.info("找到ActivityManagerWrapper类，开始Hook...")

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
                            } catch (e: Exception) {
                                // 跳过无法识别的任务
                            }
                        }

                        if (protectedCount > 0) {
                            logger.trace("传统架构: 批量清理包含 $protectedCount 个白名单应用，阻止清理")
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
                        logger.trace("传统架构: 阻止杀死白名单应用: $pkgName")
                        return@hookWithId null
                    }

                    chain.proceed()
                }

                logger.info("ActivityManagerWrapper Hook完成 [OK]，白名单机制生效")
            } else {
                logger.warn("未找到ActivityManagerWrapper类，尝试其他Hook点...")
                // 可以添加备用的Hook点
            }
        } catch (e: Exception) {
            logger.error("Failed to hook legacy launcher", e)
        }
    }

    /**
     * Android 16可能新增的Hook点
     */
    private fun hookAdditionalAndroid16Methods(classLoader: ClassLoader) {
        try {
            // 尝试Hook Android 16可能新增的任务管理相关方法
            val potentialClasses = arrayOf(
                "com.zui.launcher.taskbar.TaskbarManager",
                "com.zui.launcher.recents.RecentsModel",
                "com.zui.launcher.recents.TaskStackListener"
            )

            for (className in potentialClasses) {
                val targetClass = try {
                    classLoader.loadClass(className)
                } catch (e: ClassNotFoundException) {
                    null
                }
                if (targetClass != null) {
                    logger.debug("Android 16 new class: $className")
                    // 可以根据需要添加具体的Hook逻辑
                }
            }
        } catch (t: Throwable) {
            // 忽略错误，这些是可选的Hook点
            logger.info("Android 16 extra hook points detection completed.")
        }
    }

    /**
     * 从任务对象中提取包名
     * @param task 任务对象
     * @return 包名，如果无法提取则返回null
     */
    private fun getPackageNameFromTask(task: Any?): String? {
        if (task == null) {
            return null
        }

        try {
            // 方法1：尝试通过ComponentName获取包名
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

            // 方法2：尝试直接获取packageName字段
            try {
                val packageNameField: Field = task.javaClass.getDeclaredField("packageName")
                packageNameField.isAccessible = true
                val packageNameFieldVal = packageNameField.get(task)
                if (packageNameFieldVal is String) {
                    return packageNameFieldVal
                }
            } catch (e: NoSuchFieldException) {
                // 字段可能不存在，继续尝试其他方法
            }

            // 方法3：尝试通过BaseActivityInfo获取包名
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
            } catch (e: NoSuchFieldException) {
                // 字段可能不存在
            }

            // 方法4：尝试通过taskDescription获取包名
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
            } catch (e: NoSuchFieldException) {
                // 字段可能不存在
            }
        } catch (e: Exception) {
            // 所有方法都失败，返回null
        }

        return null
    }

    /**
     * 从离线索引读取 OverviewUtilities 中签名 (Context, String, int)→void 的混淆方法名。
     * 索引缺失/失败时回退硬编码 "c"。
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
        return "c" // 回退硬编码
    }

    /**
     * 通过反射查找内部类（处理混淆后的内部类名）。
     * 遍历可能的内部类名（$1-$5, $a-$e）直到找到有 doInBackground 方法的类。
     */
    private fun findInnerClass(classLoader: ClassLoader): Class<*>? {
        // 先尝试常见混淆模式: $a, $b, $c, $d, $e
        for (suffix in 'a'..'e') {
            try {
                val cls = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities" + "$" + suffix)
                // 验证：该内部类应有 doInBackground 方法
                try {
                    cls.getDeclaredMethod("doInBackground", arrayOf<Void>().javaClass)
                    logger.info("Found inner class: ${cls.name}")
                    return cls
                } catch (_: NoSuchMethodException) {
                }
            } catch (_: ClassNotFoundException) {
            }
        }
        // 再尝试数字后缀: $1, $2, $3, $4, $5
        for (i in 1..5) {
            try {
                val cls = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities" + "$" + i)
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
     * 获取当前Android SDK版本
     */
    private fun getSDKVersion(): Int {
        return try {
            Build.VERSION.SDK_INT
        } catch (t: Throwable) {
            logger.error("Failed to fetch SDK level, use default.", t)
            Build.VERSION_CODES.BASE // 返回最低版本
        }
    }
}
