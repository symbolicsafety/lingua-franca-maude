package org.lflang.analyses.maude;

import org.lflang.TimeUnit;
import org.lflang.ast.ASTUtils;
import org.lflang.lf.Expression;
import org.lflang.lf.Literal;
import org.lflang.lf.Time;

/** Converts supported LF connection-delay expressions to nanoseconds. */
final class MaudeConnectionDelay {

  private MaudeConnectionDelay() {}

  static long toNanoseconds(Expression delay) {
    if (delay == null) {
      return 0;
    }
    if (delay instanceof Literal literal && ASTUtils.isZero(literal.getLiteral())) {
      return 0;
    }
    if (delay instanceof Time time) {
      final TimeUnit unit;
      try {
        unit = TimeUnit.fromName(time.getUnit());
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException(
            "Unknown connection delay unit '" + time.getUnit() + "'.", exception);
      }
      return MaudeTime.toNanoseconds(time.getInterval(), unit, "Connection delay", true);
    }
    throw new IllegalArgumentException(
        "Maude only supports literal connection delays; parameterized and other delay "
            + "expressions are not yet supported.");
  }
}
