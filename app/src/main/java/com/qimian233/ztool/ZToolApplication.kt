package com.qimian233.ztool

import android.app.Application
import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Custom Application class.
 * <p>
 * Follows the HyperCeiler pattern: the Application itself implements
 * [XposedServiceHelper.OnServiceListener] and registers the listener in
 * [attachBaseContext] (earlier than onCreate), minimizing the window between
 * binder arrival and listener registration.
 * </p>
 * <p>
 * Also exposes [isModuleActivatedFlow] (a [StateFlow]) so the UI layer can
 * <b>hot-update</b> the module activation state without polling.
 * </p>
 */
class ZToolApplication : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        private const val TAG = "ZToolApplication"

        private val _isModuleActivated = MutableStateFlow(false)

        /** Hot-update flow of the module activation state; the UI layer can collect it to react to changes in real time */
        val isModuleActivatedFlow: StateFlow<Boolean> = _isModuleActivated.asStateFlow()

        /** Whether the module is activated, maintained by onServiceBind/onServiceDied (instant query) */
        @Volatile
        var isModuleActivated: Boolean = false
            private set
    }

    // Offline DexKit indexing triggers have been migrated to the home page entry check
    // (HomeViewModel.checkDexIndexOnEntry):
    // - Firstrun (no index file): full background indexing, with a Toast of the result after entering home;
    // - Non-Firstrun but stale/corrupted cache: foreground progress Dialog refresh.
    // Therefore the Application startup phase no longer scans automatically.

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Register the listener as early as possible, earlier than onCreate(),
        // to reduce the race where linkToDeath fails when a cached binder is drained later
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        isModuleActivated = true
        _isModuleActivated.value = true
        XposedServiceBridge.currentService = service
    }

    override fun onServiceDied(service: XposedService) {
        isModuleActivated = false
        _isModuleActivated.value = false
        XposedServiceBridge.currentService = null
    }
}
