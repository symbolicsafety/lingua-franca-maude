package org.lflang.analyses.maude;

import org.lflang.dsl.LTLParser;
import org.lflang.dsl.LTLParserBaseVisitor;
import org.lflang.generator.CodeBuilder;

public class LTLVisitor extends LTLParserBaseVisitor<String> {

    private final MaudeInstanceRegistry registry;

    protected CodeBuilder code = new CodeBuilder();

    public LTLVisitor(MaudeInstanceRegistry registry) {
        this.registry = registry;
    }

    public String visitLtl(LTLParser.LtlContext ctx) {
        //Add "global" parentheses around entire expression
        return "(" + visitEquivalence(ctx.equivalence()) + ")";
    }

    public String visitEquivalence(LTLParser.EquivalenceContext ctx) {

        if (ctx.right == null) {
            return visitImplication(ctx.left);
        }

        return visitImplication(ctx.left)  + " <-> "  +
             visitImplication(ctx.right);
    }

    public String visitImplication(LTLParser.ImplicationContext ctx) {
        if (ctx.right == null) {
            return visitDisjunction(ctx.left);
        }
        return visitDisjunction(ctx.left) + " -> "
            + visitDisjunction(ctx.right);
    }

    public String visitDisjunction(LTLParser.DisjunctionContext ctx) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < ctx.terms.size(); i++) {
            str.append(visitConjunction(ctx.terms.get(i))).append(
                i == ctx.terms.size() - 1 ? "" : " \\/ ");
        }
        return str.toString();
    }

    public String visitConjunction(LTLParser.ConjunctionContext ctx) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < ctx.terms.size(); i++) {
            str.append(visitUntil(
                (LTLParser.UntilContext) ctx.terms.get(i))).append(
                i == ctx.terms.size() - 1 ? "" : " /\\ ");
        }
        return str.toString();
    }

    //check if unary operator requires parens
    //it does not require for nested unary ops, atomic props or primary ID
    private boolean needsParens(LTLParser.UnaryOpContext ctx) {
        if (ctx instanceof LTLParser.NestedContext) {
            // neste unary op, no parens required
            return false;
        }

        //no extra parens needed
        if (ctx instanceof LTLParser.NoUnaryOpContext nupctx) {
            if (nupctx.formula.atom != null) return false;  //atomicProp add their own parens when needed
            if (nupctx.formula.id != null) return false;    //primary ID adds its own parens

            return true;
        }

        //default keep parens
        return true;
    }

    // A custom dispatch function
    public String _visitUnaryOp(LTLParser.UnaryOpContext ctx) {
        if (ctx instanceof LTLParser.NoUnaryOpContext _ctx) {
            return visitNoUnaryOp(_ctx);
        }
        if (ctx instanceof LTLParser.NestedContext _ctx) {
            boolean keep = needsParens(_ctx);
            switch (_ctx.nuop.getType()) {
                case LTLParser.NEGATION:
                    return keep ? "~ (" + _visitUnaryOp(_ctx.nested) + ")"
                                : "~ " + _visitUnaryOp(_ctx.nested);
                case LTLParser.ALWAYS:
                    return keep ? "[] (" + _visitUnaryOp(_ctx.nested) + ")"
                                : "[] " + _visitUnaryOp(_ctx.nested);
                case LTLParser.EVENTUALLY:
                    return keep ? "<> (" + _visitUnaryOp(_ctx.nested) + ")"
                                : "<> " + _visitUnaryOp(_ctx.nested);
                case LTLParser.NEXT:
                    return keep ? "O (" + _visitUnaryOp(_ctx.nested) + ")"
                                : "O " + _visitUnaryOp(_ctx.nested);

                default:
                    throw new RuntimeException("Unexpected nested operand "+_ctx.nuop.getText());
            }

        }

        throw new RuntimeException("Unexpected context: " + ctx.getText());

    }

        public String visitUntil(LTLParser.UntilContext ctx) {

        if (ctx.right == null) {
            return _visitUnaryOp(ctx.left);
        }

        return _visitUnaryOp(ctx.left) + " " + ctx.op.getText() + " " + _visitUnaryOp(ctx.right);
    }

    public String visitNoUnaryOp(LTLParser.NoUnaryOpContext ctx) {
        return visitPrimary(ctx.formula);
    }


    public String visitPrimary(LTLParser.PrimaryContext ctx) {
        if (ctx.atom != null)
            return visitAtomicProp(ctx.atom);
        else if (ctx.id != null) {
            throw new RuntimeException("Unrecognized id "+ctx.id.getText());
        }
        else
            //go to equivalence instead of "global" ltl as we already have parens there
            return "(" + visitEquivalence(ctx.formula.equivalence()) + ")";
    }

    public String visitAtomicProp(LTLParser.AtomicPropContext ctx) {
        if (ctx.primitive != null) {
            String ret = "";
            if (ctx.primitive.getText().equals("true") || ctx.primitive.getText().equals("false"))
               return "@ [" + ctx.primitive.getText() + "]";
            else
                return ctx.primitive.getText();
        }
        else if (ctx.lfname  != null) {
            if (ctx.reactor == null)
                throw new RuntimeException("Reactor not defined for "+ctx.lfname.getText() +" IN");
            MaudeReactorInstance reactor = registry.resolveReactor(ctx.reactor.getText());

            String maudeName = getMaudeobjByLFname(reactor, ctx.lfname.getText());
            if (maudeName == null)
                throw new RuntimeException("Could not find component " + ctx.lfname.getText() + " IN "+ctx.reactor.getText());

            String op = "";
            if (ctx.bop == null) {
                op = ctx.op.getText();
                if (op.equals("=="))
                    op = "===";
                else if (op.equals("!="))
                    op = "==/=";
                return "( (@ " + maudeName + " in " + reactor.getName() + ") " + op
                    + " @ [" + ctx.val.getText() + "] )";
            }
            else {
                op = ctx.bop.getText();
                if (op.equals("=="))
                    op = "===";
                else if (op.equals("!="))
                    op = "==/=";
                return "( (@ " + maudeName + " in " + reactor.getName() + ") " + op
                    + " @ [" + ctx.bval.getText() + "] )";
            }
        }
        else if (ctx.reaction != null) {
            MaudeReactorInstance reactor = registry.resolveReactor(ctx.reactor.getText());
            return "(" + reactor.getName() + " . " + ctx.reaction.getText() + ") invoked ";
        }
        else if (ctx.event != null) {
            if (ctx.reactor == null)
                throw new RuntimeException("Reactor not defined for event "+ctx.reactor.getText());
            MaudeReactorInstance reactor = registry.resolveReactor(ctx.reactor.getText());

            String maudeName = getMaudeobjByLFname(reactor, ctx.trigger.getText());
            if (maudeName == null)
                throw new RuntimeException("Could not find component " + ctx.trigger.getText() + " for event in  "+ctx.reactor.getText());

            String ret = "(event("+reactor+", "+maudeName;
            if (ctx.val != null)
                ret += ", [" + ctx.val.getText() + "]";

            ret += ") inQueue)";
            return ret;
        }
        else if (ctx.rtime != null) {
            //TODO: change tval to actual timeValue
            return ctx.rtime.getText() + " " + ctx.rval.getText();
        }
        else {
            String op = ctx.op.getText();
            if (op.equals("=="))
                op = "===";
            else if (op.equals("!="))
                op = "==/=";

            return "(" + visitSum(ctx.left) + ") " + op + " (" + visitSum(ctx.right)
                + ")";
        }
    }

    public String visitExpr(LTLParser.ExprContext ctx) {
        if (ctx.lfname != null) {
            //TODO: Translate LF name to Maude name
            MaudeReactorInstance reactor = registry.resolveReactor(ctx.reactor.getText());
            String maudeName = getMaudeobjByLFname(reactor, ctx.lfname.getText());


            return "( @ " + maudeName + " in " + reactor.getName() + ") ";
        }
        else if (ctx.INTEGER() != null) {
            return "@ [" + ctx.INTEGER().getText() + "]";
        }

        else return visitSum(ctx.sum());
    }

    public String visitSum(LTLParser.SumContext ctx) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < ctx.terms.size(); i++) {
            str.append(visitDifference(ctx.terms.get(i))).append(
                i == ctx.terms.size() - 1 ? "" : "+");
        }
        return str.toString();
    }

    public String visitDifference(LTLParser.DifferenceContext ctx) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < ctx.terms.size(); i++) {
            str.append(visitProduct(ctx.terms.get(i))).append(
                i == ctx.terms.size() - 1 ? "" : "-");
        }
        return str.toString();
    }

    public String visitProduct(LTLParser.ProductContext ctx) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < ctx.terms.size(); i++) {
            str.append(visitQuotient(ctx.terms.get(i))).append(
                i == ctx.terms.size() - 1 ? "" : "*");
        }
        return str.toString();
    }
    public String visitQuotient(LTLParser.QuotientContext ctx) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < ctx.terms.size(); i++) {
            str.append(visitExpr(ctx.terms.get(i))).append(
                i == ctx.terms.size() - 1 ? "" : "/");
        }
        return str.toString();
    }

    private String getMaudeobjByLFname(MaudeReactorInstance reactor, String lfName) {
        for (MaudeStateInstance svar : reactor.stateVars) {
            if (svar.getLfStateVar().getName().equals(lfName))
                return svar.getName();
        }
        for (MaudePortInstance port : reactor.inPorts) {
            if (port.getLfPort().getName().equals(lfName))
                return port.getName();
        }
        for (MaudePortInstance port : reactor.outPorts) {
            if (port.getLfPort().getName().equals(lfName))
                return port.getName();
        }

        for (MaudeActionInstance action : reactor.logicalActions)
            if (action.getLfAction().getName().equals(lfName))
                return action.getName();

        for (MaudeActionInstance action : reactor.physicalActions)
            if (action.getLfAction().getName().equals(lfName))
                return action.getName();

        for (MaudeTimerInstance timer : reactor.timers)
            if (timer.getLfTimer().getName().equals(lfName))
                return timer.getName();

        return null;
    }

}
