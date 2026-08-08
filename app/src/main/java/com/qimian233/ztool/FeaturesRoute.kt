package com.qimian233.ztool

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.utils.ScopeUtils
import io.github.libxposed.service.XposedService

enum class FeatureDestination(
    val route: String
) {
    SettingsDetail("feature/settings-detail"),
    GameTool("feature/game-tool"),
    Ota("feature/ota"),
    PackageInstaller("feature/package-installer"),
    SystemUi("feature/system-ui"),
    Launcher("feature/launcher"),
    MobileDesktop("feature/mobile-desktop"),
    Framework("feature/framework"),
    SafeCenter("feature/safe-center")
}

@Composable
fun FeaturesMainRoute(
    onFeatureDestinationSelected: (FeatureDestination) -> Unit = {}
) {
    val context = LocalContext.current
    val allItems = rememberFeatureItems(context)
    val installedPackages = rememberInstalledPackages(context)
    var scopeSet by remember { mutableStateOf(XposedServiceBridge.getScope().toSet()) }
    val scopeRequestFailReason = stringResource(R.string.scope_request_fail_message)
    // "system" is the LSPosed system-server scope entry — not a real installed package
    val systemScopePackages = setOf(ScopeKeys.SYSTEM_SERVER.packageName)
    var scopeRequestItem by remember { mutableStateOf<FeatureItem?>(null) }

    val (visibleItems, warningMessageRes) = remember(allItems, installedPackages, scopeSet) {
        val scopedItems = allItems.map { item ->
            item.copy(inScope = item.scopePackages
                .filter { it in installedPackages || it in systemScopePackages }
                .all { it in scopeSet })
        }
        val visible = scopedItems.filter { item ->
            item.alwaysVisible || item.packageName in installedPackages
        }
        if (installedPackages.isEmpty()) {
            scopedItems to R.string.features_app_list_permission_warning
        } else if (visible.isEmpty()) {
            scopedItems to R.string.features_all_filtered_warning
        } else {
            visible to null
        }
    }

    // 作用域申请对话框
    scopeRequestItem?.let { item ->
        ZToolDialog(
            onDismissRequest = { scopeRequestItem = null },
            title = { Text(stringResource(item.nameRes)) },
            text = { Text(stringResource(R.string.scope_request_dialog_message)) },
            confirmButton = {
                ZToolTextButton(
                    onClick = {
                        XposedServiceBridge.requestScope(
                            item.scopePackages.filter { it in installedPackages || it in systemScopePackages },
                            object : XposedService.OnScopeEventListener {

                                override fun onScopeRequestApproved(packages: List<String>) {
                                    scopeSet = scopeSet + packages
                                }

                                override fun onScopeRequestFailed(reason: String) {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(
                                            context,
                                            String.format(scopeRequestFailReason, reason),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                        scopeRequestItem = null
                    },
                    text = stringResource(R.string.confirm)
                )
            },
            dismissButton = {
                ZToolTextButton(
                    onClick = { scopeRequestItem = null },
                    text = stringResource(R.string.cancel),
                    isPrimary = false
                )
            }
        )
    }

    FeaturesRoute(
        items = visibleItems,
        warningMessageRes = warningMessageRes,
        onFeatureClick = { item ->
            if (item.inScope) {
                onFeatureDestinationSelected(item.destination)
            } else {
                scopeRequestItem = item
            }
        }
    )
}

private data class FeatureItem(
    val nameRes: Int,
    val descriptionRes: Int,
    val packageName: String,
    val icon: Drawable?,
    val destination: FeatureDestination,
    val alwaysVisible: Boolean = false,
    val inScope: Boolean = true,
    val scopePackages: List<String> = listOf(packageName)
)

private val FeatureCardHeight: Dp = 112.dp

@Composable
private fun rememberFeatureItems(context: Context): List<FeatureItem> {
    return remember(context) {
        listOf(
            featureItem(
                context = context,
                nameRes = R.string.settings_app_name,
                descriptionRes = R.string.settings_app_description,
                packageName = ScopeKeys.SETTINGS.packageName,
                destination = FeatureDestination.SettingsDetail,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.SettingsDetail)
            ),
            featureItem(
                context = context,
                nameRes = R.string.game_tool_app_name,
                descriptionRes = R.string.game_tool_app_description,
                packageName = ScopeKeys.GAME_SERVICE.packageName,
                destination = FeatureDestination.GameTool,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.GameTool)
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_update_app_name,
                descriptionRes = R.string.system_update_app_description,
                packageName = ScopeKeys.OTA.packageName,
                destination = FeatureDestination.Ota,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.Ota)
            ),
            featureItem(
                context = context,
                nameRes = R.string.package_installer_app_name,
                descriptionRes = R.string.package_installer_app_description,
                packageName = ScopeKeys.PACKAGE_INSTALLER.packageName,
                destination = FeatureDestination.PackageInstaller,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.PackageInstaller)
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_ui_app_name,
                descriptionRes = R.string.system_ui_app_description,
                packageName = ScopeKeys.SYSTEM_UI.packageName,
                destination = FeatureDestination.SystemUi,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.SystemUi)
            ),
            featureItem(
                context = context,
                nameRes = R.string.launcher_app_name,
                descriptionRes = R.string.launcher_app_description,
                packageName = ScopeKeys.LAUNCHER.packageName,
                destination = FeatureDestination.Launcher,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.Launcher)
            ),
            featureItem(
                context = context,
                nameRes = R.string.mobile_desktop_app_name,
                descriptionRes = R.string.mobile_desktop_app_description,
                packageName = ScopeKeys.MOBILE_DESKTOP.packageName,
                destination = FeatureDestination.MobileDesktop,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.MobileDesktop)
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_framework_app_name,
                descriptionRes = R.string.system_framework_app_description,
                packageName = ScopeKeys.ANDROID_SYSTEM.packageName,
                destination = FeatureDestination.Framework,
                alwaysVisible = true,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.Framework)
            ),
            featureItem(
                context = context,
                nameRes = R.string.safe_center_app_name,
                descriptionRes = R.string.safe_center_app_description,
                packageName = ScopeKeys.ZUI_SAFE_CENTER.packageName,
                destination = FeatureDestination.SafeCenter,
                scopePackages = ScopeUtils.getScopePackages(FeatureDestination.SafeCenter)
            )
        )
    }
}

