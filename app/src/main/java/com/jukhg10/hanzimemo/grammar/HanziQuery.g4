// HanziQuery.g4 - Grammar for Chinese character/word search with ANTLR4
grammar HanziQuery;

/**
 * Reglas principales:
 * - query: consulta completa (una o más condiciones)
 * - condition: una condición de búsqueda (p:pinyin d:definition h:hanzi)
 * - term: token de búsqueda (palabra/frase)
 * 
 * Ejemplos:
 *   p:ni3      (buscar pinyin "ni3")
 *   d:person   (buscar definición "person")
 *   h:你       (buscar carácter/palabra "你")
 *   p:ni3 d:person (buscar pinyin "ni3" Y definición "person")
 */

query
    : condition (WHITESPACE condition)*
    ;

condition
    : pinyinSearch
    | definitionSearch
    | hanziSearch
    | generalSearch
    ;

pinyinSearch
    : PINYIN_PREFIX term
    ;

definitionSearch
    : DEFINITION_PREFIX term
    ;

hanziSearch
    : HANZI_PREFIX term
    ;

generalSearch
    : term
    ;

term
    : WORD
    | QUOTED_STRING
    ;

// Lexer rules
PINYIN_PREFIX
    : 'p' ':' | 'P' ':'
    ;

DEFINITION_PREFIX
    : 'd' ':' | 'D' ':'
    ;

HANZI_PREFIX
    : 'h' ':' | 'H' ':'
    ;

WORD
    : [a-zA-Z0-9\u4E00-\u9FFF]+
    ;

QUOTED_STRING
    : '"' ~["\r\n]* '"'
    | '\'' ~['\r\n]* '\''
    ;

WHITESPACE
    : [ \t\r\n]+ -> skip
    ;
