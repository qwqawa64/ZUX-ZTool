package com.qimian233.ztool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.qimian233.ztool.settingactivity.gametool.GameToolSettngs
import com.qimian233.ztool.settingactivity.launcher.LauncherSettingsActivity
import com.qimian233.ztool.settingactivity.ota.OtaSettings
import com.qimian233.ztool.settingactivity.packageinstaller.packageinstallersettings
import com.qimian233.ztool.settingactivity.safecenter.SafeCenterSettingsActivity
import com.qimian233.ztool.settingactivity.setting.SettingsDetailActivity
import com.qimian233.ztool.settingactivity.systemframework.FrameworkSettingsActivity
import com.qimian233.ztool.settingactivity.systemui.systemUISettings
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.theme.ZToolTheme

class FeaturesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ZToolTheme {
                    FeaturesRoute(
                        items = rememberFeatureItems(requireContext()),
                        onFeatureClick = ::openFeatureSettings
                    )
                }
            }
        }
    }

    private fun openFeatureSettings(item: FeatureItem) {
        val context = requireContext()
        context.startActivity(
            Intent(context, item.targetActivity).apply {
                putExtra("app_name", item.name)
                putExtra("app_package", item.packageName)
            }
        )
    }
}

@Composable
fun FeaturesMainRoute() {
    val context = LocalContext.current
    FeaturesRoute(
        items = rememberFeatureItems(context),
        onFeatureClick = { item ->
            context.startActivity(
                Intent(context, item.targetActivity).apply {
                    putExtra("app_name", item.name)
                    putExtra("app_package", item.packageName)
                }
            )
        }
    )
}

private data class FeatureItem(
    val name: String,
    val description: String,
    val packageName: String,
    val icon: Drawable?,
    val targetActivity: Class<*>
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
                targetActivity = SettingsDetailActivity::class.java
            ),
            featureItem(
                context = context,
                nameRes = R.string.game_tool_app_name,
                descriptionRes = R.string.game_tool_app_description,
                packageName = "com.zui.game.service",
                targetActivity = GameToolSettngs::class.java
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_update_app_name,
                descriptionRes = R.string.system_update_app_description,
                packageName = "com.lenovo.ota",
                targetActivity = OtaSettings::class.java
            ),
            featureItem(
                context = context,
                nameRes = R.string.package_installer_app_name,
                descriptionRes = R.string.package_installer_app_description,
                packageName = "com.android.packageinstaller",
                targetActivity = packageinstallersettings::class.java
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_ui_app_name,
                descriptionRes = R.string.system_ui_app_description,
                packageName = "com.android.systemui",
                targetActivity = systemUISettings::class.java
            ),
            featureItem(
                context = context,
                nameRes = R.string.launcher_app_name,
                descriptionRes = R.string.launcher_app_description,
                packageName = "com.zui.launcher",
                targetActivity = LauncherSettingsActivity::class.java
            ),
            featureItem(
                context = context,
                nameRes = R.string.system_framework_app_name,
                descriptionRes = R.string.system_framework_app_description,
                packageName = "android",
                targetActivity = FrameworkSettingsActivity::class.java
            ),
            featureItem(
                context = context,
                nameRes = R.string.safe_center_app_name,
                descriptionRes = R.string.safe_center_app_description,
                packageName = "com.zui.safecenter",
                targetActivity = SafeCenterSettingsActivity::class.java
            )
        )
    }
}

private fun featureItem(
    context: Context,
    nameRes: Int,
    descriptionRes: Int,
    packageName: String,
    targetActivity: Class<*>
): FeatureItem {
    return FeatureItem(
        name = context.getString(nameRes),
        description = context.getString(descriptionRes),
        packageName = packageName,
        icon = getApplicationIcon(context, packageName),
        targetActivity = targetActivity
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
private fun FeaturesRoute(
    items: List<FeatureItem>,
    onFeatureClick: (FeatureItem) -> Unit
) {
    ZToolScaffold { innerPadding ->
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
                Text(
                    text = stringResource(R.string.featuresFragment_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))
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

@Composable
private fun FeatureCard(
    item: FeatureItem,
    onClick: () -> Unit
) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(FeatureCardHeight)
            .clickable(onClick = onClick)
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
                contentDescription = item.name
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
