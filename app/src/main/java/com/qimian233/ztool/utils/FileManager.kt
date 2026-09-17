package com.qimian233.ztool.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File management utilities for Android API 33+
 * Supports reading and writing files via SAF (Storage Access Framework) and MediaStore
 */
object FileManager {
    private const val TAG = "FileManager"

    /**
     * Export a file via SAF
     * @param context context
     * @param uri target directory Uri
     * @param fileName file name to save
     * @param sourceFile source file to export
     * @return whether the export succeeded
     */
    fun exportFileWithSAF(context: Context, uri: Uri?, fileName: String, sourceFile: File?): Boolean {
        if (uri == null || sourceFile == null || !sourceFile.exists()) return false

        val resolver = context.contentResolver
        return try {
            FileInputStream(sourceFile).use { inputStream ->
                val outputStream = resolver.openOutputStream(uri)
                if (outputStream == null) return false
                outputStream.use { out ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        out.write(buffer, 0, length)
                    }
                }
            }
            Log.i(TAG, "file exported to $uri$fileName")
            true
        } catch (e: IOException) {
            Log.e(TAG, "failed to export file: " + e.message)
            false
        }
    }

    /**
     * Create a file via SAF and save config content
     */
    fun saveConfigWithSAF(context: Context, uri: Uri?, fileName: String, configContent: String?): Boolean {
        if (uri == null) {
            Log.e(TAG, "uri is null")
            return false
        }
        if (configContent == null) {
            Log.e(TAG, "config content is null")
            return false
        }
        val resolver = context.contentResolver
        return try {
            val outputStream = resolver.openOutputStream(uri)
            if (outputStream != null) {
                outputStream.use { out ->
                    out.write(configContent.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                    Log.i(TAG, "config saved to $uri$fileName")
                    true
                }
            } else {
                Log.e(TAG, "output stream is null")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "failed to save file: " + e.message)
            false
        }
    }

    /**
     * Open and read a file via SAF
     */
    fun readConfigWithSAF(context: Context, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri)
            if (inputStream != null) {
                val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
                reader.close()
                inputStream.close()
                stringBuilder.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SAF", "SAF failed to read file: " + e.message)
            null
        }
    }

    /**
     * Generate a backup file name
     */
    fun generateBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        return "ZTool_Config_Backup_$timestamp.json"
    }
}
