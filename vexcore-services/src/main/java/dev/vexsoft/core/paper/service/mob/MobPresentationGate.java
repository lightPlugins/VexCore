package dev.vexsoft.core.paper.service.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Keeps presentation behind the native spawn and rejects stale tracking callbacks. */
final class MobPresentationGate {

    private final Map<UUID, Object> pending = new ConcurrentHashMap<>();

    void defer(final UUID viewer, final BiConsumer<Runnable, Runnable> scheduler,
        final BooleanSupplier valid, final Runnable present) {
        Object token = new Object();
        pending.put(viewer, token);
        scheduler.accept(() -> {
            // An older tracking callback must not consume a newer tracking session.
            if (pending.remove(viewer, token) && valid.getAsBoolean()) {
                present.run();
            }
        }, () -> pending.remove(viewer, token));
    }

    boolean isPending(final UUID viewer) {
        return pending.containsKey(viewer);
    }

    void cancel(final UUID viewer) {
        pending.remove(viewer);
    }
}
