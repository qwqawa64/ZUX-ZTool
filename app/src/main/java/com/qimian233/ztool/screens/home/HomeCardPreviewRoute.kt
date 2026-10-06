package com.qimian233.ztool.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.viewmodel.HomeUiState
import com.qimian233.ztool.viewmodel.UpdateInfo

/**
 * Developer-only preview of the home screen's card stack.
 *
 * The stack is rendered by the same `HomeCardStack` the home screen uses — same
 * order, same width cap, same paddings — from a mock [HomeUiState], so spacing and
 * geometry problems can be diagnosed on any device, including the states that cannot
 * be produced on demand (a non-ZUX OS device, a pending update, a missing root or
 * module). The console at the top edits that mock state; the section at the bottom
 * shows the one-line-changelog variant of the update card, which is not visible in
 * the stack itself.
 *
 * Reachable from Settings → Advanced; that row, this screen and its nav destination
 * exist only in `BuildConfig.IS_DEV_BUILD` builds.
 */
@Composable
fun HomeCardPreviewRoute(onBack: () -> Unit) {
    var mockState by remember { mutableStateOf(previewHomeState()) }
    var consoleExpanded by rememberSaveable { mutableStateOf(false) }
    var variantVisible by rememberSaveable { mutableStateOf(true) }

    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.home_card_preview_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = stringResource(R.string.home_card_preview_note),
                style = MaterialTheme.typography.bodySmall,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            MockConsole(
                state = mockState,
                expanded = consoleExpanded,
                onExpandedChange = { consoleExpanded = it },
                onStateChange = { mockState = it }
            )

            HorizontalDivider()

            // The mirrored home stack keeps ownership of its own scroll container, so
            // it stays inside this weighted box rather than a parent scroller.
            Box(modifier = Modifier.weight(1f)) {
                ZToolPageSurface(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    HomeCardStack(
                        state = mockState,
                        onDismissNonZuxOsWarning = {
                            mockState = mockState.copy(isNonZuxOsWarningDismissed = true)
                        },
                        onToggleUpdateExpanded = {
                            mockState = mockState.copy(
                                updateInfo = mockState.updateInfo?.let { it.copy(expanded = !it.expanded) }
                            )
                        },
                        // Mirrors the home behaviour: ignoring removes the card.
                        onIgnoreUpdate = { mockState = mockState.copy(updateInfo = null) },
                        onOpenUpdate = { /* Dev preview: never open an external browser. */ },
                        onRefreshEnvironment = { /* The console drives the mocked environment. */ }
                    )
                }
            }

            HorizontalDivider()

            OneLineChangelogVariant(
                visible = variantVisible,
                onVisibleChange = { variantVisible = it }
            )
        }
    }
}

/**
 * Switches that drive the mocked [HomeUiState]. Collapsed it shows one summary line,
 * so the mirrored stack normally gets the full preview height.
 */
