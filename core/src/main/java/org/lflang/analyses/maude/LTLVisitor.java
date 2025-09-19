package org.lflang.analyses.maude;

import org.lflang.dsl.LTLParser;
import org.lflang.dsl.LTLParserBaseVisitor;
import org.lflang.generator.CodeBuilder;

public class LTLVisitor extends LTLParserBaseVisitor<String> {

    protected CodeBuilder code = new CodeBuilder();

    public LTLVisitor() {    }

    public String visitLtl(LTLParser.LtlContext ctx) {
        return visitEquivalence(ctx.equivalence());
    }

    public String visitEquivalence(LTLParser.EquivalenceContext ctx) {
        return "";
    }

}