package com.qimian233.ztool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.audit.LogParser
import com.qimian233.ztool.audit.LogParser.LogEntry
import com.qimian233.ztool.audit.LogParser.LogLevel
import com.qimian233.ztool.data.audit.AuditRepository
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolCircularProgressIndicator
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolPopupMenuField
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.AuditUiState
import com.qimian233.ztool.viewmodel.AuditViewModel
import com.qimian233.ztool.viewmodel.ModuleOption
import kotlinx.coroutines.launch

@Composable
fun AuditMainRoute() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val viewModel = remember {
        val repository = AuditRepository(context.applicationContext)
        ViewModelProvider(
            activity,
            AuditViewModelFactory(repository)
        )[AuditViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val exportLogsSuccessStr = stringResource(R.string.export_logs_success)
    val exportLogsFailedStr = stringResource(R.string.export_logs_failed)
    
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLogsToUri(uri) { success, error ->
                activity.runOnUiThread {
                    Toast.makeText(
                        context,
                        when {
                            success -> exportLogsSuccessStr
                            error != null -> exportLogsFailedStr + error
                            else -> exportLogsFailedStr
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    AuditScreen(
        state = uiState,
        onCategorySelected = viewModel::selectCategory,
        onModuleSelected = viewModel::selectModule,
        onLevelSelected = viewModel::selectLevel,
        onSearchTextChanged = viewModel::setSearchText,
        onShowErrorsOnlyChanged = viewModel::setShowErrorsOnly,
        onRefresh = viewModel::loadAllLogFiles,
        onClear = viewModel::showClearDialog,
        onShowStatistics = viewModel::showStatistics,
        onSave = { exportLogLauncher.launch(viewModel.exportFileName()) },
        onLogSelected = viewModel::selectLogEntry
    )

    uiState.selectedLogEntry?.let { entry ->
        LogDetailDialog(
            entry = entry,
            onCopy = {
                copyLogDetailsToClipboard(context, viewModel.buildLogDetails(entry))
                viewModel.dismissLogEntry()
            },
            onDismiss = viewModel::dismissLogEntry
        )
    }

    val clearLogsSuccessStr = stringResource(R.string.clear_logs_success)
    val clearLogsFailedStr = stringResource(R.string.clear_logs_failed)
    
    if (uiState.showClearDialog) {
        ClearLogsDialog(
            onConfirm = {
                viewModel.clearAllLogs { success, error ->
                    activity.runOnUiThread {
                        Toast.makeText(
                            context,
                            if (success) {
                                clearLogsSuccessStr
                            } else {
                                clearLogsFailedStr + error.orEmpty()
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = viewModel::dismissClearDialog
        )
    }

    uiState.statisticsMessage?.let { message ->
        StatisticsDialog(
            message = message,
            onDismiss = viewModel::dismissStatistics
        )
    }
}

private fun copyLogDetailsToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(context.getString(R.string.log_content), text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
}

private class AuditViewModelFactory(
    private val repository: AuditRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuditViewModel::class.java)) {
            return AuditViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun AuditScreen(
    state: AuditUiState,
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

    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.logsFragment_title),
                addNavIcon = false
            )
        },
        floatingActionButton = {
            if (state.filteredLogEntries.isNotEmpty()) {
                ZToolExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    icon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                    text = { Text(stringResource(R.string.back_to_top)) }
                )
            }
        }
    ) { innerPadding ->
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.statsText.ifEmpty { stringResource(R.string.placeHolderLogStat) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                FilterCard(
                    categoryOptions = state.categoryOptions,
                    selectedCategory = state.selectedCategory,
                    moduleOptions = state.moduleOptions,
                    selectedModuleLabel = state.selectedModuleLabel,
                    levelOptions = state.levelOptions,
                    selectedLevel = state.selectedLevel,
                    searchText = state.searchText,
                    showErrorsOnly = state.showErrorsOnly,
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

                ZToolCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ZToolCircularProgressIndicator()
                            }
                        }
                        state.emptyMessage.isNotEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.emptyMessage,
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
                                items(state.filteredLogEntries) { entry ->
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
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                PopupMenuField(
                    title = stringResource(R.string.filterByType),
                    value = selectedCategory,
                    options = categoryOptions,
                    optionLabel = { it },
                    onOptionSelected = onCategorySelected
                )
                PopupMenuField(
                    title = stringResource(R.string.filterByModule),
                    value = selectedModuleLabel,
                    options = moduleOptions,
                    optionLabel = { it.label },
                    onOptionSelected = onModuleSelected
                )
                PopupMenuField(
                    title = stringResource(R.string.filterByLevel),
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.logClear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onShowStatistics) {
                    Icon(
                        imageVector = Icons.Rounded.QueryStats,
                        contentDescription = stringResource(R.string.logStatistic),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun <T> PopupMenuField(
    title: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    ZToolPopupMenuField(
        value = value,
        options = options,
        optionLabel = optionLabel,
        onOptionSelected = onOptionSelected,
        dialogTitle = title
    )
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
            .padding(vertical = 8.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp)
                .background(color = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    return details.joinToString(" | ")
}

@Composable
private fun LogDetailDialog(
    entry: LogEntry,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_detail_title)) },
        text = {
            Text(
                text = buildDialogLogDetails(entry),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        dismissButton = {
            ZToolTextButton(onClick = onCopy, text = stringResource(R.string.copy_button), isPrimary = false)
        },
        confirmButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.close_button))
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
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_logs_title)) },
        text = { Text(stringResource(R.string.clear_logs_message)) },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.clear_button))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.restart_no), isPrimary = false)
        }
    )
}

@Composable
private fun StatisticsDialog(
    message: String,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_statistics_title)) },
        text = { Text(message) },
        confirmButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.confirm))
        }
    )
}
