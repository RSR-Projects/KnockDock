package com.example.knockdock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knockdock.model.DockProfile
import com.example.knockdock.service.MonitoringController
import com.example.knockdock.ui.theme.LiquidGlassBottomSheet
import com.example.knockdock.ui.theme.LiquidGlassCard
import com.example.knockdock.ui.theme.LiquidGlassDialog
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlassSwitch
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LiquidStatusPill
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ProfilesScreen(
    controller: MonitoringController
) {
    val profiles by controller.repository.getAllProfiles().collectAsState(initial = emptyList())
    val activeProfile by controller.repository.getActiveProfile().collectAsState(initial = null)
    val mappings by controller.repository.getResolvedMappingsForActiveProfile().collectAsState(initial = emptyList())
    val gestures by controller.repository.getAllGestures().collectAsState(initial = emptyList())
    val actions by controller.repository.getAllActions().collectAsState(initial = emptyList())
    val settings by controller.repository.settingsFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Dialog States
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileDesc by remember { mutableStateOf("") }

    var editingProfile by remember { mutableStateOf<DockProfile?>(null) }
    var editProfileName by remember { mutableStateOf("") }
    var editProfileDesc by remember { mutableStateOf("") }

    var showMappingDialog by remember { mutableStateOf(false) }
    var editingMappingId by remember { mutableStateOf<String?>(null) } // null = creating new
    var selectedGestureId by remember { mutableStateOf("") }
    var selectedActionId by remember { mutableStateOf("") }
    var selectedRequiredModifier by remember { mutableStateOf<String?>(null) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf<String?>(null) }

    // Check for conflicts in active profile
    val conflicts = activeProfile?.let { controller.repository.detectConflicts(it) } ?: emptyList()

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
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = "Profiles & Mappings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Desk Automation Profiles & Bindings",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassIconButton(
                        onClick = { showImportDialog = true },
                        size = 40.dp,
                        accentColor = CyanGlow
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = "Import Profile",
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    LiquidGlowButton(
                        text = "+ New",
                        onClick = {
                            newProfileName = ""
                            newProfileDesc = ""
                            showCreateDialog = true
                        },
                        accentColor = CyanGlow,
                        modifier = Modifier.testTag("create_profile_button")
                    )
                }
            }
        }

        // Active Profile Conflict Warning
        if (conflicts.isNotEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = AmberAlert,
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAlert)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mapping Conflict Detected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberAlert
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        conflicts.forEach { conflict ->
                            Text(
                                text = "• $conflict",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Profiles List
        item {
            Text(
                text = "Available Profiles (${profiles.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(profiles) { profile ->
            val isActive = profile.isActive
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_card_${profile.id}"),
                accentColor = if (isActive) CyanGlow else null,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion,
                onClick = {
                    scope.launch {
                        controller.repository.setActiveProfile(profile.id)
                    }
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Row: Profile Name + Status Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isActive) {
                                LiquidStatusPill(text = "Active", statusColor = MintSuccess)
                            }
                            if (profile.isDefault) {
                                LiquidStatusPill(text = "Default", statusColor = IndigoPulse)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Row: Description + Action Icon Buttons
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
                                text = profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${profile.mappings.size} gesture mappings • Tap to activate",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) CyanGlow else TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rename/Edit profile
                            LiquidGlassIconButton(
                                onClick = {
                                    editingProfile = profile
                                    editProfileName = profile.name
                                    editProfileDesc = profile.description
                                },
                                size = 34.dp,
                                accentColor = CyanGlow
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = CyanGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Duplicate
                            LiquidGlassIconButton(
                                onClick = {
                                    scope.launch {
                                        controller.repository.duplicateProfile(profile.id, "${profile.name} (Copy)")
                                    }
                                },
                                size = 34.dp,
                                accentColor = IndigoPulse
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Export JSON
                            LiquidGlassIconButton(
                                onClick = {
                                    val json = controller.repository.exportProfileJson(profile)
                                    showExportDialog = json
                                },
                                size = 34.dp,
                                accentColor = IndigoPulse
                            ) {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = "Export JSON",
                                    tint = IndigoPulse,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Delete
                            if (!profile.isDefault && profiles.size > 1) {
                                LiquidGlassIconButton(
                                    onClick = {
                                        scope.launch {
                                            controller.repository.deleteProfile(profile.id)
                                        }
                                    },
                                    size = 34.dp,
                                    accentColor = RoseError
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = RoseError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Profile's Mappings Section
        item {
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
                        text = "Mappings: ${activeProfile?.name ?: "Active Profile"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Bind knock patterns to macro actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LiquidGlowButton(
                    text = "+ Mapping",
                    onClick = {
                        editingMappingId = null
                        selectedGestureId = gestures.firstOrNull()?.id ?: ""
                        selectedActionId = actions.firstOrNull()?.id ?: ""
                        selectedRequiredModifier = null
                        showMappingDialog = true
                    },
                    accentColor = IndigoPulse
                )
            }
        }

        if (mappings.isEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Text(
                        text = "No mappings configured for this profile yet. Tap '+ Mapping' above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            items(mappings) { m ->
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    visualEffectsQuality = settings.visualEffectsQuality,
                    reducedMotion = settings.reducedMotion
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top Row: Gesture (+ Modifier) -> Action Title
                        val modTag = if (!m.requiredModifier.isNullOrBlank()) " [${m.requiredModifier}]" else ""
                        Text(
                            text = "${m.gesture.name}$modTag  →  ${m.action.name}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Bottom Row: Description + Edit / LiquidGlassSwitch / Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = m.action.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Edit mapping button
                                LiquidGlassIconButton(
                                    onClick = {
                                        editingMappingId = m.mappingId
                                        selectedGestureId = m.gesture.id
                                        selectedActionId = m.action.id
                                        selectedRequiredModifier = m.requiredModifier
                                        showMappingDialog = true
                                    },
                                    size = 34.dp,
                                    accentColor = CyanGlow
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Mapping",
                                        tint = CyanGlow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Liquid Glass Switch enable/disable
                                LiquidGlassSwitch(
                                    checked = m.isEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            controller.repository.toggleMapping(m.mappingId, checked)
                                        }
                                    },
                                    accentColor = CyanGlow
                                )

                                // Remove mapping
                                LiquidGlassIconButton(
                                    onClick = {
                                        scope.launch {
                                            controller.repository.removeMapping(m.mappingId)
                                        }
                                    },
                                    size = 34.dp,
                                    accentColor = RoseError
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Mapping",
                                        tint = RoseError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Restore Factory Defaults
        item {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        controller.repository.restoreDefaultProfile()
                    }
                },
                accentColor = CyanGlow,
                visualEffectsQuality = settings.visualEffectsQuality,
                reducedMotion = settings.reducedMotion
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CyanGlow)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Restore Factory Default Profile",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Reset Desk Commander with default media & flashlight bindings",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }

    // Liquid Glass Dialog: Create Profile
    if (showCreateDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showCreateDialog = false },
            title = "Create New Profile",
            content = {
                Column {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile Name (e.g. Gaming Setup)") },
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProfileDesc,
                        onValueChange = { newProfileDesc = it },
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
                }
            },
            confirmButton = {
                LiquidGlowButton(
                    text = "Create",
                    accentColor = CyanGlow,
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            val newProf = DockProfile(
                                id = "profile_${UUID.randomUUID().toString().take(8)}",
                                name = newProfileName.trim(),
                                description = newProfileDesc.trim().ifEmpty { "Custom automation profile" },
                                isActive = false,
                                isDefault = false,
                                mappings = emptyList()
                            )
                            scope.launch {
                                controller.repository.saveProfile(newProf)
                                showCreateDialog = false
                            }
                        }
                    }
                )
            },
            dismissButton = {
                LiquidGlowButton(
                    text = "Cancel",
                    accentColor = Color.Gray,
                    onClick = { showCreateDialog = false }
                )
            }
        )
    }

    // Liquid Glass Dialog: Edit / Rename Profile
    if (editingProfile != null) {
        LiquidGlassDialog(
            onDismissRequest = { editingProfile = null },
            title = "Edit Profile Details",
            content = {
                Column {
                    OutlinedTextField(
                        value = editProfileName,
                        onValueChange = { editProfileName = it },
                        label = { Text("Profile Name") },
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editProfileDesc,
                        onValueChange = { editProfileDesc = it },
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
                }
            },
            confirmButton = {
                LiquidGlowButton(
                    text = "Save Changes",
                    accentColor = CyanGlow,
                    onClick = {
                        if (editProfileName.isNotBlank()) {
                            val updated = editingProfile!!.copy(
                                name = editProfileName.trim(),
                                description = editProfileDesc.trim()
                            )
                            scope.launch {
                                controller.repository.saveProfile(updated)
                                editingProfile = null
                            }
                        }
                    }
                )
            },
            dismissButton = {
                LiquidGlowButton(
                    text = "Cancel",
                    accentColor = Color.Gray,
                    onClick = { editingProfile = null }
                )
            }
        )
    }

    // Liquid Glass Bottom Sheet: Add or Edit Mapping
    if (showMappingDialog && activeProfile != null) {
        LiquidGlassBottomSheet(
            onDismissRequest = { showMappingDialog = false },
            title = if (editingMappingId != null) "Edit Gesture Mapping" else "Add Gesture Mapping",
            subtitle = "Bind a calibrated desk knock gesture to a macro action"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "1. Select Trigger Gesture",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow
                )
                Spacer(modifier = Modifier.height(6.dp))

                gestures.forEach { g ->
                    val isSelected = selectedGestureId == g.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedRectangle(14.dp))
                            .background(if (isSelected) CyanGlow.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                            .border(
                                1.dp,
                                if (isSelected) CyanGlow else Color.White.copy(alpha = 0.12f),
                                RoundedRectangle(14.dp)
                            )
                            .clickable { selectedGestureId = g.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) CyanGlow else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = g.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${g.expectedImpacts}x impact(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "2. Select Action to Execute",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = IndigoPulse
                )
                Spacer(modifier = Modifier.height(6.dp))

                actions.forEach { a ->
                    val isSelected = selectedActionId == a.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedRectangle(14.dp))
                            .background(if (isSelected) IndigoPulse.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                            .border(
                                1.dp,
                                if (isSelected) IndigoPulse else Color.White.copy(alpha = 0.12f),
                                RoundedRectangle(14.dp)
                            )
                            .clickable { selectedActionId = a.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) IndigoPulse else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = a.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = a.type.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "3. Optional Gesture Modifier Key",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MintSuccess
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(null to "None", "MOD_A" to "MOD_A", "MOD_SHIFT" to "SHIFT", "MOD_MUSIC" to "MUSIC").forEach { (modKey, label) ->
                        val isModSelected = selectedRequiredModifier == modKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedRectangle(12.dp))
                                .background(if (isModSelected) MintSuccess.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    1.dp,
                                    if (isModSelected) MintSuccess else Color.White.copy(alpha = 0.12f),
                                    RoundedRectangle(12.dp)
                                )
                                .clickable { selectedRequiredModifier = modKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isModSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isModSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlowButton(
                        text = "Cancel",
                        accentColor = Color.Gray,
                        onClick = { showMappingDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    LiquidGlowButton(
                        text = if (editingMappingId != null) "Update Mapping" else "Bind Mapping",
                        accentColor = CyanGlow,
                        onClick = {
                            if (selectedGestureId.isNotBlank() && selectedActionId.isNotBlank()) {
                                scope.launch {
                                    if (editingMappingId != null) {
                                        controller.repository.removeMapping(editingMappingId!!)
                                    }
                                    controller.repository.addOrUpdateMapping(
                                        profileId = activeProfile!!.id,
                                        gestureId = selectedGestureId,
                                        actionId = selectedActionId,
                                        requiredModifier = selectedRequiredModifier
                                    )
                                    showMappingDialog = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Liquid Glass Dialog: Export Profile
    if (showExportDialog != null) {
        LiquidGlassDialog(
            onDismissRequest = { showExportDialog = null },
            title = "Export Profile JSON",
            content = {
                Column {
                    Text(
                        text = "Copy this JSON payload to backup or transfer this profile:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedRectangle(12.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedRectangle(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = showExportDialog!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanGlow
                        )
                    }
                }
            },
            confirmButton = {
                LiquidGlowButton(
                    text = "Copy JSON",
                    accentColor = CyanGlow,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(showExportDialog!!))
                        showExportDialog = null
                    }
                )
            },
            dismissButton = {
                LiquidGlowButton(
                    text = "Close",
                    accentColor = Color.Gray,
                    onClick = { showExportDialog = null }
                )
            }
        )
    }

    // Liquid Glass Dialog: Import Profile
    if (showImportDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showImportDialog = false },
            title = "Import Profile JSON",
            content = {
                Column {
                    Text(
                        text = "Paste JSON profile data below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        placeholder = { Text("{ \"name\": \"...\", \"mappings\": [...] }") }
                    )
                }
            },
            confirmButton = {
                LiquidGlowButton(
                    text = "Import",
                    accentColor = CyanGlow,
                    onClick = {
                        if (importJsonText.isNotBlank()) {
                            scope.launch {
                                controller.repository.importProfileJson(importJsonText)
                                showImportDialog = false
                            }
                        }
                    }
                )
            },
            dismissButton = {
                LiquidGlowButton(
                    text = "Cancel",
                    accentColor = Color.Gray,
                    onClick = { showImportDialog = false }
                )
            }
        )
    }
}
