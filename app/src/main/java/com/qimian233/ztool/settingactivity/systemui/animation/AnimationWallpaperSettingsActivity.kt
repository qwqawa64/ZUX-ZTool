package com.qimian233.ztool.settingactivity.systemui.animation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemui.AnimationWallpaperSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.data.systemui.AnimationWallpaperSettingsUiState
import com.qimian233.ztool.viewmodel.AnimationWallpaperSettingsViewModel

@Composable
fun AnimationWallpaperSettingsRoute(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("AnimationWallpaperSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            AnimationWallpaperSettingsViewModelFactory(
                AnimationWallpaperSettingsRepository(context.applicationContext)
            )
        )[AnimationWallpaperSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    val customChargeVideoSavedText = stringResource(R.string.custom_charge_animation_video_saved)
    val customChargeVideoSaveFailedText = stringResource(R.string.custom_charge_animation_video_save_failed)

    val portraitVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveCustomChargeVideo(
                uri, "charging_animation_portrait.mp4"
            )
            Toast.makeText(
                context,
                if (saved) customChargeVideoSavedText else customChargeVideoSaveFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val landVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveCustomChargeVideo(
                uri, "charging_animation_land.mp4"
            )
            Toast.makeText(
                context,
                if (saved) customChargeVideoSavedText else customChargeVideoSaveFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val wpPortraitLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveWallpaperVideo(
                uri, "wallpaper_portrait.mp4"
            )
            Toast.makeText(
                context,
                if (saved) "壁纸竖屏视频已保存" else "壁纸竖屏视频保存失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val wpLandLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveWallpaperVideo(
                uri, "wallpaper_land.mp4"
            )
            Toast.makeText(
                context,
                if (saved) "壁纸横屏视频已保存" else "壁纸横屏视频保存失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AnimationWallpaperSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onNoChargeAnimationChanged = viewModel::setNoChargeAnimation,
        onChargeAnimationFixChanged = viewModel::setChargeAnimationFix,
        onCustomChargeAnimationChanged = viewModel::setCustomChargeAnimation,
        onDesktopLiveWallpaperChanged = viewModel::setDesktopLiveWallpaper,
        onRestartScope = viewModel::showRestartDialog,
        onSelectPortraitVideo = { portraitVideoLauncher.launch(arrayOf("video/*")) },
        onSelectLandVideo = { landVideoLauncher.launch(arrayOf("video/*")) },
        onSelectWpPortraitVideo = { wpPortraitLauncher.launch(arrayOf("video/*")) },
        onSelectWpLandVideo = { wpLandLauncher.launch(arrayOf("video/*")) }
    )

    if (uiState.showRestartDialog) {
        val restartFailString = stringResource(R.string.restartFail)
        RestartScopeDialog(
            onConfirm = {
                viewModel.forceStopScope { success, error ->
                    if (success) {
                        Toast.makeText(context, R.string.restartSuccess, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, restartFailString + error, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }
}

private class AnimationWallpaperSettingsViewModelFactory(
    private val repository: AnimationWallpaperSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnimationWallpaperSettingsViewModel::class.java)) {
            return AnimationWallpaperSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun AnimationWallpaperSettingsScreen(
    title: String,
    state: AnimationWallpaperSettingsUiState,
    onBack: () -> Unit,
    onNoChargeAnimationChanged: (Boolean) -> Unit,
    onChargeAnimationFixChanged: (Boolean) -> Unit,
    onCustomChargeAnimationChanged: (Boolean) -> Unit,
    onDesktopLiveWallpaperChanged: (Boolean) -> Unit,
    onRestartScope: () -> Unit,
    onSelectPortraitVideo: () -> Unit,
    onSelectLandVideo: () -> Unit,
    onSelectWpPortraitVideo: () -> Unit,
    onSelectWpLandVideo: () -> Unit,
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ZToolExtendedFloatingActionButton(
                onClick = onRestartScope,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.restart_yes)) }
            )
        }
    ) { innerPadding ->
        Box(
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
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = animationWallpaperSettingsSections(
                        state = state,
                        onNoChargeAnimationChanged = onNoChargeAnimationChanged,
                        onChargeAnimationFixChanged = onChargeAnimationFixChanged,
                        onCustomChargeAnimationChanged = onCustomChargeAnimationChanged,
                        onDesktopLiveWallpaperChanged = onDesktopLiveWallpaperChanged,
                        onSelectPortraitVideo = onSelectPortraitVideo,
                        onSelectLandVideo = onSelectLandVideo,
                        onSelectWpPortraitVideo = onSelectWpPortraitVideo,
                        onSelectWpLandVideo = onSelectWpLandVideo,
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun animationWallpaperSettingsSections(
    state: AnimationWallpaperSettingsUiState,
    onNoChargeAnimationChanged: (Boolean) -> Unit,
    onChargeAnimationFixChanged: (Boolean) -> Unit,
    onCustomChargeAnimationChanged: (Boolean) -> Unit,
    onDesktopLiveWallpaperChanged: (Boolean) -> Unit,
    onSelectPortraitVideo: () -> Unit,
    onSelectLandVideo: () -> Unit,
    onSelectWpPortraitVideo: () -> Unit,
    onSelectWpLandVideo: () -> Unit,
): List<SettingSection> {
    val chargeAnimItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.noChargingAnimation_enable_title),
                summary = stringResource(R.string.noChargingAnimation_enable_summary),
                checked = state.noChargeAnimation,
                onCheckedChange = onNoChargeAnimationChanged
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.Charge_Animation_Fix),
                summary = stringResource(R.string.Charge_Animation_Fix_Summary),
                checked = state.chargeAnimationFix,
                onCheckedChange = onChargeAnimationFixChanged
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.custom_charge_animation_title),
                summary = stringResource(R.string.custom_charge_animation_summary),
                checked = state.customChargeAnimation,
                onCheckedChange = onCustomChargeAnimationChanged
            )
        )
        if (state.customChargeAnimation) {
            add(
                SettingItem.Action(
                    title = stringResource(R.string.custom_charge_animation_portrait_action_title),
                    onClick = onSelectPortraitVideo,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
            add(
                SettingItem.Action(
                    title = stringResource(R.string.custom_charge_animation_land_action_title),
                    onClick = onSelectLandVideo,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        }
    }

    val desktopWallpaperItems = buildList {
        add(
            SettingItem.Switch(
                title = "桌面动态壁纸",
                summary = "使用自定义视频替换桌面动态壁纸。视频文件将保存至 /sdcard/Download/ZTool/ 目录，重启 SystemUI 后生效。",
                checked = state.desktopLiveWallpaper,
                onCheckedChange = onDesktopLiveWallpaperChanged
            )
        )
        if (state.desktopLiveWallpaper) {
            add(
                SettingItem.Action(
                    title = "选择竖屏壁纸视频",
                    onClick = onSelectWpPortraitVideo,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
            add(
                SettingItem.Action(
                    title = "选择横屏壁纸视频",
                    onClick = onSelectWpLandVideo,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        }
    }

    return listOf(
        SettingSection(
            title = stringResource(R.string.noChargingAnimation_title),
            items = chargeAnimItems
        ),
        SettingSection(
            title = "桌面动态壁纸",
            items = desktopWallpaperItems
        )
    )
}

@Composable
private fun RestartScopeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.restart_xp_message_header) +
                    "com.android.systemui，com.zui.wallpapersetting" +
                    stringResource(R.string.restart_xp_message)
            )
        },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.restart_yes))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.restart_no), isPrimary = false)
        }
    )
}
