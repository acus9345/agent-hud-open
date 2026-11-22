package org.agenthud.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.StartOffer
import org.agenthud.android.ui.theme.HudColors

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

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            attachments.add(readAttachmentMeta(context, uri))
            errorMsg = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HudColors.SurfaceElevated,
        contentColor = HudColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New session", style = MaterialTheme.typography.headlineSmall)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Agent", style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    starts.forEach { offer ->
                        FilterChip(
                            selected = offer.client == selectedClient,
                            onClick = {
                                selectedClient = offer.client
                                selectedFolder = offer.folders.firstOrNull().orEmpty()
                                errorMsg = null
                            },
                            label = { Text(QuotaFormatting.providerDisplayName(offer.client)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HudColors.BlueSoft,
                                selectedLabelColor = HudColors.TextPrimary,
                                containerColor = HudColors.Surface,
                                labelColor = HudColors.TextSecondary,
                            ),
                        )
                    }
                }
            }

            val folders = currentOffer?.folders.orEmpty()
            if (folders.isNotEmpty() || currentOffer?.withoutFolder != true) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Project folder", style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
                    if (folders.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            folders.take(6).forEach { folder ->
                                val shortName = folder.substringAfterLast('/').substringAfterLast('\\').ifBlank { folder }
                                FilterChip(
                                    selected = folder == selectedFolder,
                                    onClick = { selectedFolder = folder },
                                    label = { Text(shortName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = HudColors.BlueSoft,
                                        selectedLabelColor = HudColors.TextPrimary,
                                        containerColor = HudColors.Surface,
                                        labelColor = HudColors.TextSecondary,
                                    ),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = selectedFolder,
                        onValueChange = { selectedFolder = it },
                        placeholder = { Text("Folder path on desktop", color = HudColors.TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HudColors.BlueAccent,
                            unfocusedBorderColor = HudColors.BorderStrong,
                            focusedTextColor = HudColors.TextPrimary,
                            unfocusedTextColor = HudColors.TextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prompt", style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        prompt = it
                        errorMsg = null
                    },
                    placeholder = { Text("Describe the task or dictate…", color = HudColors.TextMuted) },
                    minLines = 3,
                    maxLines = 6,
                    trailingIcon = {
                        IconButton(onClick = {
                            onStartVoiceInput { spoken ->
                                prompt = if (prompt.isBlank()) spoken else "$prompt $spoken"
                            }
                        }) {
                            Icon(Icons.Outlined.Mic, contentDescription = "Dictate prompt", tint = HudColors.TextSecondary)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HudColors.BlueAccent,
                        unfocusedBorderColor = HudColors.BorderStrong,
                        focusedTextColor = HudColors.TextPrimary,
                        unfocusedTextColor = HudColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (attachments.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(attachments, key = { it.uri.toString() }) { att ->
                            AttachmentChip(attachment = att, onRemove = { attachments.remove(att) })
                        }
                    }
                }

                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !sending,
                    border = BorderStroke(1.dp, HudColors.BorderStrong),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = HudColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach file or photo", color = HudColors.TextSecondary)
                }
            }

            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = HudColors.Terracotta)
            }

            val folderOk = selectedFolder.isNotBlank() || currentOffer?.withoutFolder == true
            val contentOk = prompt.isNotBlank() || attachments.isNotEmpty()
            Button(
                onClick = {
                    val clientName = selectedClient.ifBlank { return@Button }
                    val pendingFiles = attachments.toList()
                    val textBody = prompt.trim()
                    scope.launch {
                        sending = true
                        errorMsg = null
                        try {
                            val uploadedPaths = ArrayList<String>()
                            for (file in pendingFiles) {
                                val bytes = readUriBytes(context, file.uri)
                                val uploaded = repository.uploadFile(file.name, bytes).getOrThrow()
                                uploaded.path?.let { uploadedPaths.add(it) }
                            }
                            val finalPrompt = buildString {
                                if (uploadedPaths.isNotEmpty()) {
                                    uploadedPaths.forEach { path ->
                                        append("Attached file: ")
                                        append(path)
                                        append('\n')
                                    }
                                    if (textBody.isNotEmpty()) append('\n')
                                }
                                if (textBody.isNotEmpty()) append(textBody)
                                else append("Please inspect the attached file.")
                            }
                            repository.startNewSession(clientName, selectedFolder.trim(), finalPrompt).getOrThrow()
                            onDismiss()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Could not start session"
                        } finally {
                            sending = false
                        }
                    }
                },
                enabled = !sending && selectedClient.isNotBlank() && folderOk && contentOk,
                colors = ButtonDefaults.buttonColors(containerColor = HudColors.BlueAccent, contentColor = Color.White),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Starting…")
                } else {
                    Text("Start session")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
