package com.qimian233.ztool.data.keys

data class Scope(val packageName: String, val howToRestart: HowToRestart)

enum class HowToRestart {
    KillAll,
    AmStop,
    Reboot
}
/**
 * 存储作用域包名的字符串变量。减少错误和重复输入，让 IDE 自动补全搞定一切。
 * 
 * 可以改良一下，再描述一个优先推荐的重启作用域方式
 * 
 * 性能服务之类的到时候也加上，要记得在这里添加作用域 key 了。
 */
object ScopeKeys { 
    val SETTINGS = Scope("com.android.settings", HowToRestart.AmStop)
    val PERMISSION_CONTROLLER = Scope("com.android.permissioncontroller", HowToRestart.AmStop)
    val ZUI_SAFE_CENTER = Scope("com.zui.safecenter", HowToRestart.AmStop)
    val OTA = Scope("com.lenovo.ota", HowToRestart.AmStop)
    val TB_ENGINE = Scope("com.lenovo.tbengine", HowToRestart.AmStop)
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
}