package com.qimian233.ztool.data.misc

import android.annotation.SuppressLint
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.XposedServiceBridge
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository

/**
 * Miscellaneous settings repository: maintenance tools and device info
 * that are neither experimental nor part of the hook pipeline.
 */
class MiscSettingsRepository(
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {

    /** Get the libxposed API version, or 0 when not activated */
    fun getApiVersion(): Int = XposedServiceBridge.getApiVersion()

    /**
     * Delete /data/ota_package and everything inside it via root shell.
     * The UDS real-time connection engine (com.lenovo.tbengine) drops automatically downloaded update packages there.
     *
     * @param onComplete callback (caller thread), params: (status, message)
     */
    fun deleteOtaPackage(
        onComplete: (status: String, message: String) -> Unit
    ) {
        val exists = shellExecutor.executeRootCommand("ls -d $OTA_PACKAGE_DIR")
        if (!exists.isSuccess || exists.output.trim().isEmpty()) {
            onComplete(STATUS_NOT_EXIST, "$OTA_PACKAGE_DIR 不存在")
            return
        }
        val result = shellExecutor.executeRootCommand("rm -rf $OTA_PACKAGE_DIR", DELETE_TIMEOUT_SECONDS)
        if (result.isSuccess) {
            // Verify the directory is really gone, so a silent rm failure is not treated as success
            val verify = shellExecutor.executeRootCommand("ls -d $OTA_PACKAGE_DIR")
            if (verify.isSuccess && verify.output.trim().isNotEmpty()) {
                onComplete(STATUS_FAILED, "删除后 /data/ota_package 仍存在：${result.error.ifEmpty { "未知原因" }}")
            } else {
                onComplete(STATUS_SUCCESS, "已删除 $OTA_PACKAGE_DIR")
            }
        } else {
            onComplete(STATUS_FAILED, "删除失败：${result.error}")
        }
    }

    /**
     * One-shot root fix: clears the ZUI-persisted night-mode override
     * (ui_night_mode_override_on/off) and retunes uimode so dark theme
     * auto switching ("sunset to sunrise") takes effect immediately.
     */
    fun fixNightModeOverride(context: android.content.Context) =
        FrameworkSettingsRepository(context).fixNightModeOverride()

    companion object {
        private const val OTA_PACKAGE_DIR = "/data/ota_package"
        private const val DELETE_TIMEOUT_SECONDS = 120

        const val STATUS_SUCCESS = "SUCCEEDED"
        const val STATUS_NOT_EXIST = "NOT_EXIST"
        const val STATUS_FAILED = "FAILED"
    }
}
