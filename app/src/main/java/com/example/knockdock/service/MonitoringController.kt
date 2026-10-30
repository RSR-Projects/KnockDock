package com.example.knockdock.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.knockdock.action.ActionExecutor
import com.example.knockdock.data.KnockDockRepository
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.GestureDetectionEvent
import com.example.knockdock.recognition.GestureRecognizer
import com.example.knockdock.sensor.SensorManagerHelper
import com.example.knockdock.sensor.SensorReplayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Central controller coordinating sensor streaming, gesture recognition, modifier-aware action routing,
 * and foreground service lifecycle across the application.
 */
class MonitoringController private constructor(val appContext: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: KnockDockRepository = KnockDockRepository(appContext, appScope)
    val sensorHelper: SensorManagerHelper = SensorManagerHelper(appContext, appScope)
    val gestureRecognizer: GestureRecognizer = GestureRecognizer(appScope)
    val replayer: SensorReplayer = SensorReplayer()

    @Volatile
    private var cachedActiveProfileId: String = "profile_desk_commander"

    val actionExecutor: ActionExecutor = ActionExecutor(
        context = appContext,
        onToggleMonitoring = { toggleMonitoring() },
        onSwitchProfile = { profileId ->
            appScope.launch {
                repository.setActiveProfile(profileId)
                val targetProfile = repository.getAllProfiles().first().find { it.id == profileId }
                if (targetProfile != null) {
                    val cur = repository.settingsFlow.value
                    val nextRate = targetProfile.preferredSamplingRate ?: cur.samplingRate
                    val nextSens = targetProfile.preferredSensitivity ?: cur.sensitivity
                    if (nextRate != cur.samplingRate || nextSens != cur.sensitivity) {
                        repository.updateSettings(
                            cur.copy(samplingRate = nextRate, sensitivity = nextSens)
                        )
                    }
                }
            }
        },
        getActiveProfileId = { cachedActiveProfileId },
        isPcPairedProvider = {
            val s = repository.settingsFlow.value
            s.pcControlEnabled && s.isPcPaired
        },
        onRunSecondaryActionById = { secondaryActionId, isSimulated ->
            val act = repository.getAllActions().first().find { it.id == secondaryActionId }
            if (act != null && act.type != com.example.knockdock.model.ActionType.DESK_MODE_TOGGLE &&
                act.type != com.example.knockdock.model.ActionType.SWITCH_PROFILE
            ) {
                actionExecutor.executeAction(act, isSimulated = isSimulated)
            } else {
                null
            }
        },
        executorScope = appScope
    )

    private val _lastDetectedGesture = MutableStateFlow<GestureDetectionEvent?>(null)
    val lastDetectedGesture: StateFlow<GestureDetectionEvent?> = _lastDetectedGesture.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var gestureObservationJob: Job? = null
    private var impactBridgeJob: Job? = null

    init {
        // Forward sensor impacts to gesture recognizer
        impactBridgeJob = appScope.launch {
            sensorHelper.impactEvents.collect { impact ->
                gestureRecognizer.onImpact(impact)
            }
        }

        // Forward gesture detections to action executor and UI
        gestureObservationJob = appScope.launch {
            gestureRecognizer.detectedGestures.collect { gestureEvent ->
                onGestureRecognized(gestureEvent)
            }
        }

        // Sync settings to sensorHelper and gestureRecognizer
        appScope.launch {
            repository.settingsFlow.collect { settings ->
                sensorHelper.updateSettings(settings)
                gestureRecognizer.updateSettings(settings)
                if (_isMonitoring.value && settings.backgroundMonitoringEnabled) {
                    startForegroundMonitoringService()
                }
            }
        }

        // Sync active profile's gestures
        appScope.launch {
            combine(
                repository.getActiveProfile(),
                repository.getAllGestures()
            ) { activeProfile, allGestures ->
                if (activeProfile != null) {
                    cachedActiveProfileId = activeProfile.id
                    val profileGestureIds = activeProfile.mappings.filter { it.isEnabled }.map { it.gestureId }
                    val activeGestures = allGestures.filter { it.id in profileGestureIds }
                    gestureRecognizer.updateActiveGestures(activeGestures.ifEmpty { allGestures })
                }
            }.collect {}
        }
    }

    fun ensureSensorListeningFromService() {
        val settings = repository.settingsFlow.value
        sensorHelper.startListening(settings.samplingRate)
        _isMonitoring.value = true
    }

    private fun startForegroundMonitoringService() {
        try {
            val intent = Intent(appContext, KnockMonitoringService::class.java).apply {
                action = KnockMonitoringService.ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    fun startMonitoring() {
        val settings = repository.settingsFlow.value
        sensorHelper.startListening(settings.samplingRate)
        _isMonitoring.value = true

        if (settings.backgroundMonitoringEnabled) {
            startForegroundMonitoringService()
        }
    }

    fun stopMonitoring() {
        sensorHelper.stopListening()
        _isMonitoring.value = false

        try {
            val intent = Intent(appContext, KnockMonitoringService::class.java).apply {
                action = KnockMonitoringService.ACTION_STOP_MONITORING
            }
            appContext.stopService(intent)
        } catch (_: Exception) {
        }
    }

    fun toggleMonitoring() {
        if (_isMonitoring.value) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun onGestureRecognized(gestureEvent: GestureDetectionEvent) {
        _lastDetectedGesture.value = gestureEvent

        appScope.launch {
            val activeProfile = repository.getActiveProfile().first() ?: return@launch
            val activeModifier = if (gestureEvent.isSynthetic) {
                actionExecutor.activeModifierFlow.value?.modifierKey
            } else {
                actionExecutor.consumeActiveModifierIfValid()
            }

            val mapping = if (activeModifier != null) {
                // Modifier is active: match only mappings explicitly bound to this modifier,
                // OR if this gesture itself is the same modifier gesture (to allow cancellation)
                activeProfile.mappings.find {
                    it.gestureId == gestureEvent.gestureId &&
                        it.isEnabled &&
                        it.requiredModifier.equals(activeModifier, ignoreCase = true)
                }
            } else {
                activeProfile.mappings.find {
                    it.gestureId == gestureEvent.gestureId &&
                        it.isEnabled &&
                        it.requiredModifier.isNullOrBlank()
                }
            } ?: return@launch

            val actions = repository.getAllActions().first()
            val action = actions.find { it.id == mapping.actionId } ?: return@launch

            // Trigger feedback if enabled (respecting simulation mode)
            val settings = repository.settingsFlow.value
            if (settings.hapticFeedbackEnabled) {
                actionExecutor.executeAction(
                    ActionDefinition(
                        id = "haptic_internal",
                        name = "Haptic Click",
                        description = "",
                        type = com.example.knockdock.model.ActionType.HAPTIC_PULSE,
                        parameters = mapOf("pattern" to "CLICK")
                    ),
                    isSimulated = gestureEvent.isSynthetic
                )
            }

            // Execute mapped macro action
            val result = actionExecutor.executeAction(action, isSimulated = gestureEvent.isSynthetic)

            // Record execution log in database
            val modTag = if (activeModifier != null) " [$activeModifier]" else ""
            repository.recordExecutionLog(
                actionId = action.id,
                actionName = action.name,
                gestureName = "${gestureEvent.gestureName}$modTag",
                result = result
            )
        }
    }

    /**
     * Run interactive synthetic simulation trial without physical knock requirement.
     */
    suspend fun simulateReplay(preset: SensorReplayer.ReplayPreset) {
        replayer.replay(preset) { x, y, z, gyroVel, isSynthetic ->
            sensorHelper.processAccelerometerSample(
                timestampNanos = System.nanoTime(),
                x = x,
                y = y,
                z = z,
                gyroAngularVel = gyroVel,
                isSynthetic = isSynthetic
            )
        }
    }

    companion object {
        @Volatile
        private var instance: MonitoringController? = null

        fun getInstance(context: Context): MonitoringController {
            return instance ?: synchronized(this) {
                instance ?: MonitoringController(context.applicationContext).also { instance = it }
            }
        }
    }
}
