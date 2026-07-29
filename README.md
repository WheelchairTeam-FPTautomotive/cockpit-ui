# 🚗 Wheelchair Cockpit UI (Android Automotive OS)

> **Team Wheelchair** — FPT Hackathon 2026
> Android Automotive (AAOS) Digital Cockpit UI for KMS AI Agent — Voice-driven automotive documentation assistant.

---

## 📌 Overview

`cockpit-ui` is the native Android Automotive OS application that serves as the central digital cockpit interface for drivers. It integrates real-time voice interaction, Vehicle Hardware Abstraction Layer (VHAL) property bindings, audio waveform visualization, and connects seamlessly to the backend AI RAG orchestrator.

### Key Features

* 🎙️ **Voice Interaction (STT & TTS)**:
  * Hands-free wake-word detection ("Hey Car", "Hey Copilot", "Trợ lý ơi").
  * Local Android `SpeechRecognizer` for continuous Speech-to-Text.
  * Native Vietnamese Text-to-Speech (`TextToSpeech` with `vi_VN` locale) for spoken AI responses.
* 🌊 **Real-Time Audio Waveform**:
  * Dynamic Compose Canvas wave animation synchronized with microphone input level (`rmsLevel`).
* 🔌 **Backend AI RAG Integration**:
  * Retrofit 2 + OkHttp 4 client with 30s timeouts and logging interceptors.
  * Connects to `backend-orchestrator` (`http://10.0.2.2:8000/api/v1/copilot/query`).
  * Renders detailed response cards and page-level citation sources (`CitationCard`).
* 🚗 **VHAL Vehicle Signals (`android.car`)**:
  * Real-time monitoring of vehicle speed (`PERF_VEHICLE_SPEED`) and HVAC climate state (`HVAC_AC_ON`).
  * Safety speed warnings: Triggers warning banner and voice alert when vehicle speed exceeds `80 km/h`.

---

## 🛠️ Prerequisites

Before building or running the app, ensure your development environment has:

1. **Android Studio**: Android Studio Jellyfish (2023.3.1) or Koala (2024.1.1)+.
2. **Android SDK**:
   * **Target SDK**: `34` (Android 14)
   * **Compile SDK**: `34`
   * **Min SDK**: `33` (Android 13+ Automotive)
3. **Java Development Kit (JDK)**: JDK 17 (Java 17).
4. **Android Automotive Emulator**:
   * Create an AVD in Android Studio using an **Automotive** system image **WITHOUT Google Play** (e.g., *Android Automotive (1080p landscape)* API 33/34).
   * ⚠️ **Important**: Do NOT use the "with Play Store" image for development. The Play Store images are `user` builds which strictly enforce Driver Distraction (CarUxRestrictions) security and signature checks, blocking side-loaded dev apps when driving. Using a non-Play Store image (`userdebug` build) prevents these OS-level locks and allows full access to VHAL simulation testing.

---

## 🚀 How to Setup & Run

### Step 1: Open Project in Android Studio

1. Launch Android Studio.
2. Select **Open** and navigate to `d:\Hackathon\cockpit-ui`.
3. Wait for Gradle sync to complete automatically.

### Step 2: Configure Backend Connection

The app connects to the `backend-orchestrator` gateway server.

* **Android Emulator (Default)**:
  `http://10.0.2.2:8000/` automatically maps the Android Emulator loopback to the host machine's `localhost:8000`.
* **Physical Device / Custom Server**:
  If running on a physical head unit or custom network, edit the `BASE_URL` in `CopilotClient.kt`:
  ```kotlin
  // File: app/src/main/java/com/wheelchair/cockpit/api/CopilotClient.kt
  const val BASE_URL = "http://YOUR_SERVER_IP:8000/"
  ```

### Step 3: Build & Run via Command Line

You can build the debug APK using Gradle wrapper:

**Windows (PowerShell / Command Prompt)**:

```powershell
# Navigate to cockpit-ui root
cd d:\Hackathon\cockpit-ui

# Build debug APK
.\gradlew.bat assembleDebug
```

**Linux / macOS**:

```bash
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Step 4: Deploy onto Emulator or CarSky Simulator

1. Start your Android Automotive Emulator (or connect device via ADB).
2. Install & run the app via ADB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.wheelchair.cockpit/.MainActivity
   ```
3. **Grant Microphone Permission**:
   When launched for the first time, accept the audio recording permission prompt so the voice assistant can listen to your voice.

---

## 📁 Repository Structure

