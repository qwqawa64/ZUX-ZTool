package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.Boolean
import kotlin.Array
import kotlin.Exception
import kotlin.String
import kotlin.Throwable
import kotlin.arrayOf

/**
 * ZUI包安装器Hook模块
 * 功能：绕过ZUI系统的安装限制，修改包安装器界面样式
 * 目标：com.android.packageinstaller (ZUI系统包安装器)
 */
@SuppressLint("PrivateApi")
class PackageInstallerStyleHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.PACKAGE_INSTALLER_STYLE_HOOK.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.PACKAGE_INSTALLER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookZuiPackageInstaller(classLoader)
        doNotShowWarnTextHook(classLoader)
    }

    private fun hookZuiPackageInstaller(classLoader: ClassLoader) {
        try {
            // 1. Hook Utils类的isCTSandGTS方法，绕过安装限制
            hookInstallationRestrictions(classLoader)

            // 2. Hook Activity样式，修改界面显示
            hookActivityStyles(classLoader)

            logger.info("ZUI Package Installer Hook 成功加载")
        } catch (t: Throwable) {
            logger.error("ZUI Package Installer Hook 加载失败", t)
        }
    }

    private fun hookInstallationRestrictions(classLoader: ClassLoader) {
        try {
            val utilsClass = classLoader.loadClass(
                "com.android.packageinstaller.extra.Utils"
            )

            // Hook isCTSandGTS方法的重载版本
            val isCTSandGTS1 = utilsClass.getDeclaredMethod("isCTSandGTS", String::class.java)
            hookWithId(
                isCTSandGTS1,
                "is_ct_sand_gts1"
            ) { Boolean.TRUE }

            val isCTSandGTS2 =
                utilsClass.getDeclaredMethod("isCTSandGTS", String::class.java, Intent::class.java)
            hookWithId(
                isCTSandGTS2,
                "is_ct_sand_gts2"
            ) { Boolean.TRUE }

            logger.info("成功Hook安装限制检查方法")
        } catch (t: Throwable) {
            logger.error("Hook安装限制检查方法失败", t)
        }
    }

    private fun hookActivityStyles(classLoader: ClassLoader) {
        try {
            // 获取Theme_AlertDialogActivity的资源ID
            val styleClass = classLoader.loadClass(
                $$"com.android.packageinstaller.R$style"
            )
            val themeField = styleClass.getDeclaredField("Theme_AlertDialogActivity")
            themeField.isAccessible = true
            val themeAlertDialogActivity = themeField.getInt(null)

            // Hook Activity的onCreate方法，修改主题和窗口属性
            val onCreate = Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
            hookWithId(onCreate, "on_create") { chain ->
                val activity = chain.thisObject as Activity
                // 检查是否为目标包安装器的Activity
                if (activity.packageName == ScopeKeys.PACKAGE_INSTALLER.packageName) {
                    try {
                        // 设置对话框主题
                        activity.setTheme(themeAlertDialogActivity)

                        // 设置透明背景
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            activity.setTranslucent(true)
                        }

                        // 请求无标题栏
                        activity.requestWindowFeature(1) // 1对应Window.FEATURE_NO_TITLE

                        // 禁用窗口动画
                        activity.window.setWindowAnimations(0)

                        logger.debug("成功修改包安装器Activity样式")
                    } catch (t: Throwable) {
                        logger.error("修改Activity样式时出错", t)
                    }
                }
                chain.proceed()
            }

            logger.info("成功Hook Activity样式修改")
        } catch (t: Throwable) {
            logger.error("Hook Activity样式修改失败", t)
        }
    }

    private fun doNotShowWarnTextHook(classLoader: ClassLoader) {
        try {
            val activityClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivity"
            )
            val startInstallConfirm = activityClass.getDeclaredMethod("startInstallConfirm")
            hookWithId(
                startInstallConfirm,
                "start_install_confirm"
            ) { chain ->
                val result = chain.proceed()
                try {
                    val resourcesClass =
                        classLoader.loadClass($$"com.android.packageinstaller.R$id")
                    val warnTextViewIdField =
                        resourcesClass.getDeclaredField("install_confirm_question_warning")
                    warnTextViewIdField.isAccessible = true
                    val warnTextViewId = warnTextViewIdField.getInt(null)

                    val mDialogField =
                        chain.thisObject.javaClass.getDeclaredField("mDialog")
                    mDialogField.isAccessible = true
                    val dialog = mDialogField.get(chain.thisObject) as AlertDialog?

                    val tv = dialog!!.findViewById<TextView>(warnTextViewId)
                    tv.visibility = TextView.GONE
                    logger.debug("Successfully set install warn visibility to GONE")
                } catch (e: Exception) {
                    logger.error("Exception happened when trying to set warn text to GONE!", e)
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook doNotShowWarnTextHook", t)
        }
    }
}
