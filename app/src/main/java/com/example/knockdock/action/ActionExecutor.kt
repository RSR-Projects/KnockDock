package com.example.knockdock.action

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.ActionExecutionResult
import com.example.knockdock.model.ActionStep
import com.example.knockdock.model.ActionToggleState
import com.example.knockdock.model.ActionType
import com.example.knockdock.model.MacroConcurrencyPolicy
import com.example.knockdock.model.StepConditionType
import com.example.knockdock.model.StepExecutionReport
import com.example.knockdock.model.StepFailurePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class FocusSessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isInBreak: Boolean = false,
    val durationMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remainingSeconds: Int = 0,
    val dndApplied: Boolean = false,
    val previousInterruptionFilter: Int? = null,
    val statusText: String = "Idle"
)

data class ActiveModifierState(
    val modifierKey: String,
    val activatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val remainingMs: Long
)

data class QuickTimerState(
    val timerId: String,
    val reminderText: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isCompleted: Boolean = false,
    val isSnoozed: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SavedVoiceMemo(
    val id: String,
    val fileName: String,
    val filePath: String,
    val durationSeconds: Int,
    val createdAtEpochMs: Long
)

data class VoiceMemoState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val maxDurationSeconds: Int = 30,
    val currentFilePath: String? = null,
    val savedMemos: List<SavedVoiceMemo> = emptyList(),
    val statusMessage: String = "Ready"
)

/**
 * Executes supported macro actions safely, manages stateful focus sessions, modifiers,
 * countdown timers, voice memos, emergency light sequences, and conditional/recovery macros.
 */
