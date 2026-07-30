package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.lflang.DefaultMessageReporter;
import org.lflang.dsl.LTLLexer;
import org.lflang.dsl.LTLParser;
import org.lflang.generator.ReactorInstance;
import org.lflang.lf.LfFactory;

class LTLQualifiedNameParserTest {

  @Test
  void parsesQualifiedReactorReferences() {
    assertParsesCompletely("value in left.worker == 1");
    assertParsesCompletely("enabled in Main.left.worker == true");
    assertParsesCompletely("event(left.worker, tick) inQueue");
    assertParsesCompletely("left.worker.1 invoked");
    assertParsesCompletely("value in left.worker + value in right.worker > 0");
  }

  @Test
  void acceptsEquivalentReactionInvocationParentheses() {
    var registry = registryWithReactor("s");
    String expected = MaudePropertyParser.translate("s.4 invoked", registry);

    assertEquals(expected, MaudePropertyParser.translate("(s.4 invoked)", registry));
    assertEquals(expected, MaudePropertyParser.translate("(s.4) invoked", registry));
  }

  @Test
  void rejectsTrailingInput() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MaudePropertyParser.translate(
                "remainingTime 1 msec trailing", new MaudeInstanceRegistry()));
  }

  private static void assertParsesCompletely(String expression) {
    var lexer = new LTLLexer(CharStreams.fromString(expression));
    var parser = new LTLParser(new CommonTokenStream(lexer));

    parser.ltl();

    assertEquals(0, parser.getNumberOfSyntaxErrors(), expression);
    assertEquals(Token.EOF, parser.getCurrentToken().getType(), expression);
  }

  private static MaudeInstanceRegistry registryWithReactor(String name) {
    var reactor = LfFactory.eINSTANCE.createReactor();
    reactor.setName(name);
    var registry = new MaudeInstanceRegistry();
    new MaudeReactorInstance(new ReactorInstance(reactor, new DefaultMessageReporter()), registry);
    return registry;
  }
}
