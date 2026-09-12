package com.qimian233.ztool.data.keys

/**
 * 所有 xposed_module_config 偏好键的单一可信源。
 *
 * 每个键按数据类型分类，同时作为具名常量暴露，供 Repository、Hook、以及
 * ModulePreferencesUtils 的备份/恢复类型推断使用。
 *
 * 用法示例：
 * - Kotlin:  PreferenceKeys.DISABLE_FORCE_STOP.name
 * - Java:    PreferenceKeys.DISABLE_FORCE_STOP.getName()
 */
data class BoolKey(val name: String, val default: Boolean)
data class IntKey(val name: String, val default: Int)
data class FloatKey(val name: String, val default: Float)
data class StringKey(val name: String, val default: String)

object PreferenceKeys {

    // Boolean 键（Hook 启用开关 + 子功能开关 + 应用设置）

    // ── 系统框架 ──
    val DISABLE_FLAG_SECURE = BoolKey("disable_flag_secure", false)
    val NO_MORE_PASSWORD_PER_24H = BoolKey("NoMorePasswordPer24H", false)
    val ALLOW_GET_PACKAGES = BoolKey("allow_get_packages", false)
    val ALLOW_UNTRUSTED_TOUCH = BoolKey("allow_untrusted_touch", false)
    val FORCE_SCREEN_ON_OFF_ANIMATION = BoolKey("force_screen_on_off_animation", false)
    val AI_INPUT_EXPAND = BoolKey("ai_input_expand", false)
    val KEEP_ROTATION = BoolKey("keep_rotation", false)
    val ALLOW_RELATIVE_APP_LAUNCH = BoolKey("allow_relative_app_launch", false)
    val FORCE_RELATIVE_APP_FREEFORM = BoolKey("force_relative_app_freeform", false)
    val DISABLE_HBM_THERMAL_LIMIT = BoolKey("disable_hbm_thermal_limit", false)

    // ── SystemUI ──
    val STATUSBAR_DISPLAY_SECONDS = BoolKey("StatusBarDisplay_Seconds", false)
    val CUSTOM_STATUSBAR_CLOCK = BoolKey("Custom_StatusBarClock", false)
    val SYSTEMUI_CHARGE_WATTS = BoolKey("systemui_charge_watts", false)
    val SYSTEMUI_REAL_WATTS = BoolKey("systemUI_RealWatts", false)
    val NOTIFICATION_ICON_LIMIT = BoolKey("notification_icon_limit", false)
    val CUSTOM_CONTROL_CENTER_DATE = BoolKey("Custom_ControlCenterDate", false)
    val CONTROL_CENTER_NO_TILE_LABELS = BoolKey("control_center_no_tile_labels", false)
    val MEDIA_OUTPUT_DIALOG_CENTER = BoolKey("media_output_dialog_center", false)
    val NO_CHARGE_ANIMATION = BoolKey("No_ChargeAnimation", false)
    val NATIVE_NOTIFICATION_ICON = BoolKey("NativeNotificationIcon", false)
    val SYSTEMUI_NETWORK_SPEED_SIZE = BoolKey("systemui_network_speed_size", false)
    val SYSTEMUI_NETWORK_SPEED_DOUBLELAYER = BoolKey("systemui_network_speed_doublelayer", false)
    val CUSTOM_NETWORK_SPEED_REFRESH_INTERVAL = BoolKey("custom_network_speed_refresh_interval", false)
    val SYSTEMUI_NETWORK_SPEED_HIDE_SLOW = BoolKey("systemui_network_speed_hide_slow", false)
    val SYSTEMUI_NETWORK_SPEED_HIDE_BOTH = BoolKey("systemui_network_speed_hide_both", false)
    val SYSTEMUI_BATTERY_PERCENTAGE = BoolKey("systemui_battery_percentage", false)
    val FORCE_IMMERSIVE_MODE = BoolKey("force_immersive_mode", false)
    val FORCE_LENOVO_AOD = BoolKey("ForceLenovoAOD", false)
    val QS_ROUND_CORNER = BoolKey("qs_round_corner", false)
    val BRIGHTNESS_SLIDER_PERCENTAGE = BoolKey("brightness_slider_percentage", false)
    val VOLUME_SLIDER_PERCENTAGE = BoolKey("volume_slider_percentage", false)
    val QS_COLOR = BoolKey("qs_color", false)
    val NOTIFICATION_CENTER_BLUR = BoolKey("notification_center_blur", false)
    val GUEST_MODE_CONTROLLER = BoolKey("guest_mode_controller", false)
    val EXPAND_QS_PANEL_PORTRAIT = BoolKey("expand_qs_panel_portrait", false)
    val CUSTOMIZE_SLIDER_STYLE = BoolKey("customize_slider_style", false)
    val CUSTOM_CHARGE_ANIMATION = BoolKey("custom_charge_animation", false)
    val CHARGE_ANIMATION_DURATION = BoolKey("charge_animation_duration", false)
    val FORCE_NATIVE_AOD = BoolKey("ForceNativeAOD", false)
    val DISABLE_BIOMETRIC_ERROR_VIBRATION = BoolKey("disable_biometric_error_vibration", false)

