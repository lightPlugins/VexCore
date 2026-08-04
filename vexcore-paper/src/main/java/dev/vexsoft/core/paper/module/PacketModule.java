package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.internal.DisplayPacketAdapterService;
import dev.vexsoft.core.packets.internal.EntityEffectPacketAdapterService;
import dev.vexsoft.core.packets.internal.HologramInteractionAdapterService;
import dev.vexsoft.core.packets.internal.ItemMetaPacketAdapterService;
import dev.vexsoft.core.packets.internal.PacketConnectionAdapterService;
import dev.vexsoft.core.packets.internal.PacketTransportAdapterService;
import dev.vexsoft.core.packets.version.PacketVersionDefinition;
import dev.vexsoft.core.packets.version.PacketVersionService;
import dev.vexsoft.core.paper.listener.ListenerService;
import dev.vexsoft.core.paper.listener.VexListenerService;
import dev.vexsoft.core.paper.packet.PacketVersions;
import dev.vexsoft.core.paper.packet.VexPacketVersionService;
import dev.vexsoft.core.paper.packet.connection.PacketConnectionService;
import dev.vexsoft.core.paper.packet.connection.VexPacketConnectionListener;
import dev.vexsoft.core.paper.packet.connection.VexPacketConnectionService;
import dev.vexsoft.core.paper.packet.hologram.HologramTrackerService;
import dev.vexsoft.core.paper.packet.hologram.VexHologramTrackerService;
import dev.vexsoft.core.paper.packet.item.FakeItemMetaStoreService;
import dev.vexsoft.core.paper.packet.item.VexFakeItemMetaStoreService;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexScheduleService;
import org.bukkit.Bukkit;

public final class PacketModule implements VexModule {

  private final ServiceOwner plugin;

  public PacketModule(final ServiceOwner plugin) {
    this.plugin = plugin;
  }

  @Override
  public void enable(final ServiceRegistry registry) {
    VexServiceRegistry services = registry.scoped(plugin);
    PacketVersionDefinition definition = PacketVersions.select(services);
    services.register(PacketTransportAdapterService.class, definition.getTransportAdapter());
    services.register(DisplayPacketAdapterService.class, definition.getDisplayAdapter());
    services.register(EntityEffectPacketAdapterService.class, definition.getEntityEffectAdapter());
    services.register(PacketConnectionAdapterService.class, definition.getConnectionAdapter());
    services.register(ItemMetaPacketAdapterService.class, definition.getItemMetaAdapter());
    services.register(
        HologramInteractionAdapterService.class,
        definition.getHologramInteractionAdapter()
    );
    services.register(PacketVersionService.class, VexPacketVersionService.class);
    services.register(ScheduleService.class, VexScheduleService.class);
    services.register(ListenerService.class, VexListenerService.class);
    services.register(HologramTrackerService.class, VexHologramTrackerService.class);
    services.register(FakeItemMetaStoreService.class, VexFakeItemMetaStoreService.class);
    services.register(PacketConnectionService.class, VexPacketConnectionService.class);
    services.registerQueuedServices();
    services.require(ListenerService.class).register(VexPacketConnectionListener.class);
    Bukkit.getOnlinePlayers().forEach(services.require(PacketConnectionService.class)::inject);
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore-packets";
  }
}
