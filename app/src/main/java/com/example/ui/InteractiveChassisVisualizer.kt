package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ChassisTriggerEvent
import com.example.model.ConcretePhoneProfile
import com.example.model.DeviceHardwareProfiler
import com.example.model.FormFactorState
import com.example.model.FormFactorType
import com.example.model.KineticButton
import com.example.model.PhoneDesignStyle
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Concrete sub-model precision phone vector graphic visualizer.
 * Dynamically tailors hardware graphics (Pixel Visor, Galaxy Ultra sharp frame,
 * Circular watch-dial, ROG AirTriggers) to the exact physical model detected in system.
 */
@Composable
fun InteractiveChassisVisualizer(
    buttons: List<KineticButton>,
    formFactor: FormFactorState,
    triggerFlow: SharedFlow<ChassisTriggerEvent>,
    selectedButtonId: String?,
    isAddMode: Boolean = false,
    onWireframeTapped: (normalizedX: Float, normalizedY: Float) -> Unit,
    onButtonTapped: (KineticButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val phoneProfile = remember { DeviceHardwareProfiler.detectProfile(context) }

    // Pulse animations mapped by button ID
    val pulseProgressMap = remember { mutableStateMapOf<String, Animatable<Float, *>>() }
    val lastEstimatedImpact = remember { Animatable(0f) }
    var lastImpactX = remember { 0.5f }
    var lastImpactY = remember { 0.5f }

    // Initialize animations for all buttons
    buttons.forEach { button ->
        if (!pulseProgressMap.containsKey(button.id)) {
            pulseProgressMap[button.id] = remember { Animatable(0f) }
        }
    }

    // Listen to real-time physical trigger events
    LaunchedEffect(triggerFlow) {
        triggerFlow.collect { event ->
            val anim = pulseProgressMap[event.buttonId]
            lastImpactX = event.estimatedX
            lastImpactY = event.estimatedY
            coroutineScope.launch {
                lastEstimatedImpact.snapTo(1f)
                lastEstimatedImpact.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            }
            coroutineScope.launch {
                anim?.snapTo(1f)
                anim?.animateTo(0f, tween(650, easing = FastOutSlowInEasing))
            }
        }
    }

    val targetAspectRatio = when (formFactor.type) {
        FormFactorType.FOLDABLE_UNFOLDED -> 1.25f
        FormFactorType.FLIP_OPEN -> 0.44f
        FormFactorType.FLIP_HALF_OPEN -> 0.65f
        FormFactorType.FOLDABLE_FOLDED -> 0.46f
        FormFactorType.CANDYBAR -> phoneProfile.aspectRatio.coerceIn(0.44f, 0.52f)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .testTag("chassis_wireframe_visualizer")
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .aspectRatio(targetAspectRatio)
                .fillMaxSize()
                .pointerInput(buttons, isAddMode) {
                    detectTapGestures { tapOffset ->
                        val normX = (tapOffset.x / size.width).coerceIn(0f, 1f)
                        val normY = (tapOffset.y / size.height).coerceIn(0f, 1f)

                        if (isAddMode) {
                            onWireframeTapped(normX, normY)
                        } else {
                            val hitButton = findNearestButton(tapOffset, size.width.toFloat(), size.height.toFloat(), buttons)
                            if (hitButton != null) {
                                onButtonTapped(hitButton)
                                val anim = pulseProgressMap[hitButton.id]
                                coroutineScope.launch {
                                    anim?.snapTo(1f)
                                    anim?.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
                                }
                            } else {
                                onWireframeTapped(normX, normY)
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw sub-model precision phone chassis
            drawSubModelPhoneChassis(
                width = canvasWidth,
                height = canvasHeight,
                profile = phoneProfile,
                formFactor = formFactor,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor,
                surfaceColor = surfaceColor,
                outlineColor = outlineColor,
                onSurfaceColor = onSurfaceColor
            )

            // 2. Draw real-time estimated strike epicenter crosshair
            if (lastEstimatedImpact.value > 0.02f) {
                val eX = canvasWidth * lastImpactX
                val eY = canvasHeight * lastImpactY
                val alpha = lastEstimatedImpact.value
                val impactRadius = 24.dp.toPx() * (1f - alpha) + 8.dp.toPx()

                drawCircle(
                    color = Color(0xFFFF5252).copy(alpha = alpha * 0.9f),
                    radius = impactRadius,
                    center = Offset(eX, eY),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawLine(
                    color = Color(0xFFFF5252).copy(alpha = alpha),
                    start = Offset(eX - 12.dp.toPx(), eY),
                    end = Offset(eX + 12.dp.toPx(), eY),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = Color(0xFFFF5252).copy(alpha = alpha),
                    start = Offset(eX, eY - 12.dp.toPx()),
                    end = Offset(eX, eY + 12.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 3. Draw all user-defined & default Kinetic Buttons
            drawKineticButtons(
                width = canvasWidth,
                height = canvasHeight,
                buttons = buttons,
                pulseMap = pulseProgressMap,
                selectedButtonId = selectedButtonId,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                tertiaryColor = tertiaryColor
            )
        }
    }
}

private fun DrawScope.drawSubModelPhoneChassis(
    width: Float,
    height: Float,
    profile: ConcretePhoneProfile,
    formFactor: FormFactorState,
    primaryColor: Color,
    tertiaryColor: Color,
    surfaceColor: Color,
    outlineColor: Color,
    onSurfaceColor: Color
) {
    val cornerRadius = minOf(width, height) * profile.cornerRadiusFactor
    val chassisInset = 10.dp.toPx()

    val outerRect = Rect(
        offset = Offset(chassisInset, chassisInset),
        size = Size(width - chassisInset * 2, height - chassisInset * 2)
    )

    // Brushed metallic chassis frame fill
    val chassisGradient = Brush.linearGradient(
        colors = listOf(
            surfaceColor.copy(alpha = 0.40f),
            surfaceColor.copy(alpha = 0.18f),
            surfaceColor.copy(alpha = 0.32f)
        ),
        start = Offset(0f, 0f),
        end = Offset(width, height)
    )

    drawRoundRect(
        brush = chassisGradient,
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    // Outer structural rim (chassis perimeter)
    drawRoundRect(
        color = outlineColor.copy(alpha = 0.85f),
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = 3.2.dp.toPx())
    )

    // Inner edge highlight
    val innerInset = chassisInset + 6.dp.toPx()
    val innerRadius = maxOf(2.dp.toPx(), cornerRadius - 4.dp.toPx())
    drawRoundRect(
        color = primaryColor.copy(alpha = 0.20f),
        topLeft = Offset(innerInset, innerInset),
        size = Size(width - innerInset * 2, height - innerInset * 2),
        cornerRadius = CornerRadius(innerRadius, innerRadius),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    )

    // Hardware buttons tailored to detected model
    val buttonWidth = 4.5.dp.toPx()

    if (profile.hasAlertSlider) {
        // Left frame Alert Slider (OnePlus)
        drawRoundRect(
            color = tertiaryColor,
            topLeft = Offset(outerRect.left - buttonWidth, outerRect.top + height * 0.18f),
            size = Size(buttonWidth, height * 0.06f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }

    if (profile.hasAirTriggerNotches) {
        // ROG Phone ultrasonic AirTrigger shoulder zones
        val notchHeight = height * 0.12f
        drawRoundRect(
            color = tertiaryColor.copy(alpha = 0.8f),
            topLeft = Offset(outerRect.left - buttonWidth * 0.8f, outerRect.top + height * 0.05f),
            size = Size(buttonWidth, notchHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawRoundRect(
            color = tertiaryColor.copy(alpha = 0.8f),
            topLeft = Offset(outerRect.right - buttonWidth * 0.2f, outerRect.top + height * 0.05f),
            size = Size(buttonWidth, notchHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }

    // Volume Rocker
    val volumeHeight = height * 0.13f
    val isLeftVolume = profile.designStyle != PhoneDesignStyle.SAMSUNG_GALAXY_ULTRA &&
            profile.designStyle != PhoneDesignStyle.SAMSUNG_GALAXY_SERIES
    val volumeX = if (isLeftVolume) outerRect.left - buttonWidth else outerRect.right
    val volumeY = outerRect.top + height * 0.23f

    drawRoundRect(
        color = primaryColor.copy(alpha = 0.85f),
        topLeft = Offset(volumeX, volumeY),
        size = Size(buttonWidth, volumeHeight),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Power / Lock Key
    val powerHeight = height * 0.08f
    val powerX = outerRect.right
    val powerY = if (!isLeftVolume) volumeY + volumeHeight + 12.dp.toPx() else outerRect.top + height * 0.28f

    drawRoundRect(
        color = primaryColor.copy(alpha = 0.85f),
        topLeft = Offset(powerX, powerY),
        size = Size(buttonWidth, powerHeight),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    if (profile.hasDedicatedShutterKey) {
        // Sony Xperia dedicated camera two-stage shutter button
        drawRoundRect(
            color = Color(0xFFFF7043),
            topLeft = Offset(outerRect.right, outerRect.bottom - height * 0.16f),
            size = Size(buttonWidth, height * 0.07f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }

    // Antenna Bands (realistic RF isolation joints)
    val bandThickness = 3.dp.toPx()
    val bandLength = 8.dp.toPx()
    val bandColor = outlineColor.copy(alpha = 0.9f)
    drawRect(bandColor, Offset(outerRect.left, outerRect.top + height * 0.14f), Size(bandThickness, bandLength))
    drawRect(bandColor, Offset(outerRect.right - bandThickness, outerRect.top + height * 0.14f), Size(bandThickness, bandLength))
    drawRect(bandColor, Offset(outerRect.left, outerRect.bottom - height * 0.14f), Size(bandThickness, bandLength))
    drawRect(bandColor, Offset(outerRect.right - bandThickness, outerRect.bottom - height * 0.14f), Size(bandThickness, bandLength))

    // Display Punch-Hole Selfie Camera
    drawCircle(
        color = Color.Black.copy(alpha = 0.85f),
        radius = 4.5.dp.toPx(),
        center = Offset(width * profile.punchHolePosition.x, height * profile.punchHolePosition.y)
    )
    drawCircle(
        color = primaryColor.copy(alpha = 0.6f),
        radius = 1.8.dp.toPx(),
        center = Offset(width * profile.punchHolePosition.x, height * profile.punchHolePosition.y)
    )

    // Precision Sub-Model Camera Module Architecture
    when (profile.designStyle) {
        PhoneDesignStyle.GOOGLE_PIXEL_VISOR -> {
            // Full-width horizontal metallic camera visor bar
            val visorTop = height * 0.14f
            val visorHeight = height * 0.10f
            val visorWidth = outerRect.width
            val visorRect = Rect(Offset(outerRect.left, visorTop), Size(visorWidth, visorHeight))

            drawRoundRect(
                color = outlineColor.copy(alpha = 0.9f),
                topLeft = visorRect.topLeft,
                size = visorRect.size,
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Inner glass pill cut-out for lenses
            val pillWidth = visorWidth * 0.38f
            val pillHeight = visorHeight * 0.65f
            val pillLeft = outerRect.left + visorWidth * 0.10f
            val pillTop = visorTop + (visorHeight - pillHeight) / 2f

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.75f),
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f)
            )

            // Dual lenses inside pill
            drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = Offset(pillLeft + pillWidth * 0.32f, pillTop + pillHeight / 2f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = Offset(pillLeft + pillWidth * 0.70f, pillTop + pillHeight / 2f), style = Stroke(1.5.dp.toPx()))

            // Periscope telephoto circular cutout
            drawCircle(color = primaryColor, radius = 6.5.dp.toPx(), center = Offset(pillLeft + pillWidth + 24.dp.toPx(), pillTop + pillHeight / 2f), style = Stroke(1.5.dp.toPx()))

            // LED Flash
            drawCircle(color = Color(0xFFFFD54F), radius = 3.5.dp.toPx(), center = Offset(outerRect.right - 24.dp.toPx(), pillTop + pillHeight / 2f))
        }

        PhoneDesignStyle.SAMSUNG_GALAXY_ULTRA -> {
            // Floating separate circular camera rings (no shared bump)
            val leftX = width * 0.20f
            val rightX = width * 0.36f
            val startY = height * 0.11f
            val ringRadius = 12.dp.toPx()
            val stepY = 32.dp.toPx()

            // 3 Main Vertical Lenses
            for (i in 0..2) {
                val cy = startY + (i * stepY)
                drawCircle(color = outlineColor, radius = ringRadius + 2.dp.toPx(), center = Offset(leftX, cy), style = Stroke(2.dp.toPx()))
                drawCircle(color = primaryColor.copy(alpha = 0.8f), radius = ringRadius * 0.65f, center = Offset(leftX, cy), style = Stroke(2.dp.toPx()))
            }

            // Laser AF & 3x Telephoto on right column
            drawCircle(color = outlineColor, radius = ringRadius * 0.75f, center = Offset(rightX, startY + stepY * 0.3f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = outlineColor, radius = ringRadius * 0.75f, center = Offset(rightX, startY + stepY * 1.5f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = Color(0xFFFFD54F), radius = 3.dp.toPx(), center = Offset(rightX, startY + stepY * 0.9f))
        }

        PhoneDesignStyle.CIRCULAR_WATCH_DIAL -> {
            // Big centered luxury circular camera island
            val centerX = width * 0.50f
            val centerY = height * 0.21f
            val dialRadius = minOf(width, height) * 0.24f

            drawCircle(color = outlineColor.copy(alpha = 0.65f), radius = dialRadius, center = Offset(centerX, centerY), style = Stroke(3.dp.toPx()))
            drawCircle(color = surfaceColor.copy(alpha = 0.5f), radius = dialRadius - 2.dp.toPx(), center = Offset(centerX, centerY))

            // Quad lenses positioned in circle
            val lensOffset = dialRadius * 0.48f
            val lensRad = 8.dp.toPx()
            drawCircle(color = primaryColor, radius = lensRad, center = Offset(centerX - lensOffset, centerY - lensOffset * 0.4f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = primaryColor, radius = lensRad, center = Offset(centerX + lensOffset, centerY - lensOffset * 0.4f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = primaryColor, radius = lensRad, center = Offset(centerX, centerY + lensOffset * 0.7f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = Color(0xFFFFD54F), radius = 3.5.dp.toPx(), center = Offset(centerX, centerY - lensOffset * 0.8f))
        }

        else -> {
            // Modern Flagship Squircle Bump
            val islandW = width * 0.28f
            val islandH = height * 0.16f
            val islandL = width * 0.12f
            val islandT = height * 0.08f

            drawRoundRect(
                color = outlineColor.copy(alpha = 0.7f),
                topLeft = Offset(islandL, islandT),
                size = Size(islandW, islandH),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            val lRad = islandW * 0.19f
            drawCircle(color = primaryColor, radius = lRad, center = Offset(islandL + islandW * 0.36f, islandT + islandH * 0.32f), style = Stroke(2.dp.toPx()))
            drawCircle(color = primaryColor, radius = lRad, center = Offset(islandL + islandW * 0.36f, islandT + islandH * 0.70f), style = Stroke(2.dp.toPx()))
            drawCircle(color = primaryColor, radius = lRad * 0.75f, center = Offset(islandL + islandW * 0.74f, islandT + islandH * 0.35f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = Color(0xFFFFD54F), radius = 3.5.dp.toPx(), center = Offset(islandL + islandW * 0.74f, islandT + islandH * 0.68f))
        }
    }

    // USB-C and bottom Speaker grilles
    val usbWidth = width * 0.14f
    drawRoundRect(
        color = outlineColor,
        topLeft = Offset(width / 2f - usbWidth / 2f, outerRect.bottom - 4.dp.toPx()),
        size = Size(usbWidth, 3.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Foldable crease / flip spine
    if (formFactor.type == FormFactorType.FOLDABLE_UNFOLDED) {
        drawLine(
            color = tertiaryColor.copy(alpha = 0.6f),
            start = Offset(width / 2f, outerRect.top),
            end = Offset(width / 2f, outerRect.bottom),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
    } else if (formFactor.type == FormFactorType.FLIP_OPEN || formFactor.type == FormFactorType.FLIP_HALF_OPEN) {
        drawLine(
            color = tertiaryColor.copy(alpha = 0.6f),
            start = Offset(outerRect.left, height / 2f),
            end = Offset(outerRect.right, height / 2f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
    }
}

private fun DrawScope.drawKineticButtons(
    width: Float,
    height: Float,
    buttons: List<KineticButton>,
    pulseMap: Map<String, Animatable<Float, *>>,
    selectedButtonId: String?,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color
) {
    buttons.forEach { button ->
        val cx = width * button.normalizedX
        val cy = height * button.normalizedY
        val pulseVal = pulseMap[button.id]?.value ?: 0f
        val isSelected = selectedButtonId == button.id

        // Dynamic Shockwave Propagation Ripple
        if (pulseVal > 0.01f) {
            val maxRippleRadius = 52.dp.toPx()
            val currentRadius = 14.dp.toPx() + (maxRippleRadius * (1f - pulseVal))
            val alpha = pulseVal.coerceIn(0f, 1f)

            // Expanding shockwave ring
            drawCircle(
                color = tertiaryColor.copy(alpha = alpha * 0.85f),
                radius = currentRadius,
                center = Offset(cx, cy),
                style = Stroke(width = (4f * pulseVal).dp.toPx())
            )

            // Radial impulse glow
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

        // Kinetic Button Node
        val baseRadius = if (isSelected) 11.dp.toPx() else 8.dp.toPx()
        val nodeColor = when {
            pulseVal > 0.1f -> tertiaryColor
            !button.isEnabled -> Color.Gray.copy(alpha = 0.4f)
            !button.isDefault -> secondaryColor
            isSelected -> primaryColor
            else -> primaryColor.copy(alpha = 0.75f)
        }

        // Outer glow on selection
        if (isSelected) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = baseRadius + 6.dp.toPx(),
                center = Offset(cx, cy)
            )
        }

        drawCircle(
            color = nodeColor,
            radius = baseRadius,
            center = Offset(cx, cy)
        )

        // Center core
        drawCircle(
            color = Color.White,
            radius = baseRadius * 0.42f,
            center = Offset(cx, cy)
        )
    }
}

private fun findNearestButton(
    tapOffset: Offset,
    width: Float,
    height: Float,
    buttons: List<KineticButton>
): KineticButton? {
    val hitThresholdPx = 48f * 3f
    var closest: KineticButton? = null
    var minDistance = Float.MAX_VALUE

    buttons.forEach { button ->
        val bx = width * button.normalizedX
        val by = height * button.normalizedY
        val dx = tapOffset.x - bx
        val dy = tapOffset.y - by
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < hitThresholdPx && distance < minDistance) {
            minDistance = distance
            closest = button
        }
    }

    return closest
}
