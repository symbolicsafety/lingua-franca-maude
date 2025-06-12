package org.lflang.analyses.maude;

import org.lflang.generator.StateVariableInstance;

public class MaudeStateInstance {
    private final StateVariableInstance lfStateVar;
    private final String name;
    private final MaudeTypes.MaudeVarType type;

    MaudeReactorInstance parent;

    public MaudeStateInstance(StateVariableInstance lfStateVar, MaudeReactorInstance parent) {
        this.lfStateVar = lfStateVar;
        this.parent = parent;
        this.name = parent.getName() + ".sv." + lfStateVar.getName().replaceAll("_","");

        if (lfStateVar.getDefinition().getType().getId() == "bool")
            this.type = MaudeTypes.MaudeVarType.BVarId;
        else if (lfStateVar.getDefinition().getType().getId() == "int")
            this.type = MaudeTypes.MaudeVarType.RVarId;
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
