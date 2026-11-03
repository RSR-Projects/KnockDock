package com.example.knockdock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.knockdock.action.ActionExecutor
import com.example.knockdock.data.KnockDockRepository
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.GestureDetectionEvent
import com.example.knockdock.recognition.GestureRecognizer
import com.example.knockdock.sensor.SensorManagerHelper
import com.example.knockdock.sensor.SensorReplayer
import com.example.knockdock.shizuku.ShizukuBridgeManager
import com.example.knockdock.shizuku.ShizukuFeatureItem
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
 * Represents a completed macro action event for silent notifications, Dynamic Island HUD, and Edge Lighting.
 */
data class CompletedMacroEvent(
    val id: Long = System.nanoTime(),
    val actionName: String,
    val gestureName: String,
    val resultMessage: String,
    val success: Boolean,
    val impactCount: Int = 1,
    val isSimulated: Boolean = false
)

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
    val shizukuBridge: ShizukuBridgeManager = ShizukuBridgeManager(appContext)

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
        shizukuBridge = shizukuBridge,
        executorScope = appScope
    )

    private val _lastDetectedGesture = MutableStateFlow<GestureDetectionEvent?>(null)
    val lastDetectedGesture: StateFlow<GestureDetectionEvent?> = _lastDetectedGesture.asStateFlow()

    private val _lastCompletedMacro = MutableStateFlow<CompletedMacroEvent?>(null)
    val lastCompletedMacro: StateFlow<CompletedMacroEvent?> = _lastCompletedMacro.asStateFlow()

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
            val fullGestureName = "${gestureEvent.gestureName}$modTag"
            repository.recordExecutionLog(
                actionId = action.id,
                actionName = action.name,
                gestureName = fullGestureName,
                result = result
            )

            notifyMacroWorkDone(
                actionName = action.name,
                gestureName = fullGestureName,
                resultMessage = result.message,
                success = result.success,
                impactCount = gestureEvent.impactCount,
                isSimulated = gestureEvent.isSynthetic
            )
        }
    }

    /**
     * Emits a completed macro event to the in-app Liquid Glass Dynamic Island HUD + Edge Lighting,
     * and posts a silent Android notification confirming the macro work is done.
     */
    fun notifyMacroWorkDone(
        actionName: String,
        gestureName: String,
        resultMessage: String,
        success: Boolean,
        impactCount: Int = 1,
        isSimulated: Boolean = false
    ) {
        _lastCompletedMacro.value = CompletedMacroEvent(
            id = System.nanoTime(),
            actionName = actionName,
            gestureName = gestureName,
            resultMessage = resultMessage,
            success = success,
            impactCount = impactCount,
            isSimulated = isSimulated
        )
        postSilentWorkDoneNotification(
            actionName = actionName,
            gestureName = gestureName,
            resultMessage = resultMessage,
            success = success
        )
        SystemEdgeLightingOverlayManager.showEdgeLightingIfBackgroundOrLocked(
            context = appContext,
            actionName = actionName,
            gestureName = gestureName,
            impactCount = impactCount,
            success = success
        )
    }

    /**
     * Executes a [ShizukuFeatureItem] strictly via the Shizuku/Shevary bridge, logs it, and emits
     * the Full-Phone Liquid Wave + "Action Activated" HUD.
     */
    fun executeShizukuFeatureAndNotify(feature: ShizukuFeatureItem) {
        appScope.launch {
            val res = shizukuBridge.executeShizukuFeature(feature)
            notifyMacroWorkDone(
                actionName = feature.title,
                gestureName = "[Shizuku] Privileged Hub",
                resultMessage = res.output,
                success = res.success,
                impactCount = 2,
                isSimulated = false
            )
        }
    }

    /**
     * Creates or updates an [ActionDefinition] for the given [ShizukuFeatureItem] and binds it
     * to a physical knock gesture (e.g. gesture_single_knock, gesture_double_knock, gesture_triple_knock)
     * in the active profile.
     */
    fun bindShizukuFeatureToGesture(
        feature: ShizukuFeatureItem,
        gestureId: String,
        gestureLabel: String
    ) {
        appScope.launch {
            val actionId = "action_${feature.id}"
            val actionDef = ActionDefinition(
                id = actionId,
                name = feature.title,
                description = "${feature.subtitle} (Strictly executed via Shizuku/Shevary Binder)",
                type = com.example.knockdock.model.ActionType.SHIZUKU_PRIVILEGED,
                parameters = mapOf("shizukuFeatureId" to feature.id)
            )
            repository.saveAction(actionDef)
            val activeProfile = repository.getActiveProfile().first()
            val profileId = activeProfile?.id ?: cachedActiveProfileId
            repository.addOrUpdateMapping(
                profileId = profileId,
                gestureId = gestureId,
                actionId = actionId
            )
            notifyMacroWorkDone(
                actionName = "Bound ${feature.title}",
                gestureName = gestureLabel,
                resultMessage = "Assigned ${feature.title} to $gestureLabel in active profile",
                success = true,
                impactCount = 1,
                isSimulated = true
            )
        }
    }

    private fun postSilentWorkDoneNotification(
        actionName: String,
        gestureName: String,
        resultMessage: String,
        success: Boolean
    ) {
        try {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    SILENT_DONE_CHANNEL_ID,
                    "KnockDock Silent Action Confirmations",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Silent status confirmation when a desk knock macro finishes executing"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }

            val openIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                2050,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val statusPrefix = if (success) "✓ Work Done" else "⚠ Action Notice"
            val notification = NotificationCompat.Builder(appContext, SILENT_DONE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$statusPrefix • $actionName")
                .setContentText("Triggered by $gestureName — $resultMessage")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Gesture: $gestureName\nAction: $actionName\nResult: $resultMessage")
                )
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setTimeoutAfter(9000L)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(SILENT_DONE_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
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
        const val SILENT_DONE_CHANNEL_ID = "knockdock_silent_done_channel"
        const val SILENT_DONE_NOTIFICATION_ID = 2049

        @Volatile
        private var instance: MonitoringController? = null

        fun getInstance(context: Context): MonitoringController {
            return instance ?: synchronized(this) {
                instance ?: MonitoringController(context.applicationContext).also { instance = it }
            }
        }
    }
}
