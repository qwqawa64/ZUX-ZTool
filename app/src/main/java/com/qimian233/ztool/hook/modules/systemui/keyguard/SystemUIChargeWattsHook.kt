package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * SystemUI充电瓦数显示Hook模块
 * 在锁屏充电提示中添加实时充电功率显示
 */
@SuppressLint("PrivateApi")
class SystemUIChargeWattsHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_CHARGE_WATTS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookKeyguardIndicationController(classLoader)
    }

    private fun hookKeyguardIndicationController(classLoader: ClassLoader) {
        try {
            // Hook computePowerIndication方法来添加充电瓦数显示
            val computeMethod = classLoader.loadClass(TARGET_CLASS)
                .getDeclaredMethod("computePowerIndication")
            hookWithId(computeMethod, "compute") { chain ->
                try {
                    // 获取原始返回的充电提示文本
                    val result = chain.proceed()
                    val originalText = result as String

                    // 获取KeyguardIndicationController实例
                    val controller = chain.thisObject
                    val cl: Class<*> = controller.javaClass

                    // 获取充电状态相关字段
                    val isPluggedIn = cl.getDeclaredField("mPowerPluggedIn").getBoolean(controller)
                    val chargingWattage = cl.getDeclaredField("mChargingWattage").getInt(controller)
                    val chargingSpeed = cl.getDeclaredField("mChargingSpeed").getInt(controller)

                    // 只在充电状态下显示瓦数，且瓦数大于0
                    if (isPluggedIn && chargingWattage > 0) {
                        // 尝试多种单位转换
                        val watts = calculateActualWatts(chargingWattage)

                        if (watts > 0) {
                            // 使用换行符 \n 追加功率信息
                            val newText = originalText + "\n" + formatWattage(watts, chargingSpeed)
                            logger.debug("成功添加充电瓦数显示: " + watts + "W, speed=" + chargingSpeed)
                            return@hookWithId newText
                        }
                    }
                    return@hookWithId result
                } catch (t: Throwable) {
                    logger.error("computePowerIndication hook回调异常", t)
                    return@hookWithId chain.proceed()
                }
            }

            // 额外Hook电池状态更新方法，确保能获取到最新的充电数据
            // onRefreshBatteryInfo 在新版 SystemUI 中位于内部类 BaseKeyguardCallback 中
            var refreshMethod: Method? = null
            try {
                val callbackClass = classLoader.loadClass(
                    $$"com.android.systemui.statusbar.KeyguardIndicationController$BaseKeyguardCallback"
                )
                val batteryStatusClass = classLoader.loadClass(
                    "com.android.settingslib.fuelgauge.BatteryStatus"
                )
                refreshMethod =
                    callbackClass.getDeclaredMethod("onRefreshBatteryInfo", batteryStatusClass)
            } catch (e: NoSuchMethodException) {
                logger.warn("Unable to find BaseKeyguardCallback.onRefreshBatteryInfo: " + e.message)
            } catch (e: ClassNotFoundException) {
                logger.warn("Unable to find BaseKeyguardCallback.onRefreshBatteryInfo: " + e.message)
            }
            if (refreshMethod != null) {
                hookWithId(refreshMethod, "final_refresh") { chain ->
                    try {
                        val result = chain.proceed()
                        // 这个方法会在电池状态更新时调用，我们可以在这里获取最新的充电数据
                        val batteryStatus = chain.args[0]
                        if (batteryStatus != null) {
                            try {
                                // 尝试从BatteryStatus对象获取充电功率
                                val maxChargingWattage = batteryStatus.javaClass
                                    .getDeclaredField("maxChargingWattage").getInt(batteryStatus)
                                // BaseKeyguardCallback 是 KeyguardIndicationController 的非静态内部类
                                // 通过 this$0 获取外部类实例
                                val callback = chain.thisObject
                                val outerField = callback.javaClass
                                    .getDeclaredField("this$0")
                                outerField.isAccessible = true
                                val controller = outerField.get(callback)
                                val cl: Class<*> = controller!!.javaClass

                                // 记录调试信息
                                logger.debug(
                                    "BatteryStatus更新 - maxChargingWattage: " + maxChargingWattage +
                                            ", mChargingWattage: " + cl.getDeclaredField("mChargingWattage")
                                        .getInt(controller)
                                )
                            } catch (t: Throwable) {
                                logger.error("读取BatteryStatus失败", t)
                            }
                        }
                        return@hookWithId result
                    } catch (t: Throwable) {
                        logger.error("onRefreshBatteryInfo hook回调异常", t)
                        return@hookWithId chain.proceed()
                    }
                }
            } else {
                logger.warn("Cannot find onRefreshBatteryInfo, skipping this hook")
            }

            logger.info("成功Hook KeyguardIndicationController")
        } catch (t: Throwable) {
            logger.error("Hook KeyguardIndicationController失败", t)
        }
    }

    /**
     * 尝试多种方式计算实际瓦数
     */
    private fun calculateActualWatts(rawWattage: Int): Int {
        // 情况1：如果值在合理范围内（1-150W），直接使用
        if (rawWattage in 1..150000) {
            // 可能是毫瓦单位，转换为瓦
            return rawWattage / 1000
        }

        // 情况2：如果值很大，可能是微瓦单位
        if (rawWattage in 150001..150000000) {
            return rawWattage / 1000000
        }

        // 情况3：如果值异常大，尝试除以10000（某些设备的特殊单位）
        if (rawWattage > 1000000) {
            return rawWattage / 10000
        }

        // 无法确定单位，返回0表示不显示
        logger.warn("无法识别的瓦数单位: $rawWattage")
        return 0
    }

    /**
     * 格式化充电瓦数显示：显示"<功率>W <闪电符号>"
     * 根据 mChargingSpeed 字段判断充电速度等级并附加闪电符号
     * @param watts 充电功率（瓦）
     * @param chargingSpeed 充电速度等级：1=慢速, 2=快速, 3=极速
     */
    private fun formatWattage(watts: Int, chargingSpeed: Int): String {
        if (watts <= 0) return ""

        // 基础字符串："[功率]W"
        val base = watts.toString() + "W"

        // 根据充电速度等级附加闪电符号
        return when (chargingSpeed) {
            3 -> "$base⚡⚡" // 极速充电
            2 -> "$base⚡" // 快速充电
            1 -> base // 慢速充电，无闪电符号
            else -> base
        }
    }

    companion object {
        private const val TARGET_CLASS =
            "com.android.systemui.statusbar.KeyguardIndicationController"
    }
}
