parser grammar LTLParser;

options { tokenVocab=LTLLexer; }

ltl
    : equivalence
    ;

equivalence
    : left=implication ( EQUI right=implication )?
    ;

implication
    : left=disjunction ( IMPL right=disjunction )?
    ;

disjunction
    : terms+=conjunction ( LOR terms+=conjunction )*
    ;

conjunction
    : terms+=binaryOp ( LAND terms+=binaryOp )*
    ;

binaryOp
    : left=unaryOp ( op=(UNTIL|WUNTIL) right=unaryOp )? # Until
    ;

unaryOp
    : formula=primary # NoUnaryOp
    | nuop=(NEGATION | ALWAYS | EVENTUALLY | NEXT) nested=unaryOp # Nested
    ;

primary
    : atom=atomicProp
    | id=ID
    | LPAREN formula=ltl RPAREN
    ;

atomicProp
    : primitive=TRUE
    | primitive=FALSE
    | lfname=ID IN reactor=qualifiedName op=relOp val=INTEGER
    | lfname=ID IN reactor=qualifiedName bop=(EQ|NEQ) bval=(TRUE|FALSE)
    | reactor=qualifiedName DOT reaction=INTEGER INVOKED
    | event=EVENT LPAREN reactor=qualifiedName COMMA trigger=ID (COMMA val=(INTEGER|TRUE|FALSE))? RPAREN INQUEUE
    | rtime=REMAININGTIME rval=INTEGER
    | left=sum op=relOp right=sum
    ;

qualifiedName
    : ID (DOT ID)*
    ;

time
    : value=INTEGER (unit=ID)?
    ;

sum
    : terms+=difference (PLUS terms+=difference)*
    ;

difference
    : terms+=product (MINUS terms+=product)*
    ;

product
    : terms+=quotient (TIMES terms+=quotient)*
    ;

quotient
    : terms+=expr (DIV terms+=expr)*
    ;

relOp
    : EQ | NEQ | LT | LE | GT | GE
    ;

expr
    : lfname=ID IN reactor=qualifiedName
    | LPAREN sum RPAREN
    | INTEGER
    ;
