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
 * 批量卸载执行跳板：接收启动器编辑模式 Hook 分发过来的包名列表，
 * 只做鉴权和 Root shell 执行，本身无任何可视组件（除 Toast）。
 *
 * 鉴权：Hook 用 startActivityForResult 启动本页，系统会把真实调用方
 * 写入 callingPackage（由 Binder 层决定，调用方无法伪造，伪造 referrer
 * 的 extra 对其无效）。callingPackage 不是启动器（adb shell / 普通应用
 * 的 startActivity 均不满足）一律拒绝。
 *
 * 窗口设为不聚焦、不可触摸，执行期间桌面保持可交互。
 * 执行结果只通过 Toast 汇报，完成后立即结束。
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

        // 执行期间不拦截桌面交互
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

    /** Hook 侧写 ArrayList；adb 调试（am start --esa）写入的是 String[]，一并兼容。 */
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

        /** 与启动器 ZuiEditModePanel.MAX_SELECTED_COUNT 保持一致的兜底上限。 */
        private const val MAX_SELECTED_COUNT = 24
        private val PACKAGE_NAME_REGEX = Regex("[A-Za-z0-9_.]+")
    }
}
