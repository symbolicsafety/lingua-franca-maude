package org.lflang.analyses.maude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.emf.ecore.resource.Resource;

import org.lflang.TimeUnit;
import org.lflang.TimeValue;
import org.lflang.analyses.c.BuildAstParseTreeVisitor;
import org.lflang.analyses.c.CAst;
import org.lflang.analyses.c.CToMaudeVisitor;
import org.lflang.ast.ASTUtils;
import org.lflang.dsl.CParser.BlockItemListContext;
import org.lflang.dsl.LTLLexer;
import org.lflang.dsl.LTLParser;
import org.lflang.dsl.LTLParser.LtlContext;
import org.lflang.generator.ActionInstance;
import org.lflang.generator.CodeBuilder;
import org.lflang.generator.GeneratorBase;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.generator.NamedInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance.Runtime;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.RuntimeRange;
import org.lflang.generator.SendRange;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TargetTypes;
import org.lflang.generator.TimerInstance;
import org.lflang.generator.TriggerInstance;
import org.lflang.generator.docker.DockerGenerator;
import org.lflang.lf.AttrParm;
import org.lflang.lf.Attribute;
import org.lflang.lf.Connection;
import org.lflang.lf.Expression;
import org.lflang.lf.Time;
import org.lflang.target.Target;
import org.lflang.util.StringUtil;

/** (EXPERIMENTAL) Generator for Maude models. */
public class MaudeGenerator extends GeneratorBase {

    /** A runner for the generated Maude files */
    public MaudeRunner runner;


    public List<MaudeReactorInstance> maudeReactorInstances = new ArrayList<>();
    private List<MaudePortInstance> maudePortInstances = new ArrayList<>();
    private List<MaudeReactionInstance> maudeReactionInstances = new ArrayList<>();
    private List<MaudeActionInstance> maudeActionInstances = new ArrayList<>();
    private List<MaudeActionInstance> maudePhysicalActionInstances = new ArrayList<>();
    private List<MaudeTimerInstance> maudeTimerInstances = new ArrayList<>();
    private List<MaudeStateInstance> maudeStateInstances = new ArrayList<>();
    public List<MaudeTriggerInstance> maudeTriggerInstances = new ArrayList<>(); // Triggers = ports + actions + timers

    private List<Attribute> maudePhysActProperties;
    private List<Attribute> maudeProperties;

    /**
     * Create a new GeneratorBase object.
     *
     * @param context
     */
    public MaudeGenerator(LFGeneratorContext context, List<Attribute> maudeProperties, List<Attribute> maudePhysActProperties) {
        super(context);
        this.maudeProperties = maudeProperties;
        this.maudePhysActProperties = maudePhysActProperties;


        this.runner = new MaudeRunner(this);
    }
    //// Public fields
    /** A list of reaction runtime instances. */
    public List<Runtime> reactionInstances =
        new ArrayList<Runtime>();

    /** A list of action instances */
    public List<ActionInstance> actionInstances = new ArrayList<ActionInstance>();

    /** Joint lists of the lists above. */
    public List<TriggerInstance> triggerInstances; // Triggers = ports + actions + timers

    public List<NamedInstance> namedInstances; // Named instances = triggers + state variables

    /** A list of paths to the maude files generated */
    public List<Path> generatedFiles = new ArrayList<>();
    /** The directory where the generated files are placed */
    public Path outputDir;

    ////////////////////////////////////////////
    //// Private fields
    /** A list of reactor runtime instances. */
    private List<ReactorInstance> reactorInstances = new ArrayList<ReactorInstance>();

    /** State variables in the system */
    private List<StateVariableInstance> stateVariables = new ArrayList<StateVariableInstance>();

    /** A list of input port instances */
    private List<PortInstance> inputInstances = new ArrayList<PortInstance>();

    /** A list of output port instances */
    private List<PortInstance> outputInstances = new ArrayList<PortInstance>();

    /** A list of input AND output port instances */
    private List<PortInstance> portInstances = new ArrayList<PortInstance>();

