package com.qimian233.ztool.ui.firstrun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

interface AgreementReadGate {
    val satisfied: Boolean
    val description: String

    fun onScrollChanged(canScrollForward: Boolean)
    fun reset()
}

class ScrollToBottomAgreementGate : AgreementReadGate {
    override var satisfied by mutableStateOf(false)
        private set

    override val description: String
        get() = "Scroll to the bottom to unlock agreement acceptance"

    override fun onScrollChanged(canScrollForward: Boolean) {
        if (!canScrollForward) {
            satisfied = true
        }
    }

    override fun reset() {
        satisfied = false
    }
}
