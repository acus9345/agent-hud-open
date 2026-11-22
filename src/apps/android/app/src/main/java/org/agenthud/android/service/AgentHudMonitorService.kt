package org.agenthud.android.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.agenthud.android.AgentHudApp
import org.agenthud.android.MainActivity
import org.agenthud.android.R
import org.agenthud.android.data.AppUiState
import org.agenthud.android.data.ApprovalItem
import org.agenthud.android.data.ConnectionState
import org.agenthud.android.data.QuotaFormatting
import kotlin.math.roundToInt

class AgentHudMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeAlertIds = LinkedHashMap<String, Int>()
    private var nextAlertId = ALERT_BASE_ID

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as AgentHudApp
        val initial = buildOngoingNotification(app.repository.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(LIVE_NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(LIVE_NOTIFICATION_ID, initial)
        }

        scope.launch {
            app.repository.state.collectLatest { state ->
                if (state.activeDevice == null || !state.liveMonitorEnabled) {
                    stopSelf()
                    return@collectLatest
                }
                postLiveStatus(state)
                reconcileApprovalAlerts(state.pendingApprovals)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        val manager = getSystemService(NotificationManager::class.java)
        activeAlertIds.values.forEach { manager?.cancel(it) }
        activeAlertIds.clear()
        super.onDestroy()
    }

    private fun postLiveStatus(state: AppUiState) {
        if (!notificationsAllowed()) return
        getSystemService(NotificationManager::class.java)
            ?.notify(LIVE_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun buildOngoingNotification(state: AppUiState): Notification {
        val hostName = state.activeDevice?.deviceName ?: "Agent HUD"
        val connText = when (state.connectionState) {
            ConnectionState.LIVE_SSE -> "Live"
            ConnectionState.POLLING -> "Connected"
            ConnectionState.CONNECTING -> "Connecting"
            ConnectionState.UNAUTHORIZED -> "Pair again"
            ConnectionState.ERROR, ConnectionState.DISCONNECTED -> "Offline"
        }

        val waitingCount = state.pendingApprovals.size
        val runningCount = state.runningSessionCount
        val hottest = state.readings.providers
            .flatMap { p ->
                val title = QuotaFormatting.providerDisplayName(p.provider, state.readings.names)
                p.quotas.map { title to it }
            }
            .maxByOrNull { it.second.usedPercent }

        val headline = buildString {
            append("$hostName · $connText")
            if (waitingCount > 0) append(" · $waitingCount waiting")
        }

        val detail = buildList {
            if (runningCount > 0) {
                add("$runningCount active ${if (runningCount == 1) "session" else "sessions"}")
            }
            if (hottest != null) {
                add("${hottest.first} ${hottest.second.usedPercent.roundToInt()}%")
            }
            if (isEmpty()) add("Watching quotas and sessions")
        }.joinToString(" · ")

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (waitingCount > 0) putExtra(MainActivity.EXTRA_OPEN_TAB, "sessions")
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, AgentHudApp.CHANNEL_LIVE_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_agent_hud)
            .setContentTitle(headline)
            .setContentText(detail)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentPending)
            .build()
    }

    private fun reconcileApprovalAlerts(approvals: List<ApprovalItem>) {
        if (!notificationsAllowed()) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val liveKeys = approvals.mapTo(HashSet()) { it.id }

        val expired = activeAlertIds.keys.filterNot { it in liveKeys }
        for (id in expired) {
            activeAlertIds.remove(id)?.let(manager::cancel)
        }

        for (item in approvals) {
            if (activeAlertIds.containsKey(item.id)) continue
            val alertId = nextAlertId++
            activeAlertIds[item.id] = alertId
            manager.notify(alertId, buildApprovalAlert(item, alertId))
        }
    }

    private fun buildApprovalAlert(item: ApprovalItem, alertId: Int): Notification {
        val kind = item.kind ?: "permission"
        val title = when (kind) {
            "question" -> "Question from ${item.source.ifBlank { "agent" }}"
            "plan" -> "Plan review · ${item.source.ifBlank { "agent" }}"
            else -> "Approval · ${item.toolName.ifBlank { item.title }}"
        }
        val body = item.summary ?: item.detail ?: item.input.ifBlank { item.title }

        val openPending = PendingIntent.getActivity(
            this,
            alertId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "sessions")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, AgentHudApp.CHANNEL_APPROVALS)
            .setSmallIcon(R.drawable.ic_stat_agent_hud)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)

        when {
            kind == "permission" -> {
                builder.addAction(0, item.allowLabel?.ifBlank { "Allow" } ?: "Allow", decisionIntent(item.id, "allow", alertId * 2))
                builder.addAction(0, "Deny", decisionIntent(item.id, "deny", alertId * 2 + 1))
            }
            kind == "plan" && item.answersPlan -> {
                builder.addAction(0, "Approve plan", decisionIntent(item.id, "approve_plan", alertId * 2))
                builder.addAction(0, "Keep planning", decisionIntent(item.id, "keep_planning", alertId * 2 + 1))
            }
        }
        return builder.build()
    }

    private fun decisionIntent(approvalId: String, kind: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ApprovalActionReceiver::class.java).apply {
            action = ApprovalActionReceiver.ACTION_DECIDE
            putExtra(ApprovalActionReceiver.EXTRA_APPROVAL_ID, approvalId)
            putExtra(ApprovalActionReceiver.EXTRA_DECISION_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val LIVE_NOTIFICATION_ID = 1001
        private const val ALERT_BASE_ID = 2000

        fun start(context: Context) {
            val intent = Intent(context, AgentHudMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentHudMonitorService::class.java))
        }
    }
}
