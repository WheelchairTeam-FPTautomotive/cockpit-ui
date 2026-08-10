package com.wheelchair.cockpit.data

import com.wheelchair.cockpit.api.CopilotClient
import com.wheelchair.cockpit.api.QueryRequest
import com.wheelchair.cockpit.api.QueryResponse
import com.wheelchair.cockpit.dev.DevSettingsStore

// --- START MODIFICATION ---
class RemoteCopilotDataSource : CopilotDataSource {
    override suspend fun query(
        query: String,
        language: String,
        sessionId: String?,
        sessionTtlMin: Int?
    ): QueryResponse {
        return CopilotClient.service.queryCopilot(
            QueryRequest(
                query = query,
                language = language,
                session_id = sessionId,
                session_ttl_min = sessionTtlMin
            )
        )
    }

    override suspend fun checkHealth(): HealthResult {
        val started = System.currentTimeMillis()
        return try {
            val body = CopilotClient.service.checkHealth()
            val latency = System.currentTimeMillis() - started
            val status = body["status"] ?: body.toString()
            HealthResult(ok = true, message = status, latencyMs = latency)
        } catch (e: Exception) {
            HealthResult(
                ok = false,
                message = e.localizedMessage ?: e.javaClass.simpleName,
                latencyMs = System.currentTimeMillis() - started
            )
        }
    }
}

class CopilotRepository(
    private val store: DevSettingsStore,
    private val remote: CopilotDataSource = RemoteCopilotDataSource(),
    private val mock: CopilotDataSource = MockCopilotDataSource()
) {
    private fun activeSource(): CopilotDataSource =
        if (store.current().effectiveMockRag) mock else remote

    suspend fun sendQuery(
        query: String,
        language: String = "vi",
        sessionId: String? = null,
        sessionTtlMin: Int? = 5
    ): QueryResponse =
        activeSource().query(query, language, sessionId, sessionTtlMin)

    suspend fun checkHealth(): HealthResult = activeSource().checkHealth()
}
// --- END MODIFICATION ---
