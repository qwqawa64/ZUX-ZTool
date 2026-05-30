package com.qimian233.ztool

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.DynamicColors
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import com.qimian233.ztool.service.LogServiceManager
import com.qimian233.ztool.ui.components.ZToolNavigationRail
import com.qimian233.ztool.ui.components.ZToolNavigationRailItem
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.CountdownDialog

class MainActivity : AppCompatActivity(),
    HomeFragment.EnvironmentStateListener,
    LogServiceManager.ServiceStatusListener,
    CountdownDialog.OnCountdownFinishListener {

    private var isEnvironmentReady by mutableStateOf(false)
    private var currentRoute by mutableStateOf(MainRoute.Home)
    private var themeSettings by mutableStateOf(ZToolThemeSettings())
    private var lastClickTime = 0L
    private var unregisterThemeSettingsObserver: (() -> Unit)? = null

    private val clickInterval = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            currentRoute = savedInstanceState.getString(KEY_CURRENT_ROUTE)
                ?.let(MainRoute::fromName)
                ?: MainRoute.fromDestinationId(
                    savedInstanceState.getInt(KEY_CURRENT_DESTINATION, R.id.homeFragment)
                )
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
        outState.putInt(KEY_CURRENT_DESTINATION, currentRoute.destinationId)
        outState.putBoolean(KEY_ENVIRONMENT_READY, isEnvironmentReady)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterThemeSettingsObserver?.invoke()
        unregisterThemeSettingsObserver = null
        LogServiceManager.setServiceStatusListener(null)
    }

    override fun onPositiveButtonClick() {
        getSharedPreferences("ZToolPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("isFirstLaunch", false)
            .apply()
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
        val previousState = isEnvironmentReady
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
        val prefs = getSharedPreferences("ZToolPrefs", Context.MODE_PRIVATE)
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

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
        private const val KEY_CURRENT_DESTINATION = "current_destination"
        private const val KEY_CURRENT_ROUTE = "current_route"
        private const val KEY_ENVIRONMENT_READY = "environment_ready"
    }
}

private enum class MainRoute(
    val destinationId: Int,
    val labelRes: Int,
    val iconRes: Int
) {
    Home(R.id.homeFragment, R.string.gotoHomePage, R.drawable.ic_home),
    Features(R.id.featuresFragment, R.string.gotoFeaturePage, R.drawable.ic_features),
    Audit(R.id.auditFragment, R.string.gotoLogPage, R.drawable.ic_audit),
    Settings(R.id.settingsFragment, R.string.gotoSettingsPage, R.drawable.ic_settings);

    companion object {
        val entriesInOrder = listOf(Home, Features, Audit, Settings)

        fun fromDestinationId(destinationId: Int): MainRoute {
            return entriesInOrder.firstOrNull { it.destinationId == destinationId } ?: Home
        }

        fun fromName(name: String): MainRoute? {
            return entriesInOrder.firstOrNull { it.name == name }
        }
    }
}

@Composable
private fun MainTabletShell(
    environmentReady: Boolean,
    selectedRoute: MainRoute,
    onDestinationSelected: (MainRoute) -> Unit,
    onEnvironmentStateChanged: (Boolean) -> Unit,
    onRouteChanged: (MainRoute) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

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

    Row(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (environmentReady) {
            ZToolNavigationRail {
                MainRoute.entriesInOrder.forEach { destination ->
                    ZToolNavigationRailItem(
                        selected = selectedRoute == destination,
                        onClick = { onDestinationSelected(destination) },
                        icon = ImageVector.vectorResource(destination.iconRes),
                        label = stringResource(destination.labelRes)
                    )
                }
            }
        }

        MainRouteNavHost(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            navController = navController,
            onEnvironmentStateChanged = onEnvironmentStateChanged
        )
    }
}

@Composable
private fun MainRouteNavHost(
    modifier: Modifier = Modifier,
    navController: androidx.navigation.NavHostController,
    onEnvironmentStateChanged: (Boolean) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = MainRoute.Home.name,
        modifier = modifier
    ) {
        composable(MainRoute.Home.name) {
            HomeMainRoute(onEnvironmentStateChanged = onEnvironmentStateChanged)
        }
        composable(MainRoute.Features.name) {
            FeaturesMainRoute()
        }
        composable(MainRoute.Audit.name) {
            AuditMainRoute()
        }
        composable(MainRoute.Settings.name) {
            SettingsMainRoute()
        }
    }
}
