package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.content.SharedPreferences

/**
 * Drawer slot contents, ordering and recency for the sidebar host, stored in the host's own
 * `freeform_preferences` file under private keys so the host's `top_1..top_5` writer
 * cannot overwrite them.
 *
 * Values are `;`-joined `pkg:userId` keys, matching the host's own key format.
 */
internal object SidebarDrawerStore {

    private const val PREFS_NAME = "freeform_preferences"
    private const val KEY_MEMBERS = "ztool_drawer_members"
    private const val KEY_ORDER = "ztool_drawer_order"
    private const val KEY_RECENT = "ztool_drawer_recent"
    private const val KEY_CUSTOMIZED = "ztool_drawer_customized"
    private const val SEPARATOR = ";"

    /** Cap for the recency ring; larger than any slot count so the ring survives edits. */
    private const val RECENT_CAP = 8

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun split(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    /**
     * The apps the user placed in the drawer, in slot order. Null means the user has never
     * written any, which is different from an empty list (a drawer they emptied on purpose).
     */
    fun readMembers(context: Context): List<String>? =
        prefs(context).getString(KEY_MEMBERS, null)?.let { split(it) }

    fun writeMembers(context: Context, keys: List<String>) {
        prefs(context).edit().putString(KEY_MEMBERS, keys.joinToString(SEPARATOR)).commit()
    }

    /** Read-only leftover of the earlier window-over-a-permutation model, used for migration. */
    fun readLegacyOrder(context: Context): List<String> =
        split(prefs(context).getString(KEY_ORDER, null))

    fun isCustomized(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CUSTOMIZED, false)

    fun setCustomized(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CUSTOMIZED, value).commit()
    }

    fun readRecent(context: Context): List<String> =
        split(prefs(context).getString(KEY_RECENT, null))

    fun pushRecent(context: Context, key: String) {
        if (key.isEmpty()) return
        val next = ArrayList<String>(RECENT_CAP)
        next.add(key)
        for (existing in readRecent(context)) {
            if (existing != key && next.size < RECENT_CAP) next.add(existing)
        }
        prefs(context).edit().putString(KEY_RECENT, next.joinToString(SEPARATOR)).commit()
    }
}
