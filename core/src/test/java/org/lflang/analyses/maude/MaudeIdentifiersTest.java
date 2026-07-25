package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MaudeIdentifiersTest {

  @Test
  void preservesUnderscoresWithoutIntroducingCollisions() {
    assertEquals("aZub", MaudeIdentifiers.encodeIdentifier("a_b"));
    assertEquals("ab", MaudeIdentifiers.encodeIdentifier("ab"));
    assertEquals("aZZub", MaudeIdentifiers.encodeIdentifier("aZub"));

    assertNotEquals(
        MaudeIdentifiers.encodeIdentifier("a_b"), MaudeIdentifiers.encodeIdentifier("ab"));
    assertNotEquals(
        MaudeIdentifiers.encodeIdentifier("a_b"), MaudeIdentifiers.encodeIdentifier("aZub"));
  }

  @Test
  void qualifiesReactorNamesByHierarchy() {
    assertEquals(
        "Main.left.workerZuone",
        MaudeIdentifiers.reactorPath("Main.left.worker_one"));
    assertNotEquals(
        MaudeIdentifiers.reactorPath("Main.left.worker"),
        MaudeIdentifiers.reactorPath("Main.worker"));
  }

  @Test
  void encodesNonAlphanumericCharacters() {
    assertEquals("nameZx002d1", MaudeIdentifiers.encodeIdentifier("name-1"));
  }
}
