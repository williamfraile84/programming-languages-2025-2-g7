package com.jukhg10.hanzimemo.lexer

import com.jukhg10.hanzimemo.data.SearchToken
import com.jukhg10.hanzimemo.data.TokenType

/**
 * AFDLexer - Autómata Finito Determinista para tokenizar búsquedas
 * 
 * Estados:
 * - INITIAL: estado inicial
 * - PREFIX_P: leyendo "p:"
 * - PREFIX_D: leyendo "d:"
 * - PREFIX_H: leyendo "h:"
 * - COLLECTING_TOKEN: recolectando caracteres del token
 * - TERMINAL: token completo
 * 
 * Transiciones:
 * - 'p:' -> COLLECTING_TOKEN (PINYIN_SEARCH)
 * - 'd:' -> COLLECTING_TOKEN (DEFINITION_SEARCH)
 * - 'h:' -> COLLECTING_TOKEN (HANZI_SEARCH)
 * - espacio/EOF -> TERMINAL (token completo)
 */

enum class AFDState {
    INITIAL,
    PREFIX_P,
    PREFIX_D,
    PREFIX_H,
    COLLECTING_TOKEN,
    TERMINAL
}

data class Token(
    val type: TokenType,
    val value: String
)

class AFDLexer(private val input: String) {
    private var position = 0
    private var currentState = AFDState.INITIAL
    private var tokenBuffer = StringBuilder()
    private var tokenType: TokenType? = null
    private val tokens = mutableListOf<Token>()

    fun tokenize(): List<Token> {
        while (position < input.length) {
            val char = input[position]
            
            when (currentState) {
                AFDState.INITIAL -> {
                    when {
                        char == 'p' || char == 'P' -> {
                            currentState = AFDState.PREFIX_P
                            position++
                        }
                        char == 'd' || char == 'D' -> {
                            currentState = AFDState.PREFIX_D
                            position++
                        }
                        char == 'h' || char == 'H' -> {
                            currentState = AFDState.PREFIX_H
                            position++
                        }
                        char.isWhitespace() -> {
                            position++
                        }
                        else -> {
                            // GENERAL_SEARCH
                            tokenType = TokenType.GENERAL_SEARCH
                            currentState = AFDState.COLLECTING_TOKEN
                        }
                    }
                }
                
                AFDState.PREFIX_P -> {
                    if (char == ':') {
                        tokenType = TokenType.PINYIN_SEARCH
                        currentState = AFDState.COLLECTING_TOKEN
                        position++
                    } else {
                        // Fallback: es búsqueda general
                        tokenType = TokenType.GENERAL_SEARCH
                        currentState = AFDState.COLLECTING_TOKEN
                        tokenBuffer.append('p')
                    }
                }
                
                AFDState.PREFIX_D -> {
                    if (char == ':') {
                        tokenType = TokenType.DEFINITION_SEARCH
                        currentState = AFDState.COLLECTING_TOKEN
                        position++
                    } else {
                        // Fallback: es búsqueda general
                        tokenType = TokenType.GENERAL_SEARCH
                        currentState = AFDState.COLLECTING_TOKEN
                        tokenBuffer.append('d')
                    }
                }
                
                AFDState.PREFIX_H -> {
                    if (char == ':') {
                        tokenType = TokenType.HANZI_SEARCH
                        currentState = AFDState.COLLECTING_TOKEN
                        position++
                    } else {
                        // Fallback: es búsqueda general
                        tokenType = TokenType.GENERAL_SEARCH
                        currentState = AFDState.COLLECTING_TOKEN
                        tokenBuffer.append('h')
                    }
                }
                
                AFDState.COLLECTING_TOKEN -> {
                    if (char.isWhitespace()) {
                        currentState = AFDState.TERMINAL
                    } else {
                        tokenBuffer.append(char)
                        position++
                    }
                }
                
                AFDState.TERMINAL -> {
                    // Guardar token
                    if (tokenBuffer.isNotEmpty() && tokenType != null) {
                        tokens.add(Token(tokenType!!, tokenBuffer.toString()))
                    }
                    
                    // Reset para próximo token
                    tokenBuffer.clear()
                    tokenType = null
                    currentState = AFDState.INITIAL
                    
                    // Skip whitespace
                    while (position < input.length && input[position].isWhitespace()) {
                        position++
                    }
                }
            }
        }
        
        // Procesar último token
        if (tokenBuffer.isNotEmpty() && tokenType != null) {
            tokens.add(Token(tokenType!!, tokenBuffer.toString()))
        }
        
        return tokens
    }

    fun getTokens(): List<SearchToken> {
        return tokenize().map { SearchToken(it.type, it.value) }
    }
}

/**
 * Función auxiliar para tokenizar una consulta
 */
fun tokenizeQuery(query: String): List<SearchToken> {
    if (query.isBlank()) return emptyList()
    return AFDLexer(query.trim()).getTokens()
}
