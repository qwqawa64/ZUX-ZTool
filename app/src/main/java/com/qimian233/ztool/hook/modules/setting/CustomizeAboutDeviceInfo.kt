package com.qimian233.ztool.hook.modules.setting

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.widget.ImageView
import android.widget.TextView
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class CustomizeAboutDeviceInfo : AppHookModule() {
    override fun getModuleName(): String = "about_device_info"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)
    
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookSummaryIfEnabled(
            classLoader,
            CPU_CLASS,
            PreferenceKeys.ABOUT_DEVICE_INFO_CPU_ENABLED.name,
            PreferenceKeys.ABOUT_DEVICE_INFO_CPU.name,
            "CPU"
        )
        hookSummaryIfEnabled(
            classLoader,
            RAM_CLASS,
            PreferenceKeys.ABOUT_DEVICE_INFO_RAM_ENABLED.name,
            PreferenceKeys.ABOUT_DEVICE_INFO_RAM.name,
            "RAM"
        )
        hookSummaryIfEnabled(
            classLoader,
            ROM_CLASS,
            PreferenceKeys.ABOUT_DEVICE_INFO_ROM_ENABLED.name,
            PreferenceKeys.ABOUT_DEVICE_INFO_ROM.name,
            "ROM"
        )
        hookSummaryIfEnabled(
            classLoader,
            MODEL_CLASS,
            PreferenceKeys.ABOUT_DEVICE_INFO_MODEL_ENABLED.name,
            PreferenceKeys.ABOUT_DEVICE_INFO_MODEL.name,
            "model"
        )
        hookSummaryIfEnabled(
            classLoader,
            SOFTWARE_CLASS,
            PreferenceKeys.ABOUT_DEVICE_INFO_SOFTWARE_ENABLED.name,
            PreferenceKeys.ABOUT_DEVICE_INFO_SOFTWARE.name,
            "software"
        )
        hookHeaderImageAndText(classLoader)
    }

    private fun hookSummaryIfEnabled(
        classLoader: ClassLoader,
        targetClass: String,
        enabledKey: String,
        valueKey: String,
        fieldName: String
    ) {
        try {
            val m = classLoader.loadClass(targetClass).getDeclaredMethod("getSummary")
            hookWithId(m, "hook_74") { chain: XposedInterface.Chain? ->
                val prefEnabled: Boolean = try {
                    remotePreferences
                        .getBoolean(enabledKey, false)
                } catch (_: Throwable) {
                    false
                }
                if (!prefEnabled) {
                    return@hookWithId chain!!.proceed()
                }
                val value = try {
                    remotePreferences
                        .getString(valueKey, "")
                } catch (_: Throwable) {
                    ""
                }
                if (value != null && !value.trim { it <= ' ' }.isEmpty()) {
                    logger.debug("$fieldName summary -> $value")
                    return@hookWithId value
                }
                chain!!.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook $fieldName summary", t)
        }
    }

    private fun hookHeaderImageAndText(classLoader: ClassLoader) {
        try {
            val setImageMethod = classLoader
                .loadClass(HEADER_VIEW_CLASS)
                .getDeclaredMethod("setImage", ImageView::class.java)
            hookWithId(setImageMethod, "set_image") { chain: XposedInterface.Chain? ->
                val result = chain!!.proceed()
                val headerEnabled: Boolean = try {
                    remotePreferences
                        .getBoolean(PreferenceKeys.ABOUT_DEVICE_INFO_HEADER_ENABLED.name, false)
                } catch (_: Throwable) {
                    false
                }
                if (!headerEnabled) {
                    return@hookWithId result
                }
                val bitmap = decodeHeaderBitmap()
                if (bitmap == null) {
                    logger.warn(
                        "Header image file missing: " + Environment.getExternalStorageDirectory()
                            .path + DEVICE_IMAGE_PATH
                    )
                    return@hookWithId result
                }
                (chain.args[0] as ImageView).setImageBitmap(bitmap)
                logger.debug(
                    "Header image loaded from " + Environment.getExternalStorageDirectory()
                        .path + DEVICE_IMAGE_PATH
                )
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook header image", t)
        }

        try {
            val updateTextMethod = classLoader
                .loadClass(HEADER_VIEW_CLASS)
                .getDeclaredMethod("updateText")
            hookWithId(updateTextMethod, "update_text") { chain: XposedInterface.Chain? ->
                val result = chain!!.proceed()
                val modelEnabled: Boolean = try {
                    remotePreferences
                        .getBoolean(PreferenceKeys.ABOUT_DEVICE_INFO_MODEL_ENABLED.name, false)
                } catch (_: Throwable) {
                    false
                }
                if (!modelEnabled) {
                    return@hookWithId result
                }
                val model = try {
                    remotePreferences
                        .getString(PreferenceKeys.ABOUT_DEVICE_INFO_MODEL.name, "")
                } catch (_: Throwable) {
                    ""
                }
                if (model != null && !model.trim { it <= ' ' }.isEmpty()) {
                    val tvPadField = chain.thisObject.javaClass.getDeclaredField("tvPad")
                    tvPadField.isAccessible = true
                    val view = tvPadField.get(chain.thisObject)
                    if (view is TextView) {
                        view.text = model
                        logger.debug("Header model text -> $model")
                    }
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook header text", t)
        }
    }

    private fun decodeHeaderBitmap(): Bitmap? {
        val imageFile =
            File(Environment.getExternalStorageDirectory().path + DEVICE_IMAGE_PATH)
        if (!imageFile.exists() || !imageFile.isFile) {
            return null
        }
        try {
            FileInputStream(imageFile).use { inputStream ->
                return BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            logger.error("Failed to decode header image", e)
            return null
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.android.settings"

        private const val DEVICE_IMAGE_PATH = "/Download/ZTool/device_info.jpg"
        private const val HEADER_VIEW_CLASS =
            "com.lenovo.settings.deviceinfo.aboutphone.PadTopImgPreference"
        private const val CPU_CLASS =
            "com.lenovo.settings.deviceinfo.controller.CpuInfoDisplayPreferenceController"
        private const val RAM_CLASS =
            "com.lenovo.settings.deviceinfo.controller.RamSizePreferenceController"
        private const val ROM_CLASS =
            "com.lenovo.settings.deviceinfo.controller.RomSizePreferenceController"
        private const val MODEL_CLASS =
            "com.android.settings.deviceinfo.hardwareinfo.DeviceModelPreferenceController"
        private const val SOFTWARE_CLASS =
            "com.android.settings.deviceinfo.BuildNumberPreferenceController"
    }
}
