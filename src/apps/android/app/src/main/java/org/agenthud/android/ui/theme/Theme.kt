package org.agenthud.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pure-black iOS-inspired Obsidian / Sage / Terracotta design tokens.
 */
object HudColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF111115)
    val SurfaceElevated = Color(0xFF17171D)
    val SurfaceMuted = Color(0xFF202028)
    val DockGlass = Color(0xEB15151B)
    val Border = Color(0x14FFFFFF)
    val BorderStrong = Color(0x26FFFFFF)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF9898A2)
    val TextMuted = Color(0xFF63636C)

    // Signature fork quota accents (no harsh yellow)
    val Sage = Color(0xFF5B7A45)
    val SageBright = Color(0xFF729958)
    val SageSoft = Color(0x265B7A45)
    val Terracotta = Color(0xFFC84B3C)
    val TerracottaSoft = Color(0x26C84B3C)
    val BlueAccent = Color(0xFF4C8BF5)
    val BlueSoft = Color(0x244C8BF5)
    val Track = Color(0xFF22222A)

    // Token kind dot accents (matches iOS Stats & Sessions)
    val KindInput = Color(0xFF5B8DEF)
    val KindOutput = Color(0xFFE08A4E)
    val KindReasoning = Color(0xFFD67AA8)
    val KindCacheWrite = Color(0xFF9E77ED)
    val KindCacheRead = Color(0xFF4BB89A)

    // Multi-model stacked bar palette (matches iOS Stats)
    val ModelPalette = listOf(
        Color(0xFF7C9E5E),
        Color(0xFF63A4FF),
        Color(0xFFA47EF7),
        Color(0xFFE07C56),
        Color(0xFF54B8A8),
        Color(0xFFE36D8E),
        Color(0xFF8A94A6),
    )

    fun modelColor(index: Int): Color = ModelPalette[index % ModelPalette.size]

    fun providerAccent(providerId: String): Color = when (providerId.lowercase()) {
        "claude" -> Color(0xFFD97757)
        "codex", "openai" -> Color(0xFF10A37F)
        "antigravity", "gemini" -> Color(0xFF6E8EF5)
        "deepseek" -> Color(0xFF4D6BFE)
        "grok-bot", "grok" -> Color(0xFFE4E4EA)
        "cursor" -> Color(0xFF8B8BF5)
        "zcode", "glm" -> Color(0xFF59A5D8)
        else -> SageBright
    }
}

private val HudDarkScheme = darkColorScheme(
    primary = HudColors.Sage,
    onPrimary = Color.White,
    primaryContainer = HudColors.SageSoft,
    onPrimaryContainer = HudColors.TextPrimary,
    secondary = HudColors.Terracotta,
    onSecondary = Color.White,
    secondaryContainer = HudColors.TerracottaSoft,
    onSecondaryContainer = HudColors.TextPrimary,
    background = HudColors.Background,
    onBackground = HudColors.TextPrimary,
    surface = HudColors.Surface,
    onSurface = HudColors.TextPrimary,
    surfaceVariant = HudColors.SurfaceElevated,
    onSurfaceVariant = HudColors.TextSecondary,
    outline = HudColors.Border,
    error = HudColors.Terracotta,
    onError = Color.White,
)

private val HudTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
        color = HudColors.TextPrimary,
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
        color = HudColors.TextPrimary,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        color = HudColors.TextPrimary,
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = HudColors.TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        color = HudColors.TextPrimary,
    ),
    bodySmall = TextStyle(
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 17.sp,
        color = HudColors.TextSecondary,
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = HudColors.TextSecondary,
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        color = HudColors.TextMuted,
    ),
)

@Composable
fun AgentHudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HudDarkScheme,
        typography = HudTypography,
        content = content,
    )
}
