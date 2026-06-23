package com.qimian233.ztool

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.qimian233.ztool.ui.components.ExpressiveSectionItems
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsAboutRoute(
    onBack: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenUnfuckZUI: () -> Unit,
    onOpenZuxOsPlus: () -> Unit,
    onOpenQimian233: () -> Unit,
    onOpenWasdDestroy: () -> Unit
) {
    val context = LocalContext.current
    val unknownString = stringResource(R.string.unknown)
    val versionName = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.orEmpty()
        }.getOrDefault(unknownString)
    }
    val commitCount = BuildConfig.GIT_COMMIT_COUNT
    val commitHash = BuildConfig.GIT_COMMIT_HASH
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.about_ztool_title),
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                AboutHeaderCard(versionName, commitCount, commitHash)
                Spacer(modifier = Modifier.height(16.dp))
                AboutSectionCard(stringResource(R.string.about_developers_title), 2) { itemModifier ->
                    AboutActionRow(
                        title = "Qimian233",
                        summary = stringResource(R.string.about_qimian233_summary),
                        onClick = onOpenQimian233,
                        modifier = itemModifier()
                    )
                    AboutActionRow(
                        title = "WASDDestroy",
                        summary = stringResource(R.string.about_wasd_destroy_summary),
                        onClick = onOpenWasdDestroy,
                        modifier = itemModifier()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                AboutSectionCard(stringResource(R.string.about_acknowledgements_title), 2) { itemModifier ->
                    AboutActionRow(
                        title = "UnfuckZUI",
                        summary = stringResource(R.string.about_unfuckzui_summary),
                        onClick = onOpenUnfuckZUI,
                        modifier = itemModifier()
                    )
                    AboutActionRow(
                        title = "ZUXOS+",
                        summary = stringResource(R.string.about_zuxos_plus_summary),
                        onClick = onOpenZuxOsPlus,
                        modifier = itemModifier(),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                AboutSectionCard(stringResource(R.string.about_open_source_title), 2) { itemModifier ->
                    AboutActionRow(
                        title = stringResource(R.string.about_view_source_title),
                        summary = null,
                        onClick = onOpenGithub,
                        modifier = itemModifier()
                    )
                    AboutActionRow(
                        title = stringResource(R.string.about_license_title),
                        summary = stringResource(R.string.about_license_summary),
                        onClick = {},
                        modifier = itemModifier(),
                        showTrailingArrow = false
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

internal const val SettingsAboutRouteName = "SettingsAbout"

@Composable
private fun AboutHeaderCard(
    versionName: String,
    @Suppress("SameParameterValue") commitCount: Int,
    @Suppress("SameParameterValue") commitHash: String
) {
    AboutSectionContainer {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.splash_logo),
                contentDescription = stringResource(R.string.splash_logo_description),
                modifier = Modifier.height(88.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$versionName - $commitCount - $commitHash",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.splash_slogan),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutSectionContainer(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isExpressive = LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isExpressive && title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
            )
        }
        ZToolCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isExpressive) 12.dp else 0.dp)
                    .padding(vertical = if (isExpressive) 0.dp else 12.dp)
            ) {
                if (isExpressive && title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    @Suppress("SameParameterValue") itemCount: Int,
    content: @Composable ColumnScope.(itemModifier: () -> Modifier) -> Unit
) {
    val isExpressive = LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isExpressive) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
            )
        }
        ZToolCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isExpressive) 12.dp else 0.dp)
                    .padding(vertical = if (isExpressive) 0.dp else 12.dp)
            ) {
                if (isExpressive) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                ExpressiveSectionItems(count = itemCount) { itemModifier ->
                    content(itemModifier)
                }
            }
        }
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    summary: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showTrailingArrow: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix)
                    BasicComponentDefaults.titleColor().color
                else
                    MaterialTheme.colorScheme.onSurface,
                fontSize = if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) MiuixTheme.textStyles.headline1.fontSize else TextUnit.Unspecified,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix)
                        BasicComponentDefaults.summaryColor().color
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) MiuixTheme.textStyles.body2.fontSize else TextUnit.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showTrailingArrow) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun openExternalLink(
    context: android.content.Context,
    link: String,
    shouldDeterminePackage: Boolean = false,
    packageName: String = ""
) {
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, link.toUri()).apply {
                if (shouldDeterminePackage) setPackage(packageName)
            }
        )
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.open_web_link_failed), Toast.LENGTH_SHORT).show()
    }
}
