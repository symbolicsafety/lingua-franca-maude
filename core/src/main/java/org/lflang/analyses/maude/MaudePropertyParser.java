package org.lflang.analyses.maude;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.lflang.dsl.LTLLexer;
import org.lflang.dsl.LTLParser;

/** Strictly parses and translates an LF-Maude analysis property. */
final class MaudePropertyParser {

  private MaudePropertyParser() {}

  static String translate(String property, MaudeInstanceRegistry registry) {
    var errors = new PropertyErrorListener();
    var lexer = new LTLLexer(CharStreams.fromString(property));
    lexer.removeErrorListeners();
    lexer.addErrorListener(errors);

    var parser = new LTLParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(errors);
    var context = parser.ltl();

    if (!errors.messages.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid Maude property '" + property + "': " + String.join("; ", errors.messages));
    }
    return new LTLVisitor(registry).visitLtl(context);
  }

  private static final class PropertyErrorListener extends BaseErrorListener {

    private final List<String> messages = new ArrayList<>();

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String message,
        RecognitionException exception) {
      messages.add("line " + line + ":" + charPositionInLine + " " + message);
    }
  }
}
