package dev.vexsoft.core.paper.service.packets.connection;

import java.util.Optional;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.hologram.HologramInteraction;
import dev.vexsoft.core.paper.packets.service.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;
import dev.vexsoft.core.paper.packets.internal.PacketInteractionInput;
import dev.vexsoft.core.paper.service.packets.hologram.HologramTrackerService;
import dev.vexsoft.core.paper.service.packets.hologram.TrackedHologram;
import dev.vexsoft.core.paper.service.packets.item.FakeItemMetaStoreService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Dependencies({
    PacketConnectionAdapterService.class,
    HologramInteractionAdapterService.class,
    ItemMetaPacketAdapterService.class,
    HologramTrackerService.class,
    FakeItemMetaStoreService.class,
    ScheduleService.class
})
public final class VexPacketConnectionService
    implements PacketConnectionService, PacketDuplexHandler, AutoCloseable {

  private final PacketConnectionAdapterService connection;
  private final HologramInteractionAdapterService interactions;
  private final ItemMetaPacketAdapterService itemMeta;
  private final HologramTrackerService holograms;
  private final FakeItemMetaStoreService itemMetaStore;
  private final ScheduleService scheduler;

  public VexPacketConnectionService(final VexServiceRegistry services) {
    this.connection = services.require(PacketConnectionAdapterService.class);
    this.interactions = services.require(HologramInteractionAdapterService.class);
    this.itemMeta = services.require(ItemMetaPacketAdapterService.class);
    this.holograms = services.require(HologramTrackerService.class);
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
    Optional<TrackedHologram> tracked = holograms.find(
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
    holograms.find(player.getUniqueId(), input.getEntityId()).ifPresent(hologram ->
        hologram.getInteractHandler().handle(new HologramInteraction(
            player,
            hologram.getHandle(),
            input.getInteractionType(),
            input.getHand()
        ))
    );
  }
}
