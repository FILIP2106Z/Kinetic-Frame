package com.example.model

/**
 * Physical regions and trigger points across the device chassis.
 */
enum class ChassisZone(
    val id: String,
    val displayName: String,
    val shortCode: String,
    val defaultNormalizedX: Float, // 0.0f (left) to 1.0f (right)
    val defaultNormalizedY: Float, // 0.0f (top) to 1.0f (bottom)
    val isEdge: Boolean = true
) {
    SHOULDER_L1("shoulder_l1", "Left Shoulder (L1)", "L1", 0.12f, 0.04f),
    SHOULDER_R1("shoulder_r1", "Right Shoulder (R1)", "R1", 0.88f, 0.04f),
    FRAME_MID_LEFT("frame_mid_left", "Mid-Left Frame", "ML", 0.03f, 0.45f),
    FRAME_MID_RIGHT("frame_mid_right", "Mid-Right Frame", "MR", 0.97f, 0.45f),
    FRAME_BOTTOM_LEFT("frame_bottom_left", "Bottom-Left Corner", "BL", 0.12f, 0.96f),
    FRAME_BOTTOM_RIGHT("frame_bottom_right", "Bottom-Right Corner", "BR", 0.88f, 0.96f),
    BACK_TOP("back_top", "Back Top / Camera Island", "BT", 0.50f, 0.18f, isEdge = false),
    BACK_CENTER("back_center", "Back Center", "BC", 0.50f, 0.50f, isEdge = false),
    BACK_BOTTOM("back_bottom", "Back Lower", "BB", 0.50f, 0.82f, isEdge = false),
    SQUEEZE_FRAME("squeeze_frame", "Bilateral Frame Squeeze", "SQZ", 0.50f, 0.50f)
}

/**
 * Arbitrary user-defined Kinetic Button that can be placed anywhere on the chassis.
 */
data class KineticButton(
    val id: String,
    val label: String,
    val shortCode: String,
    val normalizedX: Float, // 0.0f (left) to 1.0f (right)
    val normalizedY: Float, // 0.0f (top) to 1.0f (bottom)
    val isEdge: Boolean = true,
    val isDefault: Boolean = false,
    val actionConfig: ActionConfig = ActionConfig(),
    val calibratedTorqueX: Float? = null,
    val calibratedTorqueY: Float? = null,
    val calibratedZRatio: Float? = null,
    val isEnabled: Boolean = true
)

enum class ChassisGestureType(val displayName: String) {
    SINGLE_TAP("Single Tap"),
    DOUBLE_TAP("Rapid Double Tap"),
    SQUEEZE_PULSE("Bilateral Squeeze"),
    DUAL_SHOULDER_CHORD("L1 + R1 Dual Chord"),
    DIAGONAL_CHORD("Top-L + Bottom-R Chord")
}

enum class SystemActionType(val displayName: String, val category: String) {
    NONE("No Action", "General"),
    NAV_BACK("System Back", "Navigation"),
    NAV_HOME("System Home", "Navigation"),
    NAV_RECENTS("Overview / Recents", "Navigation"),
    NAV_NOTIFICATIONS("Pull Notifications", "Navigation"),
    NAV_QUICK_SETTINGS("Quick Settings", "Navigation"),
    SCREENSHOT("Take Screenshot", "System"),
    LOCK_SCREEN("Lock Screen", "System"),
    TORCH_TOGGLE("Flashlight Toggle", "Hardware"),
    MEDIA_PLAY_PAUSE("Media Play / Pause", "Media"),
    MEDIA_NEXT("Next Track", "Media"),
    VOLUME_UP("Volume Up", "Media"),
    VOLUME_DOWN("Volume Down", "Media"),
    VIRTUAL_TOUCH_TAP("Simulate Screen Tap", "Gaming / Touch"),
    VIRTUAL_KEYCODE("Simulate Gamepad Keycode", "Gaming / Touch")
}

data class ActionConfig(
    val actionType: SystemActionType = SystemActionType.NONE,
    val touchCoordinateX: Float = 500f,
    val touchCoordinateY: Float = 500f,
    val customKeycode: Int = 0,
    val hapticFeedback: Boolean = true
)

data class ChassisTriggerEvent(
    val id: Long = System.currentTimeMillis(),
    val zone: ChassisZone,
    val buttonId: String = zone.id,
    val buttonLabel: String = zone.displayName,
    val gestureType: ChassisGestureType,
    val confidence: Float,
    val impulseEnergy: Float,
    val torqueX: Float,
    val torqueY: Float,
    val torqueZ: Float,
    val estimatedX: Float = zone.defaultNormalizedX,
    val estimatedY: Float = zone.defaultNormalizedY,
    val actionDispatched: SystemActionType,
    val dispatchLatencyMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class FormFactorType(val displayName: String) {
    CANDYBAR("Standard Smartphone"),
    FOLDABLE_UNFOLDED("Foldable (Main Display)"),
    FOLDABLE_FOLDED("Foldable (Cover Display)"),
    FLIP_OPEN("Flip Phone (Open)"),
    FLIP_HALF_OPEN("Flip Phone (Flex Mode)")
}

data class FormFactorState(
    val type: FormFactorType = FormFactorType.CANDYBAR,
    val hingeAngleDegrees: Float = 180f,
    val aspectRatio: Float = 19.5f / 9f,
    val isFoldableDevice: Boolean = false
)

data class SensorTelemetryPoint(
    val timestampMs: Long,
    val accelMagnitude: Float,
    val gyroMagnitude: Float,
    val filteredImpulse: Float,
    val ambientThreshold: Float,
    val isTriggerDetected: Boolean = false
)
