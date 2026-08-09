package dev.vexsoft.core.paper.service.bootstrap;

import dev.vexsoft.core.common.service.reactor.VexConditionRegistry;
import dev.vexsoft.core.common.service.reactor.VexEffectRegistry;
import dev.vexsoft.core.common.service.reactor.VexFilterRegistry;
import dev.vexsoft.core.common.service.reactor.VexTriggerRegistry;
import dev.vexsoft.core.paper.service.reactor.VexBlockTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.VexEntityTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.VexItemTypeRegistry;

import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.localization.LocalizedMessageService;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.player.DataService;
import dev.vexsoft.core.api.service.player.PlayerContainerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.signals.SignalService;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.paper.service.commands.CommandService;
import dev.vexsoft.core.paper.service.commands.VexCommandService;
import dev.vexsoft.core.common.service.configuration.VexConfigurationService;
import dev.vexsoft.core.common.service.data.VexDataService;
import dev.vexsoft.core.common.service.data.VexPlayerContainerService;
import dev.vexsoft.core.paper.service.dialogs.DialogService;
import dev.vexsoft.core.paper.service.inventory.InventoryService;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import dev.vexsoft.core.common.service.stats.VexStatRegistry;
import dev.vexsoft.core.api.service.reactor.ConditionRegistry;
import dev.vexsoft.core.api.service.reactor.EffectRegistry;
import dev.vexsoft.core.api.service.reactor.FilterRegistry;
import dev.vexsoft.core.api.service.reactor.ReactorEngine;
import dev.vexsoft.core.api.service.reactor.TriggerRegistry;
import dev.vexsoft.core.common.service.reactor.VexReactorEngine;
import dev.vexsoft.core.paper.service.reactor.BlockTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.EntityTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.ItemTypeRegistry;
import dev.vexsoft.core.paper.items.service.ItemService;
import dev.vexsoft.core.common.service.localization.VexLocalizationService;
import dev.vexsoft.core.common.service.localization.VexLocalizedMessageService;
import dev.vexsoft.core.common.service.messaging.VexMessagingService;
import dev.vexsoft.core.paper.packets.service.DisplayPassengerPacketService;
import dev.vexsoft.core.paper.packets.service.FakeItemMetaService;
import dev.vexsoft.core.paper.packets.service.InteractableHologramService;
import dev.vexsoft.core.paper.packets.service.ItemDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.LightningPacketService;
import dev.vexsoft.core.paper.packets.service.MobGlowPacketService;
import dev.vexsoft.core.paper.packets.service.MobHitPacketService;
import dev.vexsoft.core.paper.packets.service.TextDisplayPacketService;
import dev.vexsoft.core.paper.service.signals.VexSignalService;
import dev.vexsoft.core.paper.service.dialogs.VexDialogService;
import dev.vexsoft.core.paper.service.inventory.VexInventoryListener;
import dev.vexsoft.core.paper.service.inventory.VexInventoryService;
import dev.vexsoft.core.paper.service.items.VexItemService;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
import dev.vexsoft.core.paper.service.listeners.VexListenerService;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.messages.VexSendMessageService;
import dev.vexsoft.core.paper.service.packets.VexDisplayPassengerPacketService;
import dev.vexsoft.core.paper.service.packets.VexFakeItemMetaService;
import dev.vexsoft.core.paper.service.packets.VexInteractableHologramService;
import dev.vexsoft.core.paper.service.packets.VexItemDisplayPacketService;
import dev.vexsoft.core.paper.service.packets.VexLightningPacketService;
import dev.vexsoft.core.paper.service.packets.VexMobGlowPacketService;
import dev.vexsoft.core.paper.service.packets.VexMobHitPacketService;
import dev.vexsoft.core.paper.service.packets.VexTextDisplayPacketService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.scheduler.VexScheduleService;
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
    checkedServices.register(SignalService.class, VexSignalService.class);
    checkedServices.register(ListenerService.class, VexListenerService.class);
    checkedServices.register(DialogService.class, VexDialogService.class);
    checkedServices.register(ItemService.class, VexItemService.class);
    checkedServices.register(DataService.class, VexDataService.class);
    checkedServices.register(PlayerContainerService.class, VexPlayerContainerService.class);
    checkedServices.register(LocalizationService.class, VexLocalizationService.class);
    checkedServices.register(LocalizedMessageService.class, VexLocalizedMessageService.class);
    checkedServices.register(SendMessageService.class, VexSendMessageService.class);
    checkedServices.register(MessagingService.class, VexMessagingService.class);
    checkedServices.register(StatRegistry.class, VexStatRegistry.class);
    checkedServices.register(TriggerRegistry.class, VexTriggerRegistry.class);
    checkedServices.register(FilterRegistry.class, VexFilterRegistry.class);
    checkedServices.register(ConditionRegistry.class, VexConditionRegistry.class);
    checkedServices.register(EffectRegistry.class, VexEffectRegistry.class);
    checkedServices.register(BlockTypeRegistry.class, VexBlockTypeRegistry.class);
    checkedServices.register(EntityTypeRegistry.class, VexEntityTypeRegistry.class);
    checkedServices.register(ItemTypeRegistry.class, VexItemTypeRegistry.class);
    checkedServices.register(ReactorEngine.class, VexReactorEngine.class);
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
        .register(VexInventoryListener.class, services);
  }
}
