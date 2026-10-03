package com.qimian233.ztool.hook.modules.systemui.qs

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
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
        hookUpdateWindowSize()
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
        private const val QS_TILE_STATE_CLASS =
            $$"com.android.systemui.plugins.qs.QSTile$BooleanState"
        private const val RESOURCE_ICON_CLASS =
            $$"com.android.systemui.qs.tileimpl.QSTileImpl$ResourceIcon"
        private const val QS_FACTORY_IMPL_CLASS =
            "com.android.systemui.qs.tileimpl.QSFactoryImpl"
        private const val QS_TILE_CALLBACK_CLASS =
            $$"com.android.systemui.plugins.qs.QSTile$Callback"
        private const val EXPANDABLE_CLASS =
            "com.android.systemui.animation.Expandable"
        private const val APP_SECTION_TAG = "ztool_volume_panel_app_section"
        private const val VOLUME_PANEL_SLIDER_TAG =
            ControlCenterLongPressHook.VOLUME_PANEL_SLIDER_TAG
        private const val APP_VOLUME_SETTINGS_KEY = "zui_app_volume"
        private const val MAX_APP_ROWS = 3
        private const val TILE_LOTTIE_TAG = "ztool_tile_lottie_slowed"
        private const val TILE_LOTTIE_SPEED = 0.6f
        private const val TILE_RIPPLE_TAG = "ztool_tile_ripple"
        // VolumeSliderPercentageHook's label colors mirror the stock slider
        // icon filter against this raw-progress range; keep both in sync.
        private const val STOCK_VOLUME_RAW_RANGE = 100_000
        private const val BASE_PERCENT_COLOR = 0xffd8d8d8.toInt()
        private const val ICON_BASE_COLOR = 0x4Dffffff.toInt()
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val MODULE_PACKAGE = "com.qimian233.ztool"
        private const val MEDIA_OUTPUT_RECEIVER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogReceiver"
        // Public AOSP SystemUI action consumed by MediaOutputDialogReceiver.
        private const val ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG =
            "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"

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

    /**
     * SystemUIDialog implements ViewRootImpl.ConfigChangedCallback: on rotation
     * its onConfigurationChanged() calls updateWindowSize(), which force-sets
     * the window to the stock dialog size (fixed width, WRAP_CONTENT height) —
     * collapsing our fullscreen transparent root, so the panel visually
     * disappears. Re-assert the fullscreen layout after every stock run for
     * our panel dialog only; insets still drive the panel margins via the
     * root's OnApplyWindowInsetsListener.
     */
    private fun hookUpdateWindowSize() {
        val classLoader = systemUiClassLoader ?: return
        try {
            val dialogClass = classLoader.loadClass(SYSTEM_UI_DIALOG_CLASS)
            val update = findMethod(dialogClass, "updateWindowSize")
            hookWithId(update, "volume_panel_dialog_window_size") { chain ->
                chain.proceed()
                if (chain.thisObject === currentDialog) {
                    try {
                        val window = (chain.thisObject as Dialog).window
                        window?.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        logger.debug("volume panel: window size re-asserted after rotation")
                    } catch (t: Throwable) {
                        logger.warn("volume panel: window size re-assert failed: ${t.message}")
                    }
                }
                null
            }
            logger.info("volume panel: SystemUIDialog.updateWindowSize hook installed")
        } catch (t: Throwable) {
            logger.warn("volume panel: updateWindowSize hook failed: ${t.message}")
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
     * Resolved icon set for one column's stock-mirror behavior. Drawables are
     * resolved once per panel open (getIdentifier per drag frame would jank).
     * The media column fills the full stock family (mute / wired / BT / level
     * glyphs); the ring column only swaps normal <-> ringer-mute; app columns
     * use a static icon (null mirror).
     *
     * tintToStockBase: the ring glyphs are opaque white while the stock
     * speaker glyphs carry #ffffff@0.3 — when set, every drawable of this
     * mirror gets a color filter recomputed by the stock ramp formula so the
     * column's default shade matches the media column.
     */
    private class IconMirror(
        val baseline: android.graphics.drawable.Drawable?,
        val zero: android.graphics.drawable.Drawable? = null,
        val btZero: android.graphics.drawable.Drawable? = null,
        val comZero: android.graphics.drawable.Drawable? = null,
        val btNonMute: android.graphics.drawable.Drawable? = null,
        val comNonMute: android.graphics.drawable.Drawable? = null,
        val tintToStockBase: Boolean = false
    )

    /** Media column: the exact drawable family of updateVolumeStartImgForAnimationFlag. */
    private fun mediaIconMirror(context: Context): IconMirror = IconMirror(
        baseline = panelDrawable(context, "volume_no_poercing"),
        zero = panelDrawable(context, "volume_silence"),
        btZero = panelDrawable(context, "volume_start_bl_mute"),
        comZero = panelDrawable(context, "volume_start_com_mute"),
        btNonMute = panelDrawable(context, "volume_start_bt_non_mute"),
        comNonMute = panelDrawable(context, "volume_start_com_non_mute")
    )

    /** Ring column: ringer glyph swaps to its mute variant at volume zero. */
    private fun ringIconMirror(context: Context): IconMirror = IconMirror(
        baseline = panelDrawable(context, "ic_volume_ringer_zui"),
        zero = panelDrawable(context, "ic_volume_ringer_mute_zui"),
        tintToStockBase = true
    )

    /**
     * Prepares an opaque-white glyph (the ring family) for the stock-shade
     * mirror: mutates the drawable to a target fill color. The stock speaker
     * glyphs bake their shade into the vector's fillAlpha (#ffffff@0.3 base),
     * which an ImageView color filter cannot reproduce — SRC_ATOP keeps the
     * drawable's own alpha, so a 30%-alpha filter color would still render
     * opaque. Encoding the alpha directly into the fill color does.
     */
    private fun tintGlyph(drawable: android.graphics.drawable.Drawable?, color: Int) {
        drawable?.mutate()?.setTint(color)
    }

    private fun panelDrawable(context: Context, name: String): android.graphics.drawable.Drawable? {
        val id = resolveResourceId(context, "drawable", name, warnOnMiss = false)
        return if (id != null) context.getDrawable(id) else null
    }

    /**
     * Vertical slider column matching the control-center vertical slider
     * style: the bar spans the full column height, and the icon plus percent
     * label float INSIDE the bar's bottom end (overlay, like the native
     * slider), not as separate rows. The icon mirrors the stock slider icon
     * pipeline via [mirror] (family swaps + color-filter ramp); the percent
     * label only shows when VolumeSliderPercentageHook's switch is on. The
     * bar is a horizontal SeekBar rotated 270deg: the progress-increasing
     * axis points UP, so dragging up raises the value, dragging down lowers it.
     */
    private fun buildSliderColumn(
        context: Context,
        mirror: IconMirror?,
        fallbackIcon: android.graphics.drawable.Drawable?,
        initial: Int,
        maxValue: Int,
        iconSizeDp: Int = 30,
        onProgress: (Int) -> Unit = {},
        onStop: (Int) -> Unit = {}
    ): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val percentEnabled = isPercentageLabelEnabled()
        val percentView = TextView(context).apply {
            // Same styling as VolumeSliderPercentageHook's label.
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
            isSingleLine = true
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(resolveVolumePercentColor(initial))
            text = formatPercent(initial, maxValue)
            visibility = if (percentEnabled) View.VISIBLE else View.GONE
        }
        // The floating icon mirrors the stock slider icon pipeline: drawable
        // family swaps (mute / headset / level) plus the stock color-filter
        // ramp, all driven by the same progress the bar reports. iconSizeDp
        // is per column: the stock speaker glyph (volume_* family) draws at
        // ~55% of its 60dp viewport while the ringer/app glyphs fill ~90% of
        // theirs, so the media column takes a compensated, larger view.
        val iconView = ImageView(context).apply {
            val size = dp(context, iconSizeDp)
            layoutParams = LinearLayout.LayoutParams(size, size)
            (mirror?.baseline ?: fallbackIcon)?.let { setImageDrawable(it) }
        }
        val barLength = dp(context, 260)
        val barThickness = resolveDimenPx(context, "brightness_bar_height", dp(context, 18))
            .coerceAtLeast(dp(context, 28))
        // Overlay stack anchored at the bar's bottom end: icon on top of the
        // percent text, both centered horizontally inside the bar width. The
        // gap between them is a fraction of the icon size so visually the
        // icon-to-label spacing matches across columns of different icon
        // sizes.
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        overlay.addView(iconView)
        overlay.addView(percentView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = -(iconSizeDp / 3) + dp(context, 1) })
        val barSlot = FrameLayout(context)
        barSlot.addView(buildColumnBar(
            context, initial, maxValue, percentView, iconView, mirror, onProgress, onStop
        ).apply {
            thumb = null
            progressDrawable = resolveSliderDrawable(context)
            minHeight = barThickness
            maxHeight = barThickness
            rotation = 270f
            layoutParams = FrameLayout.LayoutParams(barLength, barThickness, Gravity.CENTER)
        })
        barSlot.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(context, 26)
        })
        column.addView(barSlot, LinearLayout.LayoutParams(
            barThickness, barLength
        ).apply {
            setMargins(dp(context, 6), 0, dp(context, 6), 0)
        })
        if (mirror != null) {
            updateColumnIcon(iconView, mirror, initial)
        } else {
            fallbackIcon?.let {
                iconView.setImageDrawable(it)
                iconView.clearColorFilter()
            }
        }
        return column
    }

    /** The panel's percent label mirrors VolumeSliderPercentageHook's switch. */
    private fun isPercentageLabelEnabled(): Boolean {
        return try {
            remotePreferences.getBoolean(PreferenceKeys.VOLUME_SLIDER_PERCENTAGE.name, false)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Icon color ramp, same stock formula as the percent label. Base color is
     * the speaker glyph's own #ffffff@0.3 (volume_piercing fillAlpha), which
     * the stock code shows filter-free below level 3.
     */
    private fun resolveIconColor(percent: Int): Int {
        val rawProgress = percent.coerceIn(0, 100) * (STOCK_VOLUME_RAW_RANGE / 100)
        val level = kotlin.math.ceil(rawProgress / 10000.0f).toInt()
        if (level < 3) {
            return ICON_BASE_COLOR
        }
        val fMin = ((level - 2) / 3.0f).coerceAtMost(1.0f)
        val gray = ((1.0f - fMin) * 216.0f).toInt()
        val alpha = (kotlin.math.floor(fMin * 85.0f).toInt() + 170).coerceAtMost(255)
        return Color.argb(alpha, gray, gray, gray)
    }

    /**
     * Mirror of the stock updateVolumeStartImgForAnimationFlag, restricted to
     * what a static ImageView can express (no lottie): drawable swaps by
     * headset state and volume band, plus the same color-filter ramp the
     * percent label uses (level = ceil(raw/10000), filter from level 3).
     * Lottie volume-zero animation and the animation-flag subtleties are
     * intentionally dropped; the stock silent state also just shows a static
     * icon in its non-animated branch.
     */
    private fun updateColumnIcon(iconView: ImageView, mirror: IconMirror, percent: Int) {
        val headset = resolveHeadSetState(iconView.context)
        val color = resolveIconColor(percent)
        val drawable = when {
            percent <= 0 -> when (headset) {
                HeadSetType.BT -> mirror.btZero
                HeadSetType.COM -> mirror.comZero
                else -> mirror.zero
            } ?: mirror.zero
            headset == HeadSetType.BT -> mirror.btNonMute ?: mirror.baseline
            headset == HeadSetType.COM -> mirror.comNonMute ?: mirror.baseline
            else -> mirror.baseline
        }
        if (mirror.tintToStockBase) {
            // Opaque ring glyph: the shade must live in the fill color itself
            // (color filters preserve the drawable's own opaque alpha), so
            // re-tint per frame with the stock ramp color.
            tintGlyph(drawable, color)
        }
        drawable?.let {
            // The two ring vectors have mismatched viewports (17x20 vs 25x24
            // with the bell at ~75% height), so the mute variant renders
            // visibly smaller in the same view. Scale its drawable bounds to
            // equalize the rendered bell height with the unmuted glyph.
            if (mirror.tintToStockBase && it === mirror.zero) {
                val w = it.intrinsicWidth
                val h = it.intrinsicHeight
                if (w > 0 && h > 0) it.setBounds(0, 0, (w * 0.8f).toInt(), (h * 0.8f).toInt())
            } else {
                it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
            }
            iconView.setImageDrawable(it)
        }
        when {
            mirror.tintToStockBase -> iconView.clearColorFilter()
            // Stock applies the ramp only on the speaker non-mute glyph; the
            // headset glyphs and the zero state carry their own alpha instead.
            percent > 0 && headset == null -> iconView.setColorFilter(color)
            else -> iconView.clearColorFilter()
        }
    }

    /**
     * Mirror of the stock isHeadSetConnect: BT headset when an A2DP-profile
     * connection is live, wired headset when any output device of the classic
     * wired types (WIRED_HEADSET/HEADPHONES/USB_HEADSET/HEARING_AID family) is
     * present. BT wins over wired exactly like the stock code.
     */
    private fun resolveHeadSetState(context: Context): HeadSetType? {
        return try {
            val bt = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? android.bluetooth.BluetoothManager
            val btConnected = bt?.adapter
                ?.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) ==
                android.bluetooth.BluetoothProfile.STATE_CONNECTED
            if (btConnected) return HeadSetType.BT
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val wired = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wired) HeadSetType.COM else null
        } catch (t: Throwable) {
            logger.debug("volume panel: headset detection failed: ${t.message}")
            null
        }
    }

    private enum class HeadSetType { BT, COM }

    /**
     * Percent label color, mirroring VolumeSliderPercentageHook's stock
     * updateVolumeStartImgForAnimationFlag formula: discrete level =
     * ceil(rawProgress/10000), filter from level 3 with fMin = (level-2)/3.
     * The panel slider runs on a 0..100 percent scale, so the percent is
     * mapped onto the stock slider's raw progress range first.
     */
    private fun resolveVolumePercentColor(percent: Int): Int {
        val rawProgress = percent.coerceIn(0, 100) * (STOCK_VOLUME_RAW_RANGE / 100)
        val level = kotlin.math.ceil(rawProgress / 10000.0f).toInt()
        if (level < 3) {
            return BASE_PERCENT_COLOR
        }
        val fMin = ((level - 2) / 3.0f).coerceAtMost(1.0f)
        val gray = ((1.0f - fMin) * 216.0f).toInt()
        val alpha = (kotlin.math.floor(fMin * 85.0f).toInt() + 170).coerceAtMost(255)
        return Color.argb(alpha, gray, gray, gray)
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
        iconView: ImageView?,
        mirror: IconMirror?,
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
        // Marks this bar for ControlCenterLongPressHook: it is a bare
        // SeekBarNps with no ToggleSliderView ancestor, so the tag is the
        // only way the shared squish detector can recognize it (and skip
        // its long-press trigger — the panel is already open).
        if (isNps) bar.tag = VOLUME_PANEL_SLIDER_TAG
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
                val percent = (progress * 100 / scale.toFloat()).roundToInt().coerceIn(0, 100)
                percentView.text = formatPercent(progress, bar.max)
                percentView.setTextColor(resolveVolumePercentColor(percent))
                if (iconView != null && mirror != null) {
                    updateColumnIcon(iconView, mirror, percent)
                }
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
        val mirror = when (stream) {
            AudioManager.STREAM_MUSIC -> mediaIconMirror(context)
            AudioManager.STREAM_RING -> ringIconMirror(context)
            else -> null
        }
        val column = buildSliderColumn(
            context,
            mirror,
            iconRes?.let { context.getDrawable(it) },
            initial = (am.getStreamVolume(stream) * 100f / streamMax).roundToInt(),
            maxValue = 100,
            iconSizeDp = if (stream == AudioManager.STREAM_MUSIC) 36 else 26,
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
            context, null, icon,
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

        muteTile = buildTile(context, classLoader, "mute")
        dndTile = buildTile(context, classLoader, "dnd")
        vibrateTile = if (hasVibrator(context)) buildTile(context, classLoader, "vibrate") else null
        val mediaTile = buildMediaOutputTile(context, classLoader)
        for (tile in listOf(muteTile, dndTile, vibrateTile, mediaTile)) {
            if (tile == null) continue
            row.addView(tile.view, LinearLayout.LayoutParams(tileWidth, tileHeight).apply {
                setMargins(margin, 0, margin, 0)
            })
        }
        refreshAllTiles()
        return row
    }

    /**
     * The media output switcher tile: single-state, no QSTile behind it — its
     * whole behavior is launching the system media output dialog via the same
     * broadcast the ZTool quick-settings tile sends (MediaOutputDialogReceiver
     * handles it; MediaOutputDialogCenterHook fixes centering/theme on this
     * path). Rendered with the same CustomizeTileView for visual consistency.
     */
    private fun buildMediaOutputTile(
        context: Context,
        classLoader: ClassLoader
    ): TileUi? {
        return try {
            val tileViewClass = classLoader.loadClass(CUSTOMIZE_TILE_VIEW_CLASS)
            val tileView = tileViewClass.getConstructor(Context::class.java)
                .newInstance(context) as ViewGroup
            val handleStateChanged = findTileStateMethod(tileViewClass, classLoader)
                ?: throw NoSuchMethodException($$"handleStateChanged(QSTile$State) not found")
            handleStateChanged.isAccessible = true

            val state = classLoader.loadClass(QS_TILE_STATE_CLASS)
                .getDeclaredConstructor().newInstance()
            // Label comes from ZTool's own resources (i18n handled app-side);
            // the cross-package read follows the RecentTaskMemoryViewHook
            // pattern: createPackageContext with IGNORE_SECURITY.
            val label = moduleString(context, "ztool_media_output_label", "媒体输出")
            // Icon: the media-output dialog's device-volume glyph — the only
            // media-output-family drawable on this ROM. The dialog draws it
            // at ~24dp; the state icon pipeline scales with the tile's own
            // qs_icon_size, so no manual sizing.
            val iconRes = resolveDrawableId(
                context,
                "media_output_icon_volume", "media_output_icon_volume_off",
                "media_output_title_icon_area",
                "ic_media_output", "media_output", "ic_audio_output"
            )
            setField(state, "label", label)
            setField(state, "contentDescription", label)
            setField(state, "state", 1)
            setField(state, "value", false)
            if (iconRes != null) {
                val icon = resourceIcon(classLoader, iconRes)
                if (icon != null) setField(state, "icon", icon)
            }
            setField(state, "spec", "ztool_media_output")
            handleStateChanged.invoke(tileView, state)
            slowDownTileLottie(tileView)

            tileView.setOnClickListener {
                // Same path as the ZTool quick-settings tile: explicit
                // broadcast to SystemUI's static receiver.
                context.sendBroadcast(
                    Intent(ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
                        setClassName(SYSTEMUI_PACKAGE, MEDIA_OUTPUT_RECEIVER_CLASS)
                    }
                )
                // The system dialog replaces this panel, matching the
                // shade-collapses-when-dialog-opens behavior.
                currentDialog?.dismiss()
            }
            TileUi(tileView) {}
        } catch (t: Throwable) {
            logger.error("Failed to build media output tile", t)
            null
        }
    }

    /**
     * Reads a string from ZTool's own package resources, so tile labels get
     * proper i18n from the app's strings.xml instead of literals baked into
     * the hook code (same cross-package read as RecentTaskMemoryViewHook).
     */
    private fun moduleString(hostContext: Context, resourceName: String, fallback: String): String {
        return try {
            val moduleContext = hostContext.createPackageContext(
                MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY
            )
            val resId = moduleContext.resources.getIdentifier(
                resourceName, "string", MODULE_PACKAGE
            )
            if (resId != 0) moduleContext.resources.getString(resId) else fallback
        } catch (t: Throwable) {
            logger.warn("volume panel: module string $resourceName failed: ${t.message}")
            fallback
        }
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
        spec: String
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
            val setListening = findMethod(tileClass, "setListening", Any::class.java,
                Boolean::class.javaPrimitiveType)

            // Expandable from the view, same as the native dialog's
            // Expandable.Companion.fromView. R8 flattened the Companion, so
            // fromView lives directly on the interface as a static; fall back
            // to a hand-rolled implementation — transitionView() returns the
            // tile, the transition-controller methods return null (their
            // absence only skips launch animations, never the action).
            val expandableClass = classLoader.loadClass(EXPANDABLE_CLASS)
            val expandable: Any = try {
                findMethod(expandableClass, "fromView", View::class.java)
                    .invoke(null, tileView)
            } catch (_: Throwable) {
                java.lang.reflect.Proxy.newProxyInstance(
                    classLoader, arrayOf(expandableClass)
                ) { _, method, _ ->
                    if (method.name == "transitionView") tileView else null
                }
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
             * tile.getState() every time we ask for a refresh. On a state
             * flip the tile's own lottie clip segment (min/maxFrame the tile
             * wrote into its state, e.g. QMuteTile's silent_off) is played
             * directly on the Lottie view, since CustomizeTileView kills the
             * native startLottieAnimation path.
             */
            var lastAppliedState = Int.MIN_VALUE
            fun applyStateSync() {
                try {
                    val state = getState.invoke(tile) ?: return
                    // mState.spec was captured before setTileSpec ran (null);
                    // handleStateChanged dereferences it for subtitles.
                    setField(state, "spec", tileSpec)
                    handleStateChanged.invoke(tileView, state)
                    slowDownTileLottie(tileView)
                    val newState = (findField(state.javaClass, "state")
                        .get(state) as? Int) ?: return
                    if (lastAppliedState != Int.MIN_VALUE && newState != lastAppliedState) {
                        val min = findFieldSafe(state, "minFrame") as? Int
                        val max = findFieldSafe(state, "maxFrame") as? Int
                        val lottieRes = findFieldSafe(state, "lottieRawResId") as? Int
                        if (lottieRes != null && lottieRes != 0 &&
                            min != null && max != null && min != max
                        ) {
                            playTileLottieSegment(tileView, min, max)
                        }
                    }
                    lastAppliedState = newState
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
            val ripple = installTileRipple(tileView)
            // Align the ripple with the tile's own animation: the state lottie
            // duration is the device's real tile-animation length (and our
            // slowDownTileLottie stretches it further by TILE_LOTTIE_SPEED).
            // The composition may load asynchronously, hence the listener.
            val lottieView = findLottieView(tileView)
            if (lottieView == null) {
                logger.trace("volume panel: no lottie view in tile (ripple keeps default)")
            }
            if (lottieView != null) {
                fun applyDuration() {
                    try {
                        val duration = findMethod(
                            lottieView.javaClass, "getDuration"
                        ).invoke(lottieView) as? Long ?: 0L
                        if (duration > 0) {
                            val perceived = (duration / TILE_LOTTIE_SPEED).toLong()
                            ripple?.setDuration(perceived)
                            logger.trace(
                                "volume panel: lottie duration=${duration}ms, " +
                                    "ripple aligned to ${perceived}ms"
                            )
                        }
                    } catch (t: Throwable) {
                        logger.debug("volume panel: lottie duration read failed: ${t.message}")
                    }
                }
                applyDuration()
                try {
                    val listenerClass = classLoader
                        .loadClass("com.airbnb.lottie.LottieOnCompositionLoadedListener")
                    val loadedListener = java.lang.reflect.Proxy.newProxyInstance(
                        classLoader, arrayOf(listenerClass)
                    ) { _, _, _ -> applyDuration(); null }
                    findMethod(
                        lottieView.javaClass, "addLottieOnCompositionLoadedListener",
                        listenerClass
                    ).invoke(lottieView, loadedListener)
                } catch (_: Throwable) {
                }
            }
            // The colored tile square is NOT the whole view: the background
            // LayerDrawable sits on the iconFrame child (see
            // CustomQSTileViewImpl.updateBackground); the rest is label space.
            // Ripple bounds follow iconFrame so the effect stays in the square.
            val iconFrame = try {
                findField(tileView.javaClass, "iconFrame").get(tileView) as? View
            } catch (_: Throwable) {
                null
            }
            tileView.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        logger.trace(
                            "volume panel: tile DOWN at(${event.x},${event.y}) " +
                                "view=${view.width}x${view.height} ripple=" +
                                (ripple != null)
                        )
                        // Ripple from the finger, like the stock press effect,
                        // clipped to the icon square rather than the whole
                        // tile. The overlay draws in the TILE's canvas space,
                        // so the area is iconFrame's absolute rect and the
                        // origin is the raw touch point (no offset math).
                        if (iconFrame != null) {
                            ripple?.trigger(
                                event.x, event.y,
                                Rect(
                                    iconFrame.left, iconFrame.top,
                                    iconFrame.left + iconFrame.width,
                                    iconFrame.top + iconFrame.height
                                )
                            )
                        } else {
                            ripple?.trigger(
                                event.x, event.y,
                                Rect(0, 0, view.width, view.height)
                            )
                        }
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
            synchronized(boundCallbacks) {
                boundCallbacks.add(TileBinding(tile, callback, callbackClass, host))
            }
            TileUi(tileView, ::refresh)
        } catch (t: Throwable) {
            logger.error("Failed to build QS tile view", t)
            null
        }
    }

    /** Tile listening registrations to undo when the panel dismisses. */
    private data class TileBinding(val tile: Any, val callback: Any, val callbackInterface: Class<*>, val host: Any)

    private val boundCallbacks = mutableListOf<TileBinding>()

    /** Releases QSTile listeners created for the panel; mirrors onStop(). */
    private fun releaseBoundTiles() {
        val bindings = synchronized(boundCallbacks) {
            val copy = boundCallbacks.toList()
            boundCallbacks.clear()
            copy
        }
        for (binding in bindings) {
            try {
                // The callback is a Proxy, so look the method up by the
                // QSTile$Callback interface parameter, never Proxy's class.
                findMethod(
                    binding.tile.javaClass, "removeCallback", binding.callbackInterface
                ).invoke(binding.tile, binding.callback)
                findMethod(
                    binding.tile.javaClass, "setListening", Any::class.java,
                    Boolean::class.javaPrimitiveType
                ).invoke(binding.tile, binding.host, false)
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

    /** Field read across the class hierarchy; null when absent. */
    private fun findFieldSafe(target: Any, name: String): Any? {
        return try {
            findField(target.javaClass, name).get(target)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * A self-drawn press ripple for the tiles. The stock background ripple is
     * unreachable from module code (CustomizeTileView disables the trigger
     * chain and the ZUI background ignores external pressed flashes; a
     * foreground RippleDrawable renders invisibly on this theme, and the tile
     * view's own foreground/draw pipeline is equally unreliable). So the
     * effect lives in the view's ViewOverlay — a draw layer independent of
     * onDraw — as an expanding circle from the touch point that fades out,
     * clipped to the tile's rounded bounds.
     */
    private class TileRippleDrawable(
        tintColor: Int,
        private val startRadiusPx: Float,
        private val log: (String) -> Unit
    ) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        ).apply {
            style = android.graphics.Paint.Style.FILL
            // Set once here: assigning paint.color per draw() would reset the
            // animation-driven alpha to the tint's own alpha every frame.
            color = tintColor
        }
        private val clip = android.graphics.Path()
        private var animator: ValueAnimator? = null
        private var originX = 0f
        private var originY = 0f
        private var bounds = Rect(0, 0, 0, 0)
        private var drawCallCount = 0
        private var lastLoggedDraw = 0L
        private var durationMs = RIPPLE_DURATION_MS
        private var maxRadius = 0f

        /** Ripple run length, aligned with the tile's own animation. */
        fun setDuration(ms: Long) {
            if (ms > 0) durationMs = ms
        }

        /**
         * ViewOverlay drawables do NOT inherit the view's size (unlike
         * foreground), so bounds must be pushed in at trigger time — with the
         * default (0,0,0,0) the clip rect is empty and nothing renders.
         * Coordinates are the HOST view's: the overlay draws in the host's
         * canvas space, so the bounds are iconFrame's absolute rect inside
         * the tile and the origin is the raw touch point.
         */
        fun trigger(x: Float, y: Float, area: Rect) {
            originX = x
            originY = y
            bounds = Rect(area)
            // Distance to the farthest corner: the radius the circle needs to
            // fully cover the area from wherever the finger landed. Linear
            // growth toward hypot(w,h) leaves off-center origins uncovered
            // and makes the tail of the run visually dead.
            maxRadius = maxOf(
                kotlin.math.hypot(
                    (x - area.left).toDouble(), (y - area.top).toDouble()
                ),
                kotlin.math.hypot(
                    (area.right - x).toDouble(), (y - area.top).toDouble()
                ),
                kotlin.math.hypot(
                    (x - area.left).toDouble(), (area.bottom - y).toDouble()
                ),
                kotlin.math.hypot(
                    (area.right - x).toDouble(), (area.bottom - y).toDouble()
                )
            ).toFloat()
            log("ripple trigger at($x,$y) bounds=$area maxR=$maxRadius " +
                "color=${Integer.toHexString(paint.color)} startR=$startRadiusPx " +
                "cornerR=$cornerRadiusPx dur=${durationMs}ms")
            animator?.cancel()
            drawCallCount = 0
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                // Hold near-full opacity through the expansion, then fade in
                // the tail — closer to the stock enter+fade rhythm.
                interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener { animation ->
                    val t = animation.animatedValue as Float
                    val fade = ((t - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)
                    paint.alpha = (RIPPLE_MAX_ALPHA * (1f - fade * fade)).toInt()
                    invalidateSelf()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        paint.alpha = 0
                        invalidateSelf()
                        log("ripple animation ended, draw calls total=$drawCallCount")
                    }
                })
                start()
            }
        }

        override fun draw(canvas: Canvas) {
            drawCallCount++
            if (paint.alpha <= 0) return
            val t = animator?.animatedValue as? Float ?: run {
                throttledLog("ripple draw skipped: animator null/expired")
                return
            }
            val bounds = bounds
            if (bounds.isEmpty) {
                throttledLog("ripple draw skipped: empty bounds")
                return
            }
            throttledLog(
                "ripple draw t=$t alpha=${paint.alpha} " +
                    "bounds=$bounds origin=($originX,$originY)"
            )
            val maxR = maxRadius
            val radius = startRadiusPx + (maxR - startRadiusPx) * t
            clip.reset()
            clip.addRoundRect(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(),
                cornerRadiusPx, cornerRadiusPx, android.graphics.Path.Direction.CW
            )
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawCircle(originX, originY, radius, paint)
            canvas.restore()
        }

        /** draw() fires every frame; log at most twice per animation run. */
        private fun throttledLog(message: String) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastLoggedDraw < 150L) return
            lastLoggedDraw = now
            log(message)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java", replaceWith = ReplaceWith(""))
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

        companion object {
            private const val RIPPLE_MAX_ALPHA = 80
            private const val RIPPLE_DURATION_MS = 500L
            private const val FADE_START = 0.6f
            var cornerRadiusPx = 0f
        }
    }

    /**
     * Finds the GradientDrawable that paints the tile's colored square:
     * iconFrame.background is the qs_tile_background RippleDrawable whose
     * "qs_tile_background_base" layer (or any nested layer) is the shape.
     */
    private fun iconFrameBackgroundGradient(tileView: View): android.graphics.drawable.GradientDrawable? {
        val iconFrame = try {
            findField(tileView.javaClass, "iconFrame").get(tileView) as? View
        } catch (_: Throwable) {
            null
        } ?: return null
        val background = iconFrame.background ?: return null
        val candidates = mutableListOf<Drawable>()
        fun collect(drawable: Drawable?) {
            when (drawable) {
                is android.graphics.drawable.GradientDrawable -> candidates.add(drawable)
                is android.graphics.drawable.LayerDrawable -> {
                    for (i in 0 until drawable.numberOfLayers) {
                        collect(drawable.getDrawable(i))
                    }
                }
            }
        }
        collect(background)
        return candidates.firstOrNull() as android.graphics.drawable.GradientDrawable?
    }

    /** Live corner radius of a GradientDrawable; null when absent or unset. */
    private fun gradientCornerRadius(drawable: Drawable?): Float? {
        val gradient = drawable as? android.graphics.drawable.GradientDrawable ?: return null
        return try {
            gradient.cornerRadius.takeIf { it > 0f }
        } catch (_: Throwable) {
            null
        }
    }

    /** First LottieAnimationView in the tile's view tree, or null. */
    private fun findLottieView(root: View): View? {
        val lottieClass = try {
            root.context.classLoader.loadClass("com.airbnb.lottie.LottieAnimationView")
        } catch (_: Throwable) {
            return null
        }
        if (lottieClass.isInstance(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findLottieView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** Installs the self-drawn press ripple into the tile's ViewOverlay. */
    private fun installTileRipple(tileView: View): TileRippleDrawable? {
        if (tileView.tag == TILE_RIPPLE_TAG) return null
        val context = tileView.context
        // The user (or another module/theme) can change the tile corner radius
        // at runtime by mutating the background shape — the qs_corner_radius
        // dimen stays stale in that case. Read the live value off the tile's
        // own background GradientDrawable (it lives on iconFrame), fall back
        // to the dimen.
        val liveRadius = gradientCornerRadius(iconFrameBackgroundGradient(tileView))
        TileRippleDrawable.cornerRadiusPx =
            liveRadius ?: resolveDimenPx(context, "qs_corner_radius", dp(context, 28)).toFloat()
        logger.trace(
            "volume panel: ripple cornerRadius=${TileRippleDrawable.cornerRadiusPx} " +
                "(live=${liveRadius ?: "unavailable"})"
        )
        // colorControlHighlight resolves to a ColorStateList reference; the
        // TypedValue.data path returned 0x10b2 (alpha 0x00 = invisible), so
        // take the CSL's default color instead and force a visible alpha.
        val highlight = try {
            val styles = context.obtainStyledAttributes(
                intArrayOf(android.R.attr.colorControlHighlight)
            )
            val color = styles.getColor(0, 0x33888888.toInt())
            styles.recycle()
            (color and 0x00FFFFFF) or 0x28000000.toInt() // ~16% alpha
        } catch (_: Throwable) {
            0x33888888.toInt()
        }
        // ViewOverlay draws above the view's own content regardless of the
        // tile's draw overrides; the drawable requests its own invalidations.
        val ripple = TileRippleDrawable(highlight, dp(context, 22).toFloat()) {
            logger.trace(it)
        }
        try {
            tileView.overlay.add(ripple)
        } catch (t: Throwable) {
            logger.warn("volume panel: overlay add failed: ${t.message}")
            return null
        }
        logger.trace(
            "volume panel: ripple installed on tile (overlay count=" +
                tileView.overlay.javaClass.name + ", color=0x" +
                Integer.toHexString(highlight) + ")"
        )
        tileView.tag = TILE_RIPPLE_TAG
        return ripple
    }

    /**
     * Tile state-change animations are Lottie clips played at native speed;
     * slow them down so the state transition reads longer.
     */
    private fun playTileLottieSegment(root: View, min: Int, max: Int) {
        try {
            val lottieClass = root.context.classLoader
                .loadClass("com.airbnb.lottie.LottieAnimationView")
            fun walk(view: View) {
                if (lottieClass.isInstance(view)) {
                    try {
                        findMethod(lottieClass, "setMinAndMaxFrame",
                            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            .invoke(view, min, max)
                    } catch (_: Throwable) {
                    }
                    try {
                        findMethod(lottieClass, "setFrame", Int::class.javaPrimitiveType)
                            .invoke(view, min)
                    } catch (_: Throwable) {
                    }
                    try {
                        findMethod(lottieClass, "playAnimation").invoke(view)
                    } catch (_: Throwable) {
                    }
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
            }
            walk(root)
        } catch (t: Throwable) {
            logger.debug("volume panel: lottie segment failed: ${t.message}")
        }
    }

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

    /** QSTileImpl$ResourceIcon.get(resId): the stock tile icon wrapper. */
    private fun resourceIcon(classLoader: ClassLoader, resId: Int): Any? {
        return try {
            val resourceIconClass = classLoader.loadClass(RESOURCE_ICON_CLASS)
            val get = resourceIconClass.getDeclaredMethod("get", Int::class.javaPrimitiveType)
            get.isAccessible = true
            get.invoke(null, resId)
        } catch (_: Throwable) {
            null
        }
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
