package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 状态栏网速指示器"隐藏慢速"Hook。
 *
 * 实现方式:拦截 [Settings.System.getInt] / [Settings.System.getIntForUser] 对
 * `network_realtime_speed_state` 的读取。慢速时把返回值伪装成 0(关闭),
 * SystemUI 自身的原始逻辑(如 `NetworkSpeedView.isIconVisible()`)读到 0 后
 * 会自行隐藏网速指示器;速度恢复后放行原始值,网速重新显示。
 * 本 Hook 不干预任何视图(View/TextView)处理。
 *
 * 速度判定:用 [android.net.TrafficStats] 两次采样间的差分自行计算,
 * 阈值单位为 KB/s(与前端设置一致)。为避免高频调用导致短差分失真,
 * 采样按最小间隔节流,间隔内沿用上一次的判定结果。
 *
 * 与其它网速 Hook 的兼容性:size / doublelayer / refresh Hook 均不读取该
 * Settings 键,互不影响;本 Hook 也不再触碰 setText / isIconVisible 等路径。
 */
@SuppressLint("PrivateApi")
class NetworkSpeedHideSlowHook : AppHookModule() {

    companion object {
        /** SystemUI 判定网速指示器是否显示的系统设置键 */
        private const val NETWORK_SPEED_STATE_KEY = "network_realtime_speed_state"
        private const val KB = 1024L

        /**
         * TrafficStats 采样最小间隔(ms)。间隔过短会导致速度差分失真,
         * 因此节流采样,间隔内沿用上一次的判定结果。
         */
        private const val SAMPLE_INTERVAL_MS = 1000L
    }

    /** 全局共享的流量基线 / 慢速判定状态(本 Hook 不再关联具体 View 实例) */
    private class SpeedState {
        var lastRxBytes = 0L
        var lastTxBytes = 0L
        var lastUpdateTime = 0L
        var lastSlow = false
    }

    private val state = SpeedState()

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_SLOW.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            logger.info("开始Hook系统UI网速隐藏慢速")

            val intArgs = arrayOf(
                android.content.ContentResolver::class.java, String::class.java
            )
            val intDefArgs = arrayOf(
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            val intForUserDefArgs = arrayOf(
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            val intMethod = Settings.System::class.java.getDeclaredMethod("getInt", *intArgs)
            hookWithId(intMethod, "hide_slow_get_int") { chain -> interceptRead(chain) }

            val intDefMethod =
                Settings.System::class.java.getDeclaredMethod("getInt", *intDefArgs)
            hookWithId(intDefMethod, "hide_slow_get_int_def") { chain -> interceptRead(chain) }

            val intForUserMethod = Settings.System::class.java.getDeclaredMethod(
                "getIntForUser", *intDefArgs
            )
            hookWithId(intForUserMethod, "hide_slow_get_int_for_user") { chain ->
                interceptRead(chain)
            }

            val intForUserDefMethod = Settings.System::class.java.getDeclaredMethod(
                "getIntForUser", *intForUserDefArgs
            )
            hookWithId(intForUserDefMethod, "hide_slow_get_int_for_user_def") { chain ->
                interceptRead(chain)
            }

            logger.info("系统UI网速隐藏慢速Hook成功")
        } catch (e: Throwable) {
            logger.error("系统UI网速隐藏慢速Hook失败", e)
        }
    }

    /**
     * 统一拦截逻辑:目标键为 network_realtime_speed_state 且当前处于慢速时,
     * 返回 0(表示用户在系统设置中关闭了网速),让 SystemUI 原始逻辑自行隐藏;
     * 其余情况放行原始读取结果。
     */
    private fun interceptRead(chain: XposedInterface.Chain): Any {
        val key = chain.args.getOrNull(1) as? String
        if (key != NETWORK_SPEED_STATE_KEY) {
            return chain.proceed()
        }
        val original = chain.proceed() as Int
        return if (original != 0 && shouldHide()) 0 else original
    }

    /**
     * 计算两次采样间的下行/上行速度并返回"当前是否处于慢速(应隐藏)"。
     * 阈值单位:KB/s(与前端设置一致),内部换算为 B/s 比较。
     * 采样间隔内沿用上次判定,避免高频 Settings 读取造成短差分误判。
     */
    private fun shouldHide(): Boolean {
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
                "NetworkSpeedView slow state -> " + if (isSlow) "slow (hide)" else "fast (show)"
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
