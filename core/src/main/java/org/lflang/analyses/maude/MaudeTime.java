package org.lflang.analyses.maude;

import org.lflang.TimeUnit;

/** Converts time-valued Maude inputs to nanoseconds. */
final class MaudeTime {

  private MaudeTime() {}

  /**
   * Convert a positive annotation parameter to nanoseconds.
   *
   * @param value The integer magnitude.
   * @param unit An LF time-unit alias, or {@code null} to use milliseconds.
   * @param parameterName The annotation parameter name, used in diagnostics.
   */
  static long toNanoseconds(String value, String unit, String parameterName) {
    return toNanoseconds(value, unit, parameterName, TimeUnit.MILLI, false);
  }

  /**
   * Convert a string-valued time to nanoseconds.
   *
   * @param value The integer magnitude.
   * @param unit An LF time-unit alias, or {@code null} to use {@code defaultUnit}.
   * @param parameterName The value name, used in diagnostics.
   * @param defaultUnit The unit to use when {@code unit} is omitted.
   * @param allowZero Whether zero is valid.
   */
  static long toNanoseconds(
      String value, String unit, String parameterName, TimeUnit defaultUnit, boolean allowZero) {
    final long magnitude;
    try {
      magnitude = Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          parameterName + " must be an integer, but was '" + value + "'.", exception);
    }

    final TimeUnit timeUnit;
    if (unit == null) {
      timeUnit = defaultUnit;
    } else {
      try {
        timeUnit = TimeUnit.fromName(unit);
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException(
            "Unknown "
                + parameterName
                + " unit '"
                + unit
                + "'. Expected one of: "
                + String.join(", ", TimeUnit.list())
                + ".",
            exception);
      }
    }

    return toNanoseconds(magnitude, timeUnit, parameterName, allowZero);
  }

  /**
   * Convert an LF time magnitude to nanoseconds.
   *
   * @param magnitude The time magnitude.
   * @param timeUnit The LF time unit.
   * @param parameterName The value name, used in diagnostics.
   * @param allowZero Whether zero is valid.
   */
  static long toNanoseconds(
      long magnitude, TimeUnit timeUnit, String parameterName, boolean allowZero) {
    if (magnitude < 0 || (!allowZero && magnitude == 0)) {
      throw new IllegalArgumentException(
          parameterName
              + (allowZero ? " must be greater than or equal to 0." : " must be greater than 0."));
    }
    if (magnitude == 0) {
      return 0;
    }
    if (timeUnit == null) {
      throw new IllegalArgumentException(parameterName + " must have a time unit.");
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
          parameterName
              + " overflows nanoseconds: "
              + magnitude
              + " "
              + timeUnit.getCanonicalName()
              + ".",
          exception);
    }
  }
}