    // SystemUI 子功能开关
    val CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE_ENABLED = BoolKey("Custom_StatusBarClockTextSizeEnabled", false)
    val CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING_ENABLED = BoolKey("Custom_StatusBarClockLetterSpacingEnabled", false)
    val CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR_ENABLED = BoolKey("Custom_StatusBarClockTextColorEnabled", false)
    val CUSTOM_STATUSBAR_CLOCK_TEXT_BOLD = BoolKey("Custom_StatusBarClockTextBold", false)
    val CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE_ENABLED = BoolKey("Custom_ControlCenterDateTextSizeEnabled", false)
    val CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING_ENABLED = BoolKey("Custom_ControlCenterDateLetterSpacingEnabled", false)
    val CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR_ENABLED = BoolKey("Custom_ControlCenterDateTextColorEnabled", false)
    val CUSTOM_CONTROL_CENTER_DATE_TEXT_BOLD = BoolKey("Custom_ControlCenterDateTextBold", false)
    val CUSTOM_QS_COLOR = BoolKey("custom_qs_color", false)
    val CUSTOM_LABEL_COLOR = BoolKey("custom_label_color", false)
    val CUSTOM_SECOND_LABEL_COLOR = BoolKey("custom_second_label_color", false)
    val CUSTOMIZE_SLIDER_STYLE_VALUE = BoolKey("customize_slider_style_value", false)

    // SystemUI 内部备份键
    val CUSTOMIZE_SLIDER_STYLE_PREVIOUS = BoolKey("customize_slider_style_previous", false)
    val CUSTOMIZE_SLIDER_STYLE_PREVIOUS_VALUE = BoolKey("customize_slider_style_previous_value", false)

    // SystemUI RealWatts 子功能开关
    val SYSTEMUI_REALWATTS_SHOW_VOLTAGE = BoolKey("systemui_realwatts_show_voltage", false)
    val SYSTEMUI_REALWATTS_SHOW_CURRENT = BoolKey("systemui_realwatts_show_current", false)
    val SYSTEMUI_REALWATTS_SHOW_POWER = BoolKey("systemui_realwatts_show_power", true)
    val SYSTEMUI_REALWATTS_SHOW_TEMPERATURE = BoolKey("systemui_realwatts_show_temperature", false)
    val SYSTEMUI_REALWATTS_SHOW_INDICATOR = BoolKey("systemui_realwatts_show_indicator", true)
    val SYSTEMUI_REALWATTS_CUSTOM_FORMAT_ENABLED = BoolKey("systemui_realwatts_custom_format_enabled", false)

    // ── Settings ──
    val REMOVE_BLACKLIST = BoolKey("remove_blacklist", false)
    val ALLOW_DISPLAY_DOLBY = BoolKey("allow_display_dolby", false)
    val PERMISSION_CONTROLLER_HOOK = BoolKey("PermissionControllerHook", false)
    val AUTO_OWNER_INFO = BoolKey("auto_owner_info", false)
    val SPLIT_SCREEN_MANDATORY = BoolKey("Split_Screen_mandatory", false)
    val APP_DETAILS = BoolKey("app_details", false)
    val ABOUT_DEVICE_INFO = BoolKey("about_device_info", false)
    val ZTOOL_SETTINGS_ENTRY = BoolKey("ztool_settings_entry", false)
    val HIDE_OTA_UPDATE_HINT = BoolKey("hide_ota_update_hint", false)
    val ALLOW_ADD_LANGUAGE = BoolKey("allow_add_language", false)

