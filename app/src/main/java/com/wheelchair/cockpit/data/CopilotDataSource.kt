package com.wheelchair.cockpit.data

import com.wheelchair.cockpit.api.CitationInfo
import com.wheelchair.cockpit.api.QueryResponse

// --- START MODIFICATION ---
interface CopilotDataSource {
    suspend fun query(query: String, language: String = "vi"): QueryResponse
    suspend fun checkHealth(): HealthResult
}

data class HealthResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long
)

class MockCopilotDataSource : CopilotDataSource {
    override suspend fun query(query: String, language: String): QueryResponse {
        val answer = if (language == "en") {
            "Based on the technical manual (MOCK):\n" +
                "1. HVAC is controlled from the center console and voice when Parked.\n" +
                "2. Avoid complex settings above 80 km/h."
        } else {
            "Dựa trên tài liệu hướng dẫn kỹ thuật (MOCK):\n" +
                "1. [2009 - gaia.pdf - GOG SRS (Trang 11)]: Hệ thống HVAC được điều khiển qua bảng điều khiển trung tâm " +
                "và có thể kích hoạt bằng lệnh thoại khi xe ở chế độ Park.\n" +
                "2. [Demo Manual - Safety (Trang 3)]: Không thao tác cài đặt phức tạp khi tốc độ vượt 80 km/h."
        }
        return QueryResponse(
            query = query,
            answer = answer,
            citations = listOf(
                CitationInfo(
                    document_id = "mock-doc-hvac-001",
                    document_name = "2009 - gaia.pdf",
                    section = "GOG SRS",
                    page = 11,
                    matched_text = "HVAC được điều khiển qua bảng điều khiển trung tâm và lệnh thoại."
                ),
                CitationInfo(
                    document_id = "mock-doc-safety-001",
                    document_name = "Demo Manual - Safety.pdf",
                    section = "Driving UX",
                    page = 3,
                    matched_text = "Hạn chế thao tác phức tạp khi tốc độ > 80 km/h."
                )
            ),
            status = "success"
        )
    }

    override suspend fun checkHealth(): HealthResult {
        return HealthResult(
            ok = true,
            message = "mock-ready",
            latencyMs = 0L
        )
    }
}
// --- END MODIFICATION ---
