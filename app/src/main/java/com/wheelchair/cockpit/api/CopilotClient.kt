package com.wheelchair.cockpit.api

import com.wheelchair.cockpit.dev.DevSettings
import com.wheelchair.cockpit.dev.DevSettingsStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

interface CopilotService {
    @GET("api/v1/health")
    suspend fun checkHealth(): Map<String, String>

    @POST("api/v1/copilot/query")
    suspend fun queryCopilot(@Body request: QueryRequest): QueryResponse

    @Multipart
    @POST("api/v1/copilot/voice-query")
    suspend fun queryCopilotVoice(
        @Part file: MultipartBody.Part,
        @Part("language") language: RequestBody
    ): VoiceQueryResponse

    @Multipart
    @POST("api/v1/copilot/stt")
    suspend fun sttOnly(
        @Part file: MultipartBody.Part
    ): SttResponse

    @POST("api/v1/copilot/tts")
    suspend fun generateTts(@Body request: TtsRequest): TtsResponse
}

data class SttResponse(
    val transcript: String,
    val latency_ms: Int
)

data class QueryRequest(
    val query: String,
    val language: String = "vi"
)

data class TtsRequest(
    val text: String,
    val language: String = "vi"
)

data class TtsResponse(
    val audio_base64: String?,
    val latency_ms: Int
)

data class CitationInfo(
    val document_id: String,
    val document_name: String,
    val section: String,
    val page: Int,
    val matched_text: String
)

data class QueryResponse(
    val query: String,
    val answer: String,
    val audio_base64: String? = null,
    val citations: List<CitationInfo>,
    val status: String
)

data class LatencyMetrics(
    val stt_ms: Int,
    val core_ai_ms: Int,
    val tts_ms: Int,
    val total_ms: Int
)

data class VoiceQueryResponse(
    val transcript: String,
    val answer: String,
    val audio_base64: String?,
    val citations: List<CitationInfo>,
    val latency: LatencyMetrics
)

// --- START MODIFICATION ---
// Singleton Retrofit client. Host overrides go through DynamicHostInterceptor.
object CopilotClient {
    const val BASE_URL = DevSettings.DEFAULT_BASE_URL

    @Volatile
    private var initialized = false

    private lateinit var loggingInterceptor: HttpLoggingInterceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var retrofitService: CopilotService

    fun init(store: DevSettingsStore) {
        if (initialized) {
            applyLogLevel(store.current())
            return
        }
        synchronized(this) {
            if (initialized) {
                applyLogLevel(store.current())
                return
            }
            loggingInterceptor = HttpLoggingInterceptor().apply {
                level = store.current().effectiveHttpLogLevel.toOkHttpLevel()
            }
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(DynamicHostInterceptor(store))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofitService = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CopilotService::class.java)

            initialized = true
        }
    }

    fun applyLogLevel(settings: DevSettings) {
        if (!::loggingInterceptor.isInitialized) return
        loggingInterceptor.level = settings.effectiveHttpLogLevel.toOkHttpLevel()
    }

    val service: CopilotService
        get() {
            check(initialized) { "CopilotClient.init(store) must be called before use" }
            return retrofitService
        }
}
// --- END MODIFICATION ---
