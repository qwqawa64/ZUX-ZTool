package com.qimian233.ztool.data.settings

import android.content.Context
import android.util.Log
import com.qimian233.ztool.R
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.PackageInfo
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

class MagicWindowSearchRepository(
    private val context: Context
) {
    private var embeddingConfig: JSONObject? = null

    fun loadEmbeddingConfig(): MagicWindowConfigLoadResult {
        return try {
            embeddingConfig = JSONObject(requireNotNull(readFileWithRoot(MODULE_CONFIG_PATH)))
            val count = embeddingConfig?.getJSONArray("packages")?.length() ?: 0
            MagicWindowConfigLoadResult.Loaded(context.getString(R.string.module_config_tips, count))
        } catch (_: Exception) {
            try {
                embeddingConfig = JSONObject(requireNotNull(loadJsonFromAsset(ASSET_CONFIG_PATH)))
                MagicWindowConfigLoadResult.Loaded(context.getString(R.string.official_config_tips))
            } catch (_: Exception) {
                embeddingConfig = null
                MagicWindowConfigLoadResult.Missing(context.getString(R.string.config_not_exists_tips))
            }
        }
    }

    fun search(keyword: String): List<PackageInfo> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()

        val config = embeddingConfig ?: return emptyList()
        val packages = config.getJSONArray("packages")
        val normalizedQuery = query.lowercase(Locale.getDefault())
        val results = mutableListOf<PackageInfo>()

        for (index in 0 until packages.length()) {
            val packageObject = packages.getJSONObject(index)
            val name = packageObject.optString("name", "")
            if (name.lowercase(Locale.getDefault()).contains(normalizedQuery)) {
                results.add(PackageInfo(packageObject))
            }
        }
        return results
    }

    private fun loadJsonFromAsset(fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading asset file: $fileName", e)
            null
        }
    }

    private fun readFileWithRoot(filePath: String): String? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = DataInputStream(process.inputStream)

            outputStream.writeBytes("cat $filePath\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val result = StringBuilder()
            val reader = BufferedReader(InputStreamReader(DataInputStream(inputStream)))
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

    companion object {
        private const val TAG = "MagicWindowSearch"
        private const val MODULE_CONFIG_PATH = "/data/system/zui/embedding/embedding_config.json"
        private const val ASSET_CONFIG_PATH = "embedding/embedding_config.json"
    }
}

sealed interface MagicWindowConfigLoadResult {
    data class Loaded(val tipsText: String) : MagicWindowConfigLoadResult
    data class Missing(val tipsText: String) : MagicWindowConfigLoadResult
}
