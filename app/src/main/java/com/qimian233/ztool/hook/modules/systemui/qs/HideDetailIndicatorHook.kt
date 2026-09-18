package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Removes the detail-indicator ImageButtons from the control center, gated by
 * the single [PreferenceKeys.HIDE_DETAIL_INDICATOR] switch.
 *
 * - Large tiles: QSTileViewImpl.detailIndicatorView (side view). Small tiles
 *   are deliberately NOT covered — their labelDetailIndicatorView participates
 *   in QSAnimator alpha animation and hiding it has no visual benefit.
 * - Brightness slider: ToggleSliderView.mBrightnessDetailIndicator
 *
 * The tile indicator is (re)shown by loadSideViewDrawableIfNecessary on
 * every state change, so hiding happens AFTER that method runs rather than
 * once at init. The brightness indicator is only ever re-clickable via
 * onStateChanged (accessibility mode), so hiding it right after inflation
 * plus stripping clickability is enough; the surrounding TouchSeekBar also
 * routes its own open-brightness-detail tap through the indicator bounds,
 * which the long-press hook replaces.
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class HideDetailIndicatorHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.HIDE_DETAIL_INDICATOR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (!remotePreferences.getBoolean(PreferenceKeys.HIDE_DETAIL_INDICATOR.name, false)) {
            return
        }
        val classLoader = param.defaultClassLoader
        hideTileIndicators(classLoader)
        hideBrightnessIndicator(classLoader)
        logger.info("HideDetailIndicatorHook installed")
    }

    /**
     * Force GONE after every visibility decision the tile view makes. Only
     * the base implementation is hooked: it drives the large-tile side-view
     * indicator. Small tiles (CustomQSTileViewImpl) are out of scope.
     */
    private fun hideTileIndicators(classLoader: ClassLoader) {
        val stateClass = findQsTileStateClass(classLoader) ?: run {
            logger.warn("QSTile.State not found; tile indicator hooks skipped")
            return
        }
        try {
            val method: Method = classLoader.loadClass(QS_TILE_VIEW_CLASS)
                .getDeclaredMethod("loadSideViewDrawableIfNecessary", stateClass)
            hookWithId(method, "hide_indicator_$QS_TILE_VIEW_CLASS") { chain ->
                val result = chain.proceed()
                hideIndicator(chain.thisObject)
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook loadSideViewDrawableIfNecessary on $QS_TILE_VIEW_CLASS", t)
        }
    }

    private fun hideIndicator(tileView: Any) {
        try {
            val view = findField(tileView.javaClass, TILE_INDICATOR_FIELD).get(tileView) as? ImageView
            if (view != null && view.visibility != View.GONE) {
                // Record that the system considered this a dual-target tile
                // before we hide the button: the long-press hook uses the tag
                // to keep routing long presses to performClick() (a GONE view
                // can still be clicked programmatically), instead of falling
                // through to the native long-click listener.
                view.tag = DUAL_TARGET_TAG
                view.visibility = View.GONE
                view.isClickable = false
                view.isFocusable = false
            }
        } catch (_: Throwable) {
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
        const val QS_TILE_VIEW_CLASS = "com.android.systemui.qs.tileimpl.QSTileViewImpl"
        const val QS_TILE_STATE_CLASS = "com.android.systemui.plugins.qs.QSTile\$State"
        const val TILE_INDICATOR_FIELD = "detailIndicatorView"
        const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        const val BRIGHTNESS_INDICATOR_FIELD = "mBrightnessDetailIndicator"

        /** Marker consumed by ControlCenterLongPressHook.findDetailIndicator. */
        const val DUAL_TARGET_TAG = "ztool_dual_target_indicator"
    }
}
