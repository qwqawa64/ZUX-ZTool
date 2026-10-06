package com.qimian233.ztool.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.BuildConfig
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.viewmodel.HomeUiState
import com.qimian233.ztool.viewmodel.UpdateInfo

/*
 * The whole home-screen card stack, extracted from `HomeRoute.kt` so that
 * `HomeCardPreviewRoute` can render it verbatim — same order, same width cap, same
 * paddings — with a mock HomeUiState. Several of these cards depend on the device
 * environment rather than on a user action (unofficial system, pending update,
 * module/root state), which makes their geometry untestable on a real device without
 * this shared path.
 *
 * Keep the composables here free of repository/network access: they take plain
 * values and lambdas so the preview and the home screen stay identical.
 */

/**
 * Horizontal gutter every home card is inset by, relative to the card column. It is
 * shared on purpose: the surfaces in the column (the warning, dev-build, update and
 * module-status cards, plus the system-info list) have to line up with each other, and
 * a card that forgets its gutter silently renders 16dp wider than its neighbours.
 */
internal val HomeCardGutter = 8.dp

/** Applies [HomeCardGutter] around a full-width home card surface. */
internal fun Modifier.homeCardGutter(): Modifier = this.padding(horizontal = HomeCardGutter)

/**
 * Container of the home card column. The width cap and the paddings are part of the
 * geometry, so every mirrored rendering (the home screen and the preview) must use
 * this exact chain — a card rendered through any other container cannot be compared
 * with the home page.
 *
 * The cap is applied *before* `fillMaxWidth`: the other order locks min/max to the
 * page width first, after which `widthIn` can only lower the maximum and the cap
 * silently stops binding. With this order the column is capped and centered by the
 * surrounding [com.qimian233.ztool.ui.components.ZToolPageSurface] in both frontend
 * styles, so no style branch is needed here.
 */
@Composable
internal fun Modifier.homeCardColumn(): Modifier = this
    .fillMaxHeight()
    .widthIn(max = 1120.dp)
    .fillMaxWidth()
    .verticalScroll(rememberScrollState())
    .padding(horizontal = 32.dp, vertical = 32.dp)

/**
 * The card column of the home page, in home-screen order. Callers only have to supply
 * the page surface around it (see `HomeScreen` and `HomeCardPreviewRoute`).
 */
@Composable
internal fun HomeCardStack(
    state: HomeUiState,
    onDismissNonZuxOsWarning: () -> Unit,
    onToggleUpdateExpanded: () -> Unit,
    onIgnoreUpdate: (Int) -> Unit,
    onOpenUpdate: (String) -> Unit,
    onRefreshEnvironment: () -> Unit
) {
    Column(modifier = Modifier.homeCardColumn()) {

        Spacer(modifier = Modifier.height(24.dp))

        if (!state.isZuxOsDevice && !state.isNonZuxOsWarningDismissed) {
            NonZuxOsCard(onDismiss = onDismissNonZuxOsWarning)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (BuildConfig.IS_DEV_BUILD) {
            DevBuildCard()
            Spacer(modifier = Modifier.height(16.dp))
        }

        AnimatedVisibility(visible = state.environmentReady && state.updateInfo != null) {
            state.updateInfo?.let { update ->
                Column {
                    UpdateCard(
                        update = update,
                        onToggleExpanded = onToggleUpdateExpanded,
                        onIgnore = { onIgnoreUpdate(update.versionCode) },
                        onOpenUpdate = { onOpenUpdate(update.downloadUrl) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        ModuleStatusCard(state, onRefreshEnvironment)

        if (state.environmentReady) {
            Spacer(modifier = Modifier.height(16.dp))
            SystemInfoCard(state)
        }

        Spacer(modifier = Modifier.padding(48.dp))
    }
}

/**
 * Warning shown on devices that do not run ZUX OS / ZUI, where most hooks cannot
 * work. Dismissible, unlike [DevBuildCard].
 */
@Composable
internal fun NonZuxOsCard(onDismiss: () -> Unit) {
    ZToolCard(
        // The dismiss button inside keeps its own clickable, so it consumes the
        // press before this card-level one and the card stays inert there.
        modifier = Modifier
            .fillMaxWidth()
            .homeCardGutter()
            .cardPressScale()
            .clickable(onClick = {}),
        containerColor = LocalZToolColorScheme.current.errorContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onErrorContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.page_home_non_zuxos_warn),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ZToolTextButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.page_home_non_zuxos_dismiss),
                    isPrimary = false
                )
            }
        }
    }
}

/**
 * Standing warning for every build that did not come out of the official release
 * pipeline (nightly / manually triggered CI, local builds). Intentionally not
 * dismissible: the fact cannot change while the APK stays installed.
 */
@Composable
internal fun DevBuildCard() {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .homeCardGutter()
            .cardPressScale()
            .clickable(onClick = {}),
        containerColor = LocalZToolColorScheme.current.errorContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onErrorContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.page_home_dev_build_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.page_home_dev_build_warn),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onErrorContainer
            )
        }
    }
}

