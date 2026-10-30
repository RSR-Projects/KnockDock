package com.example.knockdock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.ActionExecutionResult
import com.example.knockdock.model.ActionStep
import com.example.knockdock.model.ActionType
import com.example.knockdock.model.CardGlassSettings
import com.example.knockdock.model.DockProfile
import com.example.knockdock.model.GestureActionMapping
import com.example.knockdock.model.GestureCategory
import com.example.knockdock.model.GestureDefinition
import com.example.knockdock.model.GestureType
import com.example.knockdock.model.KnockSettings
import com.example.knockdock.model.MacroConcurrencyPolicy
import com.example.knockdock.model.NavigationGlassSettings
import com.example.knockdock.model.ResolvedMapping
import com.example.knockdock.model.StepConditionType
import com.example.knockdock.model.StepFailurePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Single source of truth for persistent profiles, mappings, gestures, actions, and settings.
 */
class KnockDockRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val database: KnockDockDatabase = Room.databaseBuilder(
        context.applicationContext,
        KnockDockDatabase::class.java,
        "knockdock_db"
    ).fallbackToDestructiveMigration().build()

    private val profileDao = database.profileDao()
    private val gestureDao = database.gestureDao()
    private val actionDao = database.actionDao()
    private val mappingDao = database.mappingDao()
    private val executionLogDao = database.executionLogDao()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("knockdock_settings_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettingsFromPrefs())
    val settingsFlow: StateFlow<KnockSettings> = _settingsFlow.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            seedDefaultDataIfEmpty()
        }
    }

    // ---------------- DATA SEEDING ----------------

    private suspend fun seedDefaultDataIfEmpty() {
        val existingProfiles = profileDao.getAllProfiles().first()
        if (existingProfiles.isEmpty()) {
            // 1. Insert default presets
            val defaultGestures = GestureDefinition.defaultPresets().map { it.toEntity() }
            gestureDao.insertGestures(defaultGestures)

            // 2. Insert default actions
            val defaultActions = ActionDefinition.defaultActions().map { it.toEntity() }
            actionDao.insertActions(defaultActions)

            // 3. Insert default profile "Desk Commander"
            val defaultProfile = DockProfile.createDefaultProfile()
            profileDao.insertProfile(
                ProfileEntity(
                    id = defaultProfile.id,
                    name = defaultProfile.name,
                    description = defaultProfile.description,
                    isActive = true,
                    isDefault = true,
                    createdAtEpochMs = defaultProfile.createdAtEpochMs
                )
            )

            // 4. Insert default mappings
            val mappingEntities = defaultProfile.mappings.map { mapping ->
                MappingEntity(
                    id = mapping.id,
                    profileId = defaultProfile.id,
                    gestureId = explodeGestureWithModifier(mapping.gestureId, mapping.requiredModifier),
                    actionId = mapping.actionId,
                    isEnabled = mapping.isEnabled
                )
            }
            mappingDao.insertMappings(mappingEntities)
        }
    }

    // ---------------- PROFILES ----------------

    fun getAllProfiles(): Flow<List<DockProfile>> {
        return combine(
            profileDao.getAllProfiles(),
            mappingDao.getAllMappings()
        ) { profiles, mappings ->
            profiles.map { p ->
                val profileMappings = mappings.filter { it.profileId == p.id }.map { m ->
                    val (rawGestureId, modifier) = parseGestureAndModifier(m.gestureId)
                    GestureActionMapping(
                        id = m.id,
                        gestureId = rawGestureId,
                        actionId = m.actionId,
                        isEnabled = m.isEnabled,
                        requiredModifier = modifier
                    )
                }
                val ratePref = prefs.getString("prof_rate_${p.id}", null)
                val sensPref = if (prefs.contains("prof_sens_${p.id}")) prefs.getFloat("prof_sens_${p.id}", 0.65f) else null
                val onActPref = prefs.getString("prof_onact_${p.id}", null)
                DockProfile(
                    id = p.id,
                    name = p.name,
                    description = p.description,
                    isActive = p.isActive,
                    isDefault = p.isDefault,
                    mappings = profileMappings,
                    preferredSamplingRate = ratePref,
                    preferredSensitivity = sensPref,
                    onActivateActionId = onActPref,
                    createdAtEpochMs = p.createdAtEpochMs
                )
            }
        }
    }

    fun getActiveProfile(): Flow<DockProfile?> {
        return getAllProfiles().map { list -> list.find { it.isActive } ?: list.firstOrNull() }
    }

    suspend fun setActiveProfile(profileId: String) = withContext(Dispatchers.IO) {
        profileDao.setActiveProfile(profileId)
    }

    suspend fun saveProfile(profile: DockProfile) = withContext(Dispatchers.IO) {
        profileDao.insertProfile(
            ProfileEntity(
                id = profile.id,
                name = profile.name,
                description = profile.description,
                isActive = profile.isActive,
                isDefault = profile.isDefault,
                createdAtEpochMs = profile.createdAtEpochMs
            )
        )
        prefs.edit().apply {
            if (profile.preferredSamplingRate != null) putString("prof_rate_${profile.id}", profile.preferredSamplingRate)
            else remove("prof_rate_${profile.id}")
            if (profile.preferredSensitivity != null) putFloat("prof_sens_${profile.id}", profile.preferredSensitivity)
            else remove("prof_sens_${profile.id}")
            if (!profile.onActivateActionId.isNullOrBlank()) putString("prof_onact_${profile.id}", profile.onActivateActionId)
            else remove("prof_onact_${profile.id}")
        }.apply()

        // Refresh mappings
        mappingDao.deleteMappingsByProfileId(profile.id)
        val entities = profile.mappings.map {
            MappingEntity(
                id = it.id,
                profileId = profile.id,
                gestureId = explodeGestureWithModifier(it.gestureId, it.requiredModifier),
                actionId = it.actionId,
                isEnabled = it.isEnabled
            )
        }
        mappingDao.insertMappings(entities)
    }

    suspend fun duplicateProfile(sourceProfileId: String, newName: String) = withContext(Dispatchers.IO) {
        val existing = getAllProfiles().first().find { it.id == sourceProfileId } ?: return@withContext
        val newId = "profile_${UUID.randomUUID().toString().take(8)}"
        val newMappings = existing.mappings.map { m ->
            m.copy(id = "map_${UUID.randomUUID().toString().take(8)}")
        }
        val duplicated = DockProfile(
            id = newId,
            name = newName,
            description = "Duplicated from ${existing.name}",
            isActive = false,
            isDefault = false,
            mappings = newMappings,
            preferredSamplingRate = existing.preferredSamplingRate,
            preferredSensitivity = existing.preferredSensitivity,
            onActivateActionId = existing.onActivateActionId
        )
        saveProfile(duplicated)
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        mappingDao.deleteMappingsByProfileId(profileId)
        profileDao.deleteProfileById(profileId)
    }

    suspend fun restoreDefaultProfile() = withContext(Dispatchers.IO) {
        val defaultProfile = DockProfile.createDefaultProfile()
        saveProfile(defaultProfile)
        setActiveProfile(defaultProfile.id)
    }

    // ---------------- GESTURES ----------------

    fun getAllGestures(): Flow<List<GestureDefinition>> {
        return gestureDao.getAllGestures().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveGesture(gesture: GestureDefinition) = withContext(Dispatchers.IO) {
        gestureDao.insertGesture(gesture.toEntity())
    }

    suspend fun deleteCustomGesture(gestureId: String) = withContext(Dispatchers.IO) {
        gestureDao.deleteCustomGestureById(gestureId)
    }

    // ---------------- ACTIONS ----------------

    fun getAllActions(): Flow<List<ActionDefinition>> {
        return actionDao.getAllActions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveAction(action: ActionDefinition) = withContext(Dispatchers.IO) {
        actionDao.insertAction(action.toEntity())
    }

    suspend fun deleteAction(actionId: String) = withContext(Dispatchers.IO) {
        actionDao.deleteActionById(actionId)
    }

    // ---------------- RESOLVED MAPPINGS ----------------

    fun getResolvedMappingsForActiveProfile(): Flow<List<ResolvedMapping>> {
        return combine(
            getActiveProfile(),
            getAllGestures(),
            getAllActions()
        ) { activeProfile, gestures, actions ->
            if (activeProfile == null) return@combine emptyList()

            activeProfile.mappings.mapNotNull { m ->
                val g = gestures.find { it.id == m.gestureId } ?: return@mapNotNull null
                val a = actions.find { it.id == m.actionId } ?: return@mapNotNull null
                ResolvedMapping(
                    mappingId = m.id,
                    gesture = g,
                    action = a,
                    isEnabled = m.isEnabled,
                    requiredModifier = m.requiredModifier
                )
            }
        }
    }

    suspend fun toggleMapping(mappingId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val all = mappingDao.getAllMappings().first()
        val target = all.find { it.id == mappingId } ?: return@withContext
        mappingDao.insertMapping(target.copy(isEnabled = isEnabled))
    }

    suspend fun addOrUpdateMapping(
        profileId: String,
        gestureId: String,
        actionId: String,
        requiredModifier: String? = null
    ) = withContext(Dispatchers.IO) {
        val encodedGesture = explodeGestureWithModifier(gestureId, requiredModifier)
        val all = mappingDao.getMappingsForProfile(profileId).first()
        val existing = all.find { it.gestureId == encodedGesture }
        val id = existing?.id ?: "map_${UUID.randomUUID().toString().take(8)}"
        mappingDao.insertMapping(
            MappingEntity(
                id = id,
                profileId = profileId,
                gestureId = encodedGesture,
                actionId = actionId,
                isEnabled = true
            )
        )
    }

    suspend fun removeMapping(mappingId: String) = withContext(Dispatchers.IO) {
        mappingDao.deleteMappingById(mappingId)
    }

    // ---------------- CONFLICT DETECTION ----------------

    /**
     * Checks if multiple mappings in the active profile use the same (gestureId, requiredModifier) pair.
     */
    fun detectConflicts(profile: DockProfile): List<String> {
        val conflicts = mutableListOf<String>()
        val gestureCounts = profile.mappings
            .filter { it.isEnabled }
            .groupBy { "${it.gestureId}|${it.requiredModifier.orEmpty()}" }
        for ((key, list) in gestureCounts) {
            if (list.size > 1) {
                val gId = key.substringBefore("|")
                val mod = key.substringAfter("|")
                val modSuffix = if (mod.isNotBlank()) " [modifier: $mod]" else ""
                conflicts.add("Multiple actions assigned to the same gesture ($gId$modSuffix). Only one will execute.")
            }
        }
        return conflicts
    }

    // ---------------- LOGS ----------------

    fun getRecentExecutionLogs(): Flow<List<ExecutionLogEntity>> = executionLogDao.getRecentLogs()

    suspend fun recordExecutionLog(
        actionId: String,
        actionName: String,
        gestureName: String,
        result: ActionExecutionResult
    ) = withContext(Dispatchers.IO) {
        val sanitizedMessage = sanitizeSensitiveMessage(result.message)
        executionLogDao.insertLog(
            ExecutionLogEntity(
                actionId = actionId,
                actionName = actionName,
                gestureName = gestureName,
                success = result.success,
                message = sanitizedMessage,
                executedAtEpochMs = result.executedAtEpochMs,
                isSimulated = result.isSimulated
            )
        )
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        executionLogDao.clearLogs()
    }

    // ---------------- EXPORT / IMPORT ----------------

    fun exportProfileJson(profile: DockProfile): String {
        val json = JSONObject().apply {
            put("version", 1)
            put("id", profile.id)
            put("name", profile.name)
            put("description", profile.description)
            if (profile.preferredSamplingRate != null) {
                put("preferredSamplingRate", profile.preferredSamplingRate)
            }
            if (profile.preferredSensitivity != null) {
                put("preferredSensitivity", profile.preferredSensitivity.toDouble())
            }
            val mapsArr = JSONArray()
            for (m in profile.mappings) {
                mapsArr.put(JSONObject().apply {
                    put("id", m.id)
                    put("gestureId", m.gestureId)
                    put("actionId", m.actionId)
                    put("isEnabled", m.isEnabled)
                    if (!m.requiredModifier.isNullOrBlank()) {
                        put("requiredModifier", m.requiredModifier)
                    }
                })
            }
            put("mappings", mapsArr)
        }
        return json.toString(2)
    }

    suspend fun importProfileJson(jsonString: String): Result<DockProfile> = withContext(Dispatchers.IO) {
        try {
            // Reject suspicious script/code injection attempts in imported JSON
            if (jsonString.contains("<script", ignoreCase = true) ||
                jsonString.contains("javascript:", ignoreCase = true) ||
                jsonString.length > 250_000
            ) {
                return@withContext Result.failure(IllegalArgumentException("Rejected unsafe or oversized JSON payload"))
            }

            val json = JSONObject(jsonString)
            val name = json.optString("name", "Imported Profile").take(80)
            val desc = json.optString("description", "Imported from JSON backup").take(200)
            val newId = "profile_imp_${UUID.randomUUID().toString().take(8)}"
            val prefRate = json.optString("preferredSamplingRate", "").ifBlank { null }
            val prefSens = if (json.has("preferredSensitivity")) json.optDouble("preferredSensitivity", 0.65).toFloat() else null
            val mapsArray = json.optJSONArray("mappings") ?: JSONArray()

            val mappings = mutableListOf<GestureActionMapping>()
            for (i in 0 until mapsArray.length()) {
                val obj = mapsArray.getJSONObject(i)
                val gId = obj.optString("gestureId", "").trim()
                val aId = obj.optString("actionId", "").trim()
                if (gId.isNotEmpty() && aId.isNotEmpty()) {
                    mappings.add(
                        GestureActionMapping(
                            id = "map_${UUID.randomUUID().toString().take(8)}",
                            gestureId = gId,
                            actionId = aId,
                            isEnabled = obj.optBoolean("isEnabled", true),
                            requiredModifier = obj.optString("requiredModifier", "").ifBlank { null }
                        )
                    )
                }
            }

            val imported = DockProfile(
                id = newId,
                name = name,
                description = desc,
                isActive = false,
                isDefault = false,
                mappings = mappings,
                preferredSamplingRate = prefRate,
                preferredSensitivity = prefSens
            )
            saveProfile(imported)
            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- SETTINGS ----------------

    private fun loadSettingsFromPrefs(): KnockSettings {
        return KnockSettings(
            sensitivity = prefs.getFloat("sensitivity", 0.65f),
            baseVibrationThreshold = prefs.getFloat("baseVibrationThreshold", 2.2f),
            cooldownMs = prefs.getLong("cooldownMs", 750L),
            confidenceThreshold = prefs.getFloat("confidenceThreshold", 0.65f),
            samplingRate = prefs.getString("samplingRate", "GAME") ?: "GAME",
            gyroStationaryFilter = prefs.getBoolean("gyroStationaryFilter", true),
            backgroundMonitoringEnabled = prefs.getBoolean("backgroundMonitoringEnabled", true),
            batteryConsciousMode = prefs.getBoolean("batteryConsciousMode", true),
            hapticFeedbackEnabled = prefs.getBoolean("hapticFeedbackEnabled", true),
            soundFeedbackEnabled = prefs.getBoolean("soundFeedbackEnabled", false),
            reducedMotion = prefs.getBoolean("reducedMotion", false),
            visualEffectsQuality = prefs.getString("visualEffectsQuality", "HIGH") ?: "HIGH",
            glassBlurRadius = prefs.getFloat("glassBlurRadius", 18f),
            glassRefractionAmount = prefs.getFloat("glassRefractionAmount", 38f),
            glassRefractionHeight = prefs.getFloat("glassRefractionHeight", 18f),
            glassChromaticAberration = prefs.getBoolean("glassChromaticAberration", true),
            liquidTouchPhysics = prefs.getBoolean("liquidTouchPhysics", true),
            navBlurEnabled = prefs.getBoolean("navBlurEnabled", true),
            navBackgroundOpacity = prefs.getFloat("navBackgroundOpacity", 0.30f),
            navRimHighlightIntensity = prefs.getFloat("navRimHighlightIntensity", 0.58f),
            cardBlurEnabled = prefs.getBoolean("cardBlurEnabled", true),
            cardBlurRadiusDp = prefs.getFloat("cardBlurRadiusDp", 16f),
            cardBackgroundOpacity = prefs.getFloat("cardBackgroundOpacity", 0.46f),
            cardTintColorKey = prefs.getString("cardTintColorKey", "CYAN") ?: "CYAN",
            cardTintIntensity = prefs.getFloat("cardTintIntensity", 0.16f),
            cardRefractionIntensity = prefs.getFloat("cardRefractionIntensity", 0.48f),
            cardRimHighlightIntensity = prefs.getFloat("cardRimHighlightIntensity", 0.50f),
            cardCornerRadiusDp = prefs.getFloat("cardCornerRadiusDp", 24f),
            cardSurfaceContrast = prefs.getFloat("cardSurfaceContrast", 0.50f),
            cardLightweightMode = prefs.getBoolean("cardLightweightMode", false),
            pcControlEnabled = prefs.getBoolean("pcControlEnabled", false),
            pcHost = prefs.getString("pcHost", "192.168.1.100") ?: "192.168.1.100",
            pcPort = prefs.getInt("pcPort", 8989),
            pcAuthToken = prefs.getString("pcAuthToken", "") ?: "",
            pcDeviceName = prefs.getString("pcDeviceName", "My Workstation") ?: "My Workstation",
            isPcPaired = prefs.getBoolean("isPcPaired", false)
        )
    }

    fun updateSettings(newSettings: KnockSettings) {
        prefs.edit()
            .putFloat("sensitivity", newSettings.sensitivity)
            .putFloat("baseVibrationThreshold", newSettings.baseVibrationThreshold)
            .putLong("cooldownMs", newSettings.cooldownMs)
            .putFloat("confidenceThreshold", newSettings.confidenceThreshold)
            .putString("samplingRate", newSettings.samplingRate)
            .putBoolean("gyroStationaryFilter", newSettings.gyroStationaryFilter)
            .putBoolean("backgroundMonitoringEnabled", newSettings.backgroundMonitoringEnabled)
            .putBoolean("batteryConsciousMode", newSettings.batteryConsciousMode)
            .putBoolean("hapticFeedbackEnabled", newSettings.hapticFeedbackEnabled)
            .putBoolean("soundFeedbackEnabled", newSettings.soundFeedbackEnabled)
            .putBoolean("reducedMotion", newSettings.reducedMotion)
            .putString("visualEffectsQuality", newSettings.visualEffectsQuality)
            .putFloat("glassBlurRadius", newSettings.glassBlurRadius)
            .putFloat("glassRefractionAmount", newSettings.glassRefractionAmount)
            .putFloat("glassRefractionHeight", newSettings.glassRefractionHeight)
            .putBoolean("glassChromaticAberration", newSettings.glassChromaticAberration)
            .putBoolean("liquidTouchPhysics", newSettings.liquidTouchPhysics)
            .putBoolean("navBlurEnabled", newSettings.navBlurEnabled)
            .putFloat("navBackgroundOpacity", newSettings.navBackgroundOpacity)
            .putFloat("navRimHighlightIntensity", newSettings.navRimHighlightIntensity)
            .putBoolean("cardBlurEnabled", newSettings.cardBlurEnabled)
            .putFloat("cardBlurRadiusDp", newSettings.cardBlurRadiusDp)
            .putFloat("cardBackgroundOpacity", newSettings.cardBackgroundOpacity)
            .putString("cardTintColorKey", newSettings.cardTintColorKey)
            .putFloat("cardTintIntensity", newSettings.cardTintIntensity)
            .putFloat("cardRefractionIntensity", newSettings.cardRefractionIntensity)
            .putFloat("cardRimHighlightIntensity", newSettings.cardRimHighlightIntensity)
            .putFloat("cardCornerRadiusDp", newSettings.cardCornerRadiusDp)
            .putFloat("cardSurfaceContrast", newSettings.cardSurfaceContrast)
            .putBoolean("cardLightweightMode", newSettings.cardLightweightMode)
            .putBoolean("pcControlEnabled", newSettings.pcControlEnabled)
            .putString("pcHost", newSettings.pcHost)
            .putInt("pcPort", newSettings.pcPort)
            .putString("pcAuthToken", newSettings.pcAuthToken)
            .putString("pcDeviceName", newSettings.pcDeviceName)
            .putBoolean("isPcPaired", newSettings.isPcPaired)
            .apply()

        _settingsFlow.value = newSettings
    }

    /**
     * Updates only the independent CardGlassSettings without altering any NavigationGlassSettings.
     */
    fun updateCardGlassSettings(cardSettings: CardGlassSettings) {
        updateSettings(_settingsFlow.value.withCardGlassSettings(cardSettings))
    }

    /**
     * Resets CardGlassSettings to sensible factory defaults without affecting NavigationGlassSettings.
     */
    fun resetCardGlassSettingsToDefault() {
        updateCardGlassSettings(CardGlassSettings())
    }

    /**
     * Updates only the independent NavigationGlassSettings without altering any CardGlassSettings.
     */
    fun updateNavigationGlassSettings(navSettings: NavigationGlassSettings) {
        updateSettings(_settingsFlow.value.withNavigationGlassSettings(navSettings))
    }

    /**
     * Resets NavigationGlassSettings to sensible factory defaults without affecting CardGlassSettings.
     */
    fun resetNavigationGlassSettingsToDefault() {
        updateNavigationGlassSettings(NavigationGlassSettings())
    }

    // ---------------- CONVERSION EXTENSIONS ----------------

    private fun explodeGestureWithModifier(gestureId: String, modifier: String?): String {
        return if (modifier.isNullOrBlank()) gestureId else "$gestureId@@$modifier"
    }

    private fun parseGestureAndModifier(encoded: String): Pair<String, String?> {
        return if (encoded.contains("@@")) {
            val parts = encoded.split("@@", limit = 2)
            parts[0] to parts.getOrNull(1)?.ifBlank { null }
        } else {
            encoded to null
        }
    }

    private fun sanitizeSensitiveMessage(msg: String): String {
        var result = msg
        // Scrub Bearer tokens or long auth tokens if accidentally included
        result = result.replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [REDACTED]")
        result = result.replace(Regex("token=[^&\\s]+"), "token=[REDACTED]")
        return result
    }

    private fun GestureDefinition.toEntity() = GestureEntity(
        id = id,
        name = name,
        description = description,
        type = type.name,
        category = category.name,
        expectedImpacts = expectedImpacts,
        minIntervalMs = minIntervalMs,
        maxIntervalMs = maxIntervalMs,
        minConfidence = minConfidence,
        cooldownMs = cooldownMs,
        isSystemPreset = isSystemPreset,
        trainingTrialCount = trainingTrialCount,
        templateIntervalsJson = JSONArray(templateIntervalsMs).toString()
    )

    private fun GestureEntity.toDomain(): GestureDefinition {
        val intervals = mutableListOf<Long>()
        try {
            val arr = JSONArray(templateIntervalsJson)
            for (i in 0 until arr.length()) {
                intervals.add(arr.getLong(i))
            }
        } catch (_: Exception) {}

        return GestureDefinition(
            id = id,
            name = name,
            description = description,
            type = GestureType.fromId(type),
            category = GestureCategory.entries.find { it.name == category } ?: GestureCategory.CUSTOM,
            expectedImpacts = expectedImpacts,
            minIntervalMs = minIntervalMs,
            maxIntervalMs = maxIntervalMs,
            minConfidence = minConfidence,
            cooldownMs = cooldownMs,
            isSystemPreset = isSystemPreset,
            trainingTrialCount = trainingTrialCount,
            templateIntervalsMs = intervals
        )
    }

    private fun ActionDefinition.toEntity(): ActionEntity {
        val extendedParams = parameters.toMutableMap()
        if (isReversibleToggle) extendedParams["__isReversibleToggle"] = "true"
        if (reverseActionType != null) extendedParams["__reverseActionType"] = reverseActionType.name
        if (reverseParameters.isNotEmpty()) extendedParams["__reverseParameters"] = JSONObject(reverseParameters).toString()
        if (concurrencyPolicy != MacroConcurrencyPolicy.SKIP_IF_RUNNING) {
            extendedParams["__concurrencyPolicy"] = concurrencyPolicy.name
        }

        val paramJson = JSONObject(extendedParams as Map<*, *>).toString()
        val stepsArr = JSONArray()
        for (step in steps) {
            stepsArr.put(JSONObject().apply {
                put("stepId", step.stepId)
                put("type", step.type.name)
                put("title", step.title)
                put("parameters", JSONObject(step.parameters as Map<*, *>))
                put("delayAfterMs", step.delayAfterMs)
                put("conditionType", step.conditionType.name)
                put("conditionValue", step.conditionValue)
                put("onFailurePolicy", step.onFailurePolicy.name)
                put("maxRetries", step.maxRetries)
                put("retryDelayMs", step.retryDelayMs)
                if (step.recoveryActionType != null) {
                    put("recoveryActionType", step.recoveryActionType.name)
                    put("recoveryParameters", JSONObject(step.recoveryParameters as Map<*, *>))
                }
                if (step.groupId != null) {
                    put("groupId", step.groupId)
                }
            })
        }
        return ActionEntity(
            id = id,
            name = name,
            description = description,
            type = type.name,
            isEnabled = isEnabled,
            parametersJson = paramJson,
            stepsJson = stepsArr.toString(),
            createdAtEpochMs = createdAtEpochMs
        )
    }

    private fun ActionEntity.toDomain(): ActionDefinition {
        val rawParams = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(parametersJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                rawParams[k] = obj.getString(k)
            }
        } catch (_: Exception) {}

        val isReversible = rawParams.remove("__isReversibleToggle") == "true"
        val revTypeStr = rawParams.remove("__reverseActionType")
        val revType = revTypeStr?.let { ActionType.fromId(it) }
        val revParamsStr = rawParams.remove("__reverseParameters")
        val revParams = mutableMapOf<String, String>()
        if (!revParamsStr.isNullOrBlank()) {
            try {
                val ro = JSONObject(revParamsStr)
                val rKeys = ro.keys()
                while (rKeys.hasNext()) {
                    val rk = rKeys.next()
                    revParams[rk] = ro.getString(rk)
                }
            } catch (_: Exception) {}
        }
        val concurrencyStr = rawParams.remove("__concurrencyPolicy")
        val concurrency = concurrencyStr?.let { MacroConcurrencyPolicy.fromId(it) }
            ?: MacroConcurrencyPolicy.SKIP_IF_RUNNING

        val stepList = mutableListOf<ActionStep>()
        try {
            val arr = JSONArray(stepsJson)
            for (i in 0 until arr.length()) {
                val sObj = arr.getJSONObject(i)
                val sParams = mutableMapOf<String, String>()
                val pObj = sObj.optJSONObject("parameters")
                if (pObj != null) {
                    val pKeys = pObj.keys()
                    while (pKeys.hasNext()) {
                        val pk = pKeys.next()
                        sParams[pk] = pObj.getString(pk)
                    }
                }
                val recParams = mutableMapOf<String, String>()
                val recObj = sObj.optJSONObject("recoveryParameters")
                if (recObj != null) {
                    val recKeys = recObj.keys()
                    while (recKeys.hasNext()) {
                        val rk = recKeys.next()
                        recParams[rk] = recObj.getString(rk)
                    }
                }
                val recTypeStr = sObj.optString("recoveryActionType", "")
                stepList.add(
                    ActionStep(
                        stepId = sObj.getString("stepId"),
                        type = ActionType.fromId(sObj.getString("type")),
                        title = sObj.optString("title", ""),
                        parameters = sParams,
                        delayAfterMs = sObj.optLong("delayAfterMs", 200L),
                        conditionType = StepConditionType.fromId(sObj.optString("conditionType", "NONE")),
                        conditionValue = sObj.optString("conditionValue", ""),
                        onFailurePolicy = StepFailurePolicy.fromId(sObj.optString("onFailurePolicy", "CONTINUE")),
                        maxRetries = sObj.optInt("maxRetries", 0),
                        retryDelayMs = sObj.optLong("retryDelayMs", 250L),
                        recoveryActionType = if (recTypeStr.isNotBlank()) ActionType.fromId(recTypeStr) else null,
                        recoveryParameters = recParams,
                        groupId = sObj.optString("groupId", "").ifBlank { null }
                    )
                )
            }
        } catch (_: Exception) {}

        return ActionDefinition(
            id = id,
            name = name,
            description = description,
            type = ActionType.fromId(type),
            isEnabled = isEnabled,
            parameters = rawParams,
            steps = stepList,
            isReversibleToggle = isReversible,
            reverseActionType = revType,
            reverseParameters = revParams,
            concurrencyPolicy = concurrency,
            createdAtEpochMs = createdAtEpochMs
        )
    }
}
