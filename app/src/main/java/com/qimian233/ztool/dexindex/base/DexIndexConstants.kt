package com.qimian233.ztool.dexindex.base

import com.qimian233.ztool.data.keys.PreferenceKeys

/**
 * 离线 DexKit 索引的常量定义。
 *
 * 目录结构：`files/dex_index/<scopePackage>.json`（模块私有目录，hook 侧经
 * libxposed Remote Files（`openRemoteFile`）读取，无需 chmod）。
 */
object DexIndexConstants {

    /**
     * 索引文件直接位于模块 filesDir 根目录：libxposed Remote Files 的根即
     * filesDir，文件名不支持子目录/路径分隔符，文件名为 `<scopePackage>.json`。
     *
     * v2：indexer 输出改回纯 modules 映射（v1 曾双重嵌套 modules，且 v1 读取
     * 端 ConcurrentHashMap 存 null 会 NPE）；needsReindex 校验该值以自动重建旧文件。
     */
    const val SCHEMA_VERSION = 2

    // ── JSON 结构 key ─────────────────────────────────────────────
    const val JSON_SCHEMA_VERSION = "schemaVersion"
    const val JSON_GENERATED_AT = "generatedAt"
    const val JSON_APK = "apk"
    const val JSON_PATH = "path"
    const val JSON_LAST_UPDATE_TIME = "lastUpdateTime"
    const val JSON_SIGNATURE_HASH = "signatureHash"
    const val JSON_MODULES = "modules"

    /** 生成某作用域的索引文件名（openRemoteFile 要求简单文件名，不含 / . ..）。 */
    fun fileName(scopePackage: String): String = "$scopePackage.json"

    /**
     * 模块 key（与各 Hook 的 getModuleName() 返回值一致）。
     * 索引器写入与 Hook 侧读取必须使用同一常量。
     */
    object ModuleKeys {
        val CLEAN_GLOBAL_SEARCH = PreferenceKeys.CLEAN_GLOBAL_SEARCH.name
        val DISABLE_FORCE_STOP = PreferenceKeys.DISABLE_FORCE_STOP.name
        val ZUI_LAUNCHER_HOTSEAT = PreferenceKeys.ZUI_LAUNCHER_HOTSEAT.name
        val NO_CHARGE_ANIMATION = PreferenceKeys.NO_CHARGE_ANIMATION.name
        val SYSTEMUI_NETWORK_SPEED_DOUBLELAYER = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_DOUBLELAYER.name
        val BYPASS_SHARE_WARNING = PreferenceKeys.BYPASS_SHARE_WARNING.name
        val DISABLE_NEARBY_SHARE_COUNTDOWN = PreferenceKeys.DISABLE_NEARBY_SHARE_COUNTDOWN.name
    }

    /** 各模块输出字段 key。 */
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
    }
}
