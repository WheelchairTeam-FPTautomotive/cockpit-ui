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
import java.util.concurrent.TimeUnit

interface CopilotService {
    @GET("api/v1/health")
    suspend fun checkHealth(): Map<String, String>

    @POST("api/v1/copilot/query")
    suspend fun queryCopilot(@Body request: QueryRequest): QueryResponse
}

data class QueryRequest(
    val query: String,
    val language: String = "vi"
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
    val citations: List<CitationInfo>,
    val status: String
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
