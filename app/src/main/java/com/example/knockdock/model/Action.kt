package com.example.knockdock.model

/**
 * Types of macro actions supported by KnockDock, including original core actions and
 * the 15 expanded Workspace, Music/Presentation, Productivity/Capture, and Smart-Home/Connected actions.
 */
enum class ActionType(
    val categoryName: String,
    val displayName: String,
    val iconName: String,
    val requiresPcBridge: Boolean = false
) {
    // 1. Local App Actions
    LAUNCH_APP("Local App", "Launch Installed App", "Apps"),
    LAUNCH_URL("Local App", "Open URL", "Language"),
    SYSTEM_SETTING("Local App", "Open System Screen", "Settings"),

    // 2. Media Controls
    MEDIA_PLAY_PAUSE("Media Control", "Play / Pause Media", "PlayCircle"),
    MEDIA_NEXT("Media Control", "Next Track", "SkipNext"),
    MEDIA_PREVIOUS("Media Control", "Previous Track", "SkipPrevious"),
    MEDIA_VOLUME_MUTE("Media Control", "Mute Audio", "VolumeOff"),

    // 3. Utility Actions
    NOTIFICATION("Utility", "Post Notification", "Notifications"),
    TOGGLE_FLASHLIGHT("Utility", "Toggle Flashlight", "FlashlightOn"),
    HAPTIC_PULSE("Utility", "Haptic Alert", "Vibration"),
    AUDIO_CHIRP("Utility", "Play Sound Chirp", "VolumeUp"),
    TOGGLE_MONITORING("Utility", "Toggle KnockDock Monitoring", "PowerSettingsNew"),
    SWITCH_PROFILE("Utility", "Switch Profile", "Layers"),

    // 4. Computer Actions (Local Network)
    PC_COMMAND("Computer Action", "Send PC Network Command", "Computer", requiresPcBridge = true),

    // 5. Sequences
    MULTI_STEP_SEQUENCE("Sequence", "Multi-Step Sequence", "PlaylistPlay"),

    // 6. Expanded A: Workspace & Profile Actions
    DESK_MODE_TOGGLE("Workspace & Profile", "Desk Mode Toggle", "Layers"),
    FOCUS_SESSION_CONTROLLER("Workspace & Profile", "Focus Session Controller", "Timer"),
    WORKSPACE_SWITCHER("Workspace & Profile", "Workspace Switcher (PC Bridge)", "Computer", requiresPcBridge = true),

    // 7. Expanded B: Music & Presentation Actions
    MUSIC_SESSION_CONTROLLER("Music & Presentation", "Music Session Controller", "MusicNote"),
    PRESENTATION_REMOTE("Music & Presentation", "Presentation Remote (PC Bridge)", "Slideshow", requiresPcBridge = true),
    GESTURE_MODIFIER("Music & Presentation", "Gesture Modifier", "Tune"),

    // 8. Expanded C: Productivity & Capture Actions
    SNIPPET_LAUNCHER("Productivity & Capture", "Snippet Launcher", "ContentCopy"),
    SCREENSHOT_ANNOTATION_LAUNCHER("Productivity & Capture", "Screenshot & Annotation Workflow", "Screenshot"),
    QUICK_REMINDER_TIMER("Productivity & Capture", "Quick Reminder & Timer", "Alarm"),
    QUICK_VOICE_MEMO("Productivity & Capture", "Quick Voice Memo", "Mic"),
    QUICK_TOOL_LAUNCHER("Productivity & Capture", "Quick Tool Launcher", "Build"),
    PLAYLIST_STATION_LAUNCHER("Productivity & Capture", "Context-Aware Playlist Launcher", "QueueMusic"),

    // 9. Expanded D: Smart-Home & Connected-Device Actions
    SMART_HOME_SCENE_TRIGGER("Smart Home & Connected", "Smart-Home Scene Trigger", "Home"),
    PAIRED_DEVICE_COMMAND("Smart Home & Connected", "Paired Device Command", "Devices", requiresPcBridge = true),
    EMERGENCY_LIGHT_SEQUENCE("Smart Home & Connected", "Emergency Light Sequence", "FlashlightOn");

    companion object {
        fun fromId(id: String): ActionType = entries.find { it.name == id } ?: NOTIFICATION
    }
}

