package com.example.knockdock.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.knockdock.model.CardGlassSettings
import com.example.knockdock.model.NavigationGlassSettings
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanGlowBright
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.SlateGlassCard
import com.example.ui.theme.SlateGlassDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Global motion & floating control configuration.
 */
data class LiquidGlassConfig(
    val visualEffectsQuality: String = "HIGH",
    val reducedMotion: Boolean = false,
    val blurRadiusDp: Float = 18f, // 0f..40f dp
    val refractionAmountDp: Float = 38f, // 0f..76f dp
    val refractionHeightDp: Float = 18f, // 4f..36f dp
    val chromaticAberration: Boolean = true,
    val liquidTouchPhysics: Boolean = true
)

val LocalLiquidGlassConfig = compositionLocalOf { LiquidGlassConfig() }

/**
 * Independent CompositionLocal exclusively for Card Glass / Surface Appearance.
 * Never read by the floating bottom navigation bar.
 */
val LocalCardGlassSettings = compositionLocalOf { CardGlassSettings() }

/**
 * Independent CompositionLocal exclusively for the Floating Bottom Navigation Bar.
 * Never read by LiquidGlassCard.
 */
val LocalNavigationGlassSettings = compositionLocalOf { NavigationGlassSettings() }

/**
 * CompositionLocal carrying the ambient background Kyant0 Backdrop across the Composable tree.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * CompositionLocal carrying the foreground content LayerBackdrop for floating glass bars,
 * floating action buttons, bottom sheets, and modals so they blur and refract scrolling content beneath them.
 */
val LocalContentBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Deterministic resolved rendering parameters for Card Glass surfaces.
 */
data class CardRenderParameters(
    val executesBlurPass: Boolean,
    val effectiveBlurRadiusDp: Float,
    val effectiveOpacity: Float,
    val tintColor: Color,
    val effectiveTintIntensity: Float,
    val effectiveRefractionIntensity: Float,
    val effectiveRimHighlightIntensity: Float,
    val cornerRadiusDp: Float,
    val surfaceContrast: Float,
    val isLightweightFallback: Boolean
)

/**
 * Deterministic resolved rendering parameters for the Floating Bottom Navigation Bar.
 */
data class NavigationRenderParameters(
    val executesBlurPass: Boolean,
    val effectiveBlurRadiusDp: Float,
    val effectiveRefractionAmountDp: Float,
    val effectiveRefractionHeightDp: Float,
    val chromaticAberration: Boolean,
    val effectiveOpacity: Float,
    val effectiveRimHighlightIntensity: Float
)

fun resolveCardTintColor(key: String): Color = when (key.uppercase()) {
    "VIOLET" -> IndigoPulse
    "EMERALD" -> MintSuccess
    "AMBER" -> AmberAlert
    "SLATE" -> SlateGlassCard
    "NONE" -> Color.Transparent
    else -> CyanGlow
}

/**
 * Computes resolved card rendering parameters purely from [CardGlassSettings] and quality preset.
 * Guaranteed to be 100% independent of [NavigationGlassSettings].
 */
fun computeCardRenderParameters(
    cardSettings: CardGlassSettings,
    visualEffectsQuality: String = "HIGH"
): CardRenderParameters {
    val lightweightOrLow = cardSettings.cardLightweightMode || visualEffectsQuality == "LOW"
    val blurActive = cardSettings.cardBlurEnabled && !lightweightOrLow && cardSettings.cardBlurRadiusDp > 0f
    val effBlur = if (blurActive) cardSettings.cardBlurRadiusDp.coerceIn(0f, 40f) else 0f
    val effRefraction = if (blurActive) cardSettings.cardRefractionIntensity.coerceIn(0f, 1f) else 0f
    val baseOpacity = cardSettings.cardBackgroundOpacity.coerceIn(0.15f, 0.95f)
    val effOpacity = if (lightweightOrLow) {
        (baseOpacity + 0.30f).coerceAtMost(0.95f)
    } else if (!cardSettings.cardBlurEnabled) {
        // Preserve readable non-blurred fallback surface
        (baseOpacity + 0.16f).coerceAtMost(0.92f)
    } else {
        baseOpacity
    }

    return CardRenderParameters(
        executesBlurPass = blurActive,
        effectiveBlurRadiusDp = effBlur,
        effectiveOpacity = effOpacity,
        tintColor = resolveCardTintColor(cardSettings.cardTintColorKey),
        effectiveTintIntensity = if (cardSettings.cardTintColorKey.equals("NONE", ignoreCase = true)) {
            0f
        } else {
            cardSettings.cardTintIntensity.coerceIn(0f, 0.65f)
        },
        effectiveRefractionIntensity = effRefraction,
        effectiveRimHighlightIntensity = cardSettings.cardRimHighlightIntensity.coerceIn(0f, 1f),
        cornerRadiusDp = cardSettings.cardCornerRadiusDp.coerceIn(8f, 36f),
        surfaceContrast = cardSettings.cardSurfaceContrast.coerceIn(0f, 1f),
        isLightweightFallback = lightweightOrLow || !cardSettings.cardBlurEnabled
    )
}

