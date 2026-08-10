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

    private val tokenRegex = Regex("""[a-z0-9]+(?:'[a-z]+)?""", RegexOption.IGNORE_CASE)


    data class Result(val text: String, val fixes: List<Pair<String, String>>)

    fun correct(text: String): Result {
        if (text.isBlank()) return Result(text, emptyList())

        val fixes = mutableListOf<Pair<String, String>>()
        var         out = text.trim()

        // Collapse standalone single-letter runs only ("e p b" → "epb"),
        // never join across normal words ("bên lái").
        run {
            val parts = Regex("""(\s+)""").split(out)
            // Regex.split with capturing groups isn't available the same way —
            // manual scan:
            val tokens = mutableListOf<String>()
            var idx = 0
            val s = out
            while (idx < s.length) {
                if (s[idx].isWhitespace()) {
                    val start = idx
                    while (idx < s.length && s[idx].isWhitespace()) idx++
                    tokens += s.substring(start, idx)
                } else {
                    val start = idx
                    while (idx < s.length && !s[idx].isWhitespace()) idx++
                    tokens += s.substring(start, idx)
                }
            }
            val rebuilt = StringBuilder()
            var i = 0
            while (i < tokens.size) {
                val tok = tokens[i]
                if (tok.length == 1 && tok[0].isLetter()) {
                    val letters = mutableListOf(tok)
                    var j = i + 1
                    while (j + 1 < tokens.size && tokens[j].isBlank() &&
                        tokens[j + 1].length == 1 && tokens[j + 1][0].isLetter()
                    ) {
                        letters += tokens[j + 1]
                        j += 2
                    }
                    if (letters.size >= 2) {
                        val collapsed = letters.joinToString("") { it.lowercase() }
                        val raw = tokens.subList(i, j).joinToString("")
                        fixes += raw to collapsed
                        rebuilt.append(collapsed)
                        i = j
                        continue
                    }
                }
                rebuilt.append(tok)
                i++
            }
            out = rebuilt.toString()
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
