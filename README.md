# 🚗 Wheelchair Cockpit UI (Android Automotive OS)

Automotive digital cockpit client for the **KMS AI Agent** voice-driven documentation assistant, built for **FPT Hackathon 2026**.

---

## ⚡ Core Features

* **🎙️ Voice-First Interaction**:
  * Hands-free wake-word engine ("Hey Car", "Xe ơi") for natural trigger detection.
  * Local Speech-to-Text translation utilizing Android's native `SpeechRecognizer`.
  * Vietnamese Text-to-Speech synthesis with neural `TextToSpeech` routing.
* **🌊 Micro-Animations**:
  * Real-time Compose Canvas waveform reacting dynamically to microphone input level (`rmsLevel`).
* **🚗 VHAL & Telemetry Binding (`android.car`)**:
  * Asynchronous listeners for vehicle speed (`PERF_VEHICLE_SPEED`) and HVAC (`HVAC_AC_ON`).
  * Safety override triggers: automatic speech warning and visual banner when speed exceeds `80 km/h`.
* **🧠 Grounded AI RAG Integration**:
  * Retrofit 2 client consuming backend orchestrator queries with page-level source citations.

---

## ⚙️ Setup & Prerequisites

Follow this sequence to configure your development environment before attempting to compile or deploy the app.

### 1. System Requirements
* **Android Studio**: Jellyfish (2023.3.1) or Koala (2024.1.1)+
* **Android SDK**: Compile & Target SDK `34` (Android 14), Min SDK `33` (Android 13+)
* **JDK**: Version 17

### 2. Setting Up the Android Automotive Emulator (AVD)
You must configure an **Automotive Virtual Device (AVD)** in Android Studio to test the app and simulate vehicle telemetry.

> [!IMPORTANT]
> **Use the non-Play Store system image (userdebug build).**  
> System images with the Play Store are production (`user`) builds. They strictly lock down side-loaded apps and block interactions when the vehicle is in motion (showing the *"You can't use this feature while driving"* screen) even if `distractionOptimized="true"` is declared, because the OS checks for official store signatures. Using a non-Play Store image (e.g. **Android Automotive with Google APIs** / AOSP build) bypasses these platform signature checks, allowing full access to VHAL simulation testing while driving.

#### Step-by-Step AVD Creation:
1. In Android Studio, open the **Device Manager** (Tools → Device Manager).
2. Click **Create Device** (the `+` icon).
3. Under Categories, select **Automotive**.
4. Choose **Android Automotive (1080p landscape)** and click **Next**.
5. Select a system image labeled **Android Automotive with Google APIs** (API 33 or 34). Click **Download** if needed, select it, and click **Next**.
6. Under *Advanced Settings*, allocate at least **3 GB RAM** and **2 GB Internal Storage** for optimal emulation performance.
7. Click **Finish** and run the emulator by clicking the **Play** button. Ensure the emulator is fully booted before proceeding to the run step.

---

## 🚀 Run & Deploy Guide

Once your emulator is running, build and deploy the application.

### 1. Configure Backend Connection
The app connects to the `backend-orchestrator` gateway server.
* **Android Emulator (Default)**:
  `http://10.0.2.2:8000/` automatically maps the Android Emulator loopback to the host machine's `localhost:8000`.
* **Physical Device / Custom Server**:
  If running on a custom network, edit the `BASE_URL` in `CopilotClient.kt`:
  ```kotlin
  // File: app/src/main/java/com/wheelchair/cockpit/api/CopilotClient.kt
  const val BASE_URL = "http://YOUR_SERVER_IP:8000/"
  ```

---

### 2. Choose Deployment Method

#### Option A: Run via Android Studio GUI (Recommended)
1. Ensure your created **Android Automotive Emulator** is running.
2. In the top toolbar of Android Studio, select `app` in the run configurations dropdown.
3. Select your running emulator in the target devices dropdown.
4. Click the green **Run** button (or press `Shift + F10`) to build and deploy.  
   *(To debug with breakpoints, click the **Debug** bug icon or press `Shift + F9` instead).*

#### Option B: Build & Deploy via Command Line Interface (CLI)
1. **Compile Debug APK**:
   * **Windows (PowerShell)**:
     ```powershell
     cd H:\Project\KMS\cockpit-ui
     .\gradlew.bat assembleDebug
     ```
   * **Linux / macOS**:
     ```bash
     ./gradlew assembleDebug
     ```
2. **Deploy via ADB**:
   ```bash
   # Install the compiled APK
   adb install -r app/build/outputs/apk/debug/app-debug.apk

   # Launch the application
   adb shell am start -n com.wheelchair.cockpit/.MainActivity
   ```

---

### 3. Grant Microphone Permissions
For the voice assistant to listen to audio input, grant microphone permissions via ADB:
```bash
adb shell pm grant com.wheelchair.cockpit android.permission.RECORD_AUDIO
```

---

## 📂 Project Architecture

```
cockpit-ui/
├── app/
│   ├── build.gradle.kts          # Dependencies (Compose, Retrofit, Car SDK)
│   └── src/main/
│       ├── AndroidManifest.xml   # VHAL & Audio permissions declaration
│       └── java/com/wheelchair/cockpit/
│           ├── MainActivity.kt   # HUD Compose View & Speech Controller
│           ├── CarPropertyHelper.kt # VHAL CarPropertyManager StateFlow streams
│           └── api/
│               └── CopilotClient.kt # Retrofit network client
└── README.md
```

---

## 🚘 Safety & Driver Distraction Compliance (CarUxRestrictions)

To ensure the driver remains safe and focused, the app dynamically locks interactive manual controls when the vehicle is in motion, transitioning into a **Voice-Only Interface**.

### 1. Manifest Optimization Tag
The main activity is declared as optimized in `AndroidManifest.xml`:
```xml
<activity android:name="com.wheelchair.cockpit.MainActivity" android:exported="true">
    <meta-data android:name="distractionOptimized" android:value="true" />
</activity>
```

### 2. State-Driven UI Lock
* **Voice Flow (Always Available)**: Wake-word detection, microphone input, and audio responses remain active at all times.
* **Manual UI Flow (Locked in Motion)**: Text inputs (`ManualInputBar`), Settings, and touch controls (`QuickActionCard`) are disabled automatically when `isDrivingRestricted = true` (Vehicle Speed $> 0$ km/h).

### 3. Simulating Telemetry & Safety Alerts
Use ADB shell to inject VHAL events and test the safety warning overlays:
* **Simulate Park (P)**:
  ```bash
  adb shell dumpsys car_service inject-vhal-event 0x11600400 4
  ```
* **Inject Speed Update (>80 km/h)**:
  Open the emulator's **Extended Controls (three dots) → Car Data** and slide the speed bar, or run the VHAL sender script in the gateway orchestrator repository.

---

## ❓ Troubleshooting & FAQ

#### Q1: App displays "Lỗi kết nối Backend" (Connection Error)
* **Fix**: Ensure the `backend-orchestrator` is running on your host machine. The emulator routes `http://10.0.2.2:8000` to the host's localhost.

#### Q2: Voice assistant doesn't hear the wake-word
* **Fix**: Make sure microphone permissions are granted. Double-check in the AVD settings or force-grant it via:
  ```bash
  adb shell pm grant com.wheelchair.cockpit android.permission.RECORD_AUDIO
  ```
