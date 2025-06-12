package org.lflang.analyses.maude;

import org.lflang.generator.ReactionInstance;

public class MaudeReactionInstance {
    private ReactionInstance lfReaction;
    protected String name;

    MaudeReactorInstance parent;

    public MaudeReactionInstance(ReactionInstance lfReaction, MaudeReactorInstance parent) {
        this.lfReaction = lfReaction;
        this.parent = parent;
        this.name = parent.getName() + ".re." + lfReaction.getName().replaceAll("_","");



    }

}
