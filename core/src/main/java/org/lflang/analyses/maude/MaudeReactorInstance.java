package org.lflang.analyses.maude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lflang.generator.ActionInstance;
import org.lflang.generator.NamedInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TimerInstance;
import org.lflang.generator.TriggerInstance;

public class MaudeReactorInstance {
    public final ReactorInstance lfReactor;
    private final MaudeInstanceRegistry registry;
    private String name;
    public final List<MaudeActionInstance> logicalActions = new ArrayList<MaudeActionInstance>();
    public final List<MaudeActionInstance> physicalActions = new ArrayList<MaudeActionInstance>();
    public final List<MaudePortInstance> inPorts = new ArrayList<MaudePortInstance>();
    public final List<MaudePortInstance> outPorts = new ArrayList<MaudePortInstance>();
    public final List<MaudeTimerInstance> timers = new ArrayList<MaudeTimerInstance>();
    public final List<MaudeStateInstance> stateVars = new ArrayList<MaudeStateInstance>();
    public final List<MaudeTriggerInstance> triggers = new ArrayList<MaudeTriggerInstance>();
    public final List<MaudeReactionInstance> reactions = new ArrayList<MaudeReactionInstance>();
    private final Map<String, MaudeStateInstance> statesByLfName = new HashMap<>();
    private final Map<String, String> variableNames = new HashMap<>();
    private final Map<String, MaudeActionInstance> actionsByLfName = new HashMap<>();
    private final Map<String, MaudePortInstance> portsByLfName = new HashMap<>();
    private final Map<String, MaudeTriggerInstance> triggersByLfName = new HashMap<>();
    private boolean hasStartup = false;

    public boolean hasStartup() {
        return hasStartup;
    }

    private MaudeActionInstance startup;

    public MaudeReactorInstance(
        ReactorInstance lfReactor,
        MaudeInstanceRegistry registry
    ) {
        this.lfReactor = lfReactor;
        this.registry = registry;
        this.name = MaudeIdentifiers.reactor(lfReactor);
        this.registry.register(lfReactor, this);

        for (var action: lfReactor.actions) {
            if (action.isPhysical()) {
                var mAction = new MaudeActionInstance(action, this);
                this.physicalActions.add(mAction);
                registerAction(action, mAction);
                this.registry.register(action, mAction);
                this.triggers.add(registerTrigger(action, mAction));
            }
            else {
                var mAction = new MaudeActionInstance(action, this);
                this.logicalActions.add(mAction);
                registerAction(action, mAction);
                this.registry.register(action, mAction);
                this.triggers.add(registerTrigger(action, mAction));
            }
        }

        for (var timer : lfReactor.timers) {
            var mTimer = new MaudeTimerInstance(timer, this);
            this.timers.add(mTimer);
            registerVariable(timer.getName(), mTimer.getName());
            this.registry.register(timer, mTimer);
            this.triggers.add(registerTrigger(timer, mTimer));
        }

        for (var input : lfReactor.inputs) {
            var mInput = new MaudePortInstance(input, this);
            this.inPorts.add(mInput);
            registerPort(input.getName(), mInput);
            this.registry.register(input, mInput);
            this.triggers.add(registerTrigger(input, mInput));
        }

        for (var output : lfReactor.outputs) {
            var mOutput = new MaudePortInstance(output, this);
            this.outPorts.add(mOutput);
            registerPort(output.getName(), mOutput);
            this.registry.register(output, mOutput);
            registerTrigger(output, mOutput);
        }

        for (var state : lfReactor.states) {
            var mState = new MaudeStateInstance(state, this);
            this.stateVars.add(mState);
            registerState(state.getName(), mState);
            this.registry.register(state, mState);
        }

        for (var trigger : lfReactor.getTriggers())
            if (trigger.isStartup()) {
                this.startup = MaudeActionInstance.createStartupAction(this);
                this.logicalActions.add(startup);
                this.triggers.add(registerTrigger(trigger, startup));
                this.hasStartup = true;
            }

        for (var reaction: lfReactor.reactions) {
            var mReaction = new MaudeReactionInstance(reaction, this);
            this.reactions.add(mReaction);
            this.registry.register(reaction, mReaction);
        }
    }

    String requireMemberName(String lfName) {
        var state = statesByLfName.get(lfName);
        String result = state == null ? null : state.getName();
        if (result == null) {
            result = variableNames.get(lfName);
        }
        return requireName(result, "component", lfName);
    }

    String requireTriggerName(String lfName) {
        return requireTrigger(lfName).getName();
    }

    public MaudeStateInstance requireState(String lfName) {
        return require(statesByLfName, "state variable", lfName);
    }

    public MaudePortInstance requirePort(String lfName) {
        return require(portsByLfName, "port", lfName);
    }

