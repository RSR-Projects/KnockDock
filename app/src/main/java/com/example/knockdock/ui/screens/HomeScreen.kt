package com.example.knockdock.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knockdock.sensor.SensorReplayer
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlassSlider
import com.example.knockdock.ui.theme.LiquidGlassSwitch
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LiquidStatusPill
import com.example.knockdock.ui.theme.LocalBackdrop
import com.example.knockdock.ui.theme.liquidTouchGesture
import com.example.knockdock.ui.theme.rememberLiquidTouchState
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
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    controller: MonitoringController,
    onNavigateToTrainer: () -> Unit,
    onNavigateToLab: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToActionStudio: () -> Unit
) {
    val isMonitoring by controller.isMonitoring.collectAsState()
    val lastGesture by controller.lastDetectedGesture.collectAsState()
    val activeProfile by controller.repository.getActiveProfile().collectAsState(initial = null)
    val allProfiles by controller.repository.getAllProfiles().collectAsState(initial = emptyList())
    val mappings by controller.repository.getResolvedMappingsForActiveProfile().collectAsState(initial = emptyList())
    val recentLogs by controller.repository.getRecentExecutionLogs().collectAsState(initial = emptyList())
    val settings by controller.repository.settingsFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val backdrop = LocalBackdrop.current
    val density = LocalDensity.current

    var isProfileDropdownExpanded by remember { mutableStateOf(false) }
    var isOpticsTunerExpanded by remember { mutableStateOf(false) }
    val orbTouchState = rememberLiquidTouchState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Top Status Bar — weighted left column so pill never wraps vertically
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
                        text = "KnockDock",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Acoustic Desk Macro Controller",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LiquidStatusPill(
                    text = if (isMonitoring) "Dock Active" else "Dock Idle",
                    statusColor = if (isMonitoring) MintSuccess else AmberAlert,
                    modifier = Modifier.testTag("monitoring_status_pill")
                )
            }
        }

        // Monitoring Hero Card / Live Liquid Glass Beacon
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_dock_card"),
                accentColor = if (isMonitoring) CyanGlow else Color.White.copy(alpha = 0.25f),
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val orbBlurPx = with(density) { (settings.glassBlurRadius * 0.9f).dp.toPx() }
                    val orbLensHeightPx = with(density) { (settings.glassRefractionHeight * 1.15f).dp.toPx() }
                    val orbLensAmountPx = with(density) { (settings.glassRefractionAmount * 1.15f).dp.toPx() }

                    // Refractive Liquid Glass Orb Beacon using Kyant0 drawBackdrop + lens + liquid touch physics
                    val orbBackdropModifier = if (backdrop != null && settings.visualEffectsQuality != "LOW") {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                if (orbBlurPx > 0f) blur(orbBlurPx)
                                if (orbLensAmountPx > 0f) {
                                    lens(
                                        refractionHeight = orbLensHeightPx,
                                        refractionAmount = orbLensAmountPx,
                                        depthEffect = true,
                                        chromaticAberration = settings.glassChromaticAberration
                                    )
                                }
                            },
                            layerBlock = if (settings.liquidTouchPhysics && !settings.reducedMotion) {
                                {
                                    translationX = orbTouchState.offsetXValue * 1.45f
                                    translationY = orbTouchState.offsetYValue * 1.45f
                                    scaleX = orbTouchState.scaleXValue
                                    scaleY = orbTouchState.scaleYValue
                                }
                            } else null,
                            highlight = { Highlight.Default },
                            onDrawSurface = {
                                drawRect(
                                    Brush.radialGradient(
                                        colors = if (isMonitoring) {
                                            listOf(
                                                CyanGlow.copy(alpha = 0.28f),
                                                IndigoPulse.copy(alpha = 0.12f),
                                                SlateGlassDark.copy(alpha = 0.45f)
                                            )
                                        } else {
                                            listOf(
                                                Color.White.copy(alpha = 0.10f),
                                                SlateGlassDark.copy(alpha = 0.52f)
                                            )
                                        }
                                    )
                                )
                            }
                        )
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isMonitoring) {
                                        listOf(CyanGlow.copy(alpha = 0.30f), IndigoPulse.copy(alpha = 0.08f))
                                    } else {
                                        listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                                    }
                                )
                            )
                    }

                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .liquidTouchGesture(
                                touchState = orbTouchState,
                                enabled = settings.liquidTouchPhysics && !settings.reducedMotion,
                                maxDisplacementPx = with(density) { 34.dp.toPx() }
                            )
                            .then(orbBackdropModifier)
                            .border(
                                BorderStroke(
                                    2.dp,
                                    Brush.verticalGradient(
                                        if (isMonitoring) {
                                            listOf(Color.White.copy(alpha = 0.7f), CyanGlow, IndigoPulse)
                                        } else {
                                            listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.08f))
                                        }
                                    )
                                ),
                                CircleShape
                            )
                            .clickable {
                                scope.launch {
                                    controller.simulateReplay(SensorReplayer.ReplayPreset.SINGLE_KNOCK)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isMonitoring) Icons.Default.Vibration else Icons.Default.TouchApp,
                                contentDescription = "Dock Status",
                                tint = if (isMonitoring) CyanGlowBright else TextSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isMonitoring) "LIVE" else "TAP TEST",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMonitoring) CyanGlowBright else TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isMonitoring) "Listening for Desk Knocks" else "Dock Monitoring Paused",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LiveMonitoringSubtitle(
                        controller = controller,
                        isMonitoring = isMonitoring
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Start / Stop Toggle Button
                    LiquidGlowButton(
                        text = if (isMonitoring) "Stop Monitoring" else "Start Monitoring",
                        onClick = { controller.toggleMonitoring() },
                        accentColor = if (isMonitoring) RoseError else CyanGlow,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_monitoring_button")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Knock Simulator Row
                    Text(
                        text = "Simulate / Test Knocks Directly",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlowButton(
                            text = "1x Tap",
                            onClick = {
                                scope.launch {
                                    controller.simulateReplay(SensorReplayer.ReplayPreset.SINGLE_KNOCK)
                                }
                            },
                            accentColor = CyanGlow,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidGlowButton(
                            text = "2x Double",
                            onClick = {
                                scope.launch {
                                    controller.simulateReplay(SensorReplayer.ReplayPreset.DOUBLE_KNOCK)
                                }
                            },
                            accentColor = IndigoPulse,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidGlowButton(
                            text = "3x Triple",
                            onClick = {
                                scope.launch {
                                    controller.simulateReplay(SensorReplayer.ReplayPreset.TRIPLE_KNOCK)
                                }
                            },
                            accentColor = MintSuccess,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Active Profile Selector & Switcher
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_profile_card"),
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
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active Automation Profile",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        LiquidGlassIconButton(
                            onClick = onNavigateToProfiles,
                            size = 34.dp,
                            accentColor = CyanGlow
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Manage Profiles",
                                tint = CyanGlowBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = isProfileDropdownExpanded,
                        onExpandedChange = { isProfileDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = activeProfile?.name ?: "Desk Commander",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProfileDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = isProfileDropdownExpanded,
                            onDismissRequest = { isProfileDropdownExpanded = false },
                            containerColor = SlateGlassDark
                        ) {
                            allProfiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name, color = TextPrimary, maxLines = 1) },
                                    onClick = {
                                        scope.launch {
                                            controller.repository.setActiveProfile(p.id)
                                        }
                                        isProfileDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Latest Detected Gesture Beacon
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("latest_gesture_card"),
                accentColor = if (lastGesture != null) IndigoPulse else null,
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
                            text = "Latest Gesture Trigger",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (lastGesture?.isSynthetic == true) {
                            LiquidStatusPill(
                                text = "Synthetic Replay",
                                statusColor = AmberAlert
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (lastGesture != null) {
                        val g = lastGesture!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(IndigoPulse.copy(alpha = 0.24f))
                                    .border(1.dp, IndigoPulse.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${g.impactCount}x",
                                    fontWeight = FontWeight.Bold,
                                    color = CyanGlowBright,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = g.gestureName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Confidence: ${(g.confidence * 100).toInt()}% • Gaps: ${g.intervalsMs.joinToString(", ")} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { g.confidence },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(Capsule()),
                            color = CyanGlow,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                    } else {
                        Text(
                            text = "No gestures detected yet. Double-tap your desk or use the simulator above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        // Live iOS Liquid Glass Quick Tuner Card (Independent Card Glass vs. Floating Nav Dock)
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_liquid_optics_card"),
                accentColor = CyanGlow,
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
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "Liquid Glass Quick Tuner",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Independent Card Glass vs. Floating Nav Dock optics",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        LiquidGlowButton(
                            text = if (isOpticsTunerExpanded) "Hide" else "Tune",
                            onClick = { isOpticsTunerExpanded = !isOpticsTunerExpanded },
                            accentColor = IndigoPulse
                        )
                    }

                    if (isOpticsTunerExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Card Blur On/Off Toggle (Card-Only)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Card Backdrop Blur Enabled (Cards Only)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            LiquidGlassSwitch(
                                checked = settings.cardBlurEnabled,
                                onCheckedChange = {
                                    controller.repository.updateCardGlassSettings(
                                        settings.toCardGlassSettings().copy(cardBlurEnabled = it)
                                    )
                                },
                                accentColor = CyanGlow,
                                modifier = Modifier.testTag("home_card_blur_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Card Blur Radius",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            LiquidStatusPill(
                                text = if (settings.cardBlurEnabled) String.format(Locale.US, "%.1f dp", settings.cardBlurRadiusDp) else "OFF (0 dp)",
                                statusColor = if (settings.cardBlurEnabled) CyanGlow else AmberAlert
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassSlider(
                            value = settings.cardBlurRadiusDp,
                            onValueChange = {
                                controller.repository.updateCardGlassSettings(
                                    settings.toCardGlassSettings().copy(cardBlurRadiusDp = it)
                                )
                            },
                            valueRange = 0f..40f,
                            accentColor = CyanGlow,
                            modifier = Modifier.testTag("home_card_blur_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nav Dock Blur Level (Dock Only)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            LiquidStatusPill(
                                text = String.format(Locale.US, "%.1f dp", settings.glassBlurRadius),
                                statusColor = CyanGlow
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassSlider(
                            value = settings.glassBlurRadius,
                            onValueChange = {
                                controller.repository.updateNavigationGlassSettings(
                                    settings.toNavigationGlassSettings().copy(navBlurRadiusDp = it)
                                )
                            },
                            valueRange = 0f..40f,
                            accentColor = CyanGlow,
                            modifier = Modifier.testTag("home_blur_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nav Dock Lens Refraction (Dock Only)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            LiquidStatusPill(
                                text = "${settings.glassRefractionAmount.toInt()} dp",
                                statusColor = IndigoPulse
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassSlider(
                            value = settings.glassRefractionAmount,
                            onValueChange = {
                                controller.repository.updateNavigationGlassSettings(
                                    settings.toNavigationGlassSettings().copy(navRefractionAmountDp = it)
                                )
                            },
                            valueRange = 0f..76f,
                            accentColor = IndigoPulse,
                            modifier = Modifier.testTag("home_refraction_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Liquid Touch Movement & Squish",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            LiquidGlassSwitch(
                                checked = settings.liquidTouchPhysics,
                                onCheckedChange = {
                                    controller.repository.updateSettings(
                                        settings.copy(liquidTouchPhysics = it)
                                    )
                                },
                                accentColor = MintSuccess
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Shortcuts Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_trainer_card"),
                    accentColor = IndigoPulse,
                    onClick = onNavigateToTrainer,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Column {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = IndigoPulse)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Train Gesture",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Record & Calibrate",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LiquidGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_sensor_lab_card"),
                    accentColor = CyanGlow,
                    onClick = onNavigateToLab,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Column {
                        Icon(Icons.Default.Science, contentDescription = null, tint = CyanGlow)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sensor Lab",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Live Oscilloscope",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Active Gesture Mappings Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Mappings (${mappings.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassIconButton(
                        onClick = onNavigateToActionStudio,
                        size = 36.dp,
                        accentColor = IndigoPulse
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Action Studio",
                            tint = IndigoPulse,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    LiquidGlassIconButton(
                        onClick = onNavigateToProfiles,
                        size = 36.dp,
                        accentColor = CyanGlow
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Mappings",
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (mappings.isEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Text(
                        text = "No mappings configured in current profile. Tap 'Profiles' to bind gestures to actions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            items(mappings) { m ->
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mapping_item_${m.mappingId}"),
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
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
                                text = "${m.gesture.name}  →  ${m.action.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = m.action.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        LiquidStatusPill(
                            text = if (m.isEnabled) "Ready" else "Muted",
                            statusColor = if (m.isEnabled) MintSuccess else Color.Gray
                        )
                    }
                }
            }
        }

        // Recent Event Execution Feed
        item {
            Text(
                text = "Recent Macro Activity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (recentLogs.isEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Text(
                        text = "No actions executed yet. Knock on your desk or use the simulator above to trigger macros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            items(recentLogs.take(5)) { log ->
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val timeStr = timeFormat.format(Date(log.executedAtEpochMs))

                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (log.success) MintSuccess else RoseError,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "${log.actionName} • ${log.gestureName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (log.success) TextSecondary else RoseError,
                                maxLines = 2,
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
                }
            }
        }

        // Bottom spacing for floating iOS-style Liquid Glass dock
        item {
            Spacer(modifier = Modifier.height(104.dp))
        }
    }
}

@Composable
private fun LiveMonitoringSubtitle(
    controller: MonitoringController,
    isMonitoring: Boolean
) {
    if (!isMonitoring) {
        Text(
            text = "Activate continuous acoustic surface monitoring or tap below to simulate",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    } else {
        val telemetry by controller.sensorHelper.telemetryState.collectAsState()
        Text(
            text = "Impulse: ${"%.2f".format(telemetry.filteredImpulse)} m/s² • Noise Floor: ${"%.2f".format(telemetry.currentNoiseFloor)} m/s²",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

