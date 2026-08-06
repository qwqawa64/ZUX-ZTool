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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.home.HomeRepository
import com.qimian233.ztool.data.settings.SettingsRepository
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import com.qimian233.ztool.service.LogServiceManager
import com.qimian233.ztool.settingactivity.gametool.GameToolSettingsRoute
import com.qimian233.ztool.settingactivity.launcher.LauncherSettingsRoute
import com.qimian233.ztool.settingactivity.mobiledesktop.MobileDesktopSettingsRoute
import com.qimian233.ztool.settingactivity.ota.OtaSettingsRoute
import com.qimian233.ztool.settingactivity.packageinstaller.PackageInstallerSettingsRoute
import com.qimian233.ztool.settingactivity.safecenter.SafeCenterSettingsRoute
import com.qimian233.ztool.settingactivity.setting.SettingsDetailRoute
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.SearchPageRoute
import com.qimian233.ztool.settingactivity.systemframework.FrameworkSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.ControlCenter.ControlCenterSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.SystemUiSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.animation.AnimationWallpaperSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.lockscreen.LockScreenSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.misc.SystemUiMiscSettingsRoute
import com.qimian233.ztool.settingactivity.systemui.statusBarSetting.StatusBarSettingsRoute
import com.qimian233.ztool.ui.components.FloatingBottomBar
import com.qimian233.ztool.ui.components.FloatingBottomBarItem
import com.qimian233.ztool.ui.components.ZToolNavigationBar
import com.qimian233.ztool.ui.components.ZToolNavigationBarItem
import com.qimian233.ztool.ui.components.ZToolNavigationRail
import com.qimian233.ztool.ui.components.ZToolNavigationRailItem
import com.qimian233.ztool.ui.components.ZToolNavigationRailState
import com.qimian233.ztool.ui.components.collapseNavigationRailOnPointerDown
import com.qimian233.ztool.ui.components.rememberZToolNavigationRailState
import com.qimian233.ztool.ui.firstrun.AgreementDisplayMode
import com.qimian233.ztool.ui.firstrun.FirstrunAgreementRoute
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalEnableFloatingBottomBar
import com.qimian233.ztool.ui.theme.LocalEnableFloatingBottomBarBlur
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import com.qimian233.ztool.utils.ConfigUpgrade
import com.qimian233.ztool.viewmodel.HomeViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

