package org.agenthud.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agenthud.android.data.AppUiState
import org.agenthud.android.data.ConnectionState
import org.agenthud.android.data.PairedDevice
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.ui.theme.HudColors

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "settings-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Button(
                    onClick = onAddDevice,
                    colors = ButtonDefaults.buttonColors(containerColor = HudColors.BlueAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pair")
                }
            }
        }

        // Paired computers grouped card
        item(key = "paired-group") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paired computers", style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = HudColors.Surface,
                    border = BorderStroke(1.dp, HudColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        state.devices.forEachIndexed { idx, device ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = HudColors.Border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                )
                            }
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
        }

        // Remote access (away from Wi-Fi) card
        item(key = "remote-help") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect away from Wi-Fi", style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = HudColors.Surface,
                    border = BorderStroke(1.dp, HudColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Tailscale or Remote Tunnel", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Tap the globe icon on any paired computer to add a remote https:// URL (Tailscale 100.x IP, Cloudflare Tunnel, or VPS relay). The app tries local Wi-Fi first and fails over automatically on 5G.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // Live monitor + Widget settings
        item(key = "preferences-group") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications & widget", style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = HudColors.Surface,
                    border = BorderStroke(1.dp, HudColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Live monitor & approval alerts", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Ongoing status bar monitor with instant Allow / Deny actions.",
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
                                    uncheckedThumbColor = HudColors.TextMuted,
                                    uncheckedTrackColor = HudColors.Track,
                                ),
                            )
                        }
                        HorizontalDivider(color = HudColors.Border, thickness = 1.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Home Screen Widget", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Long-press your home screen → Widgets → Agent HUD to pin live quotas.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    val remoteDevice = editingRemoteFor
    if (remoteDevice != null) {
        AlertDialog(
            onDismissRequest = { editingRemoteFor = null },
            containerColor = HudColors.SurfaceElevated,
            title = { Text("Remote access · ${remoteDevice.deviceName}", color = HudColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("lan" to "LAN", "tailscale" to "Tailscale", "relay" to "Relay").forEach { (id, label) ->
                            val selected = remoteModeDraft == id
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) HudColors.BlueSoft else HudColors.Surface,
                                border = BorderStroke(1.dp, if (selected) HudColors.BlueAccent else HudColors.BorderStrong),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { remoteModeDraft = id },
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        color = if (selected) HudColors.TextPrimary else HudColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }

                    if (remoteModeDraft == "tailscale") {
                        Text(
                            text = "Uses your computer's Tailscale 100.x address automatically, or enter a custom Tailscale URL below.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = remoteDraft,
                            onValueChange = { remoteDraft = it },
                            placeholder = { Text("https://100.64.0.10:49210 (optional)", color = HudColors.TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HudColors.BlueAccent,
                                unfocusedBorderColor = HudColors.BorderStrong,
                                focusedTextColor = HudColors.TextPrimary,
                                unfocusedTextColor = HudColors.TextPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (remoteModeDraft == "relay") {
                        Text(
                            text = "End-to-end TLS passthrough: the relay forwards encrypted bytes by SNI and never sees plaintext.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = remoteDraft,
                            onValueChange = { remoteDraft = it },
                            label = { Text("Relay URL", color = HudColors.TextMuted) },
                            placeholder = { Text("https://hud.qlinter.org", color = HudColors.TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HudColors.BlueAccent,
                                unfocusedBorderColor = HudColors.BorderStrong,
                                focusedTextColor = HudColors.TextPrimary,
                                unfocusedTextColor = HudColors.TextPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = fallbackIpDraft,
                            onValueChange = { fallbackIpDraft = it },
                            label = { Text("Server IP fallback (optional)", color = HudColors.TextMuted) },
                            placeholder = { Text("40.27.47.162", color = HudColors.TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HudColors.BlueAccent,
                                unfocusedBorderColor = HudColors.BorderStrong,
                                focusedTextColor = HudColors.TextPrimary,
                                unfocusedTextColor = HudColors.TextPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "Connect only when on the same local Wi-Fi network.",
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                    Text("Save", color = HudColors.BlueAccent)
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
            title = { Text("Remove ${toDelete.deviceName}?", color = HudColors.TextPrimary) },
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
                    Text("Remove", color = HudColors.Terracotta)
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
private fun PairedDeviceRow(
    device: PairedDevice,
    isActive: Boolean,
    connectionState: ConnectionState?,
    onSelect: () -> Unit,
    onEditRemote: () -> Unit,
    onRequestRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Active",
                        tint = HudColors.SageBright,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = device.lastActiveEndpoint ?: device.endpoint,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = HudColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!device.remoteEndpoint.isNullOrBlank()) {
                Text(
                    text = "Remote: ${device.remoteEndpoint}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    color = HudColors.BlueAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TLS ${device.certificateSha256.take(12)}…",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (connectionState != null) {
                    val color = when (connectionState) {
                        ConnectionState.LIVE_SSE, ConnectionState.POLLING -> HudColors.SageBright
                        ConnectionState.CONNECTING -> HudColors.TextMuted
                        else -> HudColors.Terracotta
                    }
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
                    Text(
                        text = connectionState.name.lowercase().replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                } else {
                    Text(
                        text = "Paired ${QuotaFormatting.formatRelativeMs(device.pairedAtMs)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        IconButton(onClick = onEditRemote) {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = "Remote endpoint",
                tint = if (!device.remoteEndpoint.isNullOrBlank()) HudColors.BlueAccent else HudColors.TextMuted,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onRequestRemove) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Remove computer",
                tint = HudColors.TextMuted,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
