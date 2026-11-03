package com.example.knockdock.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.knockdock.sensor.SensorReplayer
import com.example.knockdock.service.CompletedMacroEvent
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LocalBackdrop
import com.example.knockdock.ui.theme.LocalContentBackdrop
import com.example.knockdock.ui.theme.LocalLiquidGlassConfig
import com.example.knockdock.ui.theme.liquidSquish
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanGlowBright
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateGlassCard
import com.example.ui.theme.SlateGlassDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen 4-Edge Perimeter Neon Lighting Overlay (`KnockEdgeLightingOverlay`).
 * Illuminates all four edges + rounded screen corners of the device whenever a physical knock,
 * simulated knock, or gesture macro triggers. Never intercepts touch events.
 */
@Composable
fun KnockEdgeLightingOverlay(
    controller: MonitoringController,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val lastGesture by controller.lastDetectedGesture.collectAsState()
    val lastMacro by controller.lastCompletedMacro.collectAsState()

    val edgeIntensity = remember { Animatable(0f) }
    var primaryEdgeColor by remember { mutableStateOf(CyanGlow) }
    var secondaryEdgeColor by remember { mutableStateOf(IndigoPulse) }
    var tertiaryEdgeColor by remember { mutableStateOf(MintSuccess) }

    val infiniteTransition = rememberInfiniteTransition(label = "edge_sweep_rotation")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedMotion) 4000 else 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge_sweep_angle"
    )

    // React to every discrete knock impact from the sensor pipeline
    LaunchedEffect(controller) {
        controller.sensorHelper.impactEvents.collect { impact ->
            primaryEdgeColor = if (impact.peakAmplitude > 3.2f) CyanGlowBright else CyanGlow
            secondaryEdgeColor = IndigoPulse
            tertiaryEdgeColor = MintSuccess
            edgeIntensity.snapTo(0.82f)
            edgeIntensity.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }
    }

    // React to matched gesture events with stronger multi-color perimeter pulse
    LaunchedEffect(lastGesture?.timestampNanos) {
        val g = lastGesture ?: return@LaunchedEffect
        when (g.impactCount) {
            1 -> {
                primaryEdgeColor = CyanGlowBright
                secondaryEdgeColor = CyanGlow
                tertiaryEdgeColor = IndigoPulse
            }
            2 -> {
                primaryEdgeColor = IndigoPulse
                secondaryEdgeColor = CyanGlowBright
                tertiaryEdgeColor = MintSuccess
            }
            else -> {
                primaryEdgeColor = MintSuccess
                secondaryEdgeColor = CyanGlowBright
                tertiaryEdgeColor = IndigoPulse
            }
        }
        edgeIntensity.snapTo(1f)
        edgeIntensity.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
        )
    }

    // React to direct macro completions (e.g. from Instant Macro Launchpad or Focus Timer)
    LaunchedEffect(lastMacro?.id) {
        val m = lastMacro ?: return@LaunchedEffect
        primaryEdgeColor = if (m.success) MintSuccess else AmberAlert
        secondaryEdgeColor = CyanGlowBright
        tertiaryEdgeColor = IndigoPulse
        edgeIntensity.snapTo(1f)
        edgeIntensity.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val alpha = edgeIntensity.value
    if (alpha > 0.01f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cornerR = 36.dp.toPx()

            // 1. Soft atmospheric inward glow along all 4 screen edges
            val glowDepth = 28.dp.toPx() * (0.5f + 0.5f * alpha)
            // Top edge glow
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(primaryEdgeColor.copy(alpha = 0.42f * alpha), Color.Transparent),
                    startY = 0f,
                    endY = glowDepth
                ),
                topLeft = Offset(0f, 0f),
                size = Size(w, glowDepth)
            )
            // Bottom edge glow
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, secondaryEdgeColor.copy(alpha = 0.42f * alpha)),
                    startY = h - glowDepth,
                    endY = h
                ),
                topLeft = Offset(0f, h - glowDepth),
                size = Size(w, glowDepth)
            )
            // Left edge glow
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(tertiaryEdgeColor.copy(alpha = 0.38f * alpha), Color.Transparent),
                    startX = 0f,
                    endX = glowDepth
                ),
                topLeft = Offset(0f, 0f),
                size = Size(glowDepth, h)
            )
            // Right edge glow
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, primaryEdgeColor.copy(alpha = 0.38f * alpha)),
                    startX = w - glowDepth,
                    endX = w
                ),
                topLeft = Offset(w - glowDepth, 0f),
                size = Size(glowDepth, h)
            )

            // 2. Outer bloom contour stroke around all 4 edges & corners
            val sweepColors = listOf(
                primaryEdgeColor.copy(alpha = 0.90f * alpha),
                secondaryEdgeColor.copy(alpha = 0.85f * alpha),
                tertiaryEdgeColor.copy(alpha = 0.90f * alpha),
                Color.White.copy(alpha = 0.80f * alpha),
                primaryEdgeColor.copy(alpha = 0.90f * alpha)
            )

            rotate(degrees = sweepAngle, pivot = Offset(w / 2f, h / 2f)) {
                // Draw rotating sweep gradient brush evaluated on the screen perimeter
            }

            val perimeterBrush = Brush.sweepGradient(
                colors = sweepColors,
                center = Offset(w / 2f, h / 2f)
            )

            val outerStrokePx = (6.5f * alpha + 2f).dp.toPx()
            drawRoundRect(
                brush = perimeterBrush,
                topLeft = Offset(outerStrokePx * 0.35f, outerStrokePx * 0.35f),
                size = Size(w - outerStrokePx * 0.7f, h - outerStrokePx * 0.7f),
                cornerRadius = CornerRadius(cornerR, cornerR),
                style = Stroke(width = outerStrokePx, cap = StrokeCap.Round)
            )

            // 3. Crisp specular inner core line along the 4 edges
            val coreStrokePx = 2.2.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = 0.75f * alpha),
                topLeft = Offset(coreStrokePx, coreStrokePx),
                size = Size(w - coreStrokePx * 2f, h - coreStrokePx * 2f),
                cornerRadius = CornerRadius(cornerR, cornerR),
                style = Stroke(width = coreStrokePx)
            )
        }
    }
}

