package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.launcher.BatchUninstallRepository
import com.qimian233.ztool.data.launcher.UninstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 批量卸载页面的阶段。 */
enum class BatchUninstallStage {
    /** 待确认。 */
    Confirm,

    /** Root 执行中。 */
    Running,

    /** 全部执行完毕。 */
    Finished,

    /** Root 不可用。 */
    RootUnavailable,

    /** 未收到有效包名列表。 */
    Empty
}

data class BatchUninstallUiState(
    val packages: List<String> = emptyList(),
    val stage: BatchUninstallStage = BatchUninstallStage.Confirm,
    val results: List<UninstallResult> = emptyList()
) {
    val successCount: Int get() = results.count { it.success }
    val failureCount: Int get() = results.count { !it.success }
}

class BatchUninstallViewModel(
    private val repository: BatchUninstallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchUninstallUiState())
    val uiState = _uiState.asStateFlow()

    /** 由 Activity 在首次创建时传入外部 Intent 携带的包名列表。 */
    fun start(packages: List<String>) {
        val sanitized = packages
            .map { it.trim() }
            .filter { PACKAGE_NAME_REGEX.matches(it) }
            .distinct()
        _uiState.value = if (sanitized.isEmpty()) {
            BatchUninstallUiState(stage = BatchUninstallStage.Empty)
        } else {
            BatchUninstallUiState(packages = sanitized)
        }
    }

    fun confirmAndRun() {
        val state = _uiState.value
        if (state.stage != BatchUninstallStage.Confirm || state.packages.isEmpty()) return
        _uiState.value = state.copy(stage = BatchUninstallStage.Running)
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.checkRootAvailable()) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(stage = BatchUninstallStage.RootUnavailable) }
                }
                return@launch
            }
            val results = mutableListOf<UninstallResult>()
            for (packageName in state.packages) {
                val result = repository.uninstallPackage(packageName)
                results.add(result)
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        current.copy(results = results.toList())
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(stage = BatchUninstallStage.Finished) }
            }
        }
    }

    companion object {
        private val PACKAGE_NAME_REGEX = Regex("[A-Za-z0-9_.]+")
    }
}