    // Settings 子功能开关
    val ABOUT_DEVICE_INFO_MODEL_ENABLED = BoolKey("about_device_info_model_enabled", false)
    val ABOUT_DEVICE_INFO_CPU_ENABLED = BoolKey("about_device_info_cpu_enabled", false)
    val ABOUT_DEVICE_INFO_RAM_ENABLED = BoolKey("about_device_info_ram_enabled", false)
    val ABOUT_DEVICE_INFO_ROM_ENABLED = BoolKey("about_device_info_rom_enabled", false)
    val ABOUT_DEVICE_INFO_SOFTWARE_ENABLED = BoolKey("about_device_info_software_enabled", false)
    val ABOUT_DEVICE_INFO_HEADER_ENABLED = BoolKey("about_device_info_header_enabled", false)

    // ── PackageInstaller ──
    val DISABLE_SCAN_APK = BoolKey("disable_scanAPK", false)
    val ALWAYS_ALLOW_PERMISSION = BoolKey("Always_AllowPermission", false)
    val SKIP_WARN_PAGE = BoolKey("Skip_WarnPage", false)
    val DISABLE_INSTALLER_AD = BoolKey("disable_installerAD", false)
    val PACKAGE_INSTALLER_STYLE_HOOK = BoolKey("packageInstallerStyle_hook", false)
    val PACKAGE_INSTALLER_DISABLE_DELETE = BoolKey("package_installer_disable_delete", false)

    // ── Launcher ──
    val DISABLE_FORCE_STOP = BoolKey("disable_force_stop", false)
    val ZUI_LAUNCHER_HOTSEAT = BoolKey("zui_launcher_hotseat", false)
    val CUSTOM_GRID_SIZE = BoolKey("CustomGridSize", false)
    val CLEAN_GLOBAL_SEARCH = BoolKey("clean_global_search", false)
    val DISABLE_DOCK_BAR = BoolKey("disable_dock_bar", false)
    val LAUNCHER_RECENT_TASK_MEMORY_VIEW = BoolKey("launcher_recent_task_memory_view", false)
    val LAUNCHER_NO_LABEL_MODE = BoolKey("launcher_no_label_mode", false)
    val LAUNCHER_DRAWER_NO_LABEL_MODE = BoolKey("launcher_drawer_no_label_mode", false)
    val LAUNCHER_HIDE_BLUE_POINT = BoolKey("launcher_hide_blue_point", false)
    val DISMISS_CLOUD_FOLDER_CONFIRMATION = BoolKey("dismiss_cloud_folder_confirmation", false)
    val DISABLE_RECENT_APPS_DISPLAY = BoolKey("disable_recent_apps_display", false)
    val LAUNCHER_BATCH_UNINSTALL = BoolKey("launcher_batch_uninstall", false)
    val LAUNCHER_BIG_FOLDER_ALIGN = BoolKey("big_folder_align", false)

    // Launcher 子功能开关 + 内部键
    val REMOVE_HOT_WORD_VIEW = BoolKey("remove_hot_word_view", false)
    val REMOVE_SEARCH_RECOMMEND = BoolKey("remove_search_recommend", false)
    val BEAUTIFY_RAM_INFO = BoolKey("beautify_ram_info", false)
    val FORCE_STOP_WHITE_LIST_ENABLE = BoolKey("ForceStopWhiteListEnable", false)
    val ZUI_LAUNCHER_HOTSEAT_BACKUP = BoolKey("zui_launcher_hotseat_backup", false)
    val DISABLE_DOCK_WARNING_CONFIRMED = BoolKey("disable_dock_warning_confirmed", false)

    // ── GameTool ──
    val AUTO_MISTAKE_TOUCH = BoolKey("auto_mistake_touch", false)
    val DISABLE_GAME_AUDIO = BoolKey("disable_GameAudio", false)
    val DISABLE_GAME_AUDIO_APP = BoolKey("disable_GameAudio_app", false)
    val DISGUISE_TB322FC = BoolKey("disguise_TB322FC", false)
    val FIX_CPU_CLOCK = BoolKey("Fix_CpuClock", false)
    val FIX_SOC_TEMP = BoolKey("Fix_SocTemp", false)
    val MISTAKE_TOUCH_WHITE_LIST = BoolKey("MistakeTouchWhiteList", false)

