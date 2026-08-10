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
 * 离线索引执行器（模块 app 进程内运行）。
 *
 * 对每个作用域：取目标 apk 路径（含 split）→ 建 DexKitBridge → 跑对应
 * Indexer → 原子写 `files/dex_index/<scopePackage>.json`（含 apk 指纹）。
 */
object DexIndexManager {

    private const val TAG = "DexIndexManager"

    /** 串行化索引执行，避免 Receiver/启动检查/手动刷新并发写同一文件。 */
    private val lock = Any()

    /** 全局索引进度（scope 级）。所有触发源共享，供 UI 进度 Dialog 展示。 */
    private val _progress = MutableStateFlow(DexIndexProgress())
    val progress: StateFlow<DexIndexProgress> = _progress.asStateFlow()

    /**
     * 全量索引所有作用域。返回 scopePackage → 是否成功。
     * 扫描期间经 [progress] 上报 scope 级进度，结束后更新结果通知（兜底）。
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
     * 该作用域是否需要重新索引：schemaVersion 不匹配（结构升级）、
     * 现有文件缺失/损坏，或 apk 指纹（路径+更新+签名）变化。
     */
    fun needsReindex(context: Context, scopePackage: String): Boolean {
        val schema = readStoredSchemaVersion(context, scopePackage)
        if (schema == null || schema != DexIndexConstants.SCHEMA_VERSION) return true
        val target = readStoredFingerprint(context, scopePackage) ?: return true
        val current = currentFingerprint(context, scopePackage) ?: return false
        return target != current
    }

    /** 最近一次成功索引的时间戳（毫秒），无索引返回 0。 */
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

    /** 索引文件所在目录：Remote Files 根即模块 filesDir，不支持子目录，故直接放根目录。 */
    fun indexDir(context: Context): File = context.filesDir

    // ── 内部实现 ────────────────────────────────────────────────────

    /**
     * 串行执行一批索引器并上报 scope 级进度。
     * 无论成败都会在 finally 中将 [DexIndexProgress.running] 复位。
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
            // dexkit native 库幂等加载
            System.loadLibrary("dexkit")
            val ai = context.packageManager.getApplicationInfo(scopePackage, 0)
            val splits = ai.splitSourceDirs ?: emptyArray()
            if (splits.isEmpty()) {
                DexKitBridge.create(ai.sourceDir)
            } else {
                // split apk：以 dex 字节数组方式整体加载（create 仅支持单路径或字节数组）。
                // 注意：整包 readBytes 有内存峰值（OOM 风险），仅 split 场景触发；常规单 base 走上一分支。
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
        currentFingerprint(context, scopePackage)?.let { fp ->
            val apk = JsonObject()
            apk.addProperty(DexIndexConstants.JSON_PATH, fp.path)
            apk.addProperty(DexIndexConstants.JSON_LAST_UPDATE_TIME, fp.lastUpdateTime)
            apk.addProperty(DexIndexConstants.JSON_SIGNATURE_HASH, fp.signatureHash)
            root.add(DexIndexConstants.JSON_APK, apk)
        }
        root.add(DexIndexConstants.JSON_MODULES, modules)

        // 原子写：先 tmp 后 rename，避免 hook 进程读到半截 JSON
        tmp.writeText(root.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(root.toString())
            tmp.delete()
        }
    }

    /** 读取现有文件的 schemaVersion，缺失/损坏返回 null。 */
    private fun readStoredSchemaVersion(context: Context, scopePackage: String): Int? {
        return try {
            val file = File(indexDir(context), DexIndexConstants.fileName(scopePackage))
            if (!file.exists()) return null
            JsonParser.parseString(file.readText()).asJsonObject
                .get(DexIndexConstants.JSON_SCHEMA_VERSION)?.asInt
        } catch (_: Throwable) {
            null
        }
    }

    private fun readStoredFingerprint(context: Context, scopePackage: String): Fingerprint? {
        return try {
            val file = File(indexDir(context), DexIndexConstants.fileName(scopePackage))
            if (!file.exists()) return null
            val apk = JsonParser.parseString(file.readText()).asJsonObject
                .getAsJsonObject(DexIndexConstants.JSON_APK) ?: return null
            Fingerprint(
                path = apk.get(DexIndexConstants.JSON_PATH).asString,
                lastUpdateTime = apk.get(DexIndexConstants.JSON_LAST_UPDATE_TIME).asLong,
                signatureHash = apk.get(DexIndexConstants.JSON_SIGNATURE_HASH).asString,
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
                // API 28+：GET_SIGNING_CERTIFICATES + signingInfo（GET_SIGNATURES 已废弃）
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
}

/**
 * DexKit 索引进度（scope 级）。
 *
 * - [running]：是否有索引任务在执行；
 * - [current]/[total]：当前第几个作用域 / 共几个作用域；
 * - [currentScope]：正在索引的作用域包名。
 */
data class DexIndexProgress(
    val running: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentScope: String = "",
)
