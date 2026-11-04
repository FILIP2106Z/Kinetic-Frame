package com.example.model

data class PresetProfile(
    val id: String,
    val name: String,
    val description: String,
    val mappings: Map<ChassisZone, ActionConfig>
)

object PresetDefaults {

    val GAMER_FPS = PresetProfile(
        id = "gamer_fps",
        name = "FPS Gaming Triggers",
        description = "Turns top chassis corners into tactile L1/R1 trigger buttons for mobile shooters.",
        mappings = mapOf(
            ChassisZone.SHOULDER_L1 to ActionConfig(
                actionType = SystemActionType.VIRTUAL_TOUCH_TAP,
                touchCoordinateX = 350f,
                touchCoordinateY = 550f, // typically ADS / Aim button in games
                hapticFeedback = true
            ),
            ChassisZone.SHOULDER_R1 to ActionConfig(
                actionType = SystemActionType.VIRTUAL_TOUCH_TAP,
                touchCoordinateX = 1800f,
                touchCoordinateY = 600f, // Fire button in games
                hapticFeedback = true
            ),
            ChassisZone.FRAME_MID_LEFT to ActionConfig(
                actionType = SystemActionType.VOLUME_DOWN,
                hapticFeedback = true
            ),
            ChassisZone.FRAME_MID_RIGHT to ActionConfig(
                actionType = SystemActionType.VOLUME_UP,
                hapticFeedback = true
            ),
            ChassisZone.SQUEEZE_FRAME to ActionConfig(
                actionType = SystemActionType.VIRTUAL_TOUCH_TAP,
                touchCoordinateX = 1600f,
                touchCoordinateY = 850f, // Reload button
                hapticFeedback = true
            )
        )
    )

    val MEDIA_AND_HARDWARE = PresetProfile(
        id = "media_hardware",
        name = "Media & Hardware Quick Actions",
        description = "Control audio playback, camera torch, and volume directly through chassis taps.",
        mappings = mapOf(
            ChassisZone.SHOULDER_L1 to ActionConfig(
                actionType = SystemActionType.VOLUME_DOWN,
                hapticFeedback = true
            ),
            ChassisZone.SHOULDER_R1 to ActionConfig(
                actionType = SystemActionType.VOLUME_UP,
                hapticFeedback = true
            ),
            ChassisZone.BACK_TOP to ActionConfig(
                actionType = SystemActionType.TORCH_TOGGLE,
                hapticFeedback = true
            ),
            ChassisZone.BACK_CENTER to ActionConfig(
                actionType = SystemActionType.MEDIA_PLAY_PAUSE,
                hapticFeedback = true
            ),
            ChassisZone.BACK_BOTTOM to ActionConfig(
                actionType = SystemActionType.MEDIA_NEXT,
                hapticFeedback = true
            ),
            ChassisZone.SQUEEZE_FRAME to ActionConfig(
                actionType = SystemActionType.SCREENSHOT,
                hapticFeedback = true
            )
        )
    )

    val SYSTEM_NAV = PresetProfile(
        id = "system_nav",
        name = "One-Handed System Navigation",
        description = "Chassis edge taps act as universal navigation controls without reaching for buttons.",
        mappings = mapOf(
            ChassisZone.FRAME_MID_LEFT to ActionConfig(
                actionType = SystemActionType.NAV_BACK,
                hapticFeedback = true
            ),
            ChassisZone.FRAME_MID_RIGHT to ActionConfig(
                actionType = SystemActionType.NAV_RECENTS,
                hapticFeedback = true
            ),
            ChassisZone.SHOULDER_L1 to ActionConfig(
                actionType = SystemActionType.NAV_NOTIFICATIONS,
                hapticFeedback = true
            ),
            ChassisZone.SHOULDER_R1 to ActionConfig(
                actionType = SystemActionType.NAV_QUICK_SETTINGS,
                hapticFeedback = true
            ),
            ChassisZone.SQUEEZE_FRAME to ActionConfig(
                actionType = SystemActionType.NAV_HOME,
                hapticFeedback = true
            ),
            ChassisZone.BACK_CENTER to ActionConfig(
                actionType = SystemActionType.SCREENSHOT,
                hapticFeedback = true
            )
        )
    )

    val ALL_PRESETS = listOf(GAMER_FPS, MEDIA_AND_HARDWARE, SYSTEM_NAV)
}
