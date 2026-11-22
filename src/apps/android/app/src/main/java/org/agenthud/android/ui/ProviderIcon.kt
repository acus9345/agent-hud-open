package org.agenthud.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.agenthud.android.R
import org.agenthud.android.ui.theme.HudColors
import java.util.Locale

@DrawableRes
fun providerIconRes(rawId: String): Int? {
    val norm = rawId.removePrefix("api:").trim().lowercase(Locale.US)
    return when {
        norm.contains("claude") -> R.drawable.ic_provider_claude
        norm == "codex" || norm.contains("openai") || norm.contains("chatgpt") -> R.drawable.ic_provider_chatgpt
        norm.contains("antigravity") -> R.drawable.ic_provider_antigravity
        norm.contains("deepseek") -> R.drawable.ic_provider_deepseek
        norm.contains("grok-bot") || norm.contains("grok_bot") || norm == "grok bot" -> R.drawable.ic_provider_grok_bot
        norm.contains("grok") -> R.drawable.ic_provider_grok
        norm.contains("cursor") -> R.drawable.ic_provider_cursor
        norm.contains("copilot") -> R.drawable.ic_provider_copilot
        norm.contains("cline") -> R.drawable.ic_provider_cline
        norm.contains("zcode") -> R.drawable.ic_provider_zcode
        norm.contains("glm") -> R.drawable.ic_provider_glm
        norm.contains("qwen") -> R.drawable.ic_provider_qwen
        norm.contains("minimax") -> R.drawable.ic_provider_minimax
        norm.contains("kimi") -> R.drawable.ic_provider_kimi
        norm.contains("opencode") -> R.drawable.ic_provider_opencode_dark
        norm == "pi" || norm.startsWith("pi-") -> R.drawable.ic_provider_pi
        norm.contains("trae") -> R.drawable.ic_provider_trae
        norm.contains("roo") -> R.drawable.ic_provider_roo_code
        norm.contains("kilo") -> R.drawable.ic_provider_kilocode
        norm.contains("hermes") -> R.drawable.ic_provider_hermes
        norm.contains("devin") -> R.drawable.ic_provider_devin
        norm.contains("goose") -> R.drawable.ic_provider_goose
        norm.contains("codebuddy") -> R.drawable.ic_provider_codebuddy
        norm.contains("openclaw") -> R.drawable.ic_provider_openclaw
        norm.contains("qoder") -> R.drawable.ic_provider_qoder_dark
        norm.contains("teleagent") -> R.drawable.ic_provider_teleagent
        norm.contains("workbuddy") -> R.drawable.ic_provider_workbuddy
        norm.contains("xiaomi") || norm.contains("mimo") -> R.drawable.ic_provider_xiaomimimo
        norm.contains("ark") -> R.drawable.ic_provider_ark
        else -> null
    }
}

/**
 * Renders the real official provider icon PNG (no generic colorful dots or 2-letter stubs).
 */
@Composable
fun ProviderIcon(
    providerId: String,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val res = providerIconRes(providerId)
    if (res != null) {
        Image(
            painter = painterResource(id = res),
            contentDescription = providerId,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f)),
        )
    } else {
        val accent = HudColors.providerAccent(providerId)
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = providerId.take(1).uppercase(),
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

/**
 * Subtle breathing live indicator dot for active sessions and live SSE connections.
 */
@Composable
fun BreathingDot(
    color: Color,
    size: Dp = 7.dp,
    pulsing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!pulsing) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier.size(size * 1.8f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 1.7f)
                .graphicsLayer { alpha = (1f - pulse) * 0.45f }
                .clip(CircleShape)
                .background(color),
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Smooth entrance slide + fade motion for cards and rows.
 */
@Composable
fun RevealMotion(
    key: Any = Unit,
    delayMs: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 18f
        },
    ) {
        content()
    }
}
