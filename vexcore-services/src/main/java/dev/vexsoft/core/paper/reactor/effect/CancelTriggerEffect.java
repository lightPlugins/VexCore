package dev.vexsoft.core.paper.reactor.effect;

import dev.vexsoft.core.paper.reactor.context.CancellableReactorContext;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.effect.CompiledEffect;
import dev.vexsoft.core.gameplay.reactor.effect.Effect;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import java.util.Map;
import java.util.Objects;

@ReactorId("cancel-trigger")
@Dependencies
public final class CancelTriggerEffect implements Effect<CancellableReactorContext> {

  public CancelTriggerEffect(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Class<CancellableReactorContext> getContextType() {
    return CancellableReactorContext.class;
  }

  @Override
  public CompiledEffect<CancellableReactorContext> compile(final Map<String, Object> arguments) {
    Objects.requireNonNull(arguments, "arguments");
    return context -> context.setCancelled(true);
  }
}
