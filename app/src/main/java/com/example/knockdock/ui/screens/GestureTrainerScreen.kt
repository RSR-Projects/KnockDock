package com.example.knockdock.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knockdock.model.GestureCategory
import com.example.knockdock.model.GestureDefinition
import com.example.knockdock.model.GestureType
import com.example.knockdock.model.ImpactEvent
import com.example.knockdock.recognition.GestureTrainer
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlassDialog
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlassSlider
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LiquidStatusPill
import com.example.knockdock.ui.theme.LocalBackdrop
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanGlowBright
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateGlassDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun GestureTrainerScreen(
    controller: MonitoringController,
    onGestureSaved: () -> Unit
) {
    val trainer = remember { GestureTrainer() }
    val trainerState by trainer.state.collectAsState()
    val settings by controller.repository.settingsFlow.collectAsState()
    val allGestures by controller.repository.getAllGestures().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val backdrop = LocalBackdrop.current
    val density = LocalDensity.current

    var gestureNameInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GestureCategory.CUSTOM) }
    var saveStatusMessage by remember { mutableStateOf<String?>(null) }

    // Direct Manual Creator / Editor Dialog
    var showEditGestureDialog by remember { mutableStateOf(false) }
    var editingGestureId by remember { mutableStateOf<String?>(null) } // null = creating directly
    var dialogGestureName by remember { mutableStateOf("") }
    var dialogGestureDesc by remember { mutableStateOf("") }
    var dialogImpactCount by remember { mutableStateOf(2) }
    var dialogMinInterval by remember { mutableStateOf(100L) }
    var dialogMaxInterval by remember { mutableStateOf(450L) }
    var dialogMinConfidence by remember { mutableStateOf(0.65f) }
    var dialogCooldown by remember { mutableStateOf(750L) }

    // Connect incoming sensor impact events to trainer when recording is active
    LaunchedEffect(trainerState.isRecordingActive) {
        if (trainerState.isRecordingActive) {
            controller.startMonitoring()
            controller.sensorHelper.impactEvents.collect { impact ->
                trainer.onImpactRecorded(impact)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Gesture Trainer",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Calibrate Custom Knocks & Patterns",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LiquidGlowButton(
                    text = "+ Manual",
                    onClick = {
                        editingGestureId = null
                        dialogGestureName = "Custom Knock Pattern"
                        dialogGestureDesc = "User configured gesture"
                        dialogImpactCount = 2
                        dialogMinInterval = 100L
                        dialogMaxInterval = 450L
                        dialogMinConfidence = 0.65f
                        dialogCooldown = 750L
                        showEditGestureDialog = true
                    },
                    accentColor = IndigoPulse
                )
            }
        }

        // Section 1: Interactive Screen Tap Pad with Kyant0 Refractive Lens Orb
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tap_simulator_pad"),
                accentColor = CyanGlow,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Interactive Screen Knock Pad",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap the liquid glass orb below to simulate physical knocks or train patterns",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val padBackdropModifier = if (backdrop != null && settings.visualEffectsQuality != "LOW") {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(12f)
                                lens(8f, 16f)
                            },
                            highlight = { Highlight.Default }
                        )
                    } else Modifier

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .then(padBackdropModifier)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        CyanGlow.copy(alpha = 0.30f),
                                        IndigoPulse.copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(
                                BorderStroke(
                                    2.dp,
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.7f), CyanGlow, IndigoPulse)
                                    )
                                ),
                                CircleShape
                            )
                            .clickable {
                                val now = System.nanoTime()
                                val syntheticImpact = ImpactEvent(
                                    timestampNanos = now,
                                    peakAmplitude = 3.8f,
                                    durationMs = 28L,
                                    energy = 8.5f,
                                    dominantAxis = "Z"
                                )
                                controller.gestureRecognizer.onImpact(syntheticImpact)
                                if (trainerState.isRecordingActive) {
                                    trainer.onImpactRecorded(syntheticImpact)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Tap Pad",
                            tint = CyanGlowBright,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tap rhythmically (e.g. Double or Triple Tap)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1
                    )
                }
            }
        }

        // Section 2: Sensor Recording Workflow
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recording_workflow_card"),
                accentColor = if (trainerState.isRecordingActive) RoseError else IndigoPulse,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acoustic Signal Training",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        LiquidStatusPill(
                            text = if (trainerState.isRecordingActive) "RECORDING" else "READY",
                            statusColor = if (trainerState.isRecordingActive) RoseError else MintSuccess
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = gestureNameInput,
                        onValueChange = {
                            gestureNameInput = it
                            trainer.setGestureInfo(it, selectedCategory)
                        },
                        label = { Text("Gesture Name (e.g. Desk Triple Tap)") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gesture_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Guidance Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedRectangle(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedRectangle(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = trainerState.validationMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanGlowBright
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (trainerState.isRecordingActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(RoseError)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recorded ${trainerState.currentTrialImpacts.size} impacts in this trial",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LiquidGlowButton(
                            text = "Finish This Trial",
                            onClick = { trainer.finishCurrentTrial() },
                            accentColor = MintSuccess,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlowButton(
                                text = "Record Pattern",
                                onClick = { trainer.startRecording(isNegative = false) },
                                accentColor = CyanGlow,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        tint = RoseError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )

                            LiquidGlowButton(
                                text = "Record Noise",
                                onClick = { trainer.startRecording(isNegative = true) },
                                accentColor = AmberAlert,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (trainerState.recordedTrials.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LiquidGlowButton(
                            text = "Save Trained Gesture",
                            onClick = {
                                val trained = trainer.buildTrainedGesture()
                                if (trained != null) {
                                    scope.launch {
                                        controller.repository.saveGesture(trained)
                                        saveStatusMessage = "Gesture '${trained.name}' saved to library!"
                                        onGestureSaved()
                                    }
                                }
                            },
                            accentColor = MintSuccess,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_trained_gesture_button")
                        )
                    }

                    if (saveStatusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = saveStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MintSuccess
                        )
                    }
                }
            }
        }

        // Section 3: Gestures Library List with Full Edit/Delete & Zero Overlapping Text
        item {
            Text(
                text = "Gesture Library (${allGestures.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(allGestures) { gesture ->
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gesture_item_${gesture.id}"),
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Row: Title + Preset/Custom Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = gesture.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LiquidStatusPill(
                            text = if (gesture.isSystemPreset) "Preset" else "Custom",
                            statusColor = if (gesture.isSystemPreset) CyanGlow else IndigoPulse
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Row: Description/Stats + Glass Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = gesture.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${gesture.expectedImpacts}x impacts • ${(gesture.minConfidence * 100).toInt()}% conf • ${gesture.cooldownMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassIconButton(
                                onClick = {
                                    editingGestureId = gesture.id
                                    dialogGestureName = gesture.name
                                    dialogGestureDesc = gesture.description
                                    dialogImpactCount = gesture.expectedImpacts
                                    dialogMinInterval = gesture.minIntervalMs
                                    dialogMaxInterval = gesture.maxIntervalMs
                                    dialogMinConfidence = gesture.minConfidence
                                    dialogCooldown = gesture.cooldownMs
                                    showEditGestureDialog = true
                                },
                                size = 36.dp,
                                accentColor = CyanGlow
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Gesture",
                                    tint = CyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (!gesture.isSystemPreset) {
                                LiquidGlassIconButton(
                                    onClick = {
                                        scope.launch {
                                            controller.repository.deleteCustomGesture(gesture.id)
                                        }
                                    },
                                    size = 36.dp,
                                    accentColor = RoseError
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Gesture",
                                        tint = RoseError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }

    // Liquid Glass Dialog: Create or Edit Gesture Parameters
    if (showEditGestureDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showEditGestureDialog = false },
            title = if (editingGestureId != null) "Edit Gesture Parameters" else "Create Custom Gesture",
            content = {
                Column {
                    OutlinedTextField(
                        value = dialogGestureName,
                        onValueChange = { dialogGestureName = it },
                        label = { Text("Gesture Name") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dialogGestureDesc,
                        onValueChange = { dialogGestureDesc = it },
                        label = { Text("Description") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expected Impacts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expected Impacts",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidStatusPill(text = "${dialogImpactCount}x", statusColor = CyanGlow)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LiquidGlassSlider(
                        value = dialogImpactCount.toFloat(),
                        onValueChange = { dialogImpactCount = it.toInt() },
                        valueRange = 1f..6f,
                        steps = 4,
                        accentColor = CyanGlow
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Min Confidence Threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Min Confidence",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidStatusPill(
                            text = "${(dialogMinConfidence * 100).toInt()}%",
                            statusColor = IndigoPulse
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LiquidGlassSlider(
                        value = dialogMinConfidence,
                        onValueChange = { dialogMinConfidence = it },
                        valueRange = 0.4f..0.95f,
                        accentColor = IndigoPulse
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cooldown ms
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cooldown Window",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidStatusPill(text = "${dialogCooldown}ms", statusColor = MintSuccess)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LiquidGlassSlider(
                        value = dialogCooldown.toFloat(),
                        onValueChange = { dialogCooldown = it.toLong() },
                        valueRange = 300f..2000f,
                        accentColor = MintSuccess
                    )
                }
            },
            confirmButton = {
                LiquidGlowButton(
                    text = "Save Gesture",
                    accentColor = CyanGlow,
                    onClick = {
                        if (dialogGestureName.isNotBlank()) {
                            val id = editingGestureId ?: "gesture_${UUID.randomUUID().toString().take(8)}"
                            val type = when (dialogImpactCount) {
                                1 -> GestureType.SINGLE_KNOCK
                                2 -> GestureType.DOUBLE_KNOCK
                                3 -> GestureType.TRIPLE_KNOCK
                                else -> GestureType.CUSTOM_PATTERN
                            }
                            val updated = GestureDefinition(
                                id = id,
                                name = dialogGestureName.trim(),
                                description = dialogGestureDesc.trim().ifEmpty { "$dialogImpactCount-tap knock pattern" },
                                type = type,
                                category = GestureCategory.CUSTOM,
                                expectedImpacts = dialogImpactCount,
                                minIntervalMs = dialogMinInterval,
                                maxIntervalMs = dialogMaxInterval,
                                minConfidence = dialogMinConfidence,
                                cooldownMs = dialogCooldown,
                                isSystemPreset = false
                            )
                            scope.launch {
                                controller.repository.saveGesture(updated)
                                showEditGestureDialog = false
                            }
                        }
                    }
                )
            },
            dismissButton = {
                LiquidGlowButton(
                    text = "Cancel",
                    accentColor = Color.Gray,
                    onClick = { showEditGestureDialog = false }
                )
            }
        )
    }
}
