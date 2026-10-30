package com.example.knockdock.action

import android.content.Context
import android.content.pm.PackageManager
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.ActionType
import com.example.knockdock.model.MacroValidationResult
import com.example.knockdock.model.StepConditionType
import com.example.knockdock.model.StepFailurePolicy

data class InstalledAppInfo(
    val packageName: String,
    val appLabel: String
)

/**
 * Registry of supported action capabilities, parameters, installed applications,
 * approved PC bridge commands, and macro validation rules.
 */
class ActionRegistry(private val context: Context) {

    data class ActionMetadata(
        val type: ActionType,
        val title: String,
        val description: String,
        val parametersHelp: Map<String, String>,
        val isHardwareSupported: Boolean,
        val requiresPcBridge: Boolean = type.requiresPcBridge
    )

    companion object {
        val APPROVED_PAIRED_COMMANDS: Map<String, String> = linkedMapOf(
            "play_pause" to "Toggle desktop media play/pause",
            "next_track" to "Advance desktop media track",
            "prev_track" to "Previous desktop media track",
            "mute" to "Toggle Windows master audio mute",
            "lock_screen" to "Lock Windows workstation (Sensitive - requires approval)",
            "virtual_desktop_next" to "Switch to next Windows virtual desktop",
            "virtual_desktop_prev" to "Switch to previous Windows virtual desktop",
            "minimize_all" to "Minimize all open desktop windows (Win+D)",
            "restore_windows" to "Restore minimized desktop windows",
            "launch_workspace_set" to "Launch configured desktop workspace app bundle",
            "next_slide" to "Presentation Remote: Advance 1 slide",
            "prev_slide" to "Presentation Remote: Back 1 slide",
            "start_presentation" to "Presentation Remote: Start slideshow (F5)",
            "end_presentation" to "Presentation Remote: Exit slideshow (Esc)",
            "daw_play" to "DAW Transport: Start playback",
            "daw_stop" to "DAW Transport: Stop playback",
            "daw_record" to "DAW Transport: Arm/Trigger recording (Companion required)",
            "daw_metronome" to "DAW Transport: Toggle metronome click",
            "daw_next_marker" to "DAW Transport: Jump to next arrangement marker",
            "daw_prev_marker" to "DAW Transport: Jump to previous arrangement marker",
            "pc_capture_screenshot" to "Capture desktop screenshot on paired Windows PC",
            "toggle_desktop_mic" to "Mute/unmute desktop microphone input",
            "switch_audio_output" to "Cycle desktop speaker/headphone output",
            "ping" to "Verify authenticated companion connection"
        )

        val SENSITIVE_COMMANDS: Set<String> = setOf(
            "lock_screen",
            "daw_record",
            "pc_capture_screenshot",
            "launch_workspace_set"
        )
    }

