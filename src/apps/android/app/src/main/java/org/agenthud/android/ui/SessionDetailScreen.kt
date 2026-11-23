package org.agenthud.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.ApprovalItem
import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.ReachableSessionTarget
import org.agenthud.android.data.SessionDetailResponse
import org.agenthud.android.data.SessionSummary
import org.agenthud.android.data.Transcript
import org.agenthud.android.ui.theme.HudColors
import org.agenthud.android.ui.theme.HudType
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
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Matches LanClient's upload cap; checked before a file is read into memory. */
private const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024

/** Uploads picked files, then builds the prompt that references them on the desktop. */
internal suspend fun uploadAndCompose(
    context: Context,
    repository: AgentHudRepository,
    files: List<PendingAttachment>,
    text: String,
    onProgress: (String) -> Unit = {},
): String {
    val uploadedPaths = ArrayList<String>()
    files.firstOrNull { it.sizeBytes > MAX_UPLOAD_BYTES }?.let {
        throw IllegalArgumentException("${it.name} is over the ${MAX_UPLOAD_BYTES / (1024 * 1024)} MB upload limit")
    }
    for ((idx, file) in files.withIndex()) {
        onProgress("Uploading ${file.name} (${idx + 1}/${files.size})…")
        val bytes = readUriBytes(context, file.uri)
        repository.uploadFile(file.name, bytes).getOrThrow().path?.let { uploadedPaths.add(it) }
    }
    return buildString {
        uploadedPaths.forEach { path ->
            append("Attached file: ")
            append(path)
            append('\n')
        }
        if (uploadedPaths.isNotEmpty() && text.isNotEmpty()) append('\n')
        if (text.isNotEmpty()) append(text) else if (uploadedPaths.isNotEmpty()) append("Please inspect the attached file.")
    }
}

