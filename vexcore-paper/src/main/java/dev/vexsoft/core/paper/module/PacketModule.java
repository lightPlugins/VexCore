package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import dev.vexsoft.core.paper.packets.version.PacketVersionDefinition;
import dev.vexsoft.core.paper.packets.service.PacketVersionService;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
import dev.vexsoft.core.paper.packet.PacketVersions;
import dev.vexsoft.core.paper.service.packets.VexPacketVersionService;
import dev.vexsoft.core.paper.service.packets.connection.PacketConnectionService;
import dev.vexsoft.core.paper.service.packets.connection.VexPacketConnectionListener;
import dev.vexsoft.core.paper.service.packets.connection.VexPacketConnectionService;
import dev.vexsoft.core.paper.service.packets.hologram.HologramTrackerService;
import dev.vexsoft.core.paper.service.packets.hologram.VexHologramTrackerService;
import dev.vexsoft.core.paper.service.packets.item.FakeItemMetaStoreService;
import dev.vexsoft.core.paper.service.packets.item.VexFakeItemMetaStoreService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PacketModule implements VexModule {

  private final ServiceOwner owner;
  private final Plugin plugin;
  private VexServiceRegistry services;

  public PacketModule(final ServiceOwner owner) {
    if (!(owner instanceof Plugin bukkitPlugin)) {
      throw new IllegalArgumentException("PacketModule owner must be a Bukkit plugin");
    }
    this.owner = owner;
    this.plugin = bukkitPlugin;
  }

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
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
    services.register(HologramTrackerService.class, VexHologramTrackerService.class);
    services.register(FakeItemMetaStoreService.class, VexFakeItemMetaStoreService.class);
    services.register(PacketConnectionService.class, VexPacketConnectionService.class);
    services.registerQueuedServices();
  }

  @Override
  public void start() {
    if (services == null) {
      throw new IllegalStateException("PacketModule has not been loaded yet");
    }
    services.require(ListenerService.class).register(VexPacketConnectionListener.class, services);
    Bukkit.getOnlinePlayers().forEach(services.require(PacketConnectionService.class)::inject);
    PacketVersionService version = services.require(PacketVersionService.class);
    plugin.getLogger().info(
        "Packet support for Minecraft " + version.getMinecraftVersion()
            + " started successfully using adapter " + version.getAdapterVersion()
    );
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore-packets";
  }
}
