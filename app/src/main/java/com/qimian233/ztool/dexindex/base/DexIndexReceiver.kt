package com.qimian233.ztool.dexindex.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Triggers offline indexing after the module is installed/updated.
 *
 * The broadcast can be missed (receiver is not direct-boot aware, process killed during boot);
 * the home-entry staleness check in `HomeViewModel.checkDexIndexOnEntry` covers that case.
 */
class DexIndexReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_MY_PACKAGE_REPLACED && action != Intent.ACTION_PACKAGE_ADDED) return
        // MY_PACKAGE_REPLACED carries no data URI; it is only delivered to this package.
        val pkg = intent.data?.schemeSpecificPart ?: context.packageName
        if (pkg != context.packageName) return

        Log.i(TAG, "triggered by $action, starting offline dex index")
        val pending = goAsync()
        Thread {
            try {
                DexIndexManager.indexAll(context.applicationContext)
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
