package dev.vexsoft.core.common.data.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.common.data.SqlitePlayerDataStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcNetworkCheckpointsTest {

    @TempDir
    Path directory;

    @Test
    void migrationIncludesLegacyAccountsProfilesSharedValuesAndIdentitiesOnce() {
        try (var store = new SqlitePlayerDataStore(directory.resolve("test.db"))) {
            UUID player = UUID.randomUUID();
            var key = DataContainerKey.of("coins", String.class, () -> "0");
            store.reconcile("test", List.of(key)).join();
            store.reconcileProfileDocuments().join();
            store.reconcileGlobalData().join();
            store.reconcilePlayerIdentities().join();
            store.save("test", player, "Test", Map.of("coins", "\"9007199254740993000\"")).join();
            store.setGlobalData("test", "market", "{}").join();
            store.recordPlayerIdentity(player, "Test").join();
            var profile = new ProfileDocumentKey(PlayerDataAddress.account(player), "test", "roster");
            store.commitProfileDocuments(List.of(new ProfileDocumentChange(profile, 0, "{}"))).join();
            var repository = store.networkCheckpoints();
            var imported = repository.load("network").join();
            assertEquals(5, imported.values().size());
            assertTrue(imported.values().get("a|test|" + player + "|coins").contains("9007199254740993000"));
            store.save("test", player, "Test", Map.of("coins", "\"0\"")).join();
            assertEquals(imported, repository.load("network").join());
        }
    }

    @Test
    void publicationIsAtomicAndRejectsAnOlderCoordinator() {
        try (var store = new SqlitePlayerDataStore(directory.resolve("test.db"))) {
            var repository = store.networkCheckpoints();
            var initial = repository.load("network").join();
            String inventory = "a|test|" + UUID.randomUUID() + "|inventory";
            String money = "a|test|" + UUID.randomUUID() + "|money";
            assertTrue(repository.publish("network", new NetworkCheckpoint(initial.generation(), "1-0",
                Map.of(inventory, record("\"empty\""), money, record("100")))).join());
            assertFalse(repository.publish("network", new NetworkCheckpoint(initial.generation(), "0-0",
                Map.of(inventory, record("\"item\""), money, record("0")))).join());
            var stored = repository.load("network").join();
            assertEquals("1-0", stored.position());
            assertEquals(Map.of(inventory, record("\"empty\""), money, record("100")), stored.values());
        }
    }

    @Test
    void coldRecoveryFencesPublishersAndDisallowsLegacyMode() {
        try (var store = new SqlitePlayerDataStore(directory.resolve("test.db"))) {
            var repository = store.networkCheckpoints();
            var initial = repository.load("network").join();
            assertTrue(repository.activateEpoch("network", initial.generation(), "new-epoch").join());
            var claimed = repository.load("network").join();
            assertFalse(repository.publish("network", new NetworkCheckpoint(claimed.generation(), "1-0", Map.of()),
                "old-epoch").join());
            assertTrue(repository.publish("network", new NetworkCheckpoint(claimed.generation(), "1-0", Map.of()),
                "new-epoch").join());
            assertThrows(Exception.class, () -> repository.verifyDirectMode().join());
        }
    }

    @Test
    void handoverAndItsFinalInventoryRecoverTogetherFromOneCheckpoint() {
        try (var store = new SqlitePlayerDataStore(directory.resolve("handover.db"))) {
            var repository = store.networkCheckpoints();
            var initial = repository.load("network").join();
            UUID player = UUID.randomUUID();
            String ticket = "p|account/" + player + "|vexcore|server_transfer";
            String inventory = "p|profile/test:game/" + UUID.randomUUID() + '/' + player + "|vexcore|native_state";
            var values = Map.of(ticket, record("{\"requestId\":\"confirmed\"}"),
                inventory, record("{\"level\":42}"));
            assertTrue(repository.publish("network", new NetworkCheckpoint(initial.generation(), "2-0", values)).join());
            assertEquals(values, repository.load("network").join().values());
            assertFalse(repository.publish("network", new NetworkCheckpoint(initial.generation(), "1-0", Map.of())).join());
            assertEquals(values, repository.load("network").join().values());
        }
    }

    private static String record(final String value) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("value", value, "revision", 1));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
