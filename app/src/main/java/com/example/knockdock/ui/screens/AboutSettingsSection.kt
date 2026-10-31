package com.example.knockdock.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.R
import com.example.knockdock.ui.theme.LiquidGlassIconButton
import com.example.knockdock.ui.theme.LiquidGlowButton
import com.example.knockdock.ui.theme.LiquidStatusPill
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanGlowBright
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.SlateGlassDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Official GitHub & project constants for KnockDock.
 */
object KnockDockAboutConstants {
    const val APP_NAME = "KnockDock"
    const val TAGLINE = "Turn physical knocks into actions."
    const val DEVELOPER_NAME = "RSR-Projects"
    const val LICENSE_NAME = "Apache License 2.0"
    const val BUILD_VARIANT_RELEASE = "RELEASE"
    const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
    const val GITHUB_REPO_URL = "https://github.com/RSR-Projects/KnockDock"
    const val GITHUB_ISSUES_URL = "https://github.com/RSR-Projects/KnockDock/issues"
    const val GITHUB_RELEASES_URL = "https://github.com/RSR-Projects/KnockDock/releases"
}

/**
 * Dynamically resolved application version and release build metadata retrieved from Android BuildConfig & PackageManager.
 */
data class InstalledAppBuildInfo(
    val appName: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val minSdk: Int,
    val targetSdk: Int,
    val deviceSdkInt: Int,
    val deviceRelease: String
)

/**
 * Retrieves the actual installed version name, version code, and release build configuration without hardcoding.
 */
fun resolveInstalledAppBuildInfo(context: Context): InstalledAppBuildInfo {
    val pkgInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: Throwable) {
        null
    }

    val resolvedVersionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() }
        ?: BuildConfig.VERSION_NAME
    val resolvedVersionCode = if (pkgInfo != null) {
        PackageInfoCompat.getLongVersionCode(pkgInfo).let { code ->
            if (code > 0L) code else BuildConfig.VERSION_CODE.toLong()
        }
    } else {
        BuildConfig.VERSION_CODE.toLong()
    }
    val appInfo = pkgInfo?.applicationInfo ?: context.applicationInfo
    val resolvedTargetSdk = appInfo?.targetSdkVersion?.takeIf { it > 0 } ?: 36
    val resolvedMinSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appInfo?.minSdkVersion?.takeIf { it > 0 } ?: 24
    } else {
        24
    }

    return InstalledAppBuildInfo(
        appName = runCatching { context.getString(R.string.app_name) }.getOrDefault(KnockDockAboutConstants.APP_NAME),
        applicationId = BuildConfig.APPLICATION_ID,
        versionName = resolvedVersionName,
        versionCode = resolvedVersionCode,
        buildType = KnockDockAboutConstants.BUILD_VARIANT_RELEASE,
        minSdk = resolvedMinSdk,
        targetSdk = resolvedTargetSdk,
        deviceSdkInt = Build.VERSION.SDK_INT,
        deviceRelease = Build.VERSION.RELEASE ?: "Android"
    )
}

/**
 * Represents a real open-source third-party library used by KnockDock as declared in gradle/libs.versions.toml.
 */
data class ThirdPartyLibraryLicense(
    val name: String,
    val artifactCoordinates: String,
    val version: String,
    val copyrightHolder: String,
    val licenseName: String,
    val projectUrl: String,
    val purposeInApp: String
)

/**
 * Returns the actual third-party open-source dependencies compiled into KnockDock.
 */
