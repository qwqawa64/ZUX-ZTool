package com.qimian233.ztool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.qimian233.ztool.audit.LogParser
import com.qimian233.ztool.audit.LogParser.LogEntry
import com.qimian233.ztool.audit.LogParser.LogLevel
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.FileManager
import com.qimian233.ztool.utils.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class AuditFragment : Fragment() {

    private val allLogEntries = mutableStateListOf<LogEntry>()
    private val filteredLogEntries = mutableStateListOf<LogEntry>()

    private var logDir: File? = null
    private var modulesByCategory: Map<String, List<String>> = emptyMap()
    private var categoryOptions by mutableStateOf(emptyList<String>())
    private var levelOptions by mutableStateOf(emptyList<String>())
    private var moduleOptions by mutableStateOf(emptyList<ModuleOption>())

    private var selectedCategory by mutableStateOf("")
    private var selectedModuleKey by mutableStateOf<String?>(null)
    private var selectedModuleLabel by mutableStateOf("")
    private var selectedLevel by mutableStateOf("")
    private var searchText by mutableStateOf("")
    private var showErrorsOnly by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private var emptyMessage by mutableStateOf("")
    private var statsText by mutableStateOf("")
    private var selectedLogEntry by mutableStateOf<LogEntry?>(null)
    private var showClearDialog by mutableStateOf(false)
    private var statisticsMessage by mutableStateOf<String?>(null)

    private lateinit var exportLogLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportLogLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null) {
                exportLogsToUri(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        initializeFilters()
        loadAllLogFiles()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ZToolTheme {
                    AuditScreen(
                        statsText = statsText,
                        categoryOptions = categoryOptions,
                        selectedCategory = selectedCategory,
                        moduleOptions = moduleOptions,
                        selectedModuleLabel = selectedModuleLabel,
                        levelOptions = levelOptions,
                        selectedLevel = selectedLevel,
                        searchText = searchText,
                        showErrorsOnly = showErrorsOnly,
                        isLoading = isLoading,
                        emptyMessage = emptyMessage,
                        logEntries = filteredLogEntries,
                        onCategorySelected = {
                            selectedCategory = it
                            updateModuleDropdown()
                            applyFilters()
                        },
                        onModuleSelected = {
                            selectedModuleKey = it.key
                            selectedModuleLabel = it.label
                            applyFilters()
                        },
                        onLevelSelected = {
                            selectedLevel = it
                            applyFilters()
                        },
                        onSearchTextChanged = {
                            searchText = it
                            applyFilters()
                        },
                        onShowErrorsOnlyChanged = {
                            showErrorsOnly = it
                            applyFilters()
                        },
                        onRefresh = ::loadAllLogFiles,
                        onClear = { showClearDialog = true },
                        onShowStatistics = ::showStatistics,
                        onSave = ::saveAllLogs,
                        onLogSelected = { selectedLogEntry = it }
                    )

                    selectedLogEntry?.let { entry ->
                        LogDetailDialog(
                            entry = entry,
                            onCopy = {
                                copyToClipboard(buildLogDetails(entry))
                                selectedLogEntry = null
                            },
                            onDismiss = { selectedLogEntry = null }
                        )
                    }

                    if (showClearDialog) {
                        ClearLogsDialog(
                            onConfirm = {
                                showClearDialog = false
                                clearAllLogs()
                            },
                            onDismiss = { showClearDialog = false }
                        )
                    }

                    statisticsMessage?.let { message ->
                        StatisticsDialog(
                            message = message,
                            onDismiss = { statisticsMessage = null }
                        )
                    }
                }
            }
        }
    }

    private fun initializeFilters() {
        modulesByCategory = LogParser.getModulesByCategory()

        val allCategories = getString(R.string.all_categories)
        categoryOptions = listOf(allCategories) + modulesByCategory.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
        selectedCategory = allCategories

        levelOptions = listOf(getString(R.string.all_levels), "DEBUG", "INFO", "WARN", "ERROR")
        selectedLevel = levelOptions.first()

        updateModuleDropdown()
    }

    private fun updateModuleDropdown() {
        val moduleKeys = if (
            selectedCategory.isNotEmpty() &&
            selectedCategory != getString(R.string.all_categories) &&
            modulesByCategory.containsKey(selectedCategory)
        ) {
            modulesByCategory[selectedCategory].orEmpty()
        } else {
            LogParser.getAvailableModules()
        }

        moduleOptions = listOf(ModuleOption(null, getString(R.string.all_modules))) +
            moduleKeys.map { key ->
                ModuleOption(key, LogParser.getModuleDisplayName(key).takeIf { it.isNotBlank() } ?: key)
            }
        selectedModuleKey = null
        selectedModuleLabel = moduleOptions.firstOrNull()?.label.orEmpty()
    }

    private fun loadAllLogFiles() {
        showLoading(true)

        Thread {
            try {
                val context = requireContext()
                val dir = File(context.filesDir, "Log")
                logDir = dir

                if (!dir.exists() || !dir.isDirectory) {
                    requireActivity().runOnUiThread {
                        showEmptyState(getString(R.string.log_directory_not_exists))
                        showLoading(false)
                    }
                    return@Thread
                }

                val parsedEntries = LogParser.parseAllLogFiles(dir)
                if (parsedEntries.isEmpty()) {
                    requireActivity().runOnUiThread {
                        allLogEntries.clear()
                        filteredLogEntries.clear()
                        updateStats()
                        showEmptyState(getString(R.string.no_log_records_found))
                        showLoading(false)
                    }
                    return@Thread
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                parsedEntries.sortWith { first, second ->
                    try {
                        val firstDate = sdf.parse(first.timestamp)
                        val secondDate = sdf.parse(second.timestamp)
                        if (firstDate == null || secondDate == null) {
                            0
                        } else {
                            secondDate.compareTo(firstDate)
                        }
                    } catch (_: Exception) {
                        second.timestamp.orEmpty().compareTo(first.timestamp.orEmpty())
                    }
                }

                requireActivity().runOnUiThread {
                    allLogEntries.clear()
                    allLogEntries.addAll(parsedEntries)
                    applyFilters()
                    updateStats()
                    showLoading(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载日志文件失败", e)
                requireActivity().runOnUiThread {
                    showEmptyState(getString(R.string.load_logs_failed) + e.message)
                    showLoading(false)
                }
            }
        }.start()
    }

    private fun applyFilters() {
        if (allLogEntries.isEmpty()) return

        val categoryFilter = selectedCategory.takeIf {
            it.isNotEmpty() && it != getString(R.string.all_categories)
        }

        val levelFilter = if (
            selectedLevel.isNotEmpty() &&
            selectedLevel != getString(R.string.all_levels)
        ) {
            runCatching { LogLevel.valueOf(selectedLevel) }.getOrDefault(LogLevel.UNKNOWN)
        } else {
            LogLevel.UNKNOWN
        }

        val search = searchText.trim().ifEmpty { null }
        val tempFiltered = LogParser.filterEntries(
            allLogEntries,
            selectedModuleKey,
            levelFilter,
            search,
            categoryFilter
        )

        filteredLogEntries.clear()
        filteredLogEntries.addAll(
            tempFiltered.filter { entry ->
                !showErrorsOnly || entry.extractedData["is_error"] == "true"
            }
        )
        updateStats()

        emptyMessage = if (filteredLogEntries.isEmpty()) {
            getString(R.string.no_matching_log_records)
        } else {
            ""
        }
    }

    private fun updateStats() {
        val moduleStats = LogParser.getModuleStats(allLogEntries)
        statsText = getString(
            R.string.stats_format,
            allLogEntries.size,
            filteredLogEntries.size,
            moduleStats.size,
            getLogFileCount()
        )
    }

    private fun getLogFileCount(): String {
        val dir = logDir ?: return "0"
        if (!dir.exists()) return "0"
        val logFiles = dir.listFiles { _, name ->
            name.startsWith("hook_log_") && name.endsWith(".txt")
        }
        return (logFiles?.size ?: 0).toString()
    }

    private fun zipLogFiles(): File? {
        val dir = logDir ?: return null
        if (!dir.exists()) return null

        val logFiles = dir.listFiles { _, name ->
            name.startsWith("hook_log_") && name.endsWith(".txt")
        }
        if (logFiles.isNullOrEmpty()) return null

        val outputDir = File(requireContext().cacheDir, "temp")
        if (!outputDir.exists() && !outputDir.mkdirs()) return null

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val zipFile = File(outputDir, "logs_" + sdf.format(Date()) + ".zip")

        return if (FileUtils.createZipFromFiles(logFiles, zipFile)) zipFile else null
    }

    private fun saveAllLogs() {
        val fileName = "ZTool_Logs_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
            ".zip"
        exportLogLauncher.launch(fileName)
    }

    private fun exportLogsToUri(uri: Uri) {
        showLoading(true)

        Thread {
            try {
                val zipFile = zipLogFiles()
                val result = FileManager.exportFileWithSAF(
                    requireContext(),
                    uri,
                    "logs_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".zip",
                    zipFile
                )
                requireActivity().runOnUiThread {
                    showLoading(false)
                    Toast.makeText(
                        requireContext(),
                        if (result) R.string.export_logs_success else R.string.export_logs_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "导出日志失败", e)
                requireActivity().runOnUiThread {
                    showLoading(false)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_logs_failed) + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun clearAllLogs() {
        showLoading(true)

        Thread {
            try {
                logDir?.takeIf { it.exists() }?.listFiles { _, name ->
                    name.startsWith("hook_log_") && name.endsWith(".txt")
                }?.forEach { it.delete() }

                requireActivity().runOnUiThread {
                    allLogEntries.clear()
                    filteredLogEntries.clear()
                    updateStats()
                    showEmptyState(getString(R.string.logs_cleared_message))
                    showLoading(false)
                    Toast.makeText(requireContext(), R.string.clear_logs_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "清除日志失败", e)
                requireActivity().runOnUiThread {
                    showLoading(false)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.clear_logs_failed) + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun showStatistics() {
        val moduleStats = LogParser.getModuleStats(allLogEntries)
        val errorStats = LogParser.getErrorStats(allLogEntries)

        statisticsMessage = buildString {
            append(getString(R.string.log_statistics_header)).append("\n\n")
            append(getString(R.string.total_logs)).append(allLogEntries.size).append("\n")
            append(getString(R.string.total_modules)).append(moduleStats.size).append("\n")
            append(getString(R.string.total_errors)).append(errorStats["total_errors"]).append("\n")
            append(getString(R.string.log_files_count)).append(getLogFileCount()).append(getString(R.string.log_files_unit)).append("\n\n")
            append(getString(R.string.module_statistics_header)).append("\n")
            for ((module, count) in moduleStats) {
                append(LogParser.getModuleDisplayName(module)).append(": ")
                    .append(count)
                    .append(getString(R.string.log_count_unit))
                    .append("\n")
            }
        }
    }

    private fun buildLogDetails(entry: LogEntry): String {
        return buildString {
            append(getString(R.string.log_detail_time)).append(entry.timestamp).append("\n")
            append(getString(R.string.log_detail_module)).append(LogParser.getModuleDisplayName(entry.module)).append("\n")
            append(getString(R.string.log_detail_level)).append(entry.level).append("\n")
            append(getString(R.string.log_detail_tag)).append(entry.tag).append("\n")
            append(getString(R.string.log_detail_pid)).append(entry.pid).append("\n")
            append(getString(R.string.log_detail_mode)).append(entry.mode).append("\n")
            append(getString(R.string.log_detail_function)).append(entry.function ?: getString(R.string.none)).append("\n")
            append(getString(R.string.log_detail_multiline)).append(if (entry.isMultiLine) getString(R.string.yes) else getString(R.string.no)).append("\n\n")
            append(getString(R.string.full_message_header)).append("\n")
            append(entry.fullMessage).append("\n\n")

            if (entry.extractedData.isNotEmpty()) {
                append(getString(R.string.extracted_data_header)).append("\n")
                for ((key, value) in entry.extractedData) {
                    append(key).append(": ").append(value).append("\n")
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.log_content), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(show: Boolean) {
        isLoading = show
        if (show) {
            emptyMessage = ""
        }
    }

    private fun showEmptyState(message: String) {
        emptyMessage = message
    }

    companion object {
        private const val TAG = "AuditFragment"
    }
}

private data class ModuleOption(
    val key: String?,
    val label: String
) {
    override fun toString(): String = label
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditScreen(
    statsText: String,
    categoryOptions: List<String>,
    selectedCategory: String,
    moduleOptions: List<ModuleOption>,
    selectedModuleLabel: String,
    levelOptions: List<String>,
    selectedLevel: String,
    searchText: String,
    showErrorsOnly: Boolean,
    isLoading: Boolean,
    emptyMessage: String,
    logEntries: List<LogEntry>,
    onCategorySelected: (String) -> Unit,
    onModuleSelected: (ModuleOption) -> Unit,
    onLevelSelected: (String) -> Unit,
    onSearchTextChanged: (String) -> Unit,
    onShowErrorsOnlyChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onShowStatistics: () -> Unit,
    onSave: () -> Unit,
    onLogSelected: (LogEntry) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            if (logEntries.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    icon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                    text = { Text("") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.logsFragment_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = statsText.ifEmpty { stringResource(R.string.placeHolderLogStat) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                FilterCard(
                    categoryOptions = categoryOptions,
                    selectedCategory = selectedCategory,
                    moduleOptions = moduleOptions,
                    selectedModuleLabel = selectedModuleLabel,
                    levelOptions = levelOptions,
                    selectedLevel = selectedLevel,
                    searchText = searchText,
                    showErrorsOnly = showErrorsOnly,
                    onCategorySelected = onCategorySelected,
                    onModuleSelected = onModuleSelected,
                    onLevelSelected = onLevelSelected,
                    onSearchTextChanged = onSearchTextChanged,
                    onShowErrorsOnlyChanged = onShowErrorsOnlyChanged,
                    onRefresh = onRefresh,
                    onClear = onClear,
                    onShowStatistics = onShowStatistics,
                    onSave = onSave
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        emptyMessage.isNotEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emptyMessage,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                items(logEntries) { entry ->
                                    LogEntryRow(
                                        entry = entry,
                                        onClick = { onLogSelected(entry) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterCard(
    categoryOptions: List<String>,
    selectedCategory: String,
    moduleOptions: List<ModuleOption>,
    selectedModuleLabel: String,
    levelOptions: List<String>,
    selectedLevel: String,
    searchText: String,
    showErrorsOnly: Boolean,
    onCategorySelected: (String) -> Unit,
    onModuleSelected: (ModuleOption) -> Unit,
    onLevelSelected: (String) -> Unit,
    onSearchTextChanged: (String) -> Unit,
    onShowErrorsOnlyChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onShowStatistics: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.searchLog)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = stringResource(R.string.filterByType),
                    value = selectedCategory,
                    options = categoryOptions,
                    optionLabel = { it },
                    onOptionSelected = onCategorySelected
                )
                DropdownField(
                    label = stringResource(R.string.filterByModule),
                    value = selectedModuleLabel,
                    options = moduleOptions,
                    optionLabel = { it.label },
                    onOptionSelected = onModuleSelected
                )
                DropdownField(
                    label = stringResource(R.string.filterByLevel),
                    value = selectedLevel,
                    options = levelOptions,
                    optionLabel = { it },
                    onOptionSelected = onLevelSelected
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onShowErrorsOnlyChanged(!showErrorsOnly) }
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showErrorsOnly,
                        onCheckedChange = onShowErrorsOnlyChanged
                    )
                    Text(
                        text = stringResource(R.string.advancedFilterError),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.logClear),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onShowStatistics) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stats),
                        contentDescription = stringResource(R.string.logStatistic)
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = stringResource(R.string.export_logs_success)
                    )
                }
                FilledTonalIconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.logRefresh)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: LogEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(levelColor(entry.logLevel))
        )
        Spacer(modifier = Modifier.width(8.dp))

        StatusIcon(entry)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.timestamp?.takeIf { it.length >= 12 }?.substring(11) ?: "--:--:--",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                entry.module?.let {
                    LogTag(text = LogParser.getModuleDisplayName(it), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                LogTag(text = entry.level ?: "?", color = levelColor(entry.logLevel))
                if (entry.isMultiLine) {
                    Spacer(modifier = Modifier.width(6.dp))
                    LogTag(
                        text = stringResource(R.string.multiLineStackTrace),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Text(
                text = entry.previewMessage(stringResource(R.string.more_lines_suffix)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )

            val details = entry.detailsText()
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(entry: LogEntry) {
    when {
        entry.extractedData["is_error"] == "true" -> {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp)
            )
        }
        entry.extractedData["is_success"] == "true" -> {
            Icon(
                imageVector = Icons.Rounded.TaskAlt,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp)
            )
        }
    }
}

@Composable
private fun LogTag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 132.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun levelColor(level: LogLevel?): Color {
    return when (level) {
        LogLevel.DEBUG -> Color(0xFF2196F3)
        LogLevel.INFO -> Color(0xFF4CAF50)
        LogLevel.WARN -> Color(0xFFFFC107)
        LogLevel.ERROR -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
}

private fun LogEntry.previewMessage(moreLinesSuffix: String): String {
    var preview = fullMessage
    if (isMultiLine) {
        val lines = preview.split("\n")
        if (lines.isNotEmpty()) {
            preview = lines.first()
            if (preview.length > 100) preview = preview.take(100) + "..."
            preview += " ... [" + (lines.size - 1) + moreLinesSuffix + "]"
        }
    } else if (preview.length > 100) {
        preview = preview.take(100) + "..."
    }
    return preview
}

private fun LogEntry.detailsText(): String {
    val details = mutableListOf<String>()
    if (tag != null && tag != "ZToolXposedModule") details.add("Tag: $tag")
    if (pid != -1) details.add("PID: $pid")
    if (mode != null) details.add("Mode: $mode")
    if (function != null) details.add("Function: $function")
    return details.joinToString(" | ")
}

@Composable
private fun LogDetailDialog(
    entry: LogEntry,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_detail_title)) },
        text = {
            Text(
                text = buildDialogLogDetails(entry),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.copy_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_button))
            }
        }
    )
}

@Composable
private fun buildDialogLogDetails(entry: LogEntry): String {
    val none = stringResource(R.string.none)
    val yes = stringResource(R.string.yes)
    val no = stringResource(R.string.no)
    return buildString {
        append(stringResource(R.string.log_detail_time)).append(entry.timestamp).append("\n")
        append(stringResource(R.string.log_detail_module)).append(LogParser.getModuleDisplayName(entry.module)).append("\n")
        append(stringResource(R.string.log_detail_level)).append(entry.level).append("\n")
        append(stringResource(R.string.log_detail_tag)).append(entry.tag).append("\n")
        append(stringResource(R.string.log_detail_pid)).append(entry.pid).append("\n")
        append(stringResource(R.string.log_detail_mode)).append(entry.mode).append("\n")
        append(stringResource(R.string.log_detail_function)).append(entry.function ?: none).append("\n")
        append(stringResource(R.string.log_detail_multiline)).append(if (entry.isMultiLine) yes else no).append("\n\n")
        append(stringResource(R.string.full_message_header)).append("\n")
        append(entry.fullMessage).append("\n\n")
        if (entry.extractedData.isNotEmpty()) {
            append(stringResource(R.string.extracted_data_header)).append("\n")
            for ((key, value) in entry.extractedData) {
                append(key).append(": ").append(value).append("\n")
            }
        }
    }
}

@Composable
private fun ClearLogsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_logs_title)) },
        text = { Text(stringResource(R.string.clear_logs_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.clear_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}

@Composable
private fun StatisticsDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_statistics_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_yes))
            }
        }
    )
}
