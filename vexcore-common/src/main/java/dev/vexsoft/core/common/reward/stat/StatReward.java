package dev.vexsoft.core.common.reward.stat;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.stats.StatLocalizationService;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardContribution;
import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import dev.vexsoft.core.stats.contribution.StatRewardContribution;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Reconstructable {@code stats} reward backed by the shared stat registry. */
@Dependencies({ExpressionService.class, StatRegistry.class, StatLocalizationService.class})
public final class StatReward implements Reward {

  private final ExpressionService expressions;
  private final StatRegistry stats;
  private final StatLocalizationService localizations;

  /** Resolves expression and stat services. */
  public StatReward(final VexServiceRegistry services) {
    expressions = services.require(ExpressionService.class);
    stats = services.require(StatRegistry.class);
    localizations = services.require(StatLocalizationService.class);
  }

  @Override
  public CompiledReward compile(final Object value) {
    Map<String, Object> values = values(value);
    Map<Stat, CompiledExpression> compiled = new LinkedHashMap<>();
    values.forEach((key, expression) -> compiled.put(
        resolveStat(key),
        expressions.compile(Objects.toString(expression))
    ));
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

  private static Map<String, Object> values(final Object value) {
    if (value instanceof ConfigurationSection section) {
      return section.getValues(false);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, entry) -> result.put(Objects.toString(key), entry));
      return result;
    }
    throw new IllegalArgumentException("stats reward must be a map");
  }

  private record Compiled(
      Map<Stat, CompiledExpression> values,
      StatLocalizationService localizations
  ) implements CompiledReward {

    @Override
    public RewardBehavior getBehavior() {
      return RewardBehavior.CONTRIBUTION;
    }

    @Override
    public RewardContribution contribute(final PlayerExecutionContext context) {
      Map<StatKey, StatModifier> modifiers = new LinkedHashMap<>();
      values.forEach((stat, expression) -> modifiers.put(
          stat.getKey(),
          StatModifier.flat(expression.evaluateNumber(context))
      ));
      return new StatRewardContribution(modifiers);
    }

    @Override
    public Component describe(final PlayerExecutionContext context) {
      Component result = Component.empty();
      boolean first = true;
      for (Map.Entry<Stat, CompiledExpression> entry : values.entrySet()) {
        if (!first) {
          result = result.append(Component.text(", ", NamedTextColor.DARK_GRAY));
        }
        double amount = entry.getValue().evaluateNumber(context);
        result = result.append(Component.text('+' + format(amount) + ' ', NamedTextColor.GREEN))
            .append(localizations.getName(context.player(), entry.getKey().getKey()));
        first = false;
      }
      return result;
    }

    @Override
    public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
      return values.entrySet().stream().map(entry -> {
        String amount = format(entry.getValue().evaluateNumber(context));
        Component name = localizations.getName(context.player(), entry.getKey().getKey());
        Component fallback = Component.text('+' + amount + ' ', NamedTextColor.GREEN).append(name);
        return new ExecutionDescription(
            "",
            Map.of("amount", Component.text(amount), "name", name),
            fallback
        );
      }).toList();
    }

    private static String format(final double value) {
      return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
  }
}
