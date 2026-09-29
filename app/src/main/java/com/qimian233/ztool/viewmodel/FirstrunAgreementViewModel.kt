package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.home.FirstrunAgreementRepository
import com.qimian233.ztool.data.home.FirstrunCheckState
import com.qimian233.ztool.data.home.FirstrunPageSchema
import com.qimian233.ztool.data.home.FirstrunSchemaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FirstrunAgreementViewModel(
    private val repository: FirstrunAgreementRepository,
    private val agreementRepository: AgreementRepository,
    private val schemaRepository: FirstrunSchemaRepository
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

    /** Records that the user passed the agreement page at its current schema version. */
    fun completeAgreementPage() {
        schemaRepository.markPageAccepted(FirstrunPageSchema.AGREEMENT)
        _uiState.value = _uiState.value.copy(accepted = true)
    }

    /** Records that the user passed the source-verify page at its current schema version. */
    fun completeSourceVerifyPage() {
        schemaRepository.markPageAccepted(FirstrunPageSchema.SOURCE_VERIFY)
    }

    /**
     * Final completion (last flow page): records every registered page at its
     * current schema version, so nothing replays until a schema bumps.
     */
    fun completeFirstrun() {
        schemaRepository.markAllAccepted()
        _uiState.value = _uiState.value.copy(accepted = true)
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
