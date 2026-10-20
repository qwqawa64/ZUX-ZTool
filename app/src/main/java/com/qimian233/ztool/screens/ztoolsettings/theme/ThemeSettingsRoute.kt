package com.qimian233.ztool.screens.ztoolsettings.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.components.theme.ThemeSettingsContent
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalThemeRevealController
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.viewmodel.SettingsUiState
import com.qimian233.ztool.viewmodel.rememberSettingsViewModel

@Composable
fun ThemeSettingsRoute(
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = rememberSettingsViewModel(activity)
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val revealController = LocalThemeRevealController.current

    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        ThemeSettingsScreen(
            state = uiState,
            onBack = onBack,
            onFrontendStyleChanged = { newStyle ->
                if (newStyle != uiState.themeSettings.frontendStyle) {
                    revealController.triggerReveal(onAction = { viewModel.setFrontendStyle(newStyle) })
                }
            },
            onThemeModeChanged = viewModel::setThemeMode,
            onMaterialColorSpecChanged = viewModel::setMaterialColorSpec,
            onMaterialPaletteChanged = viewModel::setMaterialPalette,
            onDynamicColorChanged = viewModel::setDynamicColorEnabled,
            onAmoledBlackChanged = viewModel::setAmoledBlackEnabled,
            onUseAlternativeIconChanged = viewModel::setUseAlternativeIcon,
            onPredictiveBackGestureChanged = viewModel::setPredictiveBackGestureEnabled,
            onEnableFloatingBottomBarChanged = viewModel::setEnableFloatingBottomBar,
            onEnableFloatingBottomBarBlurChanged = viewModel::setEnableFloatingBottomBarBlur,
            onManualColorChanged = viewModel::setManualColorEnabled,
            onManualSeedColorTextChanged = viewModel::setManualSeedColorText,
            onManualSeedColorEditingFinished = viewModel::finishManualSeedColorEditing,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        )
    }
}

@Composable
private fun ThemeSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onFrontendStyleChanged: (FrontendStyle) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMaterialColorSpecChanged: (MaterialColorSpec) -> Unit,
    onMaterialPaletteChanged: (MaterialPalette) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onUseAlternativeIconChanged: (Boolean) -> Unit,
    onPredictiveBackGestureChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarBlurChanged: (Boolean) -> Unit,
    onManualColorChanged: (Boolean) -> Unit,
    onManualSeedColorTextChanged: (String) -> Unit,
    onManualSeedColorEditingFinished: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry,
    modifier: Modifier = Modifier
) {
    ZToolScaffold(
        modifier = modifier,
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.page_settings_app_ui_theme_settings),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ThemeSettingsContent(
                    settings = state.themeSettings,
                    manualSeedColorText = state.manualSeedColorText,
                    manualSeedColorError = state.manualSeedColorError,
                    onFrontendStyleChanged = onFrontendStyleChanged,
                    onThemeModeChanged = onThemeModeChanged,
                    onMaterialColorSpecChanged = onMaterialColorSpecChanged,
                    onMaterialPaletteChanged = onMaterialPaletteChanged,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onAmoledBlackChanged = onAmoledBlackChanged,
                    onUseAlternativeIconChanged = onUseAlternativeIconChanged,
                    onPredictiveBackGestureChanged = onPredictiveBackGestureChanged,
                    onEnableFloatingBottomBarChanged = onEnableFloatingBottomBarChanged,
                    onEnableFloatingBottomBarBlurChanged = onEnableFloatingBottomBarBlurChanged,
                    onManualColorChanged = onManualColorChanged,
                    onManualSeedColorTextChanged = onManualSeedColorTextChanged,
                    onManualSeedColorEditingFinished = onManualSeedColorEditingFinished,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}
