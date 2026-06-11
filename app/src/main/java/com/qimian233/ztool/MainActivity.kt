package com.qimian233.ztool

import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import com.qimian233.ztool.settingactivity.gametool.GameToolSettingsRoute
import com.qimian233.ztool.settingactivity.launcher.LauncherSettingsRoute
import com.qimian233.ztool.settingactivity.ota.OtaSettingsRoute
import com.qimian233.ztool.settingactivity.packageinstaller.PackageInstallerSettingsRoute
import com.qimian233.ztool.settingactivity.safecenter.SafeCenterSettingsRoute
import com.qimian233.ztool.settingactivity.setting.SettingsDetailRoute
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.SearchPageRoute
import com.qimian233.ztool.settingactivity.systemframework.FrameworkSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.ControlCenter.ControlCenterSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.SystemUiSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.lockscreen.LockScreenSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.statusBarSetting.StatusBarSettingsRoute
import com.qimian233.ztool.service.LogServiceManager
import com.qimian233.ztool.ui.components.ZToolNavigationRail
import com.qimian233.ztool.ui.components.ZToolNavigationRailItem
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import com.qimian233.ztool.utils.CountdownDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity(),
    EnvironmentStateListener,
    LogServiceManager.ServiceStatusListener,
    CountdownDialog.OnCountdownFinishListener {

    private var isEnvironmentReady by mutableStateOf(false)
    private var currentRoute by mutableStateOf(MainRoute.Home)
    private var themeSettings by mutableStateOf(ZToolThemeSettings())
    private var lastClickTime = 0L
    private var unregisterThemeSettingsObserver: (() -> Unit)? = null

    private val clickInterval = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            currentRoute = savedInstanceState.getString(KEY_CURRENT_ROUTE)
                ?.let(MainRoute::fromName)
                ?: MainRoute.Home
            isEnvironmentReady = savedInstanceState.getBoolean(KEY_ENVIRONMENT_READY, false)
        }

        val themeRepository = ThemePreferencesRepository(applicationContext)
        themeSettings = themeRepository.loadSettings()
        unregisterThemeSettingsObserver = themeRepository.observeSettings { updatedSettings ->
            runOnUiThread {
                themeSettings = updatedSettings
                setupSystemBars(updatedSettings)
            }
        }
        setupSystemBars(themeSettings)
        LogServiceManager.setServiceStatusListener(this)

        setContent {
            ZToolTheme(settings = themeSettings) {
                MainTabletShell(
                    environmentReady = isEnvironmentReady,
                    selectedRoute = currentRoute,
                    themeSettings = themeSettings,
                    onDestinationSelected = ::navigateFromRail,
                    onEnvironmentStateChanged = ::onEnvironmentStateChanged,
                    onRouteChanged = ::setCurrentRouteFromHost
                )
            }
        }

        maybeShowAgreementDialog()
        LogServiceManager.restartServiceIfNeeded(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_ROUTE, currentRoute.name)
        outState.putBoolean(KEY_ENVIRONMENT_READY, isEnvironmentReady)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterThemeSettingsObserver?.invoke()
        unregisterThemeSettingsObserver = null
        LogServiceManager.setServiceStatusListener(null)
    }

    override fun onPositiveButtonClick() {
        getSharedPreferences("ZToolPrefs", MODE_PRIVATE)
            .edit {
                putBoolean("isFirstLaunch", false)
            }
        Toast.makeText(this, getString(R.string.user_confirm_agreement), Toast.LENGTH_SHORT).show()
    }

    override fun onNegativeButtonClick() {
        Toast.makeText(this, getString(R.string.user_dismiss_agreement), Toast.LENGTH_SHORT).show()
        finishAffinity()
    }

    override fun onCountdownFinished() = Unit

    override fun onServiceStarted() {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.log_service_started), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServiceStopped() {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.log_service_stopped), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServiceRestartFailed() {
        runOnUiThread {
            Toast.makeText(
                this,
                getString(R.string.log_service_require_manual_restart),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onEnvironmentStateChanged(environmentReady: Boolean) {
        isEnvironmentReady
        isEnvironmentReady = environmentReady

        if (!environmentReady && currentRoute != MainRoute.Home) {
            currentRoute = MainRoute.Home
        }
    }

    private fun navigateFromRail(route: MainRoute) {
        if (!isEnvironmentReady && route != MainRoute.Home) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < clickInterval) return
        lastClickTime = currentTime

        if (currentRoute != route) {
            currentRoute = route
        }
    }

    private fun setCurrentRouteFromHost(route: MainRoute) {
        if (!isEnvironmentReady && route != MainRoute.Home) {
            currentRoute = MainRoute.Home
            return
        }
        if (currentRoute != route) {
            currentRoute = route
        }
    }

    private fun maybeShowAgreementDialog() {
        val prefs = getSharedPreferences("ZToolPrefs", MODE_PRIVATE)
        if (!prefs.getBoolean("isFirstLaunch", true)) return

        CountdownDialog.Builder(this, this).apply {
            setTitle(getString(R.string.agreement_title))
            setMessage(getString(R.string.agreement_text))
            setCancelable(false)
            setCountdownSeconds(30)
            setNegativeText(getString(R.string.agreement_dismiss))
            setPositiveText(getString(R.string.agreement_confirm))
            setOnCountdownFinishListener(this@MainActivity)
        }.build().show()
    }

    private fun setupSystemBars(settings: ZToolThemeSettings) {
        val window: Window = window
        val isDarkTheme = resolveDarkTheme(settings)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    private fun resolveDarkTheme(settings: ZToolThemeSettings): Boolean {
        return when (settings.themeMode) {
            ThemeMode.FollowSystem -> (
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    }

    companion object {
        private const val KEY_CURRENT_ROUTE = "current_route"
        private const val KEY_ENVIRONMENT_READY = "environment_ready"
    }
}

private enum class MainRoute(
    val labelRes: Int,
    val iconRes: Int
) {
    Home(R.string.gotoHomePage, R.drawable.ic_home),
    Features(R.string.gotoFeaturePage, R.drawable.ic_features),
    Audit(R.string.gotoLogPage, R.drawable.ic_audit),
    Settings(R.string.gotoSettingsPage, R.drawable.ic_settings);

    companion object {
        val entriesInOrder = listOf(Home, Features, Audit, Settings)

        fun fromName(name: String): MainRoute? {
            return entriesInOrder.firstOrNull { it.name == name }
        }
    }
}

private object HiddenRoute {
    const val SettingsTheme = "SettingsTheme"
    const val SystemUiStatusBar = "feature/system-ui/status-bar"
    const val SystemUiLockScreen = "feature/system-ui/lock-screen"
    const val SystemUiControlCenter = "feature/system-ui/control-center"
    const val SettingsDetailMagicWindowSearch = "feature/settings-detail/magic-window-search"
}

@Composable
private fun MainTabletShell(
    environmentReady: Boolean,
    selectedRoute: MainRoute,
    themeSettings: ZToolThemeSettings,
    onDestinationSelected: (MainRoute) -> Unit,
    onEnvironmentStateChanged: (Boolean) -> Unit,
    onRouteChanged: (MainRoute) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRouteState = rememberUpdatedState(selectedRoute)
    val onDestinationSelectedState = rememberUpdatedState(onDestinationSelected)

    LaunchedEffect(backStackEntry?.destination?.route) {
        backStackEntry?.destination?.route
            ?.let(MainRoute::fromName)
            ?.let(onRouteChanged)
    }

    LaunchedEffect(environmentReady, selectedRoute) {
        val targetRoute = if (environmentReady) selectedRoute else MainRoute.Home
        if (navController.currentDestination?.route != targetRoute.name) {
            navController.navigate(targetRoute.name) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val contentModifier = if (environmentReady) {
            Modifier.padding(start = MainNavigationRailWidth)
        } else {
            Modifier
        }

        MainRouteNavHost(
            modifier = contentModifier
                .fillMaxSize(),
            navController = navController,
            predictiveBackGestureEnabled = themeSettings.predictiveBackGestureEnabled,
            onEnvironmentStateChanged = onEnvironmentStateChanged
        )

        if (environmentReady) {
            key(MainNavigationRailKey) {
                MainNavigationRail(
                    selectedRouteState = selectedRouteState,
                    onDestinationSelectedState = onDestinationSelectedState
                )
            }
        }
    }
}

@Composable
private fun MainNavigationRail(
    selectedRouteState: State<MainRoute>,
    onDestinationSelectedState: State<(MainRoute) -> Unit>
) {
    ZToolNavigationRail(
        modifier = Modifier
            .width(MainNavigationRailWidth)
            .fillMaxHeight()
    ) {
        MainRoute.entriesInOrder.forEach { destination ->
            MainNavigationRailItem(
                destination = destination,
                selectedRouteState = selectedRouteState,
                onDestinationSelectedState = onDestinationSelectedState
            )
        }
    }
}

@Composable
private fun MainNavigationRailItem(
    destination: MainRoute,
    selectedRouteState: State<MainRoute>,
    onDestinationSelectedState: State<(MainRoute) -> Unit>
) {
    ZToolNavigationRailItem(
        selected = selectedRouteState.value == destination,
        onClick = { onDestinationSelectedState.value(destination) },
        icon = ImageVector.vectorResource(destination.iconRes),
        label = stringResource(destination.labelRes)
    )
}

@Composable
private fun MainRouteNavHost(
    modifier: Modifier = Modifier,
    navController: androidx.navigation.NavHostController,
    predictiveBackGestureEnabled: Boolean,
    onEnvironmentStateChanged: (Boolean) -> Unit
) {
    val mainRouteEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideIntoContainer(
            towards = routeSlideDirection(
                mainForwardDirection = AnimatedContentTransitionScope.SlideDirection.Up,
                mainBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Down,
                nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
            ),
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val mainRouteExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutOfContainer(
            towards = routeSlideDirection(
                mainForwardDirection = AnimatedContentTransitionScope.SlideDirection.Up,
                mainBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Down,
                nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
            ),
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val mainRoutePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideIntoContainer(
            towards = routeSlideDirection(
                mainForwardDirection = AnimatedContentTransitionScope.SlideDirection.Up,
                mainBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Down,
                nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
            ),
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val mainRoutePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutOfContainer(
            towards = routeSlideDirection(
                mainForwardDirection = AnimatedContentTransitionScope.SlideDirection.Up,
                mainBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Down,
                nestedForwardDirection = AnimatedContentTransitionScope.SlideDirection.Left,
                nestedBackwardDirection = AnimatedContentTransitionScope.SlideDirection.Right
            ),
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val horizontalEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
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
    val horizontalPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideIntoContainer(
            if (isForwardNavigation()) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            },
            animationSpec = tween(SettingsNavigationAnimationMillis)
        )
    }
    val horizontalPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
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
            route = MainRoute.Home.name,
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit
        ) {
            HomeMainRoute(onEnvironmentStateChanged = onEnvironmentStateChanged)
        }
        composable(
            route = MainRoute.Features.name,
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit
        ) {
            FeaturesMainRoute(
                onFeatureDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = MainRoute.Audit.name,
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit
        ) {
            AuditMainRoute()
        }
        composable(
            route = MainRoute.Settings.name,
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit
        ) {
            SettingsMainRoute(
                onOpenThemeSettings = {
                    navController.navigate(HiddenRoute.SettingsTheme) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = HiddenRoute.SettingsTheme,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            SettingsThemeMainRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Settings.name) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = FeatureDestination.PackageInstaller.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            PackageInstallerSettingsRoute(
                title = stringResource(R.string.package_installer_app_name),
                packageName = "com.android.packageinstaller",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = FeatureDestination.SettingsDetail.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            SettingsDetailRoute(
                title = stringResource(R.string.settings_app_name),
                packageName = "com.android.settings",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenStrategySearch = {
                    navController.navigate(HiddenRoute.SettingsDetailMagicWindowSearch) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = HiddenRoute.SettingsDetailMagicWindowSearch,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
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
            route = FeatureDestination.GameTool.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            GameToolSettingsRoute(
                title = stringResource(R.string.game_tool_app_name),
                packageName = "com.zui.game.service",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = FeatureDestination.SystemUi.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            SystemUiSettingsRoute(
                title = stringResource(R.string.system_ui_app_name),
                packageName = "com.android.systemui",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenStatusBar = {
                    navController.navigate(HiddenRoute.SystemUiStatusBar) {
                        launchSingleTop = true
                    }
                },
                onOpenLockScreen = {
                    navController.navigate(HiddenRoute.SystemUiLockScreen) {
                        launchSingleTop = true
                    }
                },
                onOpenControlCenter = {
                    navController.navigate(HiddenRoute.SystemUiControlCenter) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = HiddenRoute.SystemUiStatusBar,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            StatusBarSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                    stringResource(R.string.status_bar_settings_title_suffix),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = HiddenRoute.SystemUiLockScreen,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            LockScreenSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                    stringResource(R.string.lock_screen_settings_title_suffix),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = HiddenRoute.SystemUiControlCenter,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            ControlCenterSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                    stringResource(R.string.control_center_settings_title_suffix),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FeatureDestination.SystemUi.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = FeatureDestination.Ota.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            OtaSettingsRoute(
                title = stringResource(R.string.system_update_app_name),
                packageName = "com.lenovo.ota",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = FeatureDestination.Framework.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            FrameworkSettingsRoute(
                title = stringResource(R.string.system_framework_app_name),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = FeatureDestination.Launcher.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            LauncherSettingsRoute(
                title = stringResource(R.string.launcher_app_name),
                packageName = "com.zui.launcher",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = FeatureDestination.SafeCenter.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            SafeCenterSettingsRoute(
                title = stringResource(R.string.safe_center_app_name),
                packageName = "com.zui.safecenter",
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Features.name) {
                            launchSingleTop = true
                        }
                    }
                }
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
    return mainRouteIndex(targetState.destination.route) <= MainRoute.Settings.ordinal &&
        mainRouteIndex(initialState.destination.route) <= MainRoute.Settings.ordinal
}

private fun mainRouteIndex(route: String?): Int {
    return when (route) {
        MainRoute.Home.name -> 0
        MainRoute.Features.name -> 1
        MainRoute.Audit.name -> 2
        MainRoute.Settings.name -> 3
        else -> Int.MAX_VALUE
    }
}

private fun navigationRouteIndex(route: String?): Int {
    return when (route) {
        MainRoute.Home.name -> 0
        MainRoute.Features.name -> 1
        FeatureDestination.SettingsDetail.route -> 2
        FeatureDestination.GameTool.route -> 2
        FeatureDestination.Ota.route -> 2
        FeatureDestination.PackageInstaller.route -> 2
        FeatureDestination.SystemUi.route -> 2
        HiddenRoute.SystemUiStatusBar -> 3
        HiddenRoute.SystemUiLockScreen -> 3
        HiddenRoute.SystemUiControlCenter -> 3
        HiddenRoute.SettingsDetailMagicWindowSearch -> 3
        FeatureDestination.Launcher.route -> 2
        FeatureDestination.Framework.route -> 2
        FeatureDestination.SafeCenter.route -> 2
        MainRoute.Audit.name -> 4
        MainRoute.Settings.name -> 5
        HiddenRoute.SettingsTheme -> 6
        else -> 0
    }
}

private const val SettingsNavigationAnimationMillis = 320
private const val MainNavigationRailKey = "main_navigation_rail"
private val MainNavigationRailWidth = 80.dp
