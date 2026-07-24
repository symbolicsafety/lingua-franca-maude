package org.lflang.analyses.maude;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.ActionContributionItem;

import org.lflang.generator.ActionInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance;
import org.lflang.generator.TriggerInstance;


public class MaudeReactionInstance {
    private final ReactionInstance lfReaction;
    private String name;
    private List<MaudeTriggerInstance> triggers = new ArrayList<MaudeTriggerInstance>();
    private List<MaudeTriggerInstance> effects = new ArrayList<MaudeTriggerInstance>();

    MaudeReactorInstance parent;

    public MaudeReactionInstance(ReactionInstance lfReaction, MaudeReactorInstance parent) {
        this.lfReaction = lfReaction;
        this.parent = parent;
        this.name = parent.getName() + ".re." + lfReaction.getName().replaceAll("_","");
        for (var trigger : lfReaction.triggers)
            this.triggers.add(resolveTrigger(trigger, "trigger"));

        for (var effect : lfReaction.effects) {
            if (!(effect instanceof PortInstance) && !(effect instanceof ActionInstance))
               throw new RuntimeException("Maude only supports actions and ports as reaction effects.");

            this.effects.add(resolveTrigger(effect, "effect"));
        }
    }

    private MaudeTriggerInstance resolveTrigger(
        TriggerInstance<?> lfTrigger,
        String role
    ) {
        var result = parent.getMaudeTrigger(lfTrigger);
        if (result == null)
            throw new IllegalStateException(
                "Could not resolve Maude " + role + " for " + lfTrigger.getFullName());
        return result;
    }

    public String getName() {
        return name;
    }

    public List<MaudeTriggerInstance> getEffects() {
        return effects;
    }
    public List<MaudeTriggerInstance> getTriggers() {
        return triggers;
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public ReactionInstance getLfReaction() {
        return lfReaction;
    }

    public MaudeReactorInstance getParent() {
        return parent;
    }
}
