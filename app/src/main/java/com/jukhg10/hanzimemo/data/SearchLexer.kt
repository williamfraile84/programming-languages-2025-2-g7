package com.jukhg10.hanzimemo.data

// Lexer para mini-lenguaje de búsqueda
// Ejemplo: "p:ni hao d:water h:你 好" => tokens con tipos y valores

enum class TokenType { PINYIN_SEARCH, DEFINITION_SEARCH, HANZI_SEARCH, GENERAL_SEARCH }

data class SearchToken(val type: TokenType, val value: String)

object SearchLexer {
    // ✅ Soportar prefijos cortos (p:, d:, h:) y prefijos completos (pinyin:, definition:, hanzi:)
    private val prefixRegex = Regex("\\b(pinyin|p|definition|d|hanzi|h):", RegexOption.IGNORE_CASE)

    fun lex(input: String): List<SearchToken> {
        val text = input.trim()
        if (text.isEmpty()) return emptyList()

        val matches = prefixRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            // No hay prefijos: todo es GENERAL_SEARCH
            return listOf(SearchToken(TokenType.GENERAL_SEARCH, normalizeSpaces(text)))
        }

        val tokens = mutableListOf<SearchToken>()

        // Si hay texto antes del primer prefijo, es GENERAL
        val first = matches.first()
        if (first.range.first > 0) {
            val before = text.substring(0, first.range.first).trim()
            if (before.isNotEmpty()) tokens.add(SearchToken(TokenType.GENERAL_SEARCH, normalizeSpaces(before)))
        }

        for ((index, m) in matches.withIndex()) {
            val prefixText = m.groupValues[1].lowercase()
            val valueStart = m.range.last + 1
            val valueEnd = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            val rawValue = text.substring(valueStart, valueEnd).trim()
            if (rawValue.isEmpty()) continue // token vacío -> ignorar

            // ✅ Determinar tipo de token según prefijo (corto o largo)
            val tokenType = when (prefixText) {
                "p", "pinyin" -> TokenType.PINYIN_SEARCH
                "d", "definition" -> TokenType.DEFINITION_SEARCH
                "h", "hanzi" -> TokenType.HANZI_SEARCH
                else -> TokenType.GENERAL_SEARCH
            }

            tokens.add(SearchToken(tokenType, normalizeSpaces(rawValue)))
        }

        return tokens
    }

    private fun normalizeSpaces(s: String) = s.replace(Regex("\\s+"), " ").trim()
}
