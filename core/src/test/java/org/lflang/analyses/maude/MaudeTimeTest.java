package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lflang.TimeUnit;

class MaudeTimeTest {

  @Test
  void defaultsToMilliseconds() {
    assertEquals(2_000_000L, MaudeTime.toNanoseconds("2", null, "timeBound"));
  }

  @Test
  void acceptsEveryLfTimeUnitAlias() {
    Map<Long, String[]> aliases =
        Map.of(
            1L, new String[] {"nsec", "ns", "nsecs"},
            1_000L, new String[] {"usec", "us", "usecs"},
            1_000_000L, new String[] {"msec", "ms", "msecs"},
            1_000_000_000L, new String[] {"sec", "s", "secs", "second", "seconds"},
            60_000_000_000L, new String[] {"minute", "min", "mins", "minutes"},
            3_600_000_000_000L, new String[] {"hour", "h", "hours"},
            86_400_000_000_000L, new String[] {"day", "d", "days"},
            604_800_000_000_000L, new String[] {"week", "wk", "weeks"});

    aliases.forEach(
        (expected, names) -> {
          for (String name : names) {
            assertEquals(expected, MaudeTime.toNanoseconds("1", name, "period"), name);
          }
        });
  }

  @Test
  void rejectsNonpositiveValues() {
    assertThrows(
        IllegalArgumentException.class, () -> MaudeTime.toNanoseconds("0", "msec", "period"));
    assertThrows(
        IllegalArgumentException.class, () -> MaudeTime.toNanoseconds("-1", "msec", "period"));
  }

  @Test
  void acceptsZeroWhenAllowed() {
    assertEquals(0L, MaudeTime.toNanoseconds("0", null, "timeBound", TimeUnit.MILLI, true));
    assertThrows(
        IllegalArgumentException.class,
        () -> MaudeTime.toNanoseconds("-1", null, "timeBound", TimeUnit.MILLI, true));
  }

  @Test
  void rejectsUnknownUnits() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> MaudeTime.toNanoseconds("1", "fortnight", "timeBound"));

    assertTrue(exception.getMessage().contains("fortnight"));
  }

  @Test
  void rejectsNanosecondOverflow() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> MaudeTime.toNanoseconds(Long.toString(Long.MAX_VALUE), "sec", "timeBound"));

    assertTrue(exception.getMessage().contains("overflows"));
  }
}
