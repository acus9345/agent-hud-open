package org.agenthud.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.agenthud.android.data.Transcript
import org.agenthud.android.ui.theme.HudColors

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class CodeFence(val lang: String, val code: String) : MdBlock()
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Divider : MdBlock()
}

/**
 * Markdown for chat, styled after the Claude app: roomy prose, quiet headings, real code wells
 * (language + Copy, unwrapped lines), tables, block quotes, and local-file links that open the
 * file preview.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = HudColors.TextPrimary,
    fontSize: TextUnit = ChatProseSize,
    lineHeight: TextUnit = ChatProseLineHeight,
    onFileClick: ((label: String, fileUri: String) -> Unit)? = null,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    // Stable callback so memoized AnnotatedStrings survive recomposition (typing, animations).
    val latestFileClick = rememberUpdatedState(onFileClick)
    val hasFileClick = onFileClick != null
    val fileClick: ((String, String) -> Unit)? = remember(hasFileClick) {
        if (hasFileClick) { label, uri -> latestFileClick.value?.invoke(label, uri) } else null
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> fontSize * 1.3f
                        2 -> fontSize * 1.15f
                        else -> fontSize * 1.02f
                    }
                    SelectionContainer {
                        Text(
                            text = remember(block.text, fileClick) { parseInlineMarkdown(block.text, fileClick) },
                            fontSize = size,
                            lineHeight = size * 1.35f,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                is MdBlock.CodeFence -> CodeOutput(text = block.code, label = block.lang.ifBlank { "code" })

                is MdBlock.Bullet -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 16).dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = block.marker,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = HudColors.TextSecondary,
                            modifier = Modifier.width(if (block.marker.length > 2) 28.dp else 20.dp),
                        )
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = remember(block.text, fileClick) { parseInlineMarkdown(block.text, fileClick) },
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                color = textColor,
                            )
                        }
                    }
                }

                is MdBlock.Paragraph -> SelectionContainer {
                    Text(
                        text = remember(block.text, fileClick) { parseInlineMarkdown(block.text, fileClick) },
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = textColor,
                    )
                }

                is MdBlock.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = HudColors.BorderStrong,
                                start = Offset(1.dp.toPx(), 0f),
                                end = Offset(1.dp.toPx(), size.height),
                                strokeWidth = 3.dp.toPx(),
                            )
                        }
                        .padding(start = 14.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = remember(block.text, fileClick) { parseInlineMarkdown(block.text, fileClick) },
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = HudColors.TextSecondary,
                        )
                    }
                }

                is MdBlock.Table -> MarkdownTable(block, fontSize, fileClick)

                MdBlock.Divider -> HudDivider(Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MdBlock.Table,
    fontSize: TextUnit,
    fileClick: ((String, String) -> Unit)?,
) {
    val shape = RoundedCornerShape(12.dp)
    val cellSize = fontSize * 0.88f
    // One width per column (from its longest cell) so every row lines up.
    val columnWidths = remember(table) {
        val all = listOf(table.header) + table.rows
        table.header.indices.map { c ->
            val longest = all.maxOf { it.getOrNull(c)?.length ?: 0 }
            (longest * 7.6f + 24f).coerceIn(72f, 240f).dp
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, HudColors.BorderStrong, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            val all = listOf(table.header) + table.rows
            all.forEachIndexed { r, cells ->
                if (r > 0) HudDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (r == 0) HudColors.SurfaceElevated else Color.Transparent),
                ) {
                    cells.forEachIndexed { c, cell ->
                        Text(
                            text = remember(cell, fileClick) { parseInlineMarkdown(cell, fileClick) },
                            fontSize = cellSize,
                            lineHeight = cellSize * 1.45f,
                            fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (r == 0) HudColors.TextPrimary else HudColors.ChatText,
                            modifier = Modifier
                                .width(columnWidths.getOrElse(c) { 120.dp })
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
    val quoteBuf = ArrayList<String>()
    val tableBuf = ArrayList<String>()
    var inFence = false
    var fenceLang = ""
    val fenceBuf = ArrayList<String>()

    fun flushPara() {
        if (paraBuf.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraBuf.joinToString("\n")))
            paraBuf.clear()
        }
    }
    fun flushQuote() {
        if (quoteBuf.isNotEmpty()) {
            blocks.add(MdBlock.Quote(quoteBuf.joinToString("\n")))
            quoteBuf.clear()
        }
    }
    fun flushTable() {
        if (tableBuf.isEmpty()) return
        val rows = tableBuf.map { splitRow(it) }
        val isTable = rows.size >= 2 && rows[1].all { TABLE_RULE.matches(it) }
        if (isTable) {
            val width = rows[0].size
            blocks.add(
                MdBlock.Table(
                    header = rows[0],
                    rows = rows.drop(2).map { r -> (r + List(width) { "" }).take(width) },
                ),
            )
        } else {
            paraBuf.addAll(tableBuf)
            flushPara()
        }
        tableBuf.clear()
    }
    fun flushAll() {
        flushPara()
        flushQuote()
        flushTable()
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            if (!inFence) {
                flushAll()
                inFence = true
                fenceLang = trimmed.drop(3).trim()
                fenceBuf.clear()
            } else {
                blocks.add(MdBlock.CodeFence(fenceLang, fenceBuf.joinToString("\n").trimIndent()))
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
        if (trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2) {
            flushPara()
            flushQuote()
            tableBuf.add(trimmed)
            continue
        } else {
            flushTable()
        }
        if (trimmed.startsWith(">")) {
            flushPara()
            quoteBuf.add(trimmed.removePrefix(">").trim())
            continue
        } else {
            flushQuote()
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
            blocks.add(MdBlock.Bullet(if (indent == 0) "•" else "◦", bulletMatch.groupValues[2].trim(), indent))
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
    flushAll()
    return blocks
}

private fun splitRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

private val TABLE_RULE = Regex("^:?-{2,}:?$")

private fun parseInlineMarkdown(
    text: String,
    onFileClick: ((label: String, fileUri: String) -> Unit)? = null,
): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val len = text.length
        while (idx < len) {
            // Markdown link: [label](url) -> clickable file preview pill or web link
            if (text[idx] == '[') {
                val closeBracket = text.indexOf(']', idx + 1)
                if (closeBracket > idx && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 1) {
                        val label = text.substring(idx + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        val isFile = Transcript.isFileUri(url)
                        if (isFile && onFileClick != null) {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "file",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = HudColors.TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = { onFileClick(label, url) },
                                ),
                            ) {
                                append(label)
                            }
                        } else if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                            withLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = HudColors.BlueAccent,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                ),
                            ) {
                                append(label)
                            }
                        } else {
                            withStyle(SpanStyle(color = HudColors.TextPrimary, fontWeight = FontWeight.Medium)) {
                                append(label)
                            }
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
                            fontSize = 0.88.em,
                            color = Color(0xFFE6E6EA),
                            background = HudColors.SurfaceMuted,
                        ),
                    ) {
                        append("\u2009$code\u2009")
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
                        append(parseInlineMarkdown(text.substring(idx + 2, end), onFileClick))
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
