package com.qimian233.ztool.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.XposedServiceBridge
import java.util.zip.ZipFile

/** LSPosed framework identity reported by the libxposed service. */
data class FrameworkInfo(
    val name: String,
    val version: String,
    val versionCode: Long,
    val apiVersion: Int
)

/** ZTool's own installed version. */
data class AppVersion(
    val name: String,
    val code: Long
)

/**
 * Shared device / Root / framework probes.
 *
 * The home cards ([com.qimian233.ztool.data.home.HomeRepository], which layers its own
 * short-lived cache on top) and the system brief bundled into an exported log zip both
 * read from here, so the detection commands and the localized fallbacks exist once.
 *
 * Every probe degrades to a localized "unknown" (or null) instead of throwing: the brief
 * must still be produced on a device without Root or with the module disabled.
 */
object SystemInfoProbe {

    private const val TAG = "SystemInfoProbe"

    /** Declared LSPosed scope, read straight from the APK's build-time declaration. */
    private const val DECLARED_SCOPE_ENTRY = "META-INF/xposed/scope.list"

    /** Kernel release — the same value as `uname -r`, no Root required. */
    fun kernelVersion(): String = Os.uname().release

    /** Detects Magisk / KernelSU / APatch and returns a localized label. */
    fun rootSource(
        context: Context,
        shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
    ): String {
        val detectionCommands = arrayOf("magisk -v", "su -v", "apd -v")
        for (cmd in detectionCommands) {
            try {
                val result = shellExecutor.executeRootCommand(cmd, 3)
                if (result.isSuccess && !result.output.isBlank()) {
                    val output = result.output.trim()
                    if (cmd.contains("magisk")) {
                        return context.getString(R.string.page_home_magisk_su_format, output)
                    }
                    if (cmd.contains("su -v") && output.contains("KernelSU")) {
                        val endPosition = output.indexOf("KernelSU")
                        return context.getString(
                            R.string.page_home_kernelsu_format,
                            output.substring(0, endPosition - 1)
                        )
                    }
                    if (cmd.contains("apd")) {
                        return context.getString(R.string.page_home_apatch_format, output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to detect root source: ${e.message}")
            }
        }
        return context.getString(R.string.page_home_unknown_root_available)
    }

    /** Reads the ROM region properties, falling back to a localized "unknown". */
    fun romRegion(
        context: Context,
        shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
    ): String {
        return try {
            val commands = listOf(
                "getprop ro.boot.region",
                "getprop ro.config.zui.region",
                "getprop ro.vendor.config.zui.region"
            )
            commands.firstNotNullOfOrNull { command ->
                val result = shellExecutor.executeRootCommand(command, 3)
                result.output.trim().takeIf { it.isNotEmpty() }
            } ?: context.getString(R.string.common_unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ROM region: ${e.message}")
            context.getString(R.string.common_unknown)
        }
    }

    /** Framework identity, or null when the module is not activated. */
    fun frameworkInfo(): FrameworkInfo? {
        return try {
            val name = XposedServiceBridge.getFrameworkName()
            val version = XposedServiceBridge.getFrameworkVersion()
            val versionCode = XposedServiceBridge.getFrameworkVersionCode()
            val apiVersion = XposedServiceBridge.getApiVersion()
            if (name == null && version == null && versionCode == 0L && apiVersion == 0) {
                return null
            }
            Log.i(
                TAG,
                "framework info: name=$name, version=$version, versionCode=$versionCode, apiVersion=$apiVersion"
            )
            return FrameworkInfo(name.orEmpty(), version.orEmpty(), versionCode, apiVersion)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect framework property: ${e.message}")
            null
        }
    }

    /** Single-line framework description used by the home cards. */
    fun formatFramework(context: Context, info: FrameworkInfo): String {
        val unknown = context.getString(R.string.common_unknown)
        return context.getString(
            R.string.page_home_lsposed_standard_format,
            info.name.ifBlank { unknown },
            info.version.ifBlank { unknown },
            info.versionCode,
            info.apiVersion
        )
    }

    /** ZTool's installed version, or null when the package cannot be queried. */
    fun appVersion(context: Context): AppVersion? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersion(packageInfo.versionName.orEmpty(), packageVersionCode(packageInfo))
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get app version: ${e.message}")
            null
        }
    }

    /** `versionName (versionCode)` for ZTool, or a localized "unknown". */
    fun appVersionLabel(context: Context): String {
        val version = appVersion(context) ?: return context.getString(R.string.common_unknown)
        return if (version.name.isBlank()) version.code.toString() else "${version.name} (${version.code})"
    }

    /** `versionName (versionCode)` of an installed package, or null when it is missing. */
    fun installedPackageVersion(context: Context, packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val name = packageInfo.versionName.orEmpty()
            val code = packageVersionCode(packageInfo)
            if (name.isBlank()) code.toString() else "$name ($code)"
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Package names the module declares in its LSPosed scope, in declaration order.
     *
     * The build-time `scope.list` packaged at the APK root is authoritative; when it
     * cannot be read (or is absent from an unusual build), the scope LSPosed currently
     * reports is used instead.
     */
    fun scopePackages(context: Context): List<String> {
        val declared = readDeclaredScope(context)
        return declared.ifEmpty { XposedServiceBridge.getScope().sorted() }
    }

    private fun readDeclaredScope(context: Context): List<String> {
        return try {
            ZipFile(context.applicationInfo.sourceDir).use { apk ->
                val entry = apk.getEntry(DECLARED_SCOPE_ENTRY) ?: return emptyList()
                apk.getInputStream(entry).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toList()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read declared scope: ${e.message}")
            emptyList()
        }
    }

    private fun packageVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
}
