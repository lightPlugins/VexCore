package dev.vexsoft.core.common.data.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import dev.vexsoft.core.common.data.SqlitePlayerDataStore;
import dev.vexsoft.core.common.data.global.GlobalDataReference;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePlayerDataStoreTest {

  private static final DataContainerKey<Object> LANGUAGE = DataContainerKey.of(
      "language",
      Object.class,
      Object::new
  );
  private static final DataContainerKey<Object> STATS = DataContainerKey.of(
      "stats",
      Object.class,
      Object::new
  );

  @TempDir
  private Path temporaryDirectory;

  @Test
  void persistsPlayerDataAndExtendsTheSchemaAcrossRestarts() {
    Path file = temporaryDirectory.resolve("vexcore.db");
    UUID uniqueId = UUID.randomUUID();
    try (SqlitePlayerDataStore store = new SqlitePlayerDataStore(file)) {
      store.reconcile("vexcore", List.of(LANGUAGE)).join();
      store.save(
          "vexcore",
          uniqueId,
          "VexPlayer",
          Map.of("language", "{\"language\":\"de_DE\"}")
      ).join();
    }

    try (SqlitePlayerDataStore store = new SqlitePlayerDataStore(file)) {
      store.reconcile("vexcore", List.of(LANGUAGE, STATS)).join();
      Map<String, String> loaded = store.load(
          "vexcore",
          uniqueId,
          List.of(LANGUAGE, STATS)
      ).join();

      assertEquals("{\"language\":\"de_DE\"}", loaded.get("language"));
      assertFalse(loaded.containsKey("stats"));
      assertEquals(uniqueId, store.findUniqueId("vexcore", "vexplayer").join().orElseThrow());
    }
  }

  @Test
  void resetsSelectedPlayerValuesWithoutDeletingTheIdentity() {
    UUID uniqueId = UUID.randomUUID();
    try (SqlitePlayerDataStore store = store()) {
      store.reconcile("vexcore", List.of(LANGUAGE, STATS)).join();
      store.save(
          "vexcore",
          uniqueId,
          "Alex",
          Map.of("language", "\"en_US\"", "stats", "{\"strength\":30}")
      ).join();

      assertEquals(1, store.reset("vexcore", uniqueId, List.of("stats")).join());
      Map<String, String> loaded = store.load(
          "vexcore",
          uniqueId,
          List.of(LANGUAGE, STATS)
      ).join();
      assertTrue(loaded.containsKey("language"));
      assertFalse(loaded.containsKey("stats"));
      assertEquals(uniqueId, store.findUniqueId("vexcore", "ALEX").join().orElseThrow());
    }
  }

  @Test
  void maintainsGlobalRevisionsAndPublishesLocalInvalidations() throws Exception {
    List<GlobalDataReference> changes = new ArrayList<>();
    try (SqlitePlayerDataStore store = store()) {
      store.reconcileGlobalData().join();
      try (AutoCloseable ignored = store.subscribeGlobalDataChanges(changes::add)) {
        StoredGlobalData first = store.setGlobalData("vexcore", "motd", "\"first\"").join();
        assertEquals(1, first.revision());

        StoredGlobalData second = store.compareAndSetGlobalData(
            "vexcore",
            "motd",
            first.revision(),
            "\"second\""
        ).join().orElseThrow();
        assertEquals(2, second.revision());
        assertTrue(store.compareAndSetGlobalData(
            "vexcore",
            "motd",
            first.revision(),
            "\"stale\""
        ).join().isEmpty());
        assertEquals("\"second\"", store.loadGlobalData(
            "vexcore",
            "motd"
        ).join().orElseThrow().value());
        assertTrue(store.resetGlobalData("vexcore", "motd").join());
      }
    }
    assertEquals(3, changes.size());
    assertTrue(changes.stream().allMatch(
        reference -> reference.equals(new GlobalDataReference("vexcore", "motd"))
    ));
  }

  @Test
  void persistsAndUpdatesPlayerIdentities() {
    UUID uniqueId = UUID.randomUUID();
    try (SqlitePlayerDataStore store = store()) {
      store.reconcilePlayerIdentities().join();
      PlayerIdentity recorded = store.recordPlayerIdentity(uniqueId, "FirstName").join();
      assertEquals(recorded, store.findPlayerIdentity(uniqueId).join().orElseThrow());
      assertEquals(recorded, store.findPlayerIdentity("firstname").join().orElseThrow());

      PlayerIdentity renamed = store.recordPlayerIdentity(uniqueId, "SecondName").join();
      assertEquals(renamed, store.findPlayerIdentity("SECONDNAME").join().orElseThrow());
      assertTrue(store.findPlayerIdentity("FirstName").join().isEmpty());
    }
  }

  @Test
  void persistsGlobalDataAndIdentitiesAcrossRestarts() {
    UUID uniqueId = UUID.randomUUID();
    Path file = temporaryDirectory.resolve("persistent.db");
    try (SqlitePlayerDataStore store = new SqlitePlayerDataStore(file)) {
      store.reconcileGlobalData().join();
      store.reconcilePlayerIdentities().join();
      store.setGlobalData("vexcore", "spawn", "{\"world\":\"overworld\"}").join();
      store.recordPlayerIdentity(uniqueId, "Persistent").join();
    }

    try (SqlitePlayerDataStore store = new SqlitePlayerDataStore(file)) {
      assertEquals(
          "{\"world\":\"overworld\"}",
          store.loadGlobalData("vexcore", "spawn").join().orElseThrow().value()
      );
      assertEquals(
          uniqueId,
          store.findPlayerIdentity("persistent").join().orElseThrow().uniqueId()
      );
    }
  }

  private SqlitePlayerDataStore store() {
    return new SqlitePlayerDataStore(temporaryDirectory.resolve("vexcore.db"));
  }
}
