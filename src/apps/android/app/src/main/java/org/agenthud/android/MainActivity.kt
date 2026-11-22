package org.agenthud.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import org.agenthud.android.service.AgentHudMonitorService
import org.agenthud.android.ui.AgentHudRoot
import org.agenthud.android.ui.NavTab
import org.agenthud.android.ui.theme.AgentHudTheme

class MainActivity : ComponentActivity() {
    private val requestedTab = mutableStateOf<NavTab?>(null)
    private var voiceCallback: ((String) -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val app = application as AgentHudApp
        val state = app.repository.state.value
        if (granted && state.activeDevice != null && state.liveMonitorEnabled) {
            AgentHudMonitorService.start(this)
        }
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        val cb = voiceCallback
        voiceCallback = null
        if (!spoken.isNullOrEmpty()) {
            cb?.invoke(spoken)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val app = application as AgentHudApp
        handleIncomingIntent(intent, app)

        setContent {
            AgentHudTheme {
                val uiState by app.repository.state.collectAsState()
                val desiredTab by requestedTab

                LaunchedEffect(uiState.activeDeviceId, uiState.liveMonitorEnabled) {
                    syncMonitorService(
                        hasDevice = uiState.activeDevice != null,
                        enabled = uiState.liveMonitorEnabled,
                    )
                }

                AgentHudRoot(
                    repository = app.repository,
                    state = uiState,
                    requestedTab = desiredTab,
                    onConsumeRequestedTab = { requestedTab.value = null },
                    onStartVoiceInput = { onResult -> launchVoiceInput(onResult) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent, application as AgentHudApp)
    }

    override fun onStart() {
        super.onStart()
        (application as AgentHudApp).repository.setForeground(true)
    }

    override fun onStop() {
        (application as AgentHudApp).repository.setForeground(false)
        super.onStop()
    }

    private fun handleIncomingIntent(intent: Intent?, app: AgentHudApp) {
        if (intent == null) return
        val data = intent.data
        if (data != null && data.scheme.equals("agenthud", ignoreCase = true) && data.host.equals("pair", ignoreCase = true)) {
            app.repository.pairFromUri(data.toString())
        }
        when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            "quotas" -> requestedTab.value = NavTab.QUOTAS
            "sessions" -> requestedTab.value = NavTab.SESSIONS
            "usage" -> requestedTab.value = NavTab.USAGE
            "devices" -> requestedTab.value = NavTab.DEVICES
        }
    }

    private fun syncMonitorService(hasDevice: Boolean, enabled: Boolean) {
        if (!hasDevice || !enabled) {
            AgentHudMonitorService.stop(this)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        AgentHudMonitorService.start(this)
    }

    private fun launchVoiceInput(onResult: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message for the agent")
        }
        if (intent.resolveActivity(packageManager) == null) return
        voiceCallback = onResult
        speechLauncher.launch(intent)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
