package com.example.dispatcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import com.example.model.ActionConfig
import com.example.model.SystemActionType
import com.example.service.KineticAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

enum class PrivilegeTier(val label: String, val description: String) {
    ROOT("Root (su)", "Direct kernel /dev/input and shell injection (Zero latency)"),
    SHIZUKU("Shizuku (IPC)", "Direct system privilege shell injection"),
    ACCESSIBILITY("Accessibility Service", "Global system actions & touch gestures"),
    BASIC_HARDWARE("Standard OS", "Haptics, flashlight & media keys")
}

data class PrivilegeStatus(
    val rootAvailable: Boolean = false,
    val rootGranted: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val activeTier: PrivilegeTier = PrivilegeTier.BASIC_HARDWARE
)

/**
 * Universal privileged action dispatcher.
 * Automatically chooses the highest available execution mechanism:
 * Root -> Shizuku -> Accessibility -> Direct Hardware
 */
class ActionDispatchEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status = _status.asStateFlow()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val cameraManager: CameraManager? by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private var torchEnabled = false
    private var cachedRootProcess: Process? = null
    private var cachedRootOutputStream: OutputStream? = null
    private val isCheckingPrivileges = AtomicBoolean(false)

    init {
        refreshPrivileges()
        setupShizukuListeners()
    }

    private fun setupShizukuListeners() {
        try {
            Shizuku.addBinderReceivedListener {
                refreshPrivileges()
            }
            Shizuku.addBinderDeadListener {
                refreshPrivileges()
            }
        } catch (_: Throwable) {
            // Shizuku provider not yet available or not running
        }
    }

    fun refreshPrivileges() {
        if (isCheckingPrivileges.getAndSet(true)) return

        scope.launch(Dispatchers.IO) {
            try {
                // Check Root
                val rootGranted = checkRootAccess()

                // Check Shizuku
                val shizukuInstalled = try {
                    Shizuku.pingBinder()
                } catch (_: Throwable) {
                    false
                }

                val shizukuGranted = shizukuInstalled && try {
                    if (Shizuku.isPreV11()) {
                        false
                    } else {
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    }
                } catch (_: Throwable) {
                    false
                }

                // Check Accessibility
                val accessibilityActive = KineticAccessibilityService.instance != null

                val tier = when {
                    rootGranted -> PrivilegeTier.ROOT
                    shizukuGranted -> PrivilegeTier.SHIZUKU
                    accessibilityActive -> PrivilegeTier.ACCESSIBILITY
                    else -> PrivilegeTier.BASIC_HARDWARE
                }

                _status.value = PrivilegeStatus(
                    rootAvailable = rootGranted,
                    rootGranted = rootGranted,
                    shizukuInstalled = shizukuInstalled,
                    shizukuGranted = shizukuGranted,
                    accessibilityEnabled = accessibilityActive,
                    activeTier = tier
                )
            } finally {
                isCheckingPrivileges.set(false)
            }
        }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Executes the mapped action using the highest available privilege layer.
     */
    fun dispatch(config: ActionConfig) {
        if (config.hapticFeedback) {
            triggerHapticClick()
        }

        when (config.actionType) {
            SystemActionType.NONE -> { /* no-op */ }

            SystemActionType.VIRTUAL_TOUCH_TAP -> {
                injectTouchTap(config.touchCoordinateX, config.touchCoordinateY)
            }

            SystemActionType.VIRTUAL_KEYCODE -> {
                injectKeycode(config.customKeycode)
            }

            SystemActionType.NAV_BACK -> {
                dispatchNavAction(
                    globalAction = AccessibilityService.GLOBAL_ACTION_BACK,
                    rootKeycode = "KEYCODE_BACK"
                )
            }

            SystemActionType.NAV_HOME -> {
                dispatchNavAction(
                    globalAction = AccessibilityService.GLOBAL_ACTION_HOME,
                    rootKeycode = "KEYCODE_HOME"
                )
            }

            SystemActionType.NAV_RECENTS -> {
                dispatchNavAction(
                    globalAction = AccessibilityService.GLOBAL_ACTION_RECENTS,
                    rootKeycode = "KEYCODE_APP_SWITCH"
                )
            }

            SystemActionType.NAV_NOTIFICATIONS -> {
                dispatchNavAction(
                    globalAction = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                    rootKeycode = "28" // KEYCODE_NOTIFICATION or expand notifications
                )
            }

            SystemActionType.NAV_QUICK_SETTINGS -> {
                dispatchNavAction(
                    globalAction = AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
                    rootKeycode = "KEYCODE_SETTINGS"
                )
            }

            SystemActionType.SCREENSHOT -> {
                dispatchNavAction(
                    globalAction = AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
                    rootCommand = "screencap -p /sdcard/screenshot.png"
                )
            }

            SystemActionType.LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dispatchNavAction(
                        globalAction = AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
                        rootKeycode = "KEYCODE_POWER"
                    )
                } else {
                    executeShellCommand("input keyevent KEYCODE_POWER")
                }
            }

            SystemActionType.TORCH_TOGGLE -> {
                toggleFlashlight()
            }

            SystemActionType.MEDIA_PLAY_PAUSE -> {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }

            SystemActionType.MEDIA_NEXT -> {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }

            SystemActionType.VOLUME_UP -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }

            SystemActionType.VOLUME_DOWN -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    private fun injectTouchTap(x: Float, y: Float) {
        val currentTier = _status.value.activeTier
        when (currentTier) {
            PrivilegeTier.ROOT, PrivilegeTier.SHIZUKU -> {
                val command = "input tap ${x.toInt()} ${y.toInt()}"
                executeShellCommand(command)
            }
            PrivilegeTier.ACCESSIBILITY -> {
                KineticAccessibilityService.simulateClick(x, y)
            }
            PrivilegeTier.BASIC_HARDWARE -> {
                // Cannot inject touch without privileges
            }
        }
    }

    private fun injectKeycode(keycode: Int) {
        val cmd = "input keyevent $keycode"
        executeShellCommand(cmd)
    }

    private fun dispatchNavAction(
        globalAction: Int,
        rootKeycode: String? = null,
        rootCommand: String? = null
    ) {
        val currentTier = _status.value.activeTier
        if (currentTier == PrivilegeTier.ROOT || currentTier == PrivilegeTier.SHIZUKU) {
            val cmd = rootCommand ?: "input keyevent $rootKeycode"
            executeShellCommand(cmd)
        } else {
            // Use accessibility global action
            val success = KineticAccessibilityService.performGlobal(globalAction)
            if (!success && rootKeycode != null) {
                // Try shell execution as last resort
                executeShellCommand("input keyevent $rootKeycode")
            }
        }
    }

    private fun executeShellCommand(command: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val tier = _status.value.activeTier
                if (tier == PrivilegeTier.ROOT) {
                    executeViaRoot(command)
                } else if (tier == PrivilegeTier.SHIZUKU) {
                    executeViaShizuku(command)
                } else {
                    // Try direct runtime
                    Runtime.getRuntime().exec(command)
                }
            } catch (_: Throwable) {
                // Command failed or unauthorized
            }
        }
    }

    private fun executeViaRoot(command: String) {
        try {
            if (cachedRootProcess == null || cachedRootOutputStream == null) {
                cachedRootProcess = Runtime.getRuntime().exec("su")
                cachedRootOutputStream = cachedRootProcess?.outputStream
            }
            cachedRootOutputStream?.write("$command\n".toByteArray())
            cachedRootOutputStream?.flush()
        } catch (_: Throwable) {
            cachedRootProcess = null
            cachedRootOutputStream = null
        }
    }

    private fun executeViaShizuku(command: String) {
        try {
            val cmdArray = arrayOf("sh", "-c", command)
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, cmdArray, null, null) as? Process
            proc?.waitFor()
            proc?.destroy()
        } catch (_: Throwable) {
            // Shizuku execution error or permission not granted
        }
    }

    private fun toggleFlashlight() {
        scope.launch(Dispatchers.Default) {
            try {
                val cm = cameraManager ?: return@launch
                val cameraId = cm.cameraIdList.firstOrNull { id ->
                    val chars = cm.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: cm.cameraIdList.firstOrNull() ?: return@launch

                torchEnabled = !torchEnabled
                cm.setTorchMode(cameraId, torchEnabled)
            } catch (_: CameraAccessException) {
                torchEnabled = false
            } catch (_: Throwable) {
                torchEnabled = false
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager?.let { am ->
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            am.dispatchMediaKeyEvent(eventDown)
            am.dispatchMediaKeyEvent(eventUp)
        }
    }

    fun triggerHapticClick() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(20L)
            }
        }
    }
}
