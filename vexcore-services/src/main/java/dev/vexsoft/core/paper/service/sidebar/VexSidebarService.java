package dev.vexsoft.core.paper.service.sidebar;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;

/** Default owner-scoped facade for the shared sidebar coordinator. */
@Dependencies(SidebarCoordinatorService.class)
public final class VexSidebarService implements SidebarService, AutoCloseable {

    private final ServiceOwner owner;
    private final SidebarCoordinatorService coordinator;

    public VexSidebarService(final VexServiceRegistry services) {
        VexServiceRegistry checked = Objects.requireNonNull(services, "services");

        owner = checked.getOwner();
        coordinator = checked.require(SidebarCoordinatorService.class);
    }

    @Override
    public void setPersistent(final Player player, final String channel, final SidebarFrame frame, final int priority) {
        coordinator.setPersistent(owner, player, channel, frame, priority);
    }

    @Override
    public SidebarHandle open(final Player player, final String channel, final SidebarFrame frame, final int priority) {
        Player checkedPlayer = Objects.requireNonNull(player, "player");
        String checkedChannel = Objects.requireNonNull(channel, "channel").trim();

        if (checkedChannel.isEmpty()) {
            throw new IllegalArgumentException("channel must not be blank");
        }

        String generation = "trigger:" + UUID.randomUUID();

        coordinator.setPersistent(owner, checkedPlayer, generation, frame, priority);

        return new TriggerHandle(checkedPlayer, generation, priority);
    }

    @Override
    public void showTemporary(
        final Player player,
        final String channel,
        final SidebarFrame frame,
        final long durationTicks,
        final int priority
    ) {
        coordinator.showTemporary(owner, player, channel, frame, durationTicks, priority);
    }

    @Override
    public void clearPersistent(final Player player, final String channel) {
        coordinator.clearPersistent(owner, player, channel);
    }

    @Override
    public void clearTemporary(final Player player, final String channel) {
        coordinator.clearTemporary(owner, player, channel);
    }

    @Override
    public void clear(final Player player) {
        coordinator.clear(owner, player);
    }

    @Override
    public void close() {
        coordinator.clearOwner(owner);
    }

    private final class TriggerHandle implements SidebarHandle {

        private final Player player;
        private final String channel;
        private final int priority;
        private final AtomicBoolean closed = new AtomicBoolean();

        private TriggerHandle(final Player player, final String channel, final int priority) {
            this.player = player;
            this.channel = channel;
            this.priority = priority;
        }

        @Override
        public void update(final SidebarFrame frame) {
            if (closed.get()) {
                throw new IllegalStateException("Sidebar handle is already closed");
            }

            coordinator.setPersistent(owner, player, channel, frame, priority);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                coordinator.clearPersistent(owner, player, channel);
            }
        }
    }
}
