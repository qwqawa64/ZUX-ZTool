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
 * - 方向变化走 CanvasEngine 的 DisplayListener.onDisplayChanged 回调
 *   （SystemUI 壁纸自身的通知机制），另挂 onConfigurationChanged /
 *   onSurfaceChanged 兜底：按新方向重新选视频，
 *   该方向无对应视频时停止播放并主动触发 SystemUI 重绘，
 *   重新显示静态壁纸（严格匹配，不跨方向回退）
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
        // 视频显示模式偏好值（与前端下拉选项一一对应）
        private const val SCALE_MODE_FIT = "fit"
        private const val SCALE_MODE_COVER = "cover"
    }

    // 每个 Engine 实例一份的播放状态
    private var codec: MediaCodec? = null
    private var extractor: MediaExtractor? = null
    private var decodeThread: Thread? = null
    @Volatile private var running = false
    private var engineSurface: Surface? = null
    private var reportedShown = false
    // 当前生效的屏幕方向（用于方向切换时判断是否需要重新选视频）
    private var currentOrientation = Configuration.ORIENTATION_UNDEFINED

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

            // ④ onDisplayChanged → 方向/显示变化（SystemUI 壁纸自身的通知路径，旋转必触发）
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

            // ⑤ onConfigurationChanged → framework 分发兜底（部分 ROM 路径）
            // 用 getMethod 找继承的 public 方法，兼容 CanvasEngine 未覆写的情况
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

            // ⑥ onSurfaceChanged → Surface 尺寸变化兜底（旋转通常伴随宽高互换）
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

    // ── Surface 就绪 ──────────────────────────────────────────

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
     * 从 engine 实时解析当前有效的 Surface（避免缓存失效）。
     * mSurfaceHolder 缺失或 Surface 无效（isValid == false）时返回 null。
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
     * 检测 Engine 当前绑定的 display 方向。
     * 反射失败时返回 ORIENTATION_UNDEFINED，调用方按竖屏处理。
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
     * 严格匹配：只返回当前方向对应的视频路径。
     * 该方向视频不存在时返回 null（不跨方向回退），调用方应保持静态壁纸。
     */
    private fun videoPathFor(orientation: Int): String? {
        val baseDir = Environment.getExternalStorageDirectory().path + CUSTOM_VIDEO_DIR
        val fileName = when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> VIDEO_LAND
            else -> VIDEO_PORTRAIT // 未定义/未知方向按竖屏处理
        }
        val path = "$baseDir/$fileName"
        return if (File(path).exists()) path else null
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
            // Surface 可能已失效，configure 前再校验一次，避免 native_configure 抛异常
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
                decodeLoop(trackIndex, engine)
            }, "DesktopLiveWallpaper-Decode").also { it.start() }

            logger.info("DesktopLiveWallpaper: playback started ${w}x${h}")
        } catch (t: Throwable) {
            logger.error("DesktopLiveWallpaper: startPlayback failed", t)
            releaseCodec()
        }
    }

    /**
     * 根据用户偏好设置视频在 Surface 上的显示模式（MediaCodec surface 输出官方缩放模式）：
     * - fit   → VIDEO_SCALING_MODE_SCALE_TO_FIT（保持宽高比，完整显示，可能留黑边）
     * - cover → VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING（等比裁剪铺满全屏，无黑边不变形）
     *
     * 偏好值由前端下拉选择写入 xposed_module_config。
     * 未知值不调用（保持系统默认行为）；失败仅记录日志，不影响播放。
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
     * 停止播放。默认同时清空 engineSurface；
     * 方向切换等 Surface 仍然有效的场景传 clearSurface = false 保留 Surface 以便复用。
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

    // ── 方向切换 ──────────────────────────────────────────────

    /**
     * 方向/显示变化统一入口（onDisplayChanged / onConfigurationChanged / onSurfaceChanged 共用）：
     * 重新检测方向，与当前生效方向不同时按新方向选择视频。
     * - 新方向有对应视频 → 停止旧播放并切换（Surface 复用，不重建）
     * - 新方向无对应视频 → 停止播放，回退静态壁纸（drawFrameOnCanvas 恢复 proceed）
     */
    private fun refreshOrientationAndPlayback(engine: Any) {
        val orientation = detectOrientation(engine)
        if (orientation == currentOrientation) return
        currentOrientation = orientation
        // Surface 尚未就绪（engine 刚创建）时，等待 onSurfaceCreated 再解析
        if (engineSurface == null) return

        val videoPath = videoPathFor(orientation)
        if (videoPath == null) {
            logger.info(
                "DesktopLiveWallpaper: no video for orientation $orientation, fallback to static"
            )
            stopPlayback(clearSurface = false)
            // 清掉 Surface 上残留的视频最后一帧，重新显示静态壁纸
            redrawStaticWallpaper(engine)
            return
        }

        // Surface 可能已随方向变化失效（onSurfaceDestroyed 尚未回调），
        // 重新实时解析；无效则跳过本次切换，等 onSurfaceCreated / 下一次回调再触发
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
     * fallback 到静态壁纸时，主动触发 SystemUI 的重绘，
     * 清掉 Surface 上残留的视频最后一帧。
     *
     * CanvasEngine 的重绘入口（onSurfaceRedrawNeeded → mLongExecutor）
     * 带 `if (!mDrawn)` 检查，静态壁纸画过后 mDrawn=true 会被跳过；
     * 因此这里先重置 mDrawn=false，再在 mLock 同步下直接调用
     * drawFrameInternal()（内部经 drawFrameOnCanvas 绘制静态 bitmap）。
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
