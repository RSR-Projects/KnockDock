package com.example.knockdock.sensor

import com.example.knockdock.model.ImpactEvent
import kotlin.math.abs
import kotlin.math.max

/**
 * Detects discrete desk impact events from filtered accelerometer impulses, 3D gravity orientation,
 * and gyroscope stability while rejecting hand-holding movement, carrying, and tilt.
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

    // Slow smoothed gyroscope velocity (EMA) to distinguish continuous hand-carrying rotation
    // from brief camera-bump micro-rocking when knocking on a desk
    private var smoothedGyroVel = 0f

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
     * Returns true if the device is currently stationary and flat on a desk surface.
     */
    fun isDeviceStationaryOnDesk(
        gyroAngularVelocity: Float,
        tiltAngleDegrees: Float = 0f,
        gravityInstability: Float = 0f,
        timestampNanos: Long = 0L
    ): Boolean {
        val isGyroQuiet = gyroAngularVelocity <= 1.25f && smoothedGyroVel <= 0.65f
        val isFlatOnSurface = tiltAngleDegrees <= 30.0f
        val isGravityStable = gravityInstability <= 0.65f

        return isGyroQuiet && isFlatOnSurface && isGravityStable
    }

    /**
     * Process filtered sample and check for impact triggers.
     * @param gyroAngularVelocity Current gyroscope magnitude in rad/s.
     * @return Detected ImpactEvent if a genuine desk impact finished its decay window, null otherwise.
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

        // Update smoothed gyroscope baseline when not inside a sharp knock spike
        if (!inImpactWindow && impulse < effectiveThreshold) {
            smoothedGyroVel = (0.85f * smoothedGyroVel) + (0.15f * gyroAngularVelocity)
        }

        if (gyroStationaryFilterEnabled) {
            // 1. Hand / Wrist Rotation & Tilt Rejection:
            // - Reject if phone is tilted in hand (> 30 degrees from horizontal surface)
            // - Reject if instantaneous gyroscope rotation is high (> 1.35 rad/s, ~77 deg/s)
            // - Reject if continuous smoothed hand rotation is active (> 0.65 rad/s)
            // Brief camera-bump micro-rocking during a physical desk knock is permitted.
            val isTiltedInHand = !isSynthetic && filteredResult.tiltAngleDegrees > 30.0f
            val isRotating = gyroAngularVelocity > 1.35f || (!isSynthetic && smoothedGyroVel > 0.65f)

            if (isRotating || isTiltedInHand) {
                inImpactWindow = false
                return null
            }
        }

        // 2. Dead-time check: don't start new impact within 80ms of previous detected impact
        if (lastImpactEmitTimeNanos > 0L &&
            timestampNanos >= lastImpactEmitTimeNanos &&
            (timestampNanos - lastImpactEmitTimeNanos) < minInterImpactDeadTimeNanos
        ) {
            return null
        }

        if (!inImpactWindow) {
            // Check if impulse crossed the trigger threshold
            if (impulse >= effectiveThreshold) {
                inImpactWindow = true
                impactStartTimeNanos = timestampNanos
                peakImpulseInWindow = impulse
                accumulatedEnergy = impulse
                val absX = abs(filteredResult.filteredX)
                val absY = abs(filteredResult.filteredY)
                val absZ = abs(filteredResult.filteredZ)
                dominantAxis = if (absZ >= absX && absZ >= absY) "Z" else if (absX >= absY) "X" else "Y"
            }
        } else {
            // Inside an active impact candidate transient
            val durationMs = ((timestampNanos - impactStartTimeNanos) / 1_000_000L).coerceAtLeast(0L)
            accumulatedEnergy += impulse
            peakImpulseInWindow = max(peakImpulseInWindow, impulse)

            // 3. Reject sustained shaking (> 150ms of continuous high acceleration)
            if (durationMs > 150L) {
                inImpactWindow = false
                return null
            }

            // 4. Complete impact when impulse decays below 45% of peak OR reaches typical 55ms acoustic window
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
        smoothedGyroVel = 0f
    }
}
