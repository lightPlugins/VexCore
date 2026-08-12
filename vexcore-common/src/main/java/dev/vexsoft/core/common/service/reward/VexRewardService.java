package dev.vexsoft.core.common.service.reward;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentKind;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.CompiledRewards;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardContribution;
import dev.vexsoft.core.reward.RewardContributions;
import dev.vexsoft.core.reward.RewardExecutionReport;
import dev.vexsoft.core.reward.RewardResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Default registry-backed reward compiler and executor. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexRewardService implements RewardService {

  private final ExecutionComponentCoordinatorService components;

  /** Captures the shared component registry. */
  public VexRewardService(final VexServiceRegistry services) {
    components = Objects.requireNonNull(services, "services")
        .require(ExecutionComponentCoordinatorService.class);
  }

  @Override
  public CompiledRewards compile(final ConfigurationSection section) {
    ConfigurationSection checked = Objects.requireNonNull(section, "section");
    List<CompiledRewards.Entry> entries = new ArrayList<>();
    for (String key : checked.getKeys(false)) {
      Reward reward = components.find(ExecutionComponentKind.REWARD, key)
          .map(Reward.class::cast)
          .orElseThrow(() -> new IllegalArgumentException("Unknown reward key: " + key));
      try {
        entries.add(new CompiledRewards.Entry(key, reward.compile(checked.get(key))));
      } catch (RuntimeException exception) {
        throw new IllegalArgumentException("Invalid reward '" + key + "'", exception);
      }
    }
    return new CompiledRewards(entries);
  }

  @Override
  public RewardExecutionReport grantActions(
      final CompiledRewards rewards,
      final PlayerExecutionContext context
  ) {
    Map<String, RewardResult> results = new LinkedHashMap<>();
    for (CompiledRewards.Entry entry : Objects.requireNonNull(rewards, "rewards").entries()) {
      if (entry.reward().getBehavior() == RewardBehavior.ACTION) {
        results.put(entry.key(), entry.reward().grant(context));
      }
    }
    return new RewardExecutionReport(results);
  }

  @Override
  public RewardContributions calculateContributions(
      final Collection<RewardInvocation> invocations
  ) {
    Map<String, RewardContribution> result = new LinkedHashMap<>();
    for (RewardInvocation invocation : Objects.requireNonNull(invocations, "invocations")) {
      for (CompiledRewards.Entry entry : invocation.rewards().entries()) {
        CompiledReward reward = entry.reward();
        if (reward.getBehavior() != RewardBehavior.CONTRIBUTION) {
          continue;
        }
        RewardContribution contribution = reward.contribute(invocation.context());
        result.merge(entry.key(), contribution, RewardContribution::merge);
      }
    }
    return new RewardContributions(result);
  }

  @Override
  public List<Component> describe(
      final CompiledRewards rewards,
      final PlayerExecutionContext context
  ) {
    return rewards.entries().stream().map(entry -> entry.reward().describe(context)).toList();
  }
}
