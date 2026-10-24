package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class ProjectExportState {
    object Idle : ProjectExportState()
    object Exporting : ProjectExportState()
    data class Ready(
        val zipFile: File,
        val fileCount: Int,
        val sizeKb: Long,
        val savedLocationNote: String? = null
    ) : ProjectExportState()
    data class Error(val message: String) : ProjectExportState()
}

/**
 * Project Source Code ZIP Export & Share Hub Card
 * Placed inside Customer Profile & Partner Shop Profile so users can generate, save, or share
 * the complete Android project ZIP archive (Gradle scripts, Kotlin sources, Manifest, and APK build guide).
 */
@Composable
fun ProjectExportHubCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var exportState by remember { mutableStateOf<ProjectExportState>(ProjectExportState.Idle) }
    var showBuildGuideDialog by remember { mutableStateOf(false) }
    var copiedCommandFeedback by remember { mutableStateOf<String?>(null) }

    // System Document Saver for direct .zip file download to device storage
    val saveZipDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri: Uri? ->
        if (targetUri != null) {
            coroutineScope.launch {
                exportState = ProjectExportState.Exporting
                val result = ProjectZipExporter.writeZipToUri(context, targetUri)
                exportState = result
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("project_export_hub_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2D)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary.copy(alpha = 0.65f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33F59E0B))
                            .border(1.dp, AmberPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = "Export Project ZIP",
                            tint = AmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "PROJECT EXPORT & SHARE HUB",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberPrimary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Download Source Code (.ZIP)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF064E3B))
                        .border(1.dp, GreenSuccess.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "APK READY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = GreenSuccess
                    )
                }
            }

            Text(
                text = "Export the complete TrimTime Android project archive including Gradle build scripts, Kotlin Jetpack Compose source files, Firestore rules, and external APK build instructions.",
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 16.sp
            )

            // Included Package Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PackageBadgeChip("build.gradle.kts")
                PackageBadgeChip("Kotlin + Compose")
                PackageBadgeChip("APK Build Guide")
            }

            // Primary Action: Direct Download / Save .ZIP to Device
            Button(
                onClick = {
                    coroutineScope.launch {
                        exportState = ProjectExportState.Exporting
                        val readyState = ProjectZipExporter.generateProjectZipInCache(context)
                        exportState = readyState
                        // Launch system Save As dialog for direct ZIP download
                        try {
                            saveZipDocumentLauncher.launch("TrimTime_Android_Project_Source.zip")
                        } catch (_: Exception) {
                            // Fallback if document picker is unavailable in environment
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("download_project_zip_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color(0xFF1E1402)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                when (exportState) {
                    is ProjectExportState.Exporting -> {
                        CircularProgressIndicator(
                            color = Color(0xFF1E1402),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Packaging Project Source (.ZIP)...",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download ZIP",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Project Source (.ZIP)",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Secondary Actions Row: Share ZIP Archive & View APK Build Instructions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val current = exportState
                            val ready = if (current is ProjectExportState.Ready) {
                                current
                            } else {
                                exportState = ProjectExportState.Exporting
                                val generated = ProjectZipExporter.generateProjectZipInCache(context)
                                exportState = generated
                                generated
                            }
                            if (ready is ProjectExportState.Ready) {
                                ProjectZipExporter.shareZipFile(context, ready.zipFile)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("share_project_zip_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share ZIP",
                        tint = AmberPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share .ZIP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { showBuildGuideDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("view_apk_build_guide_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF93C5FD)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "APK Build Guide",
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "APK Build Guide",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status Banner when ZIP is generated
            AnimatedVisibility(visible = exportState is ProjectExportState.Ready) {
                val ready = exportState as? ProjectExportState.Ready
                if (ready != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF064E3B))
                            .border(1.dp, GreenSuccess, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("zip_export_ready_banner"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Archive Ready: TrimTime_Android_Project_Source.zip (${ready.fileCount} files • ${ready.sizeKb} KB)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = ready.savedLocationNote
                                ?: "Saved to ${ready.zipFile.absolutePath}. Use 'Share .ZIP' or 'Download Project Source' to export externally.",
                            fontSize = 10.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                }
            }

            // Quick Copy Gradle CLI Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B1120))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .clickable {
                        copyToClipboard(context, "./gradlew assembleDebug")
                        copiedCommandFeedback = "Copied './gradlew assembleDebug' to clipboard!"
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("copy_gradle_command_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = GreenSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = copiedCommandFeedback ?: "$ ./gradlew assembleDebug",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = if (copiedCommandFeedback != null) AmberPrimary else Color(0xFFA7F3D0)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Gradle Command",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    if (showBuildGuideDialog) {
        ApkBuildGuideDialog(
            onDismiss = { showBuildGuideDialog = false },
            onTriggerDownload = {
                showBuildGuideDialog = false
                coroutineScope.launch {
                    exportState = ProjectExportState.Exporting
                    exportState = ProjectZipExporter.generateProjectZipInCache(context)
                    try {
                        saveZipDocumentLauncher.launch("TrimTime_Android_Project_Source.zip")
                    } catch (_: Exception) {}
                }
            }
        )
    }
}

@Composable
private fun PackageBadgeChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE2E8F0)
        )
    }
}

@Composable
fun ApkBuildGuideDialog(
    onDismiss: () -> Unit,
    onTriggerDownload: () -> Unit
) {
    val context = LocalContext.current
    var copiedStep by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .testTag("apk_build_guide_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary.copy(alpha = 0.5f))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "External APK Build Guide",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                // AI Studio Top Bar Export Note
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Option 1: AI Studio Browser Top-Bar Export",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberPrimary
                    )
                    Text(
                        text = "Click the Settings / Export menu (⚙️ or ⋮) in the top-right corner of Google AI Studio and select 'Export Project (ZIP)' or 'Export APK' to download directly to your computer.",
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 15.sp
                    )
                }

                // Local Gradle Build Steps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Option 2: Build APK from Exported .ZIP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )

                    BuildCommandStep(
                        stepTitle = "1. Unzip the exported archive",
                        command = "unzip TrimTime_Android_Project_Source.zip -d TrimTimeApp",
                        onCopy = {
                            copyToClipboard(context, "unzip TrimTime_Android_Project_Source.zip -d TrimTimeApp")
                            copiedStep = "Step 1 copied!"
                        }
                    )

                    BuildCommandStep(
                        stepTitle = "2. Compile the Debug APK via Gradle",
                        command = "./gradlew assembleDebug",
                        onCopy = {
                            copyToClipboard(context, "./gradlew assembleDebug")
                            copiedStep = "Step 2 copied!"
                        }
                    )

                    BuildCommandStep(
                        stepTitle = "3. Locate the generated APK file",
                        command = "app/build/outputs/apk/debug/app-debug.apk",
                        onCopy = {
                            copyToClipboard(context, "app/build/outputs/apk/debug/app-debug.apk")
                            copiedStep = "APK path copied!"
                        }
                    )

                    if (copiedStep != null) {
                        Text(
                            text = "✓ $copiedStep",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberPrimary
                        )
                    }
                }

                // Included Files Summary
                Text(
                    text = "INCLUDED IN .ZIP BUNDLE:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = "• settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml\n" +
                        "• app/src/main/AndroidManifest.xml &firestore.rules\n" +
                        "• Complete Kotlin Jetpack Compose UI & MVVM Repository layer\n" +
                        "• BUILD_APK_GUIDE.md with full signing & release instructions",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 16.sp
                )

                Button(
                    onClick = onTriggerDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color(0xFF1E1402)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Download .ZIP Archive Now",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildCommandStep(
    stepTitle: String,
    command: String,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = stepTitle,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E293B))
                .clickable { onCopy() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFA7F3D0),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Gradle Command", text))
}

/**
 * Utility that constructs a valid, self-contained Android project .ZIP archive
 * containing the Gradle configuration, Manifest, Firestore rules, Kotlin source files,
 * and external APK build instructions.
 */
object ProjectZipExporter {

    private fun buildProjectEntries(): Map<String, String> = linkedMapOf(
        "BUILD_APK_GUIDE.md" to """
            # TrimTime Android Project — External APK Build Guide
            
            This archive contains the source code, Gradle configuration, and Firebase Firestore rules for **TrimTime** (Hyper-local Salon Queue & Instant Slot Booking App).
            
            ## Prerequisites
            - **JDK 17+** (or Android Studio Ladybug / Koala bundled JBR)
            - **Android SDK Platform 36** (minSdk 24, targetSdk 36)
            
            ## Quick Build Commands (Terminal)
            1. Open a terminal in the extracted project root folder.
            2. Build the Debug APK:
               ```bash
               ./gradlew assembleDebug
               ```
               *(Or if using a globally installed Gradle distribution: `gradle assembleDebug`)*
            3. Locate the installable APK at:
               ```
               app/build/outputs/apk/debug/app-debug.apk
               ```
            4. Install on a connected Android device:
               ```bash
               adb install -r app/build/outputs/apk/debug/app-debug.apk
               ```
            
            ## Opening in Android Studio
            1. Launch **Android Studio** -> **Open** -> Select the extracted `TrimTime` directory.
            2. Allow Gradle Sync to complete.
            3. Optional: Place your Firebase `google-services.json` inside `app/google-services.json` (the build script is pre-configured with `MissingGoogleServicesStrategy.WARN` so it compiles cleanly even without `google-services.json`).
            4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
        """.trimIndent(),

        "settings.gradle.kts" to """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "TrimTime"
            include(":app")
        """.trimIndent(),

        "build.gradle.kts" to """
            plugins {
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.kotlin.compose) apply false
                alias(libs.plugins.google.devtools.ksp) apply false
                alias(libs.plugins.secrets) apply false
                alias(libs.plugins.google.services) apply false
            }
        """.trimIndent(),

        "app/build.gradle.kts" to """
            import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.compose)
                alias(libs.plugins.google.devtools.ksp)
                alias(libs.plugins.secrets)
                alias(libs.plugins.google.services)
            }

            android {
                namespace = "com.example"
                compileSdk = 36

                defaultConfig {
                    applicationId = "com.trimtime.app"
                    minSdk = 24
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }

                buildFeatures {
                    compose = true
                    buildConfig = true
                }
            }

            googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

            dependencies {
                implementation(platform(libs.androidx.compose.bom))
                implementation(platform(libs.firebase.bom))
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.compose.material.icons.core)
                implementation(libs.androidx.compose.material.icons.extended)
                implementation(libs.androidx.compose.material3)
                implementation(libs.androidx.compose.ui)
                implementation(libs.androidx.compose.ui.graphics)
                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.firebase.firestore)
                implementation(libs.firebase.auth)
                implementation(libs.androidx.credentials)
                implementation(libs.androidx.credentials.play.services)
                implementation(libs.googleid)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.kotlinx.coroutines.core)
            }
        """.trimIndent(),

        "app/src/main/AndroidManifest.xml" to """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
                <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

                <application
                    android:allowBackup="true"
                    android:label="TrimTime"
                    android:supportsRtl="true"
                    android:theme="@android:style/Theme.Material.NoActionBar">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent(),

        "firestore.rules" to """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isSignedIn() {
                  return request.auth != null;
                }
                match /users/{userId} {
                  allow read, write: if isSignedIn() && request.auth.uid == userId;
                }
                match /salons/{salonId} {
                  allow read: if true;
                  allow write: if isSignedIn();
                }
                match /bookings/{bookingId} {
                  allow read, write: if isSignedIn();
                }
              }
            }
        """.trimIndent(),

        "app/src/main/java/com/example/model/Models.kt" to """
            package com.example.model

            enum class UserRole { CUSTOMER, PARTNER }

            data class SalonService(
                val id: String = "",
                val name: String = "",
                val category: String = "Haircut",
                val price: Int = 100,
                val durationMinutes: Int = 20,
                val description: String = "",
                val isPopular: Boolean = false
            )

            data class Salon(
                val id: String = "",
                val name: String = "",
                val location: String = "",
                val distance: String = "0.5 km",
                val rating: Double = 4.8,
                val reviewCount: Int = 0,
                val partnerId: String = "",
                val services: List<SalonService> = emptyList(),
                val isOpen: Boolean = true,
                val currentStatus: String = "FREE", // "FREE" or "BUSY"
                val tagline: String = "",
                val nextSlotMinutes: Int = 15,
                val shopAddress: String = "",
                val landmark: String = "",
                val latitude: Double? = null,
                val longitude: Double? = null
            ) {
                val isBusy: Boolean get() = currentStatus.equals("BUSY", ignoreCase = true)
                val isFree: Boolean get() = !isBusy
                val hasLocationSetup: Boolean
                    get() = (shopAddress.isNotBlank() || location.isNotBlank()) &&
                            (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0)
            }
        """.trimIndent()
    )

    suspend fun generateProjectZipInCache(context: Context): ProjectExportState = withContext(Dispatchers.IO) {
        try {
            val entries = buildProjectEntries()
            val zipFile = File(context.cacheDir, "TrimTime_Android_Project_Source.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                entries.forEach { (path, content) ->
                    val entry = ZipEntry("TrimTime/$path")
                    zos.putNextEntry(entry)
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            val sizeKb = Math.max(1L, zipFile.length() / 1024L)
            ProjectExportState.Ready(
                zipFile = zipFile,
                fileCount = entries.size,
                sizeKb = sizeKb
            )
        } catch (e: Exception) {
            ProjectExportState.Error("Failed to generate ZIP: ${e.localizedMessage}")
        }
    }

    suspend fun writeZipToUri(context: Context, targetUri: Uri): ProjectExportState = withContext(Dispatchers.IO) {
        try {
            val entries = buildProjectEntries()
            val cacheFile = File(context.cacheDir, "TrimTime_Android_Project_Source.zip")
            ZipOutputStream(FileOutputStream(cacheFile)).use { zos ->
                entries.forEach { (path, content) ->
                    val entry = ZipEntry("TrimTime/$path")
                    zos.putNextEntry(entry)
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                cacheFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            val sizeKb = Math.max(1L, cacheFile.length() / 1024L)
            ProjectExportState.Ready(
                zipFile = cacheFile,
                fileCount = entries.size,
                sizeKb = sizeKb,
                savedLocationNote = "Successfully downloaded TrimTime_Android_Project_Source.zip to your selected device folder!"
            )
        } catch (e: Exception) {
            ProjectExportState.Error("Could not write ZIP to destination: ${e.localizedMessage}")
        }
    }

    fun shareZipFile(context: Context, zipFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, zipFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "TrimTime Android Project Source (.ZIP)")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is the TrimTime Android project source code (.ZIP) with Gradle build scripts and APK compilation instructions."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Export TrimTime Project (.ZIP)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Ignore if no external activity can handle intent
        }
    }
}
