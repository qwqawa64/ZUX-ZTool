package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context

/**
 * Resolves this module's own user-visible strings inside the host process.
 *
 * Hook-side text lives in `strings.xml`; each `FALLBACK_*` literal must stay
 * character-for-character identical to the matching resource.
 */
internal object SidebarStrings {

    private const val MODULE_PACKAGE = "com.qimian233.ztool"

    const val STRING_EDIT = "ztool_sidebar_drawer_edit"
    const val STRING_DONE = "ztool_sidebar_drawer_done"
    const val STRING_MOVE_UP = "ztool_sidebar_drawer_move_up"
    const val STRING_MOVE_DOWN = "ztool_sidebar_drawer_move_down"
    const val STRING_REMOVE = "ztool_sidebar_drawer_remove"
    const val STRING_GLYPH_UP = "ztool_sidebar_drawer_glyph_up"
    const val STRING_GLYPH_DOWN = "ztool_sidebar_drawer_glyph_down"
    const val STRING_GLYPH_REMOVE = "ztool_sidebar_drawer_glyph_remove"

    const val FALLBACK_EDIT = "编辑"
    const val FALLBACK_DONE = "完成"
    const val FALLBACK_MOVE_UP = "上移"
    const val FALLBACK_MOVE_DOWN = "下移"
    const val FALLBACK_REMOVE = "移除"
    const val FALLBACK_GLYPH_UP = "▲"
    const val FALLBACK_GLYPH_DOWN = "▼"
    const val FALLBACK_GLYPH_REMOVE = "✕"

    fun get(hostContext: Context, resourceName: String, fallback: String): String = try {
        val moduleContext = hostContext.createPackageContext(
            MODULE_PACKAGE,
            Context.CONTEXT_IGNORE_SECURITY
        )
        val id = moduleContext.resources.getIdentifier(resourceName, "string", MODULE_PACKAGE)
        if (id != 0) moduleContext.resources.getString(id) else fallback
    } catch (_: Throwable) {
        fallback
    }
}
