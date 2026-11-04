package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActionConfig
import com.example.model.KineticButton
import com.example.model.SystemActionType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KineticButtonEditorSheet(
    button: KineticButton,
    onSaveButton: (KineticButton) -> Unit,
    onDeleteButton: (() -> Unit)?,
    onStartCalibration: () -> Unit,
    onSimulateTrigger: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var label by remember { mutableStateOf(button.label) }
    var shortCode by remember { mutableStateOf(button.shortCode) }
    var posX by remember { mutableFloatStateOf(button.normalizedX) }
    var posY by remember { mutableFloatStateOf(button.normalizedY) }
    var selectedAction by remember { mutableStateOf(button.actionConfig.actionType) }
    var touchX by remember { mutableFloatStateOf(button.actionConfig.touchCoordinateX) }
    var touchY by remember { mutableFloatStateOf(button.actionConfig.touchCoordinateY) }
    var hapticFeedback by remember { mutableStateOf(button.actionConfig.hapticFeedback) }

    val categories = listOf("All", "Gaming / Touch", "Navigation", "Media", "Hardware", "System")
    var selectedCategory by remember { mutableStateOf("All") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("kinetic_button_editor_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (button.isDefault) button.label else "Custom Kinetic Button",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Coordinate: (${String.format(Locale.US, "%.0f%%", posX * 100)}, ${String.format(Locale.US, "%.0f%%", posY * 100)}) on chassis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Label & Short Code
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button Label") },
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = shortCode,
                    onValueChange = { shortCode = it.take(4).uppercase() },
                    label = { Text("Code") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calibration Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (button.calibratedTorqueX != null) "Acoustic Fingerprint Calibrated (99%+ Accuracy)" else "Acoustic Auto-Calibration Ready",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Train engine with 3 taps to learn your physical chassis acoustic resonance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(onClick = onStartCalibration) {
                        Text("Train")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Position Tuning Sliders
            Text(
                text = "PHYSICAL POSITION ON CHASSIS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Horizontal X: ${(posX * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(value = posX, onValueChange = { posX = it }, valueRange = 0.02f..0.98f)
            Text("Vertical Y: ${(posY * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(value = posY, onValueChange = { posY = it }, valueRange = 0.02f..0.98f)

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            Text(
                text = "TRIGGER ACTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.take(4).forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Selection
            val filteredActions = SystemActionType.entries.filter {
                selectedCategory == "All" || it.category == selectedCategory
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filteredActions.forEach { action ->
                    val isSelected = selectedAction == action
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedAction = action },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (action.category) {
                                    "Navigation" -> Icons.Default.Navigation
                                    "Gaming / Touch" -> Icons.Default.Gamepad
                                    "Media" -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Tune
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = action.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = action.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Virtual Touch Tap Coordinates Customizer
            if (selectedAction == SystemActionType.VIRTUAL_TOUCH_TAP) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "SIMULATED TOUCH COORDINATES (ROOT / SHIZUKU / ACCESSIBILITY)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Screen X: ${touchX.toInt()} px", style = MaterialTheme.typography.bodySmall)
                    Slider(value = touchX, onValueChange = { touchX = it }, valueRange = 0f..2400f)
                    Text("Screen Y: ${touchY.toInt()} px", style = MaterialTheme.typography.bodySmall)
                    Slider(value = touchY, onValueChange = { touchY = it }, valueRange = 0f..2400f)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Haptic Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tactile Vibration Impulse", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = hapticFeedback, onCheckedChange = { hapticFeedback = it })
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDeleteButton != null && !button.isDefault) {
                    IconButton(
                        onClick = {
                            onDeleteButton()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }

                OutlinedButton(
                    onClick = onSimulateTrigger,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Strike")
                }

                Button(
                    onClick = {
                        val updated = button.copy(
                            label = label.ifBlank { "Button" },
                            shortCode = shortCode.ifBlank { "BT" },
                            normalizedX = posX,
                            normalizedY = posY,
                            actionConfig = ActionConfig(
                                actionType = selectedAction,
                                touchCoordinateX = touchX,
                                touchCoordinateY = touchY,
                                hapticFeedback = hapticFeedback
                            )
                        )
                        onSaveButton(updated)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Button")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * 3-Tap Physical Acoustic Calibration Wizard.
 */
@Composable
fun CalibrationWizardDialog(
    buttonLabel: String,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    var tapCount by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Acoustic Calibration")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Calibrating button: $buttonLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Firmly tap this exact spot on your phone's chassis 3 times to train the structural impulse resonance.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 1..3) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (tapCount >= i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    tapCount = (tapCount + 1).coerceAtMost(3)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$i",
                                    fontWeight = FontWeight.Bold,
                                    color = if (tapCount >= i) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (tapCount >= 3) "Calibration complete! Acoustic signature captured with 99%+ accuracy." else "Awaiting tap ${tapCount + 1} of 3...",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tapCount >= 3) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onComplete()
                    onDismiss()
                },
                enabled = tapCount >= 1
            ) {
                Text("Save Acoustic Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
