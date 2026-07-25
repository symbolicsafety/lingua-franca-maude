package org.lflang.analyses.maude;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HierarchyNameIndexTest {

  @Test
  void resolvesFullRootRelativeAndUniqueLocalNames() {
    var index = new HierarchyNameIndex<Entry>("LF reactor");
    var worker = new Entry();
    index.register("Main.container.worker", "worker", worker);

    assertSame(worker, index.resolve("Main.container.worker"));
    assertSame(worker, index.resolve("container.worker"));
    assertSame(worker, index.resolve("worker"));
  }

  @Test
  void rejectsAmbiguousLocalNamesAndListsCanonicalMatches() {
    var index = new HierarchyNameIndex<Entry>("LF reactor");
    var leftWorker = new Entry();
    var rightWorker = new Entry();
    index.register("Main.left.worker", "worker", leftWorker);
    index.register("Main.right.worker", "worker", rightWorker);

    assertSame(leftWorker, index.resolve("left.worker"));
    assertSame(rightWorker, index.resolve("Main.right.worker"));

    var exception = assertThrows(IllegalArgumentException.class, () -> index.resolve("worker"));
    assertTrue(exception.getMessage().contains("Main.left.worker"));
    assertTrue(exception.getMessage().contains("Main.right.worker"));
  }

  @Test
  void givesCanonicalFullNamesPrecedenceOverRootRelativeAliases() {
    var index = new HierarchyNameIndex<Entry>("LF reactor");
    var directWorker = new Entry();
    var nestedWorker = new Entry();
    index.register("Main.worker", "worker", directWorker);
    index.register("Main.Main.worker", "worker", nestedWorker);

    assertSame(directWorker, index.resolve("Main.worker"));
    assertSame(nestedWorker, index.resolve("Main.Main.worker"));
  }

  @Test
  void rejectsUnknownNames() {
    var index = new HierarchyNameIndex<Entry>("LF reactor");

    var exception = assertThrows(IllegalArgumentException.class, () -> index.resolve("missing"));
    assertTrue(exception.getMessage().contains("missing"));
  }

  private static final class Entry {}
}
