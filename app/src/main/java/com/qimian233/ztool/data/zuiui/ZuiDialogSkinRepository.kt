package com.qimian233.ztool.data.zuiui

import android.content.Context
import com.qimian233.ztool.data.advanced.AdvancedSettingsRepository
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.viewmodel.ZuiDialogSkinUiState

/**
 * Persistence and apply action for the cross-app ZUI dialog skin.
 */
class ZuiDialogSkinRepository(
    context: Context,
    private val advancedSettings: AdvancedSettingsRepository = AdvancedSettingsRepository()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): ZuiDialogSkinUiState =
        ZuiDialogSkinUiState(
            dialogSkinEnabled = prefsUtils.loadBooleanSetting(KEY_DIALOG_SKIN, false)
        )

    fun saveDialogSkinEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DIALOG_SKIN, enabled)
    }

    /** Frameworks below API 102 have no hot reload, so callers must be told instead. */
    fun supportsHotReload(): Boolean = advancedSettings.getApiVersion() >= HOT_RELOAD_MIN_API

    /**
     * Pushes the setting into every running target by hot reload, which reinstalls the
     * hooks without killing the host processes.
     */
    fun applyByHotReload(onResult: (succeeded: Int, failed: Int) -> Unit) {
        advancedSettings.performHotReloadAll(
            onProgress = { _, _ -> },
            onComplete = { succeeded, failed, _, _, _ -> onResult(succeeded, failed) }
        )
    }

    private companion object {
        const val HOT_RELOAD_MIN_API = 102
        val KEY_DIALOG_SKIN = PreferenceKeys.ZUI_DIALOG_SKIN.name
    }
}
