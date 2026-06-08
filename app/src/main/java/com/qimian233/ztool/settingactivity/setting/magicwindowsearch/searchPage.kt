package com.qimian233.ztool.settingactivity.setting.magicwindowsearch

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.settings.MagicWindowSearchRepository
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.SearchPageUiState
import com.qimian233.ztool.viewmodel.SearchPageViewModel

@Composable
fun SearchPageRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("SearchPageRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SearchPageViewModelFactory(
                MagicWindowSearchRepository(context.applicationContext)
            )
        )[SearchPageViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadEmbeddingConfig()
    }

    val uiState by viewModel.uiState.collectAsState()

    SearchPageScreen(
        state = uiState,
        onKeywordChanged = viewModel::setKeyword,
        onSearch = {
            viewModel.search {
                Toast.makeText(
                    context,
                    R.string.unable_to_find_application,
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        onResultClick = viewModel::selectPackage,
        onNavigateBack = onBack
    )

    uiState.selectedPackage?.let { packageInfo ->
        PackageDetailsDialog(
            packageInfo = packageInfo,
            onDismiss = viewModel::dismissPackageDetails
        )
    }
}

private class SearchPageViewModelFactory(
    private val repository: MagicWindowSearchRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchPageViewModel::class.java)) {
            return SearchPageViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SearchPageScreen(
    state: SearchPageUiState,
    onKeywordChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (PackageInfo) -> Unit,
    onNavigateBack: () -> Unit
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.FindRules),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                SearchCard(
                    tipsText = state.tipsText,
                    keyword = state.keyword,
                    onKeywordChanged = onKeywordChanged,
                    onSearch = onSearch
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (state.searchResults.isNotEmpty()) {
                    ResultsCard(
                        results = state.searchResults,
                        onResultClick = onResultClick
                    )
                } else if (state.hasSearched) {
                    EmptyResultCard()
                }
            }
        }
    }
}

@Composable
private fun SearchCard(
    tipsText: String,
    keyword: String,
    onKeywordChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    ZToolCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.FindRules),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tipsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.FindRules_Hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onSearch) {
                    Text(stringResource(R.string.SearchRules))
                }
            }
        }
    }
}

@Composable
private fun ResultsCard(
    results: List<PackageInfo>,
    onResultClick: (PackageInfo) -> Unit
) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.RulesResult),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(results, key = { it.name }) { packageInfo ->
                    PackageResultRow(
                        packageInfo = packageInfo,
                        onClick = { onResultClick(packageInfo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageResultRow(
    packageInfo: PackageInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(
            text = packageInfo.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = packageInfo.mainPage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyResultCard() {
    ZToolCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.unable_to_find_application),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PackageDetailsDialog(
    packageInfo: PackageInfo,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parallel_window_details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = buildPackageDetails(packageInfo),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_button))
            }
        }
    )
}

@Composable
private fun buildPackageDetails(packageInfo: PackageInfo): String {
    return buildString {
        append(stringResource(R.string.package_info_header)).append("\n\n")
        append(stringResource(R.string.app_package_name)).append(packageInfo.name).append("\n\n")
        append(stringResource(R.string.main_activity_info)).append(packageInfo.mainPage).append("\n\n")

        if (packageInfo.activityPairs.isNotEmpty()) {
            append(stringResource(R.string.activity_pairs_info)).append("\n")
            packageInfo.activityPairs.forEach { pair ->
                append(stringResource(R.string.activity_pair_format, pair.from, pair.to)).append("\n")
            }
            append("\n")
        }

        AppendStringList(stringResource(R.string.force_fullscreen_pages), packageInfo.forceFullscreenPages)
        AppendStringList(stringResource(R.string.transparent_activities), packageInfo.transActivities)
        AppendStringList(stringResource(R.string.left_transparent_activities), packageInfo.leftTransActivities)

        append(stringResource(R.string.split_screen_config_header)).append("\n\n")
        append(stringResource(R.string.adjust_window_ratio)).append(packageInfo.showEmbeddingDivider).append("\n")
        append(stringResource(R.string.skip_multi_window_mode)).append(packageInfo.skipMultiWindowMode).append("\n")
        append(stringResource(R.string.skip_letterbox_display)).append(packageInfo.skipLetterboxDisplayInfo).append("\n")
        append(stringResource(R.string.show_surface_view_bg)).append(packageInfo.showSurfaceViewBackground).append("\n")
        append(stringResource(R.string.pause_primary_activity)).append(packageInfo.shouldPausePrimaryActivity).append("\n")
    }
}

@Composable
private fun StringBuilder.AppendStringList(
    title: String,
    values: List<String>
) {
    if (values.isEmpty()) return
    append(title).append("\n")
    values.forEach { value ->
        append(stringResource(R.string.list_item_format, value)).append("\n")
    }
    append("\n")
}
