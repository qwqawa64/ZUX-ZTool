package com.qimian233.ztool.settingactivity.launcher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.R
import com.qimian233.ztool.data.launcher.BatchUninstallRepository
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.BatchUninstallStage
import com.qimian233.ztool.viewmodel.BatchUninstallUiState
import com.qimian233.ztool.viewmodel.BatchUninstallViewModel

/**
 * 批量卸载执行页：接收启动器编辑模式 Hook 分发过来的包名列表，
 * 展示确认 → Root 执行进度 → 结果摘要。
 *
 * 仅允许启动器（或无 referrer 的系统场景）唤起；执行全程需要用户
 * 在本页二次确认，Root 通过 [BatchUninstallRepository] 完成。
 */
class BatchUninstallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val referrer = referrer
        if (referrer != null && referrer.host != ScopeKeys.LAUNCHER.packageName) {
            Toast.makeText(this, R.string.batch_uninstall_referrer_rejected, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val packages = intent?.getStringArrayListExtra(EXTRA_PACKAGES).orEmpty()

        val repository = BatchUninstallRepository()
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BatchUninstallViewModel::class.java)) {
                    return BatchUninstallViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        })[BatchUninstallViewModel::class.java]

        if (savedInstanceState == null) {
            viewModel.start(packages)
        }

        setContent {
            ZToolTheme {
                val state by viewModel.uiState.collectAsState()
                BatchUninstallScreen(
                    state = state,
                    onConfirm = viewModel::confirmAndRun,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGES = "ztool_extra_batch_uninstall_packages"
    }
}

/** 单个包在结果列表中的展示状态。 */
private sealed interface PackageStatus {
    data object Pending : PackageStatus
    data object Success : PackageStatus
    data class Failed(val message: String) : PackageStatus
}

@Composable
private fun BatchUninstallScreen(
    state: BatchUninstallUiState,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    val showBottomButtons = state.stage != BatchUninstallStage.Running
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.batch_uninstall_title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (state.stage) {
                    BatchUninstallStage.Empty -> {
                        StageText(stringResource(R.string.batch_uninstall_empty))
                    }

                    BatchUninstallStage.RootUnavailable -> {
                        StageText(stringResource(R.string.batch_uninstall_root_unavailable))
                    }

                    BatchUninstallStage.Confirm -> {
                        StageText(
                            stringResource(
                                R.string.batch_uninstall_confirm_message,
                                state.packages.size
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        state.packages.forEach { packageName ->
                            PackageRow(packageName = packageName, status = PackageStatus.Pending)
                        }
                    }

                    BatchUninstallStage.Running -> {
                        val done = state.results.size
                        StageText(
                            stringResource(R.string.batch_uninstall_running) +
                                " (${done}/${state.packages.size})"
                        )
                        Spacer(Modifier.height(8.dp))
                        state.packages.forEachIndexed { index, packageName ->
                            val result = state.results.getOrNull(index)
                            val status = when {
                                result == null -> PackageStatus.Pending
                                result.success -> PackageStatus.Success
                                else -> PackageStatus.Failed(result.message)
                            }
                            PackageRow(packageName = packageName, status = status)
                        }
                    }

                    BatchUninstallStage.Finished -> {
                        StageText(
                            stringResource(
                                R.string.batch_uninstall_summary,
                                state.successCount,
                                state.failureCount
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        state.results.forEach { result ->
                            val status = if (result.success) {
                                PackageStatus.Success
                            } else {
                                PackageStatus.Failed(result.message)
                            }
                            PackageRow(packageName = result.packageName, status = status)
                        }
                    }
                }
            }

            if (showBottomButtons) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    ZToolTextButton(
                        text = stringResource(
                            if (state.stage == BatchUninstallStage.Confirm) {
                                R.string.batch_uninstall_cancel
                            } else {
                                R.string.batch_uninstall_done
                            }
                        ),
                        onClick = onClose,
                        isPrimary = false
                    )
                    if (state.stage == BatchUninstallStage.Confirm) {
                        Spacer(Modifier.width(12.dp))
                        ZToolTextButton(
                            text = stringResource(R.string.batch_uninstall_start),
                            onClick = onConfirm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PackageRow(packageName: String, status: PackageStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            PackageStatus.Pending -> {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PackageStatus.Success -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            is PackageStatus.Failed -> {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (status is PackageStatus.Failed && status.message.isNotBlank()) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
