package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hook module to disable APK scanning.
 * Intercepts the PackageInstaller scan flow and returns a safe result directly.
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
        logger.info("Starting to hook PackageInstaller scan functionality...")

        // Method 1: skip the scan directly, returning a safe result immediately
        hookScanMethods(classLoader)

        // Method 2: intercept scan result handling
        hookResultMethods(classLoader)

        // Method 3: skip scan service binding
        hookServiceMethods(classLoader)

        logger.info("PackageInstaller scan hook setup complete")
    }

    private fun hookScanMethods(classLoader: ClassLoader) {
        try {
            // Intercept startScanApps so it returns without performing the scan
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val startScanApps = activityExtraClass.getDeclaredMethod("startScanApps")
            hookWithId(startScanApps, "start_scan_apps") { chain ->
                logger.debug("Intercepted startScanApps, skipping scan flow")
                // Send the scan-finished message immediately
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
                    logger.debug("Sent SCAN_APP_OK message")
                }

                null // Return directly without scanning
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook startScanApps", t)
        }
    }

    private fun hookResultMethods(classLoader: ClassLoader) {
        try {
            // Intercept showResultIfFinish to force-show the install UI
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val showResultIfFinish = activityExtraClass.getDeclaredMethod("showResultIfFinish")
            hookWithId(
                showResultIfFinish,
                "show_result_if_finish"
            ) { chain ->
                logger.debug("Intercepted showResultIfFinish")
                val activity = chain.thisObject

                // Force the scan result to "safe"
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

                logger.debug("Forced scan result to safe state")
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook showResultIfFinish", t)
        }
    }

    private fun hookServiceMethods(classLoader: ClassLoader) {
        try {
            // Intercept bindSafeService to skip service binding
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val bindSafeService = activityExtraClass.getDeclaredMethod("bindSafeService")
            hookWithId(
                bindSafeService,
                "bind_safe_service"
            ) { chain ->
                logger.debug("Intercepted bindSafeService, skipping service binding")
                val activity = chain.thisObject

                // Mark as bound to avoid retries
                val isBindField = activity.javaClass.getDeclaredField("isBind")
                isBindField.isAccessible = true
                isBindField.setBoolean(activity, true)

                val isConnectField = activity.javaClass.getDeclaredField("isConnect")
                isConnectField.isAccessible = true
                isConnectField.setBoolean(activity, true)

                // Send the scan-begin message immediately
                val mHanderField = activity.javaClass.getDeclaredField("mHander")
                mHanderField.isAccessible = true
                val handler = mHanderField.get(activity)
                if (handler != null) {
                    handler.javaClass.getDeclaredMethod(
                        "sendEmptyMessage",
                        Int::class.javaPrimitiveType
                    )
                        .invoke(handler, 1) // SCAN_APP_BEGIN
                    logger.debug("Sent SCAN_APP_BEGIN message")
                }

                null // Skip the actual binding
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook bindSafeService", t)
        }
    }

    companion object {
        private val PACKAGE_INSTALLER = ScopeKeys.PACKAGE_INSTALLER.packageName
    }
}
