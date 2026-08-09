package dev.vexsoft.core.paper.reactor.trigger;

import dev.vexsoft.core.paper.reactor.context.BreakBlockReactorContext;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.reactor.ReactorId;
import dev.vexsoft.core.reactor.trigger.Trigger;
import java.util.Objects;

@ReactorId("break-block")
@Dependencies
public final class BreakBlockTrigger implements Trigger<BreakBlockReactorContext> {

  public BreakBlockTrigger(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Class<BreakBlockReactorContext> getContextType() {
    return BreakBlockReactorContext.class;
  }
}
