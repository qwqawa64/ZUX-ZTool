package com.qimian233.ztool.settingactivity.setting.magicwindowsearch

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.theme.ZToolTheme
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

@Suppress("ClassName")
class searchPage : ComponentActivity() {

    private var embeddingConfig: JSONObject? = null
    private var tipsText by mutableStateOf("")
    private var keyword by mutableStateOf("")
    private var searchResults by mutableStateOf<List<PackageInfo>>(emptyList())
    private var selectedPackage by mutableStateOf<PackageInfo?>(null)
    private var hasSearched by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        loadEmbeddingConfig()

        setContent {
            ZToolTheme {
                SearchPageScreen(
                    tipsText = tipsText,
                    keyword = keyword,
                    results = searchResults,
                    hasSearched = hasSearched,
                    onKeywordChanged = { keyword = it },
                    onSearch = ::performSearch,
                    onResultClick = { selectedPackage = it },
                    onNavigateBack = ::finish
                )

                selectedPackage?.let { packageInfo ->
                    PackageDetailsDialog(
                        packageInfo = packageInfo,
                        onDismiss = { selectedPackage = null }
                    )
                }
            }
        }
    }

    private fun loadEmbeddingConfig() {
        try {
            embeddingConfig = JSONObject(requireNotNull(readFile(MODULE_CONFIG_PATH)))
            val count = embeddingConfig?.getJSONArray("packages")?.length() ?: 0
            tipsText = getString(R.string.module_config_tips, count)
        } catch (_: Exception) {
            try {
                embeddingConfig = JSONObject(requireNotNull(loadJsonFromAsset("embedding/embedding_config.json")))
                tipsText = getString(R.string.official_config_tips)
            } catch (_: Exception) {
                embeddingConfig = null
                tipsText = getString(R.string.config_not_exists_tips)
            }
        }
    }

    private fun performSearch() {
        val query = keyword.trim()
        if (query.isEmpty()) return

        val config = embeddingConfig ?: return
        val results = mutableListOf<PackageInfo>()

        try {
            val packages = config.getJSONArray("packages")
            val normalizedQuery = query.lowercase(Locale.getDefault())
            for (index in 0 until packages.length()) {
                val packageObject = packages.getJSONObject(index)
                val name = packageObject.optString("name", "")
                if (name.lowercase(Locale.getDefault()).contains(normalizedQuery)) {
                    results.add(PackageInfo(packageObject))
                }
            }
            searchResults = results
            hasSearched = true

            if (results.isEmpty()) {
                Toast.makeText(this, R.string.unable_to_find_application, Toast.LENGTH_LONG).show()
            }
        } catch (e: JSONException) {
            Log.e(TAG, "搜索策略失败", e)
        }
    }

    private fun loadJsonFromAsset(fileName: String): String? {
        return try {
            assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading asset file: $fileName", e)
            null
        }
    }

    companion object {
        private const val TAG = "searchPage"
        private const val MODULE_CONFIG_PATH = "/data/system/zui/embedding/embedding_config.json"

        @JvmStatic
        fun readFile(filePath: String): String? {
            var process: Process? = null
            return try {
                process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)
                val inputStream = DataInputStream(process.inputStream)

                outputStream.writeBytes("cat $filePath\n")
                outputStream.writeBytes("exit\n")
                outputStream.flush()

                val result = StringBuilder()
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line = reader.readLine()
                while (line != null) {
                    result.append(line).append("\n")
                    line = reader.readLine()
                }

                process.waitFor()
                result.toString()
            } catch (e: Exception) {
                Log.e(TAG, "读取模块配置失败", e)
                null
            } finally {
                process?.destroy()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPageScreen(
    tipsText: String,
    keyword: String,
    results: List<PackageInfo>,
    hasSearched: Boolean,
    onKeywordChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (PackageInfo) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.FindRules),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
                    tipsText = tipsText,
                    keyword = keyword,
                    onKeywordChanged = onKeywordChanged,
                    onSearch = onSearch
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (results.isNotEmpty()) {
                    ResultsCard(
                        results = results,
                        onResultClick = onResultClick
                    )
                } else if (hasSearched) {
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
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
    AlertDialog(
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

        appendStringList(stringResource(R.string.force_fullscreen_pages), packageInfo.forceFullscreenPages)
        appendStringList(stringResource(R.string.transparent_activities), packageInfo.transActivities)
        appendStringList(stringResource(R.string.left_transparent_activities), packageInfo.leftTransActivities)

        append(stringResource(R.string.split_screen_config_header)).append("\n\n")
        append(stringResource(R.string.adjust_window_ratio)).append(packageInfo.showEmbeddingDivider).append("\n")
        append(stringResource(R.string.skip_multi_window_mode)).append(packageInfo.skipMultiWindowMode).append("\n")
        append(stringResource(R.string.skip_letterbox_display)).append(packageInfo.skipLetterboxDisplayInfo).append("\n")
        append(stringResource(R.string.show_surface_view_bg)).append(packageInfo.showSurfaceViewBackground).append("\n")
        append(stringResource(R.string.pause_primary_activity)).append(packageInfo.shouldPausePrimaryActivity).append("\n")
    }
}

@Composable
private fun StringBuilder.appendStringList(
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
