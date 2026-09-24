package com.qimian233.ztool

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static consistency check between the search index (SearchIndex.kt) and the keys
 * actually declared on the screens. Guards against index drift when a setting row or
 * a feature entry is added or moved without updating the index.
 *
 * This is regex-level analysis of the Kotlin sources (no compilation), so the rules
 * are intentionally simple:
 *  - every rendered `key = "..."` outside the exemptions must exist in the index;
 *  - every index entry's id must appear as a rendered key, except feature cards
 *    (rendered as cards on the Features grid, not as keyed SettingItems).
 * Route-level correctness is covered at runtime by SearchIndexAudit on debug builds.
 */
class SearchIndexConsistencyTest {

    private val appDir: File = locateAppDir()

    private val renderedKeys: Set<String> by lazy {
        val screensDir = File(appDir, "src/main/java/com/qimian233/ztool/screens")
        val uiDir = File(appDir, "src/main/java/com/qimian233/ztool/ui")
        (screensDir.walkTopDown() + uiDir.walkTopDown())
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { KEY_REGEX.findAll(it.readText()).map { m -> m.groupValues[2] } }
            .toSet()
    }

    /**
     * Parses SearchIndex.kt into (id, isFeatureCard) records. The index declares two
     * shapes: full `SearchEntry(...)` blocks (feature cards) and `screen.item(...)`
     * helpers (everything else), so the source is split on both block starters and
     * each block is checked for an id and a feature flag.
     */
    private val indexEntries: List<IndexRecord> by lazy {
        val source = File(appDir, "src/main/java/com/qimian233/ztool/search/SearchIndex.kt").readText()
        val blocks = source.split("SearchEntry(", ".item(")
        blocks.drop(1).mapNotNull { block ->
            val id = ID_REGEX.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            // Only the portion up to the closing paren of this entry belongs to it;
            // a lazy scan to the first ")" that closes the argument list is close
            // enough because parameter values never contain ")\n".
            val entryBody = block.substringBefore(")\n")
            IndexRecord(
                id = id,
                isFeatureCard = entryBody.contains("isFeatureCard = true")
            )
        }
    }

    @Test
    fun `every rendered key is registered in the search index`() {
        val indexIds = indexEntries.map { it.id }.toSet()
        val unindexed = renderedKeys
            .filterNot { it.startsWith(EXEMPT_PREFIX) }
            .filterNot { it.startsWith(HOME_PREFIX) }
            .filterNot { it.startsWith(DYN_PREFIX) }
            .filterNot { SUB_ROW_SUFFIXES.any { sfx -> it.endsWith(sfx) } }
            .filterNot { it in indexIds }
        assertTrue(
            "Rendered keys missing from SearchIndex (register a SearchEntry or use the " +
                "$EXEMPT_PREFIX prefix for decorative rows): $unindexed",
            unindexed.isEmpty()
        )
    }

    @Test
    fun `every index entry is rendered or is a feature card`() {
        val missing = indexEntries
            .filterNot { it.isFeatureCard }
            .map { it.id }
            .filterNot { it in renderedKeys }
        assertTrue(
            "Indexed entries with no rendered key (moved or renamed without updating " +
                "SearchIndex, or the screen row lost its key): $missing",
            missing.isEmpty()
        )
    }

    private data class IndexRecord(val id: String, val isFeatureCard: Boolean)

    private companion object {
        const val EXEMPT_PREFIX = "deco_"
        const val HOME_PREFIX = "home_"
        const val DYN_PREFIX = "dyn_"
        // Sub-rows (text inputs, pickers, config fields) that belong to an indexed
        // parent row and are not searchable on their own.
        val SUB_ROW_SUFFIXES = listOf("_input", "_config", "_picker")
        val KEY_REGEX = Regex("""(key|highlightKey)\s*=\s*"([a-zA-Z0-9_]+)"""")
        val ID_REGEX = Regex("""id\s*=\s*"([a-zA-Z0-9_]+)"""")

        fun locateAppDir(): File {
            var dir = File(System.getProperty("user.dir"))
            repeat(4) {
                if (File(dir, "src/main/java/com/qimian233/ztool/search/SearchIndex.kt").isFile) {
                    return dir
                }
                dir = dir.parentFile ?: return@repeat
            }
            error("Could not locate the app module directory from ${System.getProperty("user.dir")}")
        }
    }
}
