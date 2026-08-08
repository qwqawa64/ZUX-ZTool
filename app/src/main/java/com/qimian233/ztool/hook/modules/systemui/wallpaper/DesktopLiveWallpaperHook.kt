package com.qimian233.ztool.hook.modules.systemui.wallpaper

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
 * 桌面动态壁纸 — 技术验证 Hook（MediaCodec Surface 直渲版）。
 *
 * 劫持 ImageWallpaper.CanvasEngine：
 * - Hook onSurfaceCreated → 获取 Engine 自己的 Surface
 * - Hook drawFrameOnCanvas → 阻止静态 bitmap 渲染
 * - 用 MediaCodec 直接解码视频到 Engine 的 Surface（零拷贝）
 * - 视频播放完毕后自动循环
 *
 * 视频文件路径：
 *   /sdcard/Download/ZTool/wallpaper_portrait.mp4  (竖屏)
 *   /sdcard/Download/ZTool/wallpaper_land.mp4       (横屏)
 *
 * getModuleName() 返回 PreferenceKeys.DESKTOP_LIVE_WALLPAPER.name，
 * 由前端开关控制启用。
 */
class DesktopLiveWallpaperHook : AppHookModule() {

    companion object {
        private val SYSTEMUI_PKG = ScopeKeys.SYSTEM_UI.packageName
        private const val ENGINE_CLASS =
            "com.android.systemui.wallpapers.ImageWallpaper\$CanvasEngine"
        private const val CUSTOM_VIDEO_DIR = "/Download/ZTool"
        private const val VIDEO_PORTRAIT = "wallpaper_portrait.mp4"
        private const val VIDEO_LAND = "wallpaper_land.mp4"
        private const val DECODE_TIMEOUT_US = 10_000L
    }

    // 每个 Engine 实例一份的播放状态
    private var codec: MediaCodec? = null
    private var extractor: MediaExtractor? = null
    private var decodeThread: Thread? = null
    @Volatile private var running = false
    private var engineSurface: Surface? = null
    private var reportedShown = false

