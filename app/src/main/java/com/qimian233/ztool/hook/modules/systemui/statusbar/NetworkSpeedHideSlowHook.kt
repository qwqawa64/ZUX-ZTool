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
 * 功能:当网速低于阈值时让 com.android.systemui.zui.NetworkSpeedView 不再占位显示,
 * 且文字消失;速度恢复后再显示。
 *
 * 双挂钩点协同:
 * 1. `isIconVisible()`(StatusIconDisplayable 接口实现):`StatusIconContainer.onMeasure/
 *    onLayout` 只依据它决定是否测量/排列某个图标。慢速时让它返回 false,容器不再
 *    测量/排列该视图,其它状态栏图标即自动左移占据被网速指示器占用的空间。
 *    注意:`NetworkSpeedView.setVisibleState()` 是空实现、`getVisibleState()` 恒为 0,
 *    因此仅靠返回值隐藏并不会让视图文字消失,还需第 2 个挂钩点。
 *    **按调用上下文区分返回值**:`SystemUINetworkSpeeddoublelayerHook` 的
 *    `handleSpeedUpdate` 会反射调用 `isIconVisible()` 判定是否继续刷新循环,若这里
 *    慢速时一律返回 false,该 Hook 会直接 return——既不刷新文本也不再安排下一次
 *    更新,导致文字不消失且网速恢复后不再刷新。因此仅当调用来自
 *    `StatusIconContainer` 的测量/排列流程时覆盖为 false(释放占位),其余上下文
 *    (含双层 Hook 反射)保持原始可见,刷新循环不断。
 * 2. `TextView.setText`(限定 NetworkSpeedView 实例)的 **after** 阶段:每次网速刷新
 *    必然触发(原始实现约 3 秒一次;双层 Hook 的 `handleSpeedDisplay` 也会反射调用
 *    `setText(CharSequence)`,同样经过此 hook),慢速时设 View.GONE 让文字消失,
 *    恢复时设 View.VISIBLE。setText 是原始实现、size Hook、doublelayer Hook 三种
 *    显示路径的共同汇聚点;原始刷新循环从不恢复 VISIBLE(仅
 *    `updateNetworkSpeedViewStatus()` 设置可见性),因此 GONE 不会被后续刷新覆盖。
 *
 * 速度判定:两个挂钩点共享 [shouldHide] 与 per-instance 流量基线,用
 * [android.net.TrafficStats] 两次采样间的差分自行计算,不依赖任何其他 Hook。
 *
 * 与既有 3 个网速 Hook 的兼容性设计:
 * - `SystemUINetworkSpeeddoublelayerHook` 拦截内部 Handler 的 what==10/what==1 并
 *   `return null` 切断 hook 链;它反射调用 `isIconVisible()` 判定是否刷新。本 Hook
 *   对该反射调用保持原始可见(见挂钩点 1),因此双层 Hook 的刷新循环不断,慢速期间
 *   仍持续刷新文本并每周期触发 setText,本 Hook 的 GONE 据此维持,网速恢复后继续
 *   正常显示。
 * - size Hook 改 setText 文本,本 Hook 也在 setText 后处理可见性,互不影响。
 * - refresh Hook 只改 Handler 消息延迟,不涉及 setText/isIconVisible。
 */
@SuppressLint("PrivateApi")
class NetworkSpeedHideSlowHook : AppHookModule() {

    companion object {
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
        private const val KB = 1024L

        /** 状态栏图标容器的类名标记,用于识别 isIconVisible() 的布局调用上下文 */
        private const val STATUS_ICON_CONTAINER_MARK = "StatusIconContainer"

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

            // 挂钩点 1:isIconVisible —— 控制占位。容器只测量/排列 isIconVisible()==true
            // 的图标,慢速时返回 false,其它图标自动左移占据被网速指示器占用的空间。
            // 注意区分调用上下文:双层 Hook 的 handleSpeedUpdate 反射调用本方法判定
            // 是否继续刷新循环,若慢速时一律返回 false 会中断其刷新,因此仅在调用
            // 来自 StatusIconContainer 测量/排列流程时覆盖返回值。
            val isIconVisibleMethod =
                networkSpeedViewClass.getDeclaredMethod("isIconVisible")
            hookWithId(isIconVisibleMethod, "hide_slow_is_icon_visible") { chain ->
                try {
                    // 先执行原始实现:它读取 Settings.System 的 network_realtime_speed_state
                    val originalVisible = chain.proceed() as Boolean
                    if (!originalVisible) {
                        // 用户本就在系统设置中关闭了网速显示,保持隐藏
                        false
                    } else if (isLayoutContext()) {
                        // 用户开启了网速显示,且调用来自 StatusIconContainer 布局:
                        // 慢速时覆盖为不可见,让其它图标重新占位
                        !shouldHide(chain.thisObject as TextView)
                    } else {
                        // 其它上下文(如双层 Hook 反射判定):保持原始可见,刷新循环不断
                        true
                    }
                } catch (e: Throwable) {
                    logger.error("系统UI网速隐藏慢速 isIconVisible 判定失败", e)
                    // 回退:重新执行原始方法取真实值
                    chain.proceed()
                }
            }

            // 挂钩点 2:setText —— 控制文字消失。每次网速刷新必然触发 setText,after
            // 阶段按慢速判定设置 visibility,让文字在慢速时真正消失。
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

    /** 慢速时隐藏视图自身(GONE),速度恢复后显示(VISIBLE)。仅在实际变化时设置。 */
    private fun updateVisibility(view: TextView) {
        val hide = shouldHide(view)
        val visibility = if (hide) View.GONE else View.VISIBLE
        if (view.visibility != visibility) {
            view.visibility = visibility
            logger.debug(
                "NetworkSpeedView visibility -> " + if (hide) "GONE (slow)" else "VISIBLE"
            )
        }
    }

    /**
     * 判断当前 `isIconVisible()` 调用是否来自 `StatusIconContainer` 的测量/排列流程。
     * 该容器在 onMeasure/onLayout 中直接调用 `StatusIconDisplayable.isIconVisible()`
     * 决定是否测量/排列图标;而双层 Hook 通过反射调用同一方法判定刷新循环,两者需
     * 区分对待。通过调用栈匹配容器类名,兼容 R8 混淆前/后的类名。
     */
    private fun isLayoutContext(): Boolean {
        return try {
            Thread.currentThread().stackTrace.any {
                it.className.contains(STATUS_ICON_CONTAINER_MARK)
            }
        } catch (_: Throwable) {
            false
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
