package com.example.knockdock.ui.components

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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.knockdock.model.GestureDetectionEvent
import com.example.knockdock.model.ImpactEvent
import com.example.knockdock.model.ResolvedMapping
import com.example.knockdock.model.TelemetrySnapshot
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LocalBackdrop
import com.example.knockdock.ui.theme.LocalLiquidGlassConfig
import com.example.knockdock.ui.theme.liquidSquish
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanGlowBright
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Represents an expanding refractive shockwave ripple + radial particles spawned by a physical or touch knock.
 */
private data class ShockwaveRipple(
    val id: Long,
    val centerNormalizedX: Float,
    val centerNormalizedY: Float,
    val startTimeMs: Long,
    val primaryColor: Color,
    val secondaryColor: Color,
    val peakStrength: Float = 1.0f,
    val knockIndex: Int = 1
)

/**
 * Acoustic Desk Surface Material Calibration Profile.
 * Adjusts base vibration threshold, sensitivity, and post-trigger cooldown for specific surface acoustics.
 */
private data class DeskSurfaceMaterialPreset(
    val id: String,
    val label: String,
    val subtitle: String,
    val baseThreshold: Float,
    val sensitivity: Float,
    val cooldownMs: Long,
    val accentColor: Color
)

private val SURFACE_MATERIAL_PRESETS = listOf(
    DeskSurfaceMaterialPreset(
        id = "WOOD",
        label = "Solid Wood",
        subtitle = "Warm resonance • 2.1 m/s²",
        baseThreshold = 2.1f,
        sensitivity = 0.68f,
        cooldownMs = 720L,
        accentColor = CyanGlow
    ),
    DeskSurfaceMaterialPreset(
        id = "GLASS",
        label = "Tempered Glass",
        subtitle = "Crisp high-Q • 1.8 m/s²",
        baseThreshold = 1.8f,
        sensitivity = 0.75f,
        cooldownMs = 680L,
        accentColor = CyanGlowBright
    ),
    DeskSurfaceMaterialPreset(
        id = "METAL",
        label = "Metal / Alloy",
        subtitle = "Long ring-down • 2.5 m/s²",
        baseThreshold = 2.5f,
        sensitivity = 0.58f,
        cooldownMs = 850L,
        accentColor = IndigoPulse
    ),
    DeskSurfaceMaterialPreset(
        id = "MARBLE",
        label = "Marble / Stone",
        subtitle = "Dense & rigid • 1.9 m/s²",
        baseThreshold = 1.9f,
        sensitivity = 0.72f,
        cooldownMs = 700L,
        accentColor = MintSuccess
    ),
    DeskSurfaceMaterialPreset(
        id = "MAT",
        label = "Desk Mat / Pad",
        subtitle = "Damped wave • 1.5 m/s²",
        baseThreshold = 1.5f,
        sensitivity = 0.84f,
        cooldownMs = 650L,
        accentColor = AmberAlert
    )
)

@Composable
private fun ResonanceMiniPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * 360° Radial Acoustic Equalizer & Surface Horizon Ring rendered around the Hero Liquid Glass Orb.
 * Reacts in real time to physical desk vibrations, simulated knocks, and gesture detections.
 */
