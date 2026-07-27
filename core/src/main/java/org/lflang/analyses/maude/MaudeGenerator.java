package org.lflang.analyses.maude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.emf.ecore.resource.Resource;
import org.lflang.analyses.c.BuildAstParseTreeVisitor;
import org.lflang.analyses.c.CAst;
import org.lflang.analyses.c.CToMaudeVisitor;
import org.lflang.ast.ASTUtils;
import org.lflang.dsl.CParser.BlockItemListContext;
import org.lflang.generator.CodeBuilder;
import org.lflang.generator.GeneratorBase;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.RuntimeRange;
import org.lflang.generator.SendRange;
import org.lflang.generator.TargetTypes;
import org.lflang.generator.docker.DockerGenerator;
import org.lflang.lf.AttrParm;
import org.lflang.lf.Attribute;
import org.lflang.lf.Connection;
import org.lflang.target.Target;
import org.lflang.util.StringUtil;

/** (EXPERIMENTAL) Generator for Maude models. */
public class MaudeGenerator extends GeneratorBase {

  /** A runner for the generated Maude files */
  public MaudeRunner runner;

  private final MaudeInstanceRegistry maudeInstances = new MaudeInstanceRegistry();

  public List<MaudeReactorInstance> maudeReactorInstances = new ArrayList<>();
  private List<MaudePortInstance> maudePortInstances = new ArrayList<>();
  private List<MaudeActionInstance> maudeActionInstances = new ArrayList<>();
  private List<MaudeActionInstance> maudePhysicalActionInstances = new ArrayList<>();
  private List<MaudeTimerInstance> maudeTimerInstances = new ArrayList<>();
  private List<MaudeStateInstance> maudeStateInstances = new ArrayList<>();

  private List<Attribute> maudePhysActProperties;
  private List<Attribute> maudeProperties;

  /**
   * Create a new GeneratorBase object.
   *
   * @param context
   */
  public MaudeGenerator(
      LFGeneratorContext context,
      List<Attribute> maudeProperties,
      List<Attribute> maudePhysActProperties) {
    super(context);
    this.maudeProperties = maudeProperties;
    this.maudePhysActProperties = maudePhysActProperties;

    this.runner = new MaudeRunner(this);
  }

  /** A list of paths to the maude files generated */
  public List<Path> generatedFiles = new ArrayList<>();

  /** The directory where the generated files are placed */
  public Path outputDir;

  /** The main place to put generated code. */
  private CodeBuilder code = new CodeBuilder();

  ////////////////////////////////////////////////////////////
  //// Public methods
  public void doGenerate(Resource resource, LFGeneratorContext context) {

    // Reuse parts of doGenerate() from GeneratorBase.
    super.printInfo(context);
    ASTUtils.setMainName(context.getFileConfig().resource, context.getFileConfig().name);
    super.createMainInstantiation();
    super.setReactorsAndInstantiationGraph(context.getMode());

    // Create the main reactor instance if there is a main reactor.
    this.main =
        ASTUtils.createMainReactorInstance(mainDef, reactors, messageReporter, targetConfig);

    // Extract information from the named instances.
    populateDataStructures();

    //        for (Attribute prop : this.maudeProperties) {
    //            String physAct = StringUtil.removeQuotes(
    //                prop.getAttrParms().stream()
    //                    .filter(attr -> attr.getName().equals("physact"))
    //                    .findFirst()
    //                    .get()
    //                    .getValue());
    //            System.out.println(physAct);
    //        }

    // Create the src-gen directory
    setupDirectories();
    generateMaudeFile();
  }

  ////////////////////////////////////////////////////////////
  //// Protected methods

