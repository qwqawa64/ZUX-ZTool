package com.qimian233.ztool.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.systemui.AnimationWallpaperSettingsRepository
import com.qimian233.ztool.data.systemui.AnimationWallpaperSettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnimationWallpaperSettingsViewModel(
    private val repository: AnimationWallpaperSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AnimationWallpaperSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = loadInitialState()
    }

    private fun loadInitialState(): AnimationWallpaperSettingsUiState {
        try {
            return repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load animation wallpaper settings", e)
        }
        return AnimationWallpaperSettingsUiState()
    }

    fun setNoChargeAnimation(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(noChargeAnimation = enabled)
        repository.saveNoChargeAnimation(enabled)
    }

    fun setChargeAnimationFix(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(chargeAnimationFix = enabled)
        repository.saveChargeAnimationFix(enabled)
    }

    fun setCustomChargeAnimation(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(customChargeAnimation = enabled)
        repository.saveCustomChargeAnimation(enabled)
    }

    fun setDesktopLiveWallpaper(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(desktopLiveWallpaper = enabled)
        repository.saveDesktopLiveWallpaper(enabled)
    }

    fun saveCustomChargeVideo(uri: Uri, fileName: String): Boolean {
        return repository.saveVideo(uri, fileName)
    }

    fun saveWallpaperVideo(uri: Uri, fileName: String): Boolean {
        return repository.saveVideo(uri, fileName)
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun forceStopScope(onResult: (Boolean, String) -> Unit) {
        if (_uiState.value.isRestartProcessing) return
        _uiState.value = _uiState.value.copy(showRestartDialog = false, isRestartProcessing = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.forceStopScope()
                withContext(Dispatchers.Main) { onResult(result.success, result.error) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "") }
            } finally {
                _uiState.value = _uiState.value.copy(isRestartProcessing = false)
            }
        }
    }

    companion object {
        private const val TAG = "AnimationWallpaperSettingsViewModel"
    }
}