/**
 * Conditional execution rules for macro sequence steps.
 */
enum class StepConditionType(val label: String) {
    NONE("Always Run"),
    PREVIOUS_STEP_SUCCESS("Only if Previous Step Succeeded"),
    PREVIOUS_STEP_FAILED("Only if Previous Step Failed"),
    PC_PAIRED("Only when Windows PC is Paired"),
    ACTIVE_PROFILE_IS("Only when Active Profile Matches"),
    TIME_WINDOW_HOURS("Only within Hour Window (e.g. 09-18)"),
    FOCUS_SESSION_ACTIVE("Only when Focus Session is Active");

    companion object {
        fun fromId(id: String): StepConditionType = entries.find { it.name == id } ?: NONE
    }
}

/**
 * Failure recovery behavior when a step in a macro fails.
 */
enum class StepFailurePolicy(val label: String) {
    CONTINUE("Continue to Next Step"),
    STOP_MACRO("Abort Macro Immediately"),
    RETRY_THEN_CONTINUE("Retry N Times, Then Continue"),
    RETRY_THEN_STOP("Retry N Times, Then Abort"),
    RUN_RECOVERY_STEP("Run Recovery Step on Failure");

    companion object {
        fun fromId(id: String): StepFailurePolicy = entries.find { it.name == id } ?: CONTINUE
    }
}

/**
 * Concurrency policy when the same macro is triggered while already running.
 */
enum class MacroConcurrencyPolicy(val label: String) {
    SKIP_IF_RUNNING("Ignore Duplicate Trigger While Running"),
    CANCEL_PREVIOUS("Cancel Previous Run & Restart"),
    ALLOW_CONCURRENT("Allow Parallel Runs");

    companion object {
        fun fromId(id: String): MacroConcurrencyPolicy = entries.find { it.name == id } ?: SKIP_IF_RUNNING
    }
}

/**
 * An individual action or sequence step with conditional execution, retry, and recovery support.
 */
data class ActionStep(
    val stepId: String,
    val type: ActionType,
    val title: String,
    val parameters: Map<String, String> = emptyMap(),
    val delayAfterMs: Long = 200L,
    val conditionType: StepConditionType = StepConditionType.NONE,
    val conditionValue: String = "",
    val onFailurePolicy: StepFailurePolicy = StepFailurePolicy.CONTINUE,
    val maxRetries: Int = 0,
    val retryDelayMs: Long = 250L,
    val recoveryActionType: ActionType? = null,
    val recoveryParameters: Map<String, String> = emptyMap(),
    val groupId: String? = null
)

/**
 * Full action macro definition with reversible toggle, concurrency control, and starter presets.
 */