```
cockpit-ui/
├── app/
│   ├── build.gradle.kts                ← Dependencies (Compose, Retrofit, OkHttp, Car SDK)
│   └── src/main/
│       ├── AndroidManifest.xml         ← Permissions (RECORD_AUDIO, INTERNET, CAR_SPEED, etc.)
│       ├── res/xml/network_security_config.xml  ← Allows local HTTP cleartext traffic
│       └── java/com/wheelchair/cockpit/
│           ├── MainActivity.kt         ← Main Compose HUD View & Voice Controller (STT/TTS)
│           ├── CarPropertyHelper.kt    ← VHAL Vehicle Property Manager (Speed & HVAC)
│           └── api/
│               └── CopilotClient.kt    ← Retrofit API Client for Backend Orchestrator
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
└── README.md
```

---

## ❓ Troubleshooting & FAQs

#### Q1: App shows "Lỗi kết nối Backend" when asking a question.

* **Cause**: `backend-orchestrator` is not running or port 8000 is blocked.
* **Fix**: Ensure the FastAPI server is running on the host machine (`docker compose up` or `uv run uvicorn main:app --port 8000`). Test in browser: `http://localhost:8000/api/v1/health`.

#### Q2: SpeechRecognizer says "Microphone permission required".

* **Fix**: Grant audio permission manually via ADB:
  ```bash
  adb shell pm grant com.wheelchair.cockpit android.permission.RECORD_AUDIO
  ```

#### Q3: How to simulate vehicle speed alerts (>80 km/h)?

* Use Android Studio Emulator Extended Controls -> **Car Data**, or run the VHAL sender script in `backend-orchestrator/scripts/vhal_mock_sender.py`.

---

## 🚘 Driver Distraction Guidelines (CarUxRestrictions) & Production Setup

Android Automotive OS enforces strict **Driver Distraction Guidelines (CarUxRestrictions)** when the vehicle is in motion (Gear = D / Speed > 0).

### 🛡️ Architecture & Manifest Configuration

To comply with Google Automotive Safety Guidelines while preserving voice assistant functionality:

1. **Manifest Declaration**:
   The `MainActivity` is marked with `distractionOptimized="true"` inside `AndroidManifest.xml`:
   ```xml
   <activity android:name="com.wheelchair.cockpit.MainActivity" android:exported="true">
       <meta-data android:name="distractionOptimized" android:value="true" />
   </activity>
   ```

2. **Dynamic UI Separation (`CarUxRestrictionsManager`)**:
   - **Voice-First Group (Always Enabled)**: Wake-word listener ("Hey Car"), microphone controls, real-time STT speech recognition, and TTS audio responses remain 100% active during driving.
   - **Manual Input Group (Locked During Driving)**: Text entry field (`ManualInputBar`), settings dialog (`SystemSettingsDialog`), and manual touch shortcuts (`QuickActionCard`) are dynamically disabled and display `VOICE MODE ONLY (Đang lái xe)` when `isDrivingRestricted = true`.

---

### 🧪 Development Bypass on "user" Build Emulators (Phần A)

On Android Automotive OS **`user`** build images (such as *Automotive with Google Play Store*):
- `CarPackageManagerService` requires apps tagged with `distractionOptimized="true"` to come from a trusted installer source.
- Side-loaded debug APKs (`adb install`) on `user` builds may trigger system distraction locks when Gear = D.

#### Dev/Test Strategy on Emulator:
1. **List VHAL Properties & Obtain Property IDs**:
   ```bash
   adb shell dumpsys car_service --list
   ```
2. **Inject GEAR_PARK Event**:
   Simulate vehicle gear fixed at **P (Park)** via VHAL injection so the UI & Voice Assistant remain fully active:
   ```bash
   adb shell dumpsys car_service inject-vhal-event 0x11600400 4
   ```
3. **Test Speedometer & Speed Alert**:
   With simulated Gear = P, adjust vehicle speed in Emulator **Extended Controls -> Car Data** (> 80 km/h) to verify VHAL speed updates and safety alerts without OS distraction lockouts.

---

### 🏭 Production Deployment Pathways (Phần B)

To enable true `distractionOptimized` execution on production vehicles without OS overlay blocks, select one of the following 3 official production pathways:

1. **Google Play Store Publishing**:
   Publish the application to the Google Play Store under the **Android Automotive OS** category. Play Store installer package (`com.android.vending`) is listed in the default system whitelist (`allowedAppInstallSources`).
2. **OEM System Whitelisting**:
   For direct OEM vehicle partnerships, add the application package name (`com.wheelchair.cockpit`) to the vehicle's ROM system configuration file (`car_ux_restrictions_config.xml` under `allowedAppInstallSources` or `config_allowedSystemApps`).
3. **Privileged System App**:
   Install the application directly into the ROM's `/system/priv-app/` directory (for custom Automotive ROM builds).

---

## 📄 License

MIT — Team Wheelchair (FPT Hackathon 2026)
