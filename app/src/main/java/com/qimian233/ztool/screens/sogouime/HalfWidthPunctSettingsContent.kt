package com.qimian233.ztool.screens.sogouime

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolOutlinedTextField
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.viewmodel.SogouImeSettingsUiState

@Composable
internal fun HalfWidthPunctSettingsContent(
    state: SogouImeSettingsUiState,
    onHalfWidthPunctChanged: (Boolean) -> Unit,
    onHalfWidthPunctSignsChanged: (String) -> Unit
) {
    ZToolSwitchRow(
        title = stringResource(R.string.system_framework_half_width_punct_title),
        summary = stringResource(R.string.system_framework_half_width_punct_summary),
        checked = state.halfWidthPunct,
        onCheckedChange = onHalfWidthPunctChanged
    )
    if (state.halfWidthPunct) {
        ZToolOutlinedTextField(
            value = state.halfWidthPunctSigns,
            onValueChange = onHalfWidthPunctSignsChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            label = stringResource(R.string.system_framework_half_width_punct_hint),
            supportingText = stringResource(R.string.system_framework_half_width_punct_supporting),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
    }
}
