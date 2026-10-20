package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.home.FirstrunAgreementRepository
import com.qimian233.ztool.data.home.FirstrunCheckState
import com.qimian233.ztool.data.home.FirstrunPageSchema
import com.qimian233.ztool.data.home.FirstrunSchemaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seconds the report page keeps its continue button disabled on first display. */
private const val REPORT_COUNTDOWN_SECONDS = 5

class FirstrunAgreementViewModel(
    private val repository: FirstrunAgreementRepository,
    private val agreementRepository: AgreementRepository,
    private val schemaRepository: FirstrunSchemaRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FirstrunAgreementUiState())
    val uiState: StateFlow<FirstrunAgreementUiState> = _uiState.asStateFlow()

    private var reportCountdownJob: Job? = null

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
     * Starts the report page countdown once per process. A finished countdown stays
     * finished, so returning to the page never replays it within the app lifetime.
     */
    fun startReportCountdown() {
        if (_uiState.value.reportCountdownFinished || reportCountdownJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            reportCountdownRemaining = REPORT_COUNTDOWN_SECONDS
        )
        reportCountdownJob = viewModelScope.launch {
            repeat(REPORT_COUNTDOWN_SECONDS) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    reportCountdownRemaining = _uiState.value.reportCountdownRemaining - 1
                )
            }
            _uiState.value = _uiState.value.copy(reportCountdownFinished = true)
        }
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
    val agreementMarkdown: String = "",
    val reportCountdownRemaining: Int = REPORT_COUNTDOWN_SECONDS,
    val reportCountdownFinished: Boolean = false
)
