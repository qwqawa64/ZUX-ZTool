package com.qimian233.ztool.data.launcher

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Reads the launcher's own desktop-layout facts without Hook or APK parsing.
 *
 * [readExternalInputs] performs blocking settings and provider reads, so callers must
 * invoke it off the main thread.
 *
 * See docs/research/zui_launcher_grid_sources.md for the discovered data flow.
 */
class LauncherPreviewRepository(private val context: Context) {

    fun readExternalInputs(): ExternalGridInputs = ExternalGridInputs(
        systemLayoutName = readSystemLayoutName(),
        providerLayouts = readProviderLayouts()
    )

    private fun readSystemLayoutName(): String? = try {
        Settings.System.getString(context.contentResolver, SETTINGS_LAYOUT_NAME_KEY)
    } catch (e: Exception) {
        Log.d(TAG, "current layout name unavailable", e)
        null
    }

    private fun readProviderLayouts(): List<ProviderLayout> = try {
        context.contentResolver.query(Uri.parse(PROVIDER_URI), null, null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(COLUMN_NAME)
                val rowsIndex = cursor.getColumnIndex(COLUMN_ROWS)
                val columnsIndex = cursor.getColumnIndex(COLUMN_COLUMNS)
                val defaultIndex = cursor.getColumnIndex(COLUMN_IS_DEFAULT)
                if (nameIndex < 0 || rowsIndex < 0 || columnsIndex < 0) {
                    emptyList()
                } else {
                    buildList {
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(nameIndex) ?: continue
                            add(
                                ProviderLayout(
                                    name = name,
                                    columns = cursor.getInt(columnsIndex),
                                    rows = cursor.getInt(rowsIndex),
                                    isDefault = defaultIndex >= 0 && TRUE.equals(
                                        cursor.getString(defaultIndex),
                                        ignoreCase = true
                                    )
                                )
                            )
                        }
                    }
                }
            } ?: emptyList()
    } catch (e: Exception) {
        // Provider missing or disabled on this ROM build; the resolver falls back.
        Log.d(TAG, "grid_control provider unavailable", e)
        emptyList()
    }

    private companion object {
        const val TAG = "LauncherPreview"

        /** `Settings.System` mirror the launcher writes when the desktop layout changes. */
        const val SETTINGS_LAYOUT_NAME_KEY = "extra_new_layout_config"

        /** Launcher-owned, exported and permission-free; see the research note. */
        const val PROVIDER_URI = "content://com.zui.launcher.grid_control/list_options"

        const val COLUMN_NAME = "name"
        const val COLUMN_ROWS = "rows"
        const val COLUMN_COLUMNS = "cols"
        const val COLUMN_IS_DEFAULT = "is_default"
        const val TRUE = "true"
    }
}
