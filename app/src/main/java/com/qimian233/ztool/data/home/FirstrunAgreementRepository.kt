package com.qimian233.ztool.data.home

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
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

    fun refreshState(): FirstrunCheckState = loadInitialState()

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
}

data class FirstrunCheckState(
    val hasRoot: Boolean = false,
    val isModuleActive: Boolean = false,
    val canListApps: Boolean = false,
    val hasUsageStats: Boolean = false,
    val hasOverlay: Boolean = false
) {
    val allGranted: Boolean
        get() = hasRoot && isModuleActive && canListApps && hasUsageStats && hasOverlay
}
