package dev.vexsoft.core.gameplay.reactor.condition;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import dev.vexsoft.core.gameplay.reactor.internal.ReactorArguments;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Applies a configured percentage chance without shared random contention. */
@ReactorId("chance")
@Dependencies
public final class ChanceCondition implements Condition<ReactorContext> {

  /** Creates the stateless chance condition. */
  public ChanceCondition(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Class<ReactorContext> getContextType() {
    return ReactorContext.class;
  }

  @Override
  public CompiledCondition<ReactorContext> compile(final Map<String, Object> arguments) {
    double percentage = ReactorArguments.number(arguments, "percentage");
    if (percentage < 0D || percentage > 100D) {
      throw new IllegalArgumentException("Chance percentage must be between 0 and 100");
    }
    if (percentage == 0D) {
      return ignored -> false;
    }
    if (percentage == 100D) {
      return ignored -> true;
    }
    return ignored -> ThreadLocalRandom.current().nextDouble(100D) < percentage;
  }
}
