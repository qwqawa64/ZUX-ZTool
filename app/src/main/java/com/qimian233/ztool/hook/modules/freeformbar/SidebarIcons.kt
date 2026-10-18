package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.drawable.Drawable

/** Resolves this module's own drawables inside the host process. */
internal object SidebarIcons {

    private const val MODULE_PACKAGE = "com.qimian233.ztool"

    const val EDIT = "ztool_ic_edit"
    const val DONE = "ztool_ic_done"

    /** Loads [resourceName] tinted to [tint], so a white glyph also works on a light panel. */
    fun get(hostContext: Context, resourceName: String, tint: Int): Drawable? = try {
        val moduleContext = hostContext.createPackageContext(
            MODULE_PACKAGE,
            Context.CONTEXT_IGNORE_SECURITY
        )
        val id = moduleContext.resources.getIdentifier(resourceName, "drawable", MODULE_PACKAGE)
        if (id != 0) {
            moduleContext.resources.getDrawable(id, moduleContext.theme)
                ?.mutate()
                ?.apply { setTint(tint) }
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }
}
