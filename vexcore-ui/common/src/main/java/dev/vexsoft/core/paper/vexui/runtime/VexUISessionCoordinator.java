package dev.vexsoft.core.paper.vexui.runtime;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.vexui.VexUISession;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

/** Keeps one authoritative dialog session per viewer across plugin scopes. */
@Dependencies
public final class VexUISessionCoordinator implements VexUICoordinatorService, AutoCloseable {

    private final ConcurrentHashMap<UUID, VexUISessionState> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public VexUISessionCoordinator(final VexServiceRegistry services) {
        Objects.requireNonNull(services, "services");
    }

    @Override
    public void replace(final VexUISessionState session) {
        VexUISessionState previous;
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("VexUI coordinator is closed");
            }
            previous = sessions.put(session.player().getUniqueId(), session);
        }
        // Extension cleanup must not run under the global coordinator lock.
        if (previous != null) {
            previous.retire(false);
        }
    }

    @Override
    public boolean active(final VexUISessionState session) {
        return sessions.get(session.player().getUniqueId()) == session;
    }

    @Override
    public void release(final VexUISessionState session) {
        sessions.remove(session.player().getUniqueId(), session);
    }

    @Override
    public boolean occupied(final UUID viewer) {
        return sessions.containsKey(viewer);
    }

    @Override
    public Optional<VexUISession> find(final ServiceOwner owner, final UUID viewer) {
        var session = sessions.get(viewer);
        return session != null && session.owner() == owner && !session.isClosed()
            ? Optional.of(session) : Optional.empty();
    }

    @Override
    public void dispatch(final Player player, final Key action, final String submittedText) {
        var session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.accept(action, submittedText);
        }
    }

    @Override
    public void disconnected(final UUID viewer) {
        var session = sessions.remove(viewer);
        if (session != null) {
            session.disconnected();
        }
    }

    @Override
    public void closeOwned(final ServiceOwner owner) {
        closeAll(sessions.values().stream().filter(session -> session.owner() == owner).toList());
    }

    @Override
    public void refreshOwned(final ServiceOwner owner) {
        sessions.values().stream().filter(session -> session.owner() == owner).forEach(VexUISessionState::refresh);
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        try {
            closeAll(sessions.values().stream().toList());
        } finally {
            sessions.clear();
        }
    }

    private void closeAll(final Iterable<VexUISessionState> values) {
        RuntimeException failure = null;
        for (var session : values) {
            try {
                session.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