private fun featureItem(
    context: Context,
    nameRes: Int,
    descriptionRes: Int,
    packageName: String,
    destination: FeatureDestination,
    alwaysVisible: Boolean = false,
    scopePackages: List<String> = listOf(packageName)
): FeatureItem {
    return FeatureItem(
        nameRes = nameRes,
        descriptionRes = descriptionRes,
        packageName = packageName,
        icon = getApplicationIcon(context, packageName),
        destination = destination,
        alwaysVisible = alwaysVisible,
        scopePackages = scopePackages
    )
}

private fun getApplicationIcon(context: Context, packageName: String): Drawable? {
    if (packageName.isBlank()) return null
    return try {
        val packageManager = context.packageManager
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        info.applicationInfo?.loadIcon(packageManager)
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun rememberInstalledPackages(context: Context): Set<String> {
    return remember(context) {
        try {
            context.packageManager.getInstalledApplications(0)
                .mapTo(mutableSetOf()) { it.packageName }
                .minus(context.packageName)
        } catch (_: Throwable) {
            emptySet()
        }
    }
}

@Composable
private fun FeaturesRoute(
    items: List<FeatureItem>,
    warningMessageRes: Int?,
    onFeatureClick: (FeatureItem) -> Unit
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.featuresFragment_title),
                addNavIcon = false
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
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 1280.dp)
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                if (warningMessageRes != null) {
                    FeatureWarningCard(message = stringResource(warningMessageRes))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (items.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(items = items, key = { it.packageName }) { item ->
                            FeatureCard(
                                item = item,
                                onClick = { onFeatureClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureWarningCard(message: String) {
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LocalZToolColorScheme.current.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = LocalZToolColorScheme.current.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onErrorContainer
            )
        }
    }
}

@Composable
private fun FeatureCard(
    item: FeatureItem,
    onClick: () -> Unit
) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(FeatureCardHeight)
            .clickable(onClick = onClick),
        containerColor = LocalZToolColorScheme.current.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                icon = item.icon,
                contentDescription = stringResource(item.nameRes)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalZToolColorScheme.current.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (item.inScope) {
                    Text(
                        text = stringResource(item.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stringResource(R.string.not_in_scope_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = LocalZToolColorScheme.current.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppIcon(
    icon: Drawable?,
    contentDescription: String
) {
    AndroidView(
        modifier = Modifier.size(48.dp),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                this.contentDescription = contentDescription
                setImageResource(R.drawable.ic_launcher_foreground)
            }
        },
        update = { imageView ->
            imageView.contentDescription = contentDescription
            if (icon != null) {
                imageView.setImageDrawable(icon)
            } else {
                imageView.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    )
}
