package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lflang.DefaultMessageReporter;
import org.lflang.generator.ReactorInstance;
import org.lflang.lf.ActionOrigin;
import org.lflang.lf.LfFactory;
import org.lflang.lf.Reactor;

class MaudeActionTimerTimingTest {

  private static final LfFactory FACTORY = LfFactory.eINSTANCE;

  @Test
  void normalizesActionAndTimerTimesAndPreservesPolicy() {
    var reactor = reactor("Timing");

    var action = FACTORY.createAction();
    action.setName("step");
    action.setOrigin(ActionOrigin.LOGICAL);
    action.setMinDelay(time(1, "week"));
    action.setMinSpacing(time(2, "msec"));
    action.setPolicy("update");
    reactor.getActions().add(action);

    var timer = FACTORY.createTimer();
    timer.setName("tick");
    timer.setOffset(time(3, "usec"));
    timer.setPeriod(time(4, "sec"));
    reactor.getTimers().add(timer);

    var maudeReactor = wrap(reactor);
    var maudeAction = maudeReactor.logicalActions.get(0);
    var maudeTimer = maudeReactor.timers.get(0);

    assertEquals(604_800_000_000_000L, maudeAction.minDelay);
    assertEquals(2_000_000L, maudeAction.minSpacing);
    assertEquals("update", maudeAction.policy);
    assertEquals(3_000L, maudeTimer.getOffset());
    assertEquals(4_000_000_000L, maudeTimer.getPeriod());
  }

  @Test
  void rejectsActionTimeOverflow() {
    var reactor = reactor("ActionOverflow");
    var action = FACTORY.createAction();
    action.setName("step");
    action.setOrigin(ActionOrigin.LOGICAL);
    action.setMinDelay(time(Integer.MAX_VALUE, "week"));
    reactor.getActions().add(action);

    var exception = assertThrows(IllegalArgumentException.class, () -> wrap(reactor));
    assertTrue(exception.getMessage().contains("Action minimum delay"));
    assertTrue(exception.getMessage().contains("overflows"));
  }

  @Test
  void rejectsTimerTimeOverflow() {
    var reactor = reactor("TimerOverflow");
    var timer = FACTORY.createTimer();
    timer.setName("tick");
    timer.setPeriod(time(Integer.MAX_VALUE, "week"));
    reactor.getTimers().add(timer);

    var exception = assertThrows(IllegalArgumentException.class, () -> wrap(reactor));
    assertTrue(exception.getMessage().contains("Timer period"));
    assertTrue(exception.getMessage().contains("overflows"));
  }

  private static MaudeReactorInstance wrap(Reactor reactor) {
    var lfReactor = new ReactorInstance(reactor, new DefaultMessageReporter());
    return new MaudeReactorInstance(lfReactor, new MaudeInstanceRegistry());
  }

  private static Reactor reactor(String name) {
    var result = FACTORY.createReactor();
    result.setName(name);
    return result;
  }

  private static org.lflang.lf.Time time(int interval, String unit) {
    var result = FACTORY.createTime();
    result.setInterval(interval);
    result.setUnit(unit);
    return result;
  }
}
