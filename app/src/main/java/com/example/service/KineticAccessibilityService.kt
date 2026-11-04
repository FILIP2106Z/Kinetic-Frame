package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accessibility Service providing reliable fallback for global navigation actions
 * and simulated screen tap gestures when Root or Shizuku are not available.
 */
class KineticAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception required; used exclusively for action dispatch
    }

    override fun onInterrupt() {
        // Interruption handler
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceActive.value = false
        }
    }

    /**
     * Simulates a screen tap at (x, y) coordinates using the Accessibility gesture engine.
     */
    fun performClickAt(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete?.invoke(false)
            }
        }, null)
    }

    companion object {
        @Volatile
        var instance: KineticAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        fun performGlobal(globalActionId: Int): Boolean {
            return instance?.performGlobalAction(globalActionId) ?: false
        }

        fun simulateClick(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
            val s = instance ?: return false
            s.performClickAt(x, y, onComplete)
            return true
        }
    }
}
