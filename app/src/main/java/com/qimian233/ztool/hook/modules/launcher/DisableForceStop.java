package com.qimian233.ztool.hook.modules.launcher

import android.content.Context
import android.os.Build
import com.qimian233.ztool.hook.base.BaseHookModule
import com.qimian233.ztool.hook.base.DexKitHelper.getBridgeForClass
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData

/**
 * ZUI Launcher后台管理优化Hook模块
 * 防止划掉后台卡片时杀死应用的后台服务
 * 智能适配Android 16+和Android 15-版本
 * 支持白名单机制，只保护指定应用
 */
class DisableForceStop : BaseHookModule() {
    // 白名单应用包名集合
    private var WHITE_LIST: Array<String>
    override fun getModuleName(): String {
        return "disable_force_stop"
    }

    override fun getTargetPackages(): Array<String?> {
        return arrayOf<String>(
            "com.zui.launcher"
        )
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.getDefaultClassLoader()
        val packageName = param.getPackageName()
        // 获取当前Android SDK版本
        val sdkVersion: Int = this.sDKVersion
        WHITE_LIST = this.whiteListPackages
        if (DEBUG) log(
            ("Current Android SDK: "
                    + sdkVersion
                    + ", target package name: "
                    + packageName)
        )
        if (DEBUG) log("White list enabled, app in whitelist: " + (WHITE_LIST.size))

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
    private fun hookForAndroid16Plus(classLoader: ClassLoader, packageName: String?) {
        try {
            if ("com.zui.launcher" == packageName) {
                hookZuiLauncherAndroid16(classLoader)
            } else if ("com.android.launcher3" == packageName) {
                hookBaseLauncherAndroid16()
            }
            log("Android 16+ Hook applied, whitelist protection enabled")
        } catch (t: Throwable) {
            logError("Android 16+ Hook failed!", t)
        }
    }

    private val isWhiteListEnabled: Boolean
        // 检查是否启用白名单保护
        get() {
            try {
                return this.xposed.getRemotePreferences("xposed_module_config")
                    .getBoolean(KEY_FORCE_STOP_WHITELIST_ENABLE, false)
            } catch (t: Throwable) {
                return false
            }
        }

    private val whiteListPackages: Array<String>
        // 获取白名单中的应用包名
        get() {
            var value: String?
            try {
                value = this.xposed.getRemotePreferences("xposed_module_config")
                    .getString(KEY_FORCE_STOP_WHITELIST, "")
            } catch (t: Throwable) {
                value = ""
            }
            if (value!!.isEmpty()) return arrayOfNulls<String>(0)
            return value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        }

    // 检查指定包名是否在白名单中
    private fun isProtectedPackage(packageName: String?): Boolean {
        if (!this.isWhiteListEnabled) return true // 白名单未启用，保护所有应用

        for (pkg in WHITE_LIST) {
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
    private fun hookForAndroid15Minus(classLoader: ClassLoader, packageName: String?) {
        try {
            if ("com.zui.launcher" == packageName || "com.android.launcher3" == packageName) {
                hookLegacyLauncher(classLoader)
            }
            log("Android 15- Hook applied, whitelist protection enabled")
        } catch (t: Throwable) {
            logError("Android 15- Hook failed!", t)
        }
    }

    /**
     * Android 16+ ZUI Launcher专用Hook（带白名单机制）
     */
    private fun hookZuiLauncherAndroid16(classLoader: ClassLoader) {
        try {
            val overviewUtilitiesClass =
                classLoader.loadClass("com.zui.launcher.util.OverviewUtilities")

            // Hook removeAppProcess方法 - 主要的进程杀死入口
            val removeAppProcessMethod = overviewUtilitiesClass.getDeclaredMethod(
                "removeAppProcess",
                Context::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            this.xposed.hook(removeAppProcessMethod)
                .intercept(Hooker { chain: XposedInterface.Chain? ->
                    val pkgName = chain!!.getArg(2) as String? // 注意：参数索引修正
                    val uid = chain.getArg(3) as Int

                    // 检查是否在白名单中
                    if (isProtectedPackage(pkgName)) {
                        // 在白名单中，阻止杀死操作
                        if (DEBUG) log(
                            ("Android 16: Avoid killing app in whitelist: "
                                    + pkgName
                                    + " (UID: " + uid + ")")
                        )
                        return@intercept null
                    }

                    // 不在白名单中，允许执行原方法
                    if (DEBUG) log("Android 16: Allow killing app: " + pkgName)
                    chain.proceed()
                })

            // Hook c方法 - 强制杀死进程的辅助方法（DEXKit 动态查找）
            val cMethodName = findCMethodName(classLoader)
            val cMethod = overviewUtilitiesClass.getDeclaredMethod(
                cMethodName, Context::class.java, String::class.java, Int::class.javaPrimitiveType
            )
            this.xposed.hook(cMethod).intercept(Hooker { chain: XposedInterface.Chain? ->
                val pkgName = chain!!.getArg(1) as String?
                val uid = chain.getArg(2) as Int

                // 检查是否在白名单中
                if (isProtectedPackage(pkgName)) {
                    // 在白名单中，阻止强制杀死
                    if (DEBUG) log(
                        ("Android 16: Blocked forced killing app in whitelist: "
                                + pkgName
                                + " (UID: " + uid + ")")
                    )
                    return@intercept null
                }

                // 不在白名单中，允许执行原方法
                if (DEBUG) log("Android 16: Allow forced killing app: " + pkgName)
                chain.proceed()
            })

            // Hook removeAllRunningAppProcesses方法 - 批量清理入口
            val removeAllMethod = overviewUtilitiesClass.getDeclaredMethod(
                "removeAllRunningAppProcesses",
                Context::class.java,
                ArrayList::class.java,
                Boolean::class.javaPrimitiveType
            )
            this.xposed.hook(removeAllMethod).intercept(Hooker { chain: XposedInterface.Chain? ->
                val tasks = chain!!.getArg(1) as ArrayList<*>?
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
                                if (DEBUG) log(
                                    "Android 16: Whitelist APP detected when performing batch kill: "
                                            + pkgName
                                )
                            }
                        } catch (e: Exception) {
                            // 如果无法获取包名，跳过
                        }
                    }

                    if (protectedCount > 0) {
                        // 如果包含白名单应用，阻止整个批量清理操作
                        if (DEBUG) log(
                            ("Android 16: " + protectedCount
                                    + "included in batch kill list, blocking kill operation")
                        )
                        return@intercept null
                    }

                    // 不包含白名单应用，允许执行批量清理
                    if (DEBUG) log("Android 16: " + totalTasks + " APP(s) are allowed to be killed.")
                }
                chain.proceed()
            })

            // Hook AsyncTask子类的doInBackground方法 - 异步清理逻辑
            val asyncTaskClass = findInnerClass(classLoader)

            if (asyncTaskClass != null) {
                val doInBackgroundMethod =
                    asyncTaskClass.getDeclaredMethod("doInBackground", Array<Void>::class.java)
                this.xposed.hook(doInBackgroundMethod)
                    .intercept(Hooker { chain: XposedInterface.Chain? ->
                        try {
                            // 尝试获取任务列表
                            val thisObject = chain!!.getThisObject()
                            val tasksField = thisObject.javaClass.getDeclaredField("tasks")
                            tasksField.setAccessible(true)
                            val tasks = tasksField.get(thisObject)

                            if (tasks is ArrayList<*>) {
                                val taskList = tasks
                                for (task in taskList) {
                                    try {
                                        val pkgName = getPackageNameFromTask(task)
                                        if (pkgName != null && isProtectedPackage(pkgName)) {
                                            if (DEBUG) log(
                                                ("Android 16: Whitelist app detected in async task, count: "
                                                        + pkgName + ", blocking async task")
                                            )
                                            return@intercept null
                                        }
                                    } catch (e: Exception) {
                                        // 跳过无法识别的任务
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // 如果无法检查，默认阻止
                            if (DEBUG) log("Android 16: Unable to check async task, blocking it by default")
                            return@intercept null
                        }
                        // 不包含白名单应用，允许执行
                        if (DEBUG) log("Android 16: Allowed to perform async kill")
                        chain.proceed()
                    })
            }

            // 尝试Hook Android 16可能新增的方法
            hookAdditionalAndroid16Methods(classLoader)

            log("Hook for Android 16+ ZUI Launcher successfully applied.")
        } catch (t: Throwable) {
            logError("Android 16+: Failed to hook ZUI Launcher", t)
        }
    }

    /**
     * Android 16+ 基础Launcher Hook
     */
    private fun hookBaseLauncherAndroid16() {
        try {
            // Android 16上基础Launcher可能的Hook点
            // 这里可以根据需要添加对com.android.launcher3的特定Hook
            log("Android 16 logic not implemented yet!")
        } catch (t: Throwable) {
            logError("Android 16+: failed to hook basic Launcher", t)
        }
    }

    /**
     * Android 15及以下版本的通用Hook策略（带白名单机制）
     */
    private fun hookLegacyLauncher(classLoader: ClassLoader) {
        try {
            log("Start hooking legacy Launcher with whitelist enabled.")

            // Hook ActivityManagerWrapper类的方法
            var amwclass: Class<*>?
            try {
                amwclass =
                    classLoader.loadClass("com.android.systemui.shared.system.ActivityManagerWrapper")
            } catch (e: ClassNotFoundException) {
                amwclass = null
            }

            if (amwclass != null) {
                if (DEBUG) log("找到ActivityManagerWrapper类，开始Hook...")

                val removeAllMethod = amwclass.getDeclaredMethod(
                    "removeAllRunningAppProcesses", Context::class.java, ArrayList::class.java
                )
                this.xposed.hook(removeAllMethod)
                    .intercept(Hooker { chain: XposedInterface.Chain? ->
                        val tasks = chain!!.getArg(1) as ArrayList<*>?
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
                                if (DEBUG) log("传统架构: 批量清理包含 " + protectedCount + " 个白名单应用，阻止清理")
                                return@intercept null
                            }
                        }
                        chain.proceed()
                    })

                val removeAppProcessMethod = amwclass.getDeclaredMethod(
                    "removeAppProcess",
                    Context::class.java,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                this.xposed.hook(removeAppProcessMethod)
                    .intercept(Hooker { chain: XposedInterface.Chain? ->
                        val pkgName = chain!!.getArg(2) as String?
                        if (isProtectedPackage(pkgName)) {
                            if (DEBUG) log("传统架构: 阻止杀死白名单应用: " + pkgName)
                            return@intercept null
                        }
                        chain.proceed()
                    })

                log("ActivityManagerWrapper Hook完成 [OK]，白名单机制生效")
            } else {
                log("未找到ActivityManagerWrapper类，尝试其他Hook点...")
                // 可以添加备用的Hook点
            }
        } catch (e: Exception) {
            logError("Failed to hook legacy launcher", e)
        }
    }

    /**
     * Android 16可能新增的Hook点
     */
    private fun hookAdditionalAndroid16Methods(classLoader: ClassLoader) {
        try {
            // 尝试Hook Android 16可能新增的任务管理相关方法
            val potentialClasses = arrayOf<String?>(
                "com.zui.launcher.taskbar.TaskbarManager",
                "com.zui.launcher.recents.RecentsModel",
                "com.zui.launcher.recents.TaskStackListener"
            )

            for (className in potentialClasses) {
                var targetClass: Class<*>?
                try {
                    targetClass = classLoader.loadClass(className)
                } catch (e: ClassNotFoundException) {
                    targetClass = null
                }
                if (targetClass != null) {
                    if (DEBUG) log("Android 16 new class: " + className)
                    // 可以根据需要添加具体的Hook逻辑
                }
            }
        } catch (t: Throwable) {
            // 忽略错误，这些是可选的Hook点
            if (DEBUG) log("Android 16 extra hook points detection completed.")
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
            val componentNameField = task.javaClass.getDeclaredField("componentName")
            componentNameField.setAccessible(true)
            val componentName = componentNameField.get(task)
            if (componentName != null) {
                val getPackageNameMethod = componentName.javaClass.getMethod("getPackageName")
                val packageNameObj = getPackageNameMethod.invoke(componentName)
                if (packageNameObj is String) {
                    return packageNameObj
                }
            }

            // 方法2：尝试直接获取packageName字段
            try {
                val packageNameField = task.javaClass.getDeclaredField("packageName")
                packageNameField.setAccessible(true)
                val packageNameFieldVal = packageNameField.get(task)
                if (packageNameFieldVal is String) {
                    return packageNameFieldVal
                }
            } catch (e: NoSuchFieldException) {
                // 字段可能不存在，继续尝试其他方法
            }

            // 方法3：尝试通过BaseActivityInfo获取包名
            try {
                val baseActivityInfoField = task.javaClass.getDeclaredField("baseActivityInfo")
                baseActivityInfoField.setAccessible(true)
                val baseActivityInfo = baseActivityInfoField.get(task)
                if (baseActivityInfo != null) {
                    val packageNameField =
                        baseActivityInfo.javaClass.getDeclaredField("packageName")
                    packageNameField.setAccessible(true)
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
                val taskDescriptionField = task.javaClass.getDeclaredField("taskDescription")
                taskDescriptionField.setAccessible(true)
                val taskDescription = taskDescriptionField.get(task)
                if (taskDescription != null) {
                    val getPackageNameMethod = taskDescription.javaClass.getMethod("getPackageName")
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
     * 通过 DEXKit 查找 OverviewUtilities 中签名 (Context, String, int)→void 的混淆方法名。
     * 跳过已知的非混淆方法 removeAppProcess (Context, int, String, int)。
     */
    private fun findCMethodName(classLoader: ClassLoader): String {
        val bridge = getBridgeForClass(
            classLoader, "com.zui.launcher.util.OverviewUtilities"
        )
        if (bridge != null) {
            try {
                val methods: MutableList<MethodData> = bridge.findMethod(
                    FindMethod.create()
                        .searchPackages("com.zui.launcher")
                        .matcher(
                            MethodMatcher.create()
                                .paramTypes(
                                    "android.content.Context",
                                    "java.lang.String", "int"
                                )
                                .returnType("void")
                                .declaredClass("com.zui.launcher.util.OverviewUtilities")
                        )
                )
                for (md in methods) {
                    if ("removeAppProcess" != md.name) {
                        if (DEBUG) log("DEXKit found force-stop method: " + md.name)
                        return md.name
                    }
                }
            } catch (ignored: Throwable) {
            }
        }
        return "c" // 回退硬编码
    }

    /**
     * 通过反射查找内部类（处理混淆后的内部类名）。
     * 遍历可能的内部类名（$1-$5, $a-$e）直到找到有 doInBackground 方法的类。
     */
    private fun findInnerClass(classLoader: ClassLoader): Class<*>? {
        // 先尝试常见混淆模式: $a, $b, $c, $d, $e
        var suffix = 'a'
        while (suffix <= 'e') {
            try {
                val cls =
                    classLoader.loadClass("com.zui.launcher.util.OverviewUtilities" + "$" + suffix)
                // 验证：该内部类应有 doInBackground 方法
                try {
                    cls.getDeclaredMethod("doInBackground", Array<Void>::class.java)
                    if (DEBUG) log("Found inner class: " + cls.getName())
                    return cls
                } catch (ignored: NoSuchMethodException) {
                }
            } catch (ignored: ClassNotFoundException) {
            }
            suffix++
        }
        // 再尝试数字后缀: $1, $2, $3, $4, $5
        for (i in 1..5) {
            try {
                val cls = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities" + "$" + i)
                try {
                    cls.getDeclaredMethod("doInBackground", Array<Void>::class.java)
                    if (DEBUG) log("Found inner class: " + cls.getName())
                    return cls
                } catch (ignored: NoSuchMethodException) {
                }
            } catch (ignored: ClassNotFoundException) {
            }
        }
        return null
    }

    private val sDKVersion: Int
        /**
         * 获取当前Android SDK版本
         */
        get() {
            try {
                return Build.VERSION.SDK_INT
            } catch (t: Throwable) {
                logError("Failed to fetch SDK level, use default.", t)
                return Build.VERSION_CODES.BASE // 返回最低版本
            }
        }

    companion object {
        private const val KEY_FORCE_STOP_WHITELIST_ENABLE = "ForceStopWhiteListEnable"
        private const val KEY_FORCE_STOP_WHITELIST = "ForceStopWhiteList"
    }
}
