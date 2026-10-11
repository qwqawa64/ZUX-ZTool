package com.qimian233.ztool.data.zuiui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.qimian233.ztool.XposedServiceBridge
import io.github.libxposed.service.XposedService

/** An installed app that bundles ZUI components but that the module scope does not cover. */
data class PendingScopeApp(val packageName: String, val label: String)

/** Result of a scope request. */
sealed interface ScopeRequestOutcome {
    data class Approved(val packages: List<String>) : ScopeRequestOutcome
    data class Failed(val reason: String) : ScopeRequestOutcome
    data object Unavailable : ScopeRequestOutcome
}

/** App-side access to the ZUI component scan: triggering it and acting on its results. */
class ZuiComponentScanRepository(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun startScan() = ZuiComponentScanManager.start(context)

    fun refreshStale() = ZuiComponentScanManager.refreshStale(context)

    fun cancelScan() = ZuiComponentScanManager.cancel()

    /** [zuiPackages] minus the current scope, labelled for display. */
    fun pendingApps(zuiPackages: List<String>): List<PendingScopeApp> {
        val scope = XposedServiceBridge.getScope().toSet()
        return zuiPackages.filter { it !in scope }
            .map { PendingScopeApp(it, labelOf(it)) }
    }

    /** Requests scope for [packages]; [onResult] is delivered on the main thread. */
    fun requestScope(packages: List<String>, onResult: (ScopeRequestOutcome) -> Unit) {
        if (XposedServiceBridge.getService() == null) {
            mainHandler.post { onResult(ScopeRequestOutcome.Unavailable) }
            return
        }
        XposedServiceBridge.requestScope(
            packages,
            object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(packages: List<String>) {
                    mainHandler.post { onResult(ScopeRequestOutcome.Approved(packages)) }
                }

                override fun onScopeRequestFailed(reason: String) {
                    mainHandler.post { onResult(ScopeRequestOutcome.Failed(reason)) }
                }
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun labelOf(packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
