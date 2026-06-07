package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class QuickHelpItem(
    val label: String,
    val description: String
)

data class QuickHelpExample(
    val value: String,
    val description: String
)

@Composable
fun ZToolQuickHelpDialog(
    title: String,
    summary: String,
    quickLabel: String,
    examplesLabel: String,
    items: List<QuickHelpItem>,
    examples: List<QuickHelpExample>,
    note: String? = null,
    onDismiss: () -> Unit,
    onCopyExample: (() -> Unit)? = null,
    copyButtonText: String? = null,
    confirmButtonText: String
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = quickLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    items.forEach { item ->
                        QuickHelpRow(
                            label = item.label,
                            description = item.description
                        )
                    }
                }

                if (examples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = examplesLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    examples.forEach { example ->
                        QuickHelpRow(
                            label = example.value,
                            description = example.description
                        )
                    }
                }

                if (!note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            if (onCopyExample != null && copyButtonText != null) {
                TextButton(onClick = onCopyExample) {
                    Text(copyButtonText)
                }
            }
        }
    )
}

@Composable
private fun QuickHelpRow(
    label: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(104.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
