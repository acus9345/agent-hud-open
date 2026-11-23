package org.agenthud.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.AgentMessageRecord
import org.agenthud.android.data.AppUiState
import org.agenthud.android.data.ApprovalItem
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.SessionSummary
import org.agenthud.android.data.UsageSummary
import org.agenthud.android.ui.theme.HudColors
import org.agenthud.android.ui.theme.HudType

private enum class SessionsFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    MESSAGES("Messages"),
}

/**
 * Last path segment of a project folder, with Windows verbatim prefixes (`\\?\`, `\\?\UNC\`)
 * stripped first.
 */
internal fun displayProjectName(raw: String?): String? = raw
    ?.trim()
    ?.removePrefix("""\\?\UNC\""")
    ?.removePrefix("""\\?\""")
    ?.trimEnd('/', '\\')
    ?.let { p ->
        if (p.contains('\\') || p.contains('/')) {
            p.substringAfterLast('\\').substringAfterLast('/').ifBlank { p }
        } else {
            p
        }
    }
    ?.ifBlank { null }

/** Picks the identifier that resolves to a real provider mark (e.g. Codex sessions via `client`). */
internal fun sessionIconKey(session: SessionSummary): String =
    listOfNotNull(session.client, session.vendorName, session.source)
        .firstOrNull { it.isNotBlank() && providerIconRes(it) != null }
        ?: listOfNotNull(session.vendorName, session.source, session.client)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

@Composable
fun SessionsScreen(
    state: AppUiState,
    repository: AgentHudRepository,
    onOpenSession: (SessionSummary) -> Unit,
    onOpenNewSession: () -> Unit,
    onStartVoiceInput: ((String) -> Unit) -> Unit,
) {
    var filter by remember { mutableStateOf(SessionsFilter.ALL) }
    val scope = rememberCoroutineScope()
    val approvals = state.pendingApprovals
    val allSessions = state.sessions
    val displayedSessions = remember(allSessions, filter) {
        when (filter) {
            SessionsFilter.ACTIVE -> allSessions.filter { it.state == "running" || it.state == "waiting" }
            else -> allSessions
        }
    }
    val agentMessages = state.companionState?.agentMessages.orEmpty()
    val canStartNew = state.companionState?.targets?.starts?.isNotEmpty() == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = LocalDockInset.current),
    ) {
        section(key = "sessions-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${state.runningSessionCount} running · ${allSessions.size} total",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            style = HudType.Tabular,
                            color = HudColors.TextSecondary,
                        )
                        Text("Sessions", style = MaterialTheme.typography.headlineLarge)
                    }
                    if (canStartNew) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(HudColors.BlueAccent)
                                .bounceClick(scaleDown = 0.9f, onClick = onOpenNewSession),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "New session",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
                SlidingSegmentedSelector(
                    options = SessionsFilter.entries,
                    selected = filter,
                    onSelect = { filter = it },
                    labelFor = { it.label },
                    equalWidth = true,
                    itemVerticalPadding = 7.dp,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (!state.companionSupported) {
            section(key = "unsupported") {
                EmptySessionsCard(
                    title = "Desktop update required",
                    body = "Rebuild or update Agent HUD on your computer to enable remote sessions and approvals.",
                )
            }
        }

        if (filter != SessionsFilter.MESSAGES) {
            section(key = "today-summary") {
                TodaySummaryCard(todayUsage = state.todayUsage, sessionCount = allSessions.size)
            }
        }

        if (approvals.isNotEmpty()) {
            items(items = approvals, key = { "approval:${it.id}" }) { approval ->
                Box(Modifier.padding(bottom = SectionGap)) {
                    ApprovalCard(
                    approval = approval,
                    busy = approval.id in state.answeringApprovalIds,
                    onSubmitDecision = { decision ->
                        scope.launch { repository.answerApproval(approval.id, decision) }
                    },
                    onStartVoiceInput = onStartVoiceInput,
                    )
                }
            }
        }

        if (filter == SessionsFilter.MESSAGES) {
            if (agentMessages.isEmpty()) {
                section(key = "no-msgs") {
                    EmptySessionsCard(
                        title = "No messages between agents",
                        body = "Messages your agents send each other on the desktop show up here, newest last.",
                    )
                }
            } else {
                groupedItems(agentMessages, key = { "msg:${it.id}" }) { msg -> AgentMessageRow(msg) }
            }
        } else if (displayedSessions.isEmpty()) {
            section(key = "no-sessions") {
                EmptySessionsCard(
                    title = if (filter == SessionsFilter.ACTIVE) "Nothing running" else "No recent sessions",
                    body = "Start a session on your computer, or tap + to start one from here.",
                )
            }
        } else {
            groupedItems(displayedSessions, key = { "session:${it.id}" }) { session ->
                SessionListRow(session = session, onClick = { onOpenSession(session) })
            }
        }
    }
}

private val SectionGap = 14.dp

/** A standalone block followed by the standard gap (the list itself has no item spacing). */
private fun LazyListScope.section(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        Box(Modifier.padding(bottom = SectionGap)) { content() }
    }
}

/**
 * Emits one lazy item per row, drawn together as a single grouped card, so long lists stay lazy.
 */
private fun <T> LazyListScope.groupedItems(
    rows: List<T>,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    itemsIndexed(items = rows, key = { _, r -> key(r) }) { idx, r ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .groupedListItem(idx, rows.size),
        ) {
            if (idx > 0) HudDivider(Modifier.padding(start = 16.dp))
            row(r)
        }
    }
}

@Composable
private fun TodaySummaryCard(
    todayUsage: UsageSummary,
    sessionCount: Int,
) {
    val shown = rememberAnimatedLong(todayUsage.totalTokens)
    HudCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Today · $sessionCount session${if (sessionCount == 1) "" else "s"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = HudColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (todayUsage.estimatedCostUsd > 0.0) {
                    Text(
                        text = "≈${QuotaFormatting.formatMoney(todayUsage.estimatedCostUsd)}",
                        style = HudType.numeric(13.sp, FontWeight.Medium, HudColors.TextSecondary),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = QuotaFormatting.formatTokens(shown), style = HudType.Display)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "tokens",
                    fontSize = 14.sp,
                    color = HudColors.TextMuted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            val kinds = listOf(
                Triple("Cache write", todayUsage.cacheWriteTokens, HudColors.KindCacheWrite),
                Triple("Input", todayUsage.inputTokens, HudColors.KindInput),
                Triple("Reasoning", todayUsage.reasoningTokens, HudColors.KindReasoning),
                Triple("Output", todayUsage.outputTokens, HudColors.KindOutput),
                Triple("Cache read", todayUsage.cacheReadTokens, HudColors.KindCacheRead),
            )
            if (todayUsage.totalTokens > 0L) {
                SegmentedShareBar(
                    segments = kinds.filter { it.second > 0L }.map { it.second.toFloat() to it.third },
                    height = 5.dp,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                kinds.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        pair.forEach { (label, tokens, color) ->
                            KindStatCell(label, tokens, color, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun KindStatCell(label: String, tokens: Long, dot: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 12.sp, color = HudColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
        Text(QuotaFormatting.formatTokens(tokens), style = HudType.numeric(12.sp, FontWeight.Medium))
    }
}

@Composable
private fun SessionListRow(
    session: SessionSummary,
    onClick: () -> Unit,
) {
    val live = session.state == "running" || session.state == "waiting"
    val stateColor = when (session.state) {
        "waiting" -> HudColors.Terracotta
        "running" -> HudColors.SageBright
        else -> HudColors.TextMuted
    }
    val clientRaw = session.vendorName?.ifBlank { null }
        ?: session.client?.ifBlank { null }
        ?: session.source.ifBlank { null }
        ?: "Agent"
    val clientLabel = QuotaFormatting.providerDisplayName(clientRaw)
    val relTime = QuotaFormatting.formatRelativeTime(session.updatedAt ?: session.lastMessageAt)
    val cost = session.pricedCost ?: session.cost
    val project = displayProjectName(session.project)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.985f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.title?.ifBlank { null } ?: project ?: "Untitled session",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (session.tokens > 0L) {
                Spacer(Modifier.width(10.dp))
                Text(QuotaFormatting.formatTokens(session.tokens), style = HudType.Value)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProviderIcon(providerId = sessionIconKey(session), size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = buildList {
                    add(clientLabel)
                    (session.modelName ?: session.model)?.takeIf { it.isNotBlank() }?.let { add(it) }
                    project?.let { add(it) }
                }.joinToString(" · "),
                fontSize = 12.5.sp,
                color = HudColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (cost != null && cost > 0.0) {
                Spacer(Modifier.width(8.dp))
                Text("≈${QuotaFormatting.formatMoney(cost)}", style = HudType.Caption.copy(color = HudColors.TextMuted))
            }
        }

        val liveMessage = session.liveFacts?.message
        if (!liveMessage.isNullOrBlank()) {
            Text(
                text = liveMessage,
                fontSize = 12.5.sp,
                color = HudColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                BreathingDot(color = stateColor, size = 6.dp, pulsing = true)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = when (session.state) {
                    "waiting" -> "Needs approval"
                    "running" -> "Running"
                    else -> "Idle"
                },
                fontSize = 11.5.sp,
                fontWeight = if (live) FontWeight.Medium else FontWeight.Normal,
                color = stateColor,
                modifier = Modifier.weight(1f),
            )
            if (relTime.isNotBlank()) {
                Text(relTime, style = HudType.Caption.copy(color = HudColors.TextMuted))
            }
        }
    }
}

/** Pill-shaped action with the shared press response. */
@Composable
internal fun PillButton(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(container)
            .then(if (outlined) Modifier.border(1.dp, HudColors.BorderStrong, shape) else Modifier)
            .bounceClick(scaleDown = 0.96f, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

@Composable
private fun OptionPill(label: String, picked: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (picked) HudColors.BlueSoft else Color.Transparent, HudMotion.tint(), label = "optBg")
    val border by animateColorAsState(
        if (picked) HudColors.BlueAccent else HudColors.BorderStrong,
        HudMotion.tint(),
        label = "optBorder",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (picked) HudColors.TextPrimary else HudColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApprovalCard(
    approval: ApprovalItem,
    busy: Boolean,
    onSubmitDecision: (kotlinx.serialization.json.JsonObject) -> Unit,
    onStartVoiceInput: ((String) -> Unit) -> Unit,
) {
    val kind = approval.kind ?: "permission"
    var noteText by remember(approval.id) { mutableStateOf("") }
    val selectedAnswers = remember(approval.id) { mutableStateMapOf<String, MutableList<String>>() }
    val customAnswers = remember(approval.id) { mutableStateMapOf<String, String>() }
    val fieldShape = RoundedCornerShape(14.dp)

    HudCard(
        modifier = Modifier.fillMaxWidth(),
        color = HudColors.SurfaceElevated,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val toolLabel = approval.badge ?: approval.toolName.ifBlank { kind.replaceFirstChar { it.uppercase() } }
                Icon(
                    imageVector = toolIcon(approval.toolName),
                    contentDescription = null,
                    tint = HudColors.Terracotta,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = toolLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudColors.TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = listOfNotNull(
                        approval.source.takeIf { it.isNotBlank() }?.let { QuotaFormatting.providerDisplayName(it) },
                        displayProjectName(approval.cwd),
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = HudColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val age = QuotaFormatting.formatRelativeTime(approval.createdAt)
                if (age.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(age, style = HudType.Caption.copy(color = HudColors.TextMuted))
                }
            }

            Text(
                text = approval.title.ifBlank { approval.toolName.ifBlank { "Approval requested" } },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = HudColors.TextPrimary,
            )

            // Claude Code shows the exact change it wants to make: a diff for edits, else the command.
            val removed = approval.removed
            val added = approval.added
            val diffPreview = if (removed != null || added != null) {
                org.agenthud.android.data.Transcript.diffOf(removed.orEmpty(), added.orEmpty())
            } else {
                null
            }
            val preview = diffPreview ?: (approval.detail ?: approval.input).ifBlank { approval.summary.orEmpty() }
            if (preview.isNotBlank() && preview != approval.title) {
                CodeOutput(
                    text = preview,
                    label = approval.path?.let { org.agenthud.android.data.Transcript.fileName(it) }
                        ?: approval.toolName.ifBlank { kind }.lowercase(),
                    diff = diffPreview != null,
                    maxHeight = 220.dp,
                )
            }

            when (kind) {
                "question" -> {
                    approval.questions.forEach { q ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(q.question, style = MaterialTheme.typography.bodyMedium)
                            if (q.options.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    q.options.forEach { opt ->
                                        val picked = opt.label in selectedAnswers[q.answerKey].orEmpty()
                                        OptionPill(label = opt.label, picked = picked) {
                                            val current = selectedAnswers.getOrPut(q.answerKey) { mutableListOf() }
                                            if (q.multiSelect) {
                                                if (picked) current.remove(opt.label) else current.add(opt.label)
                                            } else {
                                                current.clear()
                                                current.add(opt.label)
                                            }
                                            selectedAnswers[q.answerKey] = current.toMutableList()
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = customAnswers[q.answerKey].orEmpty(),
                                onValueChange = { customAnswers[q.answerKey] = it },
                                placeholder = { Text("Type or dictate an answer…", color = HudColors.TextMuted) },
                                singleLine = true,
                                shape = fieldShape,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        onStartVoiceInput { spoken -> customAnswers[q.answerKey] = spoken }
                                    }) {
                                        Icon(Icons.Outlined.Mic, contentDescription = "Dictate", tint = HudColors.TextSecondary)
                                    }
                                },
                                colors = hudTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    PillButton(
                        text = if (busy) "Sending…" else "Submit answer",
                        container = HudColors.BlueAccent,
                        content = Color.White,
                        enabled = !busy && approval.answersQuestion,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val decision = buildJsonObject {
                            put("kind", "answer")
                            put(
                                "answers",
                                buildJsonObject {
                                    approval.questions.forEach { q ->
                                        val custom = customAnswers[q.answerKey]?.trim().orEmpty()
                                        val chosen = selectedAnswers[q.answerKey].orEmpty()
                                        if (chosen.isNotEmpty() || custom.isNotBlank()) {
                                            put(
                                                q.answerKey,
                                                buildJsonObject {
                                                    put("selected", buildJsonArray { chosen.forEach { add(it) } })
                                                    if (custom.isNotBlank()) put("custom", custom)
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        onSubmitDecision(decision)
                    }
                }

                "plan" -> {
                    if (!approval.plan.isNullOrBlank()) {
                        MarkdownText(
                            markdown = approval.plan.take(1600),
                            fontSize = 14.5.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(
                            text = "Keep planning",
                            container = Color.Transparent,
                            content = HudColors.TextPrimary,
                            outlined = true,
                            enabled = !busy && approval.answersPlan,
                            modifier = Modifier.weight(1f),
                        ) {
                            onSubmitDecision(
                                buildJsonObject {
                                    put("kind", "keep_planning")
                                    if (noteText.isNotBlank()) put("message", noteText.trim())
                                },
                            )
                        }
                        PillButton(
                            text = "Approve plan",
                            container = HudColors.BlueAccent,
                            content = Color.White,
                            enabled = !busy && approval.answersPlan,
                            modifier = Modifier.weight(1f),
                        ) {
                            onSubmitDecision(
                                buildJsonObject {
                                    put("kind", "approve_plan")
                                    put("acceptEdits", true)
                                },
                            )
                        }
                    }
                }

                else -> {
                    if (approval.deniesWithReply) {
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Optional reply when denying…", color = HudColors.TextMuted) },
                            singleLine = true,
                            shape = fieldShape,
                            colors = hudTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(
                            text = "Deny",
                            container = HudColors.TerracottaSoft,
                            content = HudColors.Terracotta,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            onSubmitDecision(
                                buildJsonObject {
                                    put("kind", "deny")
                                    if (noteText.isNotBlank()) put("message", noteText.trim())
                                },
                            )
                        }
                        if (approval.allowsAlways) {
                            PillButton(
                                text = "Always",
                                container = Color.Transparent,
                                content = HudColors.TextPrimary,
                                outlined = true,
                                enabled = !busy,
                                modifier = Modifier.weight(0.9f),
                            ) {
                                onSubmitDecision(buildJsonObject { put("kind", "always") })
                            }
                        }
                        PillButton(
                            text = approval.allowLabel?.ifBlank { null } ?: "Allow once",
                            container = HudColors.BlueAccent,
                            content = Color.White,
                            enabled = !busy,
                            modifier = Modifier.weight(1.1f),
                        ) {
                            onSubmitDecision(buildJsonObject { put("kind", "allow") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentMessageRow(msg: AgentMessageRecord) {
    val fromLabel = msg.from.title?.ifBlank { null } ?: msg.from.client.ifBlank { "Agent" }
    val toLabel = msg.to.title?.ifBlank { null } ?: msg.to.client.ifBlank { "Agent" }
    val stateColor = when (msg.state) {
        "refused", "failed" -> HudColors.Terracotta
        "delivered", "sent", "replied" -> HudColors.SageBright
        else -> HudColors.TextMuted
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProviderIcon(providerId = msg.from.client, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = fromLabel,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "to",
                tint = HudColors.TextMuted,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(12.dp),
            )
            Text(
                text = toLabel,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(msg.state.ifBlank { msg.kind }, fontSize = 11.5.sp, color = stateColor)
        }
        val body = msg.text ?: msg.reason
        if (!body.isNullOrBlank()) {
            MarkdownText(markdown = body.take(600), fontSize = 14.sp, lineHeight = 21.sp)
        }
        if (msg.sentAtMs > 0L) {
            Text(QuotaFormatting.formatRelativeMs(msg.sentAtMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptySessionsCard(title: String, body: String) {
    HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
