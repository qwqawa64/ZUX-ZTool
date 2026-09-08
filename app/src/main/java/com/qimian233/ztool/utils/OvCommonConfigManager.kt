package com.qimian233.ztool.utils

import android.content.Context
import android.util.Xml
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileInputStream
import java.io.StringWriter

class OvCommonConfigManager {

    // 对应 <user_persist> 的配置模型
    class AppConfig {
        var overrideSplitSupport: Boolean? = null     // overrideSplitSupport
        var overrideFreeformSupport: Boolean? = null  // overrideFreeformSupport
        var overrideFreeformDragMode: Int? = null     // overrideFreeformDragMode (1=Free, 0=Fixed)

        // 判断是否所有配置都为空（如果是，则需要删除该条目）
        fun isEmpty(): Boolean {
            return overrideSplitSupport == null && overrideFreeformSupport == null && overrideFreeformDragMode == null
        }
    }

    // 加载配置：System -> Cache -> Map
    fun loadConfig(context: Context): MutableMap<String, AppConfig> {
        val configMap = HashMap<String, AppConfig>()
        val executor = EnhancedShellExecutor.getInstance()
        val tempFile = File(context.cacheDir, TEMP_FILE_NAME)

        // 1. 尝试将系统文件复制到缓存
        // 如果文件不存在，直接返回空 Map
        val checkRes = executor.executeRootCommand("ls " + SYSTEM_FILE_PATH)
        if (!checkRes.isSuccess) {
            return configMap // 文件不存在，返回空
        }

        executor.executeRootCommand("cp " + SYSTEM_FILE_PATH + " " + tempFile.absolutePath)
        executor.executeRootCommand("chmod 644 " + tempFile.absolutePath) // 确保 App 可读

        // 2. 解析 XML
        if (tempFile.exists()) {
            try {
                FileInputStream(tempFile).use { fis ->
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(fis, "UTF-8")

                    var eventType = parser.eventType
                    var currentPackage: String? = null
                    var currentConfig: AppConfig? = null

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val tagName = parser.name
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if ("config" == tagName) {
                                    currentPackage = parser.getAttributeValue(null, "packageName")
                                    currentConfig = AppConfig()
                                } else if ("user_persist" == tagName) {
                                    val cfg = currentConfig
                                    if (cfg != null) {
                                        val split = parser.getAttributeValue(null, "overrideSplitSupport")
                                        val freeform = parser.getAttributeValue(null, "overrideFreeformSupport")
                                        val dragMode = parser.getAttributeValue(null, "overrideFreeformDragMode")

                                        if (split != null) cfg.overrideSplitSupport = "1" == split
                                        if (freeform != null) cfg.overrideFreeformSupport = "1" == freeform
                                        if (dragMode != null) cfg.overrideFreeformDragMode = dragMode.toInt()
                                    }
                                }
                            }

                            XmlPullParser.END_TAG -> {
                                if ("config" == tagName) {
                                    val pkg = currentPackage
                                    val cfg = currentConfig
                                    if (pkg != null && cfg != null) {
                                        if (!cfg.isEmpty()) {
                                            configMap[pkg] = cfg
                                        }
                                        currentPackage = null
                                        currentConfig = null
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 解析失败视为文件损坏或空，返回部分或空数据
            }
            tempFile.delete()
        }
        return configMap
    }

    // 保存配置：Map -> XML -> Cache -> System
    fun saveConfig(context: Context, configMap: MutableMap<String, AppConfig>): String {
        val tempFile = File(context.cacheDir, TEMP_FILE_NAME)
        val executor = EnhancedShellExecutor.getInstance()

        return try {
            // 1. 构建 XML 字符串
            val serializer = Xml.newSerializer()
            val writer = StringWriter()
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", true)
            serializer.text("\n")
            serializer.startTag(null, "configs")

            for ((pkg, cfg) in configMap) {
                if (cfg.isEmpty()) continue // 跳过空配置

                serializer.text("\n  ")
                serializer.startTag(null, "config")
                serializer.attribute(null, "packageName", pkg)

                serializer.text("\n    ")
                serializer.startTag(null, "user_persist")

                cfg.overrideSplitSupport?.let {
                    serializer.attribute(null, "overrideSplitSupport", if (it) "1" else "0")
                }
                cfg.overrideFreeformSupport?.let {
                    serializer.attribute(null, "overrideFreeformSupport", if (it) "1" else "0")
                }
                cfg.overrideFreeformDragMode?.let {
                    serializer.attribute(null, "overrideFreeformDragMode", it.toString())
                }

                serializer.endTag(null, "user_persist")
                serializer.text("\n  ")
                serializer.endTag(null, "config")
            }

            serializer.text("\n")
            serializer.endTag(null, "configs")
            serializer.endDocument()

            // 2. 写入临时文件
            FileUtils.writeStringToFile(tempFile, writer.toString())

            // 3. 移动回系统目录并设置权限
            // 注意：/data/system/zui/ 可能需要 mkdir，虽然通常它是存在的
            val cmd = "mkdir -p /data/system/zui/ && " +
                    "cp " + tempFile.absolutePath + " " + SYSTEM_FILE_PATH + " && " +
                    "chown 1000:1000 " + SYSTEM_FILE_PATH + " && " + // 关键：system 用户组
                    "chmod 660 " + SYSTEM_FILE_PATH // 关键：读写权限

            val result = executor.executeRootCommand(cmd)

            // 清理
            tempFile.delete()

            if (result.isSuccess) {
                "success"
            } else {
                context.getString(R.string.common_error_shell_command_failed, result.error)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Unknown error"
        }
    }

    // --- 业务逻辑辅助方法 ---

    // 获取当前开启了某项功能的包名列表
    fun getPackagesForMode(map: Map<String, AppConfig>, mode: Int): List<String> {
        val list = ArrayList<String>()
        for ((pkg, cfg) in map) {
            when (mode) {
                MODE_SPLIT_SCREEN ->
                    if (cfg.overrideSplitSupport == true) list.add(pkg)
                MODE_FREEFORM_FREE ->
                    if (cfg.overrideFreeformSupport == true && cfg.overrideFreeformDragMode == 1) {
                        list.add(pkg)
                    }
                MODE_FREEFORM_FIXED ->
                    if (cfg.overrideFreeformSupport == true && cfg.overrideFreeformDragMode == 0) {
                        list.add(pkg)
                    }
            }
        }
        return list
    }

    // 更新配置逻辑：根据用户选择的列表，更新 Map
    fun updateConfigForMode(map: MutableMap<String, AppConfig>, selectedPackages: List<String>, mode: Int) {
        // 1. 遍历现有的 Map，清理掉该模式下不再选中的包
        // 注意：为了避免并发修改异常，先收集要修改的 Key
        for ((pkg, cfg) in map) {
            // 如果该包不在新选中的列表中，且当前配置了该模式，则移除该配置
            if (!selectedPackages.contains(pkg)) {
                removeModeFromConfig(cfg, mode)
            }
        }

        // 2. 遍历选中的包，添加/更新配置
        for (pkg in selectedPackages) {
            val cfg = map.getOrPut(pkg) { AppConfig() }
            addModeToConfig(cfg, mode)
        }
    }

    private fun removeModeFromConfig(cfg: AppConfig, mode: Int) {
        when (mode) {
            MODE_SPLIT_SCREEN -> cfg.overrideSplitSupport = null
            MODE_FREEFORM_FREE -> {
                // 如果当前是自由模式，才移除。防止误伤固定模式
                if (cfg.overrideFreeformSupport == true && cfg.overrideFreeformDragMode == 1) {
                    cfg.overrideFreeformSupport = null
                    cfg.overrideFreeformDragMode = null
                }
            }
            MODE_FREEFORM_FIXED -> {
                // 如果当前是固定模式，才移除
                if (cfg.overrideFreeformSupport == true && cfg.overrideFreeformDragMode == 0) {
                    cfg.overrideFreeformSupport = null
                    cfg.overrideFreeformDragMode = null
                }
            }
        }
    }

    private fun addModeToConfig(cfg: AppConfig, mode: Int) {
        when (mode) {
            MODE_SPLIT_SCREEN -> cfg.overrideSplitSupport = true
            MODE_FREEFORM_FREE -> {
                cfg.overrideFreeformSupport = true
                cfg.overrideFreeformDragMode = 1 // 1 = Free
            }
            MODE_FREEFORM_FIXED -> {
                cfg.overrideFreeformSupport = true
                cfg.overrideFreeformDragMode = 0 // 0 = Fixed
            }
        }
    }

    companion object {
        private const val SYSTEM_FILE_PATH = "/data/system/zui/ov_common_persist_user_0.xml"
        private const val TEMP_FILE_NAME = "ov_config_temp.xml"

        // 模式定义
        const val MODE_SPLIT_SCREEN = 1
        const val MODE_FREEFORM_FREE = 2   // 自由小窗 (DragMode=1)
        const val MODE_FREEFORM_FIXED = 3  // 固定比例小窗 (DragMode=0)
    }
}
