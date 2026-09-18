package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger
import androidx.core.graphics.createBitmap

/**
 * Home screen icon unmask: after the Launcher3 icon factory flattens an icon,
 * rebuilds a bitmap from the original AdaptiveIconDrawable without shape masking
 * or IconNormalizer scaling.
 *
 * Entry point: BaseIconFactory#createBadgedIconBitmap(Drawable, IconOptions) after-hook.
 * Rebuilds only when the input is an AdaptiveIconDrawable; legacy icons keep the
 * system legacy fallback logic untouched. Dynamic icons (BitmapInfo.Extender
 * subclasses, e.g. clock ClockDrawableWrapper) are exempted by default because
 * rebuilding to a static bitmap would freeze the animation; when the sub-switch
 * LAUNCHER_APP_ICON_UNMASK_DYNAMIC is on they are rebuilt too, at the cost of a
 * frozen clock. The monochrome (themed) bitmap is rebuilt from the original
 * AdaptiveIcon via IconThemeController.createThemedBitmap; the rebuilt icon has
 * no shadow layer.
 *
 * Requires a launcher restart (AmStop) to take effect; the persistent icon cache
 * may require clearing Launcher data once.
 */
@SuppressLint("PrivateApi")
class LauncherAppIconUnmaskHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_APP_ICON_UNMASK.name

    /** Sub-switch: do not exempt dynamic icons. Read in handleLoadPackage per project rules, never inside a lambda. */
    @Volatile
    private var includeDynamicIcons = false

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (TARGET_PACKAGE != param.packageName) {
            return
        }
        val cl = param.defaultClassLoader
        includeDynamicIcons = remotePreferences.getBoolean(
            PreferenceKeys.LAUNCHER_APP_ICON_UNMASK_DYNAMIC.name,
            PreferenceKeys.LAUNCHER_APP_ICON_UNMASK_DYNAMIC.default
        )
        try {
            val factoryClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory")
            val optionsClass =
                cl.loadClass($$"com.android.launcher3.icons.BaseIconFactory$IconOptions")
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
     * Redraws an unmasked bitmap from the original AdaptiveIcon's fg/bg at full
     * bleed, and rebuilds the BitmapInfo with the original result's
     * color/flags/creationFlags.
     *
     * Returns (new BitmapInfo, bitmap edge length); returns null to keep the
     * original when any condition is unmet or on failure.
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
        // Dynamic icons such as clocks (BitmapInfo.Extender subclasses carrying extra
        // semantics) are exempted by default; when the sub-switch is on they are
        // rebuilt too (the animation will be frozen to a static bitmap)
        val bitmapInfoClass = cl.loadClass("com.android.launcher3.icons.BitmapInfo")
        if (!includeDynamicIcons && result.javaClass != bitmapInfoClass) {
            return null
        }
        val size = factory.javaClass
            .getMethod("getIconBitmapSize")
            .invoke(factory) as Int
        if (size <= 0) {
            return null
        }
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val bg = input.background
        val fg = input.foreground
        // Background fills the whole canvas at full bleed; the foreground is scaled up
        // by the official AdaptiveIcon 108/72 grid ratio to offset the extra downscale
        // caused by the system 66/72 visual safe area
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
        val newInfo: Any = of.invoke(null, bitmap, color) ?: return null
        // Preserve the original flags/creationFlags (work profile/clone badge semantics)
        bitmapInfoClass.getField("flags").setInt(newInfo, bitmapInfoClass.getField("flags").getInt(result))
        bitmapInfoClass.getField("creationFlags").setInt(
            newInfo, bitmapInfoClass.getField("creationFlags").getInt(result)
        )
        restoreThemedBitmap(cl, factory, input, newInfo, bitmapInfoClass)
        return Pair(newInfo, size)
    }

    /**
     * Rebuilds the monochrome (themed) bitmap from the original AdaptiveIcon via
     * IconThemeController, matching the same call inside the system
     * createBadgedIconBitmapZui. Silently skips when any step is unavailable.
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
            // Note: createThemedBitmap declares the parameter type as BaseIconFactory,
            // while the runtime instance is the subclass LauncherIcons; getMethod exact
            // matching must use the declared type
            val baseFactoryClass = cl.loadClass("com.android.launcher3.icons.BaseIconFactory")
            val create: Method = controller.javaClass.getMethod(
                "createThemedBitmap",
                AdaptiveIconDrawable::class.java, bitmapInfoClass,
                baseFactoryClass, sourceHintClass
            )
            val themed = create.invoke(controller, input, newInfo, factory, null) ?: return
            // setThemedBitmap declares the parameter as the parent ThemedBitmap while the
            // runtime instance is its subclass (e.g. MonoThemedBitmap); getMethod exact
            // matching must use the declared type
            val themedBitmapClass = cl.loadClass("com.android.launcher3.icons.ThemedBitmap")
            bitmapInfoClass
                .getMethod("setThemedBitmap", themedBitmapClass)
                .invoke(newInfo, themed)
        } catch (t: Throwable) {
            logger.debug("[unmask] themed bitmap restore skipped: " + t.message)
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.LAUNCHER.packageName

        /** AdaptiveIcon visible-area ratio: in the 72dp grid, 66dp is the system visible area; full bleed scales the foreground by its inverse. */
        private const val ADAPTIVE_VISIBLE_RATIO = 66f / 72f

        private const val LIMIT = 40
        private val logCounter = AtomicInteger(0)
    }
}
