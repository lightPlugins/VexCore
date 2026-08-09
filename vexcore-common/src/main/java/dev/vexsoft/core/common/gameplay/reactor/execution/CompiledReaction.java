package dev.vexsoft.core.common.gameplay.reactor.execution;


import dev.vexsoft.core.gameplay.reactor.condition.CompiledCondition;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import dev.vexsoft.core.gameplay.reactor.effect.CompiledEffect;
import dev.vexsoft.core.gameplay.reactor.filter.CompiledFilter;
import java.util.Objects;
import lombok.Value;

@Value
public final class CompiledReaction {
  String owner;
  String id;
  CompiledFilter<ReactorContext>[] filters;
  CompiledCondition<ReactorContext>[] conditions;
  NamedEffect[] effects;

  public boolean matches(final ReactorContext context) {
    for (CompiledFilter<ReactorContext> filter : filters) {
      if (!filter.test(context)) {
        return false;
      }
    }
    for (CompiledCondition<ReactorContext> condition : conditions) {
      if (!condition.test(context)) {
        return false;
      }
    }
    return true;
  }

  @Value
  public static class NamedEffect {
    String id;
    CompiledEffect<ReactorContext> effect;

    public NamedEffect(final String id, final CompiledEffect<ReactorContext> effect) {
      this.id = Objects.requireNonNull(id, "id");
      this.effect = Objects.requireNonNull(effect, "effect");
    }
  }
}