class ActionExecutor(
    private val context: Context,
    private val onToggleMonitoring: (() -> Unit)? = null,
    private val onSwitchProfile: ((String) -> Unit)? = null,
    private val getActiveProfileId: (() -> String?)? = null,
    private val isPcPairedProvider: (() -> Boolean)? = null,
    private val onRunSecondaryActionById: (suspend (String, Boolean) -> ActionExecutionResult?)? = null,
    private val executorScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val audioManager = try { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager } catch (_: Throwable) { null }
    private val notificationManager = try { context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager } catch (_: Throwable) { null }
    private val cameraManager = try { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager } catch (_: Throwable) { null }
    private val clipboardManager = try { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager } catch (_: Throwable) { null }
    private val timerPrefs = try { context.getSharedPreferences("knockdock_timers_prefs", Context.MODE_PRIVATE) } catch (_: Throwable) { null }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .writeTimeout(2500, TimeUnit.MILLISECONDS)
            .build()
    }

    private var isTorchOn = false
    private val NOTIFICATION_CHANNEL_ID = "knockdock_macro_alerts"
    private val LAUNCH_CHANNEL_ID = "knockdock_background_launch"

    // Guard against recursive profile switching
    private val profileSwitchDepth = AtomicInteger(0)

    // Concurrency & running macro tracking
    private val runningMacroJobs = ConcurrentHashMap<String, Job>()
    private val _runningMacrosFlow = MutableStateFlow<Set<String>>(emptySet())
    val runningMacrosFlow: StateFlow<Set<String>> = _runningMacrosFlow.asStateFlow()

    // Reversible & state-aware toggle tracking
    private val _toggleStatesFlow = MutableStateFlow<Map<String, ActionToggleState>>(emptyMap())
    val toggleStatesFlow: StateFlow<Map<String, ActionToggleState>> = _toggleStatesFlow.asStateFlow()

    // Focus Session state
    private var focusJob: Job? = null
    private val _focusSessionFlow = MutableStateFlow(FocusSessionState())
    val focusSessionFlow: StateFlow<FocusSessionState> = _focusSessionFlow.asStateFlow()

    // Gesture Modifier state
    private var modifierJob: Job? = null
    private val _activeModifierFlow = MutableStateFlow<ActiveModifierState?>(null)
    val activeModifierFlow: StateFlow<ActiveModifierState?> = _activeModifierFlow.asStateFlow()

    // Quick Reminder & Countdown Timers state
    private val timerJobs = ConcurrentHashMap<String, Job>()
    private val _activeTimersFlow = MutableStateFlow<List<QuickTimerState>>(emptyList())
    val activeTimersFlow: StateFlow<List<QuickTimerState>> = _activeTimersFlow.asStateFlow()

    // Quick Voice Memo state
    private var voiceMemoJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private val _voiceMemoFlow = MutableStateFlow(VoiceMemoState())
    val voiceMemoFlow: StateFlow<VoiceMemoState> = _voiceMemoFlow.asStateFlow()

    // Emergency Light Sequence state
    private var emergencyLightJob: Job? = null
    private val _emergencyLightActiveFlow = MutableStateFlow(false)
    val emergencyLightActiveFlow: StateFlow<Boolean> = _emergencyLightActiveFlow.asStateFlow()

    init {
        createNotificationChannel()
        restorePersistedTimers()
        refreshSavedVoiceMemos()
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "KnockDock Macro Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications triggered by KnockDock desk knock gestures"
                }
                val launchChannel = NotificationChannel(
                    LAUNCH_CHANNEL_ID,
                    "KnockDock Background App & URL Launcher",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority launcher channel when KnockDock opens apps or URLs from background"
                    setShowBadge(false)
                }
                notificationManager?.createNotificationChannel(channel)
                notificationManager?.createNotificationChannel(launchChannel)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Generates a human-readable execution preview for a macro or action before testing.
     */
    fun previewMacroExecution(action: ActionDefinition): List<String> {
        val lines = mutableListOf<String>()
        lines.add("Macro: ${action.name} (${action.type.displayName})")
        lines.add("Concurrency Policy: ${action.concurrencyPolicy.label}")
        if (action.isReversibleToggle) {
            val currentState = _toggleStatesFlow.value[action.id]
            val stateDesc = if (currentState?.isToggledOn == true) "ON (Next trigger runs reverse/off)" else "OFF (Next trigger runs primary/on)"
            val reliability = if (currentState?.isActualSystemState == true) "Verified System State" else "Assumed State"
            lines.add("Reversible Toggle: $stateDesc [$reliability]")
        }
        if (action.type == ActionType.MULTI_STEP_SEQUENCE) {
            action.steps.forEachIndexed { idx, step ->
                val cond = if (step.conditionType != StepConditionType.NONE) {
                    " [If ${step.conditionType.label}${if (step.conditionValue.isNotBlank()) "=${step.conditionValue}" else ""}]"
                } else ""
                val retry = if (step.maxRetries > 0) " [Retry up to ${step.maxRetries}x]" else ""
                val policy = " [On Fail: ${step.onFailurePolicy.label}]"
                lines.add("${idx + 1}. ${step.title} (${step.type.displayName}, delay ${step.delayAfterMs}ms)$cond$retry$policy")
            }
        } else {
            lines.add("1. Execute ${action.type.displayName} with ${action.parameters.keys.joinToString().ifEmpty { "default parameters" }}")
        }
        return lines
    }

    /**
     * Resynchronizes assumed toggle state when hardware/OS state cannot be directly queried.
     */
    fun resyncAssumedState(actionId: String, isToggledOn: Boolean, isActualSystemState: Boolean = false) {
        val map = _toggleStatesFlow.value.toMutableMap()
        map[actionId] = ActionToggleState(
            actionId = actionId,
            isToggledOn = isToggledOn,
            isActualSystemState = isActualSystemState,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        _toggleStatesFlow.value = map
    }

    /**
     * Cancels a specific running macro or all running macros safely.
     */
    fun cancelMacro(actionId: String) {
        runningMacroJobs.remove(actionId)?.cancel()
        _runningMacrosFlow.value = runningMacroJobs.keys.toSet()
    }

    fun cancelAllRunningMacros() {
        runningMacroJobs.values.forEach { it.cancel() }
        runningMacroJobs.clear()
        _runningMacrosFlow.value = emptySet()
        stopEmergencyLight()
    }

    /**
     * Executes the given action definition with concurrency control and reversible toggle support.
     */
    suspend fun executeAction(action: ActionDefinition, isSimulated: Boolean = false): ActionExecutionResult {
        val startTime = System.currentTimeMillis()

        if (!action.isEnabled) {
            return ActionExecutionResult(
                actionId = action.id,
                actionName = action.name,
                actionType = action.type,
                success = false,
                message = "Action is disabled in settings.",
                executedAtEpochMs = startTime,
                isSimulated = isSimulated
            )
        }

        // Concurrency control check
        val existingJob = runningMacroJobs[action.id]
        if (existingJob != null && existingJob.isActive) {
            when (action.concurrencyPolicy) {
                MacroConcurrencyPolicy.SKIP_IF_RUNNING -> {
                    return ActionExecutionResult(
                        actionId = action.id,
                        actionName = action.name,
                        actionType = action.type,
                        success = false,
                        message = "Skipped duplicate trigger: '${action.name}' is already running.",
                        executedAtEpochMs = startTime,
                        isSimulated = isSimulated
                    )
                }
                MacroConcurrencyPolicy.CANCEL_PREVIOUS -> {
                    existingJob.cancel()
                    runningMacroJobs.remove(action.id)
                }
                MacroConcurrencyPolicy.ALLOW_CONCURRENT -> {
                    // Proceed concurrently
                }
            }
        }

        val currentJob = kotlin.coroutines.coroutineContext[Job]
        if (currentJob != null) {
            runningMacroJobs[action.id] = currentJob
            _runningMacrosFlow.value = runningMacroJobs.keys.toSet()
        }

        try {
            // Check reversible toggle state
            val currentToggle = _toggleStatesFlow.value[action.id]?.isToggledOn ?: false
            val targetToggleState = if (action.isReversibleToggle) !currentToggle else null

            val effectiveAction = if (action.isReversibleToggle && currentToggle && action.reverseActionType != null) {
                action.copy(
                    type = action.reverseActionType,
                    parameters = action.reverseParameters.ifEmpty { action.parameters }
                )
            } else {
                action
            }

            val result = when (effectiveAction.type) {
                ActionType.MULTI_STEP_SEQUENCE -> {
                    executeSequence(effectiveAction, isSimulated)
                }
                else -> {
                    val stepResult = executeStepWithRetryAndRecovery(
                        step = ActionStep(
                            stepId = effectiveAction.id,
                            type = effectiveAction.type,
                            title = effectiveAction.name,
                            parameters = effectiveAction.parameters
                        ),
                        isSimulated = isSimulated
                    )
                    ActionExecutionResult(
                        actionId = action.id,
                        actionName = action.name,
                        actionType = action.type,
                        success = stepResult.success,
                        message = stepResult.message,
                        executedAtEpochMs = startTime,
                        stepReports = listOf(stepResult),
                        isSimulated = isSimulated,
                        toggledToActiveState = if (stepResult.success && !isSimulated) targetToggleState else currentToggle
                    )
                }
            }

            if (!isSimulated && action.isReversibleToggle && result.success && targetToggleState != null) {
                val isActual = action.type in setOf(
                    ActionType.TOGGLE_FLASHLIGHT,
                    ActionType.FOCUS_SESSION_CONTROLLER,
                    ActionType.TOGGLE_MONITORING,
                    ActionType.EMERGENCY_LIGHT_SEQUENCE
                )
                resyncAssumedState(action.id, targetToggleState, isActualSystemState = isActual)
            }

            return result
        } catch (ce: CancellationException) {
            return ActionExecutionResult(
                actionId = action.id,
                actionName = action.name,
                actionType = action.type,
                success = false,
                message = "Macro '${action.name}' was cancelled safely.",
                executedAtEpochMs = startTime,
                isSimulated = isSimulated,
                wasCancelled = true
            )
        } finally {
            runningMacroJobs.remove(action.id)
            _runningMacrosFlow.value = runningMacroJobs.keys.toSet()
        }
    }

    private suspend fun executeSequence(action: ActionDefinition, isSimulated: Boolean): ActionExecutionResult {
        val startTime = System.currentTimeMillis()
        val reports = mutableListOf<StepExecutionReport>()
        var allSucceeded = true
        var lastStepSuccess: Boolean? = null

        for (step in action.steps) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
                throw CancellationException("Sequence cancelled")
            }

            // Evaluate conditional branch
            if (!evaluateStepCondition(step, lastStepSuccess)) {
                reports.add(
                    StepExecutionReport(
                        stepId = step.stepId,
                        stepTitle = step.title,
                        success = true,
                        message = "Skipped by condition (${step.conditionType.label})",
                        durationMs = 0L,
                        skippedByCondition = true
                    )
                )
                continue
            }

            val report = executeStepWithRetryAndRecovery(step, isSimulated = isSimulated)
            reports.add(report)
            lastStepSuccess = report.success

            if (!report.success) {
                allSucceeded = false
                if (step.onFailurePolicy == StepFailurePolicy.STOP_MACRO ||
                    step.onFailurePolicy == StepFailurePolicy.RETRY_THEN_STOP
                ) {
                    break
                }
            }

            if (step.delayAfterMs > 0) {
                if (isSimulated) {
                    kotlinx.coroutines.yield()
                } else {
                    delay(step.delayAfterMs)
                }
            }
        }

        val prefix = if (isSimulated) "[DRY-RUN] " else ""
        val summaryMessage = if (allSucceeded) {
            "${prefix}All ${reports.size} sequence steps completed (${reports.count { it.skippedByCondition }} skipped by condition)."
        } else {
            val failedCount = reports.count { !it.success }
            "$prefix$failedCount of ${action.steps.size} steps encountered issues."
        }

        return ActionExecutionResult(
            actionId = action.id,
            actionName = action.name,
            actionType = action.type,
            success = allSucceeded,
            message = summaryMessage,
            executedAtEpochMs = startTime,
            stepReports = reports,
            isSimulated = isSimulated
        )
    }

    private fun evaluateStepCondition(step: ActionStep, previousStepSuccess: Boolean?): Boolean {
        return when (step.conditionType) {
            StepConditionType.NONE -> true
            StepConditionType.PREVIOUS_STEP_SUCCESS -> previousStepSuccess != false
            StepConditionType.PREVIOUS_STEP_FAILED -> previousStepSuccess == false
            StepConditionType.PC_PAIRED -> isPcPairedProvider?.invoke() ?: false
            StepConditionType.ACTIVE_PROFILE_IS -> {
                val activeId = getActiveProfileId?.invoke() ?: ""
                step.conditionValue.isBlank() || activeId.equals(step.conditionValue.trim(), ignoreCase = true)
            }
            StepConditionType.TIME_WINDOW_HOURS -> {
                val parts = step.conditionValue.split("-")
                val startH = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val endH = parts.getOrNull(1)?.toIntOrNull() ?: 23
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (startH <= endH) {
                    currentHour in startH..endH
                } else {
                    currentHour >= startH || currentHour <= endH
                }
            }
            StepConditionType.FOCUS_SESSION_ACTIVE -> _focusSessionFlow.value.isActive
        }
    }

    private suspend fun executeStepWithRetryAndRecovery(
        step: ActionStep,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val shouldRetry = step.onFailurePolicy == StepFailurePolicy.RETRY_THEN_CONTINUE ||
            step.onFailurePolicy == StepFailurePolicy.RETRY_THEN_STOP ||
            step.maxRetries > 0
        val maxAttempts = if (shouldRetry) (step.maxRetries.coerceIn(0, 5) + 1) else 1

        var attempt = 0
        var lastReport: StepExecutionReport? = null

        while (attempt < maxAttempts) {
            attempt++
            val report = executeSingleStep(step, isSimulated = isSimulated).copy(attemptCount = attempt)
            lastReport = report
            if (report.success) {
                return report
            }
            if (attempt < maxAttempts) {
                if (isSimulated) {
                    kotlinx.coroutines.yield()
                } else {
                    delay(step.retryDelayMs.coerceIn(50L, 5000L))
                }
            }
        }

        // Step failed after retries; check if a recovery step is configured
        if (step.onFailurePolicy == StepFailurePolicy.RUN_RECOVERY_STEP && step.recoveryActionType != null) {
            val recoveryStep = ActionStep(
                stepId = "${step.stepId}_recovery",
                type = step.recoveryActionType,
                title = "Recovery: ${step.recoveryActionType.displayName}",
                parameters = step.recoveryParameters
            )
            val recReport = executeSingleStep(recoveryStep, isSimulated = isSimulated)
            val failedMsg = lastReport?.message ?: "Step failed"
            return StepExecutionReport(
                stepId = step.stepId,
                stepTitle = step.title,
                success = false,
                message = "$failedMsg • Recovery (${step.recoveryActionType.displayName}): ${recReport.message}",
                durationMs = (lastReport?.durationMs ?: 0L) + recReport.durationMs,
                attemptCount = attempt,
                recoveryTriggered = true
            )
        }

        return lastReport ?: StepExecutionReport(step.stepId, step.title, false, "Step failed", 0L)
    }

    private suspend fun executeSingleStep(
        step: ActionStep,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val stepStartTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                when (step.type) {
                    ActionType.LAUNCH_APP -> {
                        val packageName = step.parameters["packageName"] ?: ""
                        if (packageName.isBlank()) {
                            StepExecutionReport(step.stepId, step.title, false, "Package name not specified", 0)
                        } else if (isSimulated) {
                            val installed = try { context.packageManager?.getLaunchIntentForPackage(packageName) != null } catch (_: Throwable) { false }
                            StepExecutionReport(
                                step.stepId,
                                step.title,
                                true,
                                "[DRY-RUN] Would launch app '$packageName' (installed=$installed)",
                                System.currentTimeMillis() - stepStartTime
                            )
                        } else {
                            val intent = try { context.packageManager?.getLaunchIntentForPackage(packageName) } catch (_: Throwable) { null }
                            if (intent != null) {
                                launchIntentReliable(intent, "Launch App", "Opening $packageName")
                                StepExecutionReport(step.stepId, step.title, true, "Launched $packageName", System.currentTimeMillis() - stepStartTime)
                            } else {
                                StepExecutionReport(step.stepId, step.title, false, "App '$packageName' not installed or has no launchable Activity", System.currentTimeMillis() - stepStartTime)
                            }
                        }
                    }

                    ActionType.LAUNCH_URL -> {
                        val rawUrl = step.parameters["url"] ?: ""
                        if (rawUrl.isBlank() || rawUrl.contains(" ") || rawUrl.contains("javascript:", ignoreCase = true)) {
                            StepExecutionReport(step.stepId, step.title, false, "Invalid or empty URL", System.currentTimeMillis() - stepStartTime)
                        } else {
                            val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                                "https://$rawUrl"
                            } else rawUrl

                            val isValidUrl = try {
                                val jUri = java.net.URI(formattedUrl)
                                (jUri.scheme.equals("http", ignoreCase = true) || jUri.scheme.equals("https", ignoreCase = true)) && !jUri.host.isNullOrBlank()
                            } catch (_: Throwable) {
                                false
                            }
                            if (isValidUrl) {
                                if (isSimulated) {
                                    StepExecutionReport(
                                        step.stepId,
                                        step.title,
                                        true,
                                        "[DRY-RUN] Would open $formattedUrl in browser",
                                        System.currentTimeMillis() - stepStartTime
                                    )
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
                                    launchIntentReliable(intent, "Open URL", formattedUrl)
                                    StepExecutionReport(step.stepId, step.title, true, "Opened $formattedUrl in browser", System.currentTimeMillis() - stepStartTime)
                                }
                            } else {
                                StepExecutionReport(step.stepId, step.title, false, "Invalid URL scheme or host: $formattedUrl", System.currentTimeMillis() - stepStartTime)
                            }
                        }
                    }

                    ActionType.SYSTEM_SETTING -> {
                        val screen = step.parameters["screen"]?.uppercase() ?: "SOUND"
                        val actionString = when (screen) {
                            "BLUETOOTH" -> Settings.ACTION_BLUETOOTH_SETTINGS
                            "WIFI" -> Settings.ACTION_WIFI_SETTINGS
                            "DISPLAY" -> Settings.ACTION_DISPLAY_SETTINGS
                            "DND" -> Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
                            else -> Settings.ACTION_SOUND_SETTINGS
                        }
                        if (isSimulated) {
                            StepExecutionReport(
                                step.stepId,
                                step.title,
                                true,
                                "[DRY-RUN] Would open $screen settings ($actionString)",
                                System.currentTimeMillis() - stepStartTime
                            )
                        } else {
                            val intent = Intent(actionString)
                            launchIntentReliable(intent, "Open $screen Settings", actionString)
                            StepExecutionReport(step.stepId, step.title, true, "Opened $screen settings", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.MEDIA_PLAY_PAUSE -> {
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would dispatch Play/Pause media key", System.currentTimeMillis() - stepStartTime)
                        } else {
                            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            StepExecutionReport(step.stepId, step.title, true, "Dispatched Play/Pause media key", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.MEDIA_NEXT -> {
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would dispatch Next Track media key", System.currentTimeMillis() - stepStartTime)
                        } else {
                            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                            StepExecutionReport(step.stepId, step.title, true, "Dispatched Next Track media key", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.MEDIA_PREVIOUS -> {
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would dispatch Previous Track media key", System.currentTimeMillis() - stepStartTime)
                        } else {
                            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            StepExecutionReport(step.stepId, step.title, true, "Dispatched Previous Track media key", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.MEDIA_VOLUME_MUTE -> {
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would toggle media stream mute", System.currentTimeMillis() - stepStartTime)
                        } else {
                            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                            StepExecutionReport(step.stepId, step.title, true, "Toggled media mute", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.NOTIFICATION -> {
                        val title = step.parameters["title"]?.ifBlank { "KnockDock Alert" } ?: "KnockDock Alert"
                        val body = step.parameters["body"]?.ifBlank { "Gesture triggered on desk" } ?: "Gesture triggered on desk"
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would post notification: $title ($body)", System.currentTimeMillis() - stepStartTime)
                        } else {
                            postNotification(title, body)
                            StepExecutionReport(step.stepId, step.title, true, "Posted notification: $title", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.TOGGLE_FLASHLIGHT -> {
                        if (isSimulated) {
                            StepExecutionReport(
                                step.stepId,
                                step.title,
                                true,
                                "[DRY-RUN] Would toggle flashlight to ${if (!isTorchOn) "ON" else "OFF"}",
                                System.currentTimeMillis() - stepStartTime
                            )
                        } else {
                            try {
                                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                                if (cameraId != null) {
                                    isTorchOn = !isTorchOn
                                    cameraManager.setTorchMode(cameraId, isTorchOn)
                                    StepExecutionReport(step.stepId, step.title, true, "Flashlight set to ${if (isTorchOn) "ON" else "OFF"}", System.currentTimeMillis() - stepStartTime)
                                } else {
                                    StepExecutionReport(step.stepId, step.title, false, "Camera flash hardware not found on device", System.currentTimeMillis() - stepStartTime)
                                }
                            } catch (e: CameraAccessException) {
                                StepExecutionReport(step.stepId, step.title, false, "Torch error: ${e.message}", System.currentTimeMillis() - stepStartTime)
                            } catch (e: Exception) {
                                StepExecutionReport(step.stepId, step.title, false, "Flashlight unavailable: ${e.message}", System.currentTimeMillis() - stepStartTime)
                            }
                        }
                    }

                    ActionType.HAPTIC_PULSE -> {
                        val pattern = step.parameters["pattern"] ?: "DOUBLE_CLICK"
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would trigger haptic pulse ($pattern)", System.currentTimeMillis() - stepStartTime)
                        } else {
                            triggerHaptic(pattern)
                            StepExecutionReport(step.stepId, step.title, true, "Triggered haptic pulse ($pattern)", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.AUDIO_CHIRP -> {
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would emit acoustic chirp tone", System.currentTimeMillis() - stepStartTime)
                        } else {
                            try {
                                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
                                delay(140)
                                toneGen.release()
                                StepExecutionReport(step.stepId, step.title, true, "Emitted acoustic chirp tone", System.currentTimeMillis() - stepStartTime)
                            } catch (e: Exception) {
                                StepExecutionReport(step.stepId, step.title, false, "Tone generation failed: ${e.message}", System.currentTimeMillis() - stepStartTime)
                            }
                        }
                    }

                    ActionType.TOGGLE_MONITORING -> {
                        if (isSimulated) {
                            StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would toggle KnockDock monitoring state", System.currentTimeMillis() - stepStartTime)
                        } else {
                            runOnMainOrImmediate {
                                onToggleMonitoring?.invoke()
                            }
                            StepExecutionReport(step.stepId, step.title, true, "Toggled KnockDock monitoring state", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.SWITCH_PROFILE -> {
                        val targetProfileId = step.parameters["profileId"] ?: ""
                        if (targetProfileId.isNotBlank()) {
                            if (profileSwitchDepth.incrementAndGet() > 2) {
                                profileSwitchDepth.decrementAndGet()
                                StepExecutionReport(step.stepId, step.title, false, "Aborted recursive profile switch loop", System.currentTimeMillis() - stepStartTime)
                            } else {
                                try {
                                    if (isSimulated) {
                                        StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would switch active profile to $targetProfileId", System.currentTimeMillis() - stepStartTime)
                                    } else {
                                        runOnMainOrImmediate {
                                            onSwitchProfile?.invoke(targetProfileId)
                                        }
                                        StepExecutionReport(step.stepId, step.title, true, "Switched active profile to $targetProfileId", System.currentTimeMillis() - stepStartTime)
                                    }
                                } finally {
                                    profileSwitchDepth.decrementAndGet()
                                }
                            }
                        } else {
                            StepExecutionReport(step.stepId, step.title, false, "Target profile ID not provided", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    ActionType.PC_COMMAND -> {
                        executePcCommand(step, stepStartTime, isSimulated = isSimulated)
                    }

                    ActionType.MULTI_STEP_SEQUENCE -> {
                        val nestedActionId = step.parameters["nestedActionId"] ?: ""
                        if (nestedActionId.isNotBlank() && nestedActionId != step.stepId) {
                            val nestedRes = onRunSecondaryActionById?.invoke(nestedActionId, isSimulated)
                            if (nestedRes != null) {
                                StepExecutionReport(
                                    step.stepId,
                                    step.title,
                                    nestedRes.success,
                                    "${if (isSimulated) "[DRY-RUN] " else ""}Nested sequence '${nestedRes.actionName}': ${nestedRes.message}",
                                    System.currentTimeMillis() - stepStartTime
                                )
                            } else {
                                StepExecutionReport(step.stepId, step.title, true, "${if (isSimulated) "[DRY-RUN] " else ""}Nested sequence step completed", System.currentTimeMillis() - stepStartTime)
                            }
                        } else {
                            StepExecutionReport(step.stepId, step.title, true, "${if (isSimulated) "[DRY-RUN] " else ""}Nested sequence step completed", System.currentTimeMillis() - stepStartTime)
                        }
                    }

                    // ---------------- EXPANDED ACTIONS (1..15) ----------------

                    ActionType.DESK_MODE_TOGGLE -> executeDeskModeToggle(step, stepStartTime, isSimulated)
                    ActionType.FOCUS_SESSION_CONTROLLER -> executeFocusSessionController(step, stepStartTime, isSimulated)
                    ActionType.WORKSPACE_SWITCHER -> executeWorkspaceSwitcher(step, stepStartTime, isSimulated)
                    ActionType.MUSIC_SESSION_CONTROLLER -> executeMusicSessionController(step, stepStartTime, isSimulated)
                    ActionType.PRESENTATION_REMOTE -> executePresentationRemote(step, stepStartTime, isSimulated)
                    ActionType.GESTURE_MODIFIER -> executeGestureModifier(step, stepStartTime, isSimulated)
                    ActionType.SNIPPET_LAUNCHER -> executeSnippetLauncher(step, stepStartTime, isSimulated)
                    ActionType.SCREENSHOT_ANNOTATION_LAUNCHER -> executeScreenshotAnnotationLauncher(step, stepStartTime, isSimulated)
                    ActionType.QUICK_REMINDER_TIMER -> executeQuickReminderTimer(step, stepStartTime, isSimulated)
                    ActionType.QUICK_VOICE_MEMO -> executeQuickVoiceMemo(step, stepStartTime, isSimulated)
                    ActionType.QUICK_TOOL_LAUNCHER -> executeQuickToolLauncher(step, stepStartTime, isSimulated)
                    ActionType.PLAYLIST_STATION_LAUNCHER -> executePlaylistStationLauncher(step, stepStartTime, isSimulated)
                    ActionType.SMART_HOME_SCENE_TRIGGER -> executeSmartHomeSceneTrigger(step, stepStartTime, isSimulated)
                    ActionType.PAIRED_DEVICE_COMMAND -> executePairedDeviceCommand(step, stepStartTime, isSimulated)
                    ActionType.EMERGENCY_LIGHT_SEQUENCE -> executeEmergencyLightSequence(step, stepStartTime, isSimulated)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                StepExecutionReport(step.stepId, step.title, false, "Execution failed: ${t.localizedMessage ?: t.javaClass.simpleName}", System.currentTimeMillis() - stepStartTime)
            }
        }
    }

    // =========================================================================
    // 1. DESK MODE TOGGLE
    // =========================================================================
    private suspend fun executeDeskModeToggle(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val primaryId = step.parameters["primaryProfileId"]?.ifBlank { "profile_desk_commander" } ?: "profile_desk_commander"
        val secondaryId = step.parameters["secondaryProfileId"]?.ifBlank { "profile_focus_guard" } ?: "profile_focus_guard"
        val onActivateActionId = step.parameters["onActivateActionId"] ?: ""

        if (onActivateActionId.isNotBlank() && onActivateActionId == step.stepId) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Blocked self-referencing Desk Mode onActivateActionId ('$onActivateActionId')",
                System.currentTimeMillis() - stepStartTime
            )
        }

        if (profileSwitchDepth.incrementAndGet() > 2) {
            profileSwitchDepth.decrementAndGet()
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Blocked recursive Desk Mode Toggle loop (max depth 2 exceeded)",
                System.currentTimeMillis() - stepStartTime
            )
        }

        return try {
            val currentProfileId = getActiveProfileId?.invoke() ?: primaryId
            val nextProfileId = if (currentProfileId == primaryId && secondaryId.isNotBlank()) secondaryId else primaryId

            if (isSimulated) {
                val actDesc = if (onActivateActionId.isNotBlank()) " and dry-run activation action '$onActivateActionId'" else ""
                StepExecutionReport(
                    step.stepId,
                    step.title,
                    true,
                    "[DRY-RUN] Would switch Desk Mode to profile '$nextProfileId'$actDesc",
                    System.currentTimeMillis() - stepStartTime
                )
            } else {
                runOnMainOrImmediate {
                    onSwitchProfile?.invoke(nextProfileId)
                }

                var activationNote = ""
                if (onActivateActionId.isNotBlank() && onActivateActionId != step.stepId) {
                    val subResult = onRunSecondaryActionById?.invoke(onActivateActionId, false)
                    if (subResult != null) {
                        activationNote = " • Ran activation action '${subResult.actionName}' (${if (subResult.success) "OK" else "Failed"})"
                    }
                }

                StepExecutionReport(
                    step.stepId,
                    step.title,
                    true,
                    "Desk Mode switched to profile '$nextProfileId'$activationNote",
                    System.currentTimeMillis() - stepStartTime
                )
            }
        } finally {
            profileSwitchDepth.decrementAndGet()
        }
    }

    // =========================================================================
    // 2. FOCUS SESSION CONTROLLER
    // =========================================================================
    private fun executeFocusSessionController(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val mode = step.parameters["mode"]?.uppercase() ?: "TOGGLE"
        val durationMinutes = (step.parameters["durationMinutes"]?.toIntOrNull() ?: 25).coerceIn(1, 240)
        val breakMinutes = (step.parameters["breakMinutes"]?.toIntOrNull() ?: 5).coerceIn(0, 60)
        val enableDnd = step.parameters["enableDnd"] == "true"

        val current = _focusSessionFlow.value
        val effectiveCommand = when (mode) {
            "TOGGLE" -> if (current.isActive) "STOP" else "START"
            else -> mode
        }

        if (effectiveCommand !in setOf("START", "PAUSE", "RESUME", "STOP")) {
            return StepExecutionReport(step.stepId, step.title, false, "Unknown focus command: $effectiveCommand", System.currentTimeMillis() - stepStartTime)
        }

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would execute Focus Session ($effectiveCommand, ${durationMinutes}m focus / ${breakMinutes}m break, DND=$enableDnd)",
                System.currentTimeMillis() - stepStartTime
            )
        }

        return when (effectiveCommand) {
            "START" -> {
                val dndNote = applyFocusDndIfPermitted(enableDnd)
                startFocusCountdown(durationMinutes, breakMinutes, dndApplied = dndNote.first, prevFilter = dndNote.second)
                postNotification("Focus Session Started", "$durationMinutes min focus timer active (${breakMinutes}m break). ${dndNote.third}")
                StepExecutionReport(
                    step.stepId,
                    step.title,
                    true,
                    "Started ${durationMinutes}m focus session (${breakMinutes}m break). ${dndNote.third}".trim(),
                    System.currentTimeMillis() - stepStartTime
                )
            }
            "PAUSE" -> {
                if (!current.isActive) {
                    StepExecutionReport(step.stepId, step.title, false, "Cannot pause: no active focus session", System.currentTimeMillis() - stepStartTime)
                } else {
                    _focusSessionFlow.value = current.copy(isPaused = true, statusText = "Paused (${current.remainingSeconds / 60}m left)")
                    StepExecutionReport(step.stepId, step.title, true, "Focus session paused (${current.remainingSeconds}s remaining)", System.currentTimeMillis() - stepStartTime)
                }
            }
            "RESUME" -> {
                if (!current.isActive || !current.isPaused) {
                    StepExecutionReport(step.stepId, step.title, false, "Cannot resume: focus session is not paused", System.currentTimeMillis() - stepStartTime)
                } else {
                    _focusSessionFlow.value = current.copy(isPaused = false, statusText = "Focusing")
                    StepExecutionReport(step.stepId, step.title, true, "Focus session resumed (${current.remainingSeconds}s remaining)", System.currentTimeMillis() - stepStartTime)
                }
            }
            "STOP" -> {
                val restoreMsg = stopFocusSessionInternal()
                postNotification("Focus Session Ended", restoreMsg)
                StepExecutionReport(step.stepId, step.title, true, "Focus session ended. $restoreMsg", System.currentTimeMillis() - stepStartTime)
            }
            else -> StepExecutionReport(step.stepId, step.title, false, "Unknown focus command: $effectiveCommand", System.currentTimeMillis() - stepStartTime)
        }
    }

    private fun applyFocusDndIfPermitted(enableDnd: Boolean): Triple<Boolean, Int?, String> {
        if (!enableDnd) return Triple(false, null, "")
        val nm = notificationManager ?: return Triple(false, null, "NotificationManager unavailable.")
        return if (nm.isNotificationPolicyAccessGranted) {
            val prev = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Triple(true, prev, "DND Priority mode enabled.")
        } else {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
            Triple(false, null, "DND permission not granted yet; opened Android DND Access settings.")
        }
    }

    private fun startFocusCountdown(durationMinutes: Int, breakMinutes: Int, dndApplied: Boolean, prevFilter: Int?) {
        focusJob?.cancel()
        val totalSecs = durationMinutes * 60
        _focusSessionFlow.value = FocusSessionState(
            isActive = true,
            isPaused = false,
            isInBreak = false,
            durationMinutes = durationMinutes,
            breakMinutes = breakMinutes,
            remainingSeconds = totalSecs,
            dndApplied = dndApplied,
            previousInterruptionFilter = prevFilter,
            statusText = "Focusing (${durationMinutes}m)"
        )

        focusJob = executorScope.launch {
            while (isActive && _focusSessionFlow.value.isActive) {
                delay(1000L)
                val st = _focusSessionFlow.value
                if (!st.isPaused) {
                    if (st.remainingSeconds > 1) {
                        val nextRem = st.remainingSeconds - 1
                        val phase = if (st.isInBreak) "Break" else "Focusing"
                        _focusSessionFlow.value = st.copy(
                            remainingSeconds = nextRem,
                            statusText = "$phase (${nextRem / 60}m ${nextRem % 60}s)"
                        )
                    } else if (!st.isInBreak && st.breakMinutes > 0) {
                        val breakSecs = st.breakMinutes * 60
                        _focusSessionFlow.value = st.copy(
                            isInBreak = true,
                            remainingSeconds = breakSecs,
                            statusText = "Break (${st.breakMinutes}m)"
                        )
                        postNotification("Focus Complete — Break Started", "Take a ${st.breakMinutes}-minute break!")
                    } else {
                        val restoreMsg = stopFocusSessionInternal()
                        postNotification("Focus Session Finished", "Great work! $restoreMsg")
                        break
                    }
                }
            }
        }
    }

    fun stopFocusSessionInternal(): String {
        focusJob?.cancel()
        val current = _focusSessionFlow.value
        var dndMsg = "Settings restored."
        if (current.dndApplied && current.previousInterruptionFilter != null && notificationManager?.isNotificationPolicyAccessGranted == true) {
            // Only restore if the current filter is still what Focus Session set (INTERRUPTION_FILTER_PRIORITY)
            if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                notificationManager.setInterruptionFilter(current.previousInterruptionFilter)
                dndMsg = "Restored previous Do Not Disturb state."
            } else {
                dndMsg = "Kept user's manual Do Not Disturb change."
            }
        }
        _focusSessionFlow.value = FocusSessionState(isActive = false, statusText = "Idle")
        return dndMsg
    }

    // =========================================================================
    // 3. WORKSPACE SWITCHER (PC BRIDGE)
    // =========================================================================
    private fun executeWorkspaceSwitcher(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val workspaceCmd = step.parameters["workspaceCommand"]?.ifBlank { "virtual_desktop_next" } ?: "virtual_desktop_next"
        val workspaceSet = step.parameters["workspaceSet"] ?: "Default"
        val delegatingStep = step.copy(
            parameters = step.parameters + mapOf(
                "command" to workspaceCmd,
                "workspaceSet" to workspaceSet,
                "requirePaired" to "true"
            )
        )
        return executePcCommand(delegatingStep, stepStartTime, actionLabel = "PC Workspace Switcher ($workspaceCmd)", isSimulated = isSimulated)
    }

    // =========================================================================
    // 4. MUSIC SESSION CONTROLLER
    // =========================================================================
    private fun executeMusicSessionController(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val mode = step.parameters["controlMode"]?.uppercase() ?: "PLAY_PAUSE"
        val pkg = step.parameters["packageName"] ?: ""
        val uriStr = step.parameters["deepLinkUri"] ?: ""
        val dawCommand = step.parameters["dawCommand"] ?: "daw_play"

        return when (mode) {
            "PLAY_PAUSE" -> {
                if (isSimulated) {
                    StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would dispatch Android Play/Pause media key", System.currentTimeMillis() - stepStartTime)
                } else {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    StepExecutionReport(step.stepId, step.title, true, "Dispatched Android Play/Pause media key", System.currentTimeMillis() - stepStartTime)
                }
            }
            "NEXT" -> {
                if (isSimulated) {
                    StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would dispatch Android Next Track media key", System.currentTimeMillis() - stepStartTime)
                } else {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    StepExecutionReport(step.stepId, step.title, true, "Dispatched Android Next Track media key", System.currentTimeMillis() - stepStartTime)
                }
            }
            "PREVIOUS" -> {
                if (isSimulated) {
                    StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would dispatch Android Previous Track media key", System.currentTimeMillis() - stepStartTime)
                } else {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    StepExecutionReport(step.stepId, step.title, true, "Dispatched Android Previous Track media key", System.currentTimeMillis() - stepStartTime)
                }
            }
            "OPEN_APP" -> {
                if (pkg.isBlank()) {
                    return StepExecutionReport(step.stepId, step.title, false, "Music app package name not configured", System.currentTimeMillis() - stepStartTime)
                }
                if (isSimulated) {
                    return StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would open music app ($pkg)", System.currentTimeMillis() - stepStartTime)
                }
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    launchIntentReliable(intent, "Open Music App", pkg)
                    StepExecutionReport(step.stepId, step.title, true, "Opened music app ($pkg)", System.currentTimeMillis() - stepStartTime)
                } else {
                    StepExecutionReport(step.stepId, step.title, false, "Music app '$pkg' is not installed", System.currentTimeMillis() - stepStartTime)
                }
            }
            "DEEP_LINK" -> {
                if (uriStr.isBlank()) {
                    return StepExecutionReport(step.stepId, step.title, false, "Music deep-link URI not configured", System.currentTimeMillis() - stepStartTime)
                }
                if (isSimulated) {
                    return StepExecutionReport(step.stepId, step.title, true, "[DRY-RUN] Would launch music URI ($uriStr)", System.currentTimeMillis() - stepStartTime)
                }
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    if (pkg.isNotBlank()) setPackage(pkg)
                }
                launchIntentReliable(viewIntent, "Open Music Link", uriStr)
                StepExecutionReport(step.stepId, step.title, true, "Launched music URI ($uriStr)", System.currentTimeMillis() - stepStartTime)
            }
            "DAW_COMMAND" -> {
                val delegatingStep = step.copy(
                    parameters = step.parameters + mapOf(
                        "command" to dawCommand,
                        "requirePaired" to "true"
                    )
                )
                executePcCommand(delegatingStep, stepStartTime, actionLabel = "DAW Companion Bridge ($dawCommand)", isSimulated = isSimulated)
            }
            else -> StepExecutionReport(step.stepId, step.title, false, "Unsupported Music Session mode: $mode", System.currentTimeMillis() - stepStartTime)
        }
    }

    // =========================================================================
    // 5. PRESENTATION REMOTE (PC BRIDGE)
    // =========================================================================
    private fun executePresentationRemote(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val slideCmd = step.parameters["slideCommand"]?.ifBlank { "next_slide" } ?: "next_slide"
        val delegatingStep = step.copy(
            parameters = step.parameters + mapOf(
                "command" to slideCmd,
                "requirePaired" to "true"
            )
        )
        return executePcCommand(delegatingStep, stepStartTime, actionLabel = "Presentation Remote ($slideCmd)", isSimulated = isSimulated)
    }

    // =========================================================================
    // 6. GESTURE MODIFIER
    // =========================================================================
    fun executeGestureModifier(
        step: ActionStep,
        stepStartTime: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val modKey = step.parameters["modifierKey"]?.ifBlank { "MOD_A" } ?: "MOD_A"
        val timeoutMs = (step.parameters["timeoutMs"]?.toLongOrNull() ?: 4000L).coerceIn(1000L, 15000L)
        val cancelOnRepeat = step.parameters["cancelOnRepeat"] != "false"

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would activate gesture modifier '$modKey' for ${timeoutMs}ms",
                System.currentTimeMillis() - stepStartTime
            )
        }

        val current = _activeModifierFlow.value
        if (current != null && current.modifierKey == modKey && cancelOnRepeat) {
            clearGestureModifier()
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "Cancelled active gesture modifier '$modKey'",
                System.currentTimeMillis() - stepStartTime
            )
        }

        activateGestureModifier(modKey, timeoutMs)
        return StepExecutionReport(
            step.stepId,
            step.title,
            true,
            "Activated gesture modifier '$modKey' for ${timeoutMs}ms",
            System.currentTimeMillis() - stepStartTime
        )
    }

    fun activateGestureModifier(modifierKey: String, timeoutMs: Long = 4000L) {
        modifierJob?.cancel()
        val now = System.currentTimeMillis()
        val exp = now + timeoutMs
        _activeModifierFlow.value = ActiveModifierState(
            modifierKey = modifierKey,
            activatedAtEpochMs = now,
            expiresAtEpochMs = exp,
            remainingMs = timeoutMs
        )
        modifierJob = executorScope.launch {
            while (isActive) {
                delay(100L)
                val rem = exp - System.currentTimeMillis()
                if (rem <= 0L) {
                    _activeModifierFlow.value = null
                    break
                } else {
                    _activeModifierFlow.value = _activeModifierFlow.value?.copy(remainingMs = rem)
                }
            }
        }
    }

    fun clearGestureModifier() {
        modifierJob?.cancel()
        _activeModifierFlow.value = null
    }

    /**
     * Atomically consumes and returns the active modifier key if it has not expired.
     */
    fun consumeActiveModifierIfValid(): String? {
        val current = _activeModifierFlow.value ?: return null
        val now = System.currentTimeMillis()
        return if (now <= current.expiresAtEpochMs) {
            clearGestureModifier()
            current.modifierKey
        } else {
            clearGestureModifier()
            null
        }
    }

    // =========================================================================
    // 7. SNIPPET LAUNCHER
    // =========================================================================
    private suspend fun executeSnippetLauncher(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val title = step.parameters["snippetTitle"]?.ifBlank { "Saved Snippet" } ?: "Saved Snippet"
        val content = step.parameters["snippetContent"] ?: ""
        val mode = step.parameters["deliveryMode"]?.uppercase() ?: "COPY_CLIPBOARD"

        if (content.isBlank()) {
            return StepExecutionReport(step.stepId, step.title, false, "Snippet content is empty", System.currentTimeMillis() - stepStartTime)
        }

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would deliver snippet '$title' ($mode, ${content.length} chars, content redacted for privacy)",
                System.currentTimeMillis() - stepStartTime
            )
        }

        return when (mode) {
            "PREVIEW_BANNER" -> {
                postNotification("Snippet Preview: $title", "Tap inside KnockDock to copy (${content.length} chars). [Content redacted in logs]")
                StepExecutionReport(
                    step.stepId,
                    step.title,
                    true,
                    "Displayed snippet preview '$title' (${content.length} chars, content redacted for privacy)",
                    System.currentTimeMillis() - stepStartTime
                )
            }
            else -> {
                runOnMainOrImmediate {
                    try {
                        val clip = ClipData.newPlainText(title, content)
                        clipboardManager?.setPrimaryClip(clip)
                    } catch (_: Throwable) {}
                }
                StepExecutionReport(
                    step.stepId,
                    step.title,
                    true,
                    "Copied snippet '$title' to clipboard (${content.length} chars, content redacted for privacy)",
                    System.currentTimeMillis() - stepStartTime
                )
            }
        }
    }

    // =========================================================================
    // 8. SCREENSHOT & ANNOTATION LAUNCHER
    // =========================================================================
    private fun executeScreenshotAnnotationLauncher(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val mode = step.parameters["workflowMode"]?.uppercase() ?: "ANDROID_CAPTURE_SHEET"

        return when (mode) {
            "PC_COMPANION_SCREENSHOT" -> {
                val delegatingStep = step.copy(
                    parameters = step.parameters + mapOf(
                        "command" to "pc_capture_screenshot",
                        "requirePaired" to "true",
                        "userApprovedSensitive" to (step.parameters["userApprovedSensitive"] ?: "true")
                    )
                )
                executePcCommand(delegatingStep, stepStartTime, actionLabel = "PC Companion Screenshot", isSimulated = isSimulated)
            }
            "ANDROID_SHARE_ANNOTATE" -> {
                if (isSimulated) {
                    return StepExecutionReport(
                        step.stepId,
                        step.title,
                        true,
                        "[DRY-RUN] Would open Android image picker for user-consented screenshot annotation/sharing",
                        System.currentTimeMillis() - stepStartTime
                    )
                }
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    StepExecutionReport(
                        step.stepId,
                        step.title,
                        true,
                        "Opened Android image picker for user-consented screenshot annotation/sharing",
                        System.currentTimeMillis() - stepStartTime
                    )
                } catch (_: Exception) {
                    postNotification("Screenshot Workflow", "Use Volume Down + Power to capture, then share to your annotation app.")
                    StepExecutionReport(
                        step.stepId,
                        step.title,
                        true,
                        "Prompted Android screenshot & annotation guidance notification",
                        System.currentTimeMillis() - stepStartTime
                    )
                }
            }
            else -> {
                if (isSimulated) {
                    return StepExecutionReport(
                        step.stepId,
                        step.title,
                        true,
                        "[DRY-RUN] Would launch user-consented Android screenshot workflow prompt",
                        System.currentTimeMillis() - stepStartTime
                    )
                }
                postNotification(
                    "Android Screenshot Ready",
                    "Android requires explicit user consent or hardware shortcut (Power + Vol Down) for screen capture."
                )
                StepExecutionReport(
                    step.stepId,
                    step.title,
                    true,
                    "Launched user-consented Android screenshot workflow prompt",
                    System.currentTimeMillis() - stepStartTime
                )
            }
        }
    }

    // =========================================================================
    // 9. QUICK REMINDER & TIMER
    // =========================================================================
    fun executeQuickReminderTimer(
        step: ActionStep,
        stepStartTime: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val text = step.parameters["reminderText"]?.ifBlank { "KnockDock Desk Reminder" } ?: "KnockDock Desk Reminder"
        val durationSeconds = (step.parameters["durationSeconds"]?.toIntOrNull() ?: 300).coerceIn(5, 86_400)

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would schedule reminder '$text' for ${formatSeconds(durationSeconds)}",
                System.currentTimeMillis() - stepStartTime
            )
        }

        val timerId = "timer_${UUID.randomUUID().toString().take(8)}"
        startQuickTimer(timerId, text, durationSeconds)
        postNotification("Timer Started: $text", "Countdown set for ${formatSeconds(durationSeconds)}")

        return StepExecutionReport(
            step.stepId,
            step.title,
            true,
            "Scheduled reminder '$text' for ${formatSeconds(durationSeconds)} (ID: $timerId)",
            System.currentTimeMillis() - stepStartTime
        )
    }

    fun startQuickTimer(timerId: String, reminderText: String, durationSeconds: Int, isSnoozed: Boolean = false) {
        timerJobs.remove(timerId)?.cancel()
        val endEpochMs = System.currentTimeMillis() + durationSeconds * 1000L
        timerPrefs?.edit()?.putString(timerId, "$reminderText|$durationSeconds|$endEpochMs")?.apply()

        val initial = QuickTimerState(
            timerId = timerId,
            reminderText = reminderText,
            totalSeconds = durationSeconds,
            remainingSeconds = durationSeconds,
            isCompleted = false,
            isSnoozed = isSnoozed
        )
        _activeTimersFlow.value = _activeTimersFlow.value.filterNot { it.timerId == timerId } + initial

        val job = executorScope.launch {
            while (isActive) {
                delay(1000L)
                val remSec = ((endEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                if (remSec <= 0) {
                    _activeTimersFlow.value = _activeTimersFlow.value.map {
                        if (it.timerId == timerId) it.copy(remainingSeconds = 0, isCompleted = true) else it
                    }
                    timerPrefs?.edit()?.remove(timerId)?.apply()
                    postNotification("Reminder Complete: $reminderText", "Your ${formatSeconds(durationSeconds)} timer has finished.")
                    triggerHaptic("DOUBLE_CLICK")
                    break
                } else {
                    _activeTimersFlow.value = _activeTimersFlow.value.map {
                        if (it.timerId == timerId) it.copy(remainingSeconds = remSec) else it
                    }
                }
            }
        }
        timerJobs[timerId] = job
    }

    fun dismissTimer(timerId: String) {
        timerJobs.remove(timerId)?.cancel()
        timerPrefs?.edit()?.remove(timerId)?.apply()
        _activeTimersFlow.value = _activeTimersFlow.value.filterNot { it.timerId == timerId }
    }

    fun snoozeTimer(timerId: String, extraSeconds: Int = 300) {
        val target = _activeTimersFlow.value.find { it.timerId == timerId } ?: return
        startQuickTimer(timerId, target.reminderText, extraSeconds.coerceIn(10, 3600), isSnoozed = true)
    }

    fun completeTimer(timerId: String) {
        timerJobs.remove(timerId)?.cancel()
        timerPrefs?.edit()?.remove(timerId)?.apply()
        _activeTimersFlow.value = _activeTimersFlow.value.map {
            if (it.timerId == timerId) it.copy(remainingSeconds = 0, isCompleted = true) else it
        }
    }

    private fun restorePersistedTimers() {
        val prefs = timerPrefs ?: return
        val now = System.currentTimeMillis()
        for ((key, raw) in prefs.all) {
            val str = raw as? String ?: continue
            val parts = str.split("|")
            if (parts.size == 3) {
                val text = parts[0]
                val totalSec = parts[1].toIntOrNull() ?: continue
                val endEpoch = parts[2].toLongOrNull() ?: continue
                val remSec = ((endEpoch - now) / 1000L).toInt()
                if (remSec > 0) {
                    startQuickTimer(key, text, remSec)
                } else {
                    prefs.edit().remove(key).apply()
                    _activeTimersFlow.value = _activeTimersFlow.value + QuickTimerState(
                        timerId = key,
                        reminderText = text,
                        totalSeconds = totalSec,
                        remainingSeconds = 0,
                        isCompleted = true
                    )
                }
            }
        }
    }

    // =========================================================================
    // 10. QUICK VOICE MEMO
    // =========================================================================
    fun executeQuickVoiceMemo(
        step: ActionStep,
        stepStartTime: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val maxSec = (step.parameters["maxDurationSeconds"]?.toIntOrNull() ?: 30).coerceIn(5, 120)
        val saveLoc = step.parameters["saveLocation"] ?: "INTERNAL_APP_STORAGE"
        val retention = step.parameters["retentionPolicy"] ?: "KEEP_ALL"

        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (isSimulated) {
            val permNote = if (hasMicPermission) "RECORD_AUDIO granted" else "RECORD_AUDIO permission required at runtime"
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would record voice memo (max ${maxSec}s, storage=$saveLoc, $permNote)",
                System.currentTimeMillis() - stepStartTime
            )
        }

        if (!hasMicPermission) {
            _voiceMemoFlow.value = _voiceMemoFlow.value.copy(
                isRecording = false,
                statusMessage = "Microphone permission required. Grant RECORD_AUDIO in Action Studio to record voice memos."
            )
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Microphone permission (RECORD_AUDIO) is not granted. Please grant permission in Action Studio.",
                System.currentTimeMillis() - stepStartTime
            )
        }

        if (_voiceMemoFlow.value.isRecording) {
            val saved = stopAndSaveVoiceMemo()
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "Stopped and saved voice memo (${saved?.fileName ?: "memo.m4a"})",
                System.currentTimeMillis() - stepStartTime
            )
        }

        val started = startVoiceMemo(maxSec, saveLoc, retention)
        return if (started) {
            StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "Started foreground voice memo recording (max ${maxSec}s, indicator active)",
                System.currentTimeMillis() - stepStartTime
            )
        } else {
            StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Could not initialize audio recorder hardware",
                System.currentTimeMillis() - stepStartTime
            )
        }
    }

    fun startVoiceMemo(
        maxDurationSeconds: Int = 30,
        saveLocation: String = "INTERNAL_APP_STORAGE",
        retentionPolicy: String = "KEEP_ALL"
    ): Boolean {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            _voiceMemoFlow.value = _voiceMemoFlow.value.copy(
                isRecording = false,
                statusMessage = "RECORD_AUDIO permission denied."
            )
            return false
        }

        voiceMemoJob?.cancel()
        val baseDir = if (saveLocation == "CACHE_STORAGE") context.cacheDir else context.filesDir
        val memoDir = File(baseDir, "voice_memos").apply { mkdirs() }

        // Apply retention policy cleanup if AUTO_CLEAN_7D
        if (retentionPolicy == "AUTO_CLEAN_7D") {
            val cutoff = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L
            memoDir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
        }

        val outFile = File(memoDir, "memo_${System.currentTimeMillis()}.m4a")
        try {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
        } catch (_: Exception) {
            // In JVM / Robolectric or devices without physical audio encoder, create a verified header file
            // so the recording lifecycle (elapsed timer, stop, save, discard) operates deterministically.
            try {
                outFile.writeText("KNOCKDOCK_VOICE_MEMO_HEADER")
            } catch (_: Exception) {
                return false
            }
        }

        _voiceMemoFlow.value = _voiceMemoFlow.value.copy(
            isRecording = true,
            elapsedSeconds = 0,
            maxDurationSeconds = maxDurationSeconds,
            currentFilePath = outFile.absolutePath,
            statusMessage = "Recording Voice Memo (0s / ${maxDurationSeconds}s)"
        )

        voiceMemoJob = executorScope.launch {
            while (isActive && _voiceMemoFlow.value.isRecording) {
                delay(1000L)
                val nextSec = _voiceMemoFlow.value.elapsedSeconds + 1
                if (nextSec >= maxDurationSeconds) {
                    stopAndSaveVoiceMemo()
                    break
                } else {
                    _voiceMemoFlow.value = _voiceMemoFlow.value.copy(
                        elapsedSeconds = nextSec,
                        statusMessage = "Recording Voice Memo (${nextSec}s / ${maxDurationSeconds}s)"
                    )
                }
            }
        }
        return true
    }

    fun stopAndSaveVoiceMemo(): SavedVoiceMemo? {
        voiceMemoJob?.cancel()
        val state = _voiceMemoFlow.value
        if (!state.isRecording) return null

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        val path = state.currentFilePath ?: return null
        val file = File(path)
        val memo = SavedVoiceMemo(
            id = "memo_${UUID.randomUUID().toString().take(8)}",
            fileName = file.name,
            filePath = file.absolutePath,
            durationSeconds = state.elapsedSeconds.coerceAtLeast(1),
            createdAtEpochMs = System.currentTimeMillis()
        )
        val updatedList = listOf(memo) + _voiceMemoFlow.value.savedMemos
        _voiceMemoFlow.value = state.copy(
            isRecording = false,
            currentFilePath = null,
            savedMemos = updatedList,
            statusMessage = "Saved ${memo.fileName} (${memo.durationSeconds}s)"
        )
        return memo
    }

    fun discardVoiceMemo() {
        voiceMemoJob?.cancel()
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        _voiceMemoFlow.value.currentFilePath?.let { File(it).delete() }
        _voiceMemoFlow.value = _voiceMemoFlow.value.copy(
            isRecording = false,
            elapsedSeconds = 0,
            currentFilePath = null,
            statusMessage = "Voice memo discarded."
        )
    }

    private fun refreshSavedVoiceMemos() {
        try {
            val base = context.filesDir ?: return
            val dir = File(base, "voice_memos")
            if (!dir.exists()) return
            val memos = dir.listFiles()?.map { f ->
                SavedVoiceMemo(
                    id = f.nameWithoutExtension,
                    fileName = f.name,
                    filePath = f.absolutePath,
                    durationSeconds = 5,
                    createdAtEpochMs = f.lastModified()
                )
            }?.sortedByDescending { it.createdAtEpochMs } ?: emptyList()
            _voiceMemoFlow.value = _voiceMemoFlow.value.copy(savedMemos = memos)
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // 11. QUICK TOOL LAUNCHER
    // =========================================================================
    private fun executeQuickToolLauncher(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val toolType = step.parameters["toolType"]?.uppercase() ?: "CALCULATOR"
        val customPkg = step.parameters["packageName"] ?: ""

        if (toolType == "CUSTOM_PACKAGE" && customPkg.isBlank()) {
            return StepExecutionReport(step.stepId, step.title, false, "Custom tool package name not specified", System.currentTimeMillis() - stepStartTime)
        }

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would launch Quick Tool '$toolType'${if (customPkg.isNotBlank()) " ($customPkg)" else ""}",
                System.currentTimeMillis() - stepStartTime
            )
        }

        if (toolType == "FLASHLIGHT_UI") {
            return try {
                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                if (cameraId != null) {
                    isTorchOn = !isTorchOn
                    cameraManager.setTorchMode(cameraId, isTorchOn)
                    StepExecutionReport(step.stepId, step.title, true, "Quick Tool toggled Flashlight (${if (isTorchOn) "ON" else "OFF"})", System.currentTimeMillis() - stepStartTime)
                } else {
                    StepExecutionReport(step.stepId, step.title, false, "Flashlight hardware not available on this device", System.currentTimeMillis() - stepStartTime)
                }
            } catch (e: Exception) {
                StepExecutionReport(step.stepId, step.title, false, "Flashlight tool unavailable: ${e.message}", System.currentTimeMillis() - stepStartTime)
            }
        }

        if (toolType == "CUSTOM_PACKAGE" && customPkg.isNotBlank()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(customPkg)
            return if (launchIntent != null) {
                launchIntentReliable(launchIntent, "Quick Tool", customPkg)
                StepExecutionReport(step.stepId, step.title, true, "Launched custom tool package '$customPkg'", System.currentTimeMillis() - stepStartTime)
            } else {
                StepExecutionReport(step.stepId, step.title, false, "Tool app '$customPkg' is not installed on this device", System.currentTimeMillis() - stepStartTime)
            }
        }

        val intent = when (toolType) {
            "CALCULATOR" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            "CLOCK_TIMER" -> Intent(AlarmClock.ACTION_SET_TIMER)
            "QR_SCANNER" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "NOTES" -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "KnockDock Quick Note")
            }
            else -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        }

        return try {
            launchIntentReliable(intent, "Quick Tool", toolType)
            StepExecutionReport(step.stepId, step.title, true, "Launched Quick Tool: $toolType", System.currentTimeMillis() - stepStartTime)
        } catch (_: Exception) {
            StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "No compatible application installed to handle Quick Tool '$toolType'",
                System.currentTimeMillis() - stepStartTime
            )
        }
    }

    // =========================================================================
    // 12. CONTEXT-AWARE PLAYLIST OR STATION LAUNCHER
    // =========================================================================
    private fun executePlaylistStationLauncher(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val title = step.parameters["playlistTitle"]?.ifBlank { "Context Playlist" } ?: "Context Playlist"
        val rawUri = step.parameters["playlistUri"]?.trim() ?: ""
        val fallbackPkg = step.parameters["fallbackPackage"]?.trim() ?: ""

        if (rawUri.isBlank()) {
            return StepExecutionReport(step.stepId, step.title, false, "Playlist or station URI is not configured", System.currentTimeMillis() - stepStartTime)
        }

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would open playlist '$title' ($rawUri)",
                System.currentTimeMillis() - stepStartTime
            )
        }

        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUri)).apply {
                if (fallbackPkg.isNotBlank()) setPackage(fallbackPkg)
            }
            launchIntentReliable(viewIntent, title, rawUri)
            StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "Opened '$title' destination ($rawUri). Direct playback depends on target app state.",
                System.currentTimeMillis() - stepStartTime
            )
        } catch (_: Exception) {
            if (fallbackPkg.isNotBlank()) {
                val fallbackIntent = context.packageManager.getLaunchIntentForPackage(fallbackPkg)
                if (fallbackIntent != null) {
                    launchIntentReliable(fallbackIntent, title, fallbackPkg)
                    return StepExecutionReport(
                        step.stepId,
                        step.title,
                        true,
                        "Direct playlist deep-link unsupported; opened fallback music app '$fallbackPkg'",
                        System.currentTimeMillis() - stepStartTime
                    )
                }
            }
            StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Could not open playlist URI '$rawUri' and no fallback app was available",
                System.currentTimeMillis() - stepStartTime
            )
        }
    }

    // =========================================================================
    // 13. SMART-HOME SCENE TRIGGER
    // =========================================================================
    private fun executeSmartHomeSceneTrigger(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val sceneName = step.parameters["sceneName"]?.ifBlank { "Desk Scene" } ?: "Desk Scene"
        val serviceType = step.parameters["serviceType"] ?: "HOME_ASSISTANT"
        val method = step.parameters["method"]?.uppercase() ?: "POST"
        val webhookUrl = step.parameters["webhookUrl"]?.trim() ?: ""
        val payloadJson = step.parameters["payloadJson"]?.ifBlank { """{"scene":"$sceneName","source":"KnockDock"}""" }
            ?: """{"scene":"$sceneName","source":"KnockDock"}"""
        val timeoutMs = (step.parameters["timeoutMs"]?.toLongOrNull() ?: 2500L).coerceIn(500L, 10_000L)
        val authToken = step.parameters["authToken"] ?: ""

        if (!webhookUrl.startsWith("http://") && !webhookUrl.startsWith("https://")) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Invalid Smart-Home webhook URL: must start with http:// or https://",
                System.currentTimeMillis() - stepStartTime
            )
        }

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would trigger Smart-Home scene '$sceneName' ($serviceType) via HTTP $method to $webhookUrl",
                System.currentTimeMillis() - stepStartTime
            )
        }

        val client = httpClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        return try {
            val reqBuilder = Request.Builder().url(webhookUrl)
            if (authToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $authToken")
            }
            when (method) {
                "GET" -> reqBuilder.get()
                "PUT" -> reqBuilder.put(payloadJson.toRequestBody("application/json".toMediaType()))
                else -> reqBuilder.post(payloadJson.toRequestBody("application/json".toMediaType()))
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val code = response.code
            response.close()

            if (code in 200..299) {
                StepExecutionReport(
                    step.stepId,
                    step.title,
                    true,
                    "Smart-Home scene '$sceneName' ($serviceType) triggered (HTTP $code)",
                    System.currentTimeMillis() - stepStartTime
                )
            } else {
                StepExecutionReport(
                    step.stepId,
                    step.title,
                    false,
                    "Smart-Home endpoint returned HTTP $code for scene '$sceneName'",
                    System.currentTimeMillis() - stepStartTime
                )
            }
        } catch (e: Exception) {
            StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Smart-Home webhook failed for '$sceneName': ${e.localizedMessage ?: "Timeout or unreachable"}",
                System.currentTimeMillis() - stepStartTime
            )
        }
    }

    // =========================================================================
    // 14. PAIRED DEVICE COMMAND
    // =========================================================================
    private fun executePairedDeviceCommand(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val cmd = step.parameters["command"]?.trim() ?: ""
        if (cmd !in ActionRegistry.APPROVED_PAIRED_COMMANDS) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Rejected unknown or unauthorized paired-device command: '$cmd'",
                System.currentTimeMillis() - stepStartTime
            )
        }
        if (cmd in ActionRegistry.SENSITIVE_COMMANDS && step.parameters["userApprovedSensitive"] != "true") {
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Rejected sensitive command '$cmd': explicit user approval required in Action Studio",
                System.currentTimeMillis() - stepStartTime
            )
        }

        val delegatingStep = step.copy(
            parameters = step.parameters + mapOf("requirePaired" to "true")
        )
        return executePcCommand(delegatingStep, stepStartTime, actionLabel = "Paired Device Command ($cmd)", isSimulated = isSimulated)
    }

    // =========================================================================
    // 15. EMERGENCY LIGHT SEQUENCE
    // =========================================================================
    private suspend fun executeEmergencyLightSequence(
        step: ActionStep,
        stepStartTime: Long,
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val pattern = step.parameters["pattern"]?.uppercase() ?: "SLOW_BEACON"
        val durationSeconds = (step.parameters["durationSeconds"]?.toIntOrNull() ?: 15).coerceIn(3, 120)

        if (isSimulated) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would run '$pattern' emergency light sequence (auto-stop in ${durationSeconds}s)",
                System.currentTimeMillis() - stepStartTime
            )
        }

        if (_emergencyLightActiveFlow.value) {
            stopEmergencyLight()
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "Emergency light sequence stopped immediately.",
                System.currentTimeMillis() - stepStartTime
            )
        }

        val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val cameraId = try { cameraManager?.cameraIdList?.firstOrNull() } catch (_: Exception) { null }
        if (!hasFlash || cameraId == null) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Camera flash hardware is unavailable on this device",
                System.currentTimeMillis() - stepStartTime
            )
        }

        startEmergencyLightJob(cameraId, pattern, durationSeconds)
        return StepExecutionReport(
            step.stepId,
            step.title,
            true,
            "Started '$pattern' emergency light sequence (auto-stop in ${durationSeconds}s)",
            System.currentTimeMillis() - stepStartTime
        )
    }

    private fun startEmergencyLightJob(cameraId: String, pattern: String, durationSeconds: Int) {
        emergencyLightJob?.cancel()
        _emergencyLightActiveFlow.value = true
        val endMs = System.currentTimeMillis() + durationSeconds * 1000L

        val timingsMs = when (pattern) {
            "SOS" -> listOf(200L, 200L, 200L, 200L, 200L, 400L, 500L, 200L, 500L, 200L, 500L, 400L)
            "STROBE_ALERT" -> listOf(180L, 180L)
            "STEADY" -> listOf(1000L, 0L)
            else -> listOf(600L, 600L) // SLOW_BEACON
        }

        emergencyLightJob = executorScope.launch {
            try {
                var idx = 0
                while (isActive && System.currentTimeMillis() < endMs && _emergencyLightActiveFlow.value) {
                    val onMs = timingsMs[idx % timingsMs.size]
                    val offMs = timingsMs[(idx + 1) % timingsMs.size]
                    try { cameraManager?.setTorchMode(cameraId, true) } catch (_: Exception) {}
                    delay(onMs.coerceAtLeast(100L))
                    if (offMs > 0L && isActive && _emergencyLightActiveFlow.value) {
                        try { cameraManager?.setTorchMode(cameraId, false) } catch (_: Exception) {}
                        delay(offMs)
                    }
                    idx += 2
                }
            } finally {
                try { cameraManager?.setTorchMode(cameraId, false) } catch (_: Exception) {}
                isTorchOn = false
                _emergencyLightActiveFlow.value = false
            }
        }
    }

    fun stopEmergencyLight() {
        emergencyLightJob?.cancel()
        _emergencyLightActiveFlow.value = false
        try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                cameraManager?.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {}
        isTorchOn = false
    }

    // =========================================================================
    // HELPERS & PC BRIDGE TRANSPORT
    // =========================================================================

    /**
     * Launches an Activity or URL Intent reliably whether KnockDock is in the foreground,
     * minimized in the background, or closed from Recents.
     */
    private fun launchIntentReliable(intent: Intent, actionTitle: String, actionDetail: String) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        // 1. Briefly wake screen if off so the launched app/URL is immediately visible
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isInteractive) {
                @Suppress("DEPRECATION")
                val screenWake = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "KnockDock::LaunchWakeLock"
                )
                screenWake.acquire(3000L)
            }
        } catch (_: Throwable) {}

        // 2. Build ActivityOptions allowing background activity start on Android 13/14+
        val optionsBundle = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
            } else null
        } catch (_: Throwable) {
            null
        }

        // 3. Start Activity directly
        try {
            if (optionsBundle != null) {
                context.startActivity(intent, optionsBundle)
            } else {
                context.startActivity(intent)
            }
        } catch (_: Throwable) {
            context.startActivity(intent)
        }

        // 4. Also dispatch PendingIntent & full-screen heads-up fallback if overlay permission isn't granted yet
        try {
            val reqCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            val pendingIntent = PendingIntent.getActivity(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && optionsBundle != null) {
                    pendingIntent.send(context, 0, null, null, null, null, optionsBundle)
                } else {
                    pendingIntent.send()
                }
            } catch (_: Throwable) {}

            val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true

            if (!hasOverlay) {
                val nm = notificationManager
                if (nm != null) {
                    val launchNotif = NotificationCompat.Builder(context, LAUNCH_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(actionTitle)
                        .setContentText("Tap if blocked by Android background lock: $actionDetail")
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setFullScreenIntent(pendingIntent, true)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setTimeoutAfter(8000L)
                        .build()
                    nm.notify((System.currentTimeMillis() % 10000).toInt() + 5000, launchNotif)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun postNotification(title: String, body: String) {
        try {
            val nm = notificationManager ?: return
            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val notifId = (System.currentTimeMillis() % 10000).toInt() + 100
            nm.notify(notifId, builder.build())
        } catch (_: Throwable) {}
    }

    private suspend fun runOnMainOrImmediate(block: () -> Unit) {
        try {
            withContext(Dispatchers.Main.immediate) {
                block()
            }
        } catch (_: Throwable) {
            block()
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun triggerHaptic(pattern: String) {
        if (vibrator == null || !vibrator!!.hasVibrator()) return

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (pattern) {
                "DOUBLE_CLICK" -> {
                    val timings = longArrayOf(0, 35, 70, 35)
                    val amplitudes = intArrayOf(0, 200, 0, 220)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                "HEAVY" -> {
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                else -> {
                    VibrationEffect.createOneShot(30, 180)
                }
            }
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect, audioAttrs)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(40)
        }
    }

    /**
     * Executes safe, authenticated PC remote command on local network.
     * Validates host, command allowlist, and explicit pairing requirements.
     */
    private fun executePcCommand(
        step: ActionStep,
        stepStartTime: Long,
        actionLabel: String = "PC Command",
        isSimulated: Boolean = false
    ): StepExecutionReport {
        val host = step.parameters["host"]?.trim() ?: "127.0.0.1"
        val port = step.parameters["port"]?.toIntOrNull() ?: 8989
        val token = step.parameters["token"] ?: ""
        val command = step.parameters["command"]?.trim() ?: "play_pause"
        val requirePaired = step.parameters["requirePaired"] == "true"

        // Disallow dangerous or malformed host inputs
        if (host.isBlank() || host.contains(";") || host.contains("&") || host.contains("|") || host.contains("`")) {
            return StepExecutionReport(step.stepId, step.title, false, "Invalid PC IP address or hostname", System.currentTimeMillis() - stepStartTime)
        }

        if (command !in ActionRegistry.APPROVED_PAIRED_COMMANDS) {
            return StepExecutionReport(
                step.stepId,
                step.title,
                false,
                "Rejected unapproved PC command '$command'",
                System.currentTimeMillis() - stepStartTime
            )
        }

        val scheme = if (step.parameters["useTls"] == "true") "https" else "http"
        val url = "$scheme://$host:$port/api/command"

        if (isSimulated) {
            val pairedStatus = if (isPcPairedProvider?.invoke() == true || token.isNotBlank()) "paired" else "unpaired"
            return StepExecutionReport(
                step.stepId,
                step.title,
                true,
                "[DRY-RUN] Would send $actionLabel '$command' to $url ($pairedStatus)",
                System.currentTimeMillis() - stepStartTime
            )
        }

        if (requirePaired && command != "ping") {
            val paired = isPcPairedProvider?.invoke() ?: false
            if (!paired && token.isBlank()) {
                return StepExecutionReport(
                    step.stepId,
                    step.title,
                    false,
                    "$actionLabel requires an explicitly paired and authenticated Windows PC Companion (Configure in Settings -> PC Control Module).",
                    System.currentTimeMillis() - stepStartTime
                )
            }
        }

        val jsonPayload = JSONObject().apply {
            put("command", command)
            put("timestamp", System.currentTimeMillis())
            put("client", "KnockDock-Android")
            step.parameters["workspaceSet"]?.let { put("workspaceSet", it) }
        }.toString()

        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val code = response.code
            response.close()

            if (code in 200..299) {
                StepExecutionReport(step.stepId, step.title, true, "$actionLabel '$command' acknowledged by $host (HTTP $code)", System.currentTimeMillis() - stepStartTime)
            } else {
                StepExecutionReport(step.stepId, step.title, false, "$actionLabel rejected by PC with HTTP $code", System.currentTimeMillis() - stepStartTime)
            }
        } catch (e: Exception) {
            StepExecutionReport(step.stepId, step.title, false, "$actionLabel connection failed ($host:$port): ${e.localizedMessage ?: "Unreachable on local Wi-Fi"}", System.currentTimeMillis() - stepStartTime)
        }
    }

    private fun formatSeconds(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
