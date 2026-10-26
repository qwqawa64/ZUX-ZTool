package com.qimian233.ztool.dexindex.base

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.security.MessageDigest

/**
 * Offline index executor (runs in the module app process).
 *
 * For each scope: resolve the target apk path (including splits) → build a
 * DexKitBridge → run the corresponding Indexer → atomically write
 * `files/dex_index/<scopePackage>.json` (including the apk fingerprint).
 */
object DexIndexManager {

    private const val TAG = "DexIndexManager"

    /** Serializes index execution to avoid concurrent writes to the same file by receiver/startup check/manual refresh. */
    private val lock = Any()

    /** Global index progress (scope level). Shared by all trigger sources for the UI progress dialog. */
    private val _progress = MutableStateFlow(DexIndexProgress())
    val progress: StateFlow<DexIndexProgress> = _progress.asStateFlow()

    /**
     * Index all scopes. Returns scopePackage → success.
     * Reports scope-level progress via [progress] while scanning, and updates
     * the result notification (fallback) when finished.
     */
    fun indexAll(context: Context): Map<String, Boolean> {
        var results: Map<String, Boolean> = emptyMap()
        try {
            results = runIndexing(context, DexIndexRegistry.indexers)
        } finally {
            DexIndexNotifier.finish(context, results)
        }
        return results
    }

    /**
     * Whether this scope needs reindexing: an unresolvable target is never stale; otherwise a
     * missing/corrupt file, a schemaVersion or system-fingerprint mismatch, or a changed apk
     * fingerprint (path + update time + signature) requires a rebuild.
     */
    fun needsReindex(context: Context, scopePackage: String): Boolean {
        // Resolve the target first: a scope that is not installed has nothing to index, and
        // treating it as stale would rescan every scope on every launch.
        val current = currentFingerprint(context, scopePackage) ?: return false
        val stored = readStoredIndex(context, scopePackage) ?: return true
        if (stored.schemaVersion != DexIndexConstants.SCHEMA_VERSION) return true
        if (stored.systemFingerprint != Build.FINGERPRINT) return true
        return stored.fingerprint != current
    }

