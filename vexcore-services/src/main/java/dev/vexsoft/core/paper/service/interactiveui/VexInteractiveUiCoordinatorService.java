package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.interactiveui.InteractiveUi;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiInput;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiState;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput.Kind;
import dev.vexsoft.core.paper.packets.service.InteractiveUiPacketAdapterService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/** Owns exclusive viewer controls, ordered input delivery, incremental rendering, and presentation restoration. */
@Dependencies({InteractiveUiPacketAdapterService.class, ScheduleService.class})
public final class VexInteractiveUiCoordinatorService implements InteractiveUiCoordinatorService, AutoCloseable {

    private final InteractiveUiPacketAdapterService packets;
    private final ScheduleService schedules;
    private final ConcurrentHashMap<UUID, VexInteractiveUi> sessions = new ConcurrentHashMap<>();
    private final Logger logger = Logger.getLogger("VexCore");
    private volatile boolean closed;

    public VexInteractiveUiCoordinatorService(VexServiceRegistry services) {
        packets = services.require(InteractiveUiPacketAdapterService.class);
        schedules = services.require(ScheduleService.class);
    }

    @Override
    public InteractiveUi open(ServiceOwner owner, Player player, String id) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(player, "player");
        if (id == null || !id.matches("[a-zA-Z0-9_.:/-]{1,80}")) {
            throw new IllegalArgumentException("Invalid interactive screen identifier");
        }
        if (!player.getServer().isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException("Opening a canvas requires the viewer's entity scheduler");
        }
        if (closed || !player.isOnline() || player.isDead()) {
            throw new IllegalStateException("Interactive UI is unavailable");
        }
        VexInteractiveUi session = new VexInteractiveUi(this, owner, player, id);

