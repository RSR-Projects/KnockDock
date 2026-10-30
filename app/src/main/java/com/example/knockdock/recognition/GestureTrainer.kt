package com.example.knockdock.recognition

import com.example.knockdock.model.GestureCategory
import com.example.knockdock.model.GestureDefinition
import com.example.knockdock.model.GestureTrial
import com.example.knockdock.model.GestureType
import com.example.knockdock.model.ImpactEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Interactive gesture trainer state machine to record, calibrate, and synthesize custom gesture templates.
 */
class GestureTrainer {

    data class TrainerState(
        val gestureName: String = "",
        val category: GestureCategory = GestureCategory.CUSTOM,
        val recordedTrials: List<GestureTrial> = emptyList(),
        val negativeTrials: List<GestureTrial> = emptyList(),
        val isRecordingActive: Boolean = false,
        val isRecordingNegative: Boolean = false,
        val currentTrialImpacts: List<ImpactEvent> = emptyList(),
        val calculatedAverageIntervalsMs: List<Long> = emptyList(),
        val recommendedMinConfidence: Float = 0.70f,
        val validationMessage: String = "Tap 'Start Recording' and knock your desired pattern on the desk."
    )

    private val _state = MutableStateFlow(TrainerState())
    val state: StateFlow<TrainerState> = _state.asStateFlow()

    fun setGestureInfo(name: String, category: GestureCategory) {
        _state.value = _state.value.copy(gestureName = name, category = category)
    }

    fun startRecording(isNegative: Boolean = false) {
        _state.value = _state.value.copy(
            isRecordingActive = true,
            isRecordingNegative = isNegative,
            currentTrialImpacts = emptyList(),
            validationMessage = if (isNegative) {
                "Perform negative activity (e.g., type on keyboard or nudge desk)..."
            } else {
                "Knock your pattern firmly on the desk..."
            }
        )
    }

    fun onImpactRecorded(impact: ImpactEvent) {
        if (!_state.value.isRecordingActive) return

        val updated = _state.value.currentTrialImpacts + impact
        _state.value = _state.value.copy(
            currentTrialImpacts = updated,
            validationMessage = "Impact registered (${updated.size} knocks recorded so far)."
        )
    }

    fun finishCurrentTrial() {
        val impacts = _state.value.currentTrialImpacts
        if (impacts.isEmpty()) {
            _state.value = _state.value.copy(
                isRecordingActive = false,
                validationMessage = "No impacts detected in trial. Try again."
            )
            return
        }

        val intervals = mutableListOf<Long>()
        val peaks = impacts.map { it.peakAmplitude }
        for (i in 1 until impacts.size) {
            val gap = (impacts[i].timestampNanos - impacts[i - 1].timestampNanos) / 1_000_000L
            intervals.add(gap)
        }

        val trial = GestureTrial(
            trialNumber = if (_state.value.isRecordingNegative) _state.value.negativeTrials.size + 1 else _state.value.recordedTrials.size + 1,
            impactCount = impacts.size,
            intervalsMs = intervals,
            peakAmplitudes = peaks,
            isNegativeSample = _state.value.isRecordingNegative
        )

        if (_state.value.isRecordingNegative) {
            val newNegative = _state.value.negativeTrials + trial
            _state.value = _state.value.copy(
                negativeTrials = newNegative,
                isRecordingActive = false,
                currentTrialImpacts = emptyList(),
                validationMessage = "Negative sample #${trial.trialNumber} saved."
            )
        } else {
            val newRecorded = _state.value.recordedTrials + trial
            // Recompute average intervals
            val avgIntervals = calculateMeanIntervals(newRecorded)
            _state.value = _state.value.copy(
                recordedTrials = newRecorded,
                isRecordingActive = false,
                currentTrialImpacts = emptyList(),
                calculatedAverageIntervalsMs = avgIntervals,
                validationMessage = "Trial #${trial.trialNumber} recorded (${impacts.size} knocks, ${intervals.joinToString(", ")} ms gaps)."
            )
        }
    }

    private fun calculateMeanIntervals(trials: List<GestureTrial>): List<Long> {
        if (trials.isEmpty()) return emptyList()
        val firstCount = trials.first().intervalsMs.size
        // Ensure all trials have consistent interval count
        val consistent = trials.filter { it.intervalsMs.size == firstCount }
        if (consistent.isEmpty() || firstCount == 0) return emptyList()

        val result = mutableListOf<Long>()
        for (i in 0 until firstCount) {
            var sum = 0L
            for (trial in consistent) {
                sum += trial.intervalsMs[i]
            }
            result.add(sum / consistent.size)
        }
        return result
    }

    /**
     * Create the final trained GestureDefinition.
     */
    fun buildTrainedGesture(): GestureDefinition? {
        val current = _state.value
        val name = current.gestureName.trim().ifEmpty { "Custom Gesture" }
        val trials = current.recordedTrials
        if (trials.isEmpty()) return null

        val expectedImpacts = trials.map { it.impactCount }.groupBy { it }.maxByOrNull { it.value.size }?.key ?: 1
        val avgIntervals = current.calculatedAverageIntervalsMs

        val type = when (expectedImpacts) {
            1 -> GestureType.SINGLE_KNOCK
            2 -> GestureType.DOUBLE_KNOCK
            3 -> GestureType.TRIPLE_KNOCK
            else -> GestureType.CUSTOM_PATTERN
        }

        return GestureDefinition(
            id = "gesture_custom_${UUID.randomUUID().toString().take(8)}",
            name = name,
            description = "Trained with ${trials.size} positive and ${current.negativeTrials.size} negative samples.",
            type = type,
            category = current.category,
            expectedImpacts = expectedImpacts,
            minIntervalMs = (avgIntervals.minOrNull() ?: 100L) * 7 / 10,
            maxIntervalMs = (avgIntervals.maxOrNull() ?: 450L) * 13 / 10,
            minConfidence = current.recommendedMinConfidence,
            cooldownMs = 750L,
            isSystemPreset = false,
            trainingTrialCount = trials.size,
            templateIntervalsMs = avgIntervals
        )
    }

    fun reset() {
        _state.value = TrainerState()
    }
}
