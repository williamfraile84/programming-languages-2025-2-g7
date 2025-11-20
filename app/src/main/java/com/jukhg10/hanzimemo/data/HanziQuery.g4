// HanziQuery.g4 - Gramática ANTLR para búsqueda lingüística de Hanzi
// Versión mejorada con múltiples reglas de producción
// Autor: HanziMemo Project

grammar HanziQuery;

/**
 * Entrada principal del parser
 * Representa una consulta de búsqueda completa
 */
query
    : advancedQuery EOF
    | simpleQuery EOF
    ;

/**
 * Consulta avanzada con operadores lógicos y filtros
 * Ejemplos:
 *   p:shi3 AND (d:lion OR d:animal)
 *   h:狮 WITH radical:王
 */
advancedQuery
    : searchExpression (logicalOp searchExpression)*
    ;

logicalOp
    : AND | OR | NOT
    ;

searchExpression
    : '(' advancedQuery ')'
    | prefixedSearch
    | rangeFilter
    | attributeFilter
    ;

/**
 * Búsqueda con prefijos estándar
 * p: (pinyin), d: (definition), h: (hanzi)
 */
prefixedSearch
    : pinyinSearch
    | definitionSearch
    | hanziSearch
    ;

pinyinSearch
    : 'p:' pinyinValue
    ;

pinyinValue
    : SYLLABLE (SPACE SYLLABLE)*
    ;

definitionSearch
    : 'd:' definitionValue
    ;

definitionValue
    : WORD (SPACE WORD)*
    ;

hanziSearch
    : 'h:' hanziValue
    ;

hanziValue
    : HANZI+
    | QUOTED_STRING
    ;

/**
 * Filtros de rango (para stroke count, HSK level, etc.)
 * Ejemplos:
 *   strokes:4-8
 *   hsk:1-3
 */
rangeFilter
    : filterType ':' rangeValue
    ;

filterType
    : 'strokes' | 'hsk' | 'frequency'
    ;

rangeValue
    : NUMBER '..' NUMBER
    | NUMBER
    ;

/**
 * Filtros de atributos avanzados
 * Ejemplos:
 *   radical:水
 *   traditional:獅
 */
attributeFilter
    : attributeName ':' attributeValue
    ;

attributeName
    : 'radical' | 'traditional' | 'simplified' | 'pinyin' | 'frequency'
    ;

attributeValue
    : WORD
    | HANZI+
    | QUOTED_STRING
    | NUMBER
    ;

/**
 * Consulta simple sin operadores lógicos
 */
simpleQuery
    : WORD+
    | HANZI+
    | (WORD | HANZI)+
    ;

// Tokens
AND: 'AND' | 'and' | '&&' | '+' ;
OR: 'OR' | 'or' | '||' ;
NOT: 'NOT' | 'not' | '!' ;

SYLLABLE
    : ([a-z]{1,3} [1-5]?)
    | ([a-zü]{1,4})
    ;

HANZI
    : [\u4E00-\u9FFF]
    ;

WORD
    : [a-zA-Z0-9_-]+
    ;

NUMBER
    : [0-9]+
    ;

QUOTED_STRING
    : '"' (~["\r\n])* '"'
    | '\'' (~['\r\n])* '\''
    ;

SPACE
    : [ \t]+ -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
