package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * SystemUI charging wattage display hook module.
 * Adds a real-time charging power display to the lock screen charging indication.
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
            // Hook computePowerIndication to add the charging wattage display
            val computeMethod = classLoader.loadClass(TARGET_CLASS)
                .getDeclaredMethod("computePowerIndication")
            hookWithId(computeMethod, "compute") { chain ->
                try {
                    // Get the original charging indication text
                    val result = chain.proceed()
                    val originalText = result as String

                    // Get the KeyguardIndicationController instance
                    val controller = chain.thisObject
                    val cl: Class<*> = controller.javaClass

                    // Get charging-state related fields
                    val isPluggedIn = cl.getDeclaredField("mPowerPluggedIn").getBoolean(controller)
                    val chargingWattage = cl.getDeclaredField("mChargingWattage").getInt(controller)
                    val chargingSpeed = cl.getDeclaredField("mChargingSpeed").getInt(controller)

                    // Only show wattage while charging and when wattage is greater than 0
                    if (isPluggedIn && chargingWattage > 0) {
                        // Try multiple unit conversions
                        val watts = calculateActualWatts(chargingWattage)

                        if (watts > 0) {
                            // Append power info using newline \n
                            val newText = originalText + "\n" + formatWattage(watts, chargingSpeed)
                            logger.debug("Charging wattage display added: " + watts + "W, speed=" + chargingSpeed)
                            return@hookWithId newText
                        }
                    }
                    return@hookWithId result
                } catch (t: Throwable) {
                    logger.error("computePowerIndication hook callback error", t)
                    return@hookWithId chain.proceed()
                }
            }

            // Additionally hook the battery status update method to get the latest charging data
            // onRefreshBatteryInfo lives in the inner class BaseKeyguardCallback on newer SystemUI versions
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
                        // This method is called on battery status updates, so we can get the latest charging data here
                        val batteryStatus = chain.args[0]
                        if (batteryStatus != null) {
                            try {
                                // Try to get the charging power from the BatteryStatus object
                                val maxChargingWattage = batteryStatus.javaClass
                                    .getDeclaredField("maxChargingWattage").getInt(batteryStatus)
                                // BaseKeyguardCallback is a non-static inner class of KeyguardIndicationController;
                                // get the outer class instance via this$0
                                val callback = chain.thisObject
                                val outerField = callback.javaClass
                                    .getDeclaredField("this$0")
                                outerField.isAccessible = true
                                val controller = outerField.get(callback)
                                val cl: Class<*> = controller!!.javaClass

                                // Log debug info
                                logger.debug(
                                    "BatteryStatus update - maxChargingWattage: " + maxChargingWattage +
                                            ", mChargingWattage: " + cl.getDeclaredField("mChargingWattage")
                                        .getInt(controller)
                                )
                            } catch (t: Throwable) {
                                logger.error("Failed to read BatteryStatus", t)
                            }
                        }
                        return@hookWithId result
                    } catch (t: Throwable) {
                        logger.error("onRefreshBatteryInfo hook callback error", t)
                        return@hookWithId chain.proceed()
                    }
                }
            } else {
                logger.warn("Cannot find onRefreshBatteryInfo, skipping this hook")
            }

            logger.info("Successfully hooked KeyguardIndicationController")
        } catch (t: Throwable) {
            logger.error("Failed to hook KeyguardIndicationController", t)
        }
    }

    /**
     * Try multiple ways to calculate the actual wattage
     */
    private fun calculateActualWatts(rawWattage: Int): Int {
        // Case 1: value in a plausible range (1-150W), use directly
        if (rawWattage in 1..150000) {
            // Likely in milliwatts, convert to watts
            return rawWattage / 1000
        }

        // Case 2: very large value, likely in microwatts
        if (rawWattage in 150001..150000000) {
            return rawWattage / 1000000
        }

        // Case 3: abnormally large value, try dividing by 10000 (device-specific unit)
        if (rawWattage > 1000000) {
            return rawWattage / 10000
        }

        // Unknown unit, return 0 to hide the display
        logger.warn("Unrecognized wattage unit: $rawWattage")
        return 0
    }

    /**
     * Format the charging wattage display: "<power>W <lightning symbols>"
     * Appends lightning symbols based on the mChargingSpeed level.
     * @param watts charging power (watts)
     * @param chargingSpeed charging speed level: 1=slow, 2=fast, 3=turbo
     */
    private fun formatWattage(watts: Int, chargingSpeed: Int): String {
        if (watts <= 0) return ""

        // Base string: "[power]W"
        val base = watts.toString() + "W"

        // Append lightning symbols based on the charging speed level
        return when (chargingSpeed) {
            3 -> "$base⚡⚡" // Turbo charging
            2 -> "$base⚡" // Fast charging
            1 -> base // Slow charging, no lightning symbol
            else -> base
        }
    }

    companion object {
        private const val TARGET_CLASS =
            "com.android.systemui.statusbar.KeyguardIndicationController"
    }
}
