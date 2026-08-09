package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.WeakHashMap

/**
 * 状态栏网速指示器"隐藏慢速"Hook。
 *
 * 功能:当网速低于阈值时将 [com.android.systemui.zui.NetworkSpeedView] 隐藏
 * (View.GONE),速度恢复后再显示。
 *
 * 与既有 3 个网速 Hook 的兼容性设计:
 * - 速度获取:用 [android.net.TrafficStats] 两次刷新间的差分自行计算,不依赖任何
 *   其他 Hook。因此与 `SystemUINetworkSpeeddoublelayerHook`(它拦截内部 Handler 的
 *   what==10/what==1 并 `return null` 切断 hook 链)互不干扰。
 * - 挂钩点:`TextView.setText`(限定 NetworkSpeedView 实例)的 **after** 阶段。
 *   setText 是原始实现、size Hook、doublelayer Hook 三种显示路径的共同汇聚点,
 *   每次网速刷新必然触发;after 阶段设置 visibility 保证最终生效。原始刷新循环
 *   从不恢复 VISIBLE(仅 `updateNetworkSpeedViewStatus()` 设置可见性),因此
 *   GONE 不会被后续刷新覆盖。
 * - 与 size Hook 共存:二者都 hook setText,一个改文本、一个改可见性,互不影响。
 * - 与 refresh Hook 共存:refresh 只改 Handler 消息延迟,不涉及 setText/visibility。
 */
@SuppressLint("PrivateApi")
class NetworkSpeedHideSlowHook : AppHookModule() {

    companion object {
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
        private const val KB = 1024L
    }

    /** 每个 NetworkSpeedView 实例独立的流量基线 / 可见性状态 */
    private class SpeedState {
        var lastRxBytes = 0L
        var lastTxBytes = 0L
        var lastUpdateTime = 0L
        var lastVisibility = View.VISIBLE
    }

    private val stateMap = WeakHashMap<TextView, SpeedState>()

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_SLOW.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            logger.info("开始Hook系统UI网速隐藏慢速")

            val networkSpeedViewClass =
                param.defaultClassLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)

            val setTextMethod =
                TextView::class.java.getDeclaredMethod("setText", CharSequence::class.java)
            hookWithId(setTextMethod, "hide_slow_set_text") { chain ->
                // 先让原始 setText(以及 size Hook 等)执行,再在 after 阶段设可见性
                chain.proceed()
                try {
                    if (networkSpeedViewClass.isInstance(chain.thisObject)) {
                        updateVisibility(chain.thisObject as TextView)
                    }
                } catch (_: Throwable) {
                    // 可见性处理失败不影响网速文本显示
                }
            }

            logger.info("系统UI网速隐藏慢速Hook成功")
        } catch (e: Throwable) {
            logger.error("系统UI网速隐藏慢速Hook失败", e)
        }
    }

    /**
     * 计算两次刷新间的下行/上行速度并更新可见性。
     * 阈值单位:KB/s(与前端设置一致),内部换算为 B/s 比较。
     */
    private fun updateVisibility(view: TextView) {
        val state = stateMap[view] ?: SpeedState().also { stateMap[view] = it }

        val now = System.currentTimeMillis()
        val rxBytes = getTotalRxBytes()
        val txBytes = getTotalTxBytes()

        if (state.lastUpdateTime == 0L) {
            // 首次刷新,无基线,记录数据并保持可见
            state.lastRxBytes = rxBytes
            state.lastTxBytes = txBytes
            state.lastUpdateTime = now
            setVisibility(view, state, View.VISIBLE)
            return
        }

        val timeDiff = now - state.lastUpdateTime
        val rxDiff = rxBytes - state.lastRxBytes
        val txDiff = txBytes - state.lastTxBytes
        state.lastRxBytes = rxBytes
        state.lastTxBytes = txBytes
        state.lastUpdateTime = now

        if (timeDiff <= 0) return

        // B/s
        val downSpeed = rxDiff * 1000 / timeDiff
        val upSpeed = txDiff * 1000 / timeDiff

        val prefs = xposed.getRemotePreferences(PREFS_NAME)
        val thresholdBps = prefs.getFloat(
            PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_THRESHOLD.name,
            PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_THRESHOLD.default
        ).coerceAtLeast(0f) * KB
        val hideBoth = prefs.getBoolean(
            PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_BOTH.name,
            PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_BOTH.default
        )

        val isSlow = if (hideBoth) {
            downSpeed < thresholdBps && upSpeed < thresholdBps
        } else {
            downSpeed < thresholdBps
        }

        setVisibility(view, state, if (isSlow) View.GONE else View.VISIBLE)
    }

    /** 仅在实际变化时设置 visibility,避免无谓的 measure/layout。 */
    private fun setVisibility(view: TextView, state: SpeedState, visibility: Int) {
        if (state.lastVisibility != visibility) {
            state.lastVisibility = visibility
            view.setVisibility(visibility)
            logger.debug(
                "NetworkSpeedView visibility -> " + if (visibility == View.GONE) "GONE" else "VISIBLE"
            )
        }
    }

    private fun getTotalRxBytes(): Long {
        return try {
            android.net.TrafficStats.getTotalRxBytes()
        } catch (_: Throwable) {
            0L
        }
    }

    private fun getTotalTxBytes(): Long {
        return try {
            android.net.TrafficStats.getTotalTxBytes()
        } catch (_: Throwable) {
            0L
        }
    }
}
