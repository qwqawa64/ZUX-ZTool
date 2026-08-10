package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.WeakHashMap

/**
 * 状态栏网速指示器"隐藏慢速"Hook。
 *
 * 功能:当网速低于阈值时让 [com.android.systemui.zui.NetworkSpeedView] 不再占位显示,
 * 速度恢复后再显示。
 *
 * 实现思路:直接对 View 设 GONE 只能隐藏自身,无法让其它状态栏图标重新占据被网速
 * 指示器占用的空间——`StatusIconContainer.onMeasure/onLayout` 只依据
 * `StatusIconDisplayable.isIconVisible()` 决定是否测量/排列某个图标。因此本 Hook
 * 挂钩点改为 `NetworkSpeedView.isIconVisible()`(它读取 Settings.System 的
 * `network_realtime_speed_state` 持久化值,1=显示):慢速时让该方法返回 false,
 * 容器不再测量/排列该视图,其它图标即自动左移占位;速度恢复后返回 true,重新占位。
 * 注意只覆盖读取结果,绝不真实写入该持久化键,以免改动用户设置。
 *
 * 与既有 3 个网速 Hook 的兼容性设计:
 * - 速度获取:用 [android.net.TrafficStats] 两次采样间的差分自行计算,不依赖任何
 *   其他 Hook。因此与 `SystemUINetworkSpeeddoublelayerHook`(它拦截内部 Handler 的
 *   what==10/what==1 并 `return null` 切断 hook 链)互不干扰;该 Hook 反射调用
 *   `isIconVisible()` 判定是否刷新,慢速隐藏期间它会自然停止刷新文本,恢复后继续。
 * - 与 size Hook 共存:size Hook 改 setText 文本,本 Hook 改 isIconVisible 返回值,
 *   互不影响。
 * - 与 refresh Hook 共存:refresh 只改 Handler 消息延迟,不涉及 isIconVisible。
 */
@SuppressLint("PrivateApi")
class NetworkSpeedHideSlowHook : AppHookModule() {

    companion object {
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
        private const val KB = 1024L

        /**
         * TrafficStats 采样最小间隔(ms)。`isIconVisible()` 在每次 measure/layout 时都会
         * 被调用,频率远高于网速刷新;间隔过短会导致速度差分失真,因此节流采样,
         * 间隔内沿用上一次的判定结果。
         */
        private const val SAMPLE_INTERVAL_MS = 1000L
    }

    /** 每个 NetworkSpeedView 实例独立的流量基线 / 慢速判定状态 */
    private class SpeedState {
        var lastRxBytes = 0L
        var lastTxBytes = 0L
        var lastUpdateTime = 0L
        var lastSlow = false
    }

    private val stateMap = WeakHashMap<TextView, SpeedState>()

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_SLOW.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            logger.info("开始Hook系统UI网速隐藏慢速")

            val networkSpeedViewClass =
                param.defaultClassLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)

            val isIconVisibleMethod =
                networkSpeedViewClass.getDeclaredMethod("isIconVisible")
            hookWithId(isIconVisibleMethod, "hide_slow_is_icon_visible") { chain ->
                try {
                    // 先执行原始实现:它读取 Settings.System 的 network_realtime_speed_state
                    val originalVisible = chain.proceed() as Boolean
                    if (!originalVisible) {
                        // 用户本就在系统设置中关闭了网速显示,保持隐藏
                        false
                    } else {
                        // 用户开启了网速显示:慢速时覆盖为不可见,让其它图标重新占位
                        !shouldHide(chain.thisObject as TextView)
                    }
                } catch (e: Throwable) {
                    logger.error("系统UI网速隐藏慢速 isIconVisible 判定失败", e)
                    // 回退:重新执行原始方法取真实值
                    chain.proceed()
                }
            }

            logger.info("系统UI网速隐藏慢速Hook成功")
        } catch (e: Throwable) {
            logger.error("系统UI网速隐藏慢速Hook失败", e)
        }
    }

    /**
     * 计算两次采样间的下行/上行速度并返回"当前是否处于慢速(应隐藏)"。
     * 阈值单位:KB/s(与前端设置一致),内部换算为 B/s 比较。
     * 采样间隔内沿用上次判定,避免高频 measure/layout 造成短差分误判。
     */
    private fun shouldHide(view: TextView): Boolean {
        val state = stateMap[view] ?: SpeedState().also { stateMap[view] = it }

        val now = System.currentTimeMillis()
        if (state.lastUpdateTime != 0L && now - state.lastUpdateTime < SAMPLE_INTERVAL_MS) {
            return state.lastSlow
        }

        val rxBytes = getTotalRxBytes()
        val txBytes = getTotalTxBytes()

        if (state.lastUpdateTime == 0L) {
            // 首次采样,无基线,记录数据并保持显示
            state.lastRxBytes = rxBytes
            state.lastTxBytes = txBytes
            state.lastUpdateTime = now
            state.lastSlow = false
            return false
        }

        val timeDiff = now - state.lastUpdateTime
        val rxDiff = rxBytes - state.lastRxBytes
        val txDiff = txBytes - state.lastTxBytes
        state.lastRxBytes = rxBytes
        state.lastTxBytes = txBytes
        state.lastUpdateTime = now

        if (timeDiff <= 0) return state.lastSlow

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

        if (isSlow != state.lastSlow) {
            logger.debug(
                "NetworkSpeedView isIconVisible -> " + if (isSlow) "hidden (slow)" else "visible"
            )
        }
        state.lastSlow = isSlow
        return isSlow
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
