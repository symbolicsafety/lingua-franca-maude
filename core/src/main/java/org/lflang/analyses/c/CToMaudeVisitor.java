package org.lflang.analyses.c;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;

import org.lflang.analyses.maude.MaudeGenerator;
import org.lflang.analyses.uclid.UclidGenerator;
import org.lflang.ast.ASTUtils;
import org.lflang.generator.ActionInstance;
import org.lflang.generator.CodeBuilder;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.generator.NamedInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance;
import org.lflang.generator.ReactionInstance.Runtime;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TimerInstance;
import org.lflang.generator.TriggerInstance;

public class CToMaudeVisitor extends CBaseAstVisitor<String> {
    private MaudeGenerator generator;


}