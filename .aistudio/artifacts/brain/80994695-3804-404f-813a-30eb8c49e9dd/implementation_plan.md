# KineticFrame — Universal Chassis Multi-Touch & Vibration Trigger System

Transform any Android device's physical metal/glass chassis into a programmable multi-touch and vibration trigger surface using high-frequency IMU sensor fusion, structural shockwave pattern recognition, real-time wireframe telemetry, and privileged action injection (Root, Shizuku, and Accessibility).

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were aligned during Phase 1 interactive clarification:
> - **Application Identity**: Named **KineticFrame** with package identity and platform syncing.
> - **Trigger Execution Strategy**: **Smart Hybrid dispatch engine** — dynamically prioritizing Root (`su`) and Shizuku IPC for sub-millisecond virtual touch/key injection (`/dev/input`, `input keyevent`, `input tap`), with automatic graceful fallback to an Android Accessibility Service for global navigation (Back, Home, Recents, Flashlight, Screenshot, Custom Gestures).
> - **Visual Experience**: **Material You dynamic theming** with precision wireframe telemetry, reactive impulse propagation shaders/canvases, and real-time frequency-domain signal scopes.

---

## 1. Overview & Core Concept

Every physical tap, squeeze, or finger flick on a modern phone's chassis creates distinct micro-mechanical shockwaves and rotational micro-torques that propagate through the frame into the internal Inertial Measurement Unit (IMU). By sampling the accelerometer and gyroscope at high speed (~200–400Hz) and applying real-time signal processing (high-pass differential filtering, peak-to-energy ratio, zero-crossing rate, and cross-axis resonance correlation), **KineticFrame** localizes impulses into distinct physical chassis zones:
- **Shoulder Triggers (L1 / R1)**: Top-left and top-right frame taps (ideal for mobile gaming trigger buttons).
- **Side Squeeze & Bilateral Grips**: Dynamic micro-flexing of the frame when gripping tightly.
- **Back Tap Zones**: Upper (camera bump area), center, and lower back taps with single and double-tap discrimination.
- **Chassis Chords & Combos**: Simultaneous multi-zone taps (e.g., L1 + R1 dual shoulder tap, diagonal cross taps).

