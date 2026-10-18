package com.qimian233.ztool.data.features

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.Scope
import com.qimian233.ztool.data.keys.ScopeKeys

/** Where a feature card's icon comes from. */
sealed interface FeatureIconSource {
    /** Legacy PackageManager launcher icon. */
    data class Package(val packageName: String) : FeatureIconSource

    /** Module-local single-color vector, tinted by the theme. */
    data class LocalGlyph(@param:DrawableRes val resId: Int) : FeatureIconSource
}

/** How a feature card declares the scopes its hooks need. */
sealed interface FeatureScopeSource {
    /** Fixed scope union used for the in-scope gate and scope requests. */
    data class Fixed(val scopes: List<Scope>) : FeatureScopeSource

    /** Cross-app hooks with a user-selected LSPosed scope and no gate. */
    data object UserManaged : FeatureScopeSource
}

/** When a feature card is shown on the Features grid. */
sealed interface FeatureAvailability {
    /** Visible only while the given package is installed. */
    data class RequiresInstalled(val packageName: String) : FeatureAvailability

    /** Always visible, independent of installed packages. */
    data object AlwaysAvailable : FeatureAvailability
}

/** Declarative metadata for one Features-grid card. */
data class FeatureCard(
    val destination: FeatureDestination,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: FeatureIconSource,
    val scope: FeatureScopeSource,
    val availability: FeatureAvailability
) {
    /** Scopes used for the in-scope gate; empty for user-managed scopes. */
    fun scopes(): List<Scope> = when (val source = scope) {
        is FeatureScopeSource.Fixed -> source.scopes
        FeatureScopeSource.UserManaged -> emptyList()
    }

    /** Package that gates visibility, or null when always visible. */
    fun requiredPackage(): String? = when (val availability = availability) {
        is FeatureAvailability.RequiresInstalled -> availability.packageName
        FeatureAvailability.AlwaysAvailable -> null
    }
}

/** Single source of Features-grid card metadata. */
object FeatureCatalog {

    private fun appCard(
        destination: FeatureDestination,
        @StringRes nameRes: Int,
        @StringRes descriptionRes: Int,
        primary: Scope,
        scopes: List<Scope> = listOf(primary)
    ): FeatureCard = FeatureCard(
        destination = destination,
        nameRes = nameRes,
        descriptionRes = descriptionRes,
        icon = FeatureIconSource.Package(primary.packageName),
        scope = FeatureScopeSource.Fixed(scopes),
        availability = FeatureAvailability.RequiresInstalled(primary.packageName)
    )

