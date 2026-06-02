package com.qimian233.ztool.ui.theme

enum class MaterialPaletteMode {
    MaterialYou2021,
    Expressive2025
}

data class ZToolThemeSettings(
    val frontendStyle: FrontendStyle = FrontendStyle.Material3Expressive,
    val themeMode: ThemeMode = ThemeMode.FollowSystem,
    val materialPaletteMode: MaterialPaletteMode = MaterialPaletteMode.Expressive2025,
    val dynamicColorEnabled: Boolean = true,
    val amoledBlackEnabled: Boolean = false,
    val manualColorEnabled: Boolean = false,
    val manualSeedColor: Long = DEFAULT_MANUAL_SEED_COLOR
) {
    companion object {
        const val DEFAULT_MANUAL_SEED_COLOR: Long = 0xFF1D5FA8
    }
}
