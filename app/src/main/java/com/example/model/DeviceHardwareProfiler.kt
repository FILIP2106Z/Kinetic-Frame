package com.example.model

import android.content.Context
import android.os.Build
import androidx.compose.ui.geometry.Offset
import java.util.Locale

enum class PhoneDesignStyle {
    GOOGLE_PIXEL_VISOR,      // Full-width horizontal camera visor bar
    SAMSUNG_GALAXY_ULTRA,     // Boxy sharp corners, floating individual lens rings
    SAMSUNG_GALAXY_SERIES,    // Rounded corners, vertical triple lens island
    CIRCULAR_WATCH_DIAL,      // Xiaomi / OnePlus / Vivo circular centered camera module
    GAMING_PHONE_AIRTRIGGERS, // Asus ROG / Black Shark cyber contours & dual shoulder notches
    SONY_CINEMA_21_9,         // Ultra tall 21:9 flat chassis with dedicated shutter key
    MODERN_FLAGSHIP_SQUIRCLE  // Universal modern rounded squircle camera bump
}

data class ConcretePhoneProfile(
    val manufacturer: String,
    val model: String,
    val marketingName: String,
    val designStyle: PhoneDesignStyle,
    val cornerRadiusFactor: Float, // 0.0f (sharp boxy) to 0.14f (heavily rounded)
    val aspectRatio: Float,
    val cameraIslandCenter: Offset,
    val punchHolePosition: Offset,
    val hasAirTriggerNotches: Boolean = false,
    val hasAlertSlider: Boolean = false,
    val hasDedicatedShutterKey: Boolean = false
)

object DeviceHardwareProfiler {

    fun detectProfile(context: Context): ConcretePhoneProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val device = Build.DEVICE.orEmpty()

        val fullIdentity = "$manufacturer $model $brand $device".lowercase(Locale.ROOT)

        val designStyle = when {
            fullIdentity.contains("pixel") -> PhoneDesignStyle.GOOGLE_PIXEL_VISOR
            fullIdentity.contains("ultra") || fullIdentity.contains("s928") || fullIdentity.contains("s918") || fullIdentity.contains("note") -> PhoneDesignStyle.SAMSUNG_GALAXY_ULTRA
            fullIdentity.contains("samsung") || fullIdentity.contains("galaxy") || fullIdentity.contains("sm-") -> PhoneDesignStyle.SAMSUNG_GALAXY_SERIES
            fullIdentity.contains("rog") || fullIdentity.contains("blackshark") || fullIdentity.contains("redmagic") -> PhoneDesignStyle.GAMING_PHONE_AIRTRIGGERS
            fullIdentity.contains("sony") || fullIdentity.contains("xperia") -> PhoneDesignStyle.SONY_CINEMA_21_9
            fullIdentity.contains("oneplus") || fullIdentity.contains("xiaomi") || fullIdentity.contains("vivo") || fullIdentity.contains("oppo") -> PhoneDesignStyle.CIRCULAR_WATCH_DIAL
            else -> PhoneDesignStyle.MODERN_FLAGSHIP_SQUIRCLE
        }

        val cornerRadius = when (designStyle) {
            PhoneDesignStyle.SAMSUNG_GALAXY_ULTRA -> 0.035f // Sharp boxy
            PhoneDesignStyle.SONY_CINEMA_21_9 -> 0.045f
            PhoneDesignStyle.GOOGLE_PIXEL_VISOR -> 0.135f // Very rounded
            else -> 0.11f
        }

        val aspectRatio = when (designStyle) {
            PhoneDesignStyle.SONY_CINEMA_21_9 -> 21f / 9f
            PhoneDesignStyle.SAMSUNG_GALAXY_ULTRA -> 19.3f / 9f
            PhoneDesignStyle.GOOGLE_PIXEL_VISOR -> 20f / 9f
            else -> 19.5f / 9f
        }

        val marketingName = when {
            fullIdentity.contains("pixel") -> "Google ${Build.MODEL}"
            fullIdentity.contains("samsung") -> "Samsung ${Build.MODEL}"
            fullIdentity.contains("xiaomi") -> "Xiaomi ${Build.MODEL}"
            fullIdentity.contains("oneplus") -> "OnePlus ${Build.MODEL}"
            else -> "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        }

        return ConcretePhoneProfile(
            manufacturer = manufacturer,
            model = model,
            marketingName = marketingName,
            designStyle = designStyle,
            cornerRadiusFactor = cornerRadius,
            aspectRatio = aspectRatio,
            cameraIslandCenter = when (designStyle) {
                PhoneDesignStyle.GOOGLE_PIXEL_VISOR -> Offset(0.50f, 0.18f)
                PhoneDesignStyle.CIRCULAR_WATCH_DIAL -> Offset(0.50f, 0.22f)
                else -> Offset(0.24f, 0.18f)
            },
            punchHolePosition = when (designStyle) {
                PhoneDesignStyle.SONY_CINEMA_21_9 -> Offset(0.5f, 0.04f)
                else -> Offset(0.5f, 0.05f)
            },
            hasAirTriggerNotches = designStyle == PhoneDesignStyle.GAMING_PHONE_AIRTRIGGERS,
            hasAlertSlider = fullIdentity.contains("oneplus"),
            hasDedicatedShutterKey = designStyle == PhoneDesignStyle.SONY_CINEMA_21_9
        )
    }
}
