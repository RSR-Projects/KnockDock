package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.knockdock.sensor.SensorReplayer
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.screens.ActionStudioScreen
import com.example.knockdock.ui.screens.DiagnosticsScreen
import com.example.knockdock.ui.screens.GestureTrainerScreen
import com.example.knockdock.ui.screens.HomeScreen
import com.example.knockdock.ui.screens.ProfilesScreen
import com.example.knockdock.ui.screens.SensorLabScreen
import com.example.knockdock.ui.screens.SettingsScreen
import com.example.knockdock.ui.theme.LiquidGlassBackground
import com.example.knockdock.ui.theme.LiquidGlassFloatingButton
import com.example.knockdock.ui.theme.LocalBackdrop
import com.example.knockdock.ui.theme.LocalContentBackdrop
import com.example.knockdock.ui.theme.LocalLiquidGlassConfig
import com.example.knockdock.ui.theme.LocalNavigationGlassSettings
import com.example.knockdock.ui.theme.computeNavigationRenderParameters
import com.example.knockdock.ui.theme.drawCachedAuroraField
import com.example.knockdock.ui.theme.liquidTouchGesture
import com.example.knockdock.ui.theme.rememberLiquidTouchState
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanGlowBright
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.KnockDockTheme
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.SlateGlassDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

