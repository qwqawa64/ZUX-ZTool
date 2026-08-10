package com.qimian233.ztool.dexindex

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

    /**
     * 全量索引所有作用域。返回 scopePackage → 是否成功。
     * 扫描期间发送前台进度通知，结束后更新结果通知。
     */
    fun indexAll(context: Context): Map<String, Boolean> {
        DexIndexNotifier.start(context)
        var results: Map<String, Boolean> = emptyMap()
        try {
            results = synchronized(lock) {
                DexIndexRegistry.indexers.associate { it.scopePackage to indexScope(context, it) }
            }
        } finally {
            DexIndexNotifier.finish(context, results)
        }
        return results
    }

    /**
     * 仅索引指纹过期（含首次无文件）的作用域。返回 scopePackage → 是否成功。
     * 无过期作用域时静默返回，不发通知。
     */
    fun indexAllIfStale(context: Context): Map<String, Boolean> {
        val stale = DexIndexRegistry.indexers.filter { needsReindex(context, it.scopePackage) }
        if (stale.isEmpty()) return emptyMap()
        DexIndexNotifier.start(context)
        var results: Map<String, Boolean> = emptyMap()
        try {
            results = synchronized(lock) {
                stale.associate { it.scopePackage to indexScope(context, it) }
            }
        } finally {
            DexIndexNotifier.finish(context, results)
        }
        return results
    }

    /**
     * 该作用域是否需要重新索引：现有文件缺失/损坏，或 apk 指纹（路径+更新+签名）变化。
     */
    fun needsReindex(context: Context, scopePackage: String): Boolean {
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
        } catch (t: Throwable) {
            0L
        }
    }

    /** 索引文件所在目录：Remote Files 根即模块 filesDir，不支持子目录，故直接放根目录。 */
    fun indexDir(context: Context): File = context.filesDir

    // ── 内部实现 ────────────────────────────────────────────────────

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
        } catch (t: Throwable) {
            null
        }
    }

    private fun currentFingerprint(context: Context, scopePackage: String): Fingerprint? {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(scopePackage, 0)
            val pi = pm.getPackageInfo(scopePackage, PackageManager.GET_SIGNATURES)
            val sigHash = pi.signatures?.firstOrNull()?.toByteArray()?.let { sha256Hex(it) } ?: ""
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
