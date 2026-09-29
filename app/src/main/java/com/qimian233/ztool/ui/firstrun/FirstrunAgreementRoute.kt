package com.qimian233.ztool.ui.firstrun

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.home.AgreementRepository
import com.qimian233.ztool.data.home.FirstrunAgreementRepository
import com.qimian233.ztool.data.home.FirstrunCheckState
import com.qimian233.ztool.data.home.FirstrunPageSchema
import com.qimian233.ztool.data.home.FirstrunSchemaRepository
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolOutlinedTextField
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.theme.LocalThemeRevealController
import com.qimian233.ztool.ui.theme.ztoolRevealCoverColor
import com.qimian233.ztool.viewmodel.FirstrunAgreementViewModel
import kotlinx.coroutines.delay

@Composable
fun FirstrunAgreementRoute(
    agreementDisplayMode: FirstrunDisplayMode = FirstrunDisplayMode.FirstRun,
    playIntroReveal: Boolean = false,
    onIntroRevealPlayed: () -> Unit = {},
    onAgreementAccepted: () -> Unit,
    onAgreementDeclined: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val view = LocalView.current
    val agreementRepository = remember { AgreementRepository(context) }
    val schemaRepository = remember { FirstrunSchemaRepository(context, agreementRepository) }
    val viewModel = remember {
            ViewModelProvider(
                activity,
                FirstrunAgreementViewModelFactory(
                    repository = FirstrunAgreementRepository(context),
                    agreementRepository = agreementRepository,
                    schemaRepository = schemaRepository
                )
            )[FirstrunAgreementViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val agreementReadScrollState = rememberScrollState()
    val agreementPageScrollState = rememberScrollState()
    val permissionPageScrollState = rememberScrollState()
    val revealController = LocalThemeRevealController.current
    val introCoverColor = ztoolRevealCoverColor()
    val gate = remember { ScrollToBottomAgreementGate() }
    // Pages to replay, frozen once per flow session: acceptance marks written
    // while the flow runs must not shrink the list under the user's feet.
    // Splash always opens the flow and is not part of the registry.
    val replayPages = rememberSaveable(stateSaver = ReplayPagesSaver) {
        mutableStateOf(deriveReplayPages(agreementDisplayMode, schemaRepository))
    }
    val pages = listOf(FirstrunPage.Splash) + replayPages.value
    val currentPageState = rememberSaveable { mutableStateOf(FirstrunPage.Splash) }
    // Hoisted so the user's input survives page switches within the first-run flow.
    val sourceVerifyInput = rememberSaveable { mutableStateOf("") }
    // True while a page change is driven by a reveal — the pages swap instantly
    // under the snapshot so the expanding circle fully owns the transition.
    var revealNavigation by remember { mutableStateOf(false) }

    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshChecks() }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshChecks() }

    // Refresh while the Permissions page is visible: fires on entering the page and
    // on returning from any permission screen — including safecenter's auto-start
    // page, which is launched through the root shell and has no result callback.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(currentPageState.value) {
        val onPermissionsPage = currentPageState.value == FirstrunPage.Permissions
        if (onPermissionsPage) viewModel.refreshChecks()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && onPermissionsPage) {
                viewModel.refreshChecks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playIntroReveal) {
        if (!playIntroReveal) return@LaunchedEffect
        // Give the system launch mask a moment to settle, then uncover the
        // welcome page with a circle growing from the bottom-center.
        delay(300)
        revealController.triggerCoverReveal(
            anchor = Offset(view.width / 2f, view.height.toFloat()),
            coverColor = introCoverColor
        )
        onIntroRevealPlayed()
    }

    DisposableEffect(agreementReadScrollState.value, agreementReadScrollState.maxValue) {
        gate.onScrollChanged(agreementReadScrollState.value < agreementReadScrollState.maxValue)
        onDispose { }
    }

    val navigateBack: (Offset) -> Unit = { tapAnchor ->
        revealNavigation = true
        revealController.triggerReveal(
            onAction = {
                val index = pages.indexOf(currentPageState.value)
                if (index <= 0) onAgreementDeclined() else currentPageState.value = pages[index - 1]
            },
            onAnimationEnd = { revealNavigation = false },
            anchor = tapAnchor
        )
    }

    /**
     * Advances to the next replay page, recording the page the user just
     * passed via [markPage]. On the last page the whole flow is finalized
     * instead (marks every registered page accepted) and the route closes.
     */
    val navigateForward: (Offset, (() -> Unit)?) -> Unit = { tapAnchor, markPage ->
        revealNavigation = true
        revealController.triggerReveal(
            onAction = {
                markPage?.invoke()
                val index = pages.indexOf(currentPageState.value)
                if (index < 0 || index == pages.lastIndex) {
                    viewModel.completeFirstrun()
                    onAgreementAccepted()
                } else {
                    currentPageState.value = pages[index + 1]
                }
            },
            onAnimationEnd = { revealNavigation = false },
            anchor = tapAnchor
        )
    }

    BackHandler(enabled = true) {
        val index = pages.indexOf(currentPageState.value)
        if (index <= 0) onAgreementDeclined() else currentPageState.value = pages[index - 1]
    }

    ZToolPageSurface(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize(), color = LocalZToolColorScheme.current.background) {
            AnimatedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                targetState = currentPageState.value,
                label = "firstrun_pages",
                transitionSpec = {
                    if (revealNavigation) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val forward = pages.indexOf(targetState) > pages.indexOf(initialState)
                        val enterDirection = if (forward) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        val exitDirection = if (forward) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        slideIntoContainer(
                            towards = enterDirection,
                            animationSpec = tween(FirstrunPageTransitionMillis)
                        ) togetherWith slideOutOfContainer(
                            towards = exitDirection,
                            animationSpec = tween(FirstrunPageTransitionMillis)
                        )
                    }
                }
            ) { page ->
                when (page) {
                    FirstrunPage.Splash -> SplashPage(
                        onStart = { tapAnchor -> navigateForward(tapAnchor, null) }
                    )
                    FirstrunPage.Agreement -> AgreementPage(
                        showHeader = agreementDisplayMode == FirstrunDisplayMode.FirstRun,
                        isLastPage = page == pages.last(),
                        markdownText = uiState.agreementMarkdown,
                        pageScrollState = agreementPageScrollState,
                        readScrollState = agreementReadScrollState,
                        firstPageReady = gate.satisfied,
                        onNext = { tapAnchor ->
                            navigateForward(tapAnchor) { viewModel.completeAgreementPage() }
                        },
                        onDisagree = { _ ->
                            viewModel.declineAgreement()
                            onAgreementDeclined()
                        }
                    )
                    FirstrunPage.SourceVerify -> SourceVerifyPage(
                        input = sourceVerifyInput.value,
                        onInputChange = { sourceVerifyInput.value = it },
                        isLastPage = page == pages.last(),
                        onNext = { tapAnchor ->
                            navigateForward(tapAnchor) { viewModel.completeSourceVerifyPage() }
                        },
                        onBack = navigateBack
                    )
                    FirstrunPage.Permissions -> PermissionPage(
                        state = uiState.checkState,
                        allGranted = uiState.checkState.allGranted,
                        isLastPage = page == pages.last(),
                        pageScrollState = permissionPageScrollState,
                        onRequestRoot = { viewModel.refreshChecks() },
                        onCheckModule = { viewModel.refreshChecks() },
                        onRequestPackages = { viewModel.refreshChecks() },
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
                        },
                        onRequestAutoStart = {
                            viewModel.requestAutoStart { launched ->
                                if (!launched) {
                                    Toast.makeText(
                                        context,
                                        R.string.page_firstrun_autostart_open_failed,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onAgree = { tapAnchor -> navigateForward(tapAnchor, null) },
                        onBack = navigateBack
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashPage(
    onStart: (Offset) -> Unit
) {
    val hintLetterSpacing by rememberInfiniteTransition(label = "splash_hint_breathing")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "splash_hint_letter_spacing"
        )
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { pageOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onStart(pageOrigin + offset) }
            }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.splash_logo),
                contentDescription = stringResource(R.string.common_splash_logo_description),
            )
            Spacer(modifier = Modifier.height(64.dp))
            Text(
                text = stringResource(R.string.page_firstrun_splash_welcome_text),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.common_splash_slogan),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = stringResource(R.string.page_firstrun_splash_tap_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                letterSpacing = hintLetterSpacing.sp
            )
        }
    }
}