/**
 * App-update notice. [update] carries the changelog text fetched from the release
 * feed; `expanded` decides whether it is clamped to four lines or shown in full.
 * Tapping the card toggles that state, so there is one card, not one per state.
 *
 * The 8dp side inset is the same one the warning, dev-build and module-status cards
 * use: without it this card renders 16dp wider than every other card in the column.
 */
@Composable
internal fun UpdateCard(
    update: UpdateInfo,
    onToggleExpanded: () -> Unit,
    onIgnore: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .homeCardGutter()
            .clickable(onClick = onToggleExpanded),
        containerColor = LocalZToolColorScheme.current.tertiaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.page_home_update_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.page_home_build_code, update.versionName, update.versionCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalZToolColorScheme.current.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = update.changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onTertiaryContainer,
                maxLines = if (update.expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ZToolTextButton(onClick = onIgnore, text = stringResource(R.string.page_home_update_button_ignore), isPrimary = false)
                Spacer(modifier = Modifier.width(8.dp))
                ZToolButton(onClick = onOpenUpdate) {
                    Text(stringResource(R.string.page_home_update_button_update))
                }
            }
        }
    }
}

@Composable
internal fun ModuleStatusCard(
    state: HomeUiState,
    onRefreshEnvironment: () -> Unit
) {
    val themeSpec = LocalZToolThemeSpec.current
    val isDefaultColor = !themeSpec.dynamicColorEnabled && !themeSpec.manualColorEnabled
    val bothActive = state.isModuleActive && state.isRootAvailable
    val anyActive = state.isModuleActive || state.isRootAvailable

    val isDark = LocalZToolColorScheme.current.surface.luminance() < 0.5f
    val containerColor = when {
        bothActive && isDefaultColor -> if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFA5D6A7)
        !bothActive && anyActive -> if (isDark) Color(0xFFE65100).copy(alpha = 0.35f) else Color(0xFFFFE082)
        !anyActive -> if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFEF9A9A)
        else -> LocalZToolColorScheme.current.primaryContainer
    }
    val contentColor = when {
        bothActive && isDefaultColor -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
        !bothActive && anyActive -> if (isDark) Color(0xFFFFE082) else Color(0xFFE65100)
        !anyActive -> if (isDark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
        else -> LocalZToolColorScheme.current.onPrimaryContainer
    }
    val iconColor = when {
        bothActive && isDefaultColor -> if (isDark) Color(0xFF66BB6A) else Color(0xFF4CAF50)
        !bothActive && anyActive -> if (isDark) Color(0xFFFFB300) else Color(0xFFFF8F00)
        !anyActive -> if (isDark) Color(0xFFEF5350) else Color(0xFFE53935)
        else -> LocalZToolColorScheme.current.primary
    }

    val statusText = when {
        bothActive -> stringResource(R.string.page_home_module_active)
        state.isModuleActive -> stringResource(R.string.page_home_no_root_permission)
        state.isRootAvailable -> stringResource(R.string.page_home_module_inactive)
        else -> stringResource(R.string.page_home_no_root_and_module_inactive)
    }

    val summaryText = if (bothActive) {
        state.moduleVersion.ifBlank { stringResource(R.string.common_loading) }
    } else {
        null
    }

    val icon = when {
        bothActive -> Icons.Rounded.CheckCircle
        anyActive -> Icons.Rounded.Warning
        else -> Icons.Rounded.Cancel
    }

    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .homeCardGutter()
            .cardPressScale()
            .then(
                if (!bothActive) Modifier.clickable { onRefreshEnvironment() }
                else Modifier.clickable(onClick = {})
            ),
        containerColor = containerColor,
        defaultElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        if (state.apiVersion > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            ApiVersionBadge(
                                apiVersion = state.apiVersion,
                                colorOnContainer = contentColor
                            )
                        }
                    }
                    if (summaryText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                    if (!bothActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.page_home_environment_tap_to_refresh),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private data class SystemInfoRow(
    val key: String,
    val title: String,
    val value: String,
    val icon: ImageVector
)

@Composable
private fun ApiVersionBadge(
    apiVersion: Int,
    colorOnContainer: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colorOnContainer.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.page_home_api_badge_format, apiVersion),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colorOnContainer
        )
    }
}

