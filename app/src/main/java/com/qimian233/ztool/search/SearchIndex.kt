package com.qimian233.ztool.search

import androidx.annotation.StringRes
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.navigation.HiddenRoute
import com.qimian233.ztool.navigation.MainRoute
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.screens.ztoolsettings.about.SettingsAboutRouteName
import com.qimian233.ztool.ui.components.SearchHighlightIndexBridge

/**
 * How the [SearchEntry.parentTitleRes] gate is satisfied on the target screen.
 * Most conditional rows appear when the parent switch is ON; a few
 * (package-installer rows, launcher "larger dock") appear only when it is OFF.
 */
enum class SearchParentMode { REQUIRE_ON, REQUIRE_OFF }

/**
 * One searchable destination. The id is derived from the title resource name so it
 * stays stable across refactors; routes must be route names already registered in
 * [com.qimian233.ztool.navigation.ZToolNavHost] — phase 1 never creates new
 * destinations besides the search page itself.
 */
data class SearchEntry(
    val id: String,
    val route: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int? = null,
    /** Extra literal match terms (both languages welcome), e.g. English aliases. */
    val keywords: List<String> = emptyList(),
    /** Hide the entry when this package is not installed. Null = always visible. */
    val requiresPackage: String? = null,
    /** Title of the switch this row depends on, for the "requires …" annotation. */
    @StringRes val parentTitleRes: Int? = null,
    /** SearchEntry id of that parent switch; drives the highlight fallback. */
    val parentKey: String? = null,
    val parentMode: SearchParentMode = SearchParentMode.REQUIRE_ON,
    /** Free-form visibility note (e.g. style-gated theme rows). */
    @param:StringRes val conditionNoteRes: Int? = null,
    /** True for the 11 Features-route cards; they navigate via the Features tab. */
    val isFeatureCard: Boolean = false,
    val featureDestination: FeatureDestination? = null,
    @param:StringRes val groupTitleRes: Int,
    @param:StringRes val groupSuffixRes: Int? = null
)

/**
 * Static search index of every non-Home destination. Maintenance rule: a new visible
 * setting row must register a matching [SearchEntry] here (see
 * docs_archive/hook_guide/Add_Frontend_Item.md).
 */
object SearchIndex {

    private class Screen(
        val route: String,
        @param:StringRes val groupTitleRes: Int,
        @param:StringRes val groupSuffixRes: Int? = null,
        val requiresPackage: String? = null
    ) {
        fun item(
            id: String,
            @StringRes titleRes: Int,
            @StringRes summaryRes: Int? = null,
            @StringRes parentTitleRes: Int? = null,
            parentKey: String? = null,
            parentMode: SearchParentMode = SearchParentMode.REQUIRE_ON,
            @StringRes conditionNoteRes: Int? = null,
            keywords: List<String> = emptyList()
        ) = SearchEntry(
            id = id,
            route = route,
            titleRes = titleRes,
            summaryRes = summaryRes,
            keywords = keywords,
            requiresPackage = requiresPackage,
            parentTitleRes = parentTitleRes,
            parentKey = parentKey,
            parentMode = parentMode,
            conditionNoteRes = conditionNoteRes,
            isFeatureCard = false,
            featureDestination = null,
            groupTitleRes = groupTitleRes,
            groupSuffixRes = groupSuffixRes
        )
    }

    private val featureCards: List<SearchEntry> = listOf(
        SearchEntry(
            id = "feature_card_settings_detail",
            route = FeatureDestination.SettingsDetail.route,
            titleRes = R.string.settings_app_name,
            summaryRes = R.string.settings_app_description,
            requiresPackage = ScopeKeys.SETTINGS.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.SettingsDetail,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_game_tool",
            route = FeatureDestination.GameTool.route,
            titleRes = R.string.game_tool_app_name,
            summaryRes = R.string.game_tool_app_description,
            requiresPackage = ScopeKeys.GAME_SERVICE.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.GameTool,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_ota",
            route = FeatureDestination.Ota.route,
            titleRes = R.string.system_update_app_name,
            summaryRes = R.string.system_update_app_description,
            requiresPackage = ScopeKeys.OTA.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.Ota,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_package_installer",
            route = FeatureDestination.PackageInstaller.route,
            titleRes = R.string.package_installer_app_name,
            summaryRes = R.string.package_installer_app_description,
            requiresPackage = ScopeKeys.PACKAGE_INSTALLER.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.PackageInstaller,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_system_ui",
            route = FeatureDestination.SystemUi.route,
            titleRes = R.string.system_ui_app_name,
            summaryRes = R.string.system_ui_app_description,
            requiresPackage = ScopeKeys.SYSTEM_UI.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.SystemUi,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_launcher",
            route = FeatureDestination.Launcher.route,
            titleRes = R.string.launcher_app_name,
            summaryRes = R.string.launcher_app_description,
            requiresPackage = ScopeKeys.LAUNCHER.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.Launcher,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_mobile_desktop",
            route = FeatureDestination.MobileDesktop.route,
            titleRes = R.string.mobile_desktop_app_name,
            summaryRes = R.string.mobile_desktop_app_description,
            requiresPackage = ScopeKeys.MOBILE_DESKTOP.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.MobileDesktop,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_framework",
            route = FeatureDestination.Framework.route,
            titleRes = R.string.system_framework_app_name,
            summaryRes = R.string.system_framework_app_description,
            // The framework card is always visible on the Features tab (system-server scope).
            isFeatureCard = true,
            featureDestination = FeatureDestination.Framework,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_safe_center",
            route = FeatureDestination.SafeCenter.route,
            titleRes = R.string.safe_center_app_name,
            summaryRes = R.string.safe_center_app_description,
            requiresPackage = ScopeKeys.ZUI_SAFE_CENTER.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.SafeCenter,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_tb_engine",
            route = FeatureDestination.TbEngine.route,
            titleRes = R.string.tb_engine_app_name,
            summaryRes = R.string.tb_engine_app_description,
            requiresPackage = ScopeKeys.TB_ENGINE.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.TbEngine,
            groupTitleRes = R.string.search_group_feature_cards
        ),
        SearchEntry(
            id = "feature_card_zui_performance",
            route = FeatureDestination.ZuiPerformance.route,
            titleRes = R.string.zui_pp_app_name,
            summaryRes = R.string.zui_pp_app_description,
            requiresPackage = ScopeKeys.ZUI_PERFORMANCE.packageName,
            isFeatureCard = true,
            featureDestination = FeatureDestination.ZuiPerformance,
            groupTitleRes = R.string.search_group_feature_cards
        )
    )

    private val systemUiHub = Screen(
        route = FeatureDestination.SystemUi.route,
        groupTitleRes = R.string.system_ui_app_name,
        requiresPackage = ScopeKeys.SYSTEM_UI.packageName
    )

    private val systemUiStatusBar = Screen(
        route = HiddenRoute.SYSTEM_UI_STATUS_BAR,
        groupTitleRes = R.string.system_ui_app_name,
        groupSuffixRes = R.string.system_ui_status_bar_title_suffix,
        requiresPackage = ScopeKeys.SYSTEM_UI.packageName
    )

    private val systemUiLockScreen = Screen(
        route = HiddenRoute.SYSTEM_UI_LOCK_SCREEN,
        groupTitleRes = R.string.system_ui_app_name,
        groupSuffixRes = R.string.system_ui_lock_screen_title_suffix,
        requiresPackage = ScopeKeys.SYSTEM_UI.packageName
    )

