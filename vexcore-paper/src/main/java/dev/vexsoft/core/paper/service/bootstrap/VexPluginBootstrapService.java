package dev.vexsoft.core.paper.service.bootstrap;

import dev.vexsoft.core.api.service.cost.CostRegistry;
import dev.vexsoft.core.api.service.cost.CostService;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.level.LevelClaimService;
import dev.vexsoft.core.api.service.level.LevelService;
import dev.vexsoft.core.api.service.requirement.RequirementRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementService;
import dev.vexsoft.core.api.service.reward.RewardRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.api.service.stats.contribution.StatContributionRegistry;
import dev.vexsoft.core.common.service.cost.VexCostRegistry;
import dev.vexsoft.core.common.service.cost.VexCostService;
import dev.vexsoft.core.common.service.expression.VexExpressionService;
import dev.vexsoft.core.common.service.level.VexLevelClaimService;
import dev.vexsoft.core.common.service.level.VexLevelService;
import dev.vexsoft.core.common.service.requirement.VexRequirementRegistry;
import dev.vexsoft.core.common.service.requirement.VexRequirementService;
import dev.vexsoft.core.common.service.reward.VexRewardRegistry;
import dev.vexsoft.core.common.service.reward.VexRewardService;
import dev.vexsoft.core.common.service.stats.contribution.VexStatContributionRegistry;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.localization.LocalizedMessageService;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.globaldata.GlobalDataService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
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
import dev.vexsoft.core.paper.items.service.ItemService;
import dev.vexsoft.core.common.service.localization.VexLocalizationService;
import dev.vexsoft.core.common.service.localization.VexLocalizedMessageService;
import dev.vexsoft.core.common.service.messaging.VexMessagingService;
import dev.vexsoft.core.common.service.globaldata.VexGlobalDataService;
import dev.vexsoft.core.paper.packets.service.BlockDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.BlockDamageOverlayPacketService;
import dev.vexsoft.core.paper.packets.service.DisplayPassengerPacketService;
import dev.vexsoft.core.paper.packets.service.FakeItemMetaService;
import dev.vexsoft.core.paper.packets.service.InteractableHologramService;
import dev.vexsoft.core.paper.packets.service.InteractionPacketService;
import dev.vexsoft.core.paper.packets.service.ItemDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.LightningPacketService;
import dev.vexsoft.core.paper.packets.service.MobGlowPacketService;
import dev.vexsoft.core.paper.packets.service.MobHitPacketService;
import dev.vexsoft.core.paper.packets.service.PlayerAnimationPacketService;
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
import dev.vexsoft.core.paper.service.packets.VexBlockDisplayPacketService;
import dev.vexsoft.core.paper.service.packets.VexBlockDamageOverlayPacketService;
import dev.vexsoft.core.paper.service.packets.VexDisplayPassengerPacketService;
import dev.vexsoft.core.paper.service.packets.VexFakeItemMetaService;
import dev.vexsoft.core.paper.service.packets.VexInteractableHologramService;
import dev.vexsoft.core.paper.service.packets.VexInteractionPacketService;
import dev.vexsoft.core.paper.service.packets.VexItemDisplayPacketService;
import dev.vexsoft.core.paper.service.packets.VexLightningPacketService;
import dev.vexsoft.core.paper.service.packets.VexMobGlowPacketService;
import dev.vexsoft.core.paper.service.packets.VexMobHitPacketService;
import dev.vexsoft.core.paper.service.packets.VexPlayerAnimationPacketService;
import dev.vexsoft.core.paper.service.packets.VexTextDisplayPacketService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.scheduler.VexScheduleService;
import dev.vexsoft.core.paper.service.placeholder.PlaceholderApiBridgeService;
import dev.vexsoft.core.paper.service.placeholder.VexPaperPlaceholderService;
import dev.vexsoft.core.paper.service.placeholder.VexPlaceholderApiBridgeService;
import dev.vexsoft.core.paper.service.teleport.PlayerTeleportService;
import dev.vexsoft.core.paper.service.teleport.VexPlayerTeleportService;
import dev.vexsoft.core.api.service.network.PlayerDirectoryService;
import dev.vexsoft.core.paper.service.directory.VexPlayerDirectoryService;
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
    checkedServices.register(GlobalDataService.class, VexGlobalDataService.class);
    checkedServices.register(PlaceholderService.class, VexPaperPlaceholderService.class);
    checkedServices.register(PlayerTeleportService.class, VexPlayerTeleportService.class);
    checkedServices.register(PlayerDirectoryService.class, VexPlayerDirectoryService.class);
    checkedServices.register(
        PlaceholderApiBridgeService.class,
        VexPlaceholderApiBridgeService.class
    );
    checkedServices.register(StatRegistry.class, VexStatRegistry.class);
    checkedServices.register(ExpressionService.class, VexExpressionService.class);
    checkedServices.register(RewardRegistry.class, VexRewardRegistry.class);
    checkedServices.register(RewardService.class, VexRewardService.class);
    checkedServices.register(CostRegistry.class, VexCostRegistry.class);
    checkedServices.register(CostService.class, VexCostService.class);
    checkedServices.register(RequirementRegistry.class, VexRequirementRegistry.class);
    checkedServices.register(RequirementService.class, VexRequirementService.class);
    checkedServices.register(LevelService.class, VexLevelService.class);
    checkedServices.register(LevelClaimService.class, VexLevelClaimService.class);
    checkedServices.register(
        StatContributionRegistry.class,
        VexStatContributionRegistry.class
    );
    checkedServices.register(TextDisplayPacketService.class, VexTextDisplayPacketService.class);
    checkedServices.register(ItemDisplayPacketService.class, VexItemDisplayPacketService.class);
    checkedServices.register(BlockDisplayPacketService.class, VexBlockDisplayPacketService.class);
    checkedServices.register(
        BlockDamageOverlayPacketService.class,
        VexBlockDamageOverlayPacketService.class
    );
    checkedServices.register(InteractionPacketService.class, VexInteractionPacketService.class);
    checkedServices.register(
        DisplayPassengerPacketService.class,
        VexDisplayPassengerPacketService.class
    );
    checkedServices.register(
        InteractableHologramService.class,
        VexInteractableHologramService.class
    );
    checkedServices.register(MobHitPacketService.class, VexMobHitPacketService.class);
    checkedServices.register(
        PlayerAnimationPacketService.class,
        VexPlayerAnimationPacketService.class
    );
    checkedServices.register(MobGlowPacketService.class, VexMobGlowPacketService.class);
    checkedServices.register(LightningPacketService.class, VexLightningPacketService.class);
    checkedServices.register(FakeItemMetaService.class, VexFakeItemMetaService.class);
  }

  @Override
  public void enable(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    checkedServices.require(PlaceholderApiBridgeService.class).enable();
    checkedServices.require(ListenerService.class).register(VexInventoryListener.class, services);
  }
}
