package org.agenthud.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * OLED design tokens.
 *
 * One true-black canvas, three flat neutral surface steps, hairline separators, and a small set of
 * functional accents. No gradients: depth comes from the surface steps alone.
 */
object HudColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF111113)
    val SurfaceElevated = Color(0xFF1A1A1D)
    val SurfaceMuted = Color(0xFF26262A)
    val DockGlass = Color(0xF2161618)
    val Border = Color(0x12FFFFFF)
    val BorderStrong = Color(0x21FFFFFF)

    val TextPrimary = Color(0xFFF2F2F4)
    val TextSecondary = Color(0xFF8E8E95)
    val TextMuted = Color(0xFF5C5C63)

    // Quota accents: sage for healthy, terracotta for critical.
    val Sage = Color(0xFF5E8A4A)
    val SageBright = Color(0xFF7FB069)
    val SageSoft = Color(0x225E8A4A)
    val Terracotta = Color(0xFFE0604F)
    val TerracottaSoft = Color(0x2EE0604F)
    val BlueAccent = Color(0xFF3B8BFF)
    val BlueSoft = Color(0x2A3B8BFF)
    val Track = Color(0xFF1E1E22)

    // Chat: recessed code wells, the user's bubble, diff tints.
    val CodeSurface = Color(0xFF0C0C0E)
    val UserBubble = Color(0xFF26262A)
    val ChatText = Color(0xFFECECEE)
    val DiffAddBg = Color(0x1F57B26A)
    val DiffAddFg = Color(0xFF8FD3A0)
    val DiffDelBg = Color(0x24E0604F)
    val DiffDelFg = Color(0xFFF2A194)

    // Token kind accents (shared by Stats and Sessions).
    val KindInput = Color(0xFF5B8DEF)
    val KindOutput = Color(0xFFE08A4E)
    val KindReasoning = Color(0xFFD67AA8)
    val KindCacheWrite = Color(0xFF9E77ED)
    val KindCacheRead = Color(0xFF4BB89A)

    // Per-model stacked bar palette.
    val ModelPalette = listOf(
        Color(0xFF7FB069),
        Color(0xFF5B9BFF),
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

/**
 * Numeric text styles. All figures use tabular digits so values never jitter while they animate.
 */
object HudType {
    private const val TABULAR = "tnum"

    fun numeric(
        size: TextUnit,
        weight: FontWeight = FontWeight.SemiBold,
        color: Color = HudColors.TextPrimary,
        letterSpacing: TextUnit = 0.sp,
    ): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = weight,
        color = color,
        letterSpacing = letterSpacing,
        fontFeatureSettings = TABULAR,
    )

    /** Plain tabular-digit style for inline figures that otherwise inherit their look. */
    val Tabular = TextStyle(fontFeatureSettings = TABULAR)

    /** Hero figures (token totals, big percentages). */
    val Display = numeric(30.sp, FontWeight.Bold, letterSpacing = (-0.8).sp)

    /** Row-trailing figures (percentages, token counts). */
    val Value = numeric(14.sp, FontWeight.SemiBold)

    /** Small secondary figures (costs, reset times). */
    val Caption = numeric(11.5.sp, FontWeight.Normal, HudColors.TextSecondary)
}

private val HudDarkScheme = darkColorScheme(
    primary = HudColors.BlueAccent,
    onPrimary = Color.White,
    primaryContainer = HudColors.BlueSoft,
    onPrimaryContainer = HudColors.TextPrimary,
    secondary = HudColors.SageBright,
    onSecondary = Color.Black,
    secondaryContainer = HudColors.SageSoft,
    onSecondaryContainer = HudColors.TextPrimary,
    background = HudColors.Background,
    onBackground = HudColors.TextPrimary,
    surface = HudColors.Surface,
    onSurface = HudColors.TextPrimary,
    surfaceVariant = HudColors.SurfaceElevated,
    onSurfaceVariant = HudColors.TextSecondary,
    surfaceContainer = HudColors.SurfaceElevated,
    surfaceContainerHigh = HudColors.SurfaceElevated,
    surfaceContainerHighest = HudColors.SurfaceMuted,
    surfaceContainerLow = HudColors.Surface,
    outline = HudColors.BorderStrong,
    outlineVariant = HudColors.Border,
    error = HudColors.Terracotta,
    onError = Color.White,
)

private val HudTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        lineHeight = 36.sp,
        color = HudColors.TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp,
        lineHeight = 34.sp,
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
        lineHeight = 20.sp,
        color = HudColors.TextPrimary,
    ),
    titleSmall = TextStyle(
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
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
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = HudColors.TextPrimary,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = HudColors.TextSecondary,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
        color = HudColors.TextMuted,
    ),
)

/** Monospace is kept only for code-like content (commands, hashes, endpoints). */
val HudMono: FontFamily = FontFamily.Monospace

@Composable
fun AgentHudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HudDarkScheme,
        typography = HudTypography,
        content = content,
    )
}
