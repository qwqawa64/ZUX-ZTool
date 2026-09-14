package com.qimian233.ztool.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.qimian233.ztool.ui.components.ZToolOutlinedTextField
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme

@Composable
fun SearchMainRoute(
    onBack: () -> Unit,
    onOpenEntry: (SearchEntry) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("SearchMainRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SearchViewModelFactory(context.applicationContext)
        )[SearchViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    SearchScreen(
        state = uiState,
        onBack = onBack,
        onQueryChanged = viewModel::setQuery,
        onOpenEntry = { entry ->
            focusManager.clearFocus()
            onOpenEntry(entry)
        }
    )
}

private class SearchViewModelFactory(
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onOpenEntry: (SearchEntry) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.search_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ZToolOutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                label = stringResource(R.string.search_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { })
            )
            when {
                state.groups.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        state.groups.forEach { group ->
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
                state.searched -> {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.search_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                    )
                }
            }
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
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
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
