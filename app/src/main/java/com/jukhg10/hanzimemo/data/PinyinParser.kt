package com.jukhg10.hanzimemo.data

// Parser descendente simple (implementación pragmática) para validar sílabas de pinyin
// Gramática simplificada: Sílaba -> [Inicial] Final Tono

data class PinyinSyllable(val initial: String?, val `final`: String, val tone: Int)

object PinyinParser {
    // Lista de iniciales: poner las de dos letras primero para coincidencia "greedy"
    private val initials = listOf(
        "zh", "ch", "sh",
        "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x", "r", "y", "w"
    )

    // Conjunto de finales comunes (no exhaustivo, pero suficiente para validar la mayoría de sílabas)
    private val finals = setOf(
        "a","o","e","ai","ei","ao","ou","an","en","ang","eng","ong",
        "i","ia","ie","iao","iu","ian","in","iang","ing","iong",
        "u","ua","uo","uai","ui","uan","un","uang","ong",
        "ü","üe","er","uo"
    )

    private val toneDigits = setOf('1','2','3','4','5')

    // Normaliza entrada: minúsculas, convierte v o u: a ü
    private fun normalize(s: String): String {
        return s.lowercase()
            .replace("u:", "ü")
            .replace("v", "ü")
            .trim()
    }

    fun parse(syllable: String): PinyinSyllable? {
        val t = normalize(syllable)
        if (t.isEmpty()) return null
        val last = t.last()
        if (!toneDigits.contains(last)) return null
        val tone = (last - '0')
        val body = t.dropLast(1)
        if (body.isEmpty()) return null

        // intentar extraer inicial (greedy)
        var foundInitial: String? = null
        var finalPart = body
        for (init in initials) {
            if (body.startsWith(init)) {
                foundInitial = init
                finalPart = body.removePrefix(init)
                break
            }
        }

        if (finalPart.isEmpty()) return null
        if (!finals.contains(finalPart)) return null

        return PinyinSyllable(foundInitial, finalPart, tone)
    }

    fun isValidSyllable(syllable: String): Boolean = parse(syllable) != null

    // Valida un token de tipo PINYIN_SEARCH: varios sílabas separadas por espacios
    fun validateToken(value: String): Boolean {
        val parts = value.split(Regex("\\s+"))
        if (parts.isEmpty()) return false
        for (p in parts) {
            if (p.isBlank()) continue
            if (!isValidSyllable(p)) return false
        }
        return true
    }
}
