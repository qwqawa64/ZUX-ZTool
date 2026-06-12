package com.qimian233.ztool.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.audit.LogParser.LogEntry
import com.qimian233.ztool.data.audit.AuditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuditViewModel(
    private val repository: AuditRepository
) : ViewModel() {
    private val allLogEntries = mutableListOf<LogEntry>()
    private val _uiState = MutableStateFlow(repository.initialState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        loadAllLogFiles()
    }

    fun loadAllLogFiles() {
        setLoading(true)
        Thread {
            try {
                val result = repository.loadLogs()
                allLogEntries.clear()
                allLogEntries.addAll(result.entries)
                if (result.entries.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        filteredLogEntries = emptyList(),
                        emptyMessage = result.emptyMessage,
                        statsText = repository.statsText(allLogEntries, emptyList()),
                        isLoading = false
                    )
                } else {
                    applyFilters(isLoading = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load log files", e)
                _uiState.value = _uiState.value.copy(
                    filteredLogEntries = emptyList(),
                    emptyMessage = repository.loadLogsFailedMessage(e.message),
                    isLoading = false
                )
            }
        }.start()
    }

    fun selectCategory(category: String) {
        val moduleOptions = repository.moduleOptionsFor(category)
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            moduleOptions = moduleOptions,
            selectedModuleKey = null,
            selectedModuleLabel = moduleOptions.firstOrNull()?.label.orEmpty()
        )
        applyFilters()
    }

    fun selectModule(moduleOption: ModuleOption) {
        _uiState.value = _uiState.value.copy(
            selectedModuleKey = moduleOption.key,
            selectedModuleLabel = moduleOption.label
        )
        applyFilters()
    }

    fun selectLevel(level: String) {
        _uiState.value = _uiState.value.copy(selectedLevel = level)
        applyFilters()
    }

    fun setSearchText(searchText: String) {
        _uiState.value = _uiState.value.copy(searchText = searchText)
        applyFilters()
    }

    fun setShowErrorsOnly(showErrorsOnly: Boolean) {
        _uiState.value = _uiState.value.copy(showErrorsOnly = showErrorsOnly)
        applyFilters()
    }

    fun selectLogEntry(entry: LogEntry) {
        _uiState.value = _uiState.value.copy(selectedLogEntry = entry)
    }

    fun dismissLogEntry() {
        _uiState.value = _uiState.value.copy(selectedLogEntry = null)
    }

    fun showClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = true)
    }

    fun dismissClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
    }

    fun clearAllLogs(onResult: (Boolean, String?) -> Unit) {
        dismissClearDialog()
        setLoading(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.clearLogs()
                allLogEntries.clear()
                _uiState.value = _uiState.value.copy(
                    filteredLogEntries = emptyList(),
                    emptyMessage = repository.logsClearedMessage(),
                    statsText = repository.statsText(emptyList(), emptyList()),
                    isLoading = false
                )
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logs", e)
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    onResult(false, e.message)
                }
            }
        }
    }

    fun showStatistics() {
        _uiState.value = _uiState.value.copy(statisticsMessage = repository.statisticsMessage(allLogEntries))
    }

    fun dismissStatistics() {
        _uiState.value = _uiState.value.copy(statisticsMessage = null)
    }

    fun exportFileName(): String = repository.exportFileName()

    fun exportLogsToUri(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        setLoading(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.exportLogsToUri(uri)
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    onResult(result, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs", e)
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    onResult(false, e.message)
                }
            }
        }
    }

    fun buildLogDetails(entry: LogEntry): String = repository.buildLogDetails(entry)

    private fun applyFilters(isLoading: Boolean = _uiState.value.isLoading) {
        val filtered = repository.filterLogs(allLogEntries, _uiState.value)
        _uiState.value = _uiState.value.copy(
            filteredLogEntries = filtered,
            emptyMessage = when {
                allLogEntries.isEmpty() -> _uiState.value.emptyMessage
                filtered.isEmpty() -> repository.noMatchingLogsMessage()
                else -> ""
            },
            statsText = repository.statsText(allLogEntries, filtered),
            isLoading = isLoading
        )
    }

    private fun setLoading(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLoading = show,
            emptyMessage = if (show) "" else _uiState.value.emptyMessage
        )
    }

    companion object {
        private const val TAG = "AuditViewModel"
    }
}

data class ModuleOption(
    val key: String?,
    val label: String
) {
    override fun toString(): String = label
}

data class AuditUiState(
    val categoryOptions: List<String> = emptyList(),
    val levelOptions: List<String> = emptyList(),
    val moduleOptions: List<ModuleOption> = emptyList(),
    val selectedCategory: String = "",
    val selectedModuleKey: String? = null,
    val selectedModuleLabel: String = "",
    val selectedLevel: String = "",
    val searchText: String = "",
    val showErrorsOnly: Boolean = false,
    val isLoading: Boolean = false,
    val emptyMessage: String = "",
    val statsText: String = "",
    val filteredLogEntries: List<LogEntry> = emptyList(),
    val selectedLogEntry: LogEntry? = null,
    val showClearDialog: Boolean = false,
    val statisticsMessage: String? = null
)
