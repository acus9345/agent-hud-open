package org.agenthud.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.ReachableSessionTarget
import org.agenthud.android.data.SessionDetailResponse
import org.agenthud.android.data.SessionMessageItem
import org.agenthud.android.data.SessionSummary
import org.agenthud.android.ui.theme.HudColors
import kotlin.math.roundToInt

internal data class PendingAttachment(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
)

internal fun readAttachmentMeta(context: Context, uri: Uri): PendingAttachment {
    var name = "attachment"
    var size = 0L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
    return PendingAttachment(uri = uri, name = name, sizeBytes = size)
}

internal suspend fun readUriBytes(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Cannot read selected file")
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
fun SessionDetailScreen(
    session: SessionSummary,
    reachableTarget: ReachableSessionTarget?,
    repository: AgentHudRepository,
    onBack: () -> Unit,
    onStartVoiceInput: ((String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var detail by remember(session.id) { mutableStateOf<SessionDetailResponse?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var loadError by remember(session.id) { mutableStateOf<String?>(null) }
    var draft by remember(session.id) { mutableStateOf("") }
    val attachments = remember(session.id) { mutableStateListOf<PendingAttachment>() }
    var sending by remember(session.id) { mutableStateOf(false) }
    var sendStatus by remember(session.id) { mutableStateOf<String?>(null) }
    var sendError by remember(session.id) { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            attachments.add(readAttachmentMeta(context, uri))
            sendError = null
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            loadError = null
            repository.fetchSessionDetail(session.id)
                .onSuccess { detail = it }
                .onFailure { loadError = it.message ?: "Failed to load session" }
            loading = false
        }
    }

    LaunchedEffect(session.id) {
        reload()
    }

    val messages = detail?.messages.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    val canSend = reachableTarget != null && reachableTarget.reachable != false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // Minimal clean header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = HudColors.TextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title?.ifBlank { null } ?: session.project ?: "Session",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    (session.vendorName ?: session.client ?: session.source)
                        .takeIf { it.isNotBlank() }
                        ?.let { QuotaFormatting.providerDisplayName(it) },
                    session.modelName ?: session.model,
                    detail?.contextFill?.let { "Ctx ${(it * 100.0).roundToInt()}%" },
                    detail?.turnCount?.let { "$it turns" },
                ).joinToString(" · ")
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = HudColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { reload() }) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = HudColors.TextMuted,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        HorizontalDivider(color = HudColors.Border, thickness = 1.dp)

        // Clean reading transcript
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && detail == null -> {
                    CircularProgressIndicator(
                        color = HudColors.SageBright,
                        strokeWidth = 2.dp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                loadError != null && detail == null -> {
                    Text(
                        text = loadError ?: "Unable to load transcript",
                        style = MaterialTheme.typography.bodySmall,
                        color = HudColors.Terracotta,
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    )
                }
                messages.isEmpty() -> {
                    Text(
                        text = "No messages captured for this session yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HudColors.TextMuted,
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(
                            items = messages,
                            key = { it.id.ifBlank { "${it.role}-${it.timestamp}-${it.text?.hashCode()}" } },
                        ) { msg ->
                            ChatMessageView(msg)
                        }
                    }
                }
            }
        }

        // Sleek bottom composer with File Attach + Voice + Send
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudColors.Background)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!sendError.isNullOrBlank()) {
                Text(
                    text = sendError.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = HudColors.Terracotta,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            if (!sendStatus.isNullOrBlank()) {
                Text(
                    text = sendStatus.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudColors.SageBright,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            if (attachments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(attachments, key = { it.uri.toString() }) { att ->
                        AttachmentChip(
                            attachment = att,
                            onRemove = { attachments.remove(att) },
                        )
                    }
                }
            }

            if (!canSend) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HudColors.Surface,
                    border = BorderStroke(1.dp, HudColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = reachableTarget?.reason
                            ?: "This session is read-only from mobile. Start a new session with the New button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HudColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = HudColors.SurfaceElevated,
                    border = BorderStroke(1.dp, HudColors.BorderStrong),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = !sending,
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach file or photo",
                                tint = HudColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Message ${QuotaFormatting.providerDisplayName(reachableTarget?.client.orEmpty())}…",
                                    fontSize = 14.sp,
                                    color = HudColors.TextMuted,
                                )
                            }
                            BasicTextField(
                                value = draft,
                                onValueChange = {
                                    draft = it
                                    sendError = null
                                },
                                textStyle = TextStyle(
                                    color = HudColors.TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                cursorBrush = SolidColor(HudColors.BlueAccent),
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        IconButton(
                            enabled = !sending,
                            onClick = {
                                onStartVoiceInput { spoken ->
                                    draft = if (draft.isBlank()) spoken else "$draft $spoken"
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Voice input",
                                tint = HudColors.TextSecondary,
                                modifier = Modifier.size(19.dp),
                            )
                        }

                        val canSubmit = !sending && (draft.isNotBlank() || attachments.isNotEmpty())
                        Surface(
                            shape = CircleShape,
                            color = if (canSubmit) HudColors.BlueAccent else HudColors.SurfaceMuted,
                            modifier = Modifier
                                .size(34.dp)
                                .clickable(enabled = canSubmit) {
                                    val target = reachableTarget ?: return@clickable
                                    val pendingFiles = attachments.toList()
                                    val textBody = draft.trim()
                                    scope.launch {
                                        sending = true
                                        sendError = null
                                        try {
                                            val uploadedPaths = ArrayList<String>()
                                            for ((idx, file) in pendingFiles.withIndex()) {
                                                sendStatus = "Uploading ${file.name} (${idx + 1}/${pendingFiles.size})…"
                                                val bytes = readUriBytes(context, file.uri)
                                                val uploaded = repository.uploadFile(file.name, bytes).getOrThrow()
                                                uploaded.path?.let { uploadedPaths.add(it) }
                                            }
                                            sendStatus = "Sending…"
                                            val finalPrompt = buildString {
                                                if (uploadedPaths.isNotEmpty()) {
                                                    uploadedPaths.forEach { path ->
                                                        append("Attached file: ")
                                                        append(path)
                                                        append('\n')
                                                    }
                                                    if (textBody.isNotEmpty()) append('\n')
                                                }
                                                if (textBody.isNotEmpty()) {
                                                    append(textBody)
                                                } else if (uploadedPaths.isNotEmpty()) {
                                                    append("Please inspect the attached file.")
                                                }
                                            }
                                            repository.sendToSession(target.client, target.id, finalPrompt).getOrThrow()
                                            draft = ""
                                            attachments.clear()
                                            sendStatus = null
                                            reload()
                                        } catch (e: Exception) {
                                            sendStatus = null
                                            sendError = e.message ?: "Failed to send"
                                        } finally {
                                            sending = false
                                        }
                                    }
                                },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (sending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = "Send",
                                        tint = if (canSubmit) Color.White else HudColors.TextMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AttachmentChip(
    attachment: PendingAttachment,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = HudColors.SurfaceElevated,
        border = BorderStroke(1.dp, HudColors.BorderStrong),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = HudColors.SageBright,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = attachment.name,
                fontSize = 12.sp,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sz = formatFileSize(attachment.sizeBytes)
            if (sz.isNotEmpty()) {
                Text(sz, fontSize = 10.5.sp, color = HudColors.TextMuted)
            }
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove",
                tint = HudColors.TextMuted,
                modifier = Modifier
                    .size(15.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun ChatMessageView(message: SessionMessageItem) {
    val isUser = message.isPrompt || message.role.equals("user", ignoreCase = true)
    val clipboard = LocalClipboardManager.current
    val content = message.text.orEmpty().trim()
    if (content.isEmpty()) return

    if (isUser) {
        // Sleek right-aligned user bubble
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = HudColors.SurfaceElevated,
                border = BorderStroke(1.dp, HudColors.Border),
                modifier = Modifier.fillMaxWidth(0.84f),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MarkdownText(markdown = content, textColor = HudColors.TextPrimary)
                }
            }
        }
    } else {
        // Full-width clean assistant reading view (no cramped outer border!)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(HudColors.SageBright),
                    )
                    Text(
                        text = message.role.replaceFirstChar { it.uppercase() }.ifBlank { "Assistant" },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HudColors.TextMuted,
                    )
                    val ts = QuotaFormatting.formatRelativeTime(message.timestamp)
                    if (ts.isNotBlank()) {
                        Text("· $ts", fontSize = 11.sp, color = HudColors.TextMuted)
                    }
                }
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(content)) },
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy message",
                        tint = HudColors.TextMuted,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            MarkdownText(markdown = content, textColor = HudColors.TextPrimary)
        }
    }
}
