package com.qimian233.ztool.hook.modules.freeformbar

import java.lang.reflect.Method

/**
 * Row model behind the all-apps grid: the host's pinyin-sorted app list with one letter heading in
 * front of every group, optionally narrowed by a search query.
 *
 * A row keeps the index of its app in the host's own list, because the host still binds and
 * launches by that index. Letter, pinyin and pinyin initials are derived once per app from the
 * host's `PinYinUtils`, so the sections cannot disagree with the order the host sorted.
 */
internal class SidebarAllAppsModel(private val toPinYin: Method?) {

    internal class Row(val letter: String?, val app: Any?, val hostIndex: Int)

    private class Meta(val label: String, val pinyin: String, val initials: String, val letter: String)

    private val cache = HashMap<String, Meta>()
    private var rows: List<Row> = emptyList()
    private var letterByRow: IntArray = IntArray(0)

    /** Letter to the row index of its heading, in grid order. */
    var groups: List<Pair<String, Int>> = emptyList()
        private set

    val size: Int get() = rows.size
    val isEmpty: Boolean get() = rows.isEmpty()

    /**
     * Rebuilds the rows for [apps] filtered by [query]; an empty query keeps every app.
     *
     * With [sections] off the letter headings are left out, but the groups are still computed so
     * the A–Z rail keeps working on a plain grid.
     */
    fun submit(apps: List<Any>, query: String, sections: Boolean) {
        val needle = query.trim()
        val built = ArrayList<Row>(apps.size + 8)
        val letters = ArrayList<Pair<String, Int>>()
        val rowLetters = ArrayList<Int>(apps.size + 8)
        var lastLetter: String? = null
        apps.forEachIndexed { index, app ->
            if (needle.isNotEmpty() && !matches(app, needle)) return@forEachIndexed
            val meta = metaOf(app)
            if (meta.letter != lastLetter) {
                lastLetter = meta.letter
                letters.add(meta.letter to built.size)
                if (sections) {
                    rowLetters.add(letters.size - 1)
                    built.add(Row(meta.letter, null, -1))
                }
            }
            rowLetters.add(letters.size - 1)
            built.add(Row(null, app, index))
        }
        rows = built
        letterByRow = rowLetters.toIntArray()
        groups = letters
    }

    /** The app shown at [position], or null when that row is a heading. */
    fun appAt(position: Int): Any? = rows.getOrNull(position)?.app

    /** The index of [position]'s app in the host's own list, or -1 for a heading row. */
    fun hostIndexOf(position: Int): Int = rows.getOrNull(position)?.hostIndex ?: -1

    fun isHeader(position: Int): Boolean = rows.getOrNull(position)?.letter != null

    fun headerAt(position: Int): String? = rows.getOrNull(position)?.letter

    /** The letter of the group [position] belongs to, for the rail's scroll highlight. */
    fun letterAt(position: Int): String? {
        val group = letterByRow.getOrNull(position) ?: return null
        return groups.getOrNull(group)?.first
    }

    /** Drops the derived metadata; call when the host's app list itself changed. */
    fun forget() = cache.clear()

    private fun matches(app: Any, needle: String): Boolean {
        val meta = metaOf(app)
        return meta.label.contains(needle, true) ||
            meta.pinyin.contains(needle, true) ||
            meta.initials.contains(needle, true)
    }

    private fun metaOf(app: Any): Meta {
        val key = SidebarReflect.keyOf(app)
        if (key != null) cache[key]?.let { return it }
        val label = SidebarReflect.labelOf(app).orEmpty()
        val pinyin = syllableOf(label)
        val initials = StringBuilder(label.length)
        for (character in label) {
            // Latin names are already matched by the label itself, so the initials only have to
            // add the first letter of every non-Latin character.
            if (character.isLetterOrDigit() && character.code < 128) {
                initials.append(character)
            } else {
                syllableOf(character.toString()).firstOrNull { it.isLetterOrDigit() }
                    ?.let { initials.append(it) }
            }
        }
        val first = pinyin.firstOrNull()?.uppercaseChar()
        val letter = if (first != null && first in 'A'..'Z') first.toString() else OTHER
        val meta = Meta(label, pinyin, initials.toString(), letter)
        if (key != null) cache[key] = meta
        return meta
    }

    private fun syllableOf(text: String): String =
        runCatching { toPinYin?.invoke(null, text) as? String }.getOrNull() ?: text

    private companion object {
        const val OTHER = "#"
    }
}
