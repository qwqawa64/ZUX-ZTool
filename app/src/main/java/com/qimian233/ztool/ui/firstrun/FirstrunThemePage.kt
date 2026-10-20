package com.qimian233.ztool.ui.firstrun

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.theme.ThemeSettingsContent
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings

/**
 * First-run page for picking the front end, palette and launcher icon; every control
 * applies immediately and can be changed again from the theme settings screen.
 */
@Composable
internal fun FirstrunThemePage(
    settings: ZToolThemeSettings,
    manualSeedColorText: String,
    manualSeedColorError: Boolean,
    isLastPage: Boolean,
    pageScrollState: ScrollState,
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
    onNext: (Offset) -> Unit,
    onBack: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScrollState)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeader(
                title = stringResource(R.string.page_firstrun_theme_title),
                subtitle = stringResource(R.string.page_firstrun_theme_summary)
            )

            Text(
                text = stringResource(R.string.page_firstrun_theme_hint),
                style = MaterialTheme.typography.bodyMedium
            )

            ThemeSettingsContent(
                settings = settings,
                manualSeedColorText = manualSeedColorText,
                manualSeedColorError = manualSeedColorError,
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
                showSectionTitle = false,
                bottomPadding = 0.dp
            )
        }

        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            nextText = stringResource(
                if (isLastPage) {
                    R.string.page_firstrun_theme_finish_button
                } else {
                    R.string.page_firstrun_next_step
                }
            ),
            nextEnabled = true,
            onNext = onNext,
            onDisagree = onBack,
            negativeText = stringResource(R.string.page_firstrun_previous)
        )
    }
}
