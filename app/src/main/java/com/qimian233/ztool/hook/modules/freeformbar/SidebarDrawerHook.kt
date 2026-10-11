package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.provider.Settings
import android.widget.ListView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Sidebar drawer upgrade for `com.zui.freeform.sidebar`: lifts the host's hard-coded
 * 5-slot drawer cap and gives the drawer a user-editable order, driven by a trailing
 * 编辑 / 完成 row and per-row ▲ / ▼ / ✕ affordances.
 *
 * Drawer content is `recent freeform apps (N slots) + the user's order`, truncated to the
 * configured capacity. Verified against host 1.18.02.26.20.2; the obfuscated 1.18.5.x build
 * has none of these class names, so every lookup is guarded and a miss disables the module
 * instead of throwing.
 */
class SidebarDrawerHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SIDEBAR_DRAWER_ENHANCE.name

    override fun getTargetPackages(): Array<out String?>? =
        arrayOf(ScopeKeys.FREEFORM_SIDEBAR.packageName)

    private var appInfoListField: Field? = null
    private var generateLauncherApp: Method? = null
    private var visibleAppsList: Method? = null
    private var launchApp: Method? = null
    private var collectDataTop5: Method? = null
    private var showAllAppWindow: Method? = null
    private var appIconLvField: Field? = null
    private var appTop5ListField: Field? = null
    private var drawerLayoutField: Field? = null
    private var footViewField: Field? = null
    private var popupWindowField: Field? = null
    private var originalDrawerWidth: Int = 0

    private var adapter: SidebarDrawerAdapter? = null
    private var hostPackage: String = ScopeKeys.FREEFORM_SIDEBAR.packageName

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hostPackage = param.packageName

        val floatServiceClass = classLoader.loadClass("com.zui.freeform.sidebar.FloatService")
        val sidebarAppUtilsClass = classLoader.loadClass("com.zui.freeform.sidebar.utils.SidebarAppUtils")
        val appInfoClass = classLoader.loadClass("com.zui.freeform.sidebar.utils.AppInfo")

        // Version guard: the 1.18.5.x rewrite has none of these classes.
        runCatching { classLoader.loadClass("com.zui.freeform.sidebar.ui.AppIconListAdapter") }
            .onFailure {
                logger.info("host build is not the supported 1.18.02 layout, skipping")
                return
            }

        appInfoListField = findField(sidebarAppUtilsClass, "mLauncherAppInfoList")
        generateLauncherApp = findMethod(sidebarAppUtilsClass, "generateLauncherApp", Context::class.java)
        val generateTop5App = findMethod(sidebarAppUtilsClass, "generateTop5App", Context::class.java)
        visibleAppsList = findMethod(floatServiceClass, "getVisiableAppsList")
        launchApp = findMethod(floatServiceClass, "startActivity", appInfoClass)
        collectDataTop5 = findMethod(
            floatServiceClass, "collectDataTop5", Int::class.javaPrimitiveType, String::class.java
        )
        showAllAppWindow = findMethod(floatServiceClass, "showAllAppWindow")
        appIconLvField = findField(floatServiceClass, "mAppIconLv")
        appTop5ListField = findField(floatServiceClass, "mAppTop5List")
        drawerLayoutField = findField(floatServiceClass, "mDrawerLayout")
        footViewField = findField(floatServiceClass, "mFootView")
        popupWindowField = findField(floatServiceClass, "popupWindow")

        hookWithId(generateTop5App, "sidebar_drawer_top5") { chain ->
            val context = chain.args[0] as? Context
            if (context == null) return@hookWithId chain.proceed()
            runCatching { resolve(composedKeys(context)) }.getOrElse { chain.proceed() }
        }

        val onCreate = findMethod(floatServiceClass, "onCreate")
        hookWithId(onCreate, "sidebar_drawer_oncreate") { chain ->
            val result = chain.proceed()
            runCatching { installDrawer(chain.thisObject as Context) }
                .onFailure { logger.error("failed to install drawer adapter", it) }
            result
        }

        val drawerOpen = findDrawerOpenMethod(floatServiceClass)
        if (drawerOpen != null) {
            hookWithId(drawerOpen, "sidebar_drawer_open") { chain ->
                val result = chain.proceed()
                runCatching { installDrawer(chain.thisObject as Context) }
                    .onFailure { logger.error("failed to refresh drawer adapter", it) }
                result
            }
        }

        val packageReceiverClass = classLoader.loadClass("com.zui.freeform.sidebar.FloatService\$PackageReceiver")
        val onReceive = findMethod(
            packageReceiverClass, "onReceive",
            Context::class.java, android.content.Intent::class.java
        )
        hookWithId(onReceive, "sidebar_drawer_pkg") { chain ->
            val result = chain.proceed()
            val context = chain.args[0] as? Context
            if (context != null) {
                runCatching { installDrawer(context) }
                    .onFailure { logger.error("failed to refresh drawer after package change", it) }
            }
            result
        }

        logger.info("sidebar drawer hook installed")
    }

    /** The drawer-open path is an R8 lambda, so match it by prefix instead of by name. */
    private fun findDrawerOpenMethod(floatServiceClass: Class<*>): Method? =
        floatServiceClass.declaredMethods.firstOrNull {
            it.parameterCount == 0 && it.name.startsWith("lambda\$new\$1\$")
        }?.apply { isAccessible = true }

    private fun capacity(context: Context): Int =
        remotePreferences.getInt(PreferenceKeys.SIDEBAR_DRAWER_SLOTS.name, 8).coerceIn(1, MAX_SLOTS)

    private fun recentSlots(context: Context): Int =
        remotePreferences.getInt(PreferenceKeys.SIDEBAR_DRAWER_RECENT_SLOTS.name, 2).coerceIn(0, 4)

    @Suppress("UNCHECKED_CAST")
    private fun appMap(): Map<String, Any> =
        appInfoListField?.get(null) as? Map<String, Any> ?: emptyMap()

    /** Host default order: pinyin-sorted launcher apps, keyed `pkg:userId`. */
    private fun allKeys(context: Context): List<String> {
        val method = generateLauncherApp ?: return emptyList()
        val list = runCatching { method.invoke(null, context) as? List<Any> }.getOrNull()
        return list?.mapNotNull { SidebarReflect.keyOf(it) } ?: emptyList()
    }

    private fun visibleFreeformKeys(context: Context): List<String> {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, "cur_visible_freeform_list")
        }.getOrNull() ?: return emptyList()
        return raw.split(";").filter { it.isNotEmpty() }
    }

    private fun recentKeys(context: Context): List<String> =
        SidebarDrawerStore.readRecent(context).ifEmpty { visibleFreeformKeys(context) }

    /** Drawer content: recent slots first, then the user's order (or the host default). */
    private fun composedKeys(context: Context): List<String> {
        val known = allKeys(context)
        if (known.isEmpty()) return emptyList()
        val knownSet = known.toSet()
        val customized = SidebarDrawerStore.isCustomized(context)
        val base = if (customized) SidebarDrawerStore.readOrder(context) else known
        val out = LinkedHashSet<String>()
        recentKeys(context).take(recentSlots(context)).forEach { if (it in knownSet) out += it }
        base.forEach { if (it in knownSet) out += it }
        return out.take(capacity(context))
    }

    /** Edit-mode list: the user's order first, then every remaining app. */
    private fun editKeys(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        SidebarDrawerStore.readOrder(context).forEach { out += it }
        allKeys(context).forEach { out += it }
        return out.toList()
    }

    private fun resolve(keys: List<String>): List<Any> {
        val map = appMap()
        return keys.mapNotNull { map[it] }
    }

    private fun installDrawer(context: Context) {
        val listView = appIconLvField?.get(context) as? ListView ?: return
        val drawerAdapter = adapter
            ?: SidebarDrawerAdapter(context, hostPackage, callbacks(context)).also { adapter = it }
        // The host re-attaches its own capped adapter every time the drawer opens, so this
        // must re-attach ours rather than only pushing data into the existing instance.
        if (listView.adapter !== drawerAdapter) {
            listView.adapter = drawerAdapter
            listView.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, view, position, _ ->
                onItemClick(context, view, position)
            }
        }
        refreshDrawer(context)
    }

    private fun refreshDrawer(context: Context) {
        val drawerAdapter = adapter ?: return
        val editMode = drawerAdapter.isEditMode()
        val items = resolve(if (editMode) editKeys(context) else composedKeys(context))
        if (!editMode) appTop5ListField?.set(context, items)
        drawerAdapter.submit(items, visibleKeys(context), editMode)
    }

    private fun visibleKeys(context: Context): List<String> =
        runCatching { visibleAppsList?.invoke(context) as? List<String> }.getOrNull() ?: emptyList()

    /**
     * The host's "more" footer is identified by view identity, so the mapping survives the
     * host adding or removing footer views; the action row is located through the adapter.
     */
    private fun onItemClick(context: Context, view: android.view.View?, position: Int) {
        val drawerAdapter = adapter ?: return
        if (view != null && view === footViewField?.get(context)) {
            openAllAppsPanel(context)
            return
        }
        if (position == drawerAdapter.actionPosition()) {
            toggleEditMode(context)
            return
        }
        if (drawerAdapter.isEditMode()) return
        val app = drawerAdapter.getItem(position) ?: return
        SidebarReflect.keyOf(app)?.let { SidebarDrawerStore.pushRecent(context, it) }
        runCatching { launchApp?.invoke(context, app) }
        runCatching {
            collectDataTop5?.invoke(context, 1, SidebarReflect.call(app, "getPkgName"))
        }
    }

    /**
     * Mirrors the host's own "more" path: it dismisses the popup and re-shows it in all-apps
     * mode, because `showAllAppWindow` only rebuilds the popup when it is not already showing.
     */
    private fun openAllAppsPanel(context: Context) {
        val popup = popupWindowField?.get(context) as? android.widget.PopupWindow
        if (popup != null) {
            popup.animationStyle = 0
            popup.update()
            popup.dismiss()
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { showAllAppWindow?.invoke(context) }
                .onFailure { logger.error("failed to open the all-apps panel", it) }
            runCatching { collectDataTop5?.invoke(context, 2, null) }
        }
    }

    private fun toggleEditMode(context: Context) {
        val drawerAdapter = adapter ?: return
        val next = !drawerAdapter.isEditMode()
        if (next) {
            // Materialise the visible order so ▲ / ▼ / ✕ operate on a full permutation.
            SidebarDrawerStore.writeOrder(context, editKeys(context))
            SidebarDrawerStore.setCustomized(context, true)
        }
        applyEditWidth(context, next, drawerAdapter.glyphWidth)
        drawerAdapter.submit(
            resolve(if (next) editKeys(context) else composedKeys(context)),
            visibleKeys(context),
            next
        )
        // The action row moves to the other end of the list, so follow it. Posted because
        // setSelection during a pending data change is dropped by the next layout pass.
        runCatching {
            val listView = appIconLvField?.get(context) as? ListView
            listView?.post { listView.setSelection(0) }
        }
    }

    /**
     * The drawer is only `top_list_window_width` wide (70dp in landscape), so the three
     * per-row glyphs need extra room while editing.
     */
    private fun applyEditWidth(context: Context, edit: Boolean, glyphWidth: Int) {
        val layout = drawerLayoutField?.get(context) as? android.view.ViewGroup ?: return
        val params = layout.layoutParams ?: return
        if (originalDrawerWidth == 0) originalDrawerWidth = params.width
        params.width = if (edit) originalDrawerWidth + (glyphWidth * 3) else originalDrawerWidth
        layout.layoutParams = params
    }

    private fun callbacks(context: Context) = object : SidebarDrawerAdapter.DrawerCallbacks {
        override fun onToggleEditMode() = toggleEditMode(context)

        override fun onMoveUp(app: Any) = move(context, app, -1)

        override fun onMoveDown(app: Any) = move(context, app, 1)

        override fun onRemove(app: Any) = remove(context, app)
    }

    private fun move(context: Context, app: Any, delta: Int) {
        val key = SidebarReflect.keyOf(app) ?: return
        val order = editKeys(context).toMutableList()
        val index = order.indexOf(key)
        val target = index + delta
        if (index < 0 || target < 0 || target >= order.size) return
        val moved = order[index]
        order[index] = order[target]
        order[target] = moved
        SidebarDrawerStore.writeOrder(context, order)
        SidebarDrawerStore.setCustomized(context, true)
        refreshDrawer(context)
    }

    private fun remove(context: Context, app: Any) {
        val key = SidebarReflect.keyOf(app) ?: return
        val order = editKeys(context).toMutableList()
        if (!order.remove(key)) return
        SidebarDrawerStore.writeOrder(context, order)
        SidebarDrawerStore.setCustomized(context, true)
        refreshDrawer(context)
    }

    private companion object {
        /** The drawer fits 10 rows, two of which are the action row and the host's "more" row. */
        const val MAX_SLOTS = 8
    }
}
