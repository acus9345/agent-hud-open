package org.agenthud.android.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.agenthud.android.data.AgentHudRepository
import org.agenthud.android.data.FileChunkResponse
import org.agenthud.android.data.Transcript
import org.agenthud.android.ui.theme.HudColors
import java.io.File
import java.nio.charset.StandardCharsets

private const val AUTO_PREVIEW_MAX_BYTES = 4 * 1024 * 1024L
private const val TEXT_PREVIEW_MAX_BYTES = 512 * 1024L
private const val SAVE_MAX_BYTES = 150 * 1024 * 1024L
private const val CLIPBOARD_MAX_CHARS = 200_000
private const val IMAGE_MAX_EDGE_PX = 2048

/** Claude-artifact-style sheet for a desktop file the agent linked: preview, copy, save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewSheet(
    label: String,
    fileUri: String,
    repository: AgentHudRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var meta by remember(fileUri) { mutableStateOf<FileChunkResponse?>(null) }
    var fileBytes by remember(fileUri) { mutableStateOf<ByteArray?>(null) }
    var loading by remember(fileUri) { mutableStateOf(true) }
    var downloading by remember(fileUri) { mutableStateOf(false) }
    var progress by remember(fileUri) { mutableFloatStateOf(0f) }
    var errorMsg by remember(fileUri) { mutableStateOf<String?>(null) }
    var savedMsg by remember(fileUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(fileUri) {
        loading = true
        errorMsg = null
        repository.inspectDesktopFile(fileUri)
            .onSuccess { info ->
                meta = info
                val shouldPreview = (isPreviewableImage(info.mimeType) && info.size <= AUTO_PREVIEW_MAX_BYTES) ||
                    (isText(info.mimeType) && info.size <= TEXT_PREVIEW_MAX_BYTES)
                if (shouldPreview) {
                    repository.downloadDesktopFile(fileUri) { done, total ->
                        if (total > 0L) progress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }.onSuccess { (_, _, bytes) -> fileBytes = bytes }
                        .onFailure { err -> errorMsg = err.message ?: "Failed to load preview" }
                }
            }
            .onFailure { err -> errorMsg = err.message ?: "Could not access file on desktop" }
        loading = false
    }

    val title = meta?.filename?.ifBlank { null } ?: Transcript.fileName(label.ifBlank { fileUri })
    val ext = Transcript.fileExtension(title)
    val mime = meta?.mimeType.orEmpty()

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
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTile(ext = ext, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HudColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val info = listOfNotNull(
                        fileKind(ext),
                        meta?.size?.takeIf { it > 0L }?.let { formatFileSize(it) },
                    ).joinToString(" · ")
                    if (info.isNotBlank()) Text(info, fontSize = 12.5.sp, color = HudColors.TextMuted)
                }
            }

            if (loading || downloading) {
                if (progress > 0f && progress < 1f) {
                    HudProgressBar(progress = progress, color = HudColors.TextPrimary)
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HudColors.TextSecondary, strokeWidth = 2.dp)
                    }
                }
            }

            errorMsg?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = HudColors.DiffDelFg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HudColors.TerracottaSoft)
                        .padding(12.dp),
                )
            }

            val bytes = fileBytes
            when {
                bytes != null && isPreviewableImage(mime) -> {
                    var bitmap by remember(bytes) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(bytes) {
                        bitmap = withContext(Dispatchers.Default) { decodeDownsampled(bytes) }
                    }
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .clip(shape)
                            .background(HudColors.CodeSurface)
                            .border(1.dp, HudColors.Border, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = bitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 440.dp),
                            )
                        } else {
                            CircularProgressIndicator(color = HudColors.TextSecondary, strokeWidth = 2.dp)
                        }
                    }
                }
                bytes != null && isText(mime) -> {
                    val text = remember(bytes) { String(bytes, StandardCharsets.UTF_8) }
                    if (ext == "md" || mime == "text/markdown") {
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp)
                                .clip(shape)
                                .background(HudColors.CodeSurface)
                                .border(1.dp, HudColors.Border, shape)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            MarkdownText(markdown = text.take(40_000), fontSize = 14.5.sp, lineHeight = 22.sp)
                        }
                    } else {
                        CodeOutput(text = text, label = ext.ifBlank { "text" }, maxHeight = 460.dp)
                    }
                }
                !loading && errorMsg == null && meta != null -> Text(
                    text = "No preview for this file type or size. Save it to your phone to open it.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HudColors.SurfaceElevated)
                        .padding(14.dp),
                )
            }

            savedMsg?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.5.sp, color = HudColors.SageBright)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bytes != null && isText(mime)) {
                    PillButton(
                        text = "Copy",
                        container = Color.Transparent,
                        content = HudColors.TextPrimary,
                        outlined = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        val text = String(bytes, StandardCharsets.UTF_8)
                        // Clipboard payloads travel over binder (~1 MB); cap rather than crash.
                        val ok = runCatching { clipboard.setText(AnnotatedString(text.take(CLIPBOARD_MAX_CHARS))) }.isSuccess
                        savedMsg = when {
                            !ok -> null
                            text.length > CLIPBOARD_MAX_CHARS -> "Copied the first ${CLIPBOARD_MAX_CHARS / 1000}K characters"
                            else -> "Copied to clipboard"
                        }
                        if (!ok) errorMsg = "File is too large to copy"
                    }
                }
                PillButton(
                    text = if (downloading) "Saving…" else "Save to phone",
                    container = HudColors.TextPrimary,
                    content = Color.Black,
                    enabled = !loading && !downloading && meta != null,
                    modifier = Modifier.weight(1.3f),
                ) {
                    val size = meta?.size ?: 0L
                    if (size > SAVE_MAX_BYTES) {
                        errorMsg = "Files over ${SAVE_MAX_BYTES / (1024 * 1024)} MB can't be saved from the phone."
                        return@PillButton
                    }
                    scope.launch {
                        downloading = true
                        errorMsg = null
                        savedMsg = null
                        try {
                            val (dlMime, dlBytes) = fileBytes?.let { mime.ifBlank { "application/octet-stream" } to it }
                                ?: repository.downloadDesktopFile(fileUri) { done, total ->
                                    if (total > 0L) progress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                }.getOrThrow().let { (_, m, b) -> m to b }
                            val location = saveToAndroidDownloads(context, title, dlMime, dlBytes)
                            savedMsg = "Saved to $location"
                        } catch (e: OutOfMemoryError) {
                            errorMsg = "Not enough memory to download this file."
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Download failed"
                        } finally {
                            downloading = false
                        }
                    }
                }
            }
        }
    }
}

private fun isPreviewableImage(mime: String) = mime.startsWith("image/") && mime != "image/svg+xml"

private fun isText(mime: String) = mime.startsWith("text/") || mime == "application/json"

/** Decodes at most ~2048px on the long edge so huge images neither OOM nor exceed canvas limits. */
private fun decodeDownsampled(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= IMAGE_MAX_EDGE_PX || bounds.outHeight / (sample * 2) >= IMAGE_MAX_EDGE_PX) {
        sample *= 2
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}.getOrNull()

/** Keeps a desktop-supplied name to a single safe path segment. */
private fun safeFileName(name: String): String =
    Transcript.fileName(name).replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim('.', ' ').ifBlank { "download" }

private suspend fun saveToAndroidDownloads(
    context: Context,
    filename: String,
    mimeType: String,
    bytes: ByteArray,
): String = withContext(Dispatchers.IO) {
    val name = safeFileName(filename)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Agent HUD")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create download entry")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Could not write download file")
        "Downloads/Agent HUD/$name"
    } else {
        // Android 8-9: the app's own Downloads folder needs no storage permission.
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("Storage is unavailable")
        dir.mkdirs()
        val outFile = File(dir, name)
        outFile.writeBytes(bytes)
        outFile.absolutePath
    }
}
