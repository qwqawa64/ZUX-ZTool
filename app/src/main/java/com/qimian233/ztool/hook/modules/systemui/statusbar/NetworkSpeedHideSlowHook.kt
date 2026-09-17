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
 * Status bar network speed indicator "hide slow" hook.
 *
 * Implementation: intercepts [Settings.System.getInt] / [Settings.System.getIntForUser]
 * reads of `network_realtime_speed_state`. When slow, the return value is faked as 0
 * (disabled); SystemUI's own original logic hides the network speed indicator upon
 * reading 0; when speed recovers, the original value is passed through and the speed
 * indicator shows again. This hook does not directly manipulate any views (View/TextView).
 *
 * All read points of this key confirmed by decompilation (com.android.systemui):
 * - `NetworkSpeedView.isIconVisible()`: called by StatusIconContainer onMeasure/onLayout;
 *   when false, the container skips measuring/laying out (but does not hide the view itself).
 * - `NetworkSpeedView.updateNetworkSpeedViewStatus()`: reads 0 -> setVisibility(GONE)
 *   and stops the refresh loop - this is the only path that truly hides the view.
 * - `ZuiPhoneStatusBarPolicy.updateNetworkSpeed()`: reads 0 -> removes the network
 *   speed slot from StatusBarIconController.
 *
 * Key constraint: the latter two are event-driven (attach / connection change / user
 * switch / ContentObserver of real setting changes) and are not called again while slow;
 * while isIconVisible's measure skip only releases the placeholder without hiding the
 * view, causing text and wireless icon overlap. Therefore this hook additionally runs
 * periodic monitoring: when the slow state flips, it reflectively invokes the view's
 * own `updateNetworkSpeedViewStatus()`, letting SystemUI's original code perform the
 * hide/show (the setting value read is already faked by this hook).
 *
 * Speed detection: self-computed via differencing between two [android.net.TrafficStats]
 * samples; the threshold unit is KB/s (consistent with the frontend setting); sampling
 * is throttled by a minimum interval.
 */
@SuppressLint("PrivateApi")
class NetworkSpeedHideSlowHook : AppHookModule() {

    companion object {
        /** System setting key SystemUI uses to decide network speed indicator visibility */
        private const val NETWORK_SPEED_STATE_KEY = "network_realtime_speed_state"
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
        private const val KB = 1024L

        /** TrafficStats minimum sampling interval (ms); the previous decision is reused within it */
        private const val SAMPLE_INTERVAL_MS = 1000L

        /** Slow-state periodic monitoring interval (ms), aligned with the native 3s refresh loop */
        private const val WATCH_INTERVAL_MS = 3000L
    }

    /** Globally shared traffic baseline / slow-state decision state */
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
            logger.info("Hooking SystemUI network speed hide-slow")

            val networkSpeedViewClass = param.defaultClassLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)
            updateStatusMethod = networkSpeedViewClass.getDeclaredMethod("updateNetworkSpeedViewStatus")

            // Intercept all overloads of Settings.System.getInt / getIntForUser
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

            // Collect live NetworkSpeedView instances for periodic watcher-triggered native refresh
            for (ctor in networkSpeedViewClass.declaredConstructors) {
                hookWithId(ctor as Constructor<*>, "hide_slow_ctor_${ctor.parameterTypes.size}") { chain ->
                    chain.proceed()
                    synchronized(viewRefs) { viewRefs.add(WeakReference(chain.thisObject)) }
                }
            }

            startWatcher()
            logger.info("SystemUI network speed hide-slow hooks applied")
        } catch (e: Throwable) {
            logger.error("Failed to hook SystemUI network speed hide-slow", e)
        }
    }

    /**
     * Unified interception logic: when the target key is network_realtime_speed_state and
     * the device is currently slow, return 0 (as if the user disabled network speed in
     * system settings) so SystemUI's original logic hides it on its own; otherwise pass
     * through the original read result.
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
     * Main-thread periodic monitoring: when the slow state flips, call each live
     * NetworkSpeedView's own `updateNetworkSpeedViewStatus()` once. That method
     * re-reads the (already faked) setting value and performs setVisibility /
     * starts-stops the refresh loop per native logic; this hook does not touch
     * views directly.
     * The hide path calls removeMessages(10) to stop the refresh loop; this monitoring
     * triggers again when the state flips back so the speed indicator shows again.
     * The native loop outside this monitoring is not disturbed, keeping the speed
     * value refreshing normally.
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
                        // Only trigger one native refresh on state flip. Calling it every cycle
                        // would have updateNetworkSpeedViewStatus repeatedly call removeMessages(10),
                        // clearing the native 3s measurement window and resetting the traffic
                        // baseline, causing the speed to always show 0.00K/s.
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
                                        // A single instance refresh failure does not affect other instances
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logger.error("Network speed hide-slow periodic refresh failed", e)
                } finally {
                    mainHandler.postDelayed(this, WATCH_INTERVAL_MS)
                }
            }
        }, WATCH_INTERVAL_MS)
    }

    /**
     * Compute the downlink/uplink speed between two samples and return "currently slow
     * (should hide)". Threshold unit: KB/s (consistent with the frontend setting),
     * internally converted to B/s for comparison. The previous decision is reused within
     * the sampling interval to avoid short-differencing misjudgment from high-frequency
     * Settings reads.
     */
    private fun shouldHide(): Boolean {
        val now = System.currentTimeMillis()
        if (state.lastUpdateTime != 0L && now - state.lastUpdateTime < SAMPLE_INTERVAL_MS) {
            return state.lastSlow
        }

        val rxBytes = getTotalRxBytes()
        val txBytes = getTotalTxBytes()

        if (state.lastUpdateTime == 0L) {
            // First sample, no baseline; record data and keep showing
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
