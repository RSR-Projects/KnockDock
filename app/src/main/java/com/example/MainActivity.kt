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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.knockdock.sensor.SensorReplayer
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.components.KnockEdgeLightingOverlay
import com.example.knockdock.ui.components.LiquidMacroDoneHudBanner
import com.example.knockdock.ui.components.LiquidOnboardingTutorialOverlay
import com.example.knockdock.ui.screens.ActionStudioScreen
import com.example.knockdock.ui.screens.DiagnosticsScreen
import com.example.knockdock.ui.screens.GestureTrainerScreen
import com.example.knockdock.ui.screens.HomeScreen
import com.example.knockdock.ui.screens.ProfilesScreen
import com.example.knockdock.ui.screens.SensorLabScreen
import com.example.knockdock.ui.screens.SettingsScreen
import com.example.knockdock.ui.theme.DampedDragAnimation
import com.example.knockdock.ui.theme.InteractiveHighlight
import com.example.knockdock.ui.theme.LiquidGlassBackground
import com.example.knockdock.ui.theme.LiquidGlassFloatingButton
import com.example.knockdock.ui.theme.LocalBackdrop
import com.example.knockdock.ui.theme.LocalContentBackdrop
import com.example.knockdock.ui.theme.LocalLiquidGlassConfig
import com.example.knockdock.ui.theme.LocalNavigationGlassSettings
import com.example.knockdock.ui.theme.computeNavigationRenderParameters
import com.example.knockdock.ui.theme.drawCachedAuroraField
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
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
        val onboardingPrefs = getSharedPreferences("knockdock_settings_prefs", MODE_PRIVATE)

        setContent {
            KnockDockTheme {
                val settings by controller.repository.settingsFlow.collectAsState()
                val isMonitoring by controller.isMonitoring.collectAsState()
                var currentScreen by remember { mutableStateOf(AppDestination.HOME) }
                var showOnboardingTutorial by remember {
                    mutableStateOf(!onboardingPrefs.getBoolean("has_seen_onboarding_v2", false))
                }
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
                            // Ensure no composable inside the recorded screen tree ever attempts to draw
                            // contentBackdrop into itself while contentBackdrop's RenderNode is recording.
                            androidx.compose.runtime.CompositionLocalProvider(
                                LocalContentBackdrop provides null
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
                                            onNavigateToActionStudio = { currentScreen = AppDestination.ACTIONS },
                                            onOpenTutorial = { showOnboardingTutorial = true }
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

                        // Layer 3: Full-Screen 4-Edge Perimeter Neon Knock Lighting
                        KnockEdgeLightingOverlay(
                            controller = controller,
                            reducedMotion = settings.reducedMotion,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Layer 4: Kyant0 Liquid Glass Dynamic Island Action Done HUD Banner
                        LiquidMacroDoneHudBanner(
                            controller = controller,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        // Layer 5: First-Launch Interactive Kyant0 Liquid Glass Tutorial & Permission Wizard
                        LiquidOnboardingTutorialOverlay(
                            visible = showOnboardingTutorial,
                            controller = controller,
                            onDismiss = {
                                showOnboardingTutorial = false
                                onboardingPrefs.edit().putBoolean("has_seen_onboarding_v2", true).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Apple iOS 26 / Kyant0 `AndroidLiquidGlass` (`LiquidBottomTabs.kt`) Floating Bottom Navigation Bar.
 *
 * Implements Kyant0's signature squishy movement effect EXCLUSIVELY in the navigation bar:
 * 1. [DampedDragAnimation]: Multi-spring physics controller driving the sliding Liquid Glass active tab
 *    indicator droplet (`value`, `velocity`, `pressProgress`, `scaleX`, `scaleY`). Both tapping a tab and
 *    dragging horizontally across the bar deform the indicator droplet dynamically using Kyant0's velocity math:
 *    `scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.22f, 0.22f)`
 *    `scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.20f, 0.20f)`
 * 2. [InteractiveHighlight]: Touch-following specular radial glow + hyperbolic tangent (`tanh`) rubber-band
 *    damping on the outer Liquid Glass navigation bar container.
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

    val selectedIndex = navDestinations.indexOf(currentDestination).coerceAtLeast(0)
    val animationScope = rememberCoroutineScope()
    val globalConfig = LocalLiquidGlassConfig.current
    val navSettings = LocalNavigationGlassSettings.current
    val navParams = remember(navSettings, visualEffectsQuality) {
        computeNavigationRenderParameters(navSettings, visualEffectsQuality)
    }

    val backdrop = LocalContentBackdrop.current ?: LocalBackdrop.current
    val capsuleShape = remember { RoundedCornerShape(percent = 50) }
    val density = LocalDensity.current
    val touchPhysicsEnabled = globalConfig.liquidTouchPhysics && !reducedMotion

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(navDestinations.size - 1).toFloat(),
            pressedScaleX = 1.18f,
            pressedScaleY = 1.14f
        )
    }
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    // Animate indicator droplet with smooth velocity squish when selected tab changes externally (e.g. back press)
    LaunchedEffect(selectedIndex) {
        if (!dampedDragAnimation.isDragging) {
            dampedDragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val horizontalPadPx = with(density) { 4.dp.toPx() }
    val innerWidthPx = (barSize.width.toFloat() - horizontalPadPx * 2f).coerceAtLeast(1f)
    val tabWidthPx = innerWidthPx / navDestinations.size.toFloat()
    val tabWidthDp = with(density) { tabWidthPx.toDp() }

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
                CyanGlow.copy(alpha = 0.34f),
                IndigoPulse.copy(alpha = 0.24f)
            )
        )
    }
    val selectedPillBorderBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.72f),
                CyanGlowBright.copy(alpha = 0.55f),
                IndigoPulse.copy(alpha = 0.35f)
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
                    val transform = interactiveHighlight.computeBarTransform(size.width, size.height)
                    translationX = transform.translationX
                    translationY = transform.translationY
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                }
            } else null,
            onDrawSurface = {
                drawRect(SlateGlassDark.copy(alpha = navParams.effectiveOpacity))
                drawRect(Color.White.copy(alpha = 0.04f))
                drawRect(dockTintBrush)
                // InteractiveHighlight touch-following radial glow on the navigation bar surface
                val touchPos = interactiveHighlight.touchPosition
                val pressProg = interactiveHighlight.pressProgress
                if (touchPos != null && pressProg > 0.01f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * pressProg),
                                CyanGlowBright.copy(alpha = 0.14f * pressProg),
                                Color.Transparent
                            ),
                            center = touchPos,
                            radius = (size.width * 0.38f).coerceAtLeast(60f)
                        )
                    )
                }
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
            .graphicsLayer {
                if (touchPhysicsEnabled) {
                    val transform = interactiveHighlight.computeBarTransform(
                        barSize.width.toFloat(),
                        barSize.height.toFloat()
                    )
                    translationX = transform.translationX
                    translationY = transform.translationY
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                }
            }
            .clip(capsuleShape)
            .background(SlateGlassDark.copy(alpha = 0.92f))
    }

    // Indicator droplet lens parameters that swell when pressed or dragged
    val pillBaseBlurPx = with(density) { 10.dp.toPx() }
    val pillLensHeightMinPx = with(density) { 10.dp.toPx() }
    val pillLensHeightMaxPx = with(density) { 22.dp.toPx() }
    val pillLensAmountMinPx = with(density) { 18.dp.toPx() }
    val pillLensAmountMaxPx = with(density) { 48.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .onSizeChanged { barSize = it }
            .pointerInput(touchPhysicsEnabled, tabWidthPx) {
                if (!touchPhysicsEnabled) return@pointerInput
                awaitPointerEventScope {
                    var startPos: Offset? = null
                    var lastPos: Offset? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            lastPos = change.position
                            if (startPos == null) {
                                startPos = change.position
                                interactiveHighlight.onDown(change.position)
                                dampedDragAnimation.press()
                                if (tabWidthPx > 1f) {
                                    val rawTab = (change.position.x - horizontalPadPx) / tabWidthPx - 0.5f
                                    val clampedTab = rawTab.coerceIn(0f, (navDestinations.size - 1).toFloat())
                                    dampedDragAnimation.updateValue(clampedTab)
                                }
                            } else {
                                interactiveHighlight.onMove(change.position, startPos)
                                val dx = abs(change.position.x - startPos.x)
                                if (dx > 6f) {
                                    change.consume()
                                }
                                if (tabWidthPx > 1f) {
                                    val rawTab = (change.position.x - horizontalPadPx) / tabWidthPx - 0.5f
                                    val clampedTab = rawTab.coerceIn(0f, (navDestinations.size - 1).toFloat())
                                    dampedDragAnimation.updateValue(clampedTab)
                                }
                            }
                        } else if (startPos != null) {
                            val releasePos = change?.position ?: lastPos ?: startPos
                            startPos = null
                            lastPos = null
                            interactiveHighlight.onRelease()
                            dampedDragAnimation.release()
                            if (tabWidthPx > 1f) {
                                val rawReleaseTab = (releasePos.x - horizontalPadPx) / tabWidthPx - 0.5f
                                val snappedIdx = rawReleaseTab
                                    .roundToInt()
                                    .coerceIn(0, navDestinations.lastIndex)
                                dampedDragAnimation.animateToValue(snappedIdx.toFloat())
                                val snappedDest = navDestinations[snappedIdx]
                                if (snappedDest != currentDestination) {
                                    onSelectDestination(snappedDest)
                                }
                            } else {
                                dampedDragAnimation.animateToValue(selectedIndex.toFloat())
                            }
                        }
                    }
                }
            }
            .then(dockBackdropModifier)
            .border(BorderStroke(1.dp, dockBorderBrush), capsuleShape)
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .testTag("floating_bottom_nav_bar"),
        contentAlignment = Alignment.CenterStart
    ) {
        // Layer 1: Kyant0 `LiquidBottomTabs` Sliding & Squishy Liquid Glass Indicator Droplet
        if (barSize.width > 0) {
            val borderStrokePx = with(density) { 1.2.dp.toPx() }
            val pillModifier = if (backdrop != null && visualEffectsQuality != "LOW") {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        val p = dampedDragAnimation.pressProgress
                        vibrancy()
                        if (navParams.executesBlurPass) {
                            blur(pillBaseBlurPx * (1f - 0.35f * p))
                        }
                        val curHeight = pillLensHeightMinPx + (pillLensHeightMaxPx - pillLensHeightMinPx) * p
                        val curAmount = pillLensAmountMinPx + (pillLensAmountMaxPx - pillLensAmountMinPx) * p
                        lens(
                            refractionHeight = curHeight,
                            refractionAmount = curAmount,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default },
                    layerBlock = {
                        translationX = tabWidthPx * dampedDragAnimation.value
                        if (touchPhysicsEnabled) {
                            // Kyant0 LiquidBottomTabs velocity-driven squishy deformation + jelly rebound
                            val speed = (abs(dampedDragAnimation.velocity) / 11f).coerceIn(0f, 1f)
                            val wobble = (dampedDragAnimation.jellyVelocity / 12f).coerceIn(-0.22f, 0.22f)
                            val vStretch = (speed * 0.22f + wobble * 0.55f).coerceIn(-0.18f, 0.28f)
                            val vSquash = (speed * 0.18f + wobble * 0.45f).coerceIn(-0.18f, 0.24f)
                            scaleX = dampedDragAnimation.scaleX / (1f - vStretch)
                            scaleY = dampedDragAnimation.scaleY * (1f - vSquash)
                        }
                    },
                    onDrawSurface = {
                        val p = dampedDragAnimation.pressProgress
                        drawRect(selectedPillBgBrush)
                        drawRect(Color.White.copy(alpha = 0.08f + 0.14f * p))
                        val radius = size.height * 0.5f
                        drawRoundRect(
                            brush = selectedPillBorderBrush,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderStrokePx)
                        )
                    }
                )
            } else {
                Modifier
                    .graphicsLayer {
                        translationX = tabWidthPx * dampedDragAnimation.value
                        if (touchPhysicsEnabled) {
                            val speed = (abs(dampedDragAnimation.velocity) / 11f).coerceIn(0f, 1f)
                            val wobble = (dampedDragAnimation.jellyVelocity / 12f).coerceIn(-0.22f, 0.22f)
                            val vStretch = (speed * 0.22f + wobble * 0.55f).coerceIn(-0.18f, 0.28f)
                            val vSquash = (speed * 0.18f + wobble * 0.45f).coerceIn(-0.18f, 0.24f)
                            scaleX = dampedDragAnimation.scaleX / (1f - vStretch)
                            scaleY = dampedDragAnimation.scaleY * (1f - vSquash)
                        }
                    }
                    .clip(capsuleShape)
                    .background(selectedPillBgBrush)
                    .border(BorderStroke(1.dp, selectedPillBorderBrush), capsuleShape)
            }

            Box(
                modifier = Modifier
                    .width(tabWidthDp)
                    .height(48.dp)
                    .then(pillModifier)
            )
        }

        // Layer 2: Navigation Tab Icons & Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navDestinations.forEachIndexed { index, dest ->
                val isSelected = currentDestination == dest
                // Proximity to the live sliding droplet position for smooth highlight & lift
                val distanceToDroplet = abs(dampedDragAnimation.value - index.toFloat()).coerceIn(0f, 1f)
                val proximity = 1f - distanceToDroplet
                val isHighlighted = if (dampedDragAnimation.isDragging) {
                    proximity > 0.5f
                } else {
                    isSelected || proximity > 0.5f
                }
                val itemScale = if (!reducedMotion) {
                    1f + 0.08f * proximity + 0.04f * proximity * dampedDragAnimation.pressProgress
                } else {
                    1f
                }

                val iconColor by animateColorAsState(
                    targetValue = if (isHighlighted) CyanGlowBright else TextSecondary,
                    animationSpec = tween(140),
                    label = "nav_icon_color"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isHighlighted) TextPrimary else TextSecondary,
                    animationSpec = tween(140),
                    label = "nav_text_color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                            translationY = -2.2f * proximity * dampedDragAnimation.pressProgress
                        }
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(capsuleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                dampedDragAnimation.animateToValue(index.toFloat())
                                if (currentDestination != dest) {
                                    onSelectDestination(dest)
                                }
                            }
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
}
