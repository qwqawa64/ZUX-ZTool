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

    // Config model corresponding to <user_persist>
    class AppConfig {
        var overrideSplitSupport: Boolean? = null     // overrideSplitSupport
        var overrideFreeformSupport: Boolean? = null  // overrideFreeformSupport
        var overrideFreeformDragMode: Int? = null     // overrideFreeformDragMode (1=Free, 0=Fixed)

        // Whether all config fields are empty (if so, the entry should be deleted)
        fun isEmpty(): Boolean {
            return overrideSplitSupport == null && overrideFreeformSupport == null && overrideFreeformDragMode == null
        }
    }

    // Load config: System -> Cache -> Map
    fun loadConfig(context: Context): MutableMap<String, AppConfig> {
        val configMap = HashMap<String, AppConfig>()
        val executor = EnhancedShellExecutor.getInstance()
        val tempFile = File(context.cacheDir, TEMP_FILE_NAME)

        // 1. Try to copy the system file to the cache
        // If the file does not exist, return an empty Map directly
        val checkRes = executor.executeRootCommand("ls " + SYSTEM_FILE_PATH)
        if (!checkRes.isSuccess) {
            return configMap // file missing, return empty
        }

        executor.executeRootCommand("cp " + SYSTEM_FILE_PATH + " " + tempFile.absolutePath)
        executor.executeRootCommand("chmod 644 " + tempFile.absolutePath) // make sure the app can read it

        // 2. Parse the XML
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
                // Treat parse failure as a corrupted or empty file; return partial or empty data
            }
            tempFile.delete()
        }
        return configMap
    }

    // Save config: Map -> XML -> Cache -> System
    fun saveConfig(context: Context, configMap: MutableMap<String, AppConfig>): String {
        val tempFile = File(context.cacheDir, TEMP_FILE_NAME)
        val executor = EnhancedShellExecutor.getInstance()

        return try {
            // 1. Build the XML string
            val serializer = Xml.newSerializer()
            val writer = StringWriter()
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", true)
            serializer.text("\n")
            serializer.startTag(null, "configs")

            for ((pkg, cfg) in configMap) {
                if (cfg.isEmpty()) continue // skip empty configs

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

            // 2. Write the temp file
            FileUtils.writeStringToFile(tempFile, writer.toString())

            // 3. Move back to the system directory and set permissions
            // Note: /data/system/zui/ may need mkdir, although it usually exists
            val cmd = "mkdir -p /data/system/zui/ && " +
                    "cp " + tempFile.absolutePath + " " + SYSTEM_FILE_PATH + " && " +
                    "chown 1000:1000 " + SYSTEM_FILE_PATH + " && " + // critical: system user group
                    "chmod 660 " + SYSTEM_FILE_PATH // critical: read/write permissions

            val result = executor.executeRootCommand(cmd)

            // Cleanup
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

    // --- Business logic helpers ---

    // Get the list of package names that have a given feature enabled
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

    // Update config logic: update the Map according to the user's selected list
    fun updateConfigForMode(map: MutableMap<String, AppConfig>, selectedPackages: List<String>, mode: Int) {
        // 1. Iterate the existing Map and clear packages no longer selected for this mode.
        // Note: collect keys to modify first to avoid concurrent modification exceptions.
        for ((pkg, cfg) in map) {
            // If the package is not in the newly selected list and currently has this mode configured, remove that config
            if (!selectedPackages.contains(pkg)) {
                removeModeFromConfig(cfg, mode)
            }
        }

        // 2. Iterate the selected packages and add/update their configs
        for (pkg in selectedPackages) {
            val cfg = map.getOrPut(pkg) { AppConfig() }
            addModeToConfig(cfg, mode)
        }
    }

    private fun removeModeFromConfig(cfg: AppConfig, mode: Int) {
        when (mode) {
            MODE_SPLIT_SCREEN -> cfg.overrideSplitSupport = null
            MODE_FREEFORM_FREE -> {
                // Only remove if currently in free mode; avoid touching fixed mode
                if (cfg.overrideFreeformSupport == true && cfg.overrideFreeformDragMode == 1) {
                    cfg.overrideFreeformSupport = null
                    cfg.overrideFreeformDragMode = null
                }
            }
            MODE_FREEFORM_FIXED -> {
                // Only remove if currently in fixed mode
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

        // Mode definitions
        const val MODE_SPLIT_SCREEN = 1
        const val MODE_FREEFORM_FREE = 2   // free-form free window (DragMode=1)
        const val MODE_FREEFORM_FIXED = 3  // fixed-ratio free window (DragMode=0)
    }
}
