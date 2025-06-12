package org.lflang.analyses.maude;

import org.lflang.generator.ActionInstance;

public class MaudeActionInstance {
    private final ActionInstance lfAction;
    private final String name;
    private final MaudeTypes.MaudeActionType type;
    private final long minDelay;
    private final long minSpacing;
    private final String policy;
    private Object payload;

    MaudeReactorInstance parent;

    public MaudeActionInstance(ActionInstance lfAction, MaudeReactorInstance parent) {
        this.lfAction = lfAction;
        this.parent = parent;
        if (lfAction.isPhysical())
            this.name = parent.getName() + ".pa." + lfAction.getName().replaceAll("_","");
        else
            this.name = lfAction.getName() + ".la." + lfAction.getName().replaceAll("_","");

        this.minDelay = lfAction.getMinDelay().toNanoSeconds() / 1_000_000_000;
        this.minSpacing = lfAction.getMinSpacing().toNanoSeconds() / 1_000_000_000;

        //FIXME: for now we only support defer policy
        this.policy = "defer";

        if (lfAction.getDefinition().getType() != null) {
            if (lfAction.getDefinition().getType().getId() == "bool") {
                this.type = MaudeTypes.MaudeActionType.BActionId;
                this.payload = Boolean.valueOf(true); // set a default payload for this type, as actions are not initialized with a value
            } else if (lfAction.getDefinition().getType().getId() == "int") {
                this.type = MaudeTypes.MaudeActionType.RActionId;
                this.payload = Integer.valueOf(0); // set a default value.
            }
            else
                throw new RuntimeException("Maude only supports bool and int types for action payload types.");
        }
        else {
            this.type = MaudeTypes.MaudeActionType.RActionId;
            this.payload = Long.valueOf(0); // set a default value
        }


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

    public ActionInstance getLfAction() {
        return lfAction;
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