    private val systemUiControlCenter = Screen(
        route = HiddenRoute.SYSTEM_UI_CONTROL_CENTER,
        groupTitleRes = R.string.system_ui_app_name,
        groupSuffixRes = R.string.system_ui_control_center_title_suffix,
        requiresPackage = ScopeKeys.SYSTEM_UI.packageName
    )

    private val systemUiAnimationWallpaper = Screen(
        route = HiddenRoute.SYSTEM_UI_ANIMATION_WALLPAPER,
        groupTitleRes = R.string.system_ui_app_name,
        groupSuffixRes = R.string.system_ui_animation_wallpaper_title_suffix,
        requiresPackage = ScopeKeys.SYSTEM_UI.packageName
    )

    private val systemUiMisc = Screen(
        route = HiddenRoute.SYSTEM_UI_MISC,
        groupTitleRes = R.string.system_ui_app_name,
        groupSuffixRes = R.string.system_ui_common_misc,
        requiresPackage = ScopeKeys.SYSTEM_UI.packageName
    )

    private val framework = Screen(
        route = FeatureDestination.Framework.route,
        groupTitleRes = R.string.system_framework_app_name
    )

    private val settingsDetail = Screen(
        route = FeatureDestination.SettingsDetail.route,
        groupTitleRes = R.string.settings_app_name,
        requiresPackage = ScopeKeys.SETTINGS.packageName
    )

    private val gameTool = Screen(
        route = FeatureDestination.GameTool.route,
        groupTitleRes = R.string.game_tool_app_name,
        requiresPackage = ScopeKeys.GAME_SERVICE.packageName
    )

    private val ota = Screen(
        route = FeatureDestination.Ota.route,
        groupTitleRes = R.string.system_update_app_name,
        requiresPackage = ScopeKeys.OTA.packageName
    )

    private val tbEngine = Screen(
        route = FeatureDestination.TbEngine.route,
        groupTitleRes = R.string.tb_engine_app_name,
        requiresPackage = ScopeKeys.TB_ENGINE.packageName
    )

    private val packageInstaller = Screen(
        route = FeatureDestination.PackageInstaller.route,
        groupTitleRes = R.string.package_installer_app_name,
        requiresPackage = ScopeKeys.PACKAGE_INSTALLER.packageName
    )

    private val launcher = Screen(
        route = FeatureDestination.Launcher.route,
        groupTitleRes = R.string.launcher_app_name,
        requiresPackage = ScopeKeys.LAUNCHER.packageName
    )

    private val mobileDesktop = Screen(
        route = FeatureDestination.MobileDesktop.route,
        groupTitleRes = R.string.mobile_desktop_app_name,
        requiresPackage = ScopeKeys.MOBILE_DESKTOP.packageName
    )

    private val safeCenter = Screen(
        route = FeatureDestination.SafeCenter.route,
        groupTitleRes = R.string.safe_center_app_name,
        requiresPackage = ScopeKeys.ZUI_SAFE_CENTER.packageName
    )

    private val zuiPerformance = Screen(
        route = FeatureDestination.ZuiPerformance.route,
        groupTitleRes = R.string.zui_pp_app_name,
        requiresPackage = ScopeKeys.ZUI_PERFORMANCE.packageName
    )

    private val appSettings = Screen(
        route = MainRoute.Settings.name,
        groupTitleRes = R.string.page_settings_title
    )

    private val themeSettings = Screen(
        route = HiddenRoute.SETTINGS_THEME,
        groupTitleRes = R.string.page_settings_app_ui_theme_settings
    )

    private val advancedSettings = Screen(
        route = HiddenRoute.SETTINGS_ADVANCED,
        groupTitleRes = R.string.page_settings_advanced_title
    )

    private val aboutScreen = Screen(
        route = SettingsAboutRouteName,
        groupTitleRes = R.string.page_settings_about_ztool_title
    )

