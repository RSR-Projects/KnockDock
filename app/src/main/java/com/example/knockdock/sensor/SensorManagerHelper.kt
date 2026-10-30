package com.example.knockdock.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.knockdock.model.ImpactEvent
import com.example.knockdock.model.KnockSettings
import com.example.knockdock.model.TelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages physical device sensor listeners, sample filtering, and real-time telemetry streaming.
 */
class SensorManagerHelper(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val isAccelerometerAvailable: Boolean = accelerometer != null
    val isGyroscopeAvailable: Boolean = gyroscope != null
    val accelerometerName: String = accelerometer?.name ?: "Unavailable"
    val gyroscopeName: String = gyroscope?.name ?: "Unavailable"

    // Signal pipeline
    private val filter = VibrationFilter()
    val impactDetector = ImpactDetector()

    // State flows
    private val _telemetryState = MutableStateFlow(TelemetrySnapshot())
    val telemetryState: StateFlow<TelemetrySnapshot> = _telemetryState.asStateFlow()

    private val _impactEvents = MutableSharedFlow<ImpactEvent>(extraBufferCapacity = 64)
    val impactEvents: SharedFlow<ImpactEvent> = _impactEvents.asSharedFlow()

    private var isRegistered = false
    private var lastGyroVel = 0f
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f
    private var lastUiTelemetryEmitNanos = 0L

    // Waveform history buffer for the Sensor Lab graph (circular ring buffer)
    val waveformBuffer = FloatArray(300) { 0f }
    var bufferWriteIndex = 0

    fun updateSettings(settings: KnockSettings) {
        impactDetector.baseThreshold = settings.baseVibrationThreshold
        impactDetector.sensitivity = settings.sensitivity
        impactDetector.gyroStationaryFilterEnabled = settings.gyroStationaryFilter
    }

    /**
     * Start physical sensor listening with the chosen rate.
     */
    @Synchronized
    fun startListening(samplingRateStr: String = "GAME") {
        if (isRegistered || sensorManager == null) return

        val delay = when (samplingRateStr.uppercase()) {
            "FASTEST" -> SensorManager.SENSOR_DELAY_FASTEST
            "GAME" -> SensorManager.SENSOR_DELAY_GAME
            "UI" -> SensorManager.SENSOR_DELAY_UI
            else -> SensorManager.SENSOR_DELAY_NORMAL
        }

        accelerometer?.let {
            sensorManager.registerListener(this, it, delay)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, delay)
        }

        filter.reset()
        impactDetector.reset()
        isRegistered = true
        _telemetryState.value = _telemetryState.value.copy(isMonitoring = true)
    }

    /**
     * Stop listening to conserve battery.
     */
    @Synchronized
    fun stopListening() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        _telemetryState.value = _telemetryState.value.copy(isMonitoring = false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
                lastGyroVel = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val timestamp = event.timestamp
                processAccelerometerSample(timestamp, x, y, z, lastGyroVel, isSynthetic = false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Common processing logic for both physical sensor events and synthetic replay injection.
     */
    fun processAccelerometerSample(
        timestampNanos: Long,
        x: Float,
        y: Float,
        z: Float,
        gyroAngularVel: Float,
        isSynthetic: Boolean
    ) {
        val magnitude = sqrt(x * x + y * y + z * z)
        val filtered = filter.processSample(x, y, z)

        // Store into ring buffer for visualization
        synchronized(waveformBuffer) {
            waveformBuffer[bufferWriteIndex] = filtered.impulse
            bufferWriteIndex = (bufferWriteIndex + 1) % waveformBuffer.size
        }

        // Check for impact detection
        val detectedImpact = impactDetector.processImpulse(
            timestampNanos = timestampNanos,
            filteredResult = filtered,
            gyroAngularVelocity = gyroAngularVel,
            isSynthetic = isSynthetic
        )

        detectedImpact?.let { impact ->
            scope.launch {
                _impactEvents.emit(impact)
            }
        }

        // Throttle high-frequency physical sensor UI StateFlow updates to ~25fps (40ms)
        // so 200Hz hardware polling never causes Compose UI frame drops, while keeping
        // immediate updates for synthetic tests and detected impacts.
        val shouldEmitUi = isSynthetic ||
            detectedImpact != null ||
            lastUiTelemetryEmitNanos == 0L ||
            (timestampNanos - lastUiTelemetryEmitNanos) >= 40_000_000L ||
            timestampNanos < lastUiTelemetryEmitNanos

        if (shouldEmitUi) {
            lastUiTelemetryEmitNanos = timestampNanos
            _telemetryState.value = TelemetrySnapshot(
                timestampNanos = timestampNanos,
                accelX = x,
                accelY = y,
                accelZ = z,
                accelMagnitude = magnitude,
                filteredImpulse = filtered.impulse,
                gyroX = gyroX,
                gyroY = gyroY,
                gyroZ = gyroZ,
                angularVelocity = gyroAngularVel,
                isStationaryOnDesk = gyroAngularVel < 0.6f,
                currentNoiseFloor = filtered.currentNoiseFloor,
                lastImpactTimeNanos = if (detectedImpact != null) timestampNanos else _telemetryState.value.lastImpactTimeNanos,
                isMonitoring = isRegistered || isSynthetic,
                isReplayMode = isSynthetic
            )
        }
    }

    /**
     * Clear the waveform buffer history.
     */
    fun clearWaveformBuffer() {
        synchronized(waveformBuffer) {
            waveformBuffer.fill(0f)
            bufferWriteIndex = 0
        }
    }
}
