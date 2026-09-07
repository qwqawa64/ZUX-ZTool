package com.qimian233.ztool.data.launcher

import com.qimian233.ztool.EnhancedShellExecutor

/**
 * 批量卸载（桌面多选入口）的执行仓库。
 *
 * 经启动器 Hook 收集到的包名在这里通过 Root shell 执行
 * `pm uninstall --user 0 <pkg>` 静默卸载；启动器收到
 * PACKAGE_REMOVED 广播后会自行清理对应桌面图标。
 */
class BatchUninstallRepository {

    fun checkRootAvailable(): Boolean {
        return EnhancedShellExecutor.getInstance().checkRootAccess().isSuccess()
    }

    fun uninstallPackage(packageName: String): UninstallResult {
        val sanitized = packageName.trim()
        if (!PACKAGE_NAME_REGEX.matches(sanitized)) {
            return UninstallResult(sanitized, false, "invalid package name")
        }
        val result = EnhancedShellExecutor.getInstance()
            .executeRootCommand("pm uninstall --user 0 $sanitized", UNINSTALL_TIMEOUT_SECONDS)
        val success = result.isSuccess()
        val message = if (success) {
            ""
        } else {
            (result.error.ifBlank { result.output })
                .lineSequence()
                .firstOrNull { it.isNotBlank() } ?: "unknown error"
        }
        return UninstallResult(sanitized, success, message)
    }

    companion object {
        /** 包名白名单字符集，防止拼接 shell 命令时注入。 */
        private val PACKAGE_NAME_REGEX = Regex("[A-Za-z0-9_.]+")
        private const val UNINSTALL_TIMEOUT_SECONDS = 30
    }
}

data class UninstallResult(
    val packageName: String,
    val success: Boolean,
    val message: String
)
