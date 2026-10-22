package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisAqua
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricBlue
import com.example.voice.JarvisVoiceState
import kotlin.math.sin

@Composable
fun JarvisAudioWaveform(
    voiceState: JarvisVoiceState,
    audioRms: Float,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val barCount = 36
        val barWidth = 4.dp.toPx()
        val spacing = (size.width - (barCount * barWidth)) / (barCount - 1)
        val centerY = size.height / 2f

        val isActive = voiceState == JarvisVoiceState.LISTENING || voiceState == JarvisVoiceState.SPEAKING
        val baseMultiplier = if (isActive) (0.25f + audioRms * 0.75f) else 0.08f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing)
            val sineFactor = (sin(phase + (i * 0.28f)) + 1f) / 2f
            val barHeight = ((size.height * 0.85f) * (0.15f + sineFactor * baseMultiplier))
                .coerceAtLeast(4.dp.toPx())

            val topY = centerY - (barHeight / 2f)

            val brush = Brush.verticalGradient(
                colors = listOf(
                    JarvisCyan,
                    JarvisAqua,
                    JarvisElectricBlue
                ),
                startY = topY,
                endY = topY + barHeight
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                alpha = if (isActive) 0.95f else 0.35f
            )
        }
    }
}
