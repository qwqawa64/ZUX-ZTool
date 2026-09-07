package com.qimian233.ztool.uninstall

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.qimian233.ztool.R
import com.qimian233.ztool.data.launcher.BatchUninstallRepository
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.utils.ModulePreferencesUtils
import kotlin.math.abs

/**
 * 批量卸载执行跳板：接收启动器编辑模式 Hook 分发过来的包名列表，
 * 只做鉴权和 Root shell 执行，本身无任何可视组件（除 Toast）。
 *
 * 鉴权（referrer 可被任意调用方伪造，令牌才是真正的防线）：
 * 1. Hook 在用户于 ZUI 确认框点击"卸载"时生成随机令牌，写入共享的
 *    `xposed_module_config` 并随 Intent 携带；
 * 2. 本页比对令牌，通过后立即作废（一次性），并校验 30 秒时效。
 * 拿不到令牌的外部调用方（即使伪造 referrer）只会收到失败 Toast。
 *
 * 窗口设为不聚焦、不可触摸，执行期间桌面保持可交互。
 * 执行结果只通过 Toast 汇报，完成后立即结束。
 */
class BatchUninstallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val referrer = referrer
        if (referrer != null && referrer.host != ScopeKeys.LAUNCHER.packageName) {
            toast(R.string.batch_uninstall_auth_failed)
            finish()
            return
        }
        val packages = resolvePackages(intent).map { it.trim() }
            .filter { PACKAGE_NAME_REGEX.matches(it) }
            .distinct()
            .take(MAX_SELECTED_COUNT)
        if (packages.isEmpty()) {
            toast(R.string.batch_uninstall_empty)
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
            if (!authenticate(intent)) {
                runOnUiThread {
                    toast(R.string.batch_uninstall_auth_failed)
                    finish()
                }
                return@Thread
            }
            if (!repository.checkRootAvailable()) {
                runOnUiThread {
                    toast(R.string.batch_uninstall_root_unavailable)
                    finish()
                }
                return@Thread
            }
            var successCount = 0
            var failureCount = 0
            for (packageName in packages) {
                if (repository.uninstallPackage(packageName).success) {
                    successCount++
                } else {
                    failureCount++
                }
            }
            runOnUiThread {
                toast(R.string.batch_uninstall_result, successCount, failureCount)
                finish()
            }
        }.start()
    }

    /**
     * 令牌校验。Hook 侧经 LSPosed 写入共享偏好存在同步延迟，
     * 读取侧短暂重试；通过后立即清空令牌防止重放。
     */
    private fun authenticate(intent: Intent?): Boolean {
        val provided = intent?.getStringExtra(EXTRA_TOKEN)
        if (provided.isNullOrEmpty()) return false
        val prefs = ModulePreferencesUtils(this)
        val key = PreferenceKeys.LAUNCHER_BATCH_UNINSTALL_TOKEN.name
        var expected = ""
        for (attempt in 0 until TOKEN_READ_RETRY) {
            expected = prefs.loadStringSetting(key, "")
            if (expected.isNotBlank()) break
            Thread.sleep(TOKEN_READ_RETRY_INTERVAL_MS)
        }
        if (expected.isBlank() || expected != provided) return false
        prefs.saveStringSetting(key, "")
        val issuedAt = expected.substringAfterLast('|', "").toLongOrNull() ?: return false
        return abs(System.currentTimeMillis() - issuedAt) <= TOKEN_MAX_AGE_MS
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
        const val EXTRA_PACKAGES = "ztool_extra_batch_uninstall_packages"
        const val EXTRA_TOKEN = "ztool_extra_batch_uninstall_token"

        /** 与启动器 ZuiEditModePanel.MAX_SELECTED_COUNT 保持一致的兜底上限。 */
        private const val MAX_SELECTED_COUNT = 24
        private const val TOKEN_MAX_AGE_MS = 30_000L
        private const val TOKEN_READ_RETRY = 6
        private const val TOKEN_READ_RETRY_INTERVAL_MS = 250L
        private val PACKAGE_NAME_REGEX = Regex("[A-Za-z0-9_.]+")
    }
}
