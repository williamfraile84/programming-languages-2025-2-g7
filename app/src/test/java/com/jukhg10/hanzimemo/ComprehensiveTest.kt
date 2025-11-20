package com.jukhg10.hanzimemo

import com.jukhg10.hanzimemo.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias para componentes principales de HanziMemo
 * 
 * Cobertura:
 * - SearchLexer: Tokenización básica
 * - AdvancedSearchLexer: Tokenización avanzada con AFD
 * - PinyinParser: Validación de sílabas pinyin
 * - Integración: Flujo completo de búsqueda
 */

class SearchLexerTest {
    
    @Test
    fun testSimplePrefixedSearch() {
        val result = SearchLexer.lex("p:shi3")
        assertEquals(1, result.size)
        assertEquals(TokenType.PINYIN_SEARCH, result[0].type)
        assertEquals("shi3", result[0].value)
    }
    
    @Test
    fun testMultiplePrefixedSearches() {
        val result = SearchLexer.lex("p:shui3 d:water h:水")
        assertEquals(3, result.size)
        assertEquals(TokenType.PINYIN_SEARCH, result[0].type)
        assertEquals(TokenType.DEFINITION_SEARCH, result[1].type)
        assertEquals(TokenType.HANZI_SEARCH, result[2].type)
    }
    
    @Test
    fun testGeneralSearch() {
        val result = SearchLexer.lex("animal lion")
        assertEquals(1, result.size)
        assertEquals(TokenType.GENERAL_SEARCH, result[0].type)
        assertEquals("animal lion", result[0].value)
    }
    
    @Test
    fun testEmptyQuery() {
        val result = SearchLexer.lex("")
        assertEquals(0, result.size)
    }
    
    @Test
    fun testWhitespaceNormalization() {
        val result = SearchLexer.lex("p:shi  3")
        // Should normalize multiple spaces
        assertTrue(result.isNotEmpty())
    }
}

class AdvancedSearchLexerTest {
    
    @Test
    fun testBasicPinyinToken() {
        val result = AdvancedSearchLexer.lex("p:ni3 hao3")
        assertTrue(result.any { it.type == AdvancedTokenType.PINYIN_SEARCH })
    }
    
    @Test
    fun testDefinitionFilter() {
        val result = AdvancedSearchLexer.lex("d:water animal")
        assertTrue(result.any { it.type == AdvancedTokenType.DEFINITION_SEARCH })
    }
    
    @Test
    fun testHanziSearch() {
        val result = AdvancedSearchLexer.lex("h:水")
        assertTrue(result.any { it.type == AdvancedTokenType.HANZI_SEARCH })
    }
    
    @Test
    fun testRadicalFilter() {
        val result = AdvancedSearchLexer.lex("radical:水")
        assertTrue(result.any { it.type == AdvancedTokenType.RADICAL_FILTER })
    }
    
    @Test
    fun testStrokeRange() {
        val result = AdvancedSearchLexer.lex("strokes:4..8")
        assertTrue(result.any { it.type == AdvancedTokenType.STROKE_RANGE })
        val strokeToken = result.find { it.type == AdvancedTokenType.STROKE_RANGE }
        assertEquals("4..8", strokeToken?.value)
    }
    
    @Test
    fun testHSKRange() {
        val result = AdvancedSearchLexer.lex("hsk:1..3")
        assertTrue(result.any { it.type == AdvancedTokenType.HSK_RANGE })
    }
    
    @Test
    fun testLogicalAND() {
        val result = AdvancedSearchLexer.lex("p:shui3 AND d:water")
        assertTrue(result.any { it.type == AdvancedTokenType.LOGICAL_AND })
    }
    
    @Test
    fun testLogicalOR() {
        val result = AdvancedSearchLexer.lex("h:水 OR h:火")
        assertTrue(result.any { it.type == AdvancedTokenType.LOGICAL_OR })
    }
    
    @Test
    fun testParentheses() {
        val result = AdvancedSearchLexer.lex("(p:shui3 AND d:water)")
        assertTrue(result.any { it.type == AdvancedTokenType.LPAREN })
        assertTrue(result.any { it.type == AdvancedTokenType.RPAREN })
    }
    
    @Test
    fun testComplexQuery() {
        val result = AdvancedSearchLexer.lex("(p:shui3 AND d:water) OR radical:水 strokes:4..8")
        assertTrue(result.size >= 5)
    }
    
