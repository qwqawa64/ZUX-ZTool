package com.qimian233.ztool.dexindex.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Triggers offline indexing when the module is installed/updated.
 *
 * Honors the same staleness rules as the home-entry check: a fresh index is left untouched, and
 * indexer output changes still require a `SCHEMA_VERSION` bump. The broadcast can be missed
 * (receiver is not direct-boot aware, process killed during boot); the home-entry check in
 * `HomeViewModel.checkDexIndexOnEntry` covers that case.
 */
class DexIndexReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_MY_PACKAGE_REPLACED && action != Intent.ACTION_PACKAGE_ADDED) return
        // MY_PACKAGE_REPLACED carries no data URI; it is only delivered to this package.
        val pkg = intent.data?.schemeSpecificPart ?: context.packageName
        if (pkg != context.packageName) return

        Log.i(TAG, "triggered by $action, checking the offline dex index")
        val pending = goAsync()
        Thread {
            try {
                val ctx = context.applicationContext
                val stale = DexIndexRegistry.indexers.filter {
                    DexIndexManager.needsReindex(ctx, it.scopePackage)
                }
                if (stale.isEmpty()) {
                    Log.i(TAG, "index is fresh, skipping")
                    return@Thread
                }
                Log.i(TAG, "stale scopes: ${stale.map { it.scopePackage }}")
                DexIndexManager.indexAll(ctx)
            } catch (t: Throwable) {
                Log.e(TAG, "background index failed", t)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "DexIndexReceiver"
    }
}
