package com.qimian233.ztool.hook.modules.systemui.qs

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import com.qimian233.ztool.hook.modules.systemui.qs.VolumeSliderLongPressHook.Companion.onVolumeSliderLongPress
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
 * volume trigger calls [onVolumeSliderLongPress]. Everything here is reflection:
 * module classes cannot subclass SystemUI classes, and the relevant SystemUI
 * entry points (SystemUIDialog, CustomizeTileView) are only reachable that way.
 *
 * Container: stock `SystemUIDialog` built via its public 3-arg constructor
 * (it self-wires Dependency-based singletons, SCREEN_OFF dismissal and system
 * window flags), themed like BrightnessDetailDialog
 * (Theme_SystemUI_Dialog_GlobalActionsLite).
 *
 * Tiles: NOT taken from QSHostAdapter (no getTile(spec) entry point exists and
 * mute/vibrate/zen are outside the brightness-related collection). Instead a
 * `CustomizeTileView` per tile is fed a reflectively-constructed
 * `QSTile.BooleanState`, and the state logic mirrors the stock tiles:
 * QMuteTile (ringer mode 0 <-> 2), QVibrateTile (vibrate_on + vibration
 * intensities; offered only when Vibrator.hasVibrator()), and DND via
 * NotificationManager interruption filter.
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
    private var contentObservers = mutableListOf<ContentObserver>()

    /** NotificationPanelViewController used to fade the shade content out/in. */
    private var behindListener: Any? = null

    /** BrightnessDetailDialogController, source of the QS frame geometry. */
    private var brightnessDialogController: Any? = null

    override fun getModuleName(): String = PreferenceKeys.VOLUME_LONG_PRESS_PANEL.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        systemUiClassLoader = param.defaultClassLoader
        hook = this
        hookVolumeDialogImpl()
        logger.info("VolumeSliderLongPressHook installed")
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
        private const val VOLUME_DIALOG_IMPL_CLASS =
            "com.android.systemui.volume.VolumeDialogImpl"
        private const val CUSTOMIZE_TILE_VIEW_CLASS =
            "com.android.systemui.qs.customize.CustomizeTileView"
        private const val QS_TILE_STATE_CLASS =
            $$"com.android.systemui.plugins.qs.QSTile$BooleanState"
        private const val RESOURCE_ICON_CLASS =
            $$"com.android.systemui.qs.tileimpl.QSTileImpl$ResourceIcon"
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
        // Layout mirrors BrightnessDetailDialog: the panel is anchored at the
        // QS frame's left edge (left margin = qsFrameX + qsMarginStart) and
        // vertically centered on the screen's horizontal midline.
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
            // Native BrightnessDetailDialog placement: left margin =
            // qsFrameX - leftInset + qsMarginStart (the QS frame's right edge).
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            marginStart = resolvePanelMarginStart(context)
        })

        behindListener = resolveBehindListener(triggerView)
        dialog.setOnDismissListener {
            panelShowing = false
            currentDialog = null
            unregisterPanelObservers()
            // Reveal the control-center widgets the dialog had hidden and
            // release the view tree so the static hook reference cannot leak it.
            setDialogBehindAlpha(1f)
            behindListener = null
            brightnessDialogController = null
            dialogContext = null
            appSection = null
        }
        registerPanelObservers(context)

        panelShowing = true
        currentDialog = dialog
        mainHandler.post { refreshAppSection() }
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
     * Left margin of the panel: the native dialog uses
     * qsFrameX - leftInset + qsMarginStart (the QS frame's left edge on
     * split-shade / landscape layouts — measured 759.2dp on the reference
     * device). Resolve the frame position at runtime from
     * ShadeController.getQuickSettingsController().getQsFrameX(); fall back to
     * the measured constant when unavailable.
     */
    private fun resolvePanelMarginStart(context: Context): Int {
        val controller = brightnessDialogController
        if (controller != null) {
            try {
                val shade = findField(controller.javaClass, "mShadeController").get(controller)
                val qs = findMethod(shade.javaClass, "getQuickSettingsController").invoke(shade)
                val frameX = findMethod(qs.javaClass, "getQsFrameX").invoke(qs) as Float
                val qsMarginStart = resolveDimenPx(context, "qs_margin_start", dp(context, 24))
                val margin = frameX.toInt() + qsMarginStart
                return margin
            } catch (t: Throwable) {
                logger.debug("volume panel: qsFrameX unavailable: ${t.message}")
            }
        }
        return dp(context, 759)
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
        barSlot.addView(SeekBar(context).apply {
            max = maxValue
            progress = initial
            thumb = null
            progressDrawable = resolveSliderDrawable(context)
            minHeight = barThickness
            maxHeight = barThickness
            rotation = 270f
            layoutParams = FrameLayout.LayoutParams(barLength, barThickness, Gravity.CENTER)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    percentView.text = formatPercent(progress, bar.max)
                    onProgress(progress)
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}

                override fun onStopTrackingTouch(bar: SeekBar) {
                    onStop(bar.progress)
                }
            })
        })
        column.addView(barSlot, LinearLayout.LayoutParams(
            barThickness, barLength
        ).apply {
            setMargins(dp(context, 6), 0, dp(context, 6), 0)
        })
        column.addView(percentView)
        return column
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

        muteTile = buildTile(
            context, classLoader,
            labelResNames = arrayOf("widget_text_mute"),
            iconActiveNames = arrayOf("controlcenter_1_btn_mute"),
            iconInactiveNames = arrayOf("controlcenter_1_btn_mute_inactive", "controlcenter_1_btn_mute"),
            isOn = { muteTileOn() },
            onToggle = { toggleMute() }
        )
        dndTile = buildTile(
            context, classLoader,
            labelResNames = arrayOf("quick_settings_dnd_label", "widget_text_disturb_free"),
            // Stock DndTile uses zenmode_off for BOTH states; the tile tint
            // conveys activation, so no separate "on" icon exists.
            iconActiveNames = arrayOf("controlcenter_1_btn_zenmode_off"),
            iconInactiveNames = arrayOf("controlcenter_1_btn_zenmode_off"),
            isOn = { dndTileOn() },
            onToggle = { toggleDnd() }
        )
        if (hasVibrator(context)) {
            vibrateTile = buildTile(
                context, classLoader,
                labelResNames = arrayOf("widget_text_vibration"),
                iconActiveNames = arrayOf("controlcenter_1_btn_shock"),
                iconInactiveNames = arrayOf("controlcenter_1_btn_shock"),
                isOn = { vibrateTileOn(context) },
                onToggle = { toggleVibrate(context) }
            )
        }
        for (tile in listOf(muteTile, dndTile, vibrateTile)) {
            if (tile == null) continue
            row.addView(tile.view, LinearLayout.LayoutParams(tileWidth, tileHeight).apply {
                setMargins(margin, 0, margin, 0)
            })
        }
        refreshAllTiles()
        return row
    }

    private fun buildTile(
        context: Context,
        classLoader: ClassLoader,
        labelResNames: Array<String>,
        iconActiveNames: Array<String>,
        iconInactiveNames: Array<String>,
        isOn: () -> Boolean,
        onToggle: () -> Unit
    ): TileUi? {
        return try {
            val tileViewClass = classLoader.loadClass(CUSTOMIZE_TILE_VIEW_CLASS)
            val tileView = tileViewClass.getConstructor(Context::class.java)
                .newInstance(context) as ViewGroup
            // Declared on an ancestor as handleStateChanged(QSTile$State); a
            // getDeclaredMethod with the BooleanState subtype fails, so walk
            // the hierarchy and match the single parameter by assignability.
            val handleStateChanged = findTileStateMethod(tileViewClass, classLoader)
                ?: throw NoSuchMethodException($$"handleStateChanged(QSTile$State) not found")
            handleStateChanged.isAccessible = true

            val stateClass = classLoader.loadClass(QS_TILE_STATE_CLASS)
            val iconActive = iconActiveNames.firstNotNullOfOrNull { resolveDrawableId(context, it) }
            val iconInactive =
                iconInactiveNames.firstNotNullOfOrNull { resolveDrawableId(context, it) }
            val label = labelResNames.firstNotNullOfOrNull { resolveStringId(context, it) }
                ?.let { context.getString(it) } ?: ""

            fun buildState(on: Boolean): Any {
                val state = stateClass.getDeclaredConstructor().newInstance()
                setField(state, "label", label)
                setField(state, "contentDescription", label)
                setField(state, "state", if (on) 2 else 1)
                setField(state, "value", on)
                val iconRes = if (on) iconActive ?: iconInactive else iconInactive ?: iconActive
                if (iconRes != null) {
                    val icon = resourceIcon(classLoader, iconRes)
                    if (icon != null) setField(state, "icon", icon)
                }
                return state
            }

            fun refresh() {
                try {
                    handleStateChanged.invoke(tileView, buildState(isOn()))
                    slowDownTileLottie(tileView)
                } catch (t: Throwable) {
                    logger.error("tile refresh failed", t)
                }
            }

            tileView.setOnClickListener {
                onToggle()
                refresh()
            }
            tileView.setOnLongClickListener {
                try {
                    context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                } catch (t: Throwable) {
                    logger.warn("open sound settings failed: ${t.message}")
                }
                true
            }
            refresh()
            TileUi(tileView, ::refresh)
        } catch (t: Throwable) {
            logger.error("Failed to build QS tile view", t)
            null
        }
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

    private fun setField(target: Any, name: String, value: Any?) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(name)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }

    private fun refreshAllTiles() {
        muteTile?.refresh?.invoke()
        dndTile?.refresh?.invoke()
        vibrateTile?.refresh?.invoke()
    }

    // Tile state logic, mirroring QMuteTile / QVibrateTile / DndTile

    private fun muteTileOn(): Boolean {
        val am = dialogContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        // Tile ON = muted (mirrors QMuteTile: active state when ringer is
        // silent), OFF = sound on.
        return amRingerModeInternal(am) == AudioManager.RINGER_MODE_SILENT
    }

    private fun toggleMute() {
        val am = dialogContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (amRingerModeInternal(am) == AudioManager.RINGER_MODE_SILENT) {
                amSetRingerModeInternal(am, AudioManager.RINGER_MODE_NORMAL)
            } else {
                amSetRingerModeInternal(am, AudioManager.RINGER_MODE_SILENT)
            }
        } catch (t: Throwable) {
            logger.warn("toggleMute failed: ${t.message}")
        }
    }

    private fun dndTileOn(): Boolean {
        val nm = dialogContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun toggleDnd() {
        val nm = dialogContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            val target = if (dndTileOn()) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            }
            nm.setInterruptionFilter(target)
        } catch (t: Throwable) {
            logger.warn("toggleDnd failed: ${t.message}")
        }
    }

    private fun vibrateTileOn(context: Context): Boolean {
        val cr = context.contentResolver
        val userId = currentUserId()
        return try {
            settingsIntForUser(cr, "vibrate_on", 1, userId) == 1 &&
                (settingsIntForUser(cr, "ring_vibration_intensity", 0, userId) == 2 ||
                    settingsIntForUser(cr, "notification_vibration_intensity", 0, userId) == 2)
        } catch (_: Throwable) {
            false
        }
    }

    private fun toggleVibrate(context: Context) {
        val cr = context.contentResolver
        val userId = currentUserId()
        try {
            if (vibrateTileOn(context)) {
                settingsPutIntForUser(cr, "notification_vibration_intensity", 0, userId)
                settingsPutIntForUser(cr, "ring_vibration_intensity", 0, userId)
            } else {
                settingsPutIntForUser(cr, "vibrate_on", 1, userId)
                settingsPutIntForUser(cr, "notification_vibration_intensity", 2, userId)
                settingsPutIntForUser(cr, "ring_vibration_intensity", 2, userId)
            }
        } catch (t: Throwable) {
            logger.warn("toggleVibrate failed: ${t.message}")
        }
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
    // Panel-scoped observers keeping tile states fresh
    // ------------------------------------------------------------------

    private fun registerPanelObservers(context: Context) {
        val cr = context.contentResolver
        val onChange = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                mainHandler.post { refreshAllTiles() }
            }
        }
        for (uri in listOf(
            Settings.Global.getUriFor("mode_ringer"),
            Settings.Global.getUriFor("zen_mode"),
            Settings.System.getUriFor("vibrate_on"),
            Settings.System.getUriFor("ring_vibration_intensity"),
            Settings.System.getUriFor("notification_vibration_intensity"),
            Settings.System.getUriFor("vibrate_when_ringing")
        )) {
            try {
                cr.registerContentObserver(uri, false, onChange)
                contentObservers.add(onChange)
            } catch (_: Throwable) {
            }
        }
    }

    private fun unregisterPanelObservers() {
        val context = dialogContext
        for (observer in contentObservers) {
            try {
                context?.contentResolver?.unregisterContentObserver(observer)
            } catch (_: Throwable) {
            }
        }
        contentObservers.clear()
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

    // ------------------------------------------------------------------
    // Hidden-API reflection helpers (compiled against the public SDK)
    // ------------------------------------------------------------------

    /** Hidden: ActivityManager.getCurrentUser() */
    private fun currentUserId(): Int {
        return try {
            val method = ActivityManager::class.java.getDeclaredMethod("getCurrentUser")
            method.isAccessible = true
            method.invoke(null) as Int
        } catch (_: Throwable) {
            0
        }
    }

    /** Hidden: Settings.System.getIntForUser(...) */
    private fun settingsIntForUser(cr: android.content.ContentResolver, key: String, def: Int, userId: Int): Int {
        val method = Settings.System::class.java.getDeclaredMethod(
            "getIntForUser",
            android.content.ContentResolver::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(null, cr, key, def, userId) as Int
    }

    /** Hidden: Settings.System.putIntForUser(...) */
    private fun settingsPutIntForUser(cr: android.content.ContentResolver, key: String, value: Int, userId: Int) {
        val method = Settings.System::class.java.getDeclaredMethod(
            "putIntForUser",
            android.content.ContentResolver::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(null, cr, key, value, userId)
    }

    /** Hidden: AudioManager.getRingerModeInternal() */
    private fun amRingerModeInternal(am: AudioManager?): Int {
        if (am == null) return AudioManager.RINGER_MODE_NORMAL
        return try {
            val method = am.javaClass.getDeclaredMethod("getRingerModeInternal")
            method.isAccessible = true
            method.invoke(am) as Int
        } catch (_: Throwable) {
            am.ringerMode
        }
    }

    /** Hidden: AudioManager.setRingerModeInternal(int) */
    private fun amSetRingerModeInternal(am: AudioManager, mode: Int) {
        try {
            val method = am.javaClass.getDeclaredMethod("setRingerModeInternal", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(am, mode)
        } catch (t: Throwable) {
            logger.warn("setRingerModeInternal failed: ${t.message}")
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
