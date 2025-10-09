package org.lflang.analyses.maude;

import java.util.ArrayList;
import java.util.List;

import org.lflang.dsl.LTLParser;
import org.lflang.dsl.LTLParser.NestedContext;
import org.lflang.dsl.LTLParserBaseVisitor;
import org.lflang.generator.CodeBuilder;
import org.lflang.generator.NamedInstance;

public class LTLVisitor extends LTLParserBaseVisitor<String> {

    private List<NamedInstance> instances = new ArrayList<NamedInstance>();
    public List<MaudeReactorInstance> reactors = new ArrayList<>();

    protected CodeBuilder code = new CodeBuilder();

    public LTLVisitor(List<MaudeReactorInstance> reactors) {
        this.reactors.addAll(reactors);
    }

    public String visitLtl(LTLParser.LtlContext ctx) {
        return visitEquivalence(ctx.equivalence());
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
                    + (i == ctx.terms.size() - 1 ? "" : " \\/ ");
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
                    ")" + (i == ctx.terms.size() - 1 ? "" : " /\\ ");
        }
        return str;
    }

    // A custom dispatch function
    public String _visitUnaryOp(LTLParser.UnaryOpContext ctx) {
        if (ctx instanceof LTLParser.NoUnaryOpContext _ctx) {
            return visitNoUnaryOp(_ctx);
        }
        if (ctx instanceof LTLParser.NestedContext _ctx) {
            switch (_ctx.nuop.getType()) {
                case LTLParser.NEGATION:
                    return "~ (" + _visitUnaryOp(_ctx.nested)  + ")";
                case LTLParser.ALWAYS:
                    return "[] (" + _visitUnaryOp(_ctx.nested) + ")";
                case LTLParser.EVENTUALLY:
                    return "<> (" + _visitUnaryOp(_ctx.nested) + ")";
                default:
                    throw new RuntimeException("Unexpected nested operand "+_ctx.nuop.getText());
            }

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

        return "(" + _visitUnaryOp(ctx.left) + ") " + ctx.op.getText() + " (" + _visitUnaryOp(ctx.right) + ")";
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
            throw new RuntimeException("Unrecognized id "+ctx.id.getText());
        }
        else return visitLtl(ctx.formula);

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
            MaudeReactorInstance reactor = getMaudeReactorByLFname(ctx.reactor.getText());
            if (reactor == null)
                throw new RuntimeException("Could not find reactor "+ctx.reactor.getText() + " used for expr "+ctx.lfname.getText() + " IN " + ctx.reactor.getText());

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
            MaudeReactorInstance reactor = getMaudeReactorByLFname(ctx.reactor.getText());
            if (reactor == null)
                throw new RuntimeException("Could not find reactor "+ctx.reactor.getText() + " used for expr "+ctx.reactor.getText() + "." + ctx.reaction.getText() + " invoked");
            return "((" + reactor.getName() + " . " + ctx.reaction.getText() + ") invoked )";
        }
        else if (ctx.event != null) {
            if (ctx.reactor == null)
                throw new RuntimeException("Reactor not defined for event "+ctx.reactor.getText());
            MaudeReactorInstance reactor = getMaudeReactorByLFname(ctx.reactor.getText());
            if (reactor == null)
                throw new RuntimeException("Could not find reactor "+ctx.reactor.getText() + " used for expr "+ctx.event.getText() + "(" + ctx.reactor.getText() + ")");

            String maudeName = getMaudeobjByLFname(reactor, ctx.trigger.getText());
            if (maudeName == null)
                throw new RuntimeException("Could not find component " + ctx.trigger.getText() + " for event in  "+ctx.reactor.getText());

            String ret = "(event("+reactor+", "+maudeName;
            if (ctx.val != null)
                ret += ", [" + ctx.val.getText() + "]";

            ret += ") inQueue)";
            return ret;
        }
        else {
            String op = ctx.op.getText();
            if (op.equals("=="))
                op = "===";
            else if (op.equals("!="))
                op = "==/=";

            return "(" + visitExpr(ctx.left) + ") " + op + " (" + visitExpr(ctx.right)
                + ")";
        }
    }

    public String visitExpr(LTLParser.ExprContext ctx) {
        if (ctx.ID() != null) {
            //TODO: Translate LF name to Maude name
            return ctx.ID().getText();
        }
        else if (ctx.INTEGER() != null) {
            return "@ [" + ctx.INTEGER().getText() + "]";
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

    private MaudeReactorInstance getMaudeReactorByLFname(String lfReactorName) {
        for (MaudeReactorInstance r : this.reactors) {
            String reactorName = r.lfReactor.getName();
            if (r.lfReactor.getName().equals(lfReactorName))
                return r;
        }
        return null;
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