package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaudePhysicalActionValuesTest {

  @Test
  void acceptsValuesThatMatchTheActionType() {
    assertDoesNotThrow(
        () ->
            MaudePhysicalActionValues.requireCompatible(
                "true, FALSE", MaudeTypes.MaudeActionType.BActionId, "flag"));
    assertDoesNotThrow(
        () ->
            MaudePhysicalActionValues.requireCompatible(
                "0 .. 10", MaudeTypes.MaudeActionType.RActionId, "sample"));
  }

  @Test
  void leavesBackendValueSetSyntaxUnrestricted() {
    assertDoesNotThrow(
        () ->
            MaudePhysicalActionValues.requireCompatible(
                "backendValueSet", MaudeTypes.MaudeActionType.BActionId, "flag"));
    assertDoesNotThrow(
        () ->
            MaudePhysicalActionValues.requireCompatible(
                "backendValueSet", MaudeTypes.MaudeActionType.RActionId, "sample"));
  }

  @Test
  void rejectsNumericValuesForBooleanActions() {
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MaudePhysicalActionValues.requireCompatible(
                    "0 .. 1", MaudeTypes.MaudeActionType.BActionId, "flag"));

    assertTrue(exception.getMessage().contains("flag"));
    assertTrue(exception.getMessage().contains("bool"));
  }

  @Test
  void rejectsBooleanValuesForIntegerActions() {
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MaudePhysicalActionValues.requireCompatible(
                    "true, false", MaudeTypes.MaudeActionType.RActionId, "sample"));

    assertTrue(exception.getMessage().contains("sample"));
    assertTrue(exception.getMessage().contains("int"));
  }
}
