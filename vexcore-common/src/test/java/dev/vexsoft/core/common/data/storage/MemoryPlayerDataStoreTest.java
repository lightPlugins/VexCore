package dev.vexsoft.core.common.data.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
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

  @Test
  void resetsSelectedValuesWithoutDeletingPlayerIdentity() {
    MemoryPlayerDataStore store = new MemoryPlayerDataStore();
    UUID uniqueId = UUID.randomUUID();
    DataContainerKey<Object> language = DataContainerKey.of(
        "language",
        Object.class,
        Object::new
    );
    DataContainerKey<Object> stats = DataContainerKey.of("stats", Object.class, Object::new);
    store.reconcile("vexcore", List.of(language, stats)).join();
    store.save(
        "vexcore",
        uniqueId,
        "Alex",
        Map.of("language", "{\"language\":\"de_DE\"}", "stats", "{\"strength\":30}")
    ).join();

    store.reset("vexcore", uniqueId, List.of("stats")).join();

    Map<String, String> loaded = store.load(
        "vexcore",
        uniqueId,
        List.of(language, stats)
    ).join();
    assertTrue(loaded.containsKey("language"));
    assertFalse(loaded.containsKey("stats"));
    assertEquals(uniqueId, store.findUniqueId("vexcore", "alex").join().orElseThrow());
  }
}
