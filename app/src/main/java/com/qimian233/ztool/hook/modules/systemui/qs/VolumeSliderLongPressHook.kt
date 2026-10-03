package com.qimian233.ztool.hook.modules.systemui.qs

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Volume slider long-press panel: long-pressing the control-center media volume
 * slider opens a BrightnessDetailDialog-style controller with media / ring /
 * per-app volume sliders and three QS tiles (mute / DND / vibrate).
 *
 * The long-press gesture itself is owned by [ControlCenterLongPressHook]; its
 * volume trigger calls [onVolumeSliderLongPress]. The stock (normally GONE)
 * `volume_detail_indicator` ImageView next to the slider is revealed as a tap
 * entry point, unless HideDetailIndicatorHook is active (long-press only).
 * Everything here is reflection:
 * module classes cannot subclass SystemUI classes, and the relevant SystemUI
 * entry points (SystemUIDialog, CustomizeTileView) are only reachable that way.
 *
 * Container: stock `SystemUIDialog` built via its public 3-arg constructor
 * (it self-wires Dependency-based singletons, SCREEN_OFF dismissal and system
 * window flags), themed like BrightnessDetailDialog
 * (Theme_SystemUI_Dialog_GlobalActionsLite).
 *
 * Tiles: real QSTile instances created via the SystemUI QSFactoryImpl
 * (captured by hooking createTile) — "mute", "dnd", "vibrate" — each bound to
 * a stock `CustomizeTileView` exactly like the native BrightnessDetailDialog
 * does: refreshState + QSTile.Callback feeding the view + setListening. Click
 * and long-press delegate to the tile (click/longClick with an Expandable), so
 * native debounce, state lottie, press feedback, per-tile detail dialogs (DND)
 * and the keyguard unlock flow all come for free; the panel dismisses after a
 * long press like the native dialog.
 *
 * App volume: per-uid relative volume. The active-app list comes from the
 * framework callback `android.media.AudioSystem$AudioAppListCallback`
 * (payload: AudioSystem$PackageInfo[]). AudioSystem exposes a single
 * setAudioAppListCallback slot already owned by the stock volume dialog, so we
 * hook that setter to record the native callback, swap in our Proxy while the
 * panel is open (forwarding to the native callback to keep the stock panel
 * fresh), and restore the native callback on dismiss. Values are applied via
 * the hidden AudioManager.setParameters("uid=<uid>;app_volume=<v>") — the same
 * call RelativeVolumeHelper uses — and persisted to Settings.System
 * "zui_app_volume" ("uid/pct;uid/pct") with public Settings APIs.
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi")
class VolumeSliderLongPressHook : AppHookModule() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var panelShowing = false
    private var currentDialog: Dialog? = null
    private var systemUiClassLoader: ClassLoader? = null

    // Rebuilt when the app list callback fires while the panel is open.
    private var appSection: LinearLayout? = null
    private var dialogContext: Context? = null

    /** NotificationPanelViewController used to fade the shade content out/in. */
    private var behindListener: Any? = null

    /** BrightnessDetailDialogController, source of the QS frame geometry. */
    private var brightnessDialogController: Any? = null

    override fun getModuleName(): String = PreferenceKeys.VOLUME_DETAIL_PANEL.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        systemUiClassLoader = param.defaultClassLoader
        hook = this
        hookVolumeDialogImpl()
        hookVolumeDetailIndicator(param.defaultClassLoader)
        hookTileFactory(param.defaultClassLoader)
        logger.info("VolumeSliderLongPressHook installed")
    }

    // ------------------------------------------------------------------
    // Tap entry point: the stock (permanently GONE) volume detail indicator
    // ------------------------------------------------------------------

    /**
     * Reveals the stock `volume_detail_indicator` ImageView inside the volume
     * slider root — the ROM inflates it but never shows or wires it — and
     * routes its taps to the panel. Skipped when HideDetailIndicatorHook is
     * active so the long press stays the only entry; both modules read the
     * same preference, so hook execution order cannot produce a visible
     * button under that switch.
     */
    private fun hookVolumeDetailIndicator(classLoader: ClassLoader) {
        if (remotePreferences.getBoolean(PreferenceKeys.HIDE_DETAIL_INDICATOR.name, false)) {
            logger.info("volume panel: hide_detail_indicator on, detail button not shown")
            return
        }
        try {
            val ctor = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredConstructor(
                    Context::class.java, android.util.AttributeSet::class.java,
                    Int::class.javaPrimitiveType
                )
            hookWithId(ctor, "volume_detail_indicator_show") { chain ->
                chain.proceed()
                try {
                    val view = findField(chain.thisObject.javaClass, VOLUME_DETAIL_INDICATOR_FIELD)
                        .get(chain.thisObject) as? ImageView
                    if (view != null) {
                        view.visibility = View.VISIBLE
                        view.isClickable = true
                        view.isFocusable = true
                        view.setOnClickListener { onVolumeSliderLongPress(it) }
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to show volume detail indicator", t)
                }
                null
            }
            logger.info("volume panel: volume detail indicator hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook ToggleSliderView ctor for detail button", t)
        }
    }

    // ------------------------------------------------------------------
    // Entry point from the shared slider long-press detector
    // ------------------------------------------------------------------

    companion object {
        // The hook instance must outlive individual panels for the shared
        // detector to reach it; panel view fields are nulled on dismiss.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var hook: VolumeSliderLongPressHook? = null

        private const val SYSTEM_UI_DIALOG_CLASS =
            "com.android.systemui.statusbar.phone.SystemUIDialog"
        private const val TOGGLE_SLIDER_VIEW_CLASS =
            "com.android.systemui.settings.ToggleSliderView"
        private const val SEEK_BAR_NPS_CLASS =
            "zui.widget.SeekBarNps"
        private const val VOLUME_DETAIL_INDICATOR_FIELD = "mVolumeDetailIndicator"
        private const val VOLUME_DIALOG_IMPL_CLASS =
            "com.android.systemui.volume.VolumeDialogImpl"
        private const val CUSTOMIZE_TILE_VIEW_CLASS =
            "com.android.systemui.qs.customize.CustomizeTileView"
        private const val QS_FACTORY_IMPL_CLASS =
            "com.android.systemui.qs.tileimpl.QSFactoryImpl"
        private const val QS_TILE_CALLBACK_CLASS =
            $$"com.android.systemui.plugins.qs.QSTile$Callback"
        private const val EXPANDABLE_CLASS =
            "com.android.systemui.animation.Expandable"
        private const val APP_SECTION_TAG = "ztool_volume_panel_app_section"
        private const val APP_VOLUME_SETTINGS_KEY = "zui_app_volume"
        private const val MAX_APP_ROWS = 3
        private const val TILE_LOTTIE_TAG = "ztool_tile_lottie_slowed"
        private const val TILE_LOTTIE_SPEED = 0.6f

        /** Called by [ControlCenterLongPressHook] on the volume slider long press. */
        @JvmStatic
        fun onVolumeSliderLongPress(view: View) {
            hook?.handleVolumeLongPress(view)
        }
    }

    private fun handleVolumeLongPress(view: View) {
        logger.debug(
            "volume panel: trigger received, view=" + view.javaClass.simpleName +
                ", panelShowing=" + panelShowing +
                ", classLoader=" + (systemUiClassLoader?.javaClass?.name ?: "null")
        )
        val classLoader = systemUiClassLoader ?: run {
            logger.warn("volume panel: no classLoader captured, cannot open panel")
            return
        }
        if (panelShowing) {
            logger.debug("volume panel: already showing, ignoring trigger")
            return
        }
        try {
            buildAndShowPanel(view.context, classLoader, view)
        } catch (t: Throwable) {
            logger.error("Failed to show volume detail panel", t)
        }
    }

    // ------------------------------------------------------------------
    // App volume source: VolumeDialogImpl's cached package list
    // ------------------------------------------------------------------

    /** VolumeDialogImpl instance captured when the native dialog initializes. */
    @Volatile
    private var volumeDialogImpl: Any? = null

    /**
     * Skips the AudioSystem callback plumbing entirely: the native
     * VolumeDialogImpl already caches the active-audio app list in its public
     * appVolumePackgerList field, and its init() runs once at SystemUI startup.
     * Capture the instance, then read the list when the panel opens.
     */
    private fun hookVolumeDialogImpl() {
        val classLoader = systemUiClassLoader ?: return
        try {
            val implClass = classLoader.loadClass(VOLUME_DIALOG_IMPL_CLASS)
            val callbackClass = classLoader.loadClass(
                $$"com.android.systemui.plugins.VolumeDialog$Callback"
            )
            val init = implClass.getDeclaredMethod(
                "init", Int::class.javaPrimitiveType, callbackClass
            )
            init.isAccessible = true
            hookWithId(init, "volume_dialog_impl_init") { chain ->
                val result = chain.proceed()
                volumeDialogImpl = chain.thisObject
                logger.info("volume panel: VolumeDialogImpl captured at init()")
                result
            }
            logger.info("volume panel: VolumeDialogImpl init hook installed")
        } catch (t: Throwable) {
            logger.warn("volume panel: VolumeDialogImpl init hook failed: ${t.message}")
        }
    }

    private fun readDialogAppPackages(): List<Any> {
        val dialog = volumeDialogImpl ?: run {
            logger.debug("volume panel: no VolumeDialogImpl instance captured yet")
            return emptyList()
        }
        return try {
            val raw = findField(dialog.javaClass, "appVolumePackgerList").get(dialog) as? List<*>
            raw?.filterNotNull() ?: emptyList()
        } catch (t: Throwable) {
            logger.warn("volume panel: read appVolumePackgerList failed: ${t.message}")
            emptyList()
        }
    }

    // ------------------------------------------------------------------
    // Panel construction
    // ------------------------------------------------------------------

    private fun buildAndShowPanel(
        context: Context,
        classLoader: ClassLoader,
        triggerView: View
    ) {
        val themeRes = resolveStyleId(context)
            ?: android.R.style.Theme_DeviceDefault_Dialog
        val dialogClass = classLoader.loadClass(SYSTEM_UI_DIALOG_CLASS)
        val ctor = dialogClass.getDeclaredConstructor(
            Context::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
        )
        ctor.isAccessible = true
        val dialog = ctor.newInstance(context, themeRes, true) as Dialog

        // Fullscreen transparent root: the control-center blur behind the shade
        // IS the background (BrightnessDetailDialog does the same; dim stays 0).
        // Tapping anywhere outside the panel dismisses — a fullscreen window has
        // no "outside", so outside-touch dismissal must be handled here.
        // Layout mirrors BrightnessDetailDialog.updateConstraints$1: landscape
        // anchors the panel at the QS frame's left edge (margin = qsFrameX -
        // leftInset + qsMarginStart), portrait drops the margin to 0 and
        // centers horizontally. Insets drive the re-layout, so rotation is
        // covered without hooking configuration changes.
        val root = FrameLayout(context).apply {
            setOnClickListener { currentDialog?.dismiss() }
        }

        dialogContext = context
        appSection = null

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val sliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        sliderRow.addView(
            buildStreamColumn(context, am, AudioManager.STREAM_MUSIC, resolveDrawableId(
                context, "ic_volume_media_zui", "ic_volume_media"
            ), 0)
        )
        if (!AudioSystemHelperShim.isSingleVolume(context)) {
            sliderRow.addView(
                buildStreamColumn(context, am, AudioManager.STREAM_RING, resolveDrawableId(
                    context, "ic_volume_ringer_zui", "ic_volume_ringer"
                ), 28)
            )
        }
        // App columns are appended inline by refreshAppSection().
        buildAppSection(context).also {
            appSection = it
            sliderRow.addView(it)
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        }
        panel.addView(sliderRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val tileRow = buildTileRow(context, classLoader)
        panel.addView(tileRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 16) })
        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            // BrightnessDetailDialog placement (updateConstraints$1):
            // landscape -> marginStart = qsFrameX - leftInset + qsMarginStart,
            // portrait -> marginStart = 0 (horizontally centered instead).
            // Bottom inset is added on top of both, like the native dialog's
            // bottomMargin = mBottomInset.
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            marginStart = 0
            bottomMargin = bottomInset
        })

        // Mirror the native dialog: insets arriving on the root (first dispatch
        // after show() and again on every rotation) are the single authority for
        // the panel margins — they recompute the landscape/portrait branch each
        // time, so no configuration-change hook is needed.
        root.setOnApplyWindowInsetsListener { _, insets ->
            captureInsets(insets)
            applyPanelLayout(context, panel)
            insets
        }

        behindListener = resolveBehindListener(triggerView)
        dialog.setOnDismissListener {
            panelShowing = false
            currentDialog = null
            releaseBoundTiles()
            // Reveal the control-center widgets the dialog had hidden and
            // release the view tree so the static hook reference cannot leak it.
            setDialogBehindAlpha(1f)
            behindListener = null
            brightnessDialogController = null
            dialogContext = null
            appSection = null
        }

        panelShowing = true
        currentDialog = dialog
        mainHandler.post { refreshAppSection() }
        // Pre-insets layout so the landscape margin is already correct when the
        // window becomes visible; the insets dispatch right after show() refines
        // it with the real cutout/system-bar values.
        applyPanelLayout(context, panel)
        dialog.show()
        // AlertDialog.onCreate -> AlertController.installContent() runs inside
        // show() and installs the stock alert layout, REPLACING any content set
        // beforehand (that orphaned our root: visible window, empty content).
        // Native BrightnessDetailDialog sets content in onCreate after
        // super.onCreate() for the same reason; setContentView here wins.
        dialog.setContentView(root)
        try {
            val window = dialog.window
            // The framework fallback theme ships an opaque windowBackground;
            // the shade blur behind the dialog IS the background.
            window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            window?.setDimAmount(0f)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window?.attributes?.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Mirror BrightnessDetailDialog.onCreate: the fullscreen
                    // root extends under the system bars and the insets are
                    // consumed by our own OnApplyWindowInsetsListener margins.
                    // Deprecated on API 36+ with no hook-process equivalent; this
                    // mirrors the native BrightnessDetailDialog behavior.
                    @Suppress("DEPRECATION")
                    window?.setDecorFitsSystemWindows(false)
                    window?.attributes?.fitInsetsTypes = 0
                }
            } catch (_: Throwable) {
            }
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } catch (t: Throwable) {
            logger.warn("volume panel: window config failed: ${t.message}")
        }
        // BrightnessDetailDialog-style entrance: fade the control-center
        // widgets out behind the dialog while the panel fades in.
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration(250L).start()
        fadeDialogBehindOut()
    }

    /**
     * Resolves NotificationPanelViewController (mDialogBehindAlphaListener)
     * from the triggering slider: ToggleSliderView -> mBrightnessDetailDialog
     *Controller -> mDialogBehindAlphaListener. setDialogBehindAlpha() on it
     * fades the control-center content out/in, exactly what the stock
     * BrightnessDetailDialog does.
     */
    private fun resolveBehindListener(triggerView: View): Any? {
        return try {
            var current: View = triggerView
            var host: Any? = null
            repeat(6) {
                current = current.parent as? View ?: return@repeat
                if (current.javaClass.name == TOGGLE_SLIDER_VIEW_CLASS) host = current
            }
            val controller = host?.let {
                findField(it.javaClass, "mBrightnessDetailDialogController").get(it)
            } ?: return null
            brightnessDialogController = controller
            findField(controller.javaClass, "mDialogBehindAlphaListener").get(controller)
        } catch (t: Throwable) {
            logger.debug("volume panel: behind listener unavailable: ${t.message}")
            null
        }
    }

    /**
     * System-bar / cutout insets of the dialog window, mirroring the native
     * dialog's onApplyWindowInsets: each side keeps the larger of the
     * systemBars insets and the display cutout safe inset.
     */
    private var leftInset = 0
    private var bottomInset = 0

    private fun captureInsets(insets: WindowInsets) {
        val barsLeft: Int
        val barsBottom: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            barsLeft = bars.left
            barsBottom = bars.bottom
        } else {
            @Suppress("DEPRECATION")
            barsLeft = insets.systemWindowInsetLeft
            @Suppress("DEPRECATION")
            barsBottom = insets.systemWindowInsetBottom
        }
        @Suppress("DEPRECATION")
        val cutout = insets.displayCutout
        leftInset = maxOf(barsLeft, cutout?.safeInsetLeft ?: 0)
        bottomInset = maxOf(barsBottom, cutout?.safeInsetBottom ?: 0)
    }

    /**
     * Re-applies the BrightnessDetailDialog.updateConstraints$1 placement to
     * the panel. Called on every insets dispatch (initial one after show() and
     * again on rotation), which covers orientation changes.
     */
    private fun applyPanelLayout(context: Context, panel: View) {
        val lp = panel.layoutParams as? FrameLayout.LayoutParams ?: return
        val landscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            lp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            lp.marginStart = resolveLandscapeMarginStart(context)
        } else {
            // Native portrait branch drops the QS-frame margin entirely
            // (marginStart = 0) and lets the constraint center the container;
            // a centered gravity with zero margins is the FrameLayout equivalent.
            lp.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            lp.marginStart = 0
        }
        lp.bottomMargin = bottomInset
        panel.layoutParams = lp
    }

    /**
     * Landscape margin: qsFrameX - leftInset + qsMarginStart (the QS frame's
     * left edge on split-shade / landscape layouts), shifted by half the
     * display width in RTL layouts like the native dialog. Resolve the frame
     * position at runtime from ShadeController.getQuickSettingsController()
     * .getQsFrameX(); fall back to the measured constant when unavailable.
     */
    private fun resolveLandscapeMarginStart(context: Context): Int {
        var margin = run {
            val controller = brightnessDialogController
            if (controller != null) {
                try {
                    val shade = findField(controller.javaClass, "mShadeController").get(controller)
                    val qs = findMethod(shade.javaClass, "getQuickSettingsController").invoke(shade)
                    val frameX = findMethod(qs.javaClass, "getQsFrameX").invoke(qs) as Float
                    val qsMarginStart = resolveDimenPx(context, "qs_margin_start", dp(context, 24))
                    return@run frameX.toInt() - leftInset + qsMarginStart
                } catch (t: Throwable) {
                    logger.debug("volume panel: qsFrameX unavailable: ${t.message}")
                }
            }
            dp(context, 759)
        }
        if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            margin += context.resources.displayMetrics.widthPixels / 2
        }
        return margin
    }

    /** Hidden on NotificationPanelViewController: setDialogBehindAlpha(float). */
    private fun setDialogBehindAlpha(alpha: Float) {
        val listener = behindListener ?: return
        try {
            findMethod(listener.javaClass, "setDialogBehindAlpha", Float::class.javaPrimitiveType)
                .invoke(listener, alpha)
        } catch (t: Throwable) {
            logger.debug("volume panel: setDialogBehindAlpha($alpha) failed: ${t.message}")
        }
    }

    private fun fadeDialogBehindOut() {
        if (behindListener == null) return
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 250L
            addUpdateListener {
                setDialogBehindAlpha(it.animatedValue as Float)
            }
        }
        animator.start()
    }

    // ------------------------------------------------------------------
    // Stream sliders (media / ring)
    // ------------------------------------------------------------------

    /**
     * Vertical slider column (icon on top, vertical bar, percent below),
     * matching the control-center vertical slider style. The bar is a
     * horizontal SeekBar rotated 270deg: the progress-increasing axis points
     * UP, so dragging up raises the value, dragging down lowers it.
     */
    private fun buildSliderColumn(
        context: Context,
        iconDrawable: android.graphics.drawable.Drawable?,
        initial: Int,
        maxValue: Int,
        onProgress: (Int) -> Unit = {},
        onStop: (Int) -> Unit = {}
    ): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        if (iconDrawable != null) {
            column.addView(ImageView(context).apply {
                setImageDrawable(iconDrawable)
                val size = dp(context, 22)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    bottomMargin = dp(context, 10)
                }
            })
        }
        val percentView = TextView(context).apply {
            textSize = 12f
            setTextColor(obtainThemeColor(context))
            text = formatPercent(initial, maxValue)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 10) }
        }
        val barLength = dp(context, 260)
        val barThickness = resolveDimenPx(context, "brightness_bar_height", dp(context, 18))
            .coerceAtLeast(dp(context, 28))
        val barSlot = FrameLayout(context)
        barSlot.addView(buildColumnBar(context, initial, maxValue, percentView, onProgress, onStop).apply {
            thumb = null
            progressDrawable = resolveSliderDrawable(context)
            minHeight = barThickness
            maxHeight = barThickness
            rotation = 270f
            layoutParams = FrameLayout.LayoutParams(barLength, barThickness, Gravity.CENTER)
        })
        column.addView(barSlot, LinearLayout.LayoutParams(
            barThickness, barLength
        ).apply {
            setMargins(dp(context, 6), 0, dp(context, 6), 0)
        })
        column.addView(percentView)
        return column
    }

    /**
     * The column's draggable bar: the stock zui.widget.SeekBarNps when
     * available, plain SeekBar otherwise. SeekBarNps only enables its
     * relative-drag gesture (finger-delta driven, tap never repositions the
     * thumb) when max >= 100 — and it rescales any such max to 1000 internally
     * — so on the Nps path the bar runs on a 0..1000 scale and the percent
     * [maxValue] contract is converted at the listener boundary. A plain
     * SeekBar jumps straight to the tap point, which users reported as
     * accidental volume jumps.
     */
    private fun buildColumnBar(
        context: Context,
        initialPercent: Int,
        maxValue: Int,
        percentView: TextView,
        onProgress: (Int) -> Unit,
        onStop: (Int) -> Unit
    ): SeekBar {
        val bar = try {
            val classLoader = systemUiClassLoader ?: context.classLoader
            val clazz = classLoader.loadClass(SEEK_BAR_NPS_CLASS)
            clazz.getConstructor(Context::class.java).newInstance(context) as SeekBar
        } catch (t: Throwable) {
            logger.warn("volume panel: SeekBarNps unavailable, plain SeekBar fallback: ${t.message}")
            SeekBar(context)
        }
        val isNps = bar.javaClass.name == SEEK_BAR_NPS_CLASS
        val scale = if (isNps) 1000 else maxValue
        // SeekBarNps drives touch itself and never calls drawableHotspotChanged
        // when setPressed(true) on DOWN, so the theme-injected foreground ripple
        // stays anchored at local (0,0) — drawn as a stray dot at the bar's
        // corner once rotated 270°. Strip background/foreground feedback; the
        // progress bar is all this bar should draw.
        bar.background = null
        bar.foreground = null
        bar.max = scale
        bar.progress = (initialPercent * scale / maxValue.toFloat()).roundToInt()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                percentView.text = formatPercent(progress, bar.max)
                onProgress((progress * maxValue / scale.toFloat()).roundToInt())
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}

            override fun onStopTrackingTouch(bar: SeekBar) {
                onStop((bar.progress * maxValue / scale.toFloat()).roundToInt())
            }
        })
        return bar
    }

    private fun buildStreamColumn(
        context: Context,
        am: AudioManager,
        stream: Int,
        iconRes: Int?,
        marginStartDp: Int = 0
    ): View {
        // The SeekBar uses 100 display steps and maps back to the stream's own
        // (coarse) volume steps; without the finer display scale dragging feels
        // like a staircase of a few big jumps.
        val streamMax = am.getStreamMaxVolume(stream)
        val column = buildSliderColumn(
            context,
            iconRes?.let { context.getDrawable(it) },
            initial = (am.getStreamVolume(stream) * 100f / streamMax).roundToInt(),
            maxValue = 100,
            onProgress = { progress ->
                val target = (progress * streamMax / 100f).roundToInt()
                try {
                    am.setStreamVolume(stream, target, 0)
                } catch (t: Throwable) {
                    logger.warn("setStreamVolume($stream) failed: ${t.message}")
                }
            }
        )
        (column.layoutParams as? LinearLayout.LayoutParams)?.marginStart =
            dp(context, marginStartDp)
        return column
    }

    /**
     * Same progress drawable the stock ToggleSliderView uses for its sliders
     * (brightness_progress_selector), giving the panel the control-center look.
     */
    private fun resolveSliderDrawable(context: Context): android.graphics.drawable.Drawable? {
        val id = resolveDrawableId(context, "brightness_progress_selector")
        return if (id != null) context.getDrawable(id) else null
    }

    // ------------------------------------------------------------------
    // App volume section
    // ------------------------------------------------------------------

    private data class AppVolumeEntry(
        val uid: Int,
        val label: String,
        val initialPercent: Int
    )

    private fun buildAppSection(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            // App columns stand side by side with the media/ring columns.
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = APP_SECTION_TAG
        }
    }

    private fun refreshAppSection() {
        val section = appSection ?: return
        val context = dialogContext ?: return
        if (!panelShowing) return
        try {
            section.removeAllViews()
            val entries = collectAppVolumeEntries(context)
            if (entries.isEmpty()) {
                section.visibility = View.GONE
                logger.debug("volume panel: app section hidden (no entries)")
                return
            }
            section.visibility = View.VISIBLE
            for (entry in entries) {
                section.addView(buildAppColumn(context, entry))
            }
            logger.debug("volume panel: app section columns=${entries.size}")
        } catch (t: Throwable) {
            logger.error("Failed to refresh app volume section", t)
        }
    }

    /**
     * The native cache stores one "packageName/uid" string per active-audio
     * app (already filtered by the native dialog). Parse it directly.
     */
    private fun collectAppVolumeEntries(context: Context): List<AppVolumeEntry> {
        val persisted = loadPersistedAppVolumes(context)
        val entries = mutableListOf<AppVolumeEntry>()
        val seenUids = mutableSetOf<Int>()
        for (element in readDialogAppPackages()) {
            if (entries.size >= MAX_APP_ROWS) break
            if (element !is String) {
                logger.debug("volume panel: app skipped (non-string): " + element.javaClass.name)
                continue
            }
            val sep = element.lastIndexOf('/')
            if (sep <= 0) {
                logger.debug("volume panel: app skipped (bad format): $element")
                continue
            }
            val name = element.substring(0, sep)
            val uid = element.substring(sep + 1).toIntOrNull()
            if (name.isEmpty() || uid == null || uid < 0) {
                logger.debug("volume panel: app skipped (bad uid): $element")
                continue
            }
            if (!seenUids.add(uid)) continue
            val appInfo = try {
                context.packageManager.getApplicationInfo(name, 0)
            } catch (_: Exception) {
                null
            }
            val label = appInfo?.loadLabel(context.packageManager)?.toString() ?: name
            val pct = persisted[uid]?.let { (it * 100).roundToInt().coerceIn(0, 100) } ?: 100
            entries.add(AppVolumeEntry(uid, label, pct))
        }
        return entries
    }

    private fun buildAppColumn(
        context: Context,
        entry: AppVolumeEntry
    ): View {
        val icon = appIconInfo(context, entry.uid)
            ?.let { context.packageManager.getApplicationIcon(it) }
            ?: resolveDrawableId(context, "ic_volume_media_zui")
                ?.let { context.getDrawable(it) }
        val column = buildSliderColumn(
            context, icon,
            initial = entry.initialPercent,
            maxValue = 100,
            onStop = { progress -> commitAppVolume(context, entry.uid, progress) }
        )
        (column.layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(context, 28)
        return column
    }

    /** Same call path as RelativeVolumeHelper.setRelativeVolumeInternal. */
    private fun commitAppVolume(context: Context, uid: Int, percent: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val value = percent / 100.0f
        try {
            val setParameters = am.javaClass.getMethod("setParameters", String::class.java)
            setParameters.isAccessible = true
            setParameters.invoke(am, String.format(Locale.US, "uid=%d;app_volume=%f", uid, value))
        } catch (t: Throwable) {
            logger.error("app volume setParameters failed for uid=$uid", t)
        }
        persistAppVolume(context, uid, percent)
    }

    private fun loadPersistedAppVolumes(context: Context): Map<Int, Float> {
        val raw = try {
            Settings.System.getString(context.contentResolver, APP_VOLUME_SETTINGS_KEY)
        } catch (_: Throwable) {
            null
        } ?: return emptyMap()
        val result = mutableMapOf<Int, Float>()
        for (entry in raw.split(";")) {
            val parts = entry.split("/")
            if (parts.size != 2) continue
            val uid = parts[0].toIntOrNull() ?: continue
            val pct = parts[1].toFloatOrNull() ?: continue
            result[uid] = pct
        }
        return result
    }

    private fun persistAppVolume(context: Context, uid: Int, percent: Int) {
        try {
            val value = String.format(Locale.US, "%d/%f", uid, percent / 100.0f)
            val existing = loadPersistedAppVolumes(context).toMutableMap()
            existing[uid] = percent / 100.0f
            val serialized = existing.entries.joinToString(";") { (u, p) ->
                String.format(Locale.US, "%d/%f", u, p)
            }
            val finalValue = serialized.ifEmpty { value }
            Settings.System.putString(context.contentResolver, APP_VOLUME_SETTINGS_KEY, finalValue)
        } catch (t: Throwable) {
            logger.warn("persist app volume failed: ${t.message}")
        }
    }

    // ------------------------------------------------------------------
    // Tiles (mute / DND / vibrate), stock CustomizeTileView visuals
    // ------------------------------------------------------------------

    private class TileUi(
        val view: View,
        val refresh: () -> Unit
    )

    /** Captured QSFactoryImpl instance for creating real QSTiles on demand. */
    @Volatile
    private var tileFactory: Any? = null

    /** Tiles currently bound to panel views, released on panel dismiss. */
    private val boundTiles = mutableListOf<Any>()

    /**
     * Captures the SystemUI QSFactoryImpl singleton: every call to
     * createTile(spec) goes through it, so remembering `this` once gives us
     * the ability to create real QSTiles (mute/dnd/vibrate) for the panel.
     */
    private fun hookTileFactory(classLoader: ClassLoader) {
        try {
            val factoryClass = classLoader.loadClass(QS_FACTORY_IMPL_CLASS)
            val create = factoryClass.getDeclaredMethod("createTile", String::class.java)
            create.isAccessible = true
            hookWithId(create, "volume_panel_tile_factory") { chain ->
                tileFactory = chain.thisObject
                chain.proceed()
            }
            logger.info("volume panel: tile factory hook installed")
        } catch (t: Throwable) {
            logger.warn("volume panel: tile factory hook failed: ${t.message}")
        }
    }

    /** Creates a real QSTile by spec ("mute"/"dnd"/"vibrate"); null on failure. */
    private fun createQsTile(spec: String): Any? {
        val factory = tileFactory ?: return null
        return try {
            findMethod(factory.javaClass, "createTile", String::class.java)
                .invoke(factory, spec)
        } catch (t: Throwable) {
            logger.warn("volume panel: createTile($spec) failed: ${t.message}")
            null
        }
    }

    private var muteTile: TileUi? = null
    private var dndTile: TileUi? = null
    private var vibrateTile: TileUi? = null

    private fun buildTileRow(context: Context, classLoader: ClassLoader): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val tileWidth = resolveDimenPx(
            context, "brightness_detail_dialog_tile_width", dp(context, 74)
        )
        val tileHeight = resolveDimenPx(context, "qs_tile_height", dp(context, 64))
        val margin = resolveDimenPx(
            context, "brightness_detail_dialog_tile_margin", dp(context, 6)
        )

        muteTile = buildTile(context, classLoader, "mute", row)
        dndTile = buildTile(context, classLoader, "dnd", row)
        vibrateTile = if (hasVibrator(context)) buildTile(context, classLoader, "vibrate", row) else null
        for (tile in listOf(muteTile, dndTile, vibrateTile)) {
            if (tile == null) continue
            row.addView(tile.view, LinearLayout.LayoutParams(tileWidth, tileHeight).apply {
                setMargins(margin, 0, margin, 0)
            })
        }
        refreshAllTiles()
        return row
    }

    /**
     * Binds a real QSTile to a stock CustomizeTileView, mirroring the native
     * BrightnessDetailDialog.onStart() wiring: refreshState + a state callback
     * that feeds the view on the main thread + setListening so the tile's own
     * observers run. Click and long-press delegate to the tile, which brings
     * the native debounce, keyguard unlock flow, per-tile detail dialogs
     * (DND) and shade collapse with them.
     */
    private fun buildTile(
        context: Context,
        classLoader: ClassLoader,
        spec: String,
        parentRow: ViewGroup
    ): TileUi? {
        return try {
            val tile = createQsTile(spec) ?: run {
                logger.warn("volume panel: no QSTile for spec $spec")
                return null
            }
            val tileViewClass = classLoader.loadClass(CUSTOMIZE_TILE_VIEW_CLASS)
            val tileView = tileViewClass.getConstructor(Context::class.java)
                .newInstance(context) as ViewGroup
            // Declared on an ancestor as handleStateChanged(QSTile$State); a
            // getDeclaredMethod with the BooleanState subtype fails, so walk
            // the hierarchy and match the single parameter by assignability.
            val handleStateChanged = findTileStateMethod(tileViewClass, classLoader)
                ?: throw NoSuchMethodException($$"handleStateChanged(QSTile$State) not found")
            handleStateChanged.isAccessible = true

            val tileClass = tile.javaClass
            val tileSpec = try {
                findMethod(tileClass, "getTileSpec").invoke(tile) as? String ?: spec
            } catch (_: Throwable) {
                spec
            }
            val refreshState = findMethod(tileClass, "refreshState")
            val click = findMethod(tileClass, "click",
                classLoader.loadClass(EXPANDABLE_CLASS))
            val longClick = findMethod(tileClass, "longClick",
                classLoader.loadClass(EXPANDABLE_CLASS))
            val getState = findMethod(tileClass, "getState")

            // QSTile.Callback SAM: onStateChanged fires on the tile's handler
            // thread; the native dialog re-posts to main before touching views.
            val callbackClass = classLoader.loadClass(QS_TILE_CALLBACK_CLASS)
            val mainHandler = this.mainHandler
            val callback = java.lang.reflect.Proxy.newProxyInstance(
                classLoader, arrayOf(callbackClass)
            ) { _, method, args ->
                if (method.name == "onStateChanged" && args != null && args.isNotEmpty()) {
                    val state = args[0]
                    mainHandler.post {
                        try {
                            handleStateChanged.invoke(tileView, state)
                            slowDownTileLottie(tileView)
                        } catch (t: Throwable) {
                            logger.error("tile state apply failed", t)
                        }
                    }
                }
                null
            }
            val addCallback = findMethod(tileClass, "addCallback", callbackClass)
            val removeCallback = findMethod(tileClass, "removeCallback", callbackClass)
            val setListening = findMethod(tileClass, "setListening", Any::class.java,
                Boolean::class.javaPrimitiveType)

            // Expandable from the view, same as the native dialog's
            // Expandable.Companion.fromView. R8 flattened the Companion, so
            // fromView lives directly on the interface as a static; fall back
            // to a hand-rolled implementation of the 3-method interface.
            val expandableClass = classLoader.loadClass(EXPANDABLE_CLASS)
            val expandable: Any = try {
                findMethod(expandableClass, "fromView", View::class.java)
                    .invoke(null, tileView)
            } catch (_: Throwable) {
                java.lang.reflect.Proxy.newProxyInstance(
                    classLoader, arrayOf(expandableClass)
                ) { _, _, _ -> tileView }
            }

            fun refresh() {
                try {
                    refreshState.invoke(tile)
                } catch (t: Throwable) {
                    logger.error("tile refresh failed", t)
                }
            }

            /**
             * Synchronous fallback: push the tile's current state straight
             * into the view. The async callback usually delivers updates, but
             * if it never fires (handler/looper quirks on this ROM) the tile
             * would stay a blank slab — this guarantees the view matches
             * tile.getState() every time we ask for a refresh.
             */
            fun applyStateSync() {
                try {
                    val state = getState.invoke(tile) ?: return
                    // mState.spec was captured before setTileSpec ran (null);
                    // handleStateChanged dereferences it for subtitles.
                    setField(state, "spec", tileSpec)
                    handleStateChanged.invoke(tileView, state)
                    slowDownTileLottie(tileView)
                } catch (t: Throwable) {
                    logger.error("tile state apply failed", t)
                }
            }

            val host = volumeDialogImpl ?: tile
            // CustomizeTileView.isLongClickable() is hardcoded false, so the
            // framework never runs long-press detection on this view — drive
            // our own and forward to the tile (native DND detail dialog, key
            //guard flow), closing the panel like the native dialog does.
            var longPressFired = false
            var longPressPending: Runnable? = null
            tileView.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressFired = false
                        longPressPending?.let(view::removeCallbacks)
                        val lp = Runnable {
                            longPressFired = true
                            try {
                                longClick.invoke(tile, expandable)
                            } catch (t: Throwable) {
                                logger.error("tile long click failed", t)
                            }
                            currentDialog?.dismiss()
                        }
                        longPressPending = lp
                        view.postDelayed(lp, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressPending?.let(view::removeCallbacks)
                        longPressPending = null
                    }
                }
                false
            }
            tileView.setOnClickListener {
                if (longPressFired) {
                    // The long-press detector already consumed this gesture.
                    longPressFired = false
                    return@setOnClickListener
                }
                try {
                    click.invoke(tile, expandable)
                } catch (t: Throwable) {
                    logger.error("tile click failed", t)
                }
                // Apply the (optimistically toggled) state right away; the
                // async callback refines it when handleRefreshState lands.
                mainHandler.postDelayed({ applyStateSync() }, 100L)
            }
            // Native wiring order: initial state push, then register, then listen.
            addCallback.invoke(tile, callback)
            setListening.invoke(tile, host, true)
            refreshState.invoke(tile)
            // Guarantee a populated view even if the callback path is dead.
            mainHandler.postDelayed({ applyStateSync() }, 60L)
            mainHandler.postDelayed({ applyStateSync() }, 250L)
            boundTiles.add(tile)
            synchronized(boundCallbacks) { boundCallbacks.add(Pair(tile, callback)) }
            TileUi(tileView, ::refresh)
        } catch (t: Throwable) {
            logger.error("Failed to build QS tile view", t)
            null
        }
    }

    /** Tile listening registrations to undo when the panel dismisses. */
    private val boundCallbacks = mutableListOf<Pair<Any, Any>>()

    /** Releases QSTile listeners created for the panel; mirrors onStop(). */
    private fun releaseBoundTiles() {
        val pairs = synchronized(boundCallbacks) {
            val copy = boundCallbacks.toList()
            boundCallbacks.clear()
            copy
        }
        for ((tile, callback) in pairs) {
            try {
                findMethod(tile.javaClass, "removeCallback", callback.javaClass)
                    .invoke(tile, callback)
                findMethod(
                    tile.javaClass, "setListening", Any::class.java,
                    Boolean::class.javaPrimitiveType
                ).invoke(tile, volumeDialogImpl ?: tile, false)
            } catch (t: Throwable) {
                logger.debug("volume panel: tile release failed: ${t.message}")
            }
        }
        boundTiles.clear()
    }

    /**
     * Finds handleStateChanged(QSTile$State) anywhere in the tile view
     * hierarchy — CustomizeTileView may inherit it rather than declare it.
     */
    private fun findTileStateMethod(startClass: Class<*>, classLoader: ClassLoader): Method? {
        val stateParam = try {
            classLoader.loadClass($$"com.android.systemui.plugins.qs.QSTile$State")
        } catch (_: Throwable) {
            null
        }
        var clazz: Class<*>? = startClass
        while (clazz != null) {
            for (method in clazz.declaredMethods) {
                if (method.name != "handleStateChanged") continue
                val params = method.parameterTypes
                if (params.size == 1 &&
                    (stateParam == null || stateParam.isAssignableFrom(params[0]) || params[0].isAssignableFrom(stateParam))
                ) {
                    return method
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    /**
     * Tile state-change animations are Lottie clips played at native speed;
     * slow them down so the state transition reads longer.
     */
    private fun slowDownTileLottie(root: View) {
        try {
            val lottieClass = root.context.classLoader
                .loadClass("com.airbnb.lottie.LottieAnimationView")
            val setSpeed = lottieClass.getDeclaredMethod("setSpeed", Float::class.javaPrimitiveType)
            setSpeed.isAccessible = true
            fun walk(view: View) {
                if (lottieClass.isInstance(view)) {
                    if (view.tag != TILE_LOTTIE_TAG) {
                        setSpeed.invoke(view, TILE_LOTTIE_SPEED)
                        view.tag = TILE_LOTTIE_TAG
                    }
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
            }
            walk(root)
        } catch (t: Throwable) {
            logger.debug("volume panel: lottie speed unavailable: ${t.message}")
        }
    }

    private fun setField(target: Any, name: String, value: Any?): Boolean {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(name)
                f.isAccessible = true
                f.set(target, value)
                return true
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (_: Throwable) {
                return false
            }
        }
        return false
    }

    private fun refreshAllTiles() {
        muteTile?.refresh?.invoke()
        dndTile?.refresh?.invoke()
        vibrateTile?.refresh?.invoke()
    }

    private fun hasVibrator(context: Context): Boolean {
        return try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.hasVibrator() == true
        } catch (_: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Resource / reflection helpers
    // ------------------------------------------------------------------

    /** Style ids are unresolvable on this ROM; the caller must have a fallback. */
    private fun resolveStyleId(context: Context): Int? {
        return resolveResourceId(
            context, "style",
            "Theme_SystemUI_Dialog_GlobalActionsLite",
            "Theme_SystemUI_Dialog",
            "Theme_SystemUI",
            warnOnMiss = false
        )
    }

    private fun resolveDrawableId(context: Context, vararg names: String): Int? {
        return resolveResourceId(context, "drawable", *names)
    }

    private fun resolveStringId(context: Context, vararg names: String): Int? {
        return resolveResourceId(context, "string", *names)
    }

    private fun resolveRawId(context: Context, name: String): Int? {
        return resolveResourceId(context, "raw", name)
    }

    /**
     * Resolves resource ids by name via Resources.getIdentifier. Class-based
     * lookups (R$style etc.) don't work on this ROM: the R inner classes are
     * stripped from the APK dex because AGP inlines the ids at compile time.
     */
    private fun resolveResourceId(
        context: Context,
        type: String,
        vararg names: String,
        warnOnMiss: Boolean = true
    ): Int? {
        val res = context.resources
        val pkg = context.packageName
        for (name in names) {
            val id = res.getIdentifier(name, type, pkg)
            if (id != 0) return id
        }
        val message = "volume panel: resolveResourceId($type, ${names.joinToString()}) found nothing"
        if (warnOnMiss) logger.warn(message) else logger.debug(message)
        return null
    }

    private fun resolveDimenPx(context: Context, name: String, fallbackPx: Int): Int {
        val id = resolveResourceId(context, "dimen", name)
        return if (id != null) context.resources.getDimensionPixelSize(id) else fallbackPx
    }

    private fun obtainThemeColor(context: Context): Int {
        return try {
            val value = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.textColorPrimary, value, true)) value.data else Color.GRAY
        } catch (_: Throwable) {
            Color.GRAY
        }
    }

    private fun formatPercent(value: Int, max: Int): String {
        val range = 1.coerceAtLeast(max)
        val percent = (value * 100f / range).roundToInt().coerceIn(0, 100)
        return "$percent%"
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

    /**
     * Minimal shim over the ZUI helper deciding whether the device has a
     * single volume stream (no separate ring stream row).
     */
    private object AudioSystemHelperShim {
        fun isSingleVolume(context: Context): Boolean {
            return try {
                val method = Class.forName("android.media.AudioSystem").methods
                    .firstOrNull { it.name == "isSingleVolume" } ?: return false
                method.invoke(null, context) as? Boolean ?: false
            } catch (_: Throwable) {
                false
            }
        }
    }

    /** Resolve any ApplicationInfo for a uid, for the app row icon. */
    private fun appIconInfo(context: Context, uid: Int): android.content.pm.ApplicationInfo? {
        return try {
            val packages = context.packageManager.getPackagesForUid(uid) ?: return null
            packages.firstOrNull()?.let {
                context.packageManager.getApplicationInfo(it, 0)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
