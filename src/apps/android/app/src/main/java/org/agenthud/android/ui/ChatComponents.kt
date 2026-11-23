package org.agenthud.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.SessionMessageItem
import org.agenthud.android.data.SessionToolCall
import org.agenthud.android.data.Transcript
import org.agenthud.android.ui.theme.HudColors
import org.agenthud.android.ui.theme.HudMono

/** Prose size for chat: larger and airier than list text, like the Claude app. */
internal val ChatProseSize = 16.sp
internal val ChatProseLineHeight = 25.sp

/** Longest expanded tool output rendered inline by default; tap expands the rest. */
private const val MAX_OUTPUT_LINES = 1200
private const val MAX_OUTPUT_CHARS = 200_000

// ---- Transcript blocks ------------------------------------------------------------------------

@Composable
internal fun UserBubble(
    message: SessionMessageItem,
    onFileClick: (String, String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(Alignment.End)
                .clip(RoundedCornerShape(20.dp))
                .background(HudColors.UserBubble)
                .padding(horizontal = 15.dp, vertical = 10.dp),
        ) {
            MarkdownText(
                markdown = message.text.orEmpty().trim(),
                textColor = HudColors.ChatText,
                onFileClick = onFileClick,
            )
        }
    }
}

@Composable
internal fun AssistantProse(
    message: SessionMessageItem,
    endsTurn: Boolean,
    providerKey: String,
    onFileClick: (String, String) -> Unit,
) {
    val content = message.text.orEmpty().trim()
    val files = remember(content) { Transcript.fileLinks(content) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MarkdownText(markdown = content, textColor = HudColors.ChatText, onFileClick = onFileClick)
        if (files.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                files.take(20).forEach { link ->
                    FileArtifactCard(label = link.label, uri = link.uri, onOpen = { onFileClick(link.label, link.uri) })
                }
            }
        }
        if (endsTurn) {
            MessageActions(text = content, providerKey = providerKey, timestamp = message.timestamp)
        }
    }
}

/** Copy action + provider mark + time, under the last reply of a turn. */
@Composable
private fun MessageActions(text: String, providerKey: String, timestamp: String?) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProviderIcon(providerId = providerKey, size = 16.dp)
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .bounceClick(scaleDown = 0.85f) {
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = "Copy reply",
                tint = if (copied) HudColors.SageBright else HudColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        val ts = QuotaFormatting.formatRelativeTime(timestamp)
        if (ts.isNotBlank()) Text(ts, fontSize = 12.sp, color = HudColors.TextMuted)
    }
}