data class ActionDefinition(
    val id: String,
    val name: String,
    val description: String,
    val type: ActionType,
    val isEnabled: Boolean = true,
    val parameters: Map<String, String> = emptyMap(),
    val steps: List<ActionStep> = emptyList(),
    val isReversibleToggle: Boolean = false,
    val reverseActionType: ActionType? = null,
    val reverseParameters: Map<String, String> = emptyMap(),
    val concurrencyPolicy: MacroConcurrencyPolicy = MacroConcurrencyPolicy.SKIP_IF_RUNNING,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        // Built-in starter actions
        val DEFAULT_MEDIA_PLAY_PAUSE = ActionDefinition(
            id = "action_default_play_pause",
            name = "Play / Pause Music",
            description = "Toggles playback on current Android media session",
            type = ActionType.MEDIA_PLAY_PAUSE
        )

        val DEFAULT_MEDIA_NEXT = ActionDefinition(
            id = "action_default_media_next",
            name = "Skip Track",
            description = "Advances to next audio track",
            type = ActionType.MEDIA_NEXT
        )

        val DEFAULT_FLASHLIGHT = ActionDefinition(
            id = "action_default_flashlight",
            name = "Toggle Flashlight",
            description = "Turns device camera torch on or off",
            type = ActionType.TOGGLE_FLASHLIGHT
        )

        val DEFAULT_DESK_SEQUENCE = ActionDefinition(
            id = "action_default_desk_sequence",
            name = "Desk Focus Macro",
            description = "Haptic pulse, sound beep, and post notification",
            type = ActionType.MULTI_STEP_SEQUENCE,
            steps = listOf(
                ActionStep(
                    stepId = "step_1",
                    type = ActionType.HAPTIC_PULSE,
                    title = "Tactile Haptic Ping",
                    parameters = mapOf("pattern" to "DOUBLE_CLICK"),
                    delayAfterMs = 150L
                ),
                ActionStep(
                    stepId = "step_2",
                    type = ActionType.AUDIO_CHIRP,
                    title = "Acoustic Confirmation",
                    parameters = mapOf("frequency" to "880"),
                    delayAfterMs = 150L
                ),
                ActionStep(
                    stepId = "step_3",
                    type = ActionType.NOTIFICATION,
                    title = "Show Banner",
                    parameters = mapOf(
                        "title" to "KnockDock Macro Activated",
                        "body" to "Desk double-knock macro executed successfully."
                    ),
                    delayAfterMs = 0L
                )
            )
        )

        val DEFAULT_FOCUS_SESSION = ActionDefinition(
            id = "action_default_focus_session",
            name = "25m Pomodoro Focus",
            description = "Starts or toggles a 25-minute deep work focus session with 5m break",
            type = ActionType.FOCUS_SESSION_CONTROLLER,
            parameters = mapOf(
                "mode" to "TOGGLE",
                "durationMinutes" to "25",
                "breakMinutes" to "5",
                "enableDnd" to "false"
            ),
            isReversibleToggle = true
        )

        val DEFAULT_QUICK_TIMER = ActionDefinition(
            id = "action_default_quick_timer",
            name = "5m Tea / Break Timer",
            description = "Starts a 5-minute countdown reminder notification with snooze/dismiss",
            type = ActionType.QUICK_REMINDER_TIMER,
            parameters = mapOf(
                "reminderText" to "Desk Break Timer Complete",
                "durationSeconds" to "300",
                "presetLabel" to "5m"
            )
        )

        fun defaultActions(): List<ActionDefinition> = listOf(
            DEFAULT_MEDIA_PLAY_PAUSE,
            DEFAULT_MEDIA_NEXT,
            DEFAULT_FLASHLIGHT,
            DEFAULT_DESK_SEQUENCE,
            DEFAULT_FOCUS_SESSION,
            DEFAULT_QUICK_TIMER
        )
    }
}

/**
 * Result of executing an individual step.
 */
data class StepExecutionReport(
    val stepId: String,
    val stepTitle: String,
    val success: Boolean,
    val message: String,
    val durationMs: Long,
    val attemptCount: Int = 1,
    val skippedByCondition: Boolean = false,
    val recoveryTriggered: Boolean = false
)

/**
 * Comprehensive execution report for the action studio log and history.
 */
data class ActionExecutionResult(
    val actionId: String,
    val actionName: String,
    val actionType: ActionType,
    val success: Boolean,
    val message: String,
    val executedAtEpochMs: Long = System.currentTimeMillis(),
    val stepReports: List<StepExecutionReport> = emptyList(),
    val isSimulated: Boolean = false,
    val wasCancelled: Boolean = false,
    val toggledToActiveState: Boolean? = null
)

/**
 * Result of validating an ActionDefinition or Macro before saving or executing.
 */
data class MacroValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Tracks known or assumed state for reversible/state-aware toggle actions.
 */
data class ActionToggleState(
    val actionId: String,
    val isToggledOn: Boolean,
    val isActualSystemState: Boolean, // true if verified from OS/hardware, false if ASSUMED_STATE
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)
