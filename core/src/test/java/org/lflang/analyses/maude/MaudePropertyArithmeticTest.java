package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MaudePropertyArithmeticTest {

  private final MaudeInstanceRegistry registry = new MaudeInstanceRegistry();

  @Test
  void appliesConventionalPrecedence() {
    assertEquals("((@ [1] + (@ [2] * @ [3])) === (@ [7]))", translate("1 + 2 * 3 == 7"));
    assertEquals("((@ [1] * @ [2] + @ [3]) === (@ [5]))", translate("1 * 2 + 3 == 5"));
  }

  @Test
  void makesSubtractionAndDivisionLeftAssociative() {
    assertEquals("((@ [8] - @ [3] - @ [2]) === (@ [3]))", translate("8 - 3 - 2 == 3"));
    assertEquals("((@ [8] / @ [4] / @ [2]) === (@ [1]))", translate("8 / 4 / 2 == 1"));
  }

  @Test
  void preservesExplicitParentheses() {
    assertEquals("(((@ [1] + @ [2]) * @ [3]) === (@ [9]))", translate("(1 + 2) * 3 == 9"));
  }

  @Test
  void avoidsUnnecessaryParenthesesForTwoTermExpressions() {
    assertEquals("((@ [1] + @ [2]) === (@ [3]))", translate("1 + 2 == 3"));
    assertEquals("((@ [2] * @ [3]) === (@ [6]))", translate("2 * 3 == 6"));
  }

  private String translate(String property) {
    return MaudePropertyParser.translate(property, registry);
  }
}
