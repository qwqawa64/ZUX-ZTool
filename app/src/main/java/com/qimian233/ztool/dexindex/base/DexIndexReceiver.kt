package com.qimian233.ztool.dexindex.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Triggers offline indexing after the module is installed/updated.
 *
 * Note: on Android 8+ a statically registered `PACKAGE_ADDED` receiver may not
 * receive the broadcast; the first-install scenario is covered by the startup
 * fingerprint check in [com.qimian233.ztool.ZToolApplication].
 */
class DexIndexReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_MY_PACKAGE_REPLACED && action != Intent.ACTION_PACKAGE_ADDED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
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
