package com.qimian233.ztool.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.R
import com.qimian233.ztool.ZToolApplication
import com.qimian233.ztool.data.home.HomeRepository
import com.qimian233.ztool.data.home.UpdateCheckResult
import com.qimian233.ztool.dexindex.base.DexIndexManager
import com.qimian233.ztool.dexindex.base.DexIndexProgress
import com.qimian233.ztool.dexindex.base.DexIndexRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class HomeViewModel(
    private val repository: HomeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val isCheckingEnvironment = AtomicBoolean(false)
    private val isUpdatingSystemInfo = AtomicBoolean(false)
    private val isCheckingAppUpdate = AtomicBoolean(false)
    private val isDexIndexTaskRunning = AtomicBoolean(false)
    private var started = false

    private val _dexIndexState = MutableStateFlow(DexIndexUiState())
    val dexIndexState: StateFlow<DexIndexUiState> = _dexIndexState.asStateFlow()

    init {
        // Hot update: observe module activation state changes and refresh the UI in real time
        viewModelScope.launch {
            ZToolApplication.isModuleActivatedFlow.collect { activated ->
                if (!started) return@collect  // start() not yet called; start() checks on its own
                val current = _uiState.value.isModuleActive
                if (activated != current) {
                    checkEnvironment()
                }
            }
        }

        // DexKit index progress hot-updates, shown in real time by the progress Dialog
        viewModelScope.launch {
            DexIndexManager.progress.collect { p ->
                _dexIndexState.value = _dexIndexState.value.copy(progress = p)
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        _uiState.value = _uiState.value.copy(
            isNonZuxOsWarningDismissed = repository.isNonZuxOsWarningDismissed()
        )
        checkEnvironment()
        if (repository.isAutoCheckUpdateEnabled()) {
            checkAppUpdate()
        }
    }

    /**
     * Decide whether the DexKit index needs to be generated/refreshed when entering the home page:
     * - Firstrun (no index files at all): full background indexing, Toast the result when done;
     * - Non-Firstrun but some scopes are stale/corrupted: foreground progress Dialog refresh, Toast the result when done.
     */
    fun checkDexIndexOnEntry(context: Context) {
        if (isDexIndexTaskRunning.get()) return

        val anyIndexed = DexIndexRegistry.indexers.any {
            DexIndexManager.lastIndexedAt(context, it.scopePackage) > 0L
        }
        val needRefresh = if (anyIndexed) {
            // Non-Firstrun: cache exists but fingerprint/schema is stale or the file is corrupted
            DexIndexRegistry.indexers.any { DexIndexManager.needsReindex(context, it.scopePackage) }
        } else {
            true // Firstrun: index files do not exist at all
        }
        if (needRefresh) {
            startDexIndexTask(context, foreground = anyIndexed)
        }
    }

    fun consumeDexIndexToast() {
        _dexIndexState.value = _dexIndexState.value.copy(toastMessage = null)
    }

    private fun startDexIndexTask(context: Context, foreground: Boolean) {
        if (!isDexIndexTaskRunning.compareAndSet(false, true)) return
        _dexIndexState.value = _dexIndexState.value.copy(refreshing = foreground, toastMessage = null)
        viewModelScope.launch(Dispatchers.Default) {
            val results = try {
                DexIndexManager.indexAll(context)
            } catch (t: Throwable) {
                Log.e(TAG, "dex index failed", t)
                emptyMap()
            }
            val toastRes = if (results.isNotEmpty() && results.values.any { it }) {
                R.string.common_dex_index_refreshed
            } else {
                R.string.common_dex_index_refresh_failed
            }
            _dexIndexState.value = _dexIndexState.value.copy(
                refreshing = false,
                toastMessage = toastRes
            )
            isDexIndexTaskRunning.set(false)
        }
    }

    fun refreshSystemInfoIfNeeded() {
        if (repository.shouldRefreshSystemInfo()) {
            updateSystemInfoAsync()
        }
    }

    fun checkEnvironment() {
        if (isCheckingEnvironment.getAndSet(true)) {
            Log.d(TAG, "Environment check already running, skipping")
            return
        }

        Thread {
            try {
                val status = repository.checkEnvironment()
                _uiState.value = _uiState.value.copy(
                    isCheckingEnvironment = false,
                    isModuleActive = status.moduleActive,
                    isRootAvailable = status.rootAvailable
                )

                if (status.moduleActive && status.rootAvailable) {
                    updateModuleStatusAsync()
                    updateSystemInfoAsync()
                    checkConfigUpgrade()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Environment check failed", e)
                _uiState.value = _uiState.value.copy(
                    isCheckingEnvironment = false,
                    isRootAvailable = false
                )
            } finally {
                isCheckingEnvironment.set(false)
            }
        }.start()
    }

    fun dismissConfigUpgradeDialog() {
        _uiState.value = _uiState.value.copy(configUpgradeDialogVisible = false)
    }

    fun toggleUpdateExpanded() {
        _uiState.value.updateInfo?.let {
            _uiState.value = _uiState.value.copy(updateInfo = it.copy(expanded = !it.expanded))
        }
    }

    fun ignoreUpdate(versionCode: Int) {
        repository.ignoreUpdate(versionCode)
        _uiState.value = _uiState.value.copy(updateInfo = null)
    }

    fun dismissNonZuxOsWarning() {
        repository.dismissNonZuxOsWarning()
        _uiState.value = _uiState.value.copy(isNonZuxOsWarningDismissed = true)
    }

    fun showRebootConfirmation(target: RebootTarget) {
        _uiState.value = _uiState.value.copy(rebootConfirmation = target)
    }

    fun dismissRebootConfirmation() {
        _uiState.value = _uiState.value.copy(rebootConfirmation = null)
    }

    fun executeReboot(target: RebootTarget, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.executeReboot(target.command)
            withContext(Dispatchers.Main) {
                onResult(result.success, result.error)
            }
        }
    }

    fun restartAfterConfigUpgrade() {
        Thread {
            repository.restartAfterConfigUpgrade()
        }.start()
    }

    fun clearShellCache() {
        repository.clearShellCache()
    }

    private fun updateModuleStatusAsync() {
        Thread {
            try {
                val status = repository.updateModuleStatus()
                _uiState.value = _uiState.value.copy(
                    moduleVersion = status.moduleVersion,
                    rootSource = status.rootSource,
                    frameworkVersion = status.frameworkVersion,
                    apiVersion = status.apiVersion
                )
            } catch (e: Exception) {
                Log.e(TAG, "Module status update failed", e)
            }
        }.start()
    }

    private fun updateSystemInfoAsync() {
        if (isUpdatingSystemInfo.getAndSet(true)) return

        Thread {
            try {
                val systemInfo = repository.updateSystemInfo()
                _uiState.value = _uiState.value.copy(
                    deviceModel = systemInfo.deviceModel,
                    androidVersion = systemInfo.androidVersion,
                    buildVersion = systemInfo.buildVersion,
                    kernelVersion = systemInfo.kernelVersion,
                    currentSlot = systemInfo.currentSlot,
                    romRegion = systemInfo.romRegion,
                    isZuxOsDevice = systemInfo.isZuxOsDevice,
                )
            } catch (e: Exception) {
                Log.e(TAG, "System info update failed", e)
            } finally {
                isUpdatingSystemInfo.set(false)
            }
        }.start()
    }

    private fun checkConfigUpgrade() {
        Thread {
            try {
                if (repository.checkConfigUpgrade()) {
                    _uiState.value = _uiState.value.copy(configUpgradeDialogVisible = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Config upgrade check failed", e)
            }
        }.start()
    }

    fun checkAppUpdate(force: Boolean = false) {
        if (isCheckingAppUpdate.getAndSet(true)) {
            Log.d(TAG, "App update check already running, skipping")
            return
        }

        // Reuse the in-session check result: do not re-request when an update was already detected; manual refresh can force a re-check
        if (!force && _uiState.value.updateInfo != null) {
            Log.d(TAG, "Update already detected in this session, skipping re-check")
            isCheckingAppUpdate.set(false)
            return
        }

        _uiState.value = _uiState.value.copy(isCheckingAppUpdate = true)
        Thread {
            try {
                val result = repository.checkAppUpdate()
                when (result) {
                    is UpdateCheckResult.Success -> _uiState.value = _uiState.value.copy(
                        isCheckingAppUpdate = false,
                        updateCheckCompleted = true,
                        updateCheckError = null,
                        updateInfo = result.updateInfo
                    )
                    is UpdateCheckResult.Failure -> _uiState.value = _uiState.value.copy(
                        isCheckingAppUpdate = false,
                        updateCheckCompleted = true,
                        updateCheckError = result.reason
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "App update check failed", e)
                _uiState.value = _uiState.value.copy(
                    isCheckingAppUpdate = false,
                    updateCheckCompleted = true,
                    updateCheckError = e.message ?: e.javaClass.simpleName
                )
            } finally {
                isCheckingAppUpdate.set(false)
            }
        }.start()
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}

data class HomeUiState(
    val isCheckingEnvironment: Boolean = true,    val isModuleActive: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isZuxOsDevice: Boolean = true,
    val isNonZuxOsWarningDismissed: Boolean = false,
    val moduleVersion: String = "",
    val rootSource: String = "",
    val frameworkVersion: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val buildVersion: String = "",
    val kernelVersion: String = "",
    val currentSlot: String = "",
    val romRegion: String = "",
    val apiVersion: Int = 0,
    val isCheckingAppUpdate: Boolean = false,
    val updateCheckCompleted: Boolean = false,
    val updateCheckError: String? = null,
    val updateInfo: UpdateInfo? = null,
    val configUpgradeDialogVisible: Boolean = false,
    val rebootConfirmation: RebootTarget? = null
) {
    val environmentReady: Boolean
        get() = isModuleActive && isRootAvailable
}

/** DexKit index progress and result (home page path). */
data class DexIndexUiState(
    val refreshing: Boolean = false,
    val progress: DexIndexProgress = DexIndexProgress(),
    val toastMessage: Int? = null,
)

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val downloadUrl: String,
    val expanded: Boolean = false
)

enum class RebootTarget(
    val command: String,
    val messageRes: Int,
    val displayNameRes: Int
) {
    Userspace("reboot userspace", R.string.page_home_soft_reboot_confirm_message, R.string.page_home_soft_reboot),
    System("reboot", R.string.page_home_reboot_confirm_message, R.string.page_home_reboot),
    Bootloader("reboot bootloader", R.string.page_home_bootloader_confirm_message, R.string.page_home_bootloader),
    Recovery("reboot recovery", R.string.page_home_recovery_confirm_message, R.string.page_home_recovery),
    Edl("reboot edl", R.string.page_home_edl_confirm_message, R.string.page_home_edl)
}