    // ── OTA ──
    val DISABLE_OTA_CHECK = BoolKey("disable_OtaCheck", false)
    val CUSTOM_OTA_PARAMETERS = BoolKey("custom_ota_parameters", false)
    val NO_AUTO_OTA_INSTALL = BoolKey("no_auto_ota_install", false)
    val BLOCK_OTA_INSTALL_DIALOG = BoolKey("block_ota_install_dialog", false)
    val HIDE_OTA_NOTIFICATIONS = BoolKey("hide_ota_notifications", false)

    // ── TB Engine (UDS 实时连接引擎 com.lenovo.tbengine) ──
    val DISABLE_TB_ENGINE_AUTO_DOWNLOAD = BoolKey("disable_tbengine_auto_download", false)
    val DISABLE_TB_ENGINE_AUTO_INSTALL = BoolKey("disable_tbengine_auto_install", false)
    val DISABLE_TB_ENGINE_APP_UPDATE = BoolKey("disable_tbengine_app_update", false)
    val DISABLE_TB_ENGINE_PUSH = BoolKey("disable_tbengine_push", false)
    val SIGN_TB_ENGINE_LOCAL_OTA = BoolKey("sign_tbengine_local_ota", false)

    // ── Wallpaper ──
    val CHARGE_ANIMATION_FIX = BoolKey("charge_animation_fix", false)
    val DESKTOP_LIVE_WALLPAPER = BoolKey("desktop_live_wallpaper", false)

    // ── DocumentsUI ──
    val DOCUMENTS_UI_BYPASS = BoolKey("documents_ui_bypass", false)

    // ── SafeCenter ──
    val DISABLE_ALL_VIRUS_SCANS = BoolKey("disable_all_virus_scans", false)
    val DEFAULT_ENABLE_AUTORUN = BoolKey("default_enable_autorun", false)

    // ── MobileDesktop ──
    val AUTO_ACCEPT_FILE_TRANSFER = BoolKey("auto_accept_file_transfer", false)
    val BYPASS_SHARE_WARNING = BoolKey("bypass_share_warning", false)
    val DISABLE_NEARBY_SHARE_COUNTDOWN = BoolKey("disable_nearby_share_countdown", false)

    // ── 应用设置 / 杂项 ──
    val IS_DETAILED_LOGGING = BoolKey("isDetailedLogging", false)
    val ENABLE_HOMEPAGE_YIYAN = BoolKey("enable_homepage_yiyan", true)
    val AUTO_CHECK_UPDATE = BoolKey("auto_check_update", true)
    val YIYAN = BoolKey("YiYan", false)
    val IS_SYSTEMUI_PERMISSION_CONFIRMED = BoolKey("isSystemUIPermissionConfirmed", false)
    val IS_CONFIG_UPGRADED = BoolKey("isConfigUpgraded", false)

    // ═══════════════════════════════════════════════════════════
    // Int 键
    // ═══════════════════════════════════════════════════════════

    val CUSTOM_LAUNCHER_ROW = IntKey("CustomLauncherRow", 4)
    val CUSTOM_LAUNCHER_COLUMN = IntKey("CustomLauncherColumn", 6)
    val CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR = IntKey("Custom_StatusBarClockTextColor", 0xFFFFFFFF.toInt())
    val NOTIFY_NUM_SIZE = IntKey("notify_num_size", 4)
    val CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR = IntKey("Custom_ControlCenterDateTextColor", 0xFFFFFFFF.toInt())
    val TILE_ROUND_CORNER_RADIUS = IntKey("tile_round_corner_radius", 96)
    val HEAD_UP_ROUND_CORNER_RADIUS = IntKey("head_up_round_corner_radius", 32)
    val CUSTOM_QS_ACTIVE_COLOR_VAL = IntKey("custom_qs_active_color_val", 0xbfadd8e6.toInt())
    val CUSTOM_LABEL_ACTIVE_COLOR_VAL = IntKey("custom_label_active_color_val", 0xFFFFFFFF.toInt())
    val CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = IntKey("custom_second_label_active_color_val", 0xbfffffff.toInt())
    val NOTIFICATION_CENTER_BLUR_PERCENT = IntKey("notification_center_blur_percent", 0)
    val SCREEN_ON_OFF_ANIMATION_MS = IntKey("screen_on_off_animation_ms", 400)
    val QS_PANEL_WIDTH_PERCENT = IntKey("qs_panel_width_percent", 80)
    val QS_TILE_COLUMNS = IntKey("qs_tile_columns", 7)
    val CHARGE_ANIMATION_DURATION_MS = IntKey("charge_animation_duration_ms", 3500)

