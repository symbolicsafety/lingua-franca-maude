package org.lflang.analyses.maude;

import org.lflang.TimeUnit;

/** Converts time-valued Maude annotation parameters to nanoseconds. */
final class MaudeTime {

  private static final String DEFAULT_UNIT = "msec";

  private MaudeTime() {}

  /**
   * Convert a positive annotation parameter to nanoseconds.
   *
   * @param value The integer magnitude.
   * @param unit An LF time-unit alias, or {@code null} to use milliseconds.
   * @param parameterName The annotation parameter name, used in diagnostics.
   */
  static long toNanoseconds(String value, String unit, String parameterName) {
    final long magnitude;
    try {
      magnitude = Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          parameterName + " must be an integer, but was '" + value + "'.", exception);
    }

    if (magnitude <= 0) {
      throw new IllegalArgumentException(parameterName + " must be greater than 0.");
    }

    String effectiveUnit = unit == null ? DEFAULT_UNIT : unit;
    final TimeUnit timeUnit;
    try {
      timeUnit = TimeUnit.fromName(effectiveUnit);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Unknown "
              + parameterName
              + " unit '"
              + effectiveUnit
              + "'. Expected one of: "
              + String.join(", ", TimeUnit.list())
              + ".",
          exception);
    }

    long nanosecondsPerUnit =
        switch (timeUnit) {
          case NANO -> 1L;
          case MICRO -> 1_000L;
          case MILLI -> 1_000_000L;
          case SECOND -> 1_000_000_000L;
          case MINUTE -> 60_000_000_000L;
          case HOUR -> 3_600_000_000_000L;
          case DAY -> 86_400_000_000_000L;
          case WEEK -> 604_800_000_000_000L;
        };

    try {
      return Math.multiplyExact(magnitude, nanosecondsPerUnit);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          parameterName + " overflows nanoseconds: " + value + " " + effectiveUnit + ".",
          exception);
    }
  }
}
