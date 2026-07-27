package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.lflang.DefaultMessageReporter;
import org.lflang.analyses.c.BuildAstParseTreeVisitor;
import org.lflang.analyses.c.CAst;
import org.lflang.analyses.c.CAst.LiteralNode;
import org.lflang.analyses.c.CAst.ScheduleActionNode;
import org.lflang.analyses.c.CAst.SetPortNode;
import org.lflang.analyses.c.CAst.StateVarNode;
import org.lflang.analyses.c.CAst.TriggerValueNode;
import org.lflang.analyses.c.CAst.VariableNode;
import org.lflang.analyses.c.CToMaudeVisitor;
import org.lflang.dsl.CLexer;
import org.lflang.dsl.CParser;
import org.lflang.generator.ReactorInstance;
import org.lflang.lf.ActionOrigin;
import org.lflang.lf.LfFactory;
import org.lflang.lf.Reactor;

class CToMaudeVisitorTest {

  private static final LfFactory FACTORY = LfFactory.eINSTANCE;

  @Test
  void resolvesStateAndActionWithTheSameLfNameByAstNodeType() {
    var fixture = fixture();
    var visitor = new CToMaudeVisitor(fixture.leftWorker);

    assertEquals(
        fixture.leftWorker.stateVars.get(0).getName(),
        visitor.visitStateVarNode(new StateVarNode("x")));
    assertEquals(
        fixture.leftWorker.logicalActions.get(0).getName(),
        visitor.visitTriggerValueNode(new TriggerValueNode("x")));
    assertEquals(
        "schedule(" + fixture.leftWorker.logicalActions.get(0).getName() + ", [0], [0])",
        visitor.visitScheduleActionNode(schedule("x")));
  }

  @Test
  void resolvesSameLocalNamesWithinTheReactionParent() {
    var fixture = fixture();
    var leftVisitor = new CToMaudeVisitor(fixture.leftWorker);
    var rightVisitor = new CToMaudeVisitor(fixture.rightWorker);

    var leftState = leftVisitor.visitStateVarNode(new StateVarNode("x"));
    var rightState = rightVisitor.visitStateVarNode(new StateVarNode("x"));

    assertEquals(fixture.leftWorker.stateVars.get(0).getName(), leftState);
    assertEquals(fixture.rightWorker.stateVars.get(0).getName(), rightState);
    assertNotEquals(leftState, rightState);
  }

  @Test
  void resolvesPortsByTheirRequiredType() {
    var fixture = fixture();
    var visitor = new CToMaudeVisitor(fixture.leftWorker);
    var setPort = new SetPortNode();
    setPort.left = new VariableNode("out");
    setPort.right = new LiteralNode("1");

    assertEquals(
        "(" + fixture.leftWorker.outPorts.get(0).getName() + " <- [1])",
        visitor.visitSetPortNode(setPort));
    assertThrows(
        IllegalArgumentException.class, () -> visitor.visitStateVarNode(new StateVarNode("out")));
  }

  @Test
  void convertsCIntervalMacrosToNanoseconds() {
    var fixture = fixture();
    var visitor = new CToMaudeVisitor(fixture.leftWorker);
    Map<String, Long> macros =
        Map.ofEntries(
            Map.entry("NSEC", 1L),
            Map.entry("NSECS", 1L),
            Map.entry("USEC", 1_000L),
            Map.entry("USECS", 1_000L),
            Map.entry("MSEC", 1_000_000L),
            Map.entry("MSECS", 1_000_000L),
            Map.entry("SEC", 1_000_000_000L),
            Map.entry("SECS", 1_000_000_000L),
            Map.entry("SECOND", 1_000_000_000L),
            Map.entry("SECONDS", 1_000_000_000L),
            Map.entry("MINUTE", 60_000_000_000L),
            Map.entry("MINUTES", 60_000_000_000L),
            Map.entry("HOUR", 3_600_000_000_000L),
            Map.entry("HOURS", 3_600_000_000_000L),
            Map.entry("DAY", 86_400_000_000_000L),
            Map.entry("DAYS", 86_400_000_000_000L),
            Map.entry("WEEK", 604_800_000_000_000L),
            Map.entry("WEEKS", 604_800_000_000_000L));

    macros.forEach(
        (macro, expected) ->
            assertEquals(
                expectedSchedule(fixture, expected),
                visitor.visit(parse("lf_schedule_int(x, " + macro + "(1), 1);")),
                macro));
  }

