package dev.vexsoft.core.paper.reactor.trigger;

import dev.vexsoft.core.paper.reactor.context.DamageEntityReactorContext;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.reactor.ReactorId;
import dev.vexsoft.core.reactor.trigger.Trigger;
import java.util.Objects;

@ReactorId("damage-entity")
@Dependencies
public final class DamageEntityTrigger implements Trigger<DamageEntityReactorContext> {

  public DamageEntityTrigger(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Class<DamageEntityReactorContext> getContextType() {
    return DamageEntityReactorContext.class;
  }
}