```
┌────────────────────────────────────────────────────────────────────────┐
│                        KINETICFRAME ARCHITECTURE                       │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  [Physical Phone Chassis]                                              │
│     │ (Tap / Flick / Squeeze Shockwaves)                               │
│     ▼                                                                  │
│  [High-Speed IMU Sensor Pipeline] (Sensor.TYPE_ACCEL + GYRO @ Fastest) │
│     │                                                                  │
│     ▼                                                                  │
│  [Signal Processing & Feature Extraction]                              │
│     ├── Zero-Crossing Rate & RMS Impulse Energy                        │
│     ├── High-Pass Differential Filter & Peak Envelope Detection        │
│     └── Dynamic Ambient Noise Baseline Estimator (Adaptive Threshold)  │
│     │                                                                  │
│     ▼                                                                  │
│  [Chassis Zone & Combo Classifier]                                     │
│     ├── Zone Mapping (Shoulder L1/R1, Side Grips, Back Top/Mid/Bot)    │
│     ├── Combo State Machine (Chords, Double-Taps, Sequential Inputs)   │
│     └── Foldable / Posture Adapter (WindowManager hinge angle/state)   │
│     │                                                                  │
│     ▼                                                                  │
│  [Smart Hybrid Action Dispatcher]                                      │
│     ├── Tier 1: Root Shell (`su` input tap / keyevent / sendevent)     │
│     ├── Tier 2: Shizuku IPC API (Remote process command execution)     │
│     └── Tier 3: Android Accessibility Service (Global system actions)  │
│     │                                                                  │
│     ▼                                                                  │
│  [Jetpack Compose Dashboard & Real-Time Wireframe Canvas]              │
│     ├── Dynamic Phone Model Wireframe (Adaptive Candybar / Fold / Flip)│
│     ├── Shockwave Pulse & Impulse Glow Visualizer                      │
│     ├── Live Telemetry Oscilloscope (Acc/Gyro differential curves)     │
│     └── Gesture-to-Action Mapping Studio & Calibration Console         │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Zero-Configuration Instant Activation**:
   - On first launch, the service immediately initializes ambient noise baseline learning (adapting within 1.5 seconds) and displays the interactive wireframe of the detected device form-factor.
   - Tapping anywhere on the device frame produces instant visual pulse ripples and localized glows on the wireframe visualizer with tactile haptic confirmation.
2. **Privilege Auto-Detection Banner**:
   - Dynamic status chips show system capabilities: Shizuku status (Connected / Not Running), Root status (Available / Not Granted), and Accessibility Service status.
   - One-tap guided dialogs allow users to connect Shizuku or enable Accessibility if privileged touch injection is desired.
3. **Chassis Trigger Customization & Presets**:
   - Pre-configured game presets (e.g., *FPS Triggers*: L1 = Aim, R1 = Shoot; *Media Control*: Back Double-Tap = Play/Pause, Squeeze = Mute; *Productivity*: Top Squeeze = Flashlight, Back Tap = Screenshot).
   - Zone-to-Action Studio where users can tap any physical zone to select and map custom actions.
4. **Live Telemetry & Frequency Scope**:
   - Real-time animated waveforms displaying linear acceleration magnitude, angular velocity impulses, and detected trigger threshold crossings.

### Visual Identity & Theme
- **Design Language**: Material 3 (Material You) with `dynamicDarkColorScheme` / `dynamicLightColorScheme` support, honoring device wallpaper accents while maintaining high-tech telemetry aesthetics.
- **Wireframe Styling**: Precision-crafted structural chassis vector with corner rounding calculated from the actual display metrics, volume rocker/power button cutouts, camera housing island, and dynamic split/hinge lines when a foldable device is detected.
- **Motion & Feedback**:
  - Haptic feedback with subtle vibration patterns (`VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)`).
  - Spring-animated glow halos expanding outward from the struck zone coordinates on the wireframe canvas.
- **Responsive Layout**:
  - Mobile compact: Single scrolling dashboard with sticky wireframe hero pane and collapsible telemetry cards.
  - Foldables / Tablets: Side-by-side list-detail layout with full-height 3D-angled wireframe visualizer on the left pane and trigger mapping / scopes on the right pane.

---

## 3. Key Product Decisions & Trade-Offs

- **Hybrid Privilege Fallback (Root -> Shizuku -> Accessibility)**:
  - *Why*: Allows hardcore mobile gamers to achieve zero-latency direct screen coordinate tap injection (`input tap X Y`) without needing an external controller, while ensuring casual users without root or Shizuku can still fully utilize chassis taps for navigation, app launching, flashlight, and media controls via Accessibility Service.
- **Algorithmic Signal Processing Over Heavy On-Device Deep Learning**:
  - *Why*: Heavy neural networks running at 400Hz drain battery rapidly and introduce 10–30ms inference latency. Using physics-based mechanical wave propagation equations, high-pass derivation, and zero-crossing analysis runs in <0.5ms on a background thread with negligible (<1%) battery footprint.
- **Dynamic Adaptive Ambient Baseline**:
  - *Why*: Prevents annoying false triggers when the user is walking, jogging, driving on bumpy roads, or typing vigorously on a table by calculating a rolling variance envelope and raising the trigger threshold dynamically during ambient turbulence.
- **Jetpack WindowManager Posture Awareness**:
  - *Why*: Detects folding state (flat, half-opened/tabletop, folded) to switch wireframe view and automatically remap triggers (e.g., disable inner shoulder triggers when the device is closed).

---

## 4. Technical Architecture & Implementation Steps

### A. Dependencies & Build Configuration
- `androidx.window:window:1.4.0` for foldable/posture detection.
- `moe.shizuku.privilege:api:13.1.5` and `moe.shizuku.privilege:provider:13.1.5` for Shizuku IPC binder.
- Compose Material 3, Material Icons Extended, Lifecycle ViewModel Compose, and Room / Preferences for storing trigger maps.

### B. Core Components
1. **`ChassisSensorEngine` (Foreground Service)**:
   - Registers `Sensor.TYPE_ACCELEROMETER`, `Sensor.TYPE_GYROSCOPE`, and `Sensor.TYPE_LINEAR_ACCELERATION` at `SENSOR_DELAY_FASTEST`.
   - Acquires `PARTIAL_WAKE_LOCK` with intelligent duty-cycling when the screen is off.
   - Emits detected triggers via Kotlin `SharedFlow<ChassisTriggerEvent>`.
2. **`StructuralWaveClassifier`**:
   - Real-time mathematical analysis of 3-axis acceleration and gyro impulses.
   - Calculates differential jerk ($\Delta a / \Delta t$) and angular impulse torque ($\Delta \omega / \Delta t$).
   - Determines zone location based on torque-moment ratios:
     - Top taps produce positive pitching torque ($-\omega_x$) with forward vertical jerk.
     - Bottom taps produce negative pitching torque ($+\omega_x$).
     - Left-frame taps produce strong positive roll torque ($+\omega_y$).
     - Right-frame taps produce negative roll torque ($-\omega_y$).
     - Back taps generate high z-axis impulse with minimal roll/pitch torque.
     - Dual taps (chords) exhibit destructive cross-torques with doubled z-axis impulse.
3. **`ActionDispatchEngine`**:
   - Evaluates device capability:
     - Root executor (`Runtime.getRuntime().exec("su")`).
     - Shizuku binder executor (`Shizuku.newProcess(...)`).
     - Accessibility Service fallback (`KineticAccessibilityService`).
     - System hardware direct (Torch, Audio, Vibrator).
4. **`InteractiveChassisVisualizer` (Compose Canvas)**:
   - Dynamically scales and draws the chassis silhouette, bezel insets, antenna gaps, speaker grilles, and camera module.
   - Overlays interactive pulse rings and heat spots for each chassis trigger zone.
   - Interactive testing mode allowing users to tap both on-screen and physically to see instant visual confirmation.
5. **`KineticAccessibilityService`**:
   - Handles global intents (`GLOBAL_ACTION_BACK`, `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_RECENTS`, `GLOBAL_ACTION_NOTIFICATIONS`, `GLOBAL_ACTION_TAKE_SCREENSHOT`).
   - Dispatches custom simulated click gestures on screen if Shizuku/Root are not configured.

---

## 5. Verification & Testing Strategy
- Compile and verify cleanly with `./gradlew compileDebugSources`.
- Validate zero-crash sensor registration on standard devices and emulators.
- Verify smooth 60fps/120fps wireframe rendering during rapid simulated chassis impulse streams.
- Test Accessibility Service binding lifecycle and Shizuku permission detection.
