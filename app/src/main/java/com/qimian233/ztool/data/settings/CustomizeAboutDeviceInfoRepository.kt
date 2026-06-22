package com.qimian233.ztool.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CustomizeAboutDeviceInfoRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): CustomizeAboutDeviceInfoState {
        return CustomizeAboutDeviceInfoState(
            enabled = prefsUtils.loadBooleanSetting(KEY_ENABLED, false),
            modelEnabled = prefsUtils.loadBooleanSetting(KEY_MODEL_ENABLED, false),
            cpuEnabled = prefsUtils.loadBooleanSetting(KEY_CPU_ENABLED, false),
            ramEnabled = prefsUtils.loadBooleanSetting(KEY_RAM_ENABLED, false),
            romEnabled = prefsUtils.loadBooleanSetting(KEY_ROM_ENABLED, false),
            softwareEnabled = prefsUtils.loadBooleanSetting(KEY_SOFTWARE_ENABLED, false),
            headerEnabled = prefsUtils.loadBooleanSetting(KEY_HEADER_ENABLED, false),
            model = prefsUtils.loadStringSetting(KEY_MODEL, ""),
            cpu = prefsUtils.loadStringSetting(KEY_CPU, ""),
            ram = prefsUtils.loadStringSetting(KEY_RAM, ""),
            rom = prefsUtils.loadStringSetting(KEY_ROM, ""),
            software = prefsUtils.loadStringSetting(KEY_SOFTWARE, ""),
        )
    }

    fun setEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_ENABLED, enabled)
    fun setModelEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_MODEL_ENABLED, enabled)
    fun setCpuEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_CPU_ENABLED, enabled)
    fun setRamEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_RAM_ENABLED, enabled)
    fun setRomEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_ROM_ENABLED, enabled)
    fun setSoftwareEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_SOFTWARE_ENABLED, enabled)
    fun setHeaderEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_HEADER_ENABLED, enabled)

    fun setModel(value: String) = prefsUtils.saveStringSetting(KEY_MODEL, value)
    fun setCpu(value: String) = prefsUtils.saveStringSetting(KEY_CPU, value)
    fun setRam(value: String) = prefsUtils.saveStringSetting(KEY_RAM, value)
    fun setRom(value: String) = prefsUtils.saveStringSetting(KEY_ROM, value)
    fun setSoftware(value: String) = prefsUtils.saveStringSetting(KEY_SOFTWARE, value)

    fun saveDeviceHeaderImage(context: Context, uri: Uri): Boolean {
        val targetDir = File(context.filesDir, "device_info")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false
        }
        val tempFile = File(targetDir, "device_info_tmp")
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        if (bitmap == null) return false
        FileOutputStream(tempFile).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                return false
            }
        }
        val shellResult = shellExecutor.executeRootCommand(
            "mkdir -p /sdcard/Download/ZTool && cp \"${tempFile.absolutePath}\" \"$TARGET_IMAGE_PATH\" && chmod 644 \"$TARGET_IMAGE_PATH\"",
            10
        )
        return shellResult.isSuccess
    }

    companion object {
        private const val TARGET_IMAGE_PATH = "/sdcard/Download/ZTool/device_info.jpg"
        private const val KEY_ENABLED = "about_device_info"
        private const val KEY_MODEL_ENABLED = "about_device_info_model_enabled"
        private const val KEY_CPU_ENABLED = "about_device_info_cpu_enabled"
        private const val KEY_RAM_ENABLED = "about_device_info_ram_enabled"
        private const val KEY_ROM_ENABLED = "about_device_info_rom_enabled"
        private const val KEY_SOFTWARE_ENABLED = "about_device_info_software_enabled"
        private const val KEY_HEADER_ENABLED = "about_device_info_header_enabled"
        private const val KEY_MODEL = "about_device_info_model"
        private const val KEY_CPU = "about_device_info_cpu"
        private const val KEY_RAM = "about_device_info_ram"
        private const val KEY_ROM = "about_device_info_rom"
        private const val KEY_SOFTWARE = "about_device_info_software"
    }
}

data class CustomizeAboutDeviceInfoState(
    val enabled: Boolean = false,
    val modelEnabled: Boolean = false,
    val cpuEnabled: Boolean = false,
    val ramEnabled: Boolean = false,
    val romEnabled: Boolean = false,
    val softwareEnabled: Boolean = false,
    val headerEnabled: Boolean = false,
    val model: String = "",
    val cpu: String = "",
    val ram: String = "",
    val rom: String = "",
    val software: String = ""
)
