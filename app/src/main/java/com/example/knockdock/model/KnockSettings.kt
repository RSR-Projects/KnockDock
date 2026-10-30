package com.example.knockdock.model

/**
 * Dedicated, independent configuration model for Card Glass / Surface Appearance.
 * Completely decoupled from [NavigationGlassSettings] so changing card blur, opacity,
 * tint, refraction, rim highlights, corner radius, contrast, or lightweight mode
 * never alters the floating bottom navigation bar.
 */
data class CardGlassSettings(
    val cardBlurEnabled: Boolean = true,
    val cardBlurRadiusDp: Float = 16f, // 0f..40f dp
    val cardBackgroundOpacity: Float = 0.46f, // 0.15f..0.95f
    val cardTintColorKey: String = "CYAN", // "CYAN", "VIOLET", "SLATE", "EMERALD", "AMBER", "NONE"
    val cardTintIntensity: Float = 0.16f, // 0f..0.65f
    val cardRefractionIntensity: Float = 0.48f, // 0f..1.0f
    val cardRimHighlightIntensity: Float = 0.50f, // 0f..1.0f
    val cardCornerRadiusDp: Float = 24f, // 8f..36f dp
    val cardSurfaceContrast: Float = 0.50f, // 0f..1.0f
    val cardLightweightMode: Boolean = false
)

/**
 * Dedicated, independent configuration model for the Floating Liquid Glass Bottom Navigation Bar.
 * Completely decoupled from [CardGlassSettings] so changing navigation bar optics never alters cards.
 */
data class NavigationGlassSettings(
    val navBlurEnabled: Boolean = true,
    val navBlurRadiusDp: Float = 18f, // 0f..40f dp
    val navRefractionAmountDp: Float = 38f, // 0f..76f dp
    val navRefractionHeightDp: Float = 18f, // 4f..36f dp
    val navChromaticAberration: Boolean = true,
    val navBackgroundOpacity: Float = 0.30f, // 0.15f..0.85f
    val navRimHighlightIntensity: Float = 0.58f // 0f..1.0f
)

/**
 * Global settings for sensor processing, detection sensitivity, independent Card Glass vs.
 * Navigation Glass visual systems, and hardware/PC feedback.
 */
data class KnockSettings(
    // Sensor & Detection
    val sensitivity: Float = 0.65f, // 0.1 to 1.0 (higher = more sensitive to lighter knocks)
    val baseVibrationThreshold: Float = 2.2f, // m/s^2 impulse delta
    val cooldownMs: Long = 750L, // Cooldown after successful gesture trigger
    val confidenceThreshold: Float = 0.65f, // Minimum confidence (0.4 to 0.95)
    val samplingRate: String = "GAME", // "NORMAL", "UI", "GAME", "FASTEST"
    val gyroStationaryFilter: Boolean = true, // Rejects knocks if phone itself was tilted or moved in hand

    // Background & Battery
    val backgroundMonitoringEnabled: Boolean = true,
    val batteryConsciousMode: Boolean = true, // Lowers sampling rate when idle/screen-off

    // Feedback
    val hapticFeedbackEnabled: Boolean = true,
    val soundFeedbackEnabled: Boolean = false,

    // Global Motion & Floating Dock / Controls Liquid Glass Optics
    val reducedMotion: Boolean = false,
    val visualEffectsQuality: String = "HIGH", // "HIGH" (Translucent Glass + Glow), "BALANCED", "LOW" (Solid Dark)
    val glassBlurRadius: Float = 18f, // 0f to 40f dp Gaussian backdrop blur (Floating Dock & Modals)
    val glassRefractionAmount: Float = 38f, // 0f to 76f dp optical lens refraction distortion
    val glassRefractionHeight: Float = 18f, // 4f to 36f dp lens bezel curvature depth
    val glassChromaticAberration: Boolean = true, // Prismatic RGB dispersion along refracted edges
    val liquidTouchPhysics: Boolean = true, // iOS-style elastic liquid movement & squish on interactive controls

    // Independent Navigation Bar Glass Settings
    val navBlurEnabled: Boolean = true,
    val navBackgroundOpacity: Float = 0.30f,
    val navRimHighlightIntensity: Float = 0.58f,

    // Independent Card Glass & Surface Appearance Settings
    val cardBlurEnabled: Boolean = true,
    val cardBlurRadiusDp: Float = 16f,
    val cardBackgroundOpacity: Float = 0.46f,
    val cardTintColorKey: String = "CYAN",
    val cardTintIntensity: Float = 0.16f,
    val cardRefractionIntensity: Float = 0.48f,
    val cardRimHighlightIntensity: Float = 0.50f,
    val cardCornerRadiusDp: Float = 24f,
    val cardSurfaceContrast: Float = 0.50f,
    val cardLightweightMode: Boolean = false,

    // Computer Actions (Local Network)
    val pcControlEnabled: Boolean = false,
    val pcHost: String = "192.168.1.100",
    val pcPort: Int = 8989,
    val pcAuthToken: String = "",
    val pcDeviceName: String = "My Workstation",
    val isPcPaired: Boolean = false
) {
    fun toCardGlassSettings(): CardGlassSettings = CardGlassSettings(
        cardBlurEnabled = cardBlurEnabled,
        cardBlurRadiusDp = cardBlurRadiusDp,
        cardBackgroundOpacity = cardBackgroundOpacity,
        cardTintColorKey = cardTintColorKey,
        cardTintIntensity = cardTintIntensity,
        cardRefractionIntensity = cardRefractionIntensity,
        cardRimHighlightIntensity = cardRimHighlightIntensity,
        cardCornerRadiusDp = cardCornerRadiusDp,
        cardSurfaceContrast = cardSurfaceContrast,
        cardLightweightMode = cardLightweightMode
    )

    fun toNavigationGlassSettings(): NavigationGlassSettings = NavigationGlassSettings(
        navBlurEnabled = navBlurEnabled,
        navBlurRadiusDp = glassBlurRadius,
        navRefractionAmountDp = glassRefractionAmount,
        navRefractionHeightDp = glassRefractionHeight,
        navChromaticAberration = glassChromaticAberration,
        navBackgroundOpacity = navBackgroundOpacity,
        navRimHighlightIntensity = navRimHighlightIntensity
    )

    fun withCardGlassSettings(card: CardGlassSettings): KnockSettings = copy(
        cardBlurEnabled = card.cardBlurEnabled,
        cardBlurRadiusDp = card.cardBlurRadiusDp,
        cardBackgroundOpacity = card.cardBackgroundOpacity,
        cardTintColorKey = card.cardTintColorKey,
        cardTintIntensity = card.cardTintIntensity,
        cardRefractionIntensity = card.cardRefractionIntensity,
        cardRimHighlightIntensity = card.cardRimHighlightIntensity,
        cardCornerRadiusDp = card.cardCornerRadiusDp,
        cardSurfaceContrast = card.cardSurfaceContrast,
        cardLightweightMode = card.cardLightweightMode
    )

    fun withNavigationGlassSettings(nav: NavigationGlassSettings): KnockSettings = copy(
        navBlurEnabled = nav.navBlurEnabled,
        glassBlurRadius = nav.navBlurRadiusDp,
        glassRefractionAmount = nav.navRefractionAmountDp,
        glassRefractionHeight = nav.navRefractionHeightDp,
        glassChromaticAberration = nav.navChromaticAberration,
        navBackgroundOpacity = nav.navBackgroundOpacity,
        navRimHighlightIntensity = nav.navRimHighlightIntensity
    )
}
