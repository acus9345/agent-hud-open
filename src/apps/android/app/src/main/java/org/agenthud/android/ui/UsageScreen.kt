package org.agenthud.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agenthud.android.data.AppUiState
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.UsageDayPoint
import org.agenthud.android.data.UsageRange
import org.agenthud.android.data.UsageSummary
import org.agenthud.android.ui.theme.HudColors
import kotlin.math.roundToInt

private enum class TokenKindChip(val label: String, val dotColor: Color?) {
    ALL("All", null),
    INPUT("Input", HudColors.KindInput),
    OUTPUT("Output", HudColors.KindOutput),
    REASONING("Reasoning", HudColors.KindReasoning),
    CACHE_WRITE("Cache write", HudColors.KindCacheWrite),
    CACHE_READ("Cache read", HudColors.KindCacheRead),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsageScreen(
    state: AppUiState,
    onSelectRange: (UsageRange) -> Unit,
) {
    val summary = state.usageSummary
    var kindFilter by remember { mutableStateOf(TokenKindChip.ALL) }

    val displayedTotal = when (kindFilter) {
        TokenKindChip.ALL -> summary.totalTokens
        TokenKindChip.INPUT -> summary.inputTokens
        TokenKindChip.OUTPUT -> summary.outputTokens
        TokenKindChip.REASONING -> summary.reasoningTokens
        TokenKindChip.CACHE_WRITE -> summary.cacheWriteTokens
        TokenKindChip.CACHE_READ -> summary.cacheReadTokens
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header + compact segmented range pill
        item(key = "stats-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Stats", style = MaterialTheme.typography.headlineMedium)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = HudColors.Surface,
                    border = BorderStroke(1.dp, HudColors.Border),
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        UsageRange.entries.forEach { range ->
                            val selected = range == state.usageRange
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) HudColors.SurfaceMuted else Color.Transparent)
                                    .clickable { onSelectRange(range) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = range.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) HudColors.TextPrimary else HudColors.TextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Token kinds filter card (matching iOS Stats!)
        item(key = "token-kinds-card") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = HudColors.Surface,
                border = BorderStroke(1.dp, HudColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Token kinds", style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        TokenKindChip.entries.forEach { chip ->
                            val selected = chip == kindFilter
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) HudColors.SurfaceMuted else HudColors.Background,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) HudColors.BorderStrong else HudColors.Border,
                                ),
                                modifier = Modifier.clickable { kindFilter = chip },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (chip.dotColor != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(chip.dotColor),
                                        )
                                    }
                                    Text(
                                        text = chip.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) HudColors.TextPrimary else HudColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stacked-by-model Tokens bar chart card (matching iOS Stats!)
        item(key = "stacked-chart") {
            StackedTokensChartCard(
                summary = summary,
                kindFilter = kindFilter,
                displayedTotal = displayedTotal,
            )
        }

        // Recent usage card: Today / Last 7 days / Last 30 days with multi-color model bars
        item(key = "recent-usage") {
            RecentUsageCard(
                today = state.todayUsage,
                week = state.weekUsage,
                month = state.monthUsage,
            )
        }

        // Grouped By Agent card
        if (summary.byAgent.isNotEmpty()) {
            item(key = "by-agent-card") {
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
                        Text("By agent", style = MaterialTheme.typography.titleMedium)
                        summary.byAgent.forEachIndexed { idx, agent ->
                            if (idx > 0) HorizontalDivider(color = HudColors.Border, thickness = 1.dp)
                            val accent = HudColors.providerAccent(agent.agentId)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ProviderIcon(providerId = agent.agentId, size = 19.dp)
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        text = agent.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = QuotaFormatting.formatTokens(agent.totalTokens),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HudColors.TextPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${(agent.share * 100.0).roundToInt()}%",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        color = HudColors.TextMuted,
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
                                            .fillMaxWidth(agent.share.toFloat().coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(accent),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "In ${QuotaFormatting.formatTokens(agent.inputTokens)} · Out ${QuotaFormatting.formatTokens(agent.outputTokens)} · Cache ${QuotaFormatting.formatTokens(agent.cacheReadTokens)}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    if (agent.costUsd > 0.0) {
                                        Text(
                                            text = "≈${QuotaFormatting.formatMoney(agent.costUsd)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HudColors.TextSecondary,
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
}

@Composable
private fun StackedTokensChartCard(
    summary: UsageSummary,
    kindFilter: TokenKindChip,
    displayedTotal: Long,
) {
    val days = summary.daily
    val topModels = remember(summary.byModel) {
        summary.byModel.take(6).map { it.model }
    }
    val dayValue: (UsageDayPoint) -> Long = { pt ->
        when (kindFilter) {
            TokenKindChip.ALL -> pt.totalTokens
            TokenKindChip.INPUT -> pt.inputTokens
            TokenKindChip.OUTPUT -> pt.outputTokens
            TokenKindChip.REASONING -> pt.reasoningTokens
            TokenKindChip.CACHE_WRITE -> pt.cacheWriteTokens
            TokenKindChip.CACHE_READ -> pt.cacheReadTokens
        }
    }
    val maxDay = (days.maxOfOrNull(dayValue) ?: 0L).coerceAtLeast(1L)
    val barGrowth = remember(summary.range, kindFilter) { Animatable(0f) }
    LaunchedEffect(summary.range, kindFilter, displayedTotal) {
        barGrowth.snapTo(0f)
        barGrowth.animateTo(1f, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
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
                Text("Tokens", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (kindFilter == TokenKindChip.ALL) "Stacked by model" else kindFilter.label,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = QuotaFormatting.formatTokens(displayedTotal),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = HudColors.TextPrimary,
                    )
                    Text(
                        text = "Cache hit ${(summary.cacheHitRate * 100.0).roundToInt()}% · ${summary.activeDays} active d",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (summary.estimatedCostUsd > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("At API prices", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "≈${QuotaFormatting.formatMoney(summary.estimatedCostUsd)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HudColors.TextPrimary,
                        )
                    }
                }
            }

            // Compact 2-column model legend
            if (kindFilter == TokenKindChip.ALL && topModels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    topModels.chunked(2).forEachIndexed { rowIdx, pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            pair.forEachIndexed { colIdx, model ->
                                val color = HudColors.modelColor(rowIdx * 2 + colIdx)
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(color),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = model,
                                        fontSize = 11.sp,
                                        color = HudColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Stacked vertical bars
            if (days.all { dayValue(it) == 0L }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No usage recorded in this window",
                        style = MaterialTheme.typography.bodySmall,
                        color = HudColors.TextMuted,
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp),
                ) {
                    val count = days.size.coerceAtLeast(1)
                    val gap = if (count > 14) 3.dp.toPx() else 7.dp.toPx()
                    val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(3.dp.toPx())
                    val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

                    days.forEachIndexed { idx, pt ->
                        val v = dayValue(pt)
                        val x = idx * (barW + gap)
                        if (v <= 0L) {
                            drawRoundRect(
                                color = HudColors.Track,
                                topLeft = Offset(x, size.height - 2.dp.toPx()),
                                size = Size(barW, 2.dp.toPx()),
                                cornerRadius = radius,
                            )
                            return@forEachIndexed
                        }
                        val totalH = (size.height * (v.toFloat() / maxDay.toFloat()) * barGrowth.value)
                            .coerceAtLeast(if (barGrowth.value > 0.05f) 4.dp.toPx() else 0f)
                        if (kindFilter != TokenKindChip.ALL || pt.byModel.isEmpty() || topModels.isEmpty()) {
                            val color = kindFilter.dotColor ?: HudColors.SageBright
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(x, size.height - totalH),
                                size = Size(barW, totalH),
                                cornerRadius = radius,
                            )
                        } else {
                            var y = size.height
                            val sumModels = pt.byModel.values.sum().coerceAtLeast(1L)
                            topModels.forEachIndexed { mIdx, model ->
                                val mv = pt.byModel[model] ?: 0L
                                if (mv > 0L) {
                                    val segH = totalH * (mv.toFloat() / sumModels.toFloat())
                                    y -= segH
                                    drawRoundRect(
                                        color = HudColors.modelColor(mIdx),
                                        topLeft = Offset(x, y.coerceAtLeast(0f)),
                                        size = Size(barW, segH.coerceAtLeast(2f)),
                                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                                    )
                                }
                            }
                        }
                    }
                }

                if (days.size >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(days.first().label, style = MaterialTheme.typography.labelSmall)
                        if (days.size >= 5) {
                            Text(days[days.size / 2].label, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Now", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentUsageCard(
    today: UsageSummary,
    week: UsageSummary,
    month: UsageSummary,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudColors.Surface,
        border = BorderStroke(1.dp, HudColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Recent usage", style = MaterialTheme.typography.titleMedium)
            RecentWindowBlock("Today", today)
            HorizontalDivider(color = HudColors.Border, thickness = 1.dp)
            RecentWindowBlock("Last 7 days", week)
            HorizontalDivider(color = HudColors.Border, thickness = 1.dp)
            RecentWindowBlock("Last 30 days", month)
        }
    }
}

@Composable
private fun RecentWindowBlock(title: String, summary: UsageSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                val sub = buildList {
                    if (summary.estimatedCostUsd > 0.0) add("≈${QuotaFormatting.formatMoney(summary.estimatedCostUsd)}")
                    if (summary.cacheHitRate > 0.0) add("Cache hits ${(summary.cacheHitRate * 100.0).roundToInt()}%")
                }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = QuotaFormatting.formatTokens(summary.totalTokens),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = HudColors.TextPrimary,
            )
        }

        // Multi-color horizontal stacked model bar
        if (summary.byModel.isNotEmpty() && summary.totalTokens > 0L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(HudColors.Track),
            ) {
                summary.byModel.take(5).forEachIndexed { idx, row ->
                    val weight = row.share.toFloat().coerceAtLeast(0.02f)
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(HudColors.modelColor(idx)),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                summary.byModel.take(3).forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(HudColors.modelColor(idx)),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = row.model,
                            fontSize = 12.sp,
                            color = HudColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = QuotaFormatting.formatTokens(row.totalTokens),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = HudColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}