    @Test
    fun testValidTokenSequence_Valid() {
        val tokens = listOf(
            AdvancedSearchToken(AdvancedTokenType.PINYIN_SEARCH, "shui3", 0, 6),
            AdvancedSearchToken(AdvancedTokenType.LOGICAL_AND, "AND", 7, 3),
            AdvancedSearchToken(AdvancedTokenType.DEFINITION_SEARCH, "water", 11, 5)
        )
        assertTrue(AdvancedSearchLexer.validateTokenSequence(tokens))
    }
    
    @Test
    fun testValidTokenSequence_InvalidDoubleOperator() {
        val tokens = listOf(
            AdvancedSearchToken(AdvancedTokenType.LOGICAL_AND, "AND", 0, 3),
            AdvancedSearchToken(AdvancedTokenType.LOGICAL_OR, "OR", 4, 2)
        )
        assertFalse(AdvancedSearchLexer.validateTokenSequence(tokens))
    }
    
    @Test
    fun testValidTokenSequence_EndsWithOperator() {
        val tokens = listOf(
            AdvancedSearchToken(AdvancedTokenType.PINYIN_SEARCH, "shui3", 0, 6),
            AdvancedSearchToken(AdvancedTokenType.LOGICAL_AND, "AND", 7, 3)
        )
        assertFalse(AdvancedSearchLexer.validateTokenSequence(tokens))
    }
}

class PinyinParserTest {
    
    @Test
    fun testValidSyllable_WithTone() {
        assertTrue(PinyinParser.isValidSyllable("shui3"))
        assertTrue(PinyinParser.isValidSyllable("ni3"))
        assertTrue(PinyinParser.isValidSyllable("hao3"))
    }
    
    @Test
    fun testValidSyllable_AllTones() {
        assertTrue(PinyinParser.isValidSyllable("ma1"))
        assertTrue(PinyinParser.isValidSyllable("ma2"))
        assertTrue(PinyinParser.isValidSyllable("ma3"))
        assertTrue(PinyinParser.isValidSyllable("ma4"))
        assertTrue(PinyinParser.isValidSyllable("ma5")) // Tono neutral
    }
    
    @Test
    fun testValidSyllable_WithInitial() {
        assertTrue(PinyinParser.isValidSyllable("zh1"))
        assertTrue(PinyinParser.isValidSyllable("ch2"))
        assertTrue(PinyinParser.isValidSyllable("sh3"))
    }
    
    @Test
    fun testValidSyllable_WithUmlaut() {
        assertTrue(PinyinParser.isValidSyllable("lü3"))
        assertTrue(PinyinParser.isValidSyllable("nu:3"))
    }
    
    @Test
    fun testInvalidSyllable_NoTone() {
        assertFalse(PinyinParser.isValidSyllable("shui"))
    }
    
    @Test
    fun testInvalidSyllable_InvalidFinal() {
        assertFalse(PinyinParser.isValidSyllable("xyz3"))
    }
    
    @Test
    fun testInvalidSyllable_Empty() {
        assertFalse(PinyinParser.isValidSyllable(""))
    }
    
    @Test
    fun testInvalidSyllable_OnlyTone() {
        assertFalse(PinyinParser.isValidSyllable("3"))
    }
    
    @Test
    fun testParseValidSyllable() {
        val parsed = PinyinParser.parse("shui3")
        assertNotNull(parsed)
        assertEquals("sh", parsed?.initial)
        assertEquals("ui", parsed?.`final`)
        assertEquals(3, parsed?.tone)
    }
    
    @Test
    fun testParseValidSyllable_NoInitial() {
        val parsed = PinyinParser.parse("ai3")
        assertNotNull(parsed)
        assertNull(parsed?.initial)
        assertEquals("ai", parsed?.`final`)
    }
    
    @Test
    fun testParseInvalidSyllable() {
        val parsed = PinyinParser.parse("invalid")
        assertNull(parsed)
    }
    
    @Test
    fun testValidateToken_SingleSyllable() {
        assertTrue(PinyinParser.validateToken("shui3"))
    }
    
    @Test
    fun testValidateToken_MultipleSyllables() {
        assertTrue(PinyinParser.validateToken("ni3 hao3"))
        assertTrue(PinyinParser.validateToken("zhang1 san1"))
    }
    
    @Test
    fun testValidateToken_InvalidSyllable() {
        assertFalse(PinyinParser.validateToken("xyz123"))
    }
    
    @Test
    fun testValidateToken_Mixed() {
        assertFalse(PinyinParser.validateToken("ni3 xyz"))
    }
}

class IntegrationTest {
    
    @Test
    fun testFullSearchFlow_PinyinSearch() {
        // Simular flujo completo: Lexer → Tokens → Parser validation
        val query = "p:shui3"
        val tokens = SearchLexer.lex(query)
        
        assertEquals(1, tokens.size)
        assertEquals(TokenType.PINYIN_SEARCH, tokens[0].type)
        
        val isValid = PinyinParser.validateToken(tokens[0].value)
        assertTrue(isValid)
    }
    