    val cards: List<FeatureCard> = listOf(
        appCard(
            destination = FeatureDestination.SettingsDetail,
            nameRes = R.string.settings_app_name,
            descriptionRes = R.string.settings_app_description,
            primary = ScopeKeys.SETTINGS,
            scopes = listOf(
                ScopeKeys.SETTINGS,
                ScopeKeys.PERMISSION_CONTROLLER,
                ScopeKeys.ZUI_SAFE_CENTER
            )
        ),
        appCard(
            destination = FeatureDestination.GameTool,
            nameRes = R.string.game_tool_app_name,
            descriptionRes = R.string.game_tool_app_description,
            primary = ScopeKeys.GAME_SERVICE
        ),
        appCard(
            destination = FeatureDestination.Ota,
            nameRes = R.string.system_update_app_name,
            descriptionRes = R.string.system_update_app_description,
            primary = ScopeKeys.OTA,
            scopes = listOf(ScopeKeys.OTA, ScopeKeys.TB_ENGINE, ScopeKeys.SETTINGS)
        ),
        appCard(
            destination = FeatureDestination.PackageInstaller,
            nameRes = R.string.package_installer_app_name,
            descriptionRes = R.string.package_installer_app_description,
            primary = ScopeKeys.PACKAGE_INSTALLER
        ),
        appCard(
            destination = FeatureDestination.SystemUi,
            nameRes = R.string.system_ui_app_name,
            descriptionRes = R.string.system_ui_app_description,
            primary = ScopeKeys.SYSTEM_UI,
            scopes = listOf(ScopeKeys.SYSTEM_UI, ScopeKeys.WALLPAPER_SETTINGS)
        ),
        appCard(
            destination = FeatureDestination.Launcher,
            nameRes = R.string.launcher_app_name,
            descriptionRes = R.string.launcher_app_description,
            primary = ScopeKeys.LAUNCHER
        ),
        appCard(
            destination = FeatureDestination.MobileDesktop,
            nameRes = R.string.mobile_desktop_app_name,
            descriptionRes = R.string.mobile_desktop_app_description,
            primary = ScopeKeys.MOBILE_DESKTOP
        ),
        FeatureCard(
            destination = FeatureDestination.Framework,
            nameRes = R.string.system_framework_app_name,
            descriptionRes = R.string.system_framework_app_description,
            icon = FeatureIconSource.Package(ScopeKeys.ANDROID_SYSTEM.packageName),
            scope = FeatureScopeSource.Fixed(
                listOf(ScopeKeys.ANDROID_SYSTEM, ScopeKeys.SYSTEM_SERVER)
            ),
            availability = FeatureAvailability.AlwaysAvailable
        ),
        appCard(
            destination = FeatureDestination.SafeCenter,
            nameRes = R.string.safe_center_app_name,
            descriptionRes = R.string.safe_center_app_description,
            primary = ScopeKeys.ZUI_SAFE_CENTER,
            scopes = listOf(
                ScopeKeys.ZUI_SAFE_CENTER,
                ScopeKeys.LENOVO_SAFE_CENTER,
                ScopeKeys.DOCUMENTS_UI
            )
        ),
        appCard(
            destination = FeatureDestination.TbEngine,
            nameRes = R.string.tb_engine_app_name,
            descriptionRes = R.string.tb_engine_app_description,
            primary = ScopeKeys.TB_ENGINE
        ),
        appCard(
            destination = FeatureDestination.ZuiPerformance,
            nameRes = R.string.zui_pp_app_name,
            descriptionRes = R.string.zui_pp_app_description,
            primary = ScopeKeys.ZUI_PERFORMANCE
        ),
        appCard(
            destination = FeatureDestination.SogouIme,
            nameRes = R.string.sogou_ime_app_name,
            descriptionRes = R.string.sogou_ime_app_description,
            primary = ScopeKeys.SOGOU_OEM_IME
        ),
        appCard(
            destination = FeatureDestination.DeviceService,
            nameRes = R.string.device_service_app_name,
            descriptionRes = R.string.device_service_app_description,
            primary = ScopeKeys.LENOVO_DEVICE_SERVICE,
            scopes = listOf(ScopeKeys.LENOVO_DEVICE_SERVICE, ScopeKeys.TAB_PUSHOUT)
        ),
        appCard(
            destination = FeatureDestination.FreeformBar,
            nameRes = R.string.freeform_sidebar_app_name,
            descriptionRes = R.string.freeform_sidebar_app_description,
            primary = ScopeKeys.FREEFORM_SIDEBAR
        ),
        FeatureCard(
            destination = FeatureDestination.UiComponents,
            nameRes = R.string.ui_components_app_name,
            descriptionRes = R.string.ui_components_app_description,
            icon = FeatureIconSource.LocalGlyph(R.drawable.ic_feature_ui_components),
            scope = FeatureScopeSource.UserManaged,
            availability = FeatureAvailability.AlwaysAvailable
        ),
        FeatureCard(
            destination = FeatureDestination.FreeformHub,
            nameRes = R.string.freeform_hub_app_name,
            descriptionRes = R.string.freeform_hub_app_description,
            icon = FeatureIconSource.LocalGlyph(R.drawable.ic_feature_freeform_hub),
            scope = FeatureScopeSource.Fixed(
                listOf(
                    ScopeKeys.SETTINGS,
                    ScopeKeys.SYSTEM_UI,
                    ScopeKeys.LAUNCHER,
                    ScopeKeys.ANDROID_SYSTEM,
                    ScopeKeys.SYSTEM_SERVER
                )
            ),
            availability = FeatureAvailability.AlwaysAvailable
        )
    )

    private val byDestination: Map<FeatureDestination, FeatureCard> =
        cards.associateBy { it.destination }

    fun card(destination: FeatureDestination): FeatureCard = requireNotNull(
        byDestination[destination]
    ) { "No FeatureCatalog card registered for $destination" }
}