/**
 * Computes resolved navigation bar rendering parameters purely from [NavigationGlassSettings].
 * Guaranteed to be 100% independent of [CardGlassSettings].
 */
fun computeNavigationRenderParameters(
    navSettings: NavigationGlassSettings,
    visualEffectsQuality: String = "HIGH"
): NavigationRenderParameters {
    val blurActive = navSettings.navBlurEnabled && visualEffectsQuality != "LOW" && navSettings.navBlurRadiusDp > 0f
    return NavigationRenderParameters(
        executesBlurPass = blurActive,
        effectiveBlurRadiusDp = if (blurActive) navSettings.navBlurRadiusDp.coerceIn(0f, 40f) else 0f,
        effectiveRefractionAmountDp = if (visualEffectsQuality != "LOW") navSettings.navRefractionAmountDp.coerceIn(0f, 76f) else 0f,
        effectiveRefractionHeightDp = navSettings.navRefractionHeightDp.coerceIn(4f, 36f),
        chromaticAberration = navSettings.navChromaticAberration && visualEffectsQuality != "LOW",
        effectiveOpacity = if (visualEffectsQuality == "LOW") 0.92f else navSettings.navBackgroundOpacity.coerceIn(0.15f, 0.85f),
        effectiveRimHighlightIntensity = navSettings.navRimHighlightIntensity.coerceIn(0f, 1f)
    )
}

/**
 * High-performance, zero-recomposition iOS 26 Liquid Glass touch physics state for interactive controls
 * (buttons, switches, sliders, floating dock, and hero orb — NOT container cards).
 */
@Stable
class LiquidTouchState(
    private val scope: CoroutineScope
) {
    var offsetXValue by mutableFloatStateOf(0f)
        private set
    var offsetYValue by mutableFloatStateOf(0f)
        private set
    var scaleXValue by mutableFloatStateOf(1f)
        private set
    var scaleYValue by mutableFloatStateOf(1f)
        private set
    var pressIntensityValue by mutableFloatStateOf(0f)
        private set

    var touchPoint by mutableStateOf<Offset?>(null)
        private set
    var componentSize by mutableStateOf(IntSize.Zero)

    private var settleJob: Job? = null

    fun onDown(position: Offset) {
        settleJob?.cancel()
        touchPoint = position
        val w = componentSize.width.coerceAtLeast(1).toFloat()
        val h = componentSize.height.coerceAtLeast(1).toFloat()
        val relX = ((position.x - w * 0.5f) / (w * 0.5f)).coerceIn(-1f, 1f)
        val relY = ((position.y - h * 0.5f) / (h * 0.5f)).coerceIn(-1f, 1f)

        // Immediate, fast liquid droplet squish & magnetic pull toward touch point
        offsetXValue = relX * 9.5f
        offsetYValue = relY * 7.5f
        scaleXValue = 1.078f + abs(relX) * 0.025f
        scaleYValue = 0.932f - abs(relY) * 0.020f
        pressIntensityValue = 1f
    }

    fun onMove(position: Offset, startPosition: Offset, maxDisplacementPx: Float) {
        touchPoint = position
        val limit = (maxDisplacementPx * 1.45f).coerceAtLeast(18f)
        val dx = (position.x - startPosition.x).coerceIn(-limit * 2.2f, limit * 2.2f)
        val dy = (position.y - startPosition.y).coerceIn(-limit * 2.2f, limit * 2.2f)

        // High-mobility fluid displacement that follows the finger smoothly
        offsetXValue = (dx * 0.68f).coerceIn(-limit, limit)
        offsetYValue = (dy * 0.62f).coerceIn(-limit, limit)

        // Volume-preserving elastic stretch along the drag axis
        val stretchX = (abs(dx) / (limit * 1.6f)).coerceIn(0f, 1f) * 0.135f
        val stretchY = (abs(dy) / (limit * 1.6f)).coerceIn(0f, 1f) * 0.135f
        scaleXValue = (1.035f + stretchX - stretchY * 0.55f).coerceIn(0.88f, 1.18f)
        scaleYValue = (0.975f + stretchY - stretchX * 0.55f).coerceIn(0.86f, 1.18f)
    }

    fun onRelease() {
        settleJob?.cancel()
        val startOx = offsetXValue
        val startOy = offsetYValue
        // If user tapped quickly without dragging far, give an energetic elastic rebound impulse
        val startSx = if (abs(scaleXValue - 1f) < 0.05f) 1.085f else scaleXValue
        val startSy = if (abs(scaleYValue - 1f) < 0.05f) 0.925f else scaleYValue
        val startPress = pressIntensityValue

        settleJob = scope.launch {
            val anim = Animatable(1f)
            anim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    // Underdamped liquid surface-tension spring: fast initial snap + longer elastic jelly oscillation
                    dampingRatio = 0.36f,
                    stiffness = 340f
                )
            ) {
                val p = value
                offsetXValue = startOx * p
                offsetYValue = startOy * p
                scaleXValue = 1f + (startSx - 1f) * p
                scaleYValue = 1f + (startSy - 1f) * p
                pressIntensityValue = startPress * p.coerceAtLeast(0f)
            }
            touchPoint = null
        }
    }
}