@Composable
private fun MockConsole(
    state: HomeUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onStateChange: (HomeUiState) -> Unit
) {
    val notZuxOs = !state.isZuxOsDevice
    val moduleActive = state.isModuleActive
    val rootAvailable = state.isRootAvailable
    val updateAvailable = state.updateInfo != null

    Column(modifier = Modifier.fillMaxWidth()) {
        PreviewStripHeader(
            title = stringResource(R.string.home_card_preview_console_title),
            actionLabel = stringResource(
                if (expanded) R.string.home_card_preview_collapse
                else R.string.home_card_preview_expand
            ),
            onAction = { onExpandedChange(!expanded) }
        )

        if (expanded) {
            ZToolSettingsList(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                sections = listOf(
                    SettingSection(
                        items = listOf(
                            SettingItem.Switch(
                                key = "deco_preview_not_zuxos",
                                title = stringResource(R.string.home_card_preview_not_zuxos),
                                checked = notZuxOs,
                                // Re-arm the dismissible warning whenever the device
                                // state is toggled, so the card can be inspected again.
                                onCheckedChange = {
                                    onStateChange(
                                        state.copy(
                                            isZuxOsDevice = !it,
                                            isNonZuxOsWarningDismissed = false
                                        )
                                    )
                                }
                            ),
                            SettingItem.Switch(
                                key = "deco_preview_module_active",
                                title = stringResource(R.string.home_card_preview_module_active),
                                checked = moduleActive,
                                onCheckedChange = { onStateChange(state.copy(isModuleActive = it)) }
                            ),
                            SettingItem.Switch(
                                key = "deco_preview_root_available",
                                title = stringResource(R.string.home_card_preview_root_available),
                                checked = rootAvailable,
                                onCheckedChange = { onStateChange(state.copy(isRootAvailable = it)) }
                            ),
                            SettingItem.Switch(
                                key = "deco_preview_update_available",
                                title = stringResource(R.string.home_card_preview_update_available),
                                checked = updateAvailable,
                                onCheckedChange = {
                                    onStateChange(
                                        state.copy(
                                            updateInfo = if (it) previewUpdateLong() else null
                                        )
                                    )
                                }
                            )
                        )
                    )
                )
            )
        } else {
            val activeLabels = buildList {
                if (notZuxOs) add(stringResource(R.string.home_card_preview_not_zuxos))
                if (moduleActive) add(stringResource(R.string.home_card_preview_module_active))
                if (rootAvailable) add(stringResource(R.string.home_card_preview_root_available))
                if (updateAvailable) add(stringResource(R.string.home_card_preview_update_available))
            }
            Text(
                text = activeLabels.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
        }
    }
}

/**
 * The one-line-changelog case of [UpdateCard], rendered outside the stack: the home
 * page has exactly one update card, so this variant is deliberately not part of the
 * mirrored layout. It only exists to check the card's minimum height.
 */
@Composable
private fun OneLineChangelogVariant(
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit
) {
    var ignored by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        PreviewStripHeader(
            title = stringResource(R.string.home_card_preview_variant_title),
            actionLabel = stringResource(
                if (visible) R.string.home_card_preview_hide
                else R.string.home_card_preview_show
            ),
            onAction = { onVisibleChange(!visible) }
        )

        if (visible) {
            Text(
                text = stringResource(R.string.home_card_preview_variant_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        // Same insets as the mirrored stack, minus the scroll: this
                        // block is short enough to size itself.
                        .then(
                            if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
                                Modifier
                            } else {
                                Modifier.widthIn(max = 1120.dp)
                            }
                        )
                        .padding(horizontal = 32.dp)
                ) {
                    if (ignored) {
                        PreviewResetRow(onReset = { ignored = false })
                    } else {
                        UpdateCard(
                            update = previewUpdateShort(),
                            onToggleExpanded = { /* A single changelog line cannot expand. */ },
                            onIgnore = { ignored = true },
                            onOpenUpdate = { /* Dev preview: never open an external browser. */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewStripHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = LocalZToolColorScheme.current.onSurface,
            modifier = Modifier.weight(1f)
        )
        ZToolTextButton(onClick = onAction, text = actionLabel, isPrimary = false)
    }
}

/** Placeholder that takes the card's place after "ignore" was pressed in the preview. */
@Composable
private fun PreviewResetRow(onReset: () -> Unit) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_card_preview_ignored_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            ZToolTextButton(
                onClick = onReset,
                text = stringResource(R.string.home_card_preview_restore),
                isPrimary = false
            )
        }
    }
}

/*
 * Sample data below mimics an environment in which every home card is visible. The
 * changelog is raw text from the release feed, so it stays a code literal instead of
 * a translated resource.
 */

private fun previewHomeState() = HomeUiState(
    isCheckingEnvironment = false,
    isModuleActive = true,
    isRootAvailable = true,
    // Not a ZUX OS device by default: this is the state that cannot be reproduced on
    // the target hardware, and it gates the warning card.
    isZuxOsDevice = false,
    isNonZuxOsWarningDismissed = false,
    moduleVersion = "Beta/260131 (c1907)",
    rootSource = "KernelSU 1.0.7 (v3)",
    frameworkVersion = "libxposed 102",
    deviceModel = "Lenovo TB321FU",
    androidVersion = "16 (API 36)",
    buildVersion = "ZUXOS 3.1.0_16.0.1.100",
    kernelVersion = "6.6.30-android16-9-g1a2b3c4",
    currentSlot = "_a",
    romRegion = "CN",
    apiVersion = 102,
    isCheckingAppUpdate = false,
    updateCheckCompleted = true,
    updateCheckError = null,
    updateInfo = previewUpdateLong()
)

private fun previewUpdateLong() = UpdateInfo(
    versionName = "Beta/260131",
    versionCode = 1234,
    changelog = """
        新增：控制中心磁贴圆角自定义
        新增：状态栏网速双行显示
        新增：设置页支持全局搜索并高亮命中项
        修复：深色模式下部分卡片文字对比度过低
        修复：超广角镜头在部分机型上切换失败
        优化：首页环境检测改为并行执行，冷启动更快
        优化：日志导出不再阻塞主线程
        变更：配置备份格式升级，旧备份仍可恢复
    """.trimIndent(),
    downloadUrl = "https://github.com/qwqawa64/ZUX-ZTool/releases",
    expanded = false
)

private fun previewUpdateShort() = UpdateInfo(
    versionName = "Beta/260131",
    versionCode = 1234,
    changelog = "修复：设置页在深色模式下部分文字对比度过低",
    downloadUrl = "https://github.com/qwqawa64/ZUX-ZTool/releases",
    expanded = false
)
