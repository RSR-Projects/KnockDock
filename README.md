<div align="center">

<img src="fastlane/icon.png" alt="KnockDock App Icon" width="120" height="120" />

# KnockDock

**Turn Physical Desk Knocks into Instant Android, Smart Home & PC Actions**

[![Platform API 24+](fastlane/badges/platform_api24.svg)](#releases--release-apk-download)
[![Package ID](fastlane/badges/package_id.svg)](#releases--release-apk-download)
[![Release Build](fastlane/badges/release_apk.svg)](#releases--release-apk-download)
[![Kotlin & Compose](fastlane/badges/kotlin_compose.svg)](#complete-feature-reference)
[![Liquid Glass Engine](fastlane/badges/liquid_glass.svg)](#8-kyant0-liquid-glass-optical-engine--12-folder-settings)
[![Action Studio](fastlane/badges/action_studio.svg)](#4-custom-action-studio-all-30-actions-explained)
[![License](fastlane/badges/license_apache.svg)](https://github.com/RSR-Projects/KnockDock)

<br />

[![Download Release KnockDock.apk](fastlane/badges/dl_knockdock_apk.svg)](./KnockDock.apk)
[![Download app-release.apk](fastlane/badges/dl_app_release.svg)](./app-release.apk)
[![GitHub Releases](fastlane/badges/github_releases.svg)](https://github.com/RSR-Projects/KnockDock/releases)
[![Browse Screenshots](fastlane/badges/screenshots_gallery.svg)](#screenshots--fastlane-visual-gallery)

</div>

---

## Overview

**KnockDock** transforms the physical surface of your desk into an acoustic and structural-vibration macro controller. Place your Android phone anywhere on your desk and knock, tap, or rap on the wood, glass, laminate, or metal surface to trigger:

- **30 Built-In Android, Smart Home & PC Actions** across 10 specialized categories
- **Multi-Step Conditional Macros** with dry-run simulation, cycle protection, step retries, and concurrency policies
- **Context Profiles & Gesture Modifier Chords** (`MOD_A`, `MOD_B`, `MOD_C`) that multiply your knock vocabulary
- **Background App & URL Launching** that works reliably even when KnockDock is minimized or closed
- **Authenticated Wi-Fi PC Control Bridge** for Windows/Mac virtual desktops, DAW transport, and presentation slides
- **Real-Time Kyant0 Liquid Glass UI** (`io.github.kyant0:backdrop` & `io.github.kyant0:shapes`) with independent optical settings for Cards vs. Floating Bottom Navigation Bar, stationary container cards, and zero-recomposition hardware-layer touch physics on interactive controls
- **12 Collapsible Settings & About Folders** (closed by default) for a clean, minimal dark-mode experience with zero text clipping

---

## Releases & Release APK Download

![Release Version](fastlane/badges/release_apk.svg)
![App ID](fastlane/badges/package_id.svg)
![Min SDK](fastlane/badges/min_sdk_24.svg)
![Target SDK](fastlane/badges/target_sdk_36.svg)

### Direct Release APK Download Options

| Package / Artifact | Location | Description | Direct Link |
| :--- | :--- | :--- | :--- |
| **Root Release APK (Primary)** | [`./KnockDock.apk`](./KnockDock.apk) | Signed production **Release APK** in the repository root | [**Download `KnockDock.apk`**](./KnockDock.apk) |
| **Root Release APK (Lowercase)** | [`./knockdock.apk`](./knockdock.apk) | Lowercase alias of the signed **Release APK** for CLI scripts | [**Download `knockdock.apk`**](./knockdock.apk) |
| **Root Standard Release APK** | [`./app-release.apk`](./app-release.apk) | Signed **Release APK** (`app-release.apk`) in the repository root | [**Download `app-release.apk`**](./app-release.apk) |
| **Gradle Release Output APK** | [`app/build/outputs/apk/release/app-release.apk`](./app/build/outputs/apk/release/app-release.apk) | Standard Gradle `assembleRelease` build output path | [**Download Gradle Release APK**](./app/build/outputs/apk/release/app-release.apk) |
| **GitHub Releases** | [`https://github.com/RSR-Projects/KnockDock/releases`](https://github.com/RSR-Projects/KnockDock/releases) | Official GitHub Releases page for versioned downloads & changelogs | [**Open GitHub Releases**](https://github.com/RSR-Projects/KnockDock/releases) |

> **Important Upgrade Note (`com.rsrprojects.knockdock`)**:
> If you previously installed an early development prototype that used a different package ID or signing key, back up your custom profiles, gestures, and actions first via **Profiles & Mappings → Export JSON**, uninstall the old prototype build, install the signed **Release APK**, and import your JSON backup.

---

## Screenshots & Fastlane Visual Gallery

![Screenshots Format](https://img.shields.io/badge/Screenshots-1.png_to_6.png-00E5FF?style=plastic&labelColor=0B132B)
![Fastlane Metadata](https://img.shields.io/badge/Fastlane-en--US_Store_Metadata-10B981?style=plastic&labelColor=0B132B)
![Icon Path](https://img.shields.io/badge/App_Icon-fastlane%2Ficon.png-6366F1?style=plastic&labelColor=0B132B)

Screenshots are referenced strictly as `1.png`, `2.png`, `3.png`, `4.png`, `5.png`, and `6.png` under `fastlane/screenshots/` and `fastlane/metadata/android/en-US/images/phoneScreenshots/`.

| 1. Home Dock & Live Beacon (`1.png`) | 2. 60 FPS Sensor Lab & Oscilloscope (`2.png`) | 3. 3-Trial Gesture Trainer (`3.png`) |
| :---: | :---: | :---: |
| <a href="fastlane/screenshots/1.png"><img src="fastlane/screenshots/1.png" alt="1.png - Home Dock Screen" width="260" /></a> | <a href="fastlane/screenshots/2.png"><img src="fastlane/screenshots/2.png" alt="2.png - Sensor Lab Screen" width="260" /></a> | <a href="fastlane/screenshots/3.png"><img src="fastlane/screenshots/3.png" alt="3.png - Gesture Trainer Screen" width="260" /></a> |
| [`fastlane/screenshots/1.png`](fastlane/screenshots/1.png) · [Fastlane `1.png`](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | [`fastlane/screenshots/2.png`](fastlane/screenshots/2.png) · [Fastlane `2.png`](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) | [`fastlane/screenshots/3.png`](fastlane/screenshots/3.png) · [Fastlane `3.png`](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) |

| 4. Custom Action Studio (`4.png`) | 5. Context Profiles & Chords (`5.png`) | 6. 12-Folder Settings & About (`6.png`) |
| :---: | :---: | :---: |
| <a href="fastlane/screenshots/4.png"><img src="fastlane/screenshots/4.png" alt="4.png - Action Studio Screen" width="260" /></a> | <a href="fastlane/screenshots/5.png"><img src="fastlane/screenshots/5.png" alt="5.png - Profiles & Mappings Screen" width="260" /></a> | <a href="fastlane/screenshots/6.png"><img src="fastlane/screenshots/6.png" alt="6.png - Settings & About Folders Screen" width="260" /></a> |
| [`fastlane/screenshots/4.png`](fastlane/screenshots/4.png) · [Fastlane `4.png`](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png) | [`fastlane/screenshots/5.png`](fastlane/screenshots/5.png) · [Fastlane `5.png`](fastlane/metadata/android/en-US/images/phoneScreenshots/5.png) | [`fastlane/screenshots/6.png`](fastlane/screenshots/6.png) · [Fastlane `6.png`](fastlane/metadata/android/en-US/images/phoneScreenshots/6.png) |

---

## Complete Feature Reference

### 1. Acoustic & Structural Vibration Detection Engine

![DSP Engine](https://img.shields.io/badge/DSP-High--Pass_Filter_%2B_EMA_Noise_Floor-00E5FF?style=plastic&labelColor=0B132B)
![Sampling](https://img.shields.io/badge/Sampling-SENSOR__DELAY__GAME_(~200_Hz)-10B981?style=plastic&labelColor=0B132B)
![Anti-Echo](https://img.shields.io/badge/Anti--Echo-Refractory_%26_Gyro_Rejection-6366F1?style=plastic&labelColor=0B132B)

- **6-Axis Sensor Fusion (`SensorHelper.kt` & `SignalFilter.kt`)**:
  - Combines 3-axis `TYPE_ACCELEROMETER` and 3-axis `TYPE_GYROSCOPE` streams in real time.
  - Applies a first-order **High-Pass Filter (HPF)** ($\alpha = 0.82$) to strip static gravity ($9.81\text{ m/s}^2$) and slow desk tilt while isolating sharp z-axis structural knock impulses.
- **Dynamic Exponential Moving Average (EMA) Noise Floor (`ImpactDetector.kt`)**:
  - Continuously tracks ambient room/desk vibration (typing, fan hum, speakers) and computes an adaptive trigger threshold:
    $$\text{Effective Threshold} = \frac{\text{BaseThreshold} + (\text{NoiseFloor} \times 2.6)}{\text{SensitivityMultiplier} \times \text{SurfaceGain}}$$
- **Refractory Window & Structural Ringing Suppression**:
  - Enforces a configurable refractory cooldown (`60 ms – 220 ms`, default `95 ms`) after each detected impact so resonant wood/glass after-vibrations are never double-counted as extra knocks.
- **Device-Handling & Pick-Up Rejection**:
  - Monitors gyroscope angular velocity magnitude (`rad/s`) and suppresses false knock triggers when the phone is being picked up, tilted, or carried in hand.
- **Desk Surface Material Presets**:
  - Dedicated acoustic gain and damping presets for **Wood Desk**, **Glass Table**, **Metal / Standing Desk**, and **Soft / Mat Surface**.

---

### 2. Real-Time Sensor Lab & 60 FPS Oscilloscope

![Oscilloscope](https://img.shields.io/badge/Oscilloscope-60_FPS_Canvas_Waveform-00E5FF?style=plastic&labelColor=0B132B)
![Synthetic Injector](https://img.shields.io/badge/Testing-Synthetic_Knock_Injector-10B981?style=plastic&labelColor=0B132B)

- **Live High-Pass Impulse Waveform**:
  - Hardware-accelerated Canvas oscilloscope plotting real-time filtered impulse magnitude against the live adaptive threshold line.
  - Supports **Pause / Resume Freeze-Frame** and **Peak-Hold Capture** to inspect exact impulse amplitudes.
- **6-Axis Telemetry Cards**:
  - Displays live `Accel X`, `Accel Y`, `Accel Z`, `Filtered Impulse`, `Dynamic Noise Floor`, and `Gyroscope Magnitude` in spacious, non-clipping cards.
- **Synthetic Signal Replayer (`SensorReplayer.kt`)**:
  - Injects deterministic sensor waveforms directly into the live detection pipeline (`Single Knock`, `Double Knock`, `Triple Knock`, `Quadruple Knock`, and `Rap-Pause-Rap`) so you can verify gestures and macros without physically tapping.

---

### 3. Multi-Trial Gesture Recognition & Gesture Trainer

![3-Trial Calibration](https://img.shields.io/badge/Trainer-3--Trial_Auto_Calibration-00E5FF?style=plastic&labelColor=0B132B)
![Manual Editor](https://img.shields.io/badge/Editor-Millisecond_Precision-6366F1?style=plastic&labelColor=0B132B)

- **Temporal Pattern Classifier (`GestureClassifier.kt`)**:
  - Segments impact sequences when quiet time exceeds the sequence timeout window (`520 ms`).
  - Scores candidate gestures by matching **impact count**, **inter-knock interval bounds** (`minIntervalMs` .. `maxIntervalMs`), **rhythm ratio**, and **impulse confidence**, selecting the highest-confidence match above `minConfidence`.
- **Interactive 3-Trial Gesture Trainer (`GestureTrainer.kt`)**:
  - Guides you through recording a custom knock pattern **3 times in a row**.
  - Validates that all 3 trials have matching impact counts, computes the **Consistency Score (0–100%)**, and automatically derives optimal `minIntervalMs` and `maxIntervalMs` tolerances.
- **Direct Manual Gesture Creator & Editor**:
  - Create or edit any gesture directly via sliders for **Impact Count (1–6)**, **Min Interval (50–800 ms)**, **Max Interval (120–1500 ms)**, **Min Confidence (30–95%)**, and **Cooldown (200–3000 ms)**.

---

### 4. Custom Action Studio (All 30 Actions Explained)

![30 Actions](https://img.shields.io/badge/Total_Actions-30_Built--In_Types-00E5FF?style=plastic&labelColor=0B132B)
![Dry-Run Safe](https://img.shields.io/badge/Dry--Run_Simulation-Zero_Side_Effects-10B981?style=plastic&labelColor=0B132B)
![Validation](https://img.shields.io/badge/Validation-Real--Time_Schema_Check-6366F1?style=plastic&labelColor=0B132B)

Every action in KnockDock includes a dedicated configuration UI, real-time parameter validation, a **Dry-Run Simulation** button (validates without executing real side effects), a **Live Test** button, and structured execution logs.

#### A. Workspace & Profile Actions
1. **Desk Mode Toggle (`DESK_MODE_TOGGLE`)**:
   - Alternates between a Primary Profile and Secondary Profile (e.g., *Work* ↔ *Gaming*), optionally firing an activation action while preventing recursive profile-switching loops.
2. **Focus Session Controller (`FOCUS_SESSION_CONTROLLER`)**:
   - Starts, pauses, resumes, toggles, or ends a timed Pomodoro/Focus session (`durationMinutes`, `breakMinutes`).
   - Optionally enables Android **Do Not Disturb (DND)** when permission is granted and **automatically restores** the prior interruption filter when the session ends (unless the user manually changed DND in the meantime).
3. **Workspace Switcher (`WORKSPACE_SWITCHER`)** *(PC Bridge)*:
   - Sends authenticated workspace commands to a paired Windows/Mac PC (`virtual_desktop_next`, `virtual_desktop_prev`, `minimize_all`, `restore_windows`, `launch_workspace_set`).
4. **Switch Profile (`SWITCH_PROFILE`)**:
   - Directly activates a target `DockProfile` and applies its sensitivity and gesture mappings.

#### B. Music, Media & Presentation Actions
5. **Music & DAW Session Controller (`MUSIC_SESSION_CONTROLLER`)**:
   - Controls Android media playback (`PLAY_PAUSE`, `NEXT`, `PREVIOUS`, `STOP`), launches a target music app or playlist deep link, and optionally dispatches authenticated DAW transport commands (`daw_play`, `daw_stop`, `daw_record`, `daw_metronome`, `daw_next_marker`, `daw_prev_marker`) to a paired PC.
6. **Presentation Remote (`PRESENTATION_REMOTE`)** *(PC Bridge)*:
   - Advances or reverses slides (`next_slide`, `previous_slide`, `start_presentation`, `end_presentation`, `blackout_screen`) on a paired PC with connection status and test verification.
7. **Gesture Modifier (`GESTURE_MODIFIER`)**:
   - Activates a temporary modifier chord (`MOD_A`, `MOD_B`, or `MOD_C`) for a configurable countdown window (`1500 ms – 10000 ms`, default `4000 ms`) so the next knock pattern triggers an alternate bound action.
8. **Playlist / Station Launcher (`PLAYLIST_STATION_LAUNCHER`)**:
   - Opens a configured Spotify, YouTube Music, or custom `https://` / `spotify:` deep link URI with an optional fallback package.
9. **Media Play / Pause (`MEDIA_PLAY_PAUSE`)**:
   - Dispatches hardware `KEYCODE_MEDIA_PLAY_PAUSE` via Android `AudioManager`.
10. **Media Next Track (`MEDIA_NEXT`)**:
    - Dispatches `KEYCODE_MEDIA_NEXT` to skip to the next track.
11. **Media Previous Track (`MEDIA_PREVIOUS`)**:
    - Dispatches `KEYCODE_MEDIA_PREVIOUS` to return to the previous track.

#### C. Productivity & Capture Actions
12. **Snippet & Clipboard Launcher (`SNIPPET_LAUNCHER`)**:
    - Copies a multi-line text template to the Android clipboard (`COPY_CLIPBOARD`), opens the Android Share Sheet (`SHARE_SHEET`), or displays a quick overlay card.
13. **Screenshot & Annotation Workflow (`SCREENSHOT_ANNOTATION_LAUNCHER`)**:
    - Triggers an Android capture/share workflow (`ANDROID_CAPTURE_SHEET`, `OPEN_GALLERY_LATEST`, or paired PC `PC_SCREENSHOT_ANNOTATE`).
14. **Quick Reminder & Countdown Timer (`QUICK_REMINDER_TIMER`)**:
    - Starts an in-app and notification countdown timer (`1m`, `5m`, `10m`, `15m`, `30m`, or custom seconds) with live remaining-time tracking and completion alert.
15. **Quick Voice Memo (`QUICK_VOICE_MEMO`)**:
    - Records an AAC/M4A voice note (`5s – 300s`) using `MediaRecorder` into app storage with runtime `RECORD_AUDIO` permission handling, live recording state, and stop-on-second-knock support.
16. **Quick Tool Launcher (`QUICK_TOOL_LAUNCHER`)**:
    - Launches everyday desk utilities (`CALCULATOR`, `CLOCK_ALARM`, `CALENDAR`, `NOTES`, `CAMERA`, or a custom package fallback).

#### D. Smart Home, Network & Connected Actions
17. **Smart Home Scene Trigger (`SMART_HOME_SCENE`)**:
    - Triggers a Home Assistant, webhook, or local IoT scene payload with configurable endpoint URL, scene ID, and bearer token.
18. **Local Webhook / HTTP Request (`WEBHOOK_HTTP_REQUEST`)**:
    - Executes real `GET`, `POST`, or `PUT` HTTP requests with custom JSON headers, payload body, and timeout (`1000 ms – 15000 ms`), returning HTTP status codes and response latency.
19. **Reversible State Toggle (`REVERSIBLE_STATE_TOGGLE`)**:
    - Tracks assumed `ON` / `OFF` state for non-idempotent devices, alternates between Primary (ON) and Reverse (OFF) actions on each knock, and provides a 1-tap **Resync State** button in Action Studio.
20. **PC Command Bridge (`PC_COMMAND`)** *(PC Bridge)*:
    - Sends low-latency JSON commands (`lock_workstation`, `mute_mic`, `play_pause`, `screenshot`, `open_terminal`, etc.) over LAN to the companion PC server.

#### E. Multi-Step Macro & Sequence Engine
21. **Action Sequence / Conditional Macro (`ACTION_SEQUENCE`)**:
    - Runs an ordered list of steps (`ActionId` + `DelayMs` + `StepCondition` + `RetryCount` + `FailurePolicy`) with full dry-run support and cycle detection.

#### F. Device & Utility Actions
22. **Launch App (`LAUNCH_APP`)**:
    - Opens any installed launchable Android application by package name (includes a searchable installed-app picker in Action Studio) and works from the background.
23. **Launch URL / Deep Link (`LAUNCH_URL`)**:
    - Opens any web URL or custom scheme URI in the default browser or handler app, including from background monitoring.
24. **Flashlight / Torch Toggle (`FLASHLIGHT_TOGGLE`)**:
    - Toggles the rear camera LED torch via `CameraManager`.
25. **Emergency Desk Light (`EMERGENCY_LIGHT`)**:
    - Activates the camera torch and/or high-visibility screen strobe beacon for dark desk environments.
26. **Device Finder Alert (`DEVICE_FINDER_ALERT`)**:
    - Plays an acoustic locator siren chirp and pulses haptics so you can find your phone on a cluttered desk.
27. **Ringer Mode Cycle (`RINGER_MODE_CYCLE`)**:
    - Cycles or sets Android `AudioManager` ringer state (`NORMAL`, `VIBRATE`, `SILENT`).
28. **Volume Step Up / Down / Mute (`VOLUME_ADJUST`)**:
    - Adjusts media or ring stream volume (`UP`, `DOWN`, `MUTE`, `UNMUTE`).
29. **System Settings Panel (`SYSTEM_SETTING`)**:
    - Opens Android system settings (`SOUND`, `WIFI`, `BLUETOOTH`, `DISPLAY`, `DND`, `BATTERY`, `OVERLAY`).
30. **Custom Notification (`NOTIFICATION`)**, **Haptic Pulse (`HAPTIC_PULSE`)**, and **Acoustic Synth Chirp (`AUDIO_CHIRP`)**:
    - Fires rich status notifications, tactile vibration patterns (`CLICK`, `DOUBLE`, `HEAVY`, `SUCCESS`), or synthesized sine-wave audio chirps (`220 Hz – 2400 Hz`).

---

### 5. Multi-Step Conditional Macro Engine & Dry-Run Simulator

![Macro Engine](https://img.shields.io/badge/Macros-Conditional_Branching_%2B_Retries-00E5FF?style=plastic&labelColor=0B132B)
![Cycle Guard](https://img.shields.io/badge/Safety-Cycle_%26_Depth_Guard_(Max_4)-10B981?style=plastic&labelColor=0B132B)

- **Per-Step Conditions (`StepConditionType`)**:
  - `ALWAYS`: Always execute step.
  - `ACTIVE_PROFILE_IS`: Execute only if the active profile matches `conditionValue`.
  - `CHARGING_STATE_IS`: Execute only when the device is `CHARGING` or `DISCHARGING`.
  - `TIME_WINDOW`: Execute only within a `HH:mm-HH:mm` time window (supports overnight spans like `22:00-06:00`).
  - `PC_BRIDGE_REACHABLE`: Execute only if the paired PC companion host is configured/reachable.
  - `DND_PERMISSION_GRANTED`: Execute only if Android Notification Policy Access is granted.
- **Failure & Retry Policies (`StepFailurePolicy`)**:
  - Each step supports `0–3` automatic retries with configurable retry delay (`retryDelayMs`), plus `STOP_MACRO`, `CONTINUE_NEXT`, or `FALLBACK_ACTION` failure handling.
- **Concurrency Policies (`MacroConcurrencyPolicy`)**:
  - Choose how overlapping triggers behave: `IGNORE_IF_RUNNING`, `RESTART`, `QUEUE`, or `CANCEL_TOGGLE`.
- **Guaranteed Side-Effect-Free Dry-Run (`dryRun = true`)**:
  - Tracing `dryRun` through `ActionExecutor.kt` validates every parameter, branch condition, nested sequence, and target reference **without** launching apps, toggling hardware torches, altering DND/ringer state, sending network webhooks, recording audio, or mutating profile state.

---

### 6. Context Profiles, Gesture Chords & JSON Backup/Restore

![Profiles](https://img.shields.io/badge/Profiles-Per--Context_Sensitivity_%26_Bindings-6366F1?style=plastic&labelColor=0B132B)
![Portability](https://img.shields.io/badge/Backup-Full_JSON_Import_%2F_Export-10B981?style=plastic&labelColor=0B132B)

- **Context Profiles (`DockProfile`)**:
  - Switch between *Desk Commander*, *Focus Guard*, *Media Deck*, or custom profiles. Each profile stores its own sensitivity multiplier, refractory window override, and gesture-to-action mappings.
- **Modifier Chord Support**:
  - Bind the same physical gesture (e.g., *Double Knock*) to different actions depending on whether `No Modifier`, `MOD_A`, `MOD_B`, or `MOD_C` is currently active.
- **Conflict Detector**:
  - Automatically scans the active profile for overlapping interval windows or duplicate gesture+modifier bindings and highlights conflicts in `ProfilesScreen`.
- **Full JSON Import & Export**:
  - Export individual profiles or the entire workspace repository to JSON and import them on any device.

---

### 7. Background Monitoring & Closed-App Execution Bridge

![Foreground Service](https://img.shields.io/badge/Service-Persistent_Foreground_Sensor_Service-00E5FF?style=plastic&labelColor=0B132B)
![Android 10+ Launch](https://img.shields.io/badge/Android_10%2B-Background_Activity_Launch_Bridge-10B981?style=plastic&labelColor=0B132B)

- **Persistent Foreground Service (`KnockMonitoringService.kt`)**:
  - Runs with `START_STICKY`, a dedicated persistent notification, and a partial CPU `WakeLock` (`KnockDock::SensorMonitoringWakeLock`) so desk knocks are detected even when the screen is off or the app is swiped away.
- **Android 10+ Background App & URL Launch Bridge**:
  - Combines direct `FLAG_ACTIVITY_NEW_TASK` intent dispatch, a transient 1×1 transparent `TYPE_APPLICATION_OVERLAY` window bridge (when *"Appear on Top"* is granted), and a high-priority full-screen `PendingIntent` fallback so launching apps, URLs, playlists, and system panels works reliably from the background.

---

### 8. Kyant0 Liquid Glass Optical Engine & 12-Folder Settings

![Kyant0 Backdrop](https://img.shields.io/badge/Backdrop-io.github.kyant0%3Abackdrop%3A1.0.6-00E5FF?style=plastic&labelColor=0B132B)
![Independent Optics](https://img.shields.io/badge/Optics-Independent_Card_vs_Nav_Dock_Glass-6366F1?style=plastic&labelColor=0B132B)
![Stationary Cards](https://img.shields.io/badge/Touch_Physics-Interactive_Controls_Only_(Stationary_Cards)-10B981?style=plastic&labelColor=0B132B)

- **Real-Time Kyant0 Backdrop Optical Engine (`LiquidGlassComponents.kt`)**:
  - Powered by [`Kyant0/AndroidLiquidGlass`](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop:1.0.6` and `io.github.kyant0:shapes:1.2.0`) with `rememberLayerBackdrop()`, real-time GPU blur (`blur()`), lens refraction (`lens()`), chromatic dispersion (`chromaticAberration`), and `RoundedRectangle` continuous capsule geometry.
- **Strict Optical Independence**:
  - **Cards (`LocalCardGlassSettings`)** and the **Floating Bottom Navigation Bar (`LocalNavigationGlassSettings`)** read from completely independent `CompositionLocal` state objects—adjusting card blur, refraction, or tint never alters the navigation dock, and vice versa.
- **Zero-Recomposition Hardware-Layer Touch Physics (`LiquidTouchModifier.kt`)**:
  - Interactive controls (`LiquidGlassButton`, `LiquidGlassSwitch`, `LiquidGlassSlider`, `LiquidGlassChip`, `LiquidGlassSegmentedRow`, `LiquidGlassNavBar`) respond to touch and drag via `graphicsLayer` translation and directional stretch/squish, while container cards (`LiquidGlassCard`) remain completely stationary so scrolling and reading are never disrupted.
- **12 Collapsible Settings & About Folders (`SettingsScreen.kt` & `AboutSettingsSection.kt`)**:
  - All 12 folders start **closed by default** so the Settings screen is uncluttered, fast to scan, and free of text clipping:
    1. **Background Launch & Power** (*Appear on Top* overlay permission, battery optimization exemption, auto-start on boot)
    2. **Card Liquid Glass** (Card blur radius, quadratic fine-grained blue/tint intensity curve, plus a nested *Advanced Card Optics* sub-folder for refraction bevel, rim highlight, corner radius, contrast, and lightweight mode)
    3. **Bottom Nav Bar Glass** (Independent floating navigation dock blur radius, lens refraction, chromatic dispersion, and pill opacity)
    4. **Liquid Touch Physics** (Enable/disable interactive control squish & drag, stretch intensity, drag responsiveness, and spring bounce)
    5. **Sensor & Knock Detection** (Sensitivity multiplier `0.4x–2.5x`, refractory window `60–220 ms`, gyro pick-up rejection, surface material preset)
    6. **Haptics & Audio Feedback** (Haptic confirmation toggle and acoustic synth chirp toggle)
    7. **PC Control Bridge (Wi-Fi)** (Host IP, port, auth token, and live connection test)
    8. **About KnockDock** (Centered hero app icon, tagline *"Turn physical knocks into actions."*, installed release version `v1.0 (1) · RELEASE`, and project overview)
    9. **Developer & Licence** (Developer `RSR-Projects`, `Apache License 2.0`, and open-source project details)
    10. **GitHub Repository & Links** (Interactive buttons for **Star on GitHub**, **View Source Code**, **Report a Bug**, and **View Releases** pointing to `https://github.com/RSR-Projects/KnockDock`)
    11. **Privacy & Sensor Data** (Transparent breakdown of local accelerometer/gyroscope processing, opt-in voice memo storage, and explicit LAN webhook/PC bridge traffic)
    12. **Licences & Release Build** (Full-width stacked build metadata for `com.rsrprojects.knockdock`, `RELEASE` build type, `Min SDK 24 / Target SDK 36`, and third-party open-source dependency notices)

---

## Fastlane Directory & Store Metadata

```text
fastlane/
├── Appfile                                          # Package ID: com.rsrprojects.knockdock
├── Fastfile                                         # Lanes: test, build_debug, build_release, internal, deploy
├── screenshots/                                     # Phone Screenshots directory (1.png .. 6.png)
│   └── .gitkeep
└── metadata/
    └── android/
        └── en-US/
            ├── title.txt                            # "KnockDock"
            ├── short_description.txt                # 80-char Play Store short summary
            ├── full_description.txt                 # Full Play Store listing description
            ├── changelogs/
            │   ├── default.txt
            │   └── 1.txt
            └── images/
                ├── .gitkeep
                └── phoneScreenshots/                # Fastlane Phone Screenshots (1.png .. 6.png)
                    └── .gitkeep
```

---

## Building, Signing & Running Tests

![Gradle Wrapper](https://img.shields.io/badge/Gradle_Wrapper-8.13_(gradle--wrapper.jar_Included)-00E5FF?style=plastic&labelColor=0B132B&logo=gradle&logoColor=00E5FF)
![Release Signing](https://img.shields.io/badge/Release_Signing-my--upload--key.jks_(Alias%3A_upload)-10B981?style=plastic&labelColor=0B132B)

```bash
# Verify Gradle Wrapper
./gradlew --version

# Run unit & regression test suites
./gradlew :app:testDebugUnitTest

# Build signed Release APK using the included upload keystore
KEYSTORE_PATH="$(pwd)/my-upload-key.jks" \
STORE_PASSWORD="android" \
KEY_PASSWORD="android" \
./gradlew :app:assembleRelease

# Copy signed Release APK to repository root
cp app/build/outputs/apk/release/app-release.apk ./KnockDock.apk
```
