package com.qimian233.ztool.ui.theme

enum class MaterialColorSpec {
    Spec2021,
    Spec2025
}

enum class MaterialPalette {
    TonalSpot,
    Neutral,
    Vibrant,
    Expressive,
    Rainbow,
    FruitSalad,
    MonoChrome,
    Fidelity,
    Content
}

data class ZToolThemeSettings(
    val frontendStyle: FrontendStyle = FrontendStyle.Material3Expressive,
    val themeMode: ThemeMode = ThemeMode.FollowSystem,
    val materialColorSpec: MaterialColorSpec = MaterialColorSpec.Spec2025,
    val materialPalette: MaterialPalette = MaterialPalette.TonalSpot,
    val dynamicColorEnabled: Boolean = true,
    val amoledBlackEnabled: Boolean = false,
    /** Use the alternative brand artwork for the launcher icon and the in-app logo. */
    val useAlternativeIcon: Boolean = false,
    val predictiveBackGestureEnabled: Boolean = true,
    val manualColorEnabled: Boolean = false,
    val manualSeedColor: Long = DEFAULT_MANUAL_SEED_COLOR,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false
) {
    companion object {
        const val DEFAULT_MANUAL_SEED_COLOR: Long = 0xFF1D5FA8
    }
}
