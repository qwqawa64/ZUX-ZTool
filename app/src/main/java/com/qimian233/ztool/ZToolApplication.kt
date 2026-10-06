package com.qimian233.ztool

import android.app.Application
import android.content.Context
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

        /**
         * Process-lifetime scope for startup work that must outlive any Activity.
         * Never cancelled: the process going away is the only teardown.
         *
         * The Activity must not own this work — it can be recreated for reasons the
         * manifest cannot suppress (locale change, "don't keep activities"), and
         * cancelling a startup task mid-flight while a caller has already claimed a
         * "did this run" flag would drop it entirely.
         */
        val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    // Offline DexKit indexing triggers have been migrated to the home page entry check
    // (HomeViewModel.checkDexIndexOnEntry):
    // - Firstrun (no index file): full background indexing, with a Toast of the result after entering home;
    // - Non-Firstrun but stale/corrupted cache: foreground progress Dialog refresh.
    // Therefore the Application startup phase no longer scans automatically.

    /**
     * Startup warm-up, all of it off the main thread and ahead of the Activity:
     *
     * 1. The root probe. `environmentReady` is `isModuleActive && isRootAvailable` and
     *    the root half needs a `su` round trip, but the probe used to start only once
     *    HomeRoute composed — leaving the shell thread idle for the whole Activity
     *    startup. `checkRootAccess()` caches its outcome for 30s, so the home path then
     *    reads it instead of paying for the round trip itself. The serial shell
     *    dispatcher is deliberate: a parallel probe would race the other startup root
     *    commands, and EnhancedShellExecutor fails such a command outright instead of
     *    queueing it.
     * 2. The theme preferences. Their first `getSharedPreferences` call parses the
     *    backing XML, which [MainActivity] does on the main thread because it needs the
     *    theme before `setContent` to avoid a first-frame flash — so it cannot await
     *    this. Parsing the file here instead makes that main-thread read a memory hit.
     */
    override fun onCreate() {
        super.onCreate()

        startupScope.launch(EnhancedShellExecutor.shellWorkDispatcher) {
            EnhancedShellExecutor.getInstance().checkRootAccess()
        }

        startupScope.launch(Dispatchers.IO) {
            ThemePreferencesRepository(this@ZToolApplication).loadSettings()
        }
    }

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
