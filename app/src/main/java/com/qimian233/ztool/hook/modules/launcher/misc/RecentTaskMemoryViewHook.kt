package com.qimian233.ztool.hook.modules.launcher.misc

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Locale
import java.util.WeakHashMap

@SuppressLint("PrivateApi")
class RecentTaskMemoryViewHook : AppHookModule() {

    private val updateRunnables = WeakHashMap<TextView, Runnable>()
    private val overviewEnabledStates = WeakHashMap<View, Boolean>()
    @Volatile
    private var cachedRamFormatter: String? = null
    @Volatile
    private var cachedRamUnavailable: String? = null
    @Volatile
    private var systemPropertiesGetWithDefaultMethod: Method? = null

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_RECENT_TASK_MEMORY_VIEW.name

    override fun getTargetPackages(): Array<String> = arrayOf(LAUNCHER_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            val recentsViewClass = classLoader.loadClass(RECENTS_VIEW_CLASS)

            val onAttachedMethod: Method =
                recentsViewClass.getDeclaredMethod("onAttachedToWindow")
            hookWithId(onAttachedMethod, "on_attached") { chain ->
                try {
                    chain.proceed()
                    attachMemoryView(chain.thisObject as View)
                    logger.debug("onAttachedToWindow hook executed successfully.")
                } catch (e: Exception) {
                    logger.error("Failed to hook onAttachedToWindow: ", e)
                }
                null
            }

            val onDetachedMethod: Method =
                recentsViewClass.getDeclaredMethod("onDetachedFromWindow")
            hookWithId(onDetachedMethod, "on_detached") { chain ->
                try {
                    detachMemoryView(chain.thisObject as View)
                    logger.info("onDetachedFromWindow hook executed successfully")
                } catch (e: Exception) {
                    logger.error("Failed to hook onDetachedFromWindow: ", e)
                }
                chain.proceed()
            }

            val setOverviewMethod: Method =
                recentsViewClass.getDeclaredMethod("setOverviewStateEnabled", Boolean::class.javaPrimitiveType)
            hookWithId(setOverviewMethod, "set_overview") { chain ->
                chain.proceed()
                val recentsView = chain.thisObject as View
                val enabled = chain.args[0] as Boolean
                overviewEnabledStates[recentsView] = enabled
                updateMemoryViewVisibility(recentsView)
                logger.info("setOverviewStateEnabled hook executed successfully")
                null
            }

            val setVisibilityMethod: Method =
                recentsViewClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            hookWithId(setVisibilityMethod, "set_visibility") { chain ->
                chain.proceed()
                updateMemoryViewVisibility(chain.thisObject as View)
                logger.info("setVisibility hook executed successfully")
                null
            }

            val onLayoutMethod: Method = recentsViewClass.getDeclaredMethod(
                "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            hookWithId(onLayoutMethod, "on_layout") { chain ->
                chain.proceed()
                attachMemoryView(chain.thisObject as View)
                logger.info("onLayout hook executed successfully")
                null
            }

            logger.info("Recent task memory view hooks installed")
        } catch (t: Throwable) {
            logger.error("Failed to install recent task memory view hooks", t)
        }
    }

    private fun attachMemoryView(recentsView: View) {
        try {
            val dragLayer = getDragLayer(recentsView)
            if (dragLayer == null) {
                return
            }

            var memoryView = findMemoryView(dragLayer)
            if (memoryView == null) {
                memoryView = createMemoryView(recentsView.context)
                dragLayer.addView(memoryView, createLayoutParams(recentsView.context))
                logger.debug("Memory view added to launcher drag layer")
            } else {
                ensureLayoutParams(memoryView, recentsView.context)
            }

            refreshMemoryText(memoryView)
            updateMemoryViewVisibility(recentsView, memoryView)
        } catch (t: Throwable) {
            logger.error("Failed to attach memory view", t)
        }
    }

    private fun detachMemoryView(recentsView: View) {
        try {
            val dragLayer = getDragLayer(recentsView)
            if (dragLayer == null) {
                return
            }

            val memoryView = findMemoryView(dragLayer)
            if (memoryView != null) {
                stopRefreshing(memoryView)
                dragLayer.removeView(memoryView)
                updateRunnables.remove(memoryView)
                logger.debug("Memory view removed from launcher drag layer")
            }
            overviewEnabledStates.remove(recentsView)
        } catch (t: Throwable) {
            logger.error("Failed to detach memory view", t)
        }
    }

    private fun createMemoryView(context: Context): TextView {
        val textView = TextView(context)
        textView.setTag(MEMORY_VIEW_TAG)
        textView.setTextColor(Color.argb(0xd9, 0xff, 0xff, 0xff))
        textView.alpha = 0.8f
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        textView.gravity = Gravity.CENTER
        textView.setSingleLine(true)
        textView.includeFontPadding = false
        textView.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
        textView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        textView.isClickable = false
        textView.isFocusable = false
        textView.elevation = dp(context, 4).toFloat()
        return textView
    }

    private fun createLayoutParams(context: Context): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.topMargin = dp(context, 26)
        params.leftMargin = dp(context, 16)
        return params
    }

    private fun ensureLayoutParams(memoryView: TextView, context: Context) {
        val currentParams = memoryView.layoutParams
        if (currentParams !is FrameLayout.LayoutParams) {
            memoryView.layoutParams = createLayoutParams(context)
            return
        }

        val params = currentParams
        val topMargin = dp(context, 52)
        val leftMargin = dp(context, 16)
        if (params.gravity != (Gravity.TOP or Gravity.START)
            || params.topMargin != topMargin
            || params.leftMargin != leftMargin
        ) {
            params.gravity = Gravity.TOP or Gravity.START
            params.topMargin = topMargin
            params.leftMargin = leftMargin
            params.bottomMargin = 0
            memoryView.layoutParams = params
        }
    }

    private fun getDragLayer(recentsView: View): ViewGroup? {
        try {
            val containerField = findField(recentsView.javaClass, "mContainer")
            val container = containerField.get(recentsView)
            if (container == null) {
                return null
            }
            val getDragLayerMethod = findMethod(container.javaClass, "getDragLayer")
            val dragLayer = getDragLayerMethod.invoke(container)
            return dragLayer as? ViewGroup
        } catch (t: Throwable) {
            logger.error("Exception happened in getDragLayer: ", t)
            return null
        }
    }

    private fun findMemoryView(dragLayer: ViewGroup): TextView? {
        val view = dragLayer.findViewWithTag<View>(MEMORY_VIEW_TAG)
        return view as? TextView
    }

    private fun updateMemoryViewVisibility(recentsView: View) {
        try {
            val dragLayer = getDragLayer(recentsView)
            if (dragLayer == null) {
                return
            }

            val memoryView = findMemoryView(dragLayer)
            if (memoryView != null) {
                updateMemoryViewVisibility(recentsView, memoryView)
            }
        } catch (t: Throwable) {
            logger.error("Failed to update memory view visibility", t)
        }
    }

    private fun updateMemoryViewVisibility(recentsView: View, memoryView: TextView) {
        val overviewEnabled = java.lang.Boolean.TRUE == overviewEnabledStates[recentsView]
        val visible = overviewEnabled && recentsView.visibility == View.VISIBLE
        memoryView.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            refreshMemoryText(memoryView)
        } else {
            stopRefreshing(memoryView)
        }
    }

    private fun refreshMemoryText(memoryView: TextView) {
        stopRefreshing(memoryView)
        updateMemoryText(memoryView)

        val updater = object : Runnable {
            override fun run() {
                if (!memoryView.isAttachedToWindow || memoryView.visibility != View.VISIBLE) {
                    return
                }
                updateMemoryText(memoryView)
                memoryView.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
        updateRunnables[memoryView] = updater

        if (memoryView.visibility == View.VISIBLE) {
            memoryView.postDelayed(updater, REFRESH_INTERVAL_MS)
        }
    }

    private fun stopRefreshing(memoryView: TextView) {
        val updater = updateRunnables.remove(memoryView)
        if (updater != null) {
            memoryView.removeCallbacks(updater)
        }
    }

    private fun updateMemoryText(memoryView: TextView) {
        try {
            val context = memoryView.context
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            if (activityManager == null) {
                memoryView.text = getRamUnavailableText(context)
                return
            }

            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val usedMemory = Math.max(0L, memoryInfo.totalMem - memoryInfo.availMem)
            memoryView.text = getRamFormatterText(
                context,
                formatBytesToGigSuffix(usedMemory),
                getTotalRamInfo(Math.max(0L, memoryInfo.totalMem))
            )
        } catch (t: Throwable) {
            memoryView.text = getRamUnavailableText(memoryView.context)
            logger.error("Failed to update memory text", t)
        }
    }

    private fun formatBytesToGigSuffix(bytes: Long): String {
        return String.format(Locale.US, "%.1f GB", formatBytes(bytes))
    }

    private fun formatBytesToGigSuffix(gig: String): String {
        return String.format(Locale.US, "%s GB", gig)
    }

    private fun formatBytes(bytes: Long): Double {
        return bytes / 1073741824.0
    }

    private fun dp(context: Context, value: Int): Int {
        return Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics
            )
        )
    }

    private fun getRamFormatterText(context: Context, vararg args: Any): String {
        var template = cachedRamFormatter
        if (template == null) {
            template = getModuleString(context, STRING_RAM_FORMATTER, FALLBACK_RAM_FORMATTER)
            cachedRamFormatter = template
        }
        return String.format(Locale.getDefault(), template, *args)
    }

    private fun getRamUnavailableText(context: Context): String {
        var value = cachedRamUnavailable
        if (value == null) {
            value = getModuleString(context, STRING_RAM_UNAVAILABLE, FALLBACK_RAM_UNAVAILABLE)
            cachedRamUnavailable = value
        }
        return value
    }

    private fun getModuleString(hostContext: Context, resourceName: String, fallback: String): String {
        try {
            val resources = getModuleResources(hostContext)
            if (resources == null) {
                return fallback
            }

            @SuppressLint("DiscouragedApi")
            val resId = resources.getIdentifier(resourceName, "string", MODULE_PACKAGE)
            if (resId == 0) {
                return fallback
            }
            return resources.getString(resId)
        } catch (t: Throwable) {
            logger.error("Failed to load module string: $resourceName", t)
            return fallback
        }
    }

    private fun getModuleResources(hostContext: Context): Resources? {
        return try {
            val moduleContext = hostContext.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
            moduleContext.resources
        } catch (t: Throwable) {
            logger.error("Failed to create module context for resources", t)
            null
        }
    }

    private fun getTotalRamInfo(availableMem: Long): String {
        val beautifyRamInfo = try {
            remotePreferences.getBoolean(PreferenceKeys.BEAUTIFY_RAM_INFO.name, false)
        } catch (_: Throwable) {
            false
        }
        if (!beautifyRamInfo) {
            return formatBytesToGigSuffix(availableMem)
        }

        val guessedRam = guessRamSize(availableMem)
        val expansionSize = getMemoryExpansionSize()
        if (expansionSize == null || expansionSize.isEmpty() || "0" == expansionSize) {
            logger.info("RAM expansion disabled, return guessed value")
            return guessedRam
        }
        return String.format(Locale.getDefault(), "%s + %s", guessedRam, normalizeExpansionSize(expansionSize))
    }

    private fun getMemoryExpansionSize(): String? {
        if ("true" != getSystemProperty(PROP_MEMORY_EXPANSION_ENABLED, "false")) {
            return null
        }

        val list = getSystemProperty(PROP_MEMORY_EXPANSION_LIST, "")
        if (list == null || list.isEmpty()) {
            return null
        }

        return getSystemProperty(PROP_MEMORY_EXPANSION_SIZE, "0")
    }

    private fun normalizeExpansionSize(size: String?): String {
        var value = size?.trim() ?: ""
        if (value.isEmpty()
            || "0" == value || value.equals("0G", ignoreCase = true) || value.equals("0GB", ignoreCase = true)
            || "0.0" == value || value.equals("0 G", ignoreCase = true) || value.equals("0 GB", ignoreCase = true)
        ) {
            return "0.0 GB"
        }
        value = value.replace("GB", "").replace("G", "").trim()
        return try {
            String.format(Locale.getDefault(), "%.1f GB", value.toDouble())
        } catch (t: Throwable) {
            logger.error("Failed to normalize expansion size: $size", t)
            if (size != null && size.endsWith("GB")) size else "$value.0 GB"
        }
    }

    private fun guessRamSize(availableMem: Long): String {
        val ramInGig = formatBytes(availableMem)
        val ramSizes = doubleArrayOf(
            1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0,
            14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0,
            28.0, 30.0, 32.0, 34.0, 36.0, 38.0, 40.0,
            42.0, 44.0, 46.0, 48.0, 50.0, 52.0, 54.0,
            56.0, 58.0, 60.0, 62.0, 64.0, 128.0
        )
        for (ramSize in ramSizes) {
            if (ramSize >= ramInGig) {
                return formatBytesToGigSuffix(String.format(Locale.US, "%.1f", ramSize))
            }
        }
        return formatBytesToGigSuffix(availableMem)
    }

    private fun getSystemProperty(key: String, defValue: String): String {
        try {
            val method = getSystemPropertiesGetWithDefaultMethod()
            if (method != null) {
                val result = method.invoke(null, key, defValue)
                return result as? String ?: defValue
            }
        } catch (t: Throwable) {
            logger.error("Failed to read system property: $key", t)
        }
        return defValue
    }

    private fun getSystemPropertiesGetWithDefaultMethod(): Method? {
        var method = systemPropertiesGetWithDefaultMethod
        if (method != null) {
            return method
        }
        synchronized(this) {
            method = systemPropertiesGetWithDefaultMethod
            if (method != null) {
                return method
            }
            try {
                val clz = Class.forName(SYSTEM_PROPERTIES_CLASS)
                method = clz.getMethod(SYSTEM_PROPERTIES_GET_WITH_DEFAULT, String::class.java, String::class.java)
                method.isAccessible = true
                systemPropertiesGetWithDefaultMethod = method
                return method
            } catch (t: Throwable) {
                logger.error("Failed to resolve SystemProperties.get(String, String)", t)
                return null
            }
        }
    }

    companion object {
        private const val MODULE_PACKAGE = "com.qimian233.ztool"
        private val LAUNCHER_PACKAGE = ScopeKeys.LAUNCHER.packageName
        private const val RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView"
        private const val MEMORY_VIEW_TAG = "ztool_recent_task_memory_view"
        private const val REFRESH_INTERVAL_MS = 1000L
        private const val STRING_RAM_FORMATTER = "ram_formatter"
        private const val STRING_RAM_UNAVAILABLE = "ram_unavailable"
        private const val FALLBACK_RAM_FORMATTER = "%s | %s"
        private const val FALLBACK_RAM_UNAVAILABLE = "-- | --"

        private const val PROP_MEMORY_EXPANSION_LIST = "persist.sys.zram_wb_list"
        private const val PROP_MEMORY_EXPANSION_ENABLED = "persist.sys.zram_wb_enabled"
        private const val PROP_MEMORY_EXPANSION_SIZE = "persist.sys.zram_wb_size"
        private const val SYSTEM_PROPERTIES_CLASS = "android.os.SystemProperties"
        private const val SYSTEM_PROPERTIES_GET_WITH_DEFAULT = "get"
    }
}
