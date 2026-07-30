package com.qimian233.ztool.hook.modules.systemui.misc

import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.view.View
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File

/**
 * 自定义充电动画 Hook。
 *
 * 拦截 [android.widget.VideoView.setVideoURI] 调用，
 * 当调用方为 ChargingVideoView 时，将内置资源 URI 替换为外部存储的自定义视频文件。
 *
 * 视频文件路径：
 *   /sdcard/Download/ZTool/charging_animation_portrait.mp4  (竖屏)
 *   /sdcard/Download/ZTool/charging_animation_land.mp4       (横屏)
 *
 * 方向判断与原始 ChargingStyleDefault.getRawId() 一致：
 *   Configuration.ORIENTATION_LANDSCAPE == 2
 */
class CustomChargeAnimation : BaseHookModule() {

    companion object {
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val CHARGING_VIDEO_VIEW_CLASS =
            "com.android.keyguard.lockscreen.charge.ChargingVideoView"
        private const val CUSTOM_VIDEO_DIR = "/Download/ZTool"
        private const val VIDEO_PORTRAIT = "charging_animation_portrait.mp4"
        private const val VIDEO_LAND = "charging_animation_land.mp4"
    }

    override fun getModuleName(): String = "custom_charge_animation"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        log("Loading module CustomChargeAnimation.")

        try {
            val videoViewClass = param.defaultClassLoader.loadClass("android.widget.VideoView")

            // 必须 Hook 两参数版本 setVideoURI(Uri, Map)，因为单参数版本内部
            // 调用 this.setVideoURI(uri, null) 使用的是局部变量 uri，
            // 修改 args[0] 不会影响局部变量。
            val setVideoURIMethod = videoViewClass.getDeclaredMethod(
                "setVideoURI",
                Uri::class.java,
                Map::class.java
            )

            hookWithId(setVideoURIMethod, "set_video_uri") {  chain ->
                val thisObject = chain.thisObject
                if (thisObject != null &&
                    thisObject.javaClass.name == CHARGING_VIDEO_VIEW_CLASS
                ) {
                    val originalUri = chain.args[0] as Uri?
                    val view = thisObject as View
                    val isLandscape =
                        view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val fileName = if (isLandscape) VIDEO_LAND else VIDEO_PORTRAIT
                    val filePath =
                        Environment.getExternalStorageDirectory().path +
                            CUSTOM_VIDEO_DIR + "/" + fileName
                    val file = File(filePath)

                    if (file.exists()) {
                        val customUri = Uri.fromFile(file)
                        log("CustomChargeAnimation: redirecting " +
                            "original=$originalUri -> $filePath")
                        // 构建新 args 显式传入 proceed，避免原地修改被忽略
                        val newArgs = chain.args.toMutableList()
                        newArgs[0] = customUri
                        chain.proceed(newArgs.toTypedArray())
                    } else {
                        log("CustomChargeAnimation: $fileName not found at $filePath, " +
                            "using default $originalUri.")
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            }

            log("CustomChargeAnimation: VideoView.setVideoURI(Uri, Map) hooked successfully.")
        } catch (e: Throwable) {
            logError("Failed to hook VideoView.setVideoURI", e)
        }
    }
}