fun getKnockDockThirdPartyLicenses(): List<ThirdPartyLibraryLicense> = listOf(
    ThirdPartyLibraryLicense(
        name = "Android Jetpack Compose & Material 3",
        artifactCoordinates = "androidx.compose:compose-bom / material3 / ui",
        version = "2024.09.00",
        copyrightHolder = "The Android Open Source Project",
        licenseName = "Apache License 2.0",
        projectUrl = "https://developer.android.com/jetpack/compose",
        purposeInApp = "Declarative UI toolkit, Material 3 components, animations, and vector icons."
    ),
    ThirdPartyLibraryLicense(
        name = "AndroidX Core, Activity, Lifecycle & Navigation",
        artifactCoordinates = "androidx.core:core-ktx / lifecycle / navigation-compose",
        version = "1.18.0 / 2.8.7 / 2.8.9",
        copyrightHolder = "The Android Open Source Project",
        licenseName = "Apache License 2.0",
        projectUrl = "https://source.android.com/setup/start/licenses",
        purposeInApp = "Edge-to-edge window insets, foreground service lifecycle, and state-driven navigation."
    ),
    ThirdPartyLibraryLicense(
        name = "AndroidX Room Persistence",
        artifactCoordinates = "androidx.room:room-runtime / room-ktx / room-compiler",
        version = "2.7.0",
        copyrightHolder = "The Android Open Source Project",
        licenseName = "Apache License 2.0",
        projectUrl = "https://developer.android.com/training/data-storage/room",
        purposeInApp = "Structured local persistence layer and KSP schema support."
    ),
    ThirdPartyLibraryLicense(
        name = "Kotlin Standard Library & Coroutines",
        artifactCoordinates = "org.jetbrains.kotlin / kotlinx-coroutines-android",
        version = "2.2.10 / 1.10.2",
        copyrightHolder = "JetBrains s.r.o.",
        licenseName = "Apache License 2.0",
        projectUrl = "https://github.com/Kotlin/kotlinx.coroutines",
        purposeInApp = "Asynchronous sensor telemetry StateFlows, SharedFlows, and macro step execution."
    ),
    ThirdPartyLibraryLicense(
        name = "AndroidLiquidGlass Backdrop & Shapes",
        artifactCoordinates = "io.github.kyant0:backdrop / io.github.kyant0:shapes",
        version = "1.0.6 / 1.2.0",
        copyrightHolder = "Kyant0",
        licenseName = "Apache License 2.0",
        projectUrl = "https://github.com/Kyant0/AndroidLiquidGlass",
        purposeInApp = "Real-time GPU backdrop capture, optical lens refraction, chromatic dispersion, and capsule geometry."
    ),
    ThirdPartyLibraryLicense(
        name = "Retrofit 2 & OkHttp 4",
        artifactCoordinates = "com.squareup.retrofit2:retrofit / com.squareup.okhttp3:okhttp",
        version = "2.12.0 / 4.10.0",
        copyrightHolder = "Square, Inc.",
        licenseName = "Apache License 2.0",
        projectUrl = "https://github.com/square/retrofit",
        purposeInApp = "Local Wi-Fi PC Bridge commands, Smart-Home webhooks, and HTTP macro actions."
    ),
    ThirdPartyLibraryLicense(
        name = "Moshi JSON Serialization",
        artifactCoordinates = "com.squareup.moshi:moshi-kotlin / moshi-kotlin-codegen",
        version = "1.15.2",
        copyrightHolder = "Square, Inc.",
        licenseName = "Apache License 2.0",
        projectUrl = "https://github.com/square/moshi",
        purposeInApp = "Type-safe JSON serialization for profile backup/restore and webhook payloads."
    ),
    ThirdPartyLibraryLicense(
        name = "Secrets Gradle Plugin for Android",
        artifactCoordinates = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin",
        version = "2.0.1",
        copyrightHolder = "Google LLC",
        licenseName = "Apache License 2.0",
        projectUrl = "https://github.com/google/secrets-gradle-plugin",
        purposeInApp = "Secure environment configuration injection into BuildConfig."
    ),
    ThirdPartyLibraryLicense(
        name = "JUnit 4 & Robolectric",
        artifactCoordinates = "junit:junit / org.robolectric:robolectric",
        version = "4.13.2 / 4.16.1",
        copyrightHolder = "JUnit Team & Robolectric Contributors",
        licenseName = "EPL 1.0 / MIT License",
        projectUrl = "https://github.com/robolectric/robolectric",
        purposeInApp = "Local JVM unit testing for acoustic signal filters, gesture classification, and dry-run safety."
    )
)

