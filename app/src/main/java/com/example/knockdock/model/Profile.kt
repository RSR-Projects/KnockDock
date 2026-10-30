package com.example.knockdock.model

/**
 * A profile binding a set of gestures to actions, with optional monitoring preferences
 * and activation macro hooks for Desk Mode Toggle.
 */
data class DockProfile(
    val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean = false,
    val isDefault: Boolean = false,
    val mappings: List<GestureActionMapping> = emptyList(),
    val preferredSamplingRate: String? = null,
    val preferredSensitivity: Float? = null,
    val onActivateActionId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        val DEFAULT_PROFILE_ID = "profile_desk_commander"
        val FOCUS_PROFILE_ID = "profile_focus_guard"
        val MUSIC_PROFILE_ID = "profile_music_studio"
        val GAMING_PROFILE_ID = "profile_gaming_dock"
        val NIGHT_PROFILE_ID = "profile_nightstand"

        fun createDefaultProfile(): DockProfile {
            return DockProfile(
                id = DEFAULT_PROFILE_ID,
                name = "Desk Commander",
                description = "Default desk automation profile for media & productivity",
                isActive = true,
                isDefault = true,
                mappings = listOf(
                    GestureActionMapping(
                        id = "mapping_single_knock",
                        gestureId = GestureDefinition.PRESET_SINGLE.id,
                        actionId = ActionDefinition.DEFAULT_MEDIA_PLAY_PAUSE.id,
                        isEnabled = true
                    ),
                    GestureActionMapping(
                        id = "mapping_double_knock",
                        gestureId = GestureDefinition.PRESET_DOUBLE.id,
                        actionId = ActionDefinition.DEFAULT_MEDIA_NEXT.id,
                        isEnabled = true
                    ),
                    GestureActionMapping(
                        id = "mapping_triple_knock",
                        gestureId = GestureDefinition.PRESET_TRIPLE.id,
                        actionId = ActionDefinition.DEFAULT_FLASHLIGHT.id,
                        isEnabled = true
                    )
                )
            )
        }
    }
}

/**
 * Single mapping between a recognized gesture and a triggered action, with optional
 * modifier requirement (e.g. "MOD_A", "MOD_SHIFT", "MOD_MUSIC").
 */
data class GestureActionMapping(
    val id: String,
    val gestureId: String,
    val actionId: String,
    val isEnabled: Boolean = true,
    val requiredModifier: String? = null
)

/**
 * Full resolved mapping combining gesture metadata and action metadata for easy UI display.
 */
data class ResolvedMapping(
    val mappingId: String,
    val gesture: GestureDefinition,
    val action: ActionDefinition,
    val isEnabled: Boolean,
    val requiredModifier: String? = null
)
