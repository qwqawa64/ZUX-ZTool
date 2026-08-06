package com.qimian233.ztool.viewmodel

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

    fun saveCustomChargeVideo(context: android.content.Context, uri: android.net.Uri, fileName: String): Boolean {
        return saveVideoDirect(context, uri, fileName)
    }

    fun saveWallpaperVideo(context: android.content.Context, uri: android.net.Uri, fileName: String): Boolean {
        return saveVideoDirect(context, uri, fileName)
    }

    private fun saveVideoDirect(context: android.content.Context, uri: android.net.Uri, fileName: String): Boolean {
        try {
            val targetPath = "$CUSTOM_VIDEO_DIR/$fileName"
            val shellExecutor = com.qimian233.ztool.EnhancedShellExecutor.getInstance()
            shellExecutor.executeRootCommand("mkdir -p $CUSTOM_VIDEO_DIR", 5)

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false

            val process = Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "cat > $targetPath && chmod 644 $targetPath"))
            process.outputStream.use { it.write(bytes) }
            return process.waitFor() == 0
        } catch (e: Exception) {
            return false
        }
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
        private const val CUSTOM_VIDEO_DIR = "/sdcard/Download/ZTool"
    }
}