    val all: List<SearchEntry> = featureCards + listOf(
        systemUiHub.item(
            id = "system_ui_hub_status_bar",
            titleRes = R.string.system_ui_status_bar_setting_title,
            summaryRes = R.string.system_ui_status_bar_setting_summary
        ),
        systemUiHub.item(
            id = "system_ui_hub_lock_screen",
            titleRes = R.string.system_ui_lock_screen_setting_title,
            summaryRes = R.string.system_ui_lock_screen_summary
        ),
        systemUiHub.item(
            id = "system_ui_hub_control_center",
            titleRes = R.string.system_ui_control_center_title,
            summaryRes = R.string.system_ui_control_center_summary
        ),
        systemUiHub.item(
            id = "system_ui_hub_animation_wallpaper",
            titleRes = R.string.system_ui_animation_wallpaper_setting_title,
            summaryRes = R.string.system_ui_animation_wallpaper_setting_summary
        ),
        systemUiHub.item(
            id = "system_ui_hub_misc",
            titleRes = R.string.system_ui_common_misc,
            summaryRes = R.string.system_ui_misc_setting_summary
        ),

        systemUiStatusBar.item(
            id = "status_bar_display_seconds",
            titleRes = R.string.system_ui_status_bar_display_seconds_title,
            summaryRes = R.string.system_ui_status_bar_display_seconds_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_custom_clock",
            titleRes = R.string.system_ui_status_bar_custom_clock_title,
            summaryRes = R.string.system_ui_status_bar_custom_clock_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_clock_text_size",
            titleRes = R.string.system_ui_status_bar_text_size_title,
            parentTitleRes = R.string.system_ui_status_bar_custom_clock_title,
            parentKey = "status_bar_custom_clock"
        ),
        systemUiStatusBar.item(
            id = "status_bar_clock_letter_spacing",
            titleRes = R.string.system_ui_status_bar_letter_spacing_title,
            parentTitleRes = R.string.system_ui_status_bar_custom_clock_title,
            parentKey = "status_bar_custom_clock"
        ),
        systemUiStatusBar.item(
            id = "status_bar_clock_text_color",
            titleRes = R.string.system_ui_status_bar_text_color_title,
            parentTitleRes = R.string.system_ui_status_bar_custom_clock_title,
            parentKey = "status_bar_custom_clock"
        ),
        systemUiStatusBar.item(
            id = "status_bar_clock_text_bold",
            titleRes = R.string.system_ui_status_bar_text_bold_title,
            parentTitleRes = R.string.system_ui_status_bar_custom_clock_title,
            parentKey = "status_bar_custom_clock"
        ),
        systemUiStatusBar.item(
            id = "status_bar_notification_icon_limit",
            titleRes = R.string.system_ui_status_bar_notification_icon_limit_title,
            summaryRes = R.string.system_ui_status_bar_notification_icon_limit_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_notification_icon_native",
            titleRes = R.string.system_ui_status_bar_notification_icon_native_title,
            summaryRes = R.string.system_ui_status_bar_notification_icon_native_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_network_size",
            titleRes = R.string.system_ui_status_bar_network_size_title,
            summaryRes = R.string.system_ui_status_bar_network_size_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_network_size_double_layer",
            titleRes = R.string.system_ui_status_bar_network_size_double_layer,
            summaryRes = R.string.system_ui_status_bar_network_size_double_layer_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_network_refresh",
            titleRes = R.string.system_ui_status_bar_network_refresh_title,
            summaryRes = R.string.system_ui_status_bar_network_refresh_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_network_refresh_interval",
            titleRes = R.string.system_ui_status_bar_network_refresh_interval_title,
            parentTitleRes = R.string.system_ui_status_bar_network_refresh_title,
            parentKey = "status_bar_network_refresh"
        ),
        systemUiStatusBar.item(
            id = "status_bar_network_hide_slow",
            titleRes = R.string.system_ui_status_bar_network_hide_slow_title,
            summaryRes = R.string.system_ui_status_bar_network_hide_slow_summary
        ),
        systemUiStatusBar.item(
            id = "status_bar_network_hide_threshold",
            titleRes = R.string.system_ui_status_bar_network_hide_threshold_title,
            parentTitleRes = R.string.system_ui_status_bar_network_hide_slow_title,
            parentKey = "status_bar_network_hide_slow"
        ),
        systemUiStatusBar.item(
            id = "status_bar_network_hide_both",
            titleRes = R.string.system_ui_status_bar_network_hide_both_title,
            summaryRes = R.string.system_ui_status_bar_network_hide_both_summary,
            parentTitleRes = R.string.system_ui_status_bar_network_hide_slow_title,
            parentKey = "status_bar_network_hide_slow"
        ),
        systemUiStatusBar.item(
            id = "status_bar_battery_external",
            titleRes = R.string.system_ui_status_bar_syatus_battery_external_title,
            summaryRes = R.string.system_ui_status_bar_syatus_battery_external_summary
        ),

        systemUiLockScreen.item(
            id = "lock_screen_yi_yan",
            titleRes = R.string.system_ui_lock_screen_yi_yan_switch_title,
            summaryRes = R.string.system_ui_lock_screen_yi_yan_summary
        ),
        systemUiLockScreen.item(
            id = "lock_screen_clock_color",
            titleRes = R.string.system_ui_lock_screen_clock_color_title
        ),
        systemUiLockScreen.item(
            id = "lock_screen_aod_native",
            titleRes = R.string.system_ui_lock_screen_aod_native_enable_title,
            summaryRes = R.string.system_ui_lock_screen_aod_native_enable_summary
        ),
        systemUiLockScreen.item(
            id = "lock_screen_aod_lenovo",
            titleRes = R.string.system_ui_lock_screen_aod_lenovo_enable_title,
            summaryRes = R.string.system_ui_lock_screen_aod_lenovo_enable_summary
        ),
        systemUiLockScreen.item(
            id = "lock_screen_aod_lenovo_activity",
            titleRes = R.string.system_ui_lock_screen_aod_lenovo_activity_title,
            summaryRes = R.string.system_ui_lock_screen_aod_lenovo_activity_summary,
            parentTitleRes = R.string.system_ui_lock_screen_aod_lenovo_enable_title,
            parentKey = "lock_screen_aod_lenovo"
        ),
        systemUiLockScreen.item(
            id = "lock_screen_charge_watts",
            titleRes = R.string.system_ui_lock_screen_charge_watts_enable_title,
            summaryRes = R.string.system_ui_lock_screen_charge_watts_summary
        ),
        systemUiLockScreen.item(
            id = "lock_screen_realwatts_show_power",
            titleRes = R.string.system_ui_lock_screen_realwatts_show_power,
            summaryRes = R.string.system_ui_lock_screen_realwatts_show_power_summary,
            parentTitleRes = R.string.system_ui_lock_screen_charge_watts_enable_title,
            parentKey = "lock_screen_charge_watts"
        ),
        systemUiLockScreen.item(
            id = "lock_screen_realwatts_show_voltage",
            titleRes = R.string.system_ui_lock_screen_realwatts_show_voltage,
            summaryRes = R.string.system_ui_lock_screen_realwatts_show_voltage_summary,
            parentTitleRes = R.string.system_ui_lock_screen_charge_watts_enable_title,
            parentKey = "lock_screen_charge_watts"
        ),
        systemUiLockScreen.item(
            id = "lock_screen_realwatts_show_current",
            titleRes = R.string.system_ui_lock_screen_realwatts_show_current,
            summaryRes = R.string.system_ui_lock_screen_realwatts_show_current_summary,
            parentTitleRes = R.string.system_ui_lock_screen_charge_watts_enable_title,
            parentKey = "lock_screen_charge_watts"
        ),
        systemUiLockScreen.item(
            id = "lock_screen_realwatts_show_temperature",
            titleRes = R.string.system_ui_lock_screen_realwatts_show_temperature,
            summaryRes = R.string.system_ui_lock_screen_realwatts_show_temperature_summary,
            parentTitleRes = R.string.system_ui_lock_screen_charge_watts_enable_title,
            parentKey = "lock_screen_charge_watts"
        ),
        systemUiLockScreen.item(
            id = "lock_screen_realwatts_show_indicator",
            titleRes = R.string.system_ui_lock_screen_realwatts_show_indicator,
            summaryRes = R.string.system_ui_lock_screen_realwatts_show_indicator_summary,
            parentTitleRes = R.string.system_ui_lock_screen_charge_watts_enable_title,
            parentKey = "lock_screen_charge_watts"
        ),
        systemUiLockScreen.item(
            id = "lock_screen_realwatts_custom_format",
            titleRes = R.string.system_ui_lock_screen_realwatts_custom_format_enabled,
            summaryRes = R.string.system_ui_lock_screen_realwatts_custom_format_enabled_summary,
            parentTitleRes = R.string.system_ui_lock_screen_charge_watts_enable_title,
            parentKey = "lock_screen_charge_watts"
        ),

        systemUiControlCenter.item(
            id = "control_center_notification_blur",
            titleRes = R.string.system_ui_control_center_notification_center_blur_title,
            summaryRes = R.string.system_ui_control_center_notification_center_blur_summary
        ),
        systemUiControlCenter.item(
            id = "control_center_notification_blur_strength",
            titleRes = R.string.system_ui_control_center_notification_center_blur_strength_title,
            summaryRes = R.string.system_ui_control_center_notification_center_blur_strength_summary,
            parentTitleRes = R.string.system_ui_control_center_notification_center_blur_title,
            parentKey = "control_center_notification_blur"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_tile_radius",
            titleRes = R.string.system_ui_control_center_custom_control_center_tile_radius,
            summaryRes = R.string.system_ui_control_center_custom_control_center_tile_radius_summary
        ),
        systemUiControlCenter.item(
            id = "control_center_head_up_corner_radius",
            titleRes = R.string.system_ui_control_center_head_up_corner_radius,
            parentTitleRes = R.string.system_ui_control_center_custom_control_center_tile_radius,
            parentKey = "control_center_custom_tile_radius"
        ),
        systemUiControlCenter.item(
            id = "control_center_normal_tile_corner_radius",
            titleRes = R.string.system_ui_control_center_normal_tile_corner_radius,
            parentTitleRes = R.string.system_ui_control_center_custom_control_center_tile_radius,
            parentKey = "control_center_custom_tile_radius"
        ),
        systemUiControlCenter.item(
            id = "control_center_brightness_slider_percentage",
            titleRes = R.string.system_ui_control_center_show_brightness_slider_percentage
        ),
        systemUiControlCenter.item(
            id = "control_center_volume_slider_percentage",
            titleRes = R.string.system_ui_control_center_show_volume_slider_percentage
        ),
        systemUiControlCenter.item(
            id = "control_center_customize_slider_style",
            titleRes = R.string.system_ui_control_center_customize_slider_style_title,
            summaryRes = R.string.system_ui_control_center_customize_slider_style_summary
        ),
        systemUiControlCenter.item(
            id = "control_center_slider_style_direction",
            titleRes = R.string.system_ui_control_center_slider_style_direction_title,
            parentTitleRes = R.string.system_ui_control_center_customize_slider_style_title,
            parentKey = "control_center_customize_slider_style"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_qs_color_general",
            titleRes = R.string.system_ui_control_center_custom_qs_color_general_switch
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_qs_color",
            titleRes = R.string.system_ui_control_center_custom_qs_color_title,
            summaryRes = R.string.system_ui_control_center_custom_qs_color_summary,
            parentTitleRes = R.string.system_ui_control_center_custom_qs_color_general_switch,
            parentKey = "control_center_custom_qs_color_general"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_qs_active_color",
            titleRes = R.string.system_ui_control_center_custom_qs_active_color_title,
            parentTitleRes = R.string.system_ui_control_center_custom_qs_color_title,
            parentKey = "control_center_custom_qs_color"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_label_color",
            titleRes = R.string.system_ui_control_center_custom_label_color_title,
            summaryRes = R.string.system_ui_control_center_custom_label_color_summary,
            parentTitleRes = R.string.system_ui_control_center_custom_qs_color_general_switch,
            parentKey = "control_center_custom_qs_color_general"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_label_active_color",
            titleRes = R.string.system_ui_control_center_custom_label_active_color_title,
            parentTitleRes = R.string.system_ui_control_center_custom_label_color_title,
            parentKey = "control_center_custom_label_color"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_second_label_color",
            titleRes = R.string.system_ui_control_center_custom_second_label_color_title,
            summaryRes = R.string.system_ui_control_center_custom_second_label_color_summary,
            parentTitleRes = R.string.system_ui_control_center_custom_qs_color_general_switch,
            parentKey = "control_center_custom_qs_color_general"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_second_label_active_color",
            titleRes = R.string.system_ui_control_center_custom_second_label_active_color_title,
            parentTitleRes = R.string.system_ui_control_center_custom_second_label_color_title,
            parentKey = "control_center_custom_second_label_color"
        ),
        systemUiControlCenter.item(
            id = "control_center_no_tile_labels",
            titleRes = R.string.system_ui_control_center_no_tile_labels_title,
            summaryRes = R.string.system_ui_control_center_no_tile_labels_summary,
            parentTitleRes = R.string.system_ui_control_center_custom_qs_color_general_switch,
            parentKey = "control_center_custom_qs_color_general"
        ),
        systemUiControlCenter.item(
            id = "control_center_media_output_dialog_center",
            titleRes = R.string.system_ui_media_output_dialog_center_title,
            summaryRes = R.string.system_ui_media_output_dialog_center_summary
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_date_setting",
            titleRes = R.string.system_ui_control_center_custom_date_setting_title,
            summaryRes = R.string.system_ui_control_center_custom_date_setting_summary
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_clock_text_size",
            titleRes = R.string.system_ui_control_center_custom_clock_text_size_title,
            parentTitleRes = R.string.system_ui_control_center_custom_date_setting_title,
            parentKey = "control_center_custom_date_setting"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_clock_letter_spacing",
            titleRes = R.string.system_ui_control_center_custom_clock_letter_spacing_title,
            parentTitleRes = R.string.system_ui_control_center_custom_date_setting_title,
            parentKey = "control_center_custom_date_setting"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_clock_text_color",
            titleRes = R.string.system_ui_control_center_custom_clock_text_color_title,
            parentTitleRes = R.string.system_ui_control_center_custom_date_setting_title,
            parentKey = "control_center_custom_date_setting"
        ),
        systemUiControlCenter.item(
            id = "control_center_custom_clock_text_bold",
            titleRes = R.string.system_ui_control_center_custom_clock_text_bold_title,
            summaryRes = R.string.system_ui_control_center_use_bold_date,
            parentTitleRes = R.string.system_ui_control_center_custom_date_setting_title,
            parentKey = "control_center_custom_date_setting"
        ),
        systemUiControlCenter.item(
            id = "control_center_expand_qs_panel_portrait",
            titleRes = R.string.system_ui_control_center_expand_qs_panel_portrait_title,
            summaryRes = R.string.system_ui_control_center_expand_qs_panel_portrait_summary
        ),
        systemUiControlCenter.item(
            id = "control_center_panel_width_percent",
            titleRes = R.string.system_ui_control_center_panel_width_percent_title,
            summaryRes = R.string.system_ui_control_center_panel_width_percent_summary,
            parentTitleRes = R.string.system_ui_control_center_expand_qs_panel_portrait_title,
            parentKey = "control_center_expand_qs_panel_portrait"
        ),
        systemUiControlCenter.item(
            id = "control_center_tile_columns",
            titleRes = R.string.system_ui_control_center_tile_columns_title,
            summaryRes = R.string.system_ui_control_center_tile_columns_summary,
            parentTitleRes = R.string.system_ui_control_center_expand_qs_panel_portrait_title,
            parentKey = "control_center_expand_qs_panel_portrait"
        ),

        systemUiAnimationWallpaper.item(
            id = "animation_no_charging_animation",
            titleRes = R.string.system_ui_animation_no_charging_animation_enable_title,
            summaryRes = R.string.system_ui_animation_no_charging_animation_enable_summary
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_charge_animation_fix",
            titleRes = R.string.system_ui_animation_charge_animation_fix,
            summaryRes = R.string.system_ui_animation_charge_animation_fix_summary
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_custom_charge_animation",
            titleRes = R.string.system_ui_animation_custom_charge_animation_title
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_custom_charge_animation_portrait",
            titleRes = R.string.system_ui_animation_custom_charge_animation_portrait_action_title,
            parentTitleRes = R.string.system_ui_animation_custom_charge_animation_title,
            parentKey = "animation_custom_charge_animation"
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_custom_charge_animation_land",
            titleRes = R.string.system_ui_animation_custom_charge_animation_land_action_title,
            parentTitleRes = R.string.system_ui_animation_custom_charge_animation_title,
            parentKey = "animation_custom_charge_animation"
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_charge_anim_duration",
            titleRes = R.string.system_ui_animation_charge_anim_duration_title,
            summaryRes = R.string.system_ui_animation_charge_anim_duration_summary
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_charge_anim_duration_slider",
            titleRes = R.string.system_ui_animation_charge_anim_duration_slider_title,
            summaryRes = R.string.system_ui_animation_charge_anim_duration_slider_summary,
            parentTitleRes = R.string.system_ui_animation_charge_anim_duration_title,
            parentKey = "animation_charge_anim_duration"
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_desktop_live_wallpaper",
            titleRes = R.string.system_ui_animation_desktop_live_wallpaper_title
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_desktop_live_wallpaper_scale_mode",
            titleRes = R.string.system_ui_animation_desktop_live_wallpaper_scale_mode_title,
            parentTitleRes = R.string.system_ui_animation_desktop_live_wallpaper_title,
            parentKey = "animation_desktop_live_wallpaper"
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_desktop_live_wallpaper_portrait",
            titleRes = R.string.system_ui_animation_desktop_live_wallpaper_select_portrait,
            parentTitleRes = R.string.system_ui_animation_desktop_live_wallpaper_title,
            parentKey = "animation_desktop_live_wallpaper"
        ),
        systemUiAnimationWallpaper.item(
            id = "animation_desktop_live_wallpaper_land",
            titleRes = R.string.system_ui_animation_desktop_live_wallpaper_select_land,
            parentTitleRes = R.string.system_ui_animation_desktop_live_wallpaper_title,
            parentKey = "animation_desktop_live_wallpaper"
        ),

        systemUiMisc.item(
            id = "system_ui_misc_disable_guest_user",
            titleRes = R.string.system_ui_misc_disable_guest_user_enable_title,
            summaryRes = R.string.system_ui_misc_disable_guest_user_enable_summary
        ),
        systemUiMisc.item(
            id = "system_ui_misc_disable_biometric_error_vibration",
            titleRes = R.string.system_ui_misc_disable_biometric_error_vibration_title
        ),
        systemUiMisc.item(
            id = "system_ui_misc_bypass_face_auth_timeout",
            titleRes = R.string.system_ui_misc_bypass_face_auth_timeout_title,
            summaryRes = R.string.system_ui_misc_bypass_face_auth_timeout_summary
        ),

        framework.item(
            id = "framework_keep_rotation",
            titleRes = R.string.system_framework_keep_rotation_enable_title,
            summaryRes = R.string.system_framework_keep_rotation_enable_summary
        ),
        framework.item(
            id = "framework_disable_hbm_thermal_limit",
            titleRes = R.string.system_framework_disable_hbm_thermal_limit_title
        ),
        framework.item(
            id = "framework_force_on_off_animation",
            titleRes = R.string.system_framework_force_on_off_animation,
            summaryRes = R.string.system_framework_force_on_off_animation_summary
        ),
        framework.item(
            id = "framework_screen_on_off_animation_duration",
            titleRes = R.string.system_framework_screen_on_off_animation_duration,
            parentTitleRes = R.string.system_framework_force_on_off_animation,
            parentKey = "framework_force_on_off_animation"
        ),
        framework.item(
            id = "framework_disable_zui_applist",
            titleRes = R.string.system_framework_disable_zui_applist_enable_title,
            summaryRes = R.string.system_framework_disable_zui_applist_enable_summary
        ),
        framework.item(
            id = "framework_allow_relative_app_launch",
            titleRes = R.string.system_framework_allow_relative_app_launch_title
        ),
        framework.item(
            id = "framework_allow_untrusted_touch",
            titleRes = R.string.system_framework_allow_untrusted_touch
        ),
        framework.item(
            id = "framework_pkgmgr_allow_downgrade",
            titleRes = R.string.system_framework_pkgmgr_allow_downgrade_title,
            summaryRes = R.string.system_framework_pkgmgr_allow_downgrade_summary
        ),
        framework.item(
            id = "framework_pkgmgr_bypass_verification",
            titleRes = R.string.system_framework_pkgmgr_bypass_verification_title,
            summaryRes = R.string.system_framework_pkgmgr_bypass_verification_summary
        ),
        framework.item(
            id = "framework_pkgmgr_disable_verification_agent",
            titleRes = R.string.system_framework_pkgmgr_disable_verification_agent_title,
            summaryRes = R.string.system_framework_pkgmgr_disable_verification_agent_summary
        ),
        framework.item(
            id = "framework_pkgmgr_bypass_digest",
            titleRes = R.string.system_framework_pkgmgr_bypass_digest_title,
            summaryRes = R.string.system_framework_pkgmgr_bypass_digest_summary
        ),
        framework.item(
            id = "framework_pkgmgr_use_previous_signatures",
            titleRes = R.string.system_framework_pkgmgr_use_previous_signatures_title,
            summaryRes = R.string.system_framework_pkgmgr_use_previous_signatures_summary,
            parentTitleRes = R.string.system_framework_pkgmgr_bypass_digest_title,
            parentKey = "framework_pkgmgr_bypass_digest"
        ),
        framework.item(
            id = "framework_pkgmgr_bypass_exact_sig_match",
            titleRes = R.string.system_framework_pkgmgr_bypass_exact_sig_match_title,
            summaryRes = R.string.system_framework_pkgmgr_bypass_exact_sig_match_summary
        ),
        framework.item(
            id = "framework_pkgmgr_bypass_shared_user",
            titleRes = R.string.system_framework_pkgmgr_bypass_shared_user_title,
            summaryRes = R.string.system_framework_pkgmgr_bypass_shared_user_summary
        ),
        framework.item(
            id = "framework_pkgmgr_allow_hidden_apis",
            titleRes = R.string.system_framework_pkgmgr_allow_hidden_apis_title,
            summaryRes = R.string.system_framework_pkgmgr_allow_hidden_apis_summary
        ),
        framework.item(
            id = "framework_pkgmgr_bypass_arsc",
            titleRes = R.string.system_framework_pkgmgr_bypass_arsc_title,
            summaryRes = R.string.system_framework_pkgmgr_bypass_arsc_summary
        ),
        framework.item(
            id = "framework_force_relative_app_freeform",
            titleRes = R.string.system_framework_force_relative_app_freeform_title
        ),
        framework.item(
            id = "framework_disable_flag_secure",
            titleRes = R.string.system_framework_disable_flag_secure_title,
            summaryRes = R.string.system_framework_disable_flag_secure_summary
        ),
        framework.item(
            id = "framework_ai_input_expand",
            titleRes = R.string.system_framework_ai_input_expand_title,
            summaryRes = R.string.system_framework_ai_input_expand_summary
        ),

        settingsDetail.item(
            id = "settings_detail_remove_blacklist",
            titleRes = R.string.settings_embedding_setting_remove_blacklist,
            summaryRes = R.string.settings_embedding_setting_remove_blacklist_summary
        ),
        settingsDetail.item(
            id = "settings_detail_role_module",
            titleRes = R.string.settings_role_module_title,
            summaryRes = R.string.settings_role_module_summary
        ),
        settingsDetail.item(
            id = "settings_detail_custom_landscape_view",
            titleRes = R.string.settings_custom_landscape_view,
            summaryRes = R.string.settings_custom_landscape_view_summary
        ),
        settingsDetail.item(
            id = "settings_detail_custom_landscape_result",
            titleRes = R.string.settings_custom_landscape_result_title,
            summaryRes = R.string.settings_custom_landscape_result_summary
        ),
        settingsDetail.item(
            id = "settings_detail_yi_shi_jie_rules",
            titleRes = R.string.settings_yi_shi_jie_rules,
            summaryRes = R.string.settings_yi_shi_jie_rules_summary
        ),
        settingsDetail.item(
            id = "settings_detail_zui_force_split",
            titleRes = R.string.settings_zui_force_split_title,
            summaryRes = R.string.settings_zui_force_split_summary
        ),
        settingsDetail.item(
            id = "settings_detail_zui_force_freeform",
            titleRes = R.string.settings_zui_force_freeform_title,
            summaryRes = R.string.settings_zui_force_freeform_summary
        ),
        settingsDetail.item(
            id = "settings_detail_zui_force_fixed",
            titleRes = R.string.settings_zui_force_fixed_title,
            summaryRes = R.string.settings_zui_force_fixed_summary
        ),
        settingsDetail.item(
            id = "settings_detail_float_app_mandatory",
            titleRes = R.string.settings_float_app_mandatory,
            summaryRes = R.string.settings_float_app_mandatory_summary
        ),
        settingsDetail.item(
            id = "settings_detail_split_screen_mandatory",
            titleRes = R.string.settings_split_screen_mandatory_title,
            summaryRes = R.string.settings_split_screen_mandatory_summary
        ),
        settingsDetail.item(
            id = "settings_detail_import_font",
            titleRes = R.string.settings_import_font_title,
            summaryRes = R.string.settings_import_font_summary
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_master",
            titleRes = R.string.settings_about_device_info_master,
            summaryRes = R.string.settings_about_device_info_master_summary
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_model",
            titleRes = R.string.settings_about_device_info_model_title,
            parentTitleRes = R.string.settings_about_device_info_master,
            parentKey = "settings_detail_about_device_info_master"
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_cpu",
            titleRes = R.string.settings_about_device_info_cpu_title,
            parentTitleRes = R.string.settings_about_device_info_master,
            parentKey = "settings_detail_about_device_info_master"
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_ram",
            titleRes = R.string.settings_about_device_info_ram_title,
            parentTitleRes = R.string.settings_about_device_info_master,
            parentKey = "settings_detail_about_device_info_master"
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_rom",
            titleRes = R.string.settings_about_device_info_rom_title,
            parentTitleRes = R.string.settings_about_device_info_master,
            parentKey = "settings_detail_about_device_info_master"
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_software",
            titleRes = R.string.settings_about_device_info_software_title,
            parentTitleRes = R.string.settings_about_device_info_master,
            parentKey = "settings_detail_about_device_info_master"
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_header",
            titleRes = R.string.settings_about_device_info_header_title,
            parentTitleRes = R.string.settings_about_device_info_master,
            parentKey = "settings_detail_about_device_info_master"
        ),
        settingsDetail.item(
            id = "settings_detail_about_device_info_header_action",
            titleRes = R.string.settings_about_device_info_header_action,
            summaryRes = R.string.settings_about_device_info_header_action_summary,
            parentTitleRes = R.string.settings_about_device_info_header_title,
            parentKey = "settings_detail_about_device_info_header"
        ),
        settingsDetail.item(
            id = "settings_detail_native_permission_controller",
            titleRes = R.string.settings_native_permission_controller_enable_title,
            summaryRes = R.string.settings_native_permission_controller_enable_summary
        ),
        settingsDetail.item(
            id = "settings_detail_allow_adding_language",
            titleRes = R.string.settings_allow_adding_language
        ),
        settingsDetail.item(
            id = "settings_detail_allow_disable_dolby",
            titleRes = R.string.settings_allow_disable_dolby,
            summaryRes = R.string.settings_allow_disable_dolby_summary
        ),
        settingsDetail.item(
            id = "settings_detail_app_details_completion",
            titleRes = R.string.settings_app_details_completion,
            summaryRes = R.string.settings_app_details_completion_summary
        ),
        settingsDetail.item(
            id = "settings_detail_app_icon_unmask",
            titleRes = R.string.settings_app_icon_unmask_title,
            summaryRes = R.string.settings_app_icon_unmask_summary
        ),

        gameTool.item(
            id = "game_tool_game_audio",
            titleRes = R.string.game_tool_game_audio_title,
            summaryRes = R.string.game_tool_game_audio_summary
        ),
        gameTool.item(
            id = "game_tool_device_model_disguise",
            titleRes = R.string.game_tool_device_model_disguise,
            summaryRes = R.string.game_tool_device_model_disguise_summary
        ),
        gameTool.item(
            id = "game_tool_fix_cpu_frequency",
            titleRes = R.string.game_tool_f_ix_cpu_frequency,
            summaryRes = R.string.game_tool_f_ix_cpu_frequency_summary
        ),
        gameTool.item(
            id = "game_tool_fix_soc_temp",
            titleRes = R.string.game_tool_fix_soc_temp,
            summaryRes = R.string.game_tool_fix_soc_temp_summary
        ),
        gameTool.item(
            id = "game_tool_auto_open_prevent_touch",
            titleRes = R.string.game_tool_auto_open_prevent_touch_title,
            summaryRes = R.string.game_tool_auto_open_prevent_touch_summary
        ),
        gameTool.item(
            id = "game_tool_whitelist_config",
            titleRes = R.string.game_tool_whitelist_config_title,
            parentTitleRes = R.string.game_tool_auto_open_prevent_touch_title,
            parentKey = "game_tool_auto_open_prevent_touch"
        ),

        ota.item(
            id = "ota_disable_update",
            titleRes = R.string.system_update_ota_disable_title,
            summaryRes = R.string.system_update_ota_disable_summary
        ),
        ota.item(
            id = "ota_disable_auto_install",
            titleRes = R.string.system_update_disable_ota_auto_install_title
        ),
        ota.item(
            id = "ota_block_install_dialog",
            titleRes = R.string.system_update_block_ota_install_dialog_title
        ),
        ota.item(
            id = "ota_hide_update_hint",
            titleRes = R.string.system_update_hide_ota_update_hint
        ),
        ota.item(
            id = "ota_disable_notification_and_red_dot",
            titleRes = R.string.system_update_disable_ota_notification_and_red_dot_title,
            summaryRes = R.string.system_update_disable_ota_notification_and_red_dot_summary
        ),
        ota.item(
            id = "ota_info_fetch",
            titleRes = R.string.system_update_ota_info_fetch_title
        ),
        ota.item(
            id = "ota_pc_flash_firmware_fetch",
            titleRes = R.string.system_update_pc_flash_firmware_fetch_title
        ),

        tbEngine.item(
            id = "tb_engine_disable_auto_download",
            titleRes = R.string.tb_engine_disable_auto_download_title,
            summaryRes = R.string.tb_engine_disable_auto_download_summary
        ),
        tbEngine.item(
            id = "tb_engine_disable_auto_install",
            titleRes = R.string.tb_engine_disable_auto_install_title,
            summaryRes = R.string.tb_engine_disable_auto_install_summary
        ),
        tbEngine.item(
            id = "tb_engine_disable_app_update",
            titleRes = R.string.tb_engine_disable_app_update_title,
            summaryRes = R.string.tb_engine_disable_app_update_summary
        ),
        tbEngine.item(
            id = "tb_engine_disable_push",
            titleRes = R.string.tb_engine_disable_push_title,
            summaryRes = R.string.tb_engine_disable_push_summary
        ),
        tbEngine.item(
            id = "tb_engine_disable_reporting",
            titleRes = R.string.tb_engine_disable_reporting_title,
            summaryRes = R.string.tb_engine_disable_reporting_summary
        ),
        tbEngine.item(
            id = "tb_engine_sign_local_ota",
            titleRes = R.string.tb_engine_sign_local_ota_title,
            summaryRes = R.string.tb_engine_sign_local_ota_summary
        ),
        tbEngine.item(
            id = "tb_engine_custom_params",
            titleRes = R.string.system_update_custom_params_title
        ),

        packageInstaller.item(
            id = "package_installer_enable_row_style",
            titleRes = R.string.package_installer_enable_row_style_title,
            summaryRes = R.string.package_installer_enable_row_style_summary
        ),
        packageInstaller.item(
            id = "package_installer_disable_installer_ad",
            titleRes = R.string.package_installer_disable_installer_ad_title,
            summaryRes = R.string.package_installer_disable_installer_ad_summary,
            parentTitleRes = R.string.package_installer_enable_row_style_title,
            parentKey = "package_installer_enable_row_style",
            parentMode = SearchParentMode.REQUIRE_OFF
        ),
        packageInstaller.item(
            id = "package_installer_disable_delete_package",
            titleRes = R.string.package_installer_disable_delete_package_title,
            summaryRes = R.string.package_installer_disable_delete_package_summary,
            parentTitleRes = R.string.package_installer_enable_row_style_title,
            parentKey = "package_installer_enable_row_style",
            parentMode = SearchParentMode.REQUIRE_OFF
        ),
        packageInstaller.item(
            id = "package_installer_disable_scan_apk",
            titleRes = R.string.package_installer_disable_scan_apk_title,
            summaryRes = R.string.package_installer_disable_scan_apk_summary,
            parentTitleRes = R.string.package_installer_enable_row_style_title,
            parentKey = "package_installer_enable_row_style",
            parentMode = SearchParentMode.REQUIRE_OFF
        ),
        packageInstaller.item(
            id = "package_installer_only_allow",
            titleRes = R.string.package_installer_only_allow_title,
            summaryRes = R.string.package_installer_only_allow_summary,
            parentTitleRes = R.string.package_installer_enable_row_style_title,
            parentKey = "package_installer_enable_row_style",
            parentMode = SearchParentMode.REQUIRE_OFF
        ),
        packageInstaller.item(
            id = "package_installer_skip_warn_page",
            titleRes = R.string.package_installer_skip_warn_page_title,
            summaryRes = R.string.package_installer_skip_warn_page_summary,
            parentTitleRes = R.string.package_installer_enable_row_style_title,
            parentKey = "package_installer_enable_row_style",
            parentMode = SearchParentMode.REQUIRE_OFF
        ),

        launcher.item(
            id = "launcher_no_label_mode",
            titleRes = R.string.launcher_launcher_no_label_mode_title
        ),
        launcher.item(
            id = "launcher_drawer_no_label_mode",
            titleRes = R.string.launcher_launcher_drawer_no_label_mode_title
        ),
        launcher.item(
            id = "launcher_hide_blue_point",
            titleRes = R.string.launcher_launcher_hide_blue_point_title
        ),
        launcher.item(
            id = "launcher_cloud_folder_auto_dismiss",
            titleRes = R.string.launcher_cloud_folder_auto_dismiss_title
        ),
        launcher.item(
            id = "launcher_custom_grid",
            titleRes = R.string.launcher_custom_grid_title,
            summaryRes = R.string.launcher_custom_grid_summary
        ),
        launcher.item(
            id = "launcher_custom_grid_row",
            titleRes = R.string.launcher_input_row_number_here,
            parentTitleRes = R.string.launcher_custom_grid_title,
            parentKey = "launcher_custom_grid"
        ),
        launcher.item(
            id = "launcher_custom_grid_column",
            titleRes = R.string.launcher_input_column_number_here,
            parentTitleRes = R.string.launcher_custom_grid_title,
            parentKey = "launcher_custom_grid"
        ),
        launcher.item(
            id = "launcher_big_folder_align",
            titleRes = R.string.launcher_big_folder_align_title,
            summaryRes = R.string.launcher_big_folder_align_summary
        ),
        launcher.item(
            id = "launcher_app_icon_unmask",
            titleRes = R.string.launcher_app_icon_unmask_title,
            summaryRes = R.string.launcher_app_icon_unmask_summary
        ),
        launcher.item(
            id = "launcher_app_icon_unmask_dynamic",
            titleRes = R.string.launcher_app_icon_unmask_dynamic_title,
            summaryRes = R.string.launcher_app_icon_unmask_dynamic_summary,
            parentTitleRes = R.string.launcher_app_icon_unmask_title,
            parentKey = "launcher_app_icon_unmask"
        ),
        launcher.item(
            id = "launcher_disable_recent_app_display",
            titleRes = R.string.launcher_disable_recent_app_display
        ),
        launcher.item(
            id = "launcher_larger_dock",
            titleRes = R.string.launcher_larger_dock_title,
            parentTitleRes = R.string.launcher_disable_dock_bar_title,
            parentKey = "launcher_disable_dock_bar",
            parentMode = SearchParentMode.REQUIRE_OFF
        ),
        launcher.item(
            id = "launcher_disable_dock_bar",
            titleRes = R.string.launcher_disable_dock_bar_title,
            summaryRes = R.string.launcher_disable_dock_bar_summary
        ),
        launcher.item(
            id = "launcher_force_stop_mode",
            titleRes = R.string.launcher_disable_force_stop_enable_title,
            summaryRes = R.string.launcher_disable_force_stop_enable_summary
        ),
        launcher.item(
            id = "launcher_show_ram_info",
            titleRes = R.string.launcher_show_ram_info,
            summaryRes = R.string.launcher_show_ram_info_summary
        ),
        launcher.item(
            id = "launcher_beautify_ram_info",
            titleRes = R.string.launcher_beautify_ram_info,
            summaryRes = R.string.launcher_beautify_ram_info_summary,
            parentTitleRes = R.string.launcher_show_ram_info,
            parentKey = "launcher_show_ram_info"
        ),
        launcher.item(
            id = "launcher_batch_uninstall",
            titleRes = R.string.launcher_launcher_batch_uninstall
        ),
        launcher.item(
            id = "launcher_clean_search",
            titleRes = R.string.launcher_clean_search
        ),
        launcher.item(
            id = "launcher_remove_search_recommend",
            titleRes = R.string.launcher_remove_search_recommend,
            parentTitleRes = R.string.launcher_clean_search,
            parentKey = "launcher_clean_search"
        ),
        launcher.item(
            id = "launcher_remove_hot_word_view",
            titleRes = R.string.launcher_remove_hot_word_view,
            parentTitleRes = R.string.launcher_clean_search,
            parentKey = "launcher_clean_search"
        ),

        mobileDesktop.item(
            id = "mobile_desktop_skip_nearby_exposure_warn",
            titleRes = R.string.mobile_desktop_skip_nearby_exposure_warn,
            summaryRes = R.string.mobile_desktop_skip_nearby_exposure_warn_summary
        ),
        mobileDesktop.item(
            id = "mobile_desktop_auto_accept_file_transfer",
            titleRes = R.string.mobile_desktop_auto_accept_file_transfer,
            summaryRes = R.string.mobile_desktop_auto_accept_file_transfer_summary
        ),
        mobileDesktop.item(
            id = "mobile_desktop_disable_nearby_share_auto_shutdown",
            titleRes = R.string.mobile_desktop_disable_nearby_share_auto_shutdown_title
        ),

        safeCenter.item(
            id = "safe_center_default_allow_autorun",
            titleRes = R.string.safe_center_default_allow_autorun_enable_title,
            summaryRes = R.string.safe_center_default_allow_autorun_enable_summary
        ),
        safeCenter.item(
            id = "safe_center_disable_all_virus_scan",
            titleRes = R.string.safe_center_disable_all_virus_scan,
            summaryRes = R.string.safe_center_disable_all_virus_scan_summary
        ),
        safeCenter.item(
            id = "safe_center_bypass_documents_ui",
            titleRes = R.string.safe_center_bypass_docements_ui,
            summaryRes = R.string.safe_center_bypass_docements_ui_summary
        ),

        zuiPerformance.item(
            id = "zui_pp_block_power_policy",
            titleRes = R.string.zui_pp_block_power_policy_title,
            summaryRes = R.string.zui_pp_block_power_policy_summary
        ),
        zuiPerformance.item(
            id = "zui_pp_block_game_policy",
            titleRes = R.string.zui_pp_block_game_policy_title,
            summaryRes = R.string.zui_pp_block_game_policy_summary
        ),

        appSettings.item(
            id = "app_settings_backup_config",
            titleRes = R.string.page_settings_backup_config_to_file
        ),
        appSettings.item(
            id = "app_settings_restore_config",
            titleRes = R.string.page_settings_restore_config_from_file
        ),
        appSettings.item(
            id = "app_settings_restore_default",
            titleRes = R.string.page_settings_restore_default_config
        ),
        appSettings.item(
            id = "app_settings_display_entry_in_settings",
            titleRes = R.string.page_settings_display_entry_in_settings,
            summaryRes = R.string.page_settings_display_entry_in_settings_summary
        ),
        appSettings.item(
            id = "app_settings_auto_check_update",
            titleRes = R.string.page_settings_auto_check_update_title
        ),
        appSettings.item(
            id = "app_settings_enable_detailed_logging",
            titleRes = R.string.page_settings_enable_detailed_logging,
            summaryRes = R.string.page_settings_enable_detailed_logging_description
        ),
        appSettings.item(
            id = "app_settings_export_logs",
            titleRes = R.string.page_settings_export_logs
        ),
        appSettings.item(
            id = "app_settings_delete_all_logs",
            titleRes = R.string.page_settings_delete_all_logs,
            summaryRes = R.string.page_settings_delete_all_logs_summary
        ),
        appSettings.item(
            id = "app_settings_ui_theme",
            titleRes = R.string.page_settings_app_ui_theme_settings
        ),
        appSettings.item(
            id = "app_settings_language",
            titleRes = R.string.page_settings_app_language_settings
        ),
        appSettings.item(
            id = "app_settings_advanced",
            titleRes = R.string.page_settings_advanced_title
        ),
        appSettings.item(
            id = "app_settings_about",
            titleRes = R.string.page_settings_show_about_page
        ),

        themeSettings.item(
            id = "theme_frontend_style",
            titleRes = R.string.page_settings_frontend_style_title
        ),
        themeSettings.item(
            id = "theme_theme_mode",
            titleRes = R.string.page_settings_theme_mode_title
        ),
        themeSettings.item(
            id = "theme_material_color_spec",
            titleRes = R.string.page_settings_material_color_spec_title
        ),
        themeSettings.item(
            id = "theme_material_palette_mode",
            titleRes = R.string.page_settings_material_palette_mode_title
        ),
        themeSettings.item(
            id = "theme_predictive_back_gesture",
            titleRes = R.string.page_settings_predictive_back_gesture_title,
            summaryRes = R.string.page_settings_predictive_back_gesture_summary
        ),
        themeSettings.item(
            id = "theme_amoled_black",
            titleRes = R.string.page_settings_amoled_black_title,
            summaryRes = R.string.page_settings_amoled_black_summary,
            conditionNoteRes = R.string.search_condition_m3_expressive_only
        ),
        themeSettings.item(
            id = "theme_dynamic_color",
            titleRes = R.string.page_settings_dynamic_color_title,
            summaryRes = R.string.page_settings_dynamic_color_summary
        ),
        themeSettings.item(
            id = "theme_manual_color",
            titleRes = R.string.page_settings_manual_color_title,
            summaryRes = R.string.page_settings_manual_color_summary
        ),
        themeSettings.item(
            id = "theme_manual_seed_color",
            titleRes = R.string.page_settings_manual_seed_color_title,
            summaryRes = R.string.page_settings_manual_seed_color_summary,
            parentTitleRes = R.string.page_settings_manual_color_title,
            parentKey = "theme_manual_color"
        ),
        themeSettings.item(
            id = "theme_enable_floating_bottom_bar",
            titleRes = R.string.page_settings_enable_floating_bottom_bar_title,
            summaryRes = R.string.page_settings_enable_floating_bottom_bar_summary,
            conditionNoteRes = R.string.search_condition_miuix_only
        ),
        themeSettings.item(
            id = "theme_floating_bottom_bar_blur",
            titleRes = R.string.page_settings_enable_floating_bottom_bar_blur_title,
            summaryRes = R.string.page_settings_enable_floating_bottom_bar_blur_summary,
            parentTitleRes = R.string.page_settings_enable_floating_bottom_bar_title,
            parentKey = "theme_enable_floating_bottom_bar"
        ),

        advancedSettings.item(
            id = "advanced_refresh_dex_index",
            titleRes = R.string.page_settings_refresh_dex_index
        ),
        advancedSettings.item(
            id = "advanced_reset_persistent_values",
            titleRes = R.string.page_settings_advanced_reset_title
        ),
        advancedSettings.item(
            id = "advanced_delete_ota_package",
            titleRes = R.string.page_settings_advanced_delete_ota_package_title,
            summaryRes = R.string.page_settings_advanced_delete_ota_package_summary
        ),
        advancedSettings.item(
            id = "advanced_hot_reload",
            titleRes = R.string.page_settings_advanced_hot_reload_title
        ),
        advancedSettings.item(
            id = "advanced_open_firstrun",
            titleRes = R.string.page_settings_advanced_open_firstrun_title,
            summaryRes = R.string.page_settings_advanced_open_firstrun_summary
        ),

        aboutScreen.item(
            id = "about_dev_qimian233",
            titleRes = R.string.about_dev_qimian233,
            summaryRes = R.string.page_settings_about_qimian233_summary
        ),
        aboutScreen.item(
            id = "about_dev_wasd_destroy",
            titleRes = R.string.about_dev_wasd_destroy,
            summaryRes = R.string.page_settings_about_wasd_destroy_summary
        ),
        aboutScreen.item(
            id = "about_dev_uuuddddl",
            titleRes = R.string.about_dev_uuuddddl,
            summaryRes = R.string.page_settings_about_uuuddddl
        ),
        aboutScreen.item(
            id = "about_credit_unfuck_zui",
            titleRes = R.string.page_settings_credits_unfuck_zui,
            summaryRes = R.string.page_settings_about_unfuckzui_summary
        ),
        aboutScreen.item(
            id = "about_credit_zuxos_plus",
            titleRes = R.string.page_settings_credits_zuxos_plus,
            summaryRes = R.string.page_settings_about_zuxos_plus_summary
        ),
        aboutScreen.item(
            id = "about_credit_github_acceleration",
            titleRes = R.string.page_settings_credits_github_acceleration,
            summaryRes = R.string.page_settings_credits_github_acceleration_site
        ),
        aboutScreen.item(
            id = "about_view_source",
            titleRes = R.string.page_settings_about_view_source_title
        ),
        aboutScreen.item(
            id = "about_view_issues",
            titleRes = R.string.page_settings_about_view_issues_title,
            summaryRes = R.string.page_settings_about_view_issues_summary
        ),
        aboutScreen.item(
            id = "about_check_update",
            titleRes = R.string.page_settings_about_app_update_title,
            summaryRes = R.string.page_settings_about_app_update_placeholder_summary
        )
    )

    private val byIdMap: Map<String, SearchEntry> by lazy { all.associateBy { it.id } }

    fun byId(id: String): SearchEntry? = byIdMap[id]

    /**
     * Navigation route for a search result: the entry's destination with its id
     * attached as the optional highlight target. Feature cards navigate bare (cards
     * are not rows and never highlight).
     */
    fun targetRoute(entry: SearchEntry): String {
        if (entry.isFeatureCard) return entry.route
        return "${entry.route}?target=${java.net.URLEncoder.encode(entry.id, "UTF-8")}"
    }

    init {
        // Let the highlight controller resolve parent fallbacks without a hard
        // dependency from ui/components on the search package.
        SearchHighlightIndexBridge.parentKeyOf = { id -> byId(id)?.parentKey }
        SearchHighlightIndexBridge.routeOfId = { id -> byId(id)?.route }
    }
}
