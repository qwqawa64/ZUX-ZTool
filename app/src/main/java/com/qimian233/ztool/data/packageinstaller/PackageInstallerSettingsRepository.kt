package com.qimian233.ztool.data.packageinstaller

import android.content.Context
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.utils.ScopeUtils
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

    fun forceStopPackage(): RestartPackageResult {
        val packages = ScopeUtils.getScopePackages(FeatureDestination.PackageInstaller)
        return when (val result = ScopeUtils.restartScope(packages)) {
            is ScopeUtils.RestartResult.Success -> RestartPackageResult(success = true, error = "")
            is ScopeUtils.RestartResult.PartialSuccess -> RestartPackageResult(
                success = false,
                error = "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> RestartPackageResult(
                success = false,
                error = result.message
            )
        }
    }

    companion object {
        private val KEY_DISABLE_SCAN_APK = PreferenceKeys.DISABLE_SCAN_APK.name
        private val KEY_ALWAYS_ALLOW_PERMISSION = PreferenceKeys.ALWAYS_ALLOW_PERMISSION.name
        private val KEY_SKIP_WARN_PAGE = PreferenceKeys.SKIP_WARN_PAGE.name
        private val KEY_DISABLE_INSTALLER_AD = PreferenceKeys.DISABLE_INSTALLER_AD.name
        private val KEY_PACKAGE_INSTALLER_STYLE_HOOK = PreferenceKeys.PACKAGE_INSTALLER_STYLE_HOOK.name
        private val KEY_DISABLE_DELETE_PACKAGE = PreferenceKeys.PACKAGE_INSTALLER_DISABLE_DELETE.name
    }
}

data class RestartPackageResult(
    val success: Boolean,
    val error: String
)