class MainActivity : ComponentActivity(),
    EnvironmentStateListener,
    LogServiceManager.ServiceStatusListener {

    private var isEnvironmentReady by mutableStateOf(false)
    private var currentRoute by mutableStateOf(MainRoute.Home)
    private var themeSettings by mutableStateOf(ZToolThemeSettings())
    private var agreementDisplayMode by mutableStateOf<AgreementDisplayMode?>(null)
    private var lastClickTime = 0L
    private var unregisterThemeSettingsObserver: (() -> Unit)? = null
    private val agreementRepository by lazy { AgreementRepository(this) }

    private val clickInterval = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            ConfigUpgrade.configUpgrader(this@MainActivity)
        }

        if (savedInstanceState != null) {
            currentRoute = savedInstanceState.getString(KEY_CURRENT_ROUTE)
                ?.let(MainRoute::fromName)
                ?: MainRoute.Home
            isEnvironmentReady = savedInstanceState.getBoolean(KEY_ENVIRONMENT_READY, false)
            agreementDisplayMode = savedInstanceState.getString(KEY_AGREEMENT_DISPLAY_MODE)
                ?.let(AgreementDisplayMode::valueOf)
        }
        if (agreementDisplayMode == null) {
            agreementDisplayMode = resolveAgreementDisplayMode()
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
                com.qimian233.ztool.ui.theme.ThemeRevealProvider {
                    val currentAgreementMode = agreementDisplayMode
                    if (currentAgreementMode == null) {
                        MainTabletShell(
                            environmentReady = isEnvironmentReady,
                            selectedRoute = currentRoute,
                            themeSettings = themeSettings,
                            onDestinationSelected = ::navigateFromRail,
                            onEnvironmentStateChanged = ::onEnvironmentStateChanged,
                            onRouteChanged = ::setCurrentRouteFromHost
                        )
                    } else {
                        FirstrunAgreementRoute(
                            agreementDisplayMode = currentAgreementMode,
                            onAgreementAccepted = {
                                agreementDisplayMode = null
                            },
                            onAgreementDeclined = { finishAffinity() }
                        )
                    }
                }
            }
        }

        LogServiceManager.restartServiceIfNeeded(this)

        // 启动时清理超量日志 + 同步 LSPosed 日志
        val settingsRepo = SettingsRepository(applicationContext)
        settingsRepo.cleanupAppLogsIfNeeded()
        settingsRepo.syncLsposedLogs()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_ROUTE, currentRoute.name)
        outState.putBoolean(KEY_ENVIRONMENT_READY, isEnvironmentReady)
        agreementDisplayMode?.let { outState.putString(KEY_AGREEMENT_DISPLAY_MODE, it.name) }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterThemeSettingsObserver?.invoke()
        unregisterThemeSettingsObserver = null
        LogServiceManager.clearCallbacks()
    }

    private fun resolveAgreementDisplayMode(): AgreementDisplayMode? {
        val acceptedVersion = agreementRepository.getAcceptedAgreementVersion()
            ?: return AgreementDisplayMode.FirstRun
        return if (compareAgreementVersions(
                acceptedVersion,
                agreementRepository.getCurrentAgreementVersion()
            ) < 0
        ) {
            AgreementDisplayMode.UpdateOnly
        } else {
            null
        }
    }

    override fun onServiceStarted() {
    }

    override fun onServiceStopped() {
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
        private const val KEY_AGREEMENT_DISPLAY_MODE = "agreement_display_mode"
    }
}

private fun compareAgreementVersions(left: String, right: String): Int {
    val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
    val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
    val maxSize = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }
    return 0
}

private enum class MainRoute(
    val labelRes: Int,
    val iconRes: Int
) {
    Home(R.string.gotoHomePage, R.drawable.ic_home),
    Features(R.string.gotoFeaturePage, R.drawable.ic_features),
    Settings(R.string.gotoSettingsPage, R.drawable.ic_settings);

    companion object {
        val entriesInOrder = listOf(Home, Features, Settings)

        fun fromName(name: String): MainRoute? {
            return entriesInOrder.firstOrNull { it.name == name }
        }
    }
}

private object HiddenRoute {
    const val SETTINGS_THEME = "SettingsTheme"
    const val SETTINGS_ABOUT = "SettingsAbout"
    const val SETTINGS_ADVANCED = "SettingsAdvanced"
    const val SYSTEM_UI_STATUS_BAR = "feature/system-ui/status-bar"
    const val SYSTEM_UI_LOCK_SCREEN = "feature/system-ui/lock-screen"
    const val SYSTEM_UI_CONTROL_CENTER = "feature/system-ui/control-center"
    const val SYSTEM_UI_ANIMATION_WALLPAPER = "feature/system-ui/animation-wallpaper"
    const val SYSTEM_UI_MISC = "feature/system-ui/misc"
    const val SETTINGS_DETAIL_MAGIC_WINDOW_SEARCH = "feature/settings-detail/magic-window-search"
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ztoolThemeSpec = LocalZToolThemeSpec.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    // When Miuix FloatingBottomBar is enabled, force BottomBar even in landscape
    // since the floating pill design works well in both orientations.
    val useNavigationRail = isLandscape
            && !(ztoolThemeSpec.style == FrontendStyle.Miuix && enableFloatingBottomBar)