@Composable
fun rememberLiquidTouchState(): LiquidTouchState {
    val scope = rememberCoroutineScope()
    return remember(scope) { LiquidTouchState(scope) }
}

/**
 * Passive pointer observer that drives iOS-style Liquid Glass movement and elastic squish
 * on interactive controls without consuming pointer events.
 */
fun Modifier.liquidTouchGesture(
    touchState: LiquidTouchState,
    enabled: Boolean = true,
    maxDisplacementPx: Float = 28f
): Modifier {
    if (!enabled) return this
    return this
        .onSizeChanged { touchState.componentSize = it }
        .pointerInput(enabled, maxDisplacementPx) {
            awaitPointerEventScope {
                var startPos: Offset? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change != null && change.pressed) {
                        if (startPos == null) {
                            startPos = change.position
                            touchState.onDown(change.position)
                        } else {
                            touchState.onMove(change.position, startPos, maxDisplacementPx)
                        }
                    } else if (startPos != null) {
                        startPos = null
                        touchState.onRelease()
                    }
                }
            }
        }
}

/**
 * Minimal, clean OLED Dark Mode ambient atmosphere painter so background and content backdrops
 * provide subtle light gradients for glass refraction without heavy waves or grid clutter.
 */
fun Modifier.drawCachedAuroraField(visualEffectsQuality: String): Modifier = this.drawWithCache {
    val topRightBrush = Brush.radialGradient(
        colors = listOf(
            CyanGlow.copy(alpha = 0.14f),
            CyanGlowBright.copy(alpha = 0.04f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.85f, size.height * 0.12f),
        radius = size.maxDimension * 0.58f
    )
    val bottomLeftBrush = Brush.radialGradient(
        colors = listOf(
            IndigoPulse.copy(alpha = 0.12f),
            CyanGlow.copy(alpha = 0.03f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.15f, size.height * 0.78f),
        radius = size.maxDimension * 0.60f
    )

    onDrawBehind {
        drawRect(color = VoidBlack)
        if (visualEffectsQuality != "LOW") {
            drawRect(brush = topRightBrush)
            drawRect(brush = bottomLeftBrush)
        }
    }
}

/**
 * Ambient background container providing independent `LocalCardGlassSettings` and `LocalNavigationGlassSettings`,
 * as well as `LocalBackdrop` and `LocalContentBackdrop`.
 */
@Composable
fun LiquidGlassBackground(
    visualEffectsQuality: String = "HIGH",
    reducedMotion: Boolean = false,
    blurRadiusDp: Float = 18f,
    refractionAmountDp: Float = 38f,
    refractionHeightDp: Float = 18f,
    chromaticAberration: Boolean = true,
    liquidTouchPhysics: Boolean = true,
    cardGlassSettings: CardGlassSettings = CardGlassSettings(),
    navigationGlassSettings: NavigationGlassSettings = NavigationGlassSettings(
        navBlurRadiusDp = blurRadiusDp,
        navRefractionAmountDp = refractionAmountDp,
        navRefractionHeightDp = refractionHeightDp,
        navChromaticAberration = chromaticAberration
    ),
    content: @Composable BoxScope.() -> Unit
) {
    val config = remember(
        visualEffectsQuality,
        reducedMotion,
        blurRadiusDp,
        refractionAmountDp,
        refractionHeightDp,
        chromaticAberration,
        liquidTouchPhysics
    ) {
        LiquidGlassConfig(
            visualEffectsQuality = visualEffectsQuality,
            reducedMotion = reducedMotion,
            blurRadiusDp = blurRadiusDp,
            refractionAmountDp = refractionAmountDp,
            refractionHeightDp = refractionHeightDp,
            chromaticAberration = chromaticAberration,
            liquidTouchPhysics = liquidTouchPhysics
        )
    }

    val backgroundLayerBackdrop = rememberLayerBackdrop {
        drawRect(VoidBlack)
        drawContent()
    }
    val contentLayerBackdrop = rememberLayerBackdrop {
        drawRect(VoidBlack)
        drawContent()
    }

    CompositionLocalProvider(
        LocalLiquidGlassConfig provides config,
        LocalCardGlassSettings provides cardGlassSettings,
        LocalNavigationGlassSettings provides navigationGlassSettings,
        LocalBackdrop provides backgroundLayerBackdrop,
        LocalContentBackdrop provides contentLayerBackdrop
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VoidBlack)
        ) {
            // Static cached aurora field for LocalBackdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backgroundLayerBackdrop)
                    .drawCachedAuroraField(visualEffectsQuality)
            )

            // Foreground application content
            content()
        }
    }
}