    public MaudeActionInstance requireAction(String lfName) {
        return require(actionsByLfName, "action", lfName);
    }

    public MaudeTriggerInstance requireTrigger(String lfName) {
        return require(triggersByLfName, "trigger", lfName);
    }

    MaudeActionInstance requirePhysicalAction(String lfName) {
        var result = requireAction(lfName);
        if (!result.getLfAction().isPhysical()) {
            throw new IllegalArgumentException(
                "LF action '" + lfName + "' in reactor '" + lfReactor.getFullName()
                    + "' is not physical");
        }
        return result;
    }

    private void registerAction(ActionInstance lfAction, MaudeActionInstance maudeAction) {
        registerVariable(lfAction.getName(), maudeAction.getName());
        registerMember(actionsByLfName, lfAction.getName(), maudeAction);
    }

    private void registerState(String lfName, MaudeStateInstance maudeState) {
        registerMember(statesByLfName, lfName, maudeState);
    }

    private void registerPort(String lfName, MaudePortInstance maudePort) {
        registerVariable(lfName, maudePort.getName());
        registerMember(portsByLfName, lfName, maudePort);
    }

    private void registerVariable(String lfName, String maudeName) {
        registerMember(variableNames, lfName, maudeName);
    }

    private <T> void registerMember(Map<String, T> index, String lfName, T maudeInstance) {
        var previous = index.putIfAbsent(lfName, maudeInstance);
        if (previous != null) {
            throw duplicateMember(lfName);
        }
    }

    private IllegalStateException duplicateMember(String lfName) {
        return new IllegalStateException(
            "Multiple LF components named '" + lfName + "' exist in reactor '"
                + lfReactor.getFullName() + "'");
    }

    private <T> T require(Map<String, T> index, String description, String lfName) {
        var result = index.get(lfName);
        if (result == null) {
            throw new IllegalArgumentException(
                "No LF " + description + " named '" + lfName + "' exists in reactor '"
                    + lfReactor.getFullName() + "'");
        }
        return result;
    }

    private String requireName(String result, String description, String lfName) {
        if (result == null) {
            throw new IllegalArgumentException(
                "No LF " + description + " named '" + lfName + "' exists in reactor '"
                    + lfReactor.getFullName() + "'");
        }
        return result;
    }

    private MaudeTriggerInstance registerTrigger(
        TriggerInstance<?> lfTrigger,
        Object maudeTrigger
    ) {
        var result = new MaudeTriggerInstance(lfTrigger, maudeTrigger);
        this.registry.register(lfTrigger, result);
        if (!lfTrigger.isStartup() && !lfTrigger.isShutdown() && !lfTrigger.isReset()) {
            registerMember(triggersByLfName, lfTrigger.getName(), result);
        }
        return result;
    }

    private boolean isLocal(NamedInstance<?> lfInstance) {
        return lfInstance != null && lfInstance.getParent() == this.lfReactor;
    }

    //return maude physical action corresponding to lf physical action
    public MaudeActionInstance getMaudeAction(ActionInstance lfAction) {
        if (!isLocal(lfAction))
            return null;
        return this.registry.get(lfAction);
    }



    //return maude logical action corresponding to lf logical action
    public MaudeActionInstance getMaudeLogicalAction(ActionInstance lfAction) {
        var action = getMaudeAction(lfAction);
        return action != null && !lfAction.isPhysical() ? action : null;
    }

    //return maude physical action corresponding to lf physical action
    public MaudeActionInstance getMaudePhysicalAction(ActionInstance lfAction) {
        var action = getMaudeAction(lfAction);
        return action != null && lfAction.isPhysical() ? action : null;
    }

    //return maude timer corresponding to lf timer
    public MaudeTimerInstance getMaudeTimer(TimerInstance lfTimer) {
        if (!isLocal(lfTimer))
            return null;
        return this.registry.get(lfTimer);
    }

    //return maude port corresponding to lf port
    public MaudePortInstance getMaudePort(PortInstance lfPort) {
        if (!isLocal(lfPort))
            return null;
        return this.registry.get(lfPort);
    }

    //return maude state var corresponding to lf state var
    public MaudeStateInstance getMaudeStateVar(StateVariableInstance lfStateVar) {
        if (!isLocal(lfStateVar))
            return null;
        return this.registry.get(lfStateVar);
    }

    //return maude reaction corresponding to lf reaction
    public MaudeReactionInstance getMaudeReaction(ReactionInstance lfReaction) {
        if (!isLocal(lfReaction))
            return null;
        return this.registry.get(lfReaction);
    }

    //return maude trigger corresponding to lf trigger
    public MaudeTriggerInstance getMaudeTrigger(TriggerInstance<?> lfTrigger) {
        if (!isLocal(lfTrigger))
            return null;
        return this.registry.get(lfTrigger);
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
