package com.qimian233.ztool.hook.modules.systemui.wallpaper

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.view.Surface
import android.view.SurfaceHolder
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File

/**
 * Desktop live wallpaper - technical validation hook (MediaCodec direct Surface render).
 *
 * Hijacks ImageWallpaper.CanvasEngine:
 * - Hook onSurfaceCreated -> get the Engine's own Surface
 * - Hook drawFrameOnCanvas -> prevent static bitmap rendering
 * - Decode video directly into the Engine's Surface with MediaCodec (zero-copy)
 * - Loop the video automatically when playback finishes
 * - Orientation changes go through CanvasEngine's DisplayListener.onDisplayChanged
 *   callback (SystemUI wallpaper's own notification mechanism); additionally hooks
 *   onConfigurationChanged / onSurfaceChanged as fallback: reselect the video by the
 *   new orientation; if no video exists for that orientation, stop playback and
 *   actively trigger a SystemUI redraw to show the static wallpaper again
 *   (strict matching, no cross-orientation fallback)
 *
 * Video file paths:
 *   /sdcard/Download/ZTool/wallpaper_portrait.mp4  (portrait)
 *   /sdcard/Download/ZTool/wallpaper_land.mp4      (landscape)
 *
 * getModuleName() returns PreferenceKeys.DESKTOP_LIVE_WALLPAPER.name,
 * enabled via the frontend switch.
 */
@SuppressLint("PrivateApi")
class DesktopLiveWallpaperHook : AppHookModule() {

    companion object {
        private val SYSTEMUI_PKG = ScopeKeys.SYSTEM_UI.packageName
        private const val ENGINE_CLASS =
            $$"com.android.systemui.wallpapers.ImageWallpaper$CanvasEngine"
        private const val CUSTOM_VIDEO_DIR = "/Download/ZTool"
        private const val VIDEO_PORTRAIT = "wallpaper_portrait.mp4"
        private const val VIDEO_LAND = "wallpaper_land.mp4"
        private const val DECODE_TIMEOUT_US = 10_000L
        // Video display mode preference values (one-to-one with frontend dropdown options)
        private const val SCALE_MODE_FIT = "fit"
        private const val SCALE_MODE_COVER = "cover"
    }

    // Playback state, one per Engine instance
    private var codec: MediaCodec? = null
    private var extractor: MediaExtractor? = null
    private var decodeThread: Thread? = null
    @Volatile private var running = false
    private var engineSurface: Surface? = null
    private var reportedShown = false
    // Currently effective screen orientation (used to decide whether to reselect the video on rotation)
    private var currentOrientation = Configuration.ORIENTATION_UNDEFINED

