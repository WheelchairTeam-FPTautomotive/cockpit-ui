package com.wheelchair.cockpit.model

/**
 * Transient mock actuation event used to trigger one-shot car-control
 * confirmation animations without changing backend contracts.
 */
enum class MockActuationKind {
    DOOR,
    HVAC,
    MUSIC,
    RAG
}

data class MockActuationEvent(
    val kind: MockActuationKind,
    val titleVi: String,
    val titleEn: String,
    val subtitleVi: String,
    val subtitleEn: String,
    val token: Long = System.currentTimeMillis()
)

fun mockActuationForCommandId(commandId: String?): MockActuationEvent? {
    val id = commandId?.trim()?.uppercase().orEmpty()
    if (id.isEmpty() || id == "GENERIC_CONTROL") return null
    return when {
        id.contains("DOOR") -> MockActuationEvent(
            kind = MockActuationKind.DOOR,
            titleVi = "Cửa xe",
            titleEn = "Doors",
            subtitleVi = "Mô phỏng điều khiển cửa thành công",
            subtitleEn = "Mock door actuation succeeded"
        )
        id.contains("HVAC") || id.contains("AC") || id.contains("CLIMATE") -> MockActuationEvent(
            kind = MockActuationKind.HVAC,
            titleVi = "Điều hòa",
            titleEn = "Climate",
            subtitleVi = "Mô phỏng HVAC thành công",
            subtitleEn = "Mock HVAC actuation succeeded"
        )
        id.contains("MUSIC") || id.contains("MEDIA") -> MockActuationEvent(
            kind = MockActuationKind.MUSIC,
            titleVi = "Âm nhạc",
            titleEn = "Music",
            subtitleVi = "Mô phỏng phát nhạc",
            subtitleEn = "Mock music playback started"
        )
        else -> MockActuationEvent(
            kind = MockActuationKind.DOOR,
            titleVi = "Điều khiển xe",
            titleEn = "Vehicle control",
            subtitleVi = "Mô phỏng lệnh $id",
            subtitleEn = "Mock command $id"
        )
    }
}

fun mockActuationForRagSuccess(vietnamese: Boolean): MockActuationEvent =
    MockActuationEvent(
        kind = MockActuationKind.RAG,
        titleVi = "Tài liệu OEM",
        titleEn = "OEM manual",
        subtitleVi = "Trả lời có trích dẫn — phản hồi thành công",
        subtitleEn = "Cited answer ready — RAG success"
    )