@Composable
fun HeroAcousticSpectrumRing(
    telemetry: TelemetrySnapshot,
    waveformBuffer: FloatArray,
    bufferWriteIndex: Int,
    isMonitoring: Boolean,
    lastGesture: GestureDetectionEvent?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum_rotation")
    val phaseAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedMotion) 24000 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_angle"
    )

    val gestureBurstAnim = remember { Animatable(0f) }
    LaunchedEffect(lastGesture?.timestampNanos) {
        if (lastGesture != null) {
            gestureBurstAnim.snapTo(1f)
            gestureBurstAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = min(cx, cy) * 0.72f
        val barCount = 48
        val burst = gestureBurstAnim.value
        val liveImpulse = (telemetry.filteredImpulse / 4.5f).coerceIn(0f, 1.4f)

        // Subtle outer orbital guide ring
        drawCircle(
            color = if (isMonitoring) CyanGlow.copy(alpha = 0.14f + 0.18f * burst)
            else Color.White.copy(alpha = 0.06f),
            radius = baseRadius * 1.14f,
            center = Offset(cx, cy),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), phaseAngle * 12f)
            )
        )

        // Expanding gesture recognition shockwave halo
        if (burst > 0.01f) {
            val shockRadius = baseRadius * (0.85f + (1f - burst) * 0.48f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        MintSuccess.copy(alpha = burst * 0.42f),
                        CyanGlow.copy(alpha = burst * 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = shockRadius * 1.15f
                ),
                radius = shockRadius * 1.15f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = MintSuccess.copy(alpha = burst * 0.65f),
                radius = shockRadius,
                center = Offset(cx, cy),
                style = Stroke(width = (1.5f + burst * 2.5f).dp.toPx())
            )
        }

        // 48 Radial Spectrum Bars driven by the live ring buffer & harmonic resonance
        val bufSize = waveformBuffer.size
        for (i in 0 until barCount) {
            val angle = (i.toFloat() / barCount) * (2f * PI.toFloat()) - (PI.toFloat() / 2f)
            val sampleIdx = if (bufSize > 0) {
                (bufferWriteIndex - 1 - (i * 2) + bufSize * 4) % bufSize
            } else 0
            val rawSample = if (bufSize > 0) waveformBuffer[sampleIdx] else 0f
            val normalizedSample = (rawSample / 5.0f).coerceIn(0f, 1f)

            val ambientWave = if (isMonitoring && !reducedMotion) {
                0.08f + 0.06f * sin(angle * 4f + phaseAngle * 2f)
            } else {
                0.05f
            }

            val barEnergy = (ambientWave + normalizedSample * 0.85f + liveImpulse * 0.35f + burst * 0.45f)
                .coerceIn(0.05f, 1.0f)

            val innerR = baseRadius * 0.96f
            val maxBarLen = baseRadius * 0.28f
            val outerR = innerR + maxBarLen * barEnergy

            val cosA = cos(angle)
            val sinA = sin(angle)

            val startOffset = Offset(cx + cosA * innerR, cy + sinA * innerR)
            val endOffset = Offset(cx + cosA * outerR, cy + sinA * outerR)

            val barColor = when {
                burst > 0.2f -> MintSuccess
                barEnergy > 0.55f -> IndigoPulse
                isMonitoring -> CyanGlow
                else -> IndigoPulse.copy(alpha = 0.55f)
            }

            drawLine(
                color = barColor.copy(alpha = (0.28f + barEnergy * 0.68f).coerceIn(0f, 1f)),
                start = startOffset,
                end = endOffset,
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 2D Surface Spirit-Level / Desk Horizon Dot inside the orbital ring
        val tiltX = (telemetry.accelX / 9.8f).coerceIn(-1f, 1f)
        val tiltY = (telemetry.accelY / 9.8f).coerceIn(-1f, 1f)
        val maxTiltOffset = baseRadius * 0.24f
        val bubbleCenter = Offset(
            x = cx - tiltX * maxTiltOffset,
            y = cy + tiltY * maxTiltOffset
        )
        drawCircle(
            color = if (telemetry.isStationaryOnDesk) MintSuccess.copy(alpha = 0.28f)
            else AmberAlert.copy(alpha = 0.35f),
            radius = 5.dp.toPx(),
            center = bubbleCenter
        )
    }
}

/**
 * Interactive Liquid Knock Pad, Instant Macro Launchpad, Acoustic Desk Surface Material Calibrator,
 * and Knock-Controlled Focus/Pomodoro Capsule — all rendered with Kyant0's `drawBackdrop` Liquid Glass
 * and elastic squishy touch physics.
 */
