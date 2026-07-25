package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.lflang.dsl.LTLLexer;
import org.lflang.dsl.LTLParser;

class LTLQualifiedNameParserTest {

  @Test
  void parsesQualifiedReactorReferences() {
    assertParsesCompletely("value in left.worker == 1");
    assertParsesCompletely("enabled in Main.left.worker == true");
    assertParsesCompletely("event(left.worker, tick) inQueue");
    assertParsesCompletely("left.worker.1 invoked");
    assertParsesCompletely("value in left.worker + value in right.worker > 0");
  }

  private static void assertParsesCompletely(String expression) {
    var lexer = new LTLLexer(CharStreams.fromString(expression));
    var parser = new LTLParser(new CommonTokenStream(lexer));

    parser.ltl();

    assertEquals(0, parser.getNumberOfSyntaxErrors(), expression);
    assertEquals(Token.EOF, parser.getCurrentToken().getType(), expression);
  }
}
