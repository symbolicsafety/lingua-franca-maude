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
    | nuop=(NEGATION | ALWAYS | EVENTUALLY) nested=unaryOp # Nested
    | NEGATION formula=primary # Negation
    | NEXT formula=primary # Next
    | EVENTUALLY formula=primary # Eventually
    | ALWAYS formula=primary # Always
    ;

primary
    : atom=atomicProp
    | id=ID
    | LPAREN formula=ltl RPAREN
    ;

atomicProp
    : primitive=TRUE
    | primitive=FALSE
    | lfname=ID IN reactor=ID op=relOp val=INTEGER
    | lfname=ID IN reactor=ID bop=(EQ|NEQ) bval=(TRUE|FALSE)
    | reactor=ID DOT reaction=INTEGER INVOKED
    | event=EVENT LPAREN reactor=ID COMMA trigger=ID (COMMA val=(INTEGER|TRUE|FALSE))? RPAREN INQUEUE
    | left=expr op=relOp right=expr
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
    : ID
    | LPAREN sum RPAREN
    | INTEGER
    ;