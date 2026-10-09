package dev.vexsoft.core.reward;

import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** Runtime representation of one compiled reward entry. */
public interface CompiledReward {

    /** Returns the shared chance and announcement settings for this entry. */
    default RewardOptions getOptions() {
        return RewardOptions.guaranteed();
    }

    /** Freezes any randomly generated amounts or item identities before an action is granted. */
    default CompiledReward prepare(final PlayerExecutionContext context) {
        return this;
    }

    /** Returns whether this reward is an action or a reconstructable contribution. */
    RewardBehavior getBehavior();

    /** True for actions with player rollback or registered external compensations and deferred notifications. */
    default boolean supportsPlayerRollback() {
        return false;
    }

    /** Returns whether granting this action may change native inventory slots or the cursor. */
    default boolean changesInventory() {
        return true;
    }

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
