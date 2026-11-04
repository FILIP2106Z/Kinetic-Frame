package com.example

import com.example.engine.StructuralWaveClassifier
import com.example.model.ChassisZone
import com.example.service.KineticSensorService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testStructuralWaveClassifierInitialization() {
        val classifier = StructuralWaveClassifier(baseSensitivity = 0.85f)
        val defaultButtons = KineticSensorService.createDefaultButtons()
        classifier.setRegisteredButtons(defaultButtons)

        val initialThreshold = classifier.getCurrentThreshold()
        assertTrue("Initial threshold should be positive", initialThreshold > 0f)

        // Feed quiet samples to establish baseline
        for (i in 0..10) {
            val event = classifier.processSample(
                ax = 0f, ay = 9.8f, az = 0f,
                gx = 0f, gy = 0f, gz = 0f,
                timestampMs = i * 10L
            )
            org.junit.Assert.assertNull(event)
        }

        // Simulate sharp physical shock on Top-Left shoulder
        val strikeEvent = classifier.processSample(
            ax = 5.0f, ay = 25.0f, az = -10.0f,
            gx = -4.5f, gy = 5.2f, gz = 0.2f,
            timestampMs = 250L
        )

        assertNotNull("High-energy mechanical shockwave should trigger a kinetic event", strikeEvent)
        assertTrue(
            "Recognition confidence must exceed 75%",
            (strikeEvent?.confidence ?: 0f) >= 0.75f
        )
        assertTrue(
            "Button ID should match L1 or Left Mid",
            strikeEvent?.buttonId == ChassisZone.SHOULDER_L1.id || strikeEvent?.buttonId == ChassisZone.FRAME_MID_LEFT.id
        )

        // Test Calibrated Recognition (95%+ recognition algorithm)
        val l1Button = defaultButtons.first { it.id == ChassisZone.SHOULDER_L1.id }
        val calibratedL1 = l1Button.copy(
            calibratedTorqueX = strikeEvent!!.torqueX,
            calibratedTorqueY = strikeEvent.torqueY
        )
        val calibratedButtons = defaultButtons.map { if (it.id == calibratedL1.id) calibratedL1 else it }
        classifier.setRegisteredButtons(calibratedButtons)

        // Return to rest
        classifier.processSample(
            ax = 0f, ay = 9.8f, az = 0f,
            gx = 0f, gy = 0f, gz = 0f,
            timestampMs = 350L
        )

        // Resimulate calibrated strike
        val calibratedStrike = classifier.processSample(
            ax = 5.0f, ay = 25.0f, az = -10.0f,
            gx = -4.5f, gy = 5.2f, gz = 0.2f,
            timestampMs = 550L
        )

        assertNotNull(calibratedStrike)
        assertTrue(
            "Calibrated kinetic button recognition must exceed 90%",
            (calibratedStrike?.confidence ?: 0f) >= 0.90f
        )
        org.junit.Assert.assertEquals(
            "Calibrated button must lock onto L1",
            ChassisZone.SHOULDER_L1.id,
            calibratedStrike?.buttonId
        )
    }
}
