package dev.vexsoft.core.paper.bootstrap;

import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCacheService;
import dev.vexsoft.core.paper.command.CommandService;
import dev.vexsoft.core.command.VexCommandService;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.data.VexDataService;
import dev.vexsoft.core.dialog.DialogService;
import dev.vexsoft.core.inventory.InventoryService;
import dev.vexsoft.core.item.ItemService;
import dev.vexsoft.core.localization.VexLocalizationService;
import dev.vexsoft.core.packets.service.DisplayPassengerPacketService;
import dev.vexsoft.core.packets.service.FakeItemMetaService;
import dev.vexsoft.core.packets.service.InteractableHologramService;
import dev.vexsoft.core.packets.service.ItemDisplayPacketService;
import dev.vexsoft.core.packets.service.LightningPacketService;
import dev.vexsoft.core.packets.service.MobGlowPacketService;
import dev.vexsoft.core.packets.service.MobHitPacketService;
import dev.vexsoft.core.packets.service.TextDisplayPacketService;
import dev.vexsoft.core.paper.dialog.VexDialogService;
import dev.vexsoft.core.paper.inventory.VexInventoryListener;
import dev.vexsoft.core.paper.inventory.VexInventoryService;
import dev.vexsoft.core.paper.item.VexItemService;
import dev.vexsoft.core.paper.listener.ListenerService;
import dev.vexsoft.core.paper.listener.VexListenerService;
import dev.vexsoft.core.paper.message.SendMessageService;
import dev.vexsoft.core.paper.message.VexSendMessageService;
import dev.vexsoft.core.paper.packet.service.VexDisplayPassengerPacketService;
import dev.vexsoft.core.paper.packet.service.VexFakeItemMetaService;
import dev.vexsoft.core.paper.packet.service.VexInteractableHologramService;
import dev.vexsoft.core.paper.packet.service.VexItemDisplayPacketService;
import dev.vexsoft.core.paper.packet.service.VexLightningPacketService;
import dev.vexsoft.core.paper.packet.service.VexMobGlowPacketService;
import dev.vexsoft.core.paper.packet.service.VexMobHitPacketService;
import dev.vexsoft.core.paper.packet.service.VexTextDisplayPacketService;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexScheduleService;
import java.util.Objects;

@Dependencies
public final class VexPluginBootstrapService implements PluginBootstrapService {

  public VexPluginBootstrapService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void initialize(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    checkedServices.register(ConfigurationService.class, VexConfigurationService.class);
    checkedServices.register(ScheduleService.class, VexScheduleService.class);
    checkedServices.register(InventoryService.class, VexInventoryService.class);
    checkedServices.register(CommandService.class, VexCommandService.class);
    checkedServices.register(CacheService.class, VexCacheService.class);
    checkedServices.register(ListenerService.class, VexListenerService.class);
    checkedServices.register(DialogService.class, VexDialogService.class);
    checkedServices.register(ItemService.class, VexItemService.class);
    checkedServices.register(DataService.class, VexDataService.class);
    checkedServices.register(LocalizationService.class, VexLocalizationService.class);
    checkedServices.register(SendMessageService.class, VexSendMessageService.class);
    checkedServices.register(TextDisplayPacketService.class, VexTextDisplayPacketService.class);
    checkedServices.register(ItemDisplayPacketService.class, VexItemDisplayPacketService.class);
    checkedServices.register(
        DisplayPassengerPacketService.class,
        VexDisplayPassengerPacketService.class
    );
    checkedServices.register(
        InteractableHologramService.class,
        VexInteractableHologramService.class
    );
    checkedServices.register(MobHitPacketService.class, VexMobHitPacketService.class);
    checkedServices.register(MobGlowPacketService.class, VexMobGlowPacketService.class);
    checkedServices.register(LightningPacketService.class, VexLightningPacketService.class);
    checkedServices.register(FakeItemMetaService.class, VexFakeItemMetaService.class);
  }

  @Override
  public void enable(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services")
        .require(ListenerService.class)
        .register(VexInventoryListener.class);
  }
}
