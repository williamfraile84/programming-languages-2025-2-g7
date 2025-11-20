package com.jukhg10.hanzimemo.data

/**
 * Lexer basado en Autómata Finito Determinista (AFD)
 * 
 * Este lexer implementa un AFD que reconoce patrones de búsqueda lingüística.
 * 
 * Estados del AFD:
 * - INITIAL: Estado inicial
 * - PREFIX: Después de leer un prefijo (p, d, h)
 * - COLON: Después de leer dos puntos
 * - VALUE: Leyendo el valor del token
 * - ACCEPT: Estado de aceptación
 * 
 * Transiciones:
 * INITIAL → (prefixRegex) → PREFIX
 * PREFIX → (:) → COLON
 * COLON → (valueStart) → VALUE
 * VALUE → (valueChar*) → ACCEPT
 */

// Token types with enhanced categories
enum class AdvancedTokenType {
    PINYIN_SEARCH,
    DEFINITION_SEARCH,
    HANZI_SEARCH,
    RADICAL_FILTER,
    STROKE_RANGE,
    HSK_RANGE,
    FREQUENCY_RANGE,
    TRADITIONAL_FILTER,
    GENERAL_SEARCH,
    LOGICAL_AND,
    LOGICAL_OR,
    LOGICAL_NOT,
    LPAREN,
    RPAREN,
    INVALID
}

data class AdvancedSearchToken(
    val type: AdvancedTokenType,
    val value: String,
    val position: Int,
    val length: Int
)

/**
 * Lexer avanzado con soporte para múltiples tipos de filtros
 * Implementa un AFD con Estados y Transiciones explícitas
 */
object AdvancedSearchLexer {
    
    // Expresiones regulares avanzadas y robustas
    private val pinyinRegex = Regex("""p:([a-zü]+[1-5]?(?:\s+[a-zü]+[1-5]?)*)""", RegexOption.IGNORE_CASE)
    private val definitionRegex = Regex("""d:([a-zA-Z0-9\s_-]+?)(?=\s+[a-z]:|$)""", RegexOption.IGNORE_CASE)
    private val hanziRegex = Regex("""h:([\u4E00-\u9FFF]+)""")
    private val radicalRegex = Regex("""radical:([\u4E00-\u9FFF])""")
    private val traditionalRegex = Regex("""traditional:([\u4E00-\u9FFF]+)""")
    private val strokeRangeRegex = Regex("""strokes:(\d+)(?:\.\.(\d+))?""")
    private val hskRangeRegex = Regex("""hsk:(\d+)(?:\.\.(\d+))?""")
    private val frequencyRegex = Regex("""frequency:(\d+)(?:\.\.(\d+))?""")
    private val logicalAndRegex = Regex("""(?:AND|and|&&|\+)""")
    private val logicalOrRegex = Regex("""(?:OR|or|\|\|)""")
    private val logicalNotRegex = Regex("""(?:NOT|not|!)""")
    