/** "Thought process" disclosure, collapsed by default. */
@Composable
internal fun ThinkingDisclosure(
    message: SessionMessageItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DisclosureHeader(
            icon = Icons.Outlined.Lightbulb,
            text = "Thought process",
            expanded = expanded,
            onToggle = onToggle,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 1f, stiffness = 600f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 600f)) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp, start = 7.dp)
                    .drawBehind {
                        drawLine(
                            color = HudColors.BorderStrong,
                            start = Offset(1.dp.toPx(), 0f),
                            end = Offset(1.dp.toPx(), size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    .padding(start = 16.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = message.text.orEmpty().trim(),
                        fontSize = 14.5.sp,
                        lineHeight = 22.sp,
                        color = HudColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * A run of tool work. One step shows as its own row; several fold under a one-line summary
 * ("Read 3 files, ran a command") that opens onto a timeline of the steps.
 */
@Composable
internal fun StepsGroup(
    block: Transcript.Steps,
    live: Boolean,
    expanded: SnapshotStateMap<String, Boolean>,
) {
    val single = block.steps.singleOrNull()
    if (single != null) {
        StepRow(step = single, expanded = expanded, live = live, rail = false)
        return
    }
    val open = expanded[block.key] == true
    Column(modifier = Modifier.fillMaxWidth()) {
        DisclosureHeader(
            icon = if (block.anyFailed) Icons.Outlined.Info else Icons.Outlined.Build,
            iconTint = if (block.anyFailed) HudColors.Terracotta else HudColors.TextSecondary,
            text = remember(block.steps) { Transcript.groupSummary(block.steps) },
            expanded = open,
            live = live,
            onToggle = { expanded[block.key] = !open },
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(dampingRatio = 1f, stiffness = 600f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 600f)) + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                block.steps.forEachIndexed { idx, step ->
                    StepRow(
                        step = step,
                        expanded = expanded,
                        live = live && idx == block.steps.lastIndex,
                        rail = true,
                        lastInRail = idx == block.steps.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    step: Transcript.Step,
    expanded: SnapshotStateMap<String, Boolean>,
    live: Boolean,
    rail: Boolean,
    lastInRail: Boolean = true,
) {
    val open = expanded[step.id] == true
    val (icon, verb, target, code, failed, detail) = when (step) {
        is Transcript.ToolStep -> {
            val s = step.summary
            StepView(glyphIcon(s.glyph), s.verb, s.target, s.codeTarget, step.failed, remember(step) { Transcript.detailText(step) })
        }
        is Transcript.NoteStep -> StepView(
            Icons.Outlined.Info,
            "System note",
            null,
            false,
            false,
            step.message.text.orEmpty().trim(),
        )
    }
    val diff = step is Transcript.ToolStep && step.summary.glyph.let { it == Transcript.Glyph.EDIT || it == Transcript.Glyph.WRITE }
    val iconTint = if (failed) HudColors.Terracotta else HudColors.TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (rail && !lastInRail) {
                    // Timeline rail: a hairline from this step's icon down to the next one.
                    Modifier.drawBehind {
                        val x = 12.dp.toPx()
                        drawLine(
                            color = HudColors.BorderStrong,
                            start = Offset(x, 28.dp.toPx()),
                            end = Offset(x, size.height + 6.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (rail) {
            Box(modifier = Modifier.width(24.dp).padding(top = 9.dp), contentAlignment = Alignment.TopCenter) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .bounceClick(scaleDown = 0.985f, enabled = detail.isNotBlank()) { expanded[step.id] = !open }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!rail) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = verb,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = HudColors.TextSecondary,
                    maxLines = 1,
                )
                if (!target.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = target,
                        fontSize = if (code) 13.sp else 14.sp,
                        fontFamily = if (code) HudMono else null,
                        color = HudColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (failed) {
                    Spacer(Modifier.width(6.dp))
                    Text("failed", fontSize = 12.sp, color = HudColors.Terracotta)
                }
                if (live && step is Transcript.ToolStep && step.result == null) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = HudColors.TextSecondary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                if (detail.isNotBlank()) Chevron(open)
            }
            AnimatedVisibility(
                visible = open && detail.isNotBlank(),
                enter = expandVertically(spring(dampingRatio = 1f, stiffness = 600f)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 600f)) + fadeOut(),
            ) {
                CodeOutput(
                    text = detail,
                    label = when (step) {
                        is Transcript.ToolStep -> step.name.substringAfterLast("__").ifBlank { "output" }.lowercase()
                        is Transcript.NoteStep -> "context"
                    },
                    diff = diff,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
            }
        }
    }
}

private data class StepView(
    val icon: ImageVector,
    val verb: String,
    val target: String?,
    val code: Boolean,
    val failed: Boolean,
    val detail: String,
)

private fun glyphIcon(g: Transcript.Glyph): ImageVector = when (g) {
    Transcript.Glyph.READ -> Icons.Outlined.Description
    Transcript.Glyph.EDIT -> Icons.Outlined.EditNote
    Transcript.Glyph.WRITE -> Icons.AutoMirrored.Outlined.NoteAdd
    Transcript.Glyph.SEARCH -> Icons.Outlined.Search
    Transcript.Glyph.LIST -> Icons.Outlined.FolderOpen
    Transcript.Glyph.SHELL -> Icons.Outlined.Terminal
    Transcript.Glyph.WEB -> Icons.Outlined.Language
    Transcript.Glyph.AGENT -> Icons.Outlined.AccountTree
    Transcript.Glyph.TODO -> Icons.Outlined.Checklist
    Transcript.Glyph.SKILL -> Icons.Outlined.Extension
    Transcript.Glyph.TOOL -> Icons.Outlined.Build
    Transcript.Glyph.NOTE -> Icons.Outlined.Info
}

internal fun toolIcon(toolName: String): ImageVector = glyphIcon(
    Transcript.ToolStep(
        id = "",
        call = SessionMessageItem(toolCall = SessionToolCall(name = toolName)),
        result = null,
    ).summary.glyph,
)

@Composable
private fun DisclosureHeader(
    icon: ImageVector,
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    iconTint: Color = HudColors.TextSecondary,
    live: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .bounceClick(scaleDown = 0.985f, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = HudColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (live) {
            Spacer(Modifier.width(8.dp))
            BreathingDot(color = HudColors.TextSecondary, size = 6.dp)
        }
        Spacer(Modifier.width(4.dp))
        Chevron(expanded)
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    val angle by animateFloatAsState(if (expanded) 90f else 0f, HudMotion.slide(), label = "chevron")
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = HudColors.TextMuted,
        modifier = Modifier
            .size(18.dp)
            .rotate(angle),
    )
}

// ---- Code wells --------------------------------------------------------------------------------

/**
 * Recessed monospace well used for code fences, tool output and approval previews: a header
 * with a label and Copy, unwrapped lines with horizontal scroll, optional +/- diff tinting.
 */
@Composable
internal fun CodeOutput(
    text: String,
    label: String,
    modifier: Modifier = Modifier,
    diff: Boolean = false,
    maxHeight: Dp = 420.dp,
    fontSize: Float = 12f,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showAllLines by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    val (shown, hiddenLines) = remember(text, showAllLines) {
        val capped = text.take(MAX_OUTPUT_CHARS)
        val lines = capped.lines()
        val visible = if (showAllLines) lines else lines.take(MAX_OUTPUT_LINES)
        val hidden = Transcript.lineCount(text) - visible.size
        visible to hidden.coerceAtLeast(0)
    }
    val tintDiff = diff || remember(shown) { looksLikeDiff(shown) }
    val shownText = remember(shown) { shown.joinToString("\n") }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HudColors.CodeSurface)
            .border(1.dp, HudColors.Border, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontFamily = HudMono,
                color = HudColors.TextMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .bounceClick(scaleDown = 0.85f) {
                        // Very large strings overflow the binder; copy what is shown.
                        runCatching { clipboard.setText(AnnotatedString(text.take(MAX_OUTPUT_CHARS))) }
                        copied = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (copied) HudColors.SageBright else HudColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
        ) {
            SelectionContainer {
                if (tintDiff) {
                    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                        shown.forEach { line ->
                            val (bg, fg) = when {
                                line.startsWith("+") && !line.startsWith("+++") -> HudColors.DiffAddBg to HudColors.DiffAddFg
                                line.startsWith("-") && !line.startsWith("---") -> HudColors.DiffDelBg to HudColors.DiffDelFg
                                else -> Color.Transparent to HudColors.TextSecondary
                            }
                            Text(
                                text = line.ifEmpty { " " },
                                style = monoStyle(fontSize, fg),
                                softWrap = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 12.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = shownText,
                        style = monoStyle(fontSize, HudColors.TextSecondary),
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
        if (hiddenLines > 0) {
            Text(
                text = "$hiddenLines more lines · Tap to show all",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = HudColors.TextSecondary,
                modifier = Modifier
                    .bounceClick(scaleDown = 0.96f) { showAllLines = true }
                    .padding(start = 12.dp, bottom = 10.dp, end = 12.dp),
            )
        }
    }
}

private fun monoStyle(size: Float, color: Color) = TextStyle(
    fontFamily = HudMono,
    fontSize = size.sp,
    lineHeight = (size * 1.5f).sp,
    color = color,
)

private fun looksLikeDiff(lines: List<String>): Boolean =
    lines.any { it.startsWith("@@") } ||
        (lines.count { it.startsWith("+") || it.startsWith("-") } >= lines.size / 2 && lines.size >= 2 &&
            lines.any { it.startsWith("+") } && lines.any { it.startsWith("-") })

// ---- Files ------------------------------------------------------------------------------------

/** Claude-style artifact card for a local file the agent linked. */
@Composable
internal fun FileArtifactCard(
    label: String,
    uri: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = Transcript.fileName(label.ifBlank { uri })
    val ext = Transcript.fileExtension(Transcript.fileName(uri)).ifBlank { Transcript.fileExtension(name) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HudColors.Surface)
            .border(1.dp, HudColors.Border, shape)
            .bounceClick(scaleDown = 0.98f, onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTile(ext = ext)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(fileKind(ext), "Tap to open").joinToString(" · "),
                fontSize = 12.sp,
                color = HudColors.TextMuted,
                maxLines = 1,
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = HudColors.TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun FileTile(ext: String, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(HudColors.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (ext.isNotBlank() && ext.length <= 4) {
            Text(
                text = ext.uppercase(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = HudMono,
                color = HudColors.TextSecondary,
            )
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = HudColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun fileKind(ext: String): String? = when (ext) {
    "kt", "kts" -> "Kotlin"
    "rs" -> "Rust"
    "ts", "tsx" -> "TypeScript"
    "js", "jsx", "mjs" -> "JavaScript"
    "py" -> "Python"
    "md" -> "Markdown"
    "json" -> "JSON"
    "toml", "yaml", "yml" -> "Config"
    "png", "jpg", "jpeg", "gif", "webp" -> "Image"
    "pdf" -> "PDF"
    "txt", "log" -> "Text"
    "html", "css" -> "Web"
    "" -> null
    else -> ext.uppercase()
}

// ---- Composer ---------------------------------------------------------------------------------

/**
 * Claude-app composer: a rounded field with the text on top and a tool row beneath
 * (attach on the left; dictation and the round send button on the right).
 */
@Composable
internal fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (PendingAttachment) -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    enabled: Boolean = true,
    showSendNow: Boolean = false,
    onSendNow: (() -> Unit)? = null,
) {
    val canSend = enabled && !sending && (value.isNotBlank() || attachments.isNotEmpty())
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HudColors.SurfaceElevated)
            .border(1.dp, HudColors.Border, shape)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (attachments.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                items(attachments.distinctBy { it.uri }, key = { it.uri.toString() }) { att ->
                    AttachmentChip(attachment = att, onRemove = { onRemoveAttachment(att) })
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 16.sp,
                    color = HudColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = TextStyle(color = HudColors.ChatText, fontSize = 16.sp, lineHeight = 23.sp),
                cursorBrush = SolidColor(HudColors.TextPrimary),
                minLines = minLines,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerIconButton(
                icon = Icons.Outlined.Add,
                description = "Attach file or photo",
                outlined = true,
                enabled = enabled && !sending,
                onClick = onAttach,
            )
            Spacer(Modifier.weight(1f))
            ComposerIconButton(
                icon = Icons.Outlined.Mic,
                description = "Dictate",
                enabled = enabled && !sending,
                onClick = onVoice,
            )
            if (showSendNow && onSendNow != null && canSend) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(HudColors.SurfaceMuted)
                        .border(1.dp, HudColors.BorderStrong, RoundedCornerShape(999.dp))
                        .bounceClick(scaleDown = 0.92f, enabled = canSend, onClick = onSendNow)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Send now",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HudColors.TextPrimary,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            val sendBg by animateColorAsState(
                if (canSend || sending) HudColors.TextPrimary else HudColors.SurfaceMuted,
                HudMotion.tint(),
                label = "sendBg",
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(sendBg)
                    .bounceClick(scaleDown = 0.88f, enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (canSend) Color.Black else HudColors.TextMuted,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    outlined: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(if (outlined) Modifier.border(1.dp, HudColors.BorderStrong, CircleShape) else Modifier)
            .bounceClick(scaleDown = 0.88f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) HudColors.TextSecondary else HudColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
