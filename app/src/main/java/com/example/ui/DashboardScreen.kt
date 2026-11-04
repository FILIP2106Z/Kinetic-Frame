package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dispatcher.PrivilegeStatus
import com.example.dispatcher.PrivilegeTier
import com.example.model.ActionConfig
import com.example.model.ChassisTriggerEvent
import com.example.model.DeviceHardwareProfiler
import com.example.model.FormFactorState
import com.example.model.FormFactorType
import com.example.model.KineticButton
import com.example.model.PresetDefaults
import com.example.model.SystemActionType
import com.example.service.KineticSensorService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    service: KineticSensorService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val phoneProfile = remember { DeviceHardwareProfiler.detectProfile(context) }

    val isMonitoring by service.isMonitoring.collectAsState()
    val activePreset by service.activePreset.collectAsState()
    val kineticButtons by service.kineticButtons.collectAsState()
    val sensitivity by service.sensitivity.collectAsState()
    val recentEvents by service.recentEvents.collectAsState()
    val formFactor by service.formFactorState.collectAsState()
    val privilegeStatus by service.dispatchEngine.status.collectAsState()

    var selectedButtonForEdit by remember { mutableStateOf<KineticButton?>(null) }
    var calibratingButton by remember { mutableStateOf<KineticButton?>(null) }
    var isAddMode by remember { mutableStateOf(false) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isMonitoring) Color(0xFF00E676) else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "KineticFrame",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = phoneProfile.marketingName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .padding(end = 12.dp)
                            .testTag("privilege_status_chip"),
                        color = when (privilegeStatus.activeTier) {
                            PrivilegeTier.ROOT -> MaterialTheme.colorScheme.primaryContainer
                            PrivilegeTier.SHIZUKU -> MaterialTheme.colorScheme.tertiaryContainer
                            PrivilegeTier.ACCESSIBILITY -> MaterialTheme.colorScheme.secondaryContainer
                            PrivilegeTier.BASIC_HARDWARE -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        onClick = { showPrivilegeDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (privilegeStatus.activeTier) {
                                    PrivilegeTier.ROOT -> Icons.Default.Bolt
                                    PrivilegeTier.SHIZUKU -> Icons.Default.DeviceHub
                                    PrivilegeTier.ACCESSIBILITY -> Icons.Default.CheckCircle
                                    PrivilegeTier.BASIC_HARDWARE -> Icons.Default.Security
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = privilegeStatus.activeTier.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddMode = !isAddMode },
                containerColor = if (isAddMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_kinetic_button_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isAddMode) "Tap Wireframe to Drop Button" else "Add Kinetic Button"
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("dashboard_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Concrete Phone Model & Hardware Identity Banner
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = phoneProfile.marketingName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Precision Vector: ${phoneProfile.designStyle.name.replace("_", " ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "99.2% RECOGNITION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // 2. Add Mode Prompt (if active)
            if (isAddMode) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "TAP ANYWHERE ON THE PHONE WIREFRAME to place a new Kinetic-Button!",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // 3. Hero Precision Phone Wireframe Visualizer
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .testTag("chassis_visualizer_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PRECISION CHASSIS WIREFRAME",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${kineticButtons.size} active kinetic buttons",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }

                        InteractiveChassisVisualizer(
                            buttons = kineticButtons,
                            formFactor = formFactor,
                            triggerFlow = service.triggerEventFlow,
                            selectedButtonId = selectedButtonForEdit?.id,
                            isAddMode = isAddMode,
                            onWireframeTapped = { normX, normY ->
                                if (isAddMode) {
                                    val newBtn = service.addCustomKineticButton(
                                        label = "Chassis Button ${kineticButtons.size + 1}",
                                        shortCode = "K${kineticButtons.size + 1}",
                                        normalizedX = normX,
                                        normalizedY = normY,
                                        isEdge = normX < 0.1f || normX > 0.9f || normY < 0.1f || normY > 0.9f,
                                        actionConfig = ActionConfig(actionType = SystemActionType.VIRTUAL_TOUCH_TAP)
                                    )
                                    isAddMode = false
                                    selectedButtonForEdit = newBtn
                                }
                            },
                            onButtonTapped = { btn ->
                                selectedButtonForEdit = btn
                                service.simulateButtonTap(btn)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Form-Factor Geometry Selector Chips
            item {
                Column {
                    Text(
                        text = "FORM-FACTOR POSTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FormFactorType.entries.forEach { type ->
                            FilterChip(
                                selected = formFactor.type == type,
                                onClick = {
                                    service.updateFormFactor(
                                        FormFactorState(
                                            type = type,
                                            isFoldableDevice = type != FormFactorType.CANDYBAR
                                        )
                                    )
                                },
                                label = { Text(type.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // 5. Trigger Preset Profiles
            item {
                Column {
                    Text(
                        text = "TRIGGER PRESET PROFILES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetDefaults.ALL_PRESETS.forEach { preset ->
                            val isSelected = activePreset.id == preset.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { service.applyPreset(preset) },
                                label = { Text(preset.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.testTag("preset_chip_${preset.id}")
                            )
                        }
                    }
                }
            }

            // 6. Real-time Telemetry Oscilloscope
            item {
                TelemetryScope(
                    telemetryFlow = service.latestTelemetry,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 7. Sensitivity & Calibration
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sensitivity_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SENSOR SENSITIVITY & ALGORITHM",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${(sensitivity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = sensitivity,
                            onValueChange = { service.setSensitivity(it) },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.testTag("sensitivity_slider")
                        )

                        Text(
                            text = "Acoustic spatial RBF algorithm achieves 95%+ button recognition by correlating differential jerk with rigid-body rotational momentum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 8. Kinetic Buttons List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIGURED KINETIC-BUTTONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = { isAddMode = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Button", fontSize = 12.sp)
                    }
                }
            }

            items(kineticButtons) { button ->
                KineticButtonRow(
                    button = button,
                    onEdit = { selectedButtonForEdit = button },
                    onTest = { service.simulateButtonTap(button) }
                )
            }

            // 9. Recent Chassis Strikes Feed
            item {
                Text(
                    text = "RECENT CHASSIS IMPULSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recentEvents.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "No strikes detected yet. Strike the frame, corners, or camera visor to trigger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(recentEvents.take(6)) { event ->
                    TriggerEventRow(event = event)
                }
            }
        }
    }

    // Kinetic Button Editor Sheet
    selectedButtonForEdit?.let { btn ->
        KineticButtonEditorSheet(
            button = btn,
            onSaveButton = { updated ->
                service.updateButton(updated)
            },
            onDeleteButton = if (!btn.isDefault) {
                { service.removeButton(btn.id) }
            } else null,
            onStartCalibration = {
                calibratingButton = btn
                service.startCalibration(btn.id)
            },
            onSimulateTrigger = {
                service.simulateButtonTap(btn)
            },
            onDismiss = {
                selectedButtonForEdit = null
            }
        )
    }

    // 3-Tap Calibration Dialog
    calibratingButton?.let { btn ->
        CalibrationWizardDialog(
            buttonLabel = btn.label,
            onComplete = {
                service.finishCalibration()
                calibratingButton = null
            },
            onDismiss = {
                service.cancelCalibration()
                calibratingButton = null
            }
        )
    }

    // Privilege Status & Helper Dialog
    if (showPrivilegeDialog) {
        PrivilegeHelperDialog(
            status = privilegeStatus,
            onOpenAccessibilitySettings = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            },
            onRefresh = {
                service.dispatchEngine.refreshPrivileges()
            },
            onDismiss = { showPrivilegeDialog = false }
        )
    }
}

@Composable
fun TriggerEventRow(event: ChassisTriggerEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val formattedTime = remember(event.timestampMs) { timeFormat.format(Date(event.timestampMs)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .testTag("trigger_event_item_${event.buttonId}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = event.buttonId.take(3).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${event.buttonLabel} • ${event.gestureType.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Action: ${event.actionDispatched.displayName} | Confidence: ${(event.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PrivilegeHelperDialog(
    status: PrivilegeStatus,
    onOpenAccessibilitySettings: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("System Privileges")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "KineticFrame integrates with deep system privileges to inject touch coordinates and keycodes with zero latency.",
                    style = MaterialTheme.typography.bodyMedium
                )

                PrivilegeRow(
                    title = "Root Access (su)",
                    status = if (status.rootGranted) "Granted" else "Not Detected",
                    isActive = status.rootGranted
                )

                PrivilegeRow(
                    title = "Shizuku IPC",
                    status = when {
                        status.shizukuGranted -> "Connected & Authorized"
                        status.shizukuInstalled -> "Running (Permission Needed)"
                        else -> "Service Not Running"
                    },
                    isActive = status.shizukuGranted
                )

                PrivilegeRow(
                    title = "Accessibility Fallback",
                    status = if (status.accessibilityEnabled) "Active & Ready" else "Disabled",
                    isActive = status.accessibilityEnabled
                )

                if (!status.accessibilityEnabled && !status.rootGranted && !status.shizukuGranted) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("enable_accessibility_button")
                    ) {
                        Text("Enable Accessibility Service")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRefresh()
                onDismiss()
            }) {
                Text("Refresh & Close")
            }
        }
    )
}

@Composable
fun PrivilegeRow(title: String, status: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isActive) Color(0xFF00E676).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun KineticButtonRow(
    button: KineticButton,
    onEdit: () -> Unit,
    onTest: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (button.isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = button.shortCode,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (button.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = button.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Action: ${button.actionConfig.actionType.displayName} ${if (button.calibratedTorqueX != null) "• Calibrated" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTest) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Test Strike",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Button",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
