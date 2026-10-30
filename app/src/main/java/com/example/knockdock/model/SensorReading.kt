package com.example.knockdock.model

/**
 * Raw and processed sensor readings from accelerometer and gyroscope.
 */
data class SensorSample(
    val timestampNanos: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Float,
    val isSynthetic: Boolean = false
)

/**
 * A discrete vibration shock/impact event detected on the desk surface.
 */
data class ImpactEvent(
    val timestampNanos: Long,
    val peakAmplitude: Float,
    val durationMs: Long,
    val energy: Float,
    val dominantAxis: String,
    val isSynthetic: Boolean = false
)

/**
 * Real-time telemetry snapshot for UI visualizations and the Sensor Lab.
 */
data class TelemetrySnapshot(
    val timestampNanos: Long = 0L,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelMagnitude: Float = 9.8f,
    val filteredImpulse: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val angularVelocity: Float = 0f,
    val isStationaryOnDesk: Boolean = true,
    val currentNoiseFloor: Float = 0.2f,
    val lastImpactTimeNanos: Long = 0L,
    val isMonitoring: Boolean = false,
    val isReplayMode: Boolean = false
)
