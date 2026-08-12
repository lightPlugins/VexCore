package dev.vexsoft.core.cost;

import dev.vexsoft.core.execution.PlayerExecutionContext;
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
}
