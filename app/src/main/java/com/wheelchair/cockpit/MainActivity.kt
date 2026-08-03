package com.wheelchair.cockpit

import android.Manifest
import android.car.VehiclePropertyIds
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.wheelchair.cockpit.api.QueryRequest
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.components.*
import com.wheelchair.cockpit.ui.dialogs.SystemSettingsDialog
import com.wheelchair.cockpit.ui.theme.CockpitColors
import com.wheelchair.cockpit.vhal.CarPropertyHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var carPropertyHelper: CarPropertyHelper

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningSessionActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var restartListeningRunnable: Runnable? = null
    private var backendMediaPlayer: android.media.MediaPlayer? = null

    private var assistantState = mutableStateOf(AssistantState.IDLE)
    private var statusText = mutableStateOf("System Standby. Say \"Hey Car\" to activate.")
    private var rmsLevel = mutableFloatStateOf(0f)
    private var copilotAnswer = mutableStateOf("")
    private var citations = mutableStateOf<List<CitationInfo>>(emptyList())
    private var vehicleSpeed = mutableFloatStateOf(0.0f)
    private var isHvacOn = mutableStateOf(false)
    private var isDrivingRestricted = mutableStateOf(false)
    private var hasWarnedSpeed = false
    private var safetyWarning = mutableStateOf<String?>(null)

    private var appLanguage = mutableStateOf(AppLanguage.VIETNAMESE)
    private var displayTheme = mutableStateOf(DisplayTheme.LIGHT)
    private var showSettingsDialog = mutableStateOf(false)
    private var geminiApiKey = mutableStateOf("")

    private var autoSleepRunnable: Runnable? = null
    private var maxRmsInSession = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geminiApiKey.value = BuildConfig.GEMINI_API_KEY.ifEmpty { "ai_studio_api_key_here" }

        checkAudioPermissions()
        initTextToSpeech()

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
                    if (isRestricted) {
                        showSettingsDialog.value = false
                    }
                }
            }
        )

        setContent {
            val speed by carPropertyHelper.speedFlow.collectAsState()
            val hvacOn by carPropertyHelper.hvacOnFlow.collectAsState()
            val drivingRestricted by carPropertyHelper.uxRestrictionsFlow.collectAsState()
            val activeWarning by safetyWarning

            Box(modifier = Modifier.fillMaxSize()) {
                CockpitAppScreen(
                    assistantState = assistantState.value,
                    statusText = statusText.value,
                    copilotAnswer = copilotAnswer.value,
                    citations = citations.value,
                    vehicleSpeed = speed,
                    isHvacOn = hvacOn,
                    rmsLevel = rmsLevel.floatValue,
                    appLanguage = appLanguage.value,
                    displayTheme = displayTheme.value,
                    showSettingsDialog = showSettingsDialog.value,
                    isDrivingRestricted = drivingRestricted,
                    onHvacToggle = { toggleHvacProperty() },
                    onManualSend = { query -> processUserSpeech(query) },
                    onMicTap = { handleMicTap() },
                    onWakeSimulate = { triggerKeywordWake() },
                    onOpenSettings = { showSettingsDialog.value = true },
                    onCloseSettings = { showSettingsDialog.value = false },
                    onLanguageChange = { lang -> appLanguage.value = lang },
                    onThemeChange = { theme -> displayTheme.value = theme }
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
            }
        }
    }

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
        if (checkSelfPermission("android.car.permission.CAR_SPEED") != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add("android.car.permission.CAR_SPEED")
        }

        if (permissionsToRequest.isNotEmpty()) {
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
            }
            if (checkSelfPermission("android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED) {
                // Trigger CarService reconnection or let it bind naturally
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

    private fun playBase64Audio(base64Audio: String, fallbackText: String) {
        try {
            stopGoogleListening()
            stopVoskListening()
            tts?.stop()
            assistantState.value = AssistantState.SPEAKING
            
            val audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
            val tempFile = java.io.File(cacheDir, "response_audio.mp3")
            java.io.FileOutputStream(tempFile).use { it.write(audioBytes) }
            
            backendMediaPlayer?.release()
            backendMediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    runOnUiThread {
                        assistantState.value = AssistantState.IDLE
                        startVoskListening()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CockpitUI", "Error playing base64 audio", e)
            speakOut(fallbackText) // Fallback to local TTS
        }
    }

    private fun speakOut(text: String) {
        stopGoogleListening()
        stopVoskListening()
        assistantState.value = AssistantState.SPEAKING
        
        // Dynamically update TTS language based on current setting
        val loc = if (appLanguage.value == AppLanguage.VIETNAMESE) Locale("vi", "VN") else Locale.US
        val result = tts?.setLanguage(loc)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }

        // Hackathon trick: Phonetic replacement for basic AOSP TTS
        var speakText = text
        if (appLanguage.value == AppLanguage.VIETNAMESE) {
            speakText = speakText
                .replace(Regex("\\bAC\\b"), "Ây Xi")
                .replace(Regex("\\bHVAC\\b"), "Hát Vát")
                .replace(Regex("\\bADAS\\b"), "Ây Đát")
                .replace(Regex("\\bGPS\\b"), "Gờ Pê Ét")
                .replace(Regex("\\bCopilot\\b", RegexOption.IGNORE_CASE), "Cô Pai Lọt")
                .replace(Regex("\\bBluetooth\\b", RegexOption.IGNORE_CASE), "Bờ Lu Tút")
        } else {
            // English TTS reading Vietnamese words
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

    private var voskModel: org.vosk.Model? = null
    private var voskSpeechService: org.vosk.android.SpeechService? = null

    private fun initAudioRecognizers() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { resetAutoSleepTimer() }
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
                if (assistantState.value == AssistantState.WAKE_DETECTED) {
                    assistantState.value = AssistantState.IDLE
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
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    handleRecognizedSpeech(spokenText)
                } else if (assistantState.value == AssistantState.WAKE_DETECTED) {
                    assistantState.value = AssistantState.IDLE
                    startVoskListening()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Unpack Vosk Model
        org.vosk.android.StorageService.unpack(this, "model-en", "model",
            { model ->
                voskModel = model
                if (assistantState.value == AssistantState.IDLE) {
                    startVoskListening()
                }
            },
            { e ->
                Log.e("CockpitUI", "Failed to unpack Vosk model", e)
            }
        )
    }

    private val voskListener = object : org.vosk.android.RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (hypothesis != null) {
                Log.d("CockpitUI", "Vosk Partial: $hypothesis")
                if (containsWakeWord(hypothesis)) triggerKeywordWake()
            }
        }
        override fun onResult(hypothesis: String?) {
            if (hypothesis != null) {
                Log.d("CockpitUI", "Vosk Result: $hypothesis")
                if (containsWakeWord(hypothesis)) triggerKeywordWake()
            }
        }
        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(e: Exception?) {
            Log.e("CockpitUI", "Vosk Error", e)
        }
        override fun onTimeout() {}
    }

    private fun startVoskListening() {
        if (voskModel == null || assistantState.value != AssistantState.IDLE) return
        if (voskSpeechService != null) {
            voskSpeechService?.startListening(voskListener)
            return
        }
        try {
            val rec = org.vosk.Recognizer(voskModel, 16000.0f)
            voskSpeechService = org.vosk.android.SpeechService(rec, 16000.0f)
            voskSpeechService?.startListening(voskListener)
        } catch (e: Exception) {
            Log.e("CockpitUI", "Failed to start Vosk", e)
        }
    }

    private fun stopVoskListening() {
        voskSpeechService?.cancel()
        voskSpeechService?.shutdown()
        voskSpeechService = null
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("hey car") || 
               lower.contains("hay car") || 
               lower.contains("hây ca") || 
               lower.contains("hây car") || 
               lower.contains("he ca") || 
               lower.contains("hey call") ||
               lower.contains("hay call") ||
               lower.contains("he call") ||
               lower.contains("take care") ||
               lower.contains("xe ơi") || 
               lower.contains("chào xe") ||
               lower.matches(Regex(".*\\bhey\\b.*")) ||
               lower.matches(Regex(".*\\bhi\\b.*")) ||
               lower.matches(Regex(".*\\bhello\\b.*")) ||
               lower.matches(Regex(".*\\bokay\\b.*")) ||
               lower.matches(Regex(".*\\bok\\b.*"))
    }

    private fun startGoogleListening() {
        if (assistantState.value == AssistantState.SPEAKING) return
        maxRmsInSession = 0f

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val listenLang = if (appLanguage.value == AppLanguage.VIETNAMESE) "vi-VN" else "en-US"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, listenLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("CockpitUI", "Start listening error", e)
        }
    }

    private fun stopGoogleListening() {
        autoSleepRunnable?.let { mainHandler.removeCallbacks(it) }
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("CockpitUI", "Stop listening error", e)
        }
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
            statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                "Dạ, tôi nghe. Xin mời bạn nói..."
            } else {
                "I'm listening. Please speak now..."
            }
            copilotAnswer.value = ""
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
            
            copilotAnswer.value = warningText
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

        assistantState.value = AssistantState.PROCESSING
        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) "Đang hỏi Copilot: \"$query\"" else "Asking Copilot: \"$query\""
        copilotAnswer.value = ""
        citations.value = emptyList()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = CopilotClient.service.queryCopilot(QueryRequest(query = query))
                runOnUiThread {
                    copilotAnswer.value = response.answer
                    citations.value = response.citations
                    if (response.audio_base64 != null) {
                        playBase64Audio(response.audio_base64, response.answer)
                    } else {
                        speakOut(response.answer)
                    }
                }
            } catch (e: Exception) {
                Log.e("CockpitUI", "Backend Error", e)
                runOnUiThread {
                    val fallback = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                        "Lỗi kết nối Server: ${e.localizedMessage}"
                    } else {
                        "Gemini/Backend Error: ${e.localizedMessage}"
                    }
                    copilotAnswer.value = fallback
                    speakOut(fallback)
                }
            }
        }
    }

    private fun handleMicTap() {
        when (assistantState.value) {
            AssistantState.SPEAKING -> {
                tts?.stop()
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
        stopVoskListening()
        voskSpeechService?.shutdown()
        voskModel?.close()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        carPropertyHelper.shutdown()
    }
}

@Composable
fun CockpitAppScreen(
    assistantState: AssistantState,
    statusText: String,
    copilotAnswer: String,
    citations: List<CitationInfo>,
    vehicleSpeed: Float,
    isHvacOn: Boolean,
    rmsLevel: Float,
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    showSettingsDialog: Boolean,
    isDrivingRestricted: Boolean = false,
    onHvacToggle: () -> Unit,
    onManualSend: (String) -> Unit,
    onMicTap: () -> Unit,
    onWakeSimulate: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (DisplayTheme) -> Unit
) {
    var queryInput by remember { mutableStateOf("") }
    var activeNavIndex by remember { mutableIntStateOf(0) }

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val primaryBlue = CockpitColors.getPrimaryBlue(displayTheme)
    val primaryContainer = CockpitColors.getPrimaryContainer(displayTheme)
    val backgroundBg = CockpitColors.getBackgroundBg(displayTheme)
    val surfaceContainer = CockpitColors.getSurfaceContainer(displayTheme)
    val surfaceContainerLow = CockpitColors.getSurfaceContainerLow(displayTheme)
    val textMain = CockpitColors.getTextMain(displayTheme)
    val textSecondary = CockpitColors.getTextSecondary(displayTheme)
    val outlineVariant = CockpitColors.getOutlineVariant(displayTheme)

    val indicatorColor = when (assistantState) {
        AssistantState.IDLE -> primaryBlue
        AssistantState.WAKE_DETECTED -> androidx.compose.ui.graphics.Color(0xFF10B981)
        AssistantState.PROCESSING -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
        AssistantState.SPEAKING -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 52.dp)
        ) {
            // 1. TOP APP BAR
            AutomotiveTopBar(
                vehicleSpeed = vehicleSpeed,
                isHvacOn = isHvacOn,
                primaryBlue = primaryBlue,
                backgroundBg = backgroundBg,
                surfaceContainer = surfaceContainer,
                textMain = textMain,
                outlineVariant = outlineVariant,
                isDrivingRestricted = isDrivingRestricted,
                onHvacToggle = onHvacToggle,
                onOpenSettings = onOpenSettings
            )

            // 2. MAIN CONTENT BODY
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LEFT PANEL: Status + Input + Quick Actions
                Column(
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SystemStatusCard(
                        assistantState = assistantState,
                        statusText = statusText,
                        pulseScale = pulseScale,
                        appLanguage = appLanguage,
                        primaryBlue = primaryBlue,
                        primaryContainer = primaryContainer,
                        surfaceContainer = surfaceContainer,
                        textMain = textMain,
                        indicatorColor = indicatorColor,
                        outlineVariant = outlineVariant,
                        onWakeSimulate = onWakeSimulate,
                        onMicTap = onMicTap
                    )

                    ManualInputBar(
                        queryInput = queryInput,
                        assistantState = assistantState,
                        appLanguage = appLanguage,
                        primaryBlue = primaryBlue,
                        surfaceContainer = surfaceContainer,
                        textMain = textMain,
                        textSecondary = textSecondary,
                        outlineVariant = outlineVariant,
                        isDrivingRestricted = isDrivingRestricted,
                        onQueryInputChange = { queryInput = it },
                        onManualSend = onManualSend
                    )

                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionCard(
                            title = if (appLanguage == AppLanguage.VIETNAMESE) "Bản đồ" else "Maps",
                            icon = Icons.Rounded.Map,
                            primaryColor = primaryBlue,
                            surfaceColor = surfaceContainer,
                            textColor = textMain,
                            borderColor = outlineVariant,
                            modifier = Modifier.weight(1f),
                            enabled = !isDrivingRestricted,
                            onClick = { onManualSend(if (appLanguage == AppLanguage.VIETNAMESE) "Mở bản đồ" else "Open Maps") }
                        )
                        QuickActionCard(
                            title = if (appLanguage == AppLanguage.VIETNAMESE) "Âm nhạc" else "Music",
                            icon = Icons.Rounded.MusicNote,
                            primaryColor = primaryBlue,
                            surfaceColor = surfaceContainer,
                            textColor = textMain,
                            borderColor = outlineVariant,
                            modifier = Modifier.weight(1f),
                            enabled = !isDrivingRestricted,
                            onClick = { onManualSend(if (appLanguage == AppLanguage.VIETNAMESE) "Bật nhạc" else "Play music") }
                        )
                    }
                }

                // RIGHT PANEL: AI Copilot Results
                CopilotResponsePanel(
                    copilotAnswer = copilotAnswer,
                    citations = citations,
                    assistantState = assistantState,
                    rmsLevel = rmsLevel,
                    appLanguage = appLanguage,
                    primaryBlue = primaryBlue,
                    surfaceContainer = surfaceContainer,
                    surfaceContainerLow = surfaceContainerLow,
                    textMain = textMain,
                    textSecondary = textSecondary,
                    outlineVariant = outlineVariant,
                    modifier = Modifier.weight(2.6f)
                )
            }
        }

        // 3. BOTTOM AUTOMOTIVE NAVIGATION DOCK
        AutomotiveBottomDock(
            activeNavIndex = activeNavIndex,
            appLanguage = appLanguage,
            primaryBlue = primaryBlue,
            surfaceContainer = surfaceContainer,
            textSecondary = textSecondary,
            outlineVariant = outlineVariant,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            isDrivingRestricted = isDrivingRestricted,
            onNavSelect = { activeNavIndex = it },
            onMicTap = onMicTap,
            onOpenSettings = onOpenSettings
        )

        // 4. SYSTEM SETTINGS OVERLAY MODAL
        SystemSettingsDialog(
            show = showSettingsDialog,
            appLanguage = appLanguage,
            displayTheme = displayTheme,
            primaryBlue = primaryBlue,
            textMain = textMain,
            outlineVariant = outlineVariant,
            onClose = onCloseSettings,
            onLanguageChange = onLanguageChange,
            onThemeChange = onThemeChange
        )
    }
}
