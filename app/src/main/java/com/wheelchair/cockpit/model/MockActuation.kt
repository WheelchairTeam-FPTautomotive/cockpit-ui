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
    // --- START MODIFICATION ---
    // Wave1: TRUNK / WINDOW / HVAC_TEMP_SET parity with gateway CommandID
    val id = commandId?.trim()?.uppercase().orEmpty()
    if (id.isEmpty() || id == "GENERIC_CONTROL") return null
    return when {
        id.contains("TRUNK") -> MockActuationEvent(
            kind = MockActuationKind.DOOR,
            titleVi = "Cốp xe",
            titleEn = "Trunk",
            subtitleVi = "Mô phỏng điều khiển cốp thành công",
            subtitleEn = "Mock trunk actuation succeeded"
        )
        id.contains("WINDOW") -> MockActuationEvent(
            kind = MockActuationKind.DOOR,
            titleVi = "Cửa sổ",
            titleEn = "Windows",
            subtitleVi = "Mô phỏng điều khiển kính thành công",
            subtitleEn = "Mock window actuation succeeded"
        )
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
            subtitleVi = if (id.contains("TEMP")) {
                "Mô phỏng chỉnh nhiệt độ thành công"
            } else {
                "Mô phỏng HVAC thành công"
            },
            subtitleEn = if (id.contains("TEMP")) {
                "Mock temperature set succeeded"
            } else {
                "Mock HVAC actuation succeeded"
            }
        )
        id.contains("MUSIC") || id.contains("MEDIA") || id.contains("VOLUME") -> MockActuationEvent(
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
    // --- END MODIFICATION ---
}

fun mockActuationForRagSuccess(vietnamese: Boolean): MockActuationEvent =
    MockActuationEvent(
        kind = MockActuationKind.RAG,
        titleVi = "Tài liệu OEM",
        titleEn = "OEM manual",
        subtitleVi = "Trả lời có trích dẫn — phản hồi thành công",
        subtitleEn = "Cited answer ready — RAG success"
    )
