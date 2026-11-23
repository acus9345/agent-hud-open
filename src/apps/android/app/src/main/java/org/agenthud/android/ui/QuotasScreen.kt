package org.agenthud.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.AppUiState
import org.agenthud.android.data.ConnectionState
import org.agenthud.android.data.ProviderReading
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.QuotaWindowReading
import org.agenthud.android.data.UsageDayPoint
import org.agenthud.android.ui.theme.HudColors
import org.agenthud.android.ui.theme.HudType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Home: a calm morning-briefing layout.
 * Date + computer, greeting with a plain-language status line, the provider strip, pending
 * approvals, this week's activity, then every quota window in one grouped list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotasScreen(
    state: AppUiState,
    repository: AgentHudRepository,
    onRefresh: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onOpenNewSession: () -> Unit,
    onOpenSessionsTab: () -> Unit,
    onStartVoiceInput: ((String) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers = state.visibleProviders
    val showRemaining = state.showRemainingPercent
    val names = state.readings.names
    val approvals = state.pendingApprovals
    val canStartNew = state.companionState?.targets?.starts?.isNotEmpty() == true

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "home-top") {
                HomeHeaderRow(state = state, onSelectDevice = onSelectDevice, onRefresh = onRefresh)
            }

            item(key = "greeting") {
                GreetingHero(
                    state = state,
                    canStartNew = canStartNew,
                    onOpenNewSession = onOpenNewSession,
                    onOpenSessionsTab = onOpenSessionsTab,
                )
            }

            if (!state.connectionError.isNullOrBlank() &&
                (state.connectionState == ConnectionState.ERROR || state.connectionState == ConnectionState.UNAUTHORIZED)
            ) {
                item(key = "conn-error") {
                    HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(HudColors.Terracotta),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (state.connectionState == ConnectionState.UNAUTHORIZED) {
                                        "Pairing revoked on desktop"
                                    } else {
                                        "Computer unreachable"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(text = state.connectionError, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (providers.isNotEmpty()) {
                item(key = "provider-strip") {
                    ProviderStrip(providers = providers, names = names, showRemaining = showRemaining)
                }
            }

            if (approvals.isNotEmpty()) {
                items(items = approvals.take(3), key = { "home-approval:${it.id}" }) { approval ->
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

            if (state.weekUsage.daily.isNotEmpty()) {
                item(key = "this-week") {
                    ThisWeekSection(
                        days = state.weekUsage.daily,
                        totalTokens = state.weekUsage.totalTokens,
                        costUsd = state.weekUsage.estimatedCostUsd,
                    )
                }
            }

            if (providers.isEmpty()) {
                item(key = "empty-quotas") {
                    HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (state.connectionState == ConnectionState.CONNECTING) {
                                    "Syncing with ${state.activeDevice?.deviceName ?: "computer"}…"
                                } else {
                                    "No provider readings yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Pull down to refresh once Agent HUD finishes polling providers.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                item(key = "limits-header") {
                    SectionHeader(
                        title = "Limits",
                        trailing = state.lastSyncedAtMs.takeIf { it > 0L }
                            ?.let { "Updated ${QuotaFormatting.formatRelativeMs(it)}" },
                    )
                }
                item(key = "limits-card") {
                    HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        Column {
                            providers.forEachIndexed { idx, provider ->
                                if (idx > 0) HudDivider(Modifier.padding(start = 16.dp))
                                ProviderQuotaSection(provider = provider, names = names, showRemaining = showRemaining)
                            }
                        }
                    }
                }
            }

            val events = state.companionState?.events.orEmpty()
            if (events.isNotEmpty()) {
                item(key = "noticed-header") { SectionHeader(title = "Noticed today") }
                item(key = "noticed-card") {
                    HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        Column {
                            events.take(5).forEachIndexed { idx, ev ->
                                if (idx > 0) HudDivider(Modifier.padding(start = 16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = ev.title?.ifBlank { null } ?: ev.source.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        if (!ev.message.isNullOrBlank()) {
                                            Text(
                                                text = ev.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = QuotaFormatting.formatRelativeTime(ev.at),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderRow(
    state: AppUiState,
    onSelectDevice: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var switcherOpen by remember { mutableStateOf(false) }
    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
    }
    val dotColor = when (state.connectionState) {
        ConnectionState.LIVE_SSE, ConnectionState.POLLING -> HudColors.SageBright
        ConnectionState.CONNECTING -> HudColors.TextMuted
        else -> HudColors.Terracotta
    }
    val canSwitch = state.devices.size > 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dateLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = HudColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(HudColors.SurfaceElevated)
                    .bounceClick(scaleDown = 0.95f, enabled = canSwitch) { switcherOpen = true }
                    .padding(start = 10.dp, end = if (canSwitch) 6.dp else 11.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BreathingDot(
                    color = dotColor,
                    size = 6.dp,
                    pulsing = state.connectionState == ConnectionState.LIVE_SSE,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = state.activeDevice?.deviceName ?: "Agent HUD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = HudColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (canSwitch) {
                    Icon(
                        imageVector = Icons.Outlined.UnfoldMore,
                        contentDescription = "Switch computer",
                        tint = HudColors.TextMuted,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(14.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = switcherOpen,
                onDismissRequest = { switcherOpen = false },
                containerColor = HudColors.SurfaceElevated,
                shape = RoundedCornerShape(14.dp),
            ) {
                state.devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.deviceName, color = HudColors.TextPrimary) },
                        onClick = {
                            switcherOpen = false
                            onSelectDevice(device.deviceId)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))
        val spin = rememberInfiniteTransition(label = "refreshSpin")
        val angle by spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "spinDeg",
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .bounceClick(scaleDown = 0.9f, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh",
                tint = HudColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = if (state.isRefreshing) angle else 0f },
            )
        }
    }
}

@Composable
private fun GreetingHero(
    state: AppUiState,
    canStartNew: Boolean,
    onOpenNewSession: () -> Unit,
    onOpenSessionsTab: () -> Unit,
) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val lines = buildList {
        val approvals = state.pendingApprovals
        if (approvals.isNotEmpty()) {
            val first = approvals.first()
            val who = QuotaFormatting.providerDisplayName(first.source.ifBlank { "Agent" }, state.readings.names)
            if (approvals.size == 1) {
                add("$who is waiting for your approval.")
            } else {
                add("${approvals.size} requests are waiting for your approval.")
            }
        } else if (state.runningSessionCount > 0) {
            add("${state.runningSessionCount} session${if (state.runningSessionCount == 1) " is" else "s are"} running.")
        }

        val hottest = state.visibleProviders
            .flatMap { p ->
                val pName = QuotaFormatting.providerDisplayName(p.provider, state.readings.names)
                p.quotas.map { pName to it }
            }
            .maxByOrNull { it.second.usedPercent }
        if (hottest != null && hottest.second.usedPercent >= 75.0) {
            val reset = QuotaFormatting.formatResetTime(hottest.second.resetsAtMs)
            val label = QuotaFormatting.cleanWindowLabel(hottest.second.windowName, hottest.second.windowShortName)
            if (reset.isNotEmpty()) {
                add("${hottest.first}'s $label ${reset.replaceFirstChar { it.lowercase() }}.")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            if (canStartNew) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(HudColors.BlueAccent)
                        .bounceClick(scaleDown = 0.94f, onClick = onOpenNewSession)
                        .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "New", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }

        if (lines.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = if (state.pendingApprovals.isNotEmpty()) {
                    Modifier.bounceClick(scaleDown = 0.99f, onClick = onOpenSessionsTab)
                } else {
                    Modifier
                },
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = HudColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** Provider glance strip: mark, name, a small ring next to the figure, and when it resets. */
@Composable
private fun ProviderStrip(
    providers: List<ProviderReading>,
    names: Map<String, String>,
    showRemaining: Boolean,
) {
    val key: (ProviderReading) -> String = { "ring:${it.provider}:${it.accountKey ?: it.accountID ?: it.accountLabel}" }
    if (providers.size <= 4) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            providers.forEach { p ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ProviderGlance(provider = p, names = names, showRemaining = showRemaining)
                }
            }
        }
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            items(items = providers, key = key) { p ->
                Box(modifier = Modifier.width(86.dp), contentAlignment = Alignment.Center) {
                    ProviderGlance(provider = p, names = names, showRemaining = showRemaining)
                }
            }
        }
    }
}

@Composable
private fun ProviderGlance(
    provider: ProviderReading,
    names: Map<String, String>,
    showRemaining: Boolean,
) {
    val displayName = QuotaFormatting.providerDisplayName(provider.provider, names)
    val topQuota = provider.quotas.maxByOrNull { it.usedPercent }
    val firstBalance = provider.balances.firstOrNull()
    val critical = topQuota != null && QuotaFormatting.isCriticalLevel(topQuota)
    val accent = if (critical) HudColors.Terracotta else HudColors.SageBright

    val rawPct = when {
        topQuota == null -> 0
        showRemaining -> topQuota.remainingPercent.roundToInt().coerceIn(0, 100)
        else -> topQuota.usedPercent.roundToInt().coerceIn(0, 100)
    }
    val animatedPct by animateIntAsState(rawPct, HudMotion.fill(), label = "glancePct")
    val sweep by animateFloatAsState(rawPct / 100f, HudMotion.fill(), label = "glanceSweep")

    val sub = when {
        topQuota != null -> QuotaFormatting.formatResetTime(topQuota.resetsAtMs).removePrefix("Resets ")
            .ifBlank { topQuota.windowShortName }
        firstBalance != null -> "Balance"
        else -> ""
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProviderIcon(providerId = provider.provider, size = 30.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = displayName,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = HudColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (topQuota != null) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2f
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(HudColors.Track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(
                        accent, -90f, 360f * sweep, false, Offset(inset, inset), arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(text = "$animatedPct%", style = HudType.numeric(15.sp, FontWeight.SemiBold, accent))
            } else if (firstBalance != null) {
                val amt = firstBalance.amount.toDoubleOrNull()
                Text(
                    text = if (amt != null) QuotaFormatting.formatMoney(amt, firstBalance.currency) else firstBalance.amount,
                    style = HudType.numeric(15.sp, FontWeight.SemiBold),
                )
            } else {
                Text("—", style = HudType.numeric(15.sp, color = HudColors.TextMuted))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub.ifBlank { " " },
            fontSize = 10.5.sp,
            color = HudColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThisWeekSection(
    days: List<UsageDayPoint>,
    totalTokens: Long,
    costUsd: Double,
) {
    val last7 = days.takeLast(7)
    val maxDay = (last7.maxOfOrNull { it.totalTokens } ?: 0L).coerceAtLeast(1L)
    var selectedIdx by remember(last7.size) { mutableStateOf<Int?>(null) }
    val barGrowth = remember { Animatable(0f) }
    LaunchedEffect(totalTokens, last7.size) {
        barGrowth.animateTo(1f, HudMotion.fill())
    }
    val topModels = remember(last7) {
        val sums = HashMap<String, Long>()
        last7.forEach { d -> d.byModel.forEach { (m, v) -> sums[m] = (sums[m] ?: 0L) + v } }
        sums.entries.sortedByDescending { it.value }.map { it.key }.take(5)
    }
    val selectedDay = selectedIdx?.let { last7.getOrNull(it) }
    val trailing = if (selectedDay != null) {
        val label = runCatching { LocalDate.parse(selectedDay.day) }.getOrNull()
            ?.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())) ?: selectedDay.day
        "$label · ${QuotaFormatting.formatTokens(selectedDay.totalTokens)}"
    } else {
        buildString {
            append(QuotaFormatting.formatTokens(totalTokens))
            append(" tokens")
            if (costUsd > 0.0) append(" · ≈${QuotaFormatting.formatMoney(costUsd)}")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = "This week", trailing = trailing)
        HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                last7.forEachIndexed { idx, point ->
                    val parsed = runCatching { LocalDate.parse(point.day) }.getOrNull()
                    val dow = parsed?.dayOfWeek?.getDisplayName(TextStyle.NARROW, Locale.getDefault()) ?: ""
                    val dom = parsed?.dayOfMonth?.toString() ?: ""
                    val isToday = idx == last7.lastIndex
                    val isSelected = selectedIdx == idx
                    val dimmed = selectedIdx != null && !isSelected
                    val barAlpha by animateFloatAsState(if (dimmed) 0.35f else 1f, HudMotion.tint(), label = "dayDim")

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick(scaleDown = 0.94f) { selectedIdx = if (isSelected) null else idx },
                    ) {
                        Text(
                            text = dow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected || isToday) HudColors.TextPrimary else HudColors.TextMuted,
                        )
                        Canvas(
                            modifier = Modifier
                                .width(16.dp)
                                .height(64.dp)
                                .graphicsLayer { alpha = barAlpha },
                        ) {
                            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            drawRoundRect(color = HudColors.Track, size = size, cornerRadius = radius)
                            val totalH = (size.height * (point.totalTokens.toFloat() / maxDay) * barGrowth.value)
                                .coerceIn(0f, size.height)
                            if (totalH <= 0f) return@Canvas
                            if (point.byModel.isEmpty() || topModels.isEmpty()) {
                                drawRoundRect(
                                    color = HudColors.SageBright,
                                    topLeft = Offset(0f, size.height - totalH),
                                    size = Size(size.width, totalH),
                                    cornerRadius = radius,
                                )
                            } else {
                                val gap = 1.dp.toPx()
                                var y = size.height
                                val denom = point.totalTokens.coerceAtLeast(1L).toFloat()
                                topModels.forEachIndexed { mIdx, model ->
                                    val v = point.byModel[model] ?: 0L
                                    if (v > 0L) {
                                        val segH = totalH * (v / denom)
                                        y -= segH
                                        drawRoundRect(
                                            color = HudColors.modelColor(mIdx),
                                            topLeft = Offset(0f, y.coerceAtLeast(0f)),
                                            size = Size(size.width, (segH - gap).coerceAtLeast(1.5f)),
                                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> HudColors.BlueAccent
                                        isToday -> HudColors.TextPrimary
                                        else -> Color.Transparent
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dom,
                                style = HudType.numeric(
                                    11.sp,
                                    if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                    when {
                                        isSelected -> Color.White
                                        isToday -> Color.Black
                                        else -> HudColors.TextSecondary
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Splits "Gemini Models · Weekly limit" into a group ("Gemini Models") and a window name. */
private fun splitWindowLabel(label: String): Pair<String?, String> {
    val idx = label.indexOf(" · ")
    if (idx <= 0) return null to label
    return label.substring(0, idx).trim() to label.substring(idx + 3).trim()
}

@Composable
private fun ProviderQuotaSection(
    provider: ProviderReading,
    names: Map<String, String>,
    showRemaining: Boolean,
) {
    val displayName = QuotaFormatting.providerDisplayName(provider.provider, names)

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProviderIcon(providerId = provider.provider, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val meta = listOfNotNull(
                provider.plan?.takeIf { it.isNotBlank() },
                provider.accountLabel.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) },
            ).firstOrNull()
            if (meta != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = meta,
                    fontSize = 12.5.sp,
                    color = HudColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        var lastGroup: String? = null
        provider.quotas.forEach { window ->
            val (group, name) = splitWindowLabel(
                QuotaFormatting.cleanWindowLabel(window.windowName, window.windowShortName),
            )
            if (group != null && group != lastGroup) {
                Text(
                    text = group,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = HudColors.TextMuted,
                    modifier = Modifier.padding(top = if (lastGroup != null) 4.dp else 0.dp),
                )
            }
            lastGroup = group
            QuotaWindowRow(window = window, name = name, showRemaining = showRemaining)
        }

        if (provider.balances.isNotEmpty()) {
            val first = provider.balances.first()
            val amt = first.amount.toDoubleOrNull()
            val low = first.level == "critical" || (amt != null && amt <= 0.0)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "API balance",
                    fontSize = 14.sp,
                    color = HudColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (amt != null) QuotaFormatting.formatMoney(amt, first.currency) else first.amount,
                    style = HudType.Value.copy(color = if (low) HudColors.Terracotta else HudColors.TextPrimary),
                )
            }
        }

        provider.resetCredits?.let { rc ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Usage resets",
                    fontSize = 14.sp,
                    color = HudColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "${rc.availableCount} available", style = HudType.Caption)
            }
        }
    }
}

/**
 * One quota window. The name gets the full width (wrapping to a second line rather than
 * truncating); the figure is pinned right; reset time and outlook sit quietly beneath the bar.
 */
@Composable
private fun QuotaWindowRow(
    window: QuotaWindowReading,
    name: String,
    showRemaining: Boolean,
) {
    val reset = QuotaFormatting.formatResetTime(window.resetsAtMs)
    val outlook = QuotaFormatting.formatOutlook(window)
    val displayPct = if (showRemaining) {
        window.remainingPercent.roundToInt().coerceIn(0, 100)
    } else {
        window.usedPercent.roundToInt().coerceIn(0, 100)
    }
    val animatedPct by animateIntAsState(displayPct, HudMotion.fill(), label = "windowPct")
    val critical = QuotaFormatting.isCriticalLevel(window)
    val barColor = if (critical) HudColors.Terracotta else HudColors.SageBright

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = name,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = HudColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$animatedPct%",
                style = HudType.Value.copy(color = if (critical) HudColors.Terracotta else HudColors.TextPrimary),
            )
        }
        HudProgressBar(progress = displayPct / 100f, color = barColor)
        if (reset.isNotEmpty() || outlook != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = reset,
                    style = HudType.Caption.copy(color = HudColors.TextMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (outlook != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = outlook,
                        style = HudType.Caption.copy(
                            color = if (window.outlook == "exhausted" || window.outlook == "exhausts") {
                                HudColors.Terracotta
                            } else {
                                HudColors.TextMuted
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
