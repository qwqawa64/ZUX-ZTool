package com.qimian233.ztool.hook.base

import android.os.ParcelFileDescriptor
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook-side offline index reader (runs in the target process).
 *
 * Reads `<scopePackage>.json` from the root of the module's private `filesDir`
 * via libxposed Remote Files ([XposedInterface.openRemoteFile]); the framework
 * reads it with privileges, no chmod needed.
 *
 * Usage conventions:
 * - Call during the XposedInterface.PackageLoadedParam callback (not inside hook lambdas);
 * - Returns null on read failure (old framework / not indexed / file missing); callers fall back to hardcoded values;
 * - Results are cached per process, read once per process.
 */
object DexIndexStore {

    /** Sentinel for failed reads (ConcurrentHashMap forbids null values; a singleton empty object means "attempted but failed"). */
    private val MISSING = JsonObject()

    private val cache = ConcurrentHashMap<String, JsonObject>()

    /**
     * Returns the whole index JSON for a scope (including the modules grouping), or null on failure.
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
     * Returns a module's field value. Returns null on any failure/missing entry.
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
