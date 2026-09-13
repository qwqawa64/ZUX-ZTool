package com.qimian233.ztool.hook.modules.launcher

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * 测试性探针 Hook：确定 ZUI Launcher 桌面图标的蒙版/展平出现在管线哪一层。
 *
 * 探测点（自上游到下游）：
 *  1. com.android.launcher3.icons.GraphicsUtils#getZuiThemeAdaptiveIconDrawable
 *     —— ZUI 主题引擎（ExtraResources）对图标的替换是否发生
 *  2. com.android.launcher3.icons.BaseIconFactory#normalizeAndWrapToAdaptiveIcon
 *     —— 传统图标被包底 / 归一化的形态
 *  3. com.android.launcher3.icons.BaseIconFactory#createBadgedIconBitmapZui / createBadgedIconBitmap
 *     —— 最终展平为 BitmapInfo 的位置，记录输出位图四角 alpha 判断形状蒙版
 *
 * 本探针只记录不替换。确认注入点后再转正式功能。
 */
class LauncherAppIconUnmaskProbe : AppHookModule() {

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (TARGET_PACKAGE != param.packageName) {
            return
        }
        val cl = param.defaultClassLoader

        hookZuiThemeDrawable(cl)
        hookNormalizeAndWrap(cl)
        hookCreateBadgedIconBitmapZui(cl)
        hookCreateBadgedIconBitmap(cl)
    }

    /**
     * 探针 1：ZUI 主题引擎替换。返回值 != 入参 即说明主题层换掉了原始图标，
     * 这是第三方图标包内容被二次加工的候选位置。
     */
    private fun hookZuiThemeDrawable(cl: ClassLoader) {
        try {
            val m: Method = cl.loadClass("com.android.launcher3.icons.GraphicsUtils")
                .getDeclaredMethod(
                    "getZuiThemeAdaptiveIconDrawable", Drawable::class.java, Int::class.javaPrimitiveType
                )
            hookWithId(m, "launcher_icon_probe_zui_theme") { chain ->
                val input = chain.args[0] as? Drawable
                val result = chain.proceed()
                try {
                    if (counter1.getAndIncrement() < LIMIT) {
                        val replaced = result != null && result !== input
                        val inName = simple(input)
                        val outName = simple(result as? Drawable)
                        logger.info(
                            "[lp1 zuiTheme] in=" + inName
                                + " replaced=" + replaced
                                + " out=" + outName
                        )
                    }
                } catch (t: Throwable) {
                    logger.warn("[lp1] logging failed: " + t.message)
                }
                result
            }
            logger.info("Probe1 hooked GraphicsUtils#getZuiThemeAdaptiveIconDrawable.")
        } catch (t: Throwable) {
            logger.warn("Probe1 failed to hook getZuiThemeAdaptiveIconDrawable: " + t.message)
        }
    }

    /** 探针 2：传统图标归一化包装，记录包装前后的类型。 */
    private fun hookNormalizeAndWrap(cl: ClassLoader) {
        try {
            val m: Method = cl.loadClass("com.android.launcher3.icons.BaseIconFactory")
                .getDeclaredMethod(
                    "normalizeAndWrapToAdaptiveIcon", Drawable::class.java, FloatArray::class.java
                )
            hookWithId(m, "launcher_icon_probe_normalize") { chain ->
                val input = chain.args[0] as? Drawable
                val result = chain.proceed()
                try {
                    if (counter2.getAndIncrement() < LIMIT) {
                        val inName = simple(input)
                        val outName = simple(result as? Drawable)
                        logger.info(
                            "[lp2 normalize] in=" + inName + " out=" + outName
                        )
                    }
                } catch (t: Throwable) {
                    logger.warn("[lp2] logging failed: " + t.message)
                }
                result
            }
            logger.info("Probe2 hooked BaseIconFactory#normalizeAndWrapToAdaptiveIcon.")
        } catch (t: Throwable) {
            logger.warn("Probe2 failed to hook normalizeAndWrapToAdaptiveIcon: " + t.message)
        }
    }

    /** 探针 3：ZUI 主路径展平点，记录输入类型与输出位图四角 alpha。 */
    private fun hookCreateBadgedIconBitmapZui(cl: ClassLoader) {
        try {
            val factoryClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory")
            val optionsClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory\$IconOptions")
            val m: Method = factoryClass.getDeclaredMethod(
                "createBadgedIconBitmapZui", Drawable::class.java, optionsClass
            )
            hookWithId(m, "launcher_icon_probe_badged_zui") { chain ->
                val input = chain.args[0] as? Drawable
                val result = chain.proceed()
                try {
                    if (counter3.getAndIncrement() < LIMIT) {
                        logger.info(
                            "[lp3 badgedZui] in=" + simple(input) + " -> " + describeBitmapInfo(result)
                        )
                    }
                } catch (t: Throwable) {
                    logger.warn("[lp3] logging failed: " + t.message)
                }
                result
            }
            logger.info("Probe3 hooked BaseIconFactory#createBadgedIconBitmapZui.")
        } catch (t: Throwable) {
            logger.warn("Probe3 failed to hook createBadgedIconBitmapZui: " + t.message)
        }
    }

    /** 探针 4：AOSP 标准路径展平点（若 ZUI 走 Zui 变体则此探针可能静默，无碍）。 */
    private fun hookCreateBadgedIconBitmap(cl: ClassLoader) {
        try {
            val factoryClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory")
            val optionsClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory\$IconOptions")
            val m: Method = factoryClass.getDeclaredMethod(
                "createBadgedIconBitmap", Drawable::class.java, optionsClass
            )
            hookWithId(m, "launcher_icon_probe_badged") { chain ->
                val input = chain.args[0] as? Drawable
                val result = chain.proceed()
                try {
                    if (counter4.getAndIncrement() < LIMIT) {
                        logger.info(
                            "[lp4 badged] in=" + simple(input) + " -> " + describeBitmapInfo(result)
                        )
                    }
                } catch (t: Throwable) {
                    logger.warn("[lp4] logging failed: " + t.message)
                }
                result
            }
            logger.info("Probe4 hooked BaseIconFactory#createBadgedIconBitmap.")
        } catch (t: Throwable) {
            logger.warn("Probe4 failed to hook createBadgedIconBitmap: " + t.message)
        }
    }

    /** 反射读取 BitmapInfo.icon 位图，输出尺寸与四角 alpha（形状蒙版会把四角裁到 0）。 */
    private fun describeBitmapInfo(info: Any?): String {
        if (info == null) {
            return "null"
        }
        return try {
            val bmp = info.javaClass.getField("icon").get(info) as? Bitmap
            if (bmp == null) {
                "BitmapInfo[icon=null]"
            } else {
                "BitmapInfo[" + bitmapCorners(bmp) + "]"
            }
        } catch (t: Throwable) {
            "BitmapInfo[" + info.javaClass.simpleName + ", fields unreadable: " + t.message + "]"
        }
    }

    private fun bitmapCorners(bmp: Bitmap): String {
        if (bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) {
            return "bitmap invalid"
        }
        if (bmp.config == null) {
            return "hardware bitmap, alpha unreadable"
        }
        val w = bmp.width
        val h = bmp.height
        val tl = bmp.getPixel(0, 0) ushr 24
        val tr = bmp.getPixel(w - 1, 0) ushr 24
        val bl = bmp.getPixel(0, h - 1) ushr 24
        val br = bmp.getPixel(w - 1, h - 1) ushr 24
        return "size=" + w + "x" + h + " cornerAlpha=(" + tl + "," + tr + "," + bl + "," + br + ")"
    }

    private fun simple(d: Drawable?): String {
        if (d == null) {
            return "null"
        }
        return if (d is AdaptiveIconDrawable) {
            val fg = d.foreground?.javaClass?.simpleName ?: "null"
            val bg = d.background?.javaClass?.simpleName ?: "null"
            "AdaptiveIcon[fg=" + fg + ", bg=" + bg + "]"
        } else {
            d.javaClass.simpleName
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.LAUNCHER.packageName
        private const val LIMIT = 40

        private val counter1 = AtomicInteger(0)
        private val counter2 = AtomicInteger(0)
        private val counter3 = AtomicInteger(0)
        private val counter4 = AtomicInteger(0)
    }
}
