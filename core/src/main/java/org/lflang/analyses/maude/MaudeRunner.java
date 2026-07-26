package org.lflang.analyses.maude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.lflang.MessageReporter;
import org.lflang.generator.GeneratorCommandFactory;
import org.lflang.util.LFCommand;

/** Runs generated Maude verification models. */
public class MaudeRunner {

  private static final String MAUDE_BASE = "MAUDE_BASE";
  private static final String LF_MAUDE_BASE = "LF_MAUDE_BASE";
  private static final String MAUDE_EXECUTABLE = "maude";
  private static final String LF_MAUDE_ENTRY_POINT = "lf-main-concrete.maude";

  /** A factory for compiler commands. */
  private final GeneratorCommandFactory commandFactory;

  /** The generator that produced the models to run. */
  private final MaudeGenerator generator;

  private final MessageReporter reporter;

  public MaudeRunner(MaudeGenerator generator) {
    this.generator = generator;
    this.reporter = generator.context.getErrorReporter();
    this.commandFactory =
        new GeneratorCommandFactory(
            generator.context.getErrorReporter(), generator.context.getFileConfig());
  }

  /**
   * Resolve the executable and semantics library used for verification.
   *
   * <p>{@code MAUDE_BASE} is an optional explicit override. If it is absent, the executable is
   * resolved from {@code PATH} when the command is created. {@code LF_MAUDE_BASE} is required
   * because the LF-Maude semantics library is distributed separately from Maude.
   */
  static MaudeDependencies resolveDependencies(Map<String, String> environment) {
    Path lfMaudeBase = requireDirectory(environment, LF_MAUDE_BASE);
    Path lfMaudeEntryPoint = requireReadableFile(lfMaudeBase, LF_MAUDE_ENTRY_POINT);

    String maudeBaseValue = environment.get(MAUDE_BASE);
    if (maudeBaseValue == null || maudeBaseValue.isBlank()) {
      return new MaudeDependencies(MAUDE_EXECUTABLE, lfMaudeEntryPoint);
    }

    Path maudeBase = requireDirectory(environment, MAUDE_BASE);
    Path maudeExecutable = requireExecutableFile(maudeBase, MAUDE_EXECUTABLE);
    return new MaudeDependencies(maudeExecutable.toString(), lfMaudeEntryPoint);
  }

  private static Path requireDirectory(Map<String, String> environment, String variable) {
    String value = environment.get(variable);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Environment variable " + variable + " is not set.");
    }

    Path directory = Path.of(value).toAbsolutePath().normalize();
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException(
          variable + " does not point to an existing directory: " + directory);
    }
    if (!Files.isReadable(directory)) {
      throw new IllegalArgumentException(variable + " is not readable: " + directory);
    }
    return directory;
  }

  private static Path requireReadableFile(Path directory, String filename) {
    Path file = directory.resolve(filename);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("Required file not found: " + file);
    }
    if (!Files.isReadable(file)) {
      throw new IllegalArgumentException("Required file is not readable: " + file);
    }
    return file;
  }

  private static Path requireExecutableFile(Path directory, String filename) {
    Path file = requireReadableFile(directory, filename);
    if (!Files.isExecutable(file)) {
      throw new IllegalArgumentException("Required file is not executable: " + file);
    }
    return file;
  }

  public void run() {
    MaudeDependencies dependencies;
    try {
      dependencies = resolveDependencies(System.getenv());
    } catch (IllegalArgumentException exception) {
      reporter.nowhere().error("Cannot run Maude verification: " + exception.getMessage());
      return;
    }

    String maudeVerbose = generator.context.getArgs().maudeVerbose();
    Path maudeVerboseFile = generator.context.getArgs().maudeVerboseFile();

    if (maudeVerboseFile != null && maudeVerbose == null) {
      reporter
          .nowhere()
          .warning("Ignoring --maude-verbose-file because --maude-verbose was not provided.");
      maudeVerboseFile = null;
    }

    if (maudeVerboseFile != null) {
      try {
        Path parent = maudeVerboseFile.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
      } catch (IOException exception) {
        throw new RuntimeException("Failed to create Maude verbose output directory.", exception);
      }
    }

    for (Path path : generator.generatedFiles) {
      LFCommand command =
          commandFactory.createCommand(
              dependencies.executable(),
              List.of(dependencies.semanticsLibrary().toString(), path.toString()),
              generator.outputDir);
      if (command == null) {
        return;
      }
      if (maudeVerboseFile != null) {
        command.redirectErrorsTo(maudeVerboseFile);
      }
      runCommand(command, path, reporter);
    }
  }

  static boolean runCommand(LFCommand command, Path model, MessageReporter reporter) {
    int exitCode = command.run();
    if (exitCode == 0) {
      return true;
    }

    reporter.at(model).error("Maude failed for " + model + " with exit code " + exitCode + ".");
    return false;
  }

  record MaudeDependencies(String executable, Path semanticsLibrary) {}
}
