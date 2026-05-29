package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.R
import com.qimian233.ztool.data.home.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class HomeViewModel(
    private val repository: HomeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val isCheckingEnvironment = AtomicBoolean(false)
    private val isUpdatingSystemInfo = AtomicBoolean(false)
    private var started = false

    fun start() {
        if (started) return
        started = true
        checkEnvironment()
        checkAppUpdate()
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
                    isRootAvailable = status.rootAvailable,
                    hintText = status.hintText
                )

                if (status.moduleActive && status.rootAvailable) {
                    updateModuleStatusAsync()
                    updateSystemInfoAsync()
                    updateHomepageHint()
                    checkConfigUpgrade()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Environment check failed", e)
                _uiState.value = _uiState.value.copy(
                    isCheckingEnvironment = false,
                    isRootAvailable = false,
                    hintText = repository.missingRootEnvironmentHint()
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

    fun showRebootConfirmation(target: RebootTarget) {
        _uiState.value = _uiState.value.copy(rebootConfirmation = target)
    }

    fun dismissRebootConfirmation() {
        _uiState.value = _uiState.value.copy(rebootConfirmation = null)
    }

    fun executeReboot(target: RebootTarget, onResult: (Boolean, String) -> Unit) {
        Thread {
            val result = repository.executeReboot(target.command)
            onResult(result.success, result.error)
        }.start()
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
                    frameworkVersion = status.frameworkVersion
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
                    romRegion = systemInfo.romRegion
                )
            } catch (e: Exception) {
                Log.e(TAG, "System info update failed", e)
            } finally {
                isUpdatingSystemInfo.set(false)
            }
        }.start()
    }

    private fun updateHomepageHint() {
        _uiState.value = _uiState.value.copy(hintText = repository.environmentReadyHint())
        Thread {
            try {
                val hint = repository.loadHomepageHint()
                if (hint != null && _uiState.value.environmentReady) {
                    _uiState.value = _uiState.value.copy(hintText = hint)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Homepage hint fetch failed: ${e.message}")
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

    private fun checkAppUpdate() {
        Thread {
            try {
                val updateInfo = repository.checkAppUpdate()
                if (updateInfo != null) {
                    _uiState.value = _uiState.value.copy(updateInfo = updateInfo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "App update check failed", e)
            }
        }.start()
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}

data class HomeUiState(
    val isCheckingEnvironment: Boolean = true,
    val isModuleActive: Boolean = false,
    val isRootAvailable: Boolean = false,
    val hintText: String = "",
    val moduleVersion: String = "",
    val rootSource: String = "",
    val frameworkVersion: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val buildVersion: String = "",
    val kernelVersion: String = "",
    val currentSlot: String = "",
    val romRegion: String = "",
    val updateInfo: UpdateInfo? = null,
    val configUpgradeDialogVisible: Boolean = false,
    val rebootConfirmation: RebootTarget? = null
) {
    val environmentReady: Boolean
        get() = isModuleActive && isRootAvailable
}

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val downloadUrl: String,
    val expanded: Boolean = false
)

enum class RebootTarget(
    val command: String,
    val messageRes: Int
) {
    Userspace("reboot userspace", R.string.soft_reboot_confirm_message),
    System("reboot", R.string.reboot_confirm_message),
    Bootloader("reboot bootloader", R.string.bootloader_confirm_message),
    Recovery("reboot recovery", R.string.recovery_confirm_message),
    Edl("reboot edl", R.string.edl_confirm_message)
}
