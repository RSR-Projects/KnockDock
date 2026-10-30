package com.example.knockdock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knockdock.sensor.SensorReplayer
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.components.SensorWaveformCanvas
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlassSegmentedRow
import com.example.knockdock.ui.theme.LiquidGlassSlider
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LiquidStatusPill
import com.example.knockdock.ui.theme.LocalBackdrop
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateGlassDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch

@Composable
fun SensorLabScreen(
    controller: MonitoringController
) {
    val telemetry by controller.sensorHelper.telemetryState.collectAsState()
    val settings by controller.repository.settingsFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var isPaused by remember { mutableStateOf(false) }
    var capturedPeak by remember { mutableStateOf<Float?>(null) }
    var selectedReplayPreset by remember { mutableStateOf(SensorReplayer.ReplayPreset.DOUBLE_KNOCK) }
    var isSimulating by remember { mutableStateOf(false) }

    // Check if recent impact occurred in last 250ms
    val isImpactActive = (System.nanoTime() - telemetry.lastImpactTimeNanos) < 250_000_000L
    val effectiveThreshold = controller.sensorHelper.impactDetector.calculateEffectiveThreshold(telemetry.currentNoiseFloor)

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
                        text = "Sensor Lab",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Real-Time Acoustic Telemetry & Oscilloscope",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LiquidStatusPill(
                    text = if (isImpactActive) "IMPACT" else if (telemetry.isStationaryOnDesk) "Stationary" else "Moving",
                    statusColor = if (isImpactActive) RoseError else if (telemetry.isStationaryOnDesk) MintSuccess else AmberAlert,
                    modifier = Modifier.testTag("sensor_lab_status_pill")
                )
            }
        }

        // Oscilloscope Waveform Card
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("waveform_card"),
                accentColor = if (isImpactActive) RoseError else CyanGlow,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Filtered Vibration Impulse",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Live Peak: ${"%.2f".format(telemetry.filteredImpulse)} m/s²",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = CyanGlow,
                                maxLines = 1
                            )
                        }

                        // Oscilloscope Controls (Pause/Play, Clear)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidGlassIconButton(
                                onClick = { isPaused = !isPaused },
                                size = 36.dp,
                                accentColor = CyanGlow
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isPaused) "Resume" else "Pause",
                                    tint = CyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            LiquidGlassIconButton(
                                onClick = {
                                    controller.sensorHelper.clearWaveformBuffer()
                                    capturedPeak = null
                                },
                                size = 36.dp,
                                accentColor = IndigoPulse
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Waveform Canvas (kept crisp & unblurred for 60fps readability)
                    SensorWaveformCanvas(
                        waveformBuffer = controller.sensorHelper.waveformBuffer,
                        bufferWriteIndex = controller.sensorHelper.bufferWriteIndex,
                        effectiveThreshold = effectiveThreshold,
                        isKnockTriggered = isImpactActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Legend & Threshold status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AmberAlert)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Threshold: ${"%.2f".format(effectiveThreshold)} m/s²",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "Floor: ${"%.2f".format(telemetry.currentNoiseFloor)} m/s²",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Live Numerical Sensor Telemetry Grid
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telemetry_grid_card"),
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
                    Text(
                        text = "Physical Sensor Measurements",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Accelerometer 3-Axis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryAxisCard(
                            label = "Accel X",
                            value = telemetry.accelX,
                            unit = "m/s²",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryAxisCard(
                            label = "Accel Y",
                            value = telemetry.accelY,
                            unit = "m/s²",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryAxisCard(
                            label = "Accel Z",
                            value = telemetry.accelZ,
                            unit = "m/s²",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gyroscope 3-Axis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryAxisCard(
                            label = "Gyro X",
                            value = telemetry.gyroX,
                            unit = "rad/s",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryAxisCard(
                            label = "Gyro Y",
                            value = telemetry.gyroY,
                            unit = "rad/s",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryAxisCard(
                            label = "Gyro Z",
                            value = telemetry.gyroZ,
                            unit = "rad/s",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Sensitivity & Threshold Calibration Sliders using LiquidGlassSlider
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calibration_slider_card"),
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
                            text = "Knock Sensitivity",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidStatusPill(
                            text = "${(settings.sensitivity * 100).toInt()}%",
                            statusColor = CyanGlow
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Higher sensitivity detects lighter desk taps; lower sensitivity filters out keyboard typing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LiquidGlassSlider(
                        value = settings.sensitivity,
                        onValueChange = { newSens ->
                            controller.repository.updateSettings(settings.copy(sensitivity = newSens))
                        },
                        valueRange = 0.1f..1.0f,
                        accentColor = CyanGlow,
                        modifier = Modifier.testTag("sensitivity_slider")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Base Vibration Threshold Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Base Shock Threshold",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidStatusPill(
                            text = "${"%.1f".format(settings.baseVibrationThreshold)} m/s²",
                            statusColor = IndigoPulse
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LiquidGlassSlider(
                        value = settings.baseVibrationThreshold,
                        onValueChange = { newThresh ->
                            controller.repository.updateSettings(settings.copy(baseVibrationThreshold = newThresh))
                        },
                        valueRange = 0.8f..6.0f,
                        accentColor = IndigoPulse,
                        modifier = Modifier.testTag("threshold_slider")
                    )
                }
            }
        }

        // Interactive Replay & Emulation Testing Suite
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("replay_simulation_card"),
                accentColor = IndigoPulse,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
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
                                text = "Synthetic Signal Replay Suite",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Test algorithms without physical desk knocks",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        LiquidStatusPill(
                            text = "Lab Mode",
                            statusColor = IndigoPulse
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset selection row using LiquidGlassSegmentedRow
                    LiquidGlassSegmentedRow(
                        items = SensorReplayer.ReplayPreset.entries.take(3),
                        selectedItem = selectedReplayPreset,
                        onSelectItem = { selectedReplayPreset = it },
                        labelProvider = { it.displayName },
                        accentColor = IndigoPulse
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Trigger Simulation Button
                    LiquidGlowButton(
                        text = if (isSimulating) "Replaying Synthetic Waveform..." else "Inject Test Pattern",
                        onClick = {
                            if (!isSimulating) {
                                isSimulating = true
                                scope.launch {
                                    controller.simulateReplay(selectedReplayPreset)
                                    isSimulating = false
                                }
                            }
                        },
                        enabled = !isSimulating,
                        accentColor = IndigoPulse,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inject_replay_button")
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun TelemetryAxisCard(
    label: String,
    value: Float,
    unit: String,
    modifier: Modifier = Modifier
) {
    val shape = remember { RoundedCornerShape(14.dp) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(SlateGlassDark.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(vertical = 7.dp, horizontal = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${"%.2f".format(value)} $unit",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

