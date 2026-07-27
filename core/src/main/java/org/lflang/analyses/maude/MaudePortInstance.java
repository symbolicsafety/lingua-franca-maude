package org.lflang.analyses.maude;

import org.lflang.generator.PortInstance;

public class MaudePortInstance {
  private final PortInstance lfPort;
  private String name;
  public MaudeTypes.MaudePortType type;
  public Object value;

  MaudeReactorInstance parent;

  public MaudePortInstance(PortInstance lfPort, MaudeReactorInstance parent) {
    this.lfPort = lfPort;
    this.parent = parent;
    this.name = MaudeIdentifiers.port(parent, lfPort);

    if (lfPort.getDefinition().getType().getId() == null) {
      this.type = MaudeTypes.MaudePortType.RPortId;
      this.value = Long.valueOf(0); // set a default payload for this type
    } else if (lfPort.getDefinition().getType().getId().equals("bool")) {
      this.type = MaudeTypes.MaudePortType.BPortId;
      this.value =
          Boolean.valueOf(
              true); // set a default value for this type, as ports are not initialized with a value
    } else if (lfPort.getDefinition().getType().getId().equals("int")) {
      this.type = MaudeTypes.MaudePortType.RPortId;
      this.value =
          Long.valueOf(
              0); // set a default payload for this type, as ports are not initialized with a value
    } else
      throw new RuntimeException("Maude only supports bool and int types for port payload types.");
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

  public PortInstance getLfPort() {
    return lfPort;
  }

  @Override
  public String toString() {
    return this.getName();
  }
}
