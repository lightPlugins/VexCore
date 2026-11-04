package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.interactiveui.service.InteractiveUiCoordinatorService;
import dev.vexsoft.core.paper.interactiveui.service.VexInteractiveUiCoordinatorService;
import dev.vexsoft.core.paper.packet.PacketVersions;
import dev.vexsoft.core.paper.packets.service.block.VexVirtualBlockService;
import dev.vexsoft.core.paper.packets.service.block.VirtualBlockPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.block.VirtualBlockService;
import dev.vexsoft.core.paper.packets.service.connection.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.service.connection.PacketConnectionService;
import dev.vexsoft.core.paper.packets.service.connection.PacketTransportAdapterService;
import dev.vexsoft.core.paper.packets.service.connection.VexPacketConnectionListener;
import dev.vexsoft.core.paper.packets.service.connection.VexPacketConnectionService;
import dev.vexsoft.core.paper.packets.service.display.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.display.VexVirtualPassengerOverlayService;
import dev.vexsoft.core.paper.packets.service.display.VirtualPassengerOverlayService;
import dev.vexsoft.core.paper.packets.service.effect.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.interaction.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.interaction.InteractionTrackerService;
import dev.vexsoft.core.paper.packets.service.interaction.VexInteractionTrackerService;
import dev.vexsoft.core.paper.packets.service.interactiveui.InteractiveUiPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.item.FakeItemMetaStoreService;
import dev.vexsoft.core.paper.packets.service.item.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.item.RecipeBookPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.item.VexFakeItemMetaStoreService;
import dev.vexsoft.core.paper.packets.service.version.PacketVersionService;
import dev.vexsoft.core.paper.packets.service.version.VexPacketVersionService;
import dev.vexsoft.core.paper.packets.version.PacketVersionDefinition;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
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
        services.register(VirtualBlockService.class, VexVirtualBlockService.class);
        services.register(VirtualBlockPacketAdapterService.class, definition.getVirtualBlockAdapter());
        services.register(RecipeBookPacketAdapterService.class, definition.getRecipeBookAdapter());
        services.register(DisplayPacketAdapterService.class, definition.getDisplayAdapter());
        services.register(VirtualPassengerOverlayService.class, VexVirtualPassengerOverlayService.class);
        services.register(EntityEffectPacketAdapterService.class, definition.getEntityEffectAdapter());
        services.register(PacketConnectionAdapterService.class, definition.getConnectionAdapter());
        services.register(ItemMetaPacketAdapterService.class, definition.getItemMetaAdapter());
        services.register(HologramInteractionAdapterService.class, definition.getHologramInteractionAdapter());
        services.register(InteractiveUiPacketAdapterService.class, definition.getInteractiveUiAdapter());
        services.register(InteractiveUiCoordinatorService.class, VexInteractiveUiCoordinatorService.class);
        services.register(PacketVersionService.class, VexPacketVersionService.class);
        services.register(InteractionTrackerService.class, VexInteractionTrackerService.class);
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

        plugin.getLogger()
            .info(
                "Packet support for Minecraft " + version.getMinecraftVersion() + " started successfully using adapter "
                    + version.getAdapterVersion());
    }

    @Override
    public String getServiceOwnerName() {
        return "vexcore-packets";
    }
}
