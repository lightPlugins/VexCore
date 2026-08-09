package dev.vexsoft.core.common.gameplay.reactor.condition;

import dev.vexsoft.core.gameplay.reactor.condition.CompiledCondition;
import dev.vexsoft.core.gameplay.reactor.condition.Condition;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.context.PlayerReactorContext;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import dev.vexsoft.core.gameplay.reactor.expression.CompiledExpression;
import dev.vexsoft.core.api.service.reactor.ExpressionService;
import dev.vexsoft.core.common.gameplay.reactor.internal.ReactorArguments;
import dev.vexsoft.core.gameplay.stat.Stat;
import dev.vexsoft.core.gameplay.stat.StatContainer;
import dev.vexsoft.core.gameplay.stat.StatKey;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Compares a cached player stat against a configured expression. */
@ReactorId("stat-comparison")
@Dependencies({StatRegistry.class, ExpressionService.class})
public final class StatComparisonCondition implements Condition<PlayerReactorContext> {

  private final StatRegistry stats;
  private final ExpressionService expressions;

  /** Creates a condition backed by the shared stat and expression services. */
  public StatComparisonCondition(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    stats = checkedServices.require(StatRegistry.class);
    expressions = checkedServices.require(ExpressionService.class);
  }

  @Override
  public Class<PlayerReactorContext> getContextType() {
    return PlayerReactorContext.class;
  }

  @Override
  public CompiledCondition<PlayerReactorContext> compile(final Map<String, Object> arguments) {
    Stat stat = stats.require(StatKey.parse(ReactorArguments.string(arguments, "stat")));
    Comparison comparison = Comparison.parse(ReactorArguments.string(arguments, "operator"));
    CompiledExpression value = ReactorArguments.expression(expressions, arguments, "value");
    return context -> comparison.test(
        context.getPlayer().getContainer(StatContainer.class).getStat(stat).getValue(),
        value.evaluateNumber(context)
    );
  }

  private enum Comparison {
    EQUAL {
      @Override boolean test(final double left, final double right) {
        return Double.compare(left, right) == 0;
      }
    },
    NOT_EQUAL {
      @Override boolean test(final double left, final double right) {
        return Double.compare(left, right) != 0;
      }
    },
    GREATER_THAN {
      @Override boolean test(final double left, final double right) {
        return left > right;
      }
    },
    GREATER_THAN_OR_EQUAL {
      @Override boolean test(final double left, final double right) {
        return left >= right;
      }
    },
    LESS_THAN {
      @Override boolean test(final double left, final double right) {
        return left < right;
      }
    },
    LESS_THAN_OR_EQUAL {
      @Override boolean test(final double left, final double right) {
        return left <= right;
      }
    };

    abstract boolean test(double left, double right);

    private static Comparison parse(final String value) {
      try {
        return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("Unknown stat comparison operator: " + value, exception);
      }
    }
  }
}
