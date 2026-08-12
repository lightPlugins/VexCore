package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.cost.CostRegistry;
import dev.vexsoft.core.api.service.cost.CostService;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.level.LevelClaimService;
import dev.vexsoft.core.api.service.level.LevelService;
import dev.vexsoft.core.api.service.player.DataService;
import dev.vexsoft.core.api.service.player.PlayerContainerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementService;
import dev.vexsoft.core.api.service.reward.RewardRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.api.service.stats.StatLocalizationService;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import dev.vexsoft.core.api.service.stats.contribution.StatContributionRegistry;
import dev.vexsoft.core.common.requirement.stat.StatRequirement;
import dev.vexsoft.core.common.reward.stat.StatReward;
import dev.vexsoft.core.common.service.cost.VexCostRegistry;
import dev.vexsoft.core.common.service.cost.VexCostService;
import dev.vexsoft.core.common.service.data.VexDataService;
import dev.vexsoft.core.common.service.data.VexPlayerContainerService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.VexExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.expression.VexExpressionService;
import dev.vexsoft.core.common.service.level.VexLevelClaimService;
import dev.vexsoft.core.common.service.level.VexLevelService;
import dev.vexsoft.core.common.service.requirement.VexRequirementRegistry;
import dev.vexsoft.core.common.service.requirement.VexRequirementService;
import dev.vexsoft.core.common.service.reward.VexRewardRegistry;
import dev.vexsoft.core.common.service.reward.VexRewardService;
import dev.vexsoft.core.common.service.stats.GameplayPlayerData;
import dev.vexsoft.core.common.service.stats.StatRegistryCoordinatorService;
import dev.vexsoft.core.common.service.stats.VexStatContainer;
import dev.vexsoft.core.common.service.stats.VexStatLocalizationService;
import dev.vexsoft.core.common.service.stats.VexStatRegistry;
import dev.vexsoft.core.common.service.stats.VexStatRegistryCoordinatorService;
import dev.vexsoft.core.common.service.stats.contribution.StatContributionCoordinatorService;
import dev.vexsoft.core.common.service.stats.contribution.VexStatContributionCoordinatorService;
import dev.vexsoft.core.common.service.stats.contribution.VexStatContributionRegistry;
import dev.vexsoft.core.paper.cost.coin.VaultCoinCost;
import dev.vexsoft.core.paper.requirement.coin.VaultCoinRequirement;
import dev.vexsoft.core.paper.requirement.permission.PermissionRequirement;
import dev.vexsoft.core.paper.reward.coin.VaultCoinReward;
import dev.vexsoft.core.paper.service.economy.EconomyService;
import dev.vexsoft.core.paper.service.economy.VexVaultEconomyService;
import dev.vexsoft.core.stats.StatContainer;
import org.bukkit.Bukkit;

/** Installs stats and the extensible reward, cost, and requirement runtimes. */
public final class GameplayModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(DataService.class, VexDataService.class);
    services.register(PlayerContainerService.class, VexPlayerContainerService.class);
    services.register(StatRegistryCoordinatorService.class, VexStatRegistryCoordinatorService.class);
    services.register(
        ExecutionComponentCoordinatorService.class,
        VexExecutionComponentCoordinatorService.class
    );
    services.register(
        StatContributionCoordinatorService.class,
        VexStatContributionCoordinatorService.class
    );
    services.register(StatRegistry.class, VexStatRegistry.class);
    services.register(StatLocalizationService.class, VexStatLocalizationService.class);
    services.register(ExpressionService.class, VexExpressionService.class);
    services.register(RewardRegistry.class, VexRewardRegistry.class);
    services.register(RewardService.class, VexRewardService.class);
    services.register(CostRegistry.class, VexCostRegistry.class);
    services.register(CostService.class, VexCostService.class);
    services.register(RequirementRegistry.class, VexRequirementRegistry.class);
    services.register(RequirementService.class, VexRequirementService.class);
    services.register(LevelService.class, VexLevelService.class);
    services.register(LevelClaimService.class, VexLevelClaimService.class);
    services.register(StatContributionRegistry.class, VexStatContributionRegistry.class);
    if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
      services.register(EconomyService.class, VexVaultEconomyService.class);
    }
    services.registerQueuedServices();

    services.require(DataService.class).register(GameplayPlayerData.class);
    StatRegistryCoordinatorService coordinator = services.require(
        StatRegistryCoordinatorService.class
    );
    services.require(PlayerContainerService.class).register(
        StatContainer.class,
        player -> new VexStatContainer(player, coordinator)
    );
    registerBuiltInTypes();
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

  private void registerBuiltInTypes() {
    services.require(RewardRegistry.class).register("stats", StatReward.class);
    RequirementRegistry requirements = services.require(RequirementRegistry.class);
    requirements.register("stats", StatRequirement.class);
    requirements.register("permission", PermissionRequirement.class);

    if (services.find(EconomyService.class).filter(EconomyService::isAvailable).isPresent()) {
      services.require(RewardRegistry.class).register("coins", VaultCoinReward.class);
      services.require(CostRegistry.class).register("coins", VaultCoinCost.class);
      requirements.register("coins", VaultCoinRequirement.class);
    }
  }
}
