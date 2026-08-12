package dev.vexsoft.core.api.service.reward;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.TypedExecutionDescription;
import dev.vexsoft.core.reward.CompiledRewards;
import dev.vexsoft.core.reward.RewardContributions;
import dev.vexsoft.core.reward.RewardExecutionReport;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Compiles, executes, evaluates, and presents extensible reward sections. */
public interface RewardService extends VexService {

  /** Compiles every direct key in a reward section. */
  CompiledRewards compile(ConfigurationSection section);

  /** Executes only action rewards and leaves reconstructable contributions untouched. */
  RewardExecutionReport grantActions(CompiledRewards rewards, PlayerExecutionContext context);

  /** Aggregates contribution rewards from multiple progression rule invocations. */
  RewardContributions calculateContributions(Collection<RewardInvocation> invocations);

  /** Renders every configured reward using its owning plugin's presentation. */
  List<Component> describe(CompiledRewards rewards, PlayerExecutionContext context);

  /** Returns typed localization-ready reward lines. */
  List<TypedExecutionDescription> present(
      CompiledRewards rewards,
      PlayerExecutionContext context
  );

  /** Associates one compiled section with the variables of the progression point it represents. */
  record RewardInvocation(CompiledRewards rewards, PlayerExecutionContext context) {}
}
