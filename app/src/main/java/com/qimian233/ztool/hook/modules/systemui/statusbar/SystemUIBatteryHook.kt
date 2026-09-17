package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Locale

/**
 * SystemUI battery percentage hook module.
 * Function: force-shows the battery percentage, adjusts layout position and font size.
 */
@SuppressLint("PrivateApi", "DiscouragedApi")
class SystemUIBatteryHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_BATTERY_PERCENTAGE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (ScopeKeys.SYSTEM_UI.packageName == packageName) {
            hookSystemUIBattery(classLoader)
        }
    }

    private fun hookSystemUIBattery(classLoader: ClassLoader) {
        try {
            // Hook the BatteryMeterView class
            val batteryMeterViewClass = classLoader.loadClass(
                "com.android.systemui.battery.BatteryMeterView"
            )

            // Hook the constructor to modify the layout when the view is created
            val ctor: Constructor<*> = batteryMeterViewClass.getDeclaredConstructor(
                Context::class.java,
                AttributeSet::class.java,
                Int::class.javaPrimitiveType
            )
            hookWithId(ctor, "ctor") { chain ->
                chain.proceed()
                modifyBatteryLayout(chain.thisObject)
                null
            }

            // Hook the updateShowPercent method
            val updateShowPercentMethod: Method =
                batteryMeterViewClass.getDeclaredMethod("updateShowPercent")
            hookWithId(updateShowPercentMethod, "update_show_percent") { chain ->
                val result = chain.proceed()
                forceShowPercentage(chain.thisObject)
                result
            }

            // Hook the updatePercentText method
            val updatePercentTextMethod: Method =
                batteryMeterViewClass.getDeclaredMethod("updatePercentText")
            hookWithId(updatePercentTextMethod, "update_percent_text") { chain ->
                val result = chain.proceed()
                updatePercentageText(chain.thisObject)
                result
            }

            // Hook the scaleBatteryMeterViews method to adjust font size
            val scaleMethod: Method = batteryMeterViewClass.getDeclaredMethod("scaleBatteryMeterViews")
            hookWithId(scaleMethod, "scale") { chain ->
                val result = chain.proceed()
                adjustTextSize(chain.thisObject)
                result
            }

            logger.info("SystemUI battery percentage hook module loaded successfully")
        } catch (t: Throwable) {
            logger.error("Failed to load SystemUI battery percentage hook module", t)
        }
    }

    private fun modifyBatteryLayout(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass

            // Get the key view components
            val container = cl.getDeclaredField("mBatteryPercentViewContainer")
                .get(batteryMeterView) as FrameLayout
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            // Remove the percentage text from the FrameLayout
            container.removeView(percentView)

            // Get LinearLayout layout params
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Set the start margin so the percentage shows to the right of the battery icon
            val marginStart = getDimenValue(batteryMeterView)
            layoutParams.setMargins(marginStart, 0, 0, 0)

            // Add the percentage text directly to BatteryMeterView (LinearLayout)
            val batteryView = batteryMeterView as LinearLayout
            batteryView.addView(percentView, 1, layoutParams) // insert at index 1 (after the battery icon)

            // Adjust font size
            adjustTextSize(batteryMeterView)

            logger.debug("Battery layout modification complete")
        } catch (t: Throwable) {
            logger.error("Failed to modify battery layout", t)
        }
    }

    private fun adjustTextSize(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            // Get the original font size
            val originalSize = getOriginalTextSize(batteryMeterView)

            // Set a larger font size (+3sp)
            val newSize = originalSize + 3
            percentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSize)

            // Optional: set bold for clearer text
            percentView.setTypeface(percentView.typeface, Typeface.BOLD)

            logger.debug("Battery percentage font size adjusted to ${newSize}sp")
        } catch (t: Throwable) {
            logger.error("Failed to adjust battery percentage font size", t)
        }
    }

    private fun getOriginalTextSize(batteryMeterView: Any): Float {
        try {
            // Get the system default battery text size
            val context = getContext(batteryMeterView)
            if (context == null) return 13.0f
            val originalSizeRes = context.resources.getIdentifier(
                "status_bar_battery_text_size", "dimen", ScopeKeys.SYSTEM_UI.packageName
            )

            if (originalSizeRes != 0) {
                val sizeInPixels = context.resources.getDimension(originalSizeRes)
                // Convert pixels to sp
                val oneSpInPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 1f, context.resources.displayMetrics
                )
                return sizeInPixels / oneSpInPx
            }
        } catch (t: Throwable) {
            logger.warn("Failed to get the original battery font size, using default")
        }

        // Default value: 12sp
        return 12f
    }

    private fun forceShowPercentage(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            // Force-show the percentage view regardless of system settings
            if (percentView.visibility != View.VISIBLE) {
                percentView.visibility = View.VISIBLE
            }

            // Update the percentage text
            updatePercentageText(batteryMeterView)
        } catch (t: Throwable) {
            logger.error("Failed to force-show battery percentage", t)
        }
    }

    private fun updatePercentageText(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            // Get the current battery level
            val level = cl.getDeclaredField("mLevel").getInt(batteryMeterView)

            // Set the percentage text
            percentView.text = String.format(Locale.US, "%d%%", level)

            // Update the content description (accessibility)
            val ctx = getContext(batteryMeterView)
            if (ctx == null) return

            val charging = cl.getDeclaredField("mCharging").getBoolean(batteryMeterView)
            val description = ctx.getString(
                if (charging) {
                    getResourceId(batteryMeterView, "accessibility_battery_level_charging")
                } else {
                    getResourceId(batteryMeterView, "accessibility_battery_level")
                },
                level
            )

            if (batteryMeterView is LinearLayout) {
                batteryMeterView.contentDescription = description
            }
        } catch (t: Throwable) {
            logger.error("Failed to update battery percentage text", t)
        }
    }

    // Utility: get a dimension value
    private fun getDimenValue(batteryMeterView: Any): Int {
        return try {
            val context = getContext(batteryMeterView)
            if (context == null) return 8
            val resId = context.resources.getIdentifier(
                "qs_battery_padding", "dimen", ScopeKeys.SYSTEM_UI.packageName
            )
            context.resources.getDimensionPixelOffset(resId)
        } catch (t: Throwable) {
            8 // default value
        }
    }

    // Utility: get a resource ID
    private fun getResourceId(batteryMeterView: Any, resourceName: String): Int {
        return try {
            val context = getContext(batteryMeterView)
            if (context == null) return 0
            context.resources.getIdentifier(
                resourceName, "string", ScopeKeys.SYSTEM_UI.packageName
            )
        } catch (t: Throwable) {
            0
        }
    }

    // Utility: get the Context
    private fun getContext(batteryMeterView: Any): Context? {
        return try {
            batteryMeterView.javaClass
                .getDeclaredField("mContext").get(batteryMeterView) as Context
        } catch (t: Throwable) {
            // Fallback: BatteryMeterView extends LinearLayout extends View
            if (batteryMeterView is View) {
                batteryMeterView.context
            } else {
                null
            }
        }
    }
}
