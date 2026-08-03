package com.wheelchair.cockpit.voice

import com.wheelchair.cockpit.model.AppLanguage

// --- START MODIFICATION ---
enum class MicDiag {
    IDLE,
    LISTENING,
    OK,
    PERMISSION_DENIED,
    STT_UNAVAILABLE
}

fun MicDiag.label(appLanguage: AppLanguage): String {
    val vi = appLanguage == AppLanguage.VIETNAMESE
    return when (this) {
        MicDiag.IDLE -> if (vi) "Mic: chờ" else "Mic: idle"
        MicDiag.LISTENING -> if (vi) "Mic: đang nghe" else "Mic: listening"
        MicDiag.OK -> if (vi) "Mic: OK" else "Mic: OK"
        MicDiag.PERMISSION_DENIED -> if (vi) "Mic: bị từ chối" else "Mic: denied"
        MicDiag.STT_UNAVAILABLE -> if (vi) "STT: không khả dụng" else "STT: unavailable"
    }
}
// --- END MODIFICATION ---
