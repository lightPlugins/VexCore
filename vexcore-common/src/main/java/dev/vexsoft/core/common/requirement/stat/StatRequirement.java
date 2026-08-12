package dev.vexsoft.core.common.requirement.stat;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.stats.StatLocalizationService;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.requirement.CompiledRequirement;
import dev.vexsoft.core.requirement.Requirement;
import dev.vexsoft.core.requirement.RequirementResult;
import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatContainer;
import dev.vexsoft.core.stats.StatKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Minimum-value {@code stats} requirement backed by the shared stat system. */
@Dependencies({ExpressionService.class, StatRegistry.class, StatLocalizationService.class})
public final class StatRequirement implements Requirement {

  private final ExpressionService expressions;
  private final StatRegistry stats;
  private final StatLocalizationService localizations;

  /** Resolves expression and stat services. */
  public StatRequirement(final VexServiceRegistry services) {
    expressions = services.require(ExpressionService.class);
    stats = services.require(StatRegistry.class);
    localizations = services.require(StatLocalizationService.class);
  }

  @Override
  public CompiledRequirement compile(final Object value) {
    Map<String, Object> raw;
    if (value instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    } else if (value instanceof Map<?, ?> map) {
      raw = new LinkedHashMap<>();
      map.forEach((key, entry) -> raw.put(Objects.toString(key), entry));
    } else {
      throw new IllegalArgumentException("stats requirement must be a map");
    }
    Map<Stat, CompiledExpression> compiled = new LinkedHashMap<>();
    raw.forEach((key, expression) -> compiled.put(resolveStat(key), expressions.compile(
        Objects.toString(expression)
    )));
    return new Compiled(Map.copyOf(compiled), localizations);
  }

  private Stat resolveStat(final String input) {
    if (input.indexOf(':') >= 0) {
      return stats.require(StatKey.parse(input));
    }
    return stats.getRegisteredStats().stream()
        .filter(stat -> stat.getKey().value().equals(input))
        .reduce((first, second) -> {
          throw new IllegalArgumentException("Ambiguous stat key '" + input + "'; use namespace:value");
        })
        .orElseThrow(() -> new IllegalArgumentException("Unknown stat: " + input));
  }

  private record Compiled(
      Map<Stat, CompiledExpression> values,
      StatLocalizationService localizations
  ) implements CompiledRequirement {

    @Override
    public RequirementResult test(final PlayerExecutionContext context) {
      StatContainer container = context.player().getContainer(StatContainer.class);
      for (Map.Entry<Stat, CompiledExpression> entry : values.entrySet()) {
        double required = entry.getValue().evaluateNumber(context);
        double current = container.getStat(entry.getKey()).getValue();
        if (current < required) {
          return RequirementResult.missing(
              entry.getKey().getKey() + " requires " + required + ", current " + current
          );
        }
      }
      return RequirementResult.success();
    }

    @Override
    public Component describe(final PlayerExecutionContext context) {
      StatContainer container = context.player().getContainer(StatContainer.class);
      Component result = Component.empty();
      boolean first = true;
      for (Map.Entry<Stat, CompiledExpression> entry : values.entrySet()) {
        double required = entry.getValue().evaluateNumber(context);
        double current = container.getStat(entry.getKey()).getValue();
        if (!first) {
          result = result.append(Component.text(", ", NamedTextColor.DARK_GRAY));
        }
        result = result.append(Component.text(current >= required ? "✔ " : "✘ ",
                current >= required ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(localizations.getName(context.player(), entry.getKey().getKey()))
            .append(Component.text(" " + current + '/' + required, NamedTextColor.GRAY));
        first = false;
      }
      return result;
    }
  }
}
