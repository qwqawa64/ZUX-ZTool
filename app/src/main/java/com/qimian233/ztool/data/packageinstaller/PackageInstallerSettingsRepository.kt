package com.qimian233.ztool.data.packageinstaller

import android.content.Context
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.viewmodel.PackageInstallerSettingsUiState

class PackageInstallerSettingsRepository(
    context: Context
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): PackageInstallerSettingsUiState {
        return PackageInstallerSettingsUiState(
            disableScanApk = prefsUtils.loadBooleanSetting(KEY_DISABLE_SCAN_APK, false),
            alwaysAllowPermission = prefsUtils.loadBooleanSetting(KEY_ALWAYS_ALLOW_PERMISSION, false),
            skipWarnPage = prefsUtils.loadBooleanSetting(KEY_SKIP_WARN_PAGE, false),
            disableInstallerAd = prefsUtils.loadBooleanSetting(KEY_DISABLE_INSTALLER_AD, false),
            packageInstallerStyleHook = prefsUtils.loadBooleanSetting(KEY_PACKAGE_INSTALLER_STYLE_HOOK, false),
            disableDeletePackage = prefsUtils.loadBooleanSetting(KEY_DISABLE_DELETE_PACKAGE, false)
        )
    }

    fun saveDisableScanApk(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_SCAN_APK, enabled)
    }

    fun saveAlwaysAllowPermission(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_ALWAYS_ALLOW_PERMISSION, enabled)
    }

    fun saveSkipWarnPage(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SKIP_WARN_PAGE, enabled)
    }

    fun saveDisableInstallerAd(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_INSTALLER_AD, enabled)
    }

    fun savePackageInstallerStyleHook(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PACKAGE_INSTALLER_STYLE_HOOK, enabled)
    }

    fun saveDisableDeletePackage(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_DELETE_PACKAGE, enabled)
    }

    fun forceStopPackage(packageName: String): RestartPackageResult {
        if (packageName.isEmpty()) {
            return RestartPackageResult(success = true, error = "")
        }

        return try {
            val process = Runtime.getRuntime().exec("su -c killall $packageName")
            process.waitFor()
            RestartPackageResult(success = true, error = "")
        } catch (e: Exception) {
            RestartPackageResult(success = false, error = e.message.orEmpty())
        }
    }

    companion object {
        private const val KEY_DISABLE_SCAN_APK = "disable_scanAPK"
        private const val KEY_ALWAYS_ALLOW_PERMISSION = "Always_AllowPermission"
        private const val KEY_SKIP_WARN_PAGE = "Skip_WarnPage"
        private const val KEY_DISABLE_INSTALLER_AD = "disable_installerAD"
        private const val KEY_PACKAGE_INSTALLER_STYLE_HOOK = "packageInstallerStyle_hook"
        private const val KEY_DISABLE_DELETE_PACKAGE = "package_installer_disable_delete"
    }
}

data class RestartPackageResult(
    val success: Boolean,
    val error: String
)
