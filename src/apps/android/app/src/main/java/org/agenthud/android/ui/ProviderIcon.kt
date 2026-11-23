package org.agenthud.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agenthud.android.R
import org.agenthud.android.ui.theme.HudColors
import java.util.Locale

@DrawableRes
fun providerIconRes(rawId: String): Int? {
    val norm = rawId.removePrefix("api:").trim().lowercase(Locale.US)
    return when {
        norm.contains("claude") -> R.drawable.ic_provider_claude
        norm.contains("codex") || norm.contains("openai") || norm.contains("chatgpt") -> R.drawable.ic_provider_chatgpt
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
 * Template marks whose source bitmaps are dark/black silhouettes on transparent backgrounds
 * and must be tinted pure white when rendered on dark surfaces (Codex/OpenAI, Grok, Cursor, etc.).
 */
fun isMonochromeTemplateIcon(@DrawableRes resId: Int): Boolean = when (resId) {
    R.drawable.ic_provider_chatgpt,
    R.drawable.ic_provider_grok,
    R.drawable.ic_provider_cursor,
    R.drawable.ic_provider_copilot,
    R.drawable.ic_provider_cline,
    R.drawable.ic_provider_zcode,
    R.drawable.ic_provider_kimi,
    R.drawable.ic_provider_pi,
    R.drawable.ic_provider_trae,
    R.drawable.ic_provider_roo_code,
    R.drawable.ic_provider_kilocode,
    R.drawable.ic_provider_hermes,
    R.drawable.ic_provider_goose,
    R.drawable.ic_provider_xiaomimimo,
    -> true
    else -> false
}

/**
 * Renders the provider's own mark, unboxed. Monochrome template marks (Codex/ChatGPT, Grok, Cursor…)
 * are tinted pure white so they read crisply on the black canvas.
 */
@Composable
fun ProviderIcon(
    providerId: String,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
    tintOverride: Color? = null,
) {
    val res = providerIconRes(providerId)
    if (res != null) {
        val colorFilter = when {
            tintOverride != null -> ColorFilter.tint(tintOverride)
            isMonochromeTemplateIcon(res) -> ColorFilter.tint(Color.White)
            else -> null
        }
        Image(
            painter = painterResource(id = res),
            contentDescription = providerId,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(HudColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = providerId.trim().take(1).uppercase(),
                fontSize = (size.value * 0.48f).sp,
                fontWeight = FontWeight.SemiBold,
                color = HudColors.TextSecondary,
            )
        }
    }
}

/**
 * Live indicator: the dot itself breathes softly in opacity. No halo, no size change, so it never
 * nudges surrounding text.
 */
@Composable
fun BreathingDot(
    color: Color,
    size: Dp = 7.dp,
    pulsing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "breathe")
        val a by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breatheAlpha",
        )
        a
    } else {
        1f
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
    )
}
