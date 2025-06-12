package org.lflang.analyses.maude;

import org.lflang.generator.TimerInstance;

public class MaudeTimerInstance {

    private TimerInstance lfTimer;
    protected String name;
    private final long offset;
    private final long period;

    MaudeReactorInstance parent;

    public MaudeTimerInstance(TimerInstance lfTimer, MaudeReactorInstance parent) {
        this.lfTimer = lfTimer;
        this.parent = parent;

        this.name = parent.getName() + ".t." + lfTimer.getName().replaceAll("_", "");

        this.offset = lfTimer.getOffset().toNanoSeconds() / 1_000_000_000;
        this.period = lfTimer.getPeriod().toNanoSeconds() / 1_000_000_000;

        if (this.offset < 0 || this.period < 0)
            throw new RuntimeException("Offset and Period must be non-negative");
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

    @Override
    public String toString() {
        return this.getName();
    }
}
