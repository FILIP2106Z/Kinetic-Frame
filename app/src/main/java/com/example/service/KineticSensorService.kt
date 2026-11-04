package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.dispatcher.ActionDispatchEngine
import com.example.engine.StructuralWaveClassifier
import com.example.model.ActionConfig
import com.example.model.ChassisGestureType
import com.example.model.ChassisTriggerEvent
import com.example.model.ChassisZone
import com.example.model.FormFactorState
import com.example.model.KineticButton
import com.example.model.PresetDefaults
import com.example.model.PresetProfile
import com.example.model.SensorTelemetryPoint
import com.example.model.SystemActionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.sqrt

/**
 * High-performance foreground service managing continuous IMU sensor sampling (~200-400Hz),
 * real-time chassis strike classification, dynamic kinetic buttons, and privileged trigger dispatch.
 */
class KineticSensorService : Service(), SensorEventListener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var classifier: StructuralWaveClassifier
    lateinit var dispatchEngine: ActionDispatchEngine
        private set

    // Real-time sensor state
    private val rawAccel = FloatArray(3)
    private val rawGyro = FloatArray(3)
    private var lastSampleTimestampMs = 0L

    // Service state flows
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring = _isMonitoring.asStateFlow()

    private val _activePreset = MutableStateFlow(PresetDefaults.GAMER_FPS)
    val activePreset = _activePreset.asStateFlow()

    private val _kineticButtons = MutableStateFlow<List<KineticButton>>(createDefaultButtons())
    val kineticButtons = _kineticButtons.asStateFlow()

    private val _activeMappings = MutableStateFlow<Map<ChassisZone, ActionConfig>>(PresetDefaults.GAMER_FPS.mappings)
    val activeMappings = _activeMappings.asStateFlow()

    private val _sensitivity = MutableStateFlow(0.70f)
    val sensitivity = _sensitivity.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<ChassisTriggerEvent>>(emptyList())
    val recentEvents = _recentEvents.asStateFlow()

    private val _triggerEventFlow = MutableSharedFlow<ChassisTriggerEvent>(extraBufferCapacity = 16)
    val triggerEventFlow = _triggerEventFlow.asSharedFlow()

    private val _latestTelemetry = MutableStateFlow(
        SensorTelemetryPoint(
            timestampMs = System.currentTimeMillis(),
            accelMagnitude = 9.8f,
            gyroMagnitude = 0f,
            filteredImpulse = 0f,
            ambientThreshold = 12f
        )
    )
    val latestTelemetry = _latestTelemetry.asStateFlow()

    private val _formFactorState = MutableStateFlow(FormFactorState())
    val formFactorState = _formFactorState.asStateFlow()

    private val _calibratingButtonId = MutableStateFlow<String?>(null)
    val calibratingButtonId = _calibratingButtonId.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): KineticSensorService = this@KineticSensorService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        classifier = StructuralWaveClassifier(baseSensitivity = _sensitivity.value)
        classifier.setRegisteredButtons(_kineticButtons.value)

        dispatchEngine = ActionDispatchEngine(applicationContext)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        acquireWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        startSensorMonitoring()
        _isMonitoring.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startSensorMonitoring() {
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    private fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val nowMs = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                rawAccel[0] = event.values[0]
                rawAccel[1] = event.values[1]
                rawAccel[2] = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                rawGyro[0] = event.values[0]
                rawGyro[1] = event.values[1]
                rawGyro[2] = event.values[2]
            }
            else -> return
        }

        // Feed classifier at gyro rate
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val trigger = classifier.processSample(
                ax = rawAccel[0], ay = rawAccel[1], az = rawAccel[2],
                gx = rawGyro[0], gy = rawGyro[1], gz = rawGyro[2],
                timestampMs = nowMs
            )

            val aMag = sqrt(rawAccel[0] * rawAccel[0] + rawAccel[1] * rawAccel[1] + rawAccel[2] * rawAccel[2])
            val gMag = sqrt(rawGyro[0] * rawGyro[0] + rawGyro[1] * rawGyro[1] + rawGyro[2] * rawGyro[2])

            // Throttle telemetry flow update to ~30Hz for UI rendering performance
            if (nowMs - lastSampleTimestampMs > 30L) {
                lastSampleTimestampMs = nowMs
                _latestTelemetry.value = SensorTelemetryPoint(
                    timestampMs = nowMs,
                    accelMagnitude = aMag,
                    gyroMagnitude = gMag,
                    filteredImpulse = trigger?.impulseEnergy ?: (aMag * 0.1f + gMag),
                    ambientThreshold = classifier.getCurrentThreshold(),
                    isTriggerDetected = trigger != null
                )
            }

            if (trigger != null) {
                handleTriggerDetected(trigger)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleTriggerDetected(event: ChassisTriggerEvent) {
        val button = _kineticButtons.value.firstOrNull { it.id == event.buttonId }
        val actionConfig = button?.actionConfig
            ?: _activeMappings.value[event.zone]
            ?: ActionConfig()

        val actionToDispatch = actionConfig.actionType

        val finalEvent = event.copy(actionDispatched = actionToDispatch)

        // Dispatch action
        if (actionToDispatch != SystemActionType.NONE) {
            dispatchEngine.dispatch(actionConfig)
        } else {
            // Default subtle haptic if unmapped zone is tapped
            dispatchEngine.triggerHapticClick()
        }

        // Emit to flows
        serviceScope.launch {
            _triggerEventFlow.emit(finalEvent)
            val updated = (_recentEvents.value.toMutableList()).apply {
                add(0, finalEvent)
                if (size > 20) removeAt(size - 1)
            }
            _recentEvents.value = updated
        }
    }

    /**
     * Manually simulates a chassis strike for testing in the interactive visualizer.
     */
    fun simulateButtonTap(button: KineticButton) {
        val dummyEvent = ChassisTriggerEvent(
            zone = ChassisZone.entries.firstOrNull { it.id == button.id } ?: ChassisZone.SHOULDER_L1,
            buttonId = button.id,
            buttonLabel = button.label,
            gestureType = ChassisGestureType.SINGLE_TAP,
            confidence = 0.99f,
            impulseEnergy = 32.5f,
            torqueX = if (button.normalizedY < 0.2f) -1.2f else 0.4f,
            torqueY = if (button.normalizedX < 0.5f) 1.5f else -1.5f,
            torqueZ = 0.1f,
            estimatedX = button.normalizedX,
            estimatedY = button.normalizedY,
            actionDispatched = button.actionConfig.actionType,
            timestampMs = System.currentTimeMillis()
        )
        handleTriggerDetected(dummyEvent)
    }

    fun simulateChassisTap(zone: ChassisZone) {
        val btn = _kineticButtons.value.firstOrNull { it.id == zone.id }
        if (btn != null) {
            simulateButtonTap(btn)
        } else {
            val dummyButton = KineticButton(
                id = zone.id,
                label = zone.displayName,
                shortCode = zone.shortCode,
                normalizedX = zone.defaultNormalizedX,
                normalizedY = zone.defaultNormalizedY,
                isEdge = zone.isEdge
            )
            simulateButtonTap(dummyButton)
        }
    }

    fun addCustomKineticButton(
        label: String,
        shortCode: String,
        normalizedX: Float,
        normalizedY: Float,
        isEdge: Boolean,
        actionConfig: ActionConfig
    ): KineticButton {
        val newButton = KineticButton(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            label = label,
            shortCode = shortCode,
            normalizedX = normalizedX.coerceIn(0.02f, 0.98f),
            normalizedY = normalizedY.coerceIn(0.02f, 0.98f),
            isEdge = isEdge,
            isDefault = false,
            actionConfig = actionConfig
        )

        val updated = _kineticButtons.value + newButton
        _kineticButtons.value = updated
        classifier.setRegisteredButtons(updated)
        return newButton
    }

    fun updateButton(button: KineticButton) {
        val updated = _kineticButtons.value.map {
            if (it.id == button.id) button else it
        }
        _kineticButtons.value = updated
        classifier.setRegisteredButtons(updated)

        // Also update zone mapping if default
        val zone = ChassisZone.entries.firstOrNull { it.id == button.id }
        if (zone != null) {
            setZoneMapping(zone, button.actionConfig)
        }
    }

    fun removeButton(buttonId: String) {
        val updated = _kineticButtons.value.filterNot { it.id == buttonId }
        _kineticButtons.value = updated
        classifier.setRegisteredButtons(updated)
    }

    fun startCalibration(buttonId: String) {
        _calibratingButtonId.value = buttonId
        classifier.startCalibration(buttonId)
    }

    fun finishCalibration() {
        val result = classifier.finishCalibration()
        val btnId = _calibratingButtonId.value
        if (btnId != null && result != null) {
            val (tX, tY, zR) = result
            val current = _kineticButtons.value.firstOrNull { it.id == btnId }
            if (current != null) {
                val calibrated = current.copy(
                    calibratedTorqueX = tX,
                    calibratedTorqueY = tY,
                    calibratedZRatio = zR
                )
                updateButton(calibrated)
            }
        }
        _calibratingButtonId.value = null
    }

    fun cancelCalibration() {
        classifier.cancelCalibration()
        _calibratingButtonId.value = null
    }

    fun applyPreset(preset: PresetProfile) {
        _activePreset.value = preset
        _activeMappings.value = preset.mappings

        // Update button action configurations
        val updatedButtons = _kineticButtons.value.map { button ->
            val zone = ChassisZone.entries.firstOrNull { it.id == button.id }
            val mappedAction = zone?.let { preset.mappings[it] }
            if (mappedAction != null) button.copy(actionConfig = mappedAction) else button
        }
        _kineticButtons.value = updatedButtons
        classifier.setRegisteredButtons(updatedButtons)
    }

    fun setZoneMapping(zone: ChassisZone, config: ActionConfig) {
        val current = _activeMappings.value.toMutableMap()
        current[zone] = config
        _activeMappings.value = current

        // Update matching button
        val updated = _kineticButtons.value.map {
            if (it.id == zone.id) it.copy(actionConfig = config) else it
        }
        _kineticButtons.value = updated
        classifier.setRegisteredButtons(updated)
    }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value.coerceIn(0.1f, 1.0f)
        classifier.setSensitivity(value)
    }

    fun updateFormFactor(state: FormFactorState) {
        _formFactorState.value = state
        classifier.updateFormFactor(state)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KineticFrame::SensorEngineWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sensor_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.sensor_service_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sensor_service_notification_title))
            .setContentText(getString(R.string.sensor_service_notification_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensorMonitoring()
        releaseWakeLock()
        serviceScope.cancel()
        _isMonitoring.value = false
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        const val CHANNEL_ID = "kinetic_sensor_channel"
        const val NOTIFICATION_ID = 4040
        const val ACTION_STOP_SERVICE = "com.example.service.STOP"

        @Volatile
        var instance: KineticSensorService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, KineticSensorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KineticSensorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun createDefaultButtons(): List<KineticButton> {
            return listOf(
                KineticButton(
                    id = ChassisZone.SHOULDER_L1.id,
                    label = "Left Shoulder (L1)",
                    shortCode = "L1",
                    normalizedX = 0.12f,
                    normalizedY = 0.04f,
                    isEdge = true,
                    isDefault = true,
                    actionConfig = ActionConfig(
                        actionType = SystemActionType.VIRTUAL_TOUCH_TAP,
                        touchCoordinateX = 350f,
                        touchCoordinateY = 550f
                    )
                ),
                KineticButton(
                    id = ChassisZone.SHOULDER_R1.id,
                    label = "Right Shoulder (R1)",
                    shortCode = "R1",
                    normalizedX = 0.88f,
                    normalizedY = 0.04f,
                    isEdge = true,
                    isDefault = true,
                    actionConfig = ActionConfig(
                        actionType = SystemActionType.VIRTUAL_TOUCH_TAP,
                        touchCoordinateX = 1800f,
                        touchCoordinateY = 600f
                    )
                ),
                KineticButton(
                    id = ChassisZone.FRAME_MID_LEFT.id,
                    label = "Mid-Left Frame",
                    shortCode = "ML",
                    normalizedX = 0.03f,
                    normalizedY = 0.45f,
                    isEdge = true,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.VOLUME_DOWN)
                ),
                KineticButton(
                    id = ChassisZone.FRAME_MID_RIGHT.id,
                    label = "Mid-Right Frame",
                    shortCode = "MR",
                    normalizedX = 0.97f,
                    normalizedY = 0.45f,
                    isEdge = true,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.VOLUME_UP)
                ),
                KineticButton(
                    id = ChassisZone.FRAME_BOTTOM_LEFT.id,
                    label = "Bottom-Left Corner",
                    shortCode = "BL",
                    normalizedX = 0.12f,
                    normalizedY = 0.96f,
                    isEdge = true,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.NAV_BACK)
                ),
                KineticButton(
                    id = ChassisZone.FRAME_BOTTOM_RIGHT.id,
                    label = "Bottom-Right Corner",
                    shortCode = "BR",
                    normalizedX = 0.88f,
                    normalizedY = 0.96f,
                    isEdge = true,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.NAV_RECENTS)
                ),
                KineticButton(
                    id = ChassisZone.BACK_TOP.id,
                    label = "Back Top / Camera Island",
                    shortCode = "BT",
                    normalizedX = 0.50f,
                    normalizedY = 0.18f,
                    isEdge = false,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.TORCH_TOGGLE)
                ),
                KineticButton(
                    id = ChassisZone.BACK_CENTER.id,
                    label = "Back Center",
                    shortCode = "BC",
                    normalizedX = 0.50f,
                    normalizedY = 0.50f,
                    isEdge = false,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.MEDIA_PLAY_PAUSE)
                ),
                KineticButton(
                    id = ChassisZone.BACK_BOTTOM.id,
                    label = "Back Lower",
                    shortCode = "BB",
                    normalizedX = 0.50f,
                    normalizedY = 0.82f,
                    isEdge = false,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.MEDIA_NEXT)
                ),
                KineticButton(
                    id = ChassisZone.SQUEEZE_FRAME.id,
                    label = "Bilateral Squeeze",
                    shortCode = "SQZ",
                    normalizedX = 0.50f,
                    normalizedY = 0.50f,
                    isEdge = true,
                    isDefault = true,
                    actionConfig = ActionConfig(actionType = SystemActionType.SCREENSHOT)
                )
            )
        }
    }
}
