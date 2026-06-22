package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.home.FirstrunAgreementRepository
import com.qimian233.ztool.data.home.FirstrunCheckState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        _uiState.value = _uiState.value.copy(accepted = true)
    }

    fun declineAgreement() {
        _uiState.value = _uiState.value.copy(declined = true)
    }

    init {
        _uiState.value = _uiState.value.copy(
            checkState = repository.loadInitialState(),
            agreementMarkdown = agreementRepository.loadAgreementMarkdown()
        )
    }
}

data class FirstrunAgreementUiState(
    val isRefreshing: Boolean = false,
    val accepted: Boolean = false,
    val declined: Boolean = false,
    val checkState: FirstrunCheckState = FirstrunCheckState(),
    val agreementMarkdown: String = ""
)
