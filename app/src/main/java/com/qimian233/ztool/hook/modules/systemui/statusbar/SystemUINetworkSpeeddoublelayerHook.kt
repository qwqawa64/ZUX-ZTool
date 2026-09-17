package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.content.Context
import android.net.TrafficStats
import android.os.Message
import android.text.Html
import android.util.AttributeSet
import android.util.TypedValue
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.text.DecimalFormat
import java.util.Locale
import java.util.WeakHashMap

/**
 * SystemUI network speed display hook module.
 * Function: shows real-time uplink/downlink network speed in the status bar; supports
 * custom text size and display format.
 */
@SuppressLint("PrivateApi")
class SystemUINetworkSpeeddoublelayerHook : AppHookModule() {

    // Store the last traffic data per instance
    private val lastRxBytesMap = WeakHashMap<Any, Long>()
    private val lastTxBytesMap = WeakHashMap<Any, Long>()
    private val lastUpdateTimeMap = WeakHashMap<Any, Long>()

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_DOUBLELAYER.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (SYSTEMUI_PACKAGE == packageName) {
            hookSystemUINetworkSpeed(classLoader)
        }
    }

    private fun hookSystemUINetworkSpeed(classLoader: ClassLoader) {
        try {
            logger.info("Starting to hook SystemUI NetworkSpeedView")

            // Hook the NetworkSpeedView constructor
            val ctor: Constructor<*> = classLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)
                .getDeclaredConstructor(
                    Context::class.java,
                    AttributeSet::class.java,
                    Int::class.javaPrimitiveType
                )
            hookWithId(ctor, "ctor") { chain ->
                chain.proceed()
                initNetworkSpeedView(chain.thisObject)
                null
            }

            // Hook the Handler's handleMessage method
            hookNetworkSpeedHandler(classLoader)

            logger.info("Successfully hooked NetworkSpeedView")
        } catch (t: Throwable) {
            logger.error("Error hooking NetworkSpeedView", t)
        }
    }

    private fun initNetworkSpeedView(networkSpeedView: Any) {
        try {
            val cl = networkSpeedView.javaClass
            // Get the initial traffic data
            val initialRxBytes = getTotalRxBytes()
            val initialTxBytes = getTotalTxBytes()

            // Store the initial data
            lastRxBytesMap[networkSpeedView] = initialRxBytes
            lastTxBytesMap[networkSpeedView] = initialTxBytes
            lastUpdateTimeMap[networkSpeedView] = System.currentTimeMillis()

            // Adjust the text size
            try {
                // Get the current text size and increase it
                val getTextSizeMethod: Method = findMethod(cl, "getTextSize")
                val textSizeResult = getTextSizeMethod.invoke(networkSpeedView)
                val currentTextSize = textSizeResult as? Float ?: 8.0f
                val newTextSize = currentTextSize * 1.1f // +10%

                val setTextSizeMethod: Method =
                    findMethod(cl, "setTextSize", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                setTextSizeMethod.invoke(
                    networkSpeedView, TypedValue.COMPLEX_UNIT_PX, newTextSize
                )

                logger.debug("Adjusted text size from $currentTextSize to $newTextSize")
            } catch (sizeError: Throwable) {
                logger.error("Error adjusting text size", sizeError)
            }

            logger.debug("Initialized NetworkSpeedView instance")
        } catch (t: Throwable) {
            logger.error("Error initializing NetworkSpeedView", t)
        }
    }

    private fun hookNetworkSpeedHandler(classLoader: ClassLoader) {
        try {
            // Find NetworkSpeedView's inner Handler class via DEXKit (replacing hardcoded $3)
            val handlerClass = findHandlerInnerClass(classLoader)
            val handleMessageMethod: Method =
                handlerClass.getDeclaredMethod("handleMessage", Message::class.java)
            hookWithId(handleMessageMethod, "handle_message") { chain ->
                val handler = chain.thisObject
                val handlerCls = handler.javaClass
                val this0Field: Field = handlerCls.getDeclaredField("this\$0")
                this0Field.isAccessible = true
                val networkSpeedView = this0Field.get(handler)
                    ?: return@hookWithId chain.proceed()

                // Get the message object
                val message = chain.args[0]
                val what = message.javaClass.getDeclaredField("what").getInt(message)

                if (what == 10) { // speed update message
                    handleSpeedUpdate(networkSpeedView, handler)
                    return@hookWithId null // block the original handling
                } else if (what == 1) { // format/display message
                    handleSpeedDisplay(networkSpeedView, message)
                    return@hookWithId null // block the original handling
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Error hooking NetworkSpeed handler", t)
        }
    }

    private fun handleSpeedUpdate(networkSpeedView: Any, handler: Any) {
        try {
            val handlerCls = handler.javaClass
            // Remove the previous message
            findMethod(handlerCls, "removeMessages", Int::class.javaPrimitiveType).invoke(handler, 10)

            // Check whether the network speed should be shown
            val isIconVisibleResult = networkSpeedView.javaClass
                .getDeclaredMethod("isIconVisible").invoke(networkSpeedView)
            val shouldShow = java.lang.Boolean.TRUE == isIconVisibleResult

            if (!shouldShow) {
                return
            }

            // Get the current traffic stats
            val currentRxBytes = getTotalRxBytes()
            val currentTxBytes = getTotalTxBytes()
            val currentTime = System.currentTimeMillis()

            // Get the previous data
            val lastRxBytes = lastRxBytesMap[networkSpeedView]
            val lastTxBytes = lastTxBytesMap[networkSpeedView]
            val lastUpdateTime = lastUpdateTimeMap[networkSpeedView]

            if (lastRxBytes == null || lastTxBytes == null || lastUpdateTime == null) {
                // First update, record data without computing speed
                lastRxBytesMap[networkSpeedView] = currentRxBytes
                lastTxBytesMap[networkSpeedView] = currentTxBytes
                lastUpdateTimeMap[networkSpeedView] = currentTime
            } else {
                // Compute the time difference (seconds)
                val timeDiff = (currentTime - lastUpdateTime) / 1000
                if (timeDiff > 0) {
                    // Compute uplink/downlink speed (bytes/second)
                    val downSpeed = (currentRxBytes - lastRxBytes) / timeDiff
                    val upSpeed = (currentTxBytes - lastTxBytes) / timeDiff

                    // Log debug info
                    logger.debug(
                        String.format(
                            Locale.US,
                            "Successfully updated speed - downSpeed=%d, upSpeed=%d, timeDiff=%d",
                            downSpeed, upSpeed, timeDiff
                        )
                    )

                    // Send the display message
                    val message = findMethod(handlerCls, "obtainMessage").invoke(handler)
                    if (message != null) {
                        val msgCls = message.javaClass
                        msgCls.getDeclaredField("what").setInt(message, 1)
                        msgCls.getDeclaredField("obj").set(message, longArrayOf(downSpeed, upSpeed))
                        findMethod(handlerCls, "sendMessage", Message::class.java)
                            .invoke(handler, message)
                    }

                    // Update the data
                    lastRxBytesMap[networkSpeedView] = currentRxBytes
                    lastTxBytesMap[networkSpeedView] = currentTxBytes
                    lastUpdateTimeMap[networkSpeedView] = currentTime
                }
            }

            val refreshInterval = (xposed.getRemotePreferences(PREFS_NAME)
                .getFloat("systemui_network_speed_refresh_interval", 3.0f) * 1000.0).toLong()
            // Schedule the next update
            findMethod(handlerCls, "sendEmptyMessageDelayed", Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                .invoke(handler, 10, refreshInterval)
        } catch (t: Throwable) {
            logger.error("Error in speed update", t)
        }
    }

    private fun handleSpeedDisplay(networkSpeedView: Any, message: Any) {
        try {
            val speeds = message.javaClass.getDeclaredField("obj").get(message) as? LongArray
                ?: return
            if (speeds.size != 2) return

            val downSpeed = speeds[0]
            val upSpeed = speeds[1]

            // Format uplink/downlink speed
            val downText = formatSpeed(downSpeed)
            val upText = formatSpeed(upSpeed)

            // Create the double-layer display text with HTML formatting, adjusting font size
            val displayText = "<font size='5'><b>▴ " + upText + "</b></font><br/>" +
                "<font size='5'><b>▾ " + downText + "</b></font>"

            // Set the text using HTML formatting
            findMethod(networkSpeedView.javaClass, "setText", CharSequence::class.java)
                .invoke(networkSpeedView, Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY))
        } catch (t: Throwable) {
            logger.error("Error in speed display", t)
        }
    }

    private fun formatSpeed(speedBytes: Long): String {
        if (speedBytes <= 0) {
            return "0.00 B/s"
        }

        val speed: Double
        val unit: String

        if (speedBytes >= 1073741824) { // 1 GB
            speed = speedBytes / 1073741824.0
            unit = "G/s"
        } else if (speedBytes >= 1048576) { // 1 MB
            speed = speedBytes / 1048576.0
            unit = "M/s"
        } else if (speedBytes >= 1024) { // 1 KB
            speed = speedBytes / 1024.0
            unit = "K/s"
        } else {
            speed = speedBytes.toDouble()
            unit = "B/s"
        }

        // Choose precision based on the speed value
        val formatPattern = if (speed >= 100) {
            "0"
        } else if (speed >= 10) {
            "0.0"
        } else {
            "0.00"
        }

        val df = DecimalFormat(formatPattern)
        return df.format(speed) + " " + unit
    }

    private fun getTotalRxBytes(): Long {
        return try {
            val result = TrafficStats::class.java
                .getDeclaredMethod("getTotalRxBytes").invoke(null)
            result as? Long ?: 0L
        } catch (t: Throwable) {
            logger.error("Error getting Rx bytes", t)
            0
        }
    }

    private fun getTotalTxBytes(): Long {
        return try {
            val result = TrafficStats::class.java
                .getDeclaredMethod("getTotalTxBytes").invoke(null)
            result as? Long ?: 0L
        } catch (t: Throwable) {
            logger.error("Error getting Tx bytes", t)
            0
        }
    }

    /**
     * Find NetworkSpeedView's inner Handler subclass via reflection.
     * Iterates possible inner class indexes, replacing the hardcoded $3.
     */
    private fun findHandlerInnerClass(classLoader: ClassLoader): Class<*> {
        // First try the Handler subclass from the offline index
        val indexed = DexIndexStore.string(
            xposed, SYSTEMUI_PACKAGE,
            DexIndexConstants.ModuleKeys.SYSTEMUI_NETWORK_SPEED_DOUBLELAYER,
            DexIndexConstants.Keys.HANDLER_INNER_CLASS
        )
        if (indexed != null) {
            try {
                val cls = classLoader.loadClass(indexed)
                logger.debug("Loaded Handler inner class from dex index: $indexed")
                return cls
            } catch (_: ClassNotFoundException) {
            }
        }
        // Fallback: iterate common inner class indexes
        for (i in 1..10) {
            try {
                val cls = classLoader.loadClass(NETWORK_SPEED_VIEW_CLASS + "$" + i)
                // Verify it is a Handler subclass (has the handleMessage method)
                try {
                    cls.getDeclaredMethod("handleMessage", Message::class.java)
                    logger.debug("Found Handler inner class at index $i")
                    return cls
                } catch (_: NoSuchMethodException) {
                }
            } catch (_: ClassNotFoundException) {
            }
        }
        throw RuntimeException("Cannot find NetworkSpeedView Handler inner class")
    }

    companion object {
        private const val PREFS_NAME = "xposed_module_config"
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
    }
}
