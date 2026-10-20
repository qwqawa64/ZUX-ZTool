package com.qimian233.ztool.hook.modules.desktoplauncher

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicInteger

/**
 * Appends the portrait window bound the work desktop refuses to offer.
 *
 * ZUI's `WindowBounds(Rect, Rect)` constructor rewrites any portrait rect into a landscape one
 * and pins `rotationHint` to 1, so `DisplayController.Info.supportedBounds` only ever holds a
 * landscape entry and every `DeviceProfile` is built landscape. This module adds the real
 * portrait bound to that set; the launcher's own per-orientation slots then do the rest.
 *
 * Gated by [PreferenceKeys.WORK_MODE_PORTRAIT]; see docs/research/zui_work_mode_desktop_hook_survey.md
 */
class WorkDesktopPortrait : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.WORK_MODE_PORTRAIT.name

    override fun getTargetPackages(): Array<String> =
        arrayOf(ScopeKeys.DESKTOP_LAUNCHER.packageName)

    private val injected = AtomicInteger(0)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        runCatching {
            val windowBoundsClass = classLoader.loadClass(WINDOW_BOUNDS_CLASS)
            val infoClass = classLoader.loadClass(DISPLAY_INFO_CLASS)
            val ctor = infoClass.getDeclaredConstructor(
                Context::class.java,
                classLoader.loadClass(WINDOW_MANAGER_PROXY_CLASS),
                Map::class.java
            )
            ctor.isAccessible = true
            val portraitBoundsCtor = windowBoundsClass.getConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val supportedBoundsField = findField(infoClass, "supportedBounds")
            val currentSizeField = findField(infoClass, "currentSize")
            val rotationField = findField(infoClass, "rotation")
            val realBoundsField = findField(infoClass, "realBounds")
            val boundsField = findField(windowBoundsClass, "bounds")
            val insetsField = findField(windowBoundsClass, "insets")

            hookWithId(ctor, "work_desktop_portrait_bounds") { chain ->
                chain.proceed()
                runCatching {
                    injectPortraitBound(
                        info = chain.thisObject,
                        context = chain.args[0] as? Context,
                        supportedBoundsField = supportedBoundsField,
                        currentSizeField = currentSizeField,
                        rotationField = rotationField,
                        realBoundsField = realBoundsField,
                        boundsField = boundsField,
                        insetsField = insetsField,
                        portraitBoundsCtor = portraitBoundsCtor
                    )
                }.onFailure { logger.error("WorkDesktopPortrait: injection failed", it) }
                null
            }
            logger.info("WorkDesktopPortrait installed")
        }.onFailure { logger.error("WorkDesktopPortrait: hook not installed", it) }
    }

    private fun injectPortraitBound(
        info: Any?,
        context: Context?,
        supportedBoundsField: Field,
        currentSizeField: Field,
        rotationField: Field,
        realBoundsField: Field,
        boundsField: Field,
        insetsField: Field,
        portraitBoundsCtor: Constructor<*>
    ) {
        if (info == null || context == null) return
        val size = currentSizeField.get(info) as? Point ?: return
        if (size.y <= size.x) return
        if (!isPcMode(context)) return
        @Suppress("UNCHECKED_CAST")
        val supported = supportedBoundsField.get(info) as? MutableCollection<Any> ?: return
        if (supported.any { (boundsField.get(it) as? Rect)?.let { r -> r.height() > r.width() } == true }) {
            return
        }
        val realInsets = (realBoundsField.get(info)?.let { insetsField.get(it) } as? Rect) ?: Rect()
        val available = Point(
            size.x - realInsets.left - realInsets.right,
            size.y - realInsets.top - realInsets.bottom
        )
        val rotation = rotationField.getInt(info)
        val bound = portraitBoundsCtor.newInstance(
            size.x, size.y, available.x, available.y, rotation
        )
        supported.add(bound)
        if (injected.incrementAndGet() <= MAX_LOG_LINES) {
            logger.info(
                "WorkDesktopPortrait: added bound ${size.x}x${size.y}" +
                    " available=${available.x}x${available.y} rotation=$rotation"
            )
        }
    }

    private fun isPcMode(context: Context): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, KEY_PC_MODE, 0) == 1
    }.getOrDefault(false)

    private companion object {
        const val DISPLAY_INFO_CLASS = "com.zui.desktoplauncher.util.DisplayController\$Info"
        const val WINDOW_BOUNDS_CLASS = "com.zui.desktoplauncher.util.WindowBounds"
        const val WINDOW_MANAGER_PROXY_CLASS =
            "com.zui.desktoplauncher.util.window.WindowManagerProxy"
        const val KEY_PC_MODE = "zui_pc_mode"
        const val MAX_LOG_LINES = 6
    }
}
