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

    this.name = MaudeIdentifiers.timer(parent, lfTimer);

    this.offset =
        MaudeTime.toNanoseconds(
            lfTimer.getOffset().getMagnitude(),
            lfTimer.getOffset().getUnit(),
            "Timer offset",
            true);
    this.period =
        MaudeTime.toNanoseconds(
            lfTimer.getPeriod().getMagnitude(),
            lfTimer.getPeriod().getUnit(),
            "Timer period",
            true);
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
