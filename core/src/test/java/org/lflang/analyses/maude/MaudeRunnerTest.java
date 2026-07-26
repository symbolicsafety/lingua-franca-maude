package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaudeRunnerTest {

  @TempDir Path tempDir;

  @Test
  void fallsBackToPathWhenMaudeBaseIsUnset() throws IOException {
    Path lfMaudeBase = createLfMaudeBase();

    var dependencies =
        MaudeRunner.resolveDependencies(Map.of("LF_MAUDE_BASE", lfMaudeBase.toString()));

    assertEquals("maude", dependencies.executable());
    assertEquals(lfMaudeBase.resolve("lf-main-concrete.maude"), dependencies.semanticsLibrary());
  }

  @Test
  void maudeBaseOverridesPathLookup() throws IOException {
    Path lfMaudeBase = createLfMaudeBase();
    Path maudeBase = Files.createDirectory(tempDir.resolve("maude"));
    Path maude = Files.createFile(maudeBase.resolve("maude"));
    assertTrue(maude.toFile().setExecutable(true), "Could not make the Maude fixture executable");

    var dependencies =
        MaudeRunner.resolveDependencies(
            Map.of("LF_MAUDE_BASE", lfMaudeBase.toString(), "MAUDE_BASE", maudeBase.toString()));

    assertEquals(maude.toAbsolutePath(), Path.of(dependencies.executable()));
  }

  @Test
  void rejectsMissingLfMaudeBase() {
    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> MaudeRunner.resolveDependencies(Map.of()));

    assertTrue(exception.getMessage().contains("LF_MAUDE_BASE"));
  }

  @Test
  void rejectsInvalidMaudeBase() throws IOException {
    Path lfMaudeBase = createLfMaudeBase();
    Path missingMaudeBase = tempDir.resolve("missing-maude");

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MaudeRunner.resolveDependencies(
                    Map.of(
                        "LF_MAUDE_BASE",
                        lfMaudeBase.toString(),
                        "MAUDE_BASE",
                        missingMaudeBase.toString())));

    assertTrue(exception.getMessage().contains("MAUDE_BASE"));
    assertTrue(exception.getMessage().contains(missingMaudeBase.toString()));
  }

  private Path createLfMaudeBase() throws IOException {
    Path lfMaudeBase = Files.createDirectory(tempDir.resolve("lf-maude"));
    Files.createFile(lfMaudeBase.resolve("lf-main-concrete.maude"));
    return lfMaudeBase;
  }
}