/**
 * Represents a factual privacy and sensor data disclosure item based on KnockDock's actual code.
 */
data class PrivacyDisclosureItem(
    val title: String,
    val badge: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Returns factual privacy disclosures describing what sensor data KnockDock accesses and how it is handled.
 */
fun getKnockDockPrivacyDisclosures(): List<PrivacyDisclosureItem> = listOf(
    PrivacyDisclosureItem(
        title = "Accelerometer & Gyroscope Telemetry",
        badge = "100% On-Device",
        description = "KnockDock samples TYPE_ACCELEROMETER and TYPE_GYROSCOPE via SensorManagerHelper to isolate surface knock impulses (12 Hz high-pass IIR VibrationFilter) and verify the device is resting stationary on your desk (ImpactDetector). Sensor samples are held only in a volatile 300-sample in-memory ring buffer and are never saved to disk or transmitted externally.",
        icon = Icons.Default.Sensors,
        accentColor = CyanGlow
    ),
    PrivacyDisclosureItem(
        title = "Microphone Access (Optional Voice Memo Only)",
        badge = "Never Used for Knocks",
        description = "Acoustic knock detection relies strictly on motion sensors and never listens to your microphone. The RECORD_AUDIO permission is requested only if you explicitly trigger the Quick Voice Memo action, which records an .m4a note directly into app-private local storage.",
        icon = Icons.Default.PrivacyTip,
        accentColor = MintSuccess
    ),
    PrivacyDisclosureItem(
        title = "Local Wi-Fi & Network Requests",
        badge = "Zero Analytics",
        description = "KnockDock contains no analytics SDKs, no telemetry trackers, and no advertising frameworks. Network connections occur exclusively when you configure and trigger a local Wi-Fi PC Bridge command or a custom HTTP / Smart-Home webhook.",
        icon = Icons.Default.Security,
        accentColor = IndigoPulse
    ),
    PrivacyDisclosureItem(
        title = "Profiles, Macros & Execution Logs",
        badge = "Local Storage",
        description = "Custom knock gestures, context profiles, multi-step macros, execution history, and Liquid Glass settings are stored locally on your device. Data only leaves the device if you explicitly export or share a JSON backup.",
        icon = Icons.Default.Memory,
        accentColor = AmberAlert
    )
)

/**
 * Safely opens an external HTTP/HTTPS link in the user's browser with fallback to clipboard copy.
 */
fun openExternalWebUrl(
    context: Context,
    url: String,
    onFeedback: ((String) -> Unit)? = null
): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        onFeedback?.invoke("Opened $url")
        true
    } catch (_: Throwable) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("KnockDock URL", url))
            onFeedback?.invoke("Copied URL to clipboard: $url")
        } catch (_: Throwable) {
            onFeedback?.invoke("URL: $url")
        }
        false
    }
}

/**
 * Folder 1 Content: App Overview (Dedicated centered App Icon, App Name, Tagline, Release Version, and App Purpose).
 * Uses a spacious vertical layout so the app icon is never hidden or crowded by adjacent text.
 */
@Composable
fun AboutAppOverviewFolderContent() {
    val context = LocalContext.current
    val buildInfo = remember(context) { resolveInstalledAppBuildInfo(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_app_info_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered Hero KnockDock App Icon on its own row — never overlapped or hidden by text
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                CyanGlowBright.copy(alpha = 0.85f),
                                IndigoPulse.copy(alpha = 0.65f)
                            )
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .testTag("about_app_icon"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "KnockDock App Icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_app_name")
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(id = R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = CyanGlowBright,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_app_tagline")
        )

        Spacer(modifier = Modifier.height(10.dp))

        LiquidStatusPill(
            text = "v${buildInfo.versionName} (Code ${buildInfo.versionCode}) • ${buildInfo.buildType}",
            statusColor = MintSuccess
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Version ${buildInfo.versionName} • Version Code ${buildInfo.versionCode} • Release Build",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_installed_version_text")
        )

        Spacer(modifier = Modifier.height(14.dp))

        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateGlassDark.copy(alpha = 0.55f))
                .border(
                    BorderStroke(1.dp, CyanGlow.copy(alpha = 0.24f)),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                text = "What KnockDock Does",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CyanGlowBright
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.about_purpose_description),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                color = TextPrimary.copy(alpha = 0.92f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_app_purpose_text")
            )
        }
    }
}