    override fun getModuleName(): String = PreferenceKeys.DESKTOP_LIVE_WALLPAPER.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PKG)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PKG) return

        try {
            val engineClass = param.defaultClassLoader.loadClass(ENGINE_CLASS)

            // ① onSurfaceCreated → 获取 Surface，启动播放
            hookWithId(
                engineClass.getDeclaredMethod("onSurfaceCreated", SurfaceHolder::class.java),
                "dynwall_surface_created"
            ) { chain ->
                chain.proceed()
                onSurfaceReady(chain.thisObject)
            }

            // ② drawFrameOnCanvas → 播放中阻止静态图覆盖视频帧
            hookWithId(
                engineClass.getDeclaredMethod("drawFrameOnCanvas", android.graphics.Bitmap::class.java),
                "dynwall_draw_frame"
            ) {
                if (!running) it.proceed() else null
            }

            // ③ onSurfaceDestroyed → 清理
            hookWithId(
                engineClass.getDeclaredMethod("onSurfaceDestroyed", SurfaceHolder::class.java),
                "dynwall_surface_destroyed"
            ) { chain ->
                stopPlayback()
                chain.proceed()
            }

            logger.info("DesktopLiveWallpaper: hooks installed")
        } catch (t: Throwable) {
            logger.error("DesktopLiveWallpaper: failed to install hooks", t)
        }
    }

    // ── Surface 就绪 ──────────────────────────────────────────

    private fun onSurfaceReady(engine: Any) {
        stopPlayback()

        val holder = engine.javaClass
            .getDeclaredField("mSurfaceHolder").apply { isAccessible = true }
            .get(engine) as? SurfaceHolder ?: run {
                logger.error("DesktopLiveWallpaper: mSurfaceHolder is null")
                return
            }
        val surface = holder.surface ?: run {
            logger.error("DesktopLiveWallpaper: Surface is null")
            return
        }
        engineSurface = surface

        val videoPath = resolveVideoPath(engine)
        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            logger.warn("DesktopLiveWallpaper: video not found at $videoPath, fallback to static")
            return
        }

        startPlayback(engine, videoPath)
    }

    /**
     * 根据当前屏幕方向选择竖屏/横屏视频路径。
     * 优先使用对应方向文件，若不存在则回退到另一个方向。
     */
    private fun resolveVideoPath(engine: Any): String {
        val baseDir = Environment.getExternalStorageDirectory().path + CUSTOM_VIDEO_DIR
        val isLandscape = try {
            val displayContext = engine.javaClass.superclass
                ?.getDeclaredMethod("getDisplayContext")?.apply { isAccessible = true }
                ?.invoke(engine) as? android.content.Context
            displayContext?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
        } catch (_: Exception) { false }

        val (primary, fallback) = if (isLandscape)
            VIDEO_LAND to VIDEO_PORTRAIT
        else
            VIDEO_PORTRAIT to VIDEO_LAND

        val primaryPath = "$baseDir/$primary"
        return if (File(primaryPath).exists()) primaryPath else "$baseDir/$fallback"
    }

    // ── 播放控制 ──────────────────────────────────────────────

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
            codec.configure(format, engineSurface, null, 0)
            codec.start()

            this.extractor = extractor
            this.codec = codec
            running = true
            reportedShown = false

            decodeThread = Thread({
                decodeLoop(trackIndex, engine)
            }, "DesktopLiveWallpaper-Decode").also { it.start() }

            logger.info("DesktopLiveWallpaper: playback started ${w}x${h}")
        } catch (t: Throwable) {
            logger.error("DesktopLiveWallpaper: startPlayback failed", t)
            releaseCodec()
        }
    }

    private fun stopPlayback() {
        running = false
        decodeThread?.interrupt()
        decodeThread?.join(500)
        decodeThread = null
        releaseCodec()
        engineSurface = null
    }

    private fun releaseCodec() {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        codec = null
        try { extractor?.release() } catch (_: Exception) {}
        extractor = null
    }

    // ── 解码循环（在专用线程上运行） ──────────────────────────

    private fun decodeLoop(trackIndex: Int, engine: Any) {
        val extractor = extractor ?: return
        val codec = codec ?: return
        val bufInfo = MediaCodec.BufferInfo()
        var inputEos = false

        // 帧间隔控制：按视频时间戳同步播放速度
        var lastPtsUs = -1L           // 上一帧的 presentationTimeUs（-1 表示无上一帧）
        var lastRenderNanos = 0L      // 上一帧渲染完成的 System.nanoTime()

        try {
            while (running && !Thread.interrupted()) {
                // 喂数据
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

                // 取输出 → 渲染到 Surface
                val outIdx = codec.dequeueOutputBuffer(bufInfo, DECODE_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val render = bufInfo.size > 0 &&
                            (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0

                        if (render) {
                            // 按帧时间戳控制播放速率
                            val ptsUs = bufInfo.presentationTimeUs
                            if (lastPtsUs >= 0) {
                                val frameGapUs = ptsUs - lastPtsUs
                                if (frameGapUs > 0) {
                                    val nowNanos = System.nanoTime()
                                    val elapsedNanos = nowNanos - lastRenderNanos
                                    val targetNanos = frameGapUs * 1000L // μs → ns
                                    val sleepNanos = targetNanos - elapsedNanos
                                    if (sleepNanos > 500_000L) { // >0.5ms 才睡，避免忙等
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
                            lastPtsUs = -1L // 循环后重置，第一帧不等待
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                }
            }
        } catch (e: InterruptedException) {
            // 正常的停止信号
        } catch (t: Throwable) {
            if (running) logger.error("DesktopLiveWallpaper: decode error", t)
        }
    }

    // ── 辅助 ──────────────────────────────────────────────────

    private fun reportEngineShown(engine: Any) {
        try {
            engine.javaClass.superclass
                ?.getDeclaredMethod("reportEngineShown")
                ?.apply { isAccessible = true }
                ?.invoke(engine)
        } catch (_: Exception) { /* non-critical */ }
    }
}
