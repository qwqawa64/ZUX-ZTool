package com.qimian233.ztool.hook.modules.freeformbar

import android.view.View
import android.view.ViewGroup
import com.qimian233.ztool.hook.base.ModuleLog
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Drives the host's own `zui.widget.AlphabeticIndexBar`, which ships inside the all-apps layout
 * but is never initialised by the host.
 *
 * The grid is sorted by `PinYinUtils.toPinYin(userId + appName)`, whose user-id prefix is the
 * same for every entry, so grouping by the first letter of `toPinYin(appName)` follows the grid
 * order. The bar and the grid are reached by reflection: the app bundles its own copy of
 * `androidx.recyclerview`, and linking against ours would risk a duplicate class.
 */
internal class SidebarIndexBar(
    classLoader: ClassLoader,
    private val logger: ModuleLog
) {

    private val barClass: Class<*>? =
        runCatching { classLoader.loadClass("zui.widget.AlphabeticIndexBar") }.getOrNull()

    private val toPinYin: Method? = runCatching {
        classLoader.loadClass("com.zui.freeform.sidebar.utils.PinYinUtils")
            .getMethod("toPinYin", String::class.java)
    }.getOrNull()

    private val setValidAlphabetList: Method? = barClass?.methods
        ?.firstOrNull { it.name == "setValidAlphabetList" && it.parameterCount == 1 }

    private val setFocusedAlphabetListener: Method? = barClass?.methods
        ?.firstOrNull { it.name == "setOnFocusedAlphabetChangeListener" && it.parameterCount == 1 }

    /** True when the host's bar class and every method this class needs were resolved. */
    val available: Boolean
        get() = barClass != null && toPinYin != null && setValidAlphabetList != null &&
                setFocusedAlphabetListener != null

    /** Depth-first search for the bar, so no host resource id has to be hardcoded. */
    fun findBar(root: View): View? {
        val target = barClass ?: return null
        if (target.isInstance(root)) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            findBar(root.getChildAt(index))?.let { return it }
        }
        return null
    }

    /** Shows the rail for [apps] in grid order and scrolls [grid] to the tapped letter. */
    fun attach(bar: View, grid: View, apps: List<Any>) {
        val setter = setValidAlphabetList ?: return
        val listenerSetter = setFocusedAlphabetListener ?: return
        val letters = ArrayList<String>()
        val positions = ArrayList<Int>()
        apps.forEachIndexed { index, app ->
            val letter = letterOf(app)
            if (letters.lastOrNull() != letter) {
                letters.add(letter)
                positions.add(index)
            }
        }
        if (letters.isEmpty()) return
        val listenerType = listenerSetter.parameterTypes[0]
        runCatching { setter.invoke(bar, letters) }
            .onFailure { logger.warn("index bar rejected the alphabet list: " + it.message) }
        runCatching { listenerSetter.invoke(bar, listener(bar, grid, letters, positions, listenerType)) }
            .onFailure { logger.warn("index bar rejected the listener: " + it.message) }
        bar.visibility = View.VISIBLE
        logger.info("index bar attached with " + letters.size + " letters")
    }

    private fun listener(
        bar: View,
        grid: View,
        letters: List<String>,
        positions: List<Int>,
        listenerType: Class<*>
    ): Any = Proxy.newProxyInstance(bar.javaClass.classLoader, arrayOf(listenerType)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "ZToolAlphabeticIndexListener"
            else -> {
                val letter = args?.firstOrNull { it is String } as? String
                val index = letters.indexOf(letter)
                if (index >= 0) scrollTo(grid, positions[index])
                null
            }
        }
    }

    private fun letterOf(app: Any): String {
        val name = SidebarReflect.labelOf(app) ?: return OTHER
        val pinyin = runCatching { toPinYin?.invoke(null, name) as? String }.getOrNull() ?: name
        val first = pinyin.firstOrNull()?.uppercaseChar() ?: return OTHER
        return if (first in 'A'..'Z') first.toString() else OTHER
    }

    private fun scrollTo(grid: View, position: Int) {
        runCatching { grid.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType) }
            .onSuccess { it.invoke(grid, position) }
            .onFailure { logger.warn("could not scroll the all-apps grid: " + it.message) }
    }

    private companion object {
        const val OTHER = "#"
    }
}