    /**
     * Función principal: tokeniza una entrada usando AFD
     * 
     * Algoritmo:
     * 1. Inicio en estado INITIAL
     * 2. Lee caracteres y aplica transiciones
     * 3. Cuando encuentra patrón, crea token y cambia estado
     * 4. Repite hasta fin de entrada
     */
    fun lex(input: String): List<AdvancedSearchToken> {
        val tokens = mutableListOf<AdvancedSearchToken>()
        var pos = 0
        val text = input.trim()
        
        if (text.isEmpty()) return emptyList()
        
        // Estado actual del AFD
        var state = "INITIAL"
        
        while (pos < text.length) {
            // Saltar espacios en estado INITIAL
            if (state == "INITIAL" && text[pos].isWhitespace()) {
                pos++
                continue
            }
            
            var matched = false
            val remaining = text.substring(pos)
            
            // Aplicar transiciones según el estado actual
            when (state) {
                "INITIAL" -> {
                    // Transiciones desde estado inicial
                    when {
                        remaining.startsWith("(") -> {
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.LPAREN, "(", pos, 1))
                            pos++
                            matched = true
                        }
                        remaining.startsWith(")") -> {
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.RPAREN, ")", pos, 1))
                            pos++
                            matched = true
                        }
                        logicalAndRegex.containsMatchIn(remaining) -> {
                            val m = logicalAndRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.LOGICAL_AND, m.value, pos, m.value.length))
                            pos += m.value.length
                            matched = true
                        }
                        logicalOrRegex.containsMatchIn(remaining) -> {
                            val m = logicalOrRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.LOGICAL_OR, m.value, pos, m.value.length))
                            pos += m.value.length
                            matched = true
                        }
                        logicalNotRegex.containsMatchIn(remaining) -> {
                            val m = logicalNotRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.LOGICAL_NOT, m.value, pos, m.value.length))
                            pos += m.value.length
                            matched = true
                        }
                        pinyinRegex.containsMatchIn(remaining) -> {
                            val m = pinyinRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.PINYIN_SEARCH, m.groupValues[1], pos + 2, m.groupValues[1].length))
                            pos += m.value.length
                            matched = true
                        }
                        definitionRegex.containsMatchIn(remaining) -> {
                            val m = definitionRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.DEFINITION_SEARCH, m.groupValues[1], pos + 2, m.groupValues[1].length))
                            pos += m.value.length
                            matched = true
                        }
                        hanziRegex.containsMatchIn(remaining) -> {
                            val m = hanziRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.HANZI_SEARCH, m.groupValues[1], pos + 2, m.groupValues[1].length))
                            pos += m.value.length
                            matched = true
                        }
                        radicalRegex.containsMatchIn(remaining) -> {
                            val m = radicalRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.RADICAL_FILTER, m.groupValues[1], pos + 8, 1))
                            pos += m.value.length
                            matched = true
                        }
                        traditionalRegex.containsMatchIn(remaining) -> {
                            val m = traditionalRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.TRADITIONAL_FILTER, m.groupValues[1], pos + 12, m.groupValues[1].length))
                            pos += m.value.length
                            matched = true
                        }
                        strokeRangeRegex.containsMatchIn(remaining) -> {
                            val m = strokeRangeRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.STROKE_RANGE, m.value.removePrefix("strokes:"), pos + 8, m.value.length - 8))
                            pos += m.value.length
                            matched = true
                        }
                        hskRangeRegex.containsMatchIn(remaining) -> {
                            val m = hskRangeRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.HSK_RANGE, m.value.removePrefix("hsk:"), pos + 4, m.value.length - 4))
                            pos += m.value.length
                            matched = true
                        }
                        frequencyRegex.containsMatchIn(remaining) -> {
                            val m = frequencyRegex.find(remaining) ?: return tokens
                            tokens.add(AdvancedSearchToken(AdvancedTokenType.FREQUENCY_RANGE, m.value.removePrefix("frequency:"), pos + 10, m.value.length - 10))
                            pos += m.value.length
                            matched = true
                        }
                        else -> {
                            // Búsqueda general: consume palabras o caracteres Hanzi
                            val wordMatch = Regex("""[a-zA-Z0-9_-]+|[\u4E00-\u9FFF]+""").find(remaining)
                            if (wordMatch != null) {
                                tokens.add(AdvancedSearchToken(AdvancedTokenType.GENERAL_SEARCH, wordMatch.value, pos, wordMatch.value.length))
                                pos += wordMatch.value.length
                                matched = true
                            }
                        }
                    }
                }
            }
            
            if (!matched) {
                // Si no se puede hacer una transición válida, error
                tokens.add(AdvancedSearchToken(AdvancedTokenType.INVALID, text[pos].toString(), pos, 1))
                pos++
            }
        }
        
        return tokens
    }
    
    /**
     * Valida una secuencia de tokens (análisis sintáctico simple)
     * Ejemplo: "p:shi3 AND d:lion" es válido
     * Ejemplo: "AND AND" es inválido
     */
    fun validateTokenSequence(tokens: List<AdvancedSearchToken>): Boolean {
        if (tokens.isEmpty()) return false
        
        var lastWasOperator = true
        for (token in tokens) {
            when (token.type) {
                AdvancedTokenType.LPAREN -> {
                    lastWasOperator = true
                }
                AdvancedTokenType.RPAREN -> {
                    lastWasOperator = false
                }
                AdvancedTokenType.LOGICAL_AND, AdvancedTokenType.LOGICAL_OR, AdvancedTokenType.LOGICAL_NOT -> {
                    if (lastWasOperator) return false // Dos operadores seguidos
                    lastWasOperator = true
                }
                else -> {
                    lastWasOperator = false
                }
            }
        }
        
        return !lastWasOperator // No debe terminar con operador
    }
}
