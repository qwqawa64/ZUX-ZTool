package com.qimian233.ztool

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
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

    private var navController: NavController? = null
    private var isEnvironmentReady by mutableStateOf(false)
    private var currentDestinationId by mutableIntStateOf(R.id.homeFragment)
    private var themeSettings by mutableStateOf(ZToolThemeSettings())
    private var lastClickTime = 0L
    private var unregisterThemeSettingsObserver: (() -> Unit)? = null

    private val clickInterval = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            currentDestinationId = savedInstanceState.getInt(KEY_CURRENT_DESTINATION, R.id.homeFragment)
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
                    selectedDestinationId = currentDestinationId,
                    onDestinationSelected = ::navigateFromRail,
                    onNavHostReady = ::setupNavController
                )
            }
        }

        maybeShowAgreementDialog()
        LogServiceManager.restartServiceIfNeeded(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_DESTINATION, currentDestinationId)
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

        if (!environmentReady && navController?.currentDestination?.id != R.id.homeFragment) {
            navController?.navigate(R.id.homeFragment)
            currentDestinationId = R.id.homeFragment
        }

        if (!previousState && environmentReady && currentDestinationId != R.id.homeFragment) {
            navigateToSavedDestination()
        }
    }

    private fun setupNavController(navHostFragment: NavHostFragment) {
        if (navController === navHostFragment.navController) return

        navController = navHostFragment.navController.also { controller ->
            controller.addOnDestinationChangedListener { _, destination, _ ->
                currentDestinationId = destination.id
                if (!isEnvironmentReady && destination.id != R.id.homeFragment) {
                    controller.navigate(R.id.homeFragment)
                }
            }
        }

        if (isEnvironmentReady && currentDestinationId != R.id.homeFragment) {
            navigateToSavedDestination()
        }
    }

    private fun navigateFromRail(destinationId: Int) {
        if (!isEnvironmentReady && destinationId != R.id.homeFragment) return

        val controller = navController ?: return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < clickInterval) return
        lastClickTime = currentTime

        if (controller.currentDestination?.id == destinationId) return

        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setEnterAnim(R.anim.nav_enter)
            .setExitAnim(R.anim.nav_exit)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .setPopUpTo(controller.graph.startDestinationId, false, true)
            .build()

        try {
            controller.navigate(destinationId, null, navOptions)
        } catch (_: IllegalArgumentException) {
            controller.navigate(R.id.homeFragment)
        }
    }

    private fun navigateToSavedDestination() {
        val controller = navController ?: return
        if (currentDestinationId == R.id.homeFragment) return

        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .build()

        try {
            controller.navigate(currentDestinationId, null, navOptions)
        } catch (_: IllegalArgumentException) {
            controller.navigate(R.id.homeFragment)
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
        private const val KEY_ENVIRONMENT_READY = "environment_ready"
    }
}

private data class MainDestination(
    val id: Int,
    val labelRes: Int,
    val iconRes: Int
)

private val mainDestinations = listOf(
    MainDestination(R.id.homeFragment, R.string.gotoHomePage, R.drawable.ic_home),
    MainDestination(R.id.featuresFragment, R.string.gotoFeaturePage, R.drawable.ic_features),
    MainDestination(R.id.auditFragment, R.string.gotoLogPage, R.drawable.ic_audit),
    MainDestination(R.id.settingsFragment, R.string.gotoSettingsPage, R.drawable.ic_settings)
)

@Composable
private fun MainTabletShell(
    environmentReady: Boolean,
    selectedDestinationId: Int,
    onDestinationSelected: (Int) -> Unit,
    onNavHostReady: (NavHostFragment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (environmentReady) {
            ZToolNavigationRail {
                Spacer(modifier = Modifier.height(24.dp))
                mainDestinations.forEach { destination ->
                    ZToolNavigationRailItem(
                        selected = selectedDestinationId == destination.id,
                        onClick = { onDestinationSelected(destination.id) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = stringResource(destination.labelRes),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
        }

        LegacyNavHost(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            onNavHostReady = onNavHostReady
        )
    }
}

@Composable
private fun LegacyNavHost(
    modifier: Modifier = Modifier,
    onNavHostReady: (NavHostFragment) -> Unit
) {
    val activity = LocalContext.current as AppCompatActivity

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = R.id.nav_host_fragment
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: android.view.View) {
                        removeOnAttachStateChangeListener(this)
                        val existing = activity.supportFragmentManager
                            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                        val navHostFragment = existing ?: NavHostFragment.create(R.navigation.nav_graph).also {
                            activity.supportFragmentManager
                                .beginTransaction()
                                .replace(R.id.nav_host_fragment, it)
                                .setPrimaryNavigationFragment(it)
                                .commitNow()
                        }
                        onNavHostReady(navHostFragment)
                    }

                    override fun onViewDetachedFromWindow(view: android.view.View) = Unit
                })
            }
        }
    )
}
