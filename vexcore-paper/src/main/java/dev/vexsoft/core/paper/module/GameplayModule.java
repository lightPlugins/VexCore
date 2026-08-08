package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.gameplay.reactor.condition.ChanceCondition;
import dev.vexsoft.core.gameplay.reactor.condition.StatComparisonCondition;
import dev.vexsoft.core.gameplay.reactor.effect.AddStatEffect;
import dev.vexsoft.core.gameplay.reactor.registry.ReactorRegistryCoordinatorService;
import dev.vexsoft.core.gameplay.reactor.registry.VexConditionRegistry;
import dev.vexsoft.core.gameplay.reactor.registry.VexEffectRegistry;
import dev.vexsoft.core.gameplay.reactor.registry.VexFilterRegistry;
import dev.vexsoft.core.gameplay.reactor.registry.VexReactorRegistryCoordinatorService;
import dev.vexsoft.core.gameplay.reactor.registry.VexTriggerRegistry;
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
import dev.vexsoft.core.paper.reactor.registry.BlockTypeCoordinatorService;
import dev.vexsoft.core.paper.reactor.registry.EntityTypeCoordinatorService;
import dev.vexsoft.core.paper.reactor.registry.ItemTypeCoordinatorService;
import dev.vexsoft.core.paper.reactor.registry.VexBlockTypeCoordinatorService;
import dev.vexsoft.core.paper.reactor.registry.VexBlockTypeRegistry;
import dev.vexsoft.core.paper.reactor.registry.VexEntityTypeCoordinatorService;
import dev.vexsoft.core.paper.reactor.registry.VexEntityTypeRegistry;
import dev.vexsoft.core.paper.reactor.registry.VexItemTypeCoordinatorService;
import dev.vexsoft.core.paper.reactor.registry.VexItemTypeRegistry;
import dev.vexsoft.core.paper.reactor.trigger.BreakBlockTrigger;
import dev.vexsoft.core.paper.reactor.trigger.DamageEntityTrigger;
import dev.vexsoft.core.paper.reactor.trigger.KillTrigger;

import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.api.player.PlayerContainerService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.data.VexDataService;
import dev.vexsoft.core.data.VexPlayerContainerService;
import dev.vexsoft.core.gameplay.stat.GameplayPlayerData;
import dev.vexsoft.core.gameplay.stat.StatContainer;
import dev.vexsoft.core.gameplay.stat.StatRegistry;
import dev.vexsoft.core.gameplay.stat.StatRegistryCoordinatorService;
import dev.vexsoft.core.gameplay.stat.VexStatContainer;
import dev.vexsoft.core.gameplay.stat.VexStatRegistry;
import dev.vexsoft.core.gameplay.stat.VexStatRegistryCoordinatorService;
import dev.vexsoft.core.gameplay.reactor.condition.ConditionRegistry;
import dev.vexsoft.core.gameplay.reactor.effect.EffectRegistry;
import dev.vexsoft.core.gameplay.reactor.filter.FilterRegistry;
import dev.vexsoft.core.gameplay.reactor.ReactorEngine;
import dev.vexsoft.core.gameplay.reactor.trigger.TriggerRegistry;
import dev.vexsoft.core.gameplay.reactor.VexReactorEngine;
import dev.vexsoft.core.gameplay.reactor.expression.ExpressionService;
import dev.vexsoft.core.gameplay.reactor.expression.VexExpressionService;
import dev.vexsoft.core.paper.listener.ListenerService;
import dev.vexsoft.core.paper.reactor.provider.BlockTypeRegistry;
import dev.vexsoft.core.paper.reactor.provider.EntityTypeRegistry;
import dev.vexsoft.core.paper.reactor.provider.ItemTypeRegistry;

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
    services.register(ExpressionService.class, VexExpressionService.class);
    services.register(TriggerRegistry.class, VexTriggerRegistry.class);
    services.register(FilterRegistry.class, VexFilterRegistry.class);
    services.register(ConditionRegistry.class, VexConditionRegistry.class);
    services.register(EffectRegistry.class, VexEffectRegistry.class);
    services.register(BlockTypeRegistry.class, VexBlockTypeRegistry.class);
    services.register(EntityTypeRegistry.class, VexEntityTypeRegistry.class);
    services.register(ItemTypeRegistry.class, VexItemTypeRegistry.class);
    services.register(ReactorEngine.class, VexReactorEngine.class);
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
    services.require(ListenerService.class).register(VexReactorListener.class);
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
