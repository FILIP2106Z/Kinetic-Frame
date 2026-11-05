package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.model.FormFactorType
import com.example.model.KineticButton
import com.example.model.PresetDefaults
import com.example.model.SystemActionType
import com.example.service.KineticSensorService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    var currentTab by remember { mutableIntStateOf(0) } // 0: Visualizer, 1: Buttons, 2: Telemetry & Engine
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
                        Surface(
                            shape = CircleShape,
                            color = if (isMonitoring) Color(0xFF00E676) else Color(0xFFFF5252),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "KineticFrame",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                            Text(
                                text = "${phoneProfile.marketingName} • Auto-Calibrated",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    // Material You Privilege Status Pill
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .padding(end = 8.dp)
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
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = privilegeStatus.activeTier.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Smartphone, contentDescription = null) },
                    label = { Text("Visualizer") },
                    modifier = Modifier.testTag("nav_tab_visualizer")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                    label = { Text("Buttons (${kineticButtons.size})") },
                    modifier = Modifier.testTag("nav_tab_buttons")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text("Engine") },
                    modifier = Modifier.testTag("nav_tab_telemetry")
                )
            }
        },
        floatingActionButton = {
            if (currentTab != 2) {
                ExtendedFloatingActionButton(
                    onClick = { isAddMode = !isAddMode },
                    icon = {
                        Icon(
                            imageVector = if (isAddMode) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(if (isAddMode) "Finish Placing" else "Add Trigger")
                    },
                    containerColor = if (isAddMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isAddMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("add_kinetic_button_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> VisualizerTabContent(
                    service = service,
                    buttons = kineticButtons,
                    formFactor = formFactor,
                    isAddMode = isAddMode,
                    activePreset = activePreset,
                    recentEvents = recentEvents,
                    onToggleAddMode = { isAddMode = it },
                    onSelectButton = { selectedButtonForEdit = it }
                )
                1 -> ButtonsTabContent(
                    buttons = kineticButtons,
                    onEditButton = { selectedButtonForEdit = it },
                    onTestButton = { service.simulateButtonTap(it) },
                    onToggleEnable = { btn, enabled ->
                        service.updateButton(btn.copy(isEnabled = enabled))
                    },
                    onAddClicked = {
                        isAddMode = true
                        currentTab = 0
                    }
                )
                2 -> EngineTelemetryTabContent(
                    service = service,
                    sensitivity = sensitivity,
                    privilegeStatus = privilegeStatus,
                    onOpenPrivilegeDialog = { showPrivilegeDialog = true }
                )
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

/**
 * Tab 0: Interactive Phone Vector Graphic Visualizer
 */
@Composable
fun VisualizerTabContent(
    service: KineticSensorService,
    buttons: List<KineticButton>,
    formFactor: com.example.model.FormFactorState,
    isAddMode: Boolean,
    activePreset: com.example.model.PresetProfile,
    recentEvents: List<ChassisTriggerEvent>,
    onToggleAddMode: (Boolean) -> Unit,
    onSelectButton: (KineticButton) -> Unit
) {
    val context = LocalContext.current
    val phoneProfile = remember { DeviceHardwareProfiler.detectProfile(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("visualizer_tab_scroll"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Automatic Hardware Architecture Badge (No manual selection needed)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = phoneProfile.marketingName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (formFactor.type) {
                                    FormFactorType.FOLDABLE_UNFOLDED -> "Foldable (Main Display Active • 180°)"
                                    FormFactorType.FLIP_HALF_OPEN -> "Flip Clamshell (Flex Mode • 90°)"
                                    FormFactorType.FLIP_OPEN -> "Flip Clamshell (Open)"
                                    FormFactorType.FOLDABLE_FOLDED -> "Foldable (Cover Display)"
                                    FormFactorType.CANDYBAR -> "Automatic Architecture: ${phoneProfile.designStyle.name.replace("_", " ")}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "AUTO DETECTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // Add Mode Interactive Banner
        if (isAddMode) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "TAP ANYWHERE ON THE WIREFRAME",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Drop a new kinetic trigger button onto any edge, corner, camera visor, or back panel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Hero Precision Phone Vector Wireframe Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .testTag("chassis_visualizer_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE STRUCTURAL VECTOR",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${buttons.size} kinetic nodes active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }

                    InteractiveChassisVisualizer(
                        buttons = buttons,
                        formFactor = formFactor,
                        triggerFlow = service.triggerEventFlow,
                        selectedButtonId = null,
                        isAddMode = isAddMode,
                        onWireframeTapped = { normX, normY ->
                            if (isAddMode) {
                                val newBtn = service.addCustomKineticButton(
                                    label = "Trigger ${buttons.size + 1}",
                                    shortCode = "K${buttons.size + 1}",
                                    normalizedX = normX,
                                    normalizedY = normY,
                                    isEdge = normX < 0.12f || normX > 0.88f || normY < 0.1f || normY > 0.9f,
                                    actionConfig = ActionConfig(actionType = SystemActionType.VIRTUAL_TOUCH_TAP)
                                )
                                onToggleAddMode(false)
                                onSelectButton(newBtn)
                            }
                        },
                        onButtonTapped = { btn ->
                            onSelectButton(btn)
                            service.simulateButtonTap(btn)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Preset Profiles Row
        item {
            Column {
                Text(
                    text = "QUICK PRESET PROFILES",
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
                            label = {
                                Text(
                                    preset.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        // Recent Strikes Feed
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
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "No strikes detected yet. Firmly strike the phone frame, corners, or camera visor to trigger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(recentEvents.take(5)) { event ->
                TriggerEventRow(event = event)
            }
        }
    }
}

/**
 * Tab 1: Configured Kinetic Buttons List with Material You Cards
 */
@Composable
fun ButtonsTabContent(
    buttons: List<KineticButton>,
    onEditButton: (KineticButton) -> Unit,
    onTestButton: (KineticButton) -> Unit,
    onToggleEnable: (KineticButton, Boolean) -> Unit,
    onAddClicked: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("buttons_tab_scroll"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "KINETIC BUTTON ROSTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${buttons.count { it.isEnabled }} enabled of ${buttons.size} total buttons",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = onAddClicked,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Button", fontSize = 12.sp)
                }
            }
        }

        items(buttons) { button ->
            MaterialYouButtonCard(
                button = button,
                onEdit = { onEditButton(button) },
                onTest = { onTestButton(button) },
                onToggleEnable = { onToggleEnable(button, it) }
            )
        }
    }
}

@Composable
fun MaterialYouButtonCard(
    button: KineticButton,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onToggleEnable: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onEdit() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = if (button.isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = button.shortCode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (button.isDefault) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = button.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (button.calibratedTorqueX != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Calibrated",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "${button.actionConfig.actionType.displayName} • Location: (${(button.normalizedX * 100).toInt()}%, ${(button.normalizedY * 100).toInt()}%)",
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
                Switch(
                    checked = button.isEnabled,
                    onCheckedChange = onToggleEnable,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/**
 * Tab 2: IMU Telemetry, Waveform Scope & Acoustic Engine Tuning
 */
@Composable
fun EngineTelemetryTabContent(
    service: KineticSensorService,
    sensitivity: Float,
    privilegeStatus: PrivilegeStatus,
    onOpenPrivilegeDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("telemetry_tab_scroll"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High-Speed Oscilloscope Card
        item {
            TelemetryScope(
                telemetryFlow = service.latestTelemetry,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Sensitivity & Dynamic Auto-Calibration Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sensitivity_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
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
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ACOUSTIC SENSITIVITY",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${(sensitivity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = sensitivity,
                        onValueChange = { service.setSensitivity(it) },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.testTag("sensitivity_slider")
                    )

                    Text(
                        text = "Dynamic zero-user calibration continuously adapts the impulse noise floor to table bumps, typing shocks, and walking motion, maintaining 95%+ precision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Deep System Privileges & Touch Injection Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "SYSTEM PRIVILEGES",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenPrivilegeDialog,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Configure", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Active Tier: ${privilegeStatus.activeTier.label}. Root and Shizuku enable sub-millisecond virtual touch tap injection without reaching for the display.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TriggerEventRow(event: ChassisTriggerEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val formattedTime = remember(event.timestampMs) { timeFormat.format(Date(event.timestampMs)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("trigger_event_item_${event.buttonId}"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = event.buttonId.take(3).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
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
                            .testTag("enable_accessibility_button"),
                        shape = RoundedCornerShape(12.dp)
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
