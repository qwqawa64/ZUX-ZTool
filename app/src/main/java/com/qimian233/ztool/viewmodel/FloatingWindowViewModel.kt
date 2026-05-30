package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import com.qimian233.ztool.R
import com.qimian233.ztool.data.settings.FloatingConfigRequest
import com.qimian233.ztool.data.settings.FloatingWindowRepository

class FloatingWindowViewModel(
    private val repository: FloatingWindowRepository
) : ViewModel() {
    private val activityFromSet = linkedSetOf<String>()
    private var appPackage: String? = null
    private var mainActivity: String? = null

    var uiState = FloatingWindowUiState(
        foregroundInfo = repository.initialForegroundInfo(),
        foregroundAppLabel = repository.initialForegroundAppLabel(),
        addedActivitiesText = repository.initialAddedActivitiesText()
    )
        private set

    fun refreshForeground() {
        val snapshot = repository.loadForegroundSnapshot()
        uiState = uiState.copy(
            foregroundInfo = snapshot.foregroundInfo,
            foregroundAppLabel = snapshot.foregroundAppLabel,
            shouldBlockProgress = uiState.currentStep <= FloatingWizardStep.AddActivities &&
                uiState.selectedApp != null &&
                snapshot.appName != uiState.selectedApp
        )
    }

    fun handleNextStep(onEffect: (FloatingWindowEffect) -> Unit) {
        when (uiState.currentStep) {
            FloatingWizardStep.SelectApp -> {
                appPackage = repository.getForegroundPackageForSelection()
                if (appPackage == null) {
                    onEffect(FloatingWindowEffect.ToastResource(R.string.cannot_get_app_foreground))
                    return
                }
                uiState = uiState.copy(
                    selectedApp = repository.getAppNameFromPackage(appPackage),
                    currentStep = FloatingWizardStep.SetMainPage
                )
            }

            FloatingWizardStep.SetMainPage -> {
                mainActivity = repository.getForegroundActivityForSelection()
                if (mainActivity == null) {
                    onEffect(FloatingWindowEffect.ToastResource(R.string.cannot_get_activity))
                    return
                }
                activityFromSet.add(mainActivity.orEmpty())
                uiState = uiState.copy(
                    addedActivitiesText = repository.addedActivitiesText(activityFromSet),
                    currentStep = FloatingWizardStep.AddActivities
                )
            }

            FloatingWizardStep.AddActivities -> {
                uiState = uiState.copy(currentStep = FloatingWizardStep.SetOptions)
            }

            FloatingWizardStep.SetOptions -> {
                uiState = uiState.copy(currentStep = FloatingWizardStep.Complete)
            }

            FloatingWizardStep.Complete -> {
                val saved = repository.generateAndSaveConfig(
                    FloatingConfigRequest(
                        appPackage = appPackage,
                        mainActivity = mainActivity,
                        activityFromSet = activityFromSet,
                        showEmbeddingDivider = uiState.showEmbeddingDivider,
                        skipLetterboxDisplayInfo = uiState.skipLetterboxDisplayInfo,
                        skipMultiWindowMode = uiState.skipMultiWindowMode,
                        showSurfaceViewBackground = uiState.showSurfaceViewBackground,
                        shouldPausePrimaryActivity = uiState.shouldPausePrimaryActivity
                    )
                )
                onEffect(
                    FloatingWindowEffect.ToastResource(
                        if (saved) R.string.config_generated else R.string.config_generation_error,
                        isLong = saved
                    )
                )
                if (saved) {
                    onEffect(FloatingWindowEffect.Close)
                }
            }
        }
    }

    fun addCurrentActivity(onEffect: (FloatingWindowEffect) -> Unit) {
        val currentActivity = repository.getForegroundActivityForSelection()
        if (currentActivity == null) {
            onEffect(FloatingWindowEffect.ToastResource(R.string.cannot_get_activity))
            return
        }

        if (activityFromSet.add(currentActivity)) {
            uiState = uiState.copy(addedActivitiesText = repository.addedActivitiesText(activityFromSet))
            onEffect(FloatingWindowEffect.ToastTextResource(R.string.activity_added, currentActivity))
        } else {
            onEffect(FloatingWindowEffect.ToastResource(R.string.activity_already_added))
        }
    }

    fun setShowEmbeddingDivider(value: Boolean) {
        uiState = uiState.copy(showEmbeddingDivider = value)
    }

    fun setSkipLetterboxDisplayInfo(value: Boolean) {
        uiState = uiState.copy(skipLetterboxDisplayInfo = value)
    }

    fun setSkipMultiWindowMode(value: Boolean) {
        uiState = uiState.copy(skipMultiWindowMode = value)
    }

    fun setShowSurfaceViewBackground(value: Boolean) {
        uiState = uiState.copy(showSurfaceViewBackground = value)
    }

    fun setShouldPausePrimaryActivity(value: Boolean) {
        uiState = uiState.copy(shouldPausePrimaryActivity = value)
    }
}

data class FloatingWindowUiState(
    val currentStep: FloatingWizardStep = FloatingWizardStep.SelectApp,
    val selectedApp: String? = null,
    val foregroundInfo: String,
    val foregroundAppLabel: String,
    val shouldBlockProgress: Boolean = false,
    val addedActivitiesText: String,
    val showEmbeddingDivider: Boolean = true,
    val skipLetterboxDisplayInfo: Boolean = false,
    val skipMultiWindowMode: Boolean = true,
    val showSurfaceViewBackground: Boolean = false,
    val shouldPausePrimaryActivity: Boolean = false
)

enum class FloatingWizardStep {
    SelectApp,
    SetMainPage,
    AddActivities,
    SetOptions,
    Complete
}

sealed interface FloatingWindowEffect {
    data class ToastResource(
        val resId: Int,
        val isLong: Boolean = false
    ) : FloatingWindowEffect

    data class ToastTextResource(
        val resId: Int,
        val value: String
    ) : FloatingWindowEffect

    data object Close : FloatingWindowEffect
}
