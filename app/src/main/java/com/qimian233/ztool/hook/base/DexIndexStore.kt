package com.qimian233.ztool.hook.base

import android.os.ParcelFileDescriptor
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook 侧离线索引读取器（在目标进程内运行）。
 *
 * 通过 libxposed Remote Files（[XposedInterface.openRemoteFile]）读取模块私有
 * `filesDir` 根目录下的 `<scopePackage>.json`，框架以特权代读，无需 chmod。
 *
 * 使用约定：
 * - 在 XposedInterface.PackageLoadedParam 回调（非 hook lambda）阶段调用；
 * - 读取失败（老框架/未索引/文件缺失）返回 null，调用方回退硬编码；
 * - 结果按进程缓存，每进程只读一次。
 */
object DexIndexStore {

    /** 读取失败的哨兵（ConcurrentHashMap 不允许 null 值，用单例空对象表示"已尝试但失败"）。 */
    private val MISSING = JsonObject()

    private val cache = ConcurrentHashMap<String, JsonObject>()

    /**
     * 取某作用域的整个索引 JSON（含 modules 分组），失败返回 null。
     */
    fun lookup(xposed: XposedInterface, scopePackage: String): JsonObject? {
        val cached = cache[scopePackage]
        if (cached === MISSING) return null
        if (cached != null) return cached
        val result = try {
            val pfd = xposed.openRemoteFile(DexIndexConstants.fileName(scopePackage))
            pfd.use { p ->
                val text = ParcelFileDescriptor.AutoCloseInputStream(p)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                JsonParser.parseString(text).asJsonObject
            }
        } catch (_: Throwable) {
            null
        }
        cache[scopePackage] = result ?: MISSING
        return result
    }

    /**
     * 取某模块的某个字段值。任何失败/缺失返回 null。
     */
    fun string(
        xposed: XposedInterface,
        scopePackage: String,
        moduleKey: String,
        fieldKey: String,
    ): String? {
        return try {
            lookup(xposed, scopePackage)
                ?.getAsJsonObject(DexIndexConstants.JSON_MODULES)
                ?.getAsJsonObject(moduleKey)
                ?.get(fieldKey)
                ?.takeIf { !it.isJsonNull }
                ?.asString
        } catch (_: Throwable) {
            null
        }
    }
}
