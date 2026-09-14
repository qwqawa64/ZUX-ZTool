package com.qimian233.ztool.search

import android.util.Log
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry

/**
 * Debug-only consistency check between the static search index and the rows actually
 * rendered on a screen. Catches index drift (an indexed row whose key is missing on
 * screen, or a row keyed but never registered in the index) at development time.
 * Release builds never call this.
 */
object SearchIndexAudit {

    private const val TAG = "SearchIndexAudit"
    /** Keys with these prefixes are intentional placeholders, exempt from the audit. */
    private val EXEMPT_PREFIXES = arrayOf("deco_", "dyn_")

    /**
     * Compares the index entries bound to [route] against [renderedKeys] captured from
     * the screen's [HighlightAnchorRegistry] after first composition.
     */
    fun auditRoute(route: String, registry: HighlightAnchorRegistry) {
        val renderedKeys = registry.snapshotKeys()
        val indexedIds = SearchIndex.all
            .filter { it.route == route && !it.isFeatureCard }
            .map { it.id }
            .toSet()
        val renderedNonExempt = renderedKeys
            .filterNot { key -> EXEMPT_PREFIXES.any { key.startsWith(it) } }
            .toSet()

        val missingOnScreen = indexedIds - renderedNonExempt
        val unindexed = renderedNonExempt - indexedIds

        if (missingOnScreen.isEmpty() && unindexed.isEmpty()) {
            Log.i(TAG, "Route '$route': index and rendered keys are consistent")
            return
        }
        if (missingOnScreen.isNotEmpty()) {
            Log.w(
                TAG,
                "Route '$route': indexed but NOT rendered (conditional or missing key): " +
                    missingOnScreen.sorted()
            )
        }
        if (unindexed.isNotEmpty()) {
            Log.w(
                TAG,
                "Route '$route': rendered but NOT in the search index: " +
                    unindexed.sorted()
            )
        }
    }
}
