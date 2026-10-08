package com.qimian233.ztool.data.launcher

/** Column/row counts of one desktop grid layout. */
data class GridCounts(val columns: Int, val rows: Int)

/** One layout entry reported by the launcher's `grid_control` provider. */
data class ProviderLayout(
    val name: String,
    val columns: Int,
    val rows: Int,
    val isDefault: Boolean
)

/** Launcher-side facts combined with ZTool's own custom grid settings. */
data class ExternalGridInputs(
    val systemLayoutName: String?,
    val providerLayouts: List<ProviderLayout>
)

/** Where a resolved grid came from; used for logging only, never rendered. */
enum class GridSource { CUSTOM_GRID, PROVIDER, LAYOUT_NAME, FALLBACK }

/** Resolved desktop grid plus its origin. */
data class ResolvedGrid(val counts: GridCounts, val source: GridSource)

/**
 * Resolves the desktop grid the preview should draw.
 *
 * Precedence: ZTool's custom grid (the grid hook forces it on-device), then the
 * provider's numbers - only while its catalog is non-degenerate - then the layout
 * name parsed as `<columns>x<rows>`, then [FALLBACK].
 */
object LauncherGridResolver {

    /** Layout names look like `6x4`, optionally prefixed (e.g. `learning_6x4`). */
    private val NAME_PATTERN = Regex("""(\d{1,2})\s*[x×]\s*(\d{1,2})""")

    private const val MIN_TRACKS = 2
    private const val MAX_TRACKS = 12

    /** Mirrors `LauncherSettingsRepository`'s default row/column pair. */
    val FALLBACK = GridCounts(columns = 6, rows = 4)

    /** Parses `<columns>x<rows>`; null when [name] carries no usable pair. */
    fun parseLayoutName(name: String?): GridCounts? {
        if (name.isNullOrBlank()) return null
        val match = NAME_PATTERN.findAll(name).lastOrNull() ?: return null
        return sanitize(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    fun resolve(
        customGridEnabled: Boolean,
        customColumns: Int,
        customRows: Int,
        external: ExternalGridInputs
    ): ResolvedGrid {
        if (customGridEnabled) {
            sanitize(customColumns, customRows)?.let {
                return ResolvedGrid(it, GridSource.CUSTOM_GRID)
            }
        }
        val current = external.providerLayouts.firstOrNull { it.isDefault }
        if (isCatalogTrustworthy(external.providerLayouts)) {
            current?.let { sanitize(it.columns, it.rows) }?.let {
                return ResolvedGrid(it, GridSource.PROVIDER)
            }
        }
        val byName = parseLayoutName(external.systemLayoutName) ?: parseLayoutName(current?.name)
        if (byName != null) return ResolvedGrid(byName, GridSource.LAYOUT_NAME)
        return ResolvedGrid(FALLBACK, GridSource.FALLBACK)
    }

    /**
     * The provider reports one grid for every layout name while ZTool's grid hook is
     * active, so identical counts across entries mean the numbers cannot be trusted.
     */
    private fun isCatalogTrustworthy(layouts: List<ProviderLayout>): Boolean =
        layouts.map { it.columns to it.rows }.distinct().size > 1

    private fun sanitize(columns: Int, rows: Int): GridCounts? =
        if (columns in MIN_TRACKS..MAX_TRACKS && rows in MIN_TRACKS..MAX_TRACKS) {
            GridCounts(columns, rows)
        } else {
            null
        }
}
