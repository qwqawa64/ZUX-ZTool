package com.qimian233.ztool.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.search.SearchEntry
import com.qimian233.ztool.search.SearchParentMode
import com.qimian233.ztool.search.SearchUiState
import com.qimian233.ztool.search.SearchViewModel
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec

/**
 * Docked, in-place feature search shared by the Features and Settings tabs. Both
 * frontend styles render their own search bar (material3 DockedSearchBar vs Miuix
 * SearchBar) above the same grouped result list, backed by one [SearchViewModel].
 */
@Composable
fun FeatureSearchBar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenEntry: (SearchEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("FeatureSearchBar requires a ViewModelStoreOwner")
    val viewModel: SearchViewModel = remember(owner) {
        ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                        return SearchViewModel(context.applicationContext) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        )[SearchViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()

    when (LocalZToolThemeSpec.current.style) {
        FrontendStyle.Miuix -> MiuixFeatureSearchBar(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            uiState = uiState,
            onQueryChanged = viewModel::setQuery,
            onOpenEntry = onOpenEntry,
            modifier = modifier
        )
        FrontendStyle.Material3Expressive -> Material3FeatureSearchBar(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            uiState = uiState,
            onQueryChanged = viewModel::setQuery,
            onOpenEntry = onOpenEntry,
            modifier = modifier
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Material3FeatureSearchBar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onOpenEntry: (SearchEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.DockedSearchBar(
        query = uiState.query,
        onQueryChange = onQueryChanged,
        onSearch = { onExpandedChange(false) },
        active = expanded,
        onActiveChange = onExpandedChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null
            )
        },
        content = { resultsContent(uiState, onOpenEntry) }
    )
}

@Composable
private fun MiuixFeatureSearchBar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onOpenEntry: (SearchEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    top.yukonga.miuix.kmp.basic.SearchBar(
        inputField = {
            top.yukonga.miuix.kmp.basic.InputField(
                query = uiState.query,
                onQueryChange = onQueryChanged,
                onSearch = { onExpandedChange(false) },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                label = stringResource(R.string.search_hint)
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        content = { resultsContent(uiState, onOpenEntry) }
    )
}

/** Result area shared by both styles: hint / no-results / grouped results. */
@Composable
private fun ColumnScope.resultsContent(
    uiState: SearchUiState,
    onOpenEntry: (SearchEntry) -> Unit
) {
    when {
        uiState.groups.isNotEmpty() -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                uiState.groups.forEach { group ->
                    item(key = "header_${group.key.first}_${group.key.second}") {
                        SearchResultGroupHeader(groupTitleRes = group.key.first, groupSuffixRes = group.key.second)
                    }
                    items(items = group.entries, key = { it.id }) { entry ->
                        SearchResultRow(
                            entry = entry,
                            onClick = { onOpenEntry(entry) }
                        )
                    }
                }
            }
        }
        uiState.searched -> {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
        else -> {
            Text(
                text = stringResource(R.string.search_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
    }
}

@Composable
private fun SearchResultGroupHeader(
    groupTitleRes: Int,
    groupSuffixRes: Int?
) {
    Text(
        text = stringResource(groupTitleRes) + (groupSuffixRes?.let { stringResource(it) } ?: ""),
        style = MaterialTheme.typography.titleSmall,
        color = LocalZToolColorScheme.current.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchResultRow(
    entry: SearchEntry,
    onClick: () -> Unit
) {
    val annotation = entry.parentTitleRes?.let { parentTitleRes ->
        val parentTitle = stringResource(parentTitleRes)
        if (entry.parentMode == SearchParentMode.REQUIRE_OFF) {
            stringResource(R.string.search_requires_parent_switch_off, parentTitle)
        } else {
            stringResource(R.string.search_requires_parent_switch, parentTitle)
        }
    } ?: entry.conditionNoteRes?.let { stringResource(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = LocalZToolColorScheme.current.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            entry.summaryRes?.let { summaryRes ->
                Text(
                    text = stringResource(summaryRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZToolColorScheme.current.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            annotation?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZToolColorScheme.current.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = LocalZToolColorScheme.current.onSurfaceVariant
        )
    }
}
