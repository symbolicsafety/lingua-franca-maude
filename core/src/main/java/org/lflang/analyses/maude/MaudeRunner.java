package org.lflang.analyses.maude;

import java.nio.file.*;
import java.nio.file.Path;
import java.util.List;

import org.lflang.MessageReporter;
import org.lflang.generator.GeneratorCommandFactory;
import org.lflang.util.LFCommand;

public class MaudeRunner {
    /** A factory for compiler commands. */
    GeneratorCommandFactory commandFactory;

    /** A MaudeGenerator instance */
    MaudeGenerator generator;

    MessageReporter reporter;

    public MaudeRunner(MaudeGenerator generator) {
        this.generator = generator;
        this.reporter = generator.context.getErrorReporter();
        this.commandFactory =
            new GeneratorCommandFactory(
                generator.context.getErrorReporter(), generator.context.getFileConfig());
    }
/*
* Mikheil: we need to consider that our implementation needs Maude 3.3.1.
*         In my machine "maude" command runs "default" installation of Maude 3.2
*         I think it is best to have MAUDE_BASE environment variable pointing to Maude 3.3.1 folder
*         I modified the implementation accordingly
 */
    public Path getEnvironmentVariable(String envVarName, String fileNameToExists) {
        String envVar = System.getenv(envVarName);
        if (envVar == null || envVar.isBlank()) {
            throw new IllegalStateException("Environment variable "+envVarName+" is not set.");
        }
        Path envPath = Paths.get(envVar);

        if (!Files.exists(envPath)) {
            throw new IllegalStateException(envVarName+" pointing to path does not exist: " + envVar);
        }
        if (!Files.isDirectory(envPath)) {
            throw new IllegalStateException(envVarName+" not pointing to a directory: " + envVar);
        }
        if (!Files.isReadable(envPath)) {
            throw new IllegalStateException(envVarName+" pointing to path is not readable: " + envVar);
        }
        Path fullPath = envPath.resolve(fileNameToExists);
        if (!Files.exists(fullPath)) {
            throw new IllegalStateException("Required file not found: " + fullPath
                + ". Please make sure that LF_MAUDE_BASE is pointing to the LF Maude implementation "
                + "and MAUDE_BASE is ponting to Maude 3.3.1.");
        }
        if (!Files.isReadable(fullPath)) {
            throw new IllegalStateException("Required file is not readable: " + fullPath);
        }

        return fullPath;
    }

    public void run() {
        Path lf_maudeBase =getEnvironmentVariable("LF_MAUDE_BASE","lf-main-concrete.maude");
        Path maudeBase =getEnvironmentVariable("MAUDE_BASE","maude");

        for (Path path : generator.generatedFiles) {
            LFCommand command =
                commandFactory.createCommand(
                    maudeBase.toString(),
                    List.of(
                        lf_maudeBase.toString(),
                        path.toString(),
                        "-xml-log="+path.toString()+".xml"
                    ),
                    generator.outputDir);
            command.run();

        }
    }
}
