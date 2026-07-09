package com.qimian233.ztool.settingactivity.setting.floatingwindow

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.settings.FloatingWindowRepository
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolCheckbox
import com.qimian233.ztool.viewmodel.FloatingWindowEffect
import com.qimian233.ztool.viewmodel.FloatingWindowUiState
import com.qimian233.ztool.viewmodel.FloatingWindowViewModel
import com.qimian233.ztool.viewmodel.FloatingWizardStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingWindow private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleOwner = context as? LifecycleOwner
        ?: error("FloatingWindow context must implement LifecycleOwner")
    private val viewModelStoreOwner = context as? ViewModelStoreOwner
        ?: error("FloatingWindow context must implement ViewModelStoreOwner")
    private val savedStateRegistryOwner = context as? SavedStateRegistryOwner
        ?: error("FloatingWindow context must implement SavedStateRegistryOwner")
    private val handler = Handler(Looper.getMainLooper())
    private val recomposer = Recomposer(AndroidUiDispatcher.CurrentThread)
    private val recomposerScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
    private val recomposerJob: Job = recomposerScope.launch {
        recomposer.runRecomposeAndApplyChanges()
    }
    private val viewModel = FloatingWindowViewModel(FloatingWindowRepository(context))
    private val uiState = mutableStateOf(viewModel.uiState)
    private var floatingView: ComposeView? = null
    private var updateRunnable: Runnable? = null

    init {
        initFloatingView()
    }

    private fun initFloatingView() {
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

        floatingView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            setParentCompositionContext(recomposer)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ZToolTheme(isPlatformDialog = true) {
                    FloatingWindowContent(
                        state = uiState.value,
                        onNext = ::handleNextStep,
                        onAddActivity = ::addCurrentActivity,
                        onShowEmbeddingDividerChanged = {
                            viewModel.setShowEmbeddingDivider(it)
                            syncUiState()
                        },
                        onSkipLetterboxDisplayInfoChanged = {
                            viewModel.setSkipLetterboxDisplayInfo(it)
                            syncUiState()
                        },
                        onSkipMultiWindowModeChanged = {
                            viewModel.setSkipMultiWindowMode(it)
                            syncUiState()
                        },
                        onShowSurfaceViewBackgroundChanged = {
                            viewModel.setShowSurfaceViewBackground(it)
                            syncUiState()
                        },
                        onShouldPausePrimaryActivityChanged = {
                            viewModel.setShouldPausePrimaryActivity(it)
                            syncUiState()
                        },
                        onDrag = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                    )
                }
            }
        }

        windowManager.addView(floatingView, params)
        startUpdating()
    }

    fun show() {
        floatingView?.visibility = View.VISIBLE
        if (updateRunnable == null) startUpdating()
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

    private fun handleNextStep() {
        viewModel.handleNextStep(::handleEffect)
        syncUiState()
    }

    private fun addCurrentActivity() {
        viewModel.addCurrentActivity(::handleEffect)
        syncUiState()
    }

    private fun startUpdating() {
        updateRunnable = object : Runnable {
            override fun run() {
                viewModel.refreshForeground()
                syncUiState()
                handler.postDelayed(this, UPDATE_INTERVAL)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopUpdating() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun syncUiState() {
        uiState.value = viewModel.uiState
    }

    private fun handleEffect(effect: FloatingWindowEffect) {
        when (effect) {
            is FloatingWindowEffect.ToastResource -> {
                Toast.makeText(
                    context,
                    effect.resId,
                    if (effect.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
            }
            is FloatingWindowEffect.ToastTextResource -> {
                Toast.makeText(
                    context,
                    context.getString(effect.resId, effect.value),
                    Toast.LENGTH_SHORT
                ).show()
            }
            FloatingWindowEffect.Close -> closeFloatingWindow()
        }
    }

    companion object {
        private const val UPDATE_INTERVAL = 1000L

        fun create(hostActivity: ComponentActivity): FloatingWindow {
            return FloatingWindow(hostActivity)
        }
    }
}

@Composable
private fun DragHandle(onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    LocalZToolColorScheme.current.onSurfaceVariant.copy(alpha = 0.4f)
                )
        )
    }
}

@Composable
private fun FloatingWindowContent(
    state: FloatingWindowUiState,
    onNext: () -> Unit,
    onAddActivity: () -> Unit,
    onShowEmbeddingDividerChanged: (Boolean) -> Unit,
    onSkipLetterboxDisplayInfoChanged: (Boolean) -> Unit,
    onSkipMultiWindowModeChanged: (Boolean) -> Unit,
    onShowSurfaceViewBackgroundChanged: (Boolean) -> Unit,
    onShouldPausePrimaryActivityChanged: (Boolean) -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(12.dp)),
            color = LocalZToolColorScheme.current.surface.copy(alpha = 0.92f),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                DragHandle(onDrag = onDrag)

                Text(
                    text = if (state.shouldBlockProgress && state.selectedApp != null) {
                        stringResource(R.string.return_to_app, state.selectedApp)
                    } else {
                        stepText(state.currentStep, state.selectedApp)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.primary
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.foregroundAppLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalZToolColorScheme.current.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.foregroundInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalZToolColorScheme.current.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = titleText(state.currentStep),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.primary
                )

                if (state.currentStep == FloatingWizardStep.SetMainPage ||
                    state.currentStep == FloatingWizardStep.AddActivities
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TutorialVideo(
                        videoResId = if (state.currentStep == FloatingWizardStep.SetMainPage) {
                            R.raw.mainact
                        } else {
                            R.raw.tutorial
                        }
                    )
                }

                if (state.currentStep == FloatingWizardStep.AddActivities) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ZToolButton(
                        onClick = onAddActivity,
                        enabled = !state.shouldBlockProgress
                    ) {
                        Text(stringResource(R.string.addCurrentActivity))
                    }
                    Text(
                        text = state.addedActivitiesText,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalZToolColorScheme.current.primary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (state.currentStep == FloatingWizardStep.SetOptions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FloatingOptionRow(
                        text = stringResource(R.string.showEmbeddingDivider),
                        checked = state.showEmbeddingDivider,
                        onCheckedChange = onShowEmbeddingDividerChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.skipLetterBoxToDisplay),
                        checked = state.skipLetterboxDisplayInfo,
                        onCheckedChange = onSkipLetterboxDisplayInfoChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.skipMultiWindowMode),
                        checked = state.skipMultiWindowMode,
                        onCheckedChange = onSkipMultiWindowModeChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.displaySurfaceViewBackground),
                        checked = state.showSurfaceViewBackground,
                        onCheckedChange = onShowSurfaceViewBackgroundChanged
                    )
                    FloatingOptionRow(
                        text = stringResource(R.string.shouldStopMainActivity),
                        checked = state.shouldPausePrimaryActivity,
                        onCheckedChange = onShouldPausePrimaryActivityChanged
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ZToolButton(
                        onClick = onNext,
                        enabled = !state.shouldBlockProgress,
                    ) {
                        Text(nextButtonText(state.currentStep))
                    }
                }
            }
        }
    }

