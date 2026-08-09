package dev.vexsoft.core.paper.reactor.filter;

import dev.vexsoft.core.paper.reactor.context.TargetEntityReactorContext;
import dev.vexsoft.core.paper.service.reactor.EntityTypeRegistry;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.filter.CompiledFilter;
import dev.vexsoft.core.gameplay.reactor.filter.Filter;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.entity.Entity;

@ReactorId("not-entities")
@Dependencies(EntityTypeRegistry.class)
public final class NotEntitiesFilter implements Filter<TargetEntityReactorContext> {
  private final EntityTypeRegistry entities;

  public NotEntitiesFilter(final VexServiceRegistry services) {
    entities = Objects.requireNonNull(services, "services").require(EntityTypeRegistry.class);
  }

  @Override
  public Class<TargetEntityReactorContext> getContextType() {
    return TargetEntityReactorContext.class;
  }

  @Override
  public CompiledFilter<TargetEntityReactorContext> compile(final Object configuration) {
    Predicate<Entity> predicate = TypeFilterSupport.compile(configuration, entities::compile,
        "Entity");
    return context -> !predicate.test(context.getTarget());
  }
}
