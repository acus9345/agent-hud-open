package org.agenthud.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.AppUiState
import org.agenthud.android.data.SessionSummary
import org.agenthud.android.ui.theme.HudColors

enum class NavTab(val label: String, val icon: ImageVector) {
    QUOTAS("Home", Icons.Outlined.Home),
    USAGE("Stats", Icons.Outlined.BarChart),
    SESSIONS("Sessions", Icons.Outlined.Terminal),
    DEVICES("Settings", Icons.Outlined.Settings),
}

@Composable
fun AgentHudRoot(
    repository: AgentHudRepository,
    state: AppUiState,
    requestedTab: NavTab?,
    onConsumeRequestedTab: () -> Unit,
    onStartVoiceInput: ((String) -> Unit) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(NavTab.QUOTAS) }
    var showPairModal by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<SessionSummary?>(null) }
    var showNewSessionSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab
            onConsumeRequestedTab()
        }
    }

    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        repository.clearBanner()
    }

    if (state.devices.isEmpty()) {
        PairScreen(
            isPairing = state.isPairing,
            pairError = state.pairError,
            canDismiss = false,
            onDismiss = {},
            onClearError = { repository.clearPairError() },
            onSubmitUri = { uri -> repository.pairFromUri(uri) },
        )
        return
    }

    if (showPairModal) {
        PairScreen(
            isPairing = state.isPairing,
            pairError = state.pairError,
            canDismiss = true,
            onDismiss = {
                repository.clearPairError()
                showPairModal = false
            },
            onClearError = { repository.clearPairError() },
            onSubmitUri = { uri ->
                repository.pairFromUri(uri) {
                    showPairModal = false
                }
            },
        )
        return
    }

    val openSession = selectedSession
    if (openSession != null) {
        val reachableTarget = state.companionState?.targets?.sessions
            ?.firstOrNull { it.id == openSession.id }
        SessionDetailScreen(
            session = openSession,
            reachableTarget = reachableTarget,
            repository = repository,
            onBack = { selectedSession = null },
            onStartVoiceInput = onStartVoiceInput,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background),
    ) {
        // Active screen transitions with smooth directional slide + fade
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val slideIn = slideInHorizontally(
                    animationSpec = tween(240),
                    initialOffsetX = { if (forward) it / 10 else -it / 10 },
                ) + fadeIn(animationSpec = tween(220))
                val slideOut = slideOutHorizontally(
                    animationSpec = tween(180),
                    targetOffsetX = { if (forward) -it / 12 else it / 12 },
                ) + fadeOut(animationSpec = tween(160))
                slideIn.togetherWith(slideOut).using(SizeTransform(clip = false))
            },
            label = "tabTransition",
            modifier = Modifier.fillMaxSize(),
        ) { tab ->
            when (tab) {
                NavTab.QUOTAS -> QuotasScreen(
                    state = state,
                    repository = repository,
                    onRefresh = { repository.refreshNow() },
                    onSelectDevice = { repository.selectDevice(it) },
                    onOpenNewSession = { showNewSessionSheet = true },
                    onOpenSessionsTab = { selectedTab = NavTab.SESSIONS },
                    onStartVoiceInput = onStartVoiceInput,
                )
                NavTab.USAGE -> UsageScreen(
                    state = state,
                    onSelectRange = { repository.setUsageRange(it) },
                )
                NavTab.SESSIONS -> SessionsScreen(
                    state = state,
                    repository = repository,
                    onOpenSession = { selectedSession = it },
                    onOpenNewSession = { showNewSessionSheet = true },
                    onStartVoiceInput = onStartVoiceInput,
                )
                NavTab.DEVICES -> DevicesScreen(
                    state = state,
                    onSelectDevice = { repository.selectDevice(it) },
                    onRemoveDevice = { repository.removeDevice(it) },
                    onSaveRemoteConfig = { id, mode, url, ip -> repository.setRemoteConfig(id, mode, url, ip) },
                    onToggleLiveMonitor = { repository.setLiveMonitorEnabled(it) },
                    onAddDevice = { showPairModal = true },
                )
            }
        }

        // Subtle bottom fade + floating iOS-style frosted pill navigation bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, HudColors.Background.copy(alpha = 0.92f)),
                    ),
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            FloatingNavDock(
                selectedTab = selectedTab,
                approvalsCount = state.pendingApprovals.size,
                onSelectTab = { selectedTab = it },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 82.dp, start = 16.dp, end = 16.dp),
        )
    }

    if (showNewSessionSheet) {
        NewSessionSheet(
            starts = state.companionState?.targets?.starts.orEmpty(),
            repository = repository,
            onDismiss = { showNewSessionSheet = false },
            onStartVoiceInput = onStartVoiceInput,
        )
    }
}

@Composable
private fun FloatingNavDock(
    selectedTab: NavTab,
    approvalsCount: Int,
    onSelectTab: (NavTab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = HudColors.DockGlass,
        border = BorderStroke(1.dp, HudColors.BorderStrong),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val interaction = remember { MutableInteractionSource() }
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.1f else 0.96f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "dockScale",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) HudColors.SurfaceMuted else Color.Transparent)
                        .clickable(interactionSource = interaction, indication = null) {
                            onSelectTab(tab)
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    ) {
                        Box {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) HudColors.TextPrimary else HudColors.TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            if (tab == NavTab.SESSIONS && approvalsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(HudColors.Terracotta),
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) HudColors.TextPrimary else HudColors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}
