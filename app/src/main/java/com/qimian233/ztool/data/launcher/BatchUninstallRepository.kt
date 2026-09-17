package com.qimian233.ztool.data.launcher

import com.qimian233.ztool.EnhancedShellExecutor

/**
 * Execution repository for batch uninstall (launcher multi-select entry).
 *
 * Package names collected via the launcher hook are silently uninstalled here
 * through a root shell with `pm uninstall --user 0 <pkg>`; after the launcher
 * receives the PACKAGE_REMOVED broadcast it cleans up the corresponding desktop icons itself.
 */
class BatchUninstallRepository {

    /** Diagnostic: returns the root check result and raw output, useful for troubleshooting KernelSU/Magisk differences on site. */
    fun checkRootAccess(): Pair<Boolean, String> {
        val result = EnhancedShellExecutor.getInstance().checkRootAccess()
        return Pair(
            result.isSuccess,
            "exit=${result.exitCode} out=${result.output.take(160)} err=${result.error.take(160)}"
        )
    }

    fun uninstallPackage(packageName: String): UninstallResult {
        val sanitized = packageName.trim()
        if (!PACKAGE_NAME_REGEX.matches(sanitized)) {
            return UninstallResult(sanitized, false, "invalid package name")
        }
        val result = EnhancedShellExecutor.getInstance()
            .executeRootCommand("pm uninstall --user 0 $sanitized", UNINSTALL_TIMEOUT_SECONDS)
        val success = result.isSuccess
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
        /** Package-name whitelist charset, prevents shell command injection when concatenating commands. */
        private val PACKAGE_NAME_REGEX = Regex("[A-Za-z0-9_.]+")
        private const val UNINSTALL_TIMEOUT_SECONDS = 30
    }
}

data class UninstallResult(
    val packageName: String,
    val success: Boolean,
    val message: String
)
