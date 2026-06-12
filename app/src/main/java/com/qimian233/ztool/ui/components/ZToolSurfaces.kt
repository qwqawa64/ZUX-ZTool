package com.qimian233.ztool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults

@Composable
fun ZToolPageSurface(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = contentAlignment,
            content = content
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun ZToolCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    defaultElevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        val actualColor = if (containerColor == Color.Transparent || containerColor == MaterialTheme.colorScheme.surfaceContainer) {
            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer
        } else {
            containerColor
        }
        top.yukonga.miuix.kmp.basic.Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = actualColor
        ) {
            content()
        }
        return
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = defaultElevation)
    ) {
        content()
    }
}
