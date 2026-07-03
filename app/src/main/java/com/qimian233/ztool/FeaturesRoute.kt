package com.qimian233.ztool

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.ImageView
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
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTopAppBar

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
    val (visibleItems, warningMessageRes) = remember(allItems, installedPackages) {
        val visible = allItems.filter { item ->
            item.alwaysVisible || item.packageName in installedPackages
        }
        if (installedPackages.isEmpty()) {
            allItems to R.string.features_app_list_permission_warning
        } else if (visible.isEmpty()) {
            allItems to R.string.features_all_filtered_warning
        } else {
            visible to null
        }
    }
    FeaturesRoute(
        items = visibleItems,
        warningMessageRes = warningMessageRes,
        onFeatureClick = { item ->
            onFeatureDestinationSelected(item.destination)
        }
    )
}

private data class FeatureItem(
    val nameRes: Int,
    val descriptionRes: Int,
    val packageName: String,
    val icon: Drawable?,
    val destination: FeatureDestination,
    val alwaysVisible: Boolean = false
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
                packageName = "com.android.settings",
                destination = FeatureDestination.SettingsDetail
            ),
            featureItem(
                context = context,
                nameRes = R.string.game_tool_app_name,
                descriptionRes = R.string.game_tool_app_description,
                packageName = "com.zui.game.service",
                destination = FeatureDestination.GameTool
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_update_app_name,
                descriptionRes = R.string.system_update_app_description,
                packageName = "com.lenovo.ota",
                destination = FeatureDestination.Ota
            ),
            featureItem(
                context = context,
                nameRes = R.string.package_installer_app_name,
                descriptionRes = R.string.package_installer_app_description,
                packageName = "com.android.packageinstaller",
                destination = FeatureDestination.PackageInstaller
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_ui_app_name,
                descriptionRes = R.string.system_ui_app_description,
                packageName = "com.android.systemui",
                destination = FeatureDestination.SystemUi
            ),
            featureItem(
                context = context,
                nameRes = R.string.launcher_app_name,
                descriptionRes = R.string.launcher_app_description,
                packageName = "com.zui.launcher",
                destination = FeatureDestination.Launcher
            ),
            featureItem(
                context = context,
                nameRes = R.string.mobile_desktop_app_name,
                descriptionRes = R.string.mobile_desktop_app_description,
                packageName = "com.motorola.mobiledesktop",
                destination = FeatureDestination.MobileDesktop
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_framework_app_name,
                descriptionRes = R.string.system_framework_app_description,
                packageName = "android",
                destination = FeatureDestination.Framework,
                alwaysVisible = true
            ),
            featureItem(
                context = context,
                nameRes = R.string.safe_center_app_name,
                descriptionRes = R.string.safe_center_app_description,
                packageName = "com.zui.safecenter",
                destination = FeatureDestination.SafeCenter
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
    alwaysVisible: Boolean = false
): FeatureItem {
    return FeatureItem(
        nameRes = nameRes,
        descriptionRes = descriptionRes,
        packageName = packageName,
        icon = getApplicationIcon(context, packageName),
        destination = destination,
        alwaysVisible = alwaysVisible
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
                Text(
                    text = stringResource(item.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZToolColorScheme.current.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