@Composable
private fun AgreementPage(
    showHeader: Boolean,
    isLastPage: Boolean,
    markdownText: String,
    pageScrollState: ScrollState,
    readScrollState: ScrollState,
    firstPageReady: Boolean,
    onNext: (Offset) -> Unit,
    onDisagree: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScrollState)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showHeader) {
                PageHeader(
                    title = stringResource(R.string.page_firstrun_agreement_screen_title),
                    subtitle = stringResource(R.string.page_firstrun_agreement_screen_subtitle)
                )
            }

            Text(
                text = stringResource(R.string.page_firstrun_agreement_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            AgreementContentCard(
                markdownText = markdownText,
                scrollState = readScrollState
            )
        }

        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            nextText = stringResource(
                if (isLastPage) R.string.page_firstrun_agreement_confirm else R.string.page_firstrun_next_step
            ),
            nextEnabled = firstPageReady,
            onNext = onNext,
            onDisagree = onDisagree
        )
    }
}

@Composable
private fun SourceVerifyPage(
    input: String,
    onInputChange: (String) -> Unit,
    isLastPage: Boolean,
    onNext: (Offset) -> Unit,
    onBack: (Offset) -> Unit
) {
    val verified = input.trim() == ExpectedRepoName

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeader(
                title = stringResource(R.string.page_firstrun_verify_title),
                subtitle = stringResource(R.string.page_firstrun_verify_subtitle)
            )

            Text(
                text = stringResource(R.string.page_firstrun_verify_body),
                style = MaterialTheme.typography.bodyMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "1. " + stringResource(R.string.page_firstrun_verify_channel_1),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "2. " + stringResource(R.string.page_firstrun_verify_channel_2),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = stringResource(R.string.page_firstrun_verify_instruction),
                style = MaterialTheme.typography.bodyMedium
            )

            ZToolOutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.page_firstrun_verify_input_label),
                placeholder = stringResource(R.string.page_firstrun_verify_input_hint),
                isError = input.isNotEmpty() && !verified,
                singleLine = true
            )
        }

        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            nextText = stringResource(
                if (isLastPage) R.string.page_firstrun_agreement_confirm else R.string.page_firstrun_next_step
            ),
            nextEnabled = verified,
            onNext = onNext,
            onDisagree = onBack,
            negativeText = stringResource(R.string.page_firstrun_previous)
        )
    }
}