/**
 * Independent, customizable Liquid Glass Card:
 * - Reads ONLY [LocalCardGlassSettings] (or [overrideCardSettings] for live preview) — completely decoupled
 *   from [LocalNavigationGlassSettings] and the Floating Bottom Navigation Bar.
 * - When `cardBlurEnabled == false` or `cardLightweightMode == true`, completely skips card blur/frost/refraction
 *   passes and renders a clean, high-contrast non-blurred surface using configurable opacity and tint.
 * - When `cardBlurEnabled == true`, renders frosted blur sheen, optical lens bevels, and rim highlights,
 *   plus optional real-time Kyant0 `drawBackdrop` blur when `enableRealtimeBackdropBlur = true`.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    accentColor: Color? = null,
    visualEffectsQuality: String = "HIGH",
    reducedMotion: Boolean = false,
    useContentBackdrop: Boolean = false,
    enableRealtimeBackdropBlur: Boolean = false,
    overrideCardSettings: CardGlassSettings? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val globalConfig = LocalLiquidGlassConfig.current
    val cardSettings = overrideCardSettings ?: LocalCardGlassSettings.current
    val effectiveQuality = if (visualEffectsQuality != "HIGH") visualEffectsQuality else globalConfig.visualEffectsQuality

    val cardParams = remember(cardSettings, effectiveQuality) {
        computeCardRenderParameters(cardSettings, effectiveQuality)
    }

    val effectiveShape = shape ?: remember(cardParams.cornerRadiusDp) {
        RoundedCornerShape(cardParams.cornerRadiusDp.dp)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val primaryEdge = accentColor ?: if (cardParams.tintColor != Color.Transparent) cardParams.tintColor else CyanGlowBright
    val rimIntensity = cardParams.effectiveRimHighlightIntensity
    val refractionFactor = cardParams.effectiveRefractionIntensity
    val blurFactor = (cardParams.effectiveBlurRadiusDp / 40f).coerceIn(0f, 1f)

    val borderBrush = remember(primaryEdge, rimIntensity, refractionFactor, cardParams.executesBlurPass) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.14f + rimIntensity * 0.52f).coerceAtMost(0.85f)),
                primaryEdge.copy(alpha = (0.16f + rimIntensity * 0.45f + refractionFactor * 0.20f).coerceAtMost(0.85f)),
                GlassBorderBottom.copy(alpha = (0.10f + rimIntensity * 0.25f).coerceAtMost(0.55f))
            )
        )
    }

    val backdrop = LocalBackdrop.current
    val density = LocalDensity.current
    val realtimeBlurModifier = if (
        enableRealtimeBackdropBlur &&
        cardParams.executesBlurPass &&
        backdrop != null
    ) {
        val blurPx = with(density) { cardParams.effectiveBlurRadiusDp.dp.toPx() }
        val lensAmountPx = with(density) { (cardParams.effectiveRefractionIntensity * 48f).dp.toPx() }
        val lensHeightPx = with(density) { (8f + cardParams.effectiveRefractionIntensity * 16f).dp.toPx() }
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { effectiveShape },
            effects = {
                vibrancy()
                if (blurPx > 0f) blur(blurPx)
                if (lensAmountPx > 1f) {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = true,
                        chromaticAberration = true
                    )
                }
            }
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(realtimeBlurModifier)
            .clip(effectiveShape)
            .drawWithCache {
                val cornerPx = cardParams.cornerRadiusDp.dp.toPx()
                val surfaceAlpha = cardParams.effectiveOpacity
                val contrastBoost = cardParams.surfaceContrast * 0.16f

                val topSurfaceColor = SlateGlassCard.copy(
                    alpha = (surfaceAlpha - contrastBoost * 0.4f).coerceIn(0.12f, 0.96f)
                )
                val bottomSurfaceColor = SlateGlassDark.copy(
                    alpha = (surfaceAlpha + contrastBoost).coerceIn(0.18f, 0.98f)
                )
                val baseBrush = Brush.verticalGradient(
                    colors = listOf(topSurfaceColor, bottomSurfaceColor)
                )

                val tintColor = accentColor ?: cardParams.tintColor
                val tintAlpha = cardParams.effectiveTintIntensity
                val tintBrush = Brush.linearGradient(
                    colors = listOf(
                        tintColor.copy(alpha = tintAlpha),
                        tintColor.copy(alpha = tintAlpha * 0.35f),
                        Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )

                // Smooth, proportional blur & tint sheen with zero instant jump at low values
                val frostBlurSheenBrush = if (cardParams.executesBlurPass && blurFactor > 0.01f) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (blurFactor * 0.11f).coerceIn(0f, 0.14f)),
                            tintColor.copy(alpha = (blurFactor * tintAlpha * 0.55f).coerceIn(0f, 0.16f)),
                            Color.White.copy(alpha = (refractionFactor * blurFactor * 0.06f).coerceIn(0f, 0.08f))
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                } else null

                val lensWidthPx = (1.5f + refractionFactor * 3.5f).dp.toPx()
                val innerLensBevelBrush = if (cardParams.executesBlurPass && refractionFactor > 0.02f) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (refractionFactor * 0.22f).coerceIn(0f, 0.25f)),
                            primaryEdge.copy(alpha = (refractionFactor * tintAlpha * 0.65f).coerceIn(0f, 0.22f)),
                            Color.Transparent,
                            Color.White.copy(alpha = (refractionFactor * 0.10f).coerceIn(0f, 0.12f))
                        )
                    )
                } else null
                val lensStroke = Stroke(width = lensWidthPx)

                val specularAlpha = (rimIntensity * 0.56f).coerceIn(0f, 0.75f)
                val specularBrush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        (accentColor ?: Color.White).copy(alpha = specularAlpha),
                        Color.Transparent
                    )
                )

                onDrawBehind {
                    drawRect(brush = baseBrush)
                    if (tintAlpha > 0.005f && tintColor != Color.Transparent) {
                        drawRect(brush = tintBrush)
                    }
                    // Strictly skip card blur & refraction passes when cardBlurEnabled is false or lightweight mode is on
                    if (cardParams.executesBlurPass) {
                        if (frostBlurSheenBrush != null) {
                            drawRect(brush = frostBlurSheenBrush)
                        }
                        if (innerLensBevelBrush != null) {
                            drawRoundRect(
                                brush = innerLensBevelBrush,
                                topLeft = Offset(lensWidthPx * 0.5f, lensWidthPx * 0.5f),
                                size = Size(
                                    (size.width - lensWidthPx).coerceAtLeast(1f),
                                    (size.height - lensWidthPx).coerceAtLeast(1f)
                                ),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                style = lensStroke
                            )
                        }
                    }
                    if (!cardParams.isLightweightFallback && specularAlpha > 0.02f) {
                        drawLine(
                            brush = specularBrush,
                            start = Offset(size.width * 0.08f, 1.5f),
                            end = Offset(size.width * 0.92f, 1.5f),
                            strokeWidth = 2f
                        )
                    }
                    if (isPressed && onClick != null) {
                        drawRect(color = primaryEdge.copy(alpha = 0.10f))
                    }
                }
            }
            .border(BorderStroke(1.dp, borderBrush), effectiveShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = primaryEdge),
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * Capsule status indicator with dynamic dot and translucent liquid glass surface.
 */