/**
 * Kyant0 Liquid Glass Dynamic Island HUD Banner (`LiquidMacroDoneHudBanner`).
 * Appears at the top of the screen when a knock is done / macro executes, using real Kyant0
 * `drawBackdrop` (`vibrancy()`, `blur()`, `lens()`, `Highlight.Default`) and animated
 * liquid glass lens refraction + elastic squish physics alongside the silent Android notification.
 */
@Composable
fun LiquidMacroDoneHudBanner(
    controller: MonitoringController,
    modifier: Modifier = Modifier
) {
    val lastMacro by controller.lastCompletedMacro.collectAsState()
    var visibleEvent by remember { mutableStateOf<CompletedMacroEvent?>(null) }
    var isVisible by remember { mutableStateOf(false) }

    // Kyant0 Liquid Glass animated lens & squish pulse when a knock is done
    val knockGlassPulse = remember { Animatable(0f) }

    LaunchedEffect(lastMacro?.id) {
        val ev = lastMacro ?: return@LaunchedEffect
        visibleEvent = ev
        isVisible = true
        knockGlassPulse.snapTo(1f)
        launch {
            knockGlassPulse.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.42f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        delay(3200L)
        if (visibleEvent?.id == ev.id) {
            isVisible = false
        }
    }

    val backdrop = LocalContentBackdrop.current ?: LocalBackdrop.current
    val config = LocalLiquidGlassConfig.current
    val density = LocalDensity.current
    val capsuleShape = remember { RoundedCornerShape(percent = 50) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 6.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isVisible && visibleEvent != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 40 },
                animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(180)) + scaleIn(
                initialScale = 0.82f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it - 40 },
                animationSpec = tween(220)
            ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.88f)
        ) {
            val ev = visibleEvent ?: return@AnimatedVisibility
            val accent = if (ev.success) MintSuccess else AmberAlert
            val pulse = knockGlassPulse.value

            val glassModifier = if (backdrop != null && config.visualEffectsQuality != "LOW") {
                val blurPx = with(density) { (18f + 6f * pulse).dp.toPx() }
                val lensHeightPx = with(density) { (16f + 12f * pulse).dp.toPx() }
                val lensAmountPx = with(density) { (36f + 28f * pulse).dp.toPx() }
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        vibrancy()
                        blur(blurPx)
                        lens(
                            refractionHeight = lensHeightPx,
                            refractionAmount = lensAmountPx,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default },
                    layerBlock = {
                        scaleX = 1f + 0.08f * pulse
                        scaleY = 1f - 0.05f * pulse
                    },
                    onDrawSurface = {
                        drawRect(SlateGlassDark.copy(alpha = 0.54f))
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(
                                    accent.copy(alpha = 0.28f + 0.14f * pulse),
                                                                        CyanGlow.copy(alpha = 0.18f + 0.12f * pulse),
                                    IndigoPulse.copy(alpha = 0.22f)
                                )
                            )
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
                    .widthIn(max = 460.dp)
                    .then(glassModifier)
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.65f),
                                    accent.copy(alpha = 0.75f),
                                    CyanGlow.copy(alpha = 0.55f)
                                )
                            )
                        ),
                        capsuleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("liquid_macro_done_hud"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.22f))
                        .border(1.dp, accent.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Work Done",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "KNOCK DETECTED • ACTION ACTIVATED",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
                        fontFamily = FontFamily.Monospace,
                        color = CyanGlowBright,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ev.actionName,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${ev.gestureName} • ${ev.resultMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CyanGlow.copy(alpha = 0.16f))
                        .border(1.dp, CyanGlow.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "SILENT",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontFamily = FontFamily.Monospace,
                        color = CyanGlowBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Interactive Kyant0 Liquid Glass Onboarding Tutorial & Live Permission Center Modal.
 * Opens on first launch (or when triggered from the Home / Settings header) to explain KnockDock,
 * let the user test 4-Edge Lighting & Squish physics, and grant permissions with one tap.
 */
@Composable
fun LiquidOnboardingTutorialOverlay(
    visible: Boolean,
    controller: MonitoringController,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = LocalContentBackdrop.current ?: LocalBackdrop.current
    val config = LocalLiquidGlassConfig.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by remember { mutableIntStateOf(0) }

    // Live permission states refreshed on resume and after launcher returns
    var hasNotifications by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasOverlay by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasBatteryExemption by remember { mutableStateOf(checkBatteryExemption(context)) }
    var hasDndAccess by remember { mutableStateOf(checkDndAccess(context)) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted || checkNotificationPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifications = checkNotificationPermission(context)
                hasOverlay = checkOverlayPermission(context)
                hasBatteryExemption = checkBatteryExemption(context)
                hasDndAccess = checkDndAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val modalShape = remember { RoundedCornerShape(28.dp) }
    val modalGlassModifier = if (backdrop != null && config.visualEffectsQuality != "LOW") {
        val blurPx = with(density) { 24.dp.toPx() }
        val lensHeightPx = with(density) { 18.dp.toPx() }
        val lensAmountPx = with(density) { 42.dp.toPx() }
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { modalShape },
            effects = {
                vibrancy()
                blur(blurPx)
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = config.chromaticAberration
                )
            },
            highlight = { Highlight.Default },
            onDrawSurface = {
                drawRect(SlateGlassDark.copy(alpha = 0.76f))
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            CyanGlow.copy(alpha = 0.16f),
                            IndigoPulse.copy(alpha = 0.12f),
                            SlateGlassCard.copy(alpha = 0.45f)
                        )
                    )
                )
            }
        )
    } else {
        Modifier
            .clip(modalShape)
            .background(SlateGlassDark.copy(alpha = 0.95f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Consume background taps without closing accidentally
            )
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag("liquid_onboarding_tutorial_modal"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .then(modalGlassModifier)
                .border(
                    BorderStroke(
                        1.4.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.65f),
                                CyanGlow.copy(alpha = 0.60f),
                                IndigoPulse.copy(alpha = 0.40f)
                            )
                        )
                    ),
                    modalShape
                )
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar: Step indicator pills + close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val stepTitles = listOf("1 • How It Works", "2 • Permissions", "3 • Pro Features")
                    stepTitles.forEachIndexed { idx, label ->
                        val active = currentStep == idx
                        Box(
                            modifier = Modifier
                                .liquidSquish(maxDisplacementPx = 14f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (active) CyanGlow.copy(alpha = 0.25f)
                                    else SlateGlassDark.copy(alpha = 0.55f)
                                )
                                .border(
                                    1.dp,
                                    if (active) CyanGlow else GlassBorderTop,
                                    RoundedCornerShape(50)
                                )
                                .clickable { currentStep = idx }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (active) Color.White else TextSecondary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                LiquidGlassIconButton(
                    onClick = onDismiss,
                    size = 32.dp,
                    accentColor = CyanGlow
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Guide",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            when (currentStep) {
                0 -> {
                    // STEP 1: Welcome & Interactive Live Knock Demo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(CyanGlow.copy(alpha = 0.35f), IndigoPulse.copy(alpha = 0.35f))
                                    )
                                )
                                .border(1.5.dp, CyanGlow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = CyanGlowBright,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Welcome to KnockDock",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Turn physical desk knocks into instant actions with Kyant0 Liquid Glass",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    TutorialFeatureHighlightRow(
                        icon = Icons.Default.Tune,
                        title = "Acoustic Desk Sensing & Stationary Guard",
                        subtitle = "Place your phone on your desk. Knocking the surface sends crisp shockwaves through the accelerometer while the Gyroscope Filter rejects accidental hand movement.",
                        accentColor = CyanGlow
                    )

                    TutorialFeatureHighlightRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "4-Edge Neon Lighting & Silent Work-Done HUD",
                        subtitle = "Every knock lights up all 4 edges of your screen with refractive neon waves and posts a silent 'Work Done' confirmation when your macro finishes.",
                        accentColor = IndigoPulse
                    )

                    TutorialFeatureHighlightRow(
                        icon = Icons.Default.Layers,
                        title = "Interactive Squishy Liquid Glass Controls",
                        subtitle = "Every button, card, and navigation droplet responds to your touch with elastic rubber-band stretch, squish, and optical lens refraction.",
                        accentColor = MintSuccess
                    )

                    // Interactive demo button right inside Step 1!
                    LiquidGlowButton(
                        text = "Tap to Test 4-Edge Lighting & Squish!",
                        onClick = {
                            scope.launch {
                                controller.simulateReplay(SensorReplayer.ReplayPreset.DOUBLE_KNOCK)
                            }
                        },
                        accentColor = CyanGlow,
                        useContentBackdrop = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                1 -> {
                    // STEP 2: Interactive Permission Center
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MintSuccess.copy(alpha = 0.22f))
                                .border(1.5.dp, MintSuccess, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MintSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Permission Setup",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant these permissions so background app launching, silent notifications, and continuous desk listening work seamlessly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    PermissionSetupItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Silent 'Work Done' & Service Notifications",
                        description = "Required for background desk monitoring and silent status-bar macro confirmations.",
                        isGranted = hasNotifications,
                        buttonText = if (hasNotifications) "Granted" else "Allow",
                        onGrantClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppNotificationSettings(context)
                            }
                        }
                    )

                    PermissionSetupItem(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = "Display Over Other Apps (Background Launch)",
                        description = "Allows KnockDock to open apps, URLs, and camera macros even when KnockDock is closed or in the background.",
                        isGranted = hasOverlay,
                        buttonText = if (hasOverlay) "Granted" else "Enable",
                        onGrantClick = { openOverlaySettings(context) }
                    )

                    PermissionSetupItem(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Unrestricted Battery Usage",
                        description = "Prevents Android from putting the acoustic desk sensor listener to sleep when your screen is off.",
                        isGranted = hasBatteryExemption,
                        buttonText = if (hasBatteryExemption) "Granted" else "Optimize",
                        onGrantClick = { requestBatteryExemption(context) }
                    )

                    PermissionSetupItem(
                        icon = Icons.Default.DoNotDisturbOn,
                        title = "Do Not Disturb / Ringer Control",
                        description = "Optional: Needed only if you bind a knock gesture to toggle Silent / Vibrate / DND mode.",
                        isGranted = hasDndAccess,
                        buttonText = if (hasDndAccess) "Granted" else "Grant",
                        onGrantClick = { openDndPolicySettings(context) }
                    )
                }

                else -> {
                    // STEP 3: Rare Pro Features
                    Text(
                        text = "Rare Pro Features Included",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    TutorialFeatureHighlightRow(
                        icon = Icons.Default.Tune,
                        title = "Acoustic Desk Surface Material Calibrator",
                        subtitle = "Located on the Home screen's Knock Pad Deck — calibrate KnockDock in one tap for Solid Wood, Tempered Glass, Metal, Marble, or a Soft Desk Mat.",
                        accentColor = CyanGlow
                    )

                    TutorialFeatureHighlightRow(
                        icon = Icons.Default.Timer,
                        title = "Knock-Controlled Focus / Pomodoro Capsule",
                        subtitle = "Start a 25m Deep Focus sprint, 15m Quick Sprint, or 5m Break right from your Home Dock. Sends a silent notification and 4-edge neon pulse when done!",
                        accentColor = MintSuccess
                    )

                    TutorialFeatureHighlightRow(
                        icon = Icons.Default.Vibration,
                        title = "Live Rhythm Pad & Instant Macro Launchpad",
                        subtitle = "Tap any custom cadence on the Liquid Knock Pad to see real-time shockwaves and millisecond intervals, or tap any Launchpad chip to fire macros immediately.",
                        accentColor = IndigoPulse
                    )
                }
            }

            // Bottom Navigation Buttons inside Tutorial Modal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    LiquidGlowButton(
                        text = "Previous",
                        onClick = { currentStep = (currentStep - 1).coerceAtLeast(0) },
                        accentColor = IndigoPulse,
                        useContentBackdrop = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LiquidGlowButton(
                        text = "Skip Guide",
                        onClick = onDismiss,
                        accentColor = IndigoPulse,
                        useContentBackdrop = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (currentStep < 2) {
                    LiquidGlowButton(
                        text = "Next Step",
                        onClick = { currentStep = (currentStep + 1).coerceAtMost(2) },
                        accentColor = CyanGlow,
                        useContentBackdrop = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LiquidGlowButton(
                        text = "Start Using KnockDock",
                        onClick = onDismiss,
                        accentColor = MintSuccess,
                        useContentBackdrop = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialFeatureHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidSquish(maxDisplacementPx = 14f)
            .clip(RoundedCornerShape(16.dp))
            .background(SlateGlassDark.copy(alpha = 0.58f))
            .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f))
                .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PermissionSetupItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onGrantClick: () -> Unit
) {
    val statusColor = if (isGranted) MintSuccess else CyanGlow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidSquish(maxDisplacementPx = 14f)
            .clip(RoundedCornerShape(16.dp))
            .background(SlateGlassDark.copy(alpha = 0.62f))
            .border(1.dp, statusColor.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.18f))
                .border(1.dp, statusColor.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        LiquidGlowButton(
            text = buttonText,
            onClick = onGrantClick,
            accentColor = statusColor,
            useContentBackdrop = true
        )
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun checkBatteryExemption(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }
}

private fun checkDndAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.isNotificationPolicyAccessGranted == true
    } else {
        true
    }
}

private fun openOverlaySettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
    } catch (_: Exception) {
    }
}

private fun requestBatteryExemption(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
    } catch (_: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
        }
    }
}

private fun openDndPolicySettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (_: Exception) {
    }
}

private fun openAppNotificationSettings(context: Context) {
    try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
