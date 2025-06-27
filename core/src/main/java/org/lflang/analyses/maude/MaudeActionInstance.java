package org.lflang.analyses.maude;

import org.lflang.generator.ActionInstance;

public class MaudeActionInstance {
    public final ActionInstance lfAction;
    private final String name;
    public final MaudeTypes.MaudeActionType type;
    public final long minDelay;
    public final long minSpacing;
    public final String policy;
    public Object payload;

    MaudeReactorInstance parent;

    public MaudeActionInstance(ActionInstance lfAction, MaudeReactorInstance parent) {
        this.lfAction = lfAction;
        this.parent = parent;
        if (lfAction.isPhysical())
            this.name = parent.getName() + ".pa." + lfAction.getName().replaceAll("_","");
        else
            this.name = parent.getName() + ".la." + lfAction.getName().replaceAll("_","");

        this.minDelay = lfAction.getMinDelay().toNanoSeconds();
        if (lfAction.getMinSpacing() != null)
            this.minSpacing = lfAction.getMinSpacing().toNanoSeconds();
        else {
            this.minSpacing = 0;
        }

        //FIXME: for now we only support defer policy
        this.policy = "defer";

        if (lfAction.getDefinition().getType() != null) {
            if (lfAction.getDefinition().getType().getId().equals("bool")) {
                this.type = MaudeTypes.MaudeActionType.BActionId;
                this.payload = Boolean.valueOf(true); // set a default payload for this type, as actions are not initialized with a value
            } else if (lfAction.getDefinition().getType().getId().equals("int")) {
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

    // special constructor for startup reaction, which we declare as logical action in Maude
    private MaudeActionInstance(MaudeReactorInstance parent) {
        this.lfAction = null;
        this.parent = parent;
        this.name = parent.getName() + ".startup";
        this.minDelay = 0;
        this.minSpacing = 0;
        this.policy = "defer";
        this.type = MaudeTypes.MaudeActionType.RActionId;
        this.payload = Long.valueOf(0);
    }

    public static MaudeActionInstance createStartupAction(MaudeReactorInstance parent) {
        MaudeActionInstance startup = new MaudeActionInstance(parent);
        return startup;
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