        if (sessions.putIfAbsent(session.viewerId, session) != null) {
            throw new IllegalStateException("The viewer already has an interactive session");
        }
        synchronized (session) {
            try {
                if (closed || session.closed) {
                    throw new IllegalStateException("Interactive UI closed during setup");
                }
                player.closeInventory();
                if (closed || session.closed) {
                    throw new IllegalStateException("Interactive UI closed while closing the inventory");
                }
                session.transport = packets.open(player);
                session.task = schedules.runForTimer(player, 1, 1, () -> tick(session), () -> retire(session))
                    .orElseThrow(() -> new IllegalStateException("Viewer retired during UI setup"));
                if (closed || session.closed) {
                    throw new IllegalStateException("Interactive UI closed during setup");
                }
                session.initializing = false;
                return session;
            } catch (RuntimeException failure) {
                session.initializing = false;
                retire(session);
                throw failure;
            }
        }
    }

    @Override
    public Optional<InteractiveUi> find(ServiceOwner owner, Player player, String id) {
        VexInteractiveUi session = sessions.get(player.getUniqueId());

        return session != null && !session.closed && session.owner == owner && session.id.equals(id)
            ? Optional.of(session) : Optional.empty();
    }

    @Override
    public boolean consume(UUID viewerId, Object packet) {
        VexInteractiveUi session = sessions.get(viewerId);

        if (session == null || session.closed) {
            return false;
        }
        Optional<InteractiveUiPacketInput> decoded = packets.decode(packet);

        if (decoded.isEmpty()) {
            return false;
        }
        InteractiveUiPacketInput input = decoded.get();

        if (input.kind() != Kind.OTHER_GAMEPLAY) {
            boolean accepted = session.inputs.offer(input);

            if (accepted && input.kind() == Kind.ROTATION) {
                try {
                    presentCursor(session);
                } catch (RuntimeException failure) {
                    // Cleanup and plugin callbacks stay on the entity thread, including presentation failures.
                    session.cursorFailure = failure;
                }
            }
            return accepted;
        }
        return true;
    }

    @Override
    public Object preserveTarget(UUID viewerId, Object packet) {
        VexInteractiveUi session = sessions.get(viewerId);

        if (session == null || session.closed || session.transport == null) {
            return packet;
        }
        return packets.preserveTarget(session.transport, packet);
    }

    @Override
    public void discard(Player player) {
        VexInteractiveUi session = sessions.get(player.getUniqueId());

        if (session != null && session.player == player) {
            retire(session);
        }
    }

    @Override
    public void closeOwner(ServiceOwner owner) {
        for (VexInteractiveUi session : List.copyOf(sessions.values())) {
            if (session.owner == owner) {
                if (session.player.getServer().isOwnedByCurrentRegion(session.player)) {
                    retire(session);
                } else {
                    // Disable capture immediately; queued work still belongs to this exact session instance.
                    session.closed = true;
                    try {
                        if (schedules.runFor(session.player, () -> retire(session), () -> retire(session))
                            .isEmpty()) {
                            retire(session);
                        }
                    } catch (RuntimeException failure) {
                        retire(session);
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        for (VexInteractiveUi session : List.copyOf(sessions.values())) {
            retire(session);
        }
    }

    void retire(VexInteractiveUi session) {
        synchronized (session) {
            if (sessions.get(session.viewerId) != session) {
                return;
            }
            session.closed = true;
            if (session.initializing) {
                // Synchronous lifecycle callbacks can reenter setup; its catch block owns the complete rollback.
                return;
            }
            InteractiveUiInputBuffer.Batch remaining = session.inputs.close();

            try {
                if (remaining.acknowledgement() >= 0) {
                    packets.acknowledge(session.player, remaining.acknowledgement());
                }
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "Unable to acknowledge final UI input for " + session.viewerId, failure);
            }
            try {
                if (session.task != null) {
                    session.task.cancel();
                }
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "Unable to cancel interactive UI task for " + session.viewerId, failure);
            }
            try {
                if (session.transport != null) {
                    // Adapter cleanup uses captured packets when the entity's region is unavailable.
                    packets.close(session.player, session.transport);
                }
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "Unable to fully restore interactive UI for " + session.viewerId, failure);
            } finally {
                session.elements.clear();
                session.elementIdentities.clear();
                session.callback = ignored -> { };
                sessions.remove(session.viewerId, session);
            }
        }
    }

    private void tick(VexInteractiveUi session) {
        synchronized (session) {
            tickOwned(session);
        }
    }

    private void tickOwned(VexInteractiveUi session) {
        if (session.closed || sessions.get(session.viewerId) != session) {
            return;
        }
        try {
            if (session.cursorFailure != null) {
                throw session.cursorFailure;
            }
            if (!session.player.isOnline() || session.player.isDead()) {
                retire(session);
                return;
            }
            InteractiveUiInputBuffer.Batch batch = session.inputs.drain();

            if (batch.acknowledgement() >= 0) {
                packets.acknowledge(session.player, batch.acknowledgement());
            }
            if (batch.overflowed()) {
                retire(session);
                return;
            }
            for (InteractiveUiInputBuffer.Input queued : batch.inputs()) {
                InteractiveUiPacketInput input = queued.packet();

                if (input.kind() == Kind.EXIT) {
                    retire(session);
                    return;
                }
                dispatch(session, queued);
                if (session.closed) {
                    return;
                }
            }
            if (session.dirty) {
                emit(session, session.pointer.refresh(List.copyOf(session.elements.values())));
            }
            if (!session.closed) {
                render(session);
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Interactive UI session failed for " + session.viewerId, failure);
            retire(session);
        }
    }

    private void dispatch(VexInteractiveUi session, InteractiveUiInputBuffer.Input queued) {
        InteractiveUiPacketInput input = queued.packet();
        var elements = List.copyOf(session.elements.values());
        List<InteractiveUiInput> events;

        if (input.kind() == Kind.ROTATION) {
            events = session.pointer.move(queued.cursor(), elements);
        } else if (input.blockX() == session.transport.getBlockX()
            && input.blockY() == session.transport.getBlockY() && input.blockZ() == session.transport.getBlockZ()) {
            events = switch (input.kind()) {
                case PRESS -> session.pointer.press(elements);
                case RELEASE -> session.pointer.release(elements);
                default -> List.of();
            };
        } else {
            events = List.of();
        }
        emit(session, events);
    }

    private void emit(VexInteractiveUi session, List<InteractiveUiInput> events) {
        InteractiveUiState previous = session.snapshot;
        Map<String, Object> targets = Map.copyOf(session.elementIdentities);

        session.snapshot = session.pointer.state();
        session.dirty |= !Objects.equals(previous.hoveredId(), session.snapshot.hoveredId());
        for (InteractiveUiInput event : events) {
            if (session.closed) {
                return;
            }
            if (event.targetId() != null && event.kind() != InteractiveUiInput.Kind.LEAVE) {
                var target = session.elements.get(event.targetId());

                // A preceding callback may remove, disable, or replace the target of an already queued event.
                if (target == null || !target.interactive()
                    || targets.get(event.targetId()) != session.elementIdentities.get(event.targetId())) {
                    continue;
                }
                if (event.kind() == InteractiveUiInput.Kind.CLICK && !target.bounds().contains(event.x(), event.y())) {
                    continue;
                }
            }
            session.callback.accept(event);
        }
    }

    private void render(VexInteractiveUi session) {
        if (session.dirty) {
            packets.scene(session.player, session.transport, InteractiveUiRenderer.renderScene(
                List.copyOf(session.elements.values()), session.snapshot.hoveredId()));
            session.dirty = false;
        }
        synchronized (session.cursorLock) {
            session.cursorReady = true;
        }
        presentCursor(session);
    }

    private void presentCursor(VexInteractiveUi session) {
        synchronized (session.cursorLock) {
            if (session.closed || !session.cursorReady || session.cursorFailure != null) {
                return;
            }
            // Read the latest network projection, never an older tick snapshot that could pull the cursor backwards.
            Optional<InteractiveUiCursor> current = session.inputs.cursor();

            if (current.isEmpty()) {
                return;
            }
            InteractiveUiCursor cursor = current.get();

            if (cursor.x() == session.renderedX && cursor.y() == session.renderedY) {
                return;
            }
            packets.cursorImmediate(session.transport, InteractiveUiRenderer.renderCursor(cursor.x(), cursor.y()));
            session.renderedX = cursor.x();
            session.renderedY = cursor.y();
        }
    }
}
