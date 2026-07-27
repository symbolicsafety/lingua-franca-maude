package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaudeRemainingTimeTest {

  private final MaudeInstanceRegistry registry = new MaudeInstanceRegistry();

  @Test
  void defaultsToNanosecondsAndAcceptsZero() {
    assertEquals("(remainingTime 1)", translate("remainingTime 1"));
    assertEquals("(remainingTime 0)", translate("remainingTime 0"));
  }

  @Test
  void convertsExplicitLfTimeUnits() {
    assertEquals("(remainingTime 1000000)", translate("remainingTime 1 msec"));
    assertEquals("(remainingTime 1000)", translate("remainingTime 1 usec"));
    assertEquals("(remainingTime 0)", translate("remainingTime 0 sec"));
  }

  @Test
  void rejectsUnknownUnits() {
    var exception =
        assertThrows(IllegalArgumentException.class, () -> translate("remainingTime 1 fortnight"));

    assertTrue(exception.getMessage().contains("fortnight"));
  }

  private String translate(String property) {
    return MaudePropertyParser.translate(property, registry);
  }
}
