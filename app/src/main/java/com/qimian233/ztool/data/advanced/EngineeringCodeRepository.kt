package com.qimian233.ztool.data.advanced

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor

/**
 * One entry of the Lenovo engineering secret-code list.
 * The component is launched with root `am start` because these activities
 * are not exported by com.lenovo.EngineeringCode.
 */
data class EngineeringCodeEntry(
    val key: String,
    val dialCode: String,
    val component: String,
    val extras: String = ""
)

/**
 * Repository for opening Lenovo engineering screens (secret codes) directly.
 * Excludes destructive codes such as factory reset (7777) and NVRAM
 * country-code writers (6020/6030) on purpose.
 */
class EngineeringCodeRepository(
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun getEntries(): List<EngineeringCodeEntry> = ENTRIES

    /**
     * Launch the target component with root privileges.
     * onComplete runs on the main thread with a user-facing message
     * (empty string means success).
     */
    fun launchComponent(
        entry: EngineeringCodeEntry,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        Thread {
            val probe = shellExecutor.executeRootCommand("pm path $ENGINEERING_PACKAGE")
            if (!probe.isSuccess || probe.output.trim().isEmpty()) {
                Log.w(TAG, "Engineering package not found: ${probe.error}")
                mainHandler.post { onComplete(false, ERROR_PACKAGE_MISSING) }
                return@Thread
            }
            val command = buildString {
                append("am start -n ${entry.component}")
                if (entry.extras.isNotEmpty()) append(" ${entry.extras}")
            }
            val result = shellExecutor.executeRootCommand(command)
            if (result.isSuccess) {
                Log.d(TAG, "Launched ${entry.component}")
                mainHandler.post { onComplete(true, "") }
            } else {
                Log.w(TAG, "Launch failed for ${entry.component}: ${result.error}")
                mainHandler.post { onComplete(false, result.error.ifEmpty { "exit=${result.exitCode}" }) }
            }
        }.start()
    }

    companion object {
        private const val TAG = "EngineeringCodeRepo"
        private const val ENGINEERING_PACKAGE = "com.lenovo.EngineeringCode"
        const val ERROR_PACKAGE_MISSING = "PACKAGE_MISSING"

        private const val LOGGER_PACKAGE = "com.debug.loggerui"

        val ENTRIES = listOf(
            EngineeringCodeEntry(
                key = "version_info",
                dialCode = "0000",
                component = "$ENGINEERING_PACKAGE/.DialogVersionInfo"
            ),
            EngineeringCodeEntry(
                key = "sn",
                dialCode = "2222",
                component = "$ENGINEERING_PACKAGE/.DiaologSN"
            ),
            EngineeringCodeEntry(
                key = "framework_version",
                dialCode = "5993",
                component = "$ENGINEERING_PACKAGE/.DiaologFrameworkVerion"
            ),
            EngineeringCodeEntry(
                key = "imei",
                dialCode = "06",
                component = "$ENGINEERING_PACKAGE/.DialogIMEI"
            ),
            EngineeringCodeEntry(
                key = "usb_debug",
                dialCode = "33284",
                component = "$ENGINEERING_PACKAGE/.DialogUsbDebug"
            ),
            EngineeringCodeEntry(
                key = "offline_log",
                dialCode = "3334",
                component = "$LOGGER_PACKAGE/$LOGGER_PACKAGE.MainActivity",
                extras = "--es OpenOrCloseAllLogSwitch OpenAll"
            )
        )
    }
}