@Composable
private fun SystemInfoCard(state: HomeUiState) {
    val unknownText = stringResource(R.string.page_home_place_holder_unknown)

    val infoRows = listOf(
        SystemInfoRow(
            key = "home_device_code_name",
            title = stringResource(R.string.page_home_device_code_name),
            value = state.deviceModel,
            icon = Icons.Rounded.PhoneAndroid
        ),
        SystemInfoRow(
            key = "home_android_version",
            title = stringResource(R.string.page_home_android_version),
            value = state.androidVersion,
            icon = Icons.Rounded.Android
        ),
        SystemInfoRow(
            key = "home_build_version",
            title = stringResource(R.string.page_home_build_version),
            value = state.buildVersion,
            icon = Icons.Rounded.Memory
        ),
        SystemInfoRow(
            key = "home_kernel_version",
            title = stringResource(R.string.page_home_kernel_version),
            value = state.kernelVersion,
            icon = Icons.Rounded.DeveloperBoard
        ),
        SystemInfoRow(
            key = "home_current_slot",
            title = stringResource(R.string.page_home_current_slot),
            value = state.currentSlot,
            icon = Icons.Rounded.SwapHoriz
        ),
        SystemInfoRow(
            key = "home_rom_region",
            title = stringResource(R.string.page_home_rom_region),
            value = state.romRegion,
            icon = Icons.Rounded.Public
        ),
        SystemInfoRow(
            key = "home_root_source",
            title = stringResource(R.string.page_home_root),
            value = state.rootSource,
            icon = Icons.Rounded.Security
        ),
        SystemInfoRow(
            key = "home_framework_info",
            title = stringResource(R.string.page_home_framework),
            value = state.frameworkVersion,
            icon = Icons.Rounded.Extension
        )
    )

    ZToolSettingsList(
        sections = listOf(
            SettingSection(
                items = infoRows.map { row ->
                    SettingItem.Action(
                        key = row.key,
                        title = row.title,
                        summary = row.value.ifBlank { unknownText },
                        icon = row.icon,
                        onClick = {}
                    )
                }
            )
        ),
        // Same gutter as the cards above it, so the rows line up with their edges.
        gutter = HomeCardGutter
    )
}

/**
 * Press feedback for a whole card: scales it down slightly while a finger is held
 * on it. It listens on the Initial pointer pass so it also works for containers
 * whose children consume the press themselves (a card that hosts its own buttons,
 * for example).
 *
 * The shape is clipped by the same layer that scales. A separate `Modifier.clip`
 * in front of the scale layer loses the clip, which leaves the click ripple of a
 * following `clickable` square inside a rounded card.
 */
@Composable
internal fun Modifier.cardPressScale(
    clipShape: Shape = RoundedCornerShape(16.dp),
    pressedScale: Float = 0.98f
): Modifier {
    val isPressed = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed.value) pressedScale else 1f,
        label = "cardPressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shape = clipShape
            clip = true
        }
        .pointerInput(Unit) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                isPressed.value = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // A drag belongs to the surrounding scroll container, not to the card.
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > touchSlop) break
                    }
                } finally {
                    isPressed.value = false
                }
            }
        }
}
