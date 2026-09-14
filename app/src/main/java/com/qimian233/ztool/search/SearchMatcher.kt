package com.qimian233.ztool.search

import android.content.Context
import java.util.Locale

/**
 * Whitespace-token AND matcher. Every query token must hit the entry's title,
 * summary or keywords; the returned score encodes where the strongest hit was
 * found (title-prefix > title-substring > summary > keyword). No pinyin support
 * in phase 1 — matching is plain substring on the current-locale strings.
 */
object SearchMatcher {

    private const val SCORE_TITLE_PREFIX = 400
    private const val SCORE_TITLE_CONTAINS = 300
    private const val SCORE_SUMMARY_CONTAINS = 200
    private const val SCORE_KEYWORD_CONTAINS = 100

    fun score(
        query: String,
        entry: SearchEntry,
        context: Context,
        locale: Locale
    ): Int? {
        val tokens = query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.lowercase(locale) }
        if (tokens.isEmpty()) return null

        val title = context.getString(entry.titleRes).lowercase(locale)
        val summary = entry.summaryRes
            ?.let { context.getString(it).lowercase(locale) }
            .orEmpty()
        val keywords = entry.keywords.joinToString(separator = " ") { it.lowercase(locale) }

        var total = 0
        for (token in tokens) {
            val tokenScore = when {
                title.startsWith(token) -> SCORE_TITLE_PREFIX
                title.contains(token) -> SCORE_TITLE_CONTAINS
                summary.contains(token) -> SCORE_SUMMARY_CONTAINS
                keywords.contains(token) -> SCORE_KEYWORD_CONTAINS
                else -> return null
            }
            total += tokenScore
        }
        return total
    }
}
