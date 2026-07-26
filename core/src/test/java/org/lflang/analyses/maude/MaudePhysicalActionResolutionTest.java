package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.lflang.DefaultMessageReporter;
import org.lflang.generator.ReactorInstance;
import org.lflang.lf.ActionOrigin;
import org.lflang.lf.Attribute;
import org.lflang.lf.LfFactory;
import org.lflang.lf.Reactor;

class MaudePhysicalActionResolutionTest {

  private static final LfFactory FACTORY = LfFactory.eINSTANCE;

  @Test
  void resolvesQualifiedPhysicalActionsByIdentity() {
    var fixture = fixture();
    var leftProperty = physicalActionProperty("left.worker");
    var rightProperty = physicalActionProperty("right.worker");

    var attributes =
        MaudeGenerator.resolvePhysicalActionAttributes(
            List.of(leftProperty, rightProperty), fixture.registry);

    assertSame(leftProperty, attributes.get(fixture.leftAction));
    assertSame(rightProperty, attributes.get(fixture.rightAction));
    assertEquals(
        fixture.leftWorker.stateVars.get(0).getName(),
        fixture.leftWorker.requireMemberName("physicalAction"));
    assertEquals(
        fixture.leftAction.getName(),
        fixture.leftWorker.requireTriggerName("physicalAction"));
  }

  @Test
  void rejectsAmbiguousReactorReferences() {
    var fixture = fixture();

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MaudeGenerator.resolvePhysicalActionAttributes(
                    List.of(physicalActionProperty("worker")), fixture.registry));

    assertTrue(exception.getMessage().contains("Main.left.worker"));
    assertTrue(exception.getMessage().contains("Main.right.worker"));
  }

  @Test
  void rejectsDuplicateAttributesThatUseDifferentReactorAliases() {
    var fixture = fixture();

    var exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                MaudeGenerator.resolvePhysicalActionAttributes(
                    List.of(
                        physicalActionProperty("left.worker"),
                        physicalActionProperty("Main.left.worker")),
                    fixture.registry));

    assertTrue(exception.getMessage().contains("Main.left.worker"));
    assertTrue(exception.getMessage().contains("physicalAction"));
  }

  @Test
  void rejectsLogicalActions() {
    var fixture = fixture();

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MaudeGenerator.resolvePhysicalActionAttributes(
                    List.of(actionProperty("left.worker", "logicalAction")), fixture.registry));

    assertTrue(exception.getMessage().contains("logicalAction"));
    assertTrue(exception.getMessage().contains("is not physical"));
  }

  private static Fixture fixture() {
    var reporter = new DefaultMessageReporter();
    var registry = new MaudeInstanceRegistry();

    var main = new ReactorInstance(reactor("Main"), reporter);
    var left = reactorInstance("left", reactor("Container"), main, reporter);
    var right = reactorInstance("right", reactor("Container"), main, reporter);

    var workerClass = reactor("Worker");
    var action = FACTORY.createAction();
    action.setName("physicalAction");
    action.setOrigin(ActionOrigin.PHYSICAL);
    workerClass.getActions().add(action);

    var logicalAction = FACTORY.createAction();
    logicalAction.setName("logicalAction");
    logicalAction.setOrigin(ActionOrigin.LOGICAL);
    workerClass.getActions().add(logicalAction);

    var state = FACTORY.createStateVar();
    state.setName("physicalAction");
    var stateType = FACTORY.createType();
    stateType.setId("int");
    state.setType(stateType);
    workerClass.getStateVars().add(state);

    var leftWorkerInstance = reactorInstance("worker", workerClass, left, reporter);
    var rightWorkerInstance = reactorInstance("worker", workerClass, right, reporter);

    new MaudeReactorInstance(main, registry);
    new MaudeReactorInstance(left, registry);
    new MaudeReactorInstance(right, registry);
    var leftWorker = new MaudeReactorInstance(leftWorkerInstance, registry);
    var rightWorker = new MaudeReactorInstance(rightWorkerInstance, registry);

    return new Fixture(
        registry,
        leftWorker,
        leftWorker.physicalActions.get(0),
        rightWorker.physicalActions.get(0));
  }

  private static Reactor reactor(String name) {
    var result = FACTORY.createReactor();
    result.setName(name);
    return result;
  }

  private static ReactorInstance reactorInstance(
      String name,
      Reactor reactorClass,
      ReactorInstance parent,
      DefaultMessageReporter reporter) {
    var definition = FACTORY.createInstantiation();
    definition.setName(name);
    definition.setReactorClass(reactorClass);
    return new ReactorInstance(definition, parent, reporter, -1, List.of());
  }

  private static Attribute physicalActionProperty(String reactorReference) {
    return actionProperty(reactorReference, "physicalAction");
  }

  private static Attribute actionProperty(String reactorReference, String actionName) {
    var result = FACTORY.createAttribute();
    result.setAttrName("maudePhysAct");
    addParameter(result, "inReactor", reactorReference);
    addParameter(result, "name", actionName);
    return result;
  }

  private static void addParameter(Attribute attribute, String name, String value) {
    var parameter = FACTORY.createAttrParm();
    parameter.setName(name);
    parameter.setValue("\"" + value + "\"");
    attribute.getAttrParms().add(parameter);
  }

  private record Fixture(
      MaudeInstanceRegistry registry,
      MaudeReactorInstance leftWorker,
      MaudeActionInstance leftAction,
      MaudeActionInstance rightAction) {}
}
