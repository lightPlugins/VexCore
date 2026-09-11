package dev.vexsoft.core.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapConfigurationSectionTest {

  @Test
  void recursivelyCopiesAndResolvesValues() {
    Map<String, Object> nestedValues = new LinkedHashMap<>();
    nestedValues.put("amount", 4);
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("reward", nestedValues);
    source.put("names", List.of("cod", "salmon", 3));

    MapConfigurationSection section = new MapConfigurationSection(source);
    nestedValues.put("amount", 99);

    assertEquals(4, section.getInt("reward.amount", 0));
    assertEquals(List.of("cod", "salmon"), section.getStringList("names"));
    assertTrue(section.contains("reward.amount"));
    assertFalse(section.contains("reward.missing"));
  }

  @Test
  void exposesConsistentDeepKeysAndValues() {
    MapConfigurationSection section = new MapConfigurationSection(Map.of(
        "reward", Map.of("amount", 4, "type", "item")
    ));

    assertEquals(
        Set.of("reward", "reward.amount", "reward.type"),
        section.getKeys(true)
    );
    assertEquals(
        Map.of("reward.amount", 4, "reward.type", "item"),
        section.getValues(true)
    );
  }

  @Test
  void rejectsMutation() {
    MapConfigurationSection section = new MapConfigurationSection(Map.of());

    assertThrows(UnsupportedOperationException.class, () -> section.set("value", 1));
    assertThrows(UnsupportedOperationException.class, () -> section.getValues(false).clear());
  }
}
