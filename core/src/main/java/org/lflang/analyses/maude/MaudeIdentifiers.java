package org.lflang.analyses.maude;

import java.util.Objects;
import org.lflang.generator.ActionInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TimerInstance;

/**
 * Generates collision-free Maude identifiers for LF instances.
 *
 * <p>LF names are escaped without a prefix. Generator-owned member namespaces start with {@code
 * m-}, which keeps them distinct from LF hierarchy components.
 */
final class MaudeIdentifiers {

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private MaudeIdentifiers() {}

  static String reactor(ReactorInstance reactor) {
    Objects.requireNonNull(reactor);
    return reactorPath(reactor.getFullName());
  }

  static String action(MaudeReactorInstance parent, ActionInstance action) {
    Objects.requireNonNull(action);
    return member(parent, action.isPhysical() ? "m-pa" : "m-la", action.getName());
  }

  static String port(MaudeReactorInstance parent, PortInstance port) {
    Objects.requireNonNull(port);
    if (port.isInput()) {
      return member(parent, "m-in", port.getName());
    }
    if (port.isOutput()) {
      return member(parent, "m-out", port.getName());
    }
    throw new IllegalArgumentException(
        "LF port " + port.getName() + " is neither input nor output");
  }

  static String timer(MaudeReactorInstance parent, TimerInstance timer) {
    Objects.requireNonNull(timer);
    return member(parent, "m-t", timer.getName());
  }

  static String state(MaudeReactorInstance parent, StateVariableInstance state) {
    Objects.requireNonNull(state);
    return member(parent, "m-sv", state.getName());
  }

  static String reaction(MaudeReactorInstance parent, ReactionInstance reaction) {
    Objects.requireNonNull(reaction);
    return member(parent, "m-re", reaction.getName());
  }

  static String startup() {
    return "startup";
  }

  static String encodeIdentifier(String identifier) {
    Objects.requireNonNull(identifier);
    if (identifier.isEmpty()) {
      throw new IllegalArgumentException("Cannot encode an empty LF identifier");
    }

    var result = new StringBuilder(identifier.length() + 1);
    appendEncodedComponent(result, identifier, 0, identifier.length());
    return result.toString();
  }

  static String reactorPath(String fullName) {
    Objects.requireNonNull(fullName);
    var result = new StringBuilder(fullName.length());

    int componentStart = 0;
    for (int i = 0; i <= fullName.length(); i++) {
      if (i == fullName.length() || fullName.charAt(i) == '.') {
        if (componentStart == i) {
          throw new IllegalArgumentException("Invalid LF instance path: " + fullName);
        }
        if (!result.isEmpty()) {
          result.append('.');
        }
        appendEncodedComponent(result, fullName, componentStart, i);
        componentStart = i + 1;
      }
    }
    return result.toString();
  }

  private static String member(
      MaudeReactorInstance parent, String namespace, String lfIdentifier) {
    Objects.requireNonNull(parent);
    return parent.getName() + "." + namespace + "." + encodeIdentifier(lfIdentifier);
  }

  /**
   * Encode one LF name component injectively. {@code Z} is the escape marker, {@code Zu}
   * represents an underscore, and {@code ZZ} represents a literal uppercase Z. Other
   * non-alphanumeric UTF-16 code units use {@code Zxhhhh}.
   */
  private static void appendEncodedComponent(
      StringBuilder result, String identifier, int start, int end) {
    for (int i = start; i < end; i++) {
      char character = identifier.charAt(i);
      if (character == 'Z') {
        result.append("ZZ");
      } else if (character == '_') {
        result.append("Zu");
      } else if (isAsciiLetterOrDigit(character)) {
        result.append(character);
      } else {
        result.append("Zx");
        appendHexCodeUnit(result, character);
      }
    }
  }

  private static boolean isAsciiLetterOrDigit(char character) {
    return character >= 'a' && character <= 'z'
        || character >= 'A' && character <= 'Y'
        || character >= '0' && character <= '9';
  }

  private static void appendHexCodeUnit(StringBuilder result, char character) {
    result.append(HEX_DIGITS[character >>> 12]);
    result.append(HEX_DIGITS[character >>> 8 & 0xf]);
    result.append(HEX_DIGITS[character >>> 4 & 0xf]);
    result.append(HEX_DIGITS[character & 0xf]);
  }
}