@Composable
fun LiquidStatusPill(
    text: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val capsuleShape = remember { RoundedCornerShape(percent = 50) }
    val borderBrush = remember(statusColor) {
        Brush.horizontalGradient(
            listOf(statusColor.copy(alpha = 0.65f), Color.White.copy(alpha = 0.22f))
        )
    }
    val bgBrush = remember(statusColor) {
        Brush.verticalGradient(
            listOf(
                statusColor.copy(alpha = 0.22f),
                SlateGlassDark.copy(alpha = 0.64f)
            )
        )
    }

    Row(
        modifier = modifier
            .clip(capsuleShape)
            .background(bgBrush)
            .border(BorderStroke(1.dp, borderBrush), capsuleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * iOS-style Liquid Glass button with capsule geometry, specular highlight, and liquid touch movement.
 */
@Composable
fun LiquidGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = CyanGlow,
    useContentBackdrop: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val config = LocalLiquidGlassConfig.current
    val navSettings = LocalNavigationGlassSettings.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val touchState = rememberLiquidTouchState()
    val touchPhysicsEnabled = enabled && config.liquidTouchPhysics && !config.reducedMotion

    val backdrop = if (useContentBackdrop) LocalContentBackdrop.current else null
    val shape = remember { RoundedCornerShape(percent = 50) }
    val density = LocalDensity.current

    val buttonBrush = remember(enabled, isPressed, accentColor) {
        if (enabled) {
            Brush.horizontalGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (isPressed) 0.50f else 0.34f),
                    IndigoPulse.copy(alpha = if (isPressed) 0.40f else 0.24f)
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.03f)
                )
            )
        }
    }

    val borderBrush = remember(enabled, accentColor) {
        Brush.verticalGradient(
            if (enabled) {
                listOf(
                    Color.White.copy(alpha = 0.55f),
                    accentColor.copy(alpha = 0.72f),
                    accentColor.copy(alpha = 0.30f)
                )
            } else {
                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
            }
        )
    }

    val blurPx = with(density) { (navSettings.navBlurRadiusDp.coerceIn(0f, 40f) * 0.9f).dp.toPx() }
    val lensHeightPx = with(density) { (navSettings.navRefractionHeightDp * 0.75f).dp.toPx() }
    val lensAmountPx = with(density) { (navSettings.navRefractionAmountDp * 0.75f).dp.toPx() }
    val maxDispPx = with(density) { 24.dp.toPx() }

    val glassModifier = if (useContentBackdrop && backdrop != null && enabled && config.visualEffectsQuality != "LOW") {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                if (navSettings.navBlurEnabled && navSettings.navBlurRadiusDp > 0f) blur(blurPx)
                if (lensAmountPx > 1f) {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = true,
                        chromaticAberration = navSettings.navChromaticAberration
                    )
                }
            },
            layerBlock = if (touchPhysicsEnabled) {
                {
                    translationX = touchState.offsetXValue
                    translationY = touchState.offsetYValue
                    scaleX = touchState.scaleXValue
                    scaleY = touchState.scaleYValue
                }
            } else null,
            onDrawSurface = {
                drawRect(SlateGlassDark.copy(alpha = 0.32f))
                drawRect(brush = buttonBrush)
            }
        )
    } else {
        Modifier
            .graphicsLayer {
                if (touchPhysicsEnabled) {
                    translationX = touchState.offsetXValue
                    translationY = touchState.offsetYValue
                    scaleX = touchState.scaleXValue
                    scaleY = touchState.scaleYValue
                }
            }
            .clip(shape)
            .background(SlateGlassDark.copy(alpha = 0.45f))
            .background(buttonBrush)
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .liquidTouchGesture(
                touchState = touchState,
                enabled = touchPhysicsEnabled,
                maxDisplacementPx = maxDispPx
            )
            .then(glassModifier)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = ripple(color = accentColor),
                interactionSource = interactionSource,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact circular Liquid Glass Icon Button with hardware-layer liquid touch movement.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CyanGlow,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val config = LocalLiquidGlassConfig.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val touchState = rememberLiquidTouchState()
    val touchPhysicsEnabled = config.liquidTouchPhysics && !config.reducedMotion
    val shape = CircleShape
    val density = LocalDensity.current

    val bgBrush = remember(accentColor, isPressed) {
        Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = if (isPressed) 0.34f else 0.18f),
                SlateGlassDark.copy(alpha = 0.64f)
            )
        )
    }
    val borderBrush = remember(accentColor) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.38f),
                accentColor.copy(alpha = 0.48f)
            )
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .liquidTouchGesture(
                touchState = touchState,
                enabled = touchPhysicsEnabled,
                maxDisplacementPx = with(density) { 18.dp.toPx() }
            )
            .graphicsLayer {
                if (touchPhysicsEnabled) {
                    translationX = touchState.offsetXValue
                    translationY = touchState.offsetYValue
                    scaleX = touchState.scaleXValue
                    scaleY = touchState.scaleYValue
                }
            }
            .clip(shape)
            .background(bgBrush)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = accentColor),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Floating iOS-style Liquid Glass Action Pill / FAB that blurs and refracts the live screen content underneath it
 * via LocalContentBackdrop.
 */