    // ═══════════════════════════════════════════════════════════
    // Float 键
    // ═══════════════════════════════════════════════════════════

    val CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE = FloatKey("Custom_StatusBarClockTextSize", 16.0f)
    val CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING = FloatKey("Custom_StatusBarClockLetterSpacing", 0.1f)
    val SYSTEMUI_NETWORK_SPEED_REFRESH_INTERVAL = FloatKey("systemui_network_speed_refresh_interval", 3.0f)
    val SYSTEMUI_NETWORK_SPEED_HIDE_THRESHOLD = FloatKey("systemui_network_speed_hide_threshold", 10f)
    val CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE = FloatKey("Custom_ControlCenterDateTextSize", 16.0f)
    val CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING = FloatKey("Custom_ControlCenterDateLetterSpacing", 0.1f)

    // ═══════════════════════════════════════════════════════════
    // String 键
    // ═══════════════════════════════════════════════════════════

    val MISTAKE_TOUCH_WHITE_LIST_GAME = StringKey("MistakeTouchWhiteListGame", "")
    val FORCE_STOP_WHITE_LIST = StringKey("ForceStopWhiteList", "")
    val CUSTOM_OTA_TARGET_VERSION_NAME = StringKey("Custom_ota_target_versionName", "")
    val CUSTOM_OTA_TARGET_DEVICE_ID = StringKey("Custom_ota_target_deviceID", "")
    val ABOUT_DEVICE_INFO_MODEL = StringKey("about_device_info_model", "")
    val ABOUT_DEVICE_INFO_CPU = StringKey("about_device_info_cpu", "")
    val ABOUT_DEVICE_INFO_RAM = StringKey("about_device_info_ram", "")
    val ABOUT_DEVICE_INFO_ROM = StringKey("about_device_info_rom", "")
    val ABOUT_DEVICE_INFO_SOFTWARE = StringKey("about_device_info_software", "")
    val AI_INPUT_EXPAND_SIGNS = StringKey("AI_INPUT_EXPAND_SIGNS", "")
    val CUSTOM_CONTROL_CENTER_DATE_FORMAT = StringKey("Custom_ControlCenterDateFormat", "yyyy年MM月dd日 EEEE")
    val API_URL = StringKey("API_URL", "")
    val REGULAR = StringKey("Regular", "")
    val CUSTOM_STATUSBAR_CLOCK_FORMAT = StringKey("Custom_StatusBarClockFormat", "")
    val CHARGE_WATTS_SELECTED_OPTION = StringKey("charge_watts_selected_option", "")
    val SYSTEMUI_REALWATTS_CUSTOM_FORMAT = StringKey("systemui_realwatts_custom_format", "")
    val DESKTOP_LIVE_WALLPAPER_SCALE_MODE = StringKey("desktop_live_wallpaper_scale_mode", "fit")

    // 按类型分组的列表 —— 供 ModulePreferencesUtils 备份/恢复
    // 以及 Hook 侧循环匹配使用

