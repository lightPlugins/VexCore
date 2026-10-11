package dev.vexsoft.core.common.service.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises competing ownership, stale fences and authoritative offline guards through real Core storage. */
class VexProfileLeaseServiceTest {

    @Test
    void onlyOneOwnerCanWriteAndReleasedFencesCannotBeReused() {
        var store = new MemoryPlayerDataStore();
        var storage = storage(store);
        var registry = registry(storage);
        try (var first = new VexProfileLeaseService(registry); var second = new VexProfileLeaseService(registry)) {
            var domain = new ProfileDomain("test:game");
            var profile = UUID.randomUUID();
            var player = UUID.randomUUID();
            var owner = first.acquire(domain, profile, player, UUID.randomUUID(), 1, 1).join().orElseThrow();
            assertTrue(second.acquire(domain, profile, player, UUID.randomUUID(), 1, 1).join().isEmpty());
            var stale = first.writeGuard(owner);
            first.release(owner).join();
            assertFalse(first.isWritable(owner));
            var next = second.acquire(domain, profile, player, UUID.randomUUID(), 1, 2).join().orElseThrow();
            assertTrue(next.fence() > owner.fence());
            var data = new ProfileDocumentKey(next.address(), "test", "wallet");
            assertFalse(storage.commit(List.of(stale, new ProfileDocumentChange(data, 0, "old"))).join());
            assertTrue(storage.commit(List.of(second.writeGuard(next), new ProfileDocumentChange(data, 0, "new"))).join());
        }
    }

    @Test
    void aNewLoginInvalidatesAnOfflineExtractionGuard() {
        var storage = storage(new MemoryPlayerDataStore());
        try (var leases = new VexProfileLeaseService(registry(storage))) {
            var domain = new ProfileDomain("test:game");
            var profile = UUID.randomUUID();
            var player = UUID.randomUUID();
            var address = dev.vexsoft.core.api.player.profile.PlayerDataAddress.personal(domain, profile, player);
            var guards = leases.retire(address).join();
            leases.acquire(domain, profile, player, UUID.randomUUID(), 1, 1).join().orElseThrow();
            assertFalse(storage.commit(guards).join());
        }
    }

    private static ProfileStorageService storage(final MemoryPlayerDataStore store) {
        return (ProfileStorageService) Proxy.newProxyInstance(ProfileStorageService.class.getClassLoader(),
            new Class<?>[]{ProfileStorageService.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "load" -> store.loadProfileDocuments((java.util.Collection<ProfileDocumentKey>) arguments[0]);
                case "commit" -> store.commitProfileDocuments((java.util.Collection<ProfileDocumentChange>) arguments[0]);
                case "databaseTime" -> store.profileDatabaseTime();
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static VexServiceRegistry registry(final ProfileStorageService storage) {
        return (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (proxy, method, arguments) -> storage);
    }
}
