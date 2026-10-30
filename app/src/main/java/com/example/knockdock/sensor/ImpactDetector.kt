package com.example.knockdock.sensor

import com.example.knockdock.model.ImpactEvent
import kotlin.math.abs
import kotlin.math.max

/**
 * Detects discrete desk impact events from filtered accelerometer impulses and gyroscope stability.
 */
class ImpactDetector(
    var baseThreshold: Float = 2.2f,
    var sensitivity: Float = 0.65f, // 0.1 to 1.0
    var gyroStationaryFilterEnabled: Boolean = true
) {
    // Current state in impact candidate evaluation
    private var inImpactWindow = false
    private var impactStartTimeNanos = 0L
    private var peakImpulseInWindow = 0f
    private var accumulatedEnergy = 0f
    private var dominantAxis = "Z"

    // Refractory period between adjacent impacts (to avoid splitting one ring-down into multiple knocks)
    private var lastImpactEmitTimeNanos = 0L
    private val minInterImpactDeadTimeNanos = 80_000_000L // 80 ms dead time

    /**
     * Effective threshold based on sensitivity and current noise floor.
     * Higher sensitivity lowers the required impulse delta.
     */
    fun calculateEffectiveThreshold(noiseFloor: Float): Float {
        // Sensitivity 0.1 -> factor 1.8; Sensitivity 1.0 -> factor 0.35
        val sensitivityFactor = (1.1f - (sensitivity * 0.75f))
        return (noiseFloor * 1.5f) + (baseThreshold * sensitivityFactor)
    }

    /**
     * Process filtered sample and check for impact triggers.
     * @param gyroAngularVelocity Current gyroscope magnitude in rad/s.
     * @return Detected ImpactEvent if an impact finished its candidate window, null otherwise.
     */
    @Synchronized
    fun processImpulse(
        timestampNanos: Long,
        filteredResult: VibrationFilter.FilteredResult,
        gyroAngularVelocity: Float,
        isSynthetic: Boolean = false
    ): ImpactEvent? {
        val impulse = filteredResult.impulse
        val effectiveThreshold = calculateEffectiveThreshold(filteredResult.currentNoiseFloor)

        // 1. Gyroscope rejection: if phone is being tilted or picked up (> 1.2 rad/s), reject desk knock
        if (gyroStationaryFilterEnabled && gyroAngularVelocity > 1.2f) {
            inImpactWindow = false
            return null
        }

        // 2. Dead-time check: don't start new impact within 80ms of previous detected impact
        if (timestampNanos - lastImpactEmitTimeNanos < minInterImpactDeadTimeNanos) {
            return null
        }

        if (!inImpactWindow) {
            // Check if impulse crossed the trigger threshold
            if (impulse >= effectiveThreshold) {
                inImpactWindow = true
                impactStartTimeNanos = timestampNanos
                peakImpulseInWindow = impulse
                accumulatedEnergy = impulse

                // Determine dominant axis
                val absX = abs(filteredResult.filteredX)
                val absY = abs(filteredResult.filteredY)
                val absZ = abs(filteredResult.filteredZ)
                dominantAxis = if (absZ >= absX && absZ >= absY) "Z" else if (absX >= absY) "X" else "Y"
            }
        } else {
            // We are inside an active impact transient
            val durationMs = (timestampNanos - impactStartTimeNanos) / 1_000_000L
            accumulatedEnergy += impulse
            peakImpulseInWindow = max(peakImpulseInWindow, impulse)

            // Desk knock transients typically decay quickly (< 60ms).
            // If the signal remains high for > 150ms, it is continuous desk vibration (e.g. typing or phone vibrating), NOT a knock.
            if (durationMs > 150L) {
                // Reject continuous sustained vibration
                inImpactWindow = false
                return null
            }

            // Check if transient has decayed back below 40% of peak or 70ms has elapsed
            val hasDecayed = impulse < (peakImpulseInWindow * 0.45f) || durationMs >= 55L
            if (hasDecayed) {
                inImpactWindow = false
                lastImpactEmitTimeNanos = timestampNanos

                return ImpactEvent(
                    timestampNanos = impactStartTimeNanos,
                    peakAmplitude = peakImpulseInWindow,
                    durationMs = max(10L, durationMs),
                    energy = accumulatedEnergy,
                    dominantAxis = dominantAxis,
                    isSynthetic = isSynthetic
                )
            }
        }

        return null
    }

    @Synchronized
    fun reset() {
        inImpactWindow = false
        impactStartTimeNanos = 0L
        peakImpulseInWindow = 0f
        accumulatedEnergy = 0f
        lastImpactEmitTimeNanos = 0L
    }
}