    val bottomBarBackdrop: LayerBackdrop? =
        if (ztoolThemeSpec.style == FrontendStyle.Miuix && enableFloatingBottomBar) {
            val surfaceColor = LocalZToolColorScheme.current.surface
            rememberLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }
        } else {
            null
        }

    if (useNavigationRail) {
        // === Rail layout (landscape) ===
        val navigationRailState = rememberZToolNavigationRailState()
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val contentModifier = if (environmentReady) {
                Modifier.padding(start = MainNavigationRailWidth)
            } else {
                Modifier
            }

            MainRouteNavHost(
                modifier = contentModifier
                    .fillMaxSize()
                    .collapseNavigationRailOnPointerDown(navigationRailState),
                navController = navController,
                predictiveBackGestureEnabled = themeSettings.predictiveBackGestureEnabled,
                onEnvironmentStateChanged = onEnvironmentStateChanged
            )

            if (environmentReady) {
                key(MainNavigationRailKey) {
                    MainNavigationRail(
                        selectedRouteState = selectedRouteState,
                        onDestinationSelectedState = onDestinationSelectedState,
                        navigationRailState = navigationRailState
                    )
                }
            }
        }
    } else {
        // === Bottom bar layout (portrait) ===
        val useFloating = (ztoolThemeSpec.style == FrontendStyle.Miuix
                && enableFloatingBottomBar)

        if (useFloating) {
            // Floating mode: overlay the pill on top of content — no Scaffold bottomBar slot
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (ztoolThemeSpec.style) {
                    FrontendStyle.Miuix -> {
                        top.yukonga.miuix.kmp.basic.Scaffold(
                            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                        ) { innerPadding ->
                            MainRouteNavHost(useHorizontalAnimation = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .let { if (bottomBarBackdrop != null) it.layerBackdrop(bottomBarBackdrop) else it },
                                navController = navController,
                                predictiveBackGestureEnabled = themeSettings.predictiveBackGestureEnabled,
                                onEnvironmentStateChanged = onEnvironmentStateChanged
                            )
                        }
                    }
                    FrontendStyle.Material3Expressive -> {
                        Scaffold(
                            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                        ) { innerPadding ->
                            MainRouteNavHost(useHorizontalAnimation = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                navController = navController,
                                predictiveBackGestureEnabled = themeSettings.predictiveBackGestureEnabled,
                                onEnvironmentStateChanged = onEnvironmentStateChanged
                            )
                        }
                    }
                }

                if (environmentReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        MainNavigationBar(
                            selectedRouteState = selectedRouteState,
                            onDestinationSelectedState = onDestinationSelectedState,
                            bottomBarBackdrop = bottomBarBackdrop
                        )
                    }
                }
            }
        } else {
            // Standard mode: Scaffold with built-in bottomBar slot
            val bottomBar: @Composable () -> Unit = {
                if (environmentReady) {
                    MainNavigationBar(
                        selectedRouteState = selectedRouteState,
                        onDestinationSelectedState = onDestinationSelectedState,
                        bottomBarBackdrop = null
                    )
                }
            }

            val contentModifier = Modifier.fillMaxSize()

            when (ztoolThemeSpec.style) {
                FrontendStyle.Miuix -> {
                    top.yukonga.miuix.kmp.basic.Scaffold(
                        bottomBar = bottomBar,
                        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                    ) { innerPadding ->
                        MainRouteNavHost(useHorizontalAnimation = true,
                            modifier = contentModifier.padding(innerPadding),
                            navController = navController,
                            predictiveBackGestureEnabled = themeSettings.predictiveBackGestureEnabled,
                            onEnvironmentStateChanged = onEnvironmentStateChanged
                        )
                    }
                }
                FrontendStyle.Material3Expressive -> {
                    Scaffold(
                        bottomBar = bottomBar,
                        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                    ) { innerPadding ->
                        MainRouteNavHost(useHorizontalAnimation = true,
                            modifier = contentModifier.padding(innerPadding),
                            navController = navController,
                            predictiveBackGestureEnabled = themeSettings.predictiveBackGestureEnabled,
                            onEnvironmentStateChanged = onEnvironmentStateChanged
                        )
                    }
                }
            }
        }
}
}

