package org.lflang.analyses.c;

import org.lflang.analyses.c.CAst.AdditionNode;
import org.lflang.analyses.c.CAst.AssignmentNode;
import org.lflang.analyses.c.CAst.DivisionNode;
import org.lflang.analyses.c.CAst.EqualNode;
import org.lflang.analyses.c.CAst.GreaterEqualNode;
import org.lflang.analyses.c.CAst.GreaterThanNode;
import org.lflang.analyses.c.CAst.IfBlockNode;
import org.lflang.analyses.c.CAst.IfBodyNode;
import org.lflang.analyses.c.CAst.LessEqualNode;
import org.lflang.analyses.c.CAst.LessThanNode;
import org.lflang.analyses.c.CAst.LiteralNode;
import org.lflang.analyses.c.CAst.LogicalAndNode;
import org.lflang.analyses.c.CAst.LogicalNotNode;
import org.lflang.analyses.c.CAst.LogicalOrNode;
import org.lflang.analyses.c.CAst.MultiplicationNode;
import org.lflang.analyses.c.CAst.NegativeNode;
import org.lflang.analyses.c.CAst.NotEqualNode;
import org.lflang.analyses.c.CAst.ScheduleActionIntNode;
import org.lflang.analyses.c.CAst.ScheduleActionNode;
import org.lflang.analyses.c.CAst.SetPortNode;
import org.lflang.analyses.c.CAst.StateVarNode;
import org.lflang.analyses.c.CAst.StatementSequenceNode;
import org.lflang.analyses.c.CAst.SubtractionNode;
import org.lflang.analyses.c.CAst.TriggerIsPresentNode;
import org.lflang.analyses.c.CAst.TriggerValueNode;
import org.lflang.analyses.c.CAst.VariableNode;
import org.lflang.analyses.maude.MaudeReactorInstance;

public class CToMaudeVisitor extends CBaseAstVisitor<String> {
    private final MaudeReactorInstance parent;

    public CToMaudeVisitor(MaudeReactorInstance parent) {
        this.parent = parent;
    }

    @Override
    public String visitAdditionNode(AdditionNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);

        return "(" + lhs + " + " + rhs + ")";
    }

    @Override
    public String visitSubtractionNode(SubtractionNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " - " + rhs + ")";
    }

    @Override
    public String visitAssignmentNode(AssignmentNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " := " + rhs + ")";
    }

    @Override
    public String visitDivisionNode(DivisionNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        throw new RuntimeException("Division not yet implemented in Maude");
        //return lhs + " / " + rhs;
    }

    @Override
    public String visitEqualNode(EqualNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " === " + rhs + ")";
    }

    @Override
    public String visitNotEqualNode(NotEqualNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" +  lhs + " ==/= " + rhs + ")";
    }

    @Override
    public String visitGreaterEqualNode(GreaterEqualNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" +  lhs + " >= " + rhs + ")";
    }

    @Override
    public String visitGreaterThanNode(GreaterThanNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " > " + rhs + ")";
    }

    @Override
    public String visitLessEqualNode(LessEqualNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " <= " + rhs + ")";
    }

    @Override
    public String visitLessThanNode(LessThanNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " < " + rhs +")";
    }

    @Override
    public String visitIfBlockNode(IfBlockNode node) {
        String antecedent = visit(node.left);
        String consequent = visit(((IfBodyNode)node.right).left);
        String alternative = "";
        if (((IfBodyNode)node.right).right != null)
            alternative = visit(((IfBodyNode)node.right).right);
        if (alternative != ""){
            return "if (" + antecedent + ") then (" + consequent + ") else (" + alternative +  ") fi";
        }
        return "if (" + antecedent + ") then (" + consequent + ") fi";
    }

    // Does not get called
//    @Override
//    public String visitIfBodyNode(IfBodyNode node) {
//        String then = visit(node.left);
//        String else_ = visit(((IfBodyNode)node.right).left);
//        return "then ( " + then + " ) else ( " + else_ + " )";
//    }

    @Override
    public String visitLiteralNode(LiteralNode node) {
        return "[" + node.literal + "]";
    }

    @Override
    public String visitLogicalAndNode(LogicalAndNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + ") && (" + rhs + ")";
    }

    @Override
    public String visitLogicalOrNode(LogicalOrNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " || " + rhs + ")";
    }

    @Override
    public String visitLogicalNotNode(LogicalNotNode node) {
        return "( ! " + visit(node.child) + ") ";
    }

    @Override
    public String visitMultiplicationNode(MultiplicationNode node) {
        String lhs = visit(node.left);
        String rhs = visit(node.right);
        return "(" + lhs + " * " + rhs + ")";
    }

    @Override
    public String visitNegativeNode(NegativeNode node) {
        if (node.child instanceof LiteralNode _clit)
            return "[-" + _clit.literal + "]";
        throw new RuntimeException("Negative node not yet implemented in Maude for anything other than literals.");
    }

    @Override
    public String visitScheduleActionNode(ScheduleActionNode node) {
        String name = ((VariableNode) node.children.get(0)).name;
        var mAction = parent.requireAction(name);
        String additionalDelay = visit(node.children.get(1));
        Long delay = Long.parseLong(additionalDelay.replaceAll("\\[|\\]",""));
        String payload = "[" + mAction.payload.toString() + "]";
        return "schedule(" + mAction.getName() + ", [" + delay + "], " + payload + ")";
    }

    @Override
    public String visitScheduleActionIntNode(ScheduleActionIntNode node) {
        String name = ((VariableNode) node.children.get(0)).name;
        var mAction = parent.requireAction(name);
        String additionalDelay = visit(node.children.get(1));
        Long delay = Long.parseLong(additionalDelay.replaceAll("\\[|\\]",""));
        String payload = visit(node.children.get(2));

        return "schedule(" + mAction.getName() + ", [" + delay + "], " + payload + ")";
    }

    //TODO: Add visitScheduleActionTokenNode to handle booleans.
    //TODO: will require addition to org.lflang.analyses.c also

    @Override
    public String visitSetPortNode(SetPortNode node) {
        var port = parent.requirePort(((VariableNode) node.left).name);
        String payload = visit(node.right);
        return "(" + port.getName() + " <- " + payload + ")";
    }

    @Override
    public String visitStateVarNode(StateVarNode node) {
        return parent.requireState(node.name).getName();
    }

    @Override
    public String visitTriggerIsPresentNode(TriggerIsPresentNode node) {
        return "(isPresent(" + parent.requireTrigger(node.name).getName() +"))";
    }

    @Override
    public String visitTriggerValueNode(TriggerValueNode node) {
        return parent.requireTrigger(node.name).getName();
    }

    @Override
    public String visitVariableNode(VariableNode node) {
        if (node.type.name().equals("UNKNOWN") && (node.name.equalsIgnoreCase("true") || node.name.equalsIgnoreCase("false"))) {
            return "["+node.name.toLowerCase()+"]";
        }
        return parent.requireState(node.name).getName();
    }

    @Override
    public String visitStatementSequenceNode(StatementSequenceNode node) {
        String result = new String();
        for (int i = 0; i < node.children.size(); i++) {
            String temp = visit(node.children.get(i));
            // treat opaque node as skip instruction.
            if (temp == null)
                temp = "skip";
            result += temp;
            if (i != node.children.size() - 1) {
                    result += " ;\n";
                }
        }
        return result;
    }

}
