package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.settings.MagicWindowConfigLoadResult
import com.qimian233.ztool.data.settings.MagicWindowSearchRepository
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.PackageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException

class SearchPageViewModel(
    private val repository: MagicWindowSearchRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchPageUiState())
    val uiState: StateFlow<SearchPageUiState> = _uiState.asStateFlow()

    fun loadEmbeddingConfig() {
        Thread {
            when (val result = repository.loadEmbeddingConfig()) {
                is MagicWindowConfigLoadResult.Loaded -> {
                    _uiState.value = _uiState.value.copy(tipsText = result.tipsText)
                }
                is MagicWindowConfigLoadResult.Missing -> {
                    _uiState.value = _uiState.value.copy(tipsText = result.tipsText)
                }
            }
        }.start()
    }

    fun setKeyword(value: String) {
        _uiState.value = _uiState.value.copy(keyword = value)
    }

    fun selectPackage(packageInfo: PackageInfo) {
        _uiState.value = _uiState.value.copy(selectedPackage = packageInfo)
    }

    fun dismissPackageDetails() {
        _uiState.value = _uiState.value.copy(selectedPackage = null)
    }

    fun search(onEmptyResult: () -> Unit) {
        val query = _uiState.value.keyword.trim()
        if (query.isEmpty()) return

        try {
            val results = repository.search(query)
            _uiState.value = _uiState.value.copy(
                searchResults = results,
                hasSearched = true
            )
            if (results.isEmpty()) {
                onEmptyResult()
            }
        } catch (e: JSONException) {
            Log.e(TAG, "搜索策略失败", e)
        }
    }

    companion object {
        private const val TAG = "searchPage"
    }
}

data class SearchPageUiState(
    val tipsText: String = "",
    val keyword: String = "",
    val searchResults: List<PackageInfo> = emptyList(),
    val selectedPackage: PackageInfo? = null,
    val hasSearched: Boolean = false
)