@Composable
private fun MainNavigationRail(
    selectedRouteState: State<MainRoute>,
    onDestinationSelectedState: State<(MainRoute) -> Unit>,
    navigationRailState: ZToolNavigationRailState
) {
    ZToolNavigationRail(
        modifier = Modifier
            .fillMaxHeight(),
        state = navigationRailState
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
private fun MainNavigationBar(
    selectedRouteState: State<MainRoute>,
    onDestinationSelectedState: State<(MainRoute) -> Unit>,
    bottomBarBackdrop: Backdrop? = null
) {
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val ztoolThemeSpec = LocalZToolThemeSpec.current
    val useFloating = ztoolThemeSpec.style == FrontendStyle.Miuix
            && enableFloatingBottomBar
            && bottomBarBackdrop != null

    if (useFloating) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            FloatingBottomBar(
                modifier = Modifier.padding(bottom = 24.dp),
                selectedIndex = { MainRoute.entriesInOrder.indexOf(selectedRouteState.value) },
                onSelected = { index ->
                    MainRoute.entriesInOrder.getOrNull(index)
                        ?.let { onDestinationSelectedState.value(it) }
                },
                backdrop = bottomBarBackdrop,
                tabsCount = MainRoute.entriesInOrder.size,
                isBlurEnabled = enableFloatingBottomBarBlur,
            ) {
                MainRoute.entriesInOrder.forEach { destination ->
                    FloatingBottomBarItem(
                        onClick = { onDestinationSelectedState.value(destination) },
                        modifier = Modifier.defaultMinSize(minWidth = 96.dp)
                    ) {
                        MiuixIcon(
                            imageVector = ImageVector.vectorResource(destination.iconRes),
                            contentDescription = stringResource(destination.labelRes),
                            tint = LocalZToolColorScheme.current.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        MiuixText(
                            text = stringResource(destination.labelRes),
                            fontSize = 13.sp,
                            color = LocalZToolColorScheme.current.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        return
    }

    ZToolNavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        MainRoute.entriesInOrder.forEach { destination ->
            MainNavigationBarItem(
                destination = destination,
                selectedRouteState = selectedRouteState,
                onDestinationSelectedState = onDestinationSelectedState
            )
        }
    }
}

@Composable
private fun RowScope.MainNavigationBarItem(
    destination: MainRoute,
    selectedRouteState: State<MainRoute>,
    onDestinationSelectedState: State<(MainRoute) -> Unit>
) {
    ZToolNavigationBarItem(
        selected = selectedRouteState.value == destination,
        onClick = { onDestinationSelectedState.value(destination) },
        icon = ImageVector.vectorResource(destination.iconRes),
        label = stringResource(destination.labelRes),
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun MainRouteNavHost(
    modifier: Modifier = Modifier,
    navController: androidx.navigation.NavHostController,
    predictiveBackGestureEnabled: Boolean,
    onEnvironmentStateChanged: (Boolean) -> Unit,
    useHorizontalAnimation: Boolean = false
) {
    val context = LocalContext.current
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
            route = MainRoute.Settings.name,
            enterTransition = mainRouteEnter,
            exitTransition = mainRouteExit,
            popEnterTransition = mainRoutePopEnter,
            popExitTransition = mainRoutePopExit
        ) {
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
                }
            )
        }
        composable(
            route = HiddenRoute.SETTINGS_THEME,
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
            route = SettingsAboutRouteName,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            val activity = context as MainActivity
            val homeViewModel = androidx.compose.runtime.remember {
                val repository = HomeRepository(
                    context = context.applicationContext,
                    moduleActiveChecker = ModuleActivationProbe::isModuleActive
                )
                ViewModelProvider(
                    activity,
                    HomeViewModelFactory(repository)
                )[HomeViewModel::class.java]
            }
            val homeState by homeViewModel.uiState.collectAsState()

            SettingsAboutRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(MainRoute.Settings.name) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenGithub = {
                    openExternalLink(context, "https://github.com/qwqawa64/ZUX-ZTool")
                },
                onOpenUnfuckZUI = {
                    openExternalLink(context, "https://github.com/dantmnf/UnfuckZUI")
                },
                onOpenQimian233 = {
                    openExternalLink(
                        context,
                        "http://www.coolapk.com/u/10099756",
                        true,
                        "com.coolapk.market"
                    )
                },
                onOpenWasdDestroy = {
                    openExternalLink(
                        context,
                        "http://www.coolapk.com/u/18634835",
                        true,
                        "com.coolapk.market"
                    )
                },
                onOpenZuxOsPlus = {
                    openExternalLink(context, "https://github.com/morannlx/me.inkdye.zuxos")
                },
                onOpenUdl = {
                    openExternalLink(context, "https://github.com/uuuddddl")
                },
                onCheckUpdate = homeViewModel::checkAppUpdate,
                isCheckingUpdate = homeState.isCheckingAppUpdate,
                updateCheckCompleted = homeState.updateCheckCompleted,
                updateInfo = homeState.updateInfo,
                onOpenUpdate = { url ->
                    openExternalLink(context, url)
                }
            )
        }
        composable(
            route = HiddenRoute.SETTINGS_ADVANCED,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            SettingsAdvancedRoute(
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
                    navController.navigate(HiddenRoute.SETTINGS_DETAIL_MAGIC_WINDOW_SEARCH) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = HiddenRoute.SETTINGS_DETAIL_MAGIC_WINDOW_SEARCH,
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
            route = HiddenRoute.SYSTEM_UI_STATUS_BAR,
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
            route = HiddenRoute.SYSTEM_UI_LOCK_SCREEN,
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
            route = HiddenRoute.SYSTEM_UI_CONTROL_CENTER,
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
            route = HiddenRoute.SYSTEM_UI_ANIMATION_WALLPAPER,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            AnimationWallpaperSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                        " — 动画与壁纸",
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
            route = HiddenRoute.SYSTEM_UI_MISC,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            SystemUiMiscSettingsRoute(
                title = stringResource(R.string.system_ui_app_name) +
                        " — " + stringResource(R.string.systemUIMisc),
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
            route = FeatureDestination.MobileDesktop.route,
            enterTransition = horizontalEnter,
            exitTransition = horizontalExit,
            popEnterTransition = horizontalPopEnter,
            popExitTransition = horizontalPopExit
        ) {
            MobileDesktopSettingsRoute(
                title = stringResource(R.string.mobile_desktop_app_name),
                packageName = "com.motorola.mobiledesktop",
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
    val targetIndex = mainRouteIndex(targetState.destination.route)
    val initialIndex = mainRouteIndex(initialState.destination.route)
    return targetIndex != initialIndex && targetIndex != -1 && initialIndex != -1
}

private fun mainRouteIndex(route: String?): Int {
    if (route == null) return -1
    return when {
        route == MainRoute.Home.name -> 0
        route == MainRoute.Features.name || route.startsWith("feature/") -> 1
        route == MainRoute.Settings.name || route.startsWith("Settings") -> 2
        else -> -1
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
        MainRoute.Settings.name -> 3
        HiddenRoute.SETTINGS_THEME -> 4
        HiddenRoute.SETTINGS_ABOUT -> 4
        HiddenRoute.SETTINGS_ADVANCED -> 4
        else -> 0
    }
}

private const val SettingsNavigationAnimationMillis = 320
private const val MainNavigationRailKey = "main_navigation_rail"
private val MainNavigationRailWidth = 80.dp