    /** Timestamp (ms) of the last successful index; returns 0 if never indexed. */
    fun lastIndexedAt(context: Context, scopePackage: String): Long {
        return try {
            val file = File(indexDir(context), DexIndexConstants.fileName(scopePackage))
            if (!file.exists()) return 0L
            JsonParser.parseString(file.readText()).asJsonObject
                .get(DexIndexConstants.JSON_GENERATED_AT)?.asLong ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    /** Directory holding index files: the Remote Files root is the module filesDir and does not support subdirectories, so files go at the root. */
    fun indexDir(context: Context): File = context.filesDir

    /**
     * Runs a batch of indexers serially and reports scope-level progress.
     * [DexIndexProgress.running] is reset in finally regardless of outcome.
     */
    private fun runIndexing(context: Context, indexers: List<DexIndexer>): Map<String, Boolean> {
        val total = indexers.size
        _progress.value = DexIndexProgress(running = true, current = 0, total = total)
        return try {
            synchronized(lock) {
                indexers.mapIndexed { index, indexer ->
                    _progress.value = DexIndexProgress(
                        running = true,
                        current = index + 1,
                        total = total,
                        currentScope = indexer.scopePackage
                    )
                    indexer.scopePackage to indexScope(context, indexer)
                }.toMap()
            }
        } finally {
            _progress.value = DexIndexProgress(running = false, current = total, total = total)
        }
    }

    private fun indexScope(context: Context, indexer: DexIndexer): Boolean {
        synchronized(lock) {
            val bridge = openBridge(context, indexer.scopePackage) ?: return false
            return try {
                val modules = indexer.index(bridge, context)
                writeIndexFile(context, indexer.scopePackage, modules)
                Log.i(TAG, "indexed ${indexer.scopePackage}")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "index failed for ${indexer.scopePackage}", t)
                false
            } finally {
                try {
                    bridge.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun openBridge(context: Context, scopePackage: String): DexKitBridge? {
        return try {
            // Idempotent loading of the dexkit native library
            System.loadLibrary("dexkit")
            val ai = context.packageManager.getApplicationInfo(scopePackage, 0)
            val splits = ai.splitSourceDirs ?: emptyArray()
            if (splits.isEmpty()) {
                DexKitBridge.create(ai.sourceDir)
            } else {
                // Split APK: load everything as dex byte arrays (create only supports a single path or byte arrays).
                // Note: readBytes on the whole package has a memory spike (OOM risk); only triggered in the split
                // scenario; the regular single-base case takes the branch above.
                val apkBytes = (listOf(ai.sourceDir) + splits).map { File(it).readBytes() }
                DexKitBridge.create(apkBytes.toTypedArray())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "open bridge failed for $scopePackage", t)
            null
        }
    }

    private fun writeIndexFile(context: Context, scopePackage: String, modules: JsonObject) {
        val dir = indexDir(context).apply { mkdirs() }
        val name = DexIndexConstants.fileName(scopePackage)
        val tmp = File(dir, ".tmp-$name")
        val target = File(dir, name)

        val root = JsonObject()
        root.addProperty(DexIndexConstants.JSON_SCHEMA_VERSION, DexIndexConstants.SCHEMA_VERSION)
        root.addProperty(DexIndexConstants.JSON_GENERATED_AT, System.currentTimeMillis())
        root.addProperty(DexIndexConstants.JSON_SYSTEM_FINGERPRINT, Build.FINGERPRINT)
        currentFingerprint(context, scopePackage)?.let { fp ->
            val apk = JsonObject()
            apk.addProperty(DexIndexConstants.JSON_PATH, fp.path)
            apk.addProperty(DexIndexConstants.JSON_LAST_UPDATE_TIME, fp.lastUpdateTime)
            apk.addProperty(DexIndexConstants.JSON_SIGNATURE_HASH, fp.signatureHash)
            root.add(DexIndexConstants.JSON_APK, apk)
        }
        root.add(DexIndexConstants.JSON_MODULES, modules)

        // Atomic write: tmp first then rename, so hook processes never read a half-written JSON
        tmp.writeText(root.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(root.toString())
            tmp.delete()
        }
    }

    /** Reads the stored index's staleness metadata in one parse; null when missing/corrupt. */
    private fun readStoredIndex(context: Context, scopePackage: String): StoredIndex? {
        return try {
            val file = File(indexDir(context), DexIndexConstants.fileName(scopePackage))
            if (!file.exists()) return null
            val root = JsonParser.parseString(file.readText()).asJsonObject
            StoredIndex(
                schemaVersion = root.get(DexIndexConstants.JSON_SCHEMA_VERSION)?.asInt,
                systemFingerprint = root.get(DexIndexConstants.JSON_SYSTEM_FINGERPRINT)
                    ?.takeIf { !it.isJsonNull }
                    ?.asString,
                fingerprint = root.getAsJsonObject(DexIndexConstants.JSON_APK)?.let { apk ->
                    Fingerprint(
                        path = apk.get(DexIndexConstants.JSON_PATH).asString,
                        lastUpdateTime = apk.get(DexIndexConstants.JSON_LAST_UPDATE_TIME).asLong,
                        signatureHash = apk.get(DexIndexConstants.JSON_SIGNATURE_HASH).asString,
                    )
                },
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun currentFingerprint(context: Context, scopePackage: String): Fingerprint? {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(scopePackage, 0)
            val pi: PackageInfo
            val sigBytes: ByteArray?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+: GET_SIGNING_CERTIFICATES + signingInfo (GET_SIGNATURES is deprecated)
                pi = pm.getPackageInfo(scopePackage, PackageManager.GET_SIGNING_CERTIFICATES)
                sigBytes = pi.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                pi = pm.getPackageInfo(scopePackage, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                sigBytes = pi.signatures?.firstOrNull()?.toByteArray()
            }
            val sigHash = sigBytes?.let { sha256Hex(it) } ?: ""
            Fingerprint(ai.sourceDir, pi.lastUpdateTime, sigHash)
        } catch (t: Throwable) {
            Log.w(TAG, "fingerprint failed for $scopePackage", t)
            null
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private data class Fingerprint(
        val path: String,
        val lastUpdateTime: Long,
        val signatureHash: String,
    )

    private data class StoredIndex(
        val schemaVersion: Int?,
        val systemFingerprint: String?,
        val fingerprint: Fingerprint?,
    )
}

/**
 * DexKit index progress (scope level).
 *
 * - [running]: whether an index task is executing;
 * - [current]/[total]: current scope index / total scopes;
 * - [currentScope]: package name of the scope currently being indexed.
 */
data class DexIndexProgress(
    val running: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentScope: String = "",
)
