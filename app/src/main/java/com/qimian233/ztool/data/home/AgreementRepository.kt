package com.qimian233.ztool.data.home

import android.content.Context
import com.qimian233.ztool.R
import java.io.File

class AgreementRepository(context: Context) {
    private val appContext = context.applicationContext
    private val agreementFile = File(appContext.noBackupFilesDir, AGREEMENT_FILE_NAME)
    private val agreementMarkdown by lazy { loadAgreementMarkdownInternal() }
    private val currentAgreementVersionValue by lazy { parseAgreementVersion(agreementMarkdown) }

    fun getCurrentAgreementVersion(): String {
        return currentAgreementVersionValue
    }

    fun getAcceptedAgreementVersion(): String? {
        readAgreementState()?.let { return it }
        val legacyAccepted = readLegacyAgreementState() ?: return null
        return if (legacyAccepted) {
            LEGACY_AGREEMENT_VERSION.also { markAgreementAccepted(it) }
        } else {
            null
        }
    }

    fun markAgreementAccepted(version: String = currentAgreementVersionValue) {
        agreementFile.parentFile?.mkdirs()
        agreementFile.writeText("$STATE_PREFIX$version")
    }

    fun hasAcceptedAgreement(): Boolean = getAcceptedAgreementVersion() != null

    /**
     * Migration input for [FirstrunSchemaRepository]: whether the legacy
     * accepted agreement version covers the packaged markdown, i.e. the user
     * does not owe an agreement re-read under the legacy versioning scheme.
     */
    fun isAcceptedVersionCurrent(): Boolean {
        val accepted = getAcceptedAgreementVersion() ?: return false
        return compareVersions(accepted, currentAgreementVersionValue) >= 0
    }

    fun loadAgreementMarkdown(): String {
        return agreementMarkdown
    }

    private fun loadAgreementMarkdownInternal(): String {
        appContext.resources.openRawResource(R.raw.agreement).bufferedReader(Charsets.UTF_8).use {
            return it.readText().trim()
        }
    }

    private fun readAgreementState(): String? {
        if (!agreementFile.exists()) return null
        val storedValue = agreementFile.readText().trim().lineSequence()
            .firstOrNull { it.startsWith(STATE_PREFIX) }
            ?.substringAfter(STATE_PREFIX, missingDelimiterValue = "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return when {
            storedValue.equals("true", ignoreCase = true) -> LEGACY_AGREEMENT_VERSION
            storedValue.equals("false", ignoreCase = true) -> null
            AGREEMENT_VERSION_VALUE_PATTERN.matches(storedValue) -> storedValue
            else -> null
        }
    }

    private fun readLegacyAgreementState(): Boolean? {
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
        if (!legacyPrefs.contains(LEGACY_FIRST_LAUNCH_KEY)) return null
        return !legacyPrefs.getBoolean(LEGACY_FIRST_LAUNCH_KEY, true)
    }

    private fun parseAgreementVersion(markdown: String): String {
        AGREEMENT_VERSION_PATTERN.find(markdown)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return DEFAULT_AGREEMENT_VERSION
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }

    companion object {
        private const val AGREEMENT_FILE_NAME = "agreement_state.txt"
        private const val STATE_PREFIX = "accepted="
        private const val LEGACY_PREF_NAME = "ZToolPrefs"
        private const val LEGACY_FIRST_LAUNCH_KEY = "isFirstLaunch"
        private const val LEGACY_AGREEMENT_VERSION = "0.2"
        private const val DEFAULT_AGREEMENT_VERSION = "1.0"
        private val AGREEMENT_VERSION_PATTERN = Regex(
            """(?:协议版本|Agreement version)\s*[:：]\s*([0-9]+(?:\.[0-9]+)*)"""
        )
        private val AGREEMENT_VERSION_VALUE_PATTERN = Regex("""[0-9]+(?:\.[0-9]+)*""")
    }
}
