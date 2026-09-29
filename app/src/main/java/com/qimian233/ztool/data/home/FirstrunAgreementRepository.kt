package com.qimian233.ztool.data.home

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.ModuleActivationProbe

class FirstrunAgreementRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance(),
    private val moduleActiveChecker: () -> Boolean = ModuleActivationProbe::isModuleActive
) {
    private val appContext = context.applicationContext

    fun loadInitialState(): FirstrunCheckState {
        return FirstrunCheckState(
            hasRoot = hasRootAccess(),
            isModuleActive = isModuleActive(),
            canListApps = canListInstalledApplications(),
            hasUsageStats = hasUsageStatsPermission(),
            hasOverlay = hasOverlayPermission()
        )
    }

    /**
     * Full check for the Permissions page (background thread): adds the auto-start
     * query, which costs an extra root shell round-trip and is therefore skipped in
     * [loadInitialState] (called on the main thread at ViewModel init).
     */
    fun refreshState(): FirstrunCheckState = FirstrunCheckState(
        hasRoot = hasRootAccess(),
        isModuleActive = isModuleActive(),
        canListApps = canListInstalledApplications(),
        hasUsageStats = hasUsageStatsPermission(),
        hasOverlay = hasOverlayPermission(),
        hasAutoStart = hasAutoStartPermission()
    )

    fun hasRootAccess(): Boolean = shellExecutor.checkRootAccess().isSuccess

    fun isModuleActive(): Boolean = moduleActiveChecker()

    fun canListInstalledApplications(): Boolean {
        return try {
            val apps = appContext.packageManager.getInstalledApplications(0)
            val packages = appContext.packageManager.getInstalledPackages(0)
            apps.size > 1 || packages.size > 1 || apps.any { it.packageName == appContext.packageName }
        } catch (_: Exception) {
            false
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                appContext.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(appContext)

    /**
     * Reads ZUI's per-app auto-start switch (联想安全中心 → 自启动管理). The state lives
     * in safecenter's AutoRunProvider (`com.lenovo.performance.autorun.querylist`,
     * row column `state`, 1 = allowed). The provider is exported but guarded by a
     * signature|privileged permission, so the query runs through the root shell.
     * Authority/column names may shift across ZUX releases; any failure reports
     * "not granted" and the card simply stays unchecked (it does not gate the flow).
     */
    fun hasAutoStartPermission(): Boolean {
        if (!shellExecutor.checkRootAccess().isSuccess) return false
        val query = shellExecutor.executeRootCommand(
            "content query --uri content://$AUTO_RUN_AUTHORITY --projection pkgName:state")
        if (!query.isSuccess) return false
        val row = query.output.lineSequence()
            .firstOrNull { "pkgName=${appContext.packageName}," in it } ?: return false
        return Regex(", state=(\\d+)").find(row)?.groupValues?.get(1) == "1"
    }

    /**
     * Opens ZUI's auto-start management page via the root shell — the activity is
     * not exported, so only root/system can start it directly. Returns false so the
     * caller can fall back to [openAutoStartSettingsFallback].
     */
    fun openAutoRunPageWithRoot(): Boolean {
        if (!shellExecutor.checkRootAccess().isSuccess) return false
        val result = shellExecutor.executeRootCommand(
            "am start -n $SAFECENTER_PACKAGE/com.lenovo.performance.autorun.activitys.AutoRunMainActivity")
        // `am start` may exit 0 while reporting failures like "Error type 3".
        return result.isSuccess && !result.output.contains("Error", ignoreCase = true)
    }

    /** Exported fallbacks: safecenter's per-app permission manager, then its launcher entry. */
    fun openAutoStartSettingsFallback(): Boolean {
        val permManager = Intent(ACTION_SAFECENTER_APP_PERMISSION)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStartActivity(permManager)) return true
        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(SAFECENTER_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return tryStartActivity(launchIntent)
    }

    private fun tryStartActivity(intent: Intent): Boolean = try {
        appContext.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val SAFECENTER_PACKAGE = "com.zui.safecenter"
        const val AUTO_RUN_AUTHORITY = "com.lenovo.performance.autorun.querylist"
        const val ACTION_SAFECENTER_APP_PERMISSION = "com.zui.safecenter.permissionmanager.AppPermission"
    }
}

data class FirstrunCheckState(
    val hasRoot: Boolean = false,
    val isModuleActive: Boolean = false,
    val canListApps: Boolean = false,
    val hasUsageStats: Boolean = false,
    val hasOverlay: Boolean = false,
    // ZUI-specific assistive item, deliberately NOT part of allGranted: it is only
    // detectable with root and does not exist on non-ZUI builds.
    val hasAutoStart: Boolean = false
) {
    val allGranted: Boolean
        get() = hasRoot && isModuleActive && canListApps && hasUsageStats && hasOverlay
}
