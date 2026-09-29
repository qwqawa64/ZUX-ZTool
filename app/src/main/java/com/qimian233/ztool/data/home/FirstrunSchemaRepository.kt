package com.qimian233.ztool.data.home

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Persists which first-run pages the user has accepted, at which schema
 * version. One line per page ("PAGE_NAME=version") inside noBackupFilesDir —
 * deliberately NOT a SharedPreferences key: the state must never enter adb
 * backup or the in-app config export, otherwise a restored acceptance would
 * silently skip pages the user never actually saw (see [FirstrunPageSchema]).
 */
class FirstrunSchemaRepository(
    context: Context,
    private val agreementRepository: AgreementRepository
) {
    private val stateFile = File(context.applicationContext.noBackupFilesDir, STATE_FILE_NAME)

    data class Status(
        /** Whether the user ever accepted any first-run content (legacy agreement counts). */
        val everAcceptedAny: Boolean,
        /** Accepted schema version per page; pages missing from the map are not yet accepted. */
        val acceptedVersions: Map<FirstrunPageSchema, Int>
    )

    /**
     * Reads the accepted state, transparently migrating the legacy
     * agreement-only state on first call: installs that predate the schema
     * registry have no per-page record, so every page but a still-current
     * agreement is treated as unaccepted and gets replayed once.
     */
    fun loadStatus(): Status {
        if (stateFile.exists()) {
            return Status(everAcceptedAny = true, acceptedVersions = parseStateFile())
        }
        if (!agreementRepository.hasAcceptedAgreement()) {
            return Status(everAcceptedAny = false, acceptedVersions = emptyMap())
        }
        val accepted = if (agreementRepository.isAcceptedVersionCurrent()) {
            mapOf(FirstrunPageSchema.AGREEMENT to FirstrunPageSchema.AGREEMENT.schemaVersion)
        } else {
            emptyMap()
        }
        return Status(everAcceptedAny = true, acceptedVersions = accepted)
    }

    fun markPageAccepted(page: FirstrunPageSchema) {
        val merged = parseStateFile().toMutableMap()
        merged[page] = page.schemaVersion
        writeStateFile(merged)
    }

    /** Records every registered page as accepted at its current schema version. */
    fun markAllAccepted() {
        writeStateFile(FirstrunPageSchema.entries.associateWith { it.schemaVersion })
    }

    private fun parseStateFile(): Map<FirstrunPageSchema, Int> {
        if (!stateFile.exists()) return emptyMap()
        // A failed read degrades to "everything stale" (replay), never a crash.
        val content = runCatching { stateFile.readText() }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<FirstrunPageSchema, Int>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val page = FirstrunPageSchema.entries.firstOrNull { it.name == trimmed.substringBefore('=').trim() }
            val version = trimmed.substringAfter('=', "").trim().toIntOrNull()
            if (page != null && version != null) result[page] = version
        }
        return result
    }

    private fun writeStateFile(state: Map<FirstrunPageSchema, Int>) {
        // Acceptance is bookkeeping, not a gate: if the disk rejects the write,
        // let the flow finish — the pages simply replay on the next launch.
        runCatching {
            stateFile.parentFile?.mkdirs()
            stateFile.writeText(
                state.entries.sortedBy { it.key.order }
                    .joinToString("\n") { "${it.key.name}=${it.value}" }
            )
        }.onFailure { Log.w(TAG, "Failed to persist firstrun schema state", it) }
    }

    private companion object {
        const val TAG = "FirstrunSchemaRepo"
        const val STATE_FILE_NAME = "firstrun_schema_state.txt"
    }
}
