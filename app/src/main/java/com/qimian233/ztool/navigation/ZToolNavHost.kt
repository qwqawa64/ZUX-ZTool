package com.qimian233.ztool.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.screens.features.FeaturesMainRoute
import com.qimian233.ztool.screens.gametool.GameToolSettingsRoute
import com.qimian233.ztool.screens.home.HomeMainRoute
import com.qimian233.ztool.screens.launcher.LauncherSettingsRoute
import com.qimian233.ztool.screens.mobiledesktop.MobileDesktopSettingsRoute
import com.qimian233.ztool.screens.ota.OtaSettingsRoute
import com.qimian233.ztool.screens.packageinstaller.PackageInstallerSettingsRoute
import com.qimian233.ztool.screens.safecenter.SafeCenterSettingsRoute
import com.qimian233.ztool.screens.sogouime.SogouImeSettingsRoute
import com.qimian233.ztool.screens.pp.ZuiPerformanceSettingsRoute
import com.qimian233.ztool.screens.tbengine.TbEngineSettingsRoute
import com.qimian233.ztool.screens.ztoolsettings.SettingsMainRoute
import com.qimian233.ztool.screens.ztoolsettings.about.SettingsAboutRoute
import com.qimian233.ztool.screens.ztoolsettings.about.SettingsAboutRouteName
import com.qimian233.ztool.screens.ztoolsettings.advanced.EngineeringCodesRoute
import com.qimian233.ztool.screens.ztoolsettings.advanced.SettingsAdvancedRoute
import com.qimian233.ztool.screens.ztoolsettings.misc.SettingsMiscRoute
import com.qimian233.ztool.screens.ztoolsettings.theme.ThemeSettingsRoute
import com.qimian233.ztool.screens.systemframework.FrameworkSettingsRoute
import com.qimian233.ztool.screens.systemui.SystemUiSettingsRoute
import com.qimian233.ztool.screens.systemui.animation.AnimationWallpaperSettingsRoute
import com.qimian233.ztool.screens.systemui.controlcenter.ControlCenterSettingsRoute
import com.qimian233.ztool.screens.systemui.lockscreen.LockScreenSettingsRoute
import com.qimian233.ztool.screens.systemui.misc.SystemUiMiscSettingsRoute
import com.qimian233.ztool.screens.systemui.statusbar.StatusBarSettingsRoute
import com.qimian233.ztool.screens.zuisetting.SettingsDetailRoute
import com.qimian233.ztool.screens.zuisetting.magicwindowsearch.SearchPageRoute
import com.qimian233.ztool.search.SearchIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

private const val SettingsNavigationAnimationMillis = 320

/**
 * Optional query arg carrying a [com.qimian233.ztool.search.SearchEntry] id; screens
 * scroll to and pulse-highlight the matching row after landing.
 */
private const val TargetArg = "target"

/** Route pattern accepting the optional highlight target. */
private fun routeWithTarget(base: String) = "$base?$TargetArg={$TargetArg}"

private val highlightTargetArguments = listOf(
    androidx.navigation.navArgument(TargetArg) {
        type = androidx.navigation.NavType.StringType
        nullable = true
        defaultValue = null
    }
)

internal fun NavBackStackEntry.highlightTarget(): String? =
    arguments?.getString(TargetArg)

