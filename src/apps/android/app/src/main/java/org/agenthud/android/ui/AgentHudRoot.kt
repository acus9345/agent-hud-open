package org.agenthud.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

private val DockHeight = 58.dp
private val DockBottomGap = 10.dp
private val DockSideInset = 20.dp

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
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedSession = null
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
        val closePair = {
            repository.clearPairError()
            showPairModal = false
        }
        BackHandler(onBack = closePair)
        PairScreen(
            isPairing = state.isPairing,
            pairError = state.pairError,
            canDismiss = true,
            onDismiss = closePair,
            onClearError = { repository.clearPairError() },
            onSubmitUri = { uri ->
                repository.pairFromUri(uri) {
                    showPairModal = false
                }
            },
        )
        return
    }

    BackHandler(enabled = selectedSession != null) { selectedSession = null }

    val tabStateHolder = rememberSaveableStateHolder()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockInset = navBottom + DockBottomGap + DockHeight + 20.dp

    // Push / pop between the tab shell and a session transcript along one horizontal axis.
    AnimatedContent(
        targetState = selectedSession,
        contentKey = { it?.id },
        transitionSpec = { pushPopTransition(push = targetState != null) },
        label = "sessionPushPop",
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background),
    ) { openSession ->
        if (openSession != null) {
            val reachableTarget = state.companionState?.targets?.sessions
                ?.firstOrNull { it.id == openSession.id }
            SessionDetailScreen(
                session = openSession,
                reachableTarget = reachableTarget,
                repository = repository,
                onBack = { selectedSession = null },
                onStartVoiceInput = onStartVoiceInput,
                approvals = state.pendingApprovals.filter { it.sessionId == openSession.id },
                answeringApprovalIds = state.answeringApprovalIds,
                onAnswerApproval = { id, decision -> scope.launch { repository.answerApproval(id, decision) } },
            )
            return@AnimatedContent
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalDockInset provides dockInset) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { tabTransition(forward = targetState.ordinal > initialState.ordinal) },
                    label = "tabTransition",
                    modifier = Modifier.fillMaxSize(),
                ) { tab ->
                    // Each tab keeps its own scroll position while you hop around.
                    tabStateHolder.SaveableStateProvider(tab.name) {
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
                }
            }

            // Scrim: content dissolves into black before it reaches the dock, so text never
            // collides with the dock's edge.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(dockInset + 8.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color(0xD9000000),
                            1f to Color.Black,
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = DockSideInset, end = DockSideInset, bottom = navBottom + DockBottomGap),
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
                    .padding(start = 16.dp, end = 16.dp, bottom = navBottom + DockBottomGap + DockHeight + 12.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = HudColors.SurfaceMuted,
                    contentColor = HudColors.TextPrimary,
                    actionColor = HudColors.BlueAccent,
                )
            }
        }
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

/** Restrained fade-through with a short directional drift (the dock already shows the motion). */
private fun AnimatedContentTransitionScope<NavTab>.tabTransition(forward: Boolean): ContentTransform {
    val drift = { full: Int -> (full * 0.04f).toInt() }
    val enter = fadeIn(tween(durationMillis = 220, delayMillis = 60)) +
        slideInHorizontally(spring(dampingRatio = 1f, stiffness = 600f)) { if (forward) drift(it) else -drift(it) }
    val exit = fadeOut(tween(durationMillis = 90))
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

/** iOS-style push: new page slides over from the right while the old one recedes slightly. */
private fun pushPopTransition(push: Boolean): ContentTransform {
    val slide = spring<IntOffset>(dampingRatio = 1f, stiffness = 700f)
    return if (push) {
        (slideInHorizontally(slide) { it } + fadeIn(tween(160))) togetherWith
            (slideOutHorizontally(slide) { -it / 4 } + fadeOut(tween(200)))
    } else {
        (slideInHorizontally(slide) { -it / 4 } + fadeIn(tween(200))) togetherWith
            (slideOutHorizontally(slide) { it } + fadeOut(tween(160)))
    }.apply { targetContentZIndex = if (push) 1f else 0f }
}

@Composable
private fun FloatingNavDock(
    selectedTab: NavTab,
    approvalsCount: Int,
    onSelectTab: (NavTab) -> Unit,
) {
    val tabs = NavTab.entries
    val haptics = LocalHapticFeedback.current
    val capsuleIndex = remember { Animatable(selectedTab.ordinal.toFloat()) }
    LaunchedEffect(selectedTab) {
        capsuleIndex.animateTo(selectedTab.ordinal.toFloat(), HudMotion.slide())
    }
    val dockShape = RoundedCornerShape(50)

    Row(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .height(DockHeight)
            .clip(dockShape)
            .background(HudColors.DockGlass)
            .border(1.dp, HudColors.Border, dockShape)
            .padding(4.dp)
            // The capsule is drawn, not laid out: sliding it never re-measures the dock.
            .drawBehind {
                val slot = size.width / tabs.size
                val r = size.height / 2f
                drawRoundRect(
                    color = Color(0xFF2C2C31),
                    topLeft = Offset(slot * capsuleIndex.value, 0f),
                    size = Size(slot, size.height),
                    cornerRadius = CornerRadius(r, r),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            val tint by animateColorAsState(
                targetValue = if (selected) HudColors.TextPrimary else HudColors.TextSecondary,
                animationSpec = HudMotion.tint(),
                label = "dockTint",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .bounceClick(scaleDown = 0.92f) {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(tab)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Box {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(21.dp),
                        )
                        if (tab == NavTab.SESSIONS && approvalsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-1).dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(HudColors.Terracotta),
                            )
                        }
                    }
                    Text(
                        text = tab.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
