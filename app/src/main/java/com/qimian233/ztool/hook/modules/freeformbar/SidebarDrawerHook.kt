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
    private var allAppLayoutField: Field? = null
    private var appIconGvField: Field? = null
    private var appAllListField: Field? = null
    private var indexRail: SidebarIndexRail? = null
    private var badgeDecorator: SidebarBadgeDecorator? = null
    private var dragReorder: SidebarDragReorder? = null
    private var memberCount: Int = 0
    private var drawerWidth: Int = 0
    private var footViewField: Field? = null
    private var popupWindowField: Field? = null

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
        allAppLayoutField = findField(floatServiceClass, "mAllAppLayout")
        appIconGvField = findField(floatServiceClass, "mAppIconGv")
        appAllListField = findField(floatServiceClass, "mAppAllList")
        footViewField = findField(floatServiceClass, "mFootView")
        popupWindowField = findField(floatServiceClass, "popupWindow")
        indexRail = SidebarIndexRail(classLoader, logger)
        badgeDecorator = SidebarBadgeDecorator(hostPackage, logger)

        hookWithId(generateTop5App, "sidebar_drawer_top5") { chain ->
            val context = chain.args[0] as? Context
            if (context == null) return@hookWithId chain.proceed()
            runCatching { resolve(composedKeys(context)) }.getOrElse { chain.proceed() }
        }

        // Catch the host attaching its own adapter from any code path, not just the drawer-open
        // lambda. Deferred to the next loop turn because the host sets its click listener right
        // after setAdapter, and replacing the adapter inside the call upsets the ListView.
        runCatching {
            val setAdapter = findMethod(
                android.widget.ListView::class.java, "setAdapter",
                android.widget.ListAdapter::class.java
            )
            hookWithId(setAdapter, "sidebar_drawer_setadapter") { chain ->
                val result = chain.proceed()
                val listView = chain.thisObject as? ListView
                val context = listView?.context
                if (listView != null && context != null &&
                    listView === appIconLvField?.get(context)
                ) {
                    listView.post { runCatching { installDrawer(context) } }
                }
                result
            }
        }.onFailure { logger.warn("could not hook ListView.setAdapter: " + it.message) }

        val onCreate = findMethod(floatServiceClass, "onCreate")
        hookWithId(onCreate, "sidebar_drawer_oncreate") { chain ->
            val result = chain.proceed()
            runCatching { installDrawer(chain.thisObject as Context) }
                .onFailure { logger.error("failed to install drawer adapter", it) }
            result
        }

        // The all-apps panel is rebuilt on every show, so the rail is re-attached there.
        val allApps = showAllAppWindow
        if (allApps != null) {
            hookWithId(allApps, "sidebar_drawer_allapps") { chain ->
                val result = chain.proceed()
                val context = chain.thisObject as Context
                runCatching { attachIndexRail(context) }
                    .onFailure { logger.error("failed to attach the index rail", it) }
                runCatching { reapplyEditChrome(context) }
                    .onFailure { logger.error("failed to restore the edit layout", it) }
                result
            }
        }

        val drawerOpen = findDrawerOpenMethod(floatServiceClass)
        if (drawerOpen != null) {
            hookWithId(drawerOpen, "sidebar_drawer_open") { chain ->
                val result = chain.proceed()
                val context = chain.thisObject as Context
                runCatching { installDrawer(context) }
                    .onFailure { logger.error("failed to refresh drawer adapter", it) }
                runCatching { reapplyEditChrome(context) }
                    .onFailure { logger.error("failed to restore the edit layout", it) }
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

    /** Whether the drawer exposes the 编辑 / 完成 row and the ▲ ▼ ✕ affordances. */
    private fun editable(context: Context): Boolean =
        remotePreferences.getBoolean(PreferenceKeys.SIDEBAR_DRAWER_EDITABLE.name, true)

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

    /** How many slots the user's own apps may occupy, i.e. the capacity minus the recent slots. */
    private fun memberSlots(context: Context): Int =
        (capacity(context) - recentSlots(context)).coerceAtLeast(1)

    /**
     * The apps occupying the user's slots, in order. Before the first customisation this is the
     * host's default head, and for installs from the earlier window-over-a-permutation model it
     * is that model's first `memberSlots` entries.
     */
    private fun memberKeys(context: Context): List<String> {
        val known = allKeys(context)
        if (!SidebarDrawerStore.isCustomized(context)) return known.take(memberSlots(context))
        val knownSet = known.toSet()
        val stored = SidebarDrawerStore.readMembers(context)
            ?: SidebarDrawerStore.readLegacyOrder(context)
        return stored.filter { it in knownSet }.take(memberSlots(context))
    }

    /** Drawer content: recent slots first, then the user's slots, which may be partly empty. */
    private fun composedKeys(context: Context): List<String> {
        val known = allKeys(context)
        if (known.isEmpty()) return emptyList()
        val knownSet = known.toSet()
        val out = LinkedHashSet<String>()
        recentKeys(context).take(recentSlots(context)).forEach { if (it in knownSet) out += it }
        memberKeys(context).forEach { out += it }
        return out.take(capacity(context))
    }

    private fun resolve(keys: List<String>): List<Any> {
        val map = appMap()
        return keys.mapNotNull { map[it] }
    }

    private fun installDrawer(context: Context) {
        val listView = appIconLvField?.get(context) as? ListView
        if (listView == null) {
            logger.warn("drawer ListView not found, leaving the host adapter in place")
            return
        }
        val drawerAdapter = adapter
            ?: SidebarDrawerAdapter(context, hostPackage, editable(context), callbacks(context))
                .also { adapter = it }
        // The host re-attaches its own capped adapter and listener every time the drawer opens,
        // so both are re-asserted rather than only pushing data into the existing instance.
        if (currentAdapter(listView) !== drawerAdapter) {
            listView.adapter = drawerAdapter
            logger.info("took over the drawer adapter")
        }
        listView.onItemClickListener = clickListener
        val drag = dragReorder ?: SidebarDragReorder(
            logger,
            isDraggable = { position -> memberIndexAt(position) >= 0 },
            onSwap = { from, to -> swapMembers(context, memberIndexAt(from), memberIndexAt(to)) },
            onDragging = { position ->
                adapter?.setDraggingIndex(if (position < 0) -1 else memberIndexAt(position))
            }
        ).also { dragReorder = it }
        drag.attach(listView)
        refreshDrawer(context)
    }

    /** Adapter position to member index, or -1 when that position is not a draggable app row. */
    private fun memberIndexAt(position: Int): Int {
        val drawerAdapter = adapter ?: return -1
        if (!drawerAdapter.isEditMode()) return -1
        val index = position - 1
        return if (index in 0 until memberCount) index else -1
    }

    /** Swaps two slots and persists, so a drag and the arrows share one code path. */
    private fun swapMembers(context: Context, from: Int, to: Int) {
        val members = (SidebarDrawerStore.readMembers(context)
            ?: memberKeys(context).toMutableList()).toMutableList()
        if (from !in members.indices || to !in members.indices) return
        val moved = members[from]
        members[from] = members[to]
        members[to] = moved
        SidebarDrawerStore.writeMembers(context, members)
        SidebarDrawerStore.setCustomized(context, true)
        // Membership is unchanged by a reorder, so the all-apps badges need no refresh here.
        adapter?.let { submitDrawer(context, it, it.isEditMode(), members) }
    }

    /** The host hides one panel or the other whenever it opens them, so put the edit layout back. */
    private fun reapplyEditChrome(context: Context) {
        val drawerAdapter = adapter ?: return
        if (!drawerAdapter.isEditMode()) return
        applyEditLayout(context, true)
        refreshBadges(context)
    }

    /** Builds the A–Z rail for the all-apps panel and hides the host's crashing one. */
    private fun attachIndexRail(context: Context) {
        val rail = indexRail ?: return
        val panel = allAppLayoutField?.get(context) as? android.view.ViewGroup ?: return
        val grid = appIconGvField?.get(context) as? android.view.View ?: return
        val apps = appAllListField?.get(context) as? List<Any> ?: return
        if (apps.isEmpty()) return
        rail.attach(panel, grid, apps)
    }

    /**
     * `ListView.getAdapter()` returns a `HeaderViewListAdapter` wrapper as soon as the host has
     * attached its "more" footer, so identity has to be checked against the wrapped adapter.
     * Comparing against the wrapper would never match and re-set the adapter forever.
     */
    private fun currentAdapter(listView: ListView): android.widget.ListAdapter? {
        val current = listView.adapter ?: return null
        return if (current is android.widget.HeaderViewListAdapter) current.wrappedAdapter else current
    }

    private val clickListener = android.widget.AdapterView.OnItemClickListener { parent, view, position, _ ->
        onItemClick(parent.context, view, position)
    }

    private fun refreshDrawer(context: Context) {
        val drawerAdapter = adapter ?: return
        submitDrawer(context, drawerAdapter, drawerAdapter.isEditMode())
    }

    /**
     * While editing, the drawer shows exactly the user's slots followed by the free ones as
     * placeholders, so its length stays constant instead of shrinking as apps are removed.
     *
     * [memberOverride] lets a reorder hand over the list it just produced: re-deriving it costs a
     * full pinyin sort of every installed app, which is too much to pay on every drag crossing.
     */
    private fun submitDrawer(
        context: Context,
        drawerAdapter: SidebarDrawerAdapter,
        editMode: Boolean,
        memberOverride: List<String>? = null
    ) {
        val keys = when {
            !editMode -> composedKeys(context)
            memberOverride != null -> memberOverride
            else -> memberKeys(context)
        }
        val items = resolve(keys)
        val empty = if (editMode) (memberSlots(context) - items.size).coerceAtLeast(0) else 0
        memberCount = if (editMode) items.size else 0
        if (!editMode) appTop5ListField?.set(context, items)
        drawerAdapter.submit(items, visibleKeys(context), editMode, empty)
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
        val actionPosition = drawerAdapter.actionPosition()
        if (actionPosition >= 0 && position == actionPosition) {
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
        if (!editable(context)) return
        val next = !drawerAdapter.isEditMode()
        if (next) {
            // Take ownership of the slots, seeded from what the user currently sees so the first
            // edit starts from a familiar set.
            if (!SidebarDrawerStore.isCustomized(context)) {
                SidebarDrawerStore.writeMembers(context, memberKeys(context))
                SidebarDrawerStore.setCustomized(context, true)
            }
            // The host only builds the grid adapter inside showAllAppWindow, so the panel would
            // stay empty if the edit layout merely made it visible.
            runCatching { showAllAppWindow?.invoke(context) }
                .onFailure { logger.error("failed to populate the all-apps panel", it) }
        }
        applyEditLayout(context, next)
        if (!next) runCatching { badgeDecorator?.clear() }
        if (next) runCatching { refreshBadges(context) }
            .onFailure { logger.error("failed to attach the badges", it) }
        submitDrawer(context, drawerAdapter, next)
        // The action row moves to the other end of the list, so follow it. Posted because
        // setSelection during a pending data change is dropped by the next layout pass.
        runCatching {
            val listView = appIconLvField?.get(context) as? ListView
            listView?.post { runCatching { listView.setSelection(0) } }
        }
    }

    /**
     * Side by side while editing: the all-apps panel shifts left by the drawer's width and the
     * drawer is pinned to the end, so the popup grows leftwards instead of covering the drawer.
     */
    private fun applyEditLayout(context: Context, edit: Boolean) {
        val drawer = drawerLayoutField?.get(context) as? android.view.ViewGroup ?: return
        val panel = allAppLayoutField?.get(context) as? android.view.ViewGroup ?: return
        val drawerParams = drawer.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        val panelParams = panel.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        if (edit) {
            val reserved = drawerParams.width.takeIf { it > 0 } ?: drawerWidth
            if (drawerWidth == 0) drawerWidth = reserved
            panelParams.marginEnd = reserved
            panelParams.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            drawerParams.gravity = android.view.Gravity.END or android.view.Gravity.TOP
            panel.visibility = android.view.View.VISIBLE
            drawer.visibility = android.view.View.VISIBLE
        } else {
            panelParams.marginEnd = 0
            // Gravity.UNSPECIFIED_GRAVITY is @hide; -1 is its value and restores the XML default.
            panelParams.gravity = -1
            drawerParams.gravity = -1
            // Entering edit mode ran the host's all-apps path, which hid the drawer; leaving it
            // has to put the drawer back, or the panel would stay on screen.
            panel.visibility = android.view.View.GONE
            drawer.visibility = android.view.View.VISIBLE
        }
        panel.layoutParams = panelParams
        drawer.layoutParams = drawerParams
    }

    /**
     * Adds the app to the user's slots, or removes it. Slots are explicit, so removing leaves a
     * gap instead of pulling the next app in from the full app list, and a full drawer refuses
     * the addition rather than silently displacing an app the user chose.
     */
    private fun toggleMembership(context: Context, app: Any) {
        val key = SidebarReflect.keyOf(app) ?: return
        val members = SidebarDrawerStore.readMembers(context)
            ?: memberKeys(context).toMutableList()
        val present = key in members
        val next = members.toMutableList()
        if (present) {
            next.remove(key)
        } else if (next.size < memberSlots(context)) {
            next.add(key)
        } else {
            return
        }
        SidebarDrawerStore.writeMembers(context, next)
        SidebarDrawerStore.setCustomized(context, true)
        refreshDrawer(context)
        refreshBadges(context)
    }

    private fun refreshBadges(context: Context) {
        val decorator = badgeDecorator ?: return
        val grid = appIconGvField?.get(context) as? android.view.View ?: return
        // The host replaces its own all-apps list, so hand over a provider rather than a snapshot.
        val list: () -> List<Any> = {
            appAllListField?.get(context) as? List<Any> ?: emptyList()
        }
        val gridChildren = (grid as? android.view.ViewGroup)?.childCount ?: 0
        if (list().isEmpty() && gridChildren == 0) return
        // The membership set is built once: this runs from a layout listener, and rebuilding the
        // launcher app list per icon would stall the main thread.
        val members = memberKeys(context).toHashSet()
        val member: (Any) -> Boolean = { SidebarReflect.keyOf(it)?.let { key -> key in members } == true }
        val toggle: (Any) -> Unit = { toggleMembership(context, it) }
        val canAdd = members.size < memberSlots(context)
        logger.trace(
            "badge refresh: children=" + gridChildren +
                " service=" + list().size +
                " members=" + members.size +
                " memberSlots=" + memberSlots(context) +
                " canAdd=" + canAdd
        )
        decorator.refresh(grid, list, member, toggle, canAdd)
        // The grid may not have created its item views yet, so retry once now and once after the
        // next layout settles; the scroll and layout listeners cover the rest.
        grid.post { runCatching { decorator.refresh(grid, list, member, toggle, canAdd) } }
        grid.postDelayed({ runCatching { decorator.refresh(grid, list, member, toggle, canAdd) } }, 300L)
    }

    private fun callbacks(context: Context) = object : SidebarDrawerAdapter.DrawerCallbacks {
        override fun onToggleEditMode() = toggleEditMode(context)

        override fun onRemove(app: Any) = remove(context, app)
    }

    /** Drops an app from the drawer, leaving its slot empty. */
    private fun remove(context: Context, app: Any) {
        val key = SidebarReflect.keyOf(app) ?: return
        val members = (SidebarDrawerStore.readMembers(context)
            ?: memberKeys(context).toMutableList()).toMutableList()
        if (!members.remove(key)) return
        SidebarDrawerStore.writeMembers(context, members)
        SidebarDrawerStore.setCustomized(context, true)
        refreshDrawer(context)
        refreshBadges(context)
    }

    private companion object {
        /** The drawer fits 10 rows, two of which are the action row and the host's "more" row. */
        const val MAX_SLOTS = 8
    }
}
