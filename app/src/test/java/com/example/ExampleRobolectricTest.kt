package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.knockdock.action.ActionExecutor
import com.example.knockdock.action.ActionRegistry
import com.example.knockdock.data.KnockDockRepository
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.ActionStep
import com.example.knockdock.model.ActionType
import com.example.knockdock.model.CardGlassSettings
import com.example.knockdock.model.DockProfile
import com.example.knockdock.model.GestureActionMapping
import com.example.knockdock.model.KnockSettings
import com.example.knockdock.model.NavigationGlassSettings
import com.example.knockdock.model.StepConditionType
import com.example.knockdock.model.StepFailurePolicy
import com.example.knockdock.service.KnockMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var actionRegistry: ActionRegistry
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var testExecutorScope: CoroutineScope
    private var activeProfileId: String = "profile_desk_commander"
    private var toggleMonitoringCalls: Int = 0
    private var secondaryActionCalls: MutableList<Pair<String, Boolean>> = mutableListOf()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testExecutorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        activeProfileId = "profile_desk_commander"
        toggleMonitoringCalls = 0
        secondaryActionCalls.clear()
        actionRegistry = ActionRegistry(context)
        actionExecutor = ActionExecutor(
            context = context,
            onToggleMonitoring = { toggleMonitoringCalls++ },
            onSwitchProfile = { profileId -> activeProfileId = profileId },
            getActiveProfileId = { activeProfileId },
            isPcPairedProvider = { false },
            onRunSecondaryActionById = { actionId, isSimulated ->
                secondaryActionCalls.add(actionId to isSimulated)
                null
            },
            executorScope = testExecutorScope
        )
    }

    @After
    fun tearDown() {
        actionExecutor.stopFocusSessionInternal()
        actionExecutor.clearGestureModifier()
        actionExecutor.activeTimersFlow.value.forEach { actionExecutor.dismissTimer(it.timerId) }
        actionExecutor.discardVoiceMemo()
        actionExecutor.cancelAllRunningMacros()
        testExecutorScope.cancel()
    }

    @Test
    fun `read string and verify release application identity`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("KnockDock", appName)
        assertEquals("com.rsrprojects.knockdock", context.packageName)
        assertEquals("com.rsrprojects.knockdock.START_MONITORING", KnockMonitoringService.ACTION_START_MONITORING)
        assertEquals("com.rsrprojects.knockdock.STOP_MONITORING", KnockMonitoringService.ACTION_STOP_MONITORING)
    }

    @Test
    fun `validateAction detects invalid inputs unapproved commands and circular profile loops`() {
        val registry = actionRegistry

        // 1. Unapproved paired-device command is rejected
        val badPaired = ActionDefinition(
            id = "bad_paired",
            name = "Bad Command",
            description = "",
            type = ActionType.PAIRED_DEVICE_COMMAND,
            parameters = mapOf("command" to "rm -rf /", "host" to "192.168.1.10", "port" to "8989")
        )
        val res1 = registry.validateAction(badPaired)
        assertFalse("Unapproved paired command must fail validation", res1.isValid)

        // 2. Invalid Smart Home URL scheme is rejected
        val badScene = ActionDefinition(
            id = "bad_scene",
            name = "Bad Scene",
            description = "",
            type = ActionType.SMART_HOME_SCENE_TRIGGER,
            parameters = mapOf("webhookUrl" to "ftp://invalid-endpoint")
        )
        assertFalse(registry.validateAction(badScene).isValid)

        // 3. Self-referencing Desk Mode Toggle onActivateActionId is rejected
        val circularDeskMode = ActionDefinition(
            id = "desk_loop",
            name = "Circular Desk Mode",
            description = "",
            type = ActionType.DESK_MODE_TOGGLE,
            parameters = mapOf(
                "primaryProfileId" to "profile_desk_commander",
                "secondaryProfileId" to "profile_focus_guard",
                "onActivateActionId" to "desk_loop"
            )
        )
        assertFalse("Circular onActivateActionId must fail validation", registry.validateAction(circularDeskMode).isValid)
    }

    @Test
    fun `dry run simulation validates configuration and never executes real side effects`() = runBlocking {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("initial", "INITIAL_CLIP"))
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.clearNextStartedActivities()

        // 1. Dry-run URL launch: must NOT start any browser Activity
        val urlAction = ActionDefinition(
            id = "sim_url",
            name = "Open Docs",
            description = "",
            type = ActionType.LAUNCH_URL,
            parameters = mapOf("url" to "https://developer.android.com")
        )
        val rUrl = actionExecutor.executeAction(urlAction, isSimulated = true)
        assertTrue(rUrl.success)
        assertTrue(rUrl.isSimulated)
        assertTrue(rUrl.message.contains("[DRY-RUN]"))
        assertNull("Dry-run URL launch must not start an Activity", shadowApp.nextStartedActivity)

        // 2. Dry-run invalid URL still fails validation in simulation mode
        val badUrlAction = urlAction.copy(parameters = mapOf("url" to "javascript:alert(1)"))
        val rBadUrl = actionExecutor.executeAction(badUrlAction, isSimulated = true)
        assertFalse("Invalid URL must fail even in dry-run", rBadUrl.success)
        assertTrue(rBadUrl.isSimulated)

        // 3. Dry-run Focus Session: must NOT start focus timer or mutate focusSessionFlow
        val focusAction = ActionDefinition(
            id = "sim_focus",
            name = "Sim Focus",
            description = "",
            type = ActionType.FOCUS_SESSION_CONTROLLER,
            parameters = mapOf("mode" to "START", "durationMinutes" to "25", "breakMinutes" to "5", "enableDnd" to "true")
        )
        val rFocus = actionExecutor.executeAction(focusAction, isSimulated = true)
        assertTrue(rFocus.success)
        assertTrue(rFocus.isSimulated)
        assertFalse("Dry-run Focus Session must not activate real focus session", actionExecutor.focusSessionFlow.value.isActive)

        // 4. Dry-run Snippet Launcher: must NOT overwrite clipboard
        val snippetAction = ActionDefinition(
            id = "sim_snippet",
            name = "Sim Snippet",
            description = "",
            type = ActionType.SNIPPET_LAUNCHER,
            parameters = mapOf("snippetContent" to "SECRET_TEXT", "snippetTitle" to "Test", "deliveryMode" to "COPY_CLIPBOARD")
        )
        val rSnippet = actionExecutor.executeAction(snippetAction, isSimulated = true)
        assertTrue(rSnippet.success)
        assertEquals("INITIAL_CLIP", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // 5. Dry-run Quick Reminder Timer: must NOT schedule real timer
        val timerAction = ActionDefinition(
            id = "sim_timer",
            name = "Sim Timer",
            description = "",
            type = ActionType.QUICK_REMINDER_TIMER,
            parameters = mapOf("durationSeconds" to "120", "reminderText" to "Dry Run Reminder")
        )
        val rTimer = actionExecutor.executeAction(timerAction, isSimulated = true)
        assertTrue(rTimer.success)
        assertTrue("Dry-run timer must not add active timer", actionExecutor.activeTimersFlow.value.isEmpty())

        // 6. Dry-run Desk Mode Toggle: must NOT switch activeProfileId
        val deskAction = ActionDefinition(
            id = "sim_desk",
            name = "Sim Desk Mode",
            description = "",
            type = ActionType.DESK_MODE_TOGGLE,
            parameters = mapOf(
                "primaryProfileId" to "profile_desk_commander",
                "secondaryProfileId" to "profile_focus_guard",
                "onActivateActionId" to "sub_action_1"
            )
        )
        val rDesk = actionExecutor.executeAction(deskAction, isSimulated = true)
        assertTrue(rDesk.success)
        assertEquals("profile_desk_commander", activeProfileId)

        // 7. Dry-run Smart-Home Scene Trigger: validates URL and does not send HTTP request
        val sceneAction = ActionDefinition(
            id = "sim_scene",
            name = "Sim Scene",
            description = "",
            type = ActionType.SMART_HOME_SCENE_TRIGGER,
            parameters = mapOf("sceneName" to "Study", "webhookUrl" to "http://192.168.1.50:8123/api/webhook/study")
        )
        val rScene = actionExecutor.executeAction(sceneAction, isSimulated = true)
        assertTrue(rScene.success)
        assertTrue(rScene.message.contains("[DRY-RUN]"))

        // 8. Dry-run Reversible Toggle: must NOT mutate toggleStatesFlow
        val revAction = ActionDefinition(
            id = "sim_rev",
            name = "Sim Reversible",
            description = "",
            type = ActionType.NOTIFICATION,
            parameters = mapOf("title" to "T", "body" to "B"),
            isReversibleToggle = true
        )
        val rRev = actionExecutor.executeAction(revAction, isSimulated = true)
        assertTrue(rRev.success)
        assertNull("Dry-run must not mutate reversible toggle state", actionExecutor.toggleStatesFlow.value["sim_rev"])
    }

    @Test
    fun `multi-step macro dry run traces conditions retries and recovery without side effects`() = runBlocking {
        val macro = ActionDefinition(
            id = "sim_macro_complex",
            name = "Complex Dry Run Macro",
            description = "",
            type = ActionType.MULTI_STEP_SEQUENCE,
            steps = listOf(
                ActionStep(
                    stepId = "s1_valid",
                    type = ActionType.TOGGLE_MONITORING,
                    title = "Toggle Monitoring Step",
                    delayAfterMs = 500L
                ),
                ActionStep(
                    stepId = "s2_skipped",
                    type = ActionType.NOTIFICATION,
                    title = "Focus Only Step",
                    conditionType = StepConditionType.FOCUS_SESSION_ACTIVE
                ),
                ActionStep(
                    stepId = "s3_fail_with_retry_and_recovery",
                    type = ActionType.LAUNCH_APP,
                    title = "Missing Package Step",
                    parameters = mapOf("packageName" to ""),
                    onFailurePolicy = StepFailurePolicy.RUN_RECOVERY_STEP,
                    maxRetries = 2,
                    retryDelayMs = 50L,
                    recoveryActionType = ActionType.TOGGLE_MONITORING
                )
            )
        )

        val result = actionExecutor.executeAction(macro, isSimulated = true)
        assertTrue(result.isSimulated)
        assertFalse("Macro should report overall failure because step 3 had invalid blank packageName", result.success)
        assertEquals(3, result.stepReports.size)

        // Step 1 succeeded in dry-run without invoking real onToggleMonitoring
        assertTrue(result.stepReports[0].success)
        assertTrue(result.stepReports[0].message.contains("[DRY-RUN]"))
        assertEquals("Dry-run must never invoke real onToggleMonitoring", 0, toggleMonitoringCalls)

        // Step 2 skipped by condition
        assertTrue(result.stepReports[1].skippedByCondition)

        // Step 3 retried (1 initial + 2 retries = 3 attempts) and ran recovery step in dry-run mode
        assertFalse(result.stepReports[2].success)
        assertEquals(3, result.stepReports[2].attemptCount)
        assertTrue(result.stepReports[2].recoveryTriggered)
        assertTrue(result.stepReports[2].message.contains("[DRY-RUN]"))
        assertEquals("Recovery step in dry-run must also not invoke real onToggleMonitoring", 0, toggleMonitoringCalls)
    }

    @Test
    fun `execute real actions Focus Session Gesture Modifier Snippet Timer and Desk Mode`() = runBlocking {
        // 1. Focus Session Controller: start, pause, resume, stop
        val startFocus = ActionDefinition(
            id = "test_focus_start",
            name = "Start Focus",
            description = "",
            type = ActionType.FOCUS_SESSION_CONTROLLER,
            parameters = mapOf("mode" to "START", "durationMinutes" to "25", "breakMinutes" to "5", "enableDnd" to "false")
        )
        val rStart = actionExecutor.executeAction(startFocus)
        assertTrue(rStart.success)
        assertFalse(rStart.isSimulated)
        assertTrue(actionExecutor.focusSessionFlow.value.isActive)
        assertEquals(25 * 60, actionExecutor.focusSessionFlow.value.remainingSeconds)

        val pauseFocus = startFocus.copy(parameters = mapOf("mode" to "PAUSE"))
        actionExecutor.executeAction(pauseFocus)
        assertTrue(actionExecutor.focusSessionFlow.value.isPaused)

        val endFocus = startFocus.copy(parameters = mapOf("mode" to "STOP"))
        actionExecutor.executeAction(endFocus)
        assertFalse(actionExecutor.focusSessionFlow.value.isActive)

        // 2. Gesture Modifier: arm, consume once, clear
        val modAction = ActionDefinition(
            id = "test_mod",
            name = "Arm MOD_A",
            description = "",
            type = ActionType.GESTURE_MODIFIER,
            parameters = mapOf("modifierKey" to "MOD_A", "timeoutMs" to "5000")
        )
        val rMod = actionExecutor.executeAction(modAction)
        assertTrue(rMod.success)
        assertEquals("MOD_A", actionExecutor.activeModifierFlow.value?.modifierKey)
        val consumed = actionExecutor.consumeActiveModifierIfValid()
        assertEquals("MOD_A", consumed)
        assertEquals(null, actionExecutor.consumeActiveModifierIfValid())

        // 3. Snippet Launcher copies text to clipboard
        val snippetAction = ActionDefinition(
            id = "test_snippet",
            name = "Copy Snippet",
            description = "",
            type = ActionType.SNIPPET_LAUNCHER,
            parameters = mapOf("snippetContent" to "KnockDock Verified Snippet", "snippetTitle" to "Test", "deliveryMode" to "COPY_CLIPBOARD")
        )
        val rSnippet = actionExecutor.executeAction(snippetAction)
        assertTrue(rSnippet.success)

        // 4. Quick Reminder Timer: start, snooze, dismiss
        val timerAction = ActionDefinition(
            id = "test_timer",
            name = "Start 2m Timer",
            description = "",
            type = ActionType.QUICK_REMINDER_TIMER,
            parameters = mapOf("durationSeconds" to "120", "reminderText" to "Stretch Break", "presetLabel" to "2m")
        )
        val rTimer = actionExecutor.executeAction(timerAction)
        assertTrue(rTimer.success)
        assertEquals(1, actionExecutor.activeTimersFlow.value.size)
        val timerId = actionExecutor.activeTimersFlow.value.first().timerId
        actionExecutor.snoozeTimer(timerId, 180)
        assertTrue(actionExecutor.activeTimersFlow.value.first().remainingSeconds >= 170)
        actionExecutor.dismissTimer(timerId)
        assertTrue(actionExecutor.activeTimersFlow.value.isEmpty())

        // 5. Desk Mode Toggle switches profiles cleanly
        val deskModeAction = ActionDefinition(
            id = "test_desk_mode",
            name = "Cycle Desk Mode",
            description = "",
            type = ActionType.DESK_MODE_TOGGLE,
            parameters = mapOf(
                "primaryProfileId" to "profile_desk_commander",
                "secondaryProfileId" to "profile_focus_guard"
            )
        )
        val rDesk = actionExecutor.executeAction(deskModeAction)
        assertTrue(rDesk.success)
        assertEquals("profile_focus_guard", activeProfileId)
    }

    @Test
    fun `macro engine supports conditions retries recovery and reversible toggles`() = runBlocking {
        val reversibleAction = ActionDefinition(
            id = "rev_toggle_1",
            name = "Reversible Chime",
            description = "",
            type = ActionType.NOTIFICATION,
            parameters = mapOf("title" to "Toggle Test", "body" to "Body"),
            isReversibleToggle = true
        )
        val run1 = actionExecutor.executeAction(reversibleAction)
        assertTrue(run1.success)
        assertEquals(true, run1.toggledToActiveState)
        val run2 = actionExecutor.executeAction(reversibleAction)
        assertTrue(run2.success)
        assertEquals(false, run2.toggledToActiveState)

        actionExecutor.resyncAssumedState("rev_toggle_1", true)
        assertEquals(true, actionExecutor.toggleStatesFlow.value["rev_toggle_1"]?.isToggledOn)

        val macroWithCondition = ActionDefinition(
            id = "cond_macro",
            name = "Conditional Macro",
            description = "",
            type = ActionType.MULTI_STEP_SEQUENCE,
            steps = listOf(
                ActionStep(
                    stepId = "s1",
                    type = ActionType.NOTIFICATION,
                    title = "Always Step",
                    parameters = mapOf("title" to "Step 1", "body" to "Runs always"),
                    delayAfterMs = 1L,
                    conditionType = StepConditionType.NONE
                ),
                ActionStep(
                    stepId = "s2",
                    type = ActionType.NOTIFICATION,
                    title = "Focus Only Step",
                    parameters = mapOf("title" to "Step 2", "body" to "Requires Focus"),
                    delayAfterMs = 1L,
                    conditionType = StepConditionType.FOCUS_SESSION_ACTIVE
                )
            )
        )
        val condResult = actionExecutor.executeAction(macroWithCondition)
        assertTrue(condResult.success)
        assertEquals(2, condResult.stepReports.size)
        assertFalse("Step 1 should not be skipped", condResult.stepReports[0].skippedByCondition)
        assertTrue("Step 2 must be skipped because Focus Session is inactive", condResult.stepReports[1].skippedByCondition)

        val previewLines = actionExecutor.previewMacroExecution(macroWithCondition)
        assertTrue(previewLines.size >= 3)
    }

    @Test
    fun `repository exports and imports JSON safely and persists independent glass settings`() = runBlocking {
        val repository = KnockDockRepository(context, testExecutorScope)

        // 1. Export a profile with modifier mappings and preferences to JSON
        val sampleProfile = DockProfile(
            id = "profile_work_test",
            name = "Work Mode Backup",
            description = "Export test profile",
            preferredSamplingRate = "FASTEST",
            preferredSensitivity = 0.78f,
            mappings = listOf(
                GestureActionMapping(
                    id = "m1",
                    gestureId = "preset_double_knock",
                    actionId = "action_play_pause",
                    isEnabled = true,
                    requiredModifier = "MOD_A"
                )
            )
        )
        val exportedJson = repository.exportProfileJson(sampleProfile)
        assertTrue(exportedJson.contains("Work Mode Backup"))
        assertTrue(exportedJson.contains("MOD_A"))

        // 2. Import JSON and verify round-trip fields
        val importedResult = repository.importProfileJson(exportedJson)
        assertTrue(importedResult.isSuccess)
        val imported = importedResult.getOrThrow()
        assertEquals("Work Mode Backup", imported.name)
        assertEquals("FASTEST", imported.preferredSamplingRate)
        assertEquals(0.78f, imported.preferredSensitivity ?: 0f, 0.001f)
        assertEquals(1, imported.mappings.size)
        assertEquals("MOD_A", imported.mappings[0].requiredModifier)

        // 3. Reject unsafe script payload in importProfileJson
        val maliciousResult = repository.importProfileJson("""{"name":"<script>alert(1)</script>"}""")
        assertTrue(maliciousResult.isFailure)

        // 4. Independent CardGlassSettings and NavigationGlassSettings persistence
        val customCard = CardGlassSettings(
            cardBlurEnabled = false,
            cardBlurRadiusDp = 10f,
            cardBackgroundOpacity = 0.72f,
            cardTintColorKey = "EMERALD",
            cardTintIntensity = 0.45f,
            cardRefractionIntensity = 0.30f,
            cardRimHighlightIntensity = 0.80f,
            cardCornerRadiusDp = 28f,
            cardSurfaceContrast = 0.75f,
            cardLightweightMode = true
        )
        val navBefore = repository.settingsFlow.value.toNavigationGlassSettings()
        repository.updateCardGlassSettings(customCard)
        assertEquals(customCard, repository.settingsFlow.value.toCardGlassSettings())
        assertEquals(navBefore, repository.settingsFlow.value.toNavigationGlassSettings())

        val customNav = NavigationGlassSettings(
            navBlurEnabled = true,
            navBlurRadiusDp = 33f,
            navRefractionHeightDp = 20f,
            navRefractionAmountDp = 52f,
            navChromaticAberration = false,
            navBackgroundOpacity = 0.40f,
            navRimHighlightIntensity = 0.70f
        )
        repository.updateNavigationGlassSettings(customNav)
        repository.resetCardGlassSettingsToDefault()
        assertEquals(CardGlassSettings(), repository.settingsFlow.value.toCardGlassSettings())
        assertEquals(customNav, repository.settingsFlow.value.toNavigationGlassSettings())
    }
}
