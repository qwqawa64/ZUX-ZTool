package com.qimian233.ztool.screens.features

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qimian233.ztool.R
import com.qimian233.ztool.XposedServiceBridge
import com.qimian233.ztool.data.features.FeatureAvailability
import com.qimian233.ztool.data.features.FeatureCard
import com.qimian233.ztool.data.features.FeatureCatalog
import com.qimian233.ztool.data.features.FeatureDestination
import com.qimian233.ztool.data.features.FeatureIconLoader
import com.qimian233.ztool.data.features.FeatureIconSource
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.settings.SettingsRepository
import com.qimian233.ztool.screens.search.FeatureSearchBar
import com.qimian233.ztool.ui.components.LocalSearchOverlayState
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FeaturesMainRoute(
    onFeatureDestinationSelected: (FeatureDestination) -> Unit = {},
    onOpenSearchResult: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val allCards = FeatureCatalog.cards
    val installedPackages = rememberInstalledPackages(context)
    val showAllApps = remember(context) {
        SettingsRepository(context.applicationContext).loadState().isShowAllAppsEnabled
    }
    var scopeSet by remember { mutableStateOf(XposedServiceBridge.getScope().toSet()) }
    val scopeRequestFailReason = stringResource(R.string.page_features_scope_request_fail_message)
    // "system" is the LSPosed system-server scope entry — not a real installed package
    val systemScopePackages = setOf(ScopeKeys.SYSTEM_SERVER.packageName)
    var scopeRequestItem by remember { mutableStateOf<FeatureCard?>(null) }
    // PackageManager icon lookups survive grid item disposal during scrolling.
    val iconCache = remember { mutableStateMapOf<String, Drawable?>() }
    // Docked search is permanently docked under the top bar; only the expanded
    // result state toggles.
    var searchExpanded by remember { mutableStateOf(false) }

    val (visibleCards, warningMessageRes) = remember(allCards, installedPackages, scopeSet, showAllApps) {
        val scopedCards = allCards.map { card ->
            FeatureCardState(
                card = card,
                inScope = card.scopes()
                    .filter { it.packageName in installedPackages || it.packageName in systemScopePackages }
                    .all { it.packageName in scopeSet }
            )
        }
        val visible = scopedCards.filter { state ->
            when (val availability = state.card.availability) {
                is FeatureAvailability.RequiresInstalled ->
                    availability.packageName in installedPackages
                FeatureAvailability.AlwaysAvailable -> true
            }
        }
        when {
            showAllApps -> scopedCards to null
            installedPackages.isEmpty() ->
                scopedCards to R.string.page_features_app_list_permission_warning
            visible.isEmpty() ->
                scopedCards to R.string.page_features_all_filtered_warning
            else -> visible to null
        }
    }

    // Scope request dialog
    scopeRequestItem?.let { card ->
        ZToolDialog(
            onDismissRequest = { scopeRequestItem = null },
            title = { Text(stringResource(card.nameRes)) },
            text = { Text(stringResource(R.string.page_features_scope_request_dialog_message)) },
            confirmButton = {
                ZToolTextButton(
                    onClick = {
                        XposedServiceBridge.requestScope(
                            card.scopes()
                                .map { it.packageName }
                                .filter { it in installedPackages || it in systemScopePackages },
                            object : XposedService.OnScopeEventListener {

                                override fun onScopeRequestApproved(packages: List<String>) {
                                    scopeSet = scopeSet + packages
                                }

                                override fun onScopeRequestFailed(reason: String) {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(
                                            context,
                                            String.format(scopeRequestFailReason, reason),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                        scopeRequestItem = null
                    },
                    text = stringResource(R.string.common_confirm)
                )
            },
            dismissButton = {
                ZToolTextButton(
                    onClick = { scopeRequestItem = null },
                    text = stringResource(R.string.common_cancel),
                    isPrimary = false
                )
            }
        )
    }

    FeaturesRoute(
        items = visibleCards,
        iconCache = iconCache,
        warningMessageRes = warningMessageRes,
        onFeatureClick = { state ->
            if (state.inScope) {
                searchExpanded = false
                onFeatureDestinationSelected(state.card.destination)
            } else {
                scopeRequestItem = state.card
            }
        },
        searchExpanded = searchExpanded,
        onSearchExpandedChange = { expanded ->
            searchExpanded = expanded
        },
        onOpenEntry = { entry ->
            searchExpanded = false
            val destination = entry.featureDestination
            if (destination != null) {
                onFeatureDestinationSelected(destination)
            } else {
                onOpenSearchResult(com.qimian233.ztool.search.SearchIndex.targetRoute(entry))
            }
        }
    )
}

private data class FeatureCardState(
    val card: FeatureCard,
    val inScope: Boolean
)

private val FeatureCardHeight: Dp = 112.dp

@Composable
private fun rememberInstalledPackages(context: Context): Set<String> {
    return remember(context) {
        try {
            context.packageManager.getInstalledApplications(0)
                .mapTo(mutableSetOf()) { it.packageName }
                .minus(context.packageName)
        } catch (_: Throwable) {
            emptySet()
        }
    }
}

@Composable
private fun FeaturesRoute(
    items: List<FeatureCardState>,
    iconCache: MutableMap<String, Drawable?>,
    warningMessageRes: Int?,
    onFeatureClick: (FeatureCardState) -> Unit,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onOpenEntry: (com.qimian233.ztool.search.SearchEntry) -> Unit
) {
    // The dim layer lives in the app shell so it can cover the navigation rail too;
    // this screen only publishes where the search bar is and how to dismiss it.
    val searchOverlay = LocalSearchOverlayState.current
    SideEffect {
        searchOverlay.visible = searchExpanded
        searchOverlay.onDismiss = { onSearchExpandedChange(false) }
    }
    DisposableEffect(searchOverlay) {
        onDispose { searchOverlay.visible = false }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        ZToolScaffold(
            topBar = {
                ZToolTopAppBar(
                    title = stringResource(R.string.page_features_title),
                    addNavIcon = false
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
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .widthIn(max = 1280.dp)
                        .padding(horizontal = 32.dp, vertical = 32.dp)
                ) {
                    FeatureSearchBar(
                        visible = true,
                        expanded = searchExpanded,
                        onExpandedChange = onSearchExpandedChange,
                        onOpenEntry = onOpenEntry,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (warningMessageRes != null) {
                        FeatureWarningCard(message = stringResource(warningMessageRes))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (items.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Destination is the stable unique key for every card.
                            items(items = items, key = { it.card.destination }) { state ->
                                FeatureCardItem(
                                    state = state,
                                    iconCache = iconCache,
                                    onClick = { onFeatureClick(state) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureWarningCard(message: String) {
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LocalZToolColorScheme.current.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = LocalZToolColorScheme.current.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onErrorContainer
            )
        }
    }
}

@Composable
private fun FeatureCardItem(
    state: FeatureCardState,
    iconCache: MutableMap<String, Drawable?>,
    onClick: () -> Unit
) {
    val card = state.card
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(FeatureCardHeight)
            .clickable(onClick = onClick),
        containerColor = LocalZToolColorScheme.current.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardIcon(
                source = card.icon,
                iconCache = iconCache,
                contentDescription = stringResource(card.nameRes)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(card.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalZToolColorScheme.current.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (state.inScope) {
                    Text(
                        text = stringResource(card.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stringResource(R.string.page_features_not_in_scope_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = LocalZToolColorScheme.current.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardIcon(
    source: FeatureIconSource,
    iconCache: MutableMap<String, Drawable?>,
    contentDescription: String
) {
    when (source) {
        is FeatureIconSource.Package ->
            PackageIcon(source.packageName, iconCache, contentDescription)
        is FeatureIconSource.LocalGlyph -> {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalZToolColorScheme.current.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(source.resId),
                    contentDescription = contentDescription,
                    tint = LocalZToolColorScheme.current.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun PackageIcon(
    packageName: String,
    iconCache: MutableMap<String, Drawable?>,
    contentDescription: String
) {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(
        initialValue = iconCache[packageName],
        packageName
    ) {
        if (iconCache.containsKey(packageName)) {
            value = iconCache[packageName]
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            FeatureIconLoader.loadPackageIcon(context.applicationContext, packageName)
        }
        iconCache[packageName] = loaded
        value = loaded
    }
    if (icon == null) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LocalZToolColorScheme.current.surfaceContainerHighest)
        )
    } else {
        AndroidView(
            modifier = Modifier.size(48.dp),
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    this.contentDescription = contentDescription
                }
            },
            update = { imageView ->
                imageView.contentDescription = contentDescription
                imageView.setImageDrawable(icon)
            }
        )
    }
}
