package dev.vexsoft.core.reward;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** Runtime representation of one compiled reward entry. */
public interface CompiledReward {

  /** Returns whether this reward is an action or a reconstructable contribution. */
  RewardBehavior getBehavior();

  /** Executes an action reward. */
  default RewardResult grant(final PlayerExecutionContext context) {
    return RewardResult.skipped("Reward is a runtime contribution");
  }

  /** Calculates a reconstructable contribution reward. */
  default RewardContribution contribute(final PlayerExecutionContext context) {
    throw new IllegalStateException("Reward is not a runtime contribution");
  }

  /** Renders this reward for chat, lore, or menus. */
  Component describe(PlayerExecutionContext context);

  /** Supplies localization-ready entries; implementations may expand one map into multiple lines. */
  default List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
    Component fallback = describe(context);
    return List.of(new ExecutionDescription("", Map.of(), fallback));
  }
}
