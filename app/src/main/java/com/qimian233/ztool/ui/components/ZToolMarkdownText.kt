package com.qimian233.ztool.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.parser.Parser

@Composable
fun ZToolMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalZToolColorScheme.current.onSurfaceVariant,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val resolvedBaseFontSize = if (fontSize == TextUnit.Unspecified) style.fontSize else fontSize
    val blocks = remember(markdown, style, color) {
        parseMarkdownBlocks(markdown, style, color, resolvedBaseFontSize)
    }

    SelectionContainer {
        androidx.compose.foundation.layout.Column(modifier = modifier.semantics { }) {
            blocks.forEachIndexed { index, block ->
                Text(
                    text = block,
                    style = style,
                    color = color,
                    fontSize = fontSize,
                    lineHeight = 32.sp
                )
                if (index != blocks.lastIndex) {
                    Text(text = "")
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(
    markdown: String,
    baseStyle: TextStyle,
    baseColor: Color,
    baseFontSize: TextUnit
): List<AnnotatedString> {
    val parser = Parser.builder().build()
    val document = parser.parse(markdown) as Document
    val blocks = mutableListOf<AnnotatedString>()
    var child: Node? = document.firstChild
    while (child != null) {
        blocks += renderBlock(child, baseStyle, baseColor, baseFontSize)
        child = child.next
    }
    return blocks
}

private fun renderBlock(
    node: Node,
    baseStyle: TextStyle,
    baseColor: Color,
    baseFontSize: TextUnit
): AnnotatedString {
    return when (node) {
        is Heading -> buildAnnotatedString {
            val weight = when (node.level) {
                1 -> FontWeight.Bold
                2 -> FontWeight.Bold
                3 -> FontWeight.SemiBold
                else -> FontWeight.Medium
            }
            withStyle(
                SpanStyle(
                    fontWeight = weight,
                    fontSize = headingFontSize(node.level, baseFontSize),
                    color = baseColor
                )
            ) {
                appendInlineChildren(node, this, baseStyle, baseColor, baseFontSize)
            }
        }
        is Paragraph, is BlockQuote -> buildAnnotatedString {
            appendInlineChildren(node, this, baseStyle, baseColor, baseFontSize)
        }
        is BulletList -> buildAnnotatedString {
            var item = node.firstChild
            while (item != null) {
                if (item is ListItem) {
                    append("• ")
                    appendInlineChildren(item, this, baseStyle, baseColor, baseFontSize)
                    append('\n')
                }
                item = item.next
            }
        }
        is OrderedList -> buildAnnotatedString {
            var item = node.firstChild
            var index = 1
            while (item != null) {
                if (item is ListItem) {
                    append("$index. ")
                    appendInlineChildren(item, this, baseStyle, baseColor, baseFontSize)
                    append('\n')
                    index++
                }
                item = item.next
            }
        }
        else -> buildAnnotatedString {
            appendInlineChildren(node, this, baseStyle, baseColor, baseFontSize)
        }
    }
}

private fun appendInlineChildren(
    node: Node,
    builder: AnnotatedString.Builder,
    baseStyle: TextStyle,
    baseColor: Color,
    baseFontSize: TextUnit
) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MarkdownText -> builder.append(child.literal)
            is Emphasis -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                appendInlineChildren(child, this, baseStyle, baseColor, baseFontSize)
            }
            is StrongEmphasis -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                appendInlineChildren(child, this, baseStyle, baseColor, baseFontSize)
            }
            is Code -> builder.withStyle(
                SpanStyle(
                    fontFamily = baseStyle.fontFamily,
                    background = baseColor.copy(alpha = 0.1f),
                    color = baseColor
                )
            ) {
                append(child.literal)
            }
            is Link -> builder.withStyle(
                SpanStyle(
                    color = baseColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            ) {
                appendInlineChildren(child, this, baseStyle, baseColor, baseFontSize)
            }
            is Image -> builder.append(child.title ?: child.destination)
            is HardLineBreak -> builder.append('\n')
            is SoftLineBreak -> builder.append(' ')
            else -> appendInlineChildren(child, builder, baseStyle, baseColor, baseFontSize)
        }
        child = child.next
    }
}

private fun headingFontSize(level: Int, baseFontSize: TextUnit): TextUnit {
    val fallback = if (baseFontSize == TextUnit.Unspecified) 16.sp else baseFontSize
    return when (level) {
        1 -> fallback * 1.9f
        2 -> fallback * 1.5f
        3 -> fallback * 1.25f
        4 -> fallback * 1.1f
        else -> fallback
    }
}
