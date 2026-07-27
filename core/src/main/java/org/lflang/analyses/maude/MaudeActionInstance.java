package org.lflang.analyses.maude;

import org.lflang.generator.ActionInstance;

public class MaudeActionInstance {
  public final ActionInstance lfAction;
  private final String name;
  public final MaudeTypes.MaudeActionType type;
  public final long minDelay;
  public final long minSpacing;
  public final String policy;
  public Object payload;

  MaudeReactorInstance parent;

  public MaudeActionInstance(ActionInstance lfAction, MaudeReactorInstance parent) {
    this.lfAction = lfAction;
    this.parent = parent;
    this.name = MaudeIdentifiers.action(parent, lfAction);

    this.minDelay =
        MaudeTime.toNanoseconds(
            lfAction.getMinDelay().getMagnitude(),
            lfAction.getMinDelay().getUnit(),
            "Action minimum delay",
            true);
    if (lfAction.getMinSpacing() != null)
      this.minSpacing =
          MaudeTime.toNanoseconds(
              lfAction.getMinSpacing().getMagnitude(),
              lfAction.getMinSpacing().getUnit(),
              "Action minimum spacing",
              true);
    else {
      this.minSpacing = 0;
    }

    this.policy = lfAction.getPolicy() == null ? "defer" : lfAction.getPolicy();

    if (lfAction.getDefinition().getType() != null) {
      if (lfAction.getDefinition().getType().getId().equals("bool")) {
        this.type = MaudeTypes.MaudeActionType.BActionId;
        this.payload = Boolean.valueOf(true); // Set a default payload.
      } else if (lfAction.getDefinition().getType().getId().equals("int")) {
        this.type = MaudeTypes.MaudeActionType.RActionId;
        this.payload = Long.valueOf(0); // set a default value.
      } else
        throw new RuntimeException(
            "Maude only supports bool and int types for action payload types.");
    } else {
      this.type = MaudeTypes.MaudeActionType.RActionId;
      this.payload = Long.valueOf(0); // set a default value
    }
  }

  // special constructor for startup reaction, which we declare as logical action in Maude
  private MaudeActionInstance(MaudeReactorInstance parent) {
    this.lfAction = null;
    this.parent = parent;
    this.name = MaudeIdentifiers.startup();
    this.minDelay = 0;
    this.minSpacing = 0;
    this.policy = "defer";
    this.type = MaudeTypes.MaudeActionType.RActionId;
    this.payload = Long.valueOf(0);
  }

  public static MaudeActionInstance createStartupAction(MaudeReactorInstance parent) {
    MaudeActionInstance startup = new MaudeActionInstance(parent);
    return startup;
  }

  public MaudeReactorInstance getParent() {
    return parent;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type.name();
  }

  public ActionInstance getLfAction() {
    return lfAction;
  }

  @Override
  public String toString() {
    return this.getName();
  }
}
