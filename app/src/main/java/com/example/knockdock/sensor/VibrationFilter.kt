package com.example.knockdock.sensor

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * High-pass filter and vibration energy calculator to isolate desk knock transients
 * from static gravity and slow device tilt.
 */
class VibrationFilter(
    private val sampleRateHz: Float = 100f,
    private val cutoffFrequencyHz: Float = 12.0f
) {
    // RC high-pass filter time constant
    private val dt = 1.0f / sampleRateHz
    private val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffFrequencyHz)
    private val alpha = rc / (rc + dt)

    // Previous raw samples
    private var prevRawX = 0f
    private var prevRawY = 0f
    private var prevRawZ = 9.8f
    private var isInitialized = false

    // Previous high-pass filtered samples
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f

    // Slow low-pass 3D gravity vector estimate (only updated during quiet intervals so knocks never distort it)
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.8f
    private val gravityAlpha = 0.03f

    // Adaptive noise floor (exponential moving average)
    private var noiseFloor = 0.25f
    private val noiseFloorAlpha = 0.02f // Slow adaptation

    /**
     * Process raw accelerometer reading (in m/s^2).
     * Returns the high-pass filtered vibration impulse magnitude and slow gravity tilt angle.
     */
    @Synchronized
    fun processSample(x: Float, y: Float, z: Float): FilteredResult {
        if (!isInitialized) {
            prevRawX = x
            prevRawY = y
            prevRawZ = z
            if (x != 0f || y != 0f || z != 0f) {
                gravityX = x
                gravityY = y
                gravityZ = z
            }
            isInitialized = true
        }

        // High-pass filter: y[n] = alpha * (y[n-1] + x[n] - x[n-1])
        filteredX = alpha * (filteredX + x - prevRawX)
        filteredY = alpha * (filteredY + y - prevRawY)
        filteredZ = alpha * (filteredZ + z - prevRawZ)

        prevRawX = x
        prevRawY = y
        prevRawZ = z

        // Vibration impulse magnitude (scalar energy)
        val impulse = sqrt(filteredX * filteredX + filteredY * filteredY + filteredZ * filteredZ)

        // Update slow low-pass gravity vector ONLY when not inside a sharp knock spike
        if (impulse < 2.0f) {
            gravityX = (1f - gravityAlpha) * gravityX + gravityAlpha * x
            gravityY = (1f - gravityAlpha) * gravityY + gravityAlpha * y
            gravityZ = (1f - gravityAlpha) * gravityZ + gravityAlpha * z
        }

        // Compute tilt angle from flat horizontal surface (0 deg = flat face-up or face-down)
        val gMag = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ).coerceAtLeast(0.1f)
        val cosTilt = (abs(gravityZ) / gMag).coerceIn(0f, 1f)
        val tiltAngleDeg = Math.toDegrees(acos(cosTilt.toDouble())).toFloat()

        // Slowly adapt noise floor when signal is quiet
        if (impulse < noiseFloor * 2.5f) {
            noiseFloor = (1f - noiseFloorAlpha) * noiseFloor + noiseFloorAlpha * impulse
            noiseFloor = max(0.08f, noiseFloor)
        }

        return FilteredResult(
            impulse = impulse,
            filteredX = filteredX,
            filteredY = filteredY,
            filteredZ = filteredZ,
            currentNoiseFloor = noiseFloor,
            tiltAngleDegrees = tiltAngleDeg,
            gravityInstability = 0f
        )
    }

    @Synchronized
    fun reset() {
        prevRawX = 0f
        prevRawY = 0f
        prevRawZ = 9.8f
        isInitialized = false
        filteredX = 0f
        filteredY = 0f
        filteredZ = 0f
        gravityX = 0f
        gravityY = 0f
        gravityZ = 9.8f
        noiseFloor = 0.25f
    }

    data class FilteredResult(
        val impulse: Float,
        val filteredX: Float,
        val filteredY: Float,
        val filteredZ: Float,
        val currentNoiseFloor: Float,
        val tiltAngleDegrees: Float = 0f,
        val gravityInstability: Float = 0f
    )
}
