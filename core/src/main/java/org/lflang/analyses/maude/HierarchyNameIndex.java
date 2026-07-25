package org.lflang.analyses.maude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Resolves hierarchical LF names using full, root-relative, or unambiguous local references.
 *
 * @param <T> the indexed value type
 */
final class HierarchyNameIndex<T> {

  private final String description;
  private final Map<String, T> fullNames = new HashMap<>();
  private final Map<String, T> rootRelativeNames = new HashMap<>();
  private final Map<String, List<T>> localNames = new HashMap<>();
  private final Map<T, String> canonicalNames = new IdentityHashMap<>();

  HierarchyNameIndex(String description) {
    this.description = Objects.requireNonNull(description);
  }

  void register(String fullName, String localName, T value) {
    requireNonEmpty(fullName, "full name");
    requireNonEmpty(localName, "local name");
    Objects.requireNonNull(value);

    var previousCanonicalName = canonicalNames.putIfAbsent(value, fullName);
    if (previousCanonicalName != null && !previousCanonicalName.equals(fullName)) {
      throw new IllegalStateException(
          description
              + " is already registered as '"
              + previousCanonicalName
              + "', not '"
              + fullName
              + "'");
    }

    registerUnique(fullNames, fullName, value);

    int firstSeparator = fullName.indexOf('.');
    if (firstSeparator >= 0) {
      registerUnique(rootRelativeNames, fullName.substring(firstSeparator + 1), value);
    }

    var matches = localNames.computeIfAbsent(localName, ignored -> new ArrayList<>());
    if (matches.stream().noneMatch(candidate -> candidate == value)) {
      matches.add(value);
    }
  }

  T resolve(String reference) {
    requireNonEmpty(reference, "reference");

    var result = fullNames.get(reference);
    if (result != null) {
      return result;
    }

    result = rootRelativeNames.get(reference);
    if (result != null) {
      return result;
    }

    var localMatches = localNames.get(reference);
    if (localMatches == null) {
      throw new IllegalArgumentException(
          "No " + description + " matches '" + reference + "'");
    }
    if (localMatches.size() > 1) {
      String matches =
          localMatches.stream()
              .map(canonicalNames::get)
              .sorted()
              .collect(Collectors.joining(", "));
      throw new IllegalArgumentException(
          "Ambiguous " + description + " reference '" + reference + "'. Matches: " + matches);
    }
    return localMatches.get(0);
  }

  private void registerUnique(Map<String, T> index, String name, T value) {
    var previous = index.putIfAbsent(name, value);
    if (previous != null && previous != value) {
      throw new IllegalStateException(
          "Multiple " + description + " instances have the path '" + name + "'");
    }
  }

  private static void requireNonEmpty(String value, String role) {
    Objects.requireNonNull(value);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Cannot index an empty " + role);
    }
  }
}
