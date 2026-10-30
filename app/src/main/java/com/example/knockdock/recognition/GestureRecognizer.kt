package com.example.knockdock.recognition

import com.example.knockdock.model.GestureDefinition
import com.example.knockdock.model.GestureDetectionEvent
import com.example.knockdock.model.GestureType
import com.example.knockdock.model.ImpactEvent
import com.example.knockdock.model.KnockSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * State machine and temporal clustering engine for knock gesture recognition.
 */
class GestureRecognizer(
    private val scope: CoroutineScope
) {
    private val _detectedGestures = MutableSharedFlow<GestureDetectionEvent>(extraBufferCapacity = 32)
    val detectedGestures: SharedFlow<GestureDetectionEvent> = _detectedGestures.asSharedFlow()

    // Configurable parameters
    var cooldownMs: Long = 750L
    var minConfidenceThreshold: Float = 0.65f
    var isEnabled: Boolean = true

    // Active gestures in current profile
    private var activeGestures: List<GestureDefinition> = GestureDefinition.defaultPresets()

    // Impact buffering
    private val currentCandidateImpacts = mutableListOf<ImpactEvent>()
    private var windowFinalizerJob: Job? = null
    private var lastTriggerEpochMs = 0L

    // Inter-knock timing bounds
    private val interKnockTimeoutMs = 380L // Wait up to 380ms after last impact before deciding gesture

    fun updateActiveGestures(gestures: List<GestureDefinition>) {
        this.activeGestures = gestures
    }

    fun updateSettings(settings: KnockSettings) {
        this.cooldownMs = settings.cooldownMs
        this.minConfidenceThreshold = settings.confidenceThreshold
    }

    /**
     * Process an incoming impact event.
     */
    @Synchronized
    fun onImpact(impact: ImpactEvent) {
        if (!isEnabled) return

        val nowEpochMs = System.currentTimeMillis()

        // Check if we are currently in post-action cooldown
        if (nowEpochMs - lastTriggerEpochMs < cooldownMs) {
            return
        }

        currentCandidateImpacts.add(impact)

        // Reset or restart window finalization timer
        windowFinalizerJob?.cancel()
        windowFinalizerJob = scope.launch {
            delay(interKnockTimeoutMs)
            finalizeGestureCandidate()
        }
    }

    /**
     * Invoked when inter-knock timeout elapses with no subsequent impacts.
     */
    @Synchronized
    private fun finalizeGestureCandidate() {
        if (currentCandidateImpacts.isEmpty()) return

        val impacts = currentCandidateImpacts.toList()
        currentCandidateImpacts.clear()

        val impactCount = impacts.size
        val intervalsMs = mutableListOf<Long>()
        var totalPeak = 0f

        for (i in 0 until impacts.size) {
            totalPeak += impacts[i].peakAmplitude
            if (i > 0) {
                val gapMs = (impacts[i].timestampNanos - impacts[i - 1].timestampNanos) / 1_000_000L
                intervalsMs.add(gapMs)
            }
        }

        val avgPeak = totalPeak / impactCount
        val isSynthetic = impacts.any { it.isSynthetic }

        // Match against active gestures
        val match = matchGesture(impactCount, intervalsMs, avgPeak)

        if (match != null && match.confidence >= minConfidenceThreshold) {
            lastTriggerEpochMs = System.currentTimeMillis()
            val event = GestureDetectionEvent(
                gestureId = match.gesture.id,
                gestureName = match.gesture.name,
                type = match.gesture.type,
                confidence = match.confidence,
                timestampNanos = impacts.last().timestampNanos,
                impactCount = impactCount,
                intervalsMs = intervalsMs,
                averagePeak = avgPeak,
                isSynthetic = isSynthetic
            )
            scope.launch {
                _detectedGestures.emit(event)
            }
        }
    }

    private data class MatchResult(val gesture: GestureDefinition, val confidence: Float)

    private fun matchGesture(
        impactCount: Int,
        intervalsMs: List<Long>,
        avgPeak: Float
    ): MatchResult? {
        var bestMatch: MatchResult? = null
        var highestScore = 0f

        for (gesture in activeGestures) {
            val score = evaluateGestureScore(gesture, impactCount, intervalsMs, avgPeak)
            if (score > highestScore) {
                highestScore = score
                bestMatch = MatchResult(gesture, score)
            }
        }

        return bestMatch
    }

    /**
     * Compute confidence score based on impact count, rhythm regularities, and template distance.
     */
    private fun evaluateGestureScore(
        gesture: GestureDefinition,
        impactCount: Int,
        intervalsMs: List<Long>,
        avgPeak: Float
    ): Float {
        // Must match expected impact count
        if (gesture.expectedImpacts != impactCount) {
            return 0f
        }

        when (gesture.type) {
            GestureType.SINGLE_KNOCK -> {
                // Single knock: confidence is higher when the single peak was distinct and crisp
                val peakScore = min(1.0f, avgPeak / 3.0f) * 0.3f
                return min(0.95f, 0.70f + peakScore)
            }
            GestureType.DOUBLE_KNOCK -> {
                if (intervalsMs.isEmpty()) return 0f
                val interval = intervalsMs[0]
                // Ideal double knock interval: 120ms to 380ms
                if (interval < 80L || interval > 550L) return 0.25f

                val idealDelta = abs(interval - 240L)
                val timingScore = max(0f, 1f - (idealDelta / 300f))
                return min(0.98f, 0.70f + (timingScore * 0.25f))
            }
            GestureType.TRIPLE_KNOCK -> {
                if (intervalsMs.size < 2) return 0f
                val int1 = intervalsMs[0]
                val int2 = intervalsMs[1]
                if (int1 < 80L || int1 > 500L || int2 < 80L || int2 > 500L) return 0.25f

                // Human rhythm consistency: interval ratio int1/int2 should be close to 1.0
                val ratio = int1.toFloat() / max(1f, int2.toFloat())
                val ratioConsistency = max(0f, 1f - abs(1.0f - ratio))
                return min(0.98f, 0.72f + (ratioConsistency * 0.24f))
            }
            GestureType.CUSTOM_PATTERN -> {
                if (gesture.templateIntervalsMs.isEmpty() || intervalsMs.size != gesture.templateIntervalsMs.size) {
                    return 0.5f
                }
                var totalDeviation = 0f
                for (i in intervalsMs.indices) {
                    val expected = gesture.templateIntervalsMs[i]
                    val actual = intervalsMs[i]
                    totalDeviation += abs(expected - actual) / max(1f, expected.toFloat())
                }
                val avgDeviation = totalDeviation / intervalsMs.size
                return max(0.1f, min(0.98f, 1.0f - (avgDeviation * 0.8f)))
            }
        }
    }

    @Synchronized
    fun reset() {
        windowFinalizerJob?.cancel()
        currentCandidateImpacts.clear()
        lastTriggerEpochMs = 0L
    }
}