    val booleanKeys: List<BoolKey> = listOf(
        DISABLE_FLAG_SECURE, NO_MORE_PASSWORD_PER_24H, ALLOW_GET_PACKAGES,
        ALLOW_UNTRUSTED_TOUCH, FORCE_SCREEN_ON_OFF_ANIMATION, AI_INPUT_EXPAND,
        KEEP_ROTATION, ALLOW_RELATIVE_APP_LAUNCH, FORCE_RELATIVE_APP_FREEFORM,
        DISABLE_HBM_THERMAL_LIMIT,
        STATUSBAR_DISPLAY_SECONDS, CUSTOM_STATUSBAR_CLOCK, SYSTEMUI_CHARGE_WATTS,
        SYSTEMUI_REAL_WATTS, NOTIFICATION_ICON_LIMIT, CUSTOM_CONTROL_CENTER_DATE,
        CONTROL_CENTER_NO_TILE_LABELS, NO_CHARGE_ANIMATION, NATIVE_NOTIFICATION_ICON,
        MEDIA_OUTPUT_DIALOG_CENTER,
        SYSTEMUI_NETWORK_SPEED_SIZE, SYSTEMUI_NETWORK_SPEED_DOUBLELAYER,
        CUSTOM_NETWORK_SPEED_REFRESH_INTERVAL, SYSTEMUI_NETWORK_SPEED_HIDE_SLOW,
        SYSTEMUI_NETWORK_SPEED_HIDE_BOTH, SYSTEMUI_BATTERY_PERCENTAGE,
        FORCE_IMMERSIVE_MODE, FORCE_LENOVO_AOD, QS_ROUND_CORNER,
        BRIGHTNESS_SLIDER_PERCENTAGE, VOLUME_SLIDER_PERCENTAGE, QS_COLOR,
        NOTIFICATION_CENTER_BLUR, GUEST_MODE_CONTROLLER, EXPAND_QS_PANEL_PORTRAIT,
        CUSTOMIZE_SLIDER_STYLE, CUSTOM_CHARGE_ANIMATION, CHARGE_ANIMATION_DURATION,
        FORCE_NATIVE_AOD,
        DISABLE_BIOMETRIC_ERROR_VIBRATION,
        CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE_ENABLED, CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING_ENABLED,
        CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR_ENABLED, CUSTOM_STATUSBAR_CLOCK_TEXT_BOLD,
        CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE_ENABLED, CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING_ENABLED,
        CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR_ENABLED, CUSTOM_CONTROL_CENTER_DATE_TEXT_BOLD,
        CUSTOM_QS_COLOR, CUSTOM_LABEL_COLOR, CUSTOM_SECOND_LABEL_COLOR,
        CUSTOMIZE_SLIDER_STYLE_VALUE, CUSTOMIZE_SLIDER_STYLE_PREVIOUS,
        CUSTOMIZE_SLIDER_STYLE_PREVIOUS_VALUE,
        REMOVE_BLACKLIST, ALLOW_DISPLAY_DOLBY, PERMISSION_CONTROLLER_HOOK,
        AUTO_OWNER_INFO, SPLIT_SCREEN_MANDATORY, APP_DETAILS,
        ABOUT_DEVICE_INFO, ZTOOL_SETTINGS_ENTRY, HIDE_OTA_UPDATE_HINT, ALLOW_ADD_LANGUAGE,
        ABOUT_DEVICE_INFO_MODEL_ENABLED, ABOUT_DEVICE_INFO_CPU_ENABLED,
        ABOUT_DEVICE_INFO_RAM_ENABLED, ABOUT_DEVICE_INFO_ROM_ENABLED,
        ABOUT_DEVICE_INFO_SOFTWARE_ENABLED, ABOUT_DEVICE_INFO_HEADER_ENABLED,
        DISABLE_SCAN_APK, ALWAYS_ALLOW_PERMISSION, SKIP_WARN_PAGE,
        DISABLE_INSTALLER_AD, PACKAGE_INSTALLER_STYLE_HOOK,
        PACKAGE_INSTALLER_DISABLE_DELETE,
        DISABLE_FORCE_STOP, ZUI_LAUNCHER_HOTSEAT, CUSTOM_GRID_SIZE,
        CLEAN_GLOBAL_SEARCH, DISABLE_DOCK_BAR,
        LAUNCHER_RECENT_TASK_MEMORY_VIEW, LAUNCHER_NO_LABEL_MODE,
        LAUNCHER_DRAWER_NO_LABEL_MODE, LAUNCHER_BIG_FOLDER_ALIGN,
        LAUNCHER_HIDE_BLUE_POINT, DISMISS_CLOUD_FOLDER_CONFIRMATION,
        DISABLE_RECENT_APPS_DISPLAY, LAUNCHER_BATCH_UNINSTALL,
        REMOVE_HOT_WORD_VIEW, REMOVE_SEARCH_RECOMMEND, BEAUTIFY_RAM_INFO,
        FORCE_STOP_WHITE_LIST_ENABLE, ZUI_LAUNCHER_HOTSEAT_BACKUP,
        DISABLE_DOCK_WARNING_CONFIRMED,
        AUTO_MISTAKE_TOUCH, DISABLE_GAME_AUDIO, DISABLE_GAME_AUDIO_APP,
        DISGUISE_TB322FC, FIX_CPU_CLOCK, FIX_SOC_TEMP,
        MISTAKE_TOUCH_WHITE_LIST,
        DISABLE_OTA_CHECK, CUSTOM_OTA_PARAMETERS, NO_AUTO_OTA_INSTALL,
        BLOCK_OTA_INSTALL_DIALOG, HIDE_OTA_NOTIFICATIONS,
        DISABLE_TB_ENGINE_AUTO_DOWNLOAD, DISABLE_TB_ENGINE_AUTO_INSTALL,
        DISABLE_TB_ENGINE_APP_UPDATE, DISABLE_TB_ENGINE_PUSH,
        SIGN_TB_ENGINE_LOCAL_OTA,
        CHARGE_ANIMATION_FIX, DOCUMENTS_UI_BYPASS,
        DESKTOP_LIVE_WALLPAPER,
        DISABLE_ALL_VIRUS_SCANS, DEFAULT_ENABLE_AUTORUN,
        AUTO_ACCEPT_FILE_TRANSFER, BYPASS_SHARE_WARNING,
        DISABLE_NEARBY_SHARE_COUNTDOWN,
        IS_DETAILED_LOGGING, ENABLE_HOMEPAGE_YIYAN, AUTO_CHECK_UPDATE,
        YIYAN, IS_SYSTEMUI_PERMISSION_CONFIRMED, IS_CONFIG_UPGRADED,
        SYSTEMUI_REALWATTS_SHOW_VOLTAGE, SYSTEMUI_REALWATTS_SHOW_CURRENT,
        SYSTEMUI_REALWATTS_SHOW_POWER, SYSTEMUI_REALWATTS_SHOW_TEMPERATURE,
        SYSTEMUI_REALWATTS_SHOW_INDICATOR, SYSTEMUI_REALWATTS_CUSTOM_FORMAT_ENABLED
    )

