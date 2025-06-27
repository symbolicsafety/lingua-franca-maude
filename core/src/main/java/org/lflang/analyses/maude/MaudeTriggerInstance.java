package org.lflang.analyses.maude;

import org.lflang.generator.TriggerInstance;

public class MaudeTriggerInstance {
    private final TriggerInstance lfTriggerInstance;
    private final Object maudeTrigger;
    public final MaudeReactorInstance parent;
    private String name;


    public MaudeTriggerInstance(TriggerInstance lfTriggerInstance, Object maudeTrigger) {

        this.lfTriggerInstance = lfTriggerInstance;
        this.maudeTrigger = maudeTrigger;

        if (maudeTrigger instanceof MaudeTimerInstance) {
            this.name = ((MaudeTimerInstance) maudeTrigger).getName();
            this.parent = ((MaudeTimerInstance) maudeTrigger).getParent();
        }
        else if (maudeTrigger instanceof  MaudeActionInstance) {
            this.name = ((MaudeActionInstance) maudeTrigger).getName();
            this.parent = ((MaudeActionInstance) maudeTrigger).getParent();
        }
        else if (maudeTrigger instanceof  MaudePortInstance) {
            this.name = ((MaudePortInstance) maudeTrigger).getName();
            this.parent = ((MaudePortInstance) maudeTrigger).getParent();
        }
        else
            throw new RuntimeException("Only ports, timers and actions can be triggers in Maude.");


    }

    public Object getMaudeTrigger() {
        return maudeTrigger;
    }

    public TriggerInstance getLfTrigger() {
        return lfTriggerInstance;
    }

    public String getName() {
        return name;
    }

    public MaudeReactorInstance getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return this.getName();
    }
}