package com.jukhg10.hanzimemo

import com.jukhg10.hanzimemo.data.PinyinParser
import com.jukhg10.hanzimemo.data.SearchLexer
import com.jukhg10.hanzimemo.data.TokenType
import org.junit.Assert.*
import org.junit.Test

class SearchLexerPinyinParserTest {

    @Test
    fun lexer_parses_prefixed_tokens_and_general() {
        val input = "p:ni hao d:water h:你 好"
        val tokens = SearchLexer.lex(input)
        assertEquals(3, tokens.size)
        assertEquals(TokenType.PINYIN_SEARCH, tokens[0].type)
        assertEquals("ni hao", tokens[0].value)
        assertEquals(TokenType.DEFINITION_SEARCH, tokens[1].type)
        assertEquals("water", tokens[1].value)
        assertEquals(TokenType.HANZI_SEARCH, tokens[2].type)
        assertEquals("你 好", tokens[2].value)
    }

    @Test
    fun lexer_returns_general_when_no_prefix() {
        val input = "hello world"
        val tokens = SearchLexer.lex(input)
        assertEquals(1, tokens.size)
        assertEquals(TokenType.GENERAL_SEARCH, tokens[0].type)
        assertEquals("hello world", tokens[0].value)
    }

    @Test
    fun pinyin_parser_valid_examples() {
        assertTrue(PinyinParser.isValidSyllable("shui3"))
        assertTrue(PinyinParser.isValidSyllable("ni3"))
        assertTrue(PinyinParser.isValidSyllable("zhong1"))
        assertTrue(PinyinParser.isValidSyllable("lü4"))
        assertTrue(PinyinParser.isValidSyllable("lv4")) // v -> ü
    }

    @Test
    fun pinyin_parser_invalid_examples() {
        assertFalse(PinyinParser.isValidSyllable("nia"))
        assertFalse(PinyinParser.isValidSyllable("abc1"))
        assertFalse(PinyinParser.isValidSyllable("shui")) // falta tono
    }
}
