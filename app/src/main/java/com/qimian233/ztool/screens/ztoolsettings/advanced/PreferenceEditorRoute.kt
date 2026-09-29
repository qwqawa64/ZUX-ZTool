package com.qimian233.ztool.screens.ztoolsettings.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.R
import com.qimian233.ztool.data.preferences.PreferenceEditOutcome
import com.qimian233.ztool.data.preferences.PreferenceEditorFailureReason
import com.qimian233.ztool.data.preferences.PreferenceEditorTarget
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.viewmodel.PreferenceEditorPendingConfirm
import com.qimian233.ztool.viewmodel.PreferenceEditorUiState
import com.qimian233.ztool.viewmodel.PreferenceEditorViewModel

/**
 * SharedPreference Editor screen: read/write/list/delete raw preference values
 * of the module remote config or the theme local config.
 */
@Composable
fun PreferenceEditorRoute(
    onBack: () -> Unit,
    targetId: String? = null
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    val viewModel = remember {
        ViewModelProvider(
            activity,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PreferenceEditorViewModel(activity) as T
                }
            }
        )[PreferenceEditorViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.pendingConfirm != null) {
        PreferenceEditorConfirmDialog(
            pending = uiState.pendingConfirm!!,
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending
        )
    }

    PreferenceEditorScreen(
        state = uiState,
        onBack = onBack,
        onTargetSelected = viewModel::selectTarget,
        onKeyChange = viewModel::updateKeyText,
        onValueChange = viewModel::updateValueText,
        onRead = viewModel::read,
        onWrite = viewModel::write,
        onList = viewModel::listAll,
        onDelete = viewModel::delete
    )
}

@Composable
private fun PreferenceEditorScreen(
    state: PreferenceEditorUiState,
    onBack: () -> Unit,
    onTargetSelected: (PreferenceEditorTarget) -> Unit,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onList: () -> Unit,
    onDelete: () -> Unit
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.preference_editor_title),
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
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = listOf(
                        SettingSection(
                            items = listOfNotNull(
                                SettingItem.Custom(
                                    content = {
                                        // SettingItem.Dropdown doesn't expose the field
                                        // width knobs; render the row directly so the long
                                        // prefs file names fit the value field.
                                        ZToolPopupMenuSettingRow(
                                            title = stringResource(R.string.preference_editor_target_label),
                                            value = state.target.prefsFileName,
                                            options = PreferenceEditorTarget.entries,
                                            optionLabel = { it.prefsFileName },
                                            onOptionSelected = onTargetSelected,
                                            fieldMinWidth = 220.dp,
                                            fieldMaxWidth = 320.dp
                                        )
                                    },
                                    key = "deco_pref_editor_target"
                                ),
                                SettingItem.TextInput(
                                    key = "deco_pref_editor_key",
                                    label = stringResource(R.string.preference_editor_key_label),
                                    value = state.keyText,
                                    onValueChange = onKeyChange
                                ),
                                SettingItem.TextInput(
                                    key = "deco_pref_editor_value",
                                    label = stringResource(R.string.preference_editor_value_label),
                                    value = state.valueText,
                                    onValueChange = onValueChange
                                ),
                                state.outcome?.let { outcome ->
                                    SettingItem.Custom(
                                        content = { PreferenceEditorResultCard(outcome) },
                                        key = "deco_pref_editor_result"
                                    )
                                }
                            )
                        ),
                        SettingSection(
                            items = listOf(
                                SettingItem.Custom(
                                    content = {
                                        PreferenceEditorButtons(
                                            onRead = onRead,
                                            onWrite = onWrite,
                                            onList = onList,
                                            onDelete = onDelete
                                        )
                                    },
                                    key = "deco_pref_editor_buttons"
                                )
                            )
                        )
                    ),
                    bottomPadding = 32.dp
                )
            }
        }
    }
}