    /** A list of timer instances */
    private List<TimerInstance> timerInstances = new ArrayList<TimerInstance>();

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
            .map(p -> {
                Object v = p.getValue();      // if it's already String, this is fine; otherwise toString()
                return v == null ? null : StringUtil.removeQuotes(v.toString());
            })
            .filter(Objects::nonNull)
            .findFirst();
    }

    protected void generateMaudeTest() {
        code.pr("omod TEST-"+this.main.getName().toUpperCase() +" is");
        code.indent();
        code.pr("including " + this .main.getName().toUpperCase() + " .");
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
                throw new RuntimeException("Physical actions require a environment definition using @maudePhysAct()");
            }

            code.pr(builder.toString());
            code.indent();

            Map<String, Map<String, Attribute>> attrsByReactorThenName = new HashMap<>();

            for (Attribute prop: this.maudePhysActProperties) {
                String _reactor = getParam(prop, "inReactor").orElseThrow(
                    () -> new IllegalArgumentException("Attribute 'reactor' missing for physicalAction property")
                );
                String _name = getParam(prop, "name").orElseThrow(
                    () -> new IllegalArgumentException("Attribute 'name' missing for physicalAction property")
                );
                Map<String, Attribute> byName = attrsByReactorThenName.computeIfAbsent(_reactor, k -> new HashMap<>());

                // Fail on duplicates of the same (reactor, name)
                Attribute prev = byName.put(_name, prop);
                if (prev != null) {
                    throw new IllegalStateException(
                        "Duplicate Attribute for reactor=" + _reactor + ", name=" + _name
                    );
                }
            }

            // Validate all physActs and fill
            for (MaudeActionInstance act: this.maudePhysicalActionInstances) {

                String reactorname = act.getParent().lfReactor.getName();
                Map<String, Attribute> byName = attrsByReactorThenName.get(act.getParent().lfReactor.getName());
                if (byName == null) {
                    throw new RuntimeException("Missing Attribute for reactor=" + act.getParent().lfReactor.getName()
                        + ", name=" + act.lfAction.getName());
                }

                Attribute match = byName.get(act.lfAction.getName());

                if (match == null) {
                    throw new RuntimeException("Missing Attribute for reactor=" + act.getParent().lfReactor.getName()
                        + ", name=" + act.lfAction.getName());
                }

                String vals = getParam(match, "vals").orElseThrow(
                    () -> new IllegalArgumentException("Attribute 'vals' missing for physicalAction property "+getParam(match, "name").orElse("") )
                );

                int period = Integer.parseInt(getParam(match, "period").orElse("0"));
                if (period <= 0) { period = 0 ;}

                Boolean timeNonDet = Boolean.parseBoolean(getParam(match, "timeNonDet").orElse("true"));

//
//
//            //for (Attribute prop : this.maudePhysActProperties) {
//                    String name =
//                        StringUtil.removeQuotes(
//                            prop.getAttrParms().stream()
//                                .filter(attr -> attr.getName().equals("name"))
//                                .findFirst()
//                                .get()
//                                .getValue());
//                    String vals =
//                        StringUtil.removeQuotes(
//                            prop.getAttrParms().stream()
//                                .filter(attr -> attr.getName().equals("vals"))
//                                .findFirst()
//                                .get()
//                                .getValue());
//
//                    // What is unit for period? We need to transform it to nanoseconds
//                    int period = Integer.parseInt(
//                        StringUtil.removeQuotes(
//                            prop.getAttrParms().stream()
//                                .filter(attr -> attr.getName().equals("period"))
//                                .findFirst()
//                                .get()
//                                .getValue()));
//                    if (period<0) period = 0;
//
//                    Boolean timeNonDet = true;
//                    Optional<AttrParm> timeNonDetParam =
//                        prop.getAttrParms().stream().filter(attr -> attr.getName().equals("timeNonDet")).findFirst();
//                    if (timeNonDetParam.isPresent()) {
//                        timeNonDet = Boolean.parseBoolean(timeNonDetParam.get().getValue());
//                    }

                    builder = new StringBuilder();
                    builder.append("< (" + act.getParent().getName() + " . " + act.getName() + " ): PhysAct | ");
                    builder.append("leftOfPeriod : "+period+", period : "+period+", possibleValues : "+vals //+"[0] : [1], "
                        + ", timeNonDet : "+timeNonDet+" >");
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
        }
        else if (havestartup) {
          builder.append("addStartup(startup, init, empty)");
        }
        else
          builder.append("empty");

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
            if (reactor.lfReactor.isMainOrFederated())
                continue;

            code.indent();
            code.pr("< "+reactor.getName()+" : Reactor |");
            // generate reactor attributes
            code.indent();
            generateInports(reactor);
            code.insert(code.length()-1, ",");
            generateOutports(reactor);
            code.insert(code.length()-1, ",");
            generateStates(reactor);
            code.insert(code.length()-1, ",");
            generateTimers(reactor);
            code.insert(code.length()-1, ",");
            generateActions(reactor);
            code.insert(code.length()-1, ",");
            generateReactions(reactor);
            code.unindent();
            code.pr(">");
            code.unindent();
        }

        generateConnections();
        code.pr(".");
    }

    protected void generateConnections() {
        //generate connections
        for (var port : this.maudePortInstances) {
            for (SendRange range : port.getLfPort().getDependentPorts()) {
                MaudePortInstance mSource = port.parent.getMaudePort(range.instance);
                Connection connection = range.connection;
                List<RuntimeRange<PortInstance>> destinations = range.destinations;

                // Extract delay value
                long delay = 0;
                if (connection.getDelay() != null) {
                    // Somehow delay is an Expression,
                    // which makes it hard to convert to nanoseconds.
                    Expression delayExpr = connection.getDelay();
                    if (delayExpr instanceof Time) {
                        long interval = ((Time) delayExpr).getInterval();
                        String unit = ((Time) delayExpr).getUnit();
                        TimeValue timeValue = new TimeValue(interval, TimeUnit.fromName(unit));
                        delay = timeValue.toNanoSeconds();
                    }
                }

                for (var portRange : destinations) {
                    var destination = portRange.instance;
                    MaudePortInstance mDestination = null;
                    for (var reactor : this.maudeReactorInstances) {
                        if (reactor.getMaudePort(destination) != null) {
                            mDestination = reactor.getMaudePort(destination);
                            break;
                        }
                    }
                    if (mDestination == null)
                        throw new RuntimeException("Could not find Maude port corresponding to"
                            + " destination port " + destination.getName());
                    StringBuilder builder = new StringBuilder();
                    builder.append("(" + mSource.getParent().getName() + " : " + mSource.getName());
                    if (delay > 0) {
                        builder.append(" -- " + delay);
                    }
                    builder.append(" --> " + mDestination.getParent().getName() + " : " + mDestination.getName() + ")");
                    code.pr(builder);

                }
            }
        }
    }

    protected void generateReactions(MaudeReactorInstance mReactor) {
        StringBuilder builder = new StringBuilder();
        builder.append("reactions : ");
        if (mReactor.reactions.isEmpty()) {
            throw new RuntimeException("No reactions found for reactor " + mReactor.getName());
        }
        else {
            code.pr(builder.toString());
            code.indent();

            for (var reaction : mReactor.reactions) {

                builder = new StringBuilder();
                builder.append("(reaction when (" + reaction.getTriggers().get(0).getName());

                for (var trigger : reaction.getTriggers().stream().skip(1).toList())
                    builder.append(" ; " + trigger.getName() );

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

                CToMaudeVisitor c2mVisitor = new CToMaudeVisitor(this, reaction);

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
        }
        else{
            code.pr(builder.toString());
            code.indent();
            for (var logicalAction : mReactor.logicalActions) {
                builder = new StringBuilder();
                builder.append("< " + logicalAction.getName() + " : LogicalAction | minDelay : " +
                    logicalAction.minDelay + ", minSpacing : " + logicalAction.minSpacing +
                        ", policy : " + logicalAction.policy + ", payload : [" + logicalAction.payload +  "] >");
                code.pr(builder.toString());
            }
            for (var physicalAction : mReactor.physicalActions) {
                builder = new StringBuilder();
                builder.append("< " + physicalAction.getName() + " : PhysicalAction | minDelay : " +
                    physicalAction.minDelay + ", minSpacing : " + physicalAction.minSpacing +
                    ", policy : " + physicalAction.policy + ", payload : [" + physicalAction.payload +  "] >");
                code.pr(builder.toString());
            }
            code.unindent();
        }
    }

    protected void generateTimers(MaudeReactorInstance mReactor) {
        StringBuilder builder = new StringBuilder();
        builder.append("timers : ");
        if (mReactor.timers.isEmpty()) {
            builder.append("none");
            code.pr(builder.toString());
        }
        else{
            code.pr(builder.toString());
            code.indent();
            for (var timer : mReactor.timers) {
                builder = new StringBuilder();
                builder.append("< " + timer.getName() + " : Timer | offset : " + timer.getOffset() +
                    ", period : " + timer.getPeriod() + " >");
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
            //first element separately
            var sv = mReactor.stateVars.get(0);
            builder.append("( " +sv.getName() +" |-> [" + sv.value + "] )");
            code.pr(builder.toString());

            for (var statevar : mReactor.stateVars. stream().skip(1).toList()) {
                code.insert(code.length()-1, ";");
                builder = new StringBuilder();
                builder.append("( " + statevar.getName() + " |-> [" + statevar.value +"] )");

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
        }
        else{
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
        }
        else{
            code.pr(builder.toString());
            code.indent();
            for (var outport : mReactor.outPorts) {
                builder = new StringBuilder();
                builder.append("< " + outport.getName() + " : Port | value : ["  + outport.value + "] >");

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
            code.pr("op "+reactor.getName() + " : -> ReactorId [ctor] .");
        }
    }

    protected void generateStateVariables() {
        for (var stateVariable : this.maudeStateInstances) {
            code.pr("op "+stateVariable.getName() + " : -> " + stateVariable.getType() + " [ctor] .");
        }
    }

    protected void generatePortVariables() {
        for (var portVariable : this.maudePortInstances) {
            code.pr("op "+portVariable.getName() + " : -> " + portVariable.getType() + " [ctor] .");
        }
    }

    protected void generateTimerVariables() {
        for (var timerVariable : this.maudeTimerInstances) {
            code.pr("op "+timerVariable.getName()+ " : -> TimerId [ctor] .");
        }
    }

    protected void generateActionVariables() {
        for (var actionVariable : this.maudeActionInstances) {
            code.pr("op "+actionVariable.getName() + " : -> " + actionVariable.getType() +" [ctor] .");
        }
    }

    protected void generateAnalysis() {
        code.pr("omod ANALYSIS-"+this.main.getName().toUpperCase() +" is");
        code.indent();
        code.pr("including TEST-" + this .main.getName().toUpperCase() + " .");
        code.pr("including LF-PROP .");
        code.pr("including SEARCH-GOAL .");
        code.unindent();
        code.pr("endom");
        code.pr("");

        code.pr("omod MODELCHECKER-"+this.main.getName().toUpperCase() +" is");
        code.indent();
        code.pr("including TEST-" + this.main.getName().toUpperCase() + " .");
        code.pr("including LF-PROP .");
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

        for (Attribute prop : this.maudeProperties) {
            String analysis = // so far this has fixed value "reachability"
                StringUtil.removeQuotes(
                    prop.getAttrParms().stream()
                        .filter(attr -> attr.getName().equals("analysis"))
                        .findFirst()
                        .get()
                        .getValue());

            String goal = "";
            Optional<AttrParm> goalParam = prop.getAttrParms().stream()
                .filter(attr -> attr.getName().equals("goal"))
                .findFirst();

            if (goalParam.isPresent()) {
                goal = StringUtil.removeQuotes(goalParam.get().getValue());
            }


            String timeBound = "INF";
            Optional<AttrParm> timeBoundParam =
                prop.getAttrParms().stream().filter(attr -> attr.getName().equals("timeBound")).findFirst();
            if (timeBoundParam.isPresent()) {
                // What is unit for timeBound? We need to transform it to nanoseconds
                timeBound = timeBoundParam.get().getValue();
            }

            String rewrites = "";
            Optional<AttrParm> rewritesParam =
                prop.getAttrParms().stream().filter(attr -> attr.getName().equals("rewrites")).findFirst();
            if (rewritesParam.isPresent()) {
                rewrites = rewritesParam.get().getValue();
                if (Integer.parseInt(rewrites) < 1)
                    throw new RuntimeException("rewrites must be greater than 0");
            }

            String mode = "*";
            Optional<AttrParm> modeParam =
                prop.getAttrParms().stream().filter(attr -> attr.getName().equals("mode")).findFirst();
            if (modeParam.isPresent()) {
                mode = modeParam.get().getValue();
            }

            if (analysis.equalsIgnoreCase("reachability")) {

                if (goal.isEmpty())
                    throw new RuntimeException("Reachability analysis requires goal to be defined!");

                StringBuilder builder = new StringBuilder();
                builder.append("search [1");
                if (!rewrites.isEmpty()) {
                    builder.append("," + rewrites);
                }
                builder.append("] in ANALYSIS-"+this.main.getName().toUpperCase() + " : initSystem timeBound ");
                // Decide what kind of analysis to do

                builder.append(timeBound);

                builder.append(" =>"+mode+" {C:Configuration} timeBound TI:TimeInf");
                LTLLexer lexer = new LTLLexer(CharStreams.fromString(goal));
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                LTLParser parser = new LTLParser(tokens);
                LtlContext ltlCtx = parser.ltl();
                LTLVisitor visitor = new LTLVisitor(this.maudeReactorInstances);

                String genGoal = visitor.visitLtl(ltlCtx);
                builder.append(" such that {C:Configuration} |= "+genGoal + " .");
                code.pr(builder.toString());
                code.pr("");
            }
            else if (analysis.equalsIgnoreCase("ltl")) {
                if (goal.isEmpty())
                    throw new RuntimeException("LTL analysis requires goal to be defined!");

                StringBuilder builder = new StringBuilder();
                builder.append("red in MODELCHECKER-"+this.main.getName().toUpperCase()+" : modelCheck(initSystem timeBound "+timeBound);

                LTLLexer lexer = new LTLLexer(CharStreams.fromString(goal));
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                LTLParser parser = new LTLParser(tokens);
                LtlContext ltlCtx = parser.ltl();
                LTLVisitor visitor = new LTLVisitor(this.maudeReactorInstances);

                String genGoal = visitor.visitLtl(ltlCtx);
                builder.append(" , "+genGoal + " ) .");
                code.pr(builder.toString());
                code.pr("");

            }
            else if (analysis.equalsIgnoreCase("simulation")) {
                StringBuilder builder = new StringBuilder();
                builder.append("rew ");
                if (!rewrites.isEmpty()) {
                    builder.append("[" + rewrites+"] ");
                }

                builder.append("in SIMULATION-"+this.main.getName().toUpperCase()+" : initSystem timeBound "+timeBound+ " .");
                code.pr(builder.toString());
                code.pr("");
            }

        }
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
        // Populate lists of reactor/reaction instances,
        // state variables, actions, ports, and timers.
        populateLists(this.main);

        // Join actions, ports, and timers into a list of triggers.
        this.triggerInstances = new ArrayList<TriggerInstance>(this.actionInstances);
        this.triggerInstances.addAll(portInstances);
        this.triggerInstances.addAll(timerInstances);

        // Join state variables and triggers
        this.namedInstances = new ArrayList<NamedInstance>(this.stateVariables);
        namedInstances.addAll(this.triggerInstances);
    }

    private void populateLists(ReactorInstance reactor) {
        // Reactor and reaction instances
        this.reactorInstances.add(reactor);

        MaudeReactorInstance maudeReactor = new MaudeReactorInstance(reactor);
        this.maudeReactorInstances.add(maudeReactor);


        for (var reaction : reactor.reactions) {
            this.reactionInstances.addAll(reaction.getRuntimeInstances());
        }
        // TODO: confirm that getRuntimeInstances() returns the same reaction instance if there
        //  are no banks/nested reactors/reactions
        this.maudeReactionInstances.addAll(maudeReactor.reactions);

        this.stateVariables.addAll(reactor.states);
        this.maudeStateInstances.addAll(maudeReactor.stateVars);

        this.actionInstances.addAll(reactor.actions);
        this.maudeActionInstances.addAll(maudeReactor.logicalActions);
        this.maudeActionInstances.addAll(maudeReactor.physicalActions);
        this.maudePhysicalActionInstances.addAll(maudeReactor.physicalActions);

        this.inputInstances.addAll(reactor.inputs);
        this.portInstances.addAll(reactor.inputs);
        this.maudePortInstances.addAll(maudeReactor.inPorts);

        this.outputInstances.addAll(reactor.outputs);
        this.portInstances.addAll(reactor.outputs);
        this.maudePortInstances.addAll(maudeReactor.outPorts);

        this.timerInstances.addAll(reactor.timers);
        this.maudeTimerInstances.addAll(maudeReactor.timers);

        this.maudeTriggerInstances.addAll(maudeReactor.triggers);

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
