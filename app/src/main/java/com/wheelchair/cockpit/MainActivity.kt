package com.wheelchair.cockpit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.car.VehiclePropertyIds
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.api.CitationInfo
import com.wheelchair.cockpit.api.CopilotClient
import com.wheelchair.cockpit.api.QueryRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

enum class AssistantState {
    IDLE,           // STANDBY: Only keyword spotting, no backend streaming
    WAKE_DETECTED,  // ACTIVATED: Awake, waiting for user command
    PROCESSING,     // Querying AI RAG API
    SPEAKING        // TTS speaking response
}

enum class AppLanguage {
    VIETNAMESE,
    ENGLISH
}

enum class DisplayTheme {
    LIGHT,
    DARK,
    CENTRAL
}

class MainActivity : ComponentActivity() {

    private lateinit var carPropertyHelper: CarPropertyHelper

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningSessionActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var restartListeningRunnable: Runnable? = null

    private var assistantState = mutableStateOf(AssistantState.IDLE)
    private var statusText = mutableStateOf("System Standby. Say \"Hey Car\" to activate.")
    private var rmsLevel = mutableFloatStateOf(0f)
    private var copilotAnswer = mutableStateOf("")
    private var citations = mutableStateOf<List<CitationInfo>>(emptyList())
    private var vehicleSpeed = mutableFloatStateOf(0.0f)
    private var isHvacOn = mutableStateOf(false)
    private var hasWarnedSpeed = false

    private var appLanguage = mutableStateOf(AppLanguage.VIETNAMESE)
    private var displayTheme = mutableStateOf(DisplayTheme.LIGHT)
    private var showSettingsDialog = mutableStateOf(false)
    private var geminiApiKey = mutableStateOf("")

    private var autoSleepRunnable: Runnable? = null
    private var maxRmsInSession = 0f