    override fun getModuleName(): String = PreferenceKeys.DESKTOP_LIVE_WALLPAPER.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PKG)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PKG) return

        try {
            val engineClass = param.defaultClassLoader.loadClass(ENGINE_CLASS)

            // ① onSurfaceCreated -> get Surface, start playback
            hookWithId(
                engineClass.getDeclaredMethod("onSurfaceCreated", SurfaceHolder::class.java),
                "dynwall_surface_created"
            ) { chain ->
                chain.proceed()
                onSurfaceReady(chain.thisObject)
            }

            // ② drawFrameOnCanvas -> while playing, prevent the static image from covering video frames
            hookWithId(
                engineClass.getDeclaredMethod("drawFrameOnCanvas", android.graphics.Bitmap::class.java),
                "dynwall_draw_frame"
            ) {
                if (!running) it.proceed() else null
            }

            // ③ onSurfaceDestroyed -> cleanup
            hookWithId(
                engineClass.getDeclaredMethod("onSurfaceDestroyed", SurfaceHolder::class.java),
                "dynwall_surface_destroyed"
            ) { chain ->
                stopPlayback()
                chain.proceed()
            }

            // ④ onDisplayChanged -> orientation/display change (SystemUI wallpaper's own
            // notification path; always triggered on rotation)
            try {
                hookWithId(
                    engineClass.getDeclaredMethod(
                        "onDisplayChanged", Int::class.javaPrimitiveType
                    ),
                    "dynwall_display_changed"
                ) { chain ->
                    chain.proceed()
                    logger.debug("DesktopLiveWallpaper: onDisplayChanged invoked")
                    refreshOrientationAndPlayback(chain.thisObject)
                }
            } catch (t: Throwable) {
                logger.error("DesktopLiveWallpaper: failed to hook onDisplayChanged", t)
            }

            // ⑤ onConfigurationChanged -> framework dispatch fallback (some ROM paths)
            // Use getMethod to find the inherited public method, compatible with CanvasEngine not overriding it
            try {
                hookWithId(
                    engineClass.getMethod("onConfigurationChanged", Configuration::class.java),
                    "dynwall_config_changed"
                ) { chain ->
                    chain.proceed()
                    refreshOrientationAndPlayback(chain.thisObject)
                }
            } catch (t: Throwable) {
                logger.error("DesktopLiveWallpaper: failed to hook onConfigurationChanged", t)
            }

            // ⑥ onSurfaceChanged -> Surface size change fallback (rotation usually swaps width/height)
            try {
                hookWithId(
                    engineClass.getDeclaredMethod(
                        "onSurfaceChanged",
                        SurfaceHolder::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ),
                    "dynwall_surface_changed"
                ) { chain ->
                    chain.proceed()
                    refreshOrientationAndPlayback(chain.thisObject)
                }
            } catch (t: Throwable) {
                logger.error("DesktopLiveWallpaper: failed to hook onSurfaceChanged", t)
            }

            logger.info("DesktopLiveWallpaper: hooks installed")
        } catch (t: Throwable) {
            logger.error("DesktopLiveWallpaper: failed to install hooks", t)
        }
    }

    private fun onSurfaceReady(engine: Any) {
        stopPlayback()

        val surface = resolveSurface(engine) ?: run {
            logger.error("DesktopLiveWallpaper: mSurfaceHolder missing or Surface invalid")
            return
        }
        engineSurface = surface

        currentOrientation = detectOrientation(engine)
        val videoPath = videoPathFor(currentOrientation)
        if (videoPath == null) {
            logger.warn(
                "DesktopLiveWallpaper: no video for orientation $currentOrientation, keep static"
            )
            return
        }

        startPlayback(engine, videoPath)
    }

    /**
     * Resolve the currently valid Surface from the engine in real time (avoid stale cache).
     * Returns null when mSurfaceHolder is missing or the Surface is invalid (isValid == false).
     */
    private fun resolveSurface(engine: Any): Surface? {
        return try {
            val holder = engine.javaClass
                .getDeclaredField("mSurfaceHolder").apply { isAccessible = true }
                .get(engine) as? SurfaceHolder ?: return null
            holder.surface?.takeIf { it.isValid }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Detect the display orientation the Engine is currently bound to.
     * Returns ORIENTATION_UNDEFINED on reflection failure; the caller treats it as portrait.
     */
    private fun detectOrientation(engine: Any): Int {
        return try {
            val displayContext = engine.javaClass.superclass
                ?.getDeclaredMethod("getDisplayContext")?.apply { isAccessible = true }
                ?.invoke(engine) as? android.content.Context
            displayContext?.resources?.configuration?.orientation
                ?: Configuration.ORIENTATION_UNDEFINED
        } catch (_: Exception) {
            Configuration.ORIENTATION_UNDEFINED
        }
    }

    /**
     * Strict matching: only returns the video path for the current orientation.
     * Returns null when the video for that orientation does not exist (no cross-orientation
     * fallback); the caller should keep the static wallpaper.
     */
    private fun videoPathFor(orientation: Int): String? {
        val baseDir = Environment.getExternalStorageDirectory().path + CUSTOM_VIDEO_DIR
        val fileName = when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> VIDEO_LAND
            else -> VIDEO_PORTRAIT // undefined/unknown orientations treated as portrait
        }
        val path = "$baseDir/$fileName"
        return if (File(path).exists()) path else null
    }

    private fun startPlayback(engine: Any, videoPath: String) {
        try {
            val extractor = MediaExtractor().also { it.setDataSource(videoPath) }

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: run {
                logger.error("DesktopLiveWallpaper: no video track")
                extractor.release()
                return
            }

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val w = format.getInteger(MediaFormat.KEY_WIDTH)
            val h = format.getInteger(MediaFormat.KEY_HEIGHT)
            extractor.selectTrack(trackIndex)

            val codec = MediaCodec.createDecoderByType(mime)
            // The Surface may already be invalid; re-validate before configure to avoid a native_configure exception
            val surface = engineSurface
            if (surface == null || !surface.isValid) {
                logger.error("DesktopLiveWallpaper: invalid surface, abort playback")
                releaseCodec()
                return
            }
            codec.configure(format, surface, null, 0)
            applyVideoScalingMode(codec)
            codec.start()

            this.extractor = extractor
            this.codec = codec
            running = true
            reportedShown = false

            decodeThread = Thread({
                decodeLoop(engine)
            }, "DesktopLiveWallpaper-Decode").also { it.start() }

            logger.info("DesktopLiveWallpaper: playback started ${w}x${h}")
        } catch (t: Throwable) {
            logger.error("DesktopLiveWallpaper: startPlayback failed", t)
            releaseCodec()
        }
    }

    /**
     * Set the video display mode on the Surface according to user preference
     * (official MediaCodec surface output scaling modes):
     * - fit   -> VIDEO_SCALING_MODE_SCALE_TO_FIT (preserve aspect ratio, fully shown, may have black bars)
     * - cover -> VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING (proportional crop filling the screen, no black bars, no distortion)
     *
     * The preference value is written to xposed_module_config by the frontend dropdown.
     * Unknown values are not applied (keep system default behavior); failures are only
     * logged and do not affect playback.
     */
    private fun applyVideoScalingMode(codec: MediaCodec) {
        try {
            val mode = xposed.getRemotePreferences("xposed_module_config")
                .getString(
                    PreferenceKeys.DESKTOP_LIVE_WALLPAPER_SCALE_MODE.name,
                    PreferenceKeys.DESKTOP_LIVE_WALLPAPER_SCALE_MODE.default
                )
            val scalingMode = when (mode) {
                SCALE_MODE_COVER -> MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                SCALE_MODE_FIT -> MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT
                else -> {
                    logger.warn("DesktopLiveWallpaper: unknown scale mode '$mode', keep default")
                    return
                }
            }
            codec.setVideoScalingMode(scalingMode)
            logger.debug("DesktopLiveWallpaper: video scaling mode = $mode ($scalingMode)")
        } catch (t: Throwable) {
            logger.error("DesktopLiveWallpaper: failed to set video scaling mode", t)
        }
    }

    /**
     * Stop playback. By default also clears engineSurface;
     * for scenarios where the Surface is still valid (e.g. orientation switch), pass
     * clearSurface = false to keep the Surface for reuse.
     */
    private fun stopPlayback(clearSurface: Boolean = true) {
        running = false
        decodeThread?.interrupt()
        decodeThread?.join(500)
        decodeThread = null
        releaseCodec()
        if (clearSurface) engineSurface = null
    }

    private fun releaseCodec() {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        codec = null
        try { extractor?.release() } catch (_: Exception) {}
        extractor = null
    }

    private fun decodeLoop(engine: Any) {
        val extractor = extractor ?: return
        val codec = codec ?: return
        val bufInfo = MediaCodec.BufferInfo()
        var inputEos = false

        // Frame interval control: sync playback speed to the video timestamps
        var lastPtsUs = -1L           // previous frame's presentationTimeUs (-1 means no previous frame)
        var lastRenderNanos = 0L      // System.nanoTime() when the previous frame finished rendering

        try {
            while (running && !Thread.interrupted()) {
                // Feed data
                if (!inputEos) {
                    val inIdx = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Get output -> render to Surface
                val outIdx = codec.dequeueOutputBuffer(bufInfo, DECODE_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val render = bufInfo.size > 0 &&
                            (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0

                        if (render) {
                            // Control playback rate by frame timestamps
                            val ptsUs = bufInfo.presentationTimeUs
                            if (lastPtsUs >= 0) {
                                val frameGapUs = ptsUs - lastPtsUs
                                if (frameGapUs > 0) {
                                    val nowNanos = System.nanoTime()
                                    val elapsedNanos = nowNanos - lastRenderNanos
                                    val targetNanos = frameGapUs * 1000L // μs → ns
                                    val sleepNanos = targetNanos - elapsedNanos
                                    if (sleepNanos > 500_000L) { // only sleep if >0.5ms, avoid busy-waiting
                                        Thread.sleep(
                                            sleepNanos / 1_000_000L,
                                            (sleepNanos % 1_000_000L).toInt()
                                        )
                                    }
                                }
                            }
                        }

                        codec.releaseOutputBuffer(outIdx, render)

                        if (render) {
                            lastPtsUs = bufInfo.presentationTimeUs
                            lastRenderNanos = System.nanoTime()
                        }

                        if (!reportedShown && render) {
                            reportedShown = true
                            reportEngineShown(engine)
                        }

                        if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            logger.debug("DesktopLiveWallpaper: looping")
                            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            codec.flush()
                            inputEos = false
                            lastPtsUs = -1L // reset after looping so the first frame does not wait
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                }
            }
        } catch (_: InterruptedException) {
            // Normal stop signal
        } catch (t: Throwable) {
            if (running) logger.error("DesktopLiveWallpaper: decode error", t)
        }
    }

    /**
     * Unified entry point for orientation/display changes (shared by onDisplayChanged /
     * onConfigurationChanged / onSurfaceChanged): re-detect the orientation and select
     * the video by the new one when it differs from the currently effective orientation.
     * - Video exists for the new orientation -> stop old playback and switch (Surface reused, not recreated)
     * - No video for the new orientation -> stop playback, fall back to static wallpaper (drawFrameOnCanvas resumes proceed)
     */
    private fun refreshOrientationAndPlayback(engine: Any) {
        val orientation = detectOrientation(engine)
        if (orientation == currentOrientation) return
        currentOrientation = orientation
        // Surface not yet ready (engine just created); wait for onSurfaceCreated to resolve it
        if (engineSurface == null) return

        val videoPath = videoPathFor(orientation)
        if (videoPath == null) {
            logger.info(
                "DesktopLiveWallpaper: no video for orientation $orientation, fallback to static"
            )
            stopPlayback(clearSurface = false)
            // Clear the residual last video frame on the Surface and show the static wallpaper again
            redrawStaticWallpaper(engine)
            return
        }

        // The Surface may have become invalid with the orientation change (onSurfaceDestroyed
        // not yet called); resolve it in real time again; skip this switch if invalid and
        // wait for onSurfaceCreated / the next callback
        engineSurface = resolveSurface(engine)
        if (engineSurface == null) {
            logger.warn(
                "DesktopLiveWallpaper: surface unavailable after rotation, skip switching video"
            )
            return
        }

        logger.info(
            "DesktopLiveWallpaper: orientation changed to $orientation, switching video"
        )
        stopPlayback(clearSurface = false)
        startPlayback(engine, videoPath)
    }

    /**
     * When falling back to the static wallpaper, actively trigger a SystemUI redraw
     * to clear the residual last video frame on the Surface.
     *
     * CanvasEngine's redraw entry (onSurfaceRedrawNeeded -> mLongExecutor) has an
     * `if (!mDrawn)` check; once the static wallpaper has been drawn, mDrawn=true and
     * it is skipped. Therefore reset mDrawn=false first, then call drawFrameInternal()
     * directly under mLock synchronization (it draws the static bitmap via
     * drawFrameOnCanvas internally).
     */
    private fun redrawStaticWallpaper(engine: Any) {
        try {
            val engineClass = engine.javaClass
            engineClass.getDeclaredField("mDrawn").apply { isAccessible = true }
                .setBoolean(engine, false)
            val lock = engineClass.getDeclaredField("mLock").apply { isAccessible = true }
                .get(engine) ?: return
            val drawFrameInternal = engineClass.getDeclaredMethod("drawFrameInternal")
                .apply { isAccessible = true }
            synchronized(lock) {
                drawFrameInternal.invoke(engine)
            }
            logger.debug("DesktopLiveWallpaper: static wallpaper redrawn")
        } catch (t: Throwable) {
            logger.error("DesktopLiveWallpaper: redrawStaticWallpaper failed", t)
        }
    }

    private fun reportEngineShown(engine: Any) {
        try {
            engine.javaClass.superclass
                ?.getDeclaredMethod("reportEngineShown")
                ?.apply { isAccessible = true }
                ?.invoke(engine)
        } catch (_: Exception) { /* non-critical */ }
    }
}
