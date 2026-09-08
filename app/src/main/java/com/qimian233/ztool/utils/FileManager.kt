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
 * Android API 33+ 文件管理工具类
 * 支持 SAF (Storage Access Framework) 和 MediaStore 方式读写文件
 */
object FileManager {
    private const val TAG = "FileManager"

    /**
     * 使用 SAF 导出文件
     * @param context 上下文
     * @param uri 目标目录Uri
     * @param fileName 要保存的文件名
     * @param sourceFile 要导出的源文件
     * @return 是否成功
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
            Log.i(TAG, "文件已导出到$uri$fileName")
            true
        } catch (e: IOException) {
            Log.e(TAG, "导出文件失败: " + e.message)
            false
        }
    }

    /**
     * 使用 SAF 创建文件并保存配置
     */
    fun saveConfigWithSAF(context: Context, uri: Uri?, fileName: String, configContent: String?): Boolean {
        if (uri == null) {
            Log.e(TAG, "uri为空")
            return false
        }
        if (configContent == null) {
            Log.e(TAG, "配置内容为空")
            return false
        }
        val resolver = context.contentResolver
        return try {
            val outputStream = resolver.openOutputStream(uri)
            if (outputStream != null) {
                outputStream.use { out ->
                    out.write(configContent.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                    Log.i(TAG, "配置已保存到$uri$fileName")
                    true
                }
            } else {
                Log.e(TAG, "输出流为空")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "保存文件失败: " + e.message)
            false
        }
    }

    /**
     * 使用 SAF 打开并读取文件
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
            Log.e("SAF", "SAF 读取文件失败: " + e.message)
            null
        }
    }

    /**
     * 生成备份文件名
     */
    fun generateBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        return "ZTool_Config_Backup_$timestamp.json"
    }
}
