package com.qimian233.ztool.ui.firstrun

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.R
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.home.FirstrunAgreementRepository
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolMarkdownText
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.viewmodel.FirstrunAgreementViewModel

@Composable
fun FirstrunAgreementRoute(
    onAgreementAccepted: () -> Unit,
    onAgreementDeclined: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val viewModel = remember {
        ViewModelProvider(
            activity,
            FirstrunAgreementViewModelFactory(
                repository = FirstrunAgreementRepository(context),
                agreementRepository = AgreementRepository(context)
            )
        )[FirstrunAgreementViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val gate = remember { ScrollToBottomAgreementGate() }

    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshChecks() }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshChecks() }

    LaunchedEffect(Unit) {
        viewModel.refreshChecks()
    }

    DisposableEffect(scrollState.value, scrollState.maxValue) {
        gate.onScrollChanged(scrollState.value < scrollState.maxValue)
        onDispose { }
    }

    val allGranted = uiState.checkState.allGranted && gate.satisfied
    val markdownText = stringResource(R.string.agreement_text)

    ZToolPageSurface(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeaderCard()

                ZToolCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.agreement_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            ZToolMarkdownText(
                                markdown = markdownText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                ActionRow(
                    state = uiState.checkState,
                    onRequestRoot = { viewModel.refreshChecks() },
                    onCheckModule = { viewModel.refreshChecks() },
                    onRequestPackages = {
                        openAppDetails(context)
                        viewModel.refreshChecks()
                    },
                    onRequestUsage = {
                        usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onRequestOverlay = {
                        overlayLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                        )
                    }
                )

                BottomActionBar(
                    allGranted = allGranted,
                    onAgree = {
                        viewModel.acceptAgreement()
                        onAgreementAccepted()
                    },
                    onDisagree = {
                        viewModel.declineAgreement()
                        onAgreementDeclined()
                    }
                )
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.agreement_screen_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.agreement_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ActionRow(
    state: com.qimian233.ztool.data.home.FirstrunCheckState,
    onRequestRoot: () -> Unit,
    onCheckModule: () -> Unit,
    onRequestPackages: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FirstrunActionCard(
            title = stringResource(R.string.firstrun_root_title),
            summary = stringResource(R.string.firstrun_root_summary),
            checked = state.hasRoot,
            icon = Icons.Rounded.Lock,
            onClick = onRequestRoot
        )
        FirstrunActionCard(
            title = stringResource(R.string.firstrun_module_title),
            summary = stringResource(R.string.firstrun_module_summary),
            checked = state.isModuleActive,
            icon = Icons.Rounded.Verified,
            onClick = onCheckModule
        )
        FirstrunActionCard(
            title = stringResource(R.string.firstrun_packages_title),
            summary = stringResource(R.string.firstrun_packages_summary),
            checked = state.canListApps,
            icon = Icons.Rounded.Apps,
            onClick = onRequestPackages
        )
        FirstrunActionCard(
            title = stringResource(R.string.firstrun_usage_title),
            summary = stringResource(R.string.firstrun_usage_summary),
            checked = state.hasUsageStats,
            icon = Icons.Rounded.QueryStats,
            onClick = onRequestUsage
        )
        FirstrunActionCard(
            title = stringResource(R.string.firstrun_overlay_title),
            summary = stringResource(R.string.firstrun_overlay_summary),
            checked = state.hasOverlay,
            icon = Icons.Rounded.OpenInNew,
            onClick = onRequestOverlay
        )
    }
}

@Composable
private fun FirstrunActionCard(
    title: String,
    summary: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    ZToolCard(
        modifier = Modifier
            .width(170.dp)
            .clickable(onClick = onClick),
        containerColor = containerColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (checked) {
                    Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BottomActionBar(
    allGranted: Boolean,
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZToolTextButton(
            text = stringResource(R.string.agreement_dismiss),
            onClick = onDisagree,
            isPrimary = false,
            modifier = Modifier.weight(1f)
        )
        ZToolButton(
            onClick = onAgree,
            enabled = allGranted,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.agreement_confirm))
        }
    }
}

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    )
}

private class FirstrunAgreementViewModelFactory(
    private val repository: FirstrunAgreementRepository,
    private val agreementRepository: AgreementRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirstrunAgreementViewModel::class.java)) {
            return FirstrunAgreementViewModel(repository, agreementRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
