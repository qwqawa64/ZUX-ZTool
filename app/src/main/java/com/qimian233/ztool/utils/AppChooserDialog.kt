package com.qimian233.ztool.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.app.Dialog
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.showPlatformComposeDialog
import java.util.concurrent.Executors

import com.qimian233.ztool.ui.components.ZToolDialogSurface

object AppChooserDialog {
    interface AppSelectionCallback {
        fun onSelected(selectedApps: List<AppInfo>)
        fun onCancel()
    }

    class AppInfo(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable
    ) {
        var isSelected: Boolean = false
    }

    @JvmStatic
    fun show(
        context: Context,
        packageNames: List<String>,
        callback: AppSelectionCallback
    ) {
        show(context, packageNames, null, null, callback)
    }

    @JvmStatic
    fun show(
        context: Context,
        packageNames: List<String>,
        title: String?,
        callback: AppSelectionCallback
    ) {
        show(context, packageNames, null, title, callback)
    }

    @JvmStatic
    fun show(
        context: Context,
        packageNames: List<String>,
        selectedPackageNames: List<String>?,
        callback: AppSelectionCallback
    ) {
        show(context, packageNames, selectedPackageNames, null, callback)
    }

    @JvmStatic
    fun show(
        context: Context,
        packageNames: List<String>,
        selectedPackageNames: List<String>?,
        title: String?,
        callback: AppSelectionCallback?
    ) {
        val loadingDialog = showComposeDialog(context, cancelable = false) { _ ->
            LoadingContent(message = stringResource(R.string.loadingUserAPP))
        }

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            val appInfoList = loadApps(context, packageNames, selectedPackageNames)
            handler.post {
                loadingDialog.dismiss()
                executor.shutdown()
                showAppSelectionDialog(context, appInfoList, title, callback)
            }
        }
    }

    private fun loadApps(
        context: Context,
        packageNames: List<String>,
        selectedPackageNames: List<String>?
    ): List<AppInfo> {
        val packageManager = context.packageManager
        val selectedSet = selectedPackageNames?.toSet().orEmpty()
        val selectedApps = mutableListOf<AppInfo>()
        val unselectedApps = mutableListOf<AppInfo>()

        packageNames.forEach { packageName ->
            val app = packageManager.loadAppInfo(packageName) ?: return@forEach
            app.isSelected = packageName in selectedSet
            if (app.isSelected) {
                selectedApps += app
            } else {
                unselectedApps += app
            }
        }

        return if (selectedPackageNames == null) unselectedApps else selectedApps + unselectedApps
    }

    private fun PackageManager.loadAppInfo(packageName: String): AppInfo? {
        return try {
            val applicationInfo: ApplicationInfo = getApplicationInfo(packageName, 0)
            AppInfo(
                packageName = packageName,
                appName = getApplicationLabel(applicationInfo).toString(),
                appIcon = getApplicationIcon(applicationInfo)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun showAppSelectionDialog(
        context: Context,
        appInfoList: List<AppInfo>,
        title: String?,
        callback: AppSelectionCallback?
    ) {
        lateinit var dialog: Dialog
        dialog = showComposeDialog(context, cancelable = true) {
            AppChooserContent(
                title = title,
                apps = appInfoList,
                onConfirm = { selectedPackages ->
                    appInfoList.forEach { app ->
                        app.isSelected = app.packageName in selectedPackages
                    }
                    callback?.onSelected(appInfoList.filter { it.isSelected })
                    dialog.dismiss()
                },
                onCancel = {
                    callback?.onCancel()
                    dialog.dismiss()
                }
            )
        }
        dialog.setOnCancelListener {
            callback?.onCancel()
        }
    }

    private fun showComposeDialog(
        context: Context,
        cancelable: Boolean,
        content: @Composable (Dialog) -> Unit
    ): Dialog {
        return showPlatformComposeDialog(
            context = context,
            cancelable = cancelable,
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT,
            content = content
        )
    }
}

@Composable
private fun LoadingContent(message: String) {
    ZToolDialogSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AppChooserContent(
    title: String?,
    apps: List<AppChooserDialog.AppInfo>,
    onConfirm: (Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selectedPackages = remember {
        mutableStateListOf<String>().apply {
            addAll(apps.filter { it.isSelected }.map { it.packageName })
        }
    }
    val filteredApps = remember(query, apps, selectedPackages.size) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.appName.lowercase().contains(normalizedQuery) ||
                    app.packageName.lowercase().contains(normalizedQuery)
            }
        }
    }

    ZToolDialogSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.SearchHint)) }
            )

            Text(
                text = selectedPackages.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(top = 8.dp)
            ) {
                items(
                    items = filteredApps,
                    key = { it.packageName }
                ) { app ->
                    val selected = app.packageName in selectedPackages
                    AppChooserRow(
                        app = app,
                        selected = selected,
                        onSelectedChange = { checked ->
                            if (checked) {
                                if (app.packageName !in selectedPackages) {
                                    selectedPackages += app.packageName
                                }
                            } else {
                                selectedPackages -= app.packageName
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.restart_no))
                }
                TextButton(onClick = { onConfirm(selectedPackages.toSet()) }) {
                    Text(stringResource(R.string.restart_yes))
                }
            }
        }
    }
}

@Composable
private fun AppChooserRow(
    app: AppChooserDialog.AppInfo,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectedChange(!selected) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            modifier = Modifier.size(44.dp),
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = context.getString(R.string.app_icon)
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(app.appIcon)
            }
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = selected,
            onCheckedChange = onSelectedChange
        )
    }
}
