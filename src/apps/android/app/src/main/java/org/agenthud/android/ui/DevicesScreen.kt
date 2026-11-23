package org.agenthud.android.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agenthud.android.data.AppUiState
import org.agenthud.android.data.ConnectionState
import org.agenthud.android.data.PairedDevice
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.ui.theme.HudColors
import org.agenthud.android.ui.theme.HudMono

@Composable
fun DevicesScreen(
    state: AppUiState,
    onSelectDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onSaveRemoteConfig: (deviceId: String, mode: String, url: String, fallbackIp: String) -> Unit,
    onToggleLiveMonitor: (Boolean) -> Unit,
    onAddDevice: () -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<PairedDevice?>(null) }
    var editingRemoteFor by remember { mutableStateOf<PairedDevice?>(null) }
    var remoteModeDraft by remember { mutableStateOf("tailscale") }
    var remoteDraft by remember { mutableStateOf("") }
    var fallbackIpDraft by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "settings-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(HudColors.SurfaceElevated)
                        .bounceClick(scaleDown = 0.94f, onClick = onAddDevice)
                        .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = HudColors.BlueAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pair", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = HudColors.BlueAccent)
                }
            }
        }

        item(key = "paired-header") { SectionHeader(title = "Computers") }
        item(key = "paired-group") {
            HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Column {
                    state.devices.forEachIndexed { idx, device ->
                        if (idx > 0) HudDivider(Modifier.padding(start = 34.dp))
                        val isActive = device.deviceId == state.activeDevice?.deviceId
                        PairedDeviceRow(
                            device = device,
                            isActive = isActive,
                            connectionState = if (isActive) state.connectionState else null,
                            onSelect = { onSelectDevice(device.deviceId) },
                            onEditRemote = {
                                editingRemoteFor = device
                                remoteModeDraft = device.remoteMode
                                remoteDraft = device.remoteEndpoint.orEmpty()
                                fallbackIpDraft = device.remoteFallbackIp.orEmpty()
                            },
                            onRequestRemove = { pendingRemoval = device },
                        )
                    }
                }
            }
        }
        item(key = "paired-footnote") {
            Footnote(
                "Tap the globe on a computer to reach it away from Wi-Fi through Tailscale or a relay. " +
                    "The app tries local Wi-Fi first and fails over on its own.",
            )
        }

        item(key = "prefs-header") { SectionHeader(title = "Notifications & widget") }
        item(key = "prefs-group") {
            HudCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Live monitor & approval alerts", fontSize = 14.5.sp, color = HudColors.TextPrimary)
                            Text(
                                text = "Ongoing notification with Allow and Deny actions.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.liveMonitorEnabled,
                            onCheckedChange = onToggleLiveMonitor,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HudColors.SageBright,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = HudColors.TextSecondary,
                                uncheckedTrackColor = HudColors.SurfaceMuted,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                    HudDivider(Modifier.padding(start = 16.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Home screen widget", fontSize = 14.5.sp, color = HudColors.TextPrimary)
                        Text(
                            text = "Long-press your home screen, open Widgets, and add Agent HUD.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    val remoteDevice = editingRemoteFor
    if (remoteDevice != null) {
        val modeOptions = remember {
            listOf("lan" to "LAN", "tailscale" to "Tailscale", "relay" to "Relay")
        }
        AlertDialog(
            onDismissRequest = { editingRemoteFor = null },
            containerColor = HudColors.SurfaceElevated,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Remote access · ${remoteDevice.deviceName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudColors.TextPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SlidingSegmentedSelector(
                        options = modeOptions,
                        selected = modeOptions.firstOrNull { it.first == remoteModeDraft } ?: modeOptions[1],
                        onSelect = { remoteModeDraft = it.first },
                        labelFor = { it.second },
                        equalWidth = true,
                        itemVerticalPadding = 7.dp,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (remoteModeDraft) {
                        "tailscale" -> {
                            Text(
                                text = "Uses your computer's Tailscale 100.x address automatically, or enter a custom Tailscale URL below.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HudTextField(
                                value = remoteDraft,
                                onValueChange = { remoteDraft = it },
                                placeholder = "https://100.64.0.10:49210 (optional)",
                            )
                        }
                        "relay" -> {
                            Text(
                                text = "End-to-end TLS passthrough: the relay forwards encrypted bytes by SNI and never sees plaintext.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HudTextField(
                                value = remoteDraft,
                                onValueChange = { remoteDraft = it },
                                label = "Relay URL",
                                placeholder = "https://hud.example.com",
                            )
                            HudTextField(
                                value = fallbackIpDraft,
                                onValueChange = { fallbackIpDraft = it },
                                label = "Server IP fallback (optional)",
                                placeholder = "203.0.113.10",
                            )
                        }
                        else -> {
                            Text(
                                text = "Connect only when on the same local Wi-Fi network.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveRemoteConfig(remoteDevice.deviceId, remoteModeDraft, remoteDraft, fallbackIpDraft)
                        editingRemoteFor = null
                    },
                ) {
                    Text("Save", color = HudColors.BlueAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRemoteFor = null }) {
                    Text("Cancel", color = HudColors.TextSecondary)
                }
            },
        )
    }

    val toDelete = pendingRemoval
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = HudColors.SurfaceElevated,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Remove ${toDelete.deviceName}?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudColors.TextPrimary,
                )
            },
            text = {
                Text(
                    "Deletes the stored pairing token and TLS pin from this phone.",
                    color = HudColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemoveDevice(toDelete.deviceId)
                    },
                ) {
                    Text("Remove", color = HudColors.Terracotta, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Cancel", color = HudColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = HudColors.TextMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it, color = HudColors.TextMuted) } },
        placeholder = { Text(placeholder, color = HudColors.TextMuted) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = hudTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PairedDeviceRow(
    device: PairedDevice,
    isActive: Boolean,
    connectionState: ConnectionState?,
    onSelect: () -> Unit,
    onEditRemote: () -> Unit,
    onRequestRemove: () -> Unit,
) {
    val statusColor = when (connectionState) {
        ConnectionState.LIVE_SSE, ConnectionState.POLLING -> HudColors.SageBright
        ConnectionState.CONNECTING, null -> HudColors.TextMuted
        else -> HudColors.Terracotta
    }
    val statusText = when (connectionState) {
        ConnectionState.LIVE_SSE -> "Live"
        ConnectionState.POLLING -> "Connected"
        ConnectionState.CONNECTING -> "Connecting…"
        ConnectionState.UNAUTHORIZED -> "Pairing revoked"
        ConnectionState.ERROR -> "Unreachable"
        ConnectionState.DISCONNECTED -> "Disconnected"
        null -> "Paired ${QuotaFormatting.formatRelativeMs(device.pairedAtMs)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.985f, onClick = onSelect)
            .padding(start = 16.dp, end = 6.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(18.dp), contentAlignment = Alignment.CenterStart) {
            if (isActive) {
                BreathingDot(
                    color = statusColor,
                    size = 8.dp,
                    pulsing = connectionState == ConnectionState.LIVE_SSE,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(HudColors.SurfaceMuted),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.deviceName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = statusText, fontSize = 12.sp, color = if (isActive) statusColor else HudColors.TextMuted)
            }
            Text(
                text = device.lastActiveEndpoint ?: device.endpoint,
                fontFamily = HudMono,
                fontSize = 11.5.sp,
                color = HudColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!device.remoteEndpoint.isNullOrBlank()) {
                Text(
                    text = "Remote ${device.remoteEndpoint}",
                    fontFamily = HudMono,
                    fontSize = 11.sp,
                    color = HudColors.BlueAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "TLS ${device.certificateSha256.take(12)}…",
                fontFamily = HudMono,
                fontSize = 10.5.sp,
                color = HudColors.TextMuted,
            )
        }

        RowIconButton(
            icon = Icons.Outlined.Public,
            description = "Remote endpoint",
            tint = if (!device.remoteEndpoint.isNullOrBlank()) HudColors.BlueAccent else HudColors.TextSecondary,
            onClick = onEditRemote,
        )
        RowIconButton(
            icon = Icons.Outlined.DeleteOutline,
            description = "Remove computer",
            tint = HudColors.TextMuted,
            onClick = onRequestRemove,
        )
    }
}

@Composable
private fun RowIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .bounceClick(scaleDown = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}
