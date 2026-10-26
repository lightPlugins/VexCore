package dev.vexsoft.core.common.service.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that owner cleanup preserves reverse creation order independently of the service map's iteration order. */
final class DefaultServiceRegistryShutdownTest {

    @Test
    void closesInReverseRegistrationOrderForEveryMapOrdering() {
        List<List<Class<? extends VexService>>> orders = List.of(
            List.of(Storage.class, Transport.class, Publisher.class),
            List.of(Storage.class, Publisher.class, Transport.class),
            List.of(Transport.class, Storage.class, Publisher.class),
            List.of(Transport.class, Publisher.class, Storage.class),
            List.of(Publisher.class, Storage.class, Transport.class),
            List.of(Publisher.class, Transport.class, Storage.class));
        for (var order : orders) {
            var registry = new DefaultServiceRegistry();
            ServiceOwner owner = () -> "core";
            ServiceOwner other = () -> "other";
            var closed = new ArrayList<Class<? extends VexService>>();
            for (var type : order) {
                register(registry, owner, type, closed, false);
            }
            register(registry, other, Storage.class, closed, false);

            registry.unregisterOwnedBy(owner);
            registry.unregisterOwnedBy(owner);

            assertEquals(order.reversed(), closed);
            registry.unregisterOwnedBy(other);
            var expected = new ArrayList<>(order.reversed());
            expected.add(Storage.class);
            assertEquals(expected, closed);
        }
    }

    @Test
    void replacementAndCleanupFailuresStillCloseRemainingServicesInOrder() {
        var registry = new DefaultServiceRegistry();
        ServiceOwner owner = () -> "core";
        var closed = new ArrayList<Class<? extends VexService>>();
        register(registry, owner, Storage.class, closed, false);
        register(registry, owner, Transport.class, closed, false);
        registry.unregister(owner, Transport.class);
        register(registry, owner, Publisher.class, closed, true);
        register(registry, owner, Transport.class, closed, false);
        closed.clear();

        var failure = assertThrows(IllegalStateException.class, () -> registry.unregisterOwnedBy(owner));

        assertEquals("expected close failure", failure.getCause().getMessage());
        assertEquals(List.of(Transport.class, Publisher.class, Storage.class), closed);
        registry.unregisterOwnedBy(owner);
        assertEquals(3, closed.size());
    }

    private <T extends VexService> void register(
        final DefaultServiceRegistry registry,
        final ServiceOwner owner,
        final Class<T> type,
        final List<Class<? extends VexService>> closed,
        final boolean fail
    ) {
        registry.register(owner, type, type.cast(new RecordingService(type, closed, fail)));
    }

    private interface Storage extends VexService {
    }

    private interface Transport extends VexService {
    }

    private interface Publisher extends VexService {
    }

    private record RecordingService(
        Class<? extends VexService> type,
        List<Class<? extends VexService>> closed,
        boolean fail
    ) implements Storage, Transport, Publisher, AutoCloseable {

        @Override
        public void close() {
            closed.add(type);
            if (fail) {
                throw new IllegalStateException("expected close failure");
            }
        }
    }
}
