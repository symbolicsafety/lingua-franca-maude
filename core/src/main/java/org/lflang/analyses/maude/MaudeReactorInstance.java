package org.lflang.analyses.maude;

import java.util.ArrayList;
import java.util.List;

import org.lflang.generator.ActionInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TimerInstance;
import org.lflang.generator.TriggerInstance;

public class MaudeReactorInstance {
    public final ReactorInstance lfReactor;
    private String name;
    public final List<MaudeActionInstance> logicalActions = new ArrayList<MaudeActionInstance>();
    public final List<MaudeActionInstance> physicalActions = new ArrayList<MaudeActionInstance>();
    public final List<MaudePortInstance> inPorts = new ArrayList<MaudePortInstance>();
    public final List<MaudePortInstance> outPorts = new ArrayList<MaudePortInstance>();
    public final List<MaudeTimerInstance> timers = new ArrayList<MaudeTimerInstance>();
    public final List<MaudeStateInstance> stateVars = new ArrayList<MaudeStateInstance>();
    public final List<MaudeTriggerInstance> triggers = new ArrayList<MaudeTriggerInstance>();
    public final List<MaudeReactionInstance> reactions = new ArrayList<MaudeReactionInstance>();
    private boolean hasStartup = false;

    public boolean hasStartup() {
        return hasStartup;
    }

    private MaudeActionInstance startup;

    //TODO: '_' is a Maude special character, so we delete it in all names that we encounter
    // add code to check for name collisions and use an incrementing suffix to resolve
    public MaudeReactorInstance(ReactorInstance lfReactor) {
        this.lfReactor = lfReactor;
        this.name = lfReactor.getName().replaceAll("_","");

        for (var action: lfReactor.actions) {
            if (action.isPhysical()) {
                var mAction = new MaudeActionInstance(action, this);
                this.physicalActions.add(mAction);
                this.triggers.add(new MaudeTriggerInstance(action, mAction));
            }
            else {
                var mAction = new MaudeActionInstance(action, this);
                this.logicalActions.add(mAction);
                this.triggers.add(new MaudeTriggerInstance(action, mAction));
            }
        }

        for (var timer : lfReactor.timers) {
            var mTimer = new MaudeTimerInstance(timer, this);
            this.timers.add(mTimer);
            this.triggers.add(new MaudeTriggerInstance(timer, mTimer));
        }

        for (var input : lfReactor.inputs) {
            var mInput = new MaudePortInstance(input, this);
            this.inPorts.add(mInput);
            this.triggers.add(new MaudeTriggerInstance(input, mInput));
        }

        //TODO: when adding support for nested reactors, add output ports of contained reactors
        // to trigger list
        for (var output : lfReactor.outputs) {
            var mOutput = new MaudePortInstance(output, this);
            this.outPorts.add(mOutput);
        }

        for (var state : lfReactor.states) {
            var mState = new MaudeStateInstance(state, this);
            this.stateVars.add(mState);
        }

        for (var trigger : lfReactor.getTriggers())
            if (trigger.isStartup()) {
                this.startup = MaudeActionInstance.createStartupAction(this);
                this.logicalActions.add(startup);
                this.triggers.add(new MaudeTriggerInstance(trigger, startup));
                this.hasStartup = true;
            }

        for (var reaction: lfReactor.reactions) {
            var mReaction = new MaudeReactionInstance(reaction, this);
            this.reactions.add(mReaction);
        }
    }

    //TODO: replace linear scanning with hash table lookup for all the getMaude* methods

    //return maude physical action corresponding to lf physical action
    public MaudeActionInstance getMaudeAction(ActionInstance lfAction) {
        if (lfAction.isPhysical())
            return getMaudePhysicalAction(lfAction);
        else
            return getMaudeLogicalAction(lfAction);
    }



    //return maude logical action corresponding to lf logical action
    public MaudeActionInstance getMaudeLogicalAction(ActionInstance lfAction) {
        for (var action : logicalActions)
            if (action.getLfAction() == lfAction)
                return action;

        return null;
    }

    //return maude physical action corresponding to lf physical action
    public MaudeActionInstance getMaudePhysicalAction(ActionInstance lfAction) {
        for (var action : physicalActions)
            if (action.getLfAction() == lfAction)
                return action;

        return null;
    }

    //return maude timer corresponding to lf timer
    public MaudeTimerInstance getMaudeTimer(TimerInstance lfTimer) {
        for (var timer : timers)
            if (timer.getLfTimer() == lfTimer)
                return timer;

        return null;
    }

    //return maude port corresponding to lf port
    public MaudePortInstance getMaudePort(PortInstance lfPort) {
        for (var port : inPorts)
            if (port.getLfPort() == lfPort)
                return port;

        for (var port : outPorts)
            if (port.getLfPort() == lfPort)
                return port;

        return null;
    }

    //return maude state var corresponding to lf state var
    public MaudeStateInstance getMaudeStateVar(StateVariableInstance lfStateVar) {
        for (var sv : stateVars)
            if (sv.getLfStateVar() == lfStateVar)
                return sv;

        return null;
    }

    //return maude reaction corresponding to lf reaction
    public MaudeReactionInstance getMaudeReaction(ReactionInstance lfReaction) {
        for (var reaction : reactions)
            if (reaction.getLfReaction() == lfReaction)
                return reaction;

        return null;
    }

    //return maude trigger corresponding to lf trigger
    public MaudeTriggerInstance getMaudeTrigger(TriggerInstance lfTrigger) {
        for (var trigger : triggers)
            if (trigger.getLfTrigger() == lfTrigger)
                return trigger;

        return null;
    }



    public String getName() {
        return name;
    }

    public boolean isMainOrFederated() {
        return lfReactor.isMainOrFederated();
    }

    @Override
    public String toString() {
        return getName();
    }
}
