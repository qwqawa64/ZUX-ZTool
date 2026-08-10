package com.qimian233.ztool.dexindex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 模块安装/更新后触发离线索引。
 *
 * 注意：Android 8+ 上 `PACKAGE_ADDED` 静态注册可能收不到，
 * 首次安装场景由 [com.qimian233.ztool.ZToolApplication] 启动指纹检查兜底。
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
