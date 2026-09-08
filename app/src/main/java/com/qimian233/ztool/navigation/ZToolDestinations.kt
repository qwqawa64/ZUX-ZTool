package com.qimian233.ztool.navigation

import com.qimian233.ztool.R

internal enum class MainRoute(
    val labelRes: Int,
    val iconRes: Int
) {
    Home(R.string.gotoHomePage, R.drawable.ic_home),
    Features(R.string.gotoFeaturePage, R.drawable.ic_features),
    Settings(R.string.gotoSettingsPage, R.drawable.ic_settings);

    companion object {
        val entriesInOrder = listOf(Home, Features, Settings)

        fun fromName(name: String): MainRoute? {
            return entriesInOrder.firstOrNull { it.name == name }
        }
    }
}

internal object HiddenRoute {
    const val SETTINGS_THEME = "SettingsTheme"
    const val SETTINGS_ABOUT = "SettingsAbout"
    const val SETTINGS_ADVANCED = "SettingsAdvanced"
    const val SYSTEM_UI_STATUS_BAR = "feature/system-ui/status-bar"
    const val SYSTEM_UI_LOCK_SCREEN = "feature/system-ui/lock-screen"
    const val SYSTEM_UI_CONTROL_CENTER = "feature/system-ui/control-center"
    const val SYSTEM_UI_ANIMATION_WALLPAPER = "feature/system-ui/animation-wallpaper"
    const val SYSTEM_UI_MISC = "feature/system-ui/misc"
    const val SETTINGS_DETAIL_MAGIC_WINDOW_SEARCH = "feature/settings-detail/magic-window-search"
}
