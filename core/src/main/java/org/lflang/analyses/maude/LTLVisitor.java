package org.lflang.analyses.maude;

import java.util.ArrayList;
import java.util.List;

import org.lflang.dsl.LTLParser;
import org.lflang.dsl.LTLParserBaseVisitor;
import org.lflang.generator.CodeBuilder;
import org.lflang.generator.NamedInstance;

public class LTLVisitor extends LTLParserBaseVisitor<String> {

    private List<NamedInstance> instances = new ArrayList<NamedInstance>();

    protected CodeBuilder code = new CodeBuilder();

    public LTLVisitor() {    }

    public String visitLtl(LTLParser.LtlContext ctx) {
        return visitEquivalence(ctx.equivalence());
    }

    public String visitEquivalence(LTLParser.EquivalenceContext ctx) {

        if (ctx.right == null) {
            return visitImplication(ctx.left);
        }

        return visitImplication(ctx.left)  + " <->"  +
             visitImplication(ctx.right);
    }

    public String visitImplication(LTLParser.ImplicationContext ctx) {
        if (ctx.right == null) {
            return visitDisjunction(ctx.left);
        }
        return visitDisjunction(ctx.left) + "->"
            + visitDisjunction(ctx.right);
    }

    public String visitDisjunction(LTLParser.DisjunctionContext ctx) {
        String str = "";
        for (int i = 0; i < ctx.terms.size(); i++) {
            str +=
                "("
                    + visitConjunction(ctx.terms.get(i))
                    + ")"
                    + (i == ctx.terms.size() - 1 ? "" : "\\/");
        }
        return str;
    }

    public String visitConjunction(LTLParser.ConjunctionContext ctx) {
        String str = "";
        for (int i = 0; i < ctx.terms.size(); i++) {
            str +=
                "("
                    + visitUntil(
                    (LTLParser.UntilContext) ctx.terms.get(i)) +
                    ")" + (i == ctx.terms.size() - 1 ? "" : "/\\");
        }
        return str;
    }

    // A custom dispatch function
    public String _visitUnaryOp(LTLParser.UnaryOpContext ctx) {
        if (ctx instanceof LTLParser.NoUnaryOpContext _ctx) {
            return visitNoUnaryOp(_ctx);
        }
        if (ctx instanceof LTLParser.NegationContext _ctx) {
            return visitNegation(_ctx);
        }

        if (ctx instanceof LTLParser.NextContext _ctx) {
            return visitNext(_ctx);
        }

        if (ctx instanceof LTLParser.AlwaysContext _ctx) {
            return visitAlways(_ctx);
        }

        if (ctx instanceof LTLParser.EventuallyContext _ctx) {
            return visitEventually(_ctx);
        }

        throw new RuntimeException("Unexpected context: " + ctx.getText());
        //return "";
    }

        public String visitUntil(LTLParser.UntilContext ctx) {

        if (ctx.right == null) {
            return _visitUnaryOp(ctx.left);
        }

        return "(" + _visitUnaryOp(ctx.left) + ") U (" + _visitUnaryOp(ctx.right) + ")";
    }

    public String visitNoUnaryOp(LTLParser.NoUnaryOpContext ctx) {

        return visitPrimary(ctx.formula);
    }

    public String visitNegation(LTLParser.NegationContext ctx) {
        return "~ (" + visitPrimary(ctx.formula) + ")";
    }

    public String visitNext(LTLParser.NextContext ctx) {
        return "O (" + visitPrimary(ctx.formula) + ")";
    }

    public String visitAlways(LTLParser.AlwaysContext ctx) {
        return "[] (" + visitPrimary(ctx.formula) + ")";
    }
    public String visitEventually(LTLParser.EventuallyContext ctx) {
        return "<> (" + visitPrimary(ctx.formula) +")";
    }
    public String visitPrimary(LTLParser.PrimaryContext ctx) {
        if (ctx.atom != null)
            return visitAtomicProp(ctx.atom);
        else if (ctx.id != null) {
            // Check if the ID is a reaction.
            //FIXME: as in MTLvisitor, this is not robust
            if (ctx.id.getText().contains("reaction")) {
            }
            else if (ctx.id.getText().contains("invoked")) {

            }
        }
        else return visitLtl(ctx.formula);
        return "";
    }

    public String visitAtomicProp(LTLParser.AtomicPropContext ctx) {
        if (ctx.primitive != null) return "[" + ctx.primitive.getText() + "]";
        else
            return "(" + visitExpr(ctx.left) + ") " + ctx.op.getText() + " (" + visitExpr(ctx.right) + ")";
    }

    public String visitExpr(LTLParser.ExprContext ctx) {
        if (ctx.ID() != null) {
            //TODO: Translate LF name to Maude name
            return ctx.ID().getText();
        }
        else if (ctx.INTEGER() != null) {
            return ctx.INTEGER().getText();
        }

        else return visitSum(ctx.sum());
    }

    public String visitSum(LTLParser.SumContext ctx) {
        String str = "";
        for (int i = 0; i < ctx.terms.size(); i++) {
            str += "(" + visitDifference(ctx.terms.get(i)) + ")" +
                (i == ctx.terms.size() - 1 ? "" : "+");
        }
        return str;
    }

    public String visitDifference(LTLParser.DifferenceContext ctx) {
        String str = "";
        for (int i = 0; i < ctx.terms.size(); i++) {
            str +=
                "(" + visitProduct(ctx.terms.get(i)) + ")" +
                    (i == ctx.terms.size() - 1 ? "" : "-");
        }
        return str;
    }

    public String visitProduct(LTLParser.ProductContext ctx) {
        String str = "";
        for (int i = 0; i < ctx.terms.size(); i++) {
            str +=
                "(" + visitQuotient(ctx.terms.get(i)) + ")" +
                    (i == ctx.terms.size() - 1 ? "" : "*");
        }
        return str;
    }
    public String visitQuotient(LTLParser.QuotientContext ctx) {
        String str = "";
        for (int i = 0; i < ctx.terms.size(); i++) {
            str +=
                "(" + visitExpr(ctx.terms.get(i)) + ")" +
                    (i == ctx.terms.size() - 1 ? "" : "/");
        }
        return str;
    }


}