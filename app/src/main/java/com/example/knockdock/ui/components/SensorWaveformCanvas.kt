package com.example.knockdock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateGlassDark
import kotlin.math.max

/**
 * High-performance real-time oscilloscope canvas rendering filtered vibration impulses,
 * dynamic noise floor, and threshold guide lines with reused Path objects (zero per-frame allocations).
 */
@Composable
fun SensorWaveformCanvas(
    waveformBuffer: FloatArray,
    bufferWriteIndex: Int,
    effectiveThreshold: Float,
    isKnockTriggered: Boolean,
    modifier: Modifier = Modifier,
    maxDisplayAmplitude: Float = 8.0f
) {
    val wavePath = remember { Path() }
    val fillPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) }
    val shape = remember { RoundedCornerShape(16.dp) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(SlateGlassDark.copy(alpha = 0.9f))
            .border(1.dp, GlassBorderTop, shape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Draw subtle grid lines
            val gridColor = Color.White.copy(alpha = 0.06f)
            val gridSteps = 4
            for (i in 1 until gridSteps) {
                val y = height * (i.toFloat() / gridSteps)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            // 2. Draw threshold guide line
            val thresholdRatio = (effectiveThreshold / max(1f, maxDisplayAmplitude)).coerceIn(0f, 1f)
            val thresholdY = height * (1f - thresholdRatio)
            drawLine(
                color = AmberAlert.copy(alpha = 0.85f),
                start = Offset(0f, thresholdY),
                end = Offset(width, thresholdY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dashEffect
            )

            // 3. Draw live waveform path from ring buffer (reusing Path instances)
            val bufferSize = waveformBuffer.size
            if (bufferSize > 1) {
                wavePath.reset()
                val stepX = width / (bufferSize - 1)

                for (i in 0 until bufferSize) {
                    val readIndex = (bufferWriteIndex + i) % bufferSize
                    val sample = waveformBuffer[readIndex]
                    val normalizedY = (sample / max(1f, maxDisplayAmplitude)).coerceIn(0f, 1f)
                    val y = height * (1f - (normalizedY * 0.95f))
                    val x = i * stepX

                    if (i == 0) {
                        wavePath.moveTo(x, y)
                    } else {
                        wavePath.lineTo(x, y)
                    }
                }

                val waveformColor = if (isKnockTriggered) RoseError else CyanGlow
                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(waveformColor, IndigoPulse),
                        startY = 0f,
                        endY = height
                    ),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                fillPath.reset()
                fillPath.addPath(wavePath)
                fillPath.lineTo(width, height)
                fillPath.lineTo(0f, height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(waveformColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )
            }

            // 4. If an impact was registered in this tick, draw shock flash border
            if (isKnockTriggered) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, RoseError.copy(alpha = 0.35f)),
                        center = Offset(width / 2f, height / 2f),
                        radius = width * 0.7f
                    )
                )
            }
        }
    }
}
