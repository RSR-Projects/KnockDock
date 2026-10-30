package com.example.knockdock.sensor

import kotlin.math.max
import kotlin.math.sqrt

/**
 * High-pass filter and vibration energy calculator to isolate desk knock transients from gravity and slow device tilt.
 */
class VibrationFilter(
    private val sampleRateHz: Float = 100f,
    private val cutoffFrequencyHz: Float = 12.0f
) {
    // RC filter time constant
    private val dt = 1.0f / sampleRateHz
    private val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffFrequencyHz)
    private val alpha = rc / (rc + dt)

    // Previous raw samples
    private var prevRawX = 0f
    private var prevRawY = 0f
    private var prevRawZ = 9.8f

    // Previous filtered samples
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f

    // Adaptive noise floor (exponential moving average)
    private var noiseFloor = 0.25f
    private val noiseFloorAlpha = 0.02f // Slow adaptation

    /**
     * Process raw accelerometer reading (in m/s^2).
     * Returns the high-pass filtered vibration impulse magnitude.
     */
    @Synchronized
    fun processSample(x: Float, y: Float, z: Float): FilteredResult {
        // High-pass filter: y[n] = alpha * (y[n-1] + x[n] - x[n-1])
        filteredX = alpha * (filteredX + x - prevRawX)
        filteredY = alpha * (filteredY + y - prevRawY)
        filteredZ = alpha * (filteredZ + z - prevRawZ)

        prevRawX = x
        prevRawY = y
        prevRawZ = z

        // Vibration impulse magnitude (scalar energy)
        val impulse = sqrt(filteredX * filteredX + filteredY * filteredY + filteredZ * filteredZ)

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
            currentNoiseFloor = noiseFloor
        )
    }

    @Synchronized
    fun reset() {
        prevRawX = 0f
        prevRawY = 0f
        prevRawZ = 9.8f
        filteredX = 0f
        filteredY = 0f
        filteredZ = 0f
        noiseFloor = 0.25f
    }

    data class FilteredResult(
        val impulse: Float,
        val filteredX: Float,
        val filteredY: Float,
        val filteredZ: Float,
        val currentNoiseFloor: Float
    )
}
