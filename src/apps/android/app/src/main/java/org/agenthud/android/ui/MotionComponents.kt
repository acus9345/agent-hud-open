package org.agenthud.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.agenthud.android.ui.theme.HudColors
import org.agenthud.android.ui.theme.HudType

/**
 * Shared motion vocabulary. Every moving part of the app draws from these few specs so the whole
 * thing feels like one hand: quick, critically-damped-ish springs, no rubber-band theatrics.
 */
object HudMotion {
    /** Selection capsules (tab dock, segmented controls). A hint of settle, no visible wobble. */
    fun <T> slide(): AnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 520f)

    /** Values that fill or grow (bars, rings, counters). */
    fun <T> fill(): AnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)

    /** Press feedback. Fast in, soft out. */
    fun <T> press(): AnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = 900f)

    /** Tint cross-fades. */
    fun <T> tint(): AnimationSpec<T> = tween(durationMillis = 180)
}

/** Bottom padding every tab screen must reserve so its last row clears the floating dock. */
val LocalDockInset = staticCompositionLocalOf { 112.dp }

/**
 * Tactile press response: a small, fast scale-down with a soft return. No ripple, no bounce-back
 * overshoot past 1.0.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = HudMotion.press(),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
}

/**
 * The one card in the app: flat neutral surface, hairline edge, generous radius.
 * Cards never nest inside cards; inner grouping uses [HudDivider] instead.
 */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = HudColors.Surface,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val clickMod = if (onClick != null) Modifier.bounceClick(scaleDown = 0.985f, onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .then(clickMod)
            .clip(shape)
            .background(color, shape)
            .border(1.dp, HudColors.Border, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * One row of a grouped list that is split across LazyColumn items (so long lists stay lazy).
 * Rows share the card surface; only the first and last rows round their outer corners, and the
 * hairline edge is drawn so it reads as one continuous card.
 */
fun Modifier.groupedListItem(index: Int, count: Int, radius: Dp = 20.dp): Modifier {
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) radius else 0.dp,
        topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp,
        bottomEnd = if (last) radius else 0.dp,
    )
    return this
        .clip(shape)
        .background(HudColors.Surface)
        .drawWithContent {
            drawContent()
            val stroke = 1.dp.toPx()
            val r = radius.toPx()
            // Extend the outline past any edge that continues into a neighbouring row so the
            // clip hides it: middle rows only show their side edges.
            val top = if (first) stroke / 2f else -(r + stroke)
            val bottom = if (last) size.height - stroke / 2f else size.height + r + stroke
            drawRoundRect(
                color = HudColors.Border,
                topLeft = Offset(stroke / 2f, top),
                size = Size(size.width - stroke, bottom - top),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(stroke),
            )
        }
}

/** Text-field colors shared by every input in the app. */
@Composable
fun hudTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HudColors.BlueAccent,
    unfocusedBorderColor = HudColors.BorderStrong,
    focusedTextColor = HudColors.TextPrimary,
    unfocusedTextColor = HudColors.TextPrimary,
    cursorColor = HudColors.BlueAccent,
)

/** Hairline separator (one physical pixel). */
@Composable
fun HudDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = Dp.Hairline, color = HudColors.BorderStrong)
}

/** Section title that sits above a card, iOS grouped-list style. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = HudColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                fontSize = 12.sp,
                style = HudType.Tabular,
                color = HudColors.TextMuted,
            )
        }
    }
}

/**
 * Segmented control with a single thumb that springs between options.
 *
 * Each option's bounds are measured once laid out; the thumb is drawn behind the labels from two
 * [Animatable]s, so a slide never recomposes the row. The first placement snaps (no slide-in from
 * the left edge on first show).
 */
@Composable
fun <T> SlidingSegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = false,
    itemHorizontalPadding: Dp = 12.dp,
    itemVerticalPadding: Dp = 6.dp,
    fontSize: TextUnit = 12.5.sp,
) {
    val haptics = LocalHapticFeedback.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val thumbX = remember { Animatable(0f) }
    val thumbW = remember { Animatable(0f) }
    val target = bounds[selectedIndex]

    LaunchedEffect(target) {
        val (x, w) = target ?: return@LaunchedEffect
        if (thumbW.value == 0f) {
            thumbX.snapTo(x)
            thumbW.snapTo(w)
        } else {
            launchBoth(
                { thumbX.animateTo(x, HudMotion.slide()) },
                { thumbW.animateTo(w, HudMotion.slide()) },
            )
        }
    }

    val trackShape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(trackShape)
            .background(HudColors.SurfaceElevated)
            .padding(2.dp)
            .drawBehind {
                val w = thumbW.value
                if (w > 0f) {
                    val r = size.height / 2f
                    drawRoundRect(
                        color = HudColors.SurfaceMuted,
                        topLeft = Offset(thumbX.value, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(r, r),
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        options.forEachIndexed { idx, option ->
            val isSelected = idx == selectedIndex
            val textColor by animateColorAsState(
                targetValue = if (isSelected) HudColors.TextPrimary else HudColors.TextSecondary,
                animationSpec = HudMotion.tint(),
                label = "segText",
            )
            Box(
                modifier = (if (equalWidth) Modifier.weight(1f) else Modifier)
                    .onGloballyPositioned { c ->
                        val x = c.positionInParent().x
                        val w = c.size.width.toFloat()
                        val prev = bounds[idx]
                        if (prev == null || prev.first != x || prev.second != w) bounds[idx] = x to w
                    }
                    .bounceClick(scaleDown = 0.95f) {
                        if (!isSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(option)
                        }
                    }
                    .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelFor(option),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

private suspend fun launchBoth(a: suspend () -> Unit, b: suspend () -> Unit) = coroutineScope {
    launch { a() }
    launch { b() }
}

/** Thin solid progress bar whose fill springs to its value. */
@Composable
fun HudProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    trackColor: Color = HudColors.Track,
) {
    val fill by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = HudMotion.fill(),
        label = "barFill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val r = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, cornerRadius = r)
                val w = size.width * fill
                if (w > 0.5f) {
                    drawRoundRect(
                        color = color,
                        size = Size(w.coerceAtLeast(size.height), size.height),
                        cornerRadius = r,
                    )
                }
            },
    )
}

/** Horizontal proportion bar split into colored segments, separated by 1dp gaps. */
@Composable
fun SegmentedShareBar(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    val grow by animateFloatAsState(
        targetValue = if (segments.isEmpty()) 0f else 1f,
        animationSpec = HudMotion.fill(),
        label = "shareGrow",
    )
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(HudColors.Track)
            .drawBehind {
                if (total <= 0f) return@drawBehind
                val gap = 1.dp.toPx()
                var x = 0f
                val usable = size.width * grow
                segments.forEach { (value, c) ->
                    val w = usable * (value / total)
                    if (w > 0.5f) {
                        drawRect(color = c, topLeft = Offset(x, 0f), size = Size((w - gap).coerceAtLeast(1f), size.height))
                    }
                    x += w
                }
            },
    )
}

/** Smoothly counts a long value toward its target for hero figures. */
@Composable
fun rememberAnimatedLong(target: Long): Long {
    val v by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
        label = "countUp",
    )
    return if (v == target.toFloat()) target else v.toLong()
}
