package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.home.FirstrunAgreementRepository
import com.qimian233.ztool.data.home.FirstrunCheckState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FirstrunAgreementViewModel(
    private val repository: FirstrunAgreementRepository,
    private val agreementRepository: AgreementRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FirstrunAgreementUiState())
    val uiState: StateFlow<FirstrunAgreementUiState> = _uiState.asStateFlow()

    fun refreshChecks() {
        _uiState.value = _uiState.value.copy(
            isRefreshing = true
        )

        Thread {
            val checkState = repository.refreshState()
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                checkState = checkState
            )
        }.start()
    }

    fun acceptAgreement() {
        agreementRepository.markAgreementAccepted()
        _uiState.value = _uiState.value.copy(
            accepted = true,
            acceptedAgreementVersion = agreementRepository.getCurrentAgreementVersion()
        )
    }

    fun declineAgreement() {
        _uiState.value = _uiState.value.copy(declined = true)
    }

    /**
     * Opens ZUI's auto-start management page: root shell first (lands directly on
     * the non-exported AutoRun activity), exported intents as fallback. [onResult]
     * reports whether any launch path succeeded; the check refreshes when the user
     * returns (resume observer in the route).
     */
    fun requestAutoStart(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val launchedViaRoot = repository.openAutoRunPageWithRoot()
            withContext(Dispatchers.Main) {
                onResult(if (launchedViaRoot) true else repository.openAutoStartSettingsFallback())
            }
        }
    }

    init {
        _uiState.value = _uiState.value.copy(
            checkState = repository.loadInitialState(),
            agreementMarkdown = agreementRepository.loadAgreementMarkdown(),
            agreementVersion = agreementRepository.getCurrentAgreementVersion(),
            acceptedAgreementVersion = agreementRepository.getAcceptedAgreementVersion()
        )
    }
}

data class FirstrunAgreementUiState(
    val isRefreshing: Boolean = false,
    val accepted: Boolean = false,
    val declined: Boolean = false,
    val checkState: FirstrunCheckState = FirstrunCheckState(),
    val agreementMarkdown: String = "",
    val agreementVersion: String = "",
    val acceptedAgreementVersion: String? = null
)
