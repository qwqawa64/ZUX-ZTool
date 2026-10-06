package com.qimian233.ztool.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.HomeUiState
import com.qimian233.ztool.viewmodel.UpdateInfo

/**
 * Developer-only preview of the home screen's card stack.
 *
 * The stack is rendered by the same `HomeCardStack` (and the same `homeCardColumn`
 * container) the home screen uses, from a mock [HomeUiState], so spacing and geometry
 * problems can be diagnosed on any device — including the states that cannot be
 * produced on demand (a non-ZUX OS device, a pending update, a missing root or
 * module). The mock state is edited in a dialog, so no permanent panel steals height
 * from the mirrored column, and the one-line-changelog variant is a separate view
 * rather than a block below the stack.
 *
 * Reachable from Settings → Advanced; that row, this screen and its nav destination
 * exist only in `BuildConfig.IS_DEV_BUILD` builds.
 */
@Composable
fun HomeCardPreviewRoute(onBack: () -> Unit) {
    var mockState by remember { mutableStateOf(previewHomeState()) }
    var selectedView by rememberSaveable { mutableStateOf(PreviewView.Stack) }
    var consoleVisible by rememberSaveable { mutableStateOf(false) }

    if (consoleVisible) {
        MockStateDialog(
            state = mockState,
            onStateChange = { mockState = it },
            onDismiss = { consoleVisible = false }
        )
    }

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
                },
                actions = {
                    IconButton(onClick = { consoleVisible = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.home_card_preview_console_title)
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
            PreviewViewTabs(selected = selectedView, onSelect = { selectedView = it })
            HorizontalDivider()

            // The mirrored column owns its scroll container, so the preview never wraps
            // it in another scroller: each view gets the full page height instead.
            Box(modifier = Modifier.weight(1f)) {
                ZToolPageSurface(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (selectedView) {
                        PreviewView.Stack -> HomeCardStack(
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
                            onRefreshEnvironment = { /* The mock state drives the environment. */ }
                        )

                        PreviewView.OneLineVariant -> OneLineChangelogView()
                    }
                }
            }
        }
    }
}

/** The two renderings of this screen; each gets the whole page height. */
private enum class PreviewView { Stack, OneLineVariant }

@Composable
private fun PreviewViewTabs(
    selected: PreviewView,
    onSelect: (PreviewView) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZToolTextButton(
            text = stringResource(R.string.home_card_preview_view_stack),
            onClick = { onSelect(PreviewView.Stack) },
            isPrimary = selected == PreviewView.Stack
        )
        ZToolTextButton(
            text = stringResource(R.string.home_card_preview_view_variant),
            onClick = { onSelect(PreviewView.OneLineVariant) },
            isPrimary = selected == PreviewView.OneLineVariant
        )
    }
}

/**
 * The one-line-changelog case of [UpdateCard]. It reuses the home column container, so
 * the card is laid out at exactly the width it has in the mirrored stack — the home
 * page only ever has one update card, which is why this variant is a separate view
 * instead of an extra card inside the stack.
 */
@Composable
private fun OneLineChangelogView() {
    var ignored by remember { mutableStateOf(false) }

    Column(modifier = Modifier.homeCardColumn()) {
        Text(
            text = stringResource(R.string.home_card_preview_variant_hint),
            style = MaterialTheme.typography.bodySmall,
            color = LocalZToolColorScheme.current.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

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

/** Placeholder that takes the card's place after "ignore" was pressed in the preview. */
@Composable
private fun PreviewResetRow(onReset: () -> Unit) {
    ZToolCard(modifier = Modifier.fillMaxWidth()) {
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

/**
 * Edits the mocked [HomeUiState]. Deliberately a dialog: an inline panel would either
 * cover the mirrored column or leave it too little height to render the whole stack.
 */
@Composable
private fun MockStateDialog(
    state: HomeUiState,
    onStateChange: (HomeUiState) -> Unit,
    onDismiss: () -> Unit
) {
    val notZuxOs = !state.isZuxOsDevice
    val moduleActive = state.isModuleActive
    val rootAvailable = state.isRootAvailable
    val updateAvailable = state.updateInfo != null

    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_card_preview_console_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.home_card_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZToolColorScheme.current.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ZToolSettingsList(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
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
                                            state.copy(updateInfo = if (it) previewUpdateLong() else null)
                                        )
                                    }
                                )
                            )
                        )
                    )
                )
            }
        },
        confirmButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_confirm)
            )
        }
    )
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
