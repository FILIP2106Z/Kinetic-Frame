package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SensorTelemetryPoint
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Real-time IMU Impulse Telemetry Oscilloscope.
 * Plots linear acceleration jerk, rotational shockwave energy, and the adaptive ambient baseline.
 */
@Composable
fun TelemetryScope(
    telemetryFlow: StateFlow<SensorTelemetryPoint>,
    modifier: Modifier = Modifier
) {
    val historySize = 64
    val energyHistory = remember { mutableStateListOf<Float>() }
    val thresholdHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(telemetryFlow) {
        telemetryFlow.collect { point ->
            energyHistory.add(point.filteredImpulse)
            thresholdHistory.add(point.ambientThreshold)
            if (energyHistory.size > historySize) {
                energyHistory.removeAt(0)
            }
            if (thresholdHistory.size > historySize) {
                thresholdHistory.removeAt(0)
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val currentPoint = telemetryFlow.value

    Column(
        modifier = modifier
            .testTag("telemetry_oscilloscope_panel")
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor.copy(alpha = 0.5f))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "IMU IMPULSE TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "High-Rate Differential Waveform (~250Hz)",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ENERGY",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = tertiaryColor
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", currentPoint.filteredImpulse),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = tertiaryColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "THRESHOLD",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", currentPoint.ambientThreshold),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceVariant
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
                .padding(top = 8.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Grid lines
                val gridColor = onSurfaceVariant.copy(alpha = 0.12f)
                drawLine(gridColor, Offset(0f, canvasHeight * 0.25f), Offset(canvasWidth, canvasHeight * 0.25f), 1f)
                drawLine(gridColor, Offset(0f, canvasHeight * 0.50f), Offset(canvasWidth, canvasHeight * 0.50f), 1f)
                drawLine(gridColor, Offset(0f, canvasHeight * 0.75f), Offset(canvasWidth, canvasHeight * 0.75f), 1f)

                if (energyHistory.size < 2) return@Canvas

                val maxRange = 50.0f
                val stepX = canvasWidth / (historySize - 1)

                // Plot dynamic adaptive threshold curve (Dashed Amber line)
                val thresholdPath = Path()
                thresholdHistory.forEachIndexed { i, thresh ->
                    val x = i * stepX
                    val y = canvasHeight - ((thresh / maxRange).coerceIn(0f, 1f) * canvasHeight)
                    if (i == 0) thresholdPath.moveTo(x, y) else thresholdPath.lineTo(x, y)
                }
                drawPath(
                    path = thresholdPath,
                    color = Color(0xFFFFB74D).copy(alpha = 0.85f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                )

                // Plot impulse energy wave (Solid Cyan/Primary line)
                val energyPath = Path()
                energyHistory.forEachIndexed { i, energy ->
                    val x = i * stepX
                    val y = canvasHeight - ((energy / maxRange).coerceIn(0f, 1f) * canvasHeight)
                    if (i == 0) energyPath.moveTo(x, y) else energyPath.lineTo(x, y)
                }
                drawPath(
                    path = energyPath,
                    color = primaryColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
