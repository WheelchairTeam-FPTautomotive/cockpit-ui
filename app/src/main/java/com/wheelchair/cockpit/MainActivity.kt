package com.wheelchair.cockpit

import android.Manifest
import android.app.NotificationManager
import android.car.VehiclePropertyIds
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.api.CitationInfo
import com.wheelchair.cockpit.api.CopilotClient
import com.wheelchair.cockpit.data.CopilotRepository
import com.wheelchair.cockpit.data.HealthResult
import com.wheelchair.cockpit.dev.DevSettings
import com.wheelchair.cockpit.dev.DevSettingsStore
import com.wheelchair.cockpit.dev.HttpLogLevel
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState
import com.wheelchair.cockpit.model.ControlKind
import com.wheelchair.cockpit.model.CopilotUiState
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.model.MockActuationEvent
import com.wheelchair.cockpit.model.MockActuationKind
import com.wheelchair.cockpit.model.mockActuationForCommandId
import com.wheelchair.cockpit.model.mockActuationForRagSuccess
import com.wheelchair.cockpit.ui.components.*

import com.wheelchair.cockpit.ui.theme.CockpitColors
import com.wheelchair.cockpit.vhal.CarPropertyHelper
import com.wheelchair.cockpit.voice.MicDiag
import com.wheelchair.cockpit.voice.PartialTranscriptPublisher
import com.wheelchair.cockpit.voice.WakeWordEngine
import com.wheelchair.cockpit.voice.WakeWordForegroundService
import com.wheelchair.cockpit.voice.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var carPropertyHelper: CarPropertyHelper
    // --- START MODIFICATION ---
    private lateinit var devSettingsStore: DevSettingsStore
    private lateinit var copilotRepository: CopilotRepository
    // --- END MODIFICATION ---

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningSessionActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var restartListeningRunnable: Runnable? = null
    private var backendMediaPlayer: android.media.MediaPlayer? = null
    // MODIFIED: AAOS needs explicit speech usage + focus or Edge MP3 plays silently
    private var ttsAudioFocusRequest: AudioFocusRequest? = null

    private var assistantState = mutableStateOf(AssistantState.IDLE)
    private var statusText = mutableStateOf("System Standby. Say \"Hey Car\" to activate.")
    private var rmsLevel = mutableFloatStateOf(0f)
    private var chatHistory = androidx.compose.runtime.mutableStateListOf<com.wheelchair.cockpit.ui.components.ChatMessage>()
    private var citations = mutableStateOf<List<CitationInfo>>(emptyList())
    private var vehicleSpeed = mutableFloatStateOf(0.0f)
    private var isHvacOn = mutableStateOf(false)
    private var isDrivingRestricted = mutableStateOf(false)
    private var hasWarnedSpeed = false
    private var safetyWarning = mutableStateOf<String?>(null)
    // --- START MODIFICATION ---
    // Mentor #17: mock actuation / RAG success motion overlay
    private var mockActuation = mutableStateOf<MockActuationEvent?>(null)
    private var mockActuationClearRunnable: Runnable? = null
    // --- END MODIFICATION ---

    private var appLanguage = mutableStateOf(AppLanguage.VIETNAMESE)
    private var displayTheme = mutableStateOf(DisplayTheme.LIGHT)
    private var geminiApiKey = mutableStateOf("")
    // --- START MODIFICATION ---
    private var healthResult = mutableStateOf<HealthResult?>(null)
    private var healthChecking = mutableStateOf(false)
    private var partialTranscript = mutableStateOf("")
    private var micDiagStatus = mutableStateOf(MicDiag.IDLE)
    private var lastQueryLatencyMs = mutableStateOf<Long?>(null)
    private val partialPublisher = PartialTranscriptPublisher(mainHandler = mainHandler) { text ->
        partialTranscript.value = text
    }
    // --- END MODIFICATION ---

    private var autoSleepRunnable: Runnable? = null
    private var maxRmsInSession = 0f

    // --- START MODIFICATION: Background wake FGS (#14) ---
    /** Prefer microphone FGS; fall back to in-Activity [WakeWordEngine] if FGS start fails. */
    private var wakeUsesFgs = false
    private var fallbackWakeEngine: WakeWordEngine? = null
    // --- END MODIFICATION ---

    private val mockDrivingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.wheelchair.cockpit.MOCK_DRIVE" -> carPropertyHelper.mockDrivingState(true)
                "com.wheelchair.cockpit.MOCK_PARK" -> carPropertyHelper.mockDrivingState(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register mock driving receiver for demo
        val filter = IntentFilter().apply {
            addAction("com.wheelchair.cockpit.MOCK_DRIVE")
            addAction("com.wheelchair.cockpit.MOCK_PARK")
        }
        registerReceiver(mockDrivingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        geminiApiKey.value = BuildConfig.GEMINI_API_KEY.ifEmpty { "ai_studio_api_key_here" }

        // --- START MODIFICATION ---
        // Wire DataStore + repository before any network call.
        devSettingsStore = DevSettingsStore(this)
        CopilotClient.init(devSettingsStore)
        copilotRepository = CopilotRepository(devSettingsStore)
        // --- END MODIFICATION ---

        checkAudioPermissions()
        initTextToSpeech()
        handleWakeIntent(intent)

        carPropertyHelper = CarPropertyHelper(
            this,
            onSignalChanged = { propertyId, value ->
                runOnUiThread {
                    if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED || propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY) {
                        val rawSpeed = when (value) {
                            is Float -> value
                            is Int -> value.toFloat()
                            is Double -> value.toFloat()
                            is Number -> value.toFloat()
                            else -> 0f
                        }
                        // VHAL PERF_VEHICLE_SPEED signal in Android Automotive is in m/s (1 m/s = 3.6 km/h).
                        val speedKmh = kotlin.math.abs(rawSpeed) * 3.6f
                        vehicleSpeed.floatValue = speedKmh

                        if (speedKmh > 80f && !hasWarnedSpeed && assistantState.value != AssistantState.SPEAKING) {
                            hasWarnedSpeed = true
                            val warning = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                                "Cảnh báo: Tốc độ xe hiện tại là ${"%.0f".format(speedKmh)} km/h. Vui lòng giảm tốc độ để đảm bảo an toàn."
                            } else {
                                "Warning: Current vehicle speed is ${"%.0f".format(speedKmh)} km/h. Please slow down."
                            }
                            speakOut(warning)
                        } else if (speedKmh <= 75f) {
                            hasWarnedSpeed = false
                        }
                    } else if (propertyId == VehiclePropertyIds.HVAC_AC_ON) {
                        if (value is Boolean) {
                            isHvacOn.value = value
                        }
                    }
                }
            },
            onUxRestrictionsChanged = { isRestricted ->
                runOnUiThread {
                    isDrivingRestricted.value = isRestricted
                }
            }
        )

        setContent {
            val speed = carPropertyHelper.speedFlow.collectAsState().value
            val currentGear = carPropertyHelper.currentGearFlow.collectAsState().value
            val batteryLevel = carPropertyHelper.batteryLevelFlow.collectAsState().value
            val hvacOn = carPropertyHelper.hvacOnFlow.collectAsState().value
            val hvacTemp by carPropertyHelper.hvacTempFlow.collectAsState()
            val drivingRestricted by carPropertyHelper.uxRestrictionsFlow.collectAsState()
            val activeWarning by safetyWarning

            val doorLockFL by carPropertyHelper.doorLockFL.collectAsState()
            val doorLockFR by carPropertyHelper.doorLockFR.collectAsState()
            val doorLockRL by carPropertyHelper.doorLockRL.collectAsState()
            val doorLockRR by carPropertyHelper.doorLockRR.collectAsState()

            val tirePressureFL by carPropertyHelper.tirePressureFL.collectAsState()
            val tirePressureFR by carPropertyHelper.tirePressureFR.collectAsState()
            val tirePressureRL by carPropertyHelper.tirePressureRL.collectAsState()
            val tirePressureRR by carPropertyHelper.tirePressureRR.collectAsState()

            // --- START MODIFICATION ---
            val copilotUiState by remember {
                derivedStateOf {
                    when (val actuation = mockActuation.value) {
                        null -> when (assistantState.value) {
                            AssistantState.IDLE -> CopilotUiState.Idle
                            AssistantState.WAKE_DETECTED -> CopilotUiState.Listening
                            AssistantState.PROCESSING -> CopilotUiState.Thinking
                            AssistantState.SPEAKING -> CopilotUiState.Speaking
                        }
                        else -> when (actuation.kind) {
                            MockActuationKind.RAG -> CopilotUiState.RagAnswer
                            MockActuationKind.DOOR -> CopilotUiState.ControlSuccess(ControlKind.DOOR)
                            MockActuationKind.HVAC -> CopilotUiState.ControlSuccess(ControlKind.HVAC)
                            MockActuationKind.MUSIC -> CopilotUiState.ControlSuccess(ControlKind.MUSIC)
                        }
                    }
                }
            }
            val devSettings by devSettingsStore.settings.collectAsState()
            LaunchedEffect(devSettings) {
                CopilotClient.applyLogLevel(devSettings)
            }
            val effectiveDrivingRestricted =
                drivingRestricted &&
                    !(BuildConfig.DEBUG && devSettings.effectiveBypassDrivingLock)
            // --- END MODIFICATION ---

            Box(modifier = Modifier.fillMaxSize()) {
                CockpitAppScreen(
                    assistantState = assistantState.value,
                    copilotUiState = copilotUiState,
                    statusText = statusText.value,
                    chatHistory = chatHistory,
                    citations = citations.value,
                    vehicleSpeed = speed,
                    currentGear = currentGear,
                    batteryLevel = batteryLevel,
                    isHvacOn = hvacOn,
                    hvacTemp = hvacTemp,
                    doorLockFL = doorLockFL,
                    doorLockFR = doorLockFR,
                    doorLockRL = doorLockRL,
                    doorLockRR = doorLockRR,
                    tirePressureFL = tirePressureFL,
                    tirePressureFR = tirePressureFR,
                    tirePressureRL = tirePressureRL,
                    tirePressureRR = tirePressureRR,
                    rmsLevel = rmsLevel.floatValue,
                    appLanguage = appLanguage.value,
                    displayTheme = displayTheme.value,
                    isDrivingRestricted = effectiveDrivingRestricted,
                    onHvacToggle = { toggleHvacProperty() },
                    onTempChange = { newTemp -> carPropertyHelper.setHvacTemperature(0, newTemp) },
                    onDoorLockToggle = { areaId, lock -> carPropertyHelper.setDoorLock(areaId, lock) },
                    onManualSend = { query -> processUserSpeech(query) },
                    onMicTap = { handleMicTap() },
                    onWakeSimulate = { triggerKeywordWake() },
                    onLanguageChange = { lang -> appLanguage.value = lang },
                    onThemeChange = { theme -> displayTheme.value = theme },
                    // --- START MODIFICATION ---
                    showDeveloperControls = BuildConfig.DEBUG,
                    devSettings = devSettings,
                    healthResult = healthResult.value,
                    healthChecking = healthChecking.value,
                    onDeveloperModeChange = { enabled ->
                        // MODIFIED: sync flag so effectiveBaseUrl switches immediately
                        devSettingsStore.applyDeveloperModeNow(enabled)
                    },
                    onBaseUrlApply = { url ->
                        // MODIFIED: sync snapshot before next OkHttp call (no race with health/query)
                        devSettingsStore.applyBaseUrlNow(url)
                    },
                    onMockRagChange = { enabled ->
                        CoroutineScope(Dispatchers.IO).launch {
                            devSettingsStore.setMockRagEnabled(enabled)
                        }
                    },
                    onBypassDrivingChange = { enabled ->
                        CoroutineScope(Dispatchers.IO).launch {
                            devSettingsStore.setBypassDrivingLock(enabled)
                        }
                    },
                    onHttpLogLevelChange = { level ->
                        CoroutineScope(Dispatchers.IO).launch {
                            devSettingsStore.setHttpLogLevel(level)
                        }
                    },
                    onShowCitationCardsChange = { enabled ->
                        devSettingsStore.applyShowCitationCardsNow(enabled)
                    },
                    onHealthCheck = { runHealthCheck() },
                    // --- START MODIFICATION ---
                    partialTranscript = partialTranscript.value,
                    micDiagLabel = micDiagStatus.value.label(appLanguage.value),
                    lastQueryLatencyMs = lastQueryLatencyMs.value
                    // --- END MODIFICATION ---
                )

                // Safety Warning HUD Overlay
                activeWarning?.let { warningText ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(androidx.compose.ui.Alignment.TopCenter),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = warningText,
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }

                // --- START MODIFICATION ---
                // AAOS one-shot control / RAG success feedback overlay
                CarControlFeedbackOverlay(
                    state = copilotUiState,
                    appLanguage = appLanguage.value,
                    onDismiss = { dismissMockActuation() },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                )
                // --- END MODIFICATION ---
            }
        }
    }

    // --- START MODIFICATION ---
    private fun showMockActuation(event: MockActuationEvent) {
        mockActuationClearRunnable?.let { mainHandler.removeCallbacks(it) }
        mockActuation.value = event
        // Safety clear: if the overlay fails to dismiss, still clear after 5s.
        val clear = Runnable {
            if (mockActuation.value?.token == event.token) {
                mockActuation.value = null
            }
        }
        mockActuationClearRunnable = clear
        mainHandler.postDelayed(clear, 5000L)
    }

    private fun dismissMockActuation() {
        mockActuationClearRunnable?.let { mainHandler.removeCallbacks(it) }
        mockActuation.value = null
    }
    // --- END MODIFICATION ---

    private fun toggleHvacProperty() {
        val nextState = !isHvacOn.value
        isHvacOn.value = nextState
        carPropertyHelper.setHvacState(0, nextState)
    }

    private fun checkAudioPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        // --- START MODIFICATION: wake FGS notification permission ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // --- END MODIFICATION ---
        if (checkSelfPermission("android.car.permission.CAR_SPEED") != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add("android.car.permission.CAR_SPEED")
        }
        if (checkSelfPermission("android.car.permission.CONTROL_CAR_CLIMATE") != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add("android.car.permission.CONTROL_CAR_CLIMATE")
        }
        if (checkSelfPermission("android.car.permission.CONTROL_CAR_DOORS") != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add("android.car.permission.CONTROL_CAR_DOORS")
        }

        if (permissionsToRequest.isNotEmpty()) {
            // MODIFIED: surface mic denial until grant completes
            if (permissionsToRequest.contains(Manifest.permission.RECORD_AUDIO)) {
                micDiagStatus.value = MicDiag.PERMISSION_DENIED
            }
            requestPermissions(permissionsToRequest.toTypedArray(), 101)
        } else {
            initAudioRecognizers()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                initAudioRecognizers()
            } else {
                // MODIFIED: keep denial visible in Siri-style mic diag
                micDiagStatus.value = MicDiag.PERMISSION_DENIED
                statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                    "Cần quyền micro để nhận giọng nói."
                } else {
                    "Microphone permission required for voice input."
                }
            }
            if (checkSelfPermission("android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED) {
                Log.i("CockpitUI", "CAR_SPEED permission granted.")
            }
        }
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val loc = if (appLanguage.value == AppLanguage.VIETNAMESE) Locale("vi", "VN") else Locale.US
                val result = tts?.setLanguage(loc)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread { assistantState.value = AssistantState.SPEAKING }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            assistantState.value = AssistantState.IDLE
                            statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                                "Trạng thái chờ. Nói \"Hey Car\" để kích hoạt."
                            } else {
                                "System Standby. Say \"Hey Car\" to activate."
                            }
                            startVoskListening()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            assistantState.value = AssistantState.IDLE
                            startVoskListening()
                        }
                    }
                })
            }
        }
    }

    private fun abandonTtsAudioFocus() {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        ttsAudioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        ttsAudioFocusRequest = null
    }

    private fun playBase64Audio(base64Audio: String, fallbackText: String) {
        try {
            stopGoogleListening()
            stopVoskListening()
            tts?.stop()
            assistantState.value = AssistantState.SPEAKING

            if (base64Audio.isEmpty()) {
                speakOut(fallbackText, localOnly = true)
                return
            }

            val audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
            // MODIFIED: Edge/VieNeu return MP3 (was wrongly named .wav)
            val tempFile = java.io.File(cacheDir, "response_audio.mp3")
            java.io.FileOutputStream(tempFile).use { it.write(audioBytes) }

            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            abandonTtsAudioFocus()
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()
            ttsAudioFocusRequest = focusReq
            val focusResult = am.requestAudioFocus(focusReq)
            Log.i(
                "CockpitUI",
                "Backend TTS play bytes=${audioBytes.size} focus=$focusResult magic=${audioBytes.take(3).joinToString("") { "%02x".format(it) }}"
            )

            backendMediaPlayer?.release()
            backendMediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(tempFile.absolutePath)
                setVolume(1f, 1f)
                setOnErrorListener { _, what, extra ->
                    Log.e("CockpitUI", "MediaPlayer error what=$what extra=$extra")
                    abandonTtsAudioFocus()
                    runOnUiThread { speakOut(fallbackText, localOnly = true) }
                    true
                }
                setOnCompletionListener {
                    abandonTtsAudioFocus()
                    runOnUiThread {
                        assistantState.value = AssistantState.IDLE
                        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                            "Trạng thái chờ. Nói \"Hey Car\" để kích hoạt."
                        } else {
                            "System Standby. Say \"Hey Car\" to activate."
                        }
                        startVoskListening()
                    }
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("CockpitUI", "Error playing base64 audio", e)
            abandonTtsAudioFocus()
            speakOut(fallbackText, localOnly = true) // Fallback to local TTS
        }
    }

    private fun speakOut(text: String, forceEnglish: Boolean = false, localOnly: Boolean = false) {
        stopGoogleListening()
        stopVoskListening()
        assistantState.value = AssistantState.SPEAKING
        
        // Auto-detect language based on text content (Vietnamese diacritics)
        val isVietnamese = if (forceEnglish) false else Regex("[áàảãạăắằẳẵặâấầẩẫậđéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵ]", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val langStr = if (isVietnamese) "vi" else "en"

        // Fire request to the remote Edge-TTS service in the background
        if (!localOnly) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = com.wheelchair.cockpit.api.CopilotClient.service.generateTts(
                        com.wheelchair.cockpit.api.TtsRequest(text = text, language = langStr)
                    )
                    if (!response.audio_base64.isNullOrEmpty()) {
                        runOnUiThread { playBase64Audio(response.audio_base64, text) }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("CockpitUI", "Remote TTS failed: ${e.message}")
                }
                // Fallback to local TTS if remote fails or returns empty audio
                runOnUiThread { speakOut(text, forceEnglish, localOnly = true) }
            }
            return
        }

        // Fallback to local TTS (only reached if localOnly is true)
        if (localOnly) {
            runOnUiThread {
                val loc = if (isVietnamese) Locale("vi", "VN") else Locale.US
                val result = tts?.setLanguage(loc)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }

                // Hackathon trick: Phonetic replacement for basic AOSP TTS
                var speakText = text
                if (isVietnamese) {
                    speakText = speakText
                        .replace(Regex("\\bAC\\b"), "Ây Xi")
                        .replace(Regex("\\bHVAC\\b"), "Hát Vát")
                        .replace(Regex("\\bADAS\\b"), "Ây Đát")
                        .replace(Regex("\\bGPS\\b"), "Gờ Pê Ét")
                        .replace(Regex("\\bCopilot\\b", RegexOption.IGNORE_CASE), "Cô Pai Lọt")
                        .replace(Regex("\\bBluetooth\\b", RegexOption.IGNORE_CASE), "Bờ Lu Tút")
                } else {
                    speakText = speakText
                        .replace(Regex("\\bHà Nội\\b", RegexOption.IGNORE_CASE), "Ha-Noy")
                        .replace(Regex("\\bViệt Nam\\b", RegexOption.IGNORE_CASE), "Vee-etnahm")
                        .replace(Regex("\\bHồ Chí Minh\\b", RegexOption.IGNORE_CASE), "Ho Chee Min")
                        .replace(Regex("\\bđiều hòa\\b", RegexOption.IGNORE_CASE), "deew hwa")
                }

                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "CockpitTTS")
                tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, params, "CockpitTTS")
            }
        }
    }

    // --- START MODIFICATION: wake standby via FGS (+ Activity fallback) ---
    private fun initAudioRecognizers() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micDiagStatus.value = MicDiag.PERMISSION_DENIED
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w("CockpitUI", "Local SpeechRecognizer not available. Falling back to backend STT.")
        } else {
            micDiagStatus.value = MicDiag.OK
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    micDiagStatus.value = MicDiag.LISTENING
                }
                override fun onBeginningOfSpeech() {
                    resetAutoSleepTimer()
                    statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                        "Đang nghe bạn…"
                    } else {
                        "Hearing you…"
                    }
                }
                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    rmsLevel.floatValue = normalized
                    if (normalized > maxRmsInSession) {
                        maxRmsInSession = normalized
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    rmsLevel.floatValue = 0f
                    partialPublisher.clear()
                    if (assistantState.value == AssistantState.WAKE_DETECTED) {
                        assistantState.value = AssistantState.IDLE
                        micDiagStatus.value = MicDiag.OK
                        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                            "Không nghe rõ (Mã lỗi: $error). Trở về Standby."
                        } else {
                            "Did not catch that (Error: $error). Returning to Standby."
                        }
                        startVoskListening()
                    }
                }
                override fun onResults(results: Bundle?) {
                    rmsLevel.floatValue = 0f
                    partialPublisher.clear()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        handleRecognizedSpeech(spokenText)
                    } else if (assistantState.value == AssistantState.WAKE_DETECTED) {
                        assistantState.value = AssistantState.IDLE
                        micDiagStatus.value = MicDiag.OK
                        startVoskListening()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        partialPublisher.offer(matches[0])
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        ensureWakeStandbyStarted()
    }

    override fun onStart() {
        super.onStart()
        // while-in-use: start microphone FGS while Activity is foregrounded
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ensureWakeStandbyStarted()
        }
    }

    // --- START MODIFICATION ---
    // Foreground wake: FGS skips FSI heads-up when UI is resumed (onPause, not onStop).
    override fun onResume() {
        super.onResume()
        isUiForeground = true
    }

    override fun onPause() {
        isUiForeground = false
        super.onPause()
    }
    // --- END MODIFICATION ---

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(WakeWordForegroundService.EXTRA_WAKE_DETECTED, false) != true) {
            return
        }
        intent.removeExtra(WakeWordForegroundService.EXTRA_WAKE_DETECTED)
        getSystemService(NotificationManager::class.java)
            ?.cancel(WakeWordForegroundService.NOTIF_TRIGGER_ID)
        triggerKeywordWake()
    }

    private fun ensureWakeStandbyStarted() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (wakeUsesFgs && WakeWordForegroundService.isRunning) {
            if (assistantState.value == AssistantState.IDLE) {
                WakeWordForegroundService.resume(this)
            }
            return
        }
        if (WakeWordForegroundService.startFailed) {
            startFallbackWakeEngine()
            return
        }
        try {
            WakeWordForegroundService.start(this)
            wakeUsesFgs = true
            fallbackWakeEngine?.shutdown()
            fallbackWakeEngine = null
            Log.i("CockpitUI", "Wake standby via microphone FGS")
            mainHandler.postDelayed({
                if (WakeWordForegroundService.startFailed || !WakeWordForegroundService.isRunning) {
                    Log.w("CockpitUI", "FGS wake failed; falling back to in-Activity engine")
                    wakeUsesFgs = false
                    startFallbackWakeEngine()
                } else if (assistantState.value == AssistantState.IDLE) {
                    WakeWordForegroundService.resume(this)
                }
            }, 1500)
        } catch (e: Exception) {
            Log.e("CockpitUI", "Failed to start WakeWordForegroundService", e)
            wakeUsesFgs = false
            WakeWordForegroundService.startFailed = true
            startFallbackWakeEngine()
        }
    }

    private fun startFallbackWakeEngine() {
        if (assistantState.value != AssistantState.IDLE) return
        val engine = fallbackWakeEngine ?: WakeWordEngine(applicationContext, mainHandler).also {
            fallbackWakeEngine = it
        }
        engine.prepareModel { ok ->
            if (!ok) {
                Log.e("CockpitUI", "Fallback wake model unpack failed")
                return@prepareModel
            }
            if (assistantState.value != AssistantState.IDLE) return@prepareModel
            engine.start(object : WakeWordEngine.Callbacks {
                override fun onWakeDetected() {
                    triggerKeywordWake()
                }

                override fun onRms(normalized: Float) {
                    rmsLevel.floatValue = normalized
                }

                override fun onError(message: String) {
                    Log.e("CockpitUI", "Fallback wake: $message")
                }
            })
        }
    }

    private fun startVoskListening() {
        if (assistantState.value != AssistantState.IDLE) return
        if (wakeUsesFgs && !WakeWordForegroundService.startFailed) {
            WakeWordForegroundService.resume(this)
            return
        }
        startFallbackWakeEngine()
    }

    private fun stopVoskListening() {
        if (wakeUsesFgs) {
            WakeWordForegroundService.pause(this)
        }
        fallbackWakeEngine?.pause()
    }
    // --- END MODIFICATION ---

    private var customAudioRecord: android.media.AudioRecord? = null
    private var isCustomRecording = false

    private fun addWavHeader(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = ((totalDataLen shr 8) and 0xff).toByte(); header[6] = ((totalDataLen shr 16) and 0xff).toByte(); header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte(); header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte(); header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0; header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte(); header[41] = ((pcmData.size shr 8) and 0xff).toByte(); header[42] = ((pcmData.size shr 16) and 0xff).toByte(); header[43] = ((pcmData.size shr 24) and 0xff).toByte()
        val out = java.io.ByteArrayOutputStream()
        out.write(header)
        out.write(pcmData)
        return out.toByteArray()
    }

    private fun startGoogleListening() {
        if (assistantState.value == AssistantState.SPEAKING) return
        maxRmsInSession = 0f
        isCustomRecording = true

        Thread {
            try {
                val sampleRate = 16000
                val minBufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@Thread
                
                customAudioRecord = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, sampleRate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 2)
                
                val pcmData = java.io.ByteArrayOutputStream()
                customAudioRecord?.startRecording()
                
                val buffer = ByteArray(minBufferSize)
                val endTime = System.currentTimeMillis() + 4000 // record for 4 seconds
                
                while (isCustomRecording && System.currentTimeMillis() < endTime) {
                    val read = customAudioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        pcmData.write(buffer, 0, read)
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                            sum += sample * sample
                        }
                        val rms = Math.sqrt(sum / (read / 2)).toFloat()
                        val rmsdB = if (rms > 0) 20 * kotlin.math.log10(rms.toDouble()).toFloat() else -80f
                        val normalized = ((rmsdB - 30f) / 50f).coerceIn(0f, 1f)
                        runOnUiThread { rmsLevel.floatValue = normalized }
                    }
                }
                
                customAudioRecord?.stop()
                customAudioRecord?.release()
                customAudioRecord = null
                
                runOnUiThread {
                    rmsLevel.floatValue = 0f
                    statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) "Đang gửi âm thanh xử lý..." else "Processing audio..."
                }
                
                val wavBytes = addWavHeader(pcmData.toByteArray(), sampleRate)
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val requestBody = okhttp3.RequestBody.create("audio/wav".toMediaType(), wavBytes)
                        val part = okhttp3.MultipartBody.Part.createFormData("file", "audio.wav", requestBody)
                        val lang = if (appLanguage.value == AppLanguage.VIETNAMESE) "vi" else "en"
                        
                        // 1. Get STT instantly and show on UI
                        val sttResponse = com.wheelchair.cockpit.api.CopilotClient.service.sttOnly(part)
                        val transcript = sttResponse.transcript
                        
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            partialPublisher.clear()
                            partialTranscript.value = transcript
                            statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) "Đang suy nghĩ..." else "Thinking..."
                        }
                        
                        // 2. Local Intent parsing (re-using existing processUserSpeech)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            processUserSpeech(transcript)
                        }
                    } catch (e: Exception) {
                        Log.e("CockpitUI", "Backend voice upload failed", e)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            assistantState.value = AssistantState.IDLE
                            statusText.value = "Lỗi xử lý âm thanh. Đã về Standby."
                            startVoskListening()
                        }
                    }
                }
                
            } catch(e: Exception) {
                Log.e("CockpitUI", "Recording failed", e)
                runOnUiThread { 
                    assistantState.value = AssistantState.IDLE 
                    startVoskListening()
                }
            }
        }.start()
    }

    private fun handleVoiceQueryResponse(response: com.wheelchair.cockpit.api.VoiceQueryResponse) {
        val currentSpeed = carPropertyHelper.speedFlow.value
        val query = response.transcript.lowercase(java.util.Locale.ROOT)
        val isFoldAction = query.contains("gập") || query.contains("đóng") || query.contains("thu") || query.contains("cất") || query.contains("fold") || query.contains("close") || query.contains("retract")
        val isMirrorTarget = query.contains("gương") || query.contains("mirror")
        val isMirrorFoldingRequest = isFoldAction && isMirrorTarget
        
        if (isMirrorFoldingRequest && currentSpeed > 0f) {
            val warningText = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                "Yêu cầu bị từ chối: Không thể gập gương khi xe đang di chuyển. Vui lòng dừng xe an toàn!"
            } else {
                "Request denied: Cannot fold mirrors while the vehicle is in motion. Please stop safely first!"
            }
            chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = false, text = warningText))
            citations.value = emptyList()
            assistantState.value = AssistantState.IDLE
            statusText.value = warningText
            safetyWarning.value = warningText
            mainHandler.postDelayed({ if (safetyWarning.value == warningText) safetyWarning.value = null }, 5000)
            speakOut(warningText)
            return
        }

        chatHistory.add(
            com.wheelchair.cockpit.ui.components.ChatMessage(
                isUser = false,
                text = response.answer,
                citations = response.citations,
                timing = if (devSettingsStore.current().developerModeEnabled) {
                    val lat = response.latency
                    com.wheelchair.cockpit.ui.components.MessageTiming(
                        rttMs = lat.total_ms.toLong().coerceAtLeast(0L),
                        sttMs = lat.stt_ms.takeIf { it > 0 },
                        ragMs = lat.core_ai_ms.takeIf { it > 0 },
                        ttsMs = lat.tts_ms.takeIf { it > 0 },
                        totalMs = lat.total_ms.takeIf { it > 0 }?.toLong()
                    )
                } else {
                    null
                }
            )
        )
        citations.value = response.citations
        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) "Đã nhận câu trả lời." else "Response received."
        // MODIFIED: #17 mock motion from voice command_id / RAG cites
        val vi = appLanguage.value == AppLanguage.VIETNAMESE
        mockActuationForCommandId(response.command_id)?.let { showMockActuation(it) }
            ?: run {
                if (response.citations.isNotEmpty()) {
                    showMockActuation(mockActuationForRagSuccess(vi))
                }
            }
        
        if (!response.audio_base64.isNullOrEmpty()) {
            playBase64Audio(response.audio_base64, response.answer)
        } else {
            speakOut(response.answer)
        }
    }

    private fun stopGoogleListening() {
        isCustomRecording = false
        autoSleepRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    private fun triggerKeywordWake() {
        mainHandler.post {
            if (assistantState.value != AssistantState.IDLE) return@post
            
            stopVoskListening()
            
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                mainHandler.postDelayed({ toneGen.release() }, 200)
            } catch (e: Exception) {
                Log.e("CockpitUI", "Beep error", e)
            }

            assistantState.value = AssistantState.WAKE_DETECTED
            micDiagStatus.value = MicDiag.LISTENING
            partialPublisher.clear()
            statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                "Dạ, tôi nghe. Xin mời bạn nói..."
            } else {
                "I'm listening. Please speak now..."
            }
            citations.value = emptyList()
            resetAutoSleepTimer()
            
            // Delay starting Google STT by 500ms to let Vosk release the mic completely
            mainHandler.postDelayed({
                if (assistantState.value == AssistantState.WAKE_DETECTED) {
                    startGoogleListening()
                }
            }, 500)
        }
    }

    private fun resetAutoSleepTimer() {
        autoSleepRunnable?.let { mainHandler.removeCallbacks(it) }
        autoSleepRunnable = Runnable {
            if (assistantState.value == AssistantState.WAKE_DETECTED) {
                assistantState.value = AssistantState.IDLE
                // MODIFIED: clear Siri caption + restore mic diag on listen timeout
                partialPublisher.clear()
                micDiagStatus.value = MicDiag.OK
                statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                    "Hết thời gian chờ. Trở về trạng thái Standby."
                } else {
                    "Timeout. Returning to System Standby."
                }
                startVoskListening()
            }
        }
        mainHandler.postDelayed(autoSleepRunnable!!, 10000)
    }

    private fun handleRecognizedSpeech(text: String) {
        if (assistantState.value == AssistantState.WAKE_DETECTED) {
            processUserSpeech(text)
        } else {
            assistantState.value = AssistantState.IDLE
            startVoskListening()
        }
    }

    private fun processUserSpeech(query: String) {
        autoSleepRunnable?.let { mainHandler.removeCallbacks(it) }
        
        if (query.isNotBlank()) {
            chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = true, text = query))
        }

        // VHAL Speed-sensitive Safety Gate for Mirror Folding
        val currentSpeed = carPropertyHelper.speedFlow.value
        val isFoldAction = query.contains("gập", ignoreCase = true) || 
                           query.contains("đóng", ignoreCase = true) || 
                           query.contains("thu", ignoreCase = true) || 
                           query.contains("cất", ignoreCase = true) || 
                           query.contains("fold", ignoreCase = true) || 
                           query.contains("close", ignoreCase = true) || 
                           query.contains("retract", ignoreCase = true)
        val isMirrorTarget = query.contains("gương", ignoreCase = true) || 
                             query.contains("mirror", ignoreCase = true)
        val isMirrorFoldingRequest = isFoldAction && isMirrorTarget
        
        if (isMirrorFoldingRequest && currentSpeed > 0f) {
            val warningText = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                "Yêu cầu bị từ chối: Không thể gập gương khi xe đang di chuyển. Vui lòng dừng xe an toàn!"
            } else {
                "Request denied: Cannot fold mirrors while the vehicle is in motion. Please stop safely first!"
            }
            
            chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = false, text = warningText))
            citations.value = emptyList()
            assistantState.value = AssistantState.IDLE
            statusText.value = warningText
            safetyWarning.value = warningText
            
            // Auto-dismiss safety warning banner after 5 seconds
            mainHandler.postDelayed({
                if (safetyWarning.value == warningText) {
                    safetyWarning.value = null
                }
            }, 5000)
            
            speakOut(warningText)
            return
        }
        val q = query.lowercase(Locale.ROOT)
        
        // Auto-detect query language for dynamic response
        val isQueryVietnamese = Regex("[áàảãạăắằẳẵặâấầẩẫậđéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵ]", RegexOption.IGNORE_CASE).containsMatchIn(query)
        val isQueryEnglish = Regex("\\b(turn|on|off|open|close|fold|unfold|door|mirror|ac|hvac|air|condition|how|what|why|is|the|a|to|can|you)\\b", RegexOption.IGNORE_CASE).containsMatchIn(query)
        val replyIsVietnamese = if (isQueryVietnamese) true else if (isQueryEnglish) false else appLanguage.value == AppLanguage.VIETNAMESE
        
        // --- LOCAL VHAL INTENT PARSING ---
        
        // 1. HVAC Control
        val tempMatch = Regex("(\\d+)\\s*(độ|degrees|degree|c)").find(q)
        if (q.contains("điều hòa") || q.contains("máy lạnh") || q.contains("ac") || q.contains("a/c") || q.contains("air condition") || q.contains("nhiệt độ") || tempMatch != null) {
            val turnOn = q.contains("bật") || q.contains("mở") || q.contains("turn on")
            val turnOff = q.contains("tắt") || q.contains("turn off")
            val adjust = q.contains("tăng") || q.contains("giảm") || q.contains("turn up") || q.contains("turn down")
            
            if (turnOn || turnOff || adjust || tempMatch != null) {
                if (!turnOn && !turnOff && !isHvacOn.value) {
                    val rejectReply = if (replyIsVietnamese) "Điều hòa đang tắt, không thể điều chỉnh nhiệt độ." else "AC is currently off, cannot adjust temperature."
                    chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = false, text = rejectReply))
                    citations.value = emptyList()
                    assistantState.value = AssistantState.IDLE
                    statusText.value = rejectReply
                    speakOut(rejectReply, forceEnglish = !replyIsVietnamese)
                    return
                }
                
                if (turnOn) {
                    carPropertyHelper.setHvacState(0, true)
                    isHvacOn.value = true
                } else if (turnOff) {
                    carPropertyHelper.setHvacState(0, false)
                    isHvacOn.value = false
                }
                
                var reply = if (replyIsVietnamese) "Đã " + (if (turnOn) "bật" else if (turnOff) "tắt" else "điều chỉnh") + " điều hòa" else "HVAC " + (if (turnOn) "turned on" else if (turnOff) "turned off" else "adjusted")
                
                if (tempMatch != null) {
                    val tempValue = tempMatch.groupValues[1].toFloatOrNull()
                    if (tempValue != null) {
                        // VHAL HVAC_TEMPERATURE_SET natively expects Celsius. 
                        // If user says "62" (Fahrenheit), we auto-convert it to Celsius (~16.6C)
                        val actualCelsius = if (tempValue > 40f) ((tempValue - 32f) * 5f / 9f) else tempValue
                        carPropertyHelper.setHvacTemperature(0, actualCelsius)
                        reply += if (replyIsVietnamese) " ở mức $tempValue độ." else " to $tempValue degrees."
                    }
                } else {
                    reply += "."
                }
                
                chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = false, text = reply))
                citations.value = emptyList()
                assistantState.value = AssistantState.IDLE
                statusText.value = reply
                // MODIFIED: #17 mock HVAC motion
                showMockActuation(
                    MockActuationEvent(
                        kind = MockActuationKind.HVAC,
                        titleVi = "Điều hòa",
                        titleEn = "Climate",
                        subtitleVi = "Mô phỏng HVAC thành công",
                        subtitleEn = "Mock HVAC actuation succeeded"
                    )
                )
                speakOut(reply, forceEnglish = !replyIsVietnamese)
                return
            }
        }
        
        // 2. Door Control
        if (q.contains("cửa") || q.contains("door")) {
            val unlock = q.contains("mở") || q.contains("unlock") || q.contains("open")
            val lock = q.contains("khóa") || q.contains("đóng") || q.contains("lock") || q.contains("close")
            
            if (unlock || lock) {
                carPropertyHelper.setDoorLock(0, lock)
                val reply = if (replyIsVietnamese) {
                    if (unlock) "Đã mở khóa cửa xe." else "Đã khóa cửa xe an toàn."
                } else {
                    if (unlock) "Doors unlocked." else "Doors locked securely."
                }
                chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = false, text = reply))
                citations.value = emptyList()
                assistantState.value = AssistantState.IDLE
                statusText.value = reply
                // MODIFIED: #17 mock door motion
                showMockActuation(
                    MockActuationEvent(
                        kind = MockActuationKind.DOOR,
                        titleVi = "Cửa xe",
                        titleEn = "Doors",
                        subtitleVi = if (unlock) "Mô phỏng mở khóa cửa" else "Mô phỏng khóa cửa",
                        subtitleEn = if (unlock) "Mock unlock succeeded" else "Mock lock succeeded"
                    )
                )
                speakOut(reply, forceEnglish = !replyIsVietnamese)
                return
            }
        }

        // 2b. Music mock (quick-action / voice) — mentor demo motion, not deep media
        if (q.contains("nhạc") || q.contains("music") || q.contains("bài hát") || q.contains("play music")) {
            val reply = if (replyIsVietnamese) {
                "Đã bật nhạc (mô phỏng)."
            } else {
                "Music started (mock)."
            }
            chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = false, text = reply))
            citations.value = emptyList()
            assistantState.value = AssistantState.IDLE
            statusText.value = reply
            showMockActuation(
                MockActuationEvent(
                    kind = MockActuationKind.MUSIC,
                    titleVi = "Âm nhạc",
                    titleEn = "Music",
                    subtitleVi = "Mô phỏng phát nhạc",
                    subtitleEn = "Mock music playback started"
                )
            )
            speakOut(reply, forceEnglish = !replyIsVietnamese)
            return
        }
        
        // 3. Mirror Control
        if (q.contains("gương") || q.contains("mirror")) {
            val unfold = q.contains("mở") || q.contains("unfold") || q.contains("open")
            val fold = q.contains("gập") || q.contains("đóng") || q.contains("fold") || q.contains("close")
            
            if (unfold || fold) {
                carPropertyHelper.setMirrorFold(0, fold)
                val reply = if (replyIsVietnamese) {
                    if (unfold) "Đã mở gương chiếu hậu." else "Đã gập gương chiếu hậu."
                } else {
                    if (unfold) "Mirrors unfolded." else "Mirrors folded."
                }
                chatHistory.add(com.wheelchair.cockpit.ui.components.ChatMessage(isUser = false, text = reply))
                citations.value = emptyList()
                assistantState.value = AssistantState.IDLE
                statusText.value = reply
                speakOut(reply, forceEnglish = !replyIsVietnamese)
                return
            }
        }

        assistantState.value = AssistantState.PROCESSING
        stopVoskListening()
        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) "Đang hỏi Copilot: \"$query\"" else "Asking Copilot: \"$query\""
        citations.value = emptyList()
        partialPublisher.clear()

        // --- START MODIFICATION ---
        // Query path through repository; publish latency only in developer mode.
        CoroutineScope(Dispatchers.IO).launch {
            val started = SystemClock.elapsedRealtime()
            try {
                val langCode = if (replyIsVietnamese) "vi" else "en"
                val response = copilotRepository.sendQuery(query, language = langCode)
                val elapsed = SystemClock.elapsedRealtime() - started
                runOnUiThread {
                    val dev = devSettingsStore.current().developerModeEnabled
                    if (dev) {
                        lastQueryLatencyMs.value = elapsed
                    }
                    val timing = if (dev) {
                        com.wheelchair.cockpit.ui.components.MessageTiming(
                            rttMs = elapsed,
                            ragMs = response.latency?.core_ai_ms?.takeIf { it > 0 },
                            ttsMs = response.latency?.tts_ms?.takeIf { it > 0 },
                            totalMs = response.latency?.total_ms?.takeIf { it > 0 }?.toLong()
                        )
                    } else {
                        null
                    }
                    chatHistory.add(
                        com.wheelchair.cockpit.ui.components.ChatMessage(
                            isUser = false,
                            text = response.answer,
                            citations = response.citations,
                            timing = timing
                        )
                    )
                    citations.value = response.citations
                    // MODIFIED: #17 mock motion from command_id or RAG success
                    val vi = replyIsVietnamese
                    mockActuationForCommandId(response.command_id)?.let { showMockActuation(it) }
                        ?: run {
                            if (response.status == "success" && response.citations.isNotEmpty()) {
                                showMockActuation(mockActuationForRagSuccess(vi))
                            }
                        }
                    if (response.audio_base64 != null) {
                        playBase64Audio(response.audio_base64, response.answer)
                    } else {
                        speakOut(response.answer, forceEnglish = !replyIsVietnamese)
                    }
                }
            } catch (e: Exception) {
                Log.e("CockpitUI", "Backend Error", e)
                val elapsed = SystemClock.elapsedRealtime() - started
                runOnUiThread {
                    val dev = devSettingsStore.current().developerModeEnabled
                    if (dev) {
                        lastQueryLatencyMs.value = elapsed
                    }
                    val fallback = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                        "Lỗi kết nối Server: ${e.localizedMessage}"
                    } else {
                        "Gemini/Backend Error: ${e.localizedMessage}"
                    }
                    chatHistory.add(
                        com.wheelchair.cockpit.ui.components.ChatMessage(
                            isUser = false,
                            text = fallback,
                            timing = if (dev) {
                                com.wheelchair.cockpit.ui.components.MessageTiming(rttMs = elapsed)
                            } else {
                                null
                            }
                        )
                    )
                    speakOut(fallback)
                }
            }
        }
        // --- END MODIFICATION ---
    }

    // --- START MODIFICATION ---
    private fun runHealthCheck() {
        healthChecking.value = true
        CoroutineScope(Dispatchers.IO).launch {
            val result = copilotRepository.checkHealth()
            runOnUiThread {
                healthResult.value = result
                healthChecking.value = false
            }
        }
    }
    // --- END MODIFICATION ---

    private fun handleMicTap() {
        when (assistantState.value) {
            AssistantState.SPEAKING -> {
                tts?.stop()
                try {
                    if (backendMediaPlayer?.isPlaying == true) {
                        backendMediaPlayer?.stop()
                    }
                    backendMediaPlayer?.release()
                    backendMediaPlayer = null
                } catch (e: Exception) {
                    Log.e("CockpitUI", "Error stopping backend player", e)
                }
                abandonTtsAudioFocus()
                assistantState.value = AssistantState.IDLE
                statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) "Đã dừng trợ lý." else "Assistant stopped."
                startVoskListening()
            }
            AssistantState.IDLE -> {
                triggerKeywordWake()
            }
            AssistantState.WAKE_DETECTED -> {
                assistantState.value = AssistantState.IDLE
                statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) "Đã tắt mic." else "Mic disabled."
                startVoskListening()
            }
            AssistantState.PROCESSING -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGoogleListening()
        // Keep microphone FGS running across Activity teardown for background Hey Car.
        // Only tear down the in-Activity fallback engine here.
        fallbackWakeEngine?.shutdown()
        fallbackWakeEngine = null
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        carPropertyHelper.shutdown()
    }

    // --- START MODIFICATION ---
    companion object {
        /** True while MainActivity is between onResume and onPause (interactive foreground). */
        @Volatile
        var isUiForeground: Boolean = false
    }
    // --- END MODIFICATION ---
}