@Composable
fun LiquidResonanceDeckCard(
    controller: MonitoringController,
    activeMappings: List<ResolvedMapping>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by controller.repository.settingsFlow.collectAsState()
    val backdrop = LocalBackdrop.current
    val config = LocalLiquidGlassConfig.current
    val density = LocalDensity.current

    var isExpanded by remember { mutableStateOf(true) }
    var isLiveExecMode by remember { mutableStateOf(true) }
    var selectedMaterialId by remember { mutableStateOf("WOOD") }

    // Built-in Focus / Pomodoro Capsule state
    var focusDurationSeconds by remember { mutableIntStateOf(25 * 60) }
    var focusRemainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var isFocusTimerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isFocusTimerRunning) {
        if (!isFocusTimerRunning) return@LaunchedEffect
        while (isActive && isFocusTimerRunning && focusRemainingSeconds > 0) {
            delay(1000L)
            focusRemainingSeconds = (focusRemainingSeconds - 1).coerceAtLeast(0)
            if (focusRemainingSeconds == 0) {
                isFocusTimerRunning = false
                controller.notifyMacroWorkDone(
                    actionName = "Focus Session Complete!",
                    gestureName = "Desk Focus Timer",
                    resultMessage = "Completed ${focusDurationSeconds / 60}m session",
                    success = true,
                    impactCount = 3,
                    isSimulated = false
                )
            }
        }
    }

    // Active expanding ripples on the pad
    val ripples = remember { mutableStateListOf<ShockwaveRipple>() }
    var frameClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Live cadence tracking for multi-tap sequences
    val tapTimestampsMs = remember { mutableStateListOf<Long>() }
    val tapIntervalsMs = remember { mutableStateListOf<Long>() }
    var sequenceStartMs by remember { mutableLongStateOf(0L) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var lastQuickFiredMacroName by remember { mutableStateOf<String?>(null) }
    var totalSessionKnocks by remember { mutableIntStateOf(0) }

    // 3D Liquid Glass tilt & squish state on tap
    val padScale = remember { Animatable(1f) }
    var tiltRotX by remember { mutableFloatStateOf(0f) }
    var tiltRotY by remember { mutableFloatStateOf(0f) }

    // 60fps animation loop only while ripples or cadence countdown are active
    val hasActiveAnimation = ripples.isNotEmpty() || (lastTapMs > 0L && (frameClockMs - lastTapMs) < 900L)
    LaunchedEffect(hasActiveAnimation) {
        if (!hasActiveAnimation) return@LaunchedEffect
        while (isActive) {
            withFrameNanos {
                val now = System.currentTimeMillis()
                frameClockMs = now
                ripples.removeAll { now - it.startTimeMs > 1150L }
                if (lastTapMs > 0L && now - lastTapMs > 850L && ripples.isEmpty()) {
                    tapTimestampsMs.clear()
                    tapIntervalsMs.clear()
                    lastTapMs = 0L
                }
            }
        }
    }

    fun triggerPadKnock(normX: Float, normY: Float) {
        val nowMs = System.currentTimeMillis()
        val nowNanos = System.nanoTime()

        if (lastTapMs == 0L || (nowMs - lastTapMs) > 420L) {
            tapTimestampsMs.clear()
            tapIntervalsMs.clear()
            sequenceStartMs = nowMs
        } else {
            val interval = (nowMs - lastTapMs).coerceAtLeast(40L)
            tapIntervalsMs.add(interval)
        }
        tapTimestampsMs.add(nowMs)
        lastTapMs = nowMs
        totalSessionKnocks++

        val knockNumber = tapTimestampsMs.size
        val primaryColor = when (knockNumber % 3) {
            1 -> CyanGlow
            2 -> IndigoPulse
            else -> MintSuccess
        }
        val secondaryColor = when (knockNumber % 3) {
            1 -> IndigoPulse
            2 -> CyanGlow
            else -> IndigoPulse
        }

        if (ripples.size >= 10) {
            ripples.removeAt(0)
        }
        ripples.add(
            ShockwaveRipple(
                id = nowNanos,
                centerNormalizedX = normX.coerceIn(0.08f, 0.92f),
                centerNormalizedY = normY.coerceIn(0.12f, 0.88f),
                startTimeMs = nowMs,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                peakStrength = 1.0f,
                knockIndex = knockNumber
            )
        )

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        tiltRotY = (normX - 0.5f) * 10f
        tiltRotX = (0.5f - normY) * 10f
        scope.launch {
            padScale.snapTo(0.955f)
            padScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.42f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            tiltRotX = 0f
            tiltRotY = 0f
        }

        val helper = controller.sensorHelper
        synchronized(helper.waveformBuffer) {
            val peak = 3.8f
            for (s in 0 until 6) {
                val env = exp(-s * 0.55f)
                helper.waveformBuffer[helper.bufferWriteIndex] = peak * env
                helper.bufferWriteIndex = (helper.bufferWriteIndex + 1) % helper.waveformBuffer.size
            }
        }

        controller.gestureRecognizer.onImpact(
            ImpactEvent(
                timestampNanos = nowNanos,
                peakAmplitude = 3.8f,
                durationMs = 28L,
                energy = 6.4f,
                dominantAxis = "Z",
                isSynthetic = !isLiveExecMode
            )
        )
    }

    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("liquid_resonance_deck_card"),
        accentColor = CyanGlow,
        enableRealtimeBackdropBlur = true,
        reducedMotion = reducedMotion
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row with title, squishy Live/Dry-Run toggle, and squishy collapse toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(CyanGlow.copy(alpha = 0.25f), IndigoPulse.copy(alpha = 0.25f))
                                )
                            )
                            .border(1.dp, CyanGlow.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Waves,
                            contentDescription = "Liquid Resonance Pad",
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Liquid Knock Pad & Pro Studio",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Multi-ripple knock synth, surface tuner & focus timer",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Squishy Kyant0 Liquid Glass Mode Toggle: LIVE EXEC vs DRY-RUN
                Box(
                    modifier = Modifier
                        .liquidSquish(maxDisplacementPx = 18f)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isLiveExecMode) MintSuccess.copy(alpha = 0.18f)
                            else IndigoPulse.copy(alpha = 0.18f)
                        )
                        .border(
                            1.dp,
                            if (isLiveExecMode) MintSuccess.copy(alpha = 0.55f)
                            else IndigoPulse.copy(alpha = 0.55f),
                            RoundedCornerShape(50)
                        )
                        .clickable { isLiveExecMode = !isLiveExecMode }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("resonance_mode_toggle")
                ) {
                    Text(
                        text = if (isLiveExecMode) "LIVE EXEC" else "DRY-RUN",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isLiveExecMode) MintSuccess else CyanGlow,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .liquidSquish(maxDisplacementPx = 16f)
                        .clip(CircleShape)
                        .background(SlateGlassDark.copy(alpha = 0.6f))
                        .border(1.dp, GlassBorderTop, CircleShape)
                        .clickable { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Pad" else "Expand Pad",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Interactive Multi-Ripple Kyant0 Liquid Glass Resonance Surface
                    val padShape = RoundedCornerShape(20.dp)
                    val padBackdropModifier = if (backdrop != null && config.visualEffectsQuality != "LOW") {
                        val blurPx = with(density) { 16.dp.toPx() }
                        val lensHeightPx = with(density) { 14.dp.toPx() }
                        val lensAmountPx = with(density) { 32.dp.toPx() }
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { padShape },
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
                                drawRect(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF0A1224).copy(alpha = 0.78f),
                                            Color(0xFF101B35).copy(alpha = 0.84f),
                                            Color(0xFF090F1E).copy(alpha = 0.86f)
                                        )
                                    )
                                )
                            }
                        )
                    } else {
                        Modifier
                            .clip(padShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0A1224).copy(alpha = 0.92f),
                                        Color(0xFF101B35).copy(alpha = 0.95f),
                                        Color(0xFF090F1E).copy(alpha = 0.95f)
                                    )
                                )
                            )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                            .graphicsLayer {
                                scaleX = padScale.value
                                scaleY = padScale.value
                                rotationX = if (reducedMotion) 0f else tiltRotX
                                rotationY = if (reducedMotion) 0f else tiltRotY
                                cameraDistance = 14f * density.density
                            }
                            .then(padBackdropModifier)
                            .border(
                                width = 1.2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        CyanGlow.copy(alpha = 0.55f),
                                        IndigoPulse.copy(alpha = 0.35f),
                                        MintSuccess.copy(alpha = 0.45f)
                                    )
                                ),
                                shape = padShape
                            )
                            .pointerInput(isLiveExecMode) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val normX = if (size.width > 0) down.position.x / size.width else 0.5f
                                    val normY = if (size.height > 0) down.position.y / size.height else 0.5f
                                    triggerPadKnock(normX, normY)
                                }
                            }
                            .testTag("interactive_liquid_knock_pad")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val cols = 8
                            val rows = 4
                            val gridColor = CyanGlow.copy(alpha = 0.06f)
                            for (c in 1 until cols) {
                                val x = w * (c.toFloat() / cols)
                                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                            }
                            for (r in 1 until rows) {
                                val y = h * (r.toFloat() / rows)
                                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                            }

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.09f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = h * 0.42f
                                ),
                                size = Size(w, h * 0.42f),
                                cornerRadius = CornerRadius(20.dp.toPx())
                            )

                            val maxRadius = max(w, h) * 0.78f
                            ripples.forEach { ripple ->
                                val elapsed = (frameClockMs - ripple.startTimeMs).coerceAtLeast(0L)
                                val progress = (elapsed / 1100f).coerceIn(0f, 1f)
                                val eased = 1f - (1f - progress) * (1f - progress)
                                val alpha = (1f - progress).coerceIn(0f, 1f)

                                val cx = ripple.centerNormalizedX * w
                                val cy = ripple.centerNormalizedY * h
                                val primaryRadius = (14.dp.toPx() + maxRadius * eased)
                                val secondaryRadius = primaryRadius * 0.68f

                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ripple.primaryColor.copy(alpha = alpha * 0.34f),
                                            ripple.secondaryColor.copy(alpha = alpha * 0.14f),
                                            Color.Transparent
                                        ),
                                        center = Offset(cx, cy),
                                        radius = primaryRadius.coerceAtLeast(8f)
                                    ),
                                    radius = primaryRadius.coerceAtLeast(8f),
                                    center = Offset(cx, cy)
                                )

                                drawCircle(
                                    color = ripple.primaryColor.copy(alpha = alpha * 0.85f),
                                    radius = primaryRadius,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = (3.2f * (1f - progress * 0.6f)).dp.toPx())
                                )

                                drawCircle(
                                    color = ripple.secondaryColor.copy(alpha = alpha * 0.6f),
                                    radius = secondaryRadius,
                                    center = Offset(cx, cy),
                                    style = Stroke(
                                        width = 1.6.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), elapsed * 0.08f)
                                    )
                                )

                                if (!reducedMotion) {
                                    val sparkCount = 8
                                    for (p in 0 until sparkCount) {
                                        val angle = (p.toFloat() / sparkCount) * (2f * PI.toFloat()) + (ripple.knockIndex * 0.4f)
                                        val sparkDist = primaryRadius * 0.82f
                                        val sx = cx + cos(angle) * sparkDist
                                        val sy = cy + sin(angle) * sparkDist
                                        drawCircle(
                                            color = Color.White.copy(alpha = alpha * 0.8f),
                                            radius = (2.8f * alpha).dp.toPx(),
                                            center = Offset(sx, sy)
                                        )
                                    }
                                }
                            }

                            if (lastTapMs > 0L) {
                                val sinceLastTap = (frameClockMs - lastTapMs).coerceAtLeast(0L)
                                if (sinceLastTap <= 400L) {
                                    val lockProgress = (1f - (sinceLastTap / 380f)).coerceIn(0f, 1f)
                                    val barW = w * lockProgress
                                    drawLine(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(CyanGlow, IndigoPulse, MintSuccess)
                                        ),
                                        start = Offset((w - barW) / 2f, h - 3.dp.toPx()),
                                        end = Offset((w + barW) / 2f, h - 3.dp.toPx()),
                                        strokeWidth = 4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val currentCombo = tapTimestampsMs.size
                                    ResonanceMiniPill(
                                        text = if (currentCombo > 0) "${currentCombo}x KNOCK CHAIN"
                                        else "READY • TAP SURFACE",
                                        color = when {
                                            currentCombo >= 3 -> MintSuccess
                                            currentCombo == 2 -> IndigoPulse
                                            currentCombo == 1 -> CyanGlow
                                            else -> TextSecondary
                                        }
                                    )

                                    if (tapIntervalsMs.isNotEmpty()) {
                                        val latestGap = tapIntervalsMs.last()
                                        ResonanceMiniPill(
                                            text = "Δ ${latestGap}ms",
                                            color = CyanGlow
                                        )
                                    }
                                }

                                if (totalSessionKnocks > 0) {
                                    Text(
                                        text = "$totalSessionKnocks knocks",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextTertiary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tapTimestampsMs.isEmpty()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TouchApp,
                                                contentDescription = null,
                                                tint = CyanGlow.copy(alpha = 0.85f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Tap 1x, 2x, 3x or Custom Rhythm",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary.copy(alpha = 0.9f),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = if (isLiveExecMode) {
                                                "Live Mode: Matched gestures fire macros + 4-Edge Lighting"
                                            } else {
                                                "Dry-Run Mode: Test gesture cadence without system actions"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        val displayCount = min(tapTimestampsMs.size, 5)
                                        for (idx in 0 until displayCount) {
                                            if (idx > 0 && idx - 1 < tapIntervalsMs.size) {
                                                Text(
                                                    text = " ${tapIntervalsMs[idx - 1]}ms ",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    fontFamily = FontFamily.Monospace,
                                                    color = CyanGlow
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(CyanGlow, IndigoPulse)
                                                        )
                                                    )
                                                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = IndigoPulse,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = lastQuickFiredMacroName?.let { "Triggered: $it" }
                                            ?: "380ms cadence window • 4-Edge Neon Sync",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (lastQuickFiredMacroName != null) MintSuccess else TextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "DSP LINKED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontFamily = FontFamily.Monospace,
                                    color = CyanGlow.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 1. Instant Macro Launchpad Strip (with Kyant0 drawBackdrop + liquidSquish!)
                    val enabledMappings = activeMappings.filter { it.isEnabled }
                    if (enabledMappings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MintSuccess,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Instant Macro Launchpad",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Squish & tap to fire",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            enabledMappings.forEachIndexed { idx, resolved ->
                                val chipColor = when (idx % 3) {
                                    0 -> CyanGlow
                                    1 -> IndigoPulse
                                    else -> MintSuccess
                                }
                                val chipShape = remember { RoundedCornerShape(14.dp) }
                                val chipBackdropMod = if (backdrop != null && config.visualEffectsQuality != "LOW") {
                                    val blurPx = with(density) { 12.dp.toPx() }
                                    val lensHeightPx = with(density) { 10.dp.toPx() }
                                    val lensAmountPx = with(density) { 24.dp.toPx() }
                                    Modifier.drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { chipShape },
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
                                            drawRect(SlateGlassDark.copy(alpha = 0.58f))
                                            drawRect(chipColor.copy(alpha = 0.14f))
                                        }
                                    )
                                } else {
                                    Modifier
                                        .clip(chipShape)
                                        .background(SlateGlassDark.copy(alpha = 0.78f))
                                }

                                Box(
                                    modifier = Modifier
                                        .liquidSquish(maxDisplacementPx = 20f)
                                        .then(chipBackdropMod)
                                        .border(1.dp, chipColor.copy(alpha = 0.50f), chipShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val nowMs = System.currentTimeMillis()
                                            if (ripples.size >= 10) ripples.removeAt(0)
                                            ripples.add(
                                                ShockwaveRipple(
                                                    id = System.nanoTime(),
                                                    centerNormalizedX = (0.25f + (idx % 3) * 0.25f),
                                                    centerNormalizedY = 0.5f,
                                                    startTimeMs = nowMs,
                                                    primaryColor = chipColor,
                                                    secondaryColor = MintSuccess,
                                                    peakStrength = 1.15f,
                                                    knockIndex = idx + 1
                                                )
                                            )
                                            lastQuickFiredMacroName = "${resolved.action.name} (${resolved.gesture.name})"
                                            scope.launch {
                                                val result = controller.actionExecutor.executeAction(
                                                    action = resolved.action,
                                                    isSimulated = !isLiveExecMode
                                                )
                                                controller.repository.recordExecutionLog(
                                                    actionId = resolved.action.id,
                                                    actionName = resolved.action.name,
                                                    gestureName = "${resolved.gesture.name} [Pad Launch]",
                                                    result = result
                                                )
                                                controller.notifyMacroWorkDone(
                                                    actionName = resolved.action.name,
                                                    gestureName = "${resolved.gesture.name} [Launchpad]",
                                                    resultMessage = result.message,
                                                    success = result.success,
                                                    impactCount = idx + 1,
                                                    isSimulated = !isLiveExecMode
                                                )
                                                delay(2800L)
                                                if (lastQuickFiredMacroName == "${resolved.action.name} (${resolved.gesture.name})") {
                                                    lastQuickFiredMacroName = null
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("instant_macro_chip_${resolved.action.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(chipColor.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Execute ${resolved.action.name}",
                                                tint = chipColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = resolved.action.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = resolved.gesture.name,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = chipColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Acoustic Desk Surface Material Calibrator (Rare Pro Feature #1)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Acoustic Desk Surface Calibrator",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${(settings.sensitivity * 100).toInt()}% sens • ${settings.baseVibrationThreshold} m/s²",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontFamily = FontFamily.Monospace,
                            color = CyanGlowBright
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SURFACE_MATERIAL_PRESETS.forEach { preset ->
                            val isSelected = selectedMaterialId == preset.id
                            val matShape = remember { RoundedCornerShape(14.dp) }
                            Box(
                                modifier = Modifier
                                    .liquidSquish(maxDisplacementPx = 18f)
                                    .clip(matShape)
                                    .background(
                                        if (isSelected) preset.accentColor.copy(alpha = 0.22f)
                                        else SlateGlassDark.copy(alpha = 0.65f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) preset.accentColor else GlassBorderTop,
                                        matShape
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedMaterialId = preset.id
                                        controller.repository.updateSettings(
                                            settings.copy(
                                                baseVibrationThreshold = preset.baseThreshold,
                                                sensitivity = preset.sensitivity,
                                                cooldownMs = preset.cooldownMs
                                            )
                                        )
                                        controller.notifyMacroWorkDone(
                                            actionName = "${preset.label} Calibrated",
                                            gestureName = "Surface Impedance Tuner",
                                            resultMessage = preset.subtitle,
                                            success = true,
                                            impactCount = 2,
                                            isSimulated = true
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Column {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (isSelected) preset.accentColor else TextTertiary
                                    )
                                }
                            }
                        }
                    }

                    // 3. Knock-Controlled Desk Focus / Pomodoro Capsule (Rare Pro Feature #2)
                    Spacer(modifier = Modifier.height(14.dp))
                    val focusProgress = if (focusDurationSeconds > 0) {
                        (focusRemainingSeconds.toFloat() / focusDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val mins = focusRemainingSeconds / 60
                    val secs = focusRemainingSeconds % 60
                    val formattedTime = String.format("%02d:%02d", mins, secs)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SlateGlassDark.copy(alpha = 0.58f))
                            .border(1.dp, IndigoPulse.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (isFocusTimerRunning) MintSuccess else CyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Desk Focus & Pomodoro Capsule",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isFocusTimerRunning) "Session active • Silent notification on finish"
                                        else "Silent status-bar & 4-Edge alert when timer finishes",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = TextSecondary
                                    )
                                }
                            }

                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = if (isFocusTimerRunning) MintSuccess else CyanGlowBright,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { focusProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (isFocusTimerRunning) MintSuccess else CyanGlow,
                            trackColor = Color.White.copy(alpha = 0.08f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val presets = listOf(25 to "25m Focus", 15 to "15m Sprint", 5 to "5m Break")
                            presets.forEach { (minutesVal, label) ->
                                val activePreset = focusDurationSeconds == minutesVal * 60
                                Box(
                                    modifier = Modifier
                                        .liquidSquish(maxDisplacementPx = 14f)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (activePreset) CyanGlow.copy(alpha = 0.22f)
                                            else SlateGlassDark.copy(alpha = 0.65f)
                                        )
                                        .border(
                                            1.dp,
                                            if (activePreset) CyanGlow else GlassBorderTop,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable {
                                            isFocusTimerRunning = false
                                            focusDurationSeconds = minutesVal * 60
                                            focusRemainingSeconds = minutesVal * 60
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (activePreset) Color.White else TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            LiquidGlowButton(
                                text = if (isFocusTimerRunning) "Pause" else "Start",
                                onClick = {
                                    isFocusTimerRunning = !isFocusTimerRunning
                                    if (isFocusTimerRunning) {
                                        controller.notifyMacroWorkDone(
                                            actionName = "Focus Timer Started (${focusDurationSeconds / 60}m)",
                                            gestureName = "Desk Focus Capsule",
                                            resultMessage = "Countdown active",
                                            success = true,
                                            impactCount = 1,
                                            isSimulated = true
                                        )
                                    }
                                },
                                accentColor = if (isFocusTimerRunning) AmberAlert else MintSuccess,
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isFocusTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .liquidSquish(maxDisplacementPx = 14f)
                                    .clip(CircleShape)
                                    .background(SlateGlassDark.copy(alpha = 0.65f))
                                    .border(1.dp, GlassBorderTop, CircleShape)
                                    .clickable {
                                        isFocusTimerRunning = false
                                        focusRemainingSeconds = focusDurationSeconds
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Timer",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
