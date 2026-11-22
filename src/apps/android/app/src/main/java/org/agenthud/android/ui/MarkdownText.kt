package org.agenthud.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agenthud.android.ui.theme.HudColors

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class CodeFence(val lang: String, val code: String) : MdBlock()
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data object Divider : MdBlock()
}

/**
 * Clean, lightweight Markdown renderer for agent chat messages:
 * - Collapses verbose `[Name](file:///C:/Users/...)` links into sleek filename spans
 * - Renders headings, bold, italic, inline code pills, lists, and horizontally scrollable code blocks
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = HudColors.TextPrimary,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val clipboard = LocalClipboardManager.current

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> {
                        val size = when (block.level) {
                            1 -> 18.sp
                            2 -> 16.sp
                            else -> 14.5.sp
                        }
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontSize = size,
                            lineHeight = (size.value + 5f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    is MdBlock.CodeFence -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = HudColors.Surface,
                            border = BorderStroke(1.dp, HudColors.Border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(HudColors.SurfaceElevated)
                                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = block.lang.ifBlank { "code" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HudColors.TextMuted,
                                    )
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(block.code)) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "Copy code",
                                            tint = HudColors.TextMuted,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                HorizontalDivider(color = HudColors.Border, thickness = 1.dp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = block.code,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = HudColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }

                    is MdBlock.Bullet -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (block.indent * 12).dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = block.marker,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = HudColors.TextMuted,
                                modifier = Modifier.width(if (block.marker.length > 2) 24.dp else 16.dp),
                            )
                            Text(
                                text = parseInlineMarkdown(block.text),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = textColor,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    is MdBlock.Paragraph -> {
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = textColor,
                        )
                    }

                    MdBlock.Divider -> {
                        HorizontalDivider(
                            color = HudColors.Border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split('\n')
    val blocks = ArrayList<MdBlock>()
    val paraBuf = ArrayList<String>()
    var inFence = false
    var fenceLang = ""
    val fenceBuf = ArrayList<String>()

    fun flushPara() {
        if (paraBuf.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraBuf.joinToString("\n")))
            paraBuf.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (!inFence) {
                flushPara()
                inFence = true
                fenceLang = trimmed.removePrefix("```").trim()
                fenceBuf.clear()
            } else {
                blocks.add(MdBlock.CodeFence(fenceLang, fenceBuf.joinToString("\n")))
                inFence = false
                fenceLang = ""
                fenceBuf.clear()
            }
            continue
        }
        if (inFence) {
            fenceBuf.add(line)
            continue
        }
        if (trimmed.isEmpty()) {
            flushPara()
            continue
        }
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushPara()
            blocks.add(MdBlock.Divider)
            continue
        }
        val headingMatch = HEADING_REGEX.matchEntire(trimmed)
        if (headingMatch != null) {
            flushPara()
            blocks.add(MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim()))
            continue
        }
        val bulletMatch = BULLET_REGEX.matchEntire(line)
        if (bulletMatch != null) {
            flushPara()
            val indent = (bulletMatch.groupValues[1].length / 2).coerceAtMost(3)
            blocks.add(MdBlock.Bullet("•", bulletMatch.groupValues[2].trim(), indent))
            continue
        }
        val numberedMatch = NUMBERED_REGEX.matchEntire(line)
        if (numberedMatch != null) {
            flushPara()
            val indent = (numberedMatch.groupValues[1].length / 2).coerceAtMost(3)
            blocks.add(MdBlock.Bullet("${numberedMatch.groupValues[2]}.", numberedMatch.groupValues[3].trim(), indent))
            continue
        }
        paraBuf.add(trimmed)
    }

    if (inFence && fenceBuf.isNotEmpty()) {
        blocks.add(MdBlock.CodeFence(fenceLang, fenceBuf.joinToString("\n")))
    }
    flushPara()
    return blocks
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val len = text.length
        while (idx < len) {
            // Markdown link: [label](url) -> clean highlighted label (hides giant file:/// paths!)
            if (text[idx] == '[') {
                val closeBracket = text.indexOf(']', idx + 1)
                if (closeBracket > idx && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 1) {
                        val label = text.substring(idx + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        val isFile = url.startsWith("file://", ignoreCase = true)
                        withStyle(
                            SpanStyle(
                                color = if (isFile) HudColors.SageBright else HudColors.BlueAccent,
                                fontFamily = if (isFile) FontFamily.Monospace else null,
                                fontSize = if (isFile) 12.5.sp else 14.sp,
                                background = if (isFile) HudColors.SurfaceElevated else Color.Transparent,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(if (isFile) " $label " else label)
                        }
                        idx = closeParen + 1
                        continue
                    }
                }
            }
            // Inline code: `code`
            if (text[idx] == '`') {
                val end = text.indexOf('`', idx + 1)
                if (end > idx + 1) {
                    val code = text.substring(idx + 1, end)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            color = Color(0xFFE2E2EA),
                            background = HudColors.SurfaceMuted,
                        ),
                    ) {
                        append(" $code ")
                    }
                    idx = end + 1
                    continue
                }
            }
            // Bold: **text**
            if (idx + 1 < len && text[idx] == '*' && text[idx + 1] == '*') {
                val end = text.indexOf("**", idx + 2)
                if (end > idx + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = HudColors.TextPrimary)) {
                        append(parseInlineMarkdown(text.substring(idx + 2, end)))
                    }
                    idx = end + 2
                    continue
                }
            }
            // Italic: *text*
            if (text[idx] == '*' && (idx + 1 < len && text[idx + 1] != ' ')) {
                val end = text.indexOf('*', idx + 1)
                if (end > idx + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                    continue
                }
            }
            append(text[idx])
            idx++
        }
    }
}

private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.+)$")
private val BULLET_REGEX = Regex("^(\\s*)[-*•]\\s+(.+)$")
private val NUMBERED_REGEX = Regex("^(\\s*)(\\d+)\\.\\s+(.+)$")
