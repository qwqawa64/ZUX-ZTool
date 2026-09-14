package com.qimian233.ztool.search

import android.content.Context
import android.content.pm.PackageManager

/**
 * Applies device-dependent visibility to the static index. Phase 1 filters only on
 * installed packages (same posture as the Features tab); LSPosed scope state is not
 * consulted — out-of-scope entries stay listed so the user can still find the feature
 * and request the scope from the Features tab.
 */
object SearchIndexBuilder {

    fun visibleEntries(context: Context): List<SearchEntry> {
        val installed = try {
            context.packageManager
                .getInstalledApplications(0)
                .mapTo(mutableSetOf()) { it.packageName }
        } catch (_: Throwable) {
            // Package listing unavailable — prefer over-showing to hiding entries.
            return SearchIndex.all
        }
        return SearchIndex.all.filter { entry ->
            entry.requiresPackage == null || entry.requiresPackage in installed
        }
    }
}