    // Comprehensive wake words including Vietnamese STT phonetic mis-transcriptions for "Hey Car"
    private val wakeWords = listOf(
        "hey car", "hey copilot", "hey car assistant", "hi car", "hello car",
        "hey call", "hey card", "hey can", "hey calm", "hay call", "hê call",
        "hây ca", "hay ca", "hê ca", "he car", "hê car", "ê car", "đây car",
        "xe ơi", "trợ lý ơi", "mở xe", "bảy ca", "mấy ca", "khay ca", "thay ca", "ngày ca",
        "thu hà", "hay không", "này nọ", "thưa ca", "thu ca", "thành ca", "theo ca", "thứ ca"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasPermission = checkAndRequestAudioPermission()
        val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        Log.d("CockpitSTT", "=== SYSTEM AUDIO INIT ===")
        Log.d("CockpitSTT", "RECORD_AUDIO Permission: $hasPermission")
        Log.d("CockpitSTT", "SpeechRecognizer Available: $recognizerAvailable")

        initTts()

        setContent {
            CockpitUI(
                assistantState = assistantState.value,
                statusText = statusText.value,
                copilotAnswer = copilotAnswer.value,
                citations = citations.value,
                vehicleSpeed = vehicleSpeed.value,
                isHvacOn = isHvacOn.value,
                rmsLevel = rmsLevel.value,
                appLanguage = appLanguage.value,
                displayTheme = displayTheme.value,
                showSettingsDialog = showSettingsDialog.value,
                geminiApiKey = geminiApiKey.value,
                onHvacToggle = { carPropertyHelper.setHvacState(0, !isHvacOn.value) },
                onManualSend = { text -> if (text.isNotBlank()) processUserCommand(text) },
                onMicTap = { triggerVoiceInputManually() },
                onWakeSimulate = { simulateWakeWord() },
                onOpenSettings = { showSettingsDialog.value = true },
                onCloseSettings = { showSettingsDialog.value = false },
                onLanguageChange = { lang -> setLanguage(lang) },
                onThemeChange = { theme -> displayTheme.value = theme },
                onGeminiKeyChange = { key ->
                    geminiApiKey.value = key
                    GeminiHelper.setApiKey(key)
                }
            )
        }

        carPropertyHelper = CarPropertyHelper(this) { propId, value ->
            when (propId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val speed = (value as? Float) ?: 0.0f
                    vehicleSpeed.value = speed
                    if (speed > 80f && !hasWarnedSpeed) {
                        hasWarnedSpeed = true
                        val alertMsg = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                            "Cảnh báo an toàn: Tốc độ xe vượt quá 80 kilomet trên giờ. Vui lòng tập trung lái xe."
                        } else {
                            "Safety Alert: Vehicle speed exceeds 80 kilometers per hour. Please focus on the road."
                        }
                        @Suppress("SpellCheckingInspection")
                        speakResponse(alertMsg)
                    } else if (speed <= 80f) {
                        hasWarnedSpeed = false
                    }
                }
                VehiclePropertyIds.HVAC_AC_ON -> {
                    isHvacOn.value = (value as? Boolean) ?: false
                }
            }
        }
    }

    private fun checkAndRequestAudioPermission(): Boolean {
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
        return granted
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyTtsLanguage()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("CockpitSTT", "TTS Started speaking: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d("CockpitSTT", "TTS Finished speaking: $utteranceId")
                        mainHandler.post {
                            if (utteranceId == "response") {
                                assistantState.value = AssistantState.IDLE
                                statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                                    "Hệ thống đang chờ. Nói \"Hey Car\" để gọi trợ lý."
                                } else {
                                    "System Standby. Say \"Hey Car\" to activate."
                                }
                                startContinuousListening()
                            } else if (utteranceId == "ack" || utteranceId == "disambiguation") {
                                startContinuousListening()
                            }
                        }
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        Log.e("CockpitSTT", "TTS Error on: $utteranceId")
                        mainHandler.post {
                            assistantState.value = AssistantState.IDLE
                            startContinuousListening()
                        }
                    }
                })
                mainHandler.postDelayed({ startContinuousListening() }, 500)
            } else {
                Log.e("CockpitSTT", "TTS Initialization failed with status: $status")
            }
        }
    }

    private fun applyTtsLanguage() {
        val locale = if (appLanguage.value == AppLanguage.VIETNAMESE) Locale("vi", "VN") else Locale.US
        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
        }
    }

    private fun setLanguage(language: AppLanguage) {
        appLanguage.value = language
        applyTtsLanguage()

        statusText.value = if (language == AppLanguage.VIETNAMESE) {
            "Hệ thống đang chờ. Nói \"Hey Car\" để gọi trợ lý."
        } else {
            "System Standby. Say \"Hey Car\" to activate."
        }

        destroySpeechRecognizer()
        restartListeningDelayed(300)
    }

    /**
     * Resilient Wake Word Matcher:
     * Accepts exact phrase match, bilingual phonetic variations, and multi-word fuzzy distance <= 2.
     */
    private fun isWakeWordMatched(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = text.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9àáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ ]"), "")
            .trim()

        if (normalized.length < 2) return false

        // 1. Direct match with phrase list (including "thu hà", "hay không", "này nọ" STT artifacts)
        if (wakeWords.any { normalized.contains(it) }) {
            Log.d("CockpitSTT", "Wake Word Matcher: Direct Phrase Match for '$normalized'")
            return true
        }

        // 2. Word pair fuzzy matching
        val words = normalized.split(" ").filter { it.isNotBlank() }
        if (words.size >= 2) {
            for (i in 0 until words.size - 1) {
                val w1 = words[i]
                val w2 = words[i + 1]

                val matchFirst = levenshteinDistance(w1, "hey") <= 1 ||
                                levenshteinDistance(w1, "hay") <= 1 ||
                                levenshteinDistance(w1, "hi") <= 1 ||
                                levenshteinDistance(w1, "thu") <= 1 ||
                                w1 == "hê" || w1 == "ê" || w1 == "đây"

                val matchSecond = levenshteinDistance(w2, "car") <= 2 ||
                                 levenshteinDistance(w2, "call") <= 2 ||
                                 levenshteinDistance(w2, "ca") <= 1 ||
                                 levenshteinDistance(w2, "hà") <= 1 ||
                                 w2 == "card" || w2 == "can"

                if (matchFirst && matchSecond) {
                    Log.d("CockpitSTT", "Wake Word Matcher: Pair Match ('$w1 $w2' in '$normalized')")
                    return true
                }
            }
        }

        // 3. Single string fuzzy match
        for (w in words) {
            if (w.length >= 4) {
                if (levenshteinDistance(w, "heycar") <= 2 ||
                    levenshteinDistance(w, "hayca") <= 2 ||
                    levenshteinDistance(w, "heycall") <= 2 ||
                    levenshteinDistance(w, "thuha") <= 1
                ) {
                    Log.d("CockpitSTT", "Wake Word Matcher: Word Match ('$w' in '$normalized')")
                    return true
                }
            }
        }

        Log.d("CockpitSTT", "Wake Word Matcher: No match for '$normalized'")
        return false
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costReplace, minOf(costInsert, costDelete))
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    private fun isAmbiguousOrCallConfusion(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault()).trim()
        val ambiguousList = listOf("call", "call me", "call car", "gọi", "gọi điện", "gọi xe", "car", "hey", "hê")
        return ambiguousList.contains(lower) || lower.length < 3
    }

    private fun startAutoSleepTimer() {
        cancelAutoSleepTimer()
        autoSleepRunnable = Runnable {
            if (assistantState.value == AssistantState.WAKE_DETECTED) {
                assistantState.value = AssistantState.IDLE
                statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                    "Hệ thống đang chờ. Nói \"Hey Car\" để gọi trợ lý."
                } else {
                    "System Standby. Say \"Hey Car\" to activate."
                }
                startContinuousListening()
            }
        }
        mainHandler.postDelayed(autoSleepRunnable!!, 8000L)
    }

    private fun cancelAutoSleepTimer() {
        autoSleepRunnable?.let { mainHandler.removeCallbacks(it) }
        autoSleepRunnable = null
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("CockpitSTT", "SpeechRecognizer destroy exception: ${e.message}")
        }
        speechRecognizer = null
        isListeningSessionActive = false
    }

    private fun ensureSpeechRecognizer() {
        if (speechRecognizer != null) return

        Log.d("CockpitSTT", "Creating SpeechRecognizer instance...")
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    maxRmsInSession = 0f
                    isListeningSessionActive = true
                    Log.d("CockpitSTT", "--> Listener Event: onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("CockpitSTT", "--> Listener Event: onBeginningOfSpeech (User started talking)")
                }
                override fun onRmsChanged(v: Float) {
                    val coerced = v.coerceIn(0f, 12f)
                    if (coerced > maxRmsInSession) maxRmsInSession = coerced
                    rmsLevel.value = coerced / 12f
                }
                override fun onBufferReceived(b: ByteArray?) {}

                override fun onPartialResults(bundle: Bundle?) {
                    val candidateList = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    Log.d("CockpitSTT", "--> Listener Event: onPartialResults candidates: $candidateList")

                    val matchedText = candidateList.firstOrNull { isWakeWordMatched(it) }
                    if (matchedText != null && assistantState.value == AssistantState.IDLE) {
                        Log.d("CockpitSTT", "--> Partial Match Succeeded with candidate: '$matchedText'")
                        assistantState.value = AssistantState.WAKE_DETECTED
                        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                            "Đã nhận lệnh \"Hey Car\". Bạn cần trợ giúp gì?"
                        } else {
                            "Wake word detected! How can I help you?"
                        }
                        startAutoSleepTimer()
                    }
                }
                override fun onEvent(t: Int, b: Bundle?) {}

                override fun onResults(bundle: Bundle?) {
                    isListeningSessionActive = false
                    val candidateList = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    val primaryText = candidateList.firstOrNull()?.trim() ?: ""
                    Log.d("CockpitSTT", "==================================================")
                    Log.d("CockpitSTT", "--> Listener Event: onResults candidates: $candidateList (State: ${assistantState.value})")

                    // Search across ALL returned candidates in array for a wake word match
                    val matchedCandidate = candidateList.firstOrNull { isWakeWordMatched(it) }
                    val detectedWake = matchedCandidate != null

                    if (primaryText.isNotBlank()) {
                        var commandText = primaryText

                        if (detectedWake) {
                            wakeWords.forEach { wake ->
                                commandText = commandText.replace(wake, "", ignoreCase = true).trim()
                            }
                        }

                        if (assistantState.value == AssistantState.IDLE) {
                            if (detectedWake) {
                                cancelAutoSleepTimer()
                                if (commandText.isNotBlank() && !isAmbiguousOrCallConfusion(commandText)) {
                                    processUserCommand(commandText)
                                } else {
                                    assistantState.value = AssistantState.WAKE_DETECTED
                                    statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                                        "Tôi đang lắng nghe. Bạn cần trợ giúp gì?"
                                    } else {
                                        "Yes, I am listening. How can I help?"
                                    }
                                    speakTtsAck()
                                    startAutoSleepTimer()
                                }
                            } else {
                                Log.d("CockpitSTT", "STANDBY Gate: Discarded ambient text: '$primaryText'")
                                statusText.value = "Standby: Heard \"$primaryText\" (Say \"Hey Car\")"
                                restartListeningDelayed(800)
                            }
                        } else if (assistantState.value == AssistantState.WAKE_DETECTED) {
                            cancelAutoSleepTimer()
                            processUserCommand(commandText)
                        } else {
                            restartListeningDelayed(1000)
                        }
                    } else {
                        Log.d("CockpitSTT", "--> Listener Event: onResults returned empty text")
                        restartListeningDelayed(800)
                    }
                }

                override fun onError(errorCode: Int) {
                    isListeningSessionActive = false
                    val errorName = getSpeechErrorName(errorCode)
                    Log.w("CockpitSTT", "--> Listener Event: onError: $errorName (Code: $errorCode)")

                    if (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        statusText.value = "Microphone permission required."
                        checkAndRequestAudioPermission()
                        return
                    }

                    if (errorCode == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                        errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        errorCode == SpeechRecognizer.ERROR_CLIENT
                    ) {
                        destroySpeechRecognizer()
                        restartListeningDelayed(1500)
                        return
                    }

                    if (assistantState.value == AssistantState.IDLE) {
                        restartListeningDelayed(800)
                    } else if (assistantState.value != AssistantState.PROCESSING &&
                        assistantState.value != AssistantState.SPEAKING
                    ) {
                        assistantState.value = AssistantState.IDLE
                        restartListeningDelayed(1000)
                    }
                }

                override fun onEndOfSpeech() {
                    Log.d("CockpitSTT", "--> Listener Event: onEndOfSpeech (User stopped talking)")
                }
            })
        }
    }

    private fun getSpeechErrorName(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO (3)"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT (5)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS (9)"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK (2)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT (1)"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH (7)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY (8)"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER (4)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT (6)"
            else -> "UNKNOWN_ERROR ($errorCode)"
        }
    }

    private fun startContinuousListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("CockpitSTT", "Speech recognition unavailable on this device!")
            statusText.value = "Speech recognition unavailable. Use manual text input."
            return
        }

        if (assistantState.value == AssistantState.PROCESSING || assistantState.value == AssistantState.SPEAKING) {
            Log.d("CockpitSTT", "Skipped listening because assistant is ${assistantState.value}")
            return
        }

        if (isListeningSessionActive) {
            Log.d("CockpitSTT", "Listening session is already active. Skipping redundant startListening().")
            return
        }

        ensureSpeechRecognizer()

        // Enable bilingual recognition fallback: prefer en-US for wake words while supporting vi-VN
        val activeLocale = if (appLanguage.value == AppLanguage.VIETNAMESE) "vi-VN" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLocale)
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("en-US", "vi-VN"))
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        try {
            Log.d("CockpitSTT", "Starting SpeechRecognizer listening session (Active Language: $activeLocale)...")
            speechRecognizer?.startListening(intent)
            isListeningSessionActive = true
        } catch (e: Exception) {
            Log.e("CockpitSTT", "Failed to start listening: ${e.message}")
            destroySpeechRecognizer()
            restartListeningDelayed(1500)
        }
    }

    private fun triggerVoiceInputManually() {
        Log.d("CockpitSTT", "Manual Mic Button Tapped")
        if (assistantState.value == AssistantState.SPEAKING) {
            tts?.stop()
            assistantState.value = AssistantState.IDLE
            startContinuousListening()
            return
        }

        assistantState.value = AssistantState.WAKE_DETECTED
        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
            "Đang lắng nghe trực tiếp. Hãy nói câu hỏi..."
        } else {
            "Listening directly. Please speak now..."
        }
        startAutoSleepTimer()
        destroySpeechRecognizer()
        startContinuousListening()
    }

    private fun simulateWakeWord() {
        Log.d("CockpitSTT", "Simulate Wake Word Tapped")
        assistantState.value = AssistantState.WAKE_DETECTED
        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
            "Đã nhận lệnh \"Hey Car\". Bạn cần trợ giúp gì?"
        } else {
            "Wake word \"Hey Car\" detected! How can I help?"
        }
        speakTtsAck()
        startAutoSleepTimer()
    }

    private fun processUserCommand(queryText: String) {
        val trimmed = queryText.trim()
        Log.d("CockpitSTT", "Processing User Command: '$trimmed'")

        if (isAmbiguousOrCallConfusion(trimmed)) {
            assistantState.value = AssistantState.WAKE_DETECTED
            val disambiguationPrompt = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                "Bạn muốn gọi điện thoại hay muốn hỏi trợ lý xe? Vui lòng nói lại câu hỏi."
            } else {
                "Did you want to make a phone call or ask the vehicle assistant? Please restate your question."
            }
            statusText.value = disambiguationPrompt
            speakDisambiguationPrompt(disambiguationPrompt)
            startAutoSleepTimer()
            return
        }

        performCopilotQuery(trimmed)
    }

    private fun speakDisambiguationPrompt(promptText: String) {
        destroySpeechRecognizer()
        tts?.speak(promptText, TextToSpeech.QUEUE_FLUSH, null, "disambiguation")
    }

    private fun performCopilotQuery(queryText: String) {
        destroySpeechRecognizer()
        cancelAutoSleepTimer()
        assistantState.value = AssistantState.PROCESSING
        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
            "Trợ lý AI đang tư vấn: \"$queryText\""
        } else {
            "AI Assistant analyzing: \"$queryText\""
        }
        copilotAnswer.value = ""
        citations.value = emptyList()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. First try Gemini AI for smart natural response
                val geminiAnswer = GeminiHelper.queryGemini(queryText)
                mainHandler.post {
                    copilotAnswer.value = geminiAnswer
                    citations.value = emptyList()
                    speakResponse(geminiAnswer)
                }
            } catch (geminiException: Exception) {
                Log.w("CockpitSTT", "Gemini AI Query skipped/failed (${geminiException.message}). Falling back to local RAG backend...")
                try {
                    // 2. Fallback to Local RAG Backend API
                    val response = CopilotClient.service.queryCopilot(QueryRequest(queryText))
                    mainHandler.post {
                        copilotAnswer.value = response.answer
                        citations.value = response.citations
                        speakResponse(response.answer)
                    }
                } catch (ragException: Exception) {
                    Log.w("CockpitSTT", "Copilot Backend Connection Failed: ${ragException.message}")
                    val fallbackMsg = if (appLanguage.value == AppLanguage.VIETNAMESE) {
                        "Xin lỗi, tôi chưa thể trả lời câu hỏi này. Bạn hãy kiểm tra kết nối Gemini API Key hoặc máy chủ RAG."
                    } else {
                        "Sorry, I cannot process this question right now. Please check your Gemini API key or backend connection."
                    }
                    mainHandler.post {
                        copilotAnswer.value = "Gemini/Backend Error: ${ragException.message}"
                        citations.value = emptyList()
                        speakResponse(fallbackMsg)
                    }
                }
            }
        }
    }

    private fun speakResponse(text: String) {
        destroySpeechRecognizer()
        assistantState.value = AssistantState.SPEAKING
        statusText.value = if (appLanguage.value == AppLanguage.VIETNAMESE) {
            "Trợ lý đang trả lời..."
        } else {
            "Assistant is responding..."
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "response")
    }

    private fun speakTtsAck() {
        destroySpeechRecognizer()
        val ackText = if (appLanguage.value == AppLanguage.VIETNAMESE) {
            "Tôi đang lắng nghe. Bạn cần giúp gì?"
        } else {
            "Yes, I am listening. How can I help?"
        }
        tts?.speak(ackText, TextToSpeech.QUEUE_FLUSH, null, "ack")
    }

    private fun restartListeningDelayed(delayMs: Long = 800L) {
        restartListeningRunnable?.let { mainHandler.removeCallbacks(it) }
        restartListeningRunnable = Runnable { startContinuousListening() }
        mainHandler.postDelayed(restartListeningRunnable!!, delayMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoSleepTimer()
        restartListeningRunnable?.let { mainHandler.removeCallbacks(it) }
        destroySpeechRecognizer()
        tts?.stop()
        tts?.shutdown()
        if (::carPropertyHelper.isInitialized) carPropertyHelper.shutdown()
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun CockpitUIPreview() {
    CockpitUI(
        assistantState = AssistantState.IDLE,
        statusText = "System Standby. Say \"Hey Car\" to activate.",
        copilotAnswer = "The recommended tire pressure for your vehicle is 32 PSI for all tires.",
        citations = listOf(
            CitationInfo("manual_01", "User Manual", "Maintenance", 124, "Check tire pressure monthly when tires are cold.")
        ),
        vehicleSpeed = 65.0f,
        isHvacOn = true,
        rmsLevel = 0.5f,
        appLanguage = AppLanguage.ENGLISH,
        displayTheme = DisplayTheme.LIGHT,
        showSettingsDialog = false,
        geminiApiKey = "",
        onHvacToggle = {},
        onManualSend = {},
        onMicTap = {},
        onOpenSettings = {},
        onCloseSettings = {},
        onLanguageChange = {},
        onThemeChange = {},
        onGeminiKeyChange = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitUI(
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
    geminiApiKey: String = "",
    onHvacToggle: () -> Unit,
    onManualSend: (String) -> Unit,
    onMicTap: () -> Unit,
    onWakeSimulate: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (DisplayTheme) -> Unit,
    onGeminiKeyChange: (String) -> Unit = {}
) {
    var queryInput by remember { mutableStateOf("") }

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val primaryBlue = when (displayTheme) {
        DisplayTheme.LIGHT -> Color(0xFF2563EB)
        DisplayTheme.DARK -> Color(0xFF3B82F6)
        DisplayTheme.CENTRAL -> Color(0xFF60A5FA)
    }

    val secondaryBlue = when (displayTheme) {
        DisplayTheme.LIGHT -> Color(0xFFDBEAFE)
        DisplayTheme.DARK -> Color(0xFF1E3A8A)
        DisplayTheme.CENTRAL -> Color(0xFF1E40AF)
    }

    val backgroundBg = when (displayTheme) {
        DisplayTheme.LIGHT -> Color(0xFFF8FAFC)
        DisplayTheme.DARK -> Color(0xFF0F172A)
        DisplayTheme.CENTRAL -> Color(0xFF0B1329)
    }

    val surfaceWhite = when (displayTheme) {
        DisplayTheme.LIGHT -> Color.White
        DisplayTheme.DARK -> Color(0xFF1E293B)
        DisplayTheme.CENTRAL -> Color(0xFF172554)
    }

    val textMain = when (displayTheme) {
        DisplayTheme.LIGHT -> Color(0xFF1E293B)
        DisplayTheme.DARK -> Color(0xFFF8FAFC)
        DisplayTheme.CENTRAL -> Color.White
    }

    val textSecondary = when (displayTheme) {
        DisplayTheme.LIGHT -> Color(0xFF64748B)
        DisplayTheme.DARK -> Color(0xFF94A3B8)
        DisplayTheme.CENTRAL -> Color(0xFF93C5FD)
    }

    val indicatorColor = when (assistantState) {
        AssistantState.IDLE -> primaryBlue
        AssistantState.WAKE_DETECTED -> Color(0xFF10B981)
        AssistantState.PROCESSING -> Color(0xFFF59E0B)
        AssistantState.SPEAKING -> Color(0xFF8B5CF6)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = backgroundBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = primaryBlue,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Wheelchair Copilot",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textMain
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed Indicator Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (vehicleSpeed > 80f) Color(0xFFFEE2E2) else surfaceWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = if (vehicleSpeed > 80f) Color(0xFFEF4444) else Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${"%.0f".format(vehicleSpeed)} km/h",
                                color = if (vehicleSpeed > 80f) Color(0xFFB91C1C) else textMain,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // HVAC Toggle
                    OutlinedButton(
                        onClick = onHvacToggle,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isHvacOn) secondaryBlue else surfaceWhite,
                            contentColor = primaryBlue
                        ),
                        border = BorderStroke(1.dp, if (isHvacOn) primaryBlue else Color(0xFFE2E8F0))
                    ) {
                        Icon(
                            imageVector = if (isHvacOn) Icons.Rounded.AcUnit else Icons.Rounded.Air,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isHvacOn) "AC ON" else "AC OFF", fontWeight = FontWeight.SemiBold)
                    }

                    // Settings Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .background(surfaceWhite, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = primaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Voice Status Dashboard Component
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surfaceWhite),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (assistantState == AssistantState.IDLE || assistantState == AssistantState.WAKE_DETECTED) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .scale(pulseScale)
                                        .background(indicatorColor.copy(alpha = 0.2f), CircleShape)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(indicatorColor, CircleShape)
                            )
                        }

                        Column {
                            Text(
                                text = when (assistantState) {
                                    AssistantState.IDLE -> if (appLanguage == AppLanguage.VIETNAMESE) "TRẠNG THÁI CHỜ" else "SYSTEM STANDBY"
                                    AssistantState.WAKE_DETECTED -> if (appLanguage == AppLanguage.VIETNAMESE) "ĐANG LẮNG NGHE..." else "LISTENING..."
                                    AssistantState.PROCESSING -> if (appLanguage == AppLanguage.VIETNAMESE) "ĐANG XỬ LÝ..." else "ANALYZING QUERY..."
                                    AssistantState.SPEAKING -> if (appLanguage == AppLanguage.VIETNAMESE) "TRỢ LÝ ĐANG PHÁT" else "ASSISTANT SPEAKING"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = statusText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMain
                            )
                        }
                    }

                    // Audio Waveform Visualizer
                    if (assistantState == AssistantState.IDLE || assistantState == AssistantState.WAKE_DETECTED) {
                        AudioWaveform(
                            rmsLevel = rmsLevel,
                            color = indicatorColor,
                            modifier = Modifier
                                .width(120.dp)
                                .height(40.dp)
                                .padding(horizontal = 12.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onWakeSimulate,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, primaryBlue),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = primaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hey Car", color = primaryBlue, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onMicTap,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (assistantState == AssistantState.SPEAKING) Color(0xFFEF4444) else primaryBlue
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (assistantState == AssistantState.SPEAKING) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (assistantState == AssistantState.SPEAKING) "Stop" else "Talk",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Speed Warning Alert Banner (Conditional)
            if (vehicleSpeed > 80f) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.VIETNAMESE) {
                                "CẢNH BÁO AN TOÀN: Tốc độ xe vượt quá 80km/h. Vui lòng tập trung lái xe."
                            } else {
                                "SAFETY ALERT: Speed exceeds 80km/h. Please focus on the road."
                            },
                            color = Color(0xFF991B1B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Main Answer & Citation Card Area
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surfaceWhite),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(modifier = Modifier.padding(20.dp)) {
                    if (assistantState == AssistantState.PROCESSING) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = primaryBlue, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.VIETNAMESE) "Đang truy vấn tài liệu kỹ thuật..." else "Searching vehicle manuals...",
                                color = textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                if (copilotAnswer.isEmpty()) {
                                    EmptyStateView(textSecondary, appLanguage)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.SmartToy,
                                            contentDescription = null,
                                            tint = primaryBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (appLanguage == AppLanguage.VIETNAMESE) "KẾT QUẢ TỪ COPILOT" else "COPILOT RESPONSE",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryBlue,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = copilotAnswer,
                                        fontSize = 19.sp,
                                        color = textMain,
                                        lineHeight = 28.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }

                            if (citations.isNotEmpty()) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (appLanguage == AppLanguage.VIETNAMESE) "NGUỒN TRÍCH DẪN TÀI LIỆU" else "SOURCES & TRACEABILITY",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                items(citations) { citation ->
                                    CitationCard(citation, primaryBlue, textMain)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Input Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surfaceWhite),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        placeholder = {
                            Text(
                                if (appLanguage == AppLanguage.VIETNAMESE) "Nhập câu hỏi thủ công về xe..." else "Ask a manual question..."
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            onManualSend(queryInput)
                            queryInput = ""
                        },
                        enabled = queryInput.isNotBlank() && assistantState != AssistantState.PROCESSING,
                        modifier = Modifier
                            .background(
                                if (queryInput.isNotBlank()) primaryBlue else Color(0xFFE2E8F0),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Settings Modal Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = onCloseSettings,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = primaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.VIETNAMESE) "Cài Đặt Hệ Thống" else "System Settings",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // Language Selection
                    Column {
                        Text(
                            text = if (appLanguage == AppLanguage.VIETNAMESE) "Ngôn ngữ nhận diện & phát âm" else "Speech Language",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = appLanguage == AppLanguage.VIETNAMESE,
                                onClick = { onLanguageChange(AppLanguage.VIETNAMESE) },
                                label = { Text("Tiếng Việt (vi-VN)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            FilterChip(
                                selected = appLanguage == AppLanguage.ENGLISH,
                                onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                                label = { Text("English (en-US)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Gemini API Key Config
                    var keyInput by remember { mutableStateOf(geminiApiKey) }
                    Column {
                        Text(
                            text = if (appLanguage == AppLanguage.VIETNAMESE) "Google Gemini AI Key (aistudio.google.com)" else "Google Gemini API Key",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = {
                                keyInput = it
                                onGeminiKeyChange(it)
                            },
                            placeholder = { Text("Paste AI Studio API Key here...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Theme Selection
                    Column {
                        Text(
                            text = if (appLanguage == AppLanguage.VIETNAMESE) "Giao diện hiển thị" else "Display Theme",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = displayTheme == DisplayTheme.LIGHT,
                                onClick = { onThemeChange(DisplayTheme.LIGHT) },
                                label = { Text("Light") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.LightMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            FilterChip(
                                selected = displayTheme == DisplayTheme.DARK,
                                onClick = { onThemeChange(DisplayTheme.DARK) },
                                label = { Text("Dark") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DarkMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            FilterChip(
                                selected = displayTheme == DisplayTheme.CENTRAL,
                                onClick = { onThemeChange(DisplayTheme.CENTRAL) },
                                label = { Text("Central") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Dashboard,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onCloseSettings,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (appLanguage == AppLanguage.VIETNAMESE) "Đóng" else "Close")
                }
            }
        )
    }
}

@Composable
fun EmptyStateView(color: Color, appLanguage: AppLanguage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = color.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (appLanguage == AppLanguage.VIETNAMESE) {
                "Trợ lý lái xe AI đang sẵn sàng.\nNói \"Hey Car\" hoặc đặt câu hỏi về xe."
            } else {
                "Your AI driving assistant is ready.\nSay \"Hey Car\" or ask about your vehicle."
            },
            textAlign = TextAlign.Center,
            color = color,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                if (appLanguage == AppLanguage.VIETNAMESE) "Làm sao để kiểm tra áp suất lốp?" else "How do I check tire pressure?"
            )
            SuggestionChip(
                if (appLanguage == AppLanguage.VIETNAMESE) "Khi nào xe cần bảo dưỡng lần tới?" else "When is my next vehicle service?"
            )
            SuggestionChip(
                if (appLanguage == AppLanguage.VIETNAMESE) "Cách điều chỉnh nhiệt độ điều hòa?" else "How to adjust cabin temperature?"
            )
        }
    }
}

@Composable
fun SuggestionChip(text: String) {
    Surface(
        color = Color(0xFFF1F5F9),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 13.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CitationCard(citation: CitationInfo, primary: Color, textMain: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${citation.document_name} • Page ${citation.page}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"${citation.matched_text}\"",
                fontSize = 13.sp,
                color = textMain,
                lineHeight = 20.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun AudioWaveform(
    rmsLevel: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 7
    val animatedLevels = (0 until barCount).map { i ->
        val offset = i * 0.15f
        val rawLevel = ((rmsLevel + offset) % 1.0f)
        val barHeight = if (rmsLevel > 0.01f) {
            0.2f + rawLevel * 0.8f
        } else {
            0.15f + (kotlin.math.sin(System.nanoTime() / 500_000_000.0 + i).toFloat() * 0.1f)
        }
        animateFloatAsState(
            targetValue = barHeight.coerceIn(0.1f, 1f),
            animationSpec = tween(durationMillis = 120, easing = EaseInOutSine),
            label = "bar$i"
        ).value
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 2f)
        val totalWidth = barCount * barWidth + (barCount - 1) * barWidth
        val startX = (size.width - totalWidth) / 2f

        animatedLevels.forEachIndexed { i, level ->
            val barHeight = size.height * level
            val x = startX + i * (barWidth * 2)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.5f + level * 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}
