package org.agenthud.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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

private enum class SessionsFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    MESSAGES("Messages"),
}

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header + compact filter pill + New button
        item(key = "sessions-header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${state.runningSessionCount} active · ${allSessions.size}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text("Sessions", style = MaterialTheme.typography.headlineMedium)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = HudColors.Surface,
                            border = BorderStroke(1.dp, HudColors.Border),
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                SessionsFilter.entries.forEach { tab ->
                                    val selected = tab == filter
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (selected) HudColors.SurfaceMuted else Color.Transparent)
                                            .clickable { filter = tab }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = tab.label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (selected) HudColors.TextPrimary else HudColors.TextMuted,
                                        )
                                    }
                                }
                            }
                        }

                        if (canStartNew) {
                            Surface(
                                shape = CircleShape,
                                color = HudColors.BlueAccent,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable(onClick = onOpenNewSession),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "New session",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!state.companionSupported) {
            item(key = "unsupported") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = HudColors.Surface,
                    border = BorderStroke(1.dp, HudColors.BorderStrong),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Desktop update required", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Rebuild or update Agent HUD on your computer to enable remote sessions and approvals.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // Today hero summary card (matching iOS Sessions!)
        if (filter != SessionsFilter.MESSAGES) {
            item(key = "today-summary") {
                TodaySessionsSummaryCard(
                    todayUsage = state.todayUsage,
                    sessionCount = allSessions.size,
                )
            }
        }

        // Pending approvals
        if (approvals.isNotEmpty()) {
            items(items = approvals, key = { "approval:${it.id}" }) { approval ->
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

        // Sessions or Agent Messages list
        if (filter == SessionsFilter.MESSAGES) {
            if (agentMessages.isEmpty()) {
                item(key = "no-msgs") {
                    EmptySessionsCard(
                        title = "No inter-agent messages",
                        body = "Messages sent between agents on your desktop appear here chronologically.",
                    )
                }
            } else {
                items(items = agentMessages, key = { "msg:${it.id}" }) { msg ->
                    AgentMessageCard(msg)
                }
            }
        } else {
            if (displayedSessions.isEmpty()) {
                item(key = "no-sessions") {
                    EmptySessionsCard(
                        title = if (filter == SessionsFilter.ACTIVE) "No active sessions" else "No recent sessions",
                        body = "Tap + to start a new session remotely, or start one on your computer.",
                    )
                }
            } else {
                item(key = "sessions-grouped") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = HudColors.Surface,
                        border = BorderStroke(1.dp, HudColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            displayedSessions.forEachIndexed { idx, session ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = HudColors.Border,
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                    )
                                }
                                SessionListRow(session = session, onClick = { onOpenSession(session) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySessionsSummaryCard(
    todayUsage: UsageSummary,
    sessionCount: Int,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudColors.Surface,
        border = BorderStroke(1.dp, HudColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today · $sessionCount sessions",
                    style = MaterialTheme.typography.labelMedium,
                    color = HudColors.TextMuted,
                )
                if (todayUsage.estimatedCostUsd > 0.0) {
                    Text(
                        text = "≈${QuotaFormatting.formatMoney(todayUsage.estimatedCostUsd)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = HudColors.TextSecondary,
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = QuotaFormatting.formatTokens(todayUsage.totalTokens),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = HudColors.TextPrimary,
                )
                Text(
                    text = "tokens",
                    fontSize = 13.sp,
                    color = HudColors.TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            HorizontalDivider(color = HudColors.Border, thickness = 1.dp)

            // 2-column token breakdown with colored dots (matching iOS!)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KindStatCell("Cache write", todayUsage.cacheWriteTokens, HudColors.KindCacheWrite, Modifier.weight(1f))
                    KindStatCell("Input", todayUsage.inputTokens, HudColors.KindInput, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KindStatCell("Reasoning", todayUsage.reasoningTokens, HudColors.KindReasoning, Modifier.weight(1f))
                    KindStatCell("Output", todayUsage.outputTokens, HudColors.KindOutput, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KindStatCell("Cache read", todayUsage.cacheReadTokens, HudColors.KindCacheRead, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KindStatCell(label: String, tokens: Long, dot: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.5.sp, color = HudColors.TextMuted, modifier = Modifier.weight(1f))
        Text(
            text = QuotaFormatting.formatTokens(tokens),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            color = HudColors.TextPrimary,
        )
    }
}

@Composable
private fun SessionListRow(
    session: SessionSummary,
    onClick: () -> Unit,
) {
    val stateColor = when (session.state) {
        "waiting" -> HudColors.Terracotta
        "running" -> HudColors.SageBright
        else -> HudColors.TextMuted
    }
    val clientRaw = (session.vendorName ?: session.client ?: session.source).ifBlank { "Agent" }
    val clientLabel = QuotaFormatting.providerDisplayName(clientRaw)
    val relTime = QuotaFormatting.formatRelativeTime(session.updatedAt ?: session.lastMessageAt)
    val cost = session.pricedCost ?: session.cost

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(providerId = session.client ?: session.source, size = 20.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                text = session.title?.ifBlank { null } ?: session.project?.ifBlank { null } ?: "Untitled session",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (session.state == "running" || session.state == "waiting") {
                Spacer(Modifier.width(6.dp))
                BreathingDot(color = stateColor, size = 6.dp, pulsing = true)
            }
            if (session.tokens > 0L) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = QuotaFormatting.formatTokens(session.tokens),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudColors.TextPrimary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val subParts = buildList {
                add(clientLabel)
                (session.modelName ?: session.model)?.takeIf { it.isNotBlank() }?.let { add(it) }
                session.project?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            Text(
                text = subParts,
                fontSize = 11.5.sp,
                color = HudColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (cost != null && cost > 0.0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "≈${QuotaFormatting.formatMoney(cost)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = HudColors.TextMuted,
                )
            }
        }

        if (!session.liveFacts?.message.isNullOrBlank()) {
            Text(
                text = session.liveFacts?.message.orEmpty(),
                fontSize = 12.sp,
                color = HudColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val statusWord = when (session.state) {
                "waiting" -> "Needs approval"
                "running" -> "Running"
                else -> "Idle"
            }
            Text(
                text = statusWord,
                fontSize = 10.5.sp,
                color = if (session.state == "idle") HudColors.TextMuted else stateColor,
            )
            if (relTime.isNotBlank()) {
                Text(text = relTime, fontSize = 10.5.sp, color = HudColors.TextMuted)
            }
        }
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

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudColors.SurfaceElevated,
        border = BorderStroke(1.dp, HudColors.BorderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = HudColors.TerracottaSoft,
                ) {
                    Text(
                        text = (approval.badge ?: approval.toolName.ifBlank { kind }).uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HudColors.Terracotta,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = listOfNotNull(
                        approval.source.takeIf { it.isNotBlank() }?.let { QuotaFormatting.providerDisplayName(it) },
                        approval.cwd?.substringAfterLast('/')?.substringAfterLast('\\'),
                    ).joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = HudColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = approval.title.ifBlank { approval.toolName.ifBlank { "Approval requested" } },
                style = MaterialTheme.typography.titleMedium,
            )

            val preview = approval.summary ?: approval.detail ?: approval.input
            if (preview.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HudColors.Background,
                    border = BorderStroke(1.dp, HudColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = preview.take(360),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = HudColors.TextSecondary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            when (kind) {
                "question" -> {
                    approval.questions.forEach { q ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q.question, style = MaterialTheme.typography.bodyMedium)
                            if (q.options.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    q.options.forEach { opt ->
                                        val list = selectedAnswers[q.answerKey].orEmpty()
                                        val picked = opt.label in list
                                        FilterChip(
                                            selected = picked,
                                            onClick = {
                                                val current = selectedAnswers.getOrPut(q.answerKey) { mutableListOf() }
                                                if (q.multiSelect) {
                                                    if (picked) current.remove(opt.label) else current.add(opt.label)
                                                } else {
                                                    current.clear()
                                                    current.add(opt.label)
                                                }
                                                selectedAnswers[q.answerKey] = current.toMutableList()
                                            },
                                            label = { Text(opt.label, maxLines = 1) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = HudColors.BlueSoft,
                                                selectedLabelColor = HudColors.TextPrimary,
                                                containerColor = HudColors.Surface,
                                                labelColor = HudColors.TextSecondary,
                                            ),
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = customAnswers[q.answerKey].orEmpty(),
                                onValueChange = { customAnswers[q.answerKey] = it },
                                placeholder = { Text("Type or dictate answer…", color = HudColors.TextMuted) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        onStartVoiceInput { spoken -> customAnswers[q.answerKey] = spoken }
                                    }) {
                                        Icon(Icons.Outlined.Mic, contentDescription = "Dictate", tint = HudColors.TextSecondary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HudColors.BlueAccent,
                                    unfocusedBorderColor = HudColors.BorderStrong,
                                    focusedTextColor = HudColors.TextPrimary,
                                    unfocusedTextColor = HudColors.TextPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Button(
                        onClick = {
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
                        },
                        enabled = !busy && approval.answersQuestion,
                        colors = ButtonDefaults.buttonColors(containerColor = HudColors.BlueAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Sending…" else "Submit answer")
                    }
                }

                "plan" -> {
                    if (!approval.plan.isNullOrBlank()) {
                        MarkdownText(
                            markdown = approval.plan.take(900),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSubmitDecision(
                                    buildJsonObject {
                                        put("kind", "keep_planning")
                                        if (noteText.isNotBlank()) put("message", noteText.trim())
                                    },
                                )
                            },
                            enabled = !busy && approval.answersPlan,
                            border = BorderStroke(1.dp, HudColors.BorderStrong),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Keep planning", color = HudColors.TextSecondary)
                        }
                        Button(
                            onClick = {
                                onSubmitDecision(
                                    buildJsonObject {
                                        put("kind", "approve_plan")
                                        put("acceptEdits", true)
                                    },
                                )
                            },
                            enabled = !busy && approval.answersPlan,
                            colors = ButtonDefaults.buttonColors(containerColor = HudColors.BlueAccent, contentColor = Color.White),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Approve plan")
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HudColors.BlueAccent,
                                unfocusedBorderColor = HudColors.BorderStrong,
                                focusedTextColor = HudColors.TextPrimary,
                                unfocusedTextColor = HudColors.TextPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                onSubmitDecision(
                                    buildJsonObject {
                                        put("kind", "deny")
                                        if (noteText.isNotBlank()) put("message", noteText.trim())
                                    },
                                )
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HudColors.TerracottaSoft,
                                contentColor = HudColors.Terracotta,
                            ),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Deny", fontWeight = FontWeight.SemiBold)
                        }
                        if (approval.allowsAlways) {
                            OutlinedButton(
                                onClick = {
                                    onSubmitDecision(buildJsonObject { put("kind", "always") })
                                },
                                enabled = !busy,
                                border = BorderStroke(1.dp, HudColors.BorderStrong),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.weight(0.9f),
                            ) {
                                Text("Always", color = HudColors.TextPrimary)
                            }
                        }
                        Button(
                            onClick = {
                                onSubmitDecision(buildJsonObject { put("kind", "allow") })
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HudColors.BlueAccent,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1.1f),
                        ) {
                            Text(
                                text = approval.allowLabel?.ifBlank { "Allow once" } ?: "Allow once",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentMessageCard(msg: AgentMessageRecord) {
    val fromLabel = msg.from.title?.ifBlank { null } ?: msg.from.client.ifBlank { "Agent" }
    val toLabel = msg.to.title?.ifBlank { null } ?: msg.to.client.ifBlank { "Agent" }
    val stateColor = when (msg.state) {
        "refused", "failed" -> HudColors.Terracotta
        "delivered", "sent", "replied" -> HudColors.SageBright
        else -> HudColors.TextMuted
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudColors.Surface,
        border = BorderStroke(1.dp, HudColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(fromLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = HudColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 6.dp).size(13.dp),
                )
                Text(
                    text = toLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(msg.state.ifBlank { msg.kind }, style = MaterialTheme.typography.labelSmall, color = stateColor)
            }
            val body = msg.text ?: msg.reason
            if (!body.isNullOrBlank()) {
                MarkdownText(markdown = body.take(600))
            }
            if (msg.sentAtMs > 0L) {
                Text(QuotaFormatting.formatRelativeMs(msg.sentAtMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptySessionsCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudColors.Surface,
        border = BorderStroke(1.dp, HudColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
