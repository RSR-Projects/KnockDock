package com.example.knockdock.model

/**
 * Recognized gesture archetypes.
 */
enum class GestureType(val displayName: String, val defaultImpactCount: Int) {
    SINGLE_KNOCK("Single Knock", 1),
    DOUBLE_KNOCK("Double Knock", 2),
    TRIPLE_KNOCK("Triple Knock", 3),
    CUSTOM_PATTERN("Custom Rhythm", 0);

    companion object {
        fun fromId(id: String): GestureType = entries.find { it.name == id } ?: SINGLE_KNOCK
    }
}

/**
 * High-level category for organizational clarity.
 */
enum class GestureCategory(val displayName: String) {
    MEDIA("Media & Audio"),
    PRODUCTIVITY("Productivity & Apps"),
    UTILITY("System & Utility"),
    DESK_MACRO("Desk Automation"),
    CUSTOM("Custom")
}

/**
 * Stored gesture configuration and classifier parameters.
 */
data class GestureDefinition(
    val id: String,
    val name: String,
    val description: String,
    val type: GestureType,
    val category: GestureCategory,
    val expectedImpacts: Int,
    val minIntervalMs: Long = 100L,
    val maxIntervalMs: Long = 500L,
    val minConfidence: Float = 0.65f,
    val cooldownMs: Long = 750L,
    val isSystemPreset: Boolean = false,
    val trainingTrialCount: Int = 0,
    val templateIntervalsMs: List<Long> = emptyList()
) {
    companion object {
        val PRESET_SINGLE = GestureDefinition(
            id = "preset_single_knock",
            name = "Single Knock",
            description = "A single crisp impact on the desk surface.",
            type = GestureType.SINGLE_KNOCK,
            category = GestureCategory.DESK_MACRO,
            expectedImpacts = 1,
            minIntervalMs = 0L,
            maxIntervalMs = 0L,
            minConfidence = 0.65f,
            cooldownMs = 650L,
            isSystemPreset = true
        )

        val PRESET_DOUBLE = GestureDefinition(
            id = "preset_double_knock",
            name = "Double Knock",
            description = "Two rapid consecutive knocks (tap-tap).",
            type = GestureType.DOUBLE_KNOCK,
            category = GestureCategory.DESK_MACRO,
            expectedImpacts = 2,
            minIntervalMs = 90L,
            maxIntervalMs = 450L,
            minConfidence = 0.70f,
            cooldownMs = 750L,
            isSystemPreset = true
        )

        val PRESET_TRIPLE = GestureDefinition(
            id = "preset_triple_knock",
            name = "Triple Knock",
            description = "Three consecutive knocks in quick succession (tap-tap-tap).",
            type = GestureType.TRIPLE_KNOCK,
            category = GestureCategory.DESK_MACRO,
            expectedImpacts = 3,
            minIntervalMs = 90L,
            maxIntervalMs = 450L,
            minConfidence = 0.75f,
            cooldownMs = 900L,
            isSystemPreset = true
        )

        fun defaultPresets(): List<GestureDefinition> = listOf(PRESET_SINGLE, PRESET_DOUBLE, PRESET_TRIPLE)
    }
}

/**
 * Output of the gesture classifier.
 */
data class GestureDetectionEvent(
    val gestureId: String,
    val gestureName: String,
    val type: GestureType,
    val confidence: Float,
    val timestampNanos: Long,
    val impactCount: Int,
    val intervalsMs: List<Long>,
    val averagePeak: Float,
    val isSynthetic: Boolean = false
)

/**
 * A recorded trial captured during training.
 */
data class GestureTrial(
    val trialNumber: Int,
    val impactCount: Int,
    val intervalsMs: List<Long>,
    val peakAmplitudes: List<Float>,
    val isNegativeSample: Boolean = false,
    val notes: String = ""
)
