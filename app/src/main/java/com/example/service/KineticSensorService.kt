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
import com.example.model.ChassisTriggerEvent
import com.example.model.ChassisZone
import com.example.model.FormFactorState
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
import kotlin.math.sqrt

/**
 * High-performance foreground service managing continuous IMU sensor sampling (~200-400Hz),
 * real-time chassis strike classification, and privileged trigger dispatch.
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

    private val _activeMappings = MutableStateFlow<Map<ChassisZone, ActionConfig>>(PresetDefaults.GAMER_FPS.mappings)
    val activeMappings = _activeMappings.asStateFlow()

    private val _sensitivity = MutableStateFlow(0.65f)
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

    inner class LocalBinder : Binder() {
        fun getService(): KineticSensorService = this@KineticSensorService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        classifier = StructuralWaveClassifier(baseSensitivity = _sensitivity.value)
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
        val mapping = _activeMappings.value[event.zone]
        val actionToDispatch = mapping?.actionType ?: SystemActionType.NONE

        val finalEvent = event.copy(actionDispatched = actionToDispatch)

        // Dispatch action
        if (mapping != null && actionToDispatch != SystemActionType.NONE) {
            dispatchEngine.dispatch(mapping)
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
    fun simulateChassisTap(zone: ChassisZone) {
        val dummyEvent = ChassisTriggerEvent(
            zone = zone,
            gestureType = com.example.model.ChassisGestureType.SINGLE_TAP,
            confidence = 0.98f,
            impulseEnergy = 28.5f,
            torqueX = if (zone == ChassisZone.SHOULDER_L1 || zone == ChassisZone.SHOULDER_R1) -1.2f else 0.4f,
            torqueY = if (zone == ChassisZone.SHOULDER_L1 || zone == ChassisZone.FRAME_MID_LEFT) 1.5f else -1.5f,
            torqueZ = 0.1f,
            actionDispatched = SystemActionType.NONE,
            timestampMs = System.currentTimeMillis()
        )
        handleTriggerDetected(dummyEvent)
    }

    fun applyPreset(preset: PresetProfile) {
        _activePreset.value = preset
        _activeMappings.value = preset.mappings
    }

    fun setZoneMapping(zone: ChassisZone, config: ActionConfig) {
        val current = _activeMappings.value.toMutableMap()
        current[zone] = config
        _activeMappings.value = current
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
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
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
    }
}
