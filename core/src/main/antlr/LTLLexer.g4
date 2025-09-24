lexer grammar LTLLexer;


DOT
    : '.'
    ;

LPAREN
    : '('
    ;

RPAREN
    : ')'
    ;

LBRACKET
    : '['
    ;

RBRACKET
    : ']'
    ;

LAND
    : '/\\'
    ;

LOR
    : '\\/'
    ;

EQUI
    : '<==>'
    | '<->'
    ;

IMPL
    : '==>'
    | '->'
    ;

UNTIL
    : 'U'
    ;

NEGATION
    : '~'
    | '!'
    ;

NEXT
    : 'X'
    | 'O'
    ;

EVENTUALLY
    : '<>'
    ;

ALWAYS
    : '[]'
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

TRUE
    : 'true'
    | 'True'
    ;

FALSE
    : 'false'
    | 'False'
    ;

PLUS
    : '+'
    ;

MINUS
    : '-'
    ;

TIMES
    : '*'
    ;

DIV
    : '/'
    ;

EQ
    : '=='
    ;

NEQ
    : '=/='
    ;

LT
    : '<'
    ;

LE
    : '<='
    ;

GT
    : '>'
    ;

GE
    : '>='
    ;

INTEGER
    : [0-9]+
    ;

IN
    : 'in'
    ;

INVOKED
    : 'invoked'
    ;

ID
    : ([a-zA-Z0-9]|'_')+
    ;
