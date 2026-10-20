package com.qimian233.ztool.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.qimian233.ztool.data.settings.SettingsRepository

/** Returns the owner-scoped [SettingsViewModel] shared by the settings and first-run routes. */
@Composable
fun rememberSettingsViewModel(owner: ViewModelStoreOwner): SettingsViewModel {
    val context = LocalContext.current
    return remember(owner) {
        ViewModelProvider(
            owner,
            SettingsViewModelFactory(SettingsRepository(context.applicationContext))
        )[SettingsViewModel::class.java]
    }
}

private class SettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
