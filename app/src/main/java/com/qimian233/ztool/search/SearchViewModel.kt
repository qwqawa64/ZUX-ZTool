package com.qimian233.ztool.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Entries grouped by their target screen; [SearchGroup.key] is (groupTitleRes, groupSuffixRes). */
data class SearchGroup(
    val key: Pair<Int, Int?>,
    val entries: List<SearchEntry>
)

data class SearchUiState(
    val query: String = "",
    val groups: List<SearchGroup> = emptyList(),
    /** True once a non-blank query has been submitted — switches hint vs no-results. */
    val searched: Boolean = false
)

class SearchViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var queryJob: Job? = null

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        queryJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(groups = emptyList(), searched = false) }
            return
        }
        queryJob = viewModelScope.launch(Dispatchers.Default) {
            delay(QUERY_DEBOUNCE_MILLIS)
            val locale: Locale = appContext.resources.configuration.locales[0]
            val visible = SearchIndexBuilder.visibleEntries(appContext)
            val scored = visible.mapNotNull { entry ->
                SearchMatcher.score(query, entry, appContext, locale)?.let { entry to it }
            }
            val groups = groupAndSort(scored)
            _uiState.update { it.copy(groups = groups, searched = true) }
        }
    }

    /**
     * Groups results by target screen, ranks screens by their best entry score, and
     * ranks entries within a screen by score. Feature cards share one group key so
     * they naturally form the "Feature cards" group.
     */
    private fun groupAndSort(scored: List<Pair<SearchEntry, Int>>): List<SearchGroup> {
        return scored
            .groupBy { it.first.groupTitleRes to it.first.groupSuffixRes }
            .map { (key, members) ->
                SearchGroup(
                    key = key,
                    entries = members.sortedByDescending { it.second }.map { it.first }
                )
            }
            .sortedByDescending { group ->
                group.entries.maxOf { entry -> scored.first { it.first === entry }.second }
            }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MILLIS = 150L
    }
}
