package com.qimian233.ztool.data.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class AgreementRepository(context: Context) {
    private val appContext = context.applicationContext
    private val agreementFile = File(appContext.noBackupFilesDir, AGREEMENT_FILE_NAME)

    fun hasAcceptedAgreement(): Boolean {
        readAgreementState()?.let { return it }
        val legacyAccepted = readLegacyAgreementState() ?: return false
        if (legacyAccepted) {
            markAgreementAccepted()
        }
        return legacyAccepted
    }

    fun markAgreementAccepted() {
        agreementFile.parentFile?.mkdirs()
        agreementFile.writeText(ACCEPTED_STATE_CONTENT)
    }

    private fun readAgreementState(): Boolean? {
        if (!agreementFile.exists()) return null
        return agreementFile.readText().trim().lineSequence()
            .firstOrNull { it.startsWith(STATE_PREFIX) }
            ?.substringAfter(STATE_PREFIX, missingDelimiterValue = "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toBooleanStrictOrNull()
    }

    private fun readLegacyAgreementState(): Boolean? {
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
        if (!legacyPrefs.contains(LEGACY_FIRST_LAUNCH_KEY)) return null
        return !legacyPrefs.getBoolean(LEGACY_FIRST_LAUNCH_KEY, true)
    }

    companion object {
        private const val AGREEMENT_FILE_NAME = "agreement_state.txt"
        private const val ACCEPTED_STATE_CONTENT = "accepted=true"
        private const val STATE_PREFIX = "accepted="
        private const val LEGACY_PREF_NAME = "ZToolPrefs"
        private const val LEGACY_FIRST_LAUNCH_KEY = "isFirstLaunch"
    }
}
