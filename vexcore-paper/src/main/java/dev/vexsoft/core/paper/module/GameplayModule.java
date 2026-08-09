package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.common.reactor.condition.ChanceCondition;
import dev.vexsoft.core.common.reactor.condition.StatComparisonCondition;
import dev.vexsoft.core.common.reactor.effect.AddStatEffect;
import dev.vexsoft.core.common.service.reactor.ReactorRegistryCoordinatorService;
import dev.vexsoft.core.common.service.reactor.VexConditionRegistry;
import dev.vexsoft.core.common.service.reactor.VexEffectRegistry;
import dev.vexsoft.core.common.service.reactor.VexFilterRegistry;
import dev.vexsoft.core.common.service.reactor.VexReactorRegistryCoordinatorService;
import dev.vexsoft.core.common.service.reactor.VexTriggerRegistry;
import dev.vexsoft.core.paper.reactor.condition.PermissionCondition;
import dev.vexsoft.core.paper.reactor.effect.CancelTriggerEffect;
import dev.vexsoft.core.paper.reactor.effect.ModifyDamageEffect;
import dev.vexsoft.core.paper.reactor.filter.BlocksFilter;
import dev.vexsoft.core.paper.reactor.filter.DamageTypesFilter;
import dev.vexsoft.core.paper.reactor.filter.EntitiesFilter;
import dev.vexsoft.core.paper.reactor.filter.ItemsFilter;
import dev.vexsoft.core.paper.reactor.filter.NotBlocksFilter;
import dev.vexsoft.core.paper.reactor.filter.NotEntitiesFilter;
import dev.vexsoft.core.paper.reactor.filter.NotItemsFilter;
import dev.vexsoft.core.paper.reactor.listener.VexReactorListener;
import dev.vexsoft.core.paper.reactor.provider.MinecraftBlockTypeProvider;
import dev.vexsoft.core.paper.reactor.provider.MinecraftEntityTypeProvider;
import dev.vexsoft.core.paper.reactor.provider.MinecraftItemTypeProvider;
import dev.vexsoft.core.paper.service.reactor.BlockTypeCoordinatorService;
import dev.vexsoft.core.paper.service.reactor.EntityTypeCoordinatorService;
import dev.vexsoft.core.paper.service.reactor.ItemTypeCoordinatorService;
import dev.vexsoft.core.paper.service.reactor.VexBlockTypeCoordinatorService;
import dev.vexsoft.core.paper.service.reactor.VexBlockTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.VexEntityTypeCoordinatorService;
import dev.vexsoft.core.paper.service.reactor.VexEntityTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.VexItemTypeCoordinatorService;
import dev.vexsoft.core.paper.service.reactor.VexItemTypeRegistry;
import dev.vexsoft.core.paper.reactor.trigger.BreakBlockTrigger;
import dev.vexsoft.core.paper.reactor.trigger.DamageEntityTrigger;
import dev.vexsoft.core.paper.reactor.trigger.KillTrigger;

import dev.vexsoft.core.api.service.player.DataService;
import dev.vexsoft.core.api.service.player.PlayerContainerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.VexDataService;
import dev.vexsoft.core.common.service.data.VexPlayerContainerService;
import dev.vexsoft.core.common.service.stats.GameplayPlayerData;
import dev.vexsoft.core.stats.StatContainer;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import dev.vexsoft.core.api.service.stats.StatLocalizationService;
import dev.vexsoft.core.common.service.stats.StatRegistryCoordinatorService;
import dev.vexsoft.core.common.service.stats.VexStatContainer;
import dev.vexsoft.core.common.service.stats.VexStatRegistry;
import dev.vexsoft.core.common.service.stats.VexStatRegistryCoordinatorService;
import dev.vexsoft.core.common.service.stats.VexStatLocalizationService;
import dev.vexsoft.core.api.service.reactor.ConditionRegistry;
import dev.vexsoft.core.api.service.reactor.EffectRegistry;
import dev.vexsoft.core.api.service.reactor.FilterRegistry;
import dev.vexsoft.core.api.service.reactor.ReactorEngine;
import dev.vexsoft.core.api.service.reactor.ReactionConfigurationService;
import dev.vexsoft.core.api.service.reactor.TriggerRegistry;
import dev.vexsoft.core.common.service.reactor.VexReactorEngine;
import dev.vexsoft.core.common.service.reactor.VexReactionConfigurationService;
import dev.vexsoft.core.api.service.reactor.ExpressionService;
import dev.vexsoft.core.common.service.reactor.VexExpressionService;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
import dev.vexsoft.core.paper.service.reactor.BlockTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.EntityTypeRegistry;
import dev.vexsoft.core.paper.service.reactor.ItemTypeRegistry;

