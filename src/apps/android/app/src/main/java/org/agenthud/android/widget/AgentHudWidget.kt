package org.agenthud.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.agenthud.android.MainActivity
import org.agenthud.android.data.DeviceStore
import org.agenthud.android.data.WidgetQuotaRow
import org.agenthud.android.data.WidgetQuotaSnapshot

private val ObsidianCard = Color(0xFF111114)
private val ObsidianInner = Color(0xFF18181D)
private val TrackDark = Color(0xFF26262C)
private val InkBright = Color(0xFFF5F5F7)
private val InkMuted = Color(0xFF98989D)
private val SageBar = Color(0xFF5B7A45)
private val TerracottaBar = Color(0xFFC84B3C)

class AgentHudWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = DeviceStore(context).loadWidgetSnapshot()
        provideContent {
            HudWidgetCard(snapshot)
        }
    }
}

class AgentHudWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgentHudWidget()
}

@Composable
private fun HudWidgetCard(snapshot: WidgetQuotaSnapshot?) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(ObsidianCard))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        if (snapshot == null) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Agent HUD",
                    style = TextStyle(color = ColorProvider(InkBright), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Tap to pair your computer",
                    style = TextStyle(color = ColorProvider(InkMuted), fontSize = 12.sp),
                )
            }
            return@Box
        }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(if (snapshot.online) SageBar else TerracottaBar)),
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = snapshot.deviceName,
                    style = TextStyle(color = ColorProvider(InkBright), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                val statusTag = when {
                    snapshot.pendingApprovals > 0 -> "${snapshot.pendingApprovals} waiting"
                    snapshot.activeSessions > 0 -> "${snapshot.activeSessions} active"
                    snapshot.online -> "Live"
                    else -> "Offline"
                }
                Text(
                    text = statusTag,
                    style = TextStyle(
                        color = ColorProvider(if (snapshot.pendingApprovals > 0) TerracottaBar else InkMuted),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (snapshot.rows.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(ColorProvider(ObsidianInner))
                        .cornerRadius(10.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No active quota windows yet",
                        style = TextStyle(color = ColorProvider(InkMuted), fontSize = 12.sp),
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    snapshot.rows.take(4).forEachIndexed { idx, row ->
                        if (idx > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                        QuotaRowLine(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaRowLine(row: WidgetQuotaRow) {
    val accent = if (row.critical) TerracottaBar else SageBar
    val pctTint = if (row.critical) TerracottaBar else InkBright

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${row.provider} · ${row.label}",
                style = TextStyle(color = ColorProvider(InkBright), fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "${row.usedPercent}%",
                style = TextStyle(color = ColorProvider(pctTint), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = (row.usedPercent / 100f).coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            color = ColorProvider(accent),
            backgroundColor = ColorProvider(TrackDark),
        )
    }
}
