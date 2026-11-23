package org.agenthud.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
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
import org.agenthud.android.ui.theme.HudType
import kotlin.math.roundToInt

private enum class TokenKind(val label: String, val dotColor: Color?) {
    ALL("All", null),
    INPUT("Input", HudColors.KindInput),
    OUTPUT("Output", HudColors.KindOutput),
    REASONING("Reasoning", HudColors.KindReasoning),
    CACHE_WRITE("Cache write", HudColors.KindCacheWrite),
    CACHE_READ("Cache read", HudColors.KindCacheRead),
    ;

    fun of(s: UsageSummary): Long = when (this) {
        ALL -> s.totalTokens
        INPUT -> s.inputTokens
        OUTPUT -> s.outputTokens
        REASONING -> s.reasoningTokens
        CACHE_WRITE -> s.cacheWriteTokens
        CACHE_READ -> s.cacheReadTokens
    }

    fun of(p: UsageDayPoint): Long = when (this) {
        ALL -> p.totalTokens
        INPUT -> p.inputTokens
        OUTPUT -> p.outputTokens
        REASONING -> p.reasoningTokens
        CACHE_WRITE -> p.cacheWriteTokens
        CACHE_READ -> p.cacheReadTokens
    }
}

@Composable
fun UsageScreen(
    state: AppUiState,
    onSelectRange: (UsageRange) -> Unit,
) {
    val summary = state.usageSummary
    var kind by remember { mutableStateOf(TokenKind.ALL) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(top = 10.dp, bottom = LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "stats-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Stats", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                SlidingSegmentedSelector(
                    options = UsageRange.entries,
                    selected = state.usageRange,
                    onSelect = onSelectRange,
                    labelFor = { it.label },
                )
            }
        }

        // Token-kind filter: a quiet scrolling row of pills, edge to edge.
        item(key = "kinds") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TokenKind.entries, key = { it.name }) { chip ->
                    KindPill(chip = chip, selected = chip == kind, onClick = { kind = chip })
                }
            }
        }

        item(key = "chart") {
            TokensChartCard(summary = summary, kind = kind, modifier = Modifier.padding(horizontal = 20.dp))
        }

        item(key = "recent-header") {
            SectionHeader(title = "Recent usage", modifier = Modifier.padding(horizontal = 20.dp))
        }
        item(key = "recent") {
            HudCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Column {
                    RecentWindowBlock("Today", state.todayUsage)
                    HudDivider(Modifier.padding(start = 16.dp))
                    RecentWindowBlock("Last 7 days", state.weekUsage)
                    HudDivider(Modifier.padding(start = 16.dp))
                    RecentWindowBlock("Last 30 days", state.monthUsage)
                }
            }
        }

        if (summary.byAgent.isNotEmpty()) {
            item(key = "agents-header") {
                SectionHeader(title = "By agent", modifier = Modifier.padding(horizontal = 20.dp))
            }
            item(key = "agents") {
                HudCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column {
                        summary.byAgent.forEachIndexed { idx, agent ->
                            if (idx > 0) HudDivider(Modifier.padding(start = 48.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                ProviderIcon(providerId = agent.agentId, size = 20.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = agent.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = HudColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(QuotaFormatting.formatTokens(agent.totalTokens), style = HudType.Value)
                                        Text(
                                            text = "${(agent.share * 100.0).roundToInt()}%",
                                            style = HudType.Caption.copy(color = HudColors.TextMuted),
                                            modifier = Modifier.width(40.dp).padding(start = 8.dp),
                                            maxLines = 1,
                                        )
                                    }
                                    HudProgressBar(
                                        progress = agent.share.toFloat(),
                                        color = HudColors.providerAccent(agent.agentId),
                                        height = 3.dp,
                                    )
                                    Row {
                                        Text(
                                            text = "In ${QuotaFormatting.formatTokens(agent.inputTokens)} · " +
                                                "Out ${QuotaFormatting.formatTokens(agent.outputTokens)} · " +
                                                "Cache ${QuotaFormatting.formatTokens(agent.cacheReadTokens)}",
                                            style = HudType.Caption.copy(color = HudColors.TextMuted),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (agent.costUsd > 0.0) {
                                            Text(
                                                text = "≈${QuotaFormatting.formatMoney(agent.costUsd)}",
                                                style = HudType.Caption,
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
}

@Composable
private fun KindPill(chip: TokenKind, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) HudColors.SurfaceMuted else Color.Transparent,
        animationSpec = HudMotion.tint(),
        label = "pillBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) HudColors.TextPrimary else HudColors.TextSecondary,
        animationSpec = HudMotion.tint(),
        label = "pillFg",
    )
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else HudColors.BorderStrong, shape)
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip.dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(chip.dotColor),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text = chip.label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

@Composable
private fun TokensChartCard(
    summary: UsageSummary,
    kind: TokenKind,
    modifier: Modifier = Modifier,
) {
    val days = summary.daily
    val topModels = remember(summary.byModel) { summary.byModel.take(6).map { it.model } }
    val total = kind.of(summary)
    val shownTotal = rememberAnimatedLong(total)
    val maxDay = (days.maxOfOrNull { kind.of(it) } ?: 0L).coerceAtLeast(1L)
    val barGrowth = remember { Animatable(0f) }
    LaunchedEffect(summary.range, kind) {
        barGrowth.snapTo(0f)
        barGrowth.animateTo(1f, HudMotion.fill())
    }

    HudCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (kind == TokenKind.ALL) "Tokens" else "${kind.label} tokens",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = HudColors.TextSecondary,
                    )
                    Text(text = QuotaFormatting.formatTokens(shownTotal), style = HudType.Display)
                }
                if (summary.estimatedCostUsd > 0.0) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("At API prices", fontSize = 11.sp, color = HudColors.TextMuted)
                        Text(
                            text = "≈${QuotaFormatting.formatMoney(summary.estimatedCostUsd)}",
                            style = HudType.numeric(17.sp, FontWeight.SemiBold),
                        )
                    }
                }
            }
            Text(
                text = "Cache hits ${(summary.cacheHitRate * 100.0).roundToInt()}% · " +
                    "${summary.activeDays} active day${if (summary.activeDays == 1) "" else "s"}",
                style = HudType.Caption.copy(color = HudColors.TextMuted),
            )

            if (days.all { kind.of(it) == 0L }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No usage in this window", style = MaterialTheme.typography.bodySmall, color = HudColors.TextMuted)
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                ) {
                    val count = days.size.coerceAtLeast(1)
                    val minGap = if (count > 14) 3.dp.toPx() else 6.dp.toPx()
                    val barW = ((size.width - minGap * (count - 1)) / count).coerceIn(3.dp.toPx(), 28.dp.toPx())
                    // Bars span the full width (first bar under the first label, last under "Now");
                    // a single bar sits at the "Now" end.
                    val gap = if (count > 1) (size.width - barW * count) / (count - 1) else 0f
                    val startX = if (count == 1) size.width - barW else 0f
                    val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

                    // Quiet baseline gridlines at 50% and 100%.
                    listOf(0f, 0.5f).forEach { f ->
                        drawLine(
                            color = HudColors.Border,
                            start = Offset(0f, size.height * f),
                            end = Offset(size.width, size.height * f),
                            strokeWidth = 1f,
                        )
                    }

                    days.forEachIndexed { idx, pt ->
                        val v = kind.of(pt)
                        val x = startX + idx * (barW + gap)
                        if (v <= 0L) {
                            drawRoundRect(
                                color = HudColors.Track,
                                topLeft = Offset(x, size.height - 2.dp.toPx()),
                                size = Size(barW, 2.dp.toPx()),
                                cornerRadius = radius,
                            )
                            return@forEachIndexed
                        }
                        val totalH = (size.height * (v.toFloat() / maxDay) * barGrowth.value)
                            .coerceAtLeast(2.dp.toPx() * barGrowth.value)
                        if (kind != TokenKind.ALL || pt.byModel.isEmpty() || topModels.isEmpty()) {
                            drawRoundRect(
                                color = kind.dotColor ?: HudColors.SageBright,
                                topLeft = Offset(x, size.height - totalH),
                                size = Size(barW, totalH),
                                cornerRadius = radius,
                            )
                        } else {
                            var y = size.height
                            val sumModels = pt.byModel.values.sum().coerceAtLeast(1L).toFloat()
                            val segGap = if (barW > 6.dp.toPx()) 1.dp.toPx() else 0f
                            topModels.forEachIndexed { mIdx, model ->
                                val mv = pt.byModel[model] ?: 0L
                                if (mv > 0L) {
                                    val segH = totalH * (mv / sumModels)
                                    y -= segH
                                    drawRoundRect(
                                        color = HudColors.modelColor(mIdx),
                                        topLeft = Offset(x, y.coerceAtLeast(0f)),
                                        size = Size(barW, (segH - segGap).coerceAtLeast(1.5f)),
                                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                                    )
                                }
                            }
                        }
                    }
                }

                if (days.size >= 2) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(days.first().label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        if (days.size >= 5) {
                            Text(days[days.size / 2].label, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                        }
                        Text("Now", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (kind == TokenKind.ALL && topModels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        topModels.chunked(2).forEachIndexed { rowIdx, pair ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                pair.forEachIndexed { colIdx, model ->
                                    LegendDot(
                                        label = model,
                                        color = HudColors.modelColor(rowIdx * 2 + colIdx),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = HudColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentWindowBlock(title: String, summary: UsageSummary) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HudColors.TextPrimary)
                val sub = buildList {
                    if (summary.estimatedCostUsd > 0.0) add("≈${QuotaFormatting.formatMoney(summary.estimatedCostUsd)}")
                    if (summary.cacheHitRate > 0.0) add("Cache hits ${(summary.cacheHitRate * 100.0).roundToInt()}%")
                }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = HudType.Caption.copy(color = HudColors.TextMuted))
            }
            Text(QuotaFormatting.formatTokens(summary.totalTokens), style = HudType.numeric(17.sp, FontWeight.SemiBold))
        }

        if (summary.byModel.isNotEmpty() && summary.totalTokens > 0L) {
            SegmentedShareBar(
                segments = summary.byModel.take(5).mapIndexed { idx, row ->
                    row.share.toFloat().coerceAtLeast(0.01f) to HudColors.modelColor(idx)
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                summary.byModel.take(3).forEachIndexed { idx, row ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(label = row.model, color = HudColors.modelColor(idx), modifier = Modifier.weight(1f))
                        Text(
                            text = QuotaFormatting.formatTokens(row.totalTokens),
                            style = HudType.numeric(12.sp, FontWeight.Medium, HudColors.TextPrimary),
                        )
                    }
                }
                if (summary.byModel.size > 3) {
                    Text(
                        text = "${summary.byModel.size - 3} more",
                        style = HudType.Caption.copy(color = HudColors.TextMuted),
                        modifier = Modifier.padding(start = 13.dp),
                    )
                }
            }
        }
    }
}
