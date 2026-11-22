package org.agenthud.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * iOS-inspired Home screen:
 * - Date + Device pill header
 * - Greeting + "+ New" pill button
 * - Natural-language status summary
 * - Circular provider quota rings strip
 * - Inline pending approvals
 * - Detailed grouped quota bars
 * - "This week" stacked bar card
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top bar: date + device switcher pill + refresh
            item(key = "home-top") {
                HomeHeaderRow(
                    state = state,
                    onSelectDevice = onSelectDevice,
                    onRefresh = onRefresh,
                )
            }

            // Greeting + New pill button + natural-language summary
            item(key = "greeting") {
                GreetingHero(
                    state = state,
                    canStartNew = canStartNew,
                    onOpenNewSession = onOpenNewSession,
                    onOpenSessionsTab = onOpenSessionsTab,
                )
            }

            // Connection error banner if offline/revoked
            if (!state.connectionError.isNullOrBlank() &&
                (state.connectionState == ConnectionState.ERROR || state.connectionState == ConnectionState.UNAUTHORIZED)
            ) {
                item(key = "conn-error") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = HudColors.TerracottaSoft,
                        border = BorderStroke(1.dp, HudColors.Terracotta.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (state.connectionState == ConnectionState.UNAUTHORIZED) {
                                    "Pairing revoked on desktop"
                                } else {
                                    "Computer unreachable"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = HudColors.TextPrimary,
                            )
                            Text(
                                text = state.connectionError,
                                style = MaterialTheme.typography.bodySmall,
                                color = HudColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            // Circular provider rings strip with real icons + spring ring sweep
            if (providers.isNotEmpty()) {
                item(key = "provider-rings") {
                    RevealMotion(key = "rings", delayMs = 30) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            items(
                                items = providers,
                                key = { "ring:${it.provider}:${it.accountKey ?: it.accountID ?: it.accountLabel}" },
                            ) { provider ->
                                ProviderRingItem(
                                    provider = provider,
                                    names = names,
                                    showRemaining = showRemaining,
                                )
                            }
                        }
                    }
                }
            }

            // Pending approvals right on Home (matching iOS Home!)
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

            // "This week" stacked bar card with animated bar rise
            if (state.weekUsage.daily.isNotEmpty()) {
                item(key = "this-week") {
                    RevealMotion(key = "week", delayMs = 70) {
                        ThisWeekCard(
                            days = state.weekUsage.daily,
                            totalTokens = state.weekUsage.totalTokens,
                            costUsd = state.weekUsage.estimatedCostUsd,
                        )
                    }
                }
            }

            // Detailed quota windows grouped cleanly
            if (providers.isEmpty()) {
                item(key = "empty-quotas") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = HudColors.Surface,
                        border = BorderStroke(1.dp, HudColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
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
                item(key = "quotas-section-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Quotas & balances", style = MaterialTheme.typography.titleMedium)
                        if (state.lastSyncedAtMs > 0L) {
                            Text(
                                text = QuotaFormatting.formatRelativeMs(state.lastSyncedAtMs),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                item(key = "quotas-grouped-card") {
                    RevealMotion(key = "quotas-card", delayMs = 110) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = HudColors.Surface,
                            border = BorderStroke(1.dp, HudColors.Border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                providers.forEachIndexed { idx, provider ->
                                    if (idx > 0) {
                                        HorizontalDivider(
                                            color = HudColors.Border,
                                            thickness = 1.dp,
                                            modifier = Modifier.padding(vertical = 14.dp),
                                        )
                                    }
                                    ProviderQuotaSection(
                                        provider = provider,
                                        names = names,
                                        showRemaining = showRemaining,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Noticed today (events)
            val events = state.companionState?.events.orEmpty()
            if (events.isNotEmpty()) {
                item(key = "noticed-header") {
                    Text("Noticed today", style = MaterialTheme.typography.titleMedium)
                }
                item(key = "noticed-card") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = HudColors.Surface,
                        border = BorderStroke(1.dp, HudColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            events.take(5).forEachIndexed { idx, ev ->
                                if (idx > 0) HorizontalDivider(color = HudColors.Border, thickness = 1.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
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
                                    Spacer(Modifier.width(8.dp))
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
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))
    }
    val active = state.activeDevice
    val online = state.connectionState == ConnectionState.LIVE_SSE ||
        state.connectionState == ConnectionState.POLLING
    val dotColor = when (state.connectionState) {
        ConnectionState.LIVE_SSE, ConnectionState.POLLING -> HudColors.SageBright
        ConnectionState.CONNECTING -> HudColors.TextMuted
        else -> HudColors.Terracotta
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelMedium,
            color = HudColors.TextMuted,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = HudColors.Surface,
                    border = BorderStroke(1.dp, HudColors.Border),
                    modifier = Modifier.clickable(enabled = state.devices.size > 1) { switcherOpen = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BreathingDot(
                            color = dotColor,
                            size = 6.dp,
                            pulsing = state.connectionState == ConnectionState.LIVE_SSE,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = active?.deviceName ?: "Agent HUD",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (online) HudColors.TextPrimary else HudColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DropdownMenu(
                    expanded = switcherOpen,
                    onDismissRequest = { switcherOpen = false },
                    containerColor = HudColors.SurfaceElevated,
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

            val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "spinDeg",
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = if (state.isRefreshing) HudColors.SageBright else HudColors.TextMuted,
                    modifier = Modifier
                        .size(17.dp)
                        .graphicsLayer { rotationZ = if (state.isRefreshing) spinAngle else 0f },
                )
            }
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

    // Build natural-language status lines matching iOS Home
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
            add("${state.runningSessionCount} active session${if (state.runningSessionCount == 1) "" else "s"} running now.")
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
            )
            if (canStartNew) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = HudColors.BlueAccent,
                    modifier = Modifier.clickable(onClick = onOpenNewSession),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "New",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        if (lines.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable(enabled = state.pendingApprovals.isNotEmpty()) {
                    onOpenSessionsTab()
                },
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = HudColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderRingItem(
    provider: ProviderReading,
    names: Map<String, String>,
    showRemaining: Boolean,
) {
    val displayName = QuotaFormatting.providerDisplayName(provider.provider, names)
    val accent = HudColors.providerAccent(provider.provider)
    val topQuota = provider.quotas.maxByOrNull { it.usedPercent }
    val firstBalance = provider.balances.firstOrNull()

    val isCritical = topQuota != null && QuotaFormatting.isCriticalLevel(topQuota)
    val ringColor = when {
        topQuota != null && isCritical -> HudColors.Terracotta
        topQuota != null -> HudColors.SageBright
        firstBalance != null -> accent
        else -> HudColors.Track
    }
    val rawPct = when {
        topQuota == null -> 100
        showRemaining -> topQuota.remainingPercent.roundToInt().coerceIn(0, 100)
        else -> topQuota.usedPercent.roundToInt().coerceIn(0, 100)
    }
    val targetFraction = (rawPct / 100f).coerceIn(0f, 1f)
    val animatedSweep = remember(provider.provider, provider.accountKey) { Animatable(0f) }
    LaunchedEffect(targetFraction) {
        animatedSweep.animateTo(
            targetValue = targetFraction,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        )
    }
    val animatedPct by animateIntAsState(
        targetValue = rawPct,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "ringPct",
    )

    val headlineValue = when {
        topQuota != null -> "$animatedPct%"
        firstBalance != null -> {
            val amt = firstBalance.amount.toDoubleOrNull()
            if (amt != null) QuotaFormatting.formatMoney(amt, firstBalance.currency) else firstBalance.amount
        }
        else -> "—"
    }
    val subLabel = when {
        topQuota != null -> QuotaFormatting.formatResetTime(topQuota.resetsAtMs)
            .removePrefix("Resets ")
            .ifBlank { topQuota.windowShortName.ifBlank { "Active" } }
        firstBalance != null -> "Balance"
        else -> ""
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.width(86.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 3.5.dp.toPx()
                val pad = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = HudColors.Track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedSweep.value,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Surface(
                shape = CircleShape,
                color = HudColors.SurfaceElevated,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ProviderIcon(providerId = provider.provider, size = 24.dp)
                }
            }
        }

        Text(
            text = displayName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = HudColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = headlineValue,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCritical) HudColors.Terracotta else HudColors.SageBright,
            maxLines = 1,
        )
        if (subLabel.isNotBlank()) {
            Text(
                text = subLabel,
                fontSize = 10.sp,
                color = HudColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThisWeekCard(
    days: List<UsageDayPoint>,
    totalTokens: Long,
    costUsd: Double,
) {
    val last7 = days.takeLast(7)
    val maxDay = (last7.maxOfOrNull { it.totalTokens } ?: 0L).coerceAtLeast(1L)
    val barGrowth = remember { Animatable(0f) }
    LaunchedEffect(totalTokens, last7.size) {
        barGrowth.animateTo(1f, animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing))
    }
    val topModels = remember(last7) {
        val sums = HashMap<String, Long>()
        last7.forEach { d -> d.byModel.forEach { (m, v) -> sums[m] = (sums[m] ?: 0L) + v } }
        sums.entries.sortedByDescending { it.value }.map { it.key }.take(5)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudColors.Surface,
        border = BorderStroke(1.dp, HudColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("This week", style = MaterialTheme.typography.titleMedium)
                val right = buildString {
                    append(QuotaFormatting.formatTokens(totalTokens))
                    if (costUsd > 0.0) append(" · ≈${QuotaFormatting.formatMoney(costUsd)}")
                }
                Text(right, style = MaterialTheme.typography.labelSmall, color = HudColors.TextSecondary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                last7.forEachIndexed { idx, point ->
                    val parsed = runCatching { LocalDate.parse(point.day) }.getOrNull()
                    val dow = parsed?.dayOfWeek?.name?.take(1) ?: ""
                    val dom = parsed?.dayOfMonth?.toString() ?: ""
                    val isToday = idx == last7.lastIndex

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(dow, fontSize = 10.sp, color = HudColors.TextMuted)
                        Canvas(
                            modifier = Modifier
                                .width(14.dp)
                                .height(54.dp),
                        ) {
                            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            drawRoundRect(
                                color = HudColors.Track,
                                topLeft = Offset.Zero,
                                size = size,
                                cornerRadius = radius,
                            )
                            val totalH = (size.height * (point.totalTokens.toFloat() / maxDay.toFloat()) * barGrowth.value)
                                .coerceIn(0f, size.height)
                            if (totalH > 0f) {
                                if (point.byModel.isEmpty() || topModels.isEmpty()) {
                                    drawRoundRect(
                                        color = HudColors.SageBright,
                                        topLeft = Offset(0f, size.height - totalH),
                                        size = Size(size.width, totalH),
                                        cornerRadius = radius,
                                    )
                                } else {
                                    var y = size.height
                                    topModels.forEachIndexed { mIdx, model ->
                                        val v = point.byModel[model] ?: 0L
                                        if (v > 0L) {
                                            val segH = (totalH * (v.toFloat() / point.totalTokens.coerceAtLeast(1L).toFloat()))
                                            y -= segH
                                            drawRoundRect(
                                                color = HudColors.modelColor(mIdx),
                                                topLeft = Offset(0f, y.coerceAtLeast(0f)),
                                                size = Size(size.width, segH.coerceAtLeast(2f)),
                                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isToday) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(HudColors.TextPrimary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dom,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HudColors.Background,
                                )
                            }
                        } else {
                            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                Text(dom, fontSize = 10.sp, color = HudColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderQuotaSection(
    provider: ProviderReading,
    names: Map<String, String>,
    showRemaining: Boolean,
) {
    val displayName = QuotaFormatting.providerDisplayName(provider.provider, names)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(providerId = provider.provider, size = 20.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            val meta = listOfNotNull(
                provider.plan?.takeIf { it.isNotBlank() },
                provider.accountLabel.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) },
            ).firstOrNull()
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = HudColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        provider.quotas.forEach { window ->
            CompactQuotaBarRow(window, showRemaining = showRemaining)
        }

        if (provider.balances.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("API balance", style = MaterialTheme.typography.bodySmall, color = HudColors.TextMuted)
                val first = provider.balances.first()
                val amt = first.amount.toDoubleOrNull()
                val text = if (amt != null) QuotaFormatting.formatMoney(amt, first.currency) else first.amount
                val low = first.level == "critical" || (amt != null && amt <= 0.0)
                Text(
                    text = text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (low) HudColors.Terracotta else HudColors.TextPrimary,
                )
            }
        }

        provider.resetCredits?.let { rc ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Usage resets", style = MaterialTheme.typography.bodySmall, color = HudColors.TextMuted)
                Text(
                    text = "${rc.availableCount} available",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun CompactQuotaBarRow(
    window: QuotaWindowReading,
    showRemaining: Boolean,
) {
    val label = QuotaFormatting.cleanWindowLabel(window.windowName, window.windowShortName)
    val reset = QuotaFormatting.formatResetTime(window.resetsAtMs)
    val displayPct = if (showRemaining) {
        window.remainingPercent.roundToInt().coerceIn(0, 100)
    } else {
        window.usedPercent.roundToInt().coerceIn(0, 100)
    }
    val animatedPct by animateIntAsState(
        targetValue = displayPct,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "barPct",
    )
    val animatedFill by animateFloatAsState(
        targetValue = (displayPct / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "barFill",
    )
    val critical = QuotaFormatting.isCriticalLevel(window)
    val barColor = if (critical) HudColors.Terracotta else HudColors.Sage
    val pctColor = if (critical) HudColors.Terracotta else HudColors.TextPrimary

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (reset.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(reset, fontSize = 11.sp, color = HudColors.TextMuted, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$animatedPct%",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = pctColor,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(HudColors.Track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFill)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
        }
    }
}
