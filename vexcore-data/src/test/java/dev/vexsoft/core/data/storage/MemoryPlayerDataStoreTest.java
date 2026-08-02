package dev.vexsoft.core.data.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryPlayerDataStoreTest {

  @Test
  void addingAContainerKeepsExistingPlayerValues() {
    MemoryPlayerDataStore store = new MemoryPlayerDataStore();
    UUID uniqueId = UUID.randomUUID();
    DataContainerKey<Object> skills = DataContainerKey.of("skills", Object.class, Object::new);
    DataContainerKey<Object> quests = DataContainerKey.of("quests", Object.class, Object::new);

    store.reconcile("vexskills", List.of(skills)).join();
    store.save("vexskills", uniqueId, "VexPlayer", Map.of("skills", "{\"level\":5}")).join();
    store.reconcile("vexskills", List.of(skills, quests)).join();
    Map<String, String> loaded = store.load(
        "vexskills",
        uniqueId,
        List.of(skills, quests)
    ).join();

    assertEquals("{\"level\":5}", loaded.get("skills"));
    assertFalse(loaded.containsKey("quests"));
  }
}
