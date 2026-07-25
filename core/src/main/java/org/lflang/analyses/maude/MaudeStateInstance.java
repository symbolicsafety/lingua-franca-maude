package org.lflang.analyses.maude;

import org.lflang.ast.ASTUtils;
import org.lflang.generator.StateVariableInstance;
import org.lflang.lf.Expression;
import org.lflang.lf.Literal;

public class MaudeStateInstance {
    private final StateVariableInstance lfStateVar;
    private String name;
    private final MaudeTypes.MaudeVarType type;
    public Object value;

    MaudeReactorInstance parent;

    public MaudeStateInstance(StateVariableInstance lfStateVar, MaudeReactorInstance parent) {
        this.lfStateVar = lfStateVar;
        this.parent = parent;
        this.name = MaudeIdentifiers.state(parent, lfStateVar);

        if (lfStateVar.getDefinition().getType().getId() == null) {
            this.type = MaudeTypes.MaudeVarType.RVarId;
            this.value = Long.valueOf(0);
        }
        else if (lfStateVar.getDefinition().getType().getId().equals("bool")) {
            this.type = MaudeTypes.MaudeVarType.BVarId;
            if (ASTUtils.isInitialized(lfStateVar.getDefinition())) {
                final Expression expr = lfStateVar.getDefinition().getInit().getExpr();
                this.value = Boolean.valueOf(((Literal) expr).getLiteral());
            }
            else
                this.value = Boolean.valueOf(true); // set a default value for this type, as actions are not initialized with a value
        }
        else if (lfStateVar.getDefinition().getType().getId().equals("int")) {
            this.type = MaudeTypes.MaudeVarType.RVarId;
            if (ASTUtils.isInitialized(lfStateVar.getDefinition())) {
                final Expression expr = lfStateVar.getDefinition().getInit().getExpr();
                this.value = Long.decode(((Literal) expr).getLiteral());
            }
            else
                this.value = Long.valueOf(0); // set a default payload for this type, as actions are not initialized with a value
        }
        else
            throw new RuntimeException("Maude only supports bool and int types for variables.");

    }

    public MaudeReactorInstance getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type.name();
    }

    public StateVariableInstance getLfStateVar() {
        return lfStateVar;
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
