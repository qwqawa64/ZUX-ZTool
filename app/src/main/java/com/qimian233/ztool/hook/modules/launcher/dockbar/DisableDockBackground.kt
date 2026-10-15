package com.qimian233.ztool.hook.modules.launcher.dockbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Removes the dock background (plate, border and split line) while keeping the dock icons.
 *
 * The background is two layers - a rounded rect painted by ZuiHotseat and a window blur
 * region - so two hook points are used. Field names come from the offline index plus
 * runtime predicates. See docs/research/zui_launcher_dock_background.md.
 */
@SuppressLint("PrivateApi")
class DisableDockBackground : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_DOCK_BACKGROUND.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        // Resolved here, not inside the hook lambda: the index read does remote-file I/O.
        val paintHints = readDockPaintFieldHints()
        hookBackgroundPaints(classLoader, paintHints)
        hookBlurProvider(classLoader)
    }

    /** Layer 1: zero the alpha of the dock's own paints once the view is constructed. */
    private fun hookBackgroundPaints(classLoader: ClassLoader, hints: Set<String>) {
        val zuiHotseat = try {
            classLoader.loadClass(ZUI_HOTSEAT_CLASS)
        } catch (t: Throwable) {
            logger.error("DisableDockBackground: ZuiHotseat not found", t)
            return
        }
        val constructor = try {
            zuiHotseat.getDeclaredConstructor(
                Context::class.java,
                AttributeSet::class.java,
                Int::class.javaPrimitiveType
            )
        } catch (t: Throwable) {
            logger.error("DisableDockBackground: ZuiHotseat constructor not found", t)
            return
        }
        hookWithId(constructor, "dock_background_paints") { chain ->
            chain.proceed()
            try {
                clearBackgroundPaints(chain.thisObject, hints)
            } catch (t: Throwable) {
                logger.error("DisableDockBackground: paint resolution failed", t)
            }
            null
        }
        logger.debug("DisableDockBackground: paint hook installed")
    }

    /** Layer 2: never register the hotseat as a launcher blur-area provider. */
    private fun hookBlurProvider(classLoader: ClassLoader) {
        val add = try {
            val launcher = classLoader.loadClass(LAUNCHER_CLASS)
            val provider = classLoader.loadClass(BLUR_AREA_PROVIDER_CLASS)
            val hotseat = classLoader.loadClass(HOTSEAT_CLASS)
            resolveAddBlurAreaProvider(launcher, provider) to hotseat
        } catch (t: Throwable) {
            logger.error("DisableDockBackground: addBlurAreaProvider not resolved", t)
            return
        }
        hookWithId(add.first, "dock_blur_provider") { chain ->
            if (add.second.isInstance(chain.args[0])) null else chain.proceed()
        }
        logger.debug("DisableDockBackground: blur provider hook installed")
    }

    /**
     * Clears the dock's paints on a freshly constructed hotseat.
     *
     * Candidates come from the field type, then structural predicates pick the border, the
     * split line and the fill. The dock fill and the edit-mode empty-cell fill are identical
     * in every object-local property, so [hints] breaks that tie. Anything short of three
     * distinct fields clears every Paint field instead of guessing.
     */
    private fun clearBackgroundPaints(instance: Any, hints: Set<String>) {
        val paints = instance.javaClass.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && Paint::class.java.isAssignableFrom(it.type) }
            .mapNotNull { field ->
                field.isAccessible = true
                (field.get(instance) as? Paint)?.let { field to it }
            }
        if (paints.isEmpty()) {
            logger.warn("DisableDockBackground: no Paint fields on ${instance.javaClass.name}")
            return
        }

        val border = paints.singleOrNull { (_, p) ->
            p.style == Paint.Style.STROKE && p.strokeWidth > 0f
        }
        val splitLine = paints.singleOrNull { (_, p) ->
            p.style == Paint.Style.FILL && p.strokeWidth > 0f
        }
        val fills = paints.filter { (_, p) ->
            p.style == Paint.Style.FILL && p.strokeWidth == 0f
        }
        val fill = fills.singleOrNull { (f, _) -> f.name in hints } ?: fills.singleOrNull()

        val resolved = listOfNotNull(fill, border, splitLine)
            .takeIf { it.size == 3 && it.map { (f, _) -> f.name }.toSet().size == 3 }

        val chosen = resolved ?: paints
        if (resolved == null) {
            logger.warn("DisableDockBackground: ambiguous paint set, clearing all ${paints.size} fields")
        }
        for ((field, paint) in chosen) {
            paint.alpha = 0
            logger.debug("DisableDockBackground: cleared ${field.name}")
        }
    }

    /** Prefers the host's own method name; falls back to the single matching signature. */
    @Throws(NoSuchMethodException::class)
    private fun resolveAddBlurAreaProvider(launcher: Class<*>, provider: Class<*>): Method {
        try {
            return findMethod(launcher, "addBlurAreaProvider", provider)
        } catch (_: NoSuchMethodException) {
            return launcher.declaredMethods.singleOrNull { candidate ->
                candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == provider
            } ?: throw NoSuchMethodException("addBlurAreaProvider(${provider.name}) in ${launcher.name}")
        }
    }

    /** Comma-separated field-name hints from the offline index; empty when unavailable. */
    private fun readDockPaintFieldHints(): Set<String> {
        val raw = try {
            DexIndexStore.string(
                xposed,
                ScopeKeys.LAUNCHER.packageName,
                DexIndexConstants.ModuleKeys.DISABLE_DOCK_BACKGROUND,
                DexIndexConstants.Keys.DOCK_PAINT_FIELDS
            )
        } catch (_: Throwable) {
            null
        } ?: return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private companion object {
        const val ZUI_HOTSEAT_CLASS = "com.zui.launcher.uiextend.ZuiHotseat"
        const val HOTSEAT_CLASS = "com.android.launcher3.Hotseat"
        const val LAUNCHER_CLASS = "com.android.launcher3.Launcher"
        const val BLUR_AREA_PROVIDER_CLASS = "com.zui.launcher.blur.LauncherBlurAreaProvider"
    }
}
