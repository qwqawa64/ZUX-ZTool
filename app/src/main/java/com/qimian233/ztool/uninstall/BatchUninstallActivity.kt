package com.qimian233.ztool.uninstall

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import com.qimian233.ztool.R
import com.qimian233.ztool.data.launcher.BatchUninstallRepository
import com.qimian233.ztool.data.keys.ScopeKeys

/**
 * Batch uninstall trampoline: receives the package list dispatched by the
 * launcher edit-mode Hook. Only performs authentication and root shell
 * execution; has no visible components itself (except Toasts).
 *
 * Authentication: the Hook starts this activity with startActivityForResult,
 * so the system writes the real caller into callingPackage (decided by the
 * Binder layer; callers cannot forge it, and a forged referrer extra has no
 * effect). Any callingPackage that is not the launcher (adb shell or a normal
 * app's startActivity both fail this) is rejected outright.
 *
 * The window is set to non-focusable and non-touchable so the launcher stays
 * interactive during execution. Results are reported only via Toast, and the
 * activity finishes immediately afterwards.
 */
class BatchUninstallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val proof = IntentCompat.getParcelableExtra(
            intent, EXTRA_PROOF, PendingIntent::class.java
        )
        Log.i(TAG, "auth check: creatorPackage=${proof?.creatorPackage} referrer=$referrer")
        if (proof?.creatorPackage != ScopeKeys.LAUNCHER.packageName) {
            toast(R.string.page_uninstall_auth_failed)
            finish()
            return
        }
        val packages = resolvePackages(intent).map { it.trim() }
            .filter { PACKAGE_NAME_REGEX.matches(it) }
            .distinct()
            .take(MAX_SELECTED_COUNT)
        if (packages.isEmpty()) {
            toast(R.string.page_uninstall_empty)
            finish()
            return
        }

        // Do not block launcher interaction during execution
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )

        val repository = BatchUninstallRepository()
        Thread {
            val rootCheck = repository.checkRootAccess()
            Log.i(TAG, "root check: success=${rootCheck.first} output=${rootCheck.second}")
            if (!rootCheck.first) {
                runOnUiThread {
                    toast(R.string.page_uninstall_root_unavailable)
                    finish()
                }
                return@Thread
            }
            var successCount = 0
            var failureCount = 0
            for (packageName in packages) {
                val result = repository.uninstallPackage(packageName)
                Log.i(TAG, "uninstall $packageName -> success=${result.success} message=${result.message}")
                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                }
            }
            runOnUiThread {
                toast(R.string.page_uninstall_result, successCount, failureCount)
                finish()
            }
        }.start()
    }

    private fun toast(resId: Int, vararg args: Any) {
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_LONG).show()
    }

    /** The Hook side writes an ArrayList; adb debugging (am start --esa) writes a String[], also accepted. */
    private fun resolvePackages(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        return intent.getStringArrayListExtra(EXTRA_PACKAGES)
            ?: intent.getStringExtra(EXTRA_PACKAGES)?.let { arrayListOf(it) }
            ?: intent.getStringArrayExtra(EXTRA_PACKAGES)?.toList()
            ?: emptyList()
    }

    companion object {
        private const val TAG = "BatchUninstall"
        const val EXTRA_PACKAGES = "ztool_extra_batch_uninstall_packages"
        const val EXTRA_PROOF = "ztool_extra_batch_uninstall_proof"

        /** Fallback cap, kept consistent with the launcher's ZuiEditModePanel.MAX_SELECTED_COUNT. */
        private const val MAX_SELECTED_COUNT = 24
        private val PACKAGE_NAME_REGEX = Regex("[A-Za-z0-9_.]+")
    }
}
