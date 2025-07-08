package org.lflang.analyses.c;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;

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
import org.lflang.analyses.maude.MaudeActionInstance;
import org.lflang.analyses.maude.MaudeGenerator;
import org.lflang.analyses.maude.MaudePortInstance;
import org.lflang.analyses.maude.MaudeReactionInstance;
import org.lflang.analyses.maude.MaudeReactorInstance;
import org.lflang.analyses.maude.MaudeStateInstance;
import org.lflang.analyses.maude.MaudeTriggerInstance;
import org.lflang.analyses.maude.MaudeTypes;
import org.lflang.ast.ASTUtils;
import org.lflang.generator.ActionInstance;
import org.lflang.generator.CodeBuilder;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.generator.NamedInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance;
import org.lflang.generator.ReactionInstance.Runtime;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TimerInstance;
import org.lflang.generator.TriggerInstance;
import org.lflang.lf.Action;

public class CToMaudeVisitor extends CBaseAstVisitor<String> {
    private MaudeGenerator generator;
    private MaudeReactionInstance reaction;
    private MaudeReactorInstance parent;
    /** A list of all the named instances */
    private List<NamedInstance> instances = new ArrayList<NamedInstance>();

    public CToMaudeVisitor(MaudeGenerator generator, MaudeReactionInstance reaction) {
        this.generator = generator;
        this.reaction = reaction;
        this.parent = reaction.getParent();
        instances.addAll(this.parent.lfReactor.inputs);
        instances.addAll(this.parent.lfReactor.outputs);
        instances.addAll(this.parent.lfReactor.actions);
        instances.addAll(this.parent.lfReactor.states);
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
        throw new RuntimeException("Negative node not yet implemented in Maude");
    }

    @Override
    public String visitScheduleActionNode(ScheduleActionNode node) {
        String name = ((VariableNode) node.children.get(0)).name;
        NamedInstance instance = getInstanceByName(name);
        ActionInstance lfAction = (ActionInstance) instance;
        MaudeActionInstance mAction = reaction.getParent().getMaudeAction(lfAction);
        String additionalDelay = visit(node.children.get(1));
        Long delay = Long.parseLong(additionalDelay.replaceAll("\\[|\\]",""));
        Long totalDelay = mAction.minDelay + delay;
        String payload = "[" + mAction.payload.toString() + "]";
        return "schedule(" + mAction.getName() + ", [" + totalDelay + "], " + payload + ")";
    }

    @Override
    public String visitScheduleActionIntNode(ScheduleActionIntNode node) {
        String name = ((VariableNode) node.children.get(0)).name;
        NamedInstance instance = getInstanceByName(name);
        ActionInstance lfAction = (ActionInstance) instance;
        MaudeActionInstance mAction = parent.getMaudeAction(lfAction);
        String additionalDelay = visit(node.children.get(1));
        Long delay = Long.parseLong(additionalDelay);
        Long totalDelay = mAction.minDelay + delay;
        String payload = visit(node.children.get(2));

        return "schedule(" + mAction.getName() + ", " + totalDelay + ", " + payload + ")";
    }

    //TODO: Add visitScheduleActionTokenNode to handle booleans.
    //TODO: will require addition to org.lflang.analyses.c also

    @Override
    public String visitSetPortNode(SetPortNode node) {
        NamedInstance port = getInstanceByName(((VariableNode) node.left).name);
        String payload = visit(node.right);
        MaudePortInstance mPort = parent.getMaudePort((PortInstance) port);
        return "(" + mPort.getName() + " <- " + payload + ")";
    }

    @Override
    public String visitStateVarNode(StateVarNode node) {
        NamedInstance instance = getInstanceByName(node.name);
        MaudeStateInstance mState = parent.getMaudeStateVar((StateVariableInstance) instance);
        return mState.getName();
    }

    @Override
    public String visitTriggerIsPresentNode(TriggerIsPresentNode node) {
        NamedInstance instance = getInstanceByName(node.name);
        MaudeTriggerInstance mTrigger = parent.getMaudeTrigger((TriggerInstance) instance);

        return "(isPresent(" + mTrigger.getName() +"))";
    }

    @Override
    public String visitTriggerValueNode(TriggerValueNode node) {
        NamedInstance instance = getInstanceByName(node.name);
        MaudeTriggerInstance mTrigger = parent.getMaudeTrigger((TriggerInstance) instance);
        return mTrigger.getName();
    }

    @Override
    public String visitVariableNode(VariableNode node) {
        NamedInstance instance = getInstanceByName(node.name);
        MaudeStateInstance mState = parent.getMaudeStateVar((StateVariableInstance) instance);
        return mState.getName();
    }

    @Override
    public String visitStatementSequenceNode(StatementSequenceNode node) {
        String result = new String();
        for (int i = 0; i < node.children.size(); i++) {
            String temp = visit(node.children.get(i));
            if (temp == null)
                temp = "skip";
            result += temp;
            if (i != node.children.size() - 1) {
//                if (node.children.get(i) instanceof IfBlockNode)
//                result += "\n";
//                else
                    result += " ;\n";
                }
        }
        return result;
    }

    private NamedInstance getInstanceByName(String name) {
        for (NamedInstance i : this.instances) {
            if (i instanceof ActionInstance) {
                if (((ActionInstance) i).getDefinition().getName().equals(name)) {
                    return i;
                }
            } else if (i instanceof PortInstance) {
                if (((PortInstance) i).getDefinition().getName().equals(name)) {
                    return i;
                }
            } else if (i instanceof StateVariableInstance) {
                if (((StateVariableInstance) i).getDefinition().getName().equals(name)) {
                    return i;
                }
            }
        }
        throw new RuntimeException("NamedInstance not found!");
    }
}