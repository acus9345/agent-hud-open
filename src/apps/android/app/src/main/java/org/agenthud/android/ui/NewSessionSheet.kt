package org.agenthud.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.StartOffer
import org.agenthud.android.ui.theme.HudColors

/** New session: pick an agent and a folder, then write the first message like a new chat. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewSessionSheet(
    starts: List<StartOffer>,
    repository: AgentHudRepository,
    onDismiss: () -> Unit,
    onStartVoiceInput: ((String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedClient by remember(starts) { mutableStateOf(starts.firstOrNull()?.client.orEmpty()) }
    val currentOffer = starts.firstOrNull { it.client == selectedClient } ?: starts.firstOrNull()
    var selectedFolder by remember(selectedClient) {
        mutableStateOf(currentOffer?.folders?.firstOrNull().orEmpty())
    }
    var prompt by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<PendingAttachment>() }
    var sending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && attachments.none { it.uri == uri }) {
            attachments.add(readAttachmentMeta(context, uri))
            errorMsg = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HudColors.Surface,
        contentColor = HudColors.TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HudColors.BorderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "New session",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = HudColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 2.dp),
            )

            // Agent picker: provider marks (white template icons for Codex, Grok…).
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(starts, key = { it.client }) { offer ->
                    AgentChip(
                        client = offer.client,
                        selected = offer.client == selectedClient,
                        onClick = {
                            selectedClient = offer.client
                            selectedFolder = offer.folders.firstOrNull().orEmpty()
                            errorMsg = null
                        },
                    )
                }
            }

            val folders = currentOffer?.folders.orEmpty()
            if (folders.isNotEmpty() || currentOffer?.withoutFolder != true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Folder",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = HudColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    if (folders.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            folders.take(6).forEach { folder ->
                                FolderChip(
                                    name = displayProjectName(folder) ?: folder,
                                    selected = folder == selectedFolder,
                                    onClick = { selectedFolder = folder },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = selectedFolder,
                        onValueChange = { selectedFolder = it },
                        placeholder = { Text("Folder path on your computer", color = HudColors.TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = hudTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg.orEmpty(), fontSize = 13.sp, color = HudColors.Terracotta)
            }

            val folderOk = selectedFolder.isNotBlank() || currentOffer?.withoutFolder == true
            ChatComposer(
                value = prompt,
                onValueChange = {
                    prompt = it
                    errorMsg = null
                },
                placeholder = "How can ${QuotaFormatting.providerDisplayName(selectedClient.ifBlank { "the agent" })} help?",
                attachments = attachments,
                onRemoveAttachment = { attachments.remove(it) },
                onAttach = { filePicker.launch(arrayOf("*/*")) },
                onVoice = { onStartVoiceInput { spoken -> prompt = if (prompt.isBlank()) spoken else "$prompt $spoken" } },
                sending = sending,
                minLines = 3,
                enabled = selectedClient.isNotBlank() && folderOk,
                onSend = {
                    val clientName = selectedClient.ifBlank { return@ChatComposer }
                    val pendingFiles = attachments.toList()
                    val textBody = prompt.trim()
                    scope.launch {
                        sending = true
                        errorMsg = null
                        try {
                            val finalPrompt = uploadAndCompose(context, repository, pendingFiles, textBody)
                            repository.startNewSession(clientName, selectedFolder.trim(), finalPrompt).getOrThrow()
                            onDismiss()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Could not start session"
                        } finally {
                            sending = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AgentChip(client: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) HudColors.SurfaceMuted else Color.Transparent, HudMotion.tint(), label = "agentBg")
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) HudColors.BorderStrong else HudColors.Border, shape)
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderIcon(providerId = client, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = QuotaFormatting.providerDisplayName(client),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) HudColors.TextPrimary else HudColors.TextSecondary,
        )
    }
}

@Composable
private fun FolderChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) HudColors.SurfaceMuted else Color.Transparent, HudMotion.tint(), label = "folderBg")
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) HudColors.BorderStrong else HudColors.Border, shape)
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = if (selected) HudColors.TextPrimary else HudColors.TextMuted,
            modifier = Modifier.padding(end = 7.dp).width(15.dp),
        )
        Text(
            text = name,
            fontSize = 13.5.sp,
            color = if (selected) HudColors.TextPrimary else HudColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
