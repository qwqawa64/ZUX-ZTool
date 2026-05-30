package com.qimian233.ztool.ui.theme

data class ZToolThemeSettings(
    val frontendStyle: FrontendStyle = FrontendStyle.Material3Expressive,
    val themeMode: ThemeMode = ThemeMode.FollowSystem,
    val dynamicColorEnabled: Boolean = true,
    val amoledBlackEnabled: Boolean = false,
    val manualColorEnabled: Boolean = false,
    val manualSeedColor: Long = DEFAULT_MANUAL_SEED_COLOR
) {
    companion object {
        const val DEFAULT_MANUAL_SEED_COLOR: Long = 0xFF1D5FA8
    }
}