/** Read/Write/List/Delete: vertical stack in Miuix mode, horizontal row otherwise. */
@Composable
private fun PreferenceEditorButtons(
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onList: () -> Unit,
    onDelete: () -> Unit
) {
    val isMiuix = LocalZToolThemeSpec.current.style == FrontendStyle.Miuix
    val readText = stringResource(R.string.preference_editor_read)
    val writeText = stringResource(R.string.preference_editor_write)
    val listText = stringResource(R.string.preference_editor_list)
    val deleteText = stringResource(R.string.preference_editor_delete)
    val buttons = listOf(
        readText to onRead,
        writeText to onWrite,
        listText to onList,
        deleteText to onDelete
    )
    if (isMiuix) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            buttons.forEach { (text, action) ->
                ZToolTextButton(
                    text = text,
                    onClick = action,
                    modifier = Modifier.fillMaxWidth(),
                    isPrimary = false
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            buttons.forEach { (text, action) ->
                ZToolTextButton(
                    text = text,
                    onClick = action,
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }
        }
    }
}

@Composable
private fun PreferenceEditorResultCard(outcome: PreferenceEditOutcome) {
    ZToolCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (outcome) {
                is PreferenceEditOutcome.Read -> {
                    ResultTitle(
                        text = "${outcome.key} = ${outcome.value ?: stringResource(R.string.preference_editor_value_null)} (${outcome.typeName})"
                    )
                    ViaRemoteFooter(outcome.viaRemote)
                }
                is PreferenceEditOutcome.Write -> {
                    ResultTitle(
                        text = stringResource(
                            R.string.preference_editor_result_write_success,
                            outcome.key,
                            outcome.writtenTypeName
                        )
                    )
                    ViaRemoteFooter(outcome.viaRemote)
                }
                is PreferenceEditOutcome.Delete -> {
                    ResultTitle(
                        text = stringResource(
                            R.string.preference_editor_result_delete_success,
                            outcome.key
                        )
                    )
                    ViaRemoteFooter(outcome.viaRemote)
                }
                is PreferenceEditOutcome.ListAll -> {
                    ResultTitle(
                        text = stringResource(
                            R.string.preference_editor_result_list_header,
                            outcome.entries.size
                        )
                    )
                    outcome.entries.forEach { (key, value) ->
                        Text(
                            text = "$key = $value (${value.javaClass.simpleName})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is PreferenceEditOutcome.Failure -> {
                    val reasonText = when (outcome.reason) {
                        PreferenceEditorFailureReason.TYPE_ERROR ->
                            stringResource(R.string.preference_editor_result_type_error)
                        PreferenceEditorFailureReason.KEY_NOT_FOUND ->
                            stringResource(R.string.preference_editor_result_key_not_found)
                        PreferenceEditorFailureReason.OTHER ->
                            stringResource(R.string.preference_editor_result_other_error)
                    }
                    ResultTitle(
                        text = stringResource(
                            R.string.preference_editor_result_failure,
                            reasonText,
                            outcome.message ?: ""
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )
}

/** Remote/local-fallback tag; null for local-only targets. */
@Composable
private fun ViaRemoteFooter(viaRemote: Boolean?) {
    if (viaRemote == null) return
    Text(
        text = stringResource(
            if (viaRemote) R.string.preference_editor_via_remote
            else R.string.preference_editor_via_local_fallback
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PreferenceEditorConfirmDialog(
    pending: PreferenceEditorPendingConfirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (titleRes, messageRes) = when (pending) {
        is PreferenceEditorPendingConfirm.UnregisteredWrite ->
            R.string.preference_editor_unregistered_write_title to
                    R.string.preference_editor_unregistered_write_message
        is PreferenceEditorPendingConfirm.DeleteRegisteredKey ->
            R.string.preference_editor_delete_registered_title to
                    R.string.preference_editor_delete_registered_message
    }
    val messageArg = when (pending) {
        is PreferenceEditorPendingConfirm.UnregisteredWrite -> pending.key
        is PreferenceEditorPendingConfirm.DeleteRegisteredKey -> pending.key
    }
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes, messageArg)) },
        confirmButton = {
            ZToolTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.common_confirm)
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_cancel),
                isPrimary = false
            )
        }
    )
}
