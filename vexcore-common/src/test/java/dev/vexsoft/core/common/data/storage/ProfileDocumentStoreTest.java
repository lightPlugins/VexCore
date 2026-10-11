package dev.vexsoft.core.common.data.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.data.profile.ProfileDocumentStore;
import dev.vexsoft.core.common.data.SqlitePlayerDataStore;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.stream.IntStream;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class ProfileDocumentStoreTest {

    private static final ProfileDomain DOMAIN = new ProfileDomain("game:progress");
    @TempDir
    private Path temporaryDirectory;

    @Test
    void memoryAndSqliteIsolateAddressesAndRollbackConflicts() {
        try (var memory = new MemoryPlayerDataStore(); var sqlite = sqlite()) {
            for (ProfileDocumentStore store : List.of(memory, sqlite)) {
                store.reconcileProfileDocuments().join();
                verifyIsolationAndRollback(store);
            }
        }
    }

    @Test
    void onlyOneConcurrentAdmissionCanConsumeTheSameAccountSlot() {
        try (var store = sqlite()) {
            store.reconcileProfileDocuments().join();
            ProfileDocumentKey slot = key(PlayerDataAddress.account(UUID.randomUUID()));
            ProfileDocumentKey first = key(PlayerDataAddress.shared(DOMAIN, UUID.randomUUID()));
            ProfileDocumentKey second = key(PlayerDataAddress.shared(DOMAIN, UUID.randomUUID()));
            CompletableFuture<Boolean> left = store.commitProfileDocuments(List.of(
                new ProfileDocumentChange(slot, 0, "1"), new ProfileDocumentChange(first, 0, "1")));
            CompletableFuture<Boolean> right = store.commitProfileDocuments(List.of(
                new ProfileDocumentChange(slot, 0, "2"), new ProfileDocumentChange(second, 0, "2")));
            assertTrue(left.join() ^ right.join());
            var result = store.loadProfileDocuments(List.of(slot, first, second)).join();
            assertEquals(2, result.size());
            assertEquals(1, result.get(slot).revision());
        }
    }

    @Test
    void theMaximumBatchLoadsInOneBoundedQueryAndExpiredGuardsRollbackEverything() {
        try (var store = sqlite()) {
            store.reconcileProfileDocuments().join();
            var keys = IntStream.range(0, 1024)
                .mapToObj(index -> key(PlayerDataAddress.account(UUID.randomUUID()))).toList();
            assertTrue(store.commitProfileDocuments(keys.stream()
                .map(key -> new ProfileDocumentChange(key, 0, "42")).toList()).join());
            assertEquals(1024, store.loadProfileDocuments(keys).join().size());
            long past = store.profileDatabaseTime().join() - 1;
            assertFalse(store.commitProfileDocuments(List.of(
                new ProfileDocumentChange(keys.getFirst(), 1, "changed", past),
                new ProfileDocumentChange(keys.getLast(), 1, "changed"))).join());
            assertEquals("42", store.loadProfileDocuments(List.of(keys.getLast())).join().get(keys.getLast()).value());
        }
    }

    @Test
    void durableFencesAndPersonalDataSurviveReopen() {
        ProfileDocumentKey fence = key(PlayerDataAddress.shared(DOMAIN, UUID.randomUUID()));
        ProfileDocumentKey personal = key(PlayerDataAddress.personal(DOMAIN, UUID.randomUUID(), UUID.randomUUID()));
        try (var store = sqlite()) {
            store.reconcileProfileDocuments().join();
            assertTrue(store.commitProfileDocuments(List.of(
                new ProfileDocumentChange(fence, 0, "1"), new ProfileDocumentChange(personal, 0, "42"))).join());
        }
        try (var store = sqlite()) {
            store.reconcileProfileDocuments().join();
            assertTrue(store.commitProfileDocuments(List.of(new ProfileDocumentChange(fence, 1, "2"))).join());
            assertFalse(store.commitProfileDocuments(List.of(
                ProfileDocumentChange.check(fence, 1), new ProfileDocumentChange(personal, 1, "99"))).join());
            assertEquals("42", store.loadProfileDocuments(List.of(personal)).join().get(personal).value());
            assertTrue(Math.abs(System.currentTimeMillis() - store.profileDatabaseTime().join()) < 5_000);
        }
    }

    private static void verifyIsolationAndRollback(final ProfileDocumentStore store) {
        UUID account = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        var global = key(PlayerDataAddress.account(account));
        var personal = key(PlayerDataAddress.personal(DOMAIN, profile, account));
        var other = key(PlayerDataAddress.personal(DOMAIN, UUID.randomUUID(), account));
        var shared = key(PlayerDataAddress.shared(DOMAIN, profile));
        assertTrue(store.commitProfileDocuments(List.of(
            new ProfileDocumentChange(global, 0, "1"), new ProfileDocumentChange(personal, 0, "2"),
            new ProfileDocumentChange(other, 0, "3"), new ProfileDocumentChange(shared, 0, "4"))).join());
        assertFalse(store.commitProfileDocuments(List.of(
            new ProfileDocumentChange(global, 1, "9"), new ProfileDocumentChange(personal, 0, "9"))).join());
        var values = store.loadProfileDocuments(List.of(global, personal, other, shared)).join();
        assertEquals("1", values.get(global).value());
        assertEquals("2", values.get(personal).value());
        assertEquals("3", values.get(other).value());
        assertEquals("4", values.get(shared).value());
    }

    private SqlitePlayerDataStore sqlite() {
        return new SqlitePlayerDataStore(temporaryDirectory.resolve("profiles.db"));
    }

    private static ProfileDocumentKey key(final PlayerDataAddress address) {
        return new ProfileDocumentKey(address, "test", "state");
    }
}
