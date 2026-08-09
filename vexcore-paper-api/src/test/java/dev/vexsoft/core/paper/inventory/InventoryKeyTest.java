package dev.vexsoft.core.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InventoryKeyTest {

  @Test
  void normalizesNamespacedKeys() {
    assertEquals("vexskills:main/menu", InventoryKey.of(" VexSkills:Main/Menu ").getValue());
  }

  @Test
  void rejectsKeysWithoutNamespace() {
    assertThrows(IllegalArgumentException.class, () -> InventoryKey.of("main"));
  }
}
