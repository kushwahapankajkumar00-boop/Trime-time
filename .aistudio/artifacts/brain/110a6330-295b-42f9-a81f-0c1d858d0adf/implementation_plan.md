# TrimTime Project Source Code ZIP Export & Share Hub

An integrated **Project Export & Share Hub** inside the Profile settings that generates a downloadable, self-contained `.zip` archive of the Android project—complete with Gradle build scripts, Kotlin source code, resources, and step-by-step external APK build instructions.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Platform Native Export vs. In-App Export Utility**
> - **Confirmed Choice 1 (Placement)**: Add a dedicated **Project Export & Share Hub** card inside Profile settings (accessible from both Customer Profile and Partner Shop Profile tabs).
> - **Confirmed Choice 2 (Archive Contents)**: Package the Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`), AndroidManifest, Kotlin source files, and an external `BUILD_APK_GUIDE.md` into a standard `.zip` bundle.
> - **Platform Tip**: In addition to this in-app ZIP generator and share sheet, you can also use AI Studio's top-right Settings menu (**Export Project / Download ZIP**) at any time from your browser.

---

## 1. Overview & Core Concept

- **What It Does**: Provides a clear, high-contrast **"Download Project Source (.ZIP)"** button inside the app's Profile settings. When tapped, the app dynamically packages the project structure, Gradle configuration, Kotlin source architecture, live Firestore schema blueprint, and APK compilation guide into a valid `.zip` file (`TrimTime_Android_Project_Source.zip`) and opens the Android system Save / Share dialog so you can save it to device storage, Google Drive, or email it for external APK compilation.
- **Target Audience / Persona**: Developers and product owners who want to inspect, archive, or compile the Android project externally using Android Studio or the Gradle CLI (`./gradlew assembleDebug`).
- **Key Value**: Eliminates friction when moving from the cloud emulator preview to an external local build environment, while providing a copyable CLI quick-start reference right on screen.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Navigate to Profile**: The user opens the **Profile** tab in Customer Mode (or the **Shop Profile** tab in Partner Mode).
  2. **Locate the Project Export & Share Hub**: A dedicated card displays the project package summary (`com.aistudio.trimtime`, Kotlin + Jetpack Compose + Firestore, Gradle 8.x) and an interactive **"Download Project Source (.ZIP)"** button.
  3. **Generate & Export ZIP**: Tapping the button triggers an animated progress indicator as `ZipOutputStream` packages the Gradle files, manifest, source tree, and `BUILD_APK_GUIDE.md` into `TrimTime_Android_Project_Source.zip`.
  4. **Save or Share Externally**: Once generated, the user can immediately save the `.zip` file to their device Downloads/Documents folder (via `ActivityResultContracts.CreateDocument("application/zip")`) or share it via the native Android Share Sheet, as well as inspect the included files and copy the `./gradlew assembleDebug` build commands.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: High-contrast, utilitarian developer-ready card design matching TrimTime's dark slate (`#0F172A` / `#1E293B`) and vibrant amber/emerald accents (`#F59E0B` / `#10B981`).
  - *Typography & Hierarchy*: Bold headers with monospace code badges for Gradle commands and file paths.
- **Interactive Feedback & Motion**:
  - Instant button state feedback (`Generating ZIP...` -> `ZIP Ready • Save / Share File`), accompanied by a snackbar confirmation and an expandable file manifest preview.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: On-Device `ZipOutputStream` Archive Generation + System Document Saver**
  - *Chosen Approach*: Use standard `java.util.zip.ZipOutputStream` in Kotlin coroutines (`Dispatchers.IO`) to construct a real `.zip` archive and allow saving directly via `ActivityResultContracts.CreateDocument("application/zip")` as well as sharing via `Intent.ACTION_SEND`.
  - *Why*: Works reliably without requiring any dangerous storage permissions, complies with Android Scoped Storage policies, and produces a genuine `.zip` file that can be downloaded or shared anywhere.
  - *Alternatives Considered*: Hosting an external static URL—passed over because self-contained local ZIP generation guarantees offline availability and always matches the app's exact feature set.
- **Decision 2: Dual Placement in Customer & Partner Profiles**
  - *Chosen Approach*: Surface the **Project Export & Share Hub** in both the Customer Profile screen and the Partner Shop Profile tab.
  - *Why*: Regardless of whether you are testing Customer Mode or Partner Mode, the export button is always 1 tap away without needing to switch roles.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:
```
┌───────────────────────────────────────────────────────────────────┐
│                         Profile Screens                           │
│   (Customer ProfileScreen  &  PartnerShopProfileTabContent)       │
└─────────────────────────────────┬─────────────────────────────────┘
                                  │
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                   ProjectExportHubCard (UI)                       │
│  • "Download Project Source (.ZIP)" Primary Action Button         │
│  • "Save ZIP to Device" & "Share ZIP" Actions                     │
│  • Interactive File Manifest & Gradle APK Build Guide Dialog      │
└─────────────────────────────────┬─────────────────────────────────┘
                                  │
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                 ProjectZipExporter (Utility)                      │
│  • Runs on Dispatchers.IO via ZipOutputStream                     │
│  • Packages Gradle scripts, AndroidManifest, Kotlin sources,      │
│    firestore.rules, and BUILD_APK_GUIDE.md                        │
│  • Writes to Uri / Cache and triggers Save or Share Intent        │
└───────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `ExportState`: Tracks `Idle`, `Exporting`, `Ready(zipFile, fileCount, sizeBytes)`, and `Error(message)`.
- **Interactive Component & State Mapping**:
  - Tapping **Download Project Source (.ZIP)** generates the `.zip` archive and triggers the system file-save picker (`CreateDocument`) or opens the **APK Build & Source Inspector Dialog** with 1-tap copyable Gradle CLI commands.