@Composable
private fun titleText(step: FloatingWizardStep): String {
    return when (step) {
        FloatingWizardStep.SelectApp -> stringResource(R.string.welcome_message)
        FloatingWizardStep.SetMainPage -> stringResource(R.string.set_main_page_title)
        FloatingWizardStep.AddActivities -> stringResource(R.string.add_activities_title)
        FloatingWizardStep.SetOptions -> stringResource(R.string.config_options_title)
        FloatingWizardStep.Complete -> stringResource(R.string.config_complete_title)
    }
}

@Composable
private fun stepText(step: FloatingWizardStep, selectedApp: String?): String {
    return when (step) {
        FloatingWizardStep.SelectApp -> stringResource(R.string.step_1_instruction)
        FloatingWizardStep.SetMainPage -> stringResource(
            R.string.step_2_instruction,
            selectedApp.orEmpty()
        )
        FloatingWizardStep.AddActivities -> stringResource(R.string.step_3_instruction)
        FloatingWizardStep.SetOptions -> stringResource(R.string.step_4_instruction)
        FloatingWizardStep.Complete -> stringResource(R.string.step_complete_instruction)
    }
}

@Composable
private fun nextButtonText(step: FloatingWizardStep): String {
    return when (step) {
        FloatingWizardStep.AddActivities -> stringResource(R.string.continue_button)
        FloatingWizardStep.SetOptions -> stringResource(R.string.finish_config_button)
        FloatingWizardStep.Complete -> stringResource(R.string.save_config_button)
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
        ZToolCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = LocalZToolColorScheme.current.onSurface
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
                videoView.setVideoURI("android.resource://${videoView.context.packageName}/$videoResId".toUri())
                videoView.start()
            } else if (!videoView.isPlaying) {
                videoView.start()
            }
        }
    )
}
