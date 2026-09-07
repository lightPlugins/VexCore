package dev.vexsoft.core.common.service.reward;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentKind;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.TypedExecutionDescription;
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
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;

/** Default registry-backed reward compiler and executor. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexRewardService implements RewardService {

  private final ExecutionComponentCoordinatorService components;
  private final ObjectMapper transactionMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
          .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  @Override
  public boolean executeAtomically(
      final PlayerExecutionContext context, final BooleanSupplier operation) {
    return context
        .player()
        .atomic(value -> transactionMapper.convertValue(value, value.getClass()), operation);
  }

  /** Captures the shared component registry. */
  public VexRewardService(final VexServiceRegistry services) {
    components =
        Objects.requireNonNull(services, "services")
            .require(ExecutionComponentCoordinatorService.class);
  }

  @Override
  public CompiledRewards compile(final ConfigurationSection section) {
    ConfigurationSection checked = Objects.requireNonNull(section, "section");
    List<CompiledRewards.Entry> entries = new ArrayList<>();
    for (String key : checked.getKeys(false)) {
      Reward reward =
          components
              .find(ExecutionComponentKind.REWARD, key)
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
      final CompiledRewards rewards, final PlayerExecutionContext context) {
    Map<String, RewardResult> results = new LinkedHashMap<>();
    for (CompiledRewards.Entry entry : Objects.requireNonNull(rewards, "rewards").entries()) {
      if (entry.reward().getBehavior() == RewardBehavior.ACTION) {
        RewardResult result = entry.reward().grant(context);
        results.put(uniqueResultKey(results, entry.key()), result);
        if (result.status() != RewardResult.Status.SUCCESS) {
          break;
        }
      }
    }
    return new RewardExecutionReport(results);
  }

  private static String uniqueResultKey(final Map<String, RewardResult> results, final String key) {
    if (!results.containsKey(key)) {
      return key;
    }
    int occurrence = 2;
    while (results.containsKey(key + '#' + occurrence)) {
      occurrence++;
    }
    return key + '#' + occurrence;
  }

  @Override
  public RewardContributions calculateContributions(
      final Collection<RewardInvocation> invocations) {
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
      final CompiledRewards rewards, final PlayerExecutionContext context) {
    return rewards.entries().stream().map(entry -> entry.reward().describe(context)).toList();
  }

  @Override
  public List<TypedExecutionDescription> present(
      final CompiledRewards rewards, final PlayerExecutionContext context) {
    return rewards.entries().stream()
        .flatMap(
            entry ->
                entry.reward().describeEntries(context).stream()
                    .map(description -> TypedExecutionDescription.of(entry.key(), description)))
        .toList();
  }
}