@Composable
fun LiquidGlassFloatingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CyanGlow,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    LiquidGlowButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        accentColor = accentColor,
        useContentBackdrop = true,
        leadingIcon = leadingIcon
    )
}

/**
 * iOS-style Liquid Glass Toggle Switch with hardware-layer liquid thumb physics.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CyanGlow
) {
    val config = LocalLiquidGlassConfig.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val touchState = rememberLiquidTouchState()
    val touchPhysicsEnabled = config.liquidTouchPhysics && !config.reducedMotion
    val capsuleShape = remember { RoundedCornerShape(percent = 50) }
    val density = LocalDensity.current

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switch_offset"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "switch_thumb_scale"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) accentColor.copy(alpha = 0.44f) else SlateGlassDark.copy(alpha = 0.62f),
        animationSpec = tween(durationMillis = 180),
        label = "switch_track_color"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .liquidTouchGesture(
                touchState = touchState,
                enabled = touchPhysicsEnabled,
                maxDisplacementPx = with(density) { 18.dp.toPx() }
            )
            .graphicsLayer {
                if (touchPhysicsEnabled) {
                    translationX = touchState.offsetXValue
                    translationY = touchState.offsetYValue
                    scaleX = touchState.scaleXValue
                    scaleY = touchState.scaleYValue
                }
            }
            .clip(capsuleShape)
            .background(trackColor)
            .border(
                BorderStroke(
                    1.dp,
                    if (checked) accentColor.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.24f)
                ),
                capsuleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (checked) {
                            listOf(Color.White, accentColor.copy(alpha = 0.85f))
                        } else {
                            listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.65f))
                        }
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
        )
    }
}

/**
 * Interactive iOS-style Segmented Control row with liquid glass selection pills.
 */
