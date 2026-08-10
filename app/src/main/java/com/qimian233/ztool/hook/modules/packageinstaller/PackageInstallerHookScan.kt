package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 禁用APK扫描Hook模块
 * 拦截PackageInstaller的扫描流程，直接返回安全结果
 */
@SuppressLint("PrivateApi")
class PackageInstallerHookScan : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_SCAN_APK.name

    override fun getTargetPackages(): Array<String> = arrayOf(PACKAGE_INSTALLER)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookPackageInstaller(classLoader)
    }

    private fun hookPackageInstaller(classLoader: ClassLoader) {
        logger.info("开始Hook PackageInstaller扫描功能...")

        // 方法1：直接跳过扫描，立即返回安全结果
        hookScanMethods(classLoader)

        // 方法2：拦截扫描结果处理
        hookResultMethods(classLoader)

        // 方法3：跳过扫描服务绑定
        hookServiceMethods(classLoader)

        logger.info("PackageInstaller扫描功能Hook完成")
    }

    private fun hookScanMethods(classLoader: ClassLoader) {
        try {
            // 拦截 startScanApps 方法，直接返回不执行扫描
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val startScanApps = activityExtraClass.getDeclaredMethod("startScanApps")
            hookWithId(startScanApps, "start_scan_apps") { chain ->
                logger.debug("拦截startScanApps，跳过扫描流程")
                // 立即发送扫描完成的消息
                val activity = chain.thisObject
                val mHanderField = activity.javaClass.getDeclaredField("mHander")
                mHanderField.isAccessible = true
                val handler = mHanderField.get(activity)
                if (handler != null) {
                    handler.javaClass.getDeclaredMethod(
                        "sendEmptyMessage",
                        Int::class.javaPrimitiveType
                    )
                        .invoke(handler, 2) // SCAN_APP_OK = 2
                    logger.debug("发送SCAN_APP_OK消息")
                }

                null // 直接返回，不执行扫描
            }
        } catch (t: Throwable) {
            logger.error("Hook startScanApps失败", t)
        }
    }

    private fun hookResultMethods(classLoader: ClassLoader) {
        try {
            // 拦截 showResultIfFinish 方法，强制显示安装界面
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val showResultIfFinish = activityExtraClass.getDeclaredMethod("showResultIfFinish")
            hookWithId(
                showResultIfFinish,
                "show_result_if_finish"
            ) { chain ->
                logger.debug("拦截showResultIfFinish")
                val activity = chain.thisObject

                // 强制设置扫描结果为安全
                val mScanAppResultField = activity.javaClass.getDeclaredField("mScanAppResult")
                mScanAppResultField.isAccessible = true
                mScanAppResultField.setInt(activity, 2) // SCAN_APP_OK

                val mCheckSafeInstallResultField =
                    activity.javaClass.getDeclaredField("mCheckSafeInstallResult")
                mCheckSafeInstallResultField.isAccessible = true
                mCheckSafeInstallResultField.setInt(activity, 1)

                val isScanBeginField = activity.javaClass.getDeclaredField("isScanBegin")
                isScanBeginField.isAccessible = true
                isScanBeginField.setBoolean(activity, true)

                logger.debug("强制设置扫描结果为安全状态")
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Hook showResultIfFinish失败", t)
        }
    }

    private fun hookServiceMethods(classLoader: ClassLoader) {
        try {
            // 拦截 bindSafeService 方法，跳过服务绑定
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val bindSafeService = activityExtraClass.getDeclaredMethod("bindSafeService")
            hookWithId(
                bindSafeService,
                "bind_safe_service"
            ) { chain ->
                logger.debug("拦截bindSafeService，跳过服务绑定")
                val activity = chain.thisObject

                // 设置已绑定状态，避免重试
                val isBindField = activity.javaClass.getDeclaredField("isBind")
                isBindField.isAccessible = true
                isBindField.setBoolean(activity, true)

                val isConnectField = activity.javaClass.getDeclaredField("isConnect")
                isConnectField.isAccessible = true
                isConnectField.setBoolean(activity, true)

                // 立即发送扫描开始消息
                val mHanderField = activity.javaClass.getDeclaredField("mHander")
                mHanderField.isAccessible = true
                val handler = mHanderField.get(activity)
                if (handler != null) {
                    handler.javaClass.getDeclaredMethod(
                        "sendEmptyMessage",
                        Int::class.javaPrimitiveType
                    )
                        .invoke(handler, 1) // SCAN_APP_BEGIN
                    logger.debug("发送SCAN_APP_BEGIN消息")
                }

                null // 跳过实际绑定
            }
        } catch (t: Throwable) {
            logger.error("Hook bindSafeService失败", t)
        }
    }

    companion object {
        private val PACKAGE_INSTALLER = ScopeKeys.PACKAGE_INSTALLER.packageName
    }
}
