package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.inject.Injector;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.xtext.generator.JavaIoFileSystemAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lflang.AttributeUtils;
import org.lflang.DefaultMessageReporter;
import org.lflang.FileConfig;
import org.lflang.LFRuntimeModule;
import org.lflang.LFStandaloneSetup;
import org.lflang.ast.ASTUtils;
import org.lflang.generator.GeneratorArguments;
import org.lflang.generator.GeneratorBase;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.generator.MainContext;
import org.lflang.generator.TargetTypes;
import org.lflang.generator.c.CTypes;
import org.lflang.generator.docker.DockerGenerator;
import org.lflang.lf.Attribute;
import org.lflang.lf.Reactor;
import org.lflang.target.Target;
import org.lflang.target.property.VerifyProperty;

class MaudeRegressionTest {

  private static final Path SOURCE_DIR = Path.of("test", "Maude", "src");
  private static final Path EXPECTED_DIR = Path.of("test", "Maude", "expected");

  private final Injector injector =
      new LFStandaloneSetup(new LFRuntimeModule()).createInjectorAndDoEMFRegistration();
  private final Provider<ResourceSet> resourceSetProvider = injector.getProvider(ResourceSet.class);
  private final JavaIoFileSystemAccess fileAccess =
      injector.getInstance(JavaIoFileSystemAccess.class);

  @TempDir Path tempDir;

  @Test
  void generatedModelsMatchGoldenFiles() throws IOException {
    var sources = filesWithExtension(SOURCE_DIR, ".lf");
    var expected = filesWithExtension(EXPECTED_DIR, ".maude");

    assertEquals(
        sources.stream().map(MaudeRegressionTest::stem).toList(),
        expected.stream().map(MaudeRegressionTest::stem).toList(),
        "Each LF regression fixture must have exactly one Maude golden file");

    for (Path source : sources) {
      Path generated = generateModel(source);
      Path golden = EXPECTED_DIR.resolve(stem(source) + ".maude");
      assertEquals(
          normalize(Files.readString(golden)),
          normalize(Files.readString(generated)),
          () -> "Generated Maude model differs from " + golden);
    }
  }

  @Test
  void maudeExecutionIsSkippedWhenVerifyPropertyIsFalse() throws IOException {
    Path source = SOURCE_DIR.resolve("MainReactorComponents.lf");

    var result = runVerificationHook(source, "verification-disabled", false);
    assertTrue(Files.isRegularFile(result.generatedModel()));
    assertFalse(
        result.context().getErrorReporter().getErrorsOccurred(),
        "Generating a Maude model without verification should not require Maude");
  }

  @Test
  void rejectsMaudeAnnotationsForNonCTargets() throws IOException {
    for (Target target : List.of(Target.Python, Target.CCPP)) {
      String reactorName = "Unsupported" + target.getDisplayName();
      Path source = tempDir.resolve(reactorName + ".lf");
      Files.writeString(
          source,
          """
          target %s

          @maude(analysis="simulation", timeBound=1, rewrites=1)
          @maudePhysAct(
            inReactor="%s",
            name="sensor",
            vals="{1}",
            period=1,
            timeNonDet=false
          )
          main reactor %s {
            physical action sensor: int
          }
          """
              .formatted(target.getDisplayName(), reactorName, reactorName));

      var result =
          runVerificationHook(
              source, "unsupported-" + target.getDirectoryName().toLowerCase(), false);

      assertTrue(
          result.context().getErrorReporter().getErrorsOccurred(),
          () -> "Maude annotations on target " + target + " should report an error");
      assertFalse(
          Files.exists(result.generatedModel()),
          () -> "A Maude model should not be generated for target " + target);
    }
  }

  @Test
  void verificationUsesResolvedDependencies() throws IOException {
    requiredEnvironmentFile("LF_MAUDE_BASE", "lf-main-concrete.maude", false);
    Path source = SOURCE_DIR.resolve("MainReactorComponents.lf");

    var result = runVerificationHook(source, "verification-enabled", true);

    assertTrue(Files.isRegularFile(result.generatedModel()));
    assertFalse(
        result.context().getErrorReporter().getErrorsOccurred(),
        "Verification should use the configured Maude dependencies");
  }

  @Test
  void goldenModelsExecuteSuccessfully() throws Exception {
    Path maude = requiredEnvironmentFile("MAUDE_BASE", "maude", true);
    Path lfMain = requiredEnvironmentFile("LF_MAUDE_BASE", "lf-main-concrete.maude", false);

    for (Path golden : filesWithExtension(EXPECTED_DIR, ".maude")) {
      String model = Files.readString(golden);

      Process process =
          new ProcessBuilder(
                  maude.toString(), lfMain.toString(), golden.toAbsolutePath().toString())
              .directory(EXPECTED_DIR.toFile())
              .redirectErrorStream(true)
              .start();

      boolean completed = process.waitFor(30, TimeUnit.SECONDS);
      if (!completed) {
        process.destroyForcibly();
      }
      assertTrue(completed, () -> "Maude timed out while executing " + golden);

      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), () -> "Maude failed for " + golden + "\n" + output);

