package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lflang.lf.LfFactory;

class MaudeConnectionDelayTest {

  private static final LfFactory FACTORY = LfFactory.eINSTANCE;

  @Test
  void normalizesLiteralDelaysAndAcceptsZero() {
    assertEquals(1_000_000L, MaudeConnectionDelay.toNanoseconds(time(1_000, "usec")));
    assertEquals(604_800_000_000_000L, MaudeConnectionDelay.toNanoseconds(time(1, "week")));

    var zero = FACTORY.createLiteral();
    zero.setLiteral("0");
    assertEquals(0L, MaudeConnectionDelay.toNanoseconds(zero));
  }

  @Test
  void rejectsParameterizedAndOtherNonliteralDelays() {
    var parameter = FACTORY.createParameter();
    parameter.setName("delay");
    var reference = FACTORY.createParameterReference();
    reference.setParameter(parameter);

    IllegalArgumentException parameterized =
        assertThrows(
            IllegalArgumentException.class, () -> MaudeConnectionDelay.toNanoseconds(reference));
    assertTrue(parameterized.getMessage().contains("parameterized"));

    var nonzeroWithoutUnit = FACTORY.createLiteral();
    nonzeroWithoutUnit.setLiteral("1");
    assertThrows(
        IllegalArgumentException.class,
        () -> MaudeConnectionDelay.toNanoseconds(nonzeroWithoutUnit));
  }

  @Test
  void rejectsNegativeAndOverflowingLiteralDelays() {
    assertThrows(
        IllegalArgumentException.class, () -> MaudeConnectionDelay.toNanoseconds(time(-1, "msec")));

    IllegalArgumentException overflow =
        assertThrows(
            IllegalArgumentException.class,
            () -> MaudeConnectionDelay.toNanoseconds(time(Integer.MAX_VALUE, "week")));
    assertTrue(overflow.getMessage().contains("overflows"));
  }

  private static org.lflang.lf.Time time(int interval, String unit) {
    var result = FACTORY.createTime();
    result.setInterval(interval);
    result.setUnit(unit);
    return result;
  }
}
