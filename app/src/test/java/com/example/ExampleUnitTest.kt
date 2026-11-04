package com.example

import com.example.engine.StructuralWaveClassifier
import com.example.model.ChassisZone
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testStructuralWaveClassifierInitialization() {
        val classifier = StructuralWaveClassifier(baseSensitivity = 0.8f)
        val initialThreshold = classifier.getCurrentThreshold()
        assertTrue("Initial threshold should be positive", initialThreshold > 0f)

        // Feed quiet samples to establish baseline
        for (i in 0..10) {
            val event = classifier.processSample(
                ax = 0f, ay = 9.8f, az = 0f,
                gx = 0f, gy = 0f, gz = 0f,
                timestampMs = i * 10L
            )
            // No trigger expected during quiet state
            org.junit.Assert.assertNull(event)
        }

        // Simulate sharp shock on Top-Left shoulder (pitch negative dGx, roll positive dGy)
        val strikeEvent = classifier.processSample(
            ax = 5.0f, ay = 25.0f, az = -10.0f,
            gx = -4.5f, gy = 5.2f, gz = 0.2f,
            timestampMs = 250L
        )

        assertNotNull("High-energy shockwave should trigger an event", strikeEvent)
        assertTrue(
            "Zone should map to shoulder or edge",
            strikeEvent?.zone == ChassisZone.SHOULDER_L1 || strikeEvent?.zone == ChassisZone.FRAME_MID_LEFT
        )
    }
}
