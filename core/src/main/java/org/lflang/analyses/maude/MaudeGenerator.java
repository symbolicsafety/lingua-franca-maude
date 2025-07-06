package org.lflang.analyses.maude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.emf.ecore.resource.Resource;

import org.lflang.TimeUnit;
import org.lflang.TimeValue;
import org.lflang.analyses.c.BuildAstParseTreeVisitor;
import org.lflang.analyses.c.CAst;
import org.lflang.analyses.c.CToMaudeVisitor;
import org.lflang.analyses.c.IfNormalFormAstVisitor;
import org.lflang.analyses.c.VariablePrecedenceVisitor;
import org.lflang.ast.ASTUtils;
import org.lflang.dsl.CParser.BlockItemListContext;
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
import org.lflang.lf.Connection;
import org.lflang.lf.Expression;
import org.lflang.lf.Time;
import org.lflang.target.Target;

/** (EXPERIMENTAL) Generator for Maude models. */
public class MaudeGenerator extends GeneratorBase {

    public List<MaudeReactorInstance> maudeReactorInstances = new ArrayList<>();
    private List<MaudePortInstance> maudePortInstances = new ArrayList<>();
    private List<MaudeReactionInstance> maudeReactionInstances = new ArrayList<>();
    private List<MaudeActionInstance> maudeActionInstances = new ArrayList<>();
    private List<MaudeActionInstance> maudePhysicalActionInstances = new ArrayList<>();
    private List<MaudeTimerInstance> maudeTimerInstances = new ArrayList<>();
    private List<MaudeStateInstance> maudeStateInstances = new ArrayList<>();
    public List<MaudeTriggerInstance> maudeTriggerInstances = new ArrayList<>(); // Triggers = ports + actions + timers


    /**
     * Create a new GeneratorBase object.
     *
     * @param context
     */
    public MaudeGenerator(LFGeneratorContext context) {
        super(context);
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
                "/*******************************",
                " * Auto-generated Maude model *",
                " ******************************/"));

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
            code.pr(builder.toString());
            code.indent();
            for (var physicalAction : this.maudePhysicalActionInstances) {
                builder = new StringBuilder();
                builder.append("< (" + physicalAction.getParent().getName() + " . " + physicalAction.getName() + " : PhysAct | ");
                builder.append("leftOfPeriod : 0, period : 0, possibleValues : [0] : [1], timeNonDet : [true] >");
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
            builder.append("empty) >");
          }
        }
        else if (havestartup) {
          builder.append("addStartup(startup, init, empty");
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

                builder.append(") do {");
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

                // VariablePrecedenceVisitor
               // VariablePrecedenceVisitor precVisitor = new VariablePrecedenceVisitor();
               // precVisitor.visit(ast);

                // Convert the AST to If Normal Form (INF).
               // IfNormalFormAstVisitor infVisitor = new IfNormalFormAstVisitor();
               // infVisitor.visit(ast, new ArrayList<CAst.AstNode>());
                //CAst.StatementSequenceNode inf = infVisitor.INF;

                CToMaudeVisitor c2mVisitor = new CToMaudeVisitor(this, reaction);

                String output = c2mVisitor.visit(ast);
                code.pr(output);
                code.unindent();
                code.pr("}");


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
                        ", policy: " + logicalAction.policy + ", payload : [" + logicalAction.payload +  "] >");
                code.pr(builder.toString());
            }
            for (var physicalAction : mReactor.physicalActions) {
                builder = new StringBuilder();
                builder.append("< " + physicalAction.getName() + " : PhysicalAction | minDelay : " +
                    physicalAction.minDelay + ", minSpacing : " + physicalAction.minSpacing +
                    ", policy: " + physicalAction.policy + ", payload : [" + physicalAction.payload +  "] >");
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
                builder.append("< " + inport.getName() + " : Port | value : " + inport.value + " >");
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
                builder.append("< " + outport.getName() + " : Port | value : "  + outport.value + " >");

                code.pr(builder.toString());
            }
            code.unindent();
        }

    }


    protected void generateIdentifiers() {
        //generateReactorIdentifiers();
        generateStateVariables();
        generatePortVariables();
        generateTimerVariables();
        generateActionVariables();

        code.pr("op init : -> Configuration .\n\n");

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
        System.out.println("The models will be located in: " + outputDir);
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
