package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * TEST HOOK (enabled via getModuleName() returning a test marker): removes
 * the detail-indicator ImageButtons from the control center.
 *
 * - Large tiles: QSTileViewImpl.detailIndicatorView (side view)
 * - Small tiles: CustomQSTileViewImpl.labelDetailIndicatorView (label row)
 * - Brightness slider: ToggleSliderView.mBrightnessDetailIndicator
 *
 * The tile indicators are (re)shown by loadSideViewDrawableIfNecessary on
 * every state change, so hiding happens AFTER that method runs rather than
 * once at init. The brightness indicator is only ever re-clickable via
 * onStateChanged (accessibility mode), so hiding it right after inflation
 * plus stripping clickability is enough; the surrounding TouchSeekBar also
 * routes its own open-brightness-detail tap through the indicator bounds,
 * which the long-press hook replaces.
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class HideDetailIndicatorHook : AppHookModule() {

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hideTileIndicators(classLoader)
        hideBrightnessIndicator(classLoader)
        logger.info("HideDetailIndicatorHook installed")
    }

    /**
     * Force GONE after every visibility decision the tile view makes. Hooking
     * both implementations covers large (side view) and small (label row)
     * tiles; the base method also serves tiles that don't override it.
     */
    private fun hideTileIndicators(classLoader: ClassLoader) {
        val stateClass = findQsTileStateClass(classLoader) ?: run {
            logger.warn("QSTile.State not found; tile indicator hooks skipped")
            return
        }
        for (className in TILE_VIEW_CLASSES) {
            try {
                val method: Method = classLoader.loadClass(className)
                    .getDeclaredMethod("loadSideViewDrawableIfNecessary", stateClass)
                hookWithId(method, "hide_indicator_$className") { chain ->
                    val result = chain.proceed()
                    hideIndicator(chain.thisObject)
                    result
                }
            } catch (t: Throwable) {
                logger.error("Failed to hook loadSideViewDrawableIfNecessary on $className", t)
            }
        }
    }

    private fun hideIndicator(tileView: Any) {
        for (fieldName in INDICATOR_FIELDS) {
            try {
                val view = findField(tileView.javaClass, fieldName).get(tileView) as? ImageView
                if (view != null && view.visibility != View.GONE) {
                    view.visibility = View.GONE
                    view.isClickable = false
                    view.isFocusable = false
                }
            } catch (_: Throwable) {
                // Field absent on this tile variant — expected, try next.
            }
        }
    }

    /**
     * The brightness slider's indicator is inflated with the layout and never
     * re-shown except in accessibility mode; hide it right after the host is
     * constructed and strip clickability so the reserved tap zone goes away.
     */
    private fun hideBrightnessIndicator(classLoader: ClassLoader) {
        try {
            val ctor = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredConstructor(
                    android.content.Context::class.java,
                    android.util.AttributeSet::class.java,
                    Int::class.javaPrimitiveType
                )
            hookWithId(ctor, "hide_brightness_indicator_ctor") { chain ->
                chain.proceed()
                try {
                    val host = chain.thisObject
                    val view = findField(host.javaClass, BRIGHTNESS_INDICATOR_FIELD)
                        .get(host) as? ImageView
                    if (view != null) {
                        view.visibility = View.GONE
                        view.isClickable = false
                        view.isFocusable = false
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to hide brightness detail indicator", t)
                }
                null
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook ToggleSliderView ctor for indicator hiding", t)
        }
    }

    private fun findQsTileStateClass(classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(QS_TILE_STATE_CLASS)
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        val TILE_VIEW_CLASSES = arrayOf(
            "com.android.systemui.qs.tileimpl.QSTileViewImpl",
            "com.android.systemui.qs.tileimpl.CustomQSTileViewImpl"
        )
        const val QS_TILE_STATE_CLASS = "com.android.systemui.plugins.qs.QSTile\$State"
        val INDICATOR_FIELDS = arrayOf("detailIndicatorView", "labelDetailIndicatorView")
        const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        const val BRIGHTNESS_INDICATOR_FIELD = "mBrightnessDetailIndicator"
    }
}