@Composable
private fun PermissionPage(
    state: FirstrunCheckState,
    allGranted: Boolean,
    isLastPage: Boolean,
    pageScrollState: ScrollState,
    onRequestRoot: () -> Unit,
    onCheckModule: () -> Unit,
    onRequestPackages: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAutoStart: () -> Unit,
    onAgree: (Offset) -> Unit,
    onBack: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScrollState)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeader(
                title = stringResource(R.string.page_firstrun_permissions_title),
                subtitle = stringResource(R.string.page_firstrun_permissions_subtitle)
            )

            Text(
                text = stringResource(R.string.page_firstrun_permissions_check_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            ActionRow(
                state = state,
                onRequestRoot = onRequestRoot,
                onCheckModule = onCheckModule,
                onRequestPackages = onRequestPackages,
                onRequestUsage = onRequestUsage,
                onRequestOverlay = onRequestOverlay,
                onRequestAutoStart = onRequestAutoStart
            )

            StatusBanner(
                text = if (allGranted) {
                    stringResource(R.string.page_firstrun_permissions_ready)
                } else {
                    stringResource(R.string.page_firstrun_permissions_pending)
                },
                ready = allGranted
            )
        }

        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            nextText = stringResource(
                if (isLastPage) R.string.page_firstrun_agreement_confirm else R.string.page_firstrun_next_step
            ),
            nextEnabled = allGranted,
            onNext = onAgree,
            onDisagree = onBack,
            negativeText = stringResource(R.string.page_firstrun_previous)
        )
    }
}

@Composable
private fun StatusBanner(
    text: String,
    ready: Boolean
) {
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (ready) {
            LocalZToolColorScheme.current.secondaryContainer
        } else {
            LocalZToolColorScheme.current.tertiaryContainer
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ready) LocalZToolColorScheme.current.onSecondaryContainer else LocalZToolColorScheme.current.onTertiaryContainer
        )
    }
}