    @Test
    fun testFullSearchFlow_AdvancedQuery() {
        val query = "p:shui3 AND d:water"
        val tokens = AdvancedSearchLexer.lex(query)
        
        // Validar estructura
        assertTrue(AdvancedSearchLexer.validateTokenSequence(tokens))
        
        // Validar pinyin si está presente
        val pinyinToken = tokens.find { it.type == AdvancedTokenType.PINYIN_SEARCH }
        if (pinyinToken != null) {
            assertTrue(PinyinParser.validateToken(pinyinToken.value))
        }
    }
    
    @Test
    fun testFullSearchFlow_InvalidPinyin() {
        val query = "p:xyz123 AND d:water"
        val tokens = AdvancedSearchLexer.lex(query)
        val pinyinToken = tokens.find { it.type == AdvancedTokenType.PINYIN_SEARCH }
        
        // El lexer debería reconocerlo como PINYIN_SEARCH aunque sea inválido
        assertNotNull(pinyinToken)
        
        // Pero el parser debe rechazarlo
        if (pinyinToken != null) {
            assertFalse(PinyinParser.validateToken(pinyinToken.value))
        }
    }
    
    @Test
    fun testFullSearchFlow_ComplexQuery() {
        val query = "(p:shui3 OR p:huo3) AND radical:水 strokes:4..8"
        val tokens = AdvancedSearchLexer.lex(query)
        
        assertTrue(tokens.isNotEmpty())
        assertTrue(AdvancedSearchLexer.validateTokenSequence(tokens))
        
        // Verificar que todas las partes estén presentes
        assertTrue(tokens.any { it.type == AdvancedTokenType.PINYIN_SEARCH })
        assertTrue(tokens.any { it.type == AdvancedTokenType.LOGICAL_OR })
        assertTrue(tokens.any { it.type == AdvancedTokenType.RADICAL_FILTER })
        assertTrue(tokens.any { it.type == AdvancedTokenType.STROKE_RANGE })
    }
}

class RegularExpressionTest {
    
    @Test
    fun testHanziRegex() {
        val hanziRegex = Regex("[\\u4E00-\\u9FFF]")
        assertTrue(hanziRegex.containsMatchIn("水"))
        assertTrue(hanziRegex.containsMatchIn("火"))
        assertFalse(hanziRegex.containsMatchIn("water"))
    }
    
    @Test
    fun testPinyinRegex() {
        val pinyinRegex = Regex("[a-zü]+[1-5]?")
        assertTrue(pinyinRegex.containsMatchIn("shui3"))
        assertTrue(pinyinRegex.containsMatchIn("ma1"))
        assertFalse(pinyinRegex.containsMatchIn("123"))
    }
    
    @Test
    fun testPrefixRegex() {
        val prefixRegex = Regex("\\b([pdh]):", RegexOption.IGNORE_CASE)
        assertTrue(prefixRegex.containsMatchIn("p:shui3"))
        assertTrue(prefixRegex.containsMatchIn("d:water"))
        assertTrue(prefixRegex.containsMatchIn("h:水"))
        assertFalse(prefixRegex.containsMatchIn(":shui3"))
    }
    
    @Test
    fun testRangeRegex() {
        val rangeRegex = Regex("(\\d+)(?:\\.\\.(\\d+))?")
        val match = rangeRegex.find("4..8")
        assertNotNull(match)
        assertEquals("4..8", match?.value)
    }
}

class EdgeCaseTest {
    
    @Test
    fun testEmptyInput() {
        val lexerResult = SearchLexer.lex("")
        assertEquals(0, lexerResult.size)
    }
    
    @Test
    fun testWhitespaceOnly() {
        val lexerResult = SearchLexer.lex("   ")
        assertEquals(0, lexerResult.size)
    }
    
    @Test
    fun testSpecialCharacters() {
        val query = "p:shui3!@#"
        val tokens = SearchLexer.lex(query)
        assertTrue(tokens.isNotEmpty())
    }
    
    @Test
    fun testVeryLongQuery() {
        val longQuery = "p:" + "a".repeat(100)
        val tokens = SearchLexer.lex(longQuery)
        assertTrue(tokens.isNotEmpty())
    }
    
    @Test
    fun testMixedCase() {
        val query = "P:Shui3"
        val tokens = SearchLexer.lex(query)
        // Should be case-insensitive
        assertTrue(tokens.isNotEmpty())
    }
}