enum class AppDestination(val title: String, val icon: ImageVector, val tag: String) {
    HOME("Dock", Icons.Default.Home, "nav_dock"),
    LAB("Lab", Icons.Default.Science, "nav_lab"),
    TRAINER("Trainer", Icons.Default.Edit, "nav_trainer"),
    ACTIONS("Studio", Icons.Default.Build, "nav_actions"),
    PROFILES("Profiles", Icons.Default.Layers, "nav_profiles"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_settings"),
    DIAGNOSTICS("Diagnostics", Icons.Default.Settings, "nav_diagnostics")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = MonitoringController.getInstance(this)

        setContent {
            KnockDockTheme {
                val settings by controller.repository.settingsFlow.collectAsState()
                val isMonitoring by controller.isMonitoring.collectAsState()
                var currentScreen by remember { mutableStateOf(AppDestination.HOME) }
                val scope = rememberCoroutineScope()

                // Runtime permission request for notifications on Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasNotif = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasNotif) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Handle system back navigation
                BackHandler(enabled = currentScreen != AppDestination.HOME) {
                    currentScreen = AppDestination.HOME
                }

                val cardGlassSettings = remember(
                    settings.cardBlurEnabled,
                    settings.cardBlurRadiusDp,
                    settings.cardBackgroundOpacity,
                    settings.cardTintColorKey,
                    settings.cardTintIntensity,
                    settings.cardRefractionIntensity,
                    settings.cardRimHighlightIntensity,
                    settings.cardCornerRadiusDp,
                    settings.cardSurfaceContrast,
                    settings.cardLightweightMode
                ) {
                    settings.toCardGlassSettings()
                }

                val navigationGlassSettings = remember(
                    settings.navBlurEnabled,
                    settings.glassBlurRadius,
                    settings.glassRefractionAmount,
                    settings.glassRefractionHeight,
                    settings.glassChromaticAberration,
                    settings.navBackgroundOpacity,
                    settings.navRimHighlightIntensity
                ) {
                    settings.toNavigationGlassSettings()
                }

                LiquidGlassBackground(
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion,
                    blurRadiusDp = settings.glassBlurRadius,
                    refractionAmountDp = settings.glassRefractionAmount,
                    refractionHeightDp = settings.glassRefractionHeight,
                    chromaticAberration = settings.glassChromaticAberration,
                    liquidTouchPhysics = settings.liquidTouchPhysics,
                    cardGlassSettings = cardGlassSettings,
                    navigationGlassSettings = navigationGlassSettings
                ) {
                    val contentBackdrop = LocalContentBackdrop.current

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Layer 1: Aurora background + scrolling screen content captured into contentBackdrop
                        // so the Floating Bottom Navigation Bar and Floating Action Buttons genuinely blur
                        // and lens-refract the cards, text, and icons scrolling underneath them!
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (contentBackdrop != null) {
                                        Modifier.layerBackdrop(contentBackdrop)
                                    } else Modifier
                                )
                                .drawCachedAuroraField(settings.visualEffectsQuality)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                                label = "screen_transition"
                            ) { screen ->
                                when (screen) {
                                    AppDestination.HOME -> HomeScreen(
                                        controller = controller,
                                        onNavigateToTrainer = { currentScreen = AppDestination.TRAINER },
                                        onNavigateToLab = { currentScreen = AppDestination.LAB },
                                        onNavigateToProfiles = { currentScreen = AppDestination.PROFILES },
                                        onNavigateToActionStudio = { currentScreen = AppDestination.ACTIONS }
                                    )
                                    AppDestination.LAB -> SensorLabScreen(
                                        controller = controller
                                    )
                                    AppDestination.TRAINER -> GestureTrainerScreen(
                                        controller = controller,
                                        onGestureSaved = { currentScreen = AppDestination.PROFILES }
                                    )
                                    AppDestination.ACTIONS -> ActionStudioScreen(
                                        controller = controller
                                    )
                                    AppDestination.PROFILES -> ProfilesScreen(
                                        controller = controller
                                    )
                                    AppDestination.SETTINGS -> SettingsScreen(
                                        controller = controller,
                                        onNavigateToDiagnostics = { currentScreen = AppDestination.DIAGNOSTICS }
                                    )
                                    AppDestination.DIAGNOSTICS -> DiagnosticsScreen(
                                        controller = controller,
                                        onBack = { currentScreen = AppDestination.SETTINGS }
                                    )
                                }
                            }
                        }

                        // Layer 2: Floating iOS-style Liquid Glass Action Pill + Bottom Navigation Dock
                        // Sits outside contentBackdrop and samples contentBackdrop with real blur & lens refraction!
                        if (currentScreen != AppDestination.DIAGNOSTICS) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Contextual Floating Liquid Glass Quick Pill on secondary screens
                                if (currentScreen != AppDestination.HOME) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 560.dp)
                                            .padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LiquidGlassFloatingButton(
                                            text = if (isMonitoring) "Dock Active" else "Start Dock",
                                            onClick = { controller.toggleMonitoring() },
                                            accentColor = if (isMonitoring) MintSuccess else CyanGlow,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        LiquidGlassFloatingButton(
                                            text = "2x Knock",
                                            onClick = {
                                                scope.launch {
                                                    controller.simulateReplay(SensorReplayer.ReplayPreset.DOUBLE_KNOCK)
                                                }
                                            },
                                            accentColor = IndigoPulse,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Vibration,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }

                                GlassBottomNavigation(
                                    currentDestination = currentScreen,
                                    visualEffectsQuality = settings.visualEffectsQuality,
                                    reducedMotion = settings.reducedMotion,
                                    onSelectDestination = { currentScreen = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * iOS-style Floating Liquid Glass Bottom Navigation Bar powered by Kyant0's AndroidLiquidGlass.
 * Reads ONLY [LocalNavigationGlassSettings] — 100% independent of [LocalCardGlassSettings].
 * Samples `LocalContentBackdrop` so scrolling cards, text, and icons passing behind the floating dock
 * are visibly blurred and lens-refracted in real time.
 */
@Composable
private fun GlassBottomNavigation(
    currentDestination: AppDestination,
    visualEffectsQuality: String,
    reducedMotion: Boolean,
    onSelectDestination: (AppDestination) -> Unit
) {
    val navDestinations = remember {
        listOf(
            AppDestination.HOME,
            AppDestination.LAB,
            AppDestination.TRAINER,
            AppDestination.ACTIONS,
            AppDestination.PROFILES,
            AppDestination.SETTINGS
        )
    }

    val globalConfig = LocalLiquidGlassConfig.current
    val navSettings = LocalNavigationGlassSettings.current
    val navParams = remember(navSettings, visualEffectsQuality) {
        computeNavigationRenderParameters(navSettings, visualEffectsQuality)
    }

    val backdrop = LocalContentBackdrop.current ?: LocalBackdrop.current
    val capsuleShape = remember { RoundedCornerShape(percent = 50) }
    val density = LocalDensity.current
    val dockTouchState = rememberLiquidTouchState()
    val touchPhysicsEnabled = globalConfig.liquidTouchPhysics && !reducedMotion

    val blurPx = with(density) {
        if (!navParams.executesBlurPass) {
            0f
        } else {
            when (visualEffectsQuality) {
                "HIGH" -> (navParams.effectiveBlurRadiusDp.coerceIn(0f, 40f) * 1.15f).dp.toPx()
                "BALANCED" -> (navParams.effectiveBlurRadiusDp.coerceIn(0f, 40f) * 0.85f).dp.toPx()
                else -> 0f
            }
        }
    }
    val lensHeightPx = with(density) { navParams.effectiveRefractionHeightDp.dp.toPx() }
    val lensAmountPx = with(density) { navParams.effectiveRefractionAmountDp.dp.toPx() }

    val dockTintBrush = remember {
        Brush.horizontalGradient(
            listOf(
                CyanGlow.copy(alpha = 0.08f),
                IndigoPulse.copy(alpha = 0.06f),
                CyanGlow.copy(alpha = 0.08f)
            )
        )
    }
    val dockSpecularBrush = remember(navParams.effectiveRimHighlightIntensity) {
        val rim = navParams.effectiveRimHighlightIntensity
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = (0.16f + rim * 0.40f).coerceAtMost(0.75f)),
                CyanGlowBright.copy(alpha = (0.10f + rim * 0.28f).coerceAtMost(0.55f)),
                Color.Transparent
            )
        )
    }
    val dockBorderBrush = remember(navParams.effectiveRimHighlightIntensity) {
        val rim = navParams.effectiveRimHighlightIntensity
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (0.20f + rim * 0.42f).coerceAtMost(0.80f)),
                CyanGlow.copy(alpha = (0.12f + rim * 0.26f).coerceAtMost(0.60f)),
                Color.White.copy(alpha = 0.12f)
            )
        )
    }
    val selectedPillBgBrush = remember {
        Brush.verticalGradient(
            listOf(
                CyanGlow.copy(alpha = 0.30f),
                IndigoPulse.copy(alpha = 0.20f)
            )
        )
    }
    val selectedPillBorderBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.55f),
                CyanGlow.copy(alpha = 0.45f)
            )
        )
    }

    val dockBackdropModifier = if (backdrop != null && visualEffectsQuality != "LOW") {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { capsuleShape },
            effects = {
                vibrancy()
                if (navParams.executesBlurPass && blurPx > 0f) blur(blurPx)
                if (lensAmountPx > 1f) {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = true,
                        chromaticAberration = navParams.chromaticAberration
                    )
                }
            },
            layerBlock = if (touchPhysicsEnabled) {
                {
                    translationX = dockTouchState.offsetXValue
                    translationY = dockTouchState.offsetYValue
                    scaleX = dockTouchState.scaleXValue
                    scaleY = dockTouchState.scaleYValue
                }
            } else null,
            onDrawSurface = {
                // Translucent frosted surface so blurred scrolling cards & text shine through clearly
                drawRect(SlateGlassDark.copy(alpha = navParams.effectiveOpacity))
                drawRect(Color.White.copy(alpha = 0.04f))
                drawRect(dockTintBrush)
                drawLine(
                    brush = dockSpecularBrush,
                    start = Offset(size.width * 0.10f, 1.5f),
                    end = Offset(size.width * 0.90f, 1.5f),
                    strokeWidth = 2f
                )
            }
        )
    } else {
        Modifier
            .clip(capsuleShape)
            .background(SlateGlassDark.copy(alpha = 0.92f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .liquidTouchGesture(
                touchState = dockTouchState,
                enabled = touchPhysicsEnabled,
                maxDisplacementPx = with(density) { 26.dp.toPx() }
            )
            .then(dockBackdropModifier)
            .border(BorderStroke(1.dp, dockBorderBrush), capsuleShape)
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .testTag("floating_bottom_nav_bar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        navDestinations.forEach { dest ->
            val isSelected = currentDestination == dest
            val itemScale by animateFloatAsState(
                targetValue = if (!reducedMotion && isSelected) 1.05f else 1.0f,
                animationSpec = spring(
                    dampingRatio = 0.42f,
                    stiffness = 360f
                ),
                label = "nav_item_scale"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isSelected) CyanGlowBright else TextSecondary,
                animationSpec = tween(160),
                label = "nav_icon_color"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) TextPrimary else TextSecondary,
                animationSpec = tween(160),
                label = "nav_text_color"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = itemScale
                        scaleY = itemScale
                    }
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(capsuleShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(selectedPillBgBrush)
                                .border(BorderStroke(1.dp, selectedPillBorderBrush), capsuleShape)
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = CyanGlow),
                        role = Role.Tab,
                        onClick = { onSelectDestination(dest) }
                    )
                    .padding(vertical = 5.dp, horizontal = 1.dp)
                    .testTag(dest.tag),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.title,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dest.title,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        letterSpacing = (-0.1).sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
