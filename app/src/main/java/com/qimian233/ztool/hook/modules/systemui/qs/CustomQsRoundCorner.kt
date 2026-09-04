package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.widget.ProgressBar
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class CustomQsRoundCorner : AppHookModule() {

    private var headUpTileRoundCornerRadius = 32
    private var normalTileRoundCornerRadius = 96

    override fun getModuleName(): String = PreferenceKeys.QS_ROUND_CORNER.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        updateRoundCornerPrefs()

        // Head-up tiles
        val changeCornerRadiusMethod: Method = classLoader
            .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
            .getDeclaredMethod("changeCornerRadius", Float::class.javaPrimitiveType)
        hookWithId(changeCornerRadiusMethod, "change_corner_radius") { chain ->
            chain.proceed(arrayOf(headUpTileRoundCornerRadius.toFloat()))
        }

        // Normal tiles
        val updateRippleRadiusMethod: Method = classLoader
            .loadClass("com.android.systemui.qs.tileimpl.CustomQSTileViewImpl")
            .getDeclaredMethod("updateRippleRadius")
        hookWithId(updateRippleRadiusMethod, "update_ripple_radius") { chain ->
            val result = chain.proceed()
            try {
                val cl = chain.thisObject.javaClass

                val rippleDrawable =
                    findField(cl, "qsTileBackground").get(chain.thisObject) as? RippleDrawable

                if (rippleDrawable != null) {
                    val mask = rippleDrawable.findDrawableByLayerId(android.R.id.mask)
                    if (mask is GradientDrawable) {
                        mask.cornerRadius = normalTileRoundCornerRadius.toFloat()
                    }
                }

                val backgroundDrawable =
                    findField(cl, "backgroundDrawable").get(chain.thisObject) as? LayerDrawable

                if (backgroundDrawable != null) {
                    val count = backgroundDrawable.numberOfLayers
                    for (i in 0 until count) {
                        val layer = backgroundDrawable.getDrawable(i)
                        if (layer is GradientDrawable) {
                            layer.cornerRadius = normalTileRoundCornerRadius.toFloat()
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Cannot set normal tile round corner radius!", e)
            }
            result
        }

        // Sliders
        val toggleSliderViewClass = classLoader.loadClass("com.android.systemui.settings.ToggleSliderView")

        val refreshSeekBarMethod: Method =
            toggleSliderViewClass.getDeclaredMethod("refreshSeekBar", ProgressBar::class.java)
        hookWithId(refreshSeekBarMethod, "refresh_seek_bar") { chain ->
            logger.debug("refreshSeekBar afterHookedMethod called!")
            val result = chain.proceed()
            applySeekBarRoundCorner(chain.args[0] as? ProgressBar)
            result
        }

        val updateBrightnessSliderMethod: Method =
            toggleSliderViewClass.getDeclaredMethod("updateBrightnessSlider")
        hookWithId(updateBrightnessSliderMethod, "update_brightness_slider") { chain ->
            val result = chain.proceed()
            logger.debug("updateBrightnessSlider afterHookedMethod called!")
            val cl = chain.thisObject.javaClass
            val brightnessSlider =
                cl.getDeclaredField("mBrightnessSlider").get(chain.thisObject) as? ProgressBar
            if (brightnessSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar::class.java)
                    .invoke(chain.thisObject, brightnessSlider)
            }
            result
        }

        val updateVolumeSliderMethod: Method =
            toggleSliderViewClass.getDeclaredMethod("updateVolumeSlider")
        hookWithId(updateVolumeSliderMethod, "update_volume_slider") { chain ->
            val result = chain.proceed()
            logger.debug("updateVolumeSlider afterHookedMethod called!")
            val cl = chain.thisObject.javaClass
            val mediaSlider =
                cl.getDeclaredField("mMediaVolumeSlider").get(chain.thisObject) as? ProgressBar
            if (mediaSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar::class.java)
                    .invoke(chain.thisObject, mediaSlider)
            }
            result
        }

        val toggleCtor: Constructor<*> = toggleSliderViewClass.getDeclaredConstructor(
            Context::class.java,
            AttributeSet::class.java,
            Int::class.javaPrimitiveType
        )
        hookWithId(toggleCtor, "toggle") { chain ->
            chain.proceed()
            val cl = chain.thisObject.javaClass
            val brightnessSlider =
                cl.getDeclaredField("mBrightnessSlider").get(chain.thisObject) as? ProgressBar
            val mediaSlider =
                cl.getDeclaredField("mMediaVolumeSlider").get(chain.thisObject) as? ProgressBar
            if (brightnessSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar::class.java)
                    .invoke(chain.thisObject, brightnessSlider)
            }
            if (mediaSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar::class.java)
                    .invoke(chain.thisObject, mediaSlider)
            }
            null
        }
    }

    private fun applySeekBarRoundCorner(progressBar: ProgressBar?) {
        if (progressBar == null) {
            logger.debug("applySeekBarRoundCorner skipped from refreshSeekBar: progressBar is null")
            return
        }

        val progressDrawable = progressBar.progressDrawable
        if (progressDrawable !is LayerDrawable) {
            logger.debug(
                "applySeekBarRoundCorner skipped from refreshSeekBar: progress drawable is "
                    + describeDrawable(progressDrawable)
            )
            return
        }

        try {
            val backgroundDrawable = progressDrawable.findDrawableByLayerId(android.R.id.background)
            if (backgroundDrawable is GradientDrawable) {
                backgroundDrawable.cornerRadius = headUpTileRoundCornerRadius.toFloat()
            } else {
                logger.debug(
                    "Background layer is " + describeDrawable(backgroundDrawable) + " from refreshSeekBar"
                )
            }

            val progressLayer = progressDrawable.findDrawableByLayerId(android.R.id.progress)
            if (progressLayer is StateListDrawable) {
                for (i in 0 until progressLayer.stateCount) {
                    val stateDrawable = progressLayer.getStateDrawable(i)
                    if (stateDrawable is ClipDrawable) {
                        val innerDrawable = stateDrawable.drawable
                        if (innerDrawable is GradientDrawable) {
                            innerDrawable.cornerRadius = headUpTileRoundCornerRadius.toFloat()
                        }
                    }
                }
            } else {
                logger.debug(
                    "Progress layer is " + describeDrawable(progressLayer) + " from refreshSeekBar"
                )
            }
        } catch (t: Throwable) {
            logger.error("applySeekBarRoundCorner failed from refreshSeekBar", t)
        }
    }

    private fun describeDrawable(drawable: Drawable?): String {
        return drawable?.javaClass?.name ?: "null"
    }

    private fun updateRoundCornerPrefs() {
        headUpTileRoundCornerRadius = try {
            remotePreferences.getInt(PreferenceKeys.HEAD_UP_ROUND_CORNER_RADIUS.name, 32)
        } catch (_: Throwable) {
            32
        }
        normalTileRoundCornerRadius = try {
            remotePreferences.getInt(PreferenceKeys.TILE_ROUND_CORNER_RADIUS.name, 96)
        } catch (_: Throwable) {
            96
        }
    }
}
