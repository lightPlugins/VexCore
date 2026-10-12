package dev.vexsoft.core.common.service.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies replay rejection, target ownership and cancellation using independent services over shared storage. */
class VexPlayerTransferStoreServiceTest {

    @Test
    void onlyTheDesignatedBackendCanClaimAndCancellationCannotRevokeItsOwnership() {
        var source = service(new MemoryPlayerDataStore());
        var ticket = ticket();
        source.prepare(ticket).join();
        assertThrows(Exception.class, () -> source.claim(ticket.playerId(), "wrong", UUID.randomUUID()).join());
        var claimed = source.claim(ticket.playerId(), "isles-2", UUID.randomUUID()).join().orElseThrow();
        assertEquals(ticket.profiles(), claimed.profiles());
        assertFalse(source.cancel(ticket.playerId(), ticket.requestId()).join());
        assertThrows(Exception.class, () -> source.claim(ticket.playerId(), "isles-2", UUID.randomUUID()).join());
        source.finish(claimed).join();
        assertTrue(source.claim(ticket.playerId(), "isles-2", UUID.randomUUID()).join().isEmpty());
    }

    @Test
    void independentBackendsCannotPrepareTwoConcurrentTransfersOrReplayAConsumedRequest() {
        var memory = new MemoryPlayerDataStore();
        var first = service(memory);
        var second = service(memory);
        var ticket = ticket();
        first.prepare(ticket).join();
        second.prepare(ticket).join();
        var competing = new PlayerTransferTicket(UUID.randomUUID(), ticket.playerId(), "isles-1", "isles-2",
            null, List.of(), ticket.expiresAt(), null, false);
        assertThrows(Exception.class, () -> second.prepare(competing).join());
        assertTrue(second.cancel(ticket.playerId(), ticket.requestId()).join());
        assertThrows(Exception.class, () -> first.prepare(ticket).join());
        second.prepare(competing).join();
    }

    @Test
    void cancelledWarpCannotBeAppliedByALateDestinationLoginButTheSourceCanResume() {
        var store = service(new MemoryPlayerDataStore());
        var ticket = ticket();
        store.prepare(ticket).join();
        assertTrue(store.cancel(ticket.playerId(), ticket.requestId()).join());
        assertTrue(store.claim(ticket.playerId(), "isles-1", UUID.randomUUID()).join().isEmpty());
        assertThrows(Exception.class, () -> store.claim(ticket.playerId(), "isles-2", UUID.randomUUID()).join());
    }

    @Test
    void expiredRequestsCannotPublishOrOverwriteAnActiveHandover() {
        var store = service(new MemoryPlayerDataStore());
        var ticket = ticket();
        var expired = new PlayerTransferTicket(ticket.requestId(), ticket.playerId(), "isles-1", "isles-2",
            null, List.of(), 1, null, false);
        assertThrows(Exception.class, () -> store.prepare(expired).join());
    }

    private static PlayerTransferTicket ticket() {
        return new PlayerTransferTicket(UUID.randomUUID(), UUID.randomUUID(), "isles-1", "isles-2",
            null, List.of(), System.currentTimeMillis() + 30_000, null, false);
    }

    private static VexPlayerTransferStoreService service(final MemoryPlayerDataStore store) {
        var storage = (ProfileStorageService) Proxy.newProxyInstance(ProfileStorageService.class.getClassLoader(),
            new Class<?>[]{ProfileStorageService.class}, (object, method, arguments) -> switch (method.getName()) {
                case "load" -> store.loadProfileDocuments((Collection<ProfileDocumentKey>) arguments[0]);
                case "commit" -> store.commitProfileDocuments((Collection<ProfileDocumentChange>) arguments[0]);
                case "databaseTime" -> store.profileDatabaseTime();
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (object, method, arguments) -> storage);
        return new VexPlayerTransferStoreService(registry);
    }
}
