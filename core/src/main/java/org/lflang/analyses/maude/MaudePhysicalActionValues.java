package org.lflang.analyses.maude;

import java.util.regex.Pattern;

/** Checks that physical-action values match the action payload type. */
final class MaudePhysicalActionValues {

  private static final Pattern BOOLEAN_LITERAL =
      Pattern.compile("\\b(?:true|false)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMERIC_LITERAL =
      Pattern.compile("(?<![A-Za-z0-9_])[+-]?\\d+(?![A-Za-z0-9_])");

  private MaudePhysicalActionValues() {}

  static void requireCompatible(
      String values, MaudeTypes.MaudeActionType actionType, String actionName) {
    if (actionType == MaudeTypes.MaudeActionType.BActionId
        && NUMERIC_LITERAL.matcher(values).find()) {
      throw new IllegalArgumentException(
          "Physical action '" + actionName + "' has type bool, but 'vals' contains a number.");
    }
    if (actionType == MaudeTypes.MaudeActionType.RActionId
        && BOOLEAN_LITERAL.matcher(values).find()) {
      throw new IllegalArgumentException(
          "Physical action '" + actionName + "' has type int, but 'vals' contains a Boolean.");
    }
  }
}