    val intKeys: List<IntKey> = listOf(
        CUSTOM_LAUNCHER_ROW, CUSTOM_LAUNCHER_COLUMN,
        CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR, NOTIFY_NUM_SIZE,
        CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR,
        TILE_ROUND_CORNER_RADIUS, HEAD_UP_ROUND_CORNER_RADIUS,
        CUSTOM_QS_ACTIVE_COLOR_VAL, CUSTOM_LABEL_ACTIVE_COLOR_VAL,
        CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL,
        NOTIFICATION_CENTER_BLUR_PERCENT,
        SCREEN_ON_OFF_ANIMATION_MS,
        QS_PANEL_WIDTH_PERCENT, QS_TILE_COLUMNS,
        CHARGE_ANIMATION_DURATION_MS
    )

    val floatKeys: List<FloatKey> = listOf(
        CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE, CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING,
        SYSTEMUI_NETWORK_SPEED_REFRESH_INTERVAL, SYSTEMUI_NETWORK_SPEED_HIDE_THRESHOLD,
        CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE, CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING
    )

    val stringKeys: List<StringKey> = listOf(
        MISTAKE_TOUCH_WHITE_LIST_GAME, FORCE_STOP_WHITE_LIST,
        CUSTOM_OTA_TARGET_VERSION_NAME, CUSTOM_OTA_TARGET_DEVICE_ID,
        ABOUT_DEVICE_INFO_MODEL, ABOUT_DEVICE_INFO_CPU,
        ABOUT_DEVICE_INFO_RAM, ABOUT_DEVICE_INFO_ROM,
        ABOUT_DEVICE_INFO_SOFTWARE,
        AI_INPUT_EXPAND_SIGNS,
        CUSTOM_CONTROL_CENTER_DATE_FORMAT,
        API_URL, REGULAR,
        CUSTOM_STATUSBAR_CLOCK_FORMAT,
        CHARGE_WATTS_SELECTED_OPTION,
        SYSTEMUI_REALWATTS_CUSTOM_FORMAT,
        DESKTOP_LIVE_WALLPAPER_SCALE_MODE
    )

    // 查找辅助方法

    fun isBooleanKey(name: String): Boolean = booleanKeys.any { it.name == name }
    fun isIntKey(name: String): Boolean = intKeys.any { it.name == name }
    fun isFloatKey(name: String): Boolean = floatKeys.any { it.name == name }

    /**
     * 根据键名查找对应的类型化键对象。
     * 找不到则返回 null。
     */
    @Suppress("unused")
    fun findBooleanKey(name: String): BoolKey? = booleanKeys.find { it.name == name }

    @Suppress("unused")
    fun findIntKey(name: String): IntKey? = intKeys.find { it.name == name }

    @Suppress("unused")
    fun findFloatKey(name: String): FloatKey? = floatKeys.find { it.name == name }

    @Suppress("unused")
    fun findStringKey(name: String): StringKey? = stringKeys.find { it.name == name }
}
