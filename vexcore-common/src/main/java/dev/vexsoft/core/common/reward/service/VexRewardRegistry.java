package dev.vexsoft.core.common.reward.service;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.RewardRegistry;
import dev.vexsoft.core.common.execution.service.AbstractExecutionRegistry;
import dev.vexsoft.core.common.execution.service.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.execution.service.ExecutionComponentKind;
import dev.vexsoft.core.reward.Reward;

/** Owner-scoped reward extension registry. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexRewardRegistry extends AbstractExecutionRegistry implements RewardRegistry {

    /** Creates the registry facade for the current owner. */
    public VexRewardRegistry(final VexServiceRegistry services) {
        super(services, ExecutionComponentKind.REWARD);
    }

    @Override
    public void register(final String key, final Class<? extends Reward> rewardType) {
        registerComponent(key, rewardType);
    }

    @Override
    public boolean unregister(final String key) {
        return unregisterComponent(key);
    }
}
