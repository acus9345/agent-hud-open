package org.agenthud.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.agenthud.android.ui.theme.HudColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun PairScreen(
    isPairing: Boolean,
    pairError: String?,
    canDismiss: Boolean,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSubmitUri: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showScanner by remember { mutableStateOf(false) }
    var manualLink by remember { mutableStateOf("") }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (granted) showScanner = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canDismiss) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = HudColors.TextPrimary,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Column {
                Text(
                    text = "Pair with Agent HUD",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Connect directly over your local Wi-Fi or Tailscale with pinned TLS.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // QR Scanner card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = HudColors.Surface,
            border = BorderStroke(1.dp, HudColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showScanner && cameraGranted) {
                    QrCameraPreview(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.1f)
                            .clip(RoundedCornerShape(12.dp)),
                        onQrScanned = { value ->
                            showScanner = false
                            manualLink = value
                            onSubmitUri(value)
                        },
                    )
                    OutlinedButton(
                        onClick = { showScanner = false },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, HudColors.BorderStrong),
                    ) {
                        Text("Close camera", color = HudColors.TextSecondary)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            tint = HudColors.Sage,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Scan QR code", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "On your computer open Agent HUD → Settings → Local Network → Share with paired phones → Create invitation.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            onClearError()
                            if (cameraGranted) {
                                showScanner = true
                            } else {
                                cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !isPairing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HudColors.Sage,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open QR Scanner")
                    }
                }
            }
        }

        // Paste invitation link card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = HudColors.Surface,
            border = BorderStroke(1.dp, HudColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Or paste pairing link", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = manualLink,
                    onValueChange = {
                        manualLink = it
                        onClearError()
                    },
                    placeholder = {
                        Text("agenthud://pair?payload=…", color = HudColors.TextMuted)
                    },
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HudColors.Sage,
                        unfocusedBorderColor = HudColors.BorderStrong,
                        focusedTextColor = HudColors.TextPrimary,
                        unfocusedTextColor = HudColors.TextPrimary,
                        cursorColor = HudColors.Sage,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.isNotBlank()) {
                                manualLink = pasted
                                onClearError()
                            }
                        },
                        border = BorderStroke(1.dp, HudColors.BorderStrong),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            tint = HudColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Paste", color = HudColors.TextSecondary)
                    }
                    Button(
                        onClick = { onSubmitUri(manualLink) },
                        enabled = !isPairing && manualLink.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HudColors.Sage,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.4f),
                    ) {
                        if (isPairing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Pairing…")
                        } else {
                            Text("Connect")
                        }
                    }
                }
            }
        }

        if (!pairError.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = HudColors.TerracottaSoft,
                border = BorderStroke(1.dp, HudColors.Terracotta),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = pairError,
                    style = MaterialTheme.typography.bodySmall,
                    color = HudColors.TextPrimary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val delivered = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraFuture = ProcessCameraProvider.getInstance(ctx)
                cameraFuture.addListener(
                    {
                        val provider = runCatching { cameraFuture.get() }.getOrNull() ?: return@addListener
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analyzer ->
                                analyzer.setAnalyzer(executor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && !delivered.get()) {
                                        val input = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees,
                                        )
                                        scanner.process(input)
                                            .addOnSuccessListener { codes ->
                                                val match = codes.firstNotNullOfOrNull { it.rawValue }
                                                    ?.takeIf { it.startsWith("agenthud://", ignoreCase = true) || it.startsWith("{") }
                                                if (match != null && delivered.compareAndSet(false, true)) {
                                                    ContextCompat.getMainExecutor(ctx).execute {
                                                        onQrScanned(match)
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
