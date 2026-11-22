package org.agenthud.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.DeviceStore
import org.agenthud.android.data.LanClient
import org.agenthud.android.widget.AgentHudWidget

class AgentHudApp : Application() {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var deviceStore: DeviceStore
        private set

    lateinit var repository: AgentHudRepository
        private set

    override fun onCreate() {
        super.onCreate()
        registerChannels()
        deviceStore = DeviceStore(this)
        repository = AgentHudRepository(
            deviceStore = deviceStore,
            lanClient = LanClient(),
            onWidgetUpdated = {
                backgroundScope.launch {
                    runCatching { AgentHudWidget().updateAll(this@AgentHudApp) }
                }
            },
        )
    }

    private fun registerChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIVE_MONITOR,
                getString(R.string.notification_channel_live),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_APPROVALS,
                getString(R.string.notification_channel_approvals),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    companion object {
        const val CHANNEL_LIVE_MONITOR = "agent_hud_live"
        const val CHANNEL_APPROVALS = "agent_hud_approvals"
    }
}