  /** Generate the Maude model. */
  protected void generateMaudeFile() {
    try {
      // Generate main.maude and print to file
      code = new CodeBuilder();
      Path file = this.outputDir.resolve(this.main.getName() + ".maude");
      String filename = file.toString();
      generateMaudeCode();
      code.writeToFile(filename);
      this.generatedFiles.add(file);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** The main function that generates maude code. */
  protected void generateMaudeCode() {
    code.pr(
        String.join(
            "\n",
            "---(****************************",
            " * Auto-generated Maude model *",
            " ******************************)---"));

    code.pr("omod " + this.main.getName().toUpperCase() + " is");
    code.indent();
    code.pr("including LF-REPR .");
    code.pr("protecting LF-VALUE-TIME .");
    code.pr("");

    generateIdentifiers();
    generateInitConfiguration();

    code.unindent();
    code.pr("endom");

    code.pr("");

    generateMaudeTest();

    code.pr("");

    generateAnalysis();

    code.pr("quit");
  }

  private static Optional<String> getParam(Attribute prop, String paramName) {
    if (prop.getAttrParms() == null) return Optional.empty();
    return prop.getAttrParms().stream()
        .filter(p -> paramName.equals(p.getName()))
        .map(
            p -> {
              Object v = p.getValue(); // if it's already String, this is fine; otherwise toString()
              return v == null ? null : StringUtil.removeQuotes(v.toString());
            })
        .filter(Objects::nonNull)
        .findFirst();
  }

  static Map<MaudeActionInstance, Attribute> resolvePhysicalActionAttributes(
      List<Attribute> properties, MaudeInstanceRegistry registry) {
    Map<MaudeActionInstance, Attribute> result = new IdentityHashMap<>();
    for (Attribute property : properties) {
      String reactorReference =
          getParam(property, "inReactor")
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Attribute 'inReactor' missing for physical action property"));
      String actionName =
          getParam(property, "name")
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Attribute 'name' missing for physical action property"));

      var reactor = registry.resolveReactor(reactorReference);
      var action = reactor.requirePhysicalAction(actionName);
      var previous = result.put(action, property);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate physical action attribute for reactor='"
                + reactor.lfReactor.getFullName()
                + "', name='"
                + actionName
                + "'");
      }
    }
    return result;
  }

