package com.qimian233.ztool

import com.qimian233.ztool.data.launcher.ExternalGridInputs
import com.qimian233.ztool.data.launcher.GridCounts
import com.qimian233.ztool.data.launcher.GridSource
import com.qimian233.ztool.data.launcher.LauncherGridResolver
import com.qimian233.ztool.data.launcher.ProviderLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the preview grid resolution precedence and the launcher layout-name format
 * (`<columns>x<rows>`, optionally prefixed).
 */
class LauncherPreviewGridResolverTest {

    @Test
    fun `layout names parse as columns then rows`() {
        assertEquals(GridCounts(6, 4), LauncherGridResolver.parseLayoutName("6x4"))
        assertEquals(GridCounts(5, 6), LauncherGridResolver.parseLayoutName("5x6"))
        assertEquals(GridCounts(4, 5), LauncherGridResolver.parseLayoutName("4×5"))
        assertEquals(GridCounts(6, 4), LauncherGridResolver.parseLayoutName("learning_6x4"))
        assertEquals(GridCounts(6, 5), LauncherGridResolver.parseLayoutName("variant_2024_6x5"))
    }

    @Test
    fun `layout names without a usable pair are rejected`() {
        assertNull(LauncherGridResolver.parseLayoutName(null))
        assertNull(LauncherGridResolver.parseLayoutName(""))
        assertNull(LauncherGridResolver.parseLayoutName("fixed_landscape_mode"))
        assertNull(LauncherGridResolver.parseLayoutName("99x4"))
        assertNull(LauncherGridResolver.parseLayoutName("0x4"))
    }

    @Test
    fun `custom grid wins while the switch is on`() {
        val resolved = LauncherGridResolver.resolve(
            customGridEnabled = true,
            customColumns = 8,
            customRows = 6,
            external = externalOf(
                name = "6x4",
                layouts = listOf(ProviderLayout("6x4", 4, 6, isDefault = true))
            )
        )
        assertEquals(GridCounts(8, 6), resolved.counts)
        assertEquals(GridSource.CUSTOM_GRID, resolved.source)
    }

    @Test
    fun `degenerate provider catalog falls back to the layout name`() {
        val resolved = LauncherGridResolver.resolve(
            customGridEnabled = false,
            customColumns = 8,
            customRows = 6,
            // Hook-polluted catalog: every layout reports the same grid.
            external = externalOf(
                name = "6x4",
                layouts = listOf(
                    ProviderLayout("6x4", 8, 6, isDefault = true),
                    ProviderLayout("5x4", 8, 6, isDefault = true)
                )
            )
        )
        assertEquals(GridCounts(6, 4), resolved.counts)
        assertEquals(GridSource.LAYOUT_NAME, resolved.source)
    }

    @Test
    fun `trustworthy provider catalog supplies the current grid`() {
        val resolved = LauncherGridResolver.resolve(
            customGridEnabled = false,
            customColumns = 8,
            customRows = 6,
            external = externalOf(
                name = "5x6",
                layouts = listOf(
                    ProviderLayout("6x4", 6, 4, isDefault = false),
                    ProviderLayout("5x6", 5, 6, isDefault = true)
                )
            )
        )
        assertEquals(GridCounts(5, 6), resolved.counts)
        assertEquals(GridSource.PROVIDER, resolved.source)
    }

    @Test
    fun `provider name is used when the system mirror is empty`() {
        val resolved = LauncherGridResolver.resolve(
            customGridEnabled = false,
            customColumns = 8,
            customRows = 6,
            external = externalOf(
                name = null,
                layouts = listOf(ProviderLayout("6x5", 6, 5, isDefault = true))
            )
        )
        assertEquals(GridCounts(6, 5), resolved.counts)
        assertEquals(GridSource.LAYOUT_NAME, resolved.source)
    }

    @Test
    fun `no source at all yields the fallback`() {
        val resolved = LauncherGridResolver.resolve(
            customGridEnabled = false,
            customColumns = 8,
            customRows = 6,
            external = ExternalGridInputs(systemLayoutName = null, providerLayouts = emptyList())
        )
        assertEquals(LauncherGridResolver.FALLBACK, resolved.counts)
        assertEquals(GridSource.FALLBACK, resolved.source)
    }

    private fun externalOf(name: String?, layouts: List<ProviderLayout>) =
        ExternalGridInputs(systemLayoutName = name, providerLayouts = layouts)
}
