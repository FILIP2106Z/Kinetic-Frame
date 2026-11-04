package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ChassisTriggerEvent
import com.example.model.ChassisZone
import com.example.model.FormFactorState
import com.example.model.FormFactorType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * High-fidelity interactive wireframe preview of the smartphone chassis.
 * Renders structural metal outlines, antenna lines, camera housing, hinge folds,
 * and reactive glowing impulse ripples for every physical chassis zone.
 */
@Composable
fun InteractiveChassisVisualizer(
    formFactor: FormFactorState,
    triggerFlow: SharedFlow<ChassisTriggerEvent>,
    selectedZone: ChassisZone?,
    onZoneTapped: (ChassisZone) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pulseProgressMap = remember { mutableStateMapOf<ChassisZone, Animatable<Float, *>>() }

    // Initialize pulse animations for all zones
    ChassisZone.entries.forEach { zone ->
        if (!pulseProgressMap.containsKey(zone)) {
            pulseProgressMap[zone] = remember { Animatable(0f) }
        }
    }

    // Listen to real-time physical trigger events from sensor engine
    LaunchedEffect(triggerFlow) {
        triggerFlow.collect { event ->
            val anim = pulseProgressMap[event.zone]
            anim?.snapTo(1f)
            coroutineScope.launch {
                anim?.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    // Determine chassis aspect ratio based on active form-factor
    val targetAspectRatio = when (formFactor.type) {
        FormFactorType.FOLDABLE_UNFOLDED -> 1.25f // Wide foldable inner display
        FormFactorType.FLIP_OPEN -> 0.44f // Tall 22:9 flip display
        FormFactorType.FLIP_HALF_OPEN -> 0.65f // Flex mode
        FormFactorType.FOLDABLE_FOLDED -> 0.46f // Slim cover display
        FormFactorType.CANDYBAR -> 0.48f // Modern 19.5:9 flagship
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .testTag("chassis_wireframe_visualizer")
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .aspectRatio(targetAspectRatio)
                .fillMaxSize()
                .pointerInput(formFactor) {
                    detectTapGestures { tapOffset ->
                        val hitZone = findNearestZone(
                            tapOffset = tapOffset,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            formFactor = formFactor
                        )
                        hitZone?.let { zone ->
                            onZoneTapped(zone)
                            val anim = pulseProgressMap[zone]
                            coroutineScope.launch {
                                anim?.snapTo(1f)
                                anim?.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            drawChassisWireframe(
                width = canvasWidth,
                height = canvasHeight,
                formFactor = formFactor,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor,
                surfaceColor = surfaceColor,
                outlineColor = outlineColor,
                onSurfaceColor = onSurfaceColor
            )

            // Draw interactive touch zone nodes and dynamic glowing pulses
            drawChassisZones(
                width = canvasWidth,
                height = canvasHeight,
                pulseMap = pulseProgressMap,
                selectedZone = selectedZone,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor
            )
        }
    }
}

private fun DrawScope.drawChassisWireframe(
    width: Float,
    height: Float,
    formFactor: FormFactorState,
    primaryColor: Color,
    tertiaryColor: Color,
    surfaceColor: Color,
    outlineColor: Color,
    onSurfaceColor: Color
) {
    val cornerRadius = minOf(width, height) * 0.12f
    val chassisInset = 12.dp.toPx()

    val outerRect = Rect(
        offset = Offset(chassisInset, chassisInset),
        size = Size(width - chassisInset * 2, height - chassisInset * 2)
    )

    // Outer metal chassis frame fill with subtle gradient
    val chassisBrush = Brush.linearGradient(
        colors = listOf(
            surfaceColor.copy(alpha = 0.35f),
            surfaceColor.copy(alpha = 0.15f),
            surfaceColor.copy(alpha = 0.25f)
        ),
        start = Offset(0f, 0f),
        end = Offset(width, height)
    )

    drawRoundRect(
        brush = chassisBrush,
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    // Outer structural rim (thick metal bevel)
    drawRoundRect(
        color = outlineColor.copy(alpha = 0.85f),
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = 3.5.dp.toPx())
    )

    // Inner display bezel
    val screenInset = chassisInset + 8.dp.toPx()
    val screenCornerRadius = cornerRadius - 6.dp.toPx()
    drawRoundRect(
        color = primaryColor.copy(alpha = 0.30f),
        topLeft = Offset(screenInset, screenInset),
        size = Size(width - screenInset * 2, height - screenInset * 2),
        cornerRadius = CornerRadius(screenCornerRadius, screenCornerRadius),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
    )

    // Structural Antenna Bands (plastic RF isolation breaks in metal frame)
    val bandWidth = 4.dp.toPx()
    val bandHeight = 10.dp.toPx()
    val bandColor = outlineColor.copy(alpha = 0.9f)

    // Top and bottom antenna lines
    drawRect(bandColor, Offset(outerRect.left, outerRect.top + height * 0.15f), Size(bandWidth, bandHeight))
    drawRect(bandColor, Offset(outerRect.right - bandWidth, outerRect.top + height * 0.15f), Size(bandWidth, bandHeight))
    drawRect(bandColor, Offset(outerRect.left, outerRect.bottom - height * 0.15f), Size(bandWidth, bandHeight))
    drawRect(bandColor, Offset(outerRect.right - bandWidth, outerRect.bottom - height * 0.15f), Size(bandWidth, bandHeight))

    // Physical Hardware Buttons (Volume & Power on frame)
    val buttonWidth = 5.dp.toPx()
    val volumeHeight = height * 0.14f
    val powerHeight = height * 0.08f

    // Volume Rocker (Left Frame)
    drawRoundRect(
        color = primaryColor.copy(alpha = 0.9f),
        topLeft = Offset(outerRect.left - buttonWidth, outerRect.top + height * 0.24f),
        size = Size(buttonWidth, volumeHeight),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Power / Fingerprint Button (Right Frame)
    drawRoundRect(
        color = primaryColor.copy(alpha = 0.9f),
        topLeft = Offset(outerRect.right, outerRect.top + height * 0.28f),
        size = Size(buttonWidth, powerHeight),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Camera Module (Adaptive: Round or Island with Lenses)
    drawCameraModule(
        width = width,
        height = height,
        formFactor = formFactor,
        primaryColor = primaryColor,
        outlineColor = outlineColor
    )

    // Speaker Grille and USB-C port at bottom
    val usbWidth = width * 0.14f
    val usbHeight = 3.dp.toPx()
    drawRoundRect(
        color = outlineColor,
        topLeft = Offset(width / 2f - usbWidth / 2f, outerRect.bottom - 4.dp.toPx()),
        size = Size(usbWidth, usbHeight),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Special Form Factor Details (Foldable Spine / Hinge / Flip Crease)
    if (formFactor.type == FormFactorType.FOLDABLE_UNFOLDED) {
        val centerX = width / 2f
        // Vertical hinge crease line
        drawLine(
            color = tertiaryColor.copy(alpha = 0.6f),
            start = Offset(centerX, outerRect.top),
            end = Offset(centerX, outerRect.bottom),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
        )
    } else if (formFactor.type == FormFactorType.FLIP_OPEN || formFactor.type == FormFactorType.FLIP_HALF_OPEN) {
        val centerY = height / 2f
        // Horizontal hinge crease line
        drawLine(
            color = tertiaryColor.copy(alpha = 0.6f),
            start = Offset(outerRect.left, centerY),
            end = Offset(outerRect.right, centerY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
        )
    }
}

private fun DrawScope.drawCameraModule(
    width: Float,
    height: Float,
    formFactor: FormFactorState,
    primaryColor: Color,
    outlineColor: Color
) {
    val islandWidth = width * 0.28f
    val islandHeight = height * 0.16f
    val islandLeft = width * 0.14f
    val islandTop = height * 0.08f

    // Camera Island Pill
    drawRoundRect(
        color = outlineColor.copy(alpha = 0.45f),
        topLeft = Offset(islandLeft, islandTop),
        size = Size(islandWidth, islandHeight),
        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )

    // Dual Optical Lenses
    val lensRadius = islandWidth * 0.18f
    drawCircle(
        color = primaryColor.copy(alpha = 0.7f),
        radius = lensRadius,
        center = Offset(islandLeft + islandWidth * 0.35f, islandTop + islandHeight * 0.32f),
        style = Stroke(width = 2.dp.toPx())
    )
    drawCircle(
        color = primaryColor.copy(alpha = 0.7f),
        radius = lensRadius,
        center = Offset(islandLeft + islandWidth * 0.35f, islandTop + islandHeight * 0.70f),
        style = Stroke(width = 2.dp.toPx())
    )

    // Flash LED
    drawCircle(
        color = Color(0xFFFFD54F),
        radius = lensRadius * 0.4f,
        center = Offset(islandLeft + islandWidth * 0.74f, islandTop + islandHeight * 0.50f)
    )
}

private fun DrawScope.drawChassisZones(
    width: Float,
    height: Float,
    pulseMap: Map<ChassisZone, Animatable<Float, *>>,
    selectedZone: ChassisZone?,
    primaryColor: Color,
    tertiaryColor: Color
) {
    ChassisZone.entries.forEach { zone ->
        val cx = width * zone.defaultNormalizedX
        val cy = height * zone.defaultNormalizedY
        val pulseVal = pulseMap[zone]?.value ?: 0f
        val isSelected = selectedZone == zone

        // Dynamic Glowing Ripple Waves
        if (pulseVal > 0.01f) {
            val maxRippleRadius = 48.dp.toPx()
            val currentRadius = 14.dp.toPx() + (maxRippleRadius * (1f - pulseVal))
            val alpha = pulseVal.coerceIn(0f, 1f)

            // Outer expanding ring
            drawCircle(
                color = tertiaryColor.copy(alpha = alpha * 0.8f),
                radius = currentRadius,
                center = Offset(cx, cy),
                style = Stroke(width = (4f * pulseVal).dp.toPx())
            )

            // Inner core flash
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = alpha * 0.95f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = currentRadius
                ),
                radius = currentRadius,
                center = Offset(cx, cy)
            )
        }

        // Base Trigger Node Icon
        val baseRadius = if (isSelected) 10.dp.toPx() else 7.dp.toPx()
        val nodeColor = when {
            pulseVal > 0.1f -> tertiaryColor
            isSelected -> primaryColor
            else -> primaryColor.copy(alpha = 0.65f)
        }

        drawCircle(
            color = nodeColor,
            radius = baseRadius,
            center = Offset(cx, cy)
        )

        // Center dot
        drawCircle(
            color = Color.White,
            radius = baseRadius * 0.4f,
            center = Offset(cx, cy)
        )
    }
}

private fun findNearestZone(
    tapOffset: Offset,
    width: Float,
    height: Float,
    formFactor: FormFactorState
): ChassisZone? {
    val hitThresholdPx = 48f * 3f // Generous touch target
    var closestZone: ChassisZone? = null
    var minDistance = Float.MAX_VALUE

    ChassisZone.entries.forEach { zone ->
        val zx = width * zone.defaultNormalizedX
        val zy = height * zone.defaultNormalizedY
        val dx = tapOffset.x - zx
        val dy = tapOffset.y - zy
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        if (distance < hitThresholdPx && distance < minDistance) {
            minDistance = distance
            closestZone = zone
        }
    }

    return closestZone
}
