package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.drawable.Drawable

/** Resolves this module's own drawables inside the host process. */
internal object SidebarIcons {

    private const val MODULE_PACKAGE = "com.qimian233.ztool"

    const val EDIT = "ztool_ic_edit"
    const val DONE = "ztool_ic_done"

    fun get(hostContext: Context, resourceName: String): Drawable? = try {
        val moduleContext = hostContext.createPackageContext(
            MODULE_PACKAGE,
            Context.CONTEXT_IGNORE_SECURITY
        )
        val id = moduleContext.resources.getIdentifier(resourceName, "drawable", MODULE_PACKAGE)
        if (id != 0) moduleContext.resources.getDrawable(id, moduleContext.theme) else null
    } catch (_: Throwable) {
        null
    }
}