  @Test
  void evaluatesConstantScheduleDelayArithmetic() {
    var fixture = fixture();
    var visitor = new CToMaudeVisitor(fixture.leftWorker);

    assertEquals(
        expectedSchedule(fixture, 42L), visitor.visit(parse("lf_schedule_int(x, 42, 1);")));
    assertEquals(
        expectedSchedule(fixture, 1_500_000L),
        visitor.visit(parse("lf_schedule_int(x, MSEC(1) + USEC(500), 1);")));
  }

  @Test
  void rejectsInvalidScheduleDelays() {
    var fixture = fixture();
    var visitor = new CToMaudeVisitor(fixture.leftWorker);

    assertThrows(
        IllegalArgumentException.class,
        () -> visitor.visit(parse("lf_schedule_int(x, self->x, 1);")));
    assertThrows(
        IllegalArgumentException.class,
        () -> visitor.visit(parse("lf_schedule_int(x, 0 - 1, 1);")));
    assertThrows(
        IllegalArgumentException.class,
        () -> visitor.visit(parse("lf_schedule_int(x, WEEK(20000), 1);")));
  }

  private static String expectedSchedule(Fixture fixture, long delay) {
    return "schedule("
        + fixture.leftWorker.logicalActions.get(0).getName()
        + ", ["
        + delay
        + "], [1])";
  }

  private static CAst.AstNode parse(String source) {
    var lexer = new CLexer(CharStreams.fromString(source));
    var parser = new CParser(new CommonTokenStream(lexer));
    var result =
        new BuildAstParseTreeVisitor(new DefaultMessageReporter()).visit(parser.blockItemList());
    assertEquals(0, parser.getNumberOfSyntaxErrors());
    return result;
  }

  private static ScheduleActionNode schedule(String actionName) {
    var result = new ScheduleActionNode();
    result.children.add(new VariableNode(actionName));
    result.children.add(new LiteralNode("0"));
    return result;
  }

  private static Fixture fixture() {
    var reporter = new DefaultMessageReporter();
    var registry = new MaudeInstanceRegistry();

    var main = new ReactorInstance(reactor("Main"), reporter);
    var left = reactorInstance("left", reactor("Container"), main, reporter);
    var right = reactorInstance("right", reactor("Container"), main, reporter);

    var workerClass = reactor("Worker");

    var action = FACTORY.createAction();
    action.setName("x");
    action.setOrigin(ActionOrigin.LOGICAL);
    workerClass.getActions().add(action);

    var state = FACTORY.createStateVar();
    state.setName("x");
    state.setType(intType());
    workerClass.getStateVars().add(state);

    var output = FACTORY.createOutput();
    output.setName("out");
    output.setType(intType());
    workerClass.getOutputs().add(output);

    var leftWorkerInstance = reactorInstance("worker", workerClass, left, reporter);
    var rightWorkerInstance = reactorInstance("worker", workerClass, right, reporter);

    new MaudeReactorInstance(main, registry);
    new MaudeReactorInstance(left, registry);
    new MaudeReactorInstance(right, registry);
    var leftWorker = new MaudeReactorInstance(leftWorkerInstance, registry);
    var rightWorker = new MaudeReactorInstance(rightWorkerInstance, registry);

    return new Fixture(leftWorker, rightWorker);
  }

  private static Reactor reactor(String name) {
    var result = FACTORY.createReactor();
    result.setName(name);
    return result;
  }

  private static org.lflang.lf.Type intType() {
    var result = FACTORY.createType();
    result.setId("int");
    return result;
  }

  private static ReactorInstance reactorInstance(
      String name, Reactor reactorClass, ReactorInstance parent, DefaultMessageReporter reporter) {
    var definition = FACTORY.createInstantiation();
    definition.setName(name);
    definition.setReactorClass(reactorClass);
    return new ReactorInstance(definition, parent, reporter, -1, List.of());
  }

  private record Fixture(MaudeReactorInstance leftWorker, MaudeReactorInstance rightWorker) {}
}
