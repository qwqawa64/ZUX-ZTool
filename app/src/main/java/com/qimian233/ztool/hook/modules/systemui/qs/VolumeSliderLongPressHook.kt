package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
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
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class VolumeSliderLongPressHook : AppHookModule() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var panelShowing = false
    private var currentDialog: Dialog? = null
    private var systemUiClassLoader: ClassLoader? = null

    // Rebuilt when the app list callback fires while the panel is open.
    private var appSection: LinearLayout? = null
    private var dialogContext: Context? = null
    private var contentObservers = mutableListOf<ContentObserver>()

    override fun getModuleName(): String = PreferenceKeys.VOLUME_LONG_PRESS_PANEL.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        systemUiClassLoader = param.defaultClassLoader
        hook = this
        hookAppListCallbackSlot()
        logger.info(
            "VolumeSliderLongPressHook installed, classLoader=" +
                (systemUiClassLoader?.javaClass?.name ?: "null")
        )
    }

    // ------------------------------------------------------------------
    // Entry point from the shared slider long-press detector
    // ------------------------------------------------------------------

    companion object {
        @Volatile
        private var hook: VolumeSliderLongPressHook? = null

        /** Last non-ztool callback that occupied the AudioSystem slot. */
        @Volatile
        private var nativeAppListCallback: Any? = null

        @Volatile
        private var ourAppListProxy: Any? = null

        @Volatile
        private var latestPackages: List<Any> = emptyList()

        @Volatile
        private var audioSystemSetter: Method? = null

        private const val SYSTEM_UI_DIALOG_CLASS =
            "com.android.systemui.statusbar.phone.SystemUIDialog"
        private const val CUSTOMIZE_TILE_VIEW_CLASS =
            "com.android.systemui.qs.customize.CustomizeTileView"
        private const val QS_TILE_STATE_CLASS =
            "com.android.systemui.plugins.qs.QSTile\$BooleanState"
        private const val RESOURCE_ICON_CLASS =
            "com.android.systemui.qs.tileimpl.QSTileImpl\$ResourceIcon"
        private const val APP_VOLUME_UTILS_CLASS =
            "com.android.systemui.volume.appvolume.Utils"
        private const val APP_SECTION_TAG = "ztool_volume_panel_app_section"
        private const val APP_VOLUME_SETTINGS_KEY = "zui_app_volume"
        private const val MAX_APP_ROWS = 3

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
            buildAndShowPanel(view.context, classLoader)
        } catch (t: Throwable) {
            logger.error("Failed to show volume detail panel", t)
        }
    }

    // ------------------------------------------------------------------
    // AudioSystem app-list callback slot management
    // ------------------------------------------------------------------

    /**
     * Records whoever owns the single AudioSystem app-list callback slot so we
     * can borrow it while our panel is open and give it back afterwards.
     */
    private fun hookAppListCallbackSlot() {
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val callbackClass = Class.forName("android.media.AudioSystem\$AudioAppListCallback")
            val setter = audioSystemClass.getDeclaredMethod(
                "setAudioAppListCallback", callbackClass
            )
            setter.isAccessible = true
            audioSystemSetter = setter
            hookWithId(setter, "audio_app_list_cb_slot") { chain ->
                val arg = chain.args.getOrNull(0)
                if (arg != null && arg !== ourAppListProxy) {
                    nativeAppListCallback = arg
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.warn("AudioSystem app-list callback slot hook unavailable: ${t.message}")
        }
    }

    private fun ensureAppListProxyRegistered(classLoader: ClassLoader) {
        val setter = audioSystemSetter ?: return
        if (ourAppListProxy != null) {
            setter.invoke(null, ourAppListProxy)
            return
        }
        val callbackClass = Class.forName("android.media.AudioSystem\$AudioAppListCallback")
        val handler = InvocationHandler { _, method, args ->
            if ("onAudioAppListChanged" == method.name && args != null && args[0] != null) {
                val payload = args[0]
                val entries = mutableListOf<Any>()
                if (payload is Collection<*>) {
                    for (e in payload) if (e != null) entries.add(e)
                } else if (payload.javaClass.isArray) {
                    val length = java.lang.reflect.Array.getLength(payload)
                    for (i in 0 until length) {
                        val e = java.lang.reflect.Array.get(payload, i)
                        if (e != null) entries.add(e)
                    }
                }
                latestPackages = entries
                mainHandler.post { refreshAppSection() }
            }
            null
        }
        ourAppListProxy = Proxy.newProxyInstance(
            callbackClass.classLoader, arrayOf(callbackClass), handler
        )
        setter.invoke(null, ourAppListProxy)
    }

    private fun restoreNativeAppListCallback() {
        val setter = audioSystemSetter ?: return
        try {
            val native = nativeAppListCallback
            setter.invoke(null, native)
            logger.debug("volume panel: native app-list callback restored=${native != null}")
        } catch (t: Throwable) {
            logger.warn("Failed to restore native app-list callback: ${t.message}")
        }
    }

    // ------------------------------------------------------------------
    // Panel construction
    // ------------------------------------------------------------------

    private fun buildAndShowPanel(context: Context, classLoader: ClassLoader) {
        val themeRes = resolveStyleId(
            classLoader,
            "Theme_SystemUI_Dialog_GlobalActionsLite",
            "Theme_SystemUI_Dialog"
        ) ?: 0
        logger.debug("volume panel: themeRes=$themeRes")
        val dialogClass = classLoader.loadClass(SYSTEM_UI_DIALOG_CLASS)
        val ctor = dialogClass.getDeclaredConstructor(
            Context::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
        )
        ctor.isAccessible = true
        val dialog = ctor.newInstance(context, themeRes, true) as Dialog
        logger.debug("volume panel: SystemUIDialog created")

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16))
            background = buildPanelBackground(context)
        }
        dialog.setContentView(root)
        logger.debug("volume panel: content view set")

        dialogContext = context
        appSection = null

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        root.addView(
            buildStreamSliderRow(context, am, AudioManager.STREAM_MUSIC, resolveDrawableId(
                classLoader, "ic_volume_media_zui", "ic_volume_media"
            ))
        )
        if (!AudioSystemHelperShim.isSingleVolume(context)) {
            root.addView(
                buildStreamSliderRow(context, am, AudioManager.STREAM_RING, resolveDrawableId(
                    classLoader, "ic_volume_ringer_zui", "ic_volume_ringer"
                ), spacingTopDp = 12)
            )
        }
        root.addView(buildAppSection(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 12) })
        root.addView(buildTileRow(context, classLoader), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 12) })
        logger.debug(
            "volume panel: content built, tiles(mute/dnd/vibrate)=" +
                "${muteTile != null}/${dndTile != null}/${vibrateTile != null}"
        )

        dialog.setOnDismissListener {
            logger.debug("volume panel: dismissed")
            panelShowing = false
            currentDialog = null
            unregisterPanelObservers()
            restoreNativeAppListCallback()
            try {
                context.setTheme(resolveStyleId(classLoader, "Theme_SystemUI") ?: 0)
            } catch (_: Throwable) {
            }
        }
        registerPanelObservers(context)

        panelShowing = true
        currentDialog = dialog
        ensureAppListProxyRegistered(classLoader)
        mainHandler.post { refreshAppSection() }
        dialog.show()
        logger.debug("volume panel: shown")
    }

    private fun buildPanelBackground(context: Context): GradientDrawable {
        val color = obtainThemeColor(context, android.R.attr.colorBackgroundFloating, Color.WHITE)
        val radius = resolveDimenPx(context, "qs_corner_radius", dp(context, 28)).toFloat()
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    // ------------------------------------------------------------------
    // Stream sliders (media / ring)
    // ------------------------------------------------------------------

    private fun buildStreamSliderRow(
        context: Context,
        am: AudioManager,
        stream: Int,
        iconRes: Int?,
        spacingTopDp: Int = 0
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (iconRes != null) {
            row.addView(ImageView(context).apply {
                setImageResource(iconRes)
                val size = dp(context, 24)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp(context, 12)
                    topMargin = dp(context, spacingTopDp)
                }
            })
        }
        val percentView = TextView(context).apply {
            textSize = 13f
            setTextColor(obtainThemeColor(context, android.R.attr.textColorPrimary, Color.GRAY))
            text = formatPercent(am.getStreamVolume(stream), am.getStreamMaxVolume(stream))
        }
        val seekBar = SeekBar(context).apply {
            max = am.getStreamMaxVolume(stream)
            progress = am.getStreamVolume(stream)
            thumb = null
            progressDrawable = resolveSliderDrawable(context)
            val barHeight = resolveDimenPx(context, "brightness_bar_height", dp(context, 18))
            minHeight = barHeight
            maxHeight = barHeight
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    try {
                        am.setStreamVolume(stream, progress, 0)
                    } catch (t: Throwable) {
                        logger.warn("setStreamVolume($stream) failed: ${t.message}")
                    }
                    percentView.text = formatPercent(progress, bar.max)
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        row.addView(seekBar)
        row.addView(percentView.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(context, 10) }
        })
        return row
    }

    /**
     * Same progress drawable the stock ToggleSliderView uses for its sliders
     * (brightness_progress_selector), giving the panel the control-center look.
     */
    private fun resolveSliderDrawable(context: Context): android.graphics.drawable.Drawable? {
        val id = resolveDrawableId(systemUiClassLoader, "brightness_progress_selector")
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
            orientation = LinearLayout.VERTICAL
            tag = APP_SECTION_TAG
        }
    }

    private fun refreshAppSection() {
        val section = appSection ?: return
        val context = dialogContext ?: return
        val classLoader = systemUiClassLoader ?: return
        if (!panelShowing) return
        try {
            section.removeAllViews()
            val entries = collectAppVolumeEntries(context, classLoader)
            if (entries.isEmpty()) {
                section.visibility = View.GONE
                return
            }
            section.visibility = View.VISIBLE
            val pm = context.packageManager
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            for (entry in entries) {
                section.addView(buildAppSliderRow(context, pm, am, entry, classLoader))
            }
            logger.debug("volume panel: app section rows=${entries.size}")
        } catch (t: Throwable) {
            logger.error("Failed to refresh app volume section", t)
        }
    }

    private fun collectAppVolumeEntries(
        context: Context,
        classLoader: ClassLoader
    ): List<AppVolumeEntry> {
        val whitelistClass = try {
            classLoader.loadClass(APP_VOLUME_UTILS_CLASS)
        } catch (_: Throwable) {
            null
        }
        val isWhiteListApp = whitelistClass?.declaredMethods?.firstOrNull {
            it.name == "isWhiteListApp"
        }
        val persisted = loadPersistedAppVolumes(context)
        val entries = mutableListOf<AppVolumeEntry>()
        val seenUids = mutableSetOf<Int>()
        for (pkg in latestPackages) {
            if (entries.size >= MAX_APP_ROWS) break
            try {
                val name = readStringField(pkg, "packageName") ?: continue
                val uid = readIntField(pkg, "uid")
                    ?: resolveUidFromPid(context, readIntField(pkg, "pid") ?: -1)
                    ?: continue
                if (!seenUids.add(uid)) continue
                if (isWhiteListApp != null) {
                    val pass = isWhiteListApp.invoke(null, context, name) as? Boolean ?: true
                    if (!pass) continue
                }
                val appInfo = try {
                    context.packageManager.getApplicationInfo(name, 0)
                } catch (_: Exception) {
                    null
                }
                val label = appInfo?.loadLabel(context.packageManager)?.toString() ?: name
                val pct = persisted[uid]?.let { (it * 100).roundToInt().coerceIn(0, 100) } ?: 100
                entries.add(AppVolumeEntry(uid, label, pct))
            } catch (t: Throwable) {
                logger.debug("volume panel: skip package entry: ${t.message}")
            }
        }
        return entries
    }

    private fun buildAppSliderRow(
        context: Context,
        pm: android.content.pm.PackageManager,
        am: AudioManager,
        entry: AppVolumeEntry,
        classLoader: ClassLoader
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(context).apply {
            val appInfo = appIconInfo(context, entry.uid)
            if (appInfo != null) {
                setImageDrawable(appInfo.loadIcon(pm))
            } else {
                setImageResource(resolveDrawableId(classLoader, "ic_volume_media_zui") ?: 0)
            }
            val size = dp(context, 24)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(context, 12)
                topMargin = dp(context, 8)
            }
        })
        val percentView = TextView(context).apply {
            textSize = 13f
            setTextColor(obtainThemeColor(context, android.R.attr.textColorPrimary, Color.GRAY))
            text = "${entry.initialPercent}%"
        }
        val seekBar = SeekBar(context).apply {
            max = 100
            progress = entry.initialPercent
            thumb = null
            progressDrawable = resolveSliderDrawable(context)
            val barHeight = resolveDimenPx(context, "brightness_bar_height", dp(context, 18))
            minHeight = barHeight
            maxHeight = barHeight
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    percentView.text = "$progress%"
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}

                override fun onStopTrackingTouch(bar: SeekBar) {
                    commitAppVolume(am, context, entry.uid, bar.progress)
                }
            })
        }
        row.addView(seekBar)
        row.addView(percentView.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(context, 10) }
        })
        return row
    }

    /** Same call path as RelativeVolumeHelper.setRelativeVolumeInternal. */
    private fun commitAppVolume(am: AudioManager, context: Context, uid: Int, percent: Int) {
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
            val finalValue = if (serialized.isEmpty()) value else serialized
            Settings.System.putString(context.contentResolver, APP_VOLUME_SETTINGS_KEY, finalValue)
        } catch (t: Throwable) {
            logger.warn("persist app volume failed: ${t.message}")
        }
    }

    private fun resolveUidFromPid(context: Context, pid: Int): Int? {
        if (pid < 0) return null
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.uid
        } catch (_: Throwable) {
            null
        }
    }

    private fun readStringField(obj: Any, name: String): String? {
        return try {
            val f = obj.javaClass.getField(name)
            f.isAccessible = true
            f.get(obj) as? String
        } catch (_: Throwable) {
            null
        }
    }

    private fun readIntField(obj: Any, name: String): Int? {
        return try {
            val f = obj.javaClass.getField(name)
            f.isAccessible = true
            (f.get(obj) as? Int)
        } catch (_: Throwable) {
            null
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
            iconActiveNames = arrayOf(
                "controlcenter_1_btn_zenmode_on", "controlcenter_1_btn_zenmode_off"
            ),
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
                ?: throw NoSuchMethodException("handleStateChanged(QSTile\$State) not found")
            handleStateChanged.isAccessible = true

            val stateClass = classLoader.loadClass(QS_TILE_STATE_CLASS)
            val iconActive = iconActiveNames.firstNotNullOfOrNull { resolveDrawableId(classLoader, it) }
            val iconInactive =
                iconInactiveNames.firstNotNullOfOrNull { resolveDrawableId(classLoader, it) }
            val label = labelResNames.firstNotNullOfOrNull { resolveStringId(classLoader, it) }
                ?.let { context.getString(it) } ?: ""

            fun buildState(on: Boolean): Any {
                val state = stateClass.newInstance()
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
            classLoader.loadClass("com.android.systemui.plugins.qs.QSTile\$State")
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
        return amRingerModeInternal(am) != AudioManager.RINGER_MODE_SILENT
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
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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

    private fun resolveStyleId(classLoader: ClassLoader?, vararg names: String): Int? {
        return resolveResourceId(classLoader, "style", *names)
    }

    private fun resolveDrawableId(classLoader: ClassLoader?, vararg names: String): Int? {
        return resolveResourceId(classLoader, "drawable", *names)
    }

    private fun resolveStringId(classLoader: ClassLoader?, vararg names: String): Int? {
        return resolveResourceId(classLoader, "string", *names)
    }

    private fun resolveResourceId(classLoader: ClassLoader?, type: String, vararg names: String): Int? {
        val cl = classLoader ?: return null
        for (rClass in arrayOf("com.android.wm.shell.R", "com.android.systemui.R")) {
            try {
                val inner = cl.loadClass("$rClass\$" + type.replaceFirstChar { it.uppercaseChar() })
                for (name in names) {
                    try {
                        val field = inner.getDeclaredField(name)
                        return field.getInt(null)
                    } catch (_: NoSuchFieldException) {
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun resolveDimenPx(context: Context, name: String, fallbackPx: Int): Int {
        val id = resolveResourceId(systemUiClassLoader, "dimen", name)
        return if (id != null) context.resources.getDimensionPixelSize(id) else fallbackPx
    }

    private fun obtainThemeColor(context: Context, attr: Int, fallback: Int): Int {
        return try {
            val value = TypedValue()
            if (context.theme.resolveAttribute(attr, value, true)) value.data else fallback
        } catch (_: Throwable) {
            fallback
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
                val clazz = Class.forName("android.media.AudioSystem")
                val method = clazz.getDeclaredMethod("isSingleVolume", Context::class.java)
                method.isAccessible = true
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