@Composable
internal fun MainRouteNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    predictiveBackGestureEnabled: Boolean,
    onEnvironmentStateChanged: (Boolean) -> Unit,
    useHorizontalAnimation: Boolean = false
) {
    val mainForward = if (useHorizontalAnimation)
        AnimatedContentTransitionScope.SlideDirection.Left
    else
        AnimatedContentTransitionScope.SlideDirection.Up
    val mainBackward = if (useHorizontalAnimation)
        AnimatedContentTransitionScope.SlideDirection.Right
    else
        AnimatedContentTransitionScope.SlideDirection.Down
    val mainRouteEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideIntoContainer(
            towards = routeSlideDirection(
                mainForwardDirection = mainForward,
                mainBackwardDirection = mainBackward,
                nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
            ),
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val mainRouteExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutOfContainer(
            towards = routeSlideDirection(
                mainForwardDirection = mainForward,
                mainBackwardDirection = mainBackward,
                nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
            ),
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val mainRoutePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
        {
            slideIntoContainer(
                towards = routeSlideDirection(
                    mainForwardDirection = mainForward,
                    mainBackwardDirection = mainBackward,
                    nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                    nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
                ),
                animationSpec = tween(SettingsNavigationAnimationMillis)
            )
        }
    val mainRoutePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? =
        {
            slideOutOfContainer(
                towards = routeSlideDirection(
                    mainForwardDirection = mainForward,
                    mainBackwardDirection = mainBackward,
                    nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                    nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
                ),
                animationSpec = tween(SettingsNavigationAnimationMillis)
            )
        }
    val horizontalEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
        {
            slideIntoContainer(
                if (isForwardNavigation()) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                },
                animationSpec = tween(SettingsNavigationAnimationMillis)
            )
        }
    val horizontalExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutOfContainer(
            if (isForwardNavigation()) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            },
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val horizontalPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
        {
            slideIntoContainer(
                if (isForwardNavigation()) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                },
                animationSpec = tween(SettingsNavigationAnimationMillis)
            )
        }
    val horizontalPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? =
        {
            slideOutOfContainer(
                if (isForwardNavigation()) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                },
                animationSpec = tween(SettingsNavigationAnimationMillis)
            )
        }
    val predictiveHorizontalPopEnter:
            AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = {
        slideIntoContainer(
            if (isForwardNavigation()) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            },
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val predictiveHorizontalPopExit:
            AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = {
        slideOutOfContainer(
            if (isForwardNavigation()) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            },
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }

    val mainNavGraph: androidx.navigation.NavGraphBuilder.() -> Unit = {
        composable(
            route = routeWithTarget(MainRoute.Home.name),
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            HomeMainRoute(onEnvironmentStateChanged = onEnvironmentStateChanged)
        }
        composable(
            route = routeWithTarget(MainRoute.Features.name),
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            FeaturesMainRoute(
                onFeatureDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        launchSingleTop = true
                    }
                },
                onOpenSearchResult = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = routeWithTarget(MainRoute.Settings.name),
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SettingsMainRoute(
                onOpenThemeSettings = {
                    navController.navigate(HiddenRoute.SETTINGS_THEME) {
                        launchSingleTop = true
                    }
                },
                onOpenAbout = {
                    navController.navigate(SettingsAboutRouteName) {
                        launchSingleTop = true
                    }
                },
                onOpenAdvanced = {
                    navController.navigate(HiddenRoute.SETTINGS_ADVANCED) {
                        launchSingleTop = true
                    }
                },
                onOpenMisc = {
                    navController.navigate(HiddenRoute.SETTINGS_MISC) {
                        launchSingleTop = true
                    }
                },
                onOpenSearchResult = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SETTINGS_THEME),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            ThemeSettingsRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Settings.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(SettingsAboutRouteName),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SettingsAboutRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Settings.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SETTINGS_ADVANCED),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SettingsAdvancedRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Settings.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SETTINGS_MISC),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SettingsMiscRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Settings.name) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenEngineeringCodes = {
                    navController.navigate(HiddenRoute.SETTINGS_ENGINEERING_CODES) {
                        launchSingleTop = true
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SETTINGS_ENGINEERING_CODES),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            EngineeringCodesRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Settings.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.PackageInstaller.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            PackageInstallerSettingsRoute(
                title = stringResource(R.string.package_installer_app_name),
                packageName = ScopeKeys.PACKAGE_INSTALLER.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.SettingsDetail.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SettingsDetailRoute(
                title = stringResource(R.string.settings_app_name),
                packageName = ScopeKeys.SETTINGS.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenStrategySearch = {
                    navController.navigate(HiddenRoute.SETTINGS_DETAIL_MAGIC_WINDOW_SEARCH) {
                        launchSingleTop = true
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SETTINGS_DETAIL_MAGIC_WINDOW_SEARCH),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SearchPageRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SettingsDetail.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.GameTool.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            GameToolSettingsRoute(
                title = stringResource(R.string.game_tool_app_name),
                packageName = ScopeKeys.GAME_SERVICE.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.SystemUi.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SystemUiSettingsRoute(
                title = stringResource(R.string.system_ui_app_name),
                packageName = ScopeKeys.SYSTEM_UI.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenStatusBar = {
                    navController.navigate(HiddenRoute.SYSTEM_UI_STATUS_BAR) {
                        launchSingleTop = true
                    }
                },
                onOpenLockScreen = {
                    navController.navigate(HiddenRoute.SYSTEM_UI_LOCK_SCREEN) {
                        launchSingleTop = true
                    }
                },
                onOpenControlCenter = {
                    navController.navigate(HiddenRoute.SYSTEM_UI_CONTROL_CENTER) {
                        launchSingleTop = true
                    }
                },
                onOpenAnimationWallpaper = {
                    navController.navigate(HiddenRoute.SYSTEM_UI_ANIMATION_WALLPAPER) {
                        launchSingleTop = true
                    }
                },
                onOpenMisc = {
                    navController.navigate(HiddenRoute.SYSTEM_UI_MISC) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SYSTEM_UI_STATUS_BAR),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            StatusBarSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                        stringResource(R.string.system_ui_status_bar_title_suffix),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SYSTEM_UI_LOCK_SCREEN),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            LockScreenSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                        stringResource(R.string.system_ui_lock_screen_title_suffix),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SYSTEM_UI_CONTROL_CENTER),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            ControlCenterSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                        stringResource(R.string.system_ui_control_center_title_suffix),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SYSTEM_UI_ANIMATION_WALLPAPER),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            AnimationWallpaperSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                        " — 动画与壁纸",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(HiddenRoute.SYSTEM_UI_MISC),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SystemUiMiscSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                        " — " + stringResource(R.string.system_ui_common_misc),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.Ota.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            OtaSettingsRoute(
                title = stringResource(R.string.system_update_app_name),
                packageName = ScopeKeys.OTA.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.TbEngine.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            TbEngineSettingsRoute(
                title = stringResource(R.string.tb_engine_app_name),
                packageName = ScopeKeys.TB_ENGINE.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.ZuiPerformance.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            ZuiPerformanceSettingsRoute(
                title = stringResource(R.string.zui_pp_app_name),
                packageName = ScopeKeys.ZUI_PERFORMANCE.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.SogouIme.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SogouImeSettingsRoute(
                title = stringResource(R.string.sogou_ime_app_name),
                packageName = ScopeKeys.SOGOU_OEM_IME.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.Framework.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            FrameworkSettingsRoute(
                title = stringResource(R.string.system_framework_app_name),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.Launcher.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            LauncherSettingsRoute(
                title = stringResource(R.string.launcher_app_name),
                packageName = ScopeKeys.LAUNCHER.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.MobileDesktop.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            MobileDesktopSettingsRoute(
                title = stringResource(R.string.mobile_desktop_app_name),
                packageName = ScopeKeys.MOBILE_DESKTOP.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
        composable(
            route = routeWithTarget(FeatureDestination.SafeCenter.route),
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit,
            arguments = highlightTargetArguments
        ) { backStackEntry ->
            SafeCenterSettingsRoute(
                title = stringResource(R.string.safe_center_app_name),
                packageName = ScopeKeys.ZUI_SAFE_CENTER.packageName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                targetId = backStackEntry.highlightTarget(),
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = MainRoute.Home.name,
        modifier = modifier,
        predictivePopEnterTransition = predictiveHorizontalPopEnter,
        predictivePopExitTransition = predictiveHorizontalPopExit,
        builder = mainNavGraph
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val canNavigateBack = backStackEntry?.destination?.route != MainRoute.Home.name
    BackHandler(enabled = !predictiveBackGestureEnabled && canNavigateBack) {
        navController.popBackStack()
    }
    PredictiveBackHandler(enabled = !predictiveBackGestureEnabled && canNavigateBack) { progress ->
        try {
            progress.collect()
            navController.popBackStack()
        } catch (_: CancellationException) {
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isForwardNavigation(): Boolean {
    return navigationRouteIndex(targetState.destination.route) >
            navigationRouteIndex(initialState.destination.route)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.routeSlideDirection(
    mainForwardDirection: AnimatedContentTransitionScope.SlideDirection,
    mainBackwardDirection: AnimatedContentTransitionScope.SlideDirection,
    nestedForwardDirection: AnimatedContentTransitionScope.SlideDirection,
    nestedBackwardDirection: AnimatedContentTransitionScope.SlideDirection
): AnimatedContentTransitionScope.SlideDirection {
    return if (isNavigateBetweenMainRoutes()) {
        if (isForwardMainRouteNavigation()) mainForwardDirection else mainBackwardDirection
    } else if (isForwardNavigation()) {
        nestedForwardDirection
    } else {
        nestedBackwardDirection
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isForwardMainRouteNavigation(): Boolean {
    return mainRouteIndex(targetState.destination.route) >
            mainRouteIndex(initialState.destination.route)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isNavigateBetweenMainRoutes(): Boolean {
    val targetIndex = mainRouteIndex(targetState.destination.route)
    val initialIndex = mainRouteIndex(initialState.destination.route)
    return targetIndex != initialIndex && targetIndex != -1 && initialIndex != -1
}

private fun mainRouteIndex(rawRoute: String?): Int {
    // Routes may carry the optional ?target= highlight arg; depth order cares
    // about the path only.
    val route = rawRoute?.substringBefore('?')
    if (route == null) return -1
    return when {
        route == MainRoute.Home.name -> 0
        route == MainRoute.Features.name || route.startsWith("feature/") -> 1
        route == MainRoute.Settings.name || route.startsWith("Settings") -> 2
        else -> -1
    }
}

private fun navigationRouteIndex(rawRoute: String?): Int {
    // Strip the optional ?target= highlight arg before depth lookup.
    val route = rawRoute?.substringBefore('?') ?: return -1
    return when (route) {
        MainRoute.Home.name -> 0
        MainRoute.Features.name -> 1
        FeatureDestination.SettingsDetail.route -> 2
        FeatureDestination.GameTool.route -> 2
        FeatureDestination.Ota.route -> 2
        FeatureDestination.PackageInstaller.route -> 2
        FeatureDestination.SystemUi.route -> 2
        HiddenRoute.SYSTEM_UI_STATUS_BAR -> 3
        HiddenRoute.SYSTEM_UI_LOCK_SCREEN -> 3
        HiddenRoute.SYSTEM_UI_CONTROL_CENTER -> 3
        HiddenRoute.SYSTEM_UI_ANIMATION_WALLPAPER -> 3
        HiddenRoute.SYSTEM_UI_MISC -> 3
        HiddenRoute.SETTINGS_DETAIL_MAGIC_WINDOW_SEARCH -> 3
        FeatureDestination.Launcher.route -> 2
        FeatureDestination.MobileDesktop.route -> 2
        FeatureDestination.Framework.route -> 2
        FeatureDestination.SafeCenter.route -> 2
        FeatureDestination.TbEngine.route -> 2
        FeatureDestination.ZuiPerformance.route -> 2
        FeatureDestination.SogouIme.route -> 2
        MainRoute.Settings.name -> 3
        HiddenRoute.SETTINGS_THEME -> 4
        HiddenRoute.SETTINGS_ABOUT -> 4
        HiddenRoute.SETTINGS_ADVANCED -> 4
        HiddenRoute.SETTINGS_ENGINEERING_CODES -> 5
        else -> 0
    }
}
