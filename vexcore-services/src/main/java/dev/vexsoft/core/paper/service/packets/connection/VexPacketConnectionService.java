package dev.vexsoft.core.paper.service.packets.connection;

import java.util.Optional;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.interaction.FakeInteraction;
import dev.vexsoft.core.paper.packets.service.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;
import dev.vexsoft.core.paper.packets.internal.PacketInteractionInput;
import dev.vexsoft.core.paper.service.packets.interaction.InteractionTrackerService;
import dev.vexsoft.core.paper.service.packets.interaction.TrackedInteraction;
import dev.vexsoft.core.paper.service.packets.item.FakeItemMetaStoreService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Dependencies({
    PacketConnectionAdapterService.class,
    HologramInteractionAdapterService.class,
    ItemMetaPacketAdapterService.class,
    InteractionTrackerService.class,
    FakeItemMetaStoreService.class,
    ScheduleService.class
})
public final class VexPacketConnectionService
    implements PacketConnectionService, PacketDuplexHandler, AutoCloseable {

  private final PacketConnectionAdapterService connection;
  private final HologramInteractionAdapterService interactions;
  private final ItemMetaPacketAdapterService itemMeta;
  private final InteractionTrackerService interactionsTracker;
  private final FakeItemMetaStoreService itemMetaStore;
  private final ScheduleService scheduler;

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
    Optional<TrackedInteraction> tracked = interactionsTracker.find(
        viewerId,
        interaction.getEntityId()
    );
    if (tracked.isEmpty()) {
      return sanitized;
    }
    Player player = Bukkit.getPlayer(viewerId);
    if (player != null) {
      scheduler.runFor(player, () -> dispatch(player, interaction));
    }
    return null;
  }

  @Override
  public void close() {
    Bukkit.getOnlinePlayers().forEach(this::uninject);
  }

  private void dispatch(final Player player, final PacketInteractionInput input) {
    interactionsTracker.find(player.getUniqueId(), input.getEntityId()).ifPresent(interaction ->
        interaction.getInteractHandler().handle(new FakeInteraction(
            player,
            interaction.getHandle(),
            input.getInteractionType(),
            input.getHand()
        ))
    );
  }
}
