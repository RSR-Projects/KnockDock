package com.example.knockdock.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.knockdock.action.ActionRegistry
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.ActionExecutionResult
import com.example.knockdock.model.ActionStep
import com.example.knockdock.model.ActionType
import com.example.knockdock.model.MacroConcurrencyPolicy
import com.example.knockdock.model.StepConditionType
import com.example.knockdock.model.StepFailurePolicy
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlassSegmentedRow
import com.example.knockdock.ui.theme.LiquidGlassSwitch
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LiquidStatusPill
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateGlassDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionStudioScreen(
    controller: MonitoringController
) {
    val context = LocalContext.current
    val actions by controller.repository.getAllActions().collectAsState(initial = emptyList())
    val gestures by controller.repository.getAllGestures().collectAsState(initial = emptyList())
    val activeProfile by controller.repository.getActiveProfile().collectAsState(initial = null)
    val profiles by controller.repository.getAllProfiles().collectAsState(initial = emptyList())
    val settings by controller.repository.settingsFlow.collectAsState()

    // Live runtime states from ActionExecutor
    val focusSession by controller.actionExecutor.focusSessionFlow.collectAsState()
    val activeModifier by controller.actionExecutor.activeModifierFlow.collectAsState()
    val activeTimers by controller.actionExecutor.activeTimersFlow.collectAsState()
    val voiceMemoState by controller.actionExecutor.voiceMemoFlow.collectAsState()
    val emergencyLightActive by controller.actionExecutor.emergencyLightActiveFlow.collectAsState()
    val toggleStates by controller.actionExecutor.toggleStatesFlow.collectAsState()
    val runningMacros by controller.actionExecutor.runningMacrosFlow.collectAsState()

    val scope = rememberCoroutineScope()
    val actionRegistry = remember { ActionRegistry(controller.appContext) }
    val installedApps by androidx.compose.runtime.produceState(initialValue = emptyList(), actionRegistry) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            actionRegistry.getInstalledLaunchableApps()
        }
    }

    var selectedCategoryFilter by remember { mutableStateOf("All") }

    // Audio permission launcher for Quick Voice Memo
    var voiceMemoPermissionMessage by remember { mutableStateOf<String?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceMemoPermissionMessage = "Microphone permission granted. Starting voice memo..."
            scope.launch {
                val memoAction = ActionDefinition(
                    id = "quick_voice_memo_live",
                    name = "Quick Voice Memo",
                    description = "Voice memo capture",
                    type = ActionType.QUICK_VOICE_MEMO,
                    parameters = mapOf("maxDurationSeconds" to "30", "saveLocation" to "INTERNAL_APP_STORAGE")
                )
                controller.actionExecutor.executeAction(memoAction)
            }
        } else {
            voiceMemoPermissionMessage = "Microphone permission denied. Voice Memo requires RECORD_AUDIO permission."
        }
    }

    // Editor state
    var isEditorOpen by remember { mutableStateOf(false) }
    var editingActionId by remember { mutableStateOf<String?>(null) }
    var actionName by remember { mutableStateOf("") }
    var actionDescription by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ActionType.NOTIFICATION) }
    var actionParam1 by remember { mutableStateOf("") }
    var actionParam2 by remember { mutableStateOf("") }
    var actionParam3 by remember { mutableStateOf("") }
    var actionParam4 by remember { mutableStateOf("") }
    var isReversibleToggle by remember { mutableStateOf(false) }
    var concurrencyPolicy by remember { mutableStateOf(MacroConcurrencyPolicy.SKIP_IF_RUNNING) }
    var assignedGestureId by remember { mutableStateOf<String?>(null) }
    var assignedRequiredModifier by remember { mutableStateOf<String?>(null) }
    val sequenceSteps = remember { mutableStateListOf<ActionStep>() }

    // New step builder controls
    var newStepType by remember { mutableStateOf(ActionType.TOGGLE_FLASHLIGHT) }
    var newStepDelayMs by remember { mutableStateOf("250") }
    var newStepConditionType by remember { mutableStateOf(StepConditionType.NONE) }
    var newStepConditionValue by remember { mutableStateOf("") }
    var newStepFailurePolicy by remember { mutableStateOf(StepFailurePolicy.STOP_MACRO) }
    var newStepMaxRetries by remember { mutableStateOf("0") }
    var isNewStepTypeExpanded by remember { mutableStateOf(false) }

    var isTypeDropdownExpanded by remember { mutableStateOf(false) }
    var isAppDropdownExpanded by remember { mutableStateOf(false) }
    var isGestureDropdownExpanded by remember { mutableStateOf(false) }
    var isSettingDropdownExpanded by remember { mutableStateOf(false) }

    var latestTestResult by remember { mutableStateOf<ActionExecutionResult?>(null) }
    var isTestingAction by remember { mutableStateOf(false) }
    var showPreviewSteps by remember { mutableStateOf(false) }

    fun applyDefaultParamsForType(type: ActionType) {
        when (type) {
            ActionType.LAUNCH_APP -> {
                actionParam1 = installedApps.firstOrNull()?.packageName ?: "com.android.settings"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.LAUNCH_URL -> {
                actionParam1 = "https://developer.android.com"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.SYSTEM_SETTING -> {
                actionParam1 = "SOUND"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.NOTIFICATION -> {
                actionParam1 = "KnockDock Alert"
                actionParam2 = "Macro triggered from desk knock"
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.HAPTIC_PULSE -> {
                actionParam1 = "CLICK"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.AUDIO_CHIRP -> {
                actionParam1 = "880"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.SWITCH_PROFILE -> {
                actionParam1 = profiles.firstOrNull()?.id ?: "profile_desk_commander"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.PC_COMMAND -> {
                actionParam1 = settings.pcHost
                actionParam2 = settings.pcPort.toString()
                actionParam3 = "play_pause"
                actionParam4 = ""
            }
            ActionType.DESK_MODE_TOGGLE -> {
                actionParam1 = "profile_desk_commander"
                actionParam2 = "profile_focus_guard"
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.FOCUS_SESSION_CONTROLLER -> {
                actionParam1 = "TOGGLE"
                actionParam2 = "25"
                actionParam3 = "5"
                actionParam4 = "false"
            }
            ActionType.WORKSPACE_SWITCHER -> {
                actionParam1 = "virtual_desktop_next"
                actionParam2 = settings.pcHost
                actionParam3 = settings.pcPort.toString()
                actionParam4 = "Default"
            }
            ActionType.MUSIC_SESSION_CONTROLLER -> {
                actionParam1 = "PLAY_PAUSE"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.PRESENTATION_REMOTE -> {
                actionParam1 = "next_slide"
                actionParam2 = settings.pcHost
                actionParam3 = settings.pcPort.toString()
                actionParam4 = ""
            }
            ActionType.GESTURE_MODIFIER -> {
                actionParam1 = "MOD_A"
                actionParam2 = "4000"
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.SNIPPET_LAUNCHER -> {
                actionParam1 = "Standup Update: Completed KnockDock expansion & verified all tests."
                actionParam2 = "Quick Snippet"
                actionParam3 = "COPY_CLIPBOARD"
                actionParam4 = ""
            }
            ActionType.SCREENSHOT_ANNOTATION_LAUNCHER -> {
                actionParam1 = "ANDROID_CAPTURE_SHEET"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.QUICK_REMINDER_TIMER -> {
                actionParam1 = "300"
                actionParam2 = "Desk Stretch Reminder"
                actionParam3 = "5m"
                actionParam4 = ""
            }
            ActionType.QUICK_VOICE_MEMO -> {
                actionParam1 = "30"
                actionParam2 = "INTERNAL_APP_STORAGE"
                actionParam3 = "KEEP_ALL"
                actionParam4 = ""
            }
            ActionType.QUICK_TOOL_LAUNCHER -> {
                actionParam1 = "CALCULATOR"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.PLAYLIST_STATION_LAUNCHER -> {
                actionParam1 = "https://open.spotify.com/playlist/37i9dQZF1DX8Uebhn9wzrS"
                actionParam2 = "Focus Beats Station"
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.SMART_HOME_SCENE_TRIGGER -> {
                actionParam1 = "https://192.168.1.50:8123/api/webhook/desk_scene"
                actionParam2 = "POST"
                actionParam3 = "Desk Focus Lighting"
                actionParam4 = "{\"scene\":\"desk_focus\",\"state\":\"toggle\"}"
            }
            ActionType.PAIRED_DEVICE_COMMAND -> {
                actionParam1 = "lock_workstation"
                actionParam2 = "true"
                actionParam3 = settings.pcHost
                actionParam4 = settings.pcPort.toString()
            }
            ActionType.EMERGENCY_LIGHT_SEQUENCE -> {
                actionParam1 = "SLOW_BEACON"
                actionParam2 = "15"
                actionParam3 = ""
                actionParam4 = ""
            }
            else -> {
                actionParam1 = ""
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
        }
    }

    fun openCreateNew() {
        editingActionId = null
        actionName = ""
        actionDescription = ""
        selectedType = ActionType.DESK_MODE_TOGGLE
        applyDefaultParamsForType(ActionType.DESK_MODE_TOGGLE)
        isReversibleToggle = false
        concurrencyPolicy = MacroConcurrencyPolicy.SKIP_IF_RUNNING
        assignedGestureId = gestures.firstOrNull()?.id
        assignedRequiredModifier = null
        sequenceSteps.clear()
        latestTestResult = null
        isEditorOpen = true
    }

    fun openEditAction(action: ActionDefinition) {
        editingActionId = action.id
        actionName = action.name
        actionDescription = action.description
        selectedType = action.type
        isReversibleToggle = action.isReversibleToggle
        concurrencyPolicy = action.concurrencyPolicy

        when (action.type) {
            ActionType.LAUNCH_APP -> {
                actionParam1 = action.parameters["packageName"] ?: ""
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.LAUNCH_URL -> {
                actionParam1 = action.parameters["url"] ?: ""
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.SYSTEM_SETTING -> {
                actionParam1 = action.parameters["screen"] ?: "SOUND"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.NOTIFICATION -> {
                actionParam1 = action.parameters["title"] ?: ""
                actionParam2 = action.parameters["body"] ?: ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.HAPTIC_PULSE -> {
                actionParam1 = action.parameters["pattern"] ?: "CLICK"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.AUDIO_CHIRP -> {
                actionParam1 = action.parameters["frequency"] ?: "880"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.SWITCH_PROFILE -> {
                actionParam1 = action.parameters["profileId"] ?: ""
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.PC_COMMAND -> {
                actionParam1 = action.parameters["host"] ?: settings.pcHost
                actionParam2 = action.parameters["port"] ?: settings.pcPort.toString()
                actionParam3 = action.parameters["command"] ?: "play_pause"
                actionParam4 = ""
            }
            ActionType.DESK_MODE_TOGGLE -> {
                actionParam1 = action.parameters["primaryProfileId"] ?: "profile_desk_commander"
                actionParam2 = action.parameters["secondaryProfileId"] ?: "profile_focus_guard"
                actionParam3 = action.parameters["onActivateActionId"] ?: ""
                actionParam4 = ""
            }
            ActionType.FOCUS_SESSION_CONTROLLER -> {
                actionParam1 = action.parameters["mode"] ?: "TOGGLE"
                actionParam2 = action.parameters["durationMinutes"] ?: "25"
                actionParam3 = action.parameters["breakMinutes"] ?: "5"
                actionParam4 = action.parameters["enableDnd"] ?: "false"
            }
            ActionType.WORKSPACE_SWITCHER -> {
                actionParam1 = action.parameters["workspaceCommand"] ?: "virtual_desktop_next"
                actionParam2 = action.parameters["host"] ?: settings.pcHost
                actionParam3 = action.parameters["port"] ?: settings.pcPort.toString()
                actionParam4 = action.parameters["workspaceSet"] ?: "Default"
            }
            ActionType.MUSIC_SESSION_CONTROLLER -> {
                actionParam1 = action.parameters["controlMode"] ?: "PLAY_PAUSE"
                actionParam2 = action.parameters["packageName"] ?: ""
                actionParam3 = action.parameters["deepLinkUri"] ?: ""
                actionParam4 = action.parameters["dawCommand"] ?: "daw_play"
            }
            ActionType.PRESENTATION_REMOTE -> {
                actionParam1 = action.parameters["slideCommand"] ?: "next_slide"
                actionParam2 = action.parameters["host"] ?: settings.pcHost
                actionParam3 = action.parameters["port"] ?: settings.pcPort.toString()
                actionParam4 = ""
            }
            ActionType.GESTURE_MODIFIER -> {
                actionParam1 = action.parameters["modifierKey"] ?: "MOD_A"
                actionParam2 = action.parameters["timeoutMs"] ?: "4000"
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.SNIPPET_LAUNCHER -> {
                actionParam1 = action.parameters["snippetContent"] ?: ""
                actionParam2 = action.parameters["snippetTitle"] ?: "Saved Snippet"
                actionParam3 = action.parameters["deliveryMode"] ?: "COPY_CLIPBOARD"
                actionParam4 = ""
            }
            ActionType.SCREENSHOT_ANNOTATION_LAUNCHER -> {
                actionParam1 = action.parameters["workflowMode"] ?: "ANDROID_CAPTURE_SHEET"
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.QUICK_REMINDER_TIMER -> {
                actionParam1 = action.parameters["durationSeconds"] ?: "300"
                actionParam2 = action.parameters["reminderText"] ?: "Desk Reminder"
                actionParam3 = action.parameters["presetLabel"] ?: "5m"
                actionParam4 = ""
            }
            ActionType.QUICK_VOICE_MEMO -> {
                actionParam1 = action.parameters["maxDurationSeconds"] ?: "30"
                actionParam2 = action.parameters["saveLocation"] ?: "INTERNAL_APP_STORAGE"
                actionParam3 = action.parameters["retentionPolicy"] ?: "KEEP_ALL"
                actionParam4 = ""
            }
            ActionType.QUICK_TOOL_LAUNCHER -> {
                actionParam1 = action.parameters["toolType"] ?: "CALCULATOR"
                actionParam2 = action.parameters["packageName"] ?: ""
                actionParam3 = ""
                actionParam4 = ""
            }
            ActionType.PLAYLIST_STATION_LAUNCHER -> {
                actionParam1 = action.parameters["playlistUri"] ?: "https://open.spotify.com"
                actionParam2 = action.parameters["playlistTitle"] ?: "Context Playlist"
                actionParam3 = action.parameters["fallbackPackage"] ?: ""
                actionParam4 = ""
            }
            ActionType.SMART_HOME_SCENE_TRIGGER -> {
                actionParam1 = action.parameters["webhookUrl"] ?: "https://192.168.1.50:8123/api/webhook/desk_scene"
                actionParam2 = action.parameters["method"] ?: "POST"
                actionParam3 = action.parameters["sceneName"] ?: "Desk Focus Lighting"
                actionParam4 = action.parameters["payloadJson"] ?: "{\"scene\":\"desk_focus\"}"
            }
            ActionType.PAIRED_DEVICE_COMMAND -> {
                actionParam1 = action.parameters["command"] ?: "lock_workstation"
                actionParam2 = action.parameters["userApprovedSensitive"] ?: "true"
                actionParam3 = action.parameters["host"] ?: settings.pcHost
                actionParam4 = action.parameters["port"] ?: settings.pcPort.toString()
            }
            ActionType.EMERGENCY_LIGHT_SEQUENCE -> {
                actionParam1 = action.parameters["pattern"] ?: "SLOW_BEACON"
                actionParam2 = action.parameters["durationSeconds"] ?: "15"
                actionParam3 = ""
                actionParam4 = ""
            }
            else -> {
                actionParam1 = action.parameters.values.firstOrNull() ?: ""
                actionParam2 = ""
                actionParam3 = ""
                actionParam4 = ""
            }
        }

        val existingMapping = activeProfile?.mappings?.find { it.actionId == action.id }
        assignedGestureId = existingMapping?.gestureId
        assignedRequiredModifier = existingMapping?.requiredModifier
        sequenceSteps.clear()
        sequenceSteps.addAll(action.steps)
        latestTestResult = null
        isEditorOpen = true
    }

    fun buildCurrentAction(): ActionDefinition {
        val id = editingActionId ?: "action_${UUID.randomUUID().toString().take(8)}"
        val params = mutableMapOf<String, String>()

        when (selectedType) {
            ActionType.LAUNCH_APP -> {
                params["packageName"] = actionParam1.ifEmpty { installedApps.firstOrNull()?.packageName ?: "com.android.settings" }
            }
            ActionType.LAUNCH_URL -> {
                val url = actionParam1.trim()
                params["url"] = if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            }
            ActionType.SYSTEM_SETTING -> {
                params["screen"] = actionParam1.ifEmpty { "SOUND" }
            }
            ActionType.NOTIFICATION -> {
                params["title"] = actionParam1.ifEmpty { "KnockDock Alert" }
                params["body"] = actionParam2.ifEmpty { "Gesture executed from desk knock" }
            }
            ActionType.HAPTIC_PULSE -> {
                params["pattern"] = actionParam1.ifEmpty { "CLICK" }
            }
            ActionType.AUDIO_CHIRP -> {
                params["frequency"] = actionParam1.ifEmpty { "880" }
            }
            ActionType.SWITCH_PROFILE -> {
                params["profileId"] = actionParam1.ifEmpty { profiles.firstOrNull()?.id ?: "profile_desk_commander" }
            }
            ActionType.PC_COMMAND -> {
                params["host"] = actionParam1.ifEmpty { settings.pcHost }
                params["port"] = actionParam2.ifEmpty { settings.pcPort.toString() }
                params["token"] = settings.pcAuthToken
                params["command"] = actionParam3.ifEmpty { "play_pause" }
            }
            ActionType.DESK_MODE_TOGGLE -> {
                params["primaryProfileId"] = actionParam1.ifEmpty { "profile_desk_commander" }
                params["secondaryProfileId"] = actionParam2.ifEmpty { "profile_focus_guard" }
                if (actionParam3.isNotBlank()) params["onActivateActionId"] = actionParam3.trim()
            }
            ActionType.FOCUS_SESSION_CONTROLLER -> {
                params["mode"] = actionParam1.ifEmpty { "TOGGLE" }
                params["durationMinutes"] = actionParam2.ifEmpty { "25" }
                params["breakMinutes"] = actionParam3.ifEmpty { "5" }
                params["enableDnd"] = actionParam4.ifEmpty { "false" }
            }
            ActionType.WORKSPACE_SWITCHER -> {
                params["workspaceCommand"] = actionParam1.ifEmpty { "virtual_desktop_next" }
                params["host"] = actionParam2.ifEmpty { settings.pcHost }
                params["port"] = actionParam3.ifEmpty { settings.pcPort.toString() }
                params["workspaceSet"] = actionParam4.ifEmpty { "Default" }
                params["token"] = settings.pcAuthToken
            }
            ActionType.MUSIC_SESSION_CONTROLLER -> {
                params["controlMode"] = actionParam1.ifEmpty { "PLAY_PAUSE" }
                if (actionParam2.isNotBlank()) params["packageName"] = actionParam2.trim()
                if (actionParam3.isNotBlank()) params["deepLinkUri"] = actionParam3.trim()
                if (actionParam4.isNotBlank()) params["dawCommand"] = actionParam4.trim()
            }
            ActionType.PRESENTATION_REMOTE -> {
                params["slideCommand"] = actionParam1.ifEmpty { "next_slide" }
                params["host"] = actionParam2.ifEmpty { settings.pcHost }
                params["port"] = actionParam3.ifEmpty { settings.pcPort.toString() }
                params["token"] = settings.pcAuthToken
            }
            ActionType.GESTURE_MODIFIER -> {
                params["modifierKey"] = actionParam1.ifEmpty { "MOD_A" }
                params["timeoutMs"] = actionParam2.ifEmpty { "4000" }
            }
            ActionType.SNIPPET_LAUNCHER -> {
                params["snippetContent"] = actionParam1
                params["snippetTitle"] = actionParam2.ifEmpty { "Saved Snippet" }
                params["deliveryMode"] = actionParam3.ifEmpty { "COPY_CLIPBOARD" }
            }
            ActionType.SCREENSHOT_ANNOTATION_LAUNCHER -> {
                params["workflowMode"] = actionParam1.ifEmpty { "ANDROID_CAPTURE_SHEET" }
            }
            ActionType.QUICK_REMINDER_TIMER -> {
                params["durationSeconds"] = actionParam1.ifEmpty { "300" }
                params["reminderText"] = actionParam2.ifEmpty { "Desk Reminder" }
                params["presetLabel"] = actionParam3.ifEmpty { "5m" }
            }
            ActionType.QUICK_VOICE_MEMO -> {
                params["maxDurationSeconds"] = actionParam1.ifEmpty { "30" }
                params["saveLocation"] = actionParam2.ifEmpty { "INTERNAL_APP_STORAGE" }
                params["retentionPolicy"] = actionParam3.ifEmpty { "KEEP_ALL" }
            }
            ActionType.QUICK_TOOL_LAUNCHER -> {
                params["toolType"] = actionParam1.ifEmpty { "CALCULATOR" }
                if (actionParam2.isNotBlank()) params["packageName"] = actionParam2.trim()
            }
            ActionType.PLAYLIST_STATION_LAUNCHER -> {
                params["playlistUri"] = actionParam1.ifEmpty { "https://open.spotify.com" }
                params["playlistTitle"] = actionParam2.ifEmpty { "Context Playlist" }
                if (actionParam3.isNotBlank()) params["fallbackPackage"] = actionParam3.trim()
            }
            ActionType.SMART_HOME_SCENE_TRIGGER -> {
                params["webhookUrl"] = actionParam1.trim()
                params["method"] = actionParam2.ifEmpty { "POST" }
                params["sceneName"] = actionParam3.ifEmpty { "Desk Scene" }
                params["payloadJson"] = actionParam4.ifEmpty { "{\"scene\":\"desk_focus\"}" }
            }
            ActionType.PAIRED_DEVICE_COMMAND -> {
                params["command"] = actionParam1.ifEmpty { "lock_workstation" }
                params["userApprovedSensitive"] = actionParam2.ifEmpty { "true" }
                params["host"] = actionParam3.ifEmpty { settings.pcHost }
                params["port"] = actionParam4.ifEmpty { settings.pcPort.toString() }
                params["token"] = settings.pcAuthToken
            }
            ActionType.EMERGENCY_LIGHT_SEQUENCE -> {
                params["pattern"] = actionParam1.ifEmpty { "SLOW_BEACON" }
                params["durationSeconds"] = actionParam2.ifEmpty { "15" }
            }
            else -> {}
        }

        val name = actionName.trim().ifEmpty {
            when (selectedType) {
                ActionType.LAUNCH_APP -> "Launch ${installedApps.find { it.packageName == actionParam1 }?.appLabel ?: "App"}"
                ActionType.LAUNCH_URL -> "Open Web URL"
                ActionType.SYSTEM_SETTING -> "Open $actionParam1 Settings"
                else -> "${selectedType.displayName} Macro"
            }
        }

        return ActionDefinition(
            id = id,
            name = name,
            description = actionDescription.trim().ifEmpty { "${selectedType.displayName} action" },
            type = selectedType,
            parameters = params,
            steps = if (selectedType == ActionType.MULTI_STEP_SEQUENCE) sequenceSteps.toList() else emptyList(),
            isReversibleToggle = isReversibleToggle,
            concurrencyPolicy = concurrencyPolicy,
            createdAtEpochMs = System.currentTimeMillis()
        )
    }

    val draftAction = remember(
        editingActionId, actionName, actionDescription, selectedType,
        actionParam1, actionParam2, actionParam3, actionParam4,
        isReversibleToggle, concurrencyPolicy, sequenceSteps.size
    ) {
        buildCurrentAction()
    }
    val validationResult = remember(draftAction, actions) {
        actionRegistry.validateAction(draftAction, actions)
    }
    val previewSummary = remember(draftAction, showPreviewSteps) {
        if (showPreviewSteps) controller.actionExecutor.previewMacroExecution(draftAction) else emptyList()
    }

    val categories = listOf(
        "All",
        "Workspace & Profile",
        "Music & Presentation",
        "Productivity & Capture",
        "Smart Home & Connected",
        "Sequence",
        "Media Control",
        "Utility",
        "Local App",
        "Computer Action"
    )
    val filteredActions = remember(actions, selectedCategoryFilter) {
        if (selectedCategoryFilter == "All") actions
        else actions.filter { it.type.categoryName.equals(selectedCategoryFilter, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Action Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Advanced Actions, Conditional Macros & Live Controllers",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LiquidGlowButton(
                    text = if (isEditorOpen) "Close" else "+ New Action",
                    onClick = {
                        if (isEditorOpen) {
                            isEditorOpen = false
                        } else {
                            openCreateNew()
                        }
                    },
                    accentColor = if (isEditorOpen) RoseError else CyanGlow,
                    modifier = Modifier.testTag("create_new_action_button")
                )
            }
        }

        // LIVE RUNTIME CONTROLLERS BANNER
        if (focusSession.isActive || activeModifier != null || activeTimers.isNotEmpty() ||
            voiceMemoState.isRecording || voiceMemoState.savedMemos.isNotEmpty() ||
            emergencyLightActive || runningMacros.isNotEmpty()
        ) {
            item {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_runtime_controllers_card"),
                    accentColor = MintSuccess,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Live Active Sessions & Controllers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MintSuccess
                        )

                        // Emergency Light Sequence Immediate Stop
                        if (emergencyLightActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(RoseError.copy(alpha = 0.2f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Emergency Alert Beacon Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                LiquidGlowButton(
                                    text = "STOP NOW",
                                    accentColor = RoseError,
                                    onClick = { controller.actionExecutor.stopEmergencyLight() },
                                    modifier = Modifier.testTag("stop_emergency_light_button")
                                )
                            }
                        }

                        // Running Macros Cancellation
                        if (runningMacros.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(IndigoPulse.copy(alpha = 0.18f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${runningMacros.size} Macro(s) Running",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                LiquidGlowButton(
                                    text = "Cancel All",
                                    accentColor = RoseError,
                                    onClick = { controller.actionExecutor.cancelAllRunningMacros() },
                                    modifier = Modifier.testTag("cancel_running_macros_button")
                                )
                            }
                        }

                        // Focus Session Controller Live Status
                        if (focusSession.isActive) {
                            val mins = focusSession.remainingSeconds / 60
                            val secs = focusSession.remainingSeconds % 60
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(CyanGlow.copy(alpha = 0.14f))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Focus Session (${focusSession.statusText})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    LiquidStatusPill(
                                        text = "%02d:%02d".format(mins, secs),
                                        statusColor = if (focusSession.isPaused) AmberAlert else MintSuccess
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LiquidGlowButton(
                                        text = if (focusSession.isPaused) "Resume" else "Pause",
                                        accentColor = IndigoPulse,
                                        onClick = {
                                            scope.launch {
                                                controller.actionExecutor.executeAction(
                                                    ActionDefinition(
                                                        id = "focus_ctrl_pause",
                                                        name = "Focus Control",
                                                        description = "",
                                                        type = ActionType.FOCUS_SESSION_CONTROLLER,
                                                        parameters = mapOf("mode" to if (focusSession.isPaused) "RESUME" else "PAUSE")
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LiquidGlowButton(
                                        text = "End Session",
                                        accentColor = RoseError,
                                        onClick = {
                                            scope.launch {
                                                controller.actionExecutor.executeAction(
                                                    ActionDefinition(
                                                        id = "focus_ctrl_end",
                                                        name = "End Focus",
                                                        description = "",
                                                        type = ActionType.FOCUS_SESSION_CONTROLLER,
                                                        parameters = mapOf("mode" to "STOP")
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Active Gesture Modifier Window
                        if (activeModifier != null) {
                            val mod = activeModifier!!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(IndigoPulse.copy(alpha = 0.2f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Modifier Armed: ${mod.modifierKey}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Next knock gesture will use ${mod.modifierKey} (${mod.remainingMs / 1000f}s left)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                LiquidGlowButton(
                                    text = "Clear",
                                    accentColor = RoseError,
                                    onClick = { controller.actionExecutor.clearGestureModifier() }
                                )
                            }
                        }

                        // Active Quick Reminder Timers
                        activeTimers.forEach { timer ->
                            val mins = timer.remainingSeconds / 60
                            val secs = timer.remainingSeconds % 60
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = timer.reminderText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    LiquidStatusPill(
                                        text = if (timer.isCompleted) "COMPLETED" else "%02d:%02d".format(mins, secs),
                                        statusColor = if (timer.isCompleted) AmberAlert else CyanGlow
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LiquidGlowButton(
                                        text = "+5m Snooze",
                                        accentColor = IndigoPulse,
                                        onClick = { controller.actionExecutor.snoozeTimer(timer.timerId, 300) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LiquidGlowButton(
                                        text = "Dismiss",
                                        accentColor = RoseError,
                                        onClick = { controller.actionExecutor.dismissTimer(timer.timerId) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Voice Memo Live Recorder Status
                        if (voiceMemoState.isRecording || voiceMemoState.savedMemos.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(10.dp)
                            ) {
                                if (voiceMemoState.isRecording) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Recording Voice Memo (${voiceMemoState.elapsedSeconds}s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = RoseError
                                        )
                                        LiquidStatusPill(text = "REC", statusColor = RoseError)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LiquidGlowButton(
                                            text = "Stop & Save",
                                            accentColor = MintSuccess,
                                            onClick = { controller.actionExecutor.stopAndSaveVoiceMemo() },
                                            modifier = Modifier.weight(1f)
                                        )
                                        LiquidGlowButton(
                                            text = "Discard",
                                            accentColor = RoseError,
                                            onClick = { controller.actionExecutor.discardVoiceMemo() },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (voiceMemoState.savedMemos.isNotEmpty()) {
                                    Text(
                                        text = "Saved Voice Memos (${voiceMemoState.savedMemos.size}): ${
                                            voiceMemoState.savedMemos.take(2).joinToString { "${it.fileName} (${it.durationSeconds}s)" }
                                        }",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Voice Memo Direct Bar with Runtime Permission Handling
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_voice_memo_bar"),
                accentColor = IndigoPulse,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Quick Voice Memo Capture",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (voiceMemoState.isRecording) {
                                    "Recording in progress (${voiceMemoState.elapsedSeconds}s elapsed)..."
                                } else {
                                    "Record an instant desk audio note into app storage"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        LiquidGlowButton(
                            text = if (voiceMemoState.isRecording) "Stop Memo" else "Record Memo",
                            accentColor = if (voiceMemoState.isRecording) RoseError else IndigoPulse,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (voiceMemoState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                if (voiceMemoState.isRecording) {
                                    controller.actionExecutor.stopAndSaveVoiceMemo()
                                } else {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        scope.launch {
                                            controller.actionExecutor.executeAction(
                                                ActionDefinition(
                                                    id = "quick_voice_memo_btn",
                                                    name = "Quick Voice Memo",
                                                    description = "",
                                                    type = ActionType.QUICK_VOICE_MEMO,
                                                    parameters = mapOf("maxDurationSeconds" to "30", "saveLocation" to "INTERNAL_APP_STORAGE")
                                                )
                                            )
                                        }
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("record_voice_memo_button")
                        )
                    }
                    if (voiceMemoPermissionMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = voiceMemoPermissionMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanGlow
                        )
                    }
                }
            }
        }

        // Action Editor / Builder Card
        if (isEditorOpen) {
            item {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_builder_card"),
                    accentColor = CyanGlow,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editingActionId != null) "Edit Action" else "Create New Action",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            LiquidStatusPill(
                                text = if (editingActionId != null) "Editing" else "Draft",
                                statusColor = if (editingActionId != null) AmberAlert else CyanGlow
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Name Input
                        OutlinedTextField(
                            value = actionName,
                            onValueChange = { actionName = it },
                            label = { Text("Action Name (e.g. Desk Mode Cycle or Focus 25m)") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("action_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description Input
                        OutlinedTextField(
                            value = actionDescription,
                            onValueChange = { actionDescription = it },
                            label = { Text("Description") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category & Action Type Dropdown
                        Text("Action Category & Type (30 Actions Available)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = isTypeDropdownExpanded,
                            onExpandedChange = { isTypeDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = "${selectedType.categoryName}: ${selectedType.displayName}",
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("action_type_dropdown"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanGlow,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = isTypeDropdownExpanded,
                                onDismissRequest = { isTypeDropdownExpanded = false },
                                containerColor = SlateGlassDark
                            ) {
                                ActionType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("${type.categoryName} • ${type.displayName}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                if (type.requiresPcBridge) {
                                                    Text("Requires Paired Windows PC Bridge", style = MaterialTheme.typography.labelSmall, color = AmberAlert)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedType = type
                                            applyDefaultParamsForType(type)
                                            isTypeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedType.requiresPcBridge) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LiquidStatusPill(
                                text = "PC-Bridge-Dependent Action (Uses HTTP companion service)",
                                statusColor = AmberAlert
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic Parameter Section based on selectedType
                        when (selectedType) {
                            ActionType.LAUNCH_APP -> {
                                Text("Select Target Application", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                ExposedDropdownMenuBox(
                                    expanded = isAppDropdownExpanded,
                                    onExpandedChange = { isAppDropdownExpanded = it }
                                ) {
                                    val currentAppName = installedApps.find { it.packageName == actionParam1 }?.appLabel
                                        ?: actionParam1.ifEmpty { "Choose an installed app..." }
                                    OutlinedTextField(
                                        value = currentAppName,
                                        onValueChange = {},
                                        readOnly = true,
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAppDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanGlow,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    ExposedDropdownMenu(
                                        expanded = isAppDropdownExpanded,
                                        onDismissRequest = { isAppDropdownExpanded = false },
                                        containerColor = SlateGlassDark
                                    ) {
                                        installedApps.take(30).forEach { app ->
                                            DropdownMenuItem(
                                                text = { Text(app.appLabel, color = TextPrimary) },
                                                onClick = {
                                                    actionParam1 = app.packageName
                                                    if (actionName.isBlank()) {
                                                        actionName = "Launch ${app.appLabel}"
                                                    }
                                                    isAppDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            ActionType.LAUNCH_URL -> {
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Web URL (e.g. https://github.com)"
                                )
                            }

                            ActionType.SYSTEM_SETTING -> {
                                Text("Android Settings Screen", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                ExposedDropdownMenuBox(
                                    expanded = isSettingDropdownExpanded,
                                    onExpandedChange = { isSettingDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = actionParam1.ifEmpty { "SOUND" },
                                        onValueChange = {},
                                        readOnly = true,
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSettingDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanGlow,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    ExposedDropdownMenu(
                                        expanded = isSettingDropdownExpanded,
                                        onDismissRequest = { isSettingDropdownExpanded = false },
                                        containerColor = SlateGlassDark
                                    ) {
                                        listOf("SOUND", "BLUETOOTH", "WIFI", "DISPLAY", "DND").forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text("$s Settings", color = TextPrimary) },
                                                onClick = {
                                                    actionParam1 = s
                                                    isSettingDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            ActionType.NOTIFICATION -> {
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Notification Title"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam2,
                                    onValueChange = { actionParam2 = it },
                                    label = "Notification Message Body"
                                )
                            }

                            ActionType.HAPTIC_PULSE -> {
                                Text("Haptic Vibration Pattern", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                val options = listOf("CLICK" to "Click", "DOUBLE_CLICK" to "Double", "HEAVY" to "Heavy", "TICK" to "Tick")
                                val currentCode = actionParam1.ifEmpty { "CLICK" }
                                LiquidGlassSegmentedRow(
                                    items = options,
                                    selectedItem = options.find { it.first == currentCode } ?: options.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )
                            }

                            ActionType.AUDIO_CHIRP -> {
                                val options = listOf("880" to "High Beep", "440" to "Low Tone", "1200" to "Bright Chirp")
                                val currentCode = actionParam1.ifEmpty { "880" }
                                LiquidGlassSegmentedRow(
                                    items = options,
                                    selectedItem = options.find { it.first == currentCode } ?: options.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )
                            }

                            ActionType.SWITCH_PROFILE -> {
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Target Profile ID (e.g. ${profiles.firstOrNull()?.id ?: "profile_desk_commander"})"
                                )
                            }

                            ActionType.DESK_MODE_TOGGLE -> {
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Primary Profile ID (e.g. profile_desk_commander)"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam2,
                                    onValueChange = { actionParam2 = it },
                                    label = "Secondary Profile ID (e.g. profile_focus_guard)"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam3,
                                    onValueChange = { actionParam3 = it },
                                    label = "Optional On-Activate Action ID (Protected against loops)"
                                )
                            }

                            ActionType.FOCUS_SESSION_CONTROLLER -> {
                                Text("Focus Session Command", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                val commands = listOf("TOGGLE" to "Toggle", "START" to "Start", "PAUSE" to "Pause", "RESUME" to "Resume", "STOP" to "End")
                                LiquidGlassSegmentedRow(
                                    items = commands,
                                    selectedItem = commands.find { it.first == actionParam1 } ?: commands.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StudioTextField(
                                        value = actionParam2,
                                        onValueChange = { actionParam2 = it },
                                        label = "Focus Minutes (1-240)",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StudioTextField(
                                        value = actionParam3,
                                        onValueChange = { actionParam3 = it },
                                        label = "Break Minutes (0-60)",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Enable Do Not Disturb during session (restores on end)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    LiquidGlassSwitch(
                                        checked = actionParam4.toBooleanStrictOrNull() ?: false,
                                        onCheckedChange = { actionParam4 = it.toString() },
                                        accentColor = CyanGlow
                                    )
                                }
                            }

                            ActionType.WORKSPACE_SWITCHER -> {
                                Text("Windows PC Workspace Command (Requires PC Bridge)", style = MaterialTheme.typography.labelSmall, color = AmberAlert)
                                Spacer(modifier = Modifier.height(6.dp))
                                val cmds = listOf(
                                    "virtual_desktop_next" to "Next Desktop",
                                    "virtual_desktop_prev" to "Prev Desktop",
                                    "minimize_all_windows" to "Minimize All",
                                    "restore_workspace_windows" to "Restore"
                                )
                                LiquidGlassSegmentedRow(
                                    items = cmds,
                                    selectedItem = cmds.find { it.first == actionParam1 } ?: cmds.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = IndigoPulse
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StudioTextField(
                                        value = actionParam2,
                                        onValueChange = { actionParam2 = it },
                                        label = "PC Host IP",
                                        modifier = Modifier.weight(2f)
                                    )
                                    StudioTextField(
                                        value = actionParam3,
                                        onValueChange = { actionParam3 = it },
                                        label = "Port",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            ActionType.MUSIC_SESSION_CONTROLLER -> {
                                Text("Media Playback Command", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                val cmds = listOf(
                                    "PLAY_PAUSE" to "Play/Pause",
                                    "NEXT" to "Next",
                                    "PREVIOUS" to "Prev",
                                    "OPEN_APP" to "Open App",
                                    "DAW_COMMAND" to "DAW"
                                )
                                LiquidGlassSegmentedRow(
                                    items = cmds,
                                    selectedItem = cmds.find { it.first == actionParam1 } ?: cmds.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam2,
                                    onValueChange = { actionParam2 = it },
                                    label = "Optional Target Player Package (e.g. com.spotify.music)"
                                )
                            }

                            ActionType.PRESENTATION_REMOTE -> {
                                Text("Slide / Presentation Command (PC Bridge)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                val cmds = listOf(
                                    "next_slide" to "Next",
                                    "previous_slide" to "Prev",
                                    "start_presentation" to "Start",
                                    "black_screen" to "Blackout"
                                )
                                LiquidGlassSegmentedRow(
                                    items = cmds,
                                    selectedItem = cmds.find { it.first == actionParam1 } ?: cmds.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = IndigoPulse
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StudioTextField(
                                        value = actionParam2,
                                        onValueChange = { actionParam2 = it },
                                        label = "PC Host IP",
                                        modifier = Modifier.weight(2f)
                                    )
                                    StudioTextField(
                                        value = actionParam3,
                                        onValueChange = { actionParam3 = it },
                                        label = "Port",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            ActionType.GESTURE_MODIFIER -> {
                                Text("Modifier Key to Arm for Follow-Up Gesture", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                val mods = listOf("MOD_A" to "MOD_A", "MOD_SHIFT" to "MOD_SHIFT", "MOD_MUSIC" to "MOD_MUSIC", "MOD_WORK" to "MOD_WORK")
                                LiquidGlassSegmentedRow(
                                    items = mods,
                                    selectedItem = mods.find { it.first == actionParam1 } ?: mods.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = IndigoPulse
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam2,
                                    onValueChange = { actionParam2 = it },
                                    label = "Modifier Timeout Window (ms, 1000 - 15000)"
                                )
                            }

                            ActionType.SNIPPET_LAUNCHER -> {
                                StudioTextField(
                                    value = actionParam2,
                                    onValueChange = { actionParam2 = it },
                                    label = "Snippet Title"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Text / Template Content to Copy"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val modes = listOf("COPY_CLIPBOARD" to "Copy to Clipboard", "PREVIEW_BANNER" to "Preview Banner")
                                LiquidGlassSegmentedRow(
                                    items = modes,
                                    selectedItem = modes.find { it.first == actionParam3 } ?: modes.first(),
                                    onSelectItem = { actionParam3 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )
                            }

                            ActionType.SCREENSHOT_ANNOTATION_LAUNCHER -> {
                                val modes = listOf(
                                    "ANDROID_CAPTURE_SHEET" to "Capture Prompt",
                                    "ANDROID_SHARE_ANNOTATE" to "Media Picker",
                                    "PC_COMPANION_SCREENSHOT" to "PC Capture"
                                )
                                LiquidGlassSegmentedRow(
                                    items = modes,
                                    selectedItem = modes.find { it.first == actionParam1 } ?: modes.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )
                            }

                            ActionType.QUICK_REMINDER_TIMER -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StudioTextField(
                                        value = actionParam1,
                                        onValueChange = { actionParam1 = it },
                                        label = "Duration Seconds (e.g. 300)",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StudioTextField(
                                        value = actionParam2,
                                        onValueChange = { actionParam2 = it },
                                        label = "Reminder Label",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            ActionType.QUICK_VOICE_MEMO -> {
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Max Duration Seconds (5 - 120)"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val locs = listOf("INTERNAL_APP_STORAGE" to "App Storage", "CACHE_STORAGE" to "Temp Cache")
                                LiquidGlassSegmentedRow(
                                    items = locs,
                                    selectedItem = locs.find { it.first == actionParam2 } ?: locs.first(),
                                    onSelectItem = { actionParam2 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = IndigoPulse
                                )
                            }

                            ActionType.QUICK_TOOL_LAUNCHER -> {
                                val tools = listOf(
                                    "CALCULATOR" to "Calc",
                                    "NOTES" to "Notes",
                                    "QR_SCANNER" to "Camera",
                                    "CLOCK_TIMER" to "Timer",
                                    "FLASHLIGHT_UI" to "Torch"
                                )
                                LiquidGlassSegmentedRow(
                                    items = tools,
                                    selectedItem = tools.find { it.first == actionParam1 } ?: tools.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )
                            }

                            ActionType.PLAYLIST_STATION_LAUNCHER -> {
                                StudioTextField(
                                    value = actionParam2,
                                    onValueChange = { actionParam2 = it },
                                    label = "Playlist / Station Title"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Deep Link or Stream URI (https://, spotify:, vlc://)"
                                )
                            }

                            ActionType.SMART_HOME_SCENE_TRIGGER -> {
                                StudioTextField(
                                    value = actionParam3,
                                    onValueChange = { actionParam3 = it },
                                    label = "Scene Name (e.g. Desk Focus Lighting)"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "Webhook / Local Endpoint URL (http:// or https://)"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam4,
                                    onValueChange = { actionParam4 = it },
                                    label = "JSON Payload"
                                )
                            }

                            ActionType.PAIRED_DEVICE_COMMAND -> {
                                Text("Approved Paired-Device Command (No arbitrary shell execution)", style = MaterialTheme.typography.labelSmall, color = AmberAlert)
                                Spacer(modifier = Modifier.height(6.dp))
                                val approvedList = listOf(
                                    "lock_workstation" to "Lock PC",
                                    "mute_pc_audio" to "Mute PC",
                                    "toggle_pc_mic" to "Toggle Mic",
                                    "obs_toggle_stream" to "OBS Stream"
                                )
                                LiquidGlassSegmentedRow(
                                    items = approvedList,
                                    selectedItem = approvedList.find { it.first == actionParam1 } ?: approvedList.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = IndigoPulse
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Approve execution of sensitive command (e.g. Lock PC)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    LiquidGlassSwitch(
                                        checked = actionParam2.toBooleanStrictOrNull() ?: true,
                                        onCheckedChange = { actionParam2 = it.toString() },
                                        accentColor = AmberAlert
                                    )
                                }
                            }

                            ActionType.EMERGENCY_LIGHT_SEQUENCE -> {
                                val modes = listOf(
                                    "SLOW_BEACON" to "Slow Beacon",
                                    "SOS" to "SOS Morse",
                                    "STROBE_ALERT" to "Strobe Alert"
                                )
                                LiquidGlassSegmentedRow(
                                    items = modes,
                                    selectedItem = modes.find { it.first == actionParam1 } ?: modes.first(),
                                    onSelectItem = { actionParam1 = it.first },
                                    labelProvider = { it.second },
                                    accentColor = RoseError
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StudioTextField(
                                    value = actionParam2,
                                    onValueChange = { actionParam2 = it },
                                    label = "Auto-Stop Duration Seconds (3 - 120)"
                                )
                            }

                            ActionType.PC_COMMAND -> {
                                StudioTextField(
                                    value = actionParam1,
                                    onValueChange = { actionParam1 = it },
                                    label = "PC IP Address (e.g. 192.168.1.100)"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StudioTextField(
                                        value = actionParam2.ifEmpty { "8989" },
                                        onValueChange = { actionParam2 = it },
                                        label = "Port",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StudioTextField(
                                        value = actionParam3.ifEmpty { "play_pause" },
                                        onValueChange = { actionParam3 = it },
                                        label = "Command (e.g. play_pause)",
                                        modifier = Modifier.weight(2f)
                                    )
                                }
                            }

                            ActionType.MULTI_STEP_SEQUENCE -> {
                                Text(
                                    text = "Macro Sequence Steps (${sequenceSteps.size}) • Conditions & Recovery",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                sequenceSteps.forEachIndexed { idx, step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedRectangle(12.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedRectangle(12.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "${idx + 1}. ${step.title}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${step.type.displayName} • delay: ${step.delayAfterMs}ms • If: ${step.conditionType.label} • OnFail: ${step.onFailurePolicy.label} (retries=${step.maxRetries})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        LiquidGlassIconButton(
                                            onClick = { sequenceSteps.removeAt(idx) },
                                            size = 32.dp,
                                            accentColor = RoseError
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Step",
                                                tint = RoseError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Advanced Step Builder
                                Text("Add Conditional / Resilient Step", style = MaterialTheme.typography.labelSmall, color = CyanGlow)
                                Spacer(modifier = Modifier.height(4.dp))
                                ExposedDropdownMenuBox(
                                    expanded = isNewStepTypeExpanded,
                                    onExpandedChange = { isNewStepTypeExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = newStepType.displayName,
                                        onValueChange = {},
                                        readOnly = true,
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isNewStepTypeExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanGlow,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    ExposedDropdownMenu(
                                        expanded = isNewStepTypeExpanded,
                                        onDismissRequest = { isNewStepTypeExpanded = false },
                                        containerColor = SlateGlassDark
                                    ) {
                                        ActionType.entries.filter { it != ActionType.MULTI_STEP_SEQUENCE }.forEach { t ->
                                            DropdownMenuItem(
                                                text = { Text("${t.categoryName} • ${t.displayName}", color = TextPrimary) },
                                                onClick = {
                                                    newStepType = t
                                                    isNewStepTypeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                val condOptions: List<Pair<StepConditionType, String>> = listOf(
                                    StepConditionType.NONE to "Always",
                                    StepConditionType.PREVIOUS_STEP_SUCCESS to "Prev OK",
                                    StepConditionType.FOCUS_SESSION_ACTIVE to "If Focus",
                                    StepConditionType.PC_PAIRED to "If PC"
                                )
                                LiquidGlassSegmentedRow(
                                    items = condOptions,
                                    selectedItem = condOptions.find { it.first == newStepConditionType } ?: condOptions.first(),
                                    onSelectItem = { newStepConditionType = it.first },
                                    labelProvider = { it.second },
                                    accentColor = CyanGlow
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                val failOptions: List<Pair<StepFailurePolicy, String>> = listOf(
                                    StepFailurePolicy.STOP_MACRO to "Abort",
                                    StepFailurePolicy.CONTINUE to "Continue",
                                    StepFailurePolicy.RETRY_THEN_CONTINUE to "Retry+Cont"
                                )
                                LiquidGlassSegmentedRow(
                                    items = failOptions,
                                    selectedItem = failOptions.find { it.first == newStepFailurePolicy } ?: failOptions.first(),
                                    onSelectItem = { newStepFailurePolicy = it.first },
                                    labelProvider = { it.second },
                                    accentColor = IndigoPulse
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StudioTextField(
                                        value = newStepDelayMs,
                                        onValueChange = { newStepDelayMs = it },
                                        label = "Delay After (ms)",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StudioTextField(
                                        value = newStepMaxRetries,
                                        onValueChange = { newStepMaxRetries = it },
                                        label = "Max Retries (0-5)",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                LiquidGlowButton(
                                    text = "+ Add Step (${newStepType.displayName})",
                                    onClick = {
                                        sequenceSteps.add(
                                            ActionStep(
                                                stepId = "step_${UUID.randomUUID().toString().take(6)}",
                                                type = newStepType,
                                                title = newStepType.displayName,
                                                parameters = emptyMap(),
                                                delayAfterMs = newStepDelayMs.toLongOrNull()?.coerceIn(0L, 60_000L) ?: 250L,
                                                conditionType = newStepConditionType,
                                                conditionValue = newStepConditionValue,
                                                onFailurePolicy = newStepFailurePolicy,
                                                maxRetries = newStepMaxRetries.toIntOrNull()?.coerceIn(0, 5) ?: 0
                                            )
                                        )
                                    },
                                    accentColor = CyanGlow,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            else -> {
                                Text(
                                    text = "Ready to execute with system defaults.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Macro Engine Options: Reversible Toggle & Concurrency Policy
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "State-Aware Reversible Toggle",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Alternates ON/OFF state cleanly; supports manual resync if changed externally",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            LiquidGlassSwitch(
                                checked = isReversibleToggle,
                                onCheckedChange = { isReversibleToggle = it },
                                accentColor = MintSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Macro Concurrency Policy", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val concurrencyItems: List<Pair<MacroConcurrencyPolicy, String>> =
                            MacroConcurrencyPolicy.entries.map { it to it.label }
                        LiquidGlassSegmentedRow(
                            items = concurrencyItems,
                            selectedItem = concurrencyItems.find { it.first == concurrencyPolicy } ?: concurrencyItems.first(),
                            onSelectItem = { concurrencyPolicy = it.first },
                            labelProvider = { it.second },
                            accentColor = IndigoPulse
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gesture Binding Selector + Optional Modifier Key
                        Text("Bind to Gesture in Active Profile", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = isGestureDropdownExpanded,
                            onExpandedChange = { isGestureDropdownExpanded = it }
                        ) {
                            val gestureName = gestures.find { it.id == assignedGestureId }?.name ?: "Don't assign yet"
                            OutlinedTextField(
                                value = gestureName,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGestureDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanGlow,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = isGestureDropdownExpanded,
                                onDismissRequest = { isGestureDropdownExpanded = false },
                                containerColor = SlateGlassDark
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None / Don't assign yet", color = TextSecondary) },
                                    onClick = {
                                        assignedGestureId = null
                                        isGestureDropdownExpanded = false
                                    }
                                )
                                gestures.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text("${g.name} (${g.expectedImpacts}x)", color = TextPrimary) },
                                        onClick = {
                                            assignedGestureId = g.id
                                            isGestureDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (assignedGestureId != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Optional Required Gesture Modifier", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            val modChoices: List<Pair<String?, String>> = listOf(
                                null to "None",
                                "MOD_A" to "MOD_A",
                                "MOD_SHIFT" to "MOD_SHIFT",
                                "MOD_MUSIC" to "MOD_MUSIC"
                            )
                            LiquidGlassSegmentedRow(
                                items = modChoices,
                                selectedItem = modChoices.find { it.first == assignedRequiredModifier } ?: modChoices.first(),
                                onSelectItem = { assignedRequiredModifier = it.first },
                                labelProvider = { it.second },
                                accentColor = CyanGlow
                            )
                        }

                        // Validation Feedback Box
                        if (!validationResult.isValid || validationResult.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(
                                        if (!validationResult.isValid) RoseError.copy(alpha = 0.16f)
                                        else AmberAlert.copy(alpha = 0.14f)
                                    )
                                    .padding(10.dp)
                            ) {
                                Column {
                                    validationResult.errors.forEach { err ->
                                        Text("• Error: $err", style = MaterialTheme.typography.labelSmall, color = RoseError)
                                    }
                                    validationResult.warnings.forEach { warn ->
                                        Text("• Note: $warn", style = MaterialTheme.typography.labelSmall, color = AmberAlert)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Execution Preview Toggle
                        LiquidGlowButton(
                            text = if (showPreviewSteps) "Hide Execution Preview" else "Preview Execution Plan",
                            accentColor = CyanGlow,
                            onClick = { showPreviewSteps = !showPreviewSteps },
                            modifier = Modifier.fillMaxWidth().testTag("preview_macro_button")
                        )

                        if (showPreviewSteps && previewSummary.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                previewSummary.forEach { line ->
                                    Text(line, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Dry Run, Test Run & Save Action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlowButton(
                                text = "Dry Run",
                                onClick = {
                                    isTestingAction = true
                                    val testAction = buildCurrentAction()
                                    scope.launch {
                                        val result = controller.actionExecutor.executeAction(testAction, isSimulated = true)
                                        latestTestResult = result
                                        isTestingAction = false
                                    }
                                },
                                enabled = !isTestingAction,
                                accentColor = CyanGlow,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dry_run_action_button")
                            )

                            LiquidGlowButton(
                                text = if (isTestingAction) "Running..." else "Test Run",
                                onClick = {
                                    isTestingAction = true
                                    val testAction = buildCurrentAction()
                                    scope.launch {
                                        val result = controller.actionExecutor.executeAction(testAction, isSimulated = false)
                                        latestTestResult = result
                                        isTestingAction = false
                                    }
                                },
                                enabled = !isTestingAction,
                                accentColor = IndigoPulse,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_action_button")
                            )

                            LiquidGlowButton(
                                text = if (editingActionId != null) "Update" else "Save",
                                onClick = {
                                    val actionToSave = buildCurrentAction()
                                    scope.launch {
                                        controller.repository.saveAction(actionToSave)
                                        if (assignedGestureId != null && activeProfile != null) {
                                            controller.repository.addOrUpdateMapping(
                                                profileId = activeProfile!!.id,
                                                gestureId = assignedGestureId!!,
                                                actionId = actionToSave.id,
                                                requiredModifier = assignedRequiredModifier
                                            )
                                        }
                                        isEditorOpen = false
                                        editingActionId = null
                                    }
                                },
                                enabled = validationResult.isValid,
                                accentColor = MintSuccess,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_action_button")
                            )
                        }

                        // Test Run Result Banner
                        if (latestTestResult != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val r = latestTestResult!!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(14.dp))
                                    .background(if (r.success) MintSuccess.copy(alpha = 0.15f) else RoseError.copy(alpha = 0.15f))
                                    .border(
                                        1.dp,
                                        if (r.success) MintSuccess.copy(alpha = 0.45f) else RoseError.copy(alpha = 0.45f),
                                        RoundedRectangle(14.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (r.success) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (r.success) MintSuccess else RoseError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when {
                                                r.isSimulated && r.success -> "Dry-Run Validated (No Side Effects)"
                                                r.isSimulated && !r.success -> "Dry-Run Validation Failed"
                                                r.success -> "Action Succeeded"
                                                else -> "Action Failed"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (r.success) MintSuccess else RoseError
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = r.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                    if (r.stepReports.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        r.stepReports.forEachIndexed { idx, sr ->
                                            Text(
                                                text = "Step ${idx + 1} (${sr.stepTitle}): ${if (sr.skippedByCondition) "SKIPPED" else if (sr.success) "OK" else "FAIL"} — ${sr.message}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Filter Row
        item {
            Column {
                Text(
                    text = "Configured Actions (${filteredActions.size} / ${actions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedRectangle(12.dp))
                                .background(if (isSelected) CyanGlow.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                                .border(
                                    1.dp,
                                    if (isSelected) CyanGlow else Color.White.copy(alpha = 0.12f),
                                    RoundedRectangle(12.dp)
                                )
                                .clickable { selectedCategoryFilter = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        items(filteredActions) { action ->
            val toggleState = toggleStates[action.id]
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("action_card_${action.id}"),
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Row: Action Name + Category Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = action.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (action.type.requiresPcBridge) {
                                LiquidStatusPill(
                                    text = "PC",
                                    statusColor = AmberAlert
                                )
                            }
                            if (action.isReversibleToggle) {
                                LiquidStatusPill(
                                    text = if (toggleState?.isToggledOn == true) "ON" else "OFF",
                                    statusColor = if (toggleState?.isToggledOn == true) MintSuccess else IndigoPulse
                                )
                            }
                            LiquidStatusPill(
                                text = action.type.categoryName.substringBefore(" &"),
                                statusColor = CyanGlow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Row: Description + Glass Icon Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (action.isReversibleToggle) {
                                LiquidGlassIconButton(
                                    onClick = {
                                        val current = toggleState?.isToggledOn ?: false
                                        controller.actionExecutor.resyncAssumedState(action.id, !current)
                                    },
                                    size = 36.dp,
                                    accentColor = AmberAlert
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Resync Toggle State",
                                        tint = AmberAlert,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Edit button
                            LiquidGlassIconButton(
                                onClick = { openEditAction(action) },
                                size = 36.dp,
                                accentColor = CyanGlow
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Action",
                                    tint = CyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Test run button
                            LiquidGlassIconButton(
                                onClick = {
                                    scope.launch {
                                        val r = controller.actionExecutor.executeAction(action)
                                        controller.repository.recordExecutionLog(
                                            actionId = action.id,
                                            actionName = action.name,
                                            gestureName = "Manual Test",
                                            result = r
                                        )
                                    }
                                },
                                size = 36.dp,
                                accentColor = MintSuccess
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Run Test",
                                    tint = MintSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Delete button
                            if (actions.size > 1) {
                                LiquidGlassIconButton(
                                    onClick = {
                                        scope.launch {
                                            controller.repository.deleteAction(action.id)
                                        }
                                    },
                                    size = 36.dp,
                                    accentColor = RoseError
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Action",
                                        tint = RoseError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun StudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanGlow,
            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}
