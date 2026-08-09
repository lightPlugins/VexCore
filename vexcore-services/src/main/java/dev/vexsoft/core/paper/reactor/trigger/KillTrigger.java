package dev.vexsoft.core.paper.reactor.trigger;

import dev.vexsoft.core.paper.reactor.context.KillReactorContext;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import dev.vexsoft.core.gameplay.reactor.trigger.Trigger;
import java.util.Objects;

@ReactorId("kill")
@Dependencies
public final class KillTrigger implements Trigger<KillReactorContext> {

  public KillTrigger(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Class<KillReactorContext> getContextType() {
    return KillReactorContext.class;
  }
}
