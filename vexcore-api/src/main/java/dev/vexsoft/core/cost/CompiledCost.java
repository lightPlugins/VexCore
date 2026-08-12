package dev.vexsoft.core.cost;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** Runtime representation of one compiled cost entry. */
public interface CompiledCost {

  /** Checks availability without mutating player state. */
  CostCheckResult check(PlayerExecutionContext context);

  /** Atomically consumes this cost where supported by the backing system. */
  CostConsumeResult consume(PlayerExecutionContext context);

  /** Compensates a previously successful consumption. */
  CostConsumeResult refund(PlayerExecutionContext context, CostReceipt receipt);

  /** Renders this cost for chat, lore, or menus. */
  Component describe(PlayerExecutionContext context);

  /** Supplies localization-ready entries for uniform menus and messages. */
  default List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
    Component fallback = describe(context);
    return List.of(new ExecutionDescription("", Map.of(), fallback));
  }
}
