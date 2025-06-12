package org.lflang.analyses.maude;

import org.lflang.generator.PortInstance;

public class MaudePortInstance {
    private PortInstance lfPort;
    protected String name;
    public MaudeTypes.MaudePortType type;

    MaudeReactorInstance parent;

    public MaudePortInstance(PortInstance lfPort, MaudeReactorInstance parent) {
        this.lfPort = lfPort;
        this.lfPort = lfPort;
        this.parent = parent;

        if (this.lfPort.isInput())
            this.name = parent.getName() + ".in." + lfPort.getName().replaceAll("_","");
        else if (this.lfPort.isOutput())
            this.name = parent.getName() + ".out." + lfPort.getName().replaceAll("_","");

        if (lfPort.getDefinition().getType().getId() == "bool")
            this.type = MaudeTypes.MaudePortType.BPortId;
        else if (lfPort.getDefinition().getType().getId() == "int")
            this.type = MaudeTypes.MaudePortType.RPortId;
        else
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

    @Override
    public String toString() {
        return this.getName();
    }
}
