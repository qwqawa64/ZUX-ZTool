package com.qimian233.ztool.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

@Composable
fun ZToolMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val annotated = remember(markdown, style, color) {
        parseMarkdown(markdown, baseStyle = style, baseColor = color)
    }

    SelectionContainer {
        Text(
            text = annotated,
            modifier = modifier.semantics { },
            style = style,
            color = color
        )
    }
}

private fun parseMarkdown(
    markdown: String,
    baseStyle: TextStyle,
    baseColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.startsWith("# ") -> appendStyledBlock(line.removePrefix("# "), baseStyle, baseColor, FontWeight.Bold, 1.35f, false)
                line.startsWith("## ") -> appendStyledBlock(line.removePrefix("## "), baseStyle, baseColor, FontWeight.Bold, 1.2f, false)
                line.startsWith("### ") -> appendStyledBlock(line.removePrefix("### "), baseStyle, baseColor, FontWeight.SemiBold, 1.1f, false)
                line.startsWith("- ") || line.startsWith("* ") -> appendBullet(line.substring(2), baseStyle, baseColor)
                line.matches(Regex("""\d+\.\s+.*""")) -> appendNumbered(line, baseStyle, baseColor)
                line.isBlank() -> append('\n')
                else -> appendInlineMarkdown(line, baseStyle, baseColor)
            }
            if (index != lines.lastIndex) append('\n')
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStyledBlock(
    text: String,
    baseStyle: TextStyle,
    baseColor: Color,
    weight: FontWeight,
    lineHeightMultiplier: Float,
    withBullet: Boolean
) {
    if (withBullet) append("• ")
    withStyle(
        SpanStyle(
            fontWeight = weight,
            color = baseColor
        )
    ) {
        appendInlineMarkdown(text, baseStyle, baseColor)
    }
    if (lineHeightMultiplier > 1f) {
        // no-op marker for readability, line height follows style
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendBullet(
    text: String,
    baseStyle: TextStyle,
    baseColor: Color
) {
    append("• ")
    appendInlineMarkdown(text, baseStyle, baseColor)
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendNumbered(
    text: String,
    baseStyle: TextStyle,
    baseColor: Color
) {
    val dotIndex = text.indexOf('.')
    val prefix = text.substring(0, dotIndex + 1)
    append(prefix)
    append(" ")
    appendInlineMarkdown(text.substring(dotIndex + 1).trimStart(), baseStyle, baseColor)
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    baseStyle: TextStyle,
    baseColor: Color
) {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text.startsWith("__", index) -> {
                val end = text.indexOf("__", startIndex = index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end > index + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = baseStyle.fontFamily,
                            background = baseColor.copy(alpha = 0.1f),
                            color = baseColor
                        )
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text.startsWith("[", index) -> {
                val close = text.indexOf(']', startIndex = index + 1)
                val openParen = if (close >= 0) text.indexOf('(', startIndex = close + 1) else -1
                val closeParen = if (openParen >= 0) text.indexOf(')', startIndex = openParen + 1) else -1
                if (close > index + 1 && openParen == close + 1 && closeParen > openParen + 1) {
                    val label = text.substring(index + 1, close)
                    val url = text.substring(openParen + 1, closeParen)
                    pushStringAnnotation(tag = LINK_TAG, annotation = url)
                    withStyle(
                        SpanStyle(
                            color = baseColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(label)
                    }
                    pop()
                    index = closeParen + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text.startsWith("*", index) -> {
                val end = text.indexOf('*', startIndex = index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}

private const val LINK_TAG = "markdown_link"
