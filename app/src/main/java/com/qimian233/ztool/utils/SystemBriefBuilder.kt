package com.qimian233.ztool.utils

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.qimian233.ztool.BuildConfig
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.ScopeKeys
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the plain-text system brief shipped next to the logs inside an exported zip.
 *
 * It answers the version questions a bug report always raises: device / ROM identity,
 * Root implementation, LSPosed framework and API level, uptime, the ZTool build and the
 * installed version of every package in the module's LSPosed scope.
 *
 * The brief is best-effort: probes without Root or with the module disabled fall back to
 * a localized "unknown" so the file is always produced. The text is UTF-8 and uses LF
 * line endings regardless of the platform default.
 */
object SystemBriefBuilder {

    /** Entry name of the brief at the root of the exported zip. */
    const val FILE_NAME = "system_brief.txt"

    fun build(context: Context): String {
        val unknown = context.getString(R.string.common_unknown)
        val builder = StringBuilder()

        builder.appendLine(context.getString(R.string.page_settings_brief_title))
        builder.appendLine(
            context.getString(R.string.page_settings_brief_generated_at, timestamp())
        )

        builder.appendLine()
        builder.appendLine(context.getString(R.string.page_settings_brief_section_device))
        builder.appendLine(
            context.getString(
                R.string.page_settings_brief_device_model,
                Build.MODEL.ifBlank { unknown }
            )
        )
        builder.appendLine(
            context.getString(
                R.string.page_settings_brief_android_version,
                context.getString(
                    R.string.page_home_android_version_prefix,
                    Build.VERSION.RELEASE.ifBlank { unknown }
                )
            )
        )
        builder.appendLine(
            context.getString(
                R.string.page_settings_brief_kernel_version,
                SystemInfoProbe.kernelVersion().ifBlank { unknown }
            )
        )
        builder.appendLine(
            context.getString(
                R.string.page_settings_brief_build_number,
                Build.DISPLAY.ifBlank { unknown }
            )
        )
        builder.appendLine(
            context.getString(R.string.page_settings_brief_rom_region, SystemInfoProbe.romRegion(context))
        )
        val (days, hours, minutes) = uptimeParts(SystemClock.elapsedRealtime())
        builder.appendLine(context.getString(R.string.page_settings_brief_uptime, days, hours, minutes))

        builder.appendLine()
        builder.appendLine(context.getString(R.string.page_settings_brief_section_root))
        builder.appendLine(
            context.getString(R.string.page_settings_brief_root_source, SystemInfoProbe.rootSource(context))
        )
        val framework = SystemInfoProbe.frameworkInfo()
        if (framework == null) {
            builder.appendLine(
                context.getString(
                    R.string.page_settings_brief_framework_name,
                    context.getString(R.string.page_home_unknown_framework)
                )
            )
        } else {
            builder.appendLine(
                context.getString(
                    R.string.page_settings_brief_framework_name,
                    framework.name.ifBlank { unknown }
                )
            )
            builder.appendLine(
                context.getString(
                    R.string.page_settings_brief_framework_version,
                    framework.version.ifBlank { unknown },
                    framework.versionCode
                )
            )
            builder.appendLine(
                context.getString(R.string.page_settings_brief_framework_api, framework.apiVersion)
            )
        }

        builder.appendLine()
        builder.appendLine(context.getString(R.string.page_settings_brief_section_ztool))
        builder.appendLine(
            context.getString(
                R.string.page_settings_brief_ztool_version,
                SystemInfoProbe.appVersionLabel(context)
            )
        )
        builder.appendLine(
            context.getString(
                R.string.page_settings_brief_ztool_commit,
                BuildConfig.GIT_COMMIT_COUNT,
                BuildConfig.GIT_COMMIT_HASH
            )
        )

        builder.appendLine()
        builder.appendLine(context.getString(R.string.page_settings_brief_section_scope))
        appendScopeVersions(context, builder, unknown)

        return builder.toString()
    }

    private fun appendScopeVersions(context: Context, builder: StringBuilder, unknown: String) {
        val packages = SystemInfoProbe.scopePackages(context)
        if (packages.isEmpty()) {
            builder.appendLine(context.getString(R.string.page_settings_brief_scope_empty))
            return
        }

        val notInstalled = context.getString(R.string.page_settings_brief_scope_not_installed)
        for (packageName in packages) {
            val version = if (packageName == ScopeKeys.SYSTEM_SERVER.packageName) {
                // The system-server scope entry is not an installed package; its version is the platform one.
                Build.VERSION.RELEASE.ifBlank { unknown }
            } else {
                SystemInfoProbe.installedPackageVersion(context, packageName) ?: notInstalled
            }
            builder.appendLine(
                context.getString(R.string.page_settings_brief_scope_entry, packageName, version)
            )
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun uptimeParts(elapsedRealtimeMs: Long): Triple<Long, Long, Long> {
        val totalMinutes = elapsedRealtimeMs / 60_000
        return Triple(
            totalMinutes / (24 * 60),
            (totalMinutes / 60) % 24,
            totalMinutes % 60
        )
    }
}
