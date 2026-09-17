package com.qimian233.ztool.dexindex.base

import com.qimian233.ztool.data.keys.PreferenceKeys

/**
 * Constants for the offline DexKit index.
 *
 * Directory layout: `files/dex_index/<scopePackage>.json` (module private
 * directory; the hook side reads it via libxposed Remote Files
 * (`openRemoteFile`) without needing chmod).
 */
object DexIndexConstants {

    /**
     * Index files live directly at the module filesDir root: the Remote Files
     * root is filesDir itself, and file names do not support subdirectories or
     * path separators; the file name is `<scopePackage>.json`.
     *
     * v2: indexer output reverted to a plain modules map (v1 had a doubly
     * nested modules map, and storing null in the v1 reader's
     * ConcurrentHashMap would NPE); needsReindex checks this value to rebuild
     * legacy files automatically.
     */
    const val SCHEMA_VERSION = 2

    const val JSON_SCHEMA_VERSION = "schemaVersion"
    const val JSON_GENERATED_AT = "generatedAt"
    const val JSON_APK = "apk"
    const val JSON_PATH = "path"
    const val JSON_LAST_UPDATE_TIME = "lastUpdateTime"
    const val JSON_SIGNATURE_HASH = "signatureHash"
    const val JSON_MODULES = "modules"

    /** Builds the index file name for a scope (openRemoteFile requires a simple name without / . ..). */
    fun fileName(scopePackage: String): String = "$scopePackage.json"

    /**
     * Module keys (matching each Hook's getModuleName() return value).
     * The indexer writer and the hook-side reader must use the same constant.
     */
    object ModuleKeys {
        val CLEAN_GLOBAL_SEARCH = PreferenceKeys.CLEAN_GLOBAL_SEARCH.name
        val DISABLE_FORCE_STOP = PreferenceKeys.DISABLE_FORCE_STOP.name
        val ZUI_LAUNCHER_HOTSEAT = PreferenceKeys.ZUI_LAUNCHER_HOTSEAT.name
        val NO_CHARGE_ANIMATION = PreferenceKeys.NO_CHARGE_ANIMATION.name
        val SYSTEMUI_NETWORK_SPEED_DOUBLELAYER = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_DOUBLELAYER.name
        val BYPASS_SHARE_WARNING = PreferenceKeys.BYPASS_SHARE_WARNING.name
        val DISABLE_NEARBY_SHARE_COUNTDOWN = PreferenceKeys.DISABLE_NEARBY_SHARE_COUNTDOWN.name
        val AUTO_ACCEPT_FILE_TRANSFER = PreferenceKeys.AUTO_ACCEPT_FILE_TRANSFER.name
    }

    /** Output field keys per module. */
    object Keys {
        // CleanGlobalSearch
        const val HOTWORD_INIT_METHOD = "hotwordInitMethod"
        const val HOTWORD_DATA_METHOD = "hotwordDataMethod"
        // DisableForceStop
        const val FORCE_STOP_METHOD = "forceStopMethod"
        // ZuiLauncherHotseatHook
        const val LOADER_CURSOR_B_METHOD = "loaderCursorBMethod"
        // NoChargeAnimation
        const val HANDLER_FIELD_NAME = "handlerFieldName"
        // SystemUINetworkSpeeddoublelayerHook
        const val HANDLER_INNER_CLASS = "handlerInnerClass"
        // BypassShareWarningHook
        const val MANAGER_CLASS = "managerClass"
        const val MANAGER_FACTORY_METHOD = "managerFactoryMethod"
        const val MANAGER_SET_METHOD = "managerSetMethod"
        const val DIALOG_METHOD = "dialogMethod"
        const val TILE_REFRESH_METHOD = "tileRefreshMethod"
        // DisableNearbyShareAutoOffHook
        const val TARGET_CLASS = "targetClass"
        const val TARGET_METHOD = "targetMethod"
        // AutoAcceptFileTransferHook
        const val VM_FIELD_NAME = "vmFieldName"
        const val ACCEPTED_FIELD_NAME = "acceptedFieldName"
        const val LIVE_DATA_FIELD_NAME = "liveDataFieldName"
        const val LIVE_DATA_UPDATE_METHOD = "liveDataUpdateMethod"
    }
}
