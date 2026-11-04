package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.cost.CostRegistry;
import dev.vexsoft.core.api.service.cost.CostService;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.currency.SharedCurrencyService;
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
import dev.vexsoft.core.common.cost.currency.CurrencyCost;
import dev.vexsoft.core.common.cost.service.VexCostRegistry;
import dev.vexsoft.core.common.cost.service.VexCostService;
import dev.vexsoft.core.common.currency.service.CurrencyPlayerData;
import dev.vexsoft.core.common.currency.service.CurrencyRegistryCoordinatorService;
import dev.vexsoft.core.common.currency.service.VexCurrencyContainer;
import dev.vexsoft.core.common.currency.service.VexCurrencyLocalizationService;
import dev.vexsoft.core.common.currency.service.VexCurrencyRegistry;
import dev.vexsoft.core.common.currency.service.VexCurrencyRegistryCoordinatorService;
import dev.vexsoft.core.common.currency.service.VexSharedCurrencyService;
import dev.vexsoft.core.common.execution.service.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.execution.service.VexExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.expression.service.VexExpressionService;
import dev.vexsoft.core.common.level.service.VexLevelClaimService;
import dev.vexsoft.core.common.level.service.VexLevelService;
import dev.vexsoft.core.common.player.service.data.VexDataService;
import dev.vexsoft.core.common.player.service.data.VexPlayerContainerService;
import dev.vexsoft.core.common.requirement.service.VexRequirementRegistry;
import dev.vexsoft.core.common.requirement.service.VexRequirementService;
import dev.vexsoft.core.common.requirement.stat.StatRequirement;
import dev.vexsoft.core.common.reward.currency.CurrencyReward;
import dev.vexsoft.core.common.reward.service.VexRewardRegistry;
import dev.vexsoft.core.common.reward.service.VexRewardService;
import dev.vexsoft.core.common.reward.stat.StatReward;
import dev.vexsoft.core.common.stats.service.GameplayPlayerData;
import dev.vexsoft.core.common.stats.service.StatRegistryCoordinatorService;
import dev.vexsoft.core.common.stats.service.VexStatContainer;
import dev.vexsoft.core.common.stats.service.VexStatLocalizationService;
import dev.vexsoft.core.common.stats.service.VexStatRegistry;
import dev.vexsoft.core.common.stats.service.VexStatRegistryCoordinatorService;
import dev.vexsoft.core.common.stats.service.contribution.StatContributionCoordinatorService;
import dev.vexsoft.core.common.stats.service.contribution.VexStatContributionCoordinatorService;
import dev.vexsoft.core.common.stats.service.contribution.VexStatContributionRegistry;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.paper.block.service.VexResourceBlockService;
import dev.vexsoft.core.paper.cost.coin.VaultCoinCost;
import dev.vexsoft.core.paper.economy.service.VexVaultEconomyService;
import dev.vexsoft.core.paper.requirement.coin.VaultCoinRequirement;
import dev.vexsoft.core.paper.requirement.permission.PermissionRequirement;
import dev.vexsoft.core.paper.reward.coin.VaultCoinReward;
import dev.vexsoft.core.paper.reward.item.ItemReward;
import dev.vexsoft.core.paper.reward.listener.PrivateRewardItemListener;
import dev.vexsoft.core.paper.reward.service.VexRewardItemService;
import dev.vexsoft.core.paper.service.block.ResourceBlockService;
import dev.vexsoft.core.paper.service.economy.EconomyService;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
import dev.vexsoft.core.paper.service.reward.RewardItemService;
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
        services.register(CurrencyRegistryCoordinatorService.class, VexCurrencyRegistryCoordinatorService.class);
        services.register(SharedCurrencyService.class, VexSharedCurrencyService.class);
        services.register(ExecutionComponentCoordinatorService.class, VexExecutionComponentCoordinatorService.class);
        services.register(StatContributionCoordinatorService.class, VexStatContributionCoordinatorService.class);
        services.register(StatRegistry.class, VexStatRegistry.class);
        services.register(StatLocalizationService.class, VexStatLocalizationService.class);
        services.register(CurrencyRegistry.class, VexCurrencyRegistry.class);
        services.register(CurrencyLocalizationService.class, VexCurrencyLocalizationService.class);
        services.register(ExpressionService.class, VexExpressionService.class);
        services.register(RewardRegistry.class, VexRewardRegistry.class);
        services.register(RewardService.class, VexRewardService.class);
        services.register(RewardItemService.class, VexRewardItemService.class);
        services.register(ResourceBlockService.class, VexResourceBlockService.class);
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
        services.require(DataService.class).register(CurrencyPlayerData.class);
        StatRegistryCoordinatorService coordinator = services.require(StatRegistryCoordinatorService.class);

        services.require(PlayerContainerService.class)
            .register(
                StatContainer.class,
                player -> new VexStatContainer(player, coordinator)
            );
        services.require(PlayerContainerService.class).register(CurrencyContainer.class,
            player -> new VexCurrencyContainer(player, services.require(SharedCurrencyService.class)));
        registerBuiltInTypes();
    }

    @Override
    public void start() {
        if (services == null) {
            throw new IllegalStateException("GameplayModule has not been loaded yet");
        }

        services.require(ListenerService.class).register(PrivateRewardItemListener.class, services);
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
        services.require(RewardRegistry.class).register("currencies", CurrencyReward.class);
        services.require(RewardRegistry.class).register("items", ItemReward.class);
        services.require(CostRegistry.class).register("currencies", CurrencyCost.class);
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
