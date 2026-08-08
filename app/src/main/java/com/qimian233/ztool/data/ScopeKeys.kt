package com.qimian233.ztool.data

data class Scope(@JvmField val packageName: String, @JvmField val howToRestart: HowToRestart)

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
    @JvmField val SETTINGS = Scope("com.android.settings", HowToRestart.AmStop)
    @JvmField val PERMISSION_CONTROLLER = Scope("com.android.permissioncontroller", HowToRestart.AmStop)
    @JvmField val ZUI_SAFE_CENTER = Scope("com.zui.safecenter", HowToRestart.AmStop)
    @JvmField val OTA = Scope("com.lenovo.ota", HowToRestart.AmStop)
    @JvmField val TB_ENGINE = Scope("com.lenovo.tbengine", HowToRestart.AmStop)
    @JvmField val LENOVO_SAFE_CENTER = Scope("com.lenovo.safecenter", HowToRestart.AmStop)
    @JvmField val DOCUMENTS_UI = Scope("com.android.documentsui", HowToRestart.AmStop)
    @JvmField val ANDROID_SYSTEM = Scope("android", HowToRestart.Reboot)
    @JvmField val SYSTEM_SERVER = Scope("system", HowToRestart.Reboot)
    @JvmField val GAME_SERVICE = Scope("com.zui.game.service", HowToRestart.AmStop)
    @JvmField val LENOVO_GAME_SERVICE = Scope("com.lenovo.gamingservice", HowToRestart.AmStop)
    @JvmField val ANDROID_GAMING = Scope("com.android.gaming", HowToRestart.AmStop)
    @JvmField val PACKAGE_INSTALLER = Scope("com.android.packageinstaller", HowToRestart.AmStop)
    @JvmField val GOOGLE_PACKAGE_INSTALLER = Scope("com.google.android.packageinstaller", HowToRestart.AmStop)
    @JvmField val SYSTEM_UI = Scope("com.android.systemui", HowToRestart.KillAll)
    @JvmField val WALLPAPER_SETTINGS = Scope("com.zui.wallpapersetting", HowToRestart.AmStop)
    @JvmField val LAUNCHER = Scope("com.zui.launcher", HowToRestart.AmStop)
    @JvmField val MOBILE_DESKTOP = Scope("com.motorola.mobiledesktop", HowToRestart.AmStop)
    @JvmField val READY_FOR = Scope("com.motorola.readyfor", HowToRestart.AmStop)
}