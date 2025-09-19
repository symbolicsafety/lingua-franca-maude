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

    public void run() {
        String _lf_maudeBase = System.getenv("LF_MAUDE_BASE");
        if (_lf_maudeBase == null || _lf_maudeBase.isBlank()) {
            throw new IllegalStateException("Environment variable LF_MAUDE_BASE is not set.");
        }

        Path maudeBase = Paths.get(_lf_maudeBase);

        if (!Files.exists(maudeBase)) {
            throw new IllegalStateException("LF_MAUDE_BASE does not exist: " + maudeBase);
        }
        if (!Files.isDirectory(maudeBase)) {
            throw new IllegalStateException("LF_MAUDE_BASE is not a directory: " + maudeBase);
        }
        if (!Files.isReadable(maudeBase)) {
            throw new IllegalStateException("LF_MAUDE_BASE is not readable: " + maudeBase);
        }
        Path lfMain = maudeBase.resolve("lf-main-concrete.maude");
        if (!Files.exists(lfMain)) {
            throw new IllegalStateException("Required file not found: " + lfMain);
        }
        if (!Files.isReadable(lfMain)) {
            throw new IllegalStateException("Required file is not readable: " + lfMain);
        }


        for (Path path : generator.generatedFiles) {
            LFCommand command =
                commandFactory.createCommand(
                    "maude",
                    List.of(
                        lfMain.toString(),
                        path.toString(),
                        "-xml-log="+path.toString()+".xml"
                    ),
                    generator.outputDir);
            command.run();

        }
    }
}
