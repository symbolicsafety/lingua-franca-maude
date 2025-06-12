package org.lflang.analyses.maude;

import java.util.ArrayList;
import java.util.List;

import org.lflang.generator.ReactorInstance;

public class MaudeReactorInstance {
    private ReactorInstance lfReactor;
    private String name;
    private final List<MaudeActionInstance> logicalActions = new ArrayList<MaudeActionInstance>();
    private final List<MaudeActionInstance> physicalActions = new ArrayList<MaudeActionInstance>();
    private final List<MaudePortInstance> inPorts = new ArrayList<MaudePortInstance>();
    private final List<MaudePortInstance> outPorts = new ArrayList<MaudePortInstance>();
    private final List<MaudeTimerInstance> timers = new ArrayList<MaudeTimerInstance>();
    private final List<MaudeStateInstance> stateVars = new ArrayList<MaudeStateInstance>();

    public MaudeReactorInstance(ReactorInstance lfReactor) {
        this.lfReactor = lfReactor;
        this.name = lfReactor.getName().replaceAll("_","");

        for (var action: lfReactor.actions) {
            if (action.isPhysical())
                this.physicalActions.add(new MaudeActionInstance(action, this));
            else
                this.logicalActions.add(new MaudeActionInstance(action, this));
        }

        for (var timer : lfReactor.timers) {
            this.timers.add(new MaudeTimerInstance(timer, this));
        }

        for (var input : lfReactor.inputs) {
            this.inPorts.add(new MaudePortInstance(input, this));
        }

        for (var output : lfReactor.outputs) {
            this.outPorts.add(new MaudePortInstance(output, this));
        }

        for (var state : lfReactor.states) {
            this.stateVars.add(new MaudeStateInstance(state, this));
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
