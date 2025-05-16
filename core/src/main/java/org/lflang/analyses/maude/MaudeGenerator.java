package org.lflang.analyses.maude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;

import org.lflang.ast.ASTUtils;
import org.lflang.generator.ActionInstance;
import org.lflang.generator.CodeBuilder;
import org.lflang.generator.GeneratorBase;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.generator.NamedInstance;
import org.lflang.generator.PortInstance;
import org.lflang.generator.ReactionInstance.Runtime;
import org.lflang.generator.ReactorInstance;
import org.lflang.generator.StateVariableInstance;
import org.lflang.generator.TargetTypes;
import org.lflang.generator.TimerInstance;
import org.lflang.generator.TriggerInstance;
import org.lflang.generator.docker.DockerGenerator;
import org.lflang.lf.Reactor;
import org.lflang.target.Target;
import org.lflang.analyses.c.CToMaudeVisitor;

/** (EXPERIMENTAL) Generator for Maude models. */
public class MaudeGenerator extends GeneratorBase {

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
        code.pr("including LF-REPR .\n");
        code.pr("protecting LF-VALUE-TIME .\n");
        code.pr("");

        generateIdentifiers();
        generateInitConfiguration();

        code.pr("endom");
    }

    protected void generateInitConfiguration() {
        code.pr("eq init = ");

        for (var reactor : this.reactorInstances) {
            if (reactor.isMainOrFederated())
                continue;

            code.indent();
            code.pr("< "+reactor.getName().replaceAll("_", "")+" : Reactor |");
            // generate reactor attributes
            code.indent();
            generateInports(reactor.reactorDefinition, reactor);
            code.insert(code.length()-1, ",");
            generateOutports(reactor.reactorDefinition, reactor);
            code.insert(code.length()-1, ",");
            generateStates(reactor.reactorDefinition, reactor);
//      generateTimers(reactor.reactorDefinition, reactor);
//      generateActions(reactor.reactorDefinition, reactor);
//      generateReactions(reactor.reactorDefinition, reactor);
            code.unindent();
            code.pr(">");
            code.unindent();
        }
    }

    protected void generateStates(Reactor reactor, ReactorInstance ri) {
        StringBuilder builder = new StringBuilder();
        builder.append("state : ");
        if (reactor.getStateVars().isEmpty()) {
            builder.append("empty,");
            code.pr(builder.toString());
        } else {
            code.pr(builder.toString());
            code.indent();
            //first element separately

//            builder = new StringBuilder();
//            var sv = reactor.getStateVars().get(0);
//            //builder.append("( "+ri.getName().replaceAll("_","")+"."+sv.getName().replaceAll("_", "")+" |-> ");
//            builder.append("["+sv.getInit().getExpr()+"])");
//
//
//            builder.append(" )");
//            code.pr(builder.toString());
//            for (var statevar : reactor.getStateVars().stream().skip(1).toList()) {
            for (var statevar : reactor.getStateVars()) {
                code.insert(code.length()-1, ";");
                builder = new StringBuilder();
                builder.append("( "+ri.getName().replaceAll("_","")+"."+statevar.getName().replaceAll("_", "")+" |->");
                builder.append("["+statevar.getInit().getExpr().toString()+"])");

                code.pr(builder.toString());
            }
            code.unindent();
        }
    }

    protected void generateInports(Reactor reactor, ReactorInstance ri) {
        StringBuilder builder = new StringBuilder();
        builder.append("inports : ");
        if (reactor.getInputs().isEmpty()) {
            builder.append("none,");
            code.pr(builder.toString());
        }
        else{
            code.pr(builder.toString());
            code.indent();
            for (var inport : reactor.getInputs()) {
                builder = new StringBuilder();
                builder.append("< "+ri.getName().replaceAll("_","")+"."+inport.getName().replaceAll("_", "")+" : Port | value : ");
                if (inport.getType().getId().equals("bool"))
                    builder.append("[false]");
                else builder.append("[0]");

                builder.append(" >");
                code.pr(builder.toString());
            }
            code.unindent();
        }

    }

    protected void generateOutports(Reactor reactor, ReactorInstance ri) {
        StringBuilder builder = new StringBuilder();
        builder.append("outports : ");
        if (reactor.getOutputs().isEmpty()) {
            builder.append("none,");
            code.pr(builder.toString());
        }
        else{
            code.pr(builder.toString());
            code.indent();
            for (var outport : reactor.getOutputs()) {
                builder = new StringBuilder();
                builder.append("< "+ri.getName().replaceAll("_","")+"."+outport.getName().replaceAll("_", "")+" : Port | value : ");
                if (outport.getType().getId().equals("bool"))
                    builder.append("[false]");
                else builder.append("[0]");

                builder.append(" >");
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
        StringBuilder builder = new StringBuilder();
        builder.append("ops");
        for (var reactor : this.reactorInstances) {
            if (reactor.isMainOrFederated())
                continue;

            builder.append(" ");
            builder.append(reactor.getName().replaceAll("_", ""));
        }
        builder.append(" : -> ReactorId [ctor] .");
        code.pr(builder.toString());
    }

    protected void generateStateVariables() {
        for (var stateVariable : this.stateVariables) {

            StringBuilder builder = new StringBuilder();
            builder.append("op");
            builder.append(" "+stateVariable.getParent().getName().replaceAll("_", "")+"."+stateVariable.getName().replaceAll("_", ""));
            if (stateVariable.getDefinition().getType().getId().equals("bool"))
                builder.append(" : -> BVarId [ctor] .");
            else
                builder.append(" : -> RVarId [ctor] .");
            code.pr(builder.toString());
        }
    }

    protected void generatePortVariables() {
        for (var portVariable : this.portInstances) {
            StringBuilder builder = new StringBuilder();
            builder.append("op");
            builder.append(" "+portVariable.getParent().getName().replaceAll("_", "")+"."+portVariable.getName().replaceAll("_", ""));
            if (portVariable.getDefinition().getType().getId().equals("bool"))
                builder.append(" : -> BPortId [ctor] .");
            else
                builder.append(" : -> RPortId [ctor] .");
            code.pr(builder.toString());
        }
    }

    protected void generateTimerVariables() {
        for (var timerVariable : this.timerInstances) {
            StringBuilder builder = new StringBuilder();
            builder.append("op");
            builder.append(" "+timerVariable.getParent().getName().replaceAll("_", "")+"."+timerVariable.getName().replaceAll("_", ""));
            builder.append(" : -> TimerId [ctor] .");
            code.pr(builder.toString());
        }
    }

    protected void generateActionVariables() {
        for (var actionVariable : this.actionInstances) {
            StringBuilder builder = new StringBuilder();
            builder.append("op");
            builder.append(" "+actionVariable.getParent().getName().replaceAll("_", "")+"."+actionVariable.getName().replaceAll("_", ""));
            //FIXME: add something to distinguish between boolean and integer actions
            builder.append(" : -> ActionId [ctor] .");
            code.pr(builder.toString());
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
        for (var reaction : reactor.reactions) {
            this.reactionInstances.addAll(reaction.getRuntimeInstances());
        }

        // State variables, actions, ports, timers.
        for (var state : reactor.states) {
            this.stateVariables.add(state);
        }
        for (var action : reactor.actions) {
            this.actionInstances.add(action);
        }
        for (var port : reactor.inputs) {
            this.inputInstances.add(port);
            this.portInstances.add(port);
        }
        for (var port : reactor.outputs) {
            this.outputInstances.add(port);
            this.portInstances.add(port);
        }
        for (var timer : reactor.timers) {
            this.timerInstances.add(timer);
        }

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