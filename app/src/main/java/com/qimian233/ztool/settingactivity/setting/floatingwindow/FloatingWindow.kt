package com.qimian233.ztool.settingactivity.setting.floatingwindow

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.runtime.Recomposer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.theme.ZToolTheme
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.SortedMap
import java.util.TreeMap
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingWindow(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val recomposer = Recomposer(AndroidUiDispatcher.CurrentThread)
    private val recomposerScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
    private val recomposerJob: Job = recomposerScope.launch {
        recomposer.runRecomposeAndApplyChanges()
    }
    private var floatingView: ComposeView? = null
    private var updateRunnable: Runnable? = null

    private var currentStep by mutableStateOf(WizardStep.SelectApp)
    private var selectedApp by mutableStateOf<String?>(null)
    private var foregroundInfo by mutableStateOf(context.getString(R.string.current_activity, context.getString(R.string.unknown)))
    private var foregroundAppLabel by mutableStateOf(context.getString(R.string.app_name_label, context.getString(R.string.unknown)))
    private var shouldBlockProgress by mutableStateOf(false)
    private var addedActivitiesText by mutableStateOf(context.getString(R.string.noActivityAdded))

    private var appPackage: String? = null
    private var mainActivity: String? = null
    private val activityFromSet = linkedSetOf<String>()

    private var showEmbeddingDivider by mutableStateOf(true)
    private var skipLetterboxDisplayInfo by mutableStateOf(false)
    private var skipMultiWindowMode by mutableStateOf(true)
    private var showSurfaceViewBackground by mutableStateOf(false)
    private var shouldPausePrimaryActivity by mutableStateOf(false)

    init {
        initFloatingView()
    }

    private fun initFloatingView() {
        floatingView = ComposeView(context).apply {
            setParentCompositionContext(recomposer)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ZToolTheme {
                    FloatingWindowContent(
                        currentStep = currentStep,
                        selectedApp = selectedApp,
                        foregroundInfo = foregroundInfo,
                        foregroundAppLabel = foregroundAppLabel,
                        shouldBlockProgress = shouldBlockProgress,
                        addedActivitiesText = addedActivitiesText,
                        showEmbeddingDivider = showEmbeddingDivider,
                        skipLetterboxDisplayInfo = skipLetterboxDisplayInfo,
                        skipMultiWindowMode = skipMultiWindowMode,
                        showSurfaceViewBackground = showSurfaceViewBackground,
                        shouldPausePrimaryActivity = shouldPausePrimaryActivity,
                        onNext = ::handleNextStep,
                        onAddActivity = ::addCurrentActivity,
                        onShowEmbeddingDividerChanged = { showEmbeddingDivider = it },
                        onSkipLetterboxDisplayInfoChanged = { skipLetterboxDisplayInfo = it },
                        onSkipMultiWindowModeChanged = { skipMultiWindowMode = it },
                        onShowSurfaceViewBackgroundChanged = { showSurfaceViewBackground = it },
                        onShouldPausePrimaryActivityChanged = { shouldPausePrimaryActivity = it }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        windowManager.addView(floatingView, params)
        startUpdating()
    }

    private fun handleNextStep() {
        when (currentStep) {
            WizardStep.SelectApp -> {
                appPackage = getForegroundActivityByShell(true)
                if (appPackage == null || appPackage == context.getString(R.string.unknown)) {
                    Toast.makeText(context, R.string.cannot_get_app_foreground, Toast.LENGTH_SHORT).show()
                    return
                }
                selectedApp = getAppNameFromPackage(context, appPackage)
                currentStep = WizardStep.SetMainPage
            }

            WizardStep.SetMainPage -> {
                mainActivity = getForegroundActivityByShell(false)
                if (mainActivity == null || mainActivity == context.getString(R.string.unknown)) {
                    Toast.makeText(context, R.string.cannot_get_activity, Toast.LENGTH_SHORT).show()
                    return
                }
                activityFromSet.add(mainActivity.orEmpty())
                updateAddedActivitiesText()
                currentStep = WizardStep.AddActivities
            }

            WizardStep.AddActivities -> {
                currentStep = WizardStep.SetOptions
            }

            WizardStep.SetOptions -> {
                currentStep = WizardStep.Complete
            }

            WizardStep.Complete -> {
                generateConfig()
                closeFloatingWindow()
            }
        }
    }

    private fun addCurrentActivity() {
        val currentActivity = getForegroundActivityByShell(false)
        if (currentActivity != null && currentActivity != context.getString(R.string.unknown)) {
            if (activityFromSet.add(currentActivity)) {
                updateAddedActivitiesText()
                Toast.makeText(
                    context,
                    context.getString(R.string.activity_added, currentActivity),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, R.string.activity_already_added, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, R.string.cannot_get_activity, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAddedActivitiesText() {
        addedActivitiesText = buildString {
            append(context.getString(R.string.added_activities_count, activityFromSet.size))
            if (activityFromSet.isNotEmpty()) {
                append("\n")
                append(activityFromSet.joinToString("\n"))
            }
        }
    }

    private fun generateConfig() {
        try {
            val config = JSONObject().apply {
                put("name", appPackage)
                put("mainPage", mainActivity)

                val activityPairs = JSONArray()
                activityFromSet.forEach { fromActivity ->
                    activityPairs.put(
                        JSONObject().apply {
                            put("from", fromActivity)
                            put("to", "*")
                        }
                    )
                }
                put("activityPairs", activityPairs)

                put("showEmbeddingDivider", showEmbeddingDivider.toString())
                put("skipLetterboxDisplayInfo", skipLetterboxDisplayInfo.toString())
                put("skipMultiWindowMode", skipMultiWindowMode.toString())
                put("showSurfaceViewBackground", showSurfaceViewBackground.toString())
                put("shouldPausePrimaryActivity", shouldPausePrimaryActivity.toString())
                put("forceFullscreenPages", JSONArray())
                put("transActivities", JSONArray())
                put("leftTransActivities", JSONArray())
            }

            val configJson = config.toString(2)
            Log.d("EmbeddingConfig", "生成的配置:\n$configJson")
            Toast.makeText(context, R.string.config_generated, Toast.LENGTH_LONG).show()
            saveBase64StringToFile(context, configJson, appPackage)
        } catch (e: JSONException) {
            Log.e("FloatingWindow", "生成配置失败", e)
            Toast.makeText(context, R.string.config_generation_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun show() {
        floatingView?.visibility = View.VISIBLE
        if (updateRunnable == null) startUpdating()
    }

    private fun startUpdating() {
        updateRunnable = object : Runnable {
            override fun run() {
                val foregroundApp = getForegroundApp()
                val foregroundPackage = getForegroundActivityByShell(true)
                val appName = getAppNameFromPackage(context, foregroundPackage)
                    ?: context.getString(R.string.unknown)

                Log.i("EmbeddingConfig", "当前应用: $foregroundApp")
                foregroundInfo = context.getString(R.string.current_activity, foregroundApp)
                foregroundAppLabel = context.getString(R.string.app_name_label, appName)

                shouldBlockProgress = currentStep <= WizardStep.AddActivities &&
                    selectedApp != null &&
                    appName != selectedApp

                handler.postDelayed(this, UPDATE_INTERVAL)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopUpdating() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun getForegroundApp(): String {
        val activityInfo = getForegroundActivityByShell(false)
        return if (activityInfo != null && activityInfo != context.getString(R.string.unknown)) {
            activityInfo
        } else {
            getForegroundPackage()
        }
    }

    private fun getForegroundActivityByShell(onlyPackageName: Boolean): String? {
        return try {
            val process = Runtime.getRuntime()
                .exec("su -c dumpsys activity activities | grep -E \"ResumedActivity|mFocusedActivity\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                if (line.contains("ResumedActivity") || line.contains("mFocusedActivity")) {
                    val pattern = Pattern.compile("u0\\s+([^/]+)/([^\\s\\},]+)")
                    val matcher = pattern.matcher(line)
                    if (matcher.find()) {
                        val packageName = matcher.group(1).orEmpty()
                        val activityName = matcher.group(2).orEmpty()
                        reader.close()
                        process.destroy()
                        return if (onlyPackageName) packageName else packageName + activityName
                    }
                }
                line = reader.readLine()
            }
            reader.close()
            process.destroy()
            getForegroundActivityByShellAlternative()
        } catch (e: Exception) {
            Log.e("FloatingWindow", "读取前台 Activity 失败", e)
            getForegroundActivityByShellAlternative()
        }
    }

    private fun getForegroundActivityByShellAlternative(): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c dumpsys activity top | grep -E \"ACTIVITY\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                if (line.contains("ACTIVITY")) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        reader.close()
                        process.destroy()
                        return parts[1]
                    }
                }
                line = reader.readLine()
            }
            reader.close()
            process.destroy()
            context.getString(R.string.unknown)
        } catch (e: Exception) {
            Log.e("FloatingWindow", "读取前台 Activity 备用方法失败", e)
            context.getString(R.string.unknown)
        }
    }

    private fun getForegroundPackage(): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
        val sortedStats: SortedMap<Long, android.app.usage.UsageStats> = TreeMap()
        stats?.forEach { usageStats ->
            sortedStats[usageStats.lastTimeUsed] = usageStats
        }
        return sortedStats.takeIf { it.isNotEmpty() }?.get(sortedStats.lastKey())?.packageName
            ?: context.getString(R.string.unknown)
    }

    fun saveBase64StringToFile(context: Context, originalString: String, packageName: String?) {
        try {
            val base64String = Base64.encodeToString(originalString.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
            val dir = File(context.filesDir, "data/custom_EmbeddingConfig")
            if (!dir.exists() && !dir.mkdirs()) return

            val file = File(dir, "${System.currentTimeMillis()}_$packageName")
            FileOutputStream(file).use { outputStream ->
                outputStream.write(base64String.toByteArray(Charsets.UTF_8))
            }
        } catch (e: IOException) {
            Log.e("FloatingWindow", "保存配置失败", e)
        }
    }

    fun closeFloatingWindow() {
        stopUpdating()
        floatingView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        floatingView = null
        recomposer.cancel()
        recomposerJob.cancel()
    }

    fun hide() {
        closeFloatingWindow()
    }

    internal enum class WizardStep {
        SelectApp,
        SetMainPage,
        AddActivities,
        SetOptions,
        Complete
    }

    companion object {
        private const val UPDATE_INTERVAL = 1000L

        @JvmStatic
        fun getAppNameFromPackage(context: Context?, packageName: String?): String? {
            if (context == null || packageName.isNullOrEmpty()) return null
            val packageManager = context.packageManager
            return try {
                val applicationInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("FloatingWindow", "获取应用名称失败", e)
                null
            }
        }
    }
}

@Composable
private fun FloatingWindowContent(
    currentStep: FloatingWindow.WizardStep,
    selectedApp: String?,
    foregroundInfo: String,
    foregroundAppLabel: String,
    shouldBlockProgress: Boolean,
    addedActivitiesText: String,
    showEmbeddingDivider: Boolean,
    skipLetterboxDisplayInfo: Boolean,
    skipMultiWindowMode: Boolean,
    showSurfaceViewBackground: Boolean,
    shouldPausePrimaryActivity: Boolean,
    onNext: () -> Unit,
    onAddActivity: () -> Unit,
    onShowEmbeddingDividerChanged: (Boolean) -> Unit,
    onSkipLetterboxDisplayInfoChanged: (Boolean) -> Unit,
    onSkipMultiWindowModeChanged: (Boolean) -> Unit,
    onShowSurfaceViewBackgroundChanged: (Boolean) -> Unit,
    onShouldPausePrimaryActivityChanged: (Boolean) -> Unit
) {
    ZToolTheme {
        Surface(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = if (shouldBlockProgress && selectedApp != null) {
                        stringResource(R.string.return_to_app, selectedApp)
                    } else {
                        stepText(currentStep, selectedApp)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = foregroundAppLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = foregroundInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = titleText(currentStep),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (currentStep == FloatingWindow.WizardStep.SetMainPage ||
                    currentStep == FloatingWindow.WizardStep.AddActivities
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TutorialVideo(
                        videoResId = if (currentStep == FloatingWindow.WizardStep.SetMainPage) {
                            R.raw.mainact
                        } else {
                            R.raw.tutorial
                        }
                    )
                }

                if (currentStep == FloatingWindow.WizardStep.AddActivities) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAddActivity,
                        enabled = !shouldBlockProgress
                    ) {
                        Text(stringResource(R.string.addCurrentActivity))
                    }
                    Text(
                        text = addedActivitiesText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (currentStep == FloatingWindow.WizardStep.SetOptions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FloatingOptionRow(
                        text = stringResource(R.string.showEmbeddingDivider),
                        checked = showEmbeddingDivider,
                        onCheckedChange = onShowEmbeddingDividerChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.skipLetterBoxToDisplay),
                        checked = skipLetterboxDisplayInfo,
                        onCheckedChange = onSkipLetterboxDisplayInfoChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.skipMultiWindowMode),
                        checked = skipMultiWindowMode,
                        onCheckedChange = onSkipMultiWindowModeChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.displaySurfaceViewBackground),
                        checked = showSurfaceViewBackground,
                        onCheckedChange = onShowSurfaceViewBackgroundChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.shouldStopMainActivity),
                        checked = shouldPausePrimaryActivity,
                        onCheckedChange = onShouldPausePrimaryActivityChanged
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onNext,
                        enabled = !shouldBlockProgress,
                    ) {
                        Text(nextButtonText(currentStep))
                    }
                }
            }
        }
    }
}

@Composable
private fun titleText(step: FloatingWindow.WizardStep): String {
    return when (step) {
        FloatingWindow.WizardStep.SelectApp -> stringResource(R.string.welcome_message)
        FloatingWindow.WizardStep.SetMainPage -> stringResource(R.string.set_main_page_title)
        FloatingWindow.WizardStep.AddActivities -> stringResource(R.string.add_activities_title)
        FloatingWindow.WizardStep.SetOptions -> stringResource(R.string.config_options_title)
        FloatingWindow.WizardStep.Complete -> stringResource(R.string.config_complete_title)
    }
}

@Composable
private fun stepText(step: FloatingWindow.WizardStep, selectedApp: String?): String {
    return when (step) {
        FloatingWindow.WizardStep.SelectApp -> stringResource(R.string.step_1_instruction)
        FloatingWindow.WizardStep.SetMainPage -> stringResource(
            R.string.step_2_instruction,
            selectedApp.orEmpty()
        )
        FloatingWindow.WizardStep.AddActivities -> stringResource(R.string.step_3_instruction)
        FloatingWindow.WizardStep.SetOptions -> stringResource(R.string.step_4_instruction)
        FloatingWindow.WizardStep.Complete -> stringResource(R.string.step_complete_instruction)
    }
}

@Composable
private fun nextButtonText(step: FloatingWindow.WizardStep): String {
    return when (step) {
        FloatingWindow.WizardStep.AddActivities -> stringResource(R.string.continue_button)
        FloatingWindow.WizardStep.SetOptions -> stringResource(R.string.finish_config_button)
        FloatingWindow.WizardStep.Complete -> stringResource(R.string.save_config_button)
        else -> stringResource(R.string.next_button)
    }
}

@Composable
private fun FloatingOptionRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TutorialVideo(videoResId: Int) {
    AndroidView(
        modifier = Modifier
            .size(width = 220.dp, height = 150.dp)
            .background(Color.Black),
        factory = { context ->
            VideoView(context).apply {
                setOnCompletionListener { start() }
            }
        },
        update = { videoView ->
            if (videoView.tag != videoResId) {
                videoView.tag = videoResId
                videoView.setVideoURI(Uri.parse("android.resource://${videoView.context.packageName}/$videoResId"))
                videoView.start()
            } else if (!videoView.isPlaying) {
                videoView.start()
            }
        }
    )
}
