package com.example.knockdock.sensor

import com.example.knockdock.model.GestureType
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates and replays authentic desk vibration sensor waveforms (single knock, double knock,
 * triple knock, keyboard typing noise, and desk bump) for interactive testing and emulation.
 * Synthetic data is explicitly tagged (isSynthetic = true).
 */
class SensorReplayer {

    enum class ReplayPreset(val displayName: String, val description: String) {
        SINGLE_KNOCK("Single Knock", "Crisp single impact (1.8g peak)"),
        DOUBLE_KNOCK("Double Knock", "Standard double tap (220ms interval)"),
        TRIPLE_KNOCK("Triple Knock", "Standard triple tap (180ms intervals)"),
        KEYBOARD_TYPING("Typing Noise", "Continuous light rumble (<0.8g, rejected)"),
        DESK_BUMP("Desk Bump / Shift", "Slow heavy tilt with gyro spin (rejected)")
    }

    /**
     * Synthesizes accelerometer samples for a chosen preset.
     * Invokes [onSample] for each simulated sample at 10ms intervals (100 Hz).
     */
    suspend fun replay(
        preset: ReplayPreset,
        onSample: (x: Float, y: Float, z: Float, gyroVel: Float, isSynthetic: Boolean) -> Unit
    ) {
        when (preset) {
            ReplayPreset.SINGLE_KNOCK -> {
                simulateBaseline(30, onSample)
                simulateKnockBurst(peakG = 3.2f, durationSamples = 6, onSample)
                simulateBaseline(50, onSample)
            }
            ReplayPreset.DOUBLE_KNOCK -> {
                simulateBaseline(20, onSample)
                simulateKnockBurst(peakG = 3.0f, durationSamples = 6, onSample)
                simulateBaseline(22, onSample) // 220ms gap
                simulateKnockBurst(peakG = 2.9f, durationSamples = 6, onSample)
                simulateBaseline(40, onSample)
            }
            ReplayPreset.TRIPLE_KNOCK -> {
                simulateBaseline(20, onSample)
                simulateKnockBurst(peakG = 3.1f, durationSamples = 5, onSample)
                simulateBaseline(18, onSample) // 180ms gap
                simulateKnockBurst(peakG = 3.0f, durationSamples = 5, onSample)
                simulateBaseline(18, onSample) // 180ms gap
                simulateKnockBurst(peakG = 2.8f, durationSamples = 5, onSample)
                simulateBaseline(40, onSample)
            }
            ReplayPreset.KEYBOARD_TYPING -> {
                // Low amplitude chatter (< 0.6g), below knock threshold
                for (i in 0..80) {
                    val noiseX = (sin(i * 1.3f) * 0.4f)
                    val noiseY = (sin(i * 0.9f) * 0.3f)
                    val noiseZ = 9.8f + (sin(i * 2.1f) * 0.5f)
                    onSample(noiseX, noiseY, noiseZ, 0.05f, true)
                    delay(10)
                }
            }
            ReplayPreset.DESK_BUMP -> {
                // Heavy tilt with gyro angular velocity > 1.8 rad/s (triggers stationary filter rejection)
                for (i in 0..60) {
                    val angle = (i / 60f) * 0.8f
                    val x = sin(angle) * 9.8f
                    val z = kotlin.math.cos(angle) * 9.8f
                    val gyroSpin = 2.0f * (1f - (i / 60f)) // High gyro rotation
                    onSample(x, 0.5f, z, gyroSpin, true)
                    delay(10)
                }
            }
        }
    }

    private suspend fun simulateBaseline(
        count: Int,
        onSample: (Float, Float, Float, Float, Boolean) -> Unit
    ) {
        for (i in 0 until count) {
            val jitter = (sin(i * 0.5f) * 0.08f)
            onSample(jitter, jitter * 0.5f, 9.8f + jitter, 0.02f, true)
            delay(10)
        }
    }

    private suspend fun simulateKnockBurst(
        peakG: Float,
        durationSamples: Int,
        onSample: (Float, Float, Float, Float, Boolean) -> Unit
    ) {
        for (i in 0 until durationSamples) {
            // Damped harmonic shock impulse: A * exp(-t/decay) * sin(omega * t)
            val envelope = exp(-i * 0.65f)
            val impulse = peakG * 9.8f * envelope
            val x = (impulse * 0.25f) * sin(i * 1.5f)
            val y = (impulse * 0.20f) * sin(i * 1.8f)
            val z = 9.8f + impulse
            onSample(x, y, z, 0.08f, true) // Gyroscope remains low (phone resting flat)
            delay(10)
        }
    }
}
