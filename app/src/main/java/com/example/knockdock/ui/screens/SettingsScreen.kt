package com.example.knockdock.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlassSegmentedRow
import com.example.knockdock.ui.theme.LiquidGlassSlider
import com.example.knockdock.ui.theme.LiquidGlassSwitch
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LiquidStatusPill
import com.example.knockdock.ui.theme.resolveCardTintColor
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    controller: MonitoringController,
    onNavigateToDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by controller.repository.settingsFlow.collectAsState()
    val scope = rememberCoroutineScope()

    // Track folder open/closed states — minimal, clean folder-based organization (all closed by default)
    var isBackgroundFolderOpen by remember { mutableStateOf(false) }
    var isCardGlassFolderOpen by remember { mutableStateOf(false) }
    var isCardAdvancedOpen by remember { mutableStateOf(false) }
    var isNavGlassFolderOpen by remember { mutableStateOf(false) }
    var isDetectionFolderOpen by remember { mutableStateOf(false) }
    var isFeedbackFolderOpen by remember { mutableStateOf(false) }
    var isPcFolderOpen by remember { mutableStateOf(false) }
    var isAboutFolderOpen by remember { mutableStateOf(false) }
    var isAboutDeveloperFolderOpen by remember { mutableStateOf(false) }
    var isAboutGitHubFolderOpen by remember { mutableStateOf(false) }
    var isAboutPrivacyFolderOpen by remember { mutableStateOf(false) }
    var isAboutLicensesFolderOpen by remember { mutableStateOf(false) }
    val appBuildInfo = remember(context) { resolveInstalledAppBuildInfo(context) }

    // Live check for Overlay ("Appear on Top") & Battery Optimization exemptions
    var canDrawOverlays by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }
    var ignoringBatteryOpt by remember {
        mutableStateOf(
            try {
                val pm = context.getSystemService(PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } catch (_: Throwable) {
                false
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
                ignoringBatteryOpt = try {
                    val pm = context.getSystemService(PowerManager::class.java)
                    pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                } catch (_: Throwable) {
                    false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pcHostInput by remember(settings.pcHost) { mutableStateOf(settings.pcHost) }
    var pcPortInput by remember(settings.pcPort) { mutableStateOf(settings.pcPort.toString()) }
    var pcTokenInput by remember(settings.pcAuthToken) { mutableStateOf(settings.pcAuthToken) }
    var pcStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingPc by remember { mutableStateOf(false) }

    val cardSettings = settings.toCardGlassSettings()
    val navSettings = settings.toNavigationGlassSettings()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap any category folder below to open its controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LiquidStatusPill(
                    text = "Dark Mode",
                    statusColor = CyanGlow
                )
            }
        }

        // =========================================================================
        // FOLDER 1: BACKGROUND EXECUTION, APP/URL LAUNCH & POWER
        // =========================================================================
        item {
            SettingsFolderCard(
                title = "Background Launch & Power",
                subtitle = "Run apps, URLs & knock sensors when app is closed or minimized",
                icon = Icons.Default.Speed,
                badgeText = if (canDrawOverlays && settings.backgroundMonitoringEnabled) "Ready" else "Setup",
                badgeColor = if (canDrawOverlays && settings.backgroundMonitoringEnabled) MintSuccess else AmberAlert,
                isExpanded = isBackgroundFolderOpen,
                onToggleExpand = { isBackgroundFolderOpen = !isBackgroundFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_background")
            ) {
                // 1. Background App & URL Launch Permission ("Appear on Top" / Overlay)
                Text(
                    text = "Android 10+ blocks apps from opening other apps or browser URLs in the background unless 'Appear on Top' is allowed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

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
                            text = "Background App & URL Launch",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (canDrawOverlays) {
                                "Allowed — Apps & URLs open immediately in background."
                            } else {
                                "Tap 'Allow' so knocks can open apps & URLs when KnockDock is closed."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canDrawOverlays) MintSuccess else AmberAlert
                        )
                    }
                    LiquidGlowButton(
                        text = if (canDrawOverlays) "Allowed" else "Allow",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(intent)
                                } catch (_: Throwable) {
                                    val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(fallback)
                                }
                            }
                        },
                        accentColor = if (canDrawOverlays) MintSuccess else CyanGlow,
                        modifier = Modifier.testTag("allow_background_launch_button")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Unrestricted Background Battery
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
                            text = "Unrestricted Background Battery",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (ignoringBatteryOpt) {
                                "Unrestricted — OS will not kill background knock service."
                            } else {
                                "Prevents Android from stopping knock detection when closed."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlowButton(
                        text = if (ignoringBatteryOpt) "Active" else "Exempt",
                        onClick = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            } catch (_: Throwable) {
                                try {
                                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(fallback)
                                } catch (_: Throwable) {}
                            }
                        },
                        accentColor = if (ignoringBatteryOpt) MintSuccess else IndigoPulse
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Background Persistent Monitoring Switch
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
                            text = "Background Persistent Service",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Keeps knock detection running when app is minimized or swiped closed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = settings.backgroundMonitoringEnabled,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(backgroundMonitoringEnabled = it))
                        },
                        accentColor = CyanGlow
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Sensor Sampling Rate",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                val rateOptions = listOf("NORMAL", "UI", "GAME", "FASTEST")
                LiquidGlassSegmentedRow(
                    items = rateOptions,
                    selectedItem = settings.samplingRate,
                    onSelectItem = { newRate ->
                        controller.repository.updateSettings(settings.copy(samplingRate = newRate))
                    },
                    labelProvider = { it },
                    accentColor = CyanGlow
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Battery Conscious Mode
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
                            text = "Battery-Conscious Idle Throttling",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Lowers background sensor polling after 5 minutes of zero movement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = settings.batteryConsciousMode,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(batteryConsciousMode = it))
                        },
                        accentColor = CyanGlow
                    )
                }
            }
        }

        // =========================================================================
        // FOLDER 2: LIQUID GLASS — CARD SURFACES (INDEPENDENT OF NAV BAR)
        // =========================================================================
        item {
            SettingsFolderCard(
                title = "Card Glass & Surface Style",
                subtitle = "Fine blur, opacity & tint for cards only",
                icon = Icons.Default.Layers,
                badgeText = if (cardSettings.cardLightweightMode) {
                    "Lite"
                } else if (cardSettings.cardBlurEnabled) {
                    String.format(Locale.US, "%.1f dp", cardSettings.cardBlurRadiusDp)
                } else {
                    "Blur OFF"
                },
                badgeColor = if (cardSettings.cardBlurEnabled && !cardSettings.cardLightweightMode) CyanGlow else AmberAlert,
                isExpanded = isCardGlassFolderOpen,
                onToggleExpand = { isCardGlassFolderOpen = !isCardGlassFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("card_glass_customizer_card")
            ) {
                // Live Compact Card Preview
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_glass_live_preview"),
                    enableRealtimeBackdropBlur = true,
                    overrideCardSettings = cardSettings,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Card Glass Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (cardSettings.cardBlurEnabled && !cardSettings.cardLightweightMode) {
                                    String.format(
                                        Locale.US,
                                        "Blur %.1fdp • Opacity %d%% • Tint %d%%",
                                        cardSettings.cardBlurRadiusDp,
                                        (cardSettings.cardBackgroundOpacity * 100).toInt(),
                                        (cardSettings.cardTintIntensity * 100).toInt()
                                    )
                                } else {
                                    "Card Blur OFF — Minimal solid dark card surface"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        LiquidStatusPill(
                            text = cardSettings.cardTintColorKey,
                            statusColor = resolveCardTintColor(cardSettings.cardTintColorKey).let {
                                if (it == Color.Transparent) CyanGlow else it
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Card Background Blur Toggle
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
                            text = "Card Background Blur",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Applies exclusively to cards; never alters the bottom dock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = cardSettings.cardBlurEnabled,
                        onCheckedChange = { enabled ->
                            controller.repository.updateCardGlassSettings(
                                cardSettings.copy(cardBlurEnabled = enabled)
                            )
                        },
                        accentColor = CyanGlow,
                        modifier = Modifier.testTag("card_blur_enabled_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Fine-Grained Card Blur Intensity (0.0 .. 40.0 dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card Blur Intensity",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(
                        text = if (cardSettings.cardBlurEnabled) {
                            String.format(Locale.US, "%.1f dp", cardSettings.cardBlurRadiusDp)
                        } else {
                            "OFF (0.0 dp)"
                        },
                        statusColor = if (cardSettings.cardBlurEnabled) CyanGlow else Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = cardSettings.cardBlurRadiusDp,
                    onValueChange = {
                        controller.repository.updateCardGlassSettings(
                            cardSettings.copy(cardBlurRadiusDp = it)
                        )
                    },
                    valueRange = 0f..40f,
                    accentColor = CyanGlow,
                    modifier = Modifier.testTag("card_blur_radius_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Card Background Opacity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card Dark Opacity",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(
                        text = "${(cardSettings.cardBackgroundOpacity * 100).toInt()}%",
                        statusColor = IndigoPulse
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = cardSettings.cardBackgroundOpacity,
                    onValueChange = {
                        controller.repository.updateCardGlassSettings(
                            cardSettings.copy(cardBackgroundOpacity = it)
                        )
                    },
                    valueRange = 0.15f..0.95f,
                    accentColor = IndigoPulse,
                    modifier = Modifier.testTag("card_opacity_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Card Tint Color & Fine Tint Intensity
                Text(
                    text = "Card Accent Tint",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                val tintChoices = listOf("CYAN", "VIOLET", "EMERALD", "AMBER", "SLATE", "NONE")
                LiquidGlassSegmentedRow(
                    items = tintChoices,
                    selectedItem = cardSettings.cardTintColorKey,
                    onSelectItem = { newTint ->
                        controller.repository.updateCardGlassSettings(
                            cardSettings.copy(cardTintColorKey = newTint)
                        )
                    },
                    labelProvider = { it },
                    accentColor = CyanGlow
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tint / Blue Glow Intensity",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(
                        text = "${(cardSettings.cardTintIntensity * 100).toInt()}%",
                        statusColor = CyanGlow
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = cardSettings.cardTintIntensity,
                    onValueChange = {
                        controller.repository.updateCardGlassSettings(
                            cardSettings.copy(cardTintIntensity = it)
                        )
                    },
                    valueRange = 0f..0.60f,
                    accentColor = CyanGlow,
                    modifier = Modifier.testTag("card_tint_intensity_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sub-folder for Advanced Card Fine-Tuning so the main folder stays minimal
                LiquidGlowButton(
                    text = if (isCardAdvancedOpen) "Hide Advanced Card Options" else "More Card Options",
                    onClick = { isCardAdvancedOpen = !isCardAdvancedOpen },
                    accentColor = IndigoPulse,
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(visible = isCardAdvancedOpen) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        // 5. Card Refraction Intensity
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Card Refraction Bevel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidStatusPill(
                                text = "${(cardSettings.cardRefractionIntensity * 100).toInt()}%",
                                statusColor = IndigoPulse
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassSlider(
                            value = cardSettings.cardRefractionIntensity,
                            onValueChange = {
                                controller.repository.updateCardGlassSettings(
                                    cardSettings.copy(cardRefractionIntensity = it)
                                )
                            },
                            valueRange = 0f..1f,
                            accentColor = IndigoPulse,
                            modifier = Modifier.testTag("card_refraction_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 6. Card Rim Highlight
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Card Edge Rim Highlight",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidStatusPill(
                                text = "${(cardSettings.cardRimHighlightIntensity * 100).toInt()}%",
                                statusColor = MintSuccess
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassSlider(
                            value = cardSettings.cardRimHighlightIntensity,
                            onValueChange = {
                                controller.repository.updateCardGlassSettings(
                                    cardSettings.copy(cardRimHighlightIntensity = it)
                                )
                            },
                            valueRange = 0f..1f,
                            accentColor = MintSuccess,
                            modifier = Modifier.testTag("card_rim_highlight_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 7. Card Corner Radius & Contrast
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Card Corner Radius",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidStatusPill(
                                text = "${cardSettings.cardCornerRadiusDp.toInt()} dp",
                                statusColor = CyanGlow
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassSlider(
                            value = cardSettings.cardCornerRadiusDp,
                            onValueChange = {
                                controller.repository.updateCardGlassSettings(
                                    cardSettings.copy(cardCornerRadiusDp = it)
                                )
                            },
                            valueRange = 8f..36f,
                            accentColor = CyanGlow,
                            modifier = Modifier.testTag("card_corner_radius_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Card Surface Contrast",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidStatusPill(
                                text = "${(cardSettings.cardSurfaceContrast * 100).toInt()}%",
                                statusColor = IndigoPulse
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassSlider(
                            value = cardSettings.cardSurfaceContrast,
                            onValueChange = {
                                controller.repository.updateCardGlassSettings(
                                    cardSettings.copy(cardSurfaceContrast = it)
                                )
                            },
                            valueRange = 0f..1f,
                            accentColor = IndigoPulse,
                            modifier = Modifier.testTag("card_contrast_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 8. Lightweight Surface Mode Toggle
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
                                    text = "Lightweight Card Mode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Minimal ultra-fast dark cards without shader passes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            LiquidGlassSwitch(
                                checked = cardSettings.cardLightweightMode,
                                onCheckedChange = { lw ->
                                    controller.repository.updateCardGlassSettings(
                                        cardSettings.copy(cardLightweightMode = lw)
                                    )
                                },
                                accentColor = AmberAlert,
                                modifier = Modifier.testTag("card_lightweight_mode_switch")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LiquidGlowButton(
                    text = "Reset Card Glass to Default",
                    onClick = {
                        controller.repository.resetCardGlassSettingsToDefault()
                    },
                    accentColor = CyanGlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_card_glass_button")
                )
            }
        }

        // =========================================================================
        // FOLDER 3: LIQUID GLASS — NAVIGATION DOCK & TOUCH SQUISH
        // =========================================================================
        item {
            SettingsFolderCard(
                title = "Navigation Dock & Liquid Squish",
                subtitle = "Floating dock blur, lens refraction & elastic touch squish",
                icon = Icons.Default.Palette,
                badgeText = if (navSettings.navBlurEnabled) {
                    String.format(Locale.US, "%.1f dp", navSettings.navBlurRadiusDp)
                } else {
                    "Blur OFF"
                },
                badgeColor = IndigoPulse,
                isExpanded = isNavGlassFolderOpen,
                onToggleExpand = { isNavGlassFolderOpen = !isNavGlassFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("liquid_glass_optics_card")
            ) {
                Text(
                    text = "Global GPU Effects Quality",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val qualityLevels = listOf("HIGH", "BALANCED", "LOW")
                LiquidGlassSegmentedRow(
                    items = qualityLevels,
                    selectedItem = settings.visualEffectsQuality,
                    onSelectItem = { newLevel ->
                        controller.repository.updateSettings(settings.copy(visualEffectsQuality = newLevel))
                    },
                    labelProvider = { it },
                    accentColor = IndigoPulse
                )

                Spacer(modifier = Modifier.height(12.dp))

                // iOS Liquid Touch Physics Switch
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
                            text = "Elastic Liquid Squish & Drag Physics",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Buttons, switches, dock & orb move and stretch with fast, bouncy liquid physics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = settings.liquidTouchPhysics,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(liquidTouchPhysics = it))
                        },
                        accentColor = MintSuccess
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Bar Blur Toggle
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
                            text = "Floating Navigation Bar Blur",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Real-time backdrop blur on the floating bottom dock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = navSettings.navBlurEnabled,
                        onCheckedChange = { enabled ->
                            controller.repository.updateNavigationGlassSettings(
                                navSettings.copy(navBlurEnabled = enabled)
                            )
                        },
                        accentColor = CyanGlow,
                        modifier = Modifier.testTag("nav_blur_enabled_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Backdrop Blur Level Slider (Fine 0.1 dp display)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Navigation Dock Blur Intensity",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(
                        text = String.format(Locale.US, "%.1f dp", navSettings.navBlurRadiusDp),
                        statusColor = CyanGlow
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = navSettings.navBlurRadiusDp,
                    onValueChange = {
                        controller.repository.updateNavigationGlassSettings(
                            navSettings.copy(navBlurRadiusDp = it)
                        )
                    },
                    valueRange = 0f..40f,
                    accentColor = CyanGlow,
                    modifier = Modifier.testTag("blur_level_slider")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Optical Lens Refraction Intensity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Navigation Lens Refraction",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(
                        text = String.format(Locale.US, "%.1f dp", navSettings.navRefractionAmountDp),
                        statusColor = IndigoPulse
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = navSettings.navRefractionAmountDp,
                    onValueChange = {
                        controller.repository.updateNavigationGlassSettings(
                            navSettings.copy(navRefractionAmountDp = it)
                        )
                    },
                    valueRange = 0f..76f,
                    accentColor = IndigoPulse,
                    modifier = Modifier.testTag("refraction_amount_slider")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Lens Curvature Depth Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Navigation Bezel Curvature",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(
                        text = String.format(Locale.US, "%.1f dp", navSettings.navRefractionHeightDp),
                        statusColor = MintSuccess
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = navSettings.navRefractionHeightDp,
                    onValueChange = {
                        controller.repository.updateNavigationGlassSettings(
                            navSettings.copy(navRefractionHeightDp = it)
                        )
                    },
                    valueRange = 4f..36f,
                    accentColor = MintSuccess,
                    modifier = Modifier.testTag("refraction_height_slider")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Chromatic Aberration Switch
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
                            text = "Prismatic Edge Dispersion",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Subtle RGB lens dispersion along curved dock edges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = navSettings.navChromaticAberration,
                        onCheckedChange = {
                            controller.repository.updateNavigationGlassSettings(
                                navSettings.copy(navChromaticAberration = it)
                            )
                        },
                        accentColor = CyanGlow
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reduced Motion Switch
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
                            text = "Reduced Motion Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Disables pulse scaling and liquid displacement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = settings.reducedMotion,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(reducedMotion = it))
                        },
                        accentColor = IndigoPulse
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LiquidGlowButton(
                    text = "Reset Navigation Dock Optics",
                    onClick = {
                        controller.repository.resetNavigationGlassSettingsToDefault()
                    },
                    accentColor = IndigoPulse,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_nav_glass_button")
                )
            }
        }

        // =========================================================================
        // FOLDER 4: ACOUSTIC DETECTION PARAMETERS
        // =========================================================================
        item {
            SettingsFolderCard(
                title = "Acoustic Knock Detection",
                subtitle = "Cooldown, confidence gate & stationary guard",
                icon = Icons.Default.Tune,
                badgeText = "${(settings.confidenceThreshold * 100).toInt()}% Gate",
                badgeColor = CyanGlow,
                isExpanded = isDetectionFolderOpen,
                onToggleExpand = { isDetectionFolderOpen = !isDetectionFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_detection")
            ) {
                // Cooldown Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Post-Action Cooldown",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(text = "${settings.cooldownMs} ms", statusColor = CyanGlow)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = settings.cooldownMs.toFloat(),
                    onValueChange = {
                        controller.repository.updateSettings(settings.copy(cooldownMs = it.toLong()))
                    },
                    valueRange = 300f..2000f,
                    accentColor = CyanGlow
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Confidence Threshold Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Minimum Confidence Gate",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidStatusPill(
                        text = "${(settings.confidenceThreshold * 100).toInt()}%",
                        statusColor = IndigoPulse
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSlider(
                    value = settings.confidenceThreshold,
                    onValueChange = {
                        controller.repository.updateSettings(settings.copy(confidenceThreshold = it))
                    },
                    valueRange = 0.4f..0.95f,
                    accentColor = IndigoPulse
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Gyro Stationary Filter Toggle
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
                            text = "Desk Stationary Guard",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Rejects impacts when the phone is held in hand or tilting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = settings.gyroStationaryFilter,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(gyroStationaryFilter = it))
                        },
                        accentColor = CyanGlow
                    )
                }
            }
        }

        // =========================================================================
        // FOLDER 5: TACTILE & AUDIO FEEDBACK
        // =========================================================================
        item {
            SettingsFolderCard(
                title = "Tactile & Audio Feedback",
                subtitle = "Haptic pulse and confirmation chime on knock triggers",
                icon = Icons.Default.Vibration,
                badgeText = if (settings.hapticFeedbackEnabled) "Haptics ON" else "Silent",
                badgeColor = if (settings.hapticFeedbackEnabled) MintSuccess else Color.Gray,
                isExpanded = isFeedbackFolderOpen,
                onToggleExpand = { isFeedbackFolderOpen = !isFeedbackFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_feedback")
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
                            text = "Haptic Click on Trigger",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Emits a crisp vibration pulse when a knock is recognized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(hapticFeedbackEnabled = it))
                        },
                        accentColor = CyanGlow
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                            text = "Sound Chirp on Trigger",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Plays an acoustic confirmation chime on macro activation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    LiquidGlassSwitch(
                        checked = settings.soundFeedbackEnabled,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(soundFeedbackEnabled = it))
                        },
                        accentColor = CyanGlow
                    )
                }
            }
        }

        // =========================================================================
        // FOLDER 6: PC CONTROL MODULE (LOCAL WI-FI)
        // =========================================================================
        item {
            SettingsFolderCard(
                title = "PC Control Module (Wi-Fi)",
                subtitle = "Pair with Windows PC for workspace, DAW & slide remote",
                icon = Icons.Default.Computer,
                badgeText = if (settings.isPcPaired) "Paired" else "Optional",
                badgeColor = if (settings.isPcPaired) MintSuccess else IndigoPulse,
                isExpanded = isPcFolderOpen,
                onToggleExpand = { isPcFolderOpen = !isPcFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("pc_pairing_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable PC Network Controller",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    LiquidGlassSwitch(
                        checked = settings.pcControlEnabled,
                        onCheckedChange = {
                            controller.repository.updateSettings(settings.copy(pcControlEnabled = it))
                        },
                        accentColor = CyanGlow
                    )
                }

                if (settings.pcControlEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pcHostInput,
                        onValueChange = { pcHostInput = it },
                        label = { Text("PC Local IP (e.g. 192.168.1.100)") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pcPortInput,
                            onValueChange = { pcPortInput = it },
                            label = { Text("Port") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pcTokenInput,
                            onValueChange = { pcTokenInput = it },
                            label = { Text("Auth Token") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlowButton(
                            text = if (isTestingPc) "Pinging..." else "Test Connection",
                            onClick = {
                                isTestingPc = true
                                val port = pcPortInput.toIntOrNull() ?: 8989
                                controller.repository.updateSettings(
                                    settings.copy(
                                        pcHost = pcHostInput.trim(),
                                        pcPort = port,
                                        pcAuthToken = pcTokenInput.trim()
                                    )
                                )
                                scope.launch {
                                    val testAction = com.example.knockdock.model.ActionDefinition(
                                        id = "test_pc_ping",
                                        name = "PC Ping",
                                        description = "",
                                        type = com.example.knockdock.model.ActionType.PC_COMMAND,
                                        parameters = mapOf(
                                            "host" to pcHostInput.trim(),
                                            "port" to port.toString(),
                                            "token" to pcTokenInput.trim(),
                                            "command" to "ping"
                                        )
                                    )
                                    val res = controller.actionExecutor.executeAction(testAction)
                                    pcStatusMessage = res.message
                                    controller.repository.updateSettings(settings.copy(isPcPaired = res.success))
                                    isTestingPc = false
                                }
                            },
                            enabled = !isTestingPc,
                            accentColor = IndigoPulse,
                            modifier = Modifier.weight(1f)
                        )

                        if (settings.isPcPaired) {
                            LiquidGlowButton(
                                text = "Disconnect",
                                onClick = {
                                    controller.repository.updateSettings(settings.copy(isPcPaired = false))
                                    pcStatusMessage = "Disconnected."
                                },
                                accentColor = RoseError,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (pcStatusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = pcStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.isPcPaired) MintSuccess else AmberAlert
                        )
                    }
                }
            }
        }

        // =========================================================================
        // FOLDER 7: SYSTEM DIAGNOSTICS & HARDWARE AUDIT
        // =========================================================================
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_diagnostics_card"),
                onClick = onNavigateToDiagnostics,
                accentColor = CyanGlow,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CyanGlow)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Diagnostics & Hardware Audit",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Sensor rates, shader pipeline, and execution logs",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    LiquidGlassIconButton(
                        onClick = onNavigateToDiagnostics,
                        size = 36.dp,
                        accentColor = CyanGlow
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Diagnostics",
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // ABOUT KNOCKDOCK — SEPARATE COLLAPSIBLE FOLDERS (ALL CLOSED BY DEFAULT)
        // =========================================================================
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "About KnockDock",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "App overview, developer & Apache 2.0 licence, GitHub links, sensor privacy, and release build details",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // FOLDER 8: ABOUT KNOCKDOCK OVERVIEW (APP ICON, TAGLINE, VERSION & PURPOSE)
        item {
            SettingsFolderCard(
                title = "About KnockDock",
                subtitle = "App icon, tagline, installed release version, and what KnockDock does",
                icon = Icons.Default.Info,
                badgeText = "v${appBuildInfo.versionName} • ${appBuildInfo.buildType}",
                badgeColor = MintSuccess,
                isExpanded = isAboutFolderOpen,
                onToggleExpand = { isAboutFolderOpen = !isAboutFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_about")
            ) {
                AboutAppOverviewFolderContent()
            }
        }

        // FOLDER 9: DEVELOPER & OPEN-SOURCE LICENCE
        item {
            SettingsFolderCard(
                title = "Developer & Licence",
                subtitle = "Developed by RSR-Projects under the Apache License 2.0",
                icon = Icons.Default.Person,
                badgeText = "Apache 2.0",
                badgeColor = IndigoPulse,
                isExpanded = isAboutDeveloperFolderOpen,
                onToggleExpand = { isAboutDeveloperFolderOpen = !isAboutDeveloperFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_about_developer")
            ) {
                AboutDeveloperFolderContent()
            }
        }

        // FOLDER 10: GITHUB REPOSITORY & INTERACTIVE LINKS
        item {
            SettingsFolderCard(
                title = "GitHub Repository & Links",
                subtitle = "Star on GitHub, View Source Code, Report a Bug, and View Releases",
                icon = Icons.Default.Code,
                badgeText = "GitHub",
                badgeColor = CyanGlow,
                isExpanded = isAboutGitHubFolderOpen,
                onToggleExpand = { isAboutGitHubFolderOpen = !isAboutGitHubFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_about_github")
            ) {
                AboutGitHubFolderContent()
            }
        }

        // FOLDER 11: PRIVACY & SENSOR DATA HANDLING
        item {
            SettingsFolderCard(
                title = "Privacy & Sensor Data",
                subtitle = "On-device accelerometer & gyroscope processing with zero analytics",
                icon = Icons.Default.PrivacyTip,
                badgeText = "On-Device",
                badgeColor = MintSuccess,
                isExpanded = isAboutPrivacyFolderOpen,
                onToggleExpand = { isAboutPrivacyFolderOpen = !isAboutPrivacyFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_about_privacy")
            ) {
                AboutPrivacyFolderContent()
            }
        }

        // FOLDER 12: THIRD-PARTY LICENCES & RELEASE BUILD INFORMATION
        item {
            SettingsFolderCard(
                title = "Licences & Release Build",
                subtitle = "Open-source third-party libraries and release build configuration",
                icon = Icons.Default.Gavel,
                badgeText = appBuildInfo.buildType,
                badgeColor = MintSuccess,
                isExpanded = isAboutLicensesFolderOpen,
                onToggleExpand = { isAboutLicensesFolderOpen = !isAboutLicensesFolderOpen },
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                modifier = Modifier.testTag("settings_folder_about_licenses")
            ) {
                AboutLicensesAndBuildFolderContent()
            }
        }

        item {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

/**
 * Reusable collapsible folder card that keeps Settings minimal, organized, and fast to navigate.
 */
@Composable
private fun SettingsFolderCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    badgeColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    visualEffectsQuality: String,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "folder_chevron_rotation"
    )

    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        visualEffectsQuality = visualEffectsQuality,
        reducedMotion = reducedMotion
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp)
            ) {
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
                            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        LiquidStatusPill(
                            text = badgeText,
                            statusColor = badgeColor
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse Folder" else "Open Folder",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    content()
                }
            }
        }
    }
}
