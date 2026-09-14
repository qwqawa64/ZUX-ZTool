package com.qimian233.ztool.search

import android.content.Context
import com.github.promeg.pinyinhelper.Pinyin
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Whitespace-token AND matcher. Every query token must hit the entry's title,
 * summary, keywords or pinyin fields; the returned score encodes where the strongest
 * hit was found (title-prefix > title-substring > summary > keyword > pinyin-full >
 * pinyin-initials). Chinese titles gain pinyin matches ("ztl" → 状态栏) via
 * TinyPinyin; ASCII-only titles are skipped cheaply.
 */
object SearchMatcher {

    private const val SCORE_TITLE_PREFIX = 400
    private const val SCORE_TITLE_CONTAINS = 300
    private const val SCORE_SUMMARY_CONTAINS = 200
    private const val SCORE_KEYWORD_CONTAINS = 100
    private const val SCORE_PINYIN_FULL = 60
    private const val SCORE_PINYIN_INITIALS = 40

    /** entry id -> (full pinyin, initials), computed once per process. */
    private val pinyinCache = ConcurrentHashMap<String, Pair<String, String>>()

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

        val title = context.getString(entry.titleRes)
        val lowerTitle = title.lowercase(locale)
        val summary = entry.summaryRes
            ?.let { context.getString(it).lowercase(locale) }
            .orEmpty()
        val keywords = entry.keywords.joinToString(separator = " ") { it.lowercase(locale) }
        val (pinyinFull, pinyinInitials) = pinyinFor(entry.id, title)

        var total = 0
        for (token in tokens) {
            val tokenScore = when {
                lowerTitle.startsWith(token) -> SCORE_TITLE_PREFIX
                lowerTitle.contains(token) -> SCORE_TITLE_CONTAINS
                summary.contains(token) -> SCORE_SUMMARY_CONTAINS
                keywords.contains(token) -> SCORE_KEYWORD_CONTAINS
                pinyinFull.contains(token) -> SCORE_PINYIN_FULL
                pinyinInitials.contains(token) -> SCORE_PINYIN_INITIALS
                else -> return null
            }
            total += tokenScore
        }
        return total
    }

    private fun pinyinFor(id: String, title: String): Pair<String, String> {
        pinyinCache[id]?.let { return it }
        val computed = computePinyin(title)
        pinyinCache[id] = computed
        return computed
    }

    private fun computePinyin(title: String): Pair<String, String> {
        if (title.none { Pinyin.isChinese(it) }) return "" to ""
        val full = StringBuilder()
        val initials = StringBuilder()
        for (ch in title) {
            if (Pinyin.isChinese(ch)) {
                val py = Pinyin.toPinyin(ch).lowercase(Locale.ROOT)
                full.append(py)
                initials.append(py.firstOrNull() ?: ' ')
            } else if (ch.isLetterOrDigit()) {
                // Latin segments keep matching inside the full-pinyin string.
                full.append(ch.lowercaseChar())
            }
        }
        return full.toString() to initials.toString()
    }
}
