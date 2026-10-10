package com.qimian233.ztool.screens.zuiui

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.qimian233.ztool.data.zuiui.ZuiDialogSkinRepository
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.ZuiDialogSkinUiState
import com.qimian233.ztool.viewmodel.ZuiDialogSkinViewModel

@Composable
fun ZuiDialogSkinRoute(
    title: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("ZuiDialogSkinRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            ZuiDialogSkinViewModelFactory(
                ZuiDialogSkinRepository(context.applicationContext)
            )
        )[ZuiDialogSkinViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        ZuiDialogSkinScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onDialogSkinChanged = viewModel::setDialogSkinEnabled,
            onApply = {
                viewModel.applyChanges(
                    onUnsupported = {
                        Toast.makeText(
                            context,
                            R.string.ui_components_reload_unsupported_message,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onResult = { outcome ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.ui_components_reload_result_message,
                                outcome.succeeded,
                                outcome.failed
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            },
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }
}

private class ZuiDialogSkinViewModelFactory(
    private val repository: ZuiDialogSkinRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ZuiDialogSkinViewModel::class.java)) {
            return ZuiDialogSkinViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun ZuiDialogSkinScreen(
    title: String,
    state: ZuiDialogSkinUiState,
    onBack: () -> Unit,
    onDialogSkinChanged: (Boolean) -> Unit,
    onApply: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            ZToolExtendedFloatingActionButton(
                onClick = onApply,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.ui_components_reload_button)) }
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = zuiDialogSkinSections(
                        state = state,
                        onDialogSkinChanged = onDialogSkinChanged
                    ),
                    bottomPadding = 88.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun zuiDialogSkinSections(
    state: ZuiDialogSkinUiState,
    onDialogSkinChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.ui_components_app_name),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.ui_components_dialog_skin_title),
                    summary = stringResource(R.string.ui_components_dialog_skin_summary),
                    checked = state.dialogSkinEnabled,
                    onCheckedChange = onDialogSkinChanged,
                    key = "ui_components_dialog_skin"
                )
            )
        )
    )
}
