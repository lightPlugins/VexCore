package dev.vexsoft.core.paper.service.packets.connection;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.interaction.FakeInteraction;
import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;
import dev.vexsoft.core.paper.packets.internal.PacketInteractionInput;
import dev.vexsoft.core.paper.packets.service.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.service.packets.interaction.InteractionTrackerService;
import dev.vexsoft.core.paper.service.packets.interaction.TrackedInteraction;
import dev.vexsoft.core.paper.service.packets.item.FakeItemMetaStoreService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Coordinates player packet interception, virtual interactions, and item-presentation rewriting. */
@Dependencies({PacketConnectionAdapterService.class, HologramInteractionAdapterService.class, ItemMetaPacketAdapterService.class, InteractionTrackerService.class, FakeItemMetaStoreService.class, ScheduleService.class})
public final class VexPacketConnectionService implements PacketConnectionService, PacketDuplexHandler, AutoCloseable {

    private final PacketConnectionAdapterService connection;
    private final HologramInteractionAdapterService interactions;
    private final ItemMetaPacketAdapterService itemMeta;
    private final InteractionTrackerService interactionsTracker;
    private final FakeItemMetaStoreService itemMetaStore;
    private final ScheduleService scheduler;
    private final ConcurrentHashMap<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public VexPacketConnectionService(final VexServiceRegistry services) {
        this.connection = services.require(PacketConnectionAdapterService.class);
        this.interactions = services.require(HologramInteractionAdapterService.class);
        this.itemMeta = services.require(ItemMetaPacketAdapterService.class);
        this.interactionsTracker = services.require(InteractionTrackerService.class);
        this.itemMetaStore = services.require(FakeItemMetaStoreService.class);
        this.scheduler = services.require(ScheduleService.class);
    }

    @Override
    public void inject(final Player player) {
        connection.inject(player, this);
    }

    @Override
    public void uninject(final Player player) {
        pending.remove(player.getUniqueId());
        connection.uninject(player);
    }

    @Override
    public Object write(final UUID viewerId, final Object packet) {
        return itemMeta.rewriteOutbound(viewerId, packet, itemMetaStore);
    }

    @Override
    public Object read(final UUID viewerId, final Object packet) {
        Object sanitized = itemMeta.sanitizeInbound(viewerId, packet, itemMetaStore);
        Optional<PacketInteractionInput> input = interactions.decode(sanitized);

        if (input.isEmpty()) {
            return sanitized;
        }

        PacketInteractionInput interaction = input.get();
        Optional<TrackedInteraction> tracked = interactionsTracker.find(viewerId, interaction.getEntityId());

        if (tracked.isEmpty()) {
            return sanitized;
        }

        if (interactionsTracker.isInputBlocked(viewerId)) {
            return null;
        }

        Player player = Bukkit.getPlayer(viewerId);

        if (player != null) {
            boolean[] schedule = {false};
            PendingInput batch = pending.compute(
                viewerId,
                (ignored, current) -> {
                    if (current == null) {
                        current = new PendingInput();
                        schedule[0] = true;
                    }

                    synchronized (current) {
                        if (current.inputs.size() < 8) {
                            current.inputs.addLast(interaction);
                        }
                    }

                    return current;
                }
            );

            if (schedule[0]) {
                try {
                    if (scheduler.runFor(
                        player,
                        () -> {
                            if (!pending.remove(viewerId, batch) || !player.isOnline()
                                || interactionsTracker.isInputBlocked(viewerId)) {
                                return;
                            }

                            List<PacketInteractionInput> inputs;

                            synchronized (batch) {
                                inputs = List.copyOf(batch.inputs);
                            }

                            inputs.forEach(next -> dispatch(player, next));
                        },
                        () -> pending.remove(viewerId, batch)
                    ).isEmpty()) {
                        pending.remove(viewerId, batch);
                    }
                } catch (RuntimeException failure) {
                    pending.remove(viewerId, batch);
                    throw failure;
                }
            }
        }

        return null;
    }

    @Override
    public void close() {
        pending.clear();
        Bukkit.getOnlinePlayers().forEach(this::uninject);
    }

    private void dispatch(final Player player, final PacketInteractionInput input) {
        interactionsTracker.find(player.getUniqueId(), input.getEntityId())
            .ifPresent(interaction -> interaction.getInteractHandler()
                .handle(new FakeInteraction(
                    player,
                    interaction.getHandle(),
                    input.getInteractionType(),
                    input.getHand()
                )));
    }

    /** Queues decoded packet interactions until the player's entity scheduler processes them. */
    public static final class PendingInput {

        private final Deque<PacketInteractionInput> inputs = new ArrayDeque<>();
    }
}
