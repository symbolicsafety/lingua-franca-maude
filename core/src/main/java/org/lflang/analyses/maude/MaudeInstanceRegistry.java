package org.lflang.analyses.maude;

import java.util.IdentityHashMap;
import java.util.Map;
import org.lflang.generator.ActionInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TimerInstance;
import org.lflang.generator.TriggerInstance;

/**
 * Identity-based index from LF instances to their Maude counterparts.
 *
 * <p>The ordered lists on the generator and reactor wrappers remain the source of iteration order.
 * This registry is only used for lookup.
 */
final class MaudeInstanceRegistry {

  private final Map<ReactorInstance, MaudeReactorInstance> reactors = new IdentityHashMap<>();
  private final Map<ActionInstance, MaudeActionInstance> actions = new IdentityHashMap<>();
  private final Map<PortInstance, MaudePortInstance> ports = new IdentityHashMap<>();
  private final Map<TimerInstance, MaudeTimerInstance> timers = new IdentityHashMap<>();
  private final Map<StateVariableInstance, MaudeStateInstance> states = new IdentityHashMap<>();
  private final Map<ReactionInstance, MaudeReactionInstance> reactions = new IdentityHashMap<>();
  private final Map<TriggerInstance<?>, MaudeTriggerInstance> triggers = new IdentityHashMap<>();

  void register(ReactorInstance lfInstance, MaudeReactorInstance maudeInstance) {
    register(reactors, lfInstance, maudeInstance);
  }

  void register(ActionInstance lfInstance, MaudeActionInstance maudeInstance) {
    register(actions, lfInstance, maudeInstance);
  }

  void register(PortInstance lfInstance, MaudePortInstance maudeInstance) {
    register(ports, lfInstance, maudeInstance);
  }

  void register(TimerInstance lfInstance, MaudeTimerInstance maudeInstance) {
    register(timers, lfInstance, maudeInstance);
  }

  void register(StateVariableInstance lfInstance, MaudeStateInstance maudeInstance) {
    register(states, lfInstance, maudeInstance);
  }

  void register(ReactionInstance lfInstance, MaudeReactionInstance maudeInstance) {
    register(reactions, lfInstance, maudeInstance);
  }

  void register(TriggerInstance<?> lfInstance, MaudeTriggerInstance maudeInstance) {
    register(triggers, lfInstance, maudeInstance);
  }

  MaudeReactorInstance get(ReactorInstance lfInstance) {
    return reactors.get(lfInstance);
  }

  MaudeActionInstance get(ActionInstance lfInstance) {
    return actions.get(lfInstance);
  }

  MaudePortInstance get(PortInstance lfInstance) {
    return ports.get(lfInstance);
  }

  MaudeTimerInstance get(TimerInstance lfInstance) {
    return timers.get(lfInstance);
  }

  MaudeStateInstance get(StateVariableInstance lfInstance) {
    return states.get(lfInstance);
  }

  MaudeReactionInstance get(ReactionInstance lfInstance) {
    return reactions.get(lfInstance);
  }

  MaudeTriggerInstance get(TriggerInstance<?> lfInstance) {
    return triggers.get(lfInstance);
  }

  MaudePortInstance requirePort(PortInstance lfInstance) {
    var result = get(lfInstance);
    if (result == null) {
      throw new IllegalStateException(
          "No Maude port is registered for LF port " + lfInstance.getFullName());
    }
    return result;
  }

  private static <K, V> void register(Map<K, V> index, K key, V value) {
    var previous = index.putIfAbsent(key, value);
    if (previous != null && previous != value) {
      throw new IllegalStateException(
          "A different Maude instance is already registered for " + key);
    }
  }
}
