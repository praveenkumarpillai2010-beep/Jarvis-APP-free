package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisAqua
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisError
import com.example.ui.theme.JarvisNeonGlow
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisWarning
import com.example.voice.JarvisVoiceState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisArcReactor(
    voiceState: JarvisVoiceState,
    audioRms: Float,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_reactor")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing)
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing)
        ),
        label = "inner_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val primaryColor = when (voiceState) {
        JarvisVoiceState.READY, JarvisVoiceState.IDLE -> JarvisCyan
        JarvisVoiceState.LISTENING -> JarvisNeonGlow
        JarvisVoiceState.THINKING, JarvisVoiceState.PROCESSING -> JarvisWarning
        JarvisVoiceState.EXECUTING -> JarvisAqua
        JarvisVoiceState.SPEAKING -> JarvisElectricBlue
        JarvisVoiceState.ERROR -> JarvisError
    }

    val stateText = when (voiceState) {
        JarvisVoiceState.READY, JarvisVoiceState.IDLE -> "READY"
        JarvisVoiceState.LISTENING -> "LISTENING"
        JarvisVoiceState.THINKING, JarvisVoiceState.PROCESSING -> "PROCESSING"
        JarvisVoiceState.EXECUTING -> "EXECUTING"
        JarvisVoiceState.SPEAKING -> "SPEAKING"
        JarvisVoiceState.ERROR -> "OFFLINE"
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.92f

            // Dynamic mic audio boost
            val rmsBoost = audioRms * 14f

            // 1. Ambient Glow radial background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (voiceState == JarvisVoiceState.LISTENING) 0.40f else 0.22f),
                        primaryColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * pulseScale
                ),
                radius = baseRadius * pulseScale,
                center = center
            )

            // 2. Outer Ring with holographic tick notches
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                val tickCount = 24
                for (i in 0 until tickCount) {
                    val angle = (i * (360f / tickCount)) * (Math.PI / 180f)
                    val r1 = baseRadius - (if (i % 3 == 0) 10.dp.toPx() else 5.dp.toPx())
                    val r2 = baseRadius
                    val start = Offset(
                        (center.x + r1 * cos(angle)).toFloat(),
                        (center.y + r1 * sin(angle)).toFloat()
                    )
                    val end = Offset(
                        (center.x + r2 * cos(angle)).toFloat(),
                        (center.y + r2 * sin(angle)).toFloat()
                    )
                    drawLine(
                        color = primaryColor.copy(alpha = if (i % 3 == 0) 0.8f else 0.35f),
                        start = start,
                        end = end,
                        strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
                    )
                }
            }

            // 3. Counter-rotating segmented middle arcs
            rotate(innerRotation, pivot = center) {
                val midRadius = baseRadius * 0.76f + rmsBoost
                val arcSegments = 6
                val sweep = 42f
                val gap = (360f - (arcSegments * sweep)) / arcSegments

                for (i in 0 until arcSegments) {
                    val startAngle = i * (sweep + gap)
                    drawArc(
                        color = JarvisAqua.copy(alpha = 0.85f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - midRadius, center.y - midRadius),
                        size = Size(midRadius * 2f, midRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 4. Inner Reactor Core
            val innerCoreRadius = baseRadius * 0.48f * pulseScale
            drawCircle(
                color = primaryColor.copy(alpha = 0.25f),
                radius = innerCoreRadius,
                center = center
            )
            drawCircle(
                color = primaryColor,
                radius = innerCoreRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Inner Center glowing dot
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, primaryColor, Color.Transparent),
                    center = center,
                    radius = innerCoreRadius * 0.5f
                ),
                radius = innerCoreRadius * 0.5f,
                center = center
            )
        }

        // Center HUD label
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stateText,
                color = JarvisTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        }
    }
}