@Composable
private fun PageHeader(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZToolColorScheme.current.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(
    state: FirstrunCheckState,
    onRequestRoot: () -> Unit,
    onCheckModule: () -> Unit,
    onRequestPackages: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAutoStart: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FirstrunActionCard(
            title = stringResource(R.string.page_firstrun_root_title),
            summary = stringResource(R.string.page_firstrun_root_summary),
            checked = state.hasRoot,
            icon = Icons.Rounded.Numbers,
            onClick = onRequestRoot
        )
        FirstrunActionCard(
            title = stringResource(R.string.page_firstrun_module_title),
            summary = stringResource(R.string.page_firstrun_module_summary),
            checked = state.isModuleActive,
            icon = Icons.Rounded.Extension,
            onClick = onCheckModule
        )
        FirstrunActionCard(
            title = stringResource(R.string.page_firstrun_packages_title),
            summary = stringResource(R.string.page_firstrun_packages_summary),
            checked = state.canListApps,
            icon = Icons.Rounded.Apps,
            onClick = onRequestPackages
        )
        FirstrunActionCard(
            title = stringResource(R.string.page_firstrun_usage_title),
            summary = stringResource(R.string.page_firstrun_usage_summary),
            checked = state.hasUsageStats,
            icon = Icons.Rounded.QueryStats,
            onClick = onRequestUsage
        )
        FirstrunActionCard(
            title = stringResource(R.string.page_firstrun_overlay_title),
            summary = stringResource(R.string.page_firstrun_overlay_summary),
            checked = state.hasOverlay,
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = onRequestOverlay
        )
        FirstrunActionCard(
            title = stringResource(R.string.page_firstrun_autostart_title),
            summary = stringResource(R.string.page_firstrun_autostart_summary),
            checked = state.hasAutoStart,
            icon = Icons.Rounded.RestartAlt,
            onClick = onRequestAutoStart
        )
    }
}

@Composable
private fun FirstrunActionCard(
    title: String,
    summary: String,
    checked: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val containerColor = if (checked) {
        LocalZToolColorScheme.current.secondaryContainer
    } else {
        LocalZToolColorScheme.current.surfaceContainerHigh
    }
    val contentColor = if (checked) {
        LocalZToolColorScheme.current.onSecondaryContainer
    } else {
        LocalZToolColorScheme.current.onSurface
    }
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        containerColor = containerColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor)
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
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
                    Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = LocalZToolColorScheme.current.primary)
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
    modifier: Modifier = Modifier,
    nextText: String,
    nextEnabled: Boolean,
    onNext: (Offset) -> Unit,
    onDisagree: (Offset) -> Unit,
    negativeText: String = stringResource(R.string.page_firstrun_agreement_dismiss)
) {
    var nextButtonCenter by remember { mutableStateOf(Offset.Zero) }
    var backButtonCenter by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZToolTextButton(
            text = negativeText,
            onClick = { onDisagree(backButtonCenter) },
            isPrimary = false,
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    backButtonCenter = coordinates.positionInRoot() +
                            Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                }
        )
        ZToolButton(
            onClick = { onNext(nextButtonCenter) },
            enabled = nextEnabled,
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    nextButtonCenter = coordinates.positionInRoot() +
                            Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                }
        ) {
            Text(nextText)
        }
    }
}

private class FirstrunAgreementViewModelFactory(
    private val repository: FirstrunAgreementRepository,
    private val agreementRepository: AgreementRepository,
    private val schemaRepository: FirstrunSchemaRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirstrunAgreementViewModel::class.java)) {
            return FirstrunAgreementViewModel(repository, agreementRepository, schemaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private enum class FirstrunPage {
    Splash,
    Agreement,
    SourceVerify,
    Permissions
}

private fun FirstrunPageSchema.toFirstrunPage(): FirstrunPage = when (this) {
    FirstrunPageSchema.AGREEMENT -> FirstrunPage.Agreement
    FirstrunPageSchema.SOURCE_VERIFY -> FirstrunPage.SourceVerify
    FirstrunPageSchema.PERMISSIONS -> FirstrunPage.Permissions
}

/**
 * Pages this launch must (re)show, in flow order: everything on a true first
 * run, otherwise only pages whose accepted schema version is stale (per-page
 * selective replay). Splash is prepended by the route, not part of the registry.
 */
private fun deriveReplayPages(
    displayMode: FirstrunDisplayMode,
    schemaRepository: FirstrunSchemaRepository
): List<FirstrunPage> {
    val schemaPages = if (displayMode == FirstrunDisplayMode.FirstRun) {
        FirstrunPageSchema.entries.toList()
    } else {
        val accepted = schemaRepository.loadStatus().acceptedVersions
        FirstrunPageSchema.entries.filter { accepted[it] != it.schemaVersion }
    }
    return schemaPages.sortedBy { it.order }.map { it.toFirstrunPage() }
}

/** Persists the replay list across config changes and process death. */
private val ReplayPagesSaver = listSaver<List<FirstrunPage>, String>(
    save = { it.map(FirstrunPage::name) },
    restore = { names -> names.map(FirstrunPage::valueOf) }
)

private const val ExpectedRepoName = "ZUX-ZTool"

private const val FirstrunPageTransitionMillis = 320
