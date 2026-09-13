package com.qimian233.ztool.hook.modules.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * 桌面图标去蒙版：在 Launcher3 图标工厂展平之后，用原始 AdaptiveIconDrawable
 * 重建无形状蒙版、无 IconNormalizer 缩放的位图。
 *
 * 切入点：BaseIconFactory#createBadgedIconBitmap(Drawable, IconOptions) after-hook。
 * 仅当输入是 AdaptiveIconDrawable 且输出是纯 BitmapInfo（非 Extender 子类、
 * 非时钟动态图标）时重建；传统图标保持系统 legacy 垫底逻辑不动。
 *
 * 已知取舍：重建后的图标不带阴影层；monochrome（themed）位图通过
 * IconThemeController.createThemedBitmap 用原始 AdaptiveIcon 重建。
 *
 * getModuleName() 返回 "hook_test"，无前端开关即可启用；后续转正式功能时
 * 替换为 PreferenceKeys 键名并补前端开关。
 */
class LauncherAppIconUnmaskHook : AppHookModule() {

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (TARGET_PACKAGE != param.packageName) {
            return
        }
        val cl = param.defaultClassLoader
        try {
            val factoryClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory")
            val optionsClass =
                cl.loadClass("com.android.launcher3.icons.BaseIconFactory\$IconOptions")
            val m: Method = factoryClass.getDeclaredMethod(
                "createBadgedIconBitmap", Drawable::class.java, optionsClass
            )
            hookWithId(m, "launcher_icon_unmask_badged") { chain ->
                val result = chain.proceed()
                try {
                    val input = chain.args[0] as? AdaptiveIconDrawable
                    if (input != null) {
                        val replaced = rebuildUnmasked(chain.thisObject, input, result, cl)
                        if (replaced != null) {
                            if (logCounter.getAndIncrement() < LIMIT) {
                                logger.info(
                                    "[unmask] rebuilt unmasked icon: in="
                                        + input.javaClass.simpleName
                                        + " bitmapSize=" + replaced.second
                                )
                            }
                            return@hookWithId replaced.first
                        }
                    }
                } catch (t: Throwable) {
                    logger.warn("[unmask] rebuild failed, keep original: " + t.message)
                }
                result
            }
            logger.info("Hooked BaseIconFactory#createBadgedIconBitmap for icon unmask.")
        } catch (t: Throwable) {
            logger.warn("Failed to hook createBadgedIconBitmap: " + t.message)
        }
    }

    /**
     * 用原始 AdaptiveIcon 的 fg/bg 全出血重画一张无蒙版位图，并按原 result 的
     * color/flags/creationFlags 重建 BitmapInfo。
     *
     * 返回 (新BitmapInfo, 位图边长)；任何条件不满足或失败都返回 null 保持原样。
     */
    private fun rebuildUnmasked(
        factory: Any?,
        input: AdaptiveIconDrawable,
        result: Any?,
        cl: ClassLoader
    ): Pair<Any, Int>? {
        if (factory == null || result == null) {
            return null
        }
        // 时钟等动态图标（BitmapInfo.Extender 子类承载额外语义）一律放行
        val bitmapInfoClass = cl.loadClass("com.android.launcher3.icons.BitmapInfo")
        if (result.javaClass != bitmapInfoClass) {
            return null
        }
        val size = factory.javaClass
            .getMethod("getIconBitmapSize")
            .invoke(factory) as Int
        if (size <= 0) {
            return null
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = input.background
        val fg = input.foreground
        // 背景全出血铺满；前景按 AdaptiveIcon 官方 108/72 网格比例放大，
        // 抵消系统 66/72 视觉安全区造成的二次缩小
        val bounds = android.graphics.Rect(0, 0, size, size)
        if (bg != null) {
            bg.bounds = bounds
            bg.draw(canvas)
        }
        if (fg != null) {
            val pad = (size * (1f - ADAPTIVE_VISIBLE_RATIO) / 2f).toInt()
            val fgBounds = android.graphics.Rect(-pad, -pad, size + pad, size + pad)
            fg.bounds = fgBounds
            fg.draw(canvas)
        }
        val of: Method = bitmapInfoClass.getDeclaredMethod("of", Bitmap::class.java, Int::class.javaPrimitiveType)
        val color = bitmapInfoClass.getField("color").getInt(result)
        val newInfo = of.invoke(null, bitmap, color)
        // 保留原 flags/creationFlags（工作资料/克隆角标等语义）
        bitmapInfoClass.getField("flags").setInt(newInfo, bitmapInfoClass.getField("flags").getInt(result))
        bitmapInfoClass.getField("creationFlags").setInt(
            newInfo, bitmapInfoClass.getField("creationFlags").getInt(result)
        )
        restoreThemedBitmap(cl, factory, input, newInfo, bitmapInfoClass)
        return Pair(newInfo, size)
    }

    /**
     * 用原始 AdaptiveIcon 通过 IconThemeController 重建 monochrome（themed）位图，
     * 对齐系统 createBadgedIconBitmapZui 内的同名调用。任何一步不可用都静默跳过。
     */
    private fun restoreThemedBitmap(
        cl: ClassLoader,
        factory: Any,
        input: AdaptiveIconDrawable,
        newInfo: Any,
        bitmapInfoClass: Class<*>
    ) {
        try {
            val providerClass = cl.loadClass("com.android.launcher3.icons.IconProvider")
            val atLeastT = providerClass.getField("ATLEAST_T").getBoolean(null)
            if (!atLeastT) {
                return
            }
            val controller = factory.javaClass
                .getMethod("getThemeController").invoke(factory) ?: return
            val sourceHintClass = try {
                cl.loadClass("com.android.launcher3.icons.SourceHint")
            } catch (_: ClassNotFoundException) {
                return
            }
            // 注意：createThemedBitmap 声明的参数类型是 BaseIconFactory，
            // 运行时实例是子类 LauncherIcons，getMethod 精确匹配必须用声明类型
            val baseFactoryClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory")
            val create: Method = controller.javaClass.getMethod(
                "createThemedBitmap",
                AdaptiveIconDrawable::class.java, bitmapInfoClass,
                baseFactoryClass, sourceHintClass
            )
            val themed = create.invoke(controller, input, newInfo, factory, null) ?: return
            bitmapInfoClass
                .getMethod("setThemedBitmap", themed.javaClass)
                .invoke(newInfo, themed)
        } catch (t: Throwable) {
            logger.debug("[unmask] themed bitmap restore skipped: " + t.message)
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.LAUNCHER.packageName

        /** AdaptiveIcon 可视区占比：72dp 网格中 66dp 是系统可视区，全出血用其倒数放大前景。 */
        private const val ADAPTIVE_VISIBLE_RATIO = 66f / 72f

        private const val LIMIT = 40
        private val logCounter = AtomicInteger(0)
    }
}
