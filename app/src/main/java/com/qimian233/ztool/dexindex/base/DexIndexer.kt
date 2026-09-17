package com.qimian233.ztool.dexindex.base

import android.content.Context
import com.google.gson.JsonObject
import org.luckypray.dexkit.DexKitBridge

/**
 * Scope-level offline indexer.
 *
 * One implementation per target scope (package), responsible for
 * precomputing method/field names of all DexKit-based Hook modules for that
 * package. **Must not depend on libxposed** (runs in the module app process).
 *
 * Implementation conventions:
 * - Each module's query inside [index] gets its own try-catch; a single
 *   failure must not affect the others;
 * - Output is a JsonObject grouped by `DexIndexConstants.ModuleKeys`, with
 *   field keys from `DexIndexConstants.Keys`;
 * - **Do not write fallback values**: if a query fails, omit that key; the
 *   hook side falls back to hardcoded names.
 */
interface DexIndexer {

    /** Target scope package name (references `ScopeKeys.CONSTANT.packageName`). */
    val scopePackage: String

    /**
     * Runs all queries for this scope against the given bridge.
     * Returns JSON of the form `{ "<moduleKey>": { "<fieldKey>": "<value>" } }` —
     * **only the modules map itself**; the root wrapper (schemaVersion / apk
     * fingerprint etc.) is handled by [DexIndexManager] to avoid double nesting.
     */
    fun index(bridge: DexKitBridge, context: Context): JsonObject
}
