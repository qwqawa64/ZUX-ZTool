package com.qimian233.ztool.data.keys

data class Scope(val packageName: String, val howToRestart: HowToRestart)

enum class HowToRestart {
    KillAll,
    AmStop,
    Reboot
}
/**
 * String variables storing scope package names. Reduces errors and duplicated input,
 * and lets IDE auto-completion handle everything.
 *
 * Could be improved further by also describing the preferred way to restart each scope.
 *
 */
object ScopeKeys { 
    val SETTINGS = Scope("com.android.settings", HowToRestart.AmStop)
    val PERMISSION_CONTROLLER = Scope("com.android.permissioncontroller", HowToRestart.AmStop)
    val ZUI_SAFE_CENTER = Scope("com.zui.safecenter", HowToRestart.AmStop)
    val OTA = Scope("com.lenovo.ota", HowToRestart.AmStop)
    val TB_ENGINE = Scope("com.lenovo.tbengine", HowToRestart.KillAll)
    val LENOVO_SAFE_CENTER = Scope("com.lenovo.safecenter", HowToRestart.AmStop)
    val DOCUMENTS_UI = Scope("com.android.documentsui", HowToRestart.AmStop)
    val ANDROID_SYSTEM = Scope("android", HowToRestart.Reboot)
    val SYSTEM_SERVER = Scope("system", HowToRestart.Reboot)
    val GAME_SERVICE = Scope("com.zui.game.service", HowToRestart.AmStop)
    val PACKAGE_INSTALLER = Scope("com.android.packageinstaller", HowToRestart.AmStop)
    val SYSTEM_UI = Scope("com.android.systemui", HowToRestart.KillAll)
    val WALLPAPER_SETTINGS = Scope("com.zui.wallpapersetting", HowToRestart.AmStop)
    val LAUNCHER = Scope("com.zui.launcher", HowToRestart.AmStop)
    val MOBILE_DESKTOP = Scope("com.motorola.mobiledesktop", HowToRestart.AmStop)
    val ZUI_PERFORMANCE = Scope("com.zui.pp", HowToRestart.KillAll)
}