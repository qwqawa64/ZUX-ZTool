package com.qimian233.ztool.hook.modules.systemui.wallpaper;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.SystemClock;
import android.view.SurfaceHolder;

import com.qimian233.ztool.hook.base.AppHookModule;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModuleInterface;

/**
 * 桌面动态壁纸 — 技术验证 Hook。
 * <p>
 * 硬编码路径 + 劫持 ImageWallpaper.CanvasEngine，用 Choreographer 风格帧循环
 * 将视频帧逐帧绘制到桌面 WallpaperService 的 Surface 上。
 * </p>
 * <p>
 * getModuleName() 返回 "test_hook"，始终启用，无需前端开关。
 * </p>
 */
@SuppressWarnings("DiscouragedPrivateApi")
public class DesktopLiveWallpaperHook extends AppHookModule {

    private static final String TAG = "DesktopLiveWallpaperHook";
    // 硬编码视频路径（与锁屏动态壁纸共用）
    private static final String VIDEO_PATH =
            "/storage/emulated/0/.keyguard/video/wallpaper_port.mp4";
    // 帧间隔 ~30fps
    private static final int FRAME_INTERVAL_MS = 33;

    // Hook 期间持有的引用
    private MediaMetadataRetriever mRetriever;
    private Handler mEngineHandler;
    private Method mDrawFrameMethod;
    private long mVideoDurationUs;
    private volatile boolean mFrameLoopRunning = false;

    public DesktopLiveWallpaperHook() {}

    @Override
    public String getModuleName() {
        return "test_hook"; // 常开
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader cl = param.getDefaultClassLoader();
        if (!"com.android.systemui".equals(param.getPackageName())) return;

        try {
            hookOnSurfaceCreated(cl);
        } catch (Throwable t) {
            logger.error("DesktopLiveWallpaper: init hooks failed", t);
        }
    }

    // ── Hook: CanvasEngine.onSurfaceCreated ──────────────────────

    private void hookOnSurfaceCreated(ClassLoader cl) throws Throwable {
        Class<?> engineClass = cl.loadClass(
                "com.android.systemui.wallpapers.ImageWallpaper$CanvasEngine");
        Method onSurfaceCreated = engineClass.getDeclaredMethod(
                "onSurfaceCreated", SurfaceHolder.class);

        hookWithId(onSurfaceCreated, "desktop_lwp_surface", chain -> {
            chain.proceed(); // 先执行原逻辑

            try {
                Object engine = chain.getThisObject();
                setupFrameLoop(engine);
            } catch (Throwable t) {
                logger.error("DesktopLiveWallpaper: setupFrameLoop failed", t);
            }
            return null;
        });

        logger.info("DesktopLiveWallpaper: onSurfaceCreated hooked");
    }

    // ── 帧循环 ──────────────────────────────────────────────────

    private void setupFrameLoop(Object engine) throws Throwable {
        // 如果已有运行中的循环，先停掉
        stopFrameLoop();

        // 获取 drawFrameOnCanvas Method（public final，直接反射）
        mDrawFrameMethod = engine.getClass().getDeclaredMethod(
                "drawFrameOnCanvas", Bitmap.class);
        mDrawFrameMethod.setAccessible(true);

        // 初始化 MediaMetadataRetriever
        mRetriever = new MediaMetadataRetriever();
        try {
            mRetriever.setDataSource(VIDEO_PATH);
            String durStr = mRetriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            mVideoDurationUs = (durStr != null ? Long.parseLong(durStr) : 5000L) * 1000L;
        } catch (Exception e) {
            logger.error("DesktopLiveWallpaper: cannot open video at " + VIDEO_PATH, e);
            mRetriever.release();
            mRetriever = null;
            return;
        }

        // 在当前线程（Engine 的工作线程）创建 Handler
        mEngineHandler = new Handler();
        mFrameLoopRunning = true;

        final long startTime = SystemClock.elapsedRealtime();

        mEngineHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mFrameLoopRunning || mRetriever == null || mDrawFrameMethod == null) {
                    return;
                }

                try {
                    long elapsedMs = SystemClock.elapsedRealtime() - startTime;
                    // 循环播放：取模控制在视频时长内
                    long frameTimeUs = (elapsedMs * 1000L) % mVideoDurationUs;

                    Bitmap frame = mRetriever.getFrameAtTime(
                            frameTimeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame != null) {
                        mDrawFrameMethod.invoke(engine, frame);
                        frame.recycle();
                    }
                } catch (Exception e) {
                    logger.error("DesktopLiveWallpaper: frame loop error", e);
                }

                if (mFrameLoopRunning && mEngineHandler != null) {
                    mEngineHandler.postDelayed(this, FRAME_INTERVAL_MS);
                }
            }
        });

        logger.info("DesktopLiveWallpaper: frame loop started, duration="
                + (mVideoDurationUs / 1000L) + "ms");
    }

    private void stopFrameLoop() {
        mFrameLoopRunning = false;
        if (mEngineHandler != null) {
            mEngineHandler.removeCallbacksAndMessages(null);
            mEngineHandler = null;
        }
        if (mRetriever != null) {
            try {
                mRetriever.release();
            } catch (Exception ignored) {}
            mRetriever = null;
        }
        mDrawFrameMethod = null;
    }
}
