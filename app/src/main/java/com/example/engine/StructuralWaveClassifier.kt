package com.example.engine

import com.example.model.ChassisGestureType
import com.example.model.ChassisTriggerEvent
import com.example.model.ChassisZone
import com.example.model.FormFactorState
import com.example.model.FormFactorType
import com.example.model.SystemActionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Production-grade IMU signal processing and structural wave classifier.
 *
 * Implements:
 * 1. Differential jerk & angular momentum impulse extraction (200-400Hz).
 * 2. Real-time Exponential Moving Average (EMA) ambient noise baseline estimator (zero-user calibration).
 * 3. Spatial chassis zone mapping via cross-axis moment-of-inertia torque ratios.
 * 4. Multi-zone combo & rapid chord state machine.
 * 5. Dynamic form-factor structural adaptation (Foldables & Flips).
 */
class StructuralWaveClassifier(
    private var baseSensitivity: Float = 0.65f // 0.1 (low sensitivity) to 1.0 (hyper-responsive)
) {

    // Calibration & threshold parameters
    private var ambientNoiseBaseline = 4.2f
    private val emaAlpha = 0.04f
    private var refractoryPeriodMs = 180L // debounce window between consecutive single triggers

    // State tracking
    private var lastTriggerTimeMs = 0L
    private var lastTapZone: ChassisZone? = null
    private var lastTapTimeMs = 0L

    // Chord buffer for multi-zone simultaneous inputs (e.g., L1 + R1 within 45ms)
    private var pendingChordZone: ChassisZone? = null
    private var pendingChordTimeMs = 0L
    private val chordWindowMs = 50L

    // Circular sample buffer for derivative and zero-crossing calculation
    private val bufferSize = 16
    private val accelXBuf = FloatArray(bufferSize)
    private val accelYBuf = FloatArray(bufferSize)
    private val accelZBuf = FloatArray(bufferSize)
    private val gyroXBuf = FloatArray(bufferSize)
    private val gyroYBuf = FloatArray(bufferSize)
    private val gyroZBuf = FloatArray(bufferSize)
    private var bufIndex = 0
    private var sampleCount = 0

    // Form factor state
    private var currentFormFactor = FormFactorState()

    fun setSensitivity(sensitivity: Float) {
        this.baseSensitivity = sensitivity.coerceIn(0.1f, 1.0f)
    }

    fun updateFormFactor(formFactor: FormFactorState) {
        this.currentFormFactor = formFactor
    }

    /**
     * Process an incoming high-rate IMU packet.
     *
     * @return ChassisTriggerEvent if a validated chassis tap/squeeze/combo is identified, null otherwise.
     */
    fun processSample(
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float,
        timestampMs: Long
    ): ChassisTriggerEvent? {
        val prevIdx = if (bufIndex == 0) bufferSize - 1 else bufIndex - 1

        val prevAx = accelXBuf[prevIdx]
        val prevAy = accelYBuf[prevIdx]
        val prevAz = accelZBuf[prevIdx]
        val prevGx = gyroXBuf[prevIdx]
        val prevGy = gyroYBuf[prevIdx]
        val prevGz = gyroZBuf[prevIdx]

        // Store into circular buffer
        accelXBuf[bufIndex] = ax
        accelYBuf[bufIndex] = ay
        accelZBuf[bufIndex] = az
        gyroXBuf[bufIndex] = gx
        gyroYBuf[bufIndex] = gy
        gyroZBuf[bufIndex] = gz

        bufIndex = (bufIndex + 1) % bufferSize
        sampleCount++

        if (sampleCount < 4) return null

        // 1. High-pass differential calculation (Jerk and Angular Impulse)
        val dAx = ax - prevAx
        val dAy = ay - prevAy
        val dAz = az - prevAz
        val linearJerk = sqrt(dAx * dAx + dAy * dAy + dAz * dAz)

        val dGx = gx - prevGx
        val dGy = gy - prevGy
        val dGz = gz - prevGz
        val angularImpulse = sqrt(dGx * dGx + dGy * dGy + dGz * dGz)

        // Form-factor compensation: Unfolded foldables have higher width moment of inertia
        val widthScale = if (currentFormFactor.type == FormFactorType.FOLDABLE_UNFOLDED) 1.45f else 1.0f

        // Combined structural wave energy metric
        val impulseEnergy = (linearJerk * 1.8f + angularImpulse * 9.2f * widthScale)

        // 2. Dynamic Ambient Noise Baseline Adaptation
        // When energy is calm, continually adapt baseline to user's motion environment (walking, in car, on table)
        if (impulseEnergy < ambientNoiseBaseline * 1.8f) {
            ambientNoiseBaseline = (1f - emaAlpha) * ambientNoiseBaseline + emaAlpha * impulseEnergy
            ambientNoiseBaseline = ambientNoiseBaseline.coerceIn(1.5f, 25.0f)
        }

        // Compute dynamic trigger threshold based on baseline and user sensitivity
        // Sensitivity 1.0 -> multiplier 1.6x baseline, Sensitivity 0.1 -> multiplier 5.0x baseline
        val sensitivityMultiplier = 5.2f - (baseSensitivity * 3.4f)
        val dynamicThreshold = max(6.5f, ambientNoiseBaseline * sensitivityMultiplier)

        // 3. Peak Threshold Evaluation
        if (impulseEnergy < dynamicThreshold) {
            return null
        }

        // Debounce check
        if (timestampMs - lastTriggerTimeMs < refractoryPeriodMs) {
            return null
        }

        // 4. Spatial Zone Localization
        val detectedZone = classifyZone(
            dAx = dAx, dAy = dAy, dAz = dAz,
            dGx = dGx, dGy = dGy, dGz = dGz,
            gx = gx, gy = gy, gz = gz,
            linearJerk = linearJerk,
            angularImpulse = angularImpulse
        )

        // 5. Multi-Zone Combo & Chord State Machine
        var gestureType = ChassisGestureType.SINGLE_TAP

        // Check if there is an active chord candidate within the chord window
        if (pendingChordZone != null && (timestampMs - pendingChordTimeMs) <= chordWindowMs) {
            val zoneA = pendingChordZone!!
            val zoneB = detectedZone

            // Simultaneous dual shoulder chord
            if ((zoneA == ChassisZone.SHOULDER_L1 && zoneB == ChassisZone.SHOULDER_R1) ||
                (zoneA == ChassisZone.SHOULDER_R1 && zoneB == ChassisZone.SHOULDER_L1)) {
                gestureType = ChassisGestureType.DUAL_SHOULDER_CHORD
                pendingChordZone = null
            } else if ((zoneA == ChassisZone.SHOULDER_L1 && zoneB == ChassisZone.FRAME_BOTTOM_RIGHT) ||
                (zoneA == ChassisZone.SHOULDER_R1 && zoneB == ChassisZone.FRAME_BOTTOM_LEFT)) {
                gestureType = ChassisGestureType.DIAGONAL_CHORD
                pendingChordZone = null
            }
        } else {
            // Buffer this strike as possible first half of a chord
            pendingChordZone = detectedZone
            pendingChordTimeMs = timestampMs
        }

        // Rapid double tap detection on the same zone
        if (gestureType == ChassisGestureType.SINGLE_TAP) {
            if (lastTapZone == detectedZone && (timestampMs - lastTapTimeMs) in 110L..380L) {
                gestureType = ChassisGestureType.DOUBLE_TAP
                lastTapZone = null
            } else {
                lastTapZone = detectedZone
                lastTapTimeMs = timestampMs
            }
        }

        lastTriggerTimeMs = timestampMs

        val confidence = min(0.99f, 0.70f + (impulseEnergy / (dynamicThreshold * 3f)) * 0.29f)

        return ChassisTriggerEvent(
            zone = detectedZone,
            gestureType = gestureType,
            confidence = confidence,
            impulseEnergy = impulseEnergy,
            torqueX = dGx,
            torqueY = dGy,
            torqueZ = dGz,
            actionDispatched = SystemActionType.NONE,
            timestampMs = timestampMs
        )
    }

    /**
     * Determines physical strike location based on structural wave propagation
     * and rotational moment directions.
     */
    private fun classifyZone(
        dAx: Float, dAy: Float, dAz: Float,
        dGx: Float, dGy: Float, dGz: Float,
        gx: Float, gy: Float, gz: Float,
        linearJerk: Float,
        angularImpulse: Float
    ): ChassisZone {
        // High z-axis jerk with relatively small rotational impulse indicates direct perpendicular back impact
        val zRatio = abs(dAz) / (angularImpulse + 0.1f)

        if (zRatio > 3.8f) {
            // Back tap: determine vertical third from gy / pitch torque
            return when {
                dGx < -0.8f -> ChassisZone.BACK_TOP
                dGx > 0.8f -> ChassisZone.BACK_BOTTOM
                else -> ChassisZone.BACK_CENTER
            }
        }

        // Bilateral Squeeze detection: High symmetric damping with low net roll/pitch torque
        if (angularImpulse < 1.2f && linearJerk > 8.0f && abs(dAx) > abs(dAy) * 1.5f) {
            return ChassisZone.SQUEEZE_FRAME
        }

        // Edge and Shoulder Taps:
        // Rolling torque around Y-axis determines Left vs Right strike:
        // A strike on the Left Frame creates a positive torque dGy (phone rolls right)
        // A strike on the Right Frame creates a negative torque dGy (phone rolls left)
        val isLeft = dGy > 0.05f

        // Pitching torque around X-axis determines Top vs Bottom strike:
        // A strike on the Top edge causes positive pitch rate / negative torque dGx (phone leans forward)
        // A strike on the Bottom edge causes negative pitch rate / positive torque dGx
        val isTop = dGx < -0.10f
        val isBottom = dGx > 0.25f

        return when {
            isLeft && isTop -> ChassisZone.SHOULDER_L1
            !isLeft && isTop -> ChassisZone.SHOULDER_R1
            isLeft && isBottom -> ChassisZone.FRAME_BOTTOM_LEFT
            !isLeft && isBottom -> ChassisZone.FRAME_BOTTOM_RIGHT
            isLeft -> ChassisZone.FRAME_MID_LEFT
            else -> ChassisZone.FRAME_MID_RIGHT
        }
    }

    fun getCurrentThreshold(): Float = max(6.5f, ambientNoiseBaseline * (5.2f - (baseSensitivity * 3.4f)))
    fun getCurrentAmbientBaseline(): Float = ambientNoiseBaseline
}
