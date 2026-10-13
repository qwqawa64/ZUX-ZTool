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
    const val STRING_GLYPH_ADD = "ztool_sidebar_drawer_glyph_add"
    const val STRING_GLYPH_DROP = "ztool_sidebar_drawer_glyph_drop"
    const val STRING_ADD = "ztool_sidebar_drawer_add"
    const val STRING_DROP = "ztool_sidebar_drawer_drop"
    const val STRING_SEARCH_HINT = "ztool_sidebar_allapps_search_hint"
    const val STRING_EMPTY = "ztool_sidebar_allapps_empty_message"

    const val FALLBACK_EDIT = "编辑"
    const val FALLBACK_DONE = "完成"
    const val FALLBACK_GLYPH_ADD = "+"
    const val FALLBACK_GLYPH_DROP = "−"
    const val FALLBACK_ADD = "加入抽屉"
    const val FALLBACK_DROP = "移出抽屉"
    const val FALLBACK_SEARCH_HINT = "搜索应用"
    const val FALLBACK_EMPTY = "没有匹配的应用"

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