@Composable
fun <T> LiquidGlassSegmentedRow(
    items: List<T>,
    selectedItem: T,
    onSelectItem: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    accentColor: Color = CyanGlow
) {
    val config = LocalLiquidGlassConfig.current
    val outerShape = remember { RoundedCornerShape(percent = 50) }
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val density = LocalDensity.current
    val touchState = rememberLiquidTouchState()
    val touchPhysicsEnabled = config.liquidTouchPhysics && !config.reducedMotion

    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidTouchGesture(
                touchState = touchState,
                enabled = touchPhysicsEnabled,
                maxDisplacementPx = with(density) { 18.dp.toPx() }
            )
            .graphicsLayer {
                if (touchPhysicsEnabled) {
                    translationX = touchState.offsetXValue * 0.85f
                    translationY = touchState.offsetYValue * 0.85f
                    scaleX = touchState.scaleXValue
                    scaleY = touchState.scaleYValue
                }
            }
            .clip(outerShape)
            .background(SlateGlassDark.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), outerShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            accentColor.copy(alpha = 0.42f),
                                            IndigoPulse.copy(alpha = 0.28f)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(Color.White.copy(alpha = 0.55f), accentColor.copy(alpha = 0.75f))
                                    ),
                                    pillShape
                                )
                        } else Modifier
                    )
                    .clickable { onSelectItem(item) }
                    .padding(vertical = 7.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Interactive Liquid Glass Slider with glass capsule housing, hardware-layer liquid touch squish, and glowing thumb.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    accentColor: Color = CyanGlow,
    modifier: Modifier = Modifier
) {
    val config = LocalLiquidGlassConfig.current
    val capsuleShape = remember { RoundedCornerShape(percent = 50) }
    val density = LocalDensity.current
    val touchState = rememberLiquidTouchState()
    val touchPhysicsEnabled = config.liquidTouchPhysics && !config.reducedMotion

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidTouchGesture(
                touchState = touchState,
                enabled = touchPhysicsEnabled,
                maxDisplacementPx = with(density) { 14.dp.toPx() }
            )
            .graphicsLayer {
                if (touchPhysicsEnabled) {
                    translationX = touchState.offsetXValue * 0.45f
                    translationY = touchState.offsetYValue * 0.75f
                    scaleX = touchState.scaleXValue
                    scaleY = touchState.scaleYValue
                }
            }
            .clip(capsuleShape)
            .background(SlateGlassDark.copy(alpha = 0.52f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), capsuleShape)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Liquid glass dialog modal with Kyant0 backdrop lens refraction and frosted blur.
 */
@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val config = LocalLiquidGlassConfig.current
    val backdrop = LocalContentBackdrop.current ?: LocalBackdrop.current
    val shape = remember { RoundedCornerShape(28.dp) }
    val density = LocalDensity.current
    val blurPx = with(density) { config.blurRadiusDp.coerceIn(0f, 40f).dp.toPx() }
    val lensHeightPx = with(density) { config.refractionHeightDp.dp.toPx() }
    val lensAmountPx = with(density) { config.refractionAmountDp.dp.toPx() }

    val backdropModifier = if (backdrop != null && config.visualEffectsQuality != "LOW") {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                if (config.blurRadiusDp > 0f) blur(blurPx)
                if (lensAmountPx > 1f) {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = true,
                        chromaticAberration = config.chromaticAberration
                    )
                }
            },
            onDrawSurface = {
                drawRect(SlateGlassDark.copy(alpha = 0.68f))
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            CyanGlow.copy(alpha = 0.14f),
                            IndigoPulse.copy(alpha = 0.08f)
                        )
                    )
                )
            }
        )
    } else {
        Modifier
            .clip(shape)
            .background(SlateGlassDark.copy(alpha = 0.94f))
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 520.dp)
                .then(backdropModifier)
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                CyanGlow.copy(alpha = 0.28f),
                                GlassBorderBottom
                            )
                        )
                    ),
                    shape
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                content()
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}

