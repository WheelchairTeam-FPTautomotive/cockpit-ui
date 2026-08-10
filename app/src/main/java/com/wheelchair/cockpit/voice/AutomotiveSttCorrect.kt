package com.wheelchair.cockpit.voice

/**
 * Deterministic automotive STT typo / phonetic repair (mirrors
 * backend-orchestrator `automotive_stt_correct.py` seed table).
 *
 * Apply at processUserSpeech ingress for local VHAL + chat HUD.
 */
object AutomotiveSttCorrect {

    // --- START MODIFICATION ---
    private val canonicalAcronyms = setOf(
        "epb", "hvac", "adas", "aeb", "abs", "tpms", "isofix", "latch", "obd",
        "esc", "ldw", "lka", "bsm", "scc", "fca", "avn", "ics", "mil", "mist",
        "eco", "ev", "vdc", "rcta", "bcw", "svm"
    )

    /** Longer keys first (mirrors Python sorted by -len). */
    private val explicitMap: List<Pair<String, String>> = listOf(
        "electronic parking brake" to "epb",
        "air conditioner" to "hvac",
        "aitch vac" to "hvac",
        "iso fix" to "isofix",
        "tee pms" to "tpms",
        "tp ms" to "tpms",
        "t p m s" to "tpms",
        "a das" to "adas",
        "a-das" to "adas",
        "a e b" to "aeb",
        "a b s" to "abs",
        "e p b" to "epb",
        "h vac" to "hvac",
        "o b d" to "obd",
        "obd2" to "obd",
        "obdii" to "obd",
        "hvec" to "hvac",
        "hvacx" to "hvac",
        "epp" to "epb",
        "ebp" to "epb",
        "adass" to "adas",
    ).sortedByDescending { it.first.length }

    private val commonWordStop = setOf(
        "app", "map", "can", "car", "bus", "has", "was", "his", "her", "the",
        "and", "for", "are", "you", "all", "any", "how", "who", "why", "what",
        "when", "this", "that", "with", "from", "have", "been", "will", "just",
        "like", "make", "take", "help", "open", "close", "turn", "play", "stop",
        "next", "back", "door", "lock", "temp", "heat", "cool", "air", "fan",
        "off", "on",
        "toi", "ban", "cua", "cho", "voi", "nay", "kia", "sao", "the", "nao",
        "giup", "lam", "bat", "tat", "mo", "dong", "van", "len", "xuong",
        "nhac", "nhiet", "do", "phanh", "guong", "dieu", "hoa", "may", "lanh"
    )

    private val spacedLetters = Regex(
        """(?<![a-z0-9])(?:[a-z](?:\s+[a-z]){1,5})(?![a-z0-9])""",
        RegexOption.IGNORE_CASE
    )
    private val tokenRegex = Regex("""[a-z0-9]+(?:'[a-z]+)?""", RegexOption.IGNORE_CASE)

    data class Result(val text: String, val fixes: List<Pair<String, String>>)

    fun correct(text: String): Result {
        if (text.isBlank()) return Result(text, emptyList())

        val fixes = mutableListOf<Pair<String, String>>()
        var out = text.trim()

        out = spacedLetters.replace(out) { match ->
            val raw = match.value
            val collapsed = raw.replace(Regex("""\s+"""), "").lowercase()
            if (" " in raw && collapsed.isNotEmpty()) {
                fixes += raw to collapsed
            }
            collapsed
        }

        for ((src, dst) in explicitMap) {
            if (src == dst) continue
            val pattern = Regex("""(?<![a-z0-9])${Regex.escape(src)}(?![a-z0-9])""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(out)) {
                out = pattern.replace(out, dst)
                fixes += src to dst
            }
        }

        out = tokenRegex.replace(out) { match ->
            val token = match.value
            val low = token.lowercase()
            if (
                low.all { it.isLetter() } &&
                low.length in 3..5 &&
                low !in canonicalAcronyms &&
                low !in commonWordStop
            ) {
                val hits = canonicalAcronyms.filter { levenshtein(low, it) == 1 }
                if (hits.size == 1) {
                    fixes += low to hits[0]
                    return@replace hits[0]
                }
            }
            token
        }

        return if (fixes.isEmpty()) Result(text, emptyList()) else Result(out, fixes)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
    // --- END MODIFICATION ---
}
