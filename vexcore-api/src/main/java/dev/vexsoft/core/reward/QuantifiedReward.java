package dev.vexsoft.core.reward;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.number.WholeAmount;
import java.util.Map;

/** A reward whose resolved quantities can be persisted without parsing its presentation. */
public interface QuantifiedReward extends CompiledReward {

    /** Resolves stable resource keys and exact amounts using the supplied execution context. */
    Map<String, WholeAmount> quantities(PlayerExecutionContext context);
}
