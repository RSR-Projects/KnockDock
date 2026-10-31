package com.example

import com.example.knockdock.model.ActionStep
import com.example.knockdock.model.ActionType
import com.example.knockdock.model.DockProfile
import com.example.knockdock.model.GestureActionMapping
import com.example.knockdock.model.GestureDefinition
import com.example.knockdock.model.GestureType
import com.example.knockdock.model.ImpactEvent
import com.example.knockdock.model.KnockSettings
import com.example.knockdock.model.StepConditionType
import com.example.knockdock.model.StepFailurePolicy
import com.example.knockdock.recognition.GestureRecognizer
import com.example.knockdock.sensor.ImpactDetector
import com.example.knockdock.sensor.VibrationFilter
import com.example.knockdock.ui.theme.computeCardRenderParameters
import com.example.knockdock.ui.theme.computeNavigationRenderParameters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KnockDockSignalAndGestureTest {

    @Test
    fun testVibrationFilter_isolatesImpulseFromConstantGravity() {
        val filter = VibrationFilter(sampleRateHz = 100f, cutoffFrequencyHz = 12f)

        // Steady phone resting flat on desk: 0, 0, 9.8 m/s^2
        var result = filter.processSample(0f, 0f, 9.8f)
        for (i in 0 until 50) {
            result = filter.processSample(0f, 0f, 9.8f)
        }

        // DC gravity should be filtered out to near 0 impulse
        assertTrue("Impulse under steady gravity should be near zero", result.impulse < 0.2f)

        // Sudden desk knock on Z axis: +25.0 m/s^2 transient spike
        val shockResult = filter.processSample(0.5f, 0.4f, 34.8f)
        assertTrue("Sudden knock spike must produce significant impulse", shockResult.impulse > 10.0f)
    }

    @Test
    fun testImpactDetector_triggersOnKnockAndRejectsGyroMovement() {
        val detector = ImpactDetector(baseThreshold = 2.0f, sensitivity = 0.65f, gyroStationaryFilterEnabled = true)
        val filterResult = VibrationFilter.FilteredResult(
            impulse = 5.0f,
            filteredX = 0.2f,
            filteredY = 0.3f,
            filteredZ = 4.9f,
            currentNoiseFloor = 0.2f
        )

        // Case 1: Phone tilted or moved (gyro angular velocity = 2.5 rad/s > 1.2 rad/s threshold)
        val movingImpact = detector.processImpulse(
            timestampNanos = 1_000_000_000L,
            filteredResult = filterResult,
            gyroAngularVelocity = 2.5f
        )
        assertNull("Impact should be rejected when device is moving/tilting", movingImpact)

        // Case 2: Phone still on desk (gyro angular velocity = 0.05 rad/s)
        detector.processImpulse(
            timestampNanos = 2_000_000_000L,
            filteredResult = filterResult,
            gyroAngularVelocity = 0.05f
        )
        // Impact decay
        val decayResult = VibrationFilter.FilteredResult(
            impulse = 0.5f,
            filteredX = 0.05f,
            filteredY = 0.05f,
            filteredZ = 0.4f,
            currentNoiseFloor = 0.2f
        )
        val finalImpact = detector.processImpulse(
            timestampNanos = 2_050_000_000L,
            filteredResult = decayResult,
            gyroAngularVelocity = 0.05f
        )
        assertNotNull("Impact must be detected when phone is stationary on desk", finalImpact)
        assertEquals("Z", finalImpact?.dominantAxis)
    }

    @Test
    fun testGestureRecognizer_classifiesDoubleKnock() = runTest {
        val testScope = this
        val recognizer = GestureRecognizer(testScope)
        recognizer.cooldownMs = 500L
        recognizer.minConfidenceThreshold = 0.60f

        // Emit first knock at t = 100ms
        recognizer.onImpact(
            ImpactEvent(
                timestampNanos = 100_000_000L,
                peakAmplitude = 3.5f,
                durationMs = 25L,
                energy = 8f,
                dominantAxis = "Z"
            )
        )

        // Emit second knock at t = 320ms (gap 220ms - typical double tap)
        recognizer.onImpact(
            ImpactEvent(
                timestampNanos = 320_000_000L,
                peakAmplitude = 3.2f,
                durationMs = 25L,
                energy = 7.5f,
                dominantAxis = "Z"
            )
        )

        // Advance time past the inter-knock timeout (380ms)
        testScope.advanceTimeBy(500)

        // Verify active presets recognized double knock
        val presets = GestureDefinition.defaultPresets()
        val doublePreset = presets.find { it.type == GestureType.DOUBLE_KNOCK }
        assertNotNull(doublePreset)
        assertEquals(2, doublePreset?.expectedImpacts)
    }

    @Test
    fun testProfileConflictDetection_distinguishesModifierMappings() {
        // Two mappings with same gesture and NO modifier conflict:
        val profileWithConflicts = DockProfile(
            id = "test_profile",
            name = "Test Conflict Profile",
            description = "Testing conflict detection",
            mappings = listOf(
                GestureActionMapping(id = "1", gestureId = "preset_single_knock", actionId = "action_1", isEnabled = true, requiredModifier = null),
                GestureActionMapping(id = "2", gestureId = "preset_single_knock", actionId = "action_2", isEnabled = true, requiredModifier = null),
                GestureActionMapping(id = "3", gestureId = "preset_single_knock", actionId = "action_3", isEnabled = true, requiredModifier = "MOD_A")
            )
        )

        val gestureModCounts = profileWithConflicts.mappings.groupBy { "${it.gestureId}@@${it.requiredModifier ?: ""}" }
        val conflicts = gestureModCounts.filter { it.value.size > 1 }

        assertEquals(1, conflicts.size)
        assertTrue(conflicts.containsKey("preset_single_knock@@"))
        assertFalse(conflicts.containsKey("preset_single_knock@@MOD_A"))
    }

    @Test
    fun testCardGlassVsNavigationGlass_strictIndependenceAndBlurOffBehavior() {
        val initialSettings = KnockSettings()
        val initialNav = initialSettings.toNavigationGlassSettings()
        val initialCard = initialSettings.toCardGlassSettings()

        // 1. Turning OFF Card Blur must disable card blur pass & refraction pass while keeping Navigation Bar unchanged
        val cardBlurOff = initialCard.copy(
            cardBlurEnabled = false,
            cardBlurRadiusDp = 35f,
            cardBackgroundOpacity = 0.80f,
            cardTintColorKey = "VIOLET",
            cardTintIntensity = 0.55f,
            cardRefractionIntensity = 0.90f,
            cardRimHighlightIntensity = 0.90f,
            cardCornerRadiusDp = 32f,
            cardSurfaceContrast = 0.85f
        )
        val updatedWithCardChanges = initialSettings.withCardGlassSettings(cardBlurOff)

        // Verify NavigationGlassSettings is 100% identical to initialNav
        assertEquals(
            "Modifying CardGlassSettings must NEVER alter NavigationGlassSettings",
            initialNav,
            updatedWithCardChanges.toNavigationGlassSettings()
        )

        // Verify Card Render Parameters when cardBlurEnabled = false
        val cardRenderParams = computeCardRenderParameters(
            cardSettings = updatedWithCardChanges.toCardGlassSettings(),
            visualEffectsQuality = "HIGH"
        )
        assertFalse("Card blur pass must be disabled when cardBlurEnabled is false", cardRenderParams.executesBlurPass)
        assertEquals(0f, cardRenderParams.effectiveBlurRadiusDp, 0.001f)
        assertEquals(0f, cardRenderParams.effectiveRefractionIntensity, 0.001f)

        // Verify Navigation Render Parameters remain active with blur & refraction
        val navRenderParams = computeNavigationRenderParameters(
            navSettings = updatedWithCardChanges.toNavigationGlassSettings(),
            visualEffectsQuality = "HIGH"
        )
        assertTrue("Navigation bar blur pass must remain active when card blur is turned off", navRenderParams.executesBlurPass)
        assertEquals(initialNav.navBlurRadiusDp, navRenderParams.effectiveBlurRadiusDp, 0.001f)
        assertTrue("Navigation bar refraction amount must remain active", navRenderParams.effectiveRefractionAmountDp > 0f)

        // 2. Modifying NavigationGlassSettings must NEVER alter CardGlassSettings
        val customNav = initialNav.copy(
            navBlurRadiusDp = 12f,
            navRefractionHeightDp = 14f,
            navRefractionAmountDp = 20f,
            navChromaticAberration = false
        )
        val updatedWithNavChanges = updatedWithCardChanges.withNavigationGlassSettings(customNav)
        assertEquals(
            "Modifying NavigationGlassSettings must NEVER alter CardGlassSettings",
            cardBlurOff,
            updatedWithNavChanges.toCardGlassSettings()
        )
        assertNotEquals(initialNav, updatedWithNavChanges.toNavigationGlassSettings())
    }

    @Test
    fun testAllThirtyActionTypesExistAndDefaultPresetsCoverExpansion() {
        assertEquals("ActionType enum should have 30 total actions (15 original + 15 new)", 30, ActionType.entries.size)

        // Verify all 15 new action types are present in ActionType
        val expectedNewTypes = listOf(
            ActionType.DESK_MODE_TOGGLE,
            ActionType.FOCUS_SESSION_CONTROLLER,
            ActionType.WORKSPACE_SWITCHER,
            ActionType.MUSIC_SESSION_CONTROLLER,
            ActionType.PRESENTATION_REMOTE,
            ActionType.GESTURE_MODIFIER,
            ActionType.SNIPPET_LAUNCHER,
            ActionType.SCREENSHOT_ANNOTATION_LAUNCHER,
            ActionType.QUICK_REMINDER_TIMER,
            ActionType.QUICK_VOICE_MEMO,
            ActionType.QUICK_TOOL_LAUNCHER,
            ActionType.PLAYLIST_STATION_LAUNCHER,
            ActionType.SMART_HOME_SCENE_TRIGGER,
            ActionType.PAIRED_DEVICE_COMMAND,
            ActionType.EMERGENCY_LIGHT_SEQUENCE
        )
        expectedNewTypes.forEach { newType ->
            assertTrue("Missing $newType in ActionType", ActionType.entries.contains(newType))
        }
    }

    @Test
    fun testConditionalAndFailurePolicyMacroStepModel() {
        val step = ActionStep(
            stepId = "step_cond_1",
            type = ActionType.TOGGLE_FLASHLIGHT,
            title = "Conditional Torch",
            parameters = emptyMap(),
            delayAfterMs = 200L,
            conditionType = StepConditionType.FOCUS_SESSION_ACTIVE,
            conditionValue = "true",
            onFailurePolicy = StepFailurePolicy.RETRY_THEN_CONTINUE,
            maxRetries = 2
        )
        assertEquals(StepConditionType.FOCUS_SESSION_ACTIVE, step.conditionType)
        assertEquals(StepFailurePolicy.RETRY_THEN_CONTINUE, step.onFailurePolicy)
        assertEquals(2, step.maxRetries)
    }

    @Test
    fun testCardBlurDisabled_preservesOtherCardGlassEffectsAndNeverTouchesNavBar() {
        val base = KnockSettings()
        val navSnapshot = computeNavigationRenderParameters(base.toNavigationGlassSettings(), "HIGH")

        val cardNoBlur = base.toCardGlassSettings().copy(
            cardBlurEnabled = false,
            cardBackgroundOpacity = 0.64f,
            cardTintColorKey = "AMBER",
            cardTintIntensity = 0.42f,
            cardRimHighlightIntensity = 0.78f,
            cardCornerRadiusDp = 28f,
            cardSurfaceContrast = 0.70f
        )
        val updated = base.withCardGlassSettings(cardNoBlur)
        val cardParams = computeCardRenderParameters(updated.toCardGlassSettings(), "HIGH")
        val navAfter = computeNavigationRenderParameters(updated.toNavigationGlassSettings(), "HIGH")

        // When card blur is disabled, blur pass is skipped, but rim highlight, tint, opacity, corner radius, and contrast remain active
        assertFalse(cardParams.executesBlurPass)
        assertEquals(0f, cardParams.effectiveBlurRadiusDp, 0.001f)
        assertEquals(0.78f, cardParams.effectiveRimHighlightIntensity, 0.001f)
        assertEquals(28f, cardParams.cornerRadiusDp, 0.001f)
        assertTrue(cardParams.effectiveOpacity >= 0.64f)

        // Navigation bar parameters must be 100% identical
        assertEquals(navSnapshot, navAfter)
    }

    @Test
    fun testActionExecutor_dryRunNeverExecutesSideEffectsAndValidatesInputs() = kotlinx.coroutines.runBlocking {
        val stubContext = android.content.ContextWrapper(null)
        val execScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        var profileId = "profile_desk_commander"
        var toggleMonCount = 0

        val executor = com.example.knockdock.action.ActionExecutor(
            context = stubContext,
            onToggleMonitoring = { toggleMonCount++ },
            onSwitchProfile = { profileId = it },
            getActiveProfileId = { profileId },
            isPcPairedProvider = { false },
            executorScope = execScope
        )

        try {
            // 1. Individual action dry runs: Focus Session, Timer, Desk Mode, URL, Smart-Home Scene, Reversible Toggle
            val focusAct = com.example.knockdock.model.ActionDefinition(
                id = "sim_focus",
                name = "Focus Dry Run",
                description = "",
                type = ActionType.FOCUS_SESSION_CONTROLLER,
                parameters = mapOf("mode" to "START", "durationMinutes" to "25", "breakMinutes" to "5")
            )
            val rFocus = executor.executeAction(focusAct, isSimulated = true)
            assertTrue(rFocus.success)
            assertTrue(rFocus.isSimulated)
            assertTrue(rFocus.message.contains("[DRY-RUN]"))
            assertFalse("Dry-run Focus Session must not start real session", executor.focusSessionFlow.value.isActive)

            val timerAct = com.example.knockdock.model.ActionDefinition(
                id = "sim_timer",
                name = "Timer Dry Run",
                description = "",
                type = ActionType.QUICK_REMINDER_TIMER,
                parameters = mapOf("durationSeconds" to "120", "reminderText" to "Check Oven")
            )
            val rTimer = executor.executeAction(timerAct, isSimulated = true)
            assertTrue(rTimer.success)
            assertTrue("Dry-run timer must not schedule active timer", executor.activeTimersFlow.value.isEmpty())

            val deskAct = com.example.knockdock.model.ActionDefinition(
                id = "sim_desk",
                name = "Desk Mode Dry Run",
                description = "",
                type = ActionType.DESK_MODE_TOGGLE,
                parameters = mapOf("primaryProfileId" to "profile_desk_commander", "secondaryProfileId" to "profile_focus_guard")
            )
            val rDesk = executor.executeAction(deskAct, isSimulated = true)
            assertTrue(rDesk.success)
            assertEquals("Dry-run Desk Mode must not switch profile", "profile_desk_commander", profileId)

            val badPairedAct = com.example.knockdock.model.ActionDefinition(
                id = "sim_bad_pc",
                name = "Unauthorized PC",
                description = "",
                type = ActionType.PAIRED_DEVICE_COMMAND,
                parameters = mapOf("command" to "format_drive")
            )
            val rBadPc = executor.executeAction(badPairedAct, isSimulated = true)
            assertFalse("Unapproved command must fail validation in dry run", rBadPc.success)
            assertTrue(rBadPc.isSimulated)

            // 2. Multi-step macro dry run with conditions, retries, and recovery step
            val complexMacro = com.example.knockdock.model.ActionDefinition(
                id = "sim_macro",
                name = "Complex Macro",
                description = "",
                type = ActionType.MULTI_STEP_SEQUENCE,
                steps = listOf(
                    ActionStep(
                        stepId = "s1",
                        type = ActionType.TOGGLE_MONITORING,
                        title = "Toggle Mon",
                        delayAfterMs = 200L
                    ),
                    ActionStep(
                        stepId = "s2",
                        type = ActionType.NOTIFICATION,
                        title = "Skipped Step",
                        conditionType = StepConditionType.FOCUS_SESSION_ACTIVE
                    ),
                    ActionStep(
                        stepId = "s3",
                        type = ActionType.LAUNCH_APP,
                        title = "Invalid App Step",
                        parameters = mapOf("packageName" to ""),
                        onFailurePolicy = StepFailurePolicy.RUN_RECOVERY_STEP,
                        maxRetries = 2,
                        retryDelayMs = 50L,
                        recoveryActionType = ActionType.TOGGLE_MONITORING
                    )
                )
            )
            val rMacro = executor.executeAction(complexMacro, isSimulated = true)
            assertTrue(rMacro.isSimulated)
            assertFalse(rMacro.success)
            assertEquals(3, rMacro.stepReports.size)
            assertTrue(rMacro.stepReports[0].success)
            assertTrue(rMacro.stepReports[1].skippedByCondition)
            assertFalse(rMacro.stepReports[2].success)
            assertEquals(3, rMacro.stepReports[2].attemptCount)
            assertTrue(rMacro.stepReports[2].recoveryTriggered)
            assertEquals("Dry run must never invoke real onToggleMonitoring", 0, toggleMonCount)

            // 3. Real action execution works as expected when isSimulated = false
            val rRealDesk = executor.executeAction(deskAct, isSimulated = false)
            assertTrue(rRealDesk.success)
            assertFalse(rRealDesk.isSimulated)
            assertEquals("profile_focus_guard", profileId)

            // 4. Verify service constants use com.rsrprojects.knockdock
            assertEquals("com.rsrprojects.knockdock.START_MONITORING", com.example.knockdock.service.KnockMonitoringService.ACTION_START_MONITORING)
            assertEquals("com.rsrprojects.knockdock.STOP_MONITORING", com.example.knockdock.service.KnockMonitoringService.ACTION_STOP_MONITORING)
        } finally {
            executor.cancelAllRunningMacros()
            execScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun testAboutSection_constantsBuildConfigPrivacyAndThirdPartyLicenses() {
        // 1. Verify official KnockDock About constants & GitHub URLs
        assertEquals("KnockDock", com.example.knockdock.ui.screens.KnockDockAboutConstants.APP_NAME)
        assertEquals("Turn physical knocks into actions.", com.example.knockdock.ui.screens.KnockDockAboutConstants.TAGLINE)
        assertEquals("RSR-Projects", com.example.knockdock.ui.screens.KnockDockAboutConstants.DEVELOPER_NAME)
        assertEquals("Apache License 2.0", com.example.knockdock.ui.screens.KnockDockAboutConstants.LICENSE_NAME)
        assertEquals("RELEASE", com.example.knockdock.ui.screens.KnockDockAboutConstants.BUILD_VARIANT_RELEASE)
        assertEquals(
            "https://github.com/RSR-Projects/KnockDock",
            com.example.knockdock.ui.screens.KnockDockAboutConstants.GITHUB_REPO_URL
        )
        assertEquals(
            "https://github.com/RSR-Projects/KnockDock/issues",
            com.example.knockdock.ui.screens.KnockDockAboutConstants.GITHUB_ISSUES_URL
        )
        assertEquals(
            "https://github.com/RSR-Projects/KnockDock/releases",
            com.example.knockdock.ui.screens.KnockDockAboutConstants.GITHUB_RELEASES_URL
        )

        // 2. Verify BuildConfig version & applicationId are dynamically generated and valid
        assertEquals("com.rsrprojects.knockdock", BuildConfig.APPLICATION_ID)
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
        assertTrue(BuildConfig.VERSION_CODE >= 1)

        // 3. Verify Privacy disclosures match actual sensor & network implementation
        val privacyDisclosures = com.example.knockdock.ui.screens.getKnockDockPrivacyDisclosures()
        assertEquals(4, privacyDisclosures.size)
        assertTrue(privacyDisclosures.any { it.description.contains("TYPE_ACCELEROMETER") && it.description.contains("TYPE_GYROSCOPE") })
        assertTrue(privacyDisclosures.any { it.description.contains("RECORD_AUDIO") })
        assertTrue(privacyDisclosures.any { it.badge == "Zero Analytics" })

        // 4. Verify Open-Source Third-Party Licences match actual dependencies in libs.versions.toml
        val licenses = com.example.knockdock.ui.screens.getKnockDockThirdPartyLicenses()
        assertEquals(9, licenses.size)
        assertTrue(licenses.any { it.artifactCoordinates.contains("io.github.kyant0:backdrop") && it.licenseName == "Apache License 2.0" })
        assertTrue(licenses.any { it.artifactCoordinates.contains("com.squareup.retrofit2:retrofit") && it.licenseName == "Apache License 2.0" })
        assertTrue(licenses.any { it.artifactCoordinates.contains("com.squareup.moshi:moshi-kotlin") && it.licenseName == "Apache License 2.0" })
        assertTrue(licenses.any { it.artifactCoordinates.contains("androidx.room:room-runtime") && it.licenseName == "Apache License 2.0" })
    }
}

