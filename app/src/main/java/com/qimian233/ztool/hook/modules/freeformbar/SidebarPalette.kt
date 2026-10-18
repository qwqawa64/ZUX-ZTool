package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

/**
 * Colours for this module's own chrome, resolved from the host's current theme.
 *
 * Fills are the host's text colour at a low alpha, so one palette serves a light, dark or themed
 * panel. See docs/research/zux_freeform_bar_sidebar.md.
 */
internal class SidebarPalette(
    val textPrimary: Int,
    val textHint: Int,
    val header: Int,
    val fieldFill: Int,
    val slotFill: Int,
    val buttonFill: Int,
    val buttonFillPressed: Int,
    val pressHighlight: Int,
    val railInactive: Int,
    val railFocusedText: Int,
    val railFocusFill: Int
) {
    companion object {
        /** Resolves the palette against the theme the host's panel is currently drawn with. */
        fun of(context: Context, hostPackage: String): SidebarPalette {
            val resources = context.resources
            val theme = context.theme
            val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val primary = visible(hostColor(resources, theme, hostPackage, GRID_NAME_COLOR))
                ?.let(::opaque)
                ?: if (night) ON_DARK else ON_LIGHT
            return SidebarPalette(
                textPrimary = primary,
                textHint = withAlpha(primary, 0x80),
                header = withAlpha(primary, 0xB3),
                fieldFill = withAlpha(primary, 0x1F),
                slotFill = withAlpha(primary, 0x14),
                buttonFill = withAlpha(primary, 0x33),
                buttonFillPressed = withAlpha(primary, 0x80),
                pressHighlight = withAlpha(primary, 0x33),
                railInactive = visible(hostColor(resources, theme, hostPackage, INDEX_TEXT_COLOR))
                    ?: withAlpha(primary, 0x99),
                railFocusedText =
                    visible(hostColor(resources, theme, hostPackage, INDEX_FOCUSED_TEXT))
                        ?: primary,
                railFocusFill =
                    visible(hostColor(resources, theme, hostPackage, INDEX_FOCUSED_FILL))
                        ?: withAlpha(primary, 0x59)
            )
        }

        /** The all-apps grid's own app-name colour: the panel's primary text colour. */
        private const val GRID_NAME_COLOR = "grid_pkg_name_color"

        /** The host index bar this rail replaces, so its letters keep the host's colours. */
        private const val INDEX_TEXT_COLOR = "alphabetic_index_bar_text_color"
        private const val INDEX_FOCUSED_TEXT = "alphabetic_index_bar_focused_text_color"
        private const val INDEX_FOCUSED_FILL = "alphabetic_index_bar_focused_background"

        /** Only used when the host colour is unreachable; mirrors ZUI's dark panel. */
        private const val ON_DARK = 0xFFFFFFFF.toInt()
        private const val ON_LIGHT = 0xDE000000.toInt()

        private fun hostColor(
            resources: Resources,
            theme: Resources.Theme,
            hostPackage: String,
            name: String
        ): Int? {
            val id = resources.getIdentifier(name, "color", hostPackage)
            if (id == 0) return null
            // Some ZUI colours are selectors; the default state is what the panel shows at rest.
            return runCatching { resources.getColorStateList(id, theme).defaultColor }.getOrNull()
        }

        private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

        /** A colour the host actually draws with, i.e. one that is not fully transparent. */
        private fun visible(color: Int?): Int? = color?.takeIf { (it ushr 24) != 0 }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