/**
 * iOS-style Liquid Glass Bottom Sheet using Kyant0 backdrop blur & refraction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val config = LocalLiquidGlassConfig.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val backdrop = LocalContentBackdrop.current ?: LocalBackdrop.current
    val shape = remember { RoundedCornerShape(28.dp) }
    val density = LocalDensity.current
    val blurPx = with(density) { config.blurRadiusDp.coerceIn(0f, 40f).dp.toPx() }
    val lensHeightPx = with(density) { config.refractionHeightDp.dp.toPx() }
    val lensAmountPx = with(density) { config.refractionAmountDp.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        val backdropModifier = if (backdrop != null && config.visualEffectsQuality != "LOW") {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    if (config.blurRadiusDp > 0f) blur(blurPx)
                    if (lensAmountPx > 1f) {
                        lens(
                            refractionHeight = lensHeightPx,
                            refractionAmount = lensAmountPx,
                            depthEffect = true,
                            chromaticAberration = config.chromaticAberration
                        )
                    }
                },
                onDrawSurface = {
                    drawRect(SlateGlassDark.copy(alpha = 0.68f))
                    drawRect(CyanGlow.copy(alpha = 0.10f))
                }
            )
        } else {
            Modifier
                .clip(shape)
                .background(SlateGlassDark.copy(alpha = 0.94f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .then(backdropModifier)
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.42f), GlassBorderBottom)
                        )
                    ),
                    shape
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(5.dp)
                        .clip(Capsule())
                        .background(Color.White.copy(alpha = 0.38f))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}

/**
 * Liquid Glass section header with accent subtitle.
 */
@Composable
fun LiquidGlassSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
