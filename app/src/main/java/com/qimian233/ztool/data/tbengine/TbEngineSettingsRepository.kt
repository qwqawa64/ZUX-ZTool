package com.qimian233.ztool.data.tbengine

import android.content.Context
import android.os.Build
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.OtaCertBuilder
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.TbEngineRestartResult
import com.qimian233.ztool.viewmodel.TbEngineSettingsUiState
import java.io.File

class TbEngineSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun ensureCustomOtaParametersEnabled() {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_OTA_PARAMETERS, true)
    }

    /**
     * 首次进入页面时生成本地 OTA 重签用的 RSA-2048 密钥对。
     * 私钥（PKCS#8）与公钥（X509）以 Base64 存入 xposed_module_config，
     * Hook 侧通过 remotePreferences 读取；私钥仅在设备本机生成，不上传。
     */
    fun ensureOtaSigningKeys() {
        if (prefsUtils.loadStringSetting(KEY_OTA_PRIVATE_KEY, "").isNotEmpty() &&
            prefsUtils.loadStringSetting(KEY_OTA_PUBLIC_KEY, "").isNotEmpty()
        ) {
            ensureOtaCertificate()
            return
        }
        try {
            val keyPair = java.security.KeyPairGenerator.getInstance("RSA").run {
                initialize(2048)
                generateKeyPair()
            }
            prefsUtils.saveStringSetting(
                KEY_OTA_PRIVATE_KEY,
                android.util.Base64.encodeToString(
                    keyPair.private.encoded, android.util.Base64.NO_WRAP
                )
            )
            prefsUtils.saveStringSetting(
                KEY_OTA_PUBLIC_KEY,
                android.util.Base64.encodeToString(
                    keyPair.public.encoded, android.util.Base64.NO_WRAP
                )
            )
            ensureOtaCertificate()
        } catch (e: Exception) {
            android.util.Log.e("TbEngineSettings", "Failed to generate OTA signing keys", e)
        }
    }

    /**
     * 基于已有密钥对生成自签名 X.509 证书（otacerts.zip 信任链用）。
     * 证书 DER 以 Base64 存入偏好；生成后立即用 CertificateFactory 回读校验，
     * 保证 DER 构造正确。
     */
    private fun ensureOtaCertificate() {
        if (prefsUtils.loadStringSetting(KEY_OTA_CERT, "").isNotEmpty()) return
        try {
            val kf = java.security.KeyFactory.getInstance("RSA")
            val private = kf.generatePrivate(
                java.security.spec.PKCS8EncodedKeySpec(
                    android.util.Base64.decode(
                        prefsUtils.loadStringSetting(KEY_OTA_PRIVATE_KEY, ""),
                        android.util.Base64.DEFAULT
                    )
                )
            )
            val public = kf.generatePublic(
                java.security.spec.X509EncodedKeySpec(
                    android.util.Base64.decode(
                        prefsUtils.loadStringSetting(KEY_OTA_PUBLIC_KEY, ""),
                        android.util.Base64.DEFAULT
                    )
                )
            )
            val cert = OtaCertBuilder.buildSelfSignedCertificate(private, public)
            OtaCertBuilder.toX509Certificate(cert) // 回读校验，失败则抛异常不落盘
            prefsUtils.saveStringSetting(
                KEY_OTA_CERT,
                android.util.Base64.encodeToString(cert, android.util.Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            android.util.Log.e("TbEngineSettings", "Failed to generate OTA certificate", e)
        }
    }

    /** 当前设备上是否已具备证书与密钥（决定前端按钮可用性）。 */
    fun hasOtaCertificate(): Boolean =
        prefsUtils.loadStringSetting(KEY_OTA_CERT, "").isNotEmpty()

    /**
     * 生成并安装 OTA 证书信任模块：
     * 1. root 读取设备原 /system/etc/security/otacerts.zip；
     * 2. 追加 ZTool 自签证书（保留 OEM 证书，叠加信任）；
     * 3. 打包 Magisk/KSU 格式模块（systemless 覆盖 otacerts.zip）；
     * 4. 优先 magisk --install-module 安装，失败回退 ksud module install。
     * 返回 null 表示成功，否则为失败原因。
     */
    fun installOtaCertModule(): String? {
        val certB64 = prefsUtils.loadStringSetting(KEY_OTA_CERT, "")
        if (certB64.isEmpty()) return context.getString(R.string.tb_engine_cert_missing)
        val certDer = try {
            android.util.Base64.decode(certB64, android.util.Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return context.getString(R.string.tb_engine_cert_missing)
        }

        val moduleZip = try {
            buildOtaCertModuleZip(certDer)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to build otacerts module", e)
            return context.getString(R.string.tb_engine_cert_module_build_failed, e.message)
        }

        // 优先 Magisk，失败回退 KernelSU（ksud）
        val magiskResult = shellExecutor.executeRootCommand(
            "magisk --install-module \"${moduleZip.absolutePath}\"", 120
        )
        if (magiskResult.isSuccess) {
            return null
        }
        val ksuResult = shellExecutor.executeRootCommand(
            "ksud module install \"${moduleZip.absolutePath}\"", 120
        )
        return if (ksuResult.isSuccess) {
            null
        } else {
            context.getString(
                R.string.tb_engine_cert_module_install_failed,
                (magiskResult.output + ksuResult.output).take(400)
            )
        }
    }

    /**
     * 生成模块 zip：读取设备原 otacerts.zip（root cat），追加 ZTool 证书条目，
     * 放入模块的 system/etc/security/otacerts.zip。
     */
    private fun buildOtaCertModuleZip(certDer: ByteArray): File {
        val original = File.createTempFile("otacerts_orig", ".zip", context.cacheDir)
        try {
            val pull = shellExecutor.executeRootCommand(
                "cat /system/etc/security/otacerts.zip > \"${original.absolutePath}\"", 30
            )
            if (!pull.isSuccess) {
                throw IllegalStateException("Failed to read device otacerts.zip: ${pull.output}")
            }
            val moduleDir = File(context.filesDir, "ota_cert_module").apply {
                deleteRecursively()
                mkdirs()
            }
            val moduleSystemDir = File(moduleDir, "system/etc/security").apply { mkdirs() }
            mergeOtacerts(original, File(moduleSystemDir, "otacerts.zip"), certDer)
            writeModuleProp(moduleDir)
            writeCustomizeSh(moduleDir)
            val moduleZip = File(context.filesDir, "ztool_ota_cert_module.zip")
            zipDirectory(moduleDir, moduleZip)
            return moduleZip
        } finally {
            original.delete()
        }
    }

    /** 原 otacerts 条目全保留，追加 ZTool 证书（STORED DER 条目）。 */
    private fun mergeOtacerts(original: File, target: File, certDer: ByteArray) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(target)).use { zos ->
            val source = if (original.length() > 0) java.util.zip.ZipFile(original) else null
            try {
                if (source != null) {
                    for (entry in source.entries()) {
                        val bytes = source.getInputStream(entry).readBytes()
                        val ne = java.util.zip.ZipEntry(entry.name).apply {
                            method = entry.method
                            if (method == java.util.zip.ZipEntry.STORED) {
                                size = bytes.size.toLong()
                                crc = java.util.zip.CRC32().apply { update(bytes) }.value
                            }
                            time = entry.time
                        }
                        zos.putNextEntry(ne)
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
                val ne = java.util.zip.ZipEntry("ztool_ota.x509.pem").apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = certDer.size.toLong()
                    crc = java.util.zip.CRC32().apply { update(certDer) }.value
                    time = System.currentTimeMillis()
                }
                zos.putNextEntry(ne)
                zos.write(certDer)
                zos.closeEntry()
            } finally {
                source?.close()
            }
        }
    }

    private fun writeModuleProp(moduleDir: File) {
        File(moduleDir, "module.prop").writeText(
            """
            id=ztool_ota_cert
            name=ZTool OTA Local Signing Certificate
            version=v1.0.0
            versionCode=1
            author=ZTool
            description=Appends the ZTool local-signing certificate to /system/etc/security/otacerts.zip (systemless). Enables local OTA packages re-signed by ZTool to pass update_engine verification. Reboot required after install.
            """.trimIndent() + "\n"
        )
    }

    private fun writeCustomizeSh(moduleDir: File) {
        File(moduleDir, "customize.sh").writeText(
            """
            #!/system/bin/sh
            SKIPUNZIP=0
            ui_print "- Overlaying /system/etc/security/otacerts.zip"
            ui_print "- OEM certificates are preserved, ZTool cert appended"
            ui_print "- Reboot required to take effect"
            """.trimIndent() + "\n"
        )
    }

    private fun zipDirectory(sourceDir: File, target: File) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(target)).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(sourceDir).path.replace('\\', '/')
                val bytes = file.readBytes()
                val entry = java.util.zip.ZipEntry(entryName).apply {
                    time = file.lastModified()
                    if (entryName.endsWith(".zip")) {
                        // 内嵌 zip 无需再压缩
                        method = java.util.zip.ZipEntry.STORED
                        size = bytes.size.toLong()
                        crc = java.util.zip.CRC32().apply { update(bytes) }.value
                    }
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }


    fun loadState(): TbEngineSettingsUiState {
        return TbEngineSettingsUiState(
            disableAutoDownload = prefsUtils.loadBooleanSetting(KEY_DISABLE_AUTO_DOWNLOAD, false),
            disableAutoInstall = prefsUtils.loadBooleanSetting(KEY_DISABLE_AUTO_INSTALL, false),
            disableAppUpdate = prefsUtils.loadBooleanSetting(KEY_DISABLE_APP_UPDATE, false),
            disablePush = prefsUtils.loadBooleanSetting(KEY_DISABLE_PUSH, false),
            signLocalOta = prefsUtils.loadBooleanSetting(KEY_SIGN_LOCAL_OTA, false),
            customVersion = prefsUtils.loadStringSetting(KEY_CUSTOM_OTA_TARGET_VERSION, ""),
            customDeviceId = prefsUtils.loadStringSetting(KEY_CUSTOM_OTA_TARGET_DEVICE_ID, ""),
            currentVersion = context.getString(R.string.system_update_loading_ellipsis),
            currentSn = context.getString(R.string.system_update_loading_ellipsis)
        )
    }

    fun saveDisableAutoDownload(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_AUTO_DOWNLOAD, enabled)
    }

    fun saveDisableAutoInstall(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_AUTO_INSTALL, enabled)
    }

    fun saveDisableAppUpdate(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_APP_UPDATE, enabled)
    }

    fun saveDisablePush(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_PUSH, enabled)
    }

    fun saveSignLocalOta(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SIGN_LOCAL_OTA, enabled)
    }

    fun saveCustomVersion(value: String) {
        prefsUtils.saveStringSetting(KEY_CUSTOM_OTA_TARGET_VERSION, value)
    }

    fun saveCustomDeviceId(value: String) {
        prefsUtils.saveStringSetting(KEY_CUSTOM_OTA_TARGET_DEVICE_ID, value)
    }

    fun loadCurrentDeviceInfo(): TbEngineCurrentDeviceInfo {
        val version = Build.DISPLAY.ifEmpty {
            context.getString(R.string.common_unknown)
        }
        val sn = getMachineSnByProps()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.common_unknown)
        return TbEngineCurrentDeviceInfo(version = version, sn = sn)
    }

    fun getMachineSn(): String? = getMachineSnByProps()

    fun restartScope(): TbEngineRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.TbEngine)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> TbEngineRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> TbEngineRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> TbEngineRestartResult.Failure(result.message)
        }
    }

    private fun getMachineSnByProps(): String? {
        val keys = listOf("ro.odm.lenovo.gsn", "ro.serialno", "ro.boot.serialno")
        for (key in keys) {
            val result = shellExecutor.executeRootCommand("getprop $key", 3)
            if (result.isSuccess && result.output.trim().isNotEmpty()) {
                return result.output.trim()
            }
        }
        return null
    }

    companion object {
        private const val TAG = "TbEngineSettings"
        private val KEY_CUSTOM_OTA_PARAMETERS = PreferenceKeys.CUSTOM_OTA_PARAMETERS.name
        private val KEY_OTA_PRIVATE_KEY = PreferenceKeys.TB_ENGINE_OTA_PRIVATE_KEY.name
        private val KEY_OTA_PUBLIC_KEY = PreferenceKeys.TB_ENGINE_OTA_PUBLIC_KEY.name
        private val KEY_OTA_CERT = PreferenceKeys.TB_ENGINE_OTA_CERT.name
        private val KEY_DISABLE_AUTO_DOWNLOAD = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_DOWNLOAD.name
        private val KEY_DISABLE_AUTO_INSTALL = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_INSTALL.name
        private val KEY_DISABLE_APP_UPDATE = PreferenceKeys.DISABLE_TB_ENGINE_APP_UPDATE.name
        private val KEY_DISABLE_PUSH = PreferenceKeys.DISABLE_TB_ENGINE_PUSH.name
        private val KEY_SIGN_LOCAL_OTA = PreferenceKeys.SIGN_TB_ENGINE_LOCAL_OTA.name
        private val KEY_CUSTOM_OTA_TARGET_VERSION = PreferenceKeys.CUSTOM_OTA_TARGET_VERSION_NAME.name
        private val KEY_CUSTOM_OTA_TARGET_DEVICE_ID = PreferenceKeys.CUSTOM_OTA_TARGET_DEVICE_ID.name
    }
}

data class TbEngineCurrentDeviceInfo(
    val version: String,
    val sn: String
)
