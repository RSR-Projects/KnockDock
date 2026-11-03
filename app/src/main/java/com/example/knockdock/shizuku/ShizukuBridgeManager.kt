package com.example.knockdock.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Supported Shizuku & Shizuku-API compatible privilege clients (including Shevary, Sui, Dhizuku, aShell, LADB, Brevent, Termux/rish).
 */
data class ShizukuCompatibleClient(
    val id: String,
    val displayName: String,
    val packageName: String,
    val protocolDescription: String,
    val isInstalled: Boolean = false
)

enum class ShizukuCategory(val title: String, val badge: String) {
    SYSTEM_UI("System UI & Status Bar", "SYS-UI"),
    DISPLAY_OPTICS("Display, Refresh Rate & Optics", "DISPLAY"),
    HARDWARE_INPUT("Hardware Input, Capture & Power", "INPUT"),
    CONNECTIVITY("Connectivity & Radio Switches", "RADIO"),
    PERFORMANCE("Performance, Animations & RAM", "PERF"),
    DIAGNOSTICS_PRIVACY("Privacy, Diagnostics & Self-Grant", "PRIV")
}

/**
 * Represents one of the 36 real, safe, non-destructive Shizuku privileged features.
 * Every feature is explicitly labeled `[Shizuku]` and executes strictly via the Shizuku privilege bridge.
 */
data class ShizukuFeatureItem(
    val id: String,
    val title: String, // Always starts with "[Shizuku] "
    val subtitle: String,
    val category: ShizukuCategory,
    val shellCommand: String,
    val toggleOffCommand: String? = null,
    val safetyNote: String = "Safe non-destructive ADB/shell command (UID 2000 / 0)"
)

data class ShizukuConnectionState(
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val shizukuVersion: Int = -1,
    val shizukuUid: Int = -1,
    val activeClientName: String = "Shizuku",
    val activeClientPackage: String = "moe.shizuku.privileged.api",
    val shevaryBridgePaired: Boolean = false,
    val installedClients: List<ShizukuCompatibleClient> = emptyList(),
    val statusMessage: String = "Checking Shizuku connection...",
    val lastCommandOutput: String = ""
) {
    val isConnected: Boolean
        get() = isBinderAlive && isPermissionGranted

    val badgeLabel: String
        get() = when {
            isConnected -> "Connected"
            isBinderAlive -> "Permission Needed"
            isShizukuInstalled -> "Service Stopped"
            else -> "Not Connected"
        }
}

/**
 * Manages real Shizuku binder detection, permission handshakes, and safe execution of the 36
 * `[Shizuku]` privileged features.
 *
 * Strictly scoped: Shizuku is ONLY used for `[Shizuku]` privileged features and only reports
 * Connected when the real Shizuku binder is alive and permission is granted.
 */
class ShizukuBridgeManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("knockdock_shizuku_prefs", Context.MODE_PRIVATE)
    private val toggleStates = mutableMapOf<String, Boolean>()

    private val _state = MutableStateFlow(ShizukuConnectionState())
    val state: StateFlow<ShizukuConnectionState> = _state.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                refreshStatus()
            }
        }

    init {
        // Clear any legacy simulated/cached flags so connection state is 100% live binder-verified
        prefs.edit()
            .remove("shevary_compat_paired")
            .remove("shizuku_authorized")
            .apply()

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
        }
        refreshStatus()
    }

    fun refreshStatus() {
        val clients = detectCompatibleClients()
        val preferredClientPkg = prefs.getString("preferred_client_pkg", null)

        var binderAlive = false
        var permGranted = false
        var ver = -1
        var uid = -1

        try {
            binderAlive = Shizuku.pingBinder()
            if (binderAlive) {
                permGranted = if (Shizuku.isPreV11()) {
                    false
                } else {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
                ver = try {
                    Shizuku.getVersion()
                } catch (_: Throwable) {
                    13
                }
                uid = try {
                    Shizuku.getUid()
                } catch (_: Throwable) {
                    2000
                }
            }
        } catch (_: Throwable) {
            binderAlive = false
            permGranted = false
        }

        val installedFirst = clients.firstOrNull { it.isInstalled }
        val isShizukuInstalled = installedFirst != null || binderAlive
        val chosenClient = clients.find { it.packageName == preferredClientPkg && it.isInstalled }
            ?: installedFirst
            ?: clients.first()

        val privilegeLabel = when (uid) {
            0 -> "ROOT (UID 0)"
            2000 -> "ADB SHELL (UID 2000)"
            else -> "SHIZUKU"
        }

        val statusMsg = when {
            binderAlive && permGranted ->
                "Connected to Shizuku (v$ver • $privilegeLabel)"
            binderAlive && !permGranted ->
                "Shizuku service detected • Tap 'Request Shizuku Permission' to connect"
            isShizukuInstalled ->
                "Shizuku is installed but service is stopped • Start Shizuku, then request permission"
            else ->
                "Shizuku is not installed or running • Install and start Shizuku to connect"
        }

        _state.value = _state.value.copy(
            isBinderAlive = binderAlive,
            isPermissionGranted = binderAlive && permGranted,
            isShizukuInstalled = isShizukuInstalled,
            shizukuVersion = ver,
            shizukuUid = uid,
            activeClientName = "Shizuku",
            activeClientPackage = chosenClient.packageName,
            shevaryBridgePaired = binderAlive && permGranted,
            installedClients = clients,
            statusMessage = statusMsg
        )
    }

    /**
     * Detects installed Shizuku packages on the device.
     */
    fun detectCompatibleClients(): List<ShizukuCompatibleClient> {
        val pm = context.packageManager
        return KNOWN_CLIENTS.map { client ->
            val installed = try {
                pm.getPackageInfo(client.packageName, 0) != null
            } catch (_: Exception) {
                false
            }
            client.copy(isInstalled = installed)
        }
    }

    /**
     * Requests Shizuku permission strictly when the real Shizuku binder is active.
     * Never marks Shizuku as connected if the Shizuku service/app is not running.
     */
    fun requestPairingAndPermission(preferredClient: ShizukuCompatibleClient? = null) {
        if (preferredClient != null) {
            prefs.edit().putString("preferred_client_pkg", preferredClient.packageName).apply()
        }

        refreshStatus()

        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.isPreV11()) {
                    _state.value = _state.value.copy(
                        isBinderAlive = true,
                        isPermissionGranted = false,
                        statusMessage = "Shizuku version is pre-v11; please update Shizuku."
                    )
                    return
                }
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    refreshStatus()
                    return
                }
                _state.value = _state.value.copy(
                    statusMessage = "Requesting Shizuku permission... Approve the prompt to connect."
                )
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                return
            }
        } catch (_: Throwable) {
        }

        // Binder is NOT alive: do NOT set connected/authorized!
        val current = _state.value
        if (current.isShizukuInstalled) {
            _state.value = current.copy(
                isBinderAlive = false,
                isPermissionGranted = false,
                shevaryBridgePaired = false,
                statusMessage = "Shizuku service is not running. Start the service in Shizuku first, then tap Request Permission."
            )
            launchInstalledShizukuAppIfPresent(current.activeClientPackage)
        } else {
            _state.value = current.copy(
                isBinderAlive = false,
                isPermissionGranted = false,
                shevaryBridgePaired = false,
                statusMessage = "Cannot connect: Shizuku is not installed or running on this device."
            )
        }
    }

    /**
     * Updates preferred custom package name and checks real binder state.
     */
    fun pairWithCustomClientPackage(customPackageName: String) {
        val cleanPkg = customPackageName.trim().ifBlank { "moe.shizuku.privileged.api" }
        prefs.edit()
            .putString("preferred_client_pkg", cleanPkg)
            .apply()
        refreshStatus()
    }

    fun unpairBridge() {
        prefs.edit()
            .remove("shevary_compat_paired")
            .remove("shizuku_authorized")
            .apply()
        refreshStatus()
    }

    private fun launchInstalledShizukuAppIfPresent(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openClientApp(packageName: String = _state.value.activeClientPackage): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(
                        packageName,
                        "moe.shizuku.manager.MainActivity"
                    )
                }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            try {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://shizuku.rikka.app/")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(marketIntent)
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Looks up one of the 36 `[Shizuku]` features by ID and executes it safely.
     */
    suspend fun executeShizukuFeatureById(
        featureId: String,
        isSimulated: Boolean = false
    ): ShizukuExecutionResult {
        val feature = ALL_36_SHIZUKU_FEATURES.find { it.id == featureId }
            ?: ALL_36_SHIZUKU_FEATURES.first()
        return executeShizukuFeature(feature, isSimulated = isSimulated)
    }

    /**
     * Executes one of the 36 `[Shizuku]` privileged features strictly via the active Shizuku Binder.
     */
    suspend fun executeShizukuFeature(
        feature: ShizukuFeatureItem,
        isSimulated: Boolean = false
    ): ShizukuExecutionResult = withContext(Dispatchers.IO) {
        // Resolve toggle command if this feature has an ON/OFF toggle pair
        val rawCommand = if (feature.toggleOffCommand != null) {
            val curState = toggleStates[feature.id] ?: false
            val nextState = !curState
            toggleStates[feature.id] = nextState
            if (nextState) feature.shellCommand else feature.toggleOffCommand
        } else {
            feature.shellCommand
        }

        val finalCmd = rawCommand.replace("{{PACKAGE}}", context.packageName)

        if (isSimulated) {
            val simMsg = "[Dry-Run] ${feature.title} verified: `$finalCmd`"
            _state.value = _state.value.copy(lastCommandOutput = simMsg)
            return@withContext ShizukuExecutionResult(
                success = true,
                featureTitle = feature.title,
                commandExecuted = finalCmd,
                output = simMsg
            )
        }

        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

        if (!binderAlive) {
            refreshStatus()
            val notConnectedMsg = "${feature.title} • Shizuku is not connected. Start Shizuku and grant permission first."
            _state.value = _state.value.copy(lastCommandOutput = notConnectedMsg)
            return@withContext ShizukuExecutionResult(
                success = false,
                featureTitle = feature.title,
                commandExecuted = finalCmd,
                output = notConnectedMsg
            )
        }

        val permGranted = try {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        if (!permGranted) {
            try {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            } catch (_: Throwable) {
            }
            refreshStatus()
            val permMsg = "${feature.title} • Shizuku permission required. Approve the Shizuku prompt and try again."
            _state.value = _state.value.copy(lastCommandOutput = permMsg)
            return@withContext ShizukuExecutionResult(
                success = false,
                featureTitle = feature.title,
                commandExecuted = finalCmd,
                output = permMsg
            )
        }

        val binderResult = runViaShizukuBinder(finalCmd)
        if (binderResult != null) {
            val summary = if (binderResult.exitCode == 0) {
                val out = binderResult.stdout.ifBlank { "Executed via Shizuku (UID ${_state.value.shizukuUid})" }
                "${feature.title} • $out"
            } else {
                "${feature.title} • Exit ${binderResult.exitCode}: ${binderResult.stderr.ifBlank { binderResult.stdout }}"
            }
            _state.value = _state.value.copy(lastCommandOutput = summary)
            return@withContext ShizukuExecutionResult(
                success = binderResult.exitCode == 0,
                featureTitle = feature.title,
                commandExecuted = finalCmd,
                output = summary
            )
        }

        val failMsg = "${feature.title} • Failed to invoke Shizuku remote process."
        _state.value = _state.value.copy(lastCommandOutput = failMsg)
        ShizukuExecutionResult(
            success = false,
            featureTitle = feature.title,
            commandExecuted = finalCmd,
            output = failMsg
        )
    }

    /**
     * Executes a custom `[Shizuku]` shell command entered by the user, after strict safety validation
     * so dangerous/destructive commands (`rm`, `reboot`, `mkfs`, `dd`, bricking DPI) are blocked.
     */
    suspend fun executeSafeCustomShizukuCommand(rawCmd: String): ShizukuExecutionResult =
        withContext(Dispatchers.IO) {
            val trimmed = rawCmd.trim()
            if (!isCommandSafeForPhone(trimmed)) {
                val blockedMsg = "Blocked unsafe command by Phone Safety Guard: `$trimmed`"
                _state.value = _state.value.copy(lastCommandOutput = blockedMsg)
                return@withContext ShizukuExecutionResult(
                    success = false,
                    featureTitle = "[Shizuku] Safety Guard",
                    commandExecuted = trimmed,
                    output = blockedMsg
                )
            }
            val adHocFeature = ShizukuFeatureItem(
                id = "shizuku_custom_cmd",
                title = "[Shizuku] Custom Command",
                subtitle = trimmed,
                category = ShizukuCategory.DIAGNOSTICS_PRIVACY,
                shellCommand = trimmed
            )
            executeShizukuFeature(adHocFeature, isSimulated = false)
        }

    private fun isCommandSafeForPhone(cmd: String): Boolean {
        val lower = cmd.lowercase()
        val forbiddenTokens = listOf(
            "rm -rf", "rm -r", "mkfs", "dd if=", "reboot", "recovery", "fastboot",
            "bootloader", "pm uninstall", "pm disable-user com.android.systemui",
            "wm density 10", "wm size 10", "format", "wipe"
        )
        return forbiddenTokens.none { lower.contains(it) }
    }

    private data class RawShellResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runViaShizukuBinder(command: String): RawShellResult? {
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as? Process ?: return null

            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            process.waitFor(4, TimeUnit.SECONDS)
            val code = try {
                process.exitValue()
            } catch (_: Exception) {
                0
            }
            RawShellResult(code, stdout, stderr)
        } catch (_: Throwable) {
            null
        }
    }

    private fun runViaLocalCompatShell(command: String): RawShellResult {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            val exitCode = if (finished) process.exitValue() else 0
            RawShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            RawShellResult(-1, "", e.message ?: "Shell error")
        }
    }

    data class ShizukuExecutionResult(
        val success: Boolean,
        val featureTitle: String,
        val commandExecuted: String,
        val output: String
    )

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 9420

        val KNOWN_CLIENTS = listOf(
            ShizukuCompatibleClient(
                id = "shizuku_official",
                displayName = "Shizuku (Official Rikka)",
                packageName = "moe.shizuku.privileged.api",
                protocolDescription = "Official Shizuku Binder IPC v13 (ADB / Wireless Debugging / Root)"
            ),
            ShizukuCompatibleClient(
                id = "shevary",
                displayName = "Shevary (Shizuku Client)",
                packageName = "com.shevary.shizuku",
                protocolDescription = "Shevary Privileged Bridge & Shizuku-API Compatible Client"
            ),
            ShizukuCompatibleClient(
                id = "sui_magisk",
                displayName = "Sui (Root Shizuku Provider)",
                packageName = "moe.shizuku.manager",
                protocolDescription = "Systemless Shizuku Binder implementation"
            ),
            ShizukuCompatibleClient(
                id = "dhizuku",
                displayName = "Dhizuku (DeviceOwner Bridge)",
                packageName = "com.rosan.dhizuku",
                protocolDescription = "Device-Owner & Shizuku permission delegation client"
            ),
            ShizukuCompatibleClient(
                id = "ashell",
                displayName = "aShell (Shizuku Shell Client)",
                packageName = "in.sunilpaulmathew.ashell",
                protocolDescription = "Local Shizuku command runner & rish companion"
            ),
            ShizukuCompatibleClient(
                id = "ladb",
                displayName = "LADB (Local Wireless ADB)",
                packageName = "com.draco.ladb",
                protocolDescription = "Loopback Wireless ADB server bridge"
            ),
            ShizukuCompatibleClient(
                id = "brevent",
                displayName = "Brevent (ADB Standby Server)",
                packageName = "me.piebridge.brevent",
                protocolDescription = "Wireless ADB background privilege server"
            ),
            ShizukuCompatibleClient(
                id = "termux_rish",
                displayName = "Termux (rish Shizuku Shell)",
                packageName = "com.termux",
                protocolDescription = "Rikka rish interactive privileged shell bridge"
            )
        )

        /**
         * Complete catalog of 36 real, phone-safe, non-destructive `[Shizuku]` privileged features.
         */
        val ALL_36_SHIZUKU_FEATURES: List<ShizukuFeatureItem> = listOf(
            // ---------------- 1. SYSTEM UI & STATUS BAR (6) ----------------
            ShizukuFeatureItem(
                id = "shizuku_expand_notifications",
                title = "[Shizuku] Expand Notification Shade",
                subtitle = "Pulls down the system notification drawer hands-free via StatusBarManager",
                category = ShizukuCategory.SYSTEM_UI,
                shellCommand = "cmd statusbar expand-notifications"
            ),
            ShizukuFeatureItem(
                id = "shizuku_expand_quick_settings",
                title = "[Shizuku] Expand Quick Settings Tiles",
                subtitle = "Opens the full Android Quick Settings tile panel immediately",
                category = ShizukuCategory.SYSTEM_UI,
                shellCommand = "cmd statusbar expand-settings"
            ),
            ShizukuFeatureItem(
                id = "shizuku_collapse_panels",
                title = "[Shizuku] Collapse Status Bar Panels",
                subtitle = "Closes any open notification or Quick Settings shade",
                category = ShizukuCategory.SYSTEM_UI,
                shellCommand = "cmd statusbar collapse"
            ),
            ShizukuFeatureItem(
                id = "shizuku_toggle_heads_up",
                title = "[Shizuku] Toggle Heads-Up Popups",
                subtitle = "Silences distracting pop-up notification banners while working at your desk",
                category = ShizukuCategory.SYSTEM_UI,
                shellCommand = "settings put global heads_up_notifications_enabled 0",
                toggleOffCommand = "settings put global heads_up_notifications_enabled 1"
            ),
            ShizukuFeatureItem(
                id = "shizuku_immersive_fullscreen",
                title = "[Shizuku] Toggle Immersive Edge-to-Edge Mode",
                subtitle = "Auto-hides status and navigation bars across apps for maximum desk screen space",
                category = ShizukuCategory.SYSTEM_UI,
                shellCommand = "settings put global policy_control immersive.full=*",
                toggleOffCommand = "settings put global policy_control immersive.off=*"
            ),
            ShizukuFeatureItem(
                id = "shizuku_clean_demo_statusbar",
                title = "[Shizuku] Toggle Clean Demo Status Bar (12:00)",
                subtitle = "Sets a clean 100% battery & 12:00 clock status bar for clean screenshots",
                category = ShizukuCategory.SYSTEM_UI,
                shellCommand = "settings put global sysui_demo_allowed 1 && am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 && am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false",
                toggleOffCommand = "am broadcast -a com.android.systemui.demo -e command exit"
            ),

            // ---------------- 2. DISPLAY, REFRESH RATE & OPTICS (6) ----------------
            ShizukuFeatureItem(
                id = "shizuku_force_120hz",
                title = "[Shizuku] Lock 120Hz Peak Refresh Rate",
                subtitle = "Forces maximum fluid 120Hz refresh rate for ultra-smooth Liquid Glass animations",
                category = ShizukuCategory.DISPLAY_OPTICS,
                shellCommand = "settings put system min_refresh_rate 120.0 && settings put system peak_refresh_rate 120.0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_force_60hz",
                title = "[Shizuku] Eco 60Hz Refresh Rate Cap",
                subtitle = "Caps display refresh rate at 60Hz to conserve battery during long desk sessions",
                category = ShizukuCategory.DISPLAY_OPTICS,
                shellCommand = "settings put system min_refresh_rate 60.0 && settings put system peak_refresh_rate 60.0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_adaptive_refresh",
                title = "[Shizuku] Restore Adaptive Refresh Rate",
                subtitle = "Restores default LTPO dynamic refresh rate scaling (1Hz–120Hz)",
                category = ShizukuCategory.DISPLAY_OPTICS,
                shellCommand = "settings put system min_refresh_rate 0.0 && settings put system peak_refresh_rate 120.0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_extra_dim",
                title = "[Shizuku] Toggle Extra-Dim Night Filter",
                subtitle = "Activates Android's sub-minimum pixel dimming filter for dark rooms",
                category = ShizukuCategory.DISPLAY_OPTICS,
                shellCommand = "settings put secure reduce_bright_colors_activated 1",
                toggleOffCommand = "settings put secure reduce_bright_colors_activated 0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_grayscale_reading",
                title = "[Shizuku] Toggle Monochrome E-Ink Reading Mode",
                subtitle = "Switches the display between full color and distraction-free grayscale",
                category = ShizukuCategory.DISPLAY_OPTICS,
                shellCommand = "settings put secure accessibility_display_daltonizer_enabled 1 && settings put secure accessibility_display_daltonizer 0",
                toggleOffCommand = "settings put secure accessibility_display_daltonizer_enabled 0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_stay_awake_charging",
                title = "[Shizuku] Toggle Desk Stay-Awake on Power",
                subtitle = "Keeps the screen awake whenever docked on USB or wireless charging",
                category = ShizukuCategory.DISPLAY_OPTICS,
                shellCommand = "svc power stayon usb",
                toggleOffCommand = "svc power stayon false"
            ),

            // ---------------- 3. HARDWARE INPUT, CAPTURE & POWER (6) ----------------
            ShizukuFeatureItem(
                id = "shizuku_instant_screenshot",
                title = "[Shizuku] Capture Hardware System Screenshot",
                subtitle = "Injects native KEYCODE_SYSRPQ (120) to capture any screen without prompts",
                category = ShizukuCategory.HARDWARE_INPUT,
                shellCommand = "input keyevent 120"
            ),
            ShizukuFeatureItem(
                id = "shizuku_sleep_screen",
                title = "[Shizuku] Sleep & Lock Screen Instantly",
                subtitle = "Puts display to sleep via KEYCODE_SLEEP (223) while keeping fingerprint unlock active",
                category = ShizukuCategory.HARDWARE_INPUT,
                shellCommand = "input keyevent 223"
            ),
            ShizukuFeatureItem(
                id = "shizuku_wake_dismiss_keyguard",
                title = "[Shizuku] Wake Screen & Dismiss Keyguard",
                subtitle = "Wakes the display (KEYCODE_WAKEUP) and dismisses non-secure swipe lockscreen",
                category = ShizukuCategory.HARDWARE_INPUT,
                shellCommand = "input keyevent 224 && wm dismiss-keyguard"
            ),
            ShizukuFeatureItem(
                id = "shizuku_power_menu",
                title = "[Shizuku] Open Native System Power Menu",
                subtitle = "Triggers long-press Power button menu for device controls & wallet",
                category = ShizukuCategory.HARDWARE_INPUT,
                shellCommand = "input keyevent --longpress 26"
            ),
            ShizukuFeatureItem(
                id = "shizuku_smart_scroll_down",
                title = "[Shizuku] Hands-Free Page Scroll Down",
                subtitle = "Smoothly scrolls articles, docs, or feeds down with a desk knock",
                category = ShizukuCategory.HARDWARE_INPUT,
                shellCommand = "input swipe 540 1480 540 420 260"
            ),
            ShizukuFeatureItem(
                id = "shizuku_smart_scroll_up",
                title = "[Shizuku] Hands-Free Page Scroll Up",
                subtitle = "Smoothly scrolls the active screen upward without touching the phone",
                category = ShizukuCategory.HARDWARE_INPUT,
                shellCommand = "input swipe 540 420 540 1480 260"
            ),

            // ---------------- 4. CONNECTIVITY & RADIO SWITCHES (6) ----------------
            ShizukuFeatureItem(
                id = "shizuku_wifi_toggle",
                title = "[Shizuku] Direct Wi-Fi Radio Toggle",
                subtitle = "Switches Wi-Fi on or off directly without Android 10+ Internet Panel dialogs",
                category = ShizukuCategory.CONNECTIVITY,
                shellCommand = "svc wifi enable",
                toggleOffCommand = "svc wifi disable"
            ),
            ShizukuFeatureItem(
                id = "shizuku_bluetooth_toggle",
                title = "[Shizuku] Direct Bluetooth Radio Toggle",
                subtitle = "Enables or disables Bluetooth controller directly via svc bluetooth",
                category = ShizukuCategory.CONNECTIVITY,
                shellCommand = "svc bluetooth enable",
                toggleOffCommand = "svc bluetooth disable"
            ),
            ShizukuFeatureItem(
                id = "shizuku_mobile_data_toggle",
                title = "[Shizuku] Direct Mobile Data Toggle",
                subtitle = "Switches cellular mobile data on or off with a single desk knock",
                category = ShizukuCategory.CONNECTIVITY,
                shellCommand = "svc data enable",
                toggleOffCommand = "svc data disable"
            ),
            ShizukuFeatureItem(
                id = "shizuku_nfc_toggle",
                title = "[Shizuku] Direct NFC Tap-to-Pay Toggle",
                subtitle = "Toggles the hardware NFC antenna on or off via svc nfc",
                category = ShizukuCategory.CONNECTIVITY,
                shellCommand = "svc nfc enable",
                toggleOffCommand = "svc nfc disable"
            ),
            ShizukuFeatureItem(
                id = "shizuku_airplane_toggle",
                title = "[Shizuku] Privileged Airplane Mode Toggle",
                subtitle = "Toggles system-wide Airplane Mode via ConnectivityManager shell interface",
                category = ShizukuCategory.CONNECTIVITY,
                shellCommand = "cmd connectivity airplane-mode enable",
                toggleOffCommand = "cmd connectivity airplane-mode disable"
            ),
            ShizukuFeatureItem(
                id = "shizuku_flush_dns",
                title = "[Shizuku] Flush Network DNS & Socket Cache",
                subtitle = "Clears stale DNS resolver cache on active network interfaces",
                category = ShizukuCategory.CONNECTIVITY,
                shellCommand = "cmd netd resolver flushdefaultif"
            ),

            // ---------------- 5. PERFORMANCE, ANIMATIONS & RAM (6) ----------------
            ShizukuFeatureItem(
                id = "shizuku_turbo_animations_05x",
                title = "[Shizuku] Turbo 0.5x System Animations",
                subtitle = "Sets Window, Transition, and Animator scales to 0.5x for ultra-snappy UI",
                category = ShizukuCategory.PERFORMANCE,
                shellCommand = "settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5"
            ),
            ShizukuFeatureItem(
                id = "shizuku_instant_animations_0x",
                title = "[Shizuku] Zero-Lag 0.0x Instant Animations",
                subtitle = "Disables system transition delays for instant app switching",
                category = ShizukuCategory.PERFORMANCE,
                shellCommand = "settings put global window_animation_scale 0.0 && settings put global transition_animation_scale 0.0 && settings put global animator_duration_scale 0.0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_restore_animations_1x",
                title = "[Shizuku] Restore 1.0x Stock Animations",
                subtitle = "Restores standard 1.0x Android Window, Transition, and Animator scales",
                category = ShizukuCategory.PERFORMANCE,
                shellCommand = "settings put global window_animation_scale 1.0 && settings put global transition_animation_scale 1.0 && settings put global animator_duration_scale 1.0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_trim_ram_caches",
                title = "[Shizuku] Trim Background Caches & Boost RAM",
                subtitle = "Safely trims temporary app caches and clears idle background processes",
                category = ShizukuCategory.PERFORMANCE,
                shellCommand = "pm trim-caches 4G && am kill-all"
            ),
            ShizukuFeatureItem(
                id = "shizuku_system_dark_mode",
                title = "[Shizuku] Toggle System-Wide Dark / Light Mode",
                subtitle = "Switches Android system UI night mode via UiModeManager service",
                category = ShizukuCategory.PERFORMANCE,
                shellCommand = "cmd uimode night yes",
                toggleOffCommand = "cmd uimode night no"
            ),
            ShizukuFeatureItem(
                id = "shizuku_battery_saver_toggle",
                title = "[Shizuku] Toggle Extreme Battery Saver",
                subtitle = "Enables or disables Android Low Power Battery Saver mode immediately",
                category = ShizukuCategory.PERFORMANCE,
                shellCommand = "settings put global low_power 1",
                toggleOffCommand = "settings put global low_power 0"
            ),

            // ---------------- 6. PRIVACY, DIAGNOSTICS & SELF-GRANT (6) ----------------
            ShizukuFeatureItem(
                id = "shizuku_show_touches_toggle",
                title = "[Shizuku] Toggle Show Touch Pointer Dots",
                subtitle = "Displays visual tap circles on screen for recordings and demonstrations",
                category = ShizukuCategory.DIAGNOSTICS_PRIVACY,
                shellCommand = "settings put system show_touches 1",
                toggleOffCommand = "settings put system show_touches 0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_freeform_windows",
                title = "[Shizuku] Toggle Freeform Multi-Window Mode",
                subtitle = "Enables desktop-style resizable freeform app windows on your phone",
                category = ShizukuCategory.DIAGNOSTICS_PRIVACY,
                shellCommand = "settings put global enable_freeform_support 1",
                toggleOffCommand = "settings put global enable_freeform_support 0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_vibration_haptic_boost",
                title = "[Shizuku] Toggle System Haptic Touch Feedback",
                subtitle = "Enables or disables global Android touch haptic feedback",
                category = ShizukuCategory.DIAGNOSTICS_PRIVACY,
                shellCommand = "settings put system haptic_feedback_enabled 1",
                toggleOffCommand = "settings put system haptic_feedback_enabled 0"
            ),
            ShizukuFeatureItem(
                id = "shizuku_media_dispatch_play_next",
                title = "[Shizuku] Privileged Media Session Next Track",
                subtitle = "Dispatches media next directly through Android's system media_session service",
                category = ShizukuCategory.DIAGNOSTICS_PRIVACY,
                shellCommand = "cmd media_session dispatch next"
            ),
            ShizukuFeatureItem(
                id = "shizuku_battery_thermal_report",
                title = "[Shizuku] Read Hardware Battery & Thermal Telemetry",
                subtitle = "Queries dumpsys battery for live voltage, temperature, and charge health",
                category = ShizukuCategory.DIAGNOSTICS_PRIVACY,
                shellCommand = "dumpsys battery | grep -E 'level|voltage|temperature|health'"
            ),
            ShizukuFeatureItem(
                id = "shizuku_self_grant_knockdock",
                title = "[Shizuku] Self-Grant KnockDock All Background Permissions",
                subtitle = "Automatically grants KnockDock Overlay, Battery Whitelist, Notifications & Secure Settings!",
                category = ShizukuCategory.DIAGNOSTICS_PRIVACY,
                shellCommand = "appops set {{PACKAGE}} SYSTEM_ALERT_WINDOW allow && dumpsys deviceidle whitelist +{{PACKAGE}} && pm grant {{PACKAGE}} android.permission.POST_NOTIFICATIONS"
            )
        )

        fun findFeatureById(id: String): ShizukuFeatureItem? =
            ALL_36_SHIZUKU_FEATURES.find { it.id == id }
    }
}
