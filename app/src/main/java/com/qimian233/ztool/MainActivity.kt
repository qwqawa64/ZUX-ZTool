package com.qimian233.ztool

import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.settings.SettingsRepository
import com.qimian233.ztool.navigation.MainRouteNavHost
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import com.qimian233.ztool.navigation.MainRoute
import com.qimian233.ztool.screens.home.EnvironmentStateListener
import com.qimian233.ztool.service.LogServiceManager
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
import kotlinx.coroutines.Dispatchers
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

private const val MainNavigationRailKey = "main_navigation_rail"
private val MainNavigationRailWidth = 80.dp
