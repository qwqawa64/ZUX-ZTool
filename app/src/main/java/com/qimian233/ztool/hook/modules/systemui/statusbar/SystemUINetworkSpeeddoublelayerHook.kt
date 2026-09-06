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
 * SystemUI网络速度显示Hook模块
 * 功能：在状态栏显示实时上下行网络速度，支持自定义文本大小和显示格式
 */
@SuppressLint("PrivateApi")
class SystemUINetworkSpeeddoublelayerHook : AppHookModule() {

    // 存储每个实例的上次流量数据
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

            // Hook NetworkSpeedView 构造方法
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

            // Hook Handler 的 handleMessage 方法
            hookNetworkSpeedHandler(classLoader)

            logger.info("Successfully hooked NetworkSpeedView")
        } catch (t: Throwable) {
            logger.error("Error hooking NetworkSpeedView", t)
        }
    }

    private fun initNetworkSpeedView(networkSpeedView: Any) {
        try {
            val cl = networkSpeedView.javaClass
            // 获取初始流量数据
            val initialRxBytes = getTotalRxBytes()
            val initialTxBytes = getTotalTxBytes()

            // 存储初始数据
            lastRxBytesMap[networkSpeedView] = initialRxBytes
            lastTxBytesMap[networkSpeedView] = initialTxBytes
            lastUpdateTimeMap[networkSpeedView] = System.currentTimeMillis()

            // 调整文本大小
            try {
                // 获取当前文本大小并增加
                val getTextSizeMethod: Method = findMethod(cl, "getTextSize")
                val textSizeResult = getTextSizeMethod.invoke(networkSpeedView)
                val currentTextSize = textSizeResult as? Float ?: 8.0f
                val newTextSize = currentTextSize * 1.1f // 增加10%

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
            // 通过 DEXKit 查找 NetworkSpeedView 的内部 Handler 类（替代硬编码 $3）
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

                // 获取消息对象
                val message = chain.args[0]
                val what = message.javaClass.getDeclaredField("what").getInt(message)

                if (what == 10) { // 更新速度的消息
                    handleSpeedUpdate(networkSpeedView, handler)
                    return@hookWithId null // 阻止原始处理
                } else if (what == 1) { // 格式化显示的消息
                    handleSpeedDisplay(networkSpeedView, message)
                    return@hookWithId null // 阻止原始处理
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
            // 移除之前的消息
            findMethod(handlerCls, "removeMessages", Int::class.javaPrimitiveType).invoke(handler, 10)

            // 检查是否应该显示网速
            val isIconVisibleResult = networkSpeedView.javaClass
                .getDeclaredMethod("isIconVisible").invoke(networkSpeedView)
            val shouldShow = java.lang.Boolean.TRUE == isIconVisibleResult

            if (!shouldShow) {
                return
            }

            // 获取当前流量统计
            val currentRxBytes = getTotalRxBytes()
            val currentTxBytes = getTotalTxBytes()
            val currentTime = System.currentTimeMillis()

            // 获取上次的数据
            val lastRxBytes = lastRxBytesMap[networkSpeedView]
            val lastTxBytes = lastTxBytesMap[networkSpeedView]
            val lastUpdateTime = lastUpdateTimeMap[networkSpeedView]

            if (lastRxBytes == null || lastTxBytes == null || lastUpdateTime == null) {
                // 第一次更新，只记录数据不计算速度
                lastRxBytesMap[networkSpeedView] = currentRxBytes
                lastTxBytesMap[networkSpeedView] = currentTxBytes
                lastUpdateTimeMap[networkSpeedView] = currentTime
            } else {
                // 计算时间差（秒）
                val timeDiff = (currentTime - lastUpdateTime) / 1000
                if (timeDiff > 0) {
                    // 计算上下行速度（字节/秒）
                    val downSpeed = (currentRxBytes - lastRxBytes) / timeDiff
                    val upSpeed = (currentTxBytes - lastTxBytes) / timeDiff

                    // 记录调试信息
                    logger.debug(
                        String.format(
                            Locale.US,
                            "Successfully updated speed - downSpeed=%d, upSpeed=%d, timeDiff=%d",
                            downSpeed, upSpeed, timeDiff
                        )
                    )

                    // 发送显示消息
                    val message = findMethod(handlerCls, "obtainMessage").invoke(handler)
                    if (message != null) {
                        val msgCls = message.javaClass
                        msgCls.getDeclaredField("what").setInt(message, 1)
                        msgCls.getDeclaredField("obj").set(message, longArrayOf(downSpeed, upSpeed))
                        findMethod(handlerCls, "sendMessage", Message::class.java)
                            .invoke(handler, message)
                    }

                    // 更新数据
                    lastRxBytesMap[networkSpeedView] = currentRxBytes
                    lastTxBytesMap[networkSpeedView] = currentTxBytes
                    lastUpdateTimeMap[networkSpeedView] = currentTime
                }
            }

            val refreshInterval = (xposed.getRemotePreferences(PREFS_NAME)
                .getFloat("systemui_network_speed_refresh_interval", 3.0f) * 1000.0).toLong()
            // 安排下一次更新
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

            // 格式化上下行速度
            val downText = formatSpeed(downSpeed)
            val upText = formatSpeed(upSpeed)

            // 创建带有HTML格式的双层显示文本，调整字体大小
            val displayText = "<font size='5'><b>▴ " + upText + "</b></font><br/>" +
                "<font size='5'><b>▾ " + downText + "</b></font>"

            // 使用HTML格式设置文本
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

        // 根据速度值选择合适的精度
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
     * 通过反射查找 NetworkSpeedView 的内部 Handler 子类。
     * 遍历可能的内部类索引，替代硬编码的 $3。
     */
    private fun findHandlerInnerClass(classLoader: ClassLoader): Class<*> {
        // 先尝试离线索引中的 Handler 子类
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
        // 回退：遍历常见内部类索引
        for (i in 1..10) {
            try {
                val cls = classLoader.loadClass(NETWORK_SPEED_VIEW_CLASS + "$" + i)
                // 验证是 Handler 子类（有 handleMessage 方法）
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
