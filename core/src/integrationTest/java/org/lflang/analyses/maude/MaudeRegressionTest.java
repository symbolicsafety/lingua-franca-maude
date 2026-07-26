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
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.generator.JavaIoFileSystemAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lflang.AttributeUtils;
import org.lflang.FileConfig;
import org.lflang.LFRuntimeModule;
import org.lflang.LFStandaloneSetup;
import org.lflang.ast.ASTUtils;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.generator.MainContext;
import org.lflang.generator.c.CGenerator;
import org.lflang.lf.Attribute;
import org.lflang.lf.Reactor;
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
    var context =
        new MainContext(
            LFGeneratorContext.Mode.STANDALONE, resource, fileAccess, CancelIndicator.NullImpl);
    Reactor main = ASTUtils.getMainReactor(resource).orElseThrow();
    var generator =
        new MaudeGenerator(context, attributes(main, "maude"), attributes(main, "maudePhysAct"));
    generator.doGenerate(resource, context);

    assertFalse(context.getErrorReporter().getErrorsOccurred(), "Maude generation reported errors");
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

  private static final class VerificationHookGenerator extends CGenerator {

    private VerificationHookGenerator(LFGeneratorContext context) {
      super(context, false);
    }

    private void runVerifierIfPropertiesDetected(Resource resource) {
      super.runVerifierIfPropertiesDetected(resource, context);
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