      assertResultCount(
          model,
          output,
          "red in MODELCHECKER-",
          line -> line.equals("result Bool: true"),
          "LTL analysis",
          golden);
      assertResultCount(
          model,
          output,
          "red in ANALYSIS-",
          line -> line.equals("result SearchOutput: trace"),
          "reachability analysis",
          golden);
      assertResultCount(
          model,
          output,
          "rew ",
          line -> line.startsWith("result ClockedSystem:"),
          "simulation",
          golden);
      assertFalse(
          output.contains("Warning:") || output.contains("Error:"),
          () -> "Maude reported a diagnostic for " + golden + "\n" + output);
    }
  }

  private Path generateModel(Path source) throws IOException {
    Resource resource = FileConfig.getResource(source.toFile(), resourceSetProvider);
    assertTrue(resource.getErrors().isEmpty(), () -> "Could not parse " + source);

    fileAccess.setOutputPath(tempDir.resolve(stem(source)).resolve("src-gen").toString());
    var reporter = new RecordingMessageReporter();
    var context =
        new MainContext(
            LFGeneratorContext.Mode.STANDALONE,
            CancelIndicator.NullImpl,
            (message, completion) -> {},
            GeneratorArguments.none(),
            resource,
            fileAccess,
            ignored -> reporter);
    Reactor main = ASTUtils.getMainReactor(resource).orElseThrow();
    var generator =
        new MaudeGenerator(context, attributes(main, "maude"), attributes(main, "maudePhysAct"));
    generator.doGenerate(resource, context);

    assertFalse(context.getErrorReporter().getErrorsOccurred(), "Maude generation reported errors");
    if (source.getFileName().toString().equals("ActionTiming.lf")) {
      assertTrue(
          reporter.warnings.stream().anyMatch(message -> message.contains("currently ignores")),
          "Positive action minimum spacing should produce an LF-Maude warning");
    }
    return context.getFileConfig().getModelGenPath().resolve(stem(source) + ".maude");
  }

  private VerificationHookResult runVerificationHook(
      Path source, String outputDirectory, boolean verify) throws IOException {
    Resource resource = FileConfig.getResource(source.toFile(), resourceSetProvider);
    assertTrue(resource.getErrors().isEmpty(), () -> "Could not parse " + source);

    fileAccess.setOutputPath(tempDir.resolve(outputDirectory).resolve("src-gen").toString());
    var context =
        new MainContext(
            LFGeneratorContext.Mode.STANDALONE, resource, fileAccess, CancelIndicator.NullImpl);
    VerifyProperty.INSTANCE.override(context.getTargetConfig(), verify);

    var generator = new VerificationHookGenerator(context);
    generator.runVerifierIfPropertiesDetected(resource);

    Path generatedModel =
        context.getFileConfig().getModelGenPath().resolve(stem(source) + ".maude");
    return new VerificationHookResult(context, generatedModel);
  }

  private static void assertResultCount(
      String model,
      String output,
      String commandPrefix,
      Predicate<String> successfulResult,
      String analysis,
      Path golden) {
    long expected = model.lines().filter(line -> line.startsWith(commandPrefix)).count();
    long actual = output.lines().filter(successfulResult).count();
    assertEquals(
        expected, actual, () -> "Not every " + analysis + " passed for " + golden + "\n" + output);
  }

  private static List<Attribute> attributes(Reactor reactor, String name) {
    return AttributeUtils.getAttributes(reactor).stream()
        .filter(attribute -> name.equals(attribute.getAttrName()))
        .toList();
  }

  private record VerificationHookResult(MainContext context, Path generatedModel) {}

  private static final class RecordingMessageReporter extends DefaultMessageReporter {

    private final List<String> warnings = new java.util.ArrayList<>();

    @Override
    protected void reportOnNode(
        EObject node, EStructuralFeature feature, DiagnosticSeverity severity, String message) {
      if (severity == DiagnosticSeverity.Warning) {
        warnings.add(message);
      }
      super.reportOnNode(node, feature, severity, message);
    }
  }

  private static final class VerificationHookGenerator extends GeneratorBase {

    private VerificationHookGenerator(LFGeneratorContext context) {
      super(context);
    }

    private void runVerifierIfPropertiesDetected(Resource resource) {
      super.runVerifierIfPropertiesDetected(resource, context);
    }

    @Override
    public TargetTypes getTargetTypes() {
      return new CTypes();
    }

    @Override
    protected DockerGenerator getDockerGenerator(LFGeneratorContext context) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Target getTarget() {
      return targetConfig.target;
    }
  }

  private static List<Path> filesWithExtension(Path directory, String extension)
      throws IOException {
    assertTrue(Files.isDirectory(directory), () -> "Missing regression directory " + directory);
    try (var files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(extension))
          .sorted()
          .toList();
    }
  }

  private static String stem(Path path) {
    String filename = path.getFileName().toString();
    return filename.substring(0, filename.lastIndexOf('.'));
  }

  private static String normalize(String text) {
    return text.lines().map(String::stripTrailing).collect(Collectors.joining("\n"));
  }

  private static Path requiredEnvironmentFile(
      String variable, String filename, boolean executable) {
    String base = System.getenv(variable);
    assumeTrue(base != null && !base.isBlank(), variable + " is not set");

    Path file = Path.of(base).resolve(filename);
    assumeTrue(Files.isRegularFile(file), () -> "Missing " + file);
    if (executable) {
      assumeTrue(Files.isExecutable(file), () -> file + " is not executable");
    } else {
      assumeTrue(Files.isReadable(file), () -> file + " is not readable");
    }
    return file.toAbsolutePath();
  }
}
