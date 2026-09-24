package com.qimian233.ztool.data.misc

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.GetPCFlashFirmware
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PC flash (9008 brick-recovery) firmware lookup by device SN.
 */
class PcFlashFirmwareRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    fun loadCurrentSn(): String? {
        val keys = listOf("ro.odm.lenovo.gsn", "ro.serialno", "ro.boot.serialno")
        for (key in keys) {
            val result = shellExecutor.executeRootCommand("getprop $key", 3)
            if (result.isSuccess && result.output.trim().isNotEmpty()) {
                return result.output.trim()
            }
        }
        return null
    }

    suspend fun fetchFirmware(sn: String): FirmwareFetchResult {
        val firmwareInfo = GetPCFlashFirmware().queryFirmware(sn)
        return if (firmwareInfo != null && firmwareInfo.size >= 6) {
            FirmwareFetchResult.Success(
                FirmwareResult(
                    downloadUrl = firmwareInfo[0].orEmpty(),
                    password = firmwareInfo[1].orEmpty(),
                    platform = firmwareInfo[2].orEmpty(),
                    method = firmwareInfo[3].orEmpty(),
                    firstUploadTime = formatTimestamp(firmwareInfo[4]?.toLongOrNull() ?: 0L),
                    lastUpdateTime = formatTimestamp(firmwareInfo[5]?.toLongOrNull() ?: 0L)
                )
            )
        } else {
            FirmwareFetchResult.Failure(
                context.getString(R.string.system_update_pc_flash_firmware_fetch_failed_message)
            )
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return timestamp.toString()
        return try {
            SimpleDateFormat("yyyy.MM.dd-HH:mm:ss", Locale.getDefault())
                .format(Date(timestamp * 1000L))
        } catch (_: Exception) {
            timestamp.toString()
        }
    }
}

data class FirmwareResult(
    val downloadUrl: String,
    val password: String,
    val platform: String,
    val method: String,
    val firstUploadTime: String,
    val lastUpdateTime: String
)

sealed interface FirmwareFetchResult {
    data class Success(val firmware: FirmwareResult) : FirmwareFetchResult
    data class Failure(val message: String) : FirmwareFetchResult
}