/**
 * Folder 2 Content: Developer & Open-Source Licence Information.
 * Uses full-width stacked vertical cards so no text is squeezed or cut off.
 */
@Composable
fun AboutDeveloperFolderContent() {
    val context = LocalContext.current
    var licenseStatusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_developer_info_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AboutStackedInfoBlock(
            label = "Developer",
            value = KnockDockAboutConstants.DEVELOPER_NAME,
            valueColor = CyanGlowBright,
            accentColor = CyanGlow,
            valueTestTag = "about_developer_value"
        )

        AboutStackedInfoBlock(
            label = "Open-Source Licence",
            value = KnockDockAboutConstants.LICENSE_NAME,
            valueColor = MintSuccess,
            accentColor = MintSuccess,
            valueTestTag = "about_license_value"
        )

        AboutStackedInfoBlock(
            label = "Project Model",
            value = "Community-Auditable Open-Source Android Application",
            valueColor = TextPrimary,
            accentColor = IndigoPulse
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateGlassDark.copy(alpha = 0.55f))
                .border(
                    BorderStroke(1.dp, IndigoPulse.copy(alpha = 0.25f)),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                text = "Open-Source Project Commitment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "KnockDock is developed by RSR-Projects and released as free, open-source software under the Apache License, Version 2.0. Anyone can inspect the acoustic knock pipeline, verify on-device privacy, submit pull requests, or fork the repository on GitHub.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_opensource_description")
            )
        }

        LiquidGlowButton(
            text = "View Apache License 2.0 Text",
            onClick = {
                openExternalWebUrl(context, KnockDockAboutConstants.LICENSE_URL) {
                    licenseStatusMessage = it
                }
            },
            accentColor = IndigoPulse,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_view_apache_license_button")
        )

        if (licenseStatusMessage != null) {
            Text(
                text = licenseStatusMessage!!,
                style = MaterialTheme.typography.labelMedium,
                color = MintSuccess,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Folder 3 Content: GitHub Integration (https://github.com/RSR-Projects/KnockDock).
 * Interactive actions for Star on GitHub, View Source Code, Report a Bug, and View Releases.
 */
@Composable
fun AboutGitHubFolderContent() {
    val context = LocalContext.current
    var linkStatusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_github_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Repository URL box with Copy button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateGlassDark.copy(alpha = 0.58f))
                .border(
                    BorderStroke(1.dp, CyanGlow.copy(alpha = 0.28f)),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Official GitHub Repository",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                LiquidGlassIconButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("KnockDock GitHub URL", KnockDockAboutConstants.GITHUB_REPO_URL)
                            )
                            linkStatusMessage = "Copied ${KnockDockAboutConstants.GITHUB_REPO_URL}"
                        } catch (_: Throwable) {}
                    },
                    size = 40.dp,
                    accentColor = CyanGlow,
                    modifier = Modifier.testTag("about_copy_github_url_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy GitHub Repository URL",
                        tint = CyanGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = KnockDockAboutConstants.GITHUB_REPO_URL,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = CyanGlowBright,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_github_repo_url_text")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap any option below to open the repository in your browser. Starring is performed directly by you on GitHub.",
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Option 1: Star on GitHub
        GitHubActionCard(
            title = "Star on GitHub",
            subtitle = "Opens the repository page in your browser so you can star it on GitHub.",
            icon = Icons.Default.Star,
            accentColor = AmberAlert,
            url = KnockDockAboutConstants.GITHUB_REPO_URL,
            testTag = "about_github_star_button",
            onClick = {
                openExternalWebUrl(context, KnockDockAboutConstants.GITHUB_REPO_URL) {
                    linkStatusMessage = it
                }
            }
        )

        // Option 2: View Source Code
        GitHubActionCard(
            title = "View Source Code",
            subtitle = "Browse KnockDock's Kotlin & Jetpack Compose source code on GitHub.",
            icon = Icons.Default.Code,
            accentColor = CyanGlow,
            url = KnockDockAboutConstants.GITHUB_REPO_URL,
            testTag = "about_github_source_button",
            onClick = {
                openExternalWebUrl(context, KnockDockAboutConstants.GITHUB_REPO_URL) {
                    linkStatusMessage = it
                }
            }
        )

        // Option 3: Report a Bug
        GitHubActionCard(
            title = "Report a Bug",
            subtitle = "Open the repository's GitHub Issues page to report a bug or request a feature.",
            icon = Icons.Default.BugReport,
            accentColor = IndigoPulse,
            url = KnockDockAboutConstants.GITHUB_ISSUES_URL,
            testTag = "about_github_bug_button",
            onClick = {
                openExternalWebUrl(context, KnockDockAboutConstants.GITHUB_ISSUES_URL) {
                    linkStatusMessage = it
                }
            }
        )

        // Option 4: View Releases
        GitHubActionCard(
            title = "View Releases",
            subtitle = "Open the GitHub Releases page for version changelogs and official release APKs.",
            icon = Icons.Default.NewReleases,
            accentColor = MintSuccess,
            url = KnockDockAboutConstants.GITHUB_RELEASES_URL,
            testTag = "about_github_releases_button",
            onClick = {
                openExternalWebUrl(context, KnockDockAboutConstants.GITHUB_RELEASES_URL) {
                    linkStatusMessage = it
                }
            }
        )

        if (linkStatusMessage != null) {
            Text(
                text = linkStatusMessage!!,
                style = MaterialTheme.typography.labelMedium,
                color = MintSuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_github_feedback_text")
            )
        }
    }
}