  protected void generateMaudeTest() {
    code.pr("omod TEST-" + this.main.getName().toUpperCase() + " is");
    code.indent();
    code.pr("including " + this.main.getName().toUpperCase() + " .");
    code.pr("including DYNAMICS-WITHOUT-TICK .");
    code.pr("");
    code.pr("ops env queue rxns : -> Oid [ctor] .");
    code.pr("");
    code.pr("op initSystem : -> GlobalSystem .");
    code.pr("eq initSystem =");
    code.indent();
    code.pr("{ < env : Environment |");
    code.indent();
    StringBuilder builder = new StringBuilder();
    builder.append("physicalActions : ");
    if (this.maudePhysicalActionInstances.isEmpty()) {
      builder.append("none");
      code.pr(builder.toString());
    } else {
      if (this.maudePhysActProperties.isEmpty()) {
        throw new RuntimeException(
            "Physical actions require a environment definition using @maudePhysAct()");
      }

      code.pr(builder.toString());
      code.indent();

      Map<MaudeActionInstance, Attribute> attributes =
          resolvePhysicalActionAttributes(this.maudePhysActProperties, this.maudeInstances);

      // Validate all physActs and fill
      for (MaudeActionInstance act : this.maudePhysicalActionInstances) {
        Attribute match = attributes.get(act);
        if (match == null) {
          throw new RuntimeException(
              "Missing physical action attribute for reactor='"
                  + act.getParent().lfReactor.getFullName()
                  + "', name='"
                  + act.lfAction.getName()
                  + "'");
        }

        String vals =
            getParam(match, "vals")
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Attribute 'vals' missing for physicalAction property "
                                + getParam(match, "name").orElse("")));
        MaudePhysicalActionValues.requireCompatible(vals, act.type, act.lfAction.getName());

        long period =
            MaudeTime.toNanoseconds(
                getParam(match, "period")
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "Attribute 'period' missing for physical action property.")),
                getParam(match, "periodUnit").orElse(null),
                "period");

        Boolean timeNonDet = Boolean.parseBoolean(getParam(match, "timeNonDet").orElse("true"));

        builder = new StringBuilder();
        builder.append(
            "< (" + act.getParent().getName() + " . " + act.getName() + " ): PhysAct | ");
        vals = vals.replaceAll("[Tt][Rr][Uu][Ee]", "[true]");
        vals = vals.replaceAll("[Ff][Aa][Ll][Ss][Ee]", "[false]");
        vals = vals.replaceAll(",", ":");
        vals = vals.replaceAll("([0-9]+)", "[$1]");
        builder.append(
            "leftOfPeriod : "
                + period
                + ", period : "
                + period
                + ", possibleValues : "
                + vals // +"[0] : [1], "
                + ", timeNonDet : "
                + timeNonDet
                + " >");
        code.pr(builder.toString());
      }
      code.unindent();
    }
    code.pr(" > ");
    code.pr("addReactionIndices(init)");
    code.pr("< queue : EventQueue | queue : ");
    builder = new StringBuilder();
    boolean havestartup = false;
    for (var reactor : this.maudeReactorInstances)
      if (reactor.hasStartup()) {
        havestartup = true;
        break;
      }

    if (this.maudeTimerInstances.size() > 0) {
      builder.append("addInitialTimers(init, ");
      if (havestartup) {
        builder.append("addStartup(startup, init, empty))");
      } else {
        builder.append("empty)");
      }
    } else if (havestartup) {
      builder.append("addStartup(startup, init, empty)");
    } else builder.append("empty");

    builder.append(" >");
    code.pr(builder.toString());

    code.pr("< rxns : Invoked | reactions : none >} .");

    code.unindent();
    code.unindent();
    code.unindent();
    code.pr("endom");
  }

  protected void generateInitConfiguration() {
    code.pr("eq init = ");

    for (var reactor : this.maudeReactorInstances) {
      code.indent();
      code.pr("< " + reactor.getName() + " : Reactor |");
      // generate reactor attributes
      code.indent();
      generateInports(reactor);
      code.insert(code.length() - 1, ",");
      generateOutports(reactor);
      code.insert(code.length() - 1, ",");
      generateStates(reactor);
      code.insert(code.length() - 1, ",");
      generateTimers(reactor);
      code.insert(code.length() - 1, ",");
      generateActions(reactor);
      code.insert(code.length() - 1, ",");
      generateReactions(reactor);
      code.unindent();
      code.pr(">");
      code.unindent();
    }

    generateConnections();
    code.pr(".");
  }

  protected void generateConnections() {
    // generate connections
    for (var port : this.maudePortInstances) {
      for (SendRange range : port.getLfPort().getDependentPorts()) {
        MaudePortInstance mSource = this.maudeInstances.requirePort(range.instance);
        Connection connection = range.connection;
        List<RuntimeRange<PortInstance>> destinations = range.destinations;

        long delay = MaudeConnectionDelay.toNanoseconds(connection.getDelay());

        for (var portRange : destinations) {
          var destination = portRange.instance;
          MaudePortInstance mDestination = this.maudeInstances.requirePort(destination);
          StringBuilder builder = new StringBuilder();
          builder.append("(" + mSource.getParent().getName() + " : " + mSource.getName());
          if (delay > 0) {
            builder.append(" -- " + delay);
          }
          builder.append(
              " --> " + mDestination.getParent().getName() + " : " + mDestination.getName() + ")");
          code.pr(builder);
        }
      }
    }
  }

  protected void generateReactions(MaudeReactorInstance mReactor) {
    StringBuilder builder = new StringBuilder();
    builder.append("reactions : ");
    if (mReactor.reactions.isEmpty()) {
      builder.append("nil");
      code.pr(builder.toString());
    } else {
      code.pr(builder.toString());
      code.indent();

      for (var reaction : mReactor.reactions) {

        builder = new StringBuilder();
        builder.append("(reaction when (" + reaction.getTriggers().get(0).getName());

        for (var trigger : reaction.getTriggers().stream().skip(1).toList())
          builder.append(" ; " + trigger.getName());

        builder.append(")");

        if (reaction.getEffects().size() > 0) {
          builder.append(" --> (" + reaction.getEffects().get(0).getName());
          for (var effect : reaction.getEffects().stream().skip(1).toList())
            builder.append(" ; " + effect.getName());
          builder.append(")");
        }

        builder.append(" do {");
        code.pr(builder.toString());
        code.indent();
        String body = reaction.getLfReaction().getDefinition().getCode().getBody();

        // Generate a parse tree.
        org.lflang.dsl.CLexer lexer = new org.lflang.dsl.CLexer(CharStreams.fromString(body));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        org.lflang.dsl.CParser parser = new org.lflang.dsl.CParser(tokens);
        BlockItemListContext parseTree = parser.blockItemList();

        // Build an AST.
        BuildAstParseTreeVisitor buildAstVisitor = new BuildAstParseTreeVisitor(messageReporter);
        CAst.AstNode ast = buildAstVisitor.visitBlockItemList(parseTree);

        CToMaudeVisitor c2mVisitor = new CToMaudeVisitor(reaction.getParent());

        String output = c2mVisitor.visit(ast);
        code.pr(output);
        code.unindent();
        code.pr("})");
      }
      code.unindent();
    }
  }

  protected void generateActions(MaudeReactorInstance mReactor) {
    StringBuilder builder = new StringBuilder();
    builder.append("actions : ");
    if (mReactor.physicalActions.isEmpty() && mReactor.logicalActions.isEmpty()) {
      builder.append("none");
      code.pr(builder.toString());
    } else {
      code.pr(builder.toString());
      code.indent();
      for (var logicalAction : mReactor.logicalActions) {
        warnIfActionSpacingIsIgnored(logicalAction);
        builder = new StringBuilder();
        builder.append(
            "< "
                + logicalAction.getName()
                + " : LogicalAction | minDelay : "
                + logicalAction.minDelay
                + ", minSpacing : "
                + logicalAction.minSpacing
                + ", policy : "
                + logicalAction.policy
                + ", payload : ["
                + logicalAction.payload
                + "] >");
        code.pr(builder.toString());
      }
      for (var physicalAction : mReactor.physicalActions) {
        warnIfActionSpacingIsIgnored(physicalAction);
        builder = new StringBuilder();
        builder.append(
            "< "
                + physicalAction.getName()
                + " : PhysicalAction | minDelay : "
                + physicalAction.minDelay
                + ", minSpacing : "
                + physicalAction.minSpacing
                + ", policy : "
                + physicalAction.policy
                + ", payload : ["
                + physicalAction.payload
                + "] >");
        code.pr(builder.toString());
      }
      code.unindent();
    }
  }

  private void warnIfActionSpacingIsIgnored(MaudeActionInstance action) {
    if (action.getLfAction() != null && action.minSpacing > 0) {
      messageReporter
          .at(action.getLfAction().getDefinition())
          .warning(
              "LF-Maude currently ignores minimum spacing and spacing violation policy for "
                  + "action '"
                  + action.getLfAction().getFullName()
                  + "'. The generated model retains both values, but verification may not "
                  + "match LF runtime behavior.");
    }
  }

  protected void generateTimers(MaudeReactorInstance mReactor) {
    StringBuilder builder = new StringBuilder();
    builder.append("timers : ");
    if (mReactor.timers.isEmpty()) {
      builder.append("none");
      code.pr(builder.toString());
    } else {
      code.pr(builder.toString());
      code.indent();
      for (var timer : mReactor.timers) {
        builder = new StringBuilder();
        builder.append(
            "< "
                + timer.getName()
                + " : Timer | offset : "
                + timer.getOffset()
                + ", period : "
                + timer.getPeriod()
                + " >");
        code.pr(builder.toString());
      }
      code.unindent();
    }
  }

  protected void generateStates(MaudeReactorInstance mReactor) {
    StringBuilder builder = new StringBuilder();
    builder.append("state : ");
    if (mReactor.stateVars.isEmpty()) {
      builder.append("empty");
      code.pr(builder.toString());
    } else {
      code.pr(builder.toString());
      code.indent();

      builder = new StringBuilder();
      // first element separately
      var sv = mReactor.stateVars.get(0);
      builder.append("( " + sv.getName() + " |-> [" + sv.value + "] )");
      code.pr(builder.toString());

      for (var statevar : mReactor.stateVars.stream().skip(1).toList()) {
        code.insert(code.length() - 1, ";");
        builder = new StringBuilder();
        builder.append("( " + statevar.getName() + " |-> [" + statevar.value + "] )");

        code.pr(builder.toString());
      }
      code.unindent();
    }
  }

  protected void generateInports(MaudeReactorInstance mReactor) {
    StringBuilder builder = new StringBuilder();
    builder.append("inports : ");
    if (mReactor.inPorts.isEmpty()) {
      builder.append("none");
      code.pr(builder.toString());
    } else {
      code.pr(builder.toString());
      code.indent();
      for (var inport : mReactor.inPorts) {
        builder = new StringBuilder();
        builder.append("< " + inport.getName() + " : Port | value : [" + inport.value + "] >");
        code.pr(builder.toString());
      }
      code.unindent();
    }
  }

  protected void generateOutports(MaudeReactorInstance mReactor) {
    StringBuilder builder = new StringBuilder();
    builder.append("outports : ");
    if (mReactor.outPorts.isEmpty()) {
      builder.append("none");
      code.pr(builder.toString());
    } else {
      code.pr(builder.toString());
      code.indent();
      for (var outport : mReactor.outPorts) {
        builder = new StringBuilder();
        builder.append("< " + outport.getName() + " : Port | value : [" + outport.value + "] >");

        code.pr(builder.toString());
      }
      code.unindent();
    }
  }

  protected void generateIdentifiers() {
    generateReactorIdentifiers();
    generateStateVariables();
    generatePortVariables();
    generateTimerVariables();
    generateActionVariables();

    code.pr("op init : -> Configuration .\n\n");
  }

  protected void generateReactorIdentifiers() {
    for (var reactor : this.maudeReactorInstances) {
      code.pr("op " + reactor.getName() + " : -> ReactorId [ctor] .");
    }
  }

  protected void generateStateVariables() {
    for (var stateVariable : this.maudeStateInstances) {
      code.pr("op " + stateVariable.getName() + " : -> " + stateVariable.getType() + " [ctor] .");
    }
  }

  protected void generatePortVariables() {
    for (var portVariable : this.maudePortInstances) {
      code.pr("op " + portVariable.getName() + " : -> " + portVariable.getType() + " [ctor] .");
    }
  }

  protected void generateTimerVariables() {
    for (var timerVariable : this.maudeTimerInstances) {
      code.pr("op " + timerVariable.getName() + " : -> TimerId [ctor] .");
    }
  }

  protected void generateActionVariables() {
    for (var actionVariable : this.maudeActionInstances) {
      code.pr("op " + actionVariable.getName() + " : -> " + actionVariable.getType() + " [ctor] .");
    }
    if (this.maudeActionInstances.stream().anyMatch(action -> "update".equals(action.policy))) {
      code.pr("op update : -> ActionPolicy [ctor] .");
    }
  }

  protected void generateAnalysis() {
    code.pr("omod ANALYSIS-" + this.main.getName().toUpperCase() + " is");
    code.indent();
    code.pr("including TEST-" + this.main.getName().toUpperCase() + " .");
    code.pr("including LF-PROP-EXT .");
    code.pr("including LF-SEARCH-COMMAND .");
    code.unindent();
    code.pr("endom");
    code.pr("");

    code.pr("omod MODELCHECKER-" + this.main.getName().toUpperCase() + " is");
    code.indent();
    code.pr("including TEST-" + this.main.getName().toUpperCase() + " .");
    code.pr("including LF-OUTPUT-COUNTEREXAMPLE .");
    code.pr("including LF-PROP-EXT .");
    code.pr("including MODEL-CHECKER .");
    code.unindent();
    code.pr("endom");
    code.pr("");

    code.pr("omod SIMULATION-" + this.main.getName().toUpperCase() + " is");
    code.indent();
    code.pr("including TEST-" + this.main.getName().toUpperCase() + " .");
    code.pr("including TIMED-SIMULATION-DYNAMICS .");
    code.unindent();
    code.pr("endom");
    code.pr("");

    String maudeVerbose = context.getArgs().maudeVerbose();

    for (Attribute prop : this.maudeProperties) {
      String analysis = // so far this has fixed value "reachability"
          StringUtil.removeQuotes(
              prop.getAttrParms().stream()
                  .filter(attr -> attr.getName().equals("analysis"))
                  .findFirst()
                  .get()
                  .getValue());

      String goal = "";
      Optional<AttrParm> goalParam =
          prop.getAttrParms().stream().filter(attr -> attr.getName().equals("goal")).findFirst();

      if (goalParam.isPresent()) {
        goal = StringUtil.removeQuotes(goalParam.get().getValue());
      }

      String timeBound = "INF";
      Optional<String> timeBoundParam = getParam(prop, "timeBound");
      if (timeBoundParam.isPresent()) {
        timeBound =
            String.valueOf(
                MaudeTime.toNanoseconds(
                    timeBoundParam.get(),
                    getParam(prop, "timeBoundUnit").orElse(null),
                    "timeBound"));
      }

      String rewrites = "";
      Optional<AttrParm> rewritesParam =
          prop.getAttrParms().stream()
              .filter(attr -> attr.getName().equals("rewrites"))
              .findFirst();
      if (rewritesParam.isPresent()) {
        rewrites = rewritesParam.get().getValue();
        if (Integer.parseInt(rewrites) < 1)
          throw new RuntimeException("rewrites must be greater than 0");
      }

      String mode = "*";
      Optional<AttrParm> modeParam =
          prop.getAttrParms().stream().filter(attr -> attr.getName().equals("type")).findFirst();
      if (modeParam.isPresent()) {
        mode = StringUtil.removeQuotes(modeParam.get().getValue());
      }
      if (!mode.matches("[\\*,!1+]"))
        throw new RuntimeException(
            "invalid type: \"" + mode + "\". Allowed values are one of [1, +, *, !] .");

      if (analysis.equalsIgnoreCase("reachability")) {

        if (goal.isEmpty())
          throw new RuntimeException("Reachability analysis requires goal to be defined!");

        StringBuilder builder = new StringBuilder();
        builder.append(
            "red in ANALYSIS-"
                + this.main.getName().toUpperCase()
                + " : search in 'ANALYSIS-"
                + this.main.getName().toUpperCase()
                + " : initSystem timeBound ");
        // Decide what kind of analysis to do

        builder.append(timeBound);

        builder.append(" =>" + mode + " ");
        String genGoal = MaudePropertyParser.translate(goal, this.maudeInstances);
        builder.append(genGoal + " .");
        emitVerboseWrappedCommand(builder.toString(), analysis, maudeVerbose);
        code.pr("");
      } else if (analysis.equalsIgnoreCase("ltl")) {
        if (goal.isEmpty()) throw new RuntimeException("LTL analysis requires goal to be defined!");

        StringBuilder builder = new StringBuilder();
        builder.append(
            "red in MODELCHECKER-"
                + this.main.getName().toUpperCase()
                + " : modelCheck(initSystem timeBound "
                + timeBound);

        String genGoal = MaudePropertyParser.translate(goal, this.maudeInstances);
        builder.append(" , " + genGoal + " ) .");
        emitVerboseWrappedCommand(builder.toString(), analysis, maudeVerbose);
        code.pr("");

      } else if (analysis.equalsIgnoreCase("simulation")) {
        StringBuilder builder = new StringBuilder();
        builder.append("rew ");
        if (!rewrites.isEmpty()) {
          builder.append("[" + rewrites + "] ");
        }

        builder.append(
            "in SIMULATION-"
                + this.main.getName().toUpperCase()
                + " : initSystem timeBound "
                + timeBound
                + " .");
        code.pr(builder.toString());
        code.pr("");
      }
    }
  }

  // wrap ltl and reachability commands with verbose on/off if maudeVerbose is set.
  private void emitVerboseWrappedCommand(String command, String analysis, String maudeVerbose) {
    if (maudeVerbose == null) {
      code.pr(command);
      return;
    }
    if (maudeVerbose.equalsIgnoreCase("all")) {
      code.pr("set verbose on .");
      code.pr(command);
      return;
    }
    if (maudeVerbose.equalsIgnoreCase(analysis)) {
      code.pr("set verbose on .");
      code.pr(command);
      code.pr("set verbose off .");
      return;
    }
    code.pr(command);
  }

  ////////////////////////////////////////////////////////////
  //// Private methods

  private void setupDirectories() {
    // Make sure the target directory exists.
    Path modelGenDir = context.getFileConfig().getModelGenPath();
    this.outputDir = Paths.get(modelGenDir.toString());
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    System.out.println("The Maude files will be located in: " + outputDir);
  }

  /** Populate the data structures. */
  private void populateDataStructures() {
    populateLists(this.main);
  }

  private void populateLists(ReactorInstance reactor) {
    MaudeReactorInstance maudeReactor = new MaudeReactorInstance(reactor, this.maudeInstances);
    this.maudeReactorInstances.add(maudeReactor);

    this.maudeStateInstances.addAll(maudeReactor.stateVars);

    this.maudeActionInstances.addAll(maudeReactor.logicalActions);
    this.maudeActionInstances.addAll(maudeReactor.physicalActions);
    this.maudePhysicalActionInstances.addAll(maudeReactor.physicalActions);

    this.maudePortInstances.addAll(maudeReactor.inPorts);

    this.maudePortInstances.addAll(maudeReactor.outPorts);

    this.maudeTimerInstances.addAll(maudeReactor.timers);

    // Recursion
    for (var child : reactor.children) {
      populateLists(child);
    }
  }

  @Override
  public TargetTypes getTargetTypes() {
    throw new UnsupportedOperationException(
        "This method is not applicable for this generator since maude is not an LF target.");
  }

  @Override
  protected DockerGenerator getDockerGenerator(LFGeneratorContext context) {
    return null;
  }

  @Override
  public Target getTarget() {
    return Target.C; // Works with a C subset.
  }
}
