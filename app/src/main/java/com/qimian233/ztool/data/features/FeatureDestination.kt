package com.qimian233.ztool.data.features

enum class FeatureDestination(
    val route: String
) {
    SettingsDetail("feature/settings-detail"),
    GameTool("feature/game-tool"),
    Ota("feature/ota"),
    PackageInstaller("feature/package-installer"),
    SystemUi("feature/system-ui"),
    Launcher("feature/launcher"),
    MobileDesktop("feature/mobile-desktop"),
    Framework("feature/framework"),
    SafeCenter("feature/safe-center"),
    TbEngine("feature/tb-engine"),
    ZuiPerformance("feature/zui-performance"),
    SogouIme("feature/sogou-ime"),
    DeviceService("feature/device-service"),
    FreeformBar("feature/freeform-bar"),
    UiComponents("feature/ui-components"),
    FreeformHub("feature/freeform-hub")
}
