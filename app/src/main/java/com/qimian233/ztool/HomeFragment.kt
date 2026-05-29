package com.qimian233.ztool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.ConfigUpgrade
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class HomeFragment : Fragment() {

    interface EnvironmentStateListener {
        fun onEnvironmentStateChanged(environmentReady: Boolean)
    }

    private var environmentStateListener: EnvironmentStateListener? = null
    private var lastEnvironmentState = false

    private lateinit var shellExecutor: EnhancedShellExecutor
    private val isCheckingEnvironment = AtomicBoolean(false)
    private val isUpdatingSystemInfo = AtomicBoolean(false)

    private var cachedKernelVersion = ""
    private var cachedRootSource = ""
    private var cachedFrameworkVersion = ""
    private var cachedCurrentSlot = ""
    private var cachedRomRegion = ""
    private var lastSystemInfoUpdate = 0L

    private var uiState by mutableStateOf(HomeUiState())

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is EnvironmentStateListener) {
            environmentStateListener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shellExecutor = EnhancedShellExecutor.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ZToolTheme {
                    HomeScreen(
                        state = uiState,
                        onRestartClick = ::showRebootMenu,
                        onToggleUpdateExpanded = ::toggleUpdateExpanded,
                        onIgnoreUpdate = ::ignoreUpdate,
                        onOpenUpdate = ::openUpdateUrl
                    )

                    if (uiState.configUpgradeDialogVisible) {
                        ConfigUpgradeDialog(
                            onRestart = {
                                uiState = uiState.copy(configUpgradeDialogVisible = false)
                                shellExecutor.executeRootCommand("su -c reboot", 3)
                            },
                            onLater = {
                                uiState = uiState.copy(configUpgradeDialogVisible = false)
                                Toast.makeText(
                                    requireContext(),
                                    R.string.have_not_restart_warn,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    uiState.rebootConfirmation?.let { target ->
                        RebootConfirmDialog(
                            target = target,
                            onConfirm = {
                                uiState = uiState.copy(rebootConfirmation = null)
                                executeReboot(target)
                            },
                            onDismiss = { uiState = uiState.copy(rebootConfirmation = null) }
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.postDelayed({
            checkEnvironment()
            checkAppUpdate()
        }, 100)
    }

    override fun onResume() {
        super.onResume()
        if (System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
            updateSystemInfoAsync()
        }
    }

    override fun onDetach() {
        super.onDetach()
        environmentStateListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::shellExecutor.isInitialized) {
            shellExecutor.clearCache()
        }
        Log.d(TAG, "onDestroy: 销毁主页")
    }

    private fun checkEnvironment() {
        if (isCheckingEnvironment.getAndSet(true)) {
            Log.d(TAG, "环境检测已在执行，跳过本次检测")
            return
        }

        Thread {
            try {
                val moduleActive = isModuleActive()
                val rootAvailable = shellExecutor.checkRootAccess().isSuccess
                Log.i(TAG, "环境检测结果 - 模块激活: $moduleActive, Root可用: $rootAvailable")

                activity?.runOnUiThread {
                    val environmentReady = moduleActive && rootAvailable
                    uiState = uiState.copy(
                        isCheckingEnvironment = false,
                        isModuleActive = moduleActive,
                        isRootAvailable = rootAvailable,
                        hintText = buildHintText(moduleActive, rootAvailable)
                    )

                    notifyEnvironmentState(environmentReady)

                    if (environmentReady) {
                        updateModuleStatusAsync()
                        updateSystemInfoAsync()
                        updateHomepageHint()
                        checkConfigUpgrade()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "环境检测失败", e)
                activity?.runOnUiThread {
                    uiState = uiState.copy(
                        isCheckingEnvironment = false,
                        isRootAvailable = false,
                        hintText = getString(R.string.missing_environment) + getString(R.string.root_not_available)
                    )
                    notifyEnvironmentState(false)
                }
            } finally {
                isCheckingEnvironment.set(false)
            }
        }.start()
    }

    private fun notifyEnvironmentState(environmentReady: Boolean) {
        if (environmentReady != lastEnvironmentState) {
            environmentStateListener?.onEnvironmentStateChanged(environmentReady)
            lastEnvironmentState = environmentReady
        }
    }

    private fun buildHintText(moduleActive: Boolean, rootAvailable: Boolean): String {
        if (moduleActive && rootAvailable) return getString(R.string.environment_ready)

        return buildString {
            append(getString(R.string.missing_environment))
            if (!moduleActive && !rootAvailable) {
                append(getString(R.string.module_not_active))
                append(", ")
                append(getString(R.string.root_not_available))
            } else {
                if (!moduleActive) append(getString(R.string.module_not_active))
                if (!rootAvailable) append(getString(R.string.root_not_available))
            }
        }
    }

    private fun updateHomepageHint() {
        val enableYiyan = ModulePreferencesUtils(requireContext())
            .loadBooleanSetting("enable_homepage_yiyan", true)
        uiState = uiState.copy(hintText = getString(R.string.environment_ready))
        if (enableYiyan) {
            fetchHintFromApi()
        }
    }

    private fun checkConfigUpgrade() {
        Log.i(TAG, "开始检查配置是否为最新版本")
        if (ConfigUpgrade.configUpgrader(requireContext())) {
            uiState = uiState.copy(configUpgradeDialogVisible = true)
            Log.i(TAG, "配置升级成功")
        } else {
            Log.i(TAG, "配置已是最新版本")
        }
    }

    private fun updateModuleStatusAsync() {
        Thread {
            try {
                val version = getModuleVersionInfo()
                if (cachedRootSource.isEmpty() || isSystemInfoCacheExpired()) {
                    cachedRootSource = detectRootSource()
                }
                if (cachedFrameworkVersion.isEmpty() || isSystemInfoCacheExpired()) {
                    cachedFrameworkVersion = detectFrameworkVersionAndMode()
                }

                activity?.runOnUiThread {
                    uiState = uiState.copy(
                        moduleVersion = version,
                        rootSource = getString(R.string.root_manager_prefix, cachedRootSource),
                        frameworkVersion = getString(R.string.xp_framework_prefix, cachedFrameworkVersion)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "异步更新模块状态失败", e)
            }
        }.start()
    }

    private fun updateSystemInfoAsync() {
        if (isUpdatingSystemInfo.getAndSet(true)) return

        Thread {
            try {
                val unknown = getString(R.string.unknown)
                val deviceModel = Build.MODEL.ifBlank { unknown }
                val androidVersion = Build.VERSION.RELEASE.ifBlank { unknown }
                    .let { if (it == unknown) it else getString(R.string.android_version_prefix, it) }
                val buildVersion = Build.DISPLAY.ifBlank { unknown }

                if (cachedKernelVersion.isEmpty() || isSystemInfoCacheExpired()) {
                    cachedKernelVersion = getKernelVersion()
                }
                if (cachedCurrentSlot.isEmpty() || isSystemInfoCacheExpired()) {
                    cachedCurrentSlot = getCurrentBootSlot()
                }
                if (cachedRomRegion.isEmpty() || isSystemInfoCacheExpired()) {
                    cachedRomRegion = getRomRegion()
                    ModulePreferencesUtils(requireContext()).saveStringSetting("RomRegion", cachedRomRegion)
                }

                lastSystemInfoUpdate = System.currentTimeMillis()

                activity?.runOnUiThread {
                    uiState = uiState.copy(
                        deviceModel = deviceModel,
                        androidVersion = androidVersion,
                        buildVersion = buildVersion,
                        kernelVersion = cachedKernelVersion.ifBlank { unknown },
                        currentSlot = cachedCurrentSlot.ifBlank { unknown },
                        romRegion = cachedRomRegion.ifBlank { unknown }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "异步更新系统信息失败", e)
            } finally {
                isUpdatingSystemInfo.set(false)
            }
        }.start()
    }

    private fun checkAppUpdate() {
        Thread {
            try {
                val context = context ?: return@Thread
                val currentVersionCode = getCurrentVersionCode(context)
                val connection = URL(UPDATE_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val json = getJsonObject(connection)
                    val newVersionCode = json.getInt("newVersionCode")
                    val ignoredVersion = context
                        .getSharedPreferences(PREF_NAME_UPDATE, Context.MODE_PRIVATE)
                        .getInt(KEY_IGNORE_VERSION, 0)

                    if (newVersionCode > currentVersionCode && newVersionCode != ignoredVersion) {
                        val updateInfo = UpdateInfo(
                            versionName = json.getString("newVersionName"),
                            versionCode = newVersionCode,
                            changelog = json.getString("whatNew"),
                            downloadUrl = json.getString("url")
                        )
                        activity?.runOnUiThread {
                            uiState = uiState.copy(updateInfo = updateInfo)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Update", "检查更新失败", e)
            }
        }.start()
    }

    private fun fetchHintFromApi() {
        Thread {
            try {
                val connection = URL("https://api.xygeng.cn/one").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val jsonResponse = getJsonObject(connection)
                    if (jsonResponse.getInt("code") == 200) {
                        val data = jsonResponse.getJSONObject("data")
                        val content = data.getString("content")
                        val origin = data.getString("origin")
                        activity?.runOnUiThread {
                            if (uiState.environmentReady) {
                                uiState = uiState.copy(
                                    hintText = getString(R.string.homepage_yiyan, content, origin)
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取API提示失败: ${e.message}")
            }
        }.start()
    }

    private fun getCurrentVersionCode(context: Context): Int {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun getModuleVersionInfo(): String {
        return try {
            val packageInfo = requireActivity().packageManager
                .getPackageInfo(requireActivity().packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            getString(
                R.string.module_version_prefix,
                "${packageInfo.versionName} ($versionCode)"
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "获取模块版本信息失败: ${e.message}")
            getString(R.string.module_version_unknown)
        }
    }

    private fun detectRootSource(): String {
        val detectionCommands = arrayOf("magisk -v", "su -v", "apd -v")
        for (cmd in detectionCommands) {
            try {
                val result = shellExecutor.executeRootCommand(cmd, 3)
                if (result.isSuccess && !result.output.isNullOrBlank()) {
                    val output = result.output.trim()
                    if (cmd.contains("magisk")) {
                        return getString(R.string.magisk_su_format, output)
                    }
                    if (cmd.contains("su -v") && output.contains("KernelSU")) {
                        val endPosition = output.indexOf("KernelSU")
                        return getString(R.string.kernelsu_format, output.substring(0, endPosition - 1))
                    }
                    if (cmd.contains("apd")) {
                        return getString(R.string.apatch_format, output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "检测Root来源失败: ${e.message}")
            }
        }
        return getString(R.string.unknown_root_available)
    }

    private fun detectFrameworkVersionAndMode(): String {
        try {
            val propResult = shellExecutor.executeRootCommand("getprop ro.lsposed.version", 3)
            if (propResult.isSuccess && !propResult.output.isNullOrBlank()) {
                return getString(R.string.lsposed_standard_format, "v${propResult.output.trim()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "系统属性检测失败: ${e.message}")
        }

        try {
            val lsResult = shellExecutor.executeRootCommand("ls -la /data/adb/modules/ | grep -i lsposed", 3)
            if (lsResult.isSuccess && !lsResult.output.isNullOrBlank()) {
                return getString(R.string.lsposed_zygisk)
            }
        } catch (e: Exception) {
            Log.w(TAG, "目录检测失败: ${e.message}")
        }

        return getString(R.string.unknown_framework)
    }

    private fun getKernelVersion(): String {
        val result = shellExecutor.executeRootCommand("uname -r", 3)
        return if (result.isSuccess && !result.output.isNullOrBlank()) result.output.trim() else ""
    }

    private fun getCurrentBootSlot(): String {
        val result = shellExecutor.executeRootCommand("getprop ro.boot.slot_suffix", 3)
        return if (result.isSuccess && !result.output.isNullOrBlank()) {
            when (result.output.trim()) {
                "_a" -> getString(R.string.slot_a)
                "_b" -> getString(R.string.slot_b)
                else -> getString(R.string.unknown)
            }
        } else {
            getString(R.string.unknown)
        }
    }

    private fun getRomRegion(): String {
        return try {
            val commands = listOf(
                "getprop ro.boot.region",
                "getprop ro.config.zui.region",
                "getprop ro.vendor.config.zui.region"
            )
            commands.firstNotNullOfOrNull { command ->
                val result = shellExecutor.executeRootCommand(command, 3)
                result.output?.trim()?.takeIf { it.isNotEmpty() }
            } ?: getString(R.string.unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ROM region: ${e.message}")
            getString(R.string.unknown)
        }
    }

    private fun isSystemInfoCacheExpired(): Boolean {
        return System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION
    }

    private fun toggleUpdateExpanded() {
        uiState.updateInfo?.let {
            uiState = uiState.copy(updateInfo = it.copy(expanded = !it.expanded))
        }
    }

    private fun ignoreUpdate(versionCode: Int) {
        requireContext()
            .getSharedPreferences(PREF_NAME_UPDATE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_IGNORE_VERSION, versionCode)
            .apply()
        uiState = uiState.copy(updateInfo = null)
        Toast.makeText(requireContext(), R.string.update_ignore_toast, Toast.LENGTH_SHORT).show()
    }

    private fun openUpdateUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.open_web_link_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRebootMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.reboot_menu, menu)
            setOnMenuItemClickListener(::handleMenuItemClick)
            show()
        }
    }

    private fun handleMenuItemClick(item: MenuItem): Boolean {
        uiState = uiState.copy(rebootConfirmation = when (item.itemId) {
            R.id.menu_soft_reboot -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Toast.makeText(
                        requireContext(),
                        R.string.soft_reboot_not_supported,
                        Toast.LENGTH_LONG
                    ).show()
                    return true
                }
                RebootTarget.Userspace
            }
            R.id.menu_bootloader -> RebootTarget.Bootloader
            R.id.menu_recovery -> RebootTarget.Recovery
            R.id.menu_edl -> RebootTarget.Edl
            R.id.menu_reboot -> RebootTarget.System
            else -> return false
        })
        return true
    }

    private fun executeReboot(target: RebootTarget) {
        val result = shellExecutor.executeRootCommand(target.command, 5)
        if (result.isSuccess) {
            Toast.makeText(requireContext(), R.string.reboot_success, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.reboot_failed, result.error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isModuleActive(): Boolean {
        Log.d(TAG, "isModuleActive: 模块自检测方法被调用，默认返回false")
        return false
    }

    internal data class HomeUiState(
        val isCheckingEnvironment: Boolean = true,
        val isModuleActive: Boolean = false,
        val isRootAvailable: Boolean = false,
        val hintText: String = "",
        val moduleVersion: String = "",
        val rootSource: String = "",
        val frameworkVersion: String = "",
        val deviceModel: String = "",
        val androidVersion: String = "",
        val buildVersion: String = "",
        val kernelVersion: String = "",
        val currentSlot: String = "",
        val romRegion: String = "",
        val updateInfo: UpdateInfo? = null,
        val configUpgradeDialogVisible: Boolean = false,
        val rebootConfirmation: RebootTarget? = null
    ) {
        val environmentReady: Boolean
            get() = isModuleActive && isRootAvailable
    }

    internal data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val changelog: String,
        val downloadUrl: String,
        val expanded: Boolean = false
    )

    internal enum class RebootTarget(
        val command: String,
        val messageRes: Int
    ) {
        Userspace("reboot userspace", R.string.soft_reboot_confirm_message),
        System("reboot", R.string.reboot_confirm_message),
        Bootloader("reboot bootloader", R.string.bootloader_confirm_message),
        Recovery("reboot recovery", R.string.recovery_confirm_message),
        Edl("reboot edl", R.string.edl_confirm_message)
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val UPDATE_URL =
            "https://raw.githubusercontent.com/qwqawa64/ZUX-ZTool/refs/heads/master/UpdateCheck.json"
        private const val PREF_NAME_UPDATE = "update_prefs"
        private const val KEY_IGNORE_VERSION = "ignore_version_code"
        private const val SYSTEM_INFO_CACHE_DURATION = 60_000L

        @Throws(IOException::class, JSONException::class)
        private fun getJsonObject(connection: HttpURLConnection): JSONObject {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
            reader.close()
            return JSONObject(response)
        }
    }
}

@Composable
private fun HomeScreen(
    state: HomeFragment.HomeUiState,
    onRestartClick: (View) -> Unit,
    onToggleUpdateExpanded: () -> Unit,
    onIgnoreUpdate: (Int) -> Unit,
    onOpenUpdate: (String) -> Unit
) {
    Scaffold { innerPadding ->
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
                    .widthIn(max = 1120.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.homeFragment_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isRootAvailable) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            modifier = Modifier.size(48.dp),
                            factory = { context ->
                                android.widget.FrameLayout(context).apply {
                                    val button = android.widget.ImageButton(context).apply {
                                        setImageResource(R.drawable.ic_restart_menu)
                                        background = null
                                        contentDescription = context.getString(R.string.reboot_menu_description)
                                        setOnClickListener { onRestartClick(this) }
                                    }
                                    addView(
                                        button,
                                        android.widget.FrameLayout.LayoutParams(
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!state.environmentReady) {
                    RequirementCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                AnimatedVisibility(visible = state.environmentReady && state.updateInfo != null) {
                    state.updateInfo?.let { update ->
                        Column {
                            UpdateCard(
                                update = update,
                                onToggleExpanded = onToggleUpdateExpanded,
                                onIgnore = { onIgnoreUpdate(update.versionCode) },
                                onOpenUpdate = { onOpenUpdate(update.downloadUrl) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                if (state.environmentReady) {
                    ModuleStatusCard(state)
                    Spacer(modifier = Modifier.height(16.dp))
                    SystemInfoCard(state)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text(
                    text = state.hintText.ifBlank {
                        if (state.isCheckingEnvironment) {
                            stringResource(R.string.loading)
                        } else {
                            stringResource(R.string.workConditionTip)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RequirementCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.workModeRequirementDetail),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun UpdateCard(
    update: HomeFragment.UpdateInfo,
    onToggleExpanded: () -> Unit,
    onIgnore: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.buildCode, update.versionName, update.versionCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = update.changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = if (update.expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onIgnore) {
                    Text(stringResource(R.string.update_button_ignore))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onOpenUpdate) {
                    Text(stringResource(R.string.update_button_update))
                }
            }
        }
    }
}

@Composable
private fun ModuleStatusCard(state: HomeFragment.HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.environmentState),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (state.isModuleActive) {
                            stringResource(R.string.module_active)
                        } else {
                            stringResource(R.string.module_inactive)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBlock(
                    label = stringResource(R.string.version),
                    value = state.moduleVersion.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = MaterialTheme.colorScheme.onPrimaryContainer
                )
                InfoBlock(
                    label = stringResource(R.string.root),
                    value = state.rootSource.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = MaterialTheme.colorScheme.onPrimaryContainer
                )
                InfoBlock(
                    label = stringResource(R.string.framework),
                    value = state.frameworkVersion.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun InfoBlock(
    label: String,
    value: String,
    colorOnContainer: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.widthIn(min = 180.dp, max = 320.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colorOnContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = colorOnContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SystemInfoCard(state: HomeFragment.HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.deviceInfo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DeviceInfoItem(stringResource(R.string.deviceCodeName), state.deviceModel)
                DeviceInfoItem(stringResource(R.string.AndroidVersion), state.androidVersion)
                DeviceInfoItem(stringResource(R.string.buildVersion), state.buildVersion)
                DeviceInfoItem(stringResource(R.string.kernelVersion), state.kernelVersion)
            }
            Spacer(modifier = Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.currentSlot) + state.currentSlot.ifBlank { stringResource(R.string.unknown) })
                    }
                )
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.romRegion) + state.romRegion.ifBlank { stringResource(R.string.unknown) })
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoItem(
    label: String,
    value: String
) {
    Column(modifier = Modifier.widthIn(min = 220.dp, max = 420.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { stringResource(R.string.placeHolderUnknown) },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConfigUpgradeDialog(
    onRestart: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.config_upgraded_tip_title)) },
        text = { Text(stringResource(R.string.config_upgraded_tip_message)) },
        confirmButton = {
            TextButton(onClick = onRestart) {
                Text(stringResource(R.string.restart_system_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.do_not_restart_system_button))
            }
        }
    )
}

@Composable
private fun RebootConfirmDialog(
    target: HomeFragment.RebootTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reboot_confirm_title)) },
        text = { Text(stringResource(target.messageRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
