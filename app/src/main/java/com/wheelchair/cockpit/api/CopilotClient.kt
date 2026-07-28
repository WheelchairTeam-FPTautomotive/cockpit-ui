package com.wheelchair.cockpit.api

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

data class QueryRequest(val query: String)

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

object CopilotClient {
    // Port 8000 mapped from local RAG backend. 
    // In Android Emulator, 10.0.2.2 points to host's localhost loopback.
    const val BASE_URL = "http://10.0.2.2:8000/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val service: CopilotService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CopilotService::class.java)
    }
}

