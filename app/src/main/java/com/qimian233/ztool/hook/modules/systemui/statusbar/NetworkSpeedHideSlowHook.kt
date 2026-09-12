package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 状态栏网速指示器"隐藏慢速"Hook。
 *
 * 实现方式:拦截 [Settings.System.getInt] / [Settings.System.getIntForUser] 对
 * `network_realtime_speed_state` 的读取。慢速时把返回值伪装成 0(关闭),
 * SystemUI 自身的原始逻辑读到 0 后会自行隐藏网速指示器;速度恢复后放行原始值,
 * 网速重新显示。本 Hook 不直接干预任何视图(View/TextView)处理。
 *
 * 反编译(com.android.systemui)确认该键的全部读取点:
 * - `NetworkSpeedView.isIconVisible()`:StatusIconContainer onMeasure/onLayout 调用,
 *   false 时容器跳过测量/排列(但不隐藏视图本身)。
 * - `NetworkSpeedView.updateNetworkSpeedViewStatus()`:读 0 时 setVisibility(GONE)
 *   并停掉刷新循环——这是唯一真正隐藏视图的路径。
 * - `ZuiPhoneStatusBarPolicy.updateNetworkSpeed()`:读 0 时从 StatusBarIconController
 *   移除网速 slot。
 *
 * 关键约束:后两者是事件驱动(attach / 连接变化 / 用户切换 / 真实设置变更的
 * ContentObserver),慢速期间不会被再次调用;而 isIconVisible 的 measure 跳过
 * 只释放占位不隐藏视图,导致文字与无线图标重叠。因此本 Hook 额外做周期监测:
 * 慢速状态翻转时,反射调用视图自身的 `updateNetworkSpeedViewStatus()`,
 * 让 SystemUI 原始代码完成隐藏/显示(读到的设置值已被本 Hook 伪装)。
 *
 * 速度判定:用 [android.net.TrafficStats] 两次采样间的差分自行计算,
 * 阈值单位为 KB/s(与前端设置一致),采样按最小间隔节流。
 */
@SuppressLint("PrivateApi")
class NetworkSpeedHideSlowHook : AppHookModule() {

    companion object {
        /** SystemUI 判定网速指示器是否显示的系统设置键 */
        private const val NETWORK_SPEED_STATE_KEY = "network_realtime_speed_state"
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
        private const val KB = 1024L

        /** TrafficStats 采样最小间隔(ms),间隔内沿用上一次判定 */
        private const val SAMPLE_INTERVAL_MS = 1000L

        /** 慢速状态周期监测间隔(ms),与原生刷新循环 3s 对齐 */
        private const val WATCH_INTERVAL_MS = 3000L
    }

    /** 全局共享的流量基线 / 慢速判定状态 */
    private class SpeedState {
        var lastRxBytes = 0L
        var lastTxBytes = 0L
        var lastUpdateTime = 0L
        var lastSlow = false
    }

    private val state = SpeedState()
    private val viewRefs = ArrayList<WeakReference<Any>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updateStatusMethod: Method? = null
    private var lastAppliedSlow = false

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_HIDE_SLOW.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            logger.info("开始Hook系统UI网速隐藏慢速")

            val networkSpeedViewClass = param.defaultClassLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)
            updateStatusMethod = networkSpeedViewClass.getDeclaredMethod("updateNetworkSpeedViewStatus")

            // 拦截 Settings.System.getInt / getIntForUser 全部重载
            val resolver = android.content.ContentResolver::class.java
            val int1 = arrayOf(resolver, String::class.java)
            val int2 = arrayOf(resolver, String::class.java, Int::class.javaPrimitiveType)
            val int3 = arrayOf(resolver, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

            hookWithId(Settings.System::class.java.getDeclaredMethod("getInt", *int1),
                "hide_slow_get_int") { chain -> interceptRead(chain) }
            hookWithId(Settings.System::class.java.getDeclaredMethod("getInt", *int2),
                "hide_slow_get_int_def") { chain -> interceptRead(chain) }
            hookWithId(Settings.System::class.java.getDeclaredMethod("getIntForUser", *int2),
                "hide_slow_get_int_for_user") { chain -> interceptRead(chain) }
            hookWithId(Settings.System::class.java.getDeclaredMethod("getIntForUser", *int3),
                "hide_slow_get_int_for_user_def") { chain -> interceptRead(chain) }

            // 收集存活的 NetworkSpeedView 实例,供周期监测触发原生刷新
            for (ctor in networkSpeedViewClass.declaredConstructors) {
                hookWithId(ctor as Constructor<*>, "hide_slow_ctor_${ctor.parameterTypes.size}") { chain ->
                    chain.proceed()
                    synchronized(viewRefs) { viewRefs.add(WeakReference(chain.thisObject)) }
                }
            }

            startWatcher()
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
     * 主线程周期监测:慢速状态翻转时,对所有存活的 NetworkSpeedView 调用一次其自身的
     * `updateNetworkSpeedViewStatus()`。该方法内部重新读取(已被伪装的)设置值并按
     * 原生逻辑 setVisibility / 增停刷新循环,本 Hook 不直接操作视图。
     * 隐藏路径会 removeMessages(10) 停掉刷新循环,靠本监测在恢复翻转时再次触发,
     * 网速即可重新显示;监测之外的原生循环不受干扰,保证网速数值正常刷新。
     */
    private fun startWatcher() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                try {
                    val slow = shouldHide()
                    if (slow != lastAppliedSlow) {
                        lastAppliedSlow = slow
                        logger.debug(
                            "NetworkSpeedView slow state -> " +
                                if (slow) "slow (hide)" else "fast (show)"
                        )
                        // 只在状态翻转时触发一次原生刷新。若每周期都调用,
                        // updateNetworkSpeedViewStatus 会 removeMessages(10) 不断清掉
                        // 原生 3 秒测量窗口并重置流量基线,导致网速恒显示 0.00K/s。
                        val method = updateStatusMethod
                        if (method != null) {
                            synchronized(viewRefs) {
                                val it = viewRefs.iterator()
                                while (it.hasNext()) {
                                    val view = it.next().get()
                                    if (view == null) {
                                        it.remove()
                                        continue
                                    }
                                    try {
                                        method.invoke(view)
                                    } catch (_: Throwable) {
                                        // 单个实例刷新失败不影响其它实例
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logger.error("系统UI网速隐藏慢速周期刷新失败", e)
                } finally {
                    mainHandler.postDelayed(this, WATCH_INTERVAL_MS)
                }
            }
        }, WATCH_INTERVAL_MS)
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