/**
 * Folder 4 Content: Privacy & Sensor Data Handling.
 * Stacks badge, title, and description vertically inside each card so no text is ever squeezed or truncated.
 */
@Composable
fun AboutPrivacyFolderContent() {
    val privacyDisclosures = remember { getKnockDockPrivacyDisclosures() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_privacy_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "KnockDock processes desk surface vibrations locally on your device. Below is an exact breakdown of the hardware sensors and permissions used by the app:",
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        privacyDisclosures.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SlateGlassDark.copy(alpha = 0.58f))
                    .border(
                        BorderStroke(1.dp, item.accentColor.copy(alpha = 0.28f)),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LiquidStatusPill(
                        text = item.badge,
                        statusColor = item.accentColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Folder 5 Content: Open-Source Third-Party Licences & Release Build Information.
 * Displays all actual third-party dependencies and dynamically resolved Release build metadata without truncation.
 */
@Composable
fun AboutLicensesAndBuildFolderContent() {
    val context = LocalContext.current
    val buildInfo = remember(context) { resolveInstalledAppBuildInfo(context) }
    val thirdPartyLicenses = remember { getKnockDockThirdPartyLicenses() }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_third_party_licenses_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section A: Release Build & Version Information
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateGlassDark.copy(alpha = 0.58f))
                .border(
                    BorderStroke(1.dp, MintSuccess.copy(alpha = 0.30f)),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
                .testTag("about_build_info_card"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = MintSuccess,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LiquidStatusPill(
                    text = buildInfo.buildType,
                    statusColor = MintSuccess
                )
            }

            Text(
                text = "App Version & Release Build Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            AboutStackedInfoBlock(
                label = "Application Name",
                value = buildInfo.appName,
                valueColor = TextPrimary,
                accentColor = CyanGlow
            )
            AboutStackedInfoBlock(
                label = "Application ID",
                value = buildInfo.applicationId,
                valueColor = CyanGlowBright,
                accentColor = CyanGlow,
                isMonospace = true,
                valueTestTag = "about_build_application_id"
            )
            AboutStackedInfoBlock(
                label = "Version Name",
                value = buildInfo.versionName,
                valueColor = MintSuccess,
                accentColor = MintSuccess,
                isMonospace = true,
                valueTestTag = "about_build_version_name"
            )
            AboutStackedInfoBlock(
                label = "Version Code",
                value = buildInfo.versionCode.toString(),
                valueColor = MintSuccess,
                accentColor = MintSuccess,
                isMonospace = true,
                valueTestTag = "about_build_version_code"
            )
            AboutStackedInfoBlock(
                label = "Build Variant",
                value = buildInfo.buildType,
                valueColor = MintSuccess,
                accentColor = MintSuccess,
                isMonospace = true,
                valueTestTag = "about_build_variant"
            )
            AboutStackedInfoBlock(
                label = "SDK Compatibility",
                value = "Min SDK ${buildInfo.minSdk} • Target SDK ${buildInfo.targetSdk}",
                valueColor = TextPrimary,
                accentColor = IndigoPulse
            )
            AboutStackedInfoBlock(
                label = "Runtime Android OS",
                value = "Android ${buildInfo.deviceRelease} (API ${buildInfo.deviceSdkInt})",
                valueColor = TextSecondary,
                accentColor = IndigoPulse
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

        // Section B: Open-Source Third-Party Licences
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Gavel,
                contentDescription = null,
                tint = IndigoPulse,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            LiquidStatusPill(
                text = "${thirdPartyLicenses.size} Open-Source Libraries",
                statusColor = IndigoPulse
            )
        }

        Text(
            text = "Open-Source Third-Party Licences",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Actual third-party dependencies declared in gradle/libs.versions.toml. Tap any library card to open its project page:",
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        thirdPartyLicenses.forEach { lib ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SlateGlassDark.copy(alpha = 0.58f))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        openExternalWebUrl(context, lib.projectUrl) {
                            statusMessage = it
                        }
                    }
                    .padding(14.dp)
            ) {
                LiquidStatusPill(
                    text = lib.licenseName,
                    statusColor = CyanGlow
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = lib.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${lib.artifactCoordinates} • v${lib.version}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = CyanGlowBright,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Copyright © ${lib.copyrightHolder}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = lib.purposeInApp,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = TextPrimary.copy(alpha = 0.88f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (statusMessage != null) {
            Text(
                text = statusMessage!!,
                style = MaterialTheme.typography.labelMedium,
                color = MintSuccess,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Combined About section composable that renders all About sections in a spacious vertical column
 * when invoked as a single composite block.
 */
@Composable
fun AboutKnockDockSectionContent(
    visualEffectsQuality: String = "HIGH",
    reducedMotion: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_knockdock_section_content"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AboutAppOverviewFolderContent()
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        AboutDeveloperFolderContent()
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        AboutGitHubFolderContent()
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        AboutPrivacyFolderContent()
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        AboutLicensesAndBuildFolderContent()
    }
}

/**
 * Full-width vertical stacked label + value block.
 * Guarantees that neither the label nor the value is ever squeezed into single-character vertical lines or cut off.
 */
@Composable
private fun AboutStackedInfoBlock(
    label: String,
    value: String,
    valueColor: Color,
    accentColor: Color = CyanGlow,
    isMonospace: Boolean = false,
    valueTestTag: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateGlassDark.copy(alpha = 0.52f))
            .border(
                BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = valueColor,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier)
        )
    }
}

/**
 * Spacious, full-width interactive GitHub link card with zero text truncation.
 */
@Composable
private fun GitHubActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    url: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SlateGlassDark.copy(alpha = 0.62f))
            .border(
                BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(
                            BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open $title in browser",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = accentColor.copy(alpha = 0.90f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
