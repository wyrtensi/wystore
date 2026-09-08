package dev.wystore.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spinner shown while something is being fetched, checked or installed.
 *
 * Material's own indeterminate indicator is driven by an animation whose duration is multiplied by
 * the system animator scale, so on a phone where animations are turned off it is drawn once and
 * never moves again - a frozen arc that reads as a hung app rather than as work in progress. This
 * one steps from the frame clock instead, so it turns whatever that setting says.
 *
 * Nothing else in Wy Store overrides the setting: transitions, ripples and everything decorative
 * still obey it. A progress indicator is the one case where standing still means the wrong thing.
 */
@Composable
fun WySpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        // The frame clock is not scaled, unlike an animation's duration. Deriving the angle from
        // the frame time also keeps the speed the same on any refresh rate.
        val startedAt = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                rotation = (frame - startedAt) % ROTATION_PERIOD_MS / ROTATION_PERIOD_MS * 360f
            }
        }
    }
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val diameter = minOf(this.size.width, this.size.height) - stroke
        drawArc(
            color = color,
            startAngle = rotation - 90f,
            sweepAngle = ARC_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * The bar shown while a transfer is running but its size is not known yet, for the same reason as
 * [WySpinner]: Material's indeterminate bar stands still when animations are off, and a bar sitting
 * at zero says "stuck", not "working".
 */
@Composable
fun WyIndeterminateBar(
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startedAt = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                phase = (frame - startedAt) % SWEEP_PERIOD_MS / SWEEP_PERIOD_MS
            }
        }
    }
    Canvas(modifier = modifier.height(height)) {
        drawRect(color = trackColor, size = this.size)
        val width = this.size.width
        val segment = width * SWEEP_FRACTION
        // Travels a full width past the right edge and back in, so the segment enters and leaves
        // instead of jumping back to the start.
        val start = phase * (width + segment) - segment
        drawRect(
            color = color,
            topLeft = Offset(start.coerceAtLeast(0f), 0f),
            size = Size(
                width = (start + segment).coerceAtMost(width) - start.coerceAtLeast(0f),
                height = this.size.height
            )
        )
    }
}

private const val ROTATION_PERIOD_MS = 900f
private const val ARC_DEGREES = 260f
private const val SWEEP_PERIOD_MS = 1400f
private const val SWEEP_FRACTION = 0.35f
