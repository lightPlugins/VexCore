package dev.vexsoft.core.paper.reactor.filter;

import dev.vexsoft.core.paper.reactor.context.DamageReactorContext;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.filter.CompiledFilter;
import dev.vexsoft.core.gameplay.reactor.filter.Filter;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.event.entity.EntityDamageEvent;

@ReactorId("damage-types")
@Dependencies
public final class DamageTypesFilter implements Filter<DamageReactorContext> {

  public DamageTypesFilter(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Class<DamageReactorContext> getContextType() {
    return DamageReactorContext.class;
  }

  @Override
  public CompiledFilter<DamageReactorContext> compile(final Object configuration) {
    Collection<?> values = configuration instanceof Collection<?> collection
        ? collection : List.of(configuration);
    if (values.isEmpty()) {
      throw new IllegalArgumentException("Damage-types filter requires at least one type");
    }
    EnumSet<EntityDamageEvent.DamageCause> accepted = EnumSet.noneOf(
        EntityDamageEvent.DamageCause.class
    );
    for (Object value : values) {
      accepted.add(parse(value));
    }
    return context -> accepted.contains(context.getDamageCause());
  }

  private EntityDamageEvent.DamageCause parse(final Object value) {
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("Damage type must be a non-empty string");
    }
    String key = text.trim();
    int separator = key.indexOf(':');
    if (separator >= 0) {
      String namespace = key.substring(0, separator);
      if (!"minecraft".equals(namespace)) {
        throw new IllegalArgumentException("Unsupported damage type namespace: " + namespace);
      }
      key = key.substring(separator + 1);
    }
    try {
      return EntityDamageEvent.DamageCause.valueOf(
          key.replace('-', '_').toUpperCase(Locale.ROOT)
      );
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown Minecraft damage type: " + text, exception);
    }
  }
}
