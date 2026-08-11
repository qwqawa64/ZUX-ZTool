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
 * 系统UI电池百分比Hook模块
 * 功能：强制显示电池百分比，调整布局位置和字体大小
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
            // Hook BatteryMeterView 类
            val batteryMeterViewClass = classLoader.loadClass(
                "com.android.systemui.battery.BatteryMeterView"
            )

            // Hook 构造函数，在视图创建时修改布局
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

            // Hook updateShowPercent 方法
            val updateShowPercentMethod: Method =
                batteryMeterViewClass.getDeclaredMethod("updateShowPercent")
            hookWithId(updateShowPercentMethod, "update_show_percent") { chain ->
                val result = chain.proceed()
                forceShowPercentage(chain.thisObject)
                result
            }

            // Hook updatePercentText 方法
            val updatePercentTextMethod: Method =
                batteryMeterViewClass.getDeclaredMethod("updatePercentText")
            hookWithId(updatePercentTextMethod, "update_percent_text") { chain ->
                val result = chain.proceed()
                updatePercentageText(chain.thisObject)
                result
            }

            // Hook scaleBatteryMeterViews 方法，调整字体大小
            val scaleMethod: Method = batteryMeterViewClass.getDeclaredMethod("scaleBatteryMeterViews")
            hookWithId(scaleMethod, "scale") { chain ->
                val result = chain.proceed()
                adjustTextSize(chain.thisObject)
                result
            }

            logger.info("SystemUI电池百分比Hook模块加载成功")
        } catch (t: Throwable) {
            logger.error("SystemUI电池百分比Hook模块加载失败", t)
        }
    }

    private fun modifyBatteryLayout(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass

            // 获取关键的视图组件
            val container = cl.getDeclaredField("mBatteryPercentViewContainer")
                .get(batteryMeterView) as FrameLayout
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            if (container == null || percentView == null) {
                return
            }

            // 将百分比文本从 FrameLayout 中移除
            container.removeView(percentView)

            // 获取 LinearLayout 参数
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 设置左边距，让百分比显示在电池图标右侧
            val marginStart = getDimenValue(batteryMeterView)
            layoutParams.setMargins(marginStart, 0, 0, 0)

            // 将百分比文本直接添加到 BatteryMeterView (LinearLayout) 中
            val batteryView = batteryMeterView as LinearLayout
            batteryView.addView(percentView, 1, layoutParams) // 添加到索引1的位置（电池图标后面）

            // 调整字体大小
            adjustTextSize(batteryMeterView)

            logger.debug("电池布局修改完成")
        } catch (t: Throwable) {
            logger.error("电池布局修改失败", t)
        }
    }

    private fun adjustTextSize(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            if (percentView == null) {
                return
            }

            // 获取原始字体大小
            val originalSize = getOriginalTextSize(batteryMeterView)

            // 设置更大的字体大小（增加3sp）
            val newSize = originalSize + 3
            percentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSize)

            // 可选：设置粗体让文字更清晰
            percentView.setTypeface(percentView.typeface, Typeface.BOLD)

            logger.debug("电池百分比字体大小调整为 ${newSize}sp")
        } catch (t: Throwable) {
            logger.error("调整电池百分比字体大小失败", t)
        }
    }

    private fun getOriginalTextSize(batteryMeterView: Any): Float {
        try {
            // 获取系统默认的电池文字大小
            val context = getContext(batteryMeterView)
            if (context == null) return 13.0f
            val originalSizeRes = context.resources.getIdentifier(
                "status_bar_battery_text_size", "dimen", ScopeKeys.SYSTEM_UI.packageName
            )

            if (originalSizeRes != 0) {
                val sizeInPixels = context.resources.getDimension(originalSizeRes)
                // 将像素转换为sp
                return sizeInPixels / context.resources.displayMetrics.scaledDensity
            }
        } catch (t: Throwable) {
            logger.warn("获取原始电池字体大小失败，使用默认值")
        }

        // 默认值：12sp
        return 12f
    }

    private fun forceShowPercentage(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            if (percentView == null) {
                return
            }

            // 强制显示百分比视图，无论系统设置如何
            if (percentView.visibility != View.VISIBLE) {
                percentView.visibility = View.VISIBLE
            }

            // 更新百分比文本
            updatePercentageText(batteryMeterView)
        } catch (t: Throwable) {
            logger.error("强制显示电池百分比失败", t)
        }
    }

    private fun updatePercentageText(batteryMeterView: Any) {
        try {
            val cl = batteryMeterView.javaClass
            val percentView = cl.getDeclaredField("mBatteryPercentView")
                .get(batteryMeterView) as TextView

            if (percentView == null) {
                return
            }

            // 获取当前电量级别
            val level = cl.getDeclaredField("mLevel").getInt(batteryMeterView)

            // 设置百分比文本
            percentView.text = String.format(Locale.US, "%d%%", level)

            // 更新内容描述（辅助功能）
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
            logger.error("更新电池百分比文本失败", t)
        }
    }

    // 工具方法：获取维度值
    private fun getDimenValue(batteryMeterView: Any): Int {
        return try {
            val context = getContext(batteryMeterView)
            if (context == null) return 8
            val resId = context.resources.getIdentifier(
                "qs_battery_padding", "dimen", ScopeKeys.SYSTEM_UI.packageName
            )
            context.resources.getDimensionPixelOffset(resId)
        } catch (t: Throwable) {
            8 // 默认值
        }
    }

    // 工具方法：获取资源ID
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

    // 工具方法：获取Context
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
