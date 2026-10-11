package com.qimian233.ztool.hook.modules.freeformbar

import android.graphics.drawable.Drawable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Reflection bridge to the host's `utils.AppInfo`, which is not on this module's compile
 * classpath. Method lookups are cached because the adapter calls these per bound row.
 */
internal object SidebarReflect {

    private val methodCache = ConcurrentHashMap<String, Method?>()

    fun call(target: Any, name: String): Any? {
        val method = methodCache.computeIfAbsent(name) {
            runCatching { target.javaClass.getMethod(name) }.getOrNull()
        } ?: return null
        return runCatching { method.invoke(target) }.getOrNull()
    }

    fun keyOf(app: Any): String? {
        val pkg = call(app, "getPkgName") as? String ?: return null
        val user = call(app, "getUser") ?: return null
        val id = call(user, "getIdentifier") as? Int ?: return null
        return "$pkg:$id"
    }

    fun labelOf(app: Any): String? = call(app, "getAppName") as? String

    fun iconOf(app: Any): Drawable? = call(app, "getAppIcon") as? Drawable
}