    /**
     * Retrieve launchable applications installed on device.
     */
    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(pm).toString()
            InstalledAppInfo(packageName = pkg, appLabel = label)
        }.distinctBy { it.packageName }.sortedBy { it.appLabel }
    }

    /**
     * Get system action catalog metadata for all 30 supported ActionTypes.
     */
    fun getActionMetadataList(): List<ActionMetadata> {
        val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val hasMic = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

        return ActionType.entries.map { type ->
            when (type) {
                ActionType.LAUNCH_APP -> ActionMetadata(
                    type = type,
                    title = "Launch Installed App",
                    description = "Opens an application by package name using standard launcher intent.",
                    parametersHelp = mapOf("packageName" to "Target package name (e.g. com.spotify.music)"),
                    isHardwareSupported = true
                )
                ActionType.LAUNCH_URL -> ActionMetadata(
                    type = type,
                    title = "Open Web URL",
                    description = "Launches default web browser with a validated URL.",
                    parametersHelp = mapOf("url" to "Complete URL including https://"),
                    isHardwareSupported = true
                )
                ActionType.SYSTEM_SETTING -> ActionMetadata(
                    type = type,
                    title = "Open System Screen",
                    description = "Directly jumps to Android Settings screens (Sound, Bluetooth, Display, DND).",
                    parametersHelp = mapOf("screen" to "SOUND, BLUETOOTH, DISPLAY, WIFI, or DND"),
                    isHardwareSupported = true
                )
                ActionType.MEDIA_PLAY_PAUSE -> ActionMetadata(
                    type = type,
                    title = "Play / Pause Media",
                    description = "Dispatches standard media key events to active media player.",
                    parametersHelp = emptyMap(),
                    isHardwareSupported = true
                )
                ActionType.MEDIA_NEXT -> ActionMetadata(
                    type = type,
                    title = "Next Track",
                    description = "Skips to next audio track in active media session.",
                    parametersHelp = emptyMap(),
                    isHardwareSupported = true
                )
                ActionType.MEDIA_PREVIOUS -> ActionMetadata(
                    type = type,
                    title = "Previous Track",
                    description = "Skips to previous audio track in active media session.",
                    parametersHelp = emptyMap(),
                    isHardwareSupported = true
                )
                ActionType.MEDIA_VOLUME_MUTE -> ActionMetadata(
                    type = type,
                    title = "Mute / Unmute",
                    description = "Toggles audio volume mute status.",
                    parametersHelp = emptyMap(),
                    isHardwareSupported = true
                )
                ActionType.NOTIFICATION -> ActionMetadata(
                    type = type,
                    title = "Post Notification",
                    description = "Displays a customizable status notification on screen.",
                    parametersHelp = mapOf("title" to "Notification Title", "body" to "Message body text"),
                    isHardwareSupported = true
                )
                ActionType.TOGGLE_FLASHLIGHT -> ActionMetadata(
                    type = type,
                    title = "Toggle Flashlight",
                    description = "Toggles device camera LED torch on or off.",
                    parametersHelp = emptyMap(),
                    isHardwareSupported = hasFlash
                )
                ActionType.HAPTIC_PULSE -> ActionMetadata(
                    type = type,
                    title = "Haptic Tactile Pulse",
                    description = "Triggers a tactile vibration pulse on device.",
                    parametersHelp = mapOf("pattern" to "CLICK, DOUBLE_CLICK, or HEAVY"),
                    isHardwareSupported = true
                )
                ActionType.AUDIO_CHIRP -> ActionMetadata(
                    type = type,
                    title = "Audio Chirp",
                    description = "Emits a crisp acoustic tone to confirm gesture trigger.",
                    parametersHelp = mapOf("frequency" to "Tone frequency (e.g. 880 for A5)"),
                    isHardwareSupported = true
                )
                ActionType.TOGGLE_MONITORING -> ActionMetadata(
                    type = type,
                    title = "Toggle Monitoring State",
                    description = "Turns KnockDock desk listening on or off.",
                    parametersHelp = emptyMap(),
                    isHardwareSupported = true
                )
                ActionType.SWITCH_PROFILE -> ActionMetadata(
                    type = type,
                    title = "Switch Profile",
                    description = "Activates a selected KnockDock profile.",
                    parametersHelp = mapOf("profileId" to "Target profile ID"),
                    isHardwareSupported = true
                )
                ActionType.PC_COMMAND -> ActionMetadata(
                    type = type,
                    title = "Send PC Command",
                    description = "Sends an authenticated HTTP control command to paired desktop daemon on local Wi-Fi.",
                    parametersHelp = mapOf(
                        "command" to "play_pause, mute, lock_screen, next_track, prev_track",
                        "host" to "PC IP address",
                        "port" to "Port (e.g. 8989)",
                        "token" to "Shared authentication token"
                    ),
                    isHardwareSupported = true
                )
                ActionType.MULTI_STEP_SEQUENCE -> ActionMetadata(
                    type = type,
                    title = "Multi-Step Sequence",
                    description = "Runs multiple individual actions sequentially with conditions, retries, and recovery.",
                    parametersHelp = emptyMap(),
                    isHardwareSupported = true
                )
                ActionType.DESK_MODE_TOGGLE -> ActionMetadata(
                    type = type,
                    title = "Desk Mode Toggle",
                    description = "Switches between configured profiles (Work, Gaming, Music, Night, Focus), applies monitoring preferences, and optionally runs an activation macro without circular loops.",
                    parametersHelp = mapOf(
                        "primaryProfileId" to "Primary profile ID (e.g. Work / Desk Commander)",
                        "secondaryProfileId" to "Secondary profile ID (e.g. Focus / Nightstand)",
                        "preferredSamplingRate" to "Optional sampling rate override (NORMAL, UI, GAME, FASTEST)",
                        "onActivateActionId" to "Optional action ID to run upon switching"
                    ),
                    isHardwareSupported = true
                )
                ActionType.FOCUS_SESSION_CONTROLLER -> ActionMetadata(
                    type = type,
                    title = "Focus Session Controller",
                    description = "Starts, pauses, resumes, or ends a focus countdown session with optional break timer and Android Do Not Disturb integration.",
                    parametersHelp = mapOf(
                        "mode" to "TOGGLE, START, PAUSE, RESUME, or STOP",
                        "durationMinutes" to "Focus duration in minutes (default 25)",
                        "breakMinutes" to "Break duration in minutes (default 5)",
                        "enableDnd" to "true or false (requires DND policy permission)"
                    ),
                    isHardwareSupported = true
                )
                ActionType.WORKSPACE_SWITCHER -> ActionMetadata(
                    type = type,
                    title = "Workspace Switcher (PC Bridge)",
                    description = "Switches virtual desktops, minimizes/restores windows, or opens a workspace app set on a paired Windows PC via the companion service.",
                    parametersHelp = mapOf(
                        "workspaceCommand" to "virtual_desktop_next, virtual_desktop_prev, minimize_all, restore_windows, launch_workspace_set",
                        "workspaceSet" to "Optional workspace bundle name (e.g. Coding, Streaming)"
                    ),
                    isHardwareSupported = true,
                    requiresPcBridge = true
                )
                ActionType.MUSIC_SESSION_CONTROLLER -> ActionMetadata(
                    type = type,
                    title = "Music Session Controller",
                    description = "Controls Android media playback, opens a music app/playlist deep link, or sends authenticated DAW transport commands to a paired Windows companion.",
                    parametersHelp = mapOf(
                        "controlMode" to "PLAY_PAUSE, NEXT, PREVIOUS, OPEN_APP, DEEP_LINK, or DAW_COMMAND",
                        "packageName" to "Optional target music app package",
                        "deepLinkUri" to "Optional playlist/station URI",
                        "dawCommand" to "daw_play, daw_stop, daw_record, daw_metronome, daw_next_marker, daw_prev_marker"
                    ),
                    isHardwareSupported = true
                )
                ActionType.PRESENTATION_REMOTE -> ActionMetadata(
                    type = type,
                    title = "Presentation Remote (PC Bridge)",
                    description = "Advances or reverses slides, or starts/ends a presentation on an explicitly paired and authorized Windows PC.",
                    parametersHelp = mapOf(
                        "slideCommand" to "next_slide, prev_slide, start_presentation, end_presentation"
                    ),
                    isHardwareSupported = true,
                    requiresPcBridge = true
                )
                ActionType.GESTURE_MODIFIER -> ActionMetadata(
                    type = type,
                    title = "Gesture Modifier",
                    description = "Activates a temporary modifier layer for the next knock gesture with a configurable expiration timeout and cancellation support.",
                    parametersHelp = mapOf(
                        "modifierKey" to "Modifier identifier (e.g. MOD_A, MOD_SHIFT, MOD_MUSIC)",
                        "timeoutMs" to "Expiration timeout in ms (e.g. 4000)",
                        "cancelOnRepeat" to "true to cancel if triggered again while active"
                    ),
                    isHardwareSupported = true
                )
                ActionType.SNIPPET_LAUNCHER -> ActionMetadata(
                    type = type,
                    title = "Snippet Launcher",
                    description = "Copies a saved text snippet, URL, or note to the Android clipboard or opens a preview banner. Never silently pastes or logs sensitive content.",
                    parametersHelp = mapOf(
                        "snippetTitle" to "Label for the snippet",
                        "snippetContent" to "Text content to copy or preview",
                        "deliveryMode" to "COPY_CLIPBOARD or PREVIEW_BANNER"
                    ),
                    isHardwareSupported = true
                )
                ActionType.SCREENSHOT_ANNOTATION_LAUNCHER -> ActionMetadata(
                    type = type,
                    title = "Screenshot & Annotation Workflow",
                    description = "Opens Android's user-consented screenshot/share/annotation workflow or requests a desktop screenshot on an authorized Windows companion.",
                    parametersHelp = mapOf(
                        "workflowMode" to "ANDROID_CAPTURE_SHEET, ANDROID_SHARE_ANNOTATE, or PC_COMPANION_SCREENSHOT"
                    ),
                    isHardwareSupported = true
                )
                ActionType.QUICK_REMINDER_TIMER -> ActionMetadata(
                    type = type,
                    title = "Quick Reminder & Timer",
                    description = "Starts a configurable countdown timer or reminder notification with Dismiss, Snooze (+5m), and Complete actions.",
                    parametersHelp = mapOf(
                        "reminderText" to "Reminder title / note",
                        "durationSeconds" to "Countdown duration in seconds (e.g. 60, 300, 900)",
                        "presetLabel" to "Preset label (e.g. 1m, 5m, 15m)"
                    ),
                    isHardwareSupported = true
                )
                ActionType.QUICK_VOICE_MEMO -> ActionMetadata(
                    type = type,
                    title = "Quick Voice Memo",
                    description = "Starts or stops a short foreground voice memo with visible indicator, elapsed time, and configurable retention policy.",
                    parametersHelp = mapOf(
                        "maxDurationSeconds" to "Max recording duration in seconds (10..120)",
                        "saveLocation" to "INTERNAL_APP_STORAGE or CACHE_STORAGE",
                        "retentionPolicy" to "KEEP_ALL or AUTO_CLEAN_7D"
                    ),
                    isHardwareSupported = hasMic
                )
                ActionType.QUICK_TOOL_LAUNCHER -> ActionMetadata(
                    type = type,
                    title = "Quick Tool Launcher",
                    description = "Launches Calculator, Notes, QR/Camera Scanner, Clock/Timer, Flashlight UI, or a verified installed application.",
                    parametersHelp = mapOf(
                        "toolType" to "CALCULATOR, NOTES, QR_SCANNER, CLOCK_TIMER, FLASHLIGHT_UI, or CUSTOM_PACKAGE",
                        "packageName" to "Optional verified package name"
                    ),
                    isHardwareSupported = true
                )
                ActionType.PLAYLIST_STATION_LAUNCHER -> ActionMetadata(
                    type = type,
                    title = "Context-Aware Playlist Launcher",
                    description = "Opens a configured playlist, album, or radio station URI via Android app links, with graceful fallback to launching the music app.",
                    parametersHelp = mapOf(
                        "playlistTitle" to "Playlist or station title",
                        "playlistUri" to "https:// or spotify:/youtube: deep link URI",
                        "fallbackPackage" to "Optional fallback music app package"
                    ),
                    isHardwareSupported = true
                )
                ActionType.SMART_HOME_SCENE_TRIGGER -> ActionMetadata(
                    type = type,
                    title = "Smart-Home Scene Trigger",
                    description = "Triggers a named scene on Home Assistant, Node-RED, or a custom HTTP webhook with configurable method, headers, JSON payload, and timeouts.",
                    parametersHelp = mapOf(
                        "sceneName" to "Friendly scene name (e.g. Desk Cinema Lights)",
                        "serviceType" to "HOME_ASSISTANT, NODE_RED, or GENERIC_WEBHOOK",
                        "method" to "POST, GET, or PUT",
                        "webhookUrl" to "http:// or https:// endpoint URL",
                        "payloadJson" to "JSON body payload",
                        "timeoutMs" to "Connection/read timeout in ms (500..10000)"
                    ),
                    isHardwareSupported = true
                )
                ActionType.PAIRED_DEVICE_COMMAND -> ActionMetadata(
                    type = type,
                    title = "Paired Device Command",
                    description = "Sends an allowlisted, authenticated command to a paired Windows companion or approved local endpoint.",
                    parametersHelp = mapOf(
                        "command" to "Approved command key from allowlist",
                        "userApprovedSensitive" to "true if sensitive command is explicitly approved by user"
                    ),
                    isHardwareSupported = true,
                    requiresPcBridge = true
                )
                ActionType.EMERGENCY_LIGHT_SEQUENCE -> ActionMetadata(
                    type = type,
                    title = "Emergency Light Sequence",
                    description = "Runs a bounded flashlight signal sequence (SOS, Slow Beacon, or Steady) with automatic stop timeout and immediate stop control.",
                    parametersHelp = mapOf(
                        "pattern" to "SOS, SLOW_BEACON, STROBE_ALERT, or STEADY",
                        "durationSeconds" to "Auto-stop timeout in seconds (5..120)"
                    ),
                    isHardwareSupported = hasFlash
                )
            }
        }
    }

    /**
     * Validates an [ActionDefinition] before saving or executing.
     * Detects invalid URLs/hosts, unsupported commands, circular profile/macro references, and unsafe loops.
     */
    fun validateAction(
        action: ActionDefinition,
        allActions: List<ActionDefinition> = emptyList()
    ): MacroValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (action.name.isBlank()) {
            errors.add("Action name cannot be empty.")
        }

        when (action.type) {
            ActionType.LAUNCH_URL, ActionType.PLAYLIST_STATION_LAUNCHER -> {
                val rawUrl = action.parameters["url"] ?: action.parameters["playlistUri"] ?: ""
                if (rawUrl.isBlank()) {
                    errors.add("A valid URL or deep-link URI is required.")
                } else if (rawUrl.contains(" ") || rawUrl.contains("javascript:", ignoreCase = true)) {
                    errors.add("Malformed or unsafe URI: $rawUrl")
                }
            }

            ActionType.SMART_HOME_SCENE_TRIGGER -> {
                val url = action.parameters["webhookUrl"]?.trim() ?: ""
                if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    errors.add("Smart-Home Scene Trigger requires an http:// or https:// webhook URL.")
                }
                val payload = action.parameters["payloadJson"]?.trim() ?: ""
                if (payload.isNotEmpty() && (!payload.startsWith("{") || !payload.endsWith("}"))) {
                    errors.add("Webhook JSON payload must be a valid JSON object starting with '{' and ending with '}'.")
                }
            }

            ActionType.PC_COMMAND,
            ActionType.WORKSPACE_SWITCHER,
            ActionType.PRESENTATION_REMOTE,
            ActionType.PAIRED_DEVICE_COMMAND -> {
                val host = action.parameters["host"]?.trim() ?: ""
                if (host.contains(";") || host.contains("&") || host.contains("|") || host.contains("`")) {
                    errors.add("Invalid characters in PC host address.")
                }
                val cmd = action.parameters["command"]
                    ?: action.parameters["workspaceCommand"]
                    ?: action.parameters["slideCommand"]
                    ?: ""
                if (action.type == ActionType.PAIRED_DEVICE_COMMAND && cmd.isNotBlank() && cmd !in APPROVED_PAIRED_COMMANDS) {
                    errors.add("Command '$cmd' is not in the approved paired-device command allowlist.")
                }
                if (cmd in SENSITIVE_COMMANDS && action.parameters["userApprovedSensitive"] != "true") {
                    warnings.add("Command '$cmd' is marked sensitive; enable explicit user approval in action settings.")
                }
                warnings.add("Requires an active, authenticated Windows PC Companion on local Wi-Fi.")
            }

            ActionType.DESK_MODE_TOGGLE -> {
                val primary = action.parameters["primaryProfileId"] ?: ""
                val secondary = action.parameters["secondaryProfileId"] ?: ""
                if (primary.isBlank() && secondary.isBlank()) {
                    errors.add("At least one target profile ID must be configured for Desk Mode Toggle.")
                }
                val onActivateId = action.parameters["onActivateActionId"] ?: ""
                if (onActivateId == action.id) {
                    errors.add("Circular reference detected: Desk Mode Toggle cannot trigger itself on activation.")
                }
                val targetAction = allActions.find { it.id == onActivateId }
                if (targetAction != null && (targetAction.type == ActionType.DESK_MODE_TOGGLE || targetAction.type == ActionType.SWITCH_PROFILE)) {
                    errors.add("Circular profile transition detected: Activation action '${targetAction.name}' also switches profiles.")
                }
            }

            ActionType.EMERGENCY_LIGHT_SEQUENCE -> {
                val dur = action.parameters["durationSeconds"]?.toIntOrNull() ?: 15
                if (dur !in 3..120) {
                    errors.add("Emergency light duration must be between 3 and 120 seconds for safety.")
                }
                if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                    warnings.add("Camera LED flash hardware was not reported by this device.")
                }
            }

            ActionType.QUICK_VOICE_MEMO -> {
                val dur = action.parameters["maxDurationSeconds"]?.toIntOrNull() ?: 30
                if (dur !in 5..120) {
                    errors.add("Voice memo max duration must be between 5 and 120 seconds.")
                }
                warnings.add("Requires Android RECORD_AUDIO permission and visible foreground interaction.")
            }

            ActionType.MULTI_STEP_SEQUENCE -> {
                if (action.steps.isEmpty()) {
                    errors.add("Multi-step sequence must contain at least 1 step.")
                }
                if (action.steps.size > 25) {
                    errors.add("Macro sequence exceeds maximum safe step limit (25 steps).")
                }
                // Check for circular nested sequences or consecutive redundant profile switches
                var profileSwitchCount = 0
                action.steps.forEachIndexed { index, step ->
                    if (step.type == ActionType.MULTI_STEP_SEQUENCE) {
                        errors.add("Step ${index + 1} ('${step.title}'): Nested multi-step sequences are disallowed to prevent infinite recursion.")
                    }
                    if (step.type == ActionType.SWITCH_PROFILE || step.type == ActionType.DESK_MODE_TOGGLE) {
                        profileSwitchCount++
                    }
                    if (step.maxRetries < 0 || step.maxRetries > 5) {
                        errors.add("Step ${index + 1}: Retry count (${step.maxRetries}) must be between 0 and 5.")
                    }
                    if (step.delayAfterMs < 0L || step.delayAfterMs > 30_000L) {
                        errors.add("Step ${index + 1}: Delay after step must be between 0 and 30,000 ms.")
                    }
                    if (step.onFailurePolicy == StepFailurePolicy.RUN_RECOVERY_STEP && step.recoveryActionType == null) {
                        errors.add("Step ${index + 1}: Recovery policy selected without a recovery action type.")
                    }
                    if (step.conditionType == StepConditionType.TIME_WINDOW_HOURS && step.conditionValue.isNotBlank()) {
                        val parts = step.conditionValue.split("-")
                        val startH = parts.getOrNull(0)?.toIntOrNull()
                        val endH = parts.getOrNull(1)?.toIntOrNull()
                        if (startH == null || endH == null || startH !in 0..23 || endH !in 0..23) {
                            errors.add("Step ${index + 1}: Time window must be in HH-HH format (00-23), e.g., 09-18.")
                        }
                    }
                }
                if (profileSwitchCount > 1) {
                    warnings.add("Sequence contains multiple profile-switching steps; only the final profile will remain active.")
                }
            }

            else -> {}
        }

        return MacroValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
