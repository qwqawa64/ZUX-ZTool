package com.qimian233.ztool.utils

import android.content.Context
import android.util.Base64
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmbeddingConfigManager {

    // 单条配置文件信息
    data class ConfigFileInfo(
        val file: File,
        val timestamp: String,
        val packageName: String,
        val appName: String,
        val configContent: String,
    )

    // 加载配置文件列表
    fun loadAndValidateConfigFiles(context: Context): List<ConfigFileInfo> {
        val validConfigs = ArrayList<ConfigFileInfo>()
        val configDir = File(context.filesDir, "data/custom_EmbeddingConfig")

        if (!configDir.exists() || !configDir.isDirectory) return validConfigs

        val files = configDir.listFiles() ?: return validConfigs

        for (file in files) {
            if (file.isFile) {
                val info = parseConfigFile(context, file)
                if (info != null) {
                    validConfigs.add(info)
                } else {
                    file.delete()
                }
            }
        }
        return validConfigs
    }

    private fun parseConfigFile(context: Context, file: File): ConfigFileInfo? {
        return try {
            val parts = file.name.split("_", limit = 2)
            if (parts.size != 2) return null

            val ts = parts[0].toLong()
            val timestamp = SimpleDateFormat("yyyy.MM.dd-HH:mm:ss", Locale.getDefault()).format(Date(ts * 1000L))
            val packageName = parts[1]
            val appName = getAppName(context, packageName)

            val base64 = FileUtils.readFileContent(file) ?: return null

            val configContent = String(Base64.decode(base64, Base64.DEFAULT), StandardCharsets.UTF_8)

            // 简单验证 JSON
            JSONObject(configContent)

            ConfigFileInfo(file, timestamp, packageName, appName, configContent)
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppName(context: Context, pkg: String): String {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    // 核心功能：刷入配置
    @Throws(Exception::class)
    fun flashConfigs(context: Context, configs: List<ConfigFileInfo>) {
        val executor = EnhancedShellExecutor.getInstance()
        val cacheDir = context.cacheDir
        val tempDir = File(cacheDir, "module_temp")
        val tempJsonFile = File(tempDir, "embedding_config.json")

        // 1. 准备环境
        FileUtils.deleteRecursive(tempDir)
        if (!tempDir.mkdirs()) throw Exception(context.getString(R.string.common_error_create_temp_dir))

        // 2. 复制原配置到临时目录 (Root -> App Cache)
        val cpRes = executor.executeRootCommand("cat " + MODULE_CONFIG_FILE + " > " + tempJsonFile.absolutePath)
        if (!cpRes.isSuccess) throw Exception(context.getString(R.string.common_error_copy_original_config))

        // 3. 修改权限以便 App 读取
        val uid = android.os.Process.myUid()
        executor.executeRootCommand("chown " + uid + "." + uid + " " + tempJsonFile.absolutePath)
        executor.executeRootCommand("chmod 644 " + tempJsonFile.absolutePath)

        // 4. 解析 JSON 并合并
        val originalContent = FileUtils.readFileContent(tempJsonFile)
            ?: throw Exception(context.getString(R.string.common_error_read_original_config))

        val rootJson = JSONObject(originalContent)
        var packages = rootJson.getJSONArray("packages")

        for (config in configs) {
            val newConfig = JSONObject(config.configContent)
            val pkgName = newConfig.getString("name")

            val mergedPackages = JSONArray()
            // 过滤掉旧的同名配置
            for (i in 0 until packages.length()) {
                val p = packages.getJSONObject(i)
                if (p.getString("name") != pkgName) {
                    mergedPackages.put(p)
                }
            }
            // 添加新配置
            mergedPackages.put(newConfig)
            packages = mergedPackages
        }
        rootJson.put("packages", packages)

        // 5. 写回临时文件
        FileUtils.writeStringToFile(tempJsonFile, rootJson.toString(2))

        // 6. 覆盖回系统目录 (Root)
        val restoreCmd = "cp " + tempJsonFile.absolutePath + " " + MODULE_CONFIG_FILE + " && " +
                "chmod 644 " + MODULE_CONFIG_FILE
        val restoreRes = executor.executeRootCommand(restoreCmd)

        FileUtils.deleteRecursive(tempDir) // 清理

        if (!restoreRes.isSuccess) {
            throw Exception(context.getString(R.string.common_error_update_module_config))
        }
    }

    companion object {
        private const val MODULE_CONFIG_FILE = "/data/adb/modules/zuxos_embedding/embedding_config.json"
    }
}
