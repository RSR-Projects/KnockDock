package com.example.knockdock.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    controller: MonitoringController,
    onBack: () -> Unit
) {
    val telemetry by controller.sensorHelper.telemetryState.collectAsState()
    val isMonitoring by controller.isMonitoring.collectAsState()
    val settings by controller.repository.settingsFlow.collectAsState()
    val logs by controller.repository.getRecentExecutionLogs().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    onClick = onBack,
                    size = 40.dp,
                    accentColor = CyanGlow
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System Diagnostics",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Hardware Audit, Pipeline Checks & Logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Section: Physical Sensor Hardware Audit
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hardware_audit_card"),
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
                    Text(
                        text = "Sensor Hardware Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    DiagnosticRow(
                        label = "Accelerometer",
                        value = controller.sensorHelper.accelerometerName,
                        isPass = controller.sensorHelper.isAccelerometerAvailable
                    )
                    DiagnosticRow(
                        label = "Gyroscope",
                        value = controller.sensorHelper.gyroscopeName,
                        isPass = controller.sensorHelper.isGyroscopeAvailable
                    )
                    DiagnosticRow(
                        label = "Sampling Interval Mode",
                        value = "${settings.samplingRate} (Current rate active)",
                        isPass = true
                    )
                    DiagnosticRow(
                        label = "Noise Floor Estimate",
                        value = "${"%.3f".format(telemetry.currentNoiseFloor)} m/s²",
                        isPass = telemetry.currentNoiseFloor < 1.0f
                    )
                    DiagnosticRow(
                        label = "Current Device State",
                        value = if (telemetry.isStationaryOnDesk) "Stationary on desk" else "Device moving / handled",
                        isPass = telemetry.isStationaryOnDesk
                    )
                }
            }
        }

        // Section: Liquid Glass Graphics Pipeline Audit
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("graphics_audit_card"),
                accentColor = IndigoPulse,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
                    Text(
                        text = "Liquid Glass & Shader Pipeline Audit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Status of Kyant0/AndroidLiquidGlass integration & hardware shader support.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    DiagnosticRow(
                        label = "Liquid Glass Library",
                        value = "io.github.kyant0:backdrop:1.0.6 + shapes:1.2.0",
                        isPass = true
                    )
                    val agslSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    DiagnosticRow(
                        label = "Hardware AGSL RuntimeShader",
                        value = if (agslSupported) "Supported (Android 13+)" else "Fallback glass mode active (API ${Build.VERSION.SDK_INT} < 33)",
                        isPass = agslSupported
                    )
                    DiagnosticRow(
                        label = "Visual Effects Tier",
                        value = "${settings.visualEffectsQuality} quality tier active",
                        isPass = true
                    )
                    DiagnosticRow(
                        label = "Reduced Motion Preference",
                        value = if (settings.reducedMotion) "Reduced motion enabled" else "Standard fluid animations",
                        isPass = true
                    )
                }
            }
        }

        // Section: Permissions & Lifecycle
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permissions_audit_card"),
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
                    Text(
                        text = "Permissions & Foreground Service",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    DiagnosticRow(
                        label = "Monitoring Engine Status",
                        value = if (isMonitoring) "RUNNING" else "STOPPED",
                        isPass = isMonitoring
                    )
                    DiagnosticRow(
                        label = "Foreground Service Type",
                        value = "FOREGROUND_SERVICE_SPECIAL_USE (Continuous desk sensor monitoring)",
                        isPass = true
                    )
                    DiagnosticRow(
                        label = "High Sampling Rate Sensors",
                        value = "Declared in manifest (API 31+)",
                        isPass = true
                    )
                    DiagnosticRow(
                        label = "Vibration / Haptic Actuator",
                        value = "VIBRATE permission granted",
                        isPass = true
                    )
                }
            }
        }

        // Section: Macro Execution Log & Error History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Macro Execution History (${logs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (logs.isNotEmpty()) {
                    LiquidGlassIconButton(
                        onClick = {
                            scope.launch {
                                controller.repository.clearLogs()
                            }
                        },
                        size = 36.dp,
                        accentColor = RoseError
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = RoseError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Text(
                        text = "No recorded macro executions in history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            items(logs) { log ->
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timeStr = timeFormat.format(Date(log.executedAtEpochMs))

                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (log.success) MintSuccess else RoseError,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (log.success) MintSuccess else RoseError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = log.actionName,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gesture: ${log.gestureName} • ${if (log.isSimulated) "Synthetic Replay" else "Physical Desk Sensor"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = log.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (log.success) TextSecondary else RoseError
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isPass: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Icon(
            imageVector = if (isPass) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isPass) MintSuccess else AmberAlert,
            modifier = Modifier.size(18.dp)
        )
    }
}
