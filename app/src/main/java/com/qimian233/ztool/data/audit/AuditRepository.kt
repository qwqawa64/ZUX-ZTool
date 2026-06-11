package com.qimian233.ztool.data.audit

import android.content.Context
import android.net.Uri
import android.util.Log
import com.qimian233.ztool.R
import com.qimian233.ztool.audit.LogParser
import com.qimian233.ztool.audit.LogParser.LogEntry
import com.qimian233.ztool.audit.LogParser.LogLevel
import com.qimian233.ztool.utils.FileManager
import com.qimian233.ztool.utils.FileUtils
import com.qimian233.ztool.viewmodel.AuditUiState
import com.qimian233.ztool.viewmodel.ModuleOption
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditRepository(
    private val context: Context
) {
    private val modulesByCategory: Map<String, List<String>> = LogParser.getModulesByCategory()

    fun initialState(): AuditUiState {
        val allCategories = context.getString(R.string.all_categories)
        val levelOptions = listOf(context.getString(R.string.all_levels), "DEBUG", "INFO", "WARN", "ERROR")
        return AuditUiState(
            categoryOptions = listOf(allCategories) + modulesByCategory.keys.sortedWith(String.CASE_INSENSITIVE_ORDER),
            selectedCategory = allCategories,
            levelOptions = levelOptions,
            selectedLevel = levelOptions.first(),
            moduleOptions = moduleOptionsFor(allCategories),
            selectedModuleLabel = context.getString(R.string.all_modules),
            statsText = statsText(emptyList(), emptyList())
        )
    }

    fun moduleOptionsFor(category: String): List<ModuleOption> {
        val allCategories = context.getString(R.string.all_categories)
        val moduleKeys = if (
            category.isNotEmpty() &&
            category != allCategories &&
            modulesByCategory.containsKey(category)
        ) {
            modulesByCategory[category].orEmpty()
        } else {
            LogParser.getAvailableModules()
        }

        return listOf(ModuleOption(null, context.getString(R.string.all_modules))) +
            moduleKeys.map { key ->
                ModuleOption(key, LogParser.getModuleDisplayName(key).takeIf { it.isNotBlank() } ?: key)
            }
    }

    fun loadLogs(): LogLoadResult {
        val dir = logDir()
        if (!dir.exists() || !dir.isDirectory) {
            return LogLoadResult(emptyList(), context.getString(R.string.log_directory_not_exists))
        }

        val parsedEntries = LogParser.parseAllLogFiles(dir)
        if (parsedEntries.isEmpty()) {
            return LogLoadResult(emptyList(), context.getString(R.string.no_log_records_found))
        }

        parsedEntries.sortWith { first, second -> compareLogEntriesByTime(first, second) }
        return LogLoadResult(parsedEntries, "")
    }

    fun filterLogs(entries: List<LogEntry>, state: AuditUiState): List<LogEntry> {
        if (entries.isEmpty()) return emptyList()

        val categoryFilter = state.selectedCategory.takeIf {
            it.isNotEmpty() && it != context.getString(R.string.all_categories)
        }
        val levelFilter = if (
            state.selectedLevel.isNotEmpty() &&
            state.selectedLevel != context.getString(R.string.all_levels)
        ) {
            runCatching { LogLevel.valueOf(state.selectedLevel) }.getOrDefault(LogLevel.UNKNOWN)
        } else {
            LogLevel.UNKNOWN
        }
        val search = state.searchText.trim().ifEmpty { null }

        return LogParser.filterEntries(
            entries,
            state.selectedModuleKey,
            levelFilter,
            search,
            categoryFilter
        ).filter { entry ->
            !state.showErrorsOnly || entry.extractedData["is_error"] == "true"
        }
    }

    fun statsText(entries: List<LogEntry>, filteredEntries: List<LogEntry>): String {
        val moduleStats = LogParser.getModuleStats(entries)
        return context.getString(
            R.string.stats_format,
            entries.size,
            filteredEntries.size,
            moduleStats.size,
            getLogFileCount()
        )
    }

    fun statisticsMessage(entries: List<LogEntry>): String {
        val moduleStats = LogParser.getModuleStats(entries)
        val errorStats = LogParser.getErrorStats(entries)
        return buildString {
            append(context.getString(R.string.log_statistics_header)).append("\n\n")
            append(context.getString(R.string.total_logs)).append(entries.size).append("\n")
            append(context.getString(R.string.total_modules)).append(moduleStats.size).append("\n")
            append(context.getString(R.string.total_errors)).append(errorStats["total_errors"]).append("\n")
            append(context.getString(R.string.log_files_count)).append(getLogFileCount())
                .append(context.getString(R.string.log_files_unit)).append("\n\n")
            append(context.getString(R.string.module_statistics_header)).append("\n")
            for ((module, count) in moduleStats) {
                append(LogParser.getModuleDisplayName(module)).append(": ")
                    .append(count)
                    .append(context.getString(R.string.log_count_unit))
                    .append("\n")
            }
        }
    }

    fun clearLogs() {
        logDir().takeIf { it.exists() }?.listFiles { _, name -> isHookLogFile(name) }?.forEach { it.delete() }
    }

    fun exportLogsToUri(uri: Uri): Boolean {
        val zipFile = zipLogFiles()
        return FileManager.exportFileWithSAF(
            context,
            uri,
            "logs_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".zip",
            zipFile
        )
    }

    fun exportFileName(): String {
        return "ZTool_Logs_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
            ".zip"
    }

    fun buildLogDetails(entry: LogEntry): String {
        return buildString {
            append(context.getString(R.string.log_detail_time)).append(entry.timestamp).append("\n")
            append(context.getString(R.string.log_detail_module)).append(LogParser.getModuleDisplayName(entry.module)).append("\n")
            append(context.getString(R.string.log_detail_level)).append(entry.level).append("\n")
            append(context.getString(R.string.log_detail_tag)).append(entry.tag).append("\n")
            append(context.getString(R.string.log_detail_pid)).append(entry.pid).append("\n")
            append(context.getString(R.string.log_detail_mode)).append(entry.mode).append("\n")
            append(context.getString(R.string.log_detail_function)).append(entry.function ?: context.getString(R.string.none)).append("\n")
            append(context.getString(R.string.log_detail_multiline)).append(if (entry.isMultiLine) context.getString(R.string.yes) else context.getString(R.string.no)).append("\n\n")
            append(context.getString(R.string.full_message_header)).append("\n")
            append(entry.fullMessage).append("\n\n")

            if (entry.extractedData.isNotEmpty()) {
                append(context.getString(R.string.extracted_data_header)).append("\n")
                for ((key, value) in entry.extractedData) {
                    append(key).append(": ").append(value).append("\n")
                }
            }
        }
    }

    fun loadLogsFailedMessage(error: String?): String {
        return context.getString(R.string.load_logs_failed) + error.orEmpty()
    }

    fun logsClearedMessage(): String = context.getString(R.string.logs_cleared_message)

    fun noMatchingLogsMessage(): String = context.getString(R.string.no_matching_log_records)

    private fun zipLogFiles(): File? {
        val dir = logDir()
        if (!dir.exists()) return null

        val logFiles = dir.listFiles { _, name -> isHookLogFile(name) }
        if (logFiles.isNullOrEmpty()) return null

        val outputDir = File(context.cacheDir, "temp")
        if (!outputDir.exists() && !outputDir.mkdirs()) return null

        val zipFile = File(
            outputDir,
            "logs_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".zip"
        )
        return if (FileUtils.createZipFromFiles(logFiles, zipFile)) zipFile else null
    }

    private fun getLogFileCount(): String {
        val dir = logDir()
        if (!dir.exists()) return "0"
        return (dir.listFiles { _, name -> isHookLogFile(name) }?.size ?: 0).toString()
    }

    private fun logDir(): File = File(context.filesDir, "Log")

    private fun isHookLogFile(name: String): Boolean {
        return name.startsWith("hook_log_") && name.endsWith(".txt")
    }

    private fun compareLogEntriesByTime(first: LogEntry, second: LogEntry): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val firstDate = sdf.parse(first.timestamp)
            val secondDate = sdf.parse(second.timestamp)
            if (firstDate == null || secondDate == null) {
                0
            } else {
                secondDate.compareTo(firstDate)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compare log timestamps", e)
            second.timestamp.orEmpty().compareTo(first.timestamp.orEmpty())
        }
    }

    companion object {
        private const val TAG = "AuditRepository"
    }
}

data class LogLoadResult(
    val entries: List<LogEntry>,
    val emptyMessage: String
)
