package com.qimian233.ztool.data.zuiui

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** One APK's verdict, tied to the fingerprint it was reached on. */
data class ZuiComponentVerdict(
    val containsZuiComponents: Boolean,
    val sourceDir: String,
    val lastUpdateTime: Long,
    val checkedAt: Long
)

/**
 * Persisted scan state: whether the user ever triggered a scan, and the per-package verdicts.
 */
data class ZuiComponentScanSnapshot(
    val firstScanStarted: Boolean = false,
    val verdicts: Map<String, ZuiComponentVerdict> = emptyMap()
)

/**
 * Reads and writes the ZUI component scan cache.
 *
 * Lives in `noBackupFilesDir`: derived data that a config backup must not carry, and that
 * no hook process reads (unlike the DexKit index in `filesDir`). A version bump discards
 * every verdict, which is required whenever the scanned package set changes.
 */
class ZuiComponentScanStore(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun load(): ZuiComponentScanSnapshot {
        val text = runCatching { file.readText() }.getOrNull() ?: return ZuiComponentScanSnapshot()
        return runCatching {
            val root = JsonParser.parseString(text).asJsonObject
            if (root.get(JSON_SCHEMA)?.asInt != SCHEMA_VERSION) return ZuiComponentScanSnapshot()
            val verdicts = buildMap {
                root.getAsJsonObject(JSON_VERDICTS)?.entrySet()?.forEach { (pkg, element) ->
                    val obj = element.asJsonObject
                    put(
                        pkg,
                        ZuiComponentVerdict(
                            containsZuiComponents = obj.get(JSON_CONTAINS)?.asBoolean ?: false,
                            sourceDir = obj.get(JSON_SOURCE_DIR)?.asString.orEmpty(),
                            lastUpdateTime = obj.get(JSON_LAST_UPDATE)?.asLong ?: 0L,
                            checkedAt = obj.get(JSON_CHECKED_AT)?.asLong ?: 0L
                        )
                    )
                }
            }
            ZuiComponentScanSnapshot(
                firstScanStarted = root.get(JSON_GATE)?.asBoolean ?: false,
                verdicts = verdicts
            )
        }.getOrElse { ZuiComponentScanSnapshot() }
    }

    fun save(snapshot: ZuiComponentScanSnapshot) {
        runCatching {
            val root = JsonObject().apply {
                addProperty(JSON_SCHEMA, SCHEMA_VERSION)
                addProperty(JSON_GATE, snapshot.firstScanStarted)
                add(JSON_VERDICTS, JsonObject().apply {
                    snapshot.verdicts.forEach { (pkg, verdict) ->
                        add(pkg, JsonObject().apply {
                            addProperty(JSON_CONTAINS, verdict.containsZuiComponents)
                            addProperty(JSON_SOURCE_DIR, verdict.sourceDir)
                            addProperty(JSON_LAST_UPDATE, verdict.lastUpdateTime)
                            addProperty(JSON_CHECKED_AT, verdict.checkedAt)
                        })
                    }
                })
            }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "zui_component_scan.json"
        private const val JSON_SCHEMA = "schemaVersion"
        private const val JSON_GATE = "firstScanStarted"
        private const val JSON_VERDICTS = "verdicts"
        private const val JSON_CONTAINS = "containsZuiComponents"
        private const val JSON_SOURCE_DIR = "sourceDir"
        private const val JSON_LAST_UPDATE = "lastUpdateTime"
        private const val JSON_CHECKED_AT = "checkedAt"

        /** Bump when the scanned package set or the scanner's matching rules change. */
        const val SCHEMA_VERSION = 1
    }
}
