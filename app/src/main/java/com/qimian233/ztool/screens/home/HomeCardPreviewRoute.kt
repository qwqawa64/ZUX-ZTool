package com.qimian233.ztool.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.UpdateInfo

/**
 * Developer-only gallery for the two home cards that cannot be reproduced on demand:
 * the unofficial-system warning (`NonZuxOsCard`, hidden on every ZUX OS device and
 * dismissible) and the app-update notice (`UpdateCard`, needs a real release newer
 * than the installed build). Both are rendered here straight from `NonZuxOsCard` /
 * `UpdateCard` with sample data, so theme, style and text-scale variations can be
 * compared side by side without touching the real home-screen state.
 *
 * Reachable from Settings → Advanced; that row is compiled in only for
 * `BuildConfig.IS_DEV_BUILD` builds. Prefer this over temporarily patching
 * `HomeRoute` when checking how the cards look.
 */
@Composable
fun HomeCardPreviewRoute(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

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
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_card_preview_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZToolColorScheme.current.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                NonZuxOsPreviewBlock()

                UpdatePreviewBlock(
                    title = stringResource(R.string.home_card_preview_update_expanded_title),
                    hint = stringResource(R.string.home_card_preview_update_expanded_hint),
                    sample = PreviewUpdateLongExpanded
                )
                UpdatePreviewBlock(
                    title = stringResource(R.string.home_card_preview_update_collapsed_title),
                    hint = stringResource(R.string.home_card_preview_update_collapsed_hint),
                    sample = PreviewUpdateLongCollapsed
                )
                UpdatePreviewBlock(
                    title = stringResource(R.string.home_card_preview_update_short_title),
                    hint = stringResource(R.string.home_card_preview_update_short_hint),
                    sample = PreviewUpdateShort
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun NonZuxOsPreviewBlock() {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val hint = if (dismissed) {
        stringResource(R.string.home_card_preview_dismissed_hint)
    } else {
        stringResource(R.string.home_card_preview_non_zuxos_hint)
    }

    PreviewBlock(
        title = stringResource(R.string.home_card_preview_non_zuxos_title),
        hint = hint
    ) {
        if (dismissed) {
            PreviewResetRow(onReset = { dismissed = false })
        } else {
            NonZuxOsCard(onDismiss = { dismissed = true })
        }
    }
}

/**
 * Renders [UpdateCard] with [sample]; the card stays fully interactive (tap to
 * expand/collapse, "ignore" hides it) so the buttons can be inspected in both
 * states. "Update" deliberately does nothing — the preview must never leave the app.
 */
@Composable
private fun UpdatePreviewBlock(
    title: String,
    hint: String,
    sample: UpdateInfo
) {
    var current by remember(sample) { mutableStateOf(sample) }
    var ignored by remember(sample) { mutableStateOf(false) }

    PreviewBlock(
        title = title,
        hint = if (ignored) stringResource(R.string.home_card_preview_dismissed_hint) else hint
    ) {
        if (ignored) {
            PreviewResetRow(
                onReset = {
                    ignored = false
                    current = sample
                }
            )
        } else {
            UpdateCard(
                update = current,
                onToggleExpanded = { current = current.copy(expanded = !current.expanded) },
                onIgnore = { ignored = true },
                onOpenUpdate = { /* Dev preview: never open an external browser. */ }
            )
        }
    }
}

@Composable
private fun PreviewBlock(
    title: String,
    hint: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = LocalZToolColorScheme.current.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = LocalZToolColorScheme.current.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    content()
    Spacer(modifier = Modifier.height(32.dp))
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
 * Sample data below mimics what HomeRepository.checkAppUpdate() would return: the
 * changelog is raw text from the release feed, so it stays a code literal instead
 * of a translated resource.
 */

private val PreviewUpdateLongExpanded = UpdateInfo(
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
    expanded = true
)

private val PreviewUpdateLongCollapsed = PreviewUpdateLongExpanded.copy(expanded = false)

private val PreviewUpdateShort = UpdateInfo(
    versionName = "Beta/260131",
    versionCode = 1234,
    changelog = "修复：设置页在深色模式下部分文字对比度过低",
    downloadUrl = "https://github.com/qwqawa64/ZUX-ZTool/releases",
    expanded = false
)