@Composable
fun SessionDetailScreen(
    session: SessionSummary,
    reachableTarget: ReachableSessionTarget?,
    repository: AgentHudRepository,
    onBack: () -> Unit,
    onStartVoiceInput: ((String) -> Unit) -> Unit,
    approvals: List<ApprovalItem> = emptyList(),
    answeringApprovalIds: Set<String> = emptySet(),
    onAnswerApproval: (String, JsonObject) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var detail by remember(session.id) { mutableStateOf<SessionDetailResponse?>(null) }
    var messageLimit by remember(session.id) { mutableStateOf(500) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var loadError by remember(session.id) { mutableStateOf<String?>(null) }
    var draft by remember(session.id) { mutableStateOf("") }
    val attachments = remember(session.id) { mutableStateListOf<PendingAttachment>() }
    var sending by remember(session.id) { mutableStateOf(false) }
    var queuedMessage by remember(session.id) { mutableStateOf<String?>(null) }
    var sendingNow by remember(session.id) { mutableStateOf(false) }
    var sendStatus by remember(session.id) { mutableStateOf<String?>(null) }
    var sendError by remember(session.id) { mutableStateOf<String?>(null) }
    var previewFile by remember(session.id) { mutableStateOf<Pair<String, String>?>(null) }
    // Disclosure state lives here so it survives rows scrolling out of the lazy list.
    val expanded = remember(session.id) { mutableStateMapOf<String, Boolean>() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && attachments.none { it.uri == uri }) {
            attachments.add(readAttachmentMeta(context, uri))
            sendError = null
        }
    }

    var reloadJob by remember(session.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun reload(silent: Boolean = false, limit: Int = messageLimit) {
        // A background poll never overlaps another fetch, so an older answer
        // cannot land after a newer one.
        if (silent && reloadJob?.isActive == true) return
        reloadJob = scope.launch {
            if (!silent) {
                loading = true
                loadError = null
            }
            repository.fetchSessionDetail(session.id, messageLimit = limit)
                .onSuccess { fetched ->
                    detail = fetched
                    val q = queuedMessage
                    if (q != null && fetched.messages.any { it.isPrompt && it.text?.contains(q.take(60)) == true }) {
                        queuedMessage = null
                    }
                }
                .onFailure { if (!silent) loadError = it.message ?: "Failed to load session" }
            if (!silent) loading = false
        }
    }

    LaunchedEffect(session.id) { reload() }

    val messages = detail?.messages.orEmpty()
    val blocks = remember(messages) { Transcript.build(messages) }
    val providerKey = sessionIconKey(session)
    val running = session.state == "running" || detail?.session?.state == "running"

    LaunchedEffect(session.id, running, queuedMessage != null) {
        if (!running && queuedMessage == null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(3500)
            reload(silent = true)
        }
    }
    val agentName = QuotaFormatting.providerDisplayName(
        reachableTarget?.client?.ifBlank { null } ?: session.vendorName ?: session.client ?: session.source,
    )

    LaunchedEffect(blocks.size) {
        if (blocks.isNotEmpty()) listState.scrollToItem(blocks.lastIndex)
    }
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }

    val sendClient = (reachableTarget?.client?.ifBlank { null } ?: session.source).lowercase(java.util.Locale.US)
    val canSend = reachableTarget != null ||
        sendClient in setOf("claude", "codex", "antigravity", "deepseek", "pi", "opencode")
    val isClosedOnDesktop = reachableTarget == null || reachableTarget.reachable == false
    var openingOnDesktop by remember(session.id) { mutableStateOf(false) }

    fun openOnDesktop() {
        if (openingOnDesktop) return
        scope.launch {
            openingOnDesktop = true
            sendError = null
            sendStatus = "Opening session on computer…"
            repository.openSessionOnDesktop(session.id, sendClient)
                .onSuccess {
                    sendStatus = "Opened on computer"
                    reload()
                }
                .onFailure { err ->
                    sendStatus = null
                    sendError = err.message ?: "Could not open session on computer"
                }
            openingOnDesktop = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SessionTopBar(
            session = session,
            detail = detail,
            providerKey = providerKey,
            loading = loading && detail != null,
            openingOnDesktop = openingOnDesktop,
            onBack = onBack,
            onOpenOnDesktop = { openOnDesktop() },
            onRefresh = { reload() },
        )
        HudDivider()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                loading && detail == null -> CircularProgressIndicator(
                    color = HudColors.TextSecondary,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp),
                )
                loadError != null && detail == null -> Text(
                    text = loadError ?: "Unable to load transcript",
                    style = MaterialTheme.typography.bodySmall,
                    color = HudColors.Terracotta,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                blocks.isEmpty() -> Text(
                    text = "No messages captured for this session yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HudColors.TextMuted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (messages.size >= messageLimit && messageLimit < 800) {
                        item(key = "load_earlier") {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(HudColors.Surface)
                                        .border(1.dp, HudColors.Border, RoundedCornerShape(999.dp))
                                        .bounceClick(scaleDown = 0.96f, enabled = !loading) {
                                            val nextLimit = 800
                                            messageLimit = nextLimit
                                            reload(limit = nextLimit)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = if (loading) "Loading earlier messages…" else "Load earlier messages",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = HudColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                    items(items = blocks, key = { it.key }) { block ->
                        val onFile: (String, String) -> Unit = { label, uri -> previewFile = label to uri }
                        when (block) {
                            is Transcript.UserPrompt -> UserBubble(
                                message = block.message,
                                onFileClick = onFile,
                            )
                            is Transcript.AssistantText -> AssistantProse(
                                message = block.message,
                                endsTurn = block.endsTurn,
                                providerKey = providerKey,
                                onFileClick = onFile,
                            )
                            is Transcript.Thinking -> ThinkingDisclosure(
                                message = block.message,
                                expanded = expanded[block.key] == true,
                                onToggle = { expanded[block.key] = expanded[block.key] != true },
                            )
                            is Transcript.Steps -> StepsGroup(
                                block = block,
                                live = running && block === blocks.last(),
                                expanded = expanded,
                            )
                        }
                    }
                    if (running && blocks.lastOrNull() !is Transcript.Steps) {
                        item(key = "working") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BreathingDot(color = HudColors.TextSecondary, size = 7.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Working…", fontSize = 14.sp, color = HudColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            // Jump-to-latest, as in the Claude app, once you've scrolled up.
            androidx.compose.animation.AnimatedVisibility(
                visible = blocks.isNotEmpty() && !atBottom,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(HudColors.SurfaceMuted)
                        .border(1.dp, HudColors.BorderStrong, CircleShape)
                        .bounceClick(scaleDown = 0.88f) {
                            scope.launch { listState.animateScrollToItem(blocks.lastIndex.coerceAtLeast(0)) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ArrowDownward,
                        contentDescription = "Jump to latest",
                        tint = HudColors.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Bottom dock: pending approvals for this session, then the composer.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudColors.Background)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = approvals.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    approvals.forEach { approval ->
                        ApprovalCard(
                            approval = approval,
                            busy = approval.id in answeringApprovalIds,
                            onSubmitDecision = { decision -> onAnswerApproval(approval.id, decision) },
                            onStartVoiceInput = onStartVoiceInput,
                        )
                    }
                }
            }

            val status = sendError ?: sendStatus
            if (!status.isNullOrBlank()) {
                Text(
                    text = status,
                    fontSize = 12.5.sp,
                    color = if (sendError != null) HudColors.Terracotta else HudColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            val pendingQueued = queuedMessage
            if (pendingQueued != null) {
                val targetId = reachableTarget?.id?.ifBlank { null } ?: session.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HudColors.Surface)
                        .border(1.dp, HudColors.BorderStrong, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BreathingDot(color = HudColors.SageBright, size = 6.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Queued while working",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HudColors.TextSecondary,
                            )
                        }
                        Text(
                            text = pendingQueued.lineSequence().firstOrNull().orEmpty(),
                            fontSize = 13.sp,
                            color = HudColors.TextPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    // Only Antigravity can flush its own queue; resending the
                    // text elsewhere would deliver it twice.
                    if (sendClient == "antigravity") {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(HudColors.TextPrimary)
                                .bounceClick(scaleDown = 0.92f, enabled = !sendingNow) {
                                    scope.launch {
                                        sendingNow = true
                                        sendError = null
                                        sendStatus = "Sending now…"
                                        repository.sendNowToSession(sendClient, targetId)
                                            .onSuccess {
                                                queuedMessage = null
                                                sendStatus = "Sent now"
                                                reload(silent = true)
                                            }
                                            .onFailure { err ->
                                                sendStatus = null
                                                sendError = err.message ?: "Failed to send now"
                                            }
                                        sendingNow = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (sendingNow) "Sending…" else "Send now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = androidx.compose.ui.graphics.Color.Black,
                            )
                        }
                    }
                }
            }

            if (canSend && isClosedOnDesktop) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HudColors.Surface)
                        .border(1.dp, HudColors.Border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Closed on computer · sending will open it",
                        fontSize = 12.sp,
                        color = HudColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HudColors.SurfaceMuted)
                            .border(1.dp, HudColors.BorderStrong, RoundedCornerShape(999.dp))
                            .bounceClick(scaleDown = 0.94f, enabled = !openingOnDesktop) { openOnDesktop() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "Open on PC",
                            tint = HudColors.TextPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (openingOnDesktop) "Opening…" else "Open on PC",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = HudColors.TextPrimary,
                        )
                    }
                }
            }

            if (!canSend) {
                Text(
                    text = reachableTarget?.reason
                        ?: "This session is read-only from your phone. Start a new one with the New button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HudColors.TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HudColors.Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                fun submitPrompt(sendNow: Boolean) {
                    val pendingFiles = attachments.toList()
                    val textBody = draft.trim()
                    val targetId = reachableTarget?.id?.ifBlank { null } ?: session.id
                    scope.launch {
                        sending = true
                        sendError = null
                        try {
                            val finalPrompt = uploadAndCompose(context, repository, pendingFiles, textBody) {
                                sendStatus = it
                            }
                            sendStatus = when {
                                isClosedOnDesktop -> "Opening session & sending…"
                                sendNow -> "Sending now…"
                                running -> "Queueing message…"
                                else -> "Sending…"
                            }
                            val resp = repository.sendToSession(sendClient, targetId, finalPrompt, sendNow = sendNow).getOrThrow()
                            // Keep anything typed while this message was on its way.
                            if (draft.trim() == textBody) draft = ""
                            attachments.removeAll(pendingFiles)
                            if (!sendNow && (running || resp.state == "queued" || resp.state == "waiting")) {
                                queuedMessage = finalPrompt
                                sendStatus = null
                            } else {
                                queuedMessage = null
                                sendStatus = if (sendNow) "Sent now" else null
                            }
                            reload(silent = true)
                        } catch (e: Exception) {
                            sendStatus = null
                            sendError = e.message ?: "Failed to send"
                        } finally {
                            sending = false
                        }
                    }
                }

                ChatComposer(
                    value = draft,
                    onValueChange = {
                        draft = it
                        sendError = null
                    },
                    placeholder = when {
                        isClosedOnDesktop -> "Open & reply to $agentName"
                        running -> "Queue reply or send now…"
                        else -> "Reply to $agentName"
                    },
                    attachments = attachments,
                    onRemoveAttachment = { attachments.remove(it) },
                    onAttach = { filePicker.launch(arrayOf("*/*")) },
                    onVoice = {
                        onStartVoiceInput { spoken -> draft = if (draft.isBlank()) spoken else "$draft $spoken" }
                    },
                    sending = sending,
                    showSendNow = running,
                    onSendNow = { submitPrompt(sendNow = true) },
                    onSend = { submitPrompt(sendNow = false) },
                )
            }
        }
    }

    val openFile = previewFile
    if (openFile != null) {
        FilePreviewSheet(
            label = openFile.first,
            fileUri = openFile.second,
            repository = repository,
            onDismiss = { previewFile = null },
        )
    }
}

@Composable
private fun SessionTopBar(
    session: SessionSummary,
    detail: SessionDetailResponse?,
    providerKey: String,
    loading: Boolean,
    openingOnDesktop: Boolean,
    onBack: () -> Unit,
    onOpenOnDesktop: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .bounceClick(scaleDown = 0.9f, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                contentDescription = "Back",
                tint = HudColors.TextPrimary,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = session.title?.ifBlank { null } ?: displayProjectName(session.project) ?: "Session",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderIcon(providerId = providerKey, size = 12.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = listOfNotNull(
                        session.modelName ?: session.model,
                        displayProjectName(session.project)?.takeIf { session.title != null },
                        detail?.contextFill?.let { "${(it * 100.0).roundToInt()}% context" },
                    ).joinToString(" · ").ifBlank {
                        QuotaFormatting.providerDisplayName(session.vendorName ?: session.client ?: session.source)
                    },
                    fontSize = 12.sp,
                    style = HudType.Tabular,
                    color = HudColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .bounceClick(scaleDown = 0.9f, enabled = !openingOnDesktop, onClick = onOpenOnDesktop),
            contentAlignment = Alignment.Center,
        ) {
            if (openingOnDesktop) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 1.5.dp, color = HudColors.TextSecondary)
            } else {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open on computer", tint = HudColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .bounceClick(scaleDown = 0.9f, enabled = !loading, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = HudColors.TextSecondary)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = HudColors.TextSecondary, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/** Picked file waiting to be sent: a small tile, name, size and remove. */
@Composable
internal fun AttachmentChip(
    attachment: PendingAttachment,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HudColors.SurfaceMuted)
            .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTile(ext = Transcript.fileExtension(attachment.name), size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = attachment.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HudColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            val sz = formatFileSize(attachment.sizeBytes)
            if (sz.isNotEmpty()) Text(sz, fontSize = 11.sp, style = HudType.Tabular, color = HudColors.TextMuted)
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .bounceClick(scaleDown = 0.85f, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = HudColors.TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}