/** Installs the shared stat runtime and its player container. */
public final class GameplayModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(DataService.class, VexDataService.class);
    services.register(PlayerContainerService.class, VexPlayerContainerService.class);
    services.register(
        StatRegistryCoordinatorService.class,
        VexStatRegistryCoordinatorService.class
    );
    services.register(
        ReactorRegistryCoordinatorService.class,
        VexReactorRegistryCoordinatorService.class
    );
    services.register(BlockTypeCoordinatorService.class, VexBlockTypeCoordinatorService.class);
    services.register(EntityTypeCoordinatorService.class, VexEntityTypeCoordinatorService.class);
    services.register(ItemTypeCoordinatorService.class, VexItemTypeCoordinatorService.class);
    services.register(StatRegistry.class, VexStatRegistry.class);
    services.register(StatLocalizationService.class, VexStatLocalizationService.class);
    services.register(ExpressionService.class, VexExpressionService.class);
    services.register(TriggerRegistry.class, VexTriggerRegistry.class);
    services.register(FilterRegistry.class, VexFilterRegistry.class);
    services.register(ConditionRegistry.class, VexConditionRegistry.class);
    services.register(EffectRegistry.class, VexEffectRegistry.class);
    services.register(BlockTypeRegistry.class, VexBlockTypeRegistry.class);
    services.register(EntityTypeRegistry.class, VexEntityTypeRegistry.class);
    services.register(ItemTypeRegistry.class, VexItemTypeRegistry.class);
    services.register(ReactorEngine.class, VexReactorEngine.class);
    services.register(
        ReactionConfigurationService.class,
        VexReactionConfigurationService.class
    );
    services.registerQueuedServices();
    services.require(DataService.class).register(GameplayPlayerData.class);
    StatRegistryCoordinatorService coordinator = services.require(
        StatRegistryCoordinatorService.class
    );
    services.require(PlayerContainerService.class).register(
        StatContainer.class,
        player -> new VexStatContainer(player, coordinator)
    );
    registerReactorComponents();
  }

  @Override
  public void start() {
    services.require(ListenerService.class).register(VexReactorListener.class, services);
  }

  @Override
  public void disable() {
    if (services != null) {
      services.unregisterOwnedServices();
    }
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore_gameplay";
  }

  private void registerReactorComponents() {
    services.require(BlockTypeRegistry.class).register(MinecraftBlockTypeProvider.class);
    services.require(EntityTypeRegistry.class).register(MinecraftEntityTypeProvider.class);
    services.require(ItemTypeRegistry.class).register(MinecraftItemTypeProvider.class);
    TriggerRegistry triggers = services.require(TriggerRegistry.class);
    triggers.register(BreakBlockTrigger.class);
    triggers.register(DamageEntityTrigger.class);
    triggers.register(KillTrigger.class);
    FilterRegistry filters = services.require(FilterRegistry.class);
    filters.register(BlocksFilter.class);
    filters.register(NotBlocksFilter.class);
    filters.register(EntitiesFilter.class);
    filters.register(NotEntitiesFilter.class);
    filters.register(ItemsFilter.class);
    filters.register(NotItemsFilter.class);
    filters.register(DamageTypesFilter.class);
    ConditionRegistry conditions = services.require(ConditionRegistry.class);
    conditions.register(ChanceCondition.class);
    conditions.register(StatComparisonCondition.class);
    conditions.register(PermissionCondition.class);
    EffectRegistry effects = services.require(EffectRegistry.class);
    effects.register(AddStatEffect.class);
    effects.register(ModifyDamageEffect.class);
    effects.register(CancelTriggerEffect.class);
  }
}
