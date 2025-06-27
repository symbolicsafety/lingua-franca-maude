package org.lflang.analyses.maude;

import org.lflang.generator.TimerInstance;

public class MaudeTimerInstance {

    private final TimerInstance lfTimer;
    private String name;
    private final long offset;
    private final long period;

    MaudeReactorInstance parent;

    public MaudeTimerInstance(TimerInstance lfTimer, MaudeReactorInstance parent) {
        this.lfTimer = lfTimer;
        this.parent = parent;

        this.name = parent.getName() + ".t." + lfTimer.getName().replaceAll("_", "");

        // TODO: Add explicit time units to Maude
        // Our Maude implementation does not use time units.
        // Using nanoseconds (the most granular LF time unit) everywhere is correct and trivial,
        // but will make reading Maude's output contain large numbers whenever seconds/milliseconds are used
        this.offset = lfTimer.getOffset().toNanoSeconds();
        this.period = lfTimer.getPeriod().toNanoSeconds();



        if (this.offset < 0 || this.period <= 0)
            throw new RuntimeException("Offset and Period must be non-negative. Period cannot be 0.");
    }

    public MaudeReactorInstance getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public long getOffset() {
        return offset;
    }

    public long getPeriod() {
        return period;
    }

    public TimerInstance getLfTimer() {
        return lfTimer;
    }

    @Override
    public String toString() {
        return this.getName();
    }
}